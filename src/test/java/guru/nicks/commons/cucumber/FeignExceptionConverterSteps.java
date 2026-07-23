package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.exception.BusinessException;
import guru.nicks.commons.feign.mapper.FeignExceptionConverter;

import feign.FeignException;
import feign.Request;
import feign.Response;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Step definitions for testing {@link FeignExceptionConverter}.
 */
@RequiredArgsConstructor
public class FeignExceptionConverterSteps {

    // DI
    private final TextWorld textWorld;

    private FeignException feignException;
    private BiFunction<Integer, Throwable, BusinessException> httpStatusCodeMapper;
    private ParameterCapturingMapper parameterCapturingMapper;

    private FeignExceptionConverter converter;
    private BusinessException convertedException;
    private Integer capturedStatusCode;

    @Given("a FeignException with HTTP status {int}")
    public void givenFeignExceptionWithStatus(int status) {
        this.feignException = createFeignException(status);
    }

    @Given("a FeignException with HTTP status -1 indicating IOException")
    public void givenFeignExceptionWithIOExceptionStatus() {
        this.feignException = createFeignException(-1);
    }

    @Given("an HTTP status code mapper that creates TestBusinessException")
    public void givenHttpStatusCodeMapper() {
        httpStatusCodeMapper = (statusCode, cause) -> {
            capturedStatusCode = statusCode;
            return new TestBusinessException(cause);
        };
    }

    @Given("an HTTP status code mapper that captures parameters")
    public void givenParameterCapturingMapper() {
        parameterCapturingMapper = new ParameterCapturingMapper();
        httpStatusCodeMapper = null;
    }

    @When("the FeignExceptionConverter converts the exception")
    public void whenFeignExceptionConverterConverts() {
        if (httpStatusCodeMapper != null) {
            converter = new FeignExceptionConverter(httpStatusCodeMapper);

            Throwable throwable = catchThrowable(() ->
                    convertedException = converter.convert(feignException));
            textWorld.setLastException(throwable);
        } else if (parameterCapturingMapper != null) {
            converter = new FeignExceptionConverter(parameterCapturingMapper);

            Throwable throwable = catchThrowable(() ->
                    convertedException = converter.convert(feignException));
            textWorld.setLastException(throwable);
        }
    }

    @Then("the converter should call the mapper with status code {int}")
    public void thenMapperCalledWithStatusCode(int expectedStatusCode) {
        assertThat(capturedStatusCode)
                .as("capturedStatusCode")
                .isEqualTo(expectedStatusCode);
    }

    @Then("the result should be a TestBusinessException")
    public void thenResultIsTestBusinessException() {
        assertThat(convertedException)
                .as("convertedException")
                .isNotNull()
                .isInstanceOf(TestBusinessException.class);
    }

    @Then("the result cause should be the original FeignException")
    public void thenResultCauseIsOriginalFeignException() {
        assertThat(convertedException.getCause())
                .as("convertedException.cause")
                .isSameAs(feignException);
    }

    @Then("the mapper should receive status code {int}")
    public void thenMapperReceivedStatusCode(int expectedStatusCode) {
        assertThat(parameterCapturingMapper.getLastStatusCode())
                .as("lastStatusCode")
                .isEqualTo(expectedStatusCode);
    }

    @Then("the mapper should receive the original FeignException as cause")
    public void thenMapperReceivedOriginalFeignException() {
        assertThat(parameterCapturingMapper.getLastCause())
                .as("lastCause")
                .isSameAs(feignException);
    }

    /**
     * Creates a FeignException with the specified status code.
     *
     * @param status HTTP status code
     * @return FeignException instance
     */
    private FeignException createFeignException(int status) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/test",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null
        );

        Response.Builder responseBuilder = Response.builder()
                .status(status)
                .reason(StringUtils.EMPTY)
                .request(request)
                .headers(Collections.emptyMap());

        if (status != -1) {
            responseBuilder.body(StringUtils.EMPTY.getBytes(StandardCharsets.UTF_8));
        }

        Response response = responseBuilder.build();
        // use errorStatus which is a public static method
        return FeignException.errorStatus(String.valueOf(status), response);
    }

    /**
     * Test implementation of {@link BusinessException}.
     */
    public static class TestBusinessException extends BusinessException {

        public TestBusinessException(Throwable cause) {
            super(cause);
        }

    }

    /**
     * Mapper that captures parameters for verification.
     */
    @Getter
    private static class ParameterCapturingMapper implements BiFunction<Integer, Throwable, BusinessException> {

        private Integer lastStatusCode;
        private Throwable lastCause;

        @Override
        public BusinessException apply(Integer statusCode, Throwable cause) {
            lastStatusCode = statusCode;
            lastCause = cause;
            return new TestBusinessException(cause);
        }

    }

}
