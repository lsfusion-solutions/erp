package lsfusion.erp;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import org.slf4j.LoggerFactory;

public class ERPLoggers {

    public static Logger createLogger(String name, String fileName) {
        return createLogger(name, fileName, 10);
    }

    public static Logger createLogger(String name, String fileName, int sizeMB) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        RollingFileAppender rfAppender = new RollingFileAppender();
        rfAppender.setContext(loggerContext);
        rfAppender.setFile("logs/" + fileName + ".log");
        FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
        rollingPolicy.setMinIndex(1);
        rollingPolicy.setMaxIndex(5);
        rollingPolicy.setContext(loggerContext);
        // rolling policies need to know their parent
        // it's one of the rare cases, where a sub-component knows about its parent
        rollingPolicy.setParent(rfAppender);
        rollingPolicy.setFileNamePattern("logs/" + fileName + "-%i.log.zip");
        rollingPolicy.start();

        SizeBasedTriggeringPolicy triggeringPolicy = new SizeBasedTriggeringPolicy();
        triggeringPolicy.setMaxFileSize(new FileSize(FileSize.MB_COEFFICIENT * sizeMB));
        triggeringPolicy.start();

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{DATE} %5p %c{1} - %m%n%throwable{1000}");
        encoder.start();

        rfAppender.setEncoder(encoder);
        rfAppender.setRollingPolicy(rollingPolicy);
        rfAppender.setTriggeringPolicy(triggeringPolicy);

        rfAppender.start();

        // attach the rolling file appender to the logger of your choice
        Logger logger = loggerContext.getLogger(name);
        logger.addAppender(rfAppender);
        logger.setAdditive(false);
        return logger;
    }
}