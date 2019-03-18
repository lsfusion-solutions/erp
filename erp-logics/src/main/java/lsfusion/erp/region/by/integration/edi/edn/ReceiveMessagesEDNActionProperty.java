package lsfusion.erp.region.by.integration.edi.edn;

import com.google.common.base.Throwables;
import lsfusion.erp.region.by.integration.edi.ReceiveMessagesActionProperty;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.IOException;
import java.sql.SQLException;

public class ReceiveMessagesEDNActionProperty extends ReceiveMessagesActionProperty {
    String provider = "EDN";

    public ReceiveMessagesEDNActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            String login = (String) findProperty("loginEDN[]").read(context);
            String password = (String) findProperty("passwordEDN[]").read(context);
            String host = (String) findProperty("hostEDN[]").read(context);
            Integer port = (Integer) findProperty("portEDN[]").read(context);
            String archiveDir = (String) findProperty("archiveDirEDN[]").read(context);
            boolean disableConfirmation = findProperty("disableConfirmationEDN[]").read(context) != null;
            boolean receiveSupplierMessages = findProperty("receiveSupplierMessagesEDN[]").read(context) != null;
            if (login != null && password != null && host != null && port != null) {
                String url = String.format("https://%s:%s/topby/DmcService?wsdl", host, port);
                receiveMessages(context, url, login, password, host, port, provider, archiveDir, disableConfirmation, receiveSupplierMessages, true, false);
            } else {
                ServerLoggers.importLogger.info(provider + " ReceiveMessages: не заданы имя пользователя / пароль / хост / порт");
                context.delayUserInteraction(new MessageClientAction(provider + " сообщения не получены: не заданы имя пользователя / пароль / хост / порт", "Экспорт"));
            }

        } catch (ScriptingErrorLog.SemanticErrorException | IOException e) {
            throw Throwables.propagate(e);
        }
    }
}