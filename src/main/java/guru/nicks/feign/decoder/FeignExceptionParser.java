package guru.nicks.feign.decoder;

import guru.nicks.exception.BusinessException;
import guru.nicks.exception.SubclassBeforeSuperclassExceptionIterator;
import guru.nicks.feign.domain.FailedRemoteCall;
import guru.nicks.rest.v1.dto.BusinessExceptionDto;

import feign.FeignException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import static guru.nicks.validation.dsl.ValiDsl.checkNotNull;

/**
 * @see #parse(Throwable)
 */
@RequiredArgsConstructor
public class FeignExceptionParser {

    @NonNull // Lombok creates runtime nullness check for this own annotation only
    private final FailedRemoteCallParserVisitor visitor;

    /**
     * Tries to restore remote exception (if it's JSON which looks like {@link BusinessExceptionDto}) by exploring the
     * exception chain. Usage example:
     * <pre>
     * try {
     *      feignClient.someMethod();
     * } catch (Exception e) {
     *      throw feignExceptionParser.parse(e).getDeepCause();
     * }
     * </pre>
     *
     * @param t exception reported by Feign (usually {@link FeignException} / {@link BusinessException})
     * @return result of (successful or not) parsing
     */
    public FailedRemoteCall parse(Throwable t) {
        checkNotNull(t, "exception to parse");

        return new SubclassBeforeSuperclassExceptionIterator(t)
                .acceptUntilResult(visitor, visitor.createNewState())
                // the above visitor always returns a non-empty Optional - fully or partially populate remote call info
                .orElseThrow(() -> new IllegalStateException("Visitor contract broken: " + visitor.getClass()));
    }

}
