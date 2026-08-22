package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.exception.BusinessException;
import guru.nicks.commons.exception.http.ServiceTimeoutException;
import guru.nicks.commons.feign.decoder.FailedRemoteCallParserVisitor;
import guru.nicks.commons.feign.decoder.FeignExceptionParser;
import guru.nicks.commons.feign.decoder.HttpStatusRetrieverVisitor;
import guru.nicks.commons.feign.domain.FailedRemoteCall;
import guru.nicks.commons.rest.dto.BusinessExceptionDto;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Step definitions for testing {@link FeignExceptionParser} together with its collaborators: the stateful
 * {@link FailedRemoteCallParserVisitor} and the stateless {@link HttpStatusRetrieverVisitor}.
 */
@RequiredArgsConstructor
public class FeignExceptionParserSteps {

    /**
     * Error code which the remote exception mapper knows how to map (like a production error code registry would).
     */
    private static final String KNOWN_ERROR_CODE = "USER_NOT_FOUND";

    // DI
    private final TextWorld textWorld;

    /**
     * Maps DTOs with a known error code to a remote exception, mimicking a production error code registry.
     */
    private final Function<BusinessExceptionDto, Optional<BusinessException>> remoteExceptionMapper = dto ->
            KNOWN_ERROR_CODE.equals(dto.errorCode())
                    ? Optional.of(new RemoteBusinessException(dto))
                    : Optional.empty();

    /**
     * Maps HTTP status to a local exception, mimicking a production RootHttpStatus-based mapper.
     */
    private final BiFunction<HttpStatus, Throwable, BusinessException> localExceptionMapper =
            (httpStatus, cause) -> new LocalBusinessException(httpStatus, cause);

    private FeignExceptionParser parser;
    private HttpStatusRetrieverVisitor statusRetrieverVisitor;

    private FeignException feignException;
    private Throwable exceptionToParse;
    private FailedRemoteCall parseResult;
    private Optional<HttpStatus> retrievedStatus = Optional.empty();

    @Given("a FeignExceptionParser with test mappers")
    public void givenFeignExceptionParserWithTestMappers() {
        // BusinessException messages hold HTTP status codes, which lets feature files drive the expected statuses
        statusRetrieverVisitor = new HttpStatusRetrieverVisitor(exception ->
                Optional.ofNullable(exception.getMessage())
                        .map(Integer::parseInt)
                        .map(HttpStatus::resolve)
                        .orElse(null));

        parser = new FeignExceptionParser(new FailedRemoteCallParserVisitor(
                remoteExceptionMapper,
                localExceptionMapper,
                statusRetrieverVisitor,
                new ObjectMapper()));
    }

    @Given("a FeignException with HTTP status {int} and JSON body:")
    public void givenFeignExceptionWithStatusAndJsonBody(int status, String jsonBody) {
        feignException = createFeignException(status, jsonBody);
        exceptionToParse = feignException;
    }

    @Given("a FeignException with HTTP status {int} and body {string}")
    public void givenFeignExceptionWithStatusAndBody(int status, String body) {
        feignException = createFeignException(status, body);
        exceptionToParse = feignException;
    }

    @Given("the FeignException wrapped in a RuntimeException")
    public void givenFeignExceptionWrappedInRuntimeException() {
        exceptionToParse = new RuntimeException("wrapped", feignException);
    }

    @Given("a plain IllegalStateException with message {string}")
    public void givenPlainIllegalStateException(String message) {
        exceptionToParse = new IllegalStateException(message);
    }

    @Given("a ConnectException with message {string}")
    public void givenConnectException(String message) {
        exceptionToParse = new ConnectException(message);
    }

    @Given("a TestBusinessException with HTTP status code {int}")
    public void givenTestBusinessExceptionWithStatusCode(int statusCode) {
        // the status retriever's business exception mapper parses the message as an HTTP status code
        exceptionToParse = new TestBusinessException(String.valueOf(statusCode));
    }

    @Given("a ResponseStatusException with HTTP status {int}")
    public void givenResponseStatusException(int status) {
        exceptionToParse = new ResponseStatusException(HttpStatus.resolve(status));
    }

    @When("the FeignExceptionParser parses the exception")
    public void whenParserParsesException() {
        Throwable throwable = catchThrowable(() -> parseResult = parser.parse(exceptionToParse));
        textWorld.setLastException(throwable);
    }

    @When("the FeignExceptionParser parses null")
    public void whenParserParsesNull() {
        Throwable throwable = catchThrowable(() -> parser.parse(null));
        textWorld.setLastException(throwable);
    }

    @When("the HttpStatusRetrieverVisitor is applied to the exception")
    public void whenStatusRetrieverApplied() {
        retrievedStatus = statusRetrieverVisitor.apply(exceptionToParse);
    }

    @Then("the parse result should not be null")
    public void thenParseResultNotNull() {
        assertThat(parseResult)
                .as("parseResult")
                .isNotNull();
    }

    @Then("the original exception should be the FeignException")
    public void thenOriginalExceptionIsFeignException() {
        assertThat(parseResult.getOriginalException())
                .as("originalException")
                .isSameAs(feignException);
    }

    @Then("the original exception should be the parsed exception")
    public void thenOriginalExceptionIsParsedException() {
        assertThat(parseResult.getOriginalException())
                .as("originalException")
                .isSameAs(exceptionToParse);
    }

    @Then("the local exception should be mapped from HTTP status {int}")
    public void thenLocalExceptionMappedFromStatus(int expectedStatus) {
        assertThat(parseResult.getLocalException())
                .as("localException")
                .isInstanceOfSatisfying(LocalBusinessException.class, localException ->
                        assertThat(localException.getHttpStatus())
                                .as("local exception HTTP status")
                                .isEqualTo(HttpStatus.resolve(expectedStatus)));
    }

    @Then("the local exception should be the parsed exception itself")
    public void thenLocalExceptionIsParsedExceptionItself() {
        assertThat(parseResult.getLocalException())
                .as("localException")
                .isSameAs(exceptionToParse);
    }

    @Then("the local exception should be a ServiceTimeoutException")
    public void thenLocalExceptionIsServiceTimeoutException() {
        assertThat(parseResult.getLocalException())
                .as("localException")
                .isInstanceOf(ServiceTimeoutException.class);
    }

    @Then("the local exception should be null")
    public void thenLocalExceptionIsNull() {
        assertThat(parseResult.getLocalException())
                .as("localException")
                .isNull();
    }

    @Then("the remote exception should be a RemoteBusinessException")
    public void thenRemoteExceptionIsRemoteBusinessException() {
        assertThat(parseResult.getRemoteException())
                .as("remoteException")
                .isInstanceOf(RemoteBusinessException.class);
    }

    @Then("the remote exception should be null")
    public void thenRemoteExceptionIsNull() {
        assertThat(parseResult.getRemoteException())
                .as("remoteException")
                .isNull();
    }

    @Then("the remote exception should have error code {word}")
    public void thenRemoteExceptionErrorCode(String expectedErrorCode) {
        assertThat(remoteException().getDto().errorCode())
                .as("remote exception errorCode")
                .isEqualTo(expectedErrorCode);
    }

    @Then("the remote exception should have message {string}")
    public void thenRemoteExceptionMessage(String expectedMessage) {
        assertThat(parseResult.getRemoteException().getMessage())
                .as("remote exception message")
                .isEqualTo(expectedMessage);
    }

    @Then("the remote exception should have path {string}")
    public void thenRemoteExceptionPath(String expectedPath) {
        assertThat(remoteException().getDto().path())
                .as("remote exception path")
                .isEqualTo(expectedPath);
    }

    @Then("the remote exception should have field error {word}:{word}")
    public void thenRemoteExceptionFieldError(String expectedFieldName, String expectedErrorCode) {
        assertThat(remoteException().getDto().fieldErrors())
                .as("remote exception field errors")
                .anySatisfy(fieldError -> {
                    assertThat(fieldError.fieldName())
                            .as("field name")
                            .isEqualTo(expectedFieldName);

                    assertThat(fieldError.errorCode())
                            .as("field error code")
                            .isEqualTo(expectedErrorCode);
                });
    }

    @Then("the deep cause should be the remote exception")
    public void thenDeepCauseIsRemoteException() {
        assertThat(parseResult.getDeepCause())
                .as("deepCause")
                .isSameAs(parseResult.getRemoteException());
    }

    @Then("the deep cause should be the local exception")
    public void thenDeepCauseIsLocalException() {
        assertThat(parseResult.getDeepCause())
                .as("deepCause")
                .isSameAs(parseResult.getLocalException());
    }

    @Then("the deep cause should be the original exception")
    public void thenDeepCauseIsOriginalException() {
        assertThat(parseResult.getDeepCause())
                .as("deepCause")
                .isSameAs(parseResult.getOriginalException());
    }

    @Then("the retrieved HTTP status should be {word}")
    public void thenRetrievedHttpStatus(String expectedStatus) {
        assertThat(retrievedStatus)
                .as("retrievedStatus")
                .contains(HttpStatus.valueOf(expectedStatus));
    }

    @Then("no HTTP status should be retrieved")
    public void thenNoHttpStatusRetrieved() {
        assertThat(retrievedStatus)
                .as("retrievedStatus")
                .isEmpty();
    }

    /**
     * Convenience accessor narrowing the remote exception type for DTO assertions.
     *
     * @return remote exception created by the remote exception mapper
     */
    private RemoteBusinessException remoteException() {
        return (RemoteBusinessException) parseResult.getRemoteException();
    }

    /**
     * Creates a FeignException carrying the given response body, the same way as production code does when a remote
     * party responds with an error.
     *
     * @param status HTTP status code (-1 denotes IOException, as in FeignException.errorExecuting)
     * @param body   response body (empty string means empty body)
     * @return FeignException instance
     */
    private FeignException createFeignException(int status, String body) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/test",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null
        );

        Response response = Response.builder()
                .status(status)
                .reason(StringUtils.EMPTY)
                .request(request)
                .headers(Collections.emptyMap())
                .body(body.getBytes(StandardCharsets.UTF_8))
                .build();

        // use errorStatus which is a public static method
        return FeignException.errorStatus(String.valueOf(status), response);
    }

    /**
     * Remote exception reconstructed out of a parsed BusinessExceptionDto - keeps the whole DTO for assertions.
     */
    public static class RemoteBusinessException extends BusinessException {

        @Getter
        private final BusinessExceptionDto dto;

        public RemoteBusinessException(BusinessExceptionDto dto) {
            super(dto.message());
            this.dto = dto;
        }

    }

    /**
     * Local exception created out of an HTTP status, mimicking production RootHttpStatus-based exceptions.
     */
    public static class LocalBusinessException extends BusinessException {

        @Getter
        private final HttpStatus httpStatus;

        public LocalBusinessException(HttpStatus httpStatus, Throwable cause) {
            super(cause);
            this.httpStatus = httpStatus;
        }

    }

    /**
     * Test implementation of BusinessException whose message holds an HTTP status code.
     */
    public static class TestBusinessException extends BusinessException {

        public TestBusinessException(String message) {
            super(message);
        }

    }

}
