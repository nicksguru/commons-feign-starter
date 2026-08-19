package guru.nicks.commons.feign.injector;

import jakarta.annotation.Nullable;

/**
 * Thrown when all attempts to obtain a fresh header value have failed and no usable cached value remains (see
 * {@link ExpirableFeignHeaderInjector#getFailurePolicy()}).
 */
public class FeignHeaderRefreshException extends RuntimeException {

    /**
     * Constructor.
     *
     * @param headerName header name
     * @param cause      last refresh failure, {@code null} if unknown
     */
    public FeignHeaderRefreshException(String headerName, @Nullable Throwable cause) {
        super("Unable to obtain '"
                + headerName + "' header: all refresh attempts failed and no usable cached value remains", cause);
    }

}
