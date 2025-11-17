package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.feign.FeignRetryer;

import feign.Request;
import feign.RetryableException;
import feign.Retryer;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


/**
 * Step definitions for testing {@link FeignRetryer}.
 */
@RequiredArgsConstructor
public class FeignRetryerSteps {

    // DI
    private final TextWorld textWorld;

    private FeignRetryer feignRetryer;
    private FeignRetryer clonedRetryer;

    private long period;
    private long maxPeriod;
    private int maxAttempts;

    /**
     * Creates a {@link FeignRetryer} instance with specified parameters.
     *
     * @param period      Initial interval between attempts in milliseconds.
     * @param maxPeriod   Maximum interval between attempts in milliseconds.
     * @param maxAttempts Maximum number of attempts.
     */
    @Given("a FeignRetryer is created with period {long}, maxPeriod {long}, and maxAttempts {int}")
    public void aFeignRetryerIsCreatedWithPeriodMaxPeriodAndMaxAttempts(long period, long maxPeriod, int maxAttempts) {
        this.period = period;
        this.maxPeriod = maxPeriod;
        this.maxAttempts = maxAttempts;
        feignRetryer = new FeignRetryer(period, maxPeriod, maxAttempts);
    }

    /**
     * Calls the {@link FeignRetryer#continueOrPropagate(RetryableException)} method a specified number of times.
     * Captures the last exception thrown.
     *
     * @param calls The number of times to call the method.
     */
    @When("continueOrPropagate is called {int} times with a RetryableException")
    public void continueOrPropagateIsCalledTimesWithARetryableException(int calls) {
        Throwable caughtException = null;

        for (int i = 0; i < calls; i++) {
            // create a dummy RetryableException for each call
            var retryableException = new RetryableException(
                    500,
                    "Simulated server error",
                    Request.HttpMethod.GET,
                    null, // cause
                    0L,   // retryAfter
                    mock(Request.class)
            );

            try {
                feignRetryer.continueOrPropagate(retryableException);
            } catch (Exception e) {
                caughtException = e;
                break;
            }
        }

        textWorld.setLastException(caughtException);
    }

    /**
     * Clones the existing {@link FeignRetryer} instance.
     */
    @When("the FeignRetryer is cloned")
    public void theFeignRetryerIsCloned() {
        assertThat(feignRetryer)
                .as("original FeignRetryer before cloning")
                .isNotNull();
        clonedRetryer = (FeignRetryer) feignRetryer.clone();
    }

    /**
     * Verifies that the cloned {@link FeignRetryer} is a different instance from the original.
     */
    @Then("the cloned FeignRetryer should be a new instance")
    public void theClonedFeignRetryerShouldBeANewInstance() {
        assertThat(clonedRetryer)
                .as("cloned FeignRetryer")
                .isNotNull()
                .isNotSameAs(feignRetryer);
    }

    /**
     * Verifies that the cloned {@link FeignRetryer} retains the configuration of the original.
     * Note: This relies on the clone implementation correctly creating a new instance
     * with the original constructor parameters, as the fields themselves are private
     * and not directly accessible for comparison. We test this indirectly by cloning
     * the clone and ensuring it's also a FeignRetryer.
     */
    @Then("the cloned FeignRetryer should have the same period, maxPeriod, and maxAttempts")
    public void theClonedFeignRetryerShouldHaveTheSamePeriodMaxPeriodAndMaxAttempts() throws Exception {
        Field field = clonedRetryer.getClass().getDeclaredField("period");
        field.setAccessible(true);
        Long clonedPeriod = (Long) field.get(clonedRetryer);
        assertThat(clonedPeriod)
                .as("cloned period")
                .isEqualTo(period);

        field = clonedRetryer.getClass().getDeclaredField("maxPeriod");
        field.setAccessible(true);
        Long clonedMaxPeriod = (Long) field.get(clonedRetryer);
        assertThat(clonedMaxPeriod)
                .as("cloned maxPeriod")
                .isEqualTo(maxPeriod);

        field = clonedRetryer.getClass().getDeclaredField("maxAttempts");
        field.setAccessible(true);
        Integer clonedMaxAttempts = (Integer) field.get(clonedRetryer);
        assertThat(clonedMaxAttempts)
                .as("cloned maxAttempts")
                .isEqualTo(maxAttempts);

        // further verification: Clone the clone and check its type
        Retryer doubleClonedRetryer = clonedRetryer.clone();
        assertThat(doubleClonedRetryer)
                .as("double-cloned FeignRetryer")
                .isInstanceOf(FeignRetryer.class)
                .isNotSameAs(clonedRetryer);
    }

}
