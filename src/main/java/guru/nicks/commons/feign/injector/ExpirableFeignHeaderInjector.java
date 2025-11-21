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

import static guru.nicks.commons.validation.dsl.ValiDsl.check;

/**
 * See description in {@link #getHeaderValue()}.
 */
@Slf4j
public abstract class ExpirableFeignHeaderInjector
        implements FeignHeaderInjector, AsyncCacheRefresher<ExpirableHeader> {

    private static final String THE_ONLY_CACHE_KEY = "THE_ONLY_CACHE_KEY";

    @Getter // no 'onMethod_ = @Override', otherwise apidocs are not generated
    private final ScheduledExecutorService cacheRefresherTask = Executors.newSingleThreadScheduledExecutor();

    private final Retry retrier = Resilience4jUtils.createDefaultRetrier(getClass().getName());
    private final AtomicBoolean retrierPostConfigured = new AtomicBoolean();

    private final LoadingCache<String, ExpirableHeader> cache = CaffeineEntryExpirationCondition
            .createCaffeineBuilder(ExpirableHeader::getExpirationDate)
            .maximumSize(1)
            .build(this::loadToCache);

    /**
     * If header value hasn't expired, returns it, otherwise obtains a new one with {@link #obtainFreshHeader()} and
     * caches it until {@link ExpirableHeader#getExpirationDate()} ({@code null} is never cached, which means
     * {@link #obtainFreshHeader()} returning {@code null } is called for each HTTP request).
     * <p>
     * Most of the time, {@link #obtainFreshHeader()} doesn't need to be called here - the header value is refreshed
     * preemptively (asynchronously - see {@link #calculateAsyncRefreshDate(Instant)}).
     * <p>
     * {@link #sendAlert(Throwable)} is called on exceptions, but does not re-throw them: remote API may still accept
     * the old auth token if it hasn't expired yet.
     *
     * @return header value
     */
    @Override
    public String getHeaderValue() {
        return Optional.ofNullable(cache.get(THE_ONLY_CACHE_KEY))
                .map(header -> StringUtils.isNotBlank(header.getValuePrefix())
                        ? (header.getValuePrefix() + header.getValue())
                        : header.getValue())
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
     *
     * @param key cache key
     * @return header value or {@code null}
     */
    private ExpirableHeader loadToCache(String key) {
        check(key, "cache key").constraint(THE_ONLY_CACHE_KEY::equals, "must equal '" + THE_ONLY_CACHE_KEY + "'");

        // finish configuring (class has no constructor, therefore it's done here)
        if (retrierPostConfigured.compareAndSet(false, true)) {
            retrier.getEventPublisher()
                    .onRetry(this::handleRetryEvent)
                    .onError(this::handleErrorEvent);
        }

        ExpirableHeader header = Decorators.ofSupplier(this::obtainFreshHeader)
                .withRetry(retrier)
                .get();

        if (header == null) {
            log.error("{} header refreshed: null value obtained, keeping using the cached value", getHeaderName());
        } else {
            possiblyScheduleAsyncRefresh(header.getExpirationDate());
        }

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

}
