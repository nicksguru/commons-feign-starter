package guru.nicks.feign.injector;

import guru.nicks.auth.domain.BasicAuthCredentials;

import lombok.Getter;
import org.springframework.http.HttpHeaders;

/**
 * Injects Basic Auth header.
 */
public class BasicAuthInjector implements FeignHeaderInjector {

    @Getter // no 'onMethod_ = @Override', otherwise apidocs are not generated
    private final String headerValue;

    /**
     * Constructor.
     *
     * @param credentials username and password for Basic Auth
     */
    public BasicAuthInjector(BasicAuthCredentials credentials) {
        headerValue = credentials.convertToHeaderValue();
    }

    /**
     * @return {@value HttpHeaders#AUTHORIZATION}
     */
    @Override
    public String getHeaderName() {
        return HttpHeaders.AUTHORIZATION;
    }

}
