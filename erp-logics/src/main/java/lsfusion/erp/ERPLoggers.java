package lsfusion.erp;

import lsfusion.base.SystemUtils;
import lsfusion.base.log.FlushableRollingFileAppender;
import org.apache.log4j.*;
import org.apache.log4j.rolling.FixedWindowRollingPolicy;
import org.apache.log4j.rolling.SizeBasedTriggeringPolicy;

public class ERPLoggers {
    public static final Logger importLogger = Logger.getLogger("ImportLogger");

    public static final Logger priceCheckerLogger = Logger.getLogger("PriceCheckerLogger");

    public static final Logger terminalLogger = Logger.getLogger("TerminalLogger");

    public static Logger cashRegisterlogger;
    static {
        try {
            cashRegisterlogger = Logger.getLogger("cashRegisterLog");
            cashRegisterlogger.setLevel(Level.INFO);
            FlushableRollingFileAppender fileAppender = new FlushableRollingFileAppender();
            fileAppender.setFile(SystemUtils.getUserFile("logs/cashregister.log").getAbsolutePath());
            FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
            rollingPolicy.setMinIndex(1);
            rollingPolicy.setMaxIndex(5);
            rollingPolicy.setFileNamePattern(SystemUtils.getUserFile("logs/cashregister-%i.log.zip").getAbsolutePath());
            fileAppender.setRollingPolicy(rollingPolicy);
            fileAppender.setTriggeringPolicy(new SizeBasedTriggeringPolicy(10485760)); //10MB
            fileAppender.setLayout(new SimpleLayout());
            fileAppender.activateOptions();
            cashRegisterlogger.removeAllAppenders();
            cashRegisterlogger.addAppender(fileAppender);
        } catch (Exception ignored) {
        }
    }
}