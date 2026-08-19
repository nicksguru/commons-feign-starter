package guru.nicks.commons.feign.injector;

import guru.nicks.commons.cache.AsyncCacheRefresher;
import guru.nicks.commons.cache.CaffeineEntryExpirationCondition;
import guru.nicks.commons.feign.domain.ExpirableHeader;
import guru.nicks.commons.utils.Resilience4jUtils;
import guru.nicks.commons.utils.text.TimeUtils;

import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.event.RetryOnErrorEvent;
import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static guru.nicks.commons.validation.dsl.ValiDsl.check;

/**
 * See description in {@link #getHeaderValue()}.
 */
@Slf4j
public abstract class ExpirableFeignHeaderInjector
        implements FeignHeaderInjector, AsyncCacheRefresher<ExpirableHeader> {

    private static final String THE_ONLY_CACHE_KEY = "THE_ONLY_CACHE_KEY";

    @Getter // no 'onMethod_ = @Override', otherwise ApiDocs are not generated
    private final ScheduledExecutorService cacheRefresherTask = Executors.newSingleThreadScheduledExecutor();

    private final Retry retrier = Resilience4jUtils.createDefaultRetrier(getClass().getName());
    private final AtomicBoolean retrierPostConfigured = new AtomicBoolean();
    private final AtomicBoolean asyncRefreshInFlight = new AtomicBoolean();

    // atomic references for consistent locking-free publication; plain get/set suffices because loadToCache() is the
    // single writer (Caffeine loads are not concurrent for the same key)
    private final AtomicReference<ExpirableHeader> lastKnownGoodHeader = new AtomicReference<>();
    private final AtomicReference<RuntimeException> lastRefreshFailure = new AtomicReference<>();

    private final LoadingCache<String, ExpirableHeader> cache = CaffeineEntryExpirationCondition
            .createCaffeineBuilder(ExpirableHeader::getExpirationDate)
            .maximumSize(1)
            .build(this::loadToCache);

    /**
     * If header value hasn't expired, returns it, otherwise obtains a new one via {@link #obtainFreshHeader()} and
     * caches it until {@link ExpirableHeader#getExpirationDate()}.
     * <p>
     * Failed refreshes are negatively cached for {@link #getFailureCacheTtl()}: during that period,
     * {@link #obtainFreshHeader()} is not called again. Instead, the last known good header is served as stale while
     * {@link #getStaleWindow()} permits, and a single background refresh is scheduled (see
     * {@link #getCacheRefresherTask()}). When no usable stale value remains, {@link #getFailurePolicy()} defines the
     * behavior.
     * <p>
     * Most of the time, {@link #obtainFreshHeader()} doesn't need to be called here - the header value is refreshed
     * preemptively (asynchronously - see {@link #calculateAsyncRefreshDate(Instant)}).
     * <p>
     * {@link #sendAlert(Throwable)} is called on exceptions, but does not re-throw them: remote API may still accept
     * the old auth token if it hasn't expired yet.
     *
     * @return header value
     * @throws FeignHeaderRefreshException all refresh attempts have failed, no usable cached value remains, and failure
     *                                     policy is {@link FailurePolicy#FAIL_FAST}
     */
    @Override
    public String getHeaderValue() {
        ExpirableHeader header = cache.get(THE_ONLY_CACHE_KEY);

        // negatively cached failure - no new attempt until the negative cache entry expires
        if (header instanceof FailedHeader) {
            return serveLastKnownGoodOrFail();
        }

        return formatHeaderValue(header);
    }

    /**
     * Serves the last known good header while refreshes keep failing. If the stale window (see
     * {@link #getStaleWindow()}) permits, returns the stale header value and schedules a single background refresh.
     * Otherwise, applies {@link #getFailurePolicy()}: fails fast or returns an empty value.
     *
     * @return stale header value, or empty string if failure policy is {@link FailurePolicy#SEND_EMPTY}
     * @throws FeignHeaderRefreshException no usable stale value remains and failure policy is
     *                                     {@link FailurePolicy#FAIL_FAST}
     */
    private String serveLastKnownGoodOrFail() {
        // eternal last-known-good header is not served stale: it has no expiration date, so the stale deadline cannot
        // be calculated - serving it forever would mask a permanent refresh failure
        Instant staleDeadline = Optional.ofNullable(lastKnownGoodHeader.get())
                .map(ExpirableHeader::getExpirationDate)
                .map(expirationDate -> expirationDate.plus(getStaleWindow()))
                .orElse(null);
        Instant now = Instant.now();

        if ((staleDeadline != null) && now.isBefore(staleDeadline)) {
            log.warn("{} header refresh failed: serving stale value until {}, scheduling single async refresh",
                    getHeaderName(), staleDeadline);
            scheduleSingleAsyncRefresh();
            return formatHeaderValue(lastKnownGoodHeader.get());
        }

        if (getFailurePolicy() == FailurePolicy.FAIL_FAST) {
            throw new FeignHeaderRefreshException(getHeaderName(), lastRefreshFailure.get());
        }

        log.error("{} header refresh failed: no cached value, sending empty header value", getHeaderName());
        return "";
    }

    /**
     * Schedules a single background refresh (see {@link #refresh()}) if none is currently in flight: concurrent callers
     * served with a stale value must not cause a refresh storm.
     */
    private void scheduleSingleAsyncRefresh() {
        if (asyncRefreshInFlight.compareAndSet(false, true)) {
            cacheRefresherTask.execute(() -> {
                try {
                    refresh();
                }
                // already logged by retry event handlers; negative cache entry bounds the next attempt
                catch (RuntimeException e) {
                    // do nothing
                } finally {
                    asyncRefreshInFlight.set(false);
                }
            });
        }
    }

    /**
     * Concatenates value prefix (if any) with the header value.
     *
     * @param header cached header, nullable
     * @return header value, or empty string if there's no header
     */
    private String formatHeaderValue(@Nullable ExpirableHeader header) {
        return Optional.ofNullable(header)
                .map(cached -> StringUtils.isNotBlank(cached.getValuePrefix())
                        ? (cached.getValuePrefix() + cached.getValue())
                        : cached.getValue())
                .orElse("");
    }

    @Override
    public CompletableFuture<ExpirableHeader> createCacheRefreshFuture() {
        return cache.refresh(THE_ONLY_CACHE_KEY);
    }

    @Override
    public void possiblyScheduleAsyncRefresh(@Nullable Instant expirationDate) {
        // eternal header
        if (expirationDate == null) {
            log.info("{} header refreshed: no expiration, no async refresh", getHeaderName());
            return;
        }

        Instant now = Instant.now();
        // header already expired
        if (!expirationDate.isAfter(now)) {
            log.warn("{} header refreshed: already expired (at {}), no async refresh", getHeaderName(), expirationDate);
            return;
        }

        Duration timeUntilExpiration = Duration.between(now, expirationDate);
        Instant asyncRefreshDate = calculateAsyncRefreshDate(expirationDate).orElse(null);

        // no async refresh requested
        if (asyncRefreshDate == null) {
            log.info("{} header refreshed: expires in {} (at {}), no async refresh", getHeaderName(),
                    TimeUtils.humanFormatDuration(timeUntilExpiration), expirationDate);
            return;
        }

        // sanity check: must be in the future
        if (!asyncRefreshDate.isAfter(now)) {
            log.warn("{} header async refresh date ({}) is not in the future - disabling async refresh",
                    getHeaderName(), asyncRefreshDate);
            return;
        }

        // sanity check: must be before expiration
        if (!asyncRefreshDate.isBefore(expirationDate)) {
            log.warn("{} header async refresh date ({}) is not before expiration date ({}) - disabling async refresh",
                    getHeaderName(), asyncRefreshDate, expirationDate);
            return;
        }

        Duration timeUntilAsyncRefresh = Duration.between(now, asyncRefreshDate);

        log.info("{} header refreshed: expires in {} (at {}), async refresh in {} (at {})", getHeaderName(),
                TimeUtils.humanFormatDuration(timeUntilExpiration), expirationDate,
                TimeUtils.humanFormatDuration(timeUntilAsyncRefresh), asyncRefreshDate);

        cacheRefresherTask.schedule(this::refresh, timeUntilAsyncRefresh.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Obtains a fresh header value. For example, fetches a JWT from the auth provider.
     *
     * @return fresh header value
     */
    protected abstract ExpirableHeader obtainFreshHeader();

    /**
     * Sends alert on {@link #obtainFreshHeader()} failure.
     *
     * @param t exception caught, if any
     */
    protected abstract void sendAlert(@Nullable Throwable t);

    /**
     * Obtains a fresh header via {@link #obtainFreshHeader()} with retries. Sends an alert if all retries have failed.
     * Thanks to async refresh, there's hopefully enough time for retries until the header actually expires.
     * <p>
     * On failure, doesn't re-throw: returns a negatively cached {@link FailedHeader} instead ({@code null} is not an
     * option because Caffeine evicts null loads, which would cause a retry loop on each HTTP request). The negative
     * cache entry expires after {@link #getFailureCacheTtl()}, which defines when the next attempt takes place.
     *
     * @param key cache key
     * @return header value, or negatively cached {@link FailedHeader} if all refresh attempts have failed
     */
    private ExpirableHeader loadToCache(String key) {
        check(key, "cache key").constraint(THE_ONLY_CACHE_KEY::equals, "must equal '" + THE_ONLY_CACHE_KEY + "'");

        // finish configuring (class has no constructor, therefore it's done here)
        if (retrierPostConfigured.compareAndSet(false, true)) {
            retrier.getEventPublisher()
                    .onRetry(this::handleRetryEvent)
                    .onError(this::handleErrorEvent);
        }

        ExpirableHeader header = null;
        try {
            header = Decorators.ofSupplier(this::obtainFreshHeader)
                    .withRetry(retrier)
                    .get();
        }
        // retry limit exceeded - original exception is re-thrown by Resilience4j
        catch (RuntimeException e) {
            // stored as a cause if fail-fast kicks in - see event publisher config above for alerts
            lastRefreshFailure.set(e);
        }

        if (header == null) {
            log.warn("{} header refresh failure: negatively caching failure for {}, stale header served within {}",
                    getHeaderName(), TimeUtils.humanFormatDuration(getFailureCacheTtl()),
                    TimeUtils.humanFormatDuration(getStaleWindow()));
            return new FailedHeader(Instant.now().plus(getFailureCacheTtl()));
        }

        lastKnownGoodHeader.set(header);
        possiblyScheduleAsyncRefresh(header.getExpirationDate());
        return header;
    }

    /**
     * Logs each upcoming retry (cannot find out the failed URL because {@link #obtainFreshHeader()} is abstract).
     *
     * @param event event  the retry event
     */
    private void handleRetryEvent(RetryOnRetryEvent event) {
        log.error("Attempt #{} to refresh {} header failed (will retry in {}): {}",
                // starts with 1 because this handler is called before the 1st retry
                event.getNumberOfRetryAttempts(),
                getHeaderName(),
                TimeUtils.humanFormatDuration(event.getWaitInterval()),
                event.getLastThrowable(),
                // goes to logger implicitly, for stack trace
                event.getLastThrowable());
    }

    /**
     * Sends alert after the last failed retry (cannot find out the failed URL because {@link #obtainFreshHeader()} is
     * abstract).
     *
     * @param event event  the error event
     */
    private void handleErrorEvent(RetryOnErrorEvent event) {
        log.error("Attempt #{} to refresh {} header failed (no more retries left): {}",
                // actually this is the total number of attempts, including the very first one
                event.getNumberOfRetryAttempts(),
                getHeaderName(),
                event.getLastThrowable(),
                // goes to logger implicitly, for stack trace
                event.getLastThrowable());
        sendAlert(event.getLastThrowable());
    }

    /**
     * Shuts down the scheduled executor service when the bean is destroyed.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Feign header injector cache refresher task");
        cacheRefresherTask.shutdown();

        try {
            if (!cacheRefresherTask.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate in 5 seconds, forcing shutdown");
                cacheRefresherTask.shutdownNow();

                if (!cacheRefresherTask.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("Executor did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            log.error("Interrupted during executor shutdown", e);
            cacheRefresherTask.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Negative cache entry stored when all refresh attempts have failed. Its expiration date (see
     * {@link #getFailureCacheTtl()}) defines when the next refresh attempt takes place. Detected via {@code instanceof}
     * in {@link #getHeaderValue()}.
     */
    private static final class FailedHeader extends ExpirableHeader {

        private FailedHeader(Instant expirationDate) {
            super(null, null, Instant.now(), expirationDate);
        }

    }

}
