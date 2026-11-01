package guru.nicks.commons.cucumber;

import guru.nicks.commons.auth.domain.BasicAuthCredentials;
import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.feign.domain.ExpirableHeader;
import guru.nicks.commons.feign.injector.OAuth2AuthorizationCodeInjector;
import guru.nicks.commons.feign.mapper.ExpirableHeaderMapper;
import guru.nicks.commons.rest.dto.OAuth2AccessTokenDto;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Step definitions for testing {@link OAuth2AuthorizationCodeInjector}. All step patterns are prefixed with
 * 'authorization code' to avoid duplicates with client credentials flow steps. HTTP layer is mocked with
 * {@link MockRestServiceServer} bound to a real {@link RestClient}.
 */
@RequiredArgsConstructor
public class OAuth2AuthorizationCodeInjectorSteps {

    // DI
    private final TextWorld textWorld;

    // test data
    private String tokenUrl;
    private BasicAuthCredentials clientCredentials;
    private String scope;

    private RestClient restClient;
    private MockRestServiceServer mockServer;
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

    /**
     * Parses an url-encoded form body into a map.
     */
    private static Map<String, String> parseFormData(String body) {
        Map<String, String> formData = new LinkedHashMap<>();

        for (String pair : body.split("&")) {
            int separatorIndex = pair.indexOf('=');
            formData.put(
                    URLDecoder.decode(pair.substring(0, separatorIndex), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(separatorIndex + 1), StandardCharsets.UTF_8));
        }

        return formData;
    }

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

    @Given("an authorization code mock HTTP server that returns a successful token response with access token {string} and expires in {long} seconds")
    public void givenSuccessfulTokenResponse(String accessToken, long expiresIn) {
        if (restClient == null) {
            restClient = createRestClientWithMockServer();
        }
        expectedAccessToken = accessToken;
        expectedExpiresIn = expiresIn;

        tokenResponseStatus = HttpStatus.OK;
        setupSuccessfulTokenResponse();
    }

    @Given("an authorization code mock HTTP server that returns a successful token response")
    public void givenSuccessfulTokenResponseSimple() {
        if (restClient == null) {
            restClient = createRestClientWithMockServer();
        }

        expectedAccessToken = "default-token";
        expectedExpiresIn = 3600L;
        tokenResponseStatus = HttpStatus.OK;
        setupSuccessfulTokenResponse();
    }

    @Given("an authorization code RestClient null")
    public void givenNullRestClient() {
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

    @Given("a mock HTTP server that returns authorization code {string} in Location header")
    public void givenAuthCodeInLocationHeader(String authCode) {
        if (restClient == null) {
            restClient = createRestClientWithMockServer();
        }
        expectedAuthCode = authCode;
        authCodeLocation = "header";
        setupSuccessfulAuthCodeResponse();
    }

    @Given("a mock HTTP server that returns authorization code in Location header")
    public void givenAuthCodeInLocationHeaderDefault() {
        if (restClient == null) {
            restClient = createRestClientWithMockServer();
        }
        expectedAuthCode = "default-auth-code";
        authCodeLocation = "header";
        setupSuccessfulAuthCodeResponse();
    }

    @Given("a mock HTTP server that returns authorization code {string} in response body")
    public void givenAuthCodeInResponseBody(String authCode) {
        if (restClient == null) {
            restClient = createRestClientWithMockServer();
        }
        expectedAuthCode = authCode;
        authCodeLocation = "body";
        setupSuccessfulAuthCodeResponse();
    }

    @Given("a mock HTTP server that returns an authorization response without authorization code")
    public void givenAuthResponseWithoutCode() {
        if (restClient == null) {
            restClient = createRestClientWithMockServer();
        }
        mockServer.expect(ExpectedCount.once(), requestTo(authorizationUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));
    }

    @Given("an authorization code mock HTTP server that returns a {int} response when exchanging code for token")
    public void givenUnauthorizedTokenResponse(int statusCode) {
        if (restClient == null) {
            restClient = createRestClientWithMockServer();
        }
        tokenResponseStatus = HttpStatus.valueOf(statusCode);
        setupErrorTokenResponse();
    }

    @Given("a mock HTTP server that throws RestClientException when requesting authorization code")
    public void givenRestClientExceptionOnAuthRequest() {
        if (restClient == null) {
            restClient = createRestClientWithMockServer();
        }

        setupRestClientExceptionOnAuthRequest();
    }

    @Given("an authorization code mock HTTP server that throws RestClientException when exchanging code for token")
    public void givenRestClientExceptionOnTokenExchange() {
        if (restClient == null) {
            restClient = createRestClientWithMockServer();
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
     * Creates a real {@link RestClient} backed by {@link MockRestServiceServer}. A single mock server instance serves
     * both the authorization URL and the token URL.
     */
    private RestClient createRestClientWithMockServer() {
        var builder = RestClient.builder();
        mockServer = MockRestServiceServer
                .bindTo(builder)
                .ignoreExpectOrder(true)
                .build();

        return builder.build();
    }

    /**
     * Obtains a fresh header from the injector.
     */
    private ExpirableHeader obtainFreshHeaderFromInjector() {
        return injector.obtainFreshHeader();
    }

    /**
     * Sets up the mock server to return a successful token response.
     */
    private void setupSuccessfulTokenResponse() {
        // invalid token URL - constructor validation fails before any HTTP call, expectation would never match
        if (tokenUrl == null) {
            return;
        }

        String responseJson =
                "{\"access_token\":\"" + expectedAccessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":"
                        + expectedExpiresIn + "}";

        mockServer.expect(ExpectedCount.once(), requestTo(tokenUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(this::assertTokenRequestFormData)
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));
    }

    /**
     * Asserts that the token exchange request carries the expected form data.
     */
    private void assertTokenRequestFormData(ClientHttpRequest request) {
        Map<String, String> formData = parseFormData(((MockClientHttpRequest) request).getBodyAsString());

        assertThat(formData.get("grant_type"))
                .as("grant_type")
                .isEqualTo("authorization_code");

        assertThat(formData.get("code"))
                .as("code")
                .isEqualTo(expectedAuthCode);

        assertThat(formData.get("redirect_uri"))
                .as("redirect_uri")
                .isEqualTo(redirectUrl);

        if (StringUtils.isNotBlank(scope)) {
            assertThat(formData.get("scope"))
                    .as("scope")
                    .isEqualTo(scope);
        }
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
     * Sets up the mock server to return a successful authorization code response (in Location header or in body).
     */
    private void setupSuccessfulAuthCodeResponse() {
        mockServer.expect(ExpectedCount.once(), requestTo(authorizationUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(this::assertAuthCodeRequestFormData)
                .andRespond(this::createAuthCodeResponse);
    }

    /**
     * Asserts that the authorization request carries the expected form data.
     */
    private void assertAuthCodeRequestFormData(ClientHttpRequest request) {
        Map<String, String> formData = parseFormData(((MockClientHttpRequest) request).getBodyAsString());

        assertThat(formData.get("response_type"))
                .as("response_type")
                .isEqualTo("code");

        assertThat(formData.get("client_id"))
                .as("client_id")
                .isEqualTo(clientCredentials.getUsername());

        assertThat(formData.get("redirect_uri"))
                .as("redirect_uri")
                .isEqualTo(redirectUrl);

        assertThat(formData.get("username"))
                .as("username")
                .isEqualTo(userCredentials.getUsername());

        assertThat(formData.get("password"))
                .as("password")
                .isEqualTo(userCredentials.getPassword());

        if (StringUtils.isNotBlank(scope)) {
            assertThat(formData.get("scope"))
                    .as("scope")
                    .isEqualTo(scope);
        }
    }

    /**
     * Creates the authorization code response: either 200 with Location header (redirect-style) or 200 with the code in
     * the body.
     */
    private ClientHttpResponse createAuthCodeResponse(ClientHttpRequest request) {
        String body = "header".equals(authCodeLocation) ? "" : "code=" + expectedAuthCode;
        var response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);

        if ("header".equals(authCodeLocation)) {
            response.getHeaders().setLocation(URI.create(redirectUrl + "?code=" + expectedAuthCode));
        }

        return response;
    }

    /**
     * Sets up the mock server to return an error token response.
     */
    private void setupErrorTokenResponse() {
        mockServer.expect(ExpectedCount.once(), requestTo(tokenUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(tokenResponseStatus));
    }

    /**
     * Sets up the mock server to fail the authorization request with a connection error (surfaces as a
     * {@link RestClientException} subtype).
     */
    private void setupRestClientExceptionOnAuthRequest() {
        mockServer.expect(ExpectedCount.once(), requestTo(authorizationUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("Simulated connection error on auth request")));
    }

    /**
     * Sets up the mock server to succeed the authorization request, then fail the token exchange with a connection
     * error (surfaces as a {@link RestClientException} subtype).
     */
    private void setupRestClientExceptionOnTokenExchange() {
        // first call (auth request) succeeds
        setupSuccessfulAuthCodeResponse();

        // second call (token exchange) fails
        mockServer.expect(ExpectedCount.once(), requestTo(tokenUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("Simulated connection error on token exchange")));
    }

    /**
     * Concrete test implementation.
     */
    private static class TestOAuth2AuthorizationCodeInjector extends OAuth2AuthorizationCodeInjector {

        public TestOAuth2AuthorizationCodeInjector(String tokenUrl, BasicAuthCredentials clientCredentials,
                String scope, BasicAuthCredentials userCredentials, String authorizationUrl, String redirectUrl,
                RestClient restClient, ExpirableHeaderMapper expirableHeaderMapper) {
            super(tokenUrl, clientCredentials, scope, userCredentials, authorizationUrl, redirectUrl,
                    restClient, expirableHeaderMapper);
        }

        @Override
        protected void sendAlert(Throwable t) {
            // do nothing in tests
        }

    }

}
