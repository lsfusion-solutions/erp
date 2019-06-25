package lsfusion.erp.region.by.integration.edi.edn;

import lsfusion.erp.ERPLoggers;
import lsfusion.erp.region.by.integration.edi.SendEOrderAction;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.jdom.JDOMException;

import java.io.IOException;
import java.sql.SQLException;

public class SendEOrderEDNAction extends SendEOrderAction {
    String provider = "EDN";

    public SendEOrderEDNAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            String login = (String) findProperty("loginEDN[]").read(context);
            String password = (String) findProperty("passwordEDN[]").read(context);
            String host = (String) findProperty("hostEDN[]").read(context);
            Integer port = (Integer) findProperty("portEDN[]").read(context);
            String outputDir = (String) findProperty("outputDirEDN[]").read(context);
            if (login != null && password != null && host != null && port != null) {
                String url = String.format("https://%s:%s/topby/DmcService?wsdl", host, port);
                sendEOrder(context, url, login, password, host, port, outputDir, provider);
            } else {
                ERPLoggers.importLogger.info(provider + " SendEOrder: не заданы имя пользователя / пароль / хост / порт");
                context.delayUserInteraction(new MessageClientAction(provider + " Заказ не выгружен: не заданы имя пользователя / пароль / хост / порт", "Экспорт"));
            }

        } catch (ScriptingErrorLog.SemanticErrorException | IOException | JDOMException e) {
            ERPLoggers.importLogger.error(provider + " error: ", e);
            context.delayUserInteraction(new MessageClientAction(provider + " error: " + e.getMessage(), "Ошибка"));
        }
    }
}