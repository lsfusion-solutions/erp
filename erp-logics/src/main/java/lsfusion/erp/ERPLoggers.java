package lsfusion.erp;

import lsfusion.base.SystemUtils;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class ERPLoggers {
    public static final Logger importLogger = Logger.getLogger("ImportLogger");

    public static final Logger priceCheckerLogger = Logger.getLogger("PriceCheckerLogger");

    public static final Logger terminalLogger = Logger.getLogger("TerminalLogger");

    public static Logger cashRegisterlogger;
    static {
        try {
            cashRegisterlogger = Logger.getLogger("cashRegisterLog");
            cashRegisterlogger.setLevel(Level.INFO);
            FileAppender fileAppender = new FileAppender(new EnhancedPatternLayout("%d{DATE} %5p %c{1} - %m%n%throwable{1000}"),
                    SystemUtils.getUserFile("logs/cashregister.log").getAbsolutePath());
            cashRegisterlogger.removeAllAppenders();
            cashRegisterlogger.addAppender(fileAppender);

        } catch (Exception ignored) {
        }
    }
}