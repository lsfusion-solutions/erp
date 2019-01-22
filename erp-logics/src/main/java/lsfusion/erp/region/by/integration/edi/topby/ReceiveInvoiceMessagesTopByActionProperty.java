package lsfusion.erp.region.by.integration.edi.topby;

import com.google.common.base.Throwables;
import lsfusion.erp.region.by.integration.edi.ReceiveMessagesActionProperty;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.IOException;
import java.sql.SQLException;

public class ReceiveInvoiceMessagesTopByActionProperty extends ReceiveMessagesActionProperty {
    String provider = "TopBy";

    public ReceiveInvoiceMessagesTopByActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            String login = (String) findProperty("loginInvoiceTopBy[]").read(context);
            String password = (String) findProperty("passwordInvoiceTopBy[]").read(context);
            String host = (String) findProperty("hostInvoiceTopBy[]").read(context);
            Integer port = (Integer) findProperty("portInvoiceTopBy[]").read(context);
            String archiveDir = (String) findProperty("archiveDirTopBy[]").read(context);
            boolean disableConfirmation = findProperty("disableConfirmationTopBy[]").read(context) != null;
            boolean receiveSupplierMessages = findProperty("receiveSupplierMessagesTopBy[]").read(context) != null;

            String hostEDSService = (String) findProperty("hostEDSServiceTopBy[]").read(context);
            Integer portEDSService = (Integer) findProperty("portEDSServiceTopBy[]").read(context);

            if (login != null && password != null && host != null && port != null) {
                String url = String.format("http://%s:%s/DmcService", host, port);
                receiveMessages(context, url, login, password, host, port, hostEDSService, portEDSService,
                        provider, archiveDir, disableConfirmation, receiveSupplierMessages, false, true);
            } else {
                ServerLoggers.importLogger.info(provider + " ReceiveMessages: не заданы имя пользователя / пароль / хост / порт");
                context.delayUserInteraction(new MessageClientAction(provider + " cообщения не получены: не заданы имя пользователя / пароль / хост / порт", "Экспорт"));
            }

        } catch (ScriptingErrorLog.SemanticErrorException | IOException e) {
            throw Throwables.propagate(e);
        }
    }
}