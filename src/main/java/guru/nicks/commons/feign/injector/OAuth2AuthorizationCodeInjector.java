package guru.nicks.commons.feign.injector;

import guru.nicks.commons.auth.domain.BasicAuthCredentials;
import guru.nicks.commons.feign.domain.ExpirableHeader;
import guru.nicks.commons.feign.mapper.ExpirableHeaderMapper;
import guru.nicks.commons.rest.v1.dto.OAuth2AccessTokenDto;

import am.ik.yavi.meta.ConstraintArguments;
import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static guru.nicks.commons.validation.dsl.ValiDsl.checkNotBlank;
import static guru.nicks.commons.validation.dsl.ValiDsl.checkNotNull;

/**
 * OAuth2 Feign header injector that obtains access token using OAuth2 Authorization Code Flow. Consider creating beans
 * out of it using {@link Bean @Bean} methods.
 * <p>
 * This injector implements the complete authorization code flow. First sends user credentials to the authorization
 * endpoint to obtain an authorization code. Then exchanges the authorization code for an access token at the token
 * endpoint.
 */
@Slf4j
public abstract class OAuth2AuthorizationCodeInjector extends ExpirableFeignHeaderInjector {

    private static final Pattern AUTH_CODE_PATTERN = Pattern.compile("\\bcode=([^&]+)");

    @Getter(AccessLevel.PROTECTED)
    protected final String tokenUrl;

    @Getter(AccessLevel.PROTECTED)
    private final String authorizationUrl;
    @Getter(AccessLevel.PROTECTED)
    private final String redirectUrl;

    private final String scope;
    private final BasicAuthCredentials userCredentials;

    private final HttpHeaders clientAuthHeaders;
    private final String clientId;

    private final RestOperations restClient;
    private final ExpirableHeaderMapper expirableHeaderMapper;


    /**
     * Constructor. Validates auth provider configuration, user credentials, and URLs. Sets up client authentication
     * headers for token endpoint requests.
     *
     * @param tokenUrl              OAuth2 token URL
     * @param clientCredentials     OAuth2 client credentials (username = client_id; password = client_secret which is
     *                              optional)
     * @param scope                 OAuth2 scope (optional)
     * @param userCredentials       user credentials for authorization endpoint
     * @param authorizationUrl      authorization endpoint URL
     * @param redirectUrl           redirect URI (should be permitted on the auth provider side; no actual redirect is
     *                              performed, it's just a response header)
     * @param restClient            REST client
     * @param expirableHeaderMapper mapper for converting {@link OAuth2AccessTokenDto} to {@link ExpirableHeader}
     * @throws IllegalArgumentException any required parameter is {@code null} or blank
     */
    @ConstraintArguments
    protected OAuth2AuthorizationCodeInjector(String tokenUrl, BasicAuthCredentials clientCredentials,
            @Nullable String scope, BasicAuthCredentials userCredentials,
            String authorizationUrl, String redirectUrl,
            RestOperations restClient, ExpirableHeaderMapper expirableHeaderMapper) {
        this.tokenUrl = checkNotBlank(tokenUrl, _OAuth2AuthorizationCodeInjectorArgumentsMeta.TOKENURL.name());

        checkNotNull(clientCredentials, _OAuth2AuthorizationCodeInjectorArgumentsMeta.CLIENTCREDENTIALS.name());
        checkNotBlank(clientCredentials.getUsername(),
                _OAuth2AuthorizationCodeInjectorArgumentsMeta.CLIENTCREDENTIALS.name()
                        + "."
                        + BasicAuthCredentials.Fields.username);
        // client secret may be missing
        //        checkNotBlank(clientCredentials.getPassword(),
        //                _OAuth2AuthorizationCodeInjectorArgumentsMeta.CLIENTCREDENTIALS.name()
        //                        + "."
        //                        + BasicAuthCredentials.Fields.password);

        this.scope = scope;

        this.userCredentials = checkNotNull(userCredentials,
                _OAuth2AuthorizationCodeInjectorArgumentsMeta.USERCREDENTIALS.name());
        checkNotBlank(userCredentials.getUsername(),
                _OAuth2AuthorizationCodeInjectorArgumentsMeta.USERCREDENTIALS.name()
                        + "."
                        + BasicAuthCredentials.Fields.username);
        checkNotBlank(userCredentials.getPassword(),
                _OAuth2AuthorizationCodeInjectorArgumentsMeta.USERCREDENTIALS.name()
                        + "."
                        + BasicAuthCredentials.Fields.password);

        this.authorizationUrl = checkNotBlank(authorizationUrl,
                _OAuth2AuthorizationCodeInjectorArgumentsMeta.AUTHORIZATIONURL.name());
        this.redirectUrl = checkNotBlank(redirectUrl,
                _OAuth2AuthorizationCodeInjectorArgumentsMeta.REDIRECTURL.name());

        this.restClient = checkNotNull(restClient,
                _OAuth2AuthorizationCodeInjectorArgumentsMeta.RESTCLIENT.name());
        this.expirableHeaderMapper = checkNotNull(expirableHeaderMapper,
                _OAuth2AuthorizationCodeInjectorArgumentsMeta.EXPIRABLEHEADERMAPPER.name());

        // create Basic Auth header for client authentication
        String headerValue = clientCredentials.convertToHeaderValue();
        clientAuthHeaders = new HttpHeaders();
        clientAuthHeaders.add(HttpHeaders.AUTHORIZATION, headerValue);
        clientAuthHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        clientId = clientCredentials.getUsername();

        log.info("OAuth2 authorization code flow injector initialized for authorization URL '{}' and token URL '{}'",
                this.authorizationUrl, this.tokenUrl);
    }

    /**
     * @return {@value HttpHeaders#AUTHORIZATION}
     */
    @Override
    public String getHeaderName() {
        return HttpHeaders.AUTHORIZATION;
    }

    @Override
    public ExpirableHeader obtainFreshHeader() {
        String authorizationCode = obtainAuthorizationCode();
        return exchangeCodeForToken(authorizationCode);
    }

    /**
     * Sends user credentials to authorization endpoint to obtain authorization code.
     *
     * @return authorization code
     * @throws RestClientException authorization failed
     */
    private String obtainAuthorizationCode() {
        MultiValueMap<String, String> authFormData = new LinkedMultiValueMap<>();
        authFormData.add("response_type", "code");
        // never send client_secret
        authFormData.add("client_id", clientId);
        authFormData.add("redirect_uri", redirectUrl);
        authFormData.add("username", userCredentials.getUsername());
        authFormData.add("password", userCredentials.getPassword());

        if (StringUtils.isNotBlank(scope)) {
            authFormData.add("scope", scope);
        }

        log.info("Obtaining authorization code from '{}'", authorizationUrl);
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> authResponse;

        try {
            authResponse = restClient.exchange(authorizationUrl, HttpMethod.POST,
                    new HttpEntity<>(authFormData, authHeaders), String.class);
        } catch (RestClientException e) {
            throw new RestClientException("Failed to obtain authorization code from '" + authorizationUrl
                    + "': " + e.getMessage(), e);
        }

        String authorizationCode = extractAuthorizationCode(authResponse);
        if (StringUtils.isBlank(authorizationCode)) {
            throw new RestClientException("No authorization code found in response from '" + authorizationUrl + "'");
        }

        return authorizationCode;
    }

    /**
     * Extracts authorization code from the authorization server response. Handles both redirect responses (Location
     * header) and direct responses.
     *
     * @param response authorization server response
     * @return authorization code or {@code null} if not found
     */
    @Nullable
    private String extractAuthorizationCode(ResponseEntity<String> response) {
        // check Location header for redirect response
        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);

        if (StringUtils.isNotBlank(location)) {
            Matcher matcher = AUTH_CODE_PATTERN.matcher(location);

            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        // check response body for direct code response
        String responseBody = response.getBody();

        if (StringUtils.isNotBlank(responseBody)) {
            Matcher matcher = AUTH_CODE_PATTERN.matcher(responseBody);

            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    /**
     * Exchanges authorization code for access token.
     *
     * @param authorizationCode authorization code obtained earlier
     * @return header containing the access token
     * @throws RestClientException token exchange failed
     */
    private ExpirableHeader exchangeCodeForToken(String authorizationCode) {
        MultiValueMap<String, String> tokenFormData = new LinkedMultiValueMap<>();
        tokenFormData.add("grant_type", "authorization_code");
        tokenFormData.add("redirect_uri", redirectUrl);
        tokenFormData.add("code", authorizationCode);

        if (StringUtils.isNotBlank(scope)) {
            tokenFormData.add("scope", scope);
        }

        log.info("Exchanging authorization code for access token from '{}'", tokenUrl);
        ResponseEntity<OAuth2AccessTokenDto> tokenResponse;

        try {
            tokenResponse = restClient.exchange(tokenUrl, HttpMethod.POST,
                    new HttpEntity<>(tokenFormData, clientAuthHeaders), OAuth2AccessTokenDto.class);
        } catch (RestClientException e) {
            throw new RestClientException("Failed to exchange authorization code for access token from '"
                    + tokenUrl
                    + "': " + e.getMessage(), e);
        }

        if (!tokenResponse.getStatusCode().is2xxSuccessful()) {
            throw new RestClientException("Failed to exchange authorization code for access token from '"
                    + tokenUrl
                    + "': " + tokenResponse.getStatusCode()
                    + " - " + tokenResponse.getBody());
        }

        checkNotNull(tokenResponse.getBody(), "Token response body");
        log.info("Successfully obtained access token from '{}'", tokenUrl);
        return expirableHeaderMapper.toHeader(tokenResponse.getBody());
    }

}
