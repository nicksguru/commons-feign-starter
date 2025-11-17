package guru.nicks.commons.feign.injector;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.apache.commons.lang3.StringUtils;

/**
 * Stores {@link #getHeaderName()} + {@link #getHeaderValue()} request header if the header value is not blank.
 */
public interface FeignHeaderInjector extends RequestInterceptor {

    /**
     * If {@link #getHeaderValue()} returns a non-blank value, puts it in the request header with the name returned by
     * {@link #getHeaderName()}.
     *
     * @param request Feign request
     */
    @Override
    default void apply(RequestTemplate request) {
        String headerValue = getHeaderValue();

        if (StringUtils.isNotBlank(headerValue)) {
            request.header(getHeaderName(), headerValue);
        }
    }

    /**
     * @return header name
     */
    String getHeaderName();

    /**
     * @return header value - if blank, request header will not be set
     */
    String getHeaderValue();

}
