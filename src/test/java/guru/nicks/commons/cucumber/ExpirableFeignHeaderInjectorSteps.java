package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.feign.domain.ExpirableHeader;
import guru.nicks.commons.feign.injector.ExpirableFeignHeaderInjector;
import guru.nicks.commons.feign.injector.FeignHeaderRefreshException;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for testing {@link ExpirableFeignHeaderInjector} failure handling: negative caching, last-known-good
 * stale serving, and fail-fast.
 */
@RequiredArgsConstructor
public class ExpirableFeignHeaderInjectorSteps {

    private static final long SETTLE_POLL_INTERVAL_MS = 100;
    private static final long SETTLE_MARGIN_MS = 800;
    private static final long SETTLE_TIMEOUT_MS = 30000;

    // DI
    private final TextWorld textWorld;
    private final List<String> concurrentHeaderValues = new ArrayList<>();
    // test data
    private TestExpirableFeignHeaderInjector injector;

    /**
     * Sleeps without throwing (interrupted status is restored).
     *
     * @param millis sleep duration in milliseconds
     */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Given("an expirable header injector stub with header TTL {long} ms, failure cache TTL {long} ms, "
            + "and stale window {long} ms")
    public void givenInjectorStub(long headerTtlMs, long failureCacheTtlMs, long staleWindowMs) {
        injector = new TestExpirableFeignHeaderInjector(
                Duration.ofMillis(headerTtlMs), Duration.ofMillis(failureCacheTtlMs), Duration.ofMillis(staleWindowMs));
    }

    @Given("the stub failure policy is SEND_EMPTY")
    public void givenSendEmptyFailurePolicy() {
        injector.useSendEmptyFailurePolicy();
    }

    @Given("the stub provider goes down")
    public void givenProviderGoesDown() {
        injector.providerMode = TestExpirableFeignHeaderInjector.ProviderMode.DOWN;
    }

    @Given("the stub provider comes back up")
    public void givenProviderComesBackUp() {
        injector.providerMode = TestExpirableFeignHeaderInjector.ProviderMode.UP;
    }

    @Given("an expirable header value was already obtained")
    public void givenHeaderValueWasAlreadyObtained() {
        obtainHeaderValue();
    }

    @Given("the expirable fresh header attempt counter is reset")
    public void givenFreshHeaderAttemptCounterIsReset() {
        injector.obtainCount.set(0);
    }

    @When("the expirable header value is obtained")
    public void whenHeaderValueIsObtained() {
        obtainHeaderValue();
    }

    @When("{int} concurrent expirable header values are obtained")
    public void whenConcurrentHeaderValuesAreObtained(int count) throws InterruptedException {
        concurrentHeaderValues.clear();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            // virtual threads run truly in parallel, exercising the single-flight refresh logic
            threads.add(Thread.ofVirtual().start(() -> {
                String value;
                try {
                    value = injector.getHeaderValue();
                }
                // recorded as a wrong value to fail the assertion below with a meaningful message
                catch (RuntimeException e) {
                    value = "unexpected " + e.getClass().getSimpleName();
                }

                synchronized (concurrentHeaderValues) {
                    concurrentHeaderValues.add(value);
                }
            }));
        }

        for (Thread thread : threads) {
            thread.join();
        }
    }

    @Then("the expirable header value should be {string}")
    public void thenHeaderValueShouldBe(String expectedValue) {
        assertThat(textWorld.getText())
                .as("header value")
                .isEqualTo(expectedValue);
    }

    @Then("the expirable header value should be empty")
    public void thenHeaderValueShouldBeEmpty() {
        assertThat(textWorld.getText())
                .as("header value")
                .isEmpty();
    }

    @Then("the expirable fresh header attempt count should be {int}")
    public void thenFreshHeaderAttemptCountShouldBe(int expectedCount) {
        assertThat(injector.obtainCount.get())
                .as("fresh header attempt count")
                .isEqualTo(expectedCount);
    }

    @Then("the expirable background refresh settles with {int} fresh header attempts")
    public void thenBackgroundRefreshSettlesWith(int expectedCount) {
        long deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS;

        while ((injector.obtainCount.get() < expectedCount) && (System.currentTimeMillis() < deadline)) {
            sleepQuietly(SETTLE_POLL_INTERVAL_MS);
        }

        // margin to let an erroneously scheduled extra refresh increment the counter
        sleepQuietly(SETTLE_MARGIN_MS);

        assertThat(injector.obtainCount.get())
                .as("fresh header attempt count after background refresh settled")
                .isEqualTo(expectedCount);
    }

    @Then("all {int} concurrent expirable header values should be {string}")
    public void thenAllConcurrentHeaderValuesShouldBe(int count, String expectedValue) {
        synchronized (concurrentHeaderValues) {
            assertThat(concurrentHeaderValues)
                    .as("concurrent header values")
                    .hasSize(count)
                    .containsOnly(expectedValue);
        }
    }

    @Then("FeignHeaderRefreshException should be thrown")
    public void thenFeignHeaderRefreshExceptionShouldBeThrown() {
        assertThat(textWorld.getLastException())
                .as("lastException")
                .isInstanceOf(FeignHeaderRefreshException.class);
    }

    @Then("the exception cause should not be null")
    public void thenExceptionCauseShouldNotBeNull() {
        assertThat(textWorld.getLastException())
                .as("lastException")
                .isNotNull();

        assertThat(textWorld.getLastException().getCause())
                .as("lastException cause")
                .isNotNull();
    }

    /**
     * Obtains the header value via {@link ExpirableFeignHeaderInjector#getHeaderValue()} exactly once, remembering
     * either the value or the thrown exception.
     */
    private void obtainHeaderValue() {
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

    /**
     * Test stub with a controllable provider and short overridden TTLs.
     */
    private static final class TestExpirableFeignHeaderInjector extends ExpirableFeignHeaderInjector {

        private final Duration headerTtl;
        private final Duration failureCacheTtl;
        private final Duration staleWindow;
        private final AtomicInteger obtainCount = new AtomicInteger();
        private volatile ProviderMode providerMode = ProviderMode.UP;
        private volatile FailurePolicy failurePolicy = FailurePolicy.FAIL_FAST;

        private TestExpirableFeignHeaderInjector(Duration headerTtl, Duration failureCacheTtl, Duration staleWindow) {
            this.headerTtl = headerTtl;
            this.failureCacheTtl = failureCacheTtl;
            this.staleWindow = staleWindow;
        }

        /**
         * Switches to {@code SEND_EMPTY} failure policy (the enum itself is not accessible from the enclosing class).
         */
        private void useSendEmptyFailurePolicy() {
            failurePolicy = FailurePolicy.SEND_EMPTY;
        }

        @Override
        public String getHeaderName() {
            return "X-Test-Header";
        }

        @Override
        protected ExpirableHeader obtainFreshHeader() {
            int attempt = obtainCount.incrementAndGet();

            if (providerMode == ProviderMode.DOWN) {
                throw new IllegalStateException("simulated provider failure #" + attempt);
            }

            Instant now = Instant.now();

            return ExpirableHeader.builder()
                    .valuePrefix("Bearer ")
                    .value("token-" + attempt)
                    .issuedDate(now)
                    .expirationDate(now.plus(headerTtl))
                    .build();
        }

        @Override
        protected void sendAlert(Throwable t) {
            // do nothing in tests
        }

        @Override
        public Duration getFailureCacheTtl() {
            return failureCacheTtl;
        }

        @Override
        public Duration getStaleWindow() {
            return staleWindow;
        }

        @Override
        public FailurePolicy getFailurePolicy() {
            return failurePolicy;
        }

        // deterministic tests: no preemptive async refresh
        @Override
        public int getAsyncRefreshTtlPercent() {
            return 0;
        }

        private enum ProviderMode {
            UP,
            DOWN
        }

    }

}
