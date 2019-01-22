package lsfusion.erp.region.by.integration.edi.edn;

import lsfusion.erp.region.by.integration.edi.SendEInvoiceCustomerActionProperty;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.jdom.JDOMException;

import java.io.IOException;
import java.sql.SQLException;

public class SendEInvoiceCustomerEDNActionProperty extends SendEInvoiceCustomerActionProperty {
    String provider = "EDN";

    public SendEInvoiceCustomerEDNActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            String login = (String) findProperty("loginInvoiceEDN[]").read(context);
            String password = (String) findProperty("passwordInvoiceEDN[]").read(context);
            String host = (String) findProperty("hostInvoiceEDN[]").read(context);
            Integer port = (Integer) findProperty("portInvoiceEDN[]").read(context);
            String archiveDir = (String) findProperty("archiveDirEDN[]").read(context);

            String hostEDSService = (String) findProperty("hostEDSServiceEDN[]").read(context);
            Integer portEDSService = (Integer) findProperty("portEDSServiceEDN[]").read(context);
            boolean useEDSServiceForCustomer = findProperty("useEDSServiceForCustomerEDN[]").read(context) != null;

            if (login != null && password != null && host != null && port != null) {
                String url = String.format("https://%s:%s/topby/DmcService?wsdl", host, port);
                sendEInvoice(context, url, login, password, host, port, hostEDSService, portEDSService, useEDSServiceForCustomer, archiveDir, provider);
            } else {
                ServerLoggers.importLogger.info(provider + " SendEInvoice: не заданы имя пользователя / пароль / хост / порт");
                context.delayUserInteraction(new MessageClientAction(provider + " Заказ не выгружен: не заданы имя пользователя / пароль / хост / порт", "Экспорт"));
            }

        } catch (ScriptingErrorLog.SemanticErrorException | IOException | JDOMException e) {
            ServerLoggers.importLogger.error(provider + " error: ", e);
            context.delayUserInteraction(new MessageClientAction(provider + " error: " + e.getMessage(), "Ошибка"));
        }
    }
}