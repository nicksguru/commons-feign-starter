package guru.nicks.commons.feign.decoder;

import guru.nicks.commons.designpattern.visitor.ReflectionVisitorMethod;
import guru.nicks.commons.designpattern.visitor.StatefulReflectionVisitor;
import guru.nicks.commons.exception.BusinessException;
import guru.nicks.commons.exception.RootHttpStatus;
import guru.nicks.commons.exception.http.ServiceTimeoutException;
import guru.nicks.commons.feign.domain.FailedRemoteCall;
import guru.nicks.commons.rest.v1.dto.BusinessExceptionDto;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.http.HttpStatus;

import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Tries to map exception to the parts {@link FailedRemoteCall} consists of - each part is initialized only once. At the
 * end of the day, <b>always</b> returns non-empty {@link Optional} - fully or partially populated
 * {@link FailedRemoteCall}, maybe even with all properties set to {@code null}.
 */
@RequiredArgsConstructor
@Slf4j
public class FailedRemoteCallParserVisitor
        extends StatefulReflectionVisitor<FailedRemoteCallParserVisitor.State, FailedRemoteCall> {

    /**
     * Must tolerate {@code null} argument.
     */
    @NonNull // Lombok creates runtime nullness check for this own annotation only
    private final Function<BusinessExceptionDto, Optional<BusinessException>> businessExceptionDtoMapper;

    @NonNull // Lombok creates runtime nullness check for this own annotation only
    private final BiFunction<HttpStatus, Throwable, BusinessException> httpStatusMapper;

    @NonNull // Lombok creates runtime nullness check for this own annotation only
    private final HttpStatusRetrieverVisitor httpStatusRetrieverVisitor;

    @NonNull // Lombok creates runtime nullness check for this own annotation only
    private final ObjectMapper objectMapper;

    /**
     * Tries to fill in missing {@link State#getResultBuilder()} parts.
     */
    @ReflectionVisitorMethod
    public Optional<FailedRemoteCall> visit(Throwable t, State state) {
        var builder = state.getResultBuilder();

        // initialize original exception
        if (builder.getOriginalException() == null) {
            builder.originalException(t);
        }

        // initialize local exception
        if (builder.getLocalException() == null) {
            findLocalException(t).ifPresent(builder::localException);
        }

        // initialize remote exception
        if (builder.getRemoteException() == null) {
            findRemoteException(t).ifPresent(builder::remoteException);
        }

        boolean bothExceptionsKnown = (builder.getLocalException() != null) && (builder.getRemoteException() != null);
        boolean chainEnded = (t.getCause() == null);

        // stop exception chain traversal if both exceptions have been found
        // OR there are no more exceptions to look at (i.e. no more chance to parse something)
        return (bothExceptionsKnown || chainEnded)
                ? Optional.of(builder.build())
                : Optional.empty();
    }

    /**
     * Tries to parse response body as {@link BusinessExceptionDto} and convert it to {@link BusinessException}.
     *
     * @param t exception whose {@link FeignException#responseBody()} will be parsed
     * @return optional exception
     */
    private Optional<BusinessException> findRemoteException(Throwable t) {
        return Optional.of(t)
                .filter(FeignException.class::isInstance)
                .map(FeignException.class::cast)
                .flatMap(FeignException::responseBody)
                //
                .map(ByteBuffer::array)
                .filter(ArrayUtils::isNotEmpty)
                // parse JSON
                .map((byte[] bytes) -> {
                    if (log.isTraceEnabled()) {
                        log.trace("Parsing Feign response body: {}", new String(bytes, StandardCharsets.UTF_8));
                    }

                    try {
                        return objectMapper.readValue(bytes, BusinessExceptionDto.class);
                    } catch (Exception e) {
                        if (log.isTraceEnabled()) {
                            log.trace("Failed to parse Feign response body (not JSON?): {}", e.getMessage(), e);
                        }

                        return null;
                    }
                })
                .flatMap(businessExceptionDtoMapper);
    }

    /**
     * Maps HTTP status to 'core' exception (the root of all exceptions having the same HTTP status - see
     * {@link RootHttpStatus @RootHttpStatus}). Tries to retrieve the HTTP status by checking known exception classes.
     *
     * @param t exception
     * @return optional status-related exception
     */
    private Optional<BusinessException> findLocalException(Throwable t) {
        if (t instanceof BusinessException businessException) {
            return Optional.of(businessException);
        }

        // one of SocketException subclasses is ConnectException which is the case for e.g. unknown host
        if (t instanceof SocketException) {
            return Optional.of(new ServiceTimeoutException(t));
        }

        // call visitor explicitly - in order to process only one exception, not the whole chain
        return httpStatusRetrieverVisitor
                .apply(t)
                .map(httpStatus -> httpStatusMapper.apply(httpStatus, t));
    }

    /**
     * Stores data between calls.
     */
    @Getter
    public static class State {

        private final FailedRemoteCall.FailedRemoteCallBuilder resultBuilder = FailedRemoteCall.builder();

    }

}
