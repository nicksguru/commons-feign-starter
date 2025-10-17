package guru.nicks.feign.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.jackson.Jacksonized;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "spring.cloud.openfeign.retry")
@Validated
// immutability
@Value
@NonFinal // needed for CGLIB to bind property values (nested classes don't need this)
@Jacksonized
@Builder(toBuilder = true)
public class FeignRetryProperties {

    @NotNull
    Duration initialDelayBetweenAttempts;

    @NotNull
    Duration maxDelayBetweenAttempts;

    @Min(0)
    @NotNull
    Integer maxAttempts;

}
