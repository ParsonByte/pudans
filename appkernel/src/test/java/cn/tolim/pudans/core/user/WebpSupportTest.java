package cn.tolim.pudans.core.user;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WebpSupportTest {

    @Test
    void warnsWhenTheWebpDecoderIsMissing() {
        var logger = (Logger) LoggerFactory.getLogger(WebpSupport.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            new WebpSupport(false).warnIfUnavailable();

            assertThat(appender.list)
                    .anyMatch(event -> event.getFormattedMessage().contains("WebP"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
