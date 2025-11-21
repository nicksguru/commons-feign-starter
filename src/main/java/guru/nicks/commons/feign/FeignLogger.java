package guru.nicks.commons.feign;

import guru.nicks.commons.ApplicationContextHolder;
import guru.nicks.commons.log.domain.LogContext;
import guru.nicks.commons.utils.ExceptionUtils;
import guru.nicks.commons.utils.text.TimeUtils;
import guru.nicks.commons.utils.json.JsonUtils;

import feign.Logger;
import feign.Util;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Logs Feign client activity (requests and responses) in a way similar to what {@link Logger} does out-of-the box, with
 * the following differences:
 * <ul>
 *  <li>log level {@link Level#NONE} ({@code feign.client.config.default.loggerLevel})
 *      turns logging off; all others don't affect the logging behavior</li>
 *  <li>request and response bodies are always logged</li>
 *  <li>request and response headers are never logged, except response's HTTP status</li>
 * </ul>
 *  The point of having this logger is: default {@link Logger} logs either headers only or body+headers.
 *  But request headers contain sensitive information (auth tokens) which should never be revealed.
 */
@RequiredArgsConstructor
@Slf4j
public class FeignLogger extends Logger {

    private static final Pattern URL_PERCENT_ESCAPE_PATTERN = Pattern.compile("%(-?\\d*[a-z])");

    @NonNull // Lombok creates runtime nullness check for this own annotation only
    private final Function<String, URI> springCloudServiceNameToUrlResolver;

    /**
     * Logs message.
     * <p>
     * Format contains the URL being invoked, which may contain special characters: '%2C' is ',' etc. Without special
     * measures, {@link String#format(Locale, String, Object...)} will complain about invalid format because of '%'. For
     * this reason, this method - for logging purposes only - replaces all '%' with '%25' if it precedes optional digits
     * followed by a lowercase letter (such as '%s', '%-02d') - because URL encoding (ASCII 0..127) starts with '%00'
     * and ends with '%7F' (and always has UPPERCASE letters anyway).
     *
     * @param configKey see parent method (in fact this is a message tag)
     * @param format    see {@link String#format(String, Object...)}
     * @param args      arguments for {@code format}
     */
    @Override
    protected void log(String configKey, String format, Object... args) {
        // MDC is empty is Feign was called from a thread whose MDC wasn't initialized explicitly
        ApplicationContextHolder
                .findApplicationName()
                .ifPresent(LogContext.APP_NAME::put);

        String formatWithoutUrlSpecials = URLDecoder.decode(
                URL_PERCENT_ESCAPE_PATTERN.matcher(format).replaceAll("%25$1"),
                StandardCharsets.UTF_8);

        // heuristics based on the messages logAndRebufferResponse() creates
        if (formatWithoutUrlSpecials.contains(" [5") || formatWithoutUrlSpecials.contains(" [4")) {
            log.error("{} {}", Logger.methodTag(configKey), String.format(Locale.US, formatWithoutUrlSpecials, args));
        } else {
            log.info("{} {}", Logger.methodTag(configKey), String.format(Locale.US, formatWithoutUrlSpecials, args));
        }
    }

    @Override
    public void logRequest(String configKey, Level logLevel, feign.Request request) {
        // logging turned off in Feign config
        if (logLevel.compareTo(Level.NONE) == 0) {
            return;
        }

        String messagePrefix = String.format(Locale.US, "Sending request [%s %s]",
                request.httpMethod().name(), springCloudServiceNameToUrlResolver.apply(request.url()));

        if ((request.body() == null) || (request.body().length == 0)) {
            log(configKey, messagePrefix);
        } else {
            log(configKey, messagePrefix + " with body: %s", JsonUtils.maskSensitiveJsonFields(request.body()));
        }
    }

    /**
     * This method's body is logically similar to that of the parent class.
     */
    @Override
    public feign.Response logAndRebufferResponse(String configKey, Level logLevel, feign.Response response,
            long elapsedMillis) {
        // logging turned off in Feign config
        if (logLevel.compareTo(Level.NONE) == 0) {
            return response;
        }

        // Feign manual claims there's no reason (such as 'Not Found' for 404) when using HTTP/2
        String httpStatusExplanation = Optional.ofNullable(response.reason())
                .map(reason -> " " + reason)
                .orElse("");

        // body is null e.g. if HTTP status is 401 Unauthorized
        byte[] bodyData;

        if (response.body() == null) {
            bodyData = new byte[0];
        } else {
            try {
                bodyData = Util.toByteArray(response.body().asInputStream());
            }
            // sometimes, InterruptedIOException occurs indeed, and rethrowing it would break the retries
            catch (IOException e) {
                bodyData = new byte[0];
            }
        }

        String messagePrefix = String.format(Locale.US, "Received%s response [%s%s] from [%s %s] in %ss",
                (bodyData.length == 0) ? " empty" : "",
                response.status(), httpStatusExplanation,
                response.request().httpMethod().name(),
                springCloudServiceNameToUrlResolver.apply(response.request().url()),
                TimeUtils.convertMillisToSeconds(elapsedMillis));

        if (bodyData.length == 0) {
            log(configKey, messagePrefix);
        } else {
            log(configKey, messagePrefix + ": %s", JsonUtils.maskSensitiveJsonFields(bodyData));
        }

        // once response has been read, it should be rebuilt for further (re)reading by caller
        return response.toBuilder().body(bodyData).build();
    }

    @Override
    public IOException logIOException(String configKey, Level logLevel, IOException e, long elapsedMillis) {
        // logging turned off in Feign config
        if (logLevel.compareTo(Level.NONE) == 0) {
            return e;
        }

        log.error("{} Error after {} ms.: {}", Logger.methodTag(configKey), elapsedMillis,
                ExceptionUtils.formatWithCompactStackTrace(e));
        return e;
    }

}
