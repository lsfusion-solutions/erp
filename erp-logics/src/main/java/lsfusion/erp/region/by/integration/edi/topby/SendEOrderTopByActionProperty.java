package lsfusion.erp.region.by.integration.edi.topby;

import lsfusion.erp.region.by.integration.edi.SendEOrderActionProperty;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.jdom.JDOMException;

import java.io.IOException;
import java.sql.SQLException;

public class SendEOrderTopByActionProperty extends SendEOrderActionProperty {
    String provider = "TopBy";

    public SendEOrderTopByActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            String login = (String) findProperty("loginTopBy[]").read(context);
            String password = (String) findProperty("passwordTopBy[]").read(context);
            String host = (String) findProperty("hostTopBy[]").read(context);
            Integer port = (Integer) findProperty("portTopBy[]").read(context);
            String outputDir = (String) findProperty("outputDirTopBy[]").read(context);
            if (login != null && password != null && host != null && port != null) {
                String url = String.format("http://%s:%s/DmcService", host, port);
                sendEOrder(context, url, login, password, host, port, outputDir, provider);
            } else {
                ServerLoggers.importLogger.info(provider + " SendEOrder: не заданы имя пользователя / пароль / хост / порт");
                context.delayUserInteraction(new MessageClientAction(provider + " Заказ не выгружен: не заданы имя пользователя / пароль / хост / порт", "Экспорт"));
            }

        } catch (ScriptingErrorLog.SemanticErrorException | IOException | JDOMException e) {
            ServerLoggers.importLogger.error(provider + " error: ", e);
            context.delayUserInteraction(new MessageClientAction(provider + " error: " + e.getMessage(), "Ошибка"));
        }
    }
}