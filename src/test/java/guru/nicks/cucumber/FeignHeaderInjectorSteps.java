package guru.nicks.cucumber;

import guru.nicks.feign.injector.FeignHeaderInjector;

import feign.RequestTemplate;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for testing {@link FeignHeaderInjector}.
 */
@RequiredArgsConstructor
public class FeignHeaderInjectorSteps {

    @Spy
    private RequestTemplate requestTemplate;
    private AutoCloseable closeableMocks;

    private TestFeignHeaderInjector headerInjector = TestFeignHeaderInjector.builder().build();

    @Before
    public void beforeEachScenario() {
        closeableMocks = MockitoAnnotations.openMocks(this);
    }

    @After
    public void afterEachScenario() throws Exception {
        closeableMocks.close();
    }

    @Given("a Feign header injector with header name {string}")
    public void aFeignHeaderInjectorWithHeaderName(String headerName) {
        headerInjector = headerInjector.toBuilder()
                .headerName(headerName)
                .build();
    }

    @Given("header value {string}")
    public void headerValue(String value) {
        headerInjector = headerInjector.toBuilder()
                .headerValue(value)
                .build();
    }

    @When("the injector is applied to a request template")
    public void theInjectorIsAppliedToARequestTemplate() {
        headerInjector.apply(requestTemplate);
    }

    @Then("the request template should have header {string} with value {string}")
    public void theRequestTemplateShouldHaveHeaderWithValue(String headerName, String headerValue) {
        assertThat(requestTemplate.headers())
                .as("request headers")
                .containsEntry(headerName, List.of(headerValue));
    }

    @Then("the request template should have header {string}")
    public void theRequestTemplateShouldHaveHeader(String headerName) {
        assertThat(requestTemplate.headers())
                .as("request headers")
                .containsKey(headerName);
    }

    @Then("the request template should not have header {string}")
    public void theRequestTemplateShouldNotHaveHeader(String headerName) {
        assertThat(requestTemplate.headers())
                .as("request headers")
                .doesNotContainKey(headerName);
    }

    /**
     * Test implementation of {@link FeignHeaderInjector} for testing purposes.
     */
    @Builder(toBuilder = true)
    private static class TestFeignHeaderInjector implements FeignHeaderInjector {

        @Getter(onMethod_ = @Override)
        private final String headerName;

        @Getter(onMethod_ = @Override)
        private final String headerValue;

    }

}
