package guru.nicks.commons.feign.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "spring.cloud.openfeign.retry")
@Validated
// immutability
@Value
@NonFinal // CGLIB creates a subclass to bind property values (nested classes don't need this)
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
