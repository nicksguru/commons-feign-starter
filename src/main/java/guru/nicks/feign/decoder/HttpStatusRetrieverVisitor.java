package guru.nicks.feign.decoder;

import guru.nicks.designpattern.visitor.ReflectionVisitor;
import guru.nicks.designpattern.visitor.ReflectionVisitorMethod;
import guru.nicks.exception.BusinessException;
import guru.nicks.utils.HttpRequestUtils;

import feign.FeignException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.function.Function;

/**
 * Retrieves {@link HttpStatus} out of known classes:
 * <ul>
 *  <li>{@link BusinessException} (with the help of the custom mapper passed to the constructor)</li>
 *  <li>{@link FeignException}</li>
 *  <li>{@link ResponseStatusException}</li>
 * </ul>
 */
@RequiredArgsConstructor
public class HttpStatusRetrieverVisitor extends ReflectionVisitor<HttpStatus> {

    @NonNull // Lombok creates runtime nullness check for this own annotation only
    private final Function<BusinessException, HttpStatus> businessExceptionMapper;

    @ReflectionVisitorMethod
    public Optional<HttpStatus> visit(BusinessException exception) {
        return Optional.ofNullable(businessExceptionMapper.apply(exception));
    }

    @ReflectionVisitorMethod
    public Optional<HttpStatus> visit(FeignException e) {
        int statusCode = e.status();
        // -1 means request was not sent because of IOException - see FeignException.errorExecuting()
        if (statusCode == -1) {
            statusCode = HttpStatus.GATEWAY_TIMEOUT.value();
        }

        return HttpRequestUtils.resolveHttpStatus(statusCode);
    }

    @ReflectionVisitorMethod
    public Optional<HttpStatus> visit(ResponseStatusException e) {
        // the contract claims it's never null, but just in case
        return Optional.ofNullable(e.getStatusCode())
                .map(HttpStatusCode::value)
                .map(HttpStatus::resolve);
    }

}
