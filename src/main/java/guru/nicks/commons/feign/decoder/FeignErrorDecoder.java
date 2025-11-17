package guru.nicks.commons.feign.decoder;

import guru.nicks.commons.exception.BusinessException;
import guru.nicks.commons.exception.RootHttpStatus;
import guru.nicks.commons.utils.HttpRequestUtils;

import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.function.BiFunction;

/**
 * Wraps some exceptions in {@link BusinessException} and some in {@link RetryableException}. If Feign sees a bean of
 * this class. it uses it automatically.
 * <p>
 * {@link Throwable#getCause()} holds the original cause (usually a {@link feign.FeignException}).
 * <p>
 * {@link RetryableException} should make Feign retry the request.
 * <p>
 * NOTE: Feign retries on {@link IOException} automatically because this means no connection, invalid hostname,
 * etc.
 */
@RequiredArgsConstructor
public class FeignErrorDecoder implements ErrorDecoder {

    @NonNull // Lombok creates runtime nullness check for this own annotation only
    private final BiFunction<Integer, Throwable, BusinessException> httpStatusCodeMapper;

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        Exception cause = defaultErrorDecoder.decode(methodKey, response);

        // propagate retryable stuff as-is to make retries work
        if (cause instanceof RetryableException) {
            return cause;
        }

        // if RetryableException is returned, Feign auto-retries the request
        return createPossiblyRetryableException(response, cause);
    }

    /**
     * Returns {@link RetryableException} / {@link BusinessException}.
     * <p>
     * For retryable exceptions, the nested structure is, if {@link Response#status()} for example returns 400:
     * {@code RetryableException(BadRequestException(FeignException)}).
     * <p>
     * For non-retryable ones, the structure is: {@code BadRequestException(FeignException)}.
     * <p>
     * The purpose of this method is to map HTTP statuses to 'root HTTP status' exceptions, as per their
     * {@link RootHttpStatus @RootHttpStatus}.
     *
     * @param response response
     * @param cause    original exception returned by Feign
     * @return exception - raw or retryable
     */
    private Exception createPossiblyRetryableException(Response response, Throwable cause) {
        int httpStatusCode = response.status();
        // -1 means request was not sent because of IOException - see FeignException.errorExecuting()
        if (httpStatusCode == -1) {
            httpStatusCode = HttpStatus.GATEWAY_TIMEOUT.value();
        }
        // 500 returned from remote party becomes 502 in order to distinguish remote errors from local ones
        else if (httpStatusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            httpStatusCode = HttpStatus.BAD_GATEWAY.value();
        }

        BusinessException e = httpStatusCodeMapper.apply(httpStatusCode, cause);

        return requestIsRetryable(response)
                ? new RetryableException(response.status(), cause.getMessage(), response.request().httpMethod(),
                e, 0L, response.request())
                : e;
    }

    /**
     * Only 5xx statuses are retryable because things like '400 Bad Request' are client side errors. Don't retry POST
     * because it's not idempotent: may create multiple entities; all other HTTP methods are retryable.
     *
     * @param response response from remote party
     */
    private boolean requestIsRetryable(Response response) {
        HttpStatus httpStatus = HttpRequestUtils
                .resolveHttpStatus(response.status())
                .orElse(null);

        return (httpStatus != null)
                && httpStatus.is5xxServerError()
                && (Request.HttpMethod.POST != response.request().httpMethod());
    }

}
