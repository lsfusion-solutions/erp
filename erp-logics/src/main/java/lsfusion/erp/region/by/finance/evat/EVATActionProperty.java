package lsfusion.erp.region.by.finance.evat;

import lsfusion.base.ExceptionUtils;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ConcreteClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class EVATActionProperty extends GenerateXMLEVATActionProperty {

    private final ClassPropertyInterface typeInterface;

    public EVATActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        typeInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            ServerLoggers.importLogger.info("EVAT: action started");
            Integer type = (Integer) context.getDataKeyValue(typeInterface).getValue();
            if (type != null) {
                String serviceUrl = (String) findProperty("serviceUrlEVAT[]").read(context);
                String pathEVAT = (String) findProperty("pathEVAT[]").read(context);
                String exportPathEVAT = (String) findProperty("exportPathEVAT[]").read(context);
                String passwordEVAT = (String) findProperty("passwordEVAT[]").read(context);
                if (serviceUrl != null) {
                    if (pathEVAT != null) {
                        if (passwordEVAT != null) {
                            switch (type) {
                                case 0:
                                    ServerLoggers.importLogger.info("EVAT: sendAndSign called");
                                    sendAndSign(serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, type, context);
                                    break;
                                case 1:
                                    ServerLoggers.importLogger.info("EVAT: getStatus called");
                                    getStatus(serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, type, context);
                                    break;
                                case 2:
                                    listAndGet(serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, type, context);
                                    break;
                            }
                        } else {
                            context.delayUserInteraction(new MessageClientAction("Не указан пароль", "Ошибка"));
                        }
                    } else {
                        context.delayUserInteraction(new MessageClientAction("Не указан путь к jar и xsd", "Ошибка"));
                    }
                } else {
                    context.delayUserInteraction(new MessageClientAction("Не указан адрес WSDL", "Ошибка"));
                }
            }
        } catch (Exception e) {
            throw ExceptionUtils.propagate(e, SQLException.class, SQLHandledException.class);
        }
    }

    private void sendAndSign(String serviceUrl, String pathEVAT, String exportPathEVAT, String passwordEVAT, Integer type, ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        ServerLoggers.importLogger.info("EVAT: generateXMLs started");
        Map<String, Map<Integer, byte[]>> files = generateXMLs(context);
        if (!(files.isEmpty())) {
            ServerLoggers.importLogger.info("EVAT: client action started");
            List<List<Object>> result = (List<List<Object>>) context.requestUserInteraction(new EVATClientAction(files, null, serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, type));
            ServerLoggers.importLogger.info("EVAT: client action finished");
            String error = "";
            if (!result.isEmpty()) {
                for (List<Object> entry : result) {
                    ServerLoggers.importLogger.info("EVAT: reading result started");
                    Integer evat = (Integer) entry.get(0);
                    String message = (String) entry.get(1);
                    Boolean isError = (Boolean) entry.get(2);
                    if(isError)
                        error += message + "\n";
                    try(DataSession session = context.createSession()) {
                        findProperty("result[EVAT]").change(message, session, new DataObject(evat, (ConcreteClass) findClass("EVAT")));
                        String applyResult = session.applyMessage(context);
                        if(applyResult != null)
                            ServerLoggers.importLogger.info("EVAT: apply result: " + applyResult);
                    }
                    ServerLoggers.importLogger.info("EVAT: reading result finished");
                }
            }
            if (error.isEmpty())
                context.delayUserInteraction(new MessageClientAction("Выгрузка завершена успешно", "EVAT"));
            else
                context.delayUserInteraction(new MessageClientAction(error, "Ошибка"));
        } else {
            context.delayUserInteraction(new MessageClientAction("Не выбрано ни одного ЭСЧФ", "Ошибка"));
        }
    }

    private void getStatus(String serviceUrl, String pathEVAT, String exportPathEVAT, String passwordEVAT, Integer type, ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        Map<String, Map<Integer, String>> invoices = getInvoices(context);
        if (!(invoices.isEmpty())) {
            ServerLoggers.importLogger.info("EVAT: client action started");
            List<List<Object>> result = (List<List<Object>>) context.requestUserInteraction(new EVATClientAction(null, invoices, serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, type));
            ServerLoggers.importLogger.info("EVAT: client action finished");
            String resultMessage = "";
            if (!result.isEmpty()) {
                for (List<Object> entry : result) {
                    Integer evat = (Integer) entry.get(0);
                    String message = (String) entry.get(1);
                    ServerLoggers.importLogger.info("EVAT: reading result started");
                    resultMessage += message + "\n";
                    try(DataSession session = context.createSession()) {
                        findProperty("serverStatus[EVAT]").change(message, session, new DataObject(evat, (ConcreteClass) findClass("EVAT")));
                        String applyResult = session.applyMessage(context);
                        if(applyResult != null)
                            ServerLoggers.importLogger.info("EVAT: apply result: " + applyResult);
                    }
                }
            }
            context.delayUserInteraction(new MessageClientAction(resultMessage, "EVAT"));
        } else {
            context.delayUserInteraction(new MessageClientAction("Не выбрано ни одного ЭСЧФ", "Ошибка"));
        }
    }

    private void listAndGet(String serviceUrl, String pathEVAT, String exportPathEVAT, String passwordEVAT, Integer type, ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        String result = (String) context.requestUserInteraction(new EVATClientAction(serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, type));
        if(result != null)
            context.delayUserInteraction(new MessageClientAction(result, "Ошибка"));
        else
            context.delayUserInteraction(new MessageClientAction("ЭСЧФ загружены в папку in", "EVAT"));
    }
}