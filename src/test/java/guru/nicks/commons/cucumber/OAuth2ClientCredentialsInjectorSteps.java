package guru.nicks.commons.cucumber;

import guru.nicks.commons.auth.domain.BasicAuthCredentials;
import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.feign.domain.ExpirableHeader;
import guru.nicks.commons.feign.injector.OAuth2ClientCredentialsInjector;
import guru.nicks.commons.feign.mapper.ExpirableHeaderMapper;
import guru.nicks.commons.rest.dto.OAuth2AccessTokenDto;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
 * Step definitions for testing {@link OAuth2ClientCredentialsInjector}. All step patterns are prefixed with 'client
 * credentials' to avoid duplicates with authorization code flow steps. HTTP layer is mocked with
 * {@link MockRestServiceServer} bound to a real {@link RestClient}.
 */
@RequiredArgsConstructor
public class OAuth2ClientCredentialsInjectorSteps {

    // DI
    private final TextWorld textWorld;

    // test data
    private String tokenUrl;
    private BasicAuthCredentials clientCredentials;
    private String scope;
    private Long staleWindowMs;

    private RestClient restClient;
    private MockRestServiceServer mockServer;

    /**
     * Response for the token endpoint, delegated to at request time so later steps can override it mid-scenario.
     */
    private ResponseCreator nextTokenResponse;
    private ExpirableHeaderMapper expirableHeaderMapper;
    private String expectedAccessToken;
    private Long expectedExpiresIn;
    private HttpStatus tokenResponseStatus;

    private boolean expirableHeaderMapperExplicitlySetToNull;
    private TestOAuth2ClientCredentialsInjector injector;

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

    @Given("a client credentials token URL {string}")
    public void givenTokenUrl(String tokenUrl) {
        this.tokenUrl = "null".equals(tokenUrl)
                ? null
                : tokenUrl;
    }

    @Given("a client credentials token URL null")
    public void givenNullTokenUrl() {
        tokenUrl = null;
    }

    @Given("client credentials with client ID {string} and client secret {string}")
    public void givenClientCredentials(String clientId, String clientSecret) {
        clientCredentials = BasicAuthCredentials.builder()
                .username("null".equals(clientId) ? null : clientId)
                .password("null".equals(clientSecret) ? null : clientSecret)
                .build();
    }

    @Given("client credentials null")
    public void givenNullClientCredentials() {
        clientCredentials = null;
    }

    @Given("client credentials with client ID {string} and client secret null")
    public void givenClientCredentialsWithNullSecret(String clientId) {
        clientCredentials = BasicAuthCredentials.builder()
                .username("null".equals(clientId) ? null : clientId)
                .password(null)
                .build();
    }

    @Given("a client credentials scope {string}")
    public void givenScope(String scope) {
        this.scope = scope;
    }

    @Given("a client credentials mock HTTP server that returns a successful token response with access token {string} and expires in {long} seconds")
    public void givenSuccessfulTokenResponse(String accessToken, long expiresIn) {
        restClient = createRestClientWithMockServer();
        expectedAccessToken = accessToken;
        expectedExpiresIn = expiresIn;
        tokenResponseStatus = HttpStatus.OK;
        setupSuccessfulTokenResponse();
    }

    @Given("a client credentials mock HTTP server that returns a successful token response")
    public void givenSuccessfulTokenResponseSimple() {
        restClient = createRestClientWithMockServer();
        expectedAccessToken = "default-token";
        expectedExpiresIn = 3600L;
        tokenResponseStatus = HttpStatus.OK;
        setupSuccessfulTokenResponse();
    }

    @Given("a client credentials mock HTTP server that returns a {int} response")
    public void givenErrorResponse(int statusCode) {
        restClient = createRestClientWithMockServer();
        tokenResponseStatus = HttpStatus.valueOf(statusCode);
        setupErrorResponse();
    }

    @Given("a client credentials mock HTTP server that throws RestClientException")
    public void givenRestClientException() {
        restClient = createRestClientWithMockServer();
        setupRestClientException();
    }

    @Given("a client credentials RestClient null")
    public void givenNullRestClient() {
        restClient = null;
    }

    @Given("a client credentials stale window {long} milliseconds")
    public void givenStaleWindow(long staleWindowMs) {
        this.staleWindowMs = staleWindowMs;
    }

    @Given("the client credentials mock HTTP server starts throwing RestClientException")
    public void givenRestClientExceptionStarts() {
        // the expectation registered earlier delegates to the mutable response creator - just flip it
        nextTokenResponse = withException(new IOException("Simulated connection error"));
    }

    @Given("a client credentials ExpirableHeaderMapper null")
    public void givenNullExpirableHeaderMapper() {
        expirableHeaderMapper = null;
        expirableHeaderMapperExplicitlySetToNull = true;
    }

    @When("an OAuth2ClientCredentialsInjector is created with these parameters")
    public void whenOAuth2ClientCredentialsInjectorIsCreated() {
        textWorld.setLastException(catchThrowable(() -> {
            // create default mapper for scenarios that need it
            // only skip if explicitly testing null mapper validation
            if ((expirableHeaderMapper == null)
                    && (restClient != null)
                    && !expirableHeaderMapperExplicitlySetToNull) {
                expirableHeaderMapper = mock(ExpirableHeaderMapper.class);
                setupExpirableHeaderMapper();
            }

            injector = new TestOAuth2ClientCredentialsInjector(tokenUrl, clientCredentials, scope,
                    restClient, expirableHeaderMapper,
                    (staleWindowMs == null) ? null : Duration.ofMillis(staleWindowMs));
        }));
    }

    @When("the client credentials header value is obtained")
    public void whenHeaderValueIsObtained() {
        String value = null;
        Throwable thrown = null;

        try {
            value = injector.getHeaderValue();
        } catch (RuntimeException e) {
            thrown = e;
        }

        textWorld.setLastException(thrown);
        textWorld.setText(value);
    }

    @Then("the client credentials header name should be {string}")
    public void thenHeaderNameShouldBe(String expectedHeaderName) {
        assertThat(injector.getHeaderName())
                .as("header name")
                .isEqualTo(expectedHeaderName);
    }

    @Then("the client credentials header value should be {string}")
    public void thenHeaderValueShouldBe(String expectedValue) {
        assertThat(textWorld.getText())
                .as("header value")
                .isEqualTo(expectedValue);
    }

    @Then("the client credentials header value should start with {string}")
    public void thenHeaderValueShouldStartWith(String expectedPrefix) {
        String headerValue = injector.getHeaderValue();

        assertThat(headerValue)
                .as("header value")
                .startsWith(expectedPrefix);
    }

    @Then("the client credentials header value should contain the access token")
    public void thenHeaderValueShouldContainAccessToken() {
        String headerValue = injector.getHeaderValue();

        assertThat(headerValue)
                .as("header value")
                .contains(expectedAccessToken);
    }

    @Then("obtaining a client credentials fresh header should throw RestClientException")
    public void thenObtainingFreshHeaderShouldThrowRestClientException() {
        Throwable thrown = catchThrowable(this::obtainFreshHeaderFromInjector);

        assertThat(thrown)
                .as("exception thrown when obtaining fresh header")
                .isInstanceOf(org.springframework.web.client.RestClientException.class);
    }

    @Then("the client credentials header should have an expiration date")
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
     * Creates a real {@link RestClient} backed by {@link MockRestServiceServer}.
     */
    private RestClient createRestClientWithMockServer() {
        var builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder)
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
        nextTokenResponse = withSuccess(responseJson, MediaType.APPLICATION_JSON);
        registerTokenEndpointExpectation();
    }

    /**
     * Sets up the mock server to return an error response.
     */
    private void setupErrorResponse() {
        nextTokenResponse = withStatus(tokenResponseStatus);
        registerTokenEndpointExpectation();
    }

    /**
     * Makes subsequent token requests fail with a connection error (surfaces as a {@link RestClientException} subtype).
     * The endpoint expectation registered earlier delegates to the mutable response creator, because
     * {@link MockRestServiceServer} forbids adding expectations after actual requests are made.
     */
    private void setupRestClientException() {
        nextTokenResponse = withException(new IOException("Simulated connection error"));
        registerTokenEndpointExpectation();
    }

    /**
     * Registers the token endpoint expectation. The response creator is delegated to at request time, letting later
     * steps override {@link #nextTokenResponse} mid-scenario. The count is effectively unbounded because the injector
     * wraps token loading in a Resilience4j retry, issuing more requests than one per header read.
     */
    private void registerTokenEndpointExpectation() {
        mockServer.expect(ExpectedCount.manyTimes(), requestTo(tokenUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(this::assertTokenRequestFormData)
                .andRespond(request -> nextTokenResponse.createResponse(request));
    }

    /**
     * Asserts that the token request carries the expected form data.
     */
    private void assertTokenRequestFormData(ClientHttpRequest request) {
        Map<String, String> formData = parseFormData(((MockClientHttpRequest) request).getBodyAsString());

        assertThat(formData.get("grant_type"))
                .as("grant_type")
                .isEqualTo("client_credentials");

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
     * Concrete test implementation.
     */
    private static class TestOAuth2ClientCredentialsInjector extends OAuth2ClientCredentialsInjector {

        private final Duration staleWindowOverride;

        public TestOAuth2ClientCredentialsInjector(String tokenUrl, BasicAuthCredentials clientCredentials,
                String scope, RestClient restClient, ExpirableHeaderMapper expirableHeaderMapper,
                @Nullable Duration staleWindowOverride) {
            super(tokenUrl, clientCredentials, scope, restClient, expirableHeaderMapper);
            this.staleWindowOverride = staleWindowOverride;
        }

        @Override
        protected void sendAlert(Throwable t) {
            // do nothing in tests
        }

        @Override
        public Duration getStaleWindow() {
            return (staleWindowOverride != null) ? staleWindowOverride : super.getStaleWindow();
        }

        // deterministic tests: no preemptive async refresh
        @Override
        public int getAsyncRefreshTtlPercent() {
            return 0;
        }

    }

}
