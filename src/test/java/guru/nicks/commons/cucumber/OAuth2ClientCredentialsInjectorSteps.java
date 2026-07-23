package guru.nicks.commons.cucumber;

import guru.nicks.commons.auth.domain.BasicAuthCredentials;
import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.feign.domain.ExpirableHeader;
import guru.nicks.commons.feign.injector.OAuth2ClientCredentialsInjector;
import guru.nicks.commons.feign.mapper.ExpirableHeaderMapper;
import guru.nicks.commons.rest.v1.dto.OAuth2AccessTokenDto;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Step definitions for testing {@link OAuth2ClientCredentialsInjector}. All step patterns are prefixed with 'client
 * credentials' to avoid duplicates with authorization code flow steps.
 */
@RequiredArgsConstructor
public class OAuth2ClientCredentialsInjectorSteps {

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
    private TestOAuth2ClientCredentialsInjector injector;

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

    @Given("a client credentials RestOperations mock that returns a successful token response with access token {string} and expires in {long} seconds")
    public void givenSuccessfulTokenResponse(String accessToken, long expiresIn) {
        restClient = mock(RestOperations.class);
        expectedAccessToken = accessToken;
        expectedExpiresIn = expiresIn;
        tokenResponseStatus = HttpStatus.OK;
        setupSuccessfulTokenResponse();
    }

    @Given("a client credentials RestOperations mock that returns a successful token response")
    public void givenSuccessfulTokenResponseSimple() {
        restClient = mock(RestOperations.class);
        expectedAccessToken = "default-token";
        expectedExpiresIn = 3600L;
        tokenResponseStatus = HttpStatus.OK;
        setupSuccessfulTokenResponse();
    }

    @Given("a client credentials RestOperations mock that returns a {int} response")
    public void givenErrorResponse(int statusCode) {
        restClient = mock(RestOperations.class);
        tokenResponseStatus = HttpStatus.valueOf(statusCode);
        setupErrorResponse();
    }

    @Given("a client credentials RestOperations mock that throws RestClientException")
    public void givenRestClientException() {
        restClient = mock(RestOperations.class);
        setupRestClientException();
    }

    @Given("a client credentials RestOperations mock null")
    public void givenNullRestOperations() {
        restClient = null;
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
                    restClient, expirableHeaderMapper);
        }));
    }

    @Then("the client credentials header name should be {string}")
    public void thenHeaderNameShouldBe(String expectedHeaderName) {
        assertThat(injector.getHeaderName())
                .as("header name")
                .isEqualTo(expectedHeaderName);
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
     * Obtains a fresh header from the injector.
     */
    private ExpirableHeader obtainFreshHeaderFromInjector() {
        return injector.obtainFreshHeader();
    }

    /**
     * Sets up restClient  mock to return a successful token response.
     */
    private void setupSuccessfulTokenResponse() {
        when(restClient.exchange(
                eq(tokenUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(OAuth2AccessTokenDto.class)
        )).thenAnswer(invocation -> {
            // verify the request contains grant_type=client_credentials
            HttpEntity<?> requestEntity = invocation.getArgument(2);
            MultiValueMap<String, String> formData = (MultiValueMap<String, String>) requestEntity.getBody();

            assertThat(formData)
                    .as("form data")
                    .isNotNull();

            assertThat(formData.getFirst("grant_type"))
                    .as("grant_type")
                    .isEqualTo("client_credentials");

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
     * Sets up restClient  mock to return an error response.
     */
    private void setupErrorResponse() {
        when(restClient.exchange(
                eq(tokenUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(OAuth2AccessTokenDto.class)
        )).thenReturn(new ResponseEntity<>(tokenResponseStatus));
    }

    /**
     * Sets up restClient  mock to throw RestClientException.
     */
    private void setupRestClientException() {
        when(restClient.exchange(
                any(String.class),
                any(HttpMethod.class),
                any(HttpEntity.class),
                any(Class.class)
        )).thenThrow(new org.springframework.web.client.RestClientException("Simulated REST client error"));
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

        public TestOAuth2ClientCredentialsInjector(String tokenUrl, BasicAuthCredentials clientCredentials,
                String scope, RestOperations restClient, ExpirableHeaderMapper expirableHeaderMapper) {
            super(tokenUrl, clientCredentials, scope, restClient, expirableHeaderMapper);
        }

        @Override
        protected void sendAlert(Throwable t) {
            // do nothing in tests
        }

    }

}
