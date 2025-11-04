package guru.nicks.feign.config;

import guru.nicks.feign.BugfixSortPageableEncoder;
import guru.nicks.feign.FeignRetryer;
import guru.nicks.feign.domain.FeignRetryProperties;
import guru.nicks.utils.TimeUtils;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.Encoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.cloud.openfeign.loadbalancer.RetryableFeignBlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static guru.nicks.validation.dsl.ValiDsl.check;

/**
 * Thanks to {@link Configuration @Configuration}, this config applies to <b>all</b> Feign clients implicitly.
 * Individual clients should refer to their private configs via {@link FeignClient#configuration()} (such configs must
 * not be annotated with {@link Configuration @Configuration}), for example, to install a
 * {@link feign.RequestInterceptor}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FeignRetryProperties.class)
@Slf4j
public class FeignAutoConfiguration {

    /**
     * Sets Feign log level (to override the default which is no logging at all). A bean is needed because the official
     * config setting {@code feign.client.config.default.loggerLevel} stopped working at some point.
     */
    @ConditionalOnMissingBean(Logger.Level.class)
    @Bean
    public Logger.Level feignLoggerLevel() {
        log.info("Setting Feign log level to FULL");
        return Logger.Level.FULL;
    }

    /**
     * Overrides {@link Encoder} created by {@link FeignClientsConfiguration} which doesn't support Spring Data Web
     * Sort/Pageable parameters.
     *
     * @return encoder bean
     */
    @ConditionalOnMissingBean(Encoder.class)
    @Bean
    public Encoder feignEncoder(ObjectFactory<HttpMessageConverters> messageConverters,
            SpringDataWebProperties springDataWebProperties) {
        log.info("Fixing Feign paging/sorting encoder");
        return new BugfixSortPageableEncoder(new SpringEncoder(messageConverters), springDataWebProperties);
    }

    /**
     * Overrides {@link Retryer#NEVER_RETRY} created by {@link FeignClientsConfiguration} which disables retrying. See
     * {@link RetryableFeignBlockingLoadBalancerClient#execute(Request, Request.Options)}.
     * <p>
     * The retry settings are read from {@code feign.retry.[initialDelayMs,maxDelayMs,maxRetryAttempts]}.
     *
     * @return retryer bean
     */
    @ConditionalOnMissingBean(Retryer.class)
    @Bean
    public Retryer feignRetryer(FeignRetryProperties feignRetryProperties) {
        // in addition to bean-level validation
        check(feignRetryProperties.getInitialDelayBetweenAttempts().toMillis(),
                "initialDelayBetweenAttempts").positiveOrZero();
        check(feignRetryProperties.getMaxDelayBetweenAttempts().toMillis(),
                "maxDelayBetweenAttempts").positiveOrZero();

        log.info("Feign retry policy: {} attempts with delay changing from {} to {} between attempts",
                feignRetryProperties.getMaxAttempts(),
                TimeUtils.humanFormatDuration(feignRetryProperties.getInitialDelayBetweenAttempts()),
                TimeUtils.humanFormatDuration(feignRetryProperties.getMaxDelayBetweenAttempts()));

        return new FeignRetryer(feignRetryProperties.getInitialDelayBetweenAttempts().abs().toMillis(),
                feignRetryProperties.getMaxDelayBetweenAttempts().abs().toMillis(),
                feignRetryProperties.getMaxAttempts());
    }

}
