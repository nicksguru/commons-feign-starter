package guru.nicks.commons.cucumber;

import guru.nicks.commons.ApplicationContextHolder;
import guru.nicks.commons.feign.FeignLogger;
import guru.nicks.commons.utils.ExceptionUtils;
import guru.nicks.commons.utils.json.JsonUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import feign.Logger;
import feign.Request;
import feign.Response;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Step definitions for testing {@link FeignLogger}.
 */
@RequiredArgsConstructor
public class FeignLoggerSteps {

    // DI
    private final ListAppender<ILoggingEvent> logAppender;

    @Mock
    private Function<String, URI> urlResolver;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private Environment environment;
    private AutoCloseable closeableMocks;
    private MockedStatic<ApplicationContextHolder> applicationContextHolderMock;
    private MockedStatic<JsonUtils> jsonUtilsMock;
    private MockedStatic<ExceptionUtils> exceptionUtilsMock;

    private FeignLogger feignLogger;
    private Request request;
    private Response response;

    @Before
    public void beforeEachScenario() {
        closeableMocks = MockitoAnnotations.openMocks(this);

        // FeignLogger retrieves 'spring.application.name' from this context holder
        applicationContextHolderMock = mockStatic(ApplicationContextHolder.class);
        applicationContextHolderMock.when(ApplicationContextHolder::getApplicationContext)
                .thenReturn(applicationContext);
        when(applicationContext.getEnvironment())
                .thenReturn(environment);
        when(environment.getProperty(ApplicationContextHolder.SPRING_APPLICATION_NAME_PROPERTY))
                .thenReturn("test-app");

        // for masking sensitive request/response fields
        jsonUtilsMock = mockStatic(JsonUtils.class);
        jsonUtilsMock.when(() -> JsonUtils.maskSensitiveJsonFields(any(byte[].class)))
                .thenAnswer(invocation -> {
                    byte[] bytes = invocation.getArgument(0);
                    return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
                });
        jsonUtilsMock.when(() -> JsonUtils.maskSensitiveJsonFields(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // for logging exception stack traces
        exceptionUtilsMock = mockStatic(ExceptionUtils.class);
        exceptionUtilsMock.when(() -> ExceptionUtils.formatWithCompactStackTrace(any(Throwable.class)))
                .thenReturn("Mocked stack trace");

        // clear log messages from shared bean
        logAppender.list.clear();
    }

    @After
    public void afterEachScenario() throws Exception {
        if (applicationContextHolderMock != null) {
            applicationContextHolderMock.close();
        }

        if (jsonUtilsMock != null) {
            jsonUtilsMock.close();
        }

        if (exceptionUtilsMock != null) {
            exceptionUtilsMock.close();
        }

        closeableMocks.close();
    }

    @Given("a Feign Logger is created")
    public void aFeignLoggerIsCreated() {
        when(urlResolver.apply(anyString()))
                .thenAnswer(invocation -> URI.create(invocation.getArgument(0)));

        feignLogger = new FeignLogger(urlResolver);
    }

    @When("a request with method {string} to URL {string} with body {string} is logged")
    public void aRequestWithMethodToUrlWithBodyIsLogged(String method, String url, String body) {
        byte[] requestBody = null;
        if (!"null".equals(body)) {
            requestBody = body.isEmpty() ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        }

        request = Request.create(
                Request.HttpMethod.valueOf(method),
                url,
                Map.of(),
                requestBody,
                StandardCharsets.UTF_8,
                null
        );

        // the 1st argument (config key) must contain a '(', otherwise 'feign.logger.methodTag(tag)' fails
        feignLogger.logRequest("testMethod()", Logger.Level.FULL, request);
    }

    @When("a response with status {string} and reason {string} from request {string} to {string} with body {string} is logged")
    public void aResponseWithStatusAndReasonFromRequestToWithBodyIsLogged(String status, String reason, String method,
            String url, String body) {
        request = Request.create(
                Request.HttpMethod.valueOf(method),
                url,
                Map.of(),
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8,
                null
        );

        Response.Builder responseBuilder = Response.builder()
                .status(Integer.parseInt(status))
                .request(request);

        if (!"null".equals(reason)) {
            responseBuilder.reason(reason);
        }

        if (!"null".equals(body)) {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            InputStream inputStream = new ByteArrayInputStream(bodyBytes);
            responseBuilder.body(inputStream, bodyBytes.length);
        }

        response = responseBuilder.build();
        // the 1st argument (config key) must contain a '(', otherwise 'feign.logger.methodTag(tag)' fails
        feignLogger.logAndRebufferResponse("testMethod()", Logger.Level.FULL, response, 1L);
    }

    @When("an IOException occurs")
    public void anIOExceptionOccurs() {
        var ioException = new IOException("Test IO Exception");
        // the 1st argument (config key) must contain a '(', otherwise 'feign.logger.methodTag(tag)' fails
        feignLogger.logIOException("testMethod()", Logger.Level.FULL, ioException, 1L);
    }

    @Then("the request should be logged at info level")
    public void theRequestShouldBeLoggedAtInfoLevel() {
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .as("log events")
                .isNotEmpty();

        ILoggingEvent lastEvent = logEvents.getLast();
        assertThat(lastEvent.getLevel())
                .as("log level")
                .isEqualTo(Level.INFO);
    }

    @Then("the response should be logged at {word} level")
    public void theResponseShouldBeLoggedAtLevel(String level) {
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .as("log events")
                .isNotEmpty();

        ILoggingEvent lastEvent = logEvents.getLast();
        Level expectedLevel = Level.valueOf(level.toUpperCase());
        assertThat(lastEvent.getLevel())
                .as("log level")
                .isEqualTo(expectedLevel);
    }

    @Then("the exception should be logged at error level")
    public void theExceptionShouldBeLoggedAtErrorLevel() {
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .as("log events")
                .isNotEmpty();

        ILoggingEvent lastEvent = logEvents.getLast();
        assertThat(lastEvent.getLevel())
                .as("log level")
                .isEqualTo(Level.ERROR);
    }

    @Then("the log should contain the method and URL")
    public void theLogShouldContainTheMethodAndUrl() {
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .as("log events")
                .isNotEmpty();

        ILoggingEvent lastEvent = logEvents.getLast();
        String formattedMessage = lastEvent.getFormattedMessage();

        assertThat(formattedMessage)
                .as("log message")
                .contains(request.httpMethod().name())
                .contains(request.url());
    }

    @Then("the log should contain the status code and reason")
    public void theLogShouldContainTheStatusCodeAndReason() {
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .as("log events")
                .isNotEmpty();

        ILoggingEvent lastEvent = logEvents.getLast();
        String formattedMessage = lastEvent.getFormattedMessage();

        assertThat(formattedMessage)
                .as("log message")
                .contains(String.valueOf(response.status()));

        if (response.reason() != null) {
            assertThat(formattedMessage)
                    .as("log message with reason")
                    .contains(response.reason());
        }
    }

    @Then("the log should contain the stack trace")
    public void theLogShouldContainTheStackTrace() {
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .as("log events")
                .isNotEmpty();

        ILoggingEvent lastEvent = logEvents.getLast();
        String formattedMessage = lastEvent.getFormattedMessage();

        assertThat(formattedMessage)
                .as("log message")
                .contains("Mocked stack trace");
    }

    @Then("the log should contain the request body: {booleanValue}")
    public void theLogShouldContainTheRequestBody(boolean shouldContain) {
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .as("log events")
                .isNotEmpty();

        ILoggingEvent lastEvent = logEvents.getLast();
        String formattedMessage = lastEvent.getFormattedMessage();

        if (shouldContain) {
            assertThat(formattedMessage)
                    .as("log message")
                    .contains(": " + new String(request.body(), StandardCharsets.UTF_8));
        } else {
            assertThat(formattedMessage)
                    .as("log message")
                    // can't call 'new String(request.body(), ...)' because request.body()' may be null,
                    // so this expression check the request body prefix
                    .doesNotContain(": ");
        }
    }

    @Then("the log should {word} the response body")
    public void theLogShouldContainTheResponseBody(String containment) {
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
                .as("log events")
                .isNotEmpty();

        ILoggingEvent lastEvent = logEvents.getLast();
        String formattedMessage = lastEvent.getFormattedMessage();

        if ("contain".equals(containment)) {
            assertThat(formattedMessage)
                    .as("log message")
                    .contains(": " + response.body().toString());
        } else if ("not contain".equals(containment)) {
            assertThat(formattedMessage)
                    .as("log message")
                    // can't call 'response.body().toString()' because 'response.body()' may be null
                    // so this expression check the response body prefix
                    .doesNotContain(": ");
        }
    }

}
