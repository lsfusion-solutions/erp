package lsfusion.erp.region.by.integration.edi.edn;

import lsfusion.erp.region.by.integration.edi.SendEInvoiceSupplierActionProperty;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class SendEInvoiceSupplierEDNActionProperty extends SendEInvoiceSupplierActionProperty {
    String provider = "EDN";

    public SendEInvoiceSupplierEDNActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            String login = (String) findProperty("loginInvoiceEDN[]").read(context);
            String password = (String) findProperty("passwordInvoiceEDN[]").read(context);
            String host = (String) findProperty("hostInvoiceEDN[]").read(context);
            Integer port = (Integer) findProperty("portInvoiceEDN[]").read(context);
            String outputDir = (String) findProperty("outputDirEDN[]").read(context);

            String hostEDSService = (String) findProperty("hostEDSServiceEDN[]").read(context);
            Integer portEDSService = (Integer) findProperty("portEDSServiceEDN[]").read(context);

            if (login != null && password != null && host != null && port != null) {
                String url = String.format("https://%s:%s/topby/DmcService?wsdl", host, port);
                sendEInvoice(context, url, login, password, host, port, provider, outputDir, hostEDSService, portEDSService);
            } else {
                ServerLoggers.importLogger.info(provider + " SendEInvoice: не заданы имя пользователя / пароль / хост / порт");
                context.delayUserInteraction(new MessageClientAction(provider + " Заказ не выгружен: не заданы имя пользователя / пароль / хост / порт", "Экспорт"));
            }

        } catch (ScriptingErrorLog.SemanticErrorException e) {
            ServerLoggers.importLogger.error(provider + " error: ", e);
            context.delayUserInteraction(new MessageClientAction(provider + " error: " + e.getMessage(), "Ошибка"));
        }
    }
}