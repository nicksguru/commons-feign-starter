package guru.nicks.commons.cucumber;

import guru.nicks.commons.feign.FeignLogger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Test configuration for capturing log output from {@link FeignLogger}.
 */
@Configuration(proxyBeanMethods = false)
public class FeignLoggerTestConfig {

    /**
     * Creates a {@link ListAppender} that captures log events from {@link FeignLogger}.
     *
     * @return log appender for capturing log events
     */
    @Bean
    public ListAppender<ILoggingEvent> loggerListAppender() {
        var listAppender = new ListAppender<ILoggingEvent>();
        listAppender.start();

        Logger logger = (Logger) LoggerFactory.getLogger(FeignLogger.class);
        logger.addAppender(listAppender);
        // ensure trace messages are logged
        logger.setLevel(Level.TRACE);

        return listAppender;
    }

}
