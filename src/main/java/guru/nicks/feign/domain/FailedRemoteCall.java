package guru.nicks.feign.domain;

import guru.nicks.exception.BusinessException;
import guru.nicks.exception.http.BadRequestException;
import guru.nicks.exception.http.ForbiddenException;
import guru.nicks.exception.http.NotFoundException;
import guru.nicks.exception.user.UserNotFoundException;
import guru.nicks.rest.v1.dto.BusinessExceptionDto;

import feign.FeignException;
import feign.RetryableException;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.jackson.Jacksonized;

/**
 * Exception (such as raised by Feign) that was parsed (or not) to {@link #getRemoteException()}. The nested structure
 * is, for example, for HTTP status 404 and JSON error code denoting 'user not found':
 * <ul>
 *  <li>for retryable (i.e. non-POST) exception: original={@link RetryableException}, local={@link NotFoundException},
 *      remote={@link UserNotFoundException}</li>
 *  <li>for non-retryable (i.e. POST) exception: original={@link NotFoundException}, local={@link NotFoundException},
 *      remote={@link UserNotFoundException}</li>
 * </ul>
 * In both cases, {@link FeignException#responseBody()} is examined to see if it looks like
 * a {@link BusinessExceptionDto}, which populates {@link #getRemoteException()} on success.
 */
@Value
@NonFinal
@Jacksonized
@Builder(toBuilder = true)
public class FailedRemoteCall {

    /**
     * Original exception that was parsed (or not).
     */
    Throwable originalException;

    /**
     * Reflects what happened from the caller's point of view, such as {@link ForbiddenException}.
     * <p>
     * Can be {@code null}.
     */
    BusinessException localException;

    /**
     * If remote party returned a JSON which can be parsed to {@link BusinessExceptionDto}, this exception is
     * reconstructed out of it, e.g. {@link NotFoundException} (or its more specific subclass for 'user not found',
     * 'product not found', etc.) for 404.
     * <p>
     * Can be {@code null}.
     */
    BusinessException remoteException;

    /**
     * Infers exception in this order, skipping nulls, in this order:
     * <ul>
     *  <li>{@link #getRemoteException()}</li>
     *  <li>{@link #getLocalException()}</li>
     *  <li>{@link #getOriginalException()}</li>
     * </ul>
     * The idea is to report exceptions coming from own microservices (they return {@link BusinessExceptionDto} parsed
     * into {@link #getRemoteException()}) as if they were local ones.
     * <p>
     * Exceptions from not own services are rendered as plain {@link BadRequestException} etc., depending on their HTTP
     * status.
     */
    public Throwable getDeepCause() {
        if (remoteException != null) {
            return remoteException;
        }

        if (localException != null) {
            return localException;
        }

        return originalException;
    }

    /**
     * Adds getters which return builder fields (Lombok only creates setters for them).
     */
    public static class FailedRemoteCallBuilder {

        public Throwable getOriginalException() {
            return originalException;
        }

        public BusinessException getLocalException() {
            return localException;
        }

        public BusinessException getRemoteException() {
            return remoteException;
        }

    }

}
