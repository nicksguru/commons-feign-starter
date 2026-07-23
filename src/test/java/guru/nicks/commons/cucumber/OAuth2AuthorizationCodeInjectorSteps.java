package guru.nicks.commons.cucumber;

import guru.nicks.commons.auth.domain.BasicAuthCredentials;
import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.feign.domain.ExpirableHeader;
import guru.nicks.commons.feign.injector.OAuth2AuthorizationCodeInjector;
import guru.nicks.commons.feign.mapper.ExpirableHeaderMapper;
import guru.nicks.commons.rest.v1.dto.OAuth2AccessTokenDto;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Step definitions for testing {@link OAuth2AuthorizationCodeInjector}. All step patterns are prefixed with
 * 'authorization code' to avoid duplicates with client credentials flow steps.
 */
@RequiredArgsConstructor
public class OAuth2AuthorizationCodeInjectorSteps {

    // DI
    private final TextWorld textWorld;

    // test data
    private String tokenUrl;
    private BasicAuthCredentials clientCredentials;
    private String scope;

    private RestOperations restClient;
    private ExpirableHeaderMapper expirableHeaderMapper;
    private String expectedAccessToken;
    private Long expectedExpiresIn;
    private HttpStatus tokenResponseStatus;

    private boolean expirableHeaderMapperExplicitlySetToNull;
    private TestOAuth2AuthorizationCodeInjector injector;
    private String authorizationUrl;
    private String redirectUrl;

    private BasicAuthCredentials userCredentials;
    private String expectedAuthCode;
    // 'header' or 'body'
    private String authCodeLocation;

    @Given("an authorization code token URL {string}")
    public void givenTokenUrl(String tokenUrl) {
        this.tokenUrl = "null".equals(tokenUrl)
                ? null
                : tokenUrl;
    }

    @Given("an authorization code token URL null")
    public void givenNullTokenUrl() {
        tokenUrl = null;
    }

    @Given("authorization code client credentials with client ID {string} and client secret {string}")
    public void givenClientCredentials(String clientId, String clientSecret) {
        clientCredentials = BasicAuthCredentials.builder()
                .username("null".equals(clientId) ? null : clientId)
                .password("null".equals(clientSecret) ? null : clientSecret)
                .build();
    }

    @Given("authorization code client credentials null")
    public void givenNullClientCredentials() {
        clientCredentials = null;
    }

    @Given("authorization code client credentials with client ID {string} and client secret null")
    public void givenClientCredentialsWithNullSecret(String clientId) {
        clientCredentials = BasicAuthCredentials.builder()
                .username("null".equals(clientId) ? null : clientId)
                .password(null)
                .build();
    }

    @Given("an authorization code scope {string}")
    public void givenScope(String scope) {
        this.scope = scope;
    }

    @Given("an authorization code RestOperations mock that returns a successful token response with access token {string} and expires in {long} seconds")
    public void givenSuccessfulTokenResponse(String accessToken, long expiresIn) {
        if (restClient == null) {
            restClient = mock(RestOperations.class);
        }
        expectedAccessToken = accessToken;
        expectedExpiresIn = expiresIn;

        tokenResponseStatus = HttpStatus.OK;
        setupSuccessfulTokenResponse();
    }

    @Given("an authorization code RestOperations mock that returns a successful token response")
    public void givenSuccessfulTokenResponseSimple() {
        if (restClient == null) {
            restClient = mock(RestOperations.class);
        }

        expectedAccessToken = "default-token";
        expectedExpiresIn = 3600L;
        tokenResponseStatus = HttpStatus.OK;
        setupSuccessfulTokenResponse();
    }

    @Given("an authorization code RestOperations mock null")
    public void givenNullRestOperations() {
        restClient = null;
    }

    @Given("an authorization code ExpirableHeaderMapper null")
    public void givenNullExpirableHeaderMapper() {
        expirableHeaderMapper = null;
        expirableHeaderMapperExplicitlySetToNull = true;
    }

    @Given("an authorization URL {string}")
    public void givenAuthorizationUrl(String authorizationUrl) {
        this.authorizationUrl = "null".equals(authorizationUrl)
                ? null
                : authorizationUrl;
    }

    @Given("a redirect URL {string}")
    public void givenRedirectUrl(String redirectUrl) {
        this.redirectUrl = "null".equals(redirectUrl)
                ? null
                : redirectUrl;
    }

    @Given("user credentials with username {string} and password {string}")
    public void givenUserCredentials(String username, String password) {
        userCredentials = BasicAuthCredentials.builder()
                .username("null".equals(username) ? null : username)
                .password("null".equals(password) ? null : password)
                .build();
    }

    @Given("user credentials null")
    public void givenNullUserCredentials() {
        userCredentials = null;
    }

    @Given("a RestOperations mock that returns authorization code {string} in Location header")
    public void givenAuthCodeInLocationHeader(String authCode) {
        restClient = mock(RestOperations.class);
        expectedAuthCode = authCode;
        authCodeLocation = "header";
        setupSuccessfulAuthCodeResponse();
    }

    @Given("a RestOperations mock that returns authorization code in Location header")
    public void givenAuthCodeInLocationHeaderDefault() {
        restClient = mock(RestOperations.class);
        expectedAuthCode = "default-auth-code";
        authCodeLocation = "header";
        setupSuccessfulAuthCodeResponse();
    }

    @Given("a RestOperations mock that returns authorization code {string} in response body")
    public void givenAuthCodeInResponseBody(String authCode) {
        restClient = mock(RestOperations.class);
        expectedAuthCode = authCode;
        authCodeLocation = "body";
        setupSuccessfulAuthCodeResponse();
    }

    @Given("a RestOperations mock that returns an authorization response without authorization code")
    public void givenAuthResponseWithoutCode() {
        restClient = mock(RestOperations.class);
        setupAuthResponseWithoutCode();
    }

    @Given("an authorization code RestOperations mock that returns a {int} response when exchanging code for token")
    public void givenUnauthorizedTokenResponse(int statusCode) {
        if (restClient == null) {
            restClient = mock(RestOperations.class);
        }
        tokenResponseStatus = HttpStatus.valueOf(statusCode);
        setupErrorTokenResponse();
    }

    @Given("a RestOperations mock that throws RestClientException when requesting authorization code")
    public void givenRestClientExceptionOnAuthRequest() {
        if (restClient == null) {
            restClient = mock(RestOperations.class);
        }

        setupRestClientExceptionOnAuthRequest();
    }

    @Given("an authorization code RestOperations mock that throws RestClientException when exchanging code for token")
    public void givenRestClientExceptionOnTokenExchange() {
        if (restClient == null) {
            restClient = mock(RestOperations.class);
        }

        setupRestClientExceptionOnTokenExchange();
    }

    @When("an OAuth2AuthorizationCodeInjector is created with these parameters")
    public void whenOAuth2AuthorizationCodeInjectorIsCreated() {
        textWorld.setLastException(catchThrowable(() -> {
            // create default mapper for scenarios that need it
            // only skip if explicitly testing null mapper validation
            if ((expirableHeaderMapper == null)
                    && (restClient != null)
                    && !expirableHeaderMapperExplicitlySetToNull) {
                expirableHeaderMapper = mock(ExpirableHeaderMapper.class);
                setupExpirableHeaderMapper();
            }

            injector = new TestOAuth2AuthorizationCodeInjector(tokenUrl, clientCredentials, scope,
                    userCredentials, authorizationUrl, redirectUrl, restClient, expirableHeaderMapper);
        }));
    }

    @Then("the authorization code header name should be {string}")
    public void thenHeaderNameShouldBe(String expectedHeaderName) {
        assertThat(injector.getHeaderName())
                .as("header name")
                .isEqualTo(expectedHeaderName);
    }

    @Then("the authorization code header value should start with {string}")
    public void thenHeaderValueShouldStartWith(String expectedPrefix) {
        String headerValue = injector.getHeaderValue();

        assertThat(headerValue)
                .as("header value")
                .startsWith(expectedPrefix);
    }

    @Then("the authorization code header value should contain the access token")
    public void thenHeaderValueShouldContainAccessToken() {
        String headerValue = injector.getHeaderValue();

        assertThat(headerValue)
                .as("header value")
                .contains(expectedAccessToken);
    }

    @Then("obtaining an authorization code fresh header should throw RestClientException")
    public void thenObtainingFreshHeaderShouldThrowRestClientException() {
        Throwable thrown = catchThrowable(this::obtainFreshHeaderFromInjector);

        assertThat(thrown)
                .as("exception thrown when obtaining fresh header")
                .isInstanceOf(org.springframework.web.client.RestClientException.class);
    }

    @Then("the authorization code header should have an expiration date")
    public void thenHeaderShouldHaveExpirationDate() {
        ExpirableHeader header = obtainFreshHeaderFromInjector();

        assertThat(header.getExpirationDate())
                .as("expiration date")
                .isNotNull();

        assertThat(header.getExpirationDate())
                .as("expiration date")
                .isAfter(Instant.now());
    }

    /**
     * Obtains a fresh header from the injector.
     */
    private ExpirableHeader obtainFreshHeaderFromInjector() {
        return injector.obtainFreshHeader();
    }

    /**
     * Sets up restClient mock to return a successful token response.
     */
    private void setupSuccessfulTokenResponse() {
        when(restClient.exchange(
                eq(tokenUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(OAuth2AccessTokenDto.class)
        )).thenAnswer(invocation -> {
            // verify the request contains correct parameters
            HttpEntity<?> requestEntity = invocation.getArgument(2);
            MultiValueMap<String, String> formData = (MultiValueMap<String, String>) requestEntity.getBody();

            assertThat(formData)
                    .as("form data")
                    .isNotNull();

            assertThat(formData.getFirst("grant_type"))
                    .as("grant_type")
                    .isEqualTo("authorization_code");

            assertThat(formData.getFirst("code"))
                    .as("code")
                    .isEqualTo(expectedAuthCode);

            assertThat(formData.getFirst("redirect_uri"))
                    .as("redirect_uri")
                    .isEqualTo(redirectUrl);

            // verify scope is included if provided
            if (StringUtils.isNotBlank(scope)) {
                assertThat(formData.getFirst("scope"))
                        .as("scope")
                        .isEqualTo(scope);
            }

            // return successful response
            OAuth2AccessTokenDto tokenDto = OAuth2AccessTokenDto.builder()
                    .accessToken(expectedAccessToken)
                    .tokenType("Bearer")
                    .expiresInSeconds(expectedExpiresIn)
                    .build();
            return ResponseEntity.ok(tokenDto);
        });
    }

    /**
     * Sets up the ExpirableHeaderMapper mock.
     */
    private void setupExpirableHeaderMapper() {
        when(expirableHeaderMapper.toHeader(any(OAuth2AccessTokenDto.class)))
                .thenAnswer(invocation -> {
                    OAuth2AccessTokenDto dto = invocation.getArgument(0);

                    return ExpirableHeader.builder()
                            .valuePrefix("Bearer ")
                            .value(dto.accessToken())
                            .issuedDate(Instant.now())
                            .expirationDate((dto.expiresInSeconds() != null)
                                    ? Instant.now().plusSeconds(dto.expiresInSeconds())
                                    : null)
                            .build();
                });
    }

    /**
     * Sets up restClient mock to return a successful authorization code response.
     */
    private void setupSuccessfulAuthCodeResponse() {
        when(restClient.exchange(eq(authorizationUrl), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))
        ).thenAnswer(invocation -> {
            // verify the request contains correct parameters
            HttpEntity<?> requestEntity = invocation.getArgument(2);
            MultiValueMap<String, String> formData = (MultiValueMap<String, String>) requestEntity.getBody();

            assertThat(formData)
                    .as("form data")
                    .isNotNull();

            assertThat(formData.getFirst("response_type"))
                    .as("response_type")
                    .isEqualTo("code");

            assertThat(formData.getFirst("client_id"))
                    .as("client_id")
                    .isEqualTo(clientCredentials.getUsername());

            assertThat(formData.getFirst("redirect_uri"))
                    .as("redirect_uri")
                    .isEqualTo(redirectUrl);

            assertThat(formData.getFirst("username"))
                    .as("username")
                    .isEqualTo(userCredentials.getUsername());

            assertThat(formData.getFirst("password"))
                    .as("password")
                    .isEqualTo(userCredentials.getPassword());

            // verify scope is included if provided
            if (StringUtils.isNotBlank(scope)) {
                assertThat(formData.getFirst("scope"))
                        .as("scope")
                        .isEqualTo(scope);
            }

            // return response with authorization code
            HttpHeaders headers = new HttpHeaders();
            if ("header".equals(authCodeLocation)) {
                headers.setLocation(java.net.URI.create(redirectUrl + "?code=" + expectedAuthCode));
                return ResponseEntity.ok().headers(headers).body("");
            } else {
                return ResponseEntity.ok("code=" + expectedAuthCode);
            }
        });
    }

    /**
     * Sets up restClient mock to return an authorization response without code.
     */
    private void setupAuthResponseWithoutCode() {
        when(restClient.exchange(
                eq(authorizationUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(""));
    }

    /**
     * Sets up restClient mock to return an error token response.
     */
    private void setupErrorTokenResponse() {
        when(restClient.exchange(
                eq(tokenUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(OAuth2AccessTokenDto.class)
        )).thenReturn(new ResponseEntity<>(tokenResponseStatus));
    }

    /**
     * Sets up restClient mock to throw RestClientException on auth request.
     */
    private void setupRestClientExceptionOnAuthRequest() {
        when(restClient.exchange(
                eq(authorizationUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RestClientException("Simulated REST client error on auth request"));
    }

    /**
     * Sets up restClient mock to throw RestClientException on token exchange.
     */
    private void setupRestClientExceptionOnTokenExchange() {
        // first call (auth request) succeeds
        setupSuccessfulAuthCodeResponse();

        // second call (token exchange) fails
        when(restClient.exchange(
                eq(tokenUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(OAuth2AccessTokenDto.class)
        )).thenThrow(new RestClientException("Simulated REST client error on token exchange"));
    }

    /**
     * Concrete test implementation.
     */
    private static class TestOAuth2AuthorizationCodeInjector extends OAuth2AuthorizationCodeInjector {

        public TestOAuth2AuthorizationCodeInjector(String tokenUrl, BasicAuthCredentials clientCredentials,
                String scope, BasicAuthCredentials userCredentials, String authorizationUrl, String redirectUrl,
                RestOperations restClient, ExpirableHeaderMapper expirableHeaderMapper) {
            super(tokenUrl, clientCredentials, scope, userCredentials, authorizationUrl, redirectUrl,
                    restClient, expirableHeaderMapper);
        }

        @Override
        protected void sendAlert(Throwable t) {
            // do nothing in tests
        }

    }

}
