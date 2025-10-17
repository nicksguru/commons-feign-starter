package guru.nicks.feign;

import feign.RetryableException;
import feign.Retryer;
import lombok.extern.slf4j.Slf4j;

/**
 * As compared to parent class, adds logging.
 */
@Slf4j
public class FeignRetryer extends Retryer.Default {

    /**
     * Same variables as in parent class, where they're private.
     */
    private final long period;
    private final long maxPeriod;
    private final int maxAttempts;
    private int currentAttempt;

    /**
     * Calls parent constructor, at the same time copies the arguments to own variables (because they're private in
     * parent class, and there are no getters).
     *
     * @param period      initial interval between each attempt in milliseconds
     * @param maxPeriod   maximum interval between each attempt in milliseconds
     * @param maxAttempts maximum number of attempts
     */
    public FeignRetryer(long period, long maxPeriod, int maxAttempts) {
        super(period, maxPeriod, maxAttempts);
        this.period = period;
        this.maxPeriod = maxPeriod;
        this.maxAttempts = maxAttempts;
        currentAttempt = 1;
    }

    @Override
    public void continueOrPropagate(RetryableException e) {
        // only retries are processed here; on the very first call, currentAttempt is still 1
        if (currentAttempt < maxAttempts) {
            log.error("Sleeping {} ms. before attempt {}/{} failed because of: {}", nextMaxInterval(),
                    currentAttempt + 1, maxAttempts, e.getMessage());
        }

        currentAttempt++;
        super.continueOrPropagate(e);
    }

    /**
     * Without this, {@link Default#clone()} would be called, it creates yet another {@link feign.Retryer.Default}
     * ({@code feign.SynchronousMethodHandler#invoke(Object[])} does that on every request because the number of
     * attempts should start from 1 in every request).
     */
    @SuppressWarnings({"java:S2975", "java:S1182"}) // allow clone()
    @Override
    public Retryer clone() {
        return new FeignRetryer(period, maxPeriod, maxAttempts);
    }

    /**
     * Logic borrowed from parent class' method (the method is not accessible) and does not affect the business logic.
     * It's used for logging purposes only.
     *
     * @return time in milliseconds from now until the next attempt
     */
    @SuppressWarnings("java:S2177") // allow overridden method signature
    private long nextMaxInterval() {
        long interval = (long) (period * Math.pow(1.5, currentAttempt - 1.0));
        return Math.min(interval, maxPeriod);
    }

}
