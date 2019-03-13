package lsfusion.erp.region.by.integration.edi.edn;

import com.google.common.base.Throwables;
import lsfusion.erp.region.by.integration.edi.ReceiveMessagesActionProperty;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.IOException;
import java.sql.SQLException;

public class ReceiveInvoiceMessagesEDNActionProperty extends ReceiveMessagesActionProperty {
    String provider = "EDN";

    public ReceiveInvoiceMessagesEDNActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            String login = (String) findProperty("loginInvoiceEDN[]").read(context);
            String password = (String) findProperty("passwordInvoiceEDN[]").read(context);
            String host = (String) findProperty("hostInvoiceEDN[]").read(context);
            Integer port = (Integer) findProperty("portInvoiceEDN[]").read(context);
            String archiveDir = (String) findProperty("archiveDirEDN[]").read(context);
            boolean disableConfirmation = findProperty("disableConfirmationEDN[]").read(context) != null;
            boolean receiveSupplierMessages = findProperty("receiveSupplierMessagesEDN[]").read(context) != null;

            String hostEDSService = (String) findProperty("hostEDSServiceEDN[]").read(context);
            Integer portEDSService = (Integer) findProperty("portEDSServiceEDN[]").read(context);

            if (login != null && password != null && host != null && port != null) {
                String url = String.format("https://%s:%s/topby/DmcService?wsdl", host, port);
                receiveMessages(context, url, login, password, host, port, hostEDSService, portEDSService,
                        provider, archiveDir, disableConfirmation, receiveSupplierMessages, true, true);
            } else {
                ServerLoggers.importLogger.info(provider + " ReceiveMessages: не заданы имя пользователя / пароль / хост / порт");
                context.delayUserInteraction(new MessageClientAction(provider + " сообщения не получены: не заданы имя пользователя / пароль / хост / порт", "Экспорт"));
            }

        } catch (ScriptingErrorLog.SemanticErrorException | IOException e) {
            throw Throwables.propagate(e);
        }
    }
}