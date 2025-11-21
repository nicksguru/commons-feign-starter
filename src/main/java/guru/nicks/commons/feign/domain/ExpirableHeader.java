package guru.nicks.commons.feign.domain;

import guru.nicks.commons.utils.auth.AuthUtils;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@NonFinal
@Jacksonized
@Builder(toBuilder = true)
public class ExpirableHeader {

    /**
     * For example, {@value AuthUtils#BEARER_AUTH_PREFIX}.
     */
    String valuePrefix;

    String value;

    Instant issuedDate;

    /**
     * {@code null} means no expiration
     */
    Instant expirationDate;

}
