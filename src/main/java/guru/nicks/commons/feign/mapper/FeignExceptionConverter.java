package guru.nicks.commons.feign.mapper;

import guru.nicks.commons.exception.BusinessException;
import guru.nicks.commons.exception.ExceptionConverter;

import feign.FeignException;
import jakarta.annotation.Nonnull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import java.util.function.BiFunction;

@RequiredArgsConstructor
public class FeignExceptionConverter implements ExceptionConverter<FeignException, BusinessException> {

    @NonNull // Lombok creates runtime nullness check for this own annotation only
    private final BiFunction<Integer, Throwable, BusinessException> httpStatusCodeMapper;

    @Nonnull
    @Override
    public BusinessException convert(@Nonnull FeignException cause) {
        // -1 means request was not sent because of IOException - see FeignException.errorExecuting()
        int httpStatusCode = (cause.status() == -1)
                ? HttpStatus.GATEWAY_TIMEOUT.value()
                : cause.status();

        return httpStatusCodeMapper.apply(httpStatusCode, cause);
    }

}
