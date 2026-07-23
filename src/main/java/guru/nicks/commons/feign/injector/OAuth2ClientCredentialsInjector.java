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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import static guru.nicks.commons.validation.dsl.ValiDsl.checkNotBlank;
import static guru.nicks.commons.validation.dsl.ValiDsl.checkNotNull;

/**
 * OAuth2 Feign header injector that obtains access token using OAuth2 Client Credentials Flow.
 * <p>
 * This injector implements the client credentials flow by directly requesting an access token from the token endpoint
 * using client credentials (client_id and client_secret). This flow is suitable for server-to-server authentication
 * where no user interaction is required.
 */
@Slf4j
public abstract class OAuth2ClientCredentialsInjector extends ExpirableFeignHeaderInjector {

    @Getter(AccessLevel.PROTECTED)
    protected final String tokenUrl;
    private final String scope;
    private final HttpHeaders clientAuthHeaders;

    private final RestOperations restClient;
    private final ExpirableHeaderMapper expirableHeaderMapper;

    /**
     * Constructor. Validates OAuth2 provider configuration and sets up client authentication headers for token endpoint
     * requests.
     *
     * @param tokenUrl              OAuth2 token URL
     * @param clientCredentials     OAuth2 client credentials (username = client_id; password = client_secret which is
     *                              optional)
     * @param scope                 OAuth2 scope (optional)
     * @param restClient            REST client
     * @param expirableHeaderMapper mapper for converting {@link OAuth2AccessTokenDto} to {@link ExpirableHeader}
     * @throws IllegalArgumentException any required parameter is {@code null} or blank
     */
    @ConstraintArguments
    protected OAuth2ClientCredentialsInjector(String tokenUrl, BasicAuthCredentials clientCredentials,
            @Nullable String scope, RestOperations restClient, ExpirableHeaderMapper expirableHeaderMapper) {
        this.tokenUrl = checkNotBlank(tokenUrl, _OAuth2ClientCredentialsInjectorArgumentsMeta.TOKENURL.name());

        checkNotNull(clientCredentials, _OAuth2ClientCredentialsInjectorArgumentsMeta.CLIENTCREDENTIALS.name());
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
        this.restClient = checkNotNull(restClient,
                _OAuth2ClientCredentialsInjectorArgumentsMeta.RESTCLIENT.name());
        this.expirableHeaderMapper = checkNotNull(expirableHeaderMapper,
                _OAuth2ClientCredentialsInjectorArgumentsMeta.EXPIRABLEHEADERMAPPER.name());

        // create Basic Auth header for client authentication
        String headerValue = clientCredentials.convertToHeaderValue();
        clientAuthHeaders = new HttpHeaders();
        clientAuthHeaders.add(HttpHeaders.AUTHORIZATION, headerValue);
        clientAuthHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        log.info("OAuth2 client credentials flow injector initialized for token URL '{}'", this.tokenUrl);
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
        return obtainAccessToken();
    }

    /**
     * Fetches access token using Client Credentials flow.
     *
     * @return header containing the access token
     * @throws RestClientException token request failed
     */
    private ExpirableHeader obtainAccessToken() {
        MultiValueMap<String, String> tokenFormData = new LinkedMultiValueMap<>();
        tokenFormData.add("grant_type", "client_credentials");

        if (StringUtils.isNotBlank(scope)) {
            tokenFormData.add("scope", scope);
        }

        log.info("Requesting access token using client credentials from '{}'", tokenUrl);
        ResponseEntity<OAuth2AccessTokenDto> tokenResponse;

        try {
            tokenResponse = restClient.exchange(tokenUrl, HttpMethod.POST,
                    new HttpEntity<>(tokenFormData, clientAuthHeaders), OAuth2AccessTokenDto.class);
        } catch (RestClientException e) {
            throw new RestClientException("Failed to fetch access token using client credentials from '"
                    + tokenUrl
                    + "': " + e.getMessage(), e);
        }

        if (!tokenResponse.getStatusCode().is2xxSuccessful()) {
            throw new RestClientException("Failed to fetch access token using client credentials from '"
                    + tokenUrl
                    + "': " + tokenResponse.getStatusCode()
                    + " - " + tokenResponse.getBody());
        }

        checkNotNull(tokenResponse.getBody(), "Token response body");
        log.info("Successfully obtained access token using client credentials from '{}'", tokenUrl);
        return expirableHeaderMapper.toHeader(tokenResponse.getBody());
    }

}
