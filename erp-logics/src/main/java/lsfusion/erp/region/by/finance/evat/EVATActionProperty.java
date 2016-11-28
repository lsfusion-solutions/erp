package lsfusion.erp.region.by.finance.evat;

import lsfusion.base.ExceptionUtils;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ConcreteClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;
import java.util.*;

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
                Integer certIndex = (Integer) findProperty("certIndexEVAT[]").read(context);
                if(certIndex == null)
                    certIndex = 0;
                if (serviceUrl != null) {
                    if (pathEVAT != null) {
                        if (passwordEVAT != null) {
                            switch (type) {
                                case 0:
                                    ServerLoggers.importLogger.info("EVAT: sendAndSign called");
                                    sendAndSign(serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, certIndex, type, context);
                                    break;
                                case 1:
                                    ServerLoggers.importLogger.info("EVAT: getStatus called");
                                    getStatus(serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, certIndex, type, context);
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

    private void sendAndSign(String serviceUrl, String pathEVAT, String exportPathEVAT, String passwordEVAT, Integer certIndex, Integer type, ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        ServerLoggers.importLogger.info("EVAT: generateXMLs started");
        Map<String, Map<Integer, byte[]>> files = generateXMLs(context);
        if (!(files.isEmpty())) {
            Object evatResult = context.requestUserInteraction(new EVATClientAction(files, null, serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, certIndex, type));
            String error = "";
            if(evatResult instanceof List) {
                List<List<Object>> result = (List<List<Object>>) evatResult;
                if (!result.isEmpty()) {
                    for (List<Object> entry : result) {
                        ServerLoggers.importLogger.info("EVAT: reading result started");
                        Integer evat = (Integer) entry.get(0);
                        String message = (String) entry.get(1);
                        Boolean isError = (Boolean) entry.get(2);
                        if (isError)
                            error += message + "\n";
                        try (DataSession session = context.createSession()) {
                            findProperty("result[EVAT]").change(message, session, new DataObject(evat, (ConcreteClass) findClass("EVAT")));
                            findProperty("exported[EVAT]").change(isError ? null : true, session, new DataObject(evat, (ConcreteClass) findClass("EVAT")));
                            String applyResult = session.applyMessage(context);
                            if (applyResult != null)
                                ServerLoggers.importLogger.info("EVAT: apply result: " + applyResult);
                        }
                        ServerLoggers.importLogger.info("EVAT: reading result finished");
                    }
                }
            } else {
                error = (String) evatResult;
            }
            if (error.isEmpty())
                context.delayUserInteraction(new MessageClientAction("Выгрузка завершена успешно", "EVAT"));
            else
                context.delayUserInteraction(new MessageClientAction(error, "Ошибка"));
        } else {
            context.delayUserInteraction(new MessageClientAction("Не выбрано ни одного ЭСЧФ", "Ошибка"));
        }
    }

    private void getStatus(String serviceUrl, String pathEVAT, String exportPathEVAT, String passwordEVAT, Integer certIndex, Integer type, ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        Map<String, Map<Integer, String>> invoices = getInvoices(context);
        if (!(invoices.isEmpty())) {
            Object evatResult = context.requestUserInteraction(new EVATClientAction(null, invoices, serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, certIndex, type));
            if(evatResult instanceof List) {
                List<List<Object>> result = (List<List<Object>>) evatResult;
                String resultMessage = "";
                if (!result.isEmpty()) {
                    for (List<Object> entry : result) {
                        Integer evat = (Integer) entry.get(0);
                        String message = (String) entry.get(1);
                        String status = (String) entry.get(2);
                        ServerLoggers.importLogger.info(String.format("EVAT %s: reading result started", evat));
                        resultMessage += String.format("EVAT %s: %s\n", evat, message);
                        try (DataSession session = context.createSession()) {
                            DataObject evatObject = new DataObject(evat, (ConcreteClass) findClass("EVAT"));
                            findProperty("statusServerStatus[EVAT]").change(getServerStatusObject(session, status, evat).getValue(), session, evatObject);
                            findProperty("result[EVAT]").change(message, session, evatObject);
                            String applyResult = session.applyMessage(context);
                            if (applyResult != null)
                                resultMessage += String.format("EVAT %s: %s\n", applyResult, message);
                        }
                    }
                }
                context.delayUserInteraction(new MessageClientAction(resultMessage, "EVAT"));
            } else {
                context.delayUserInteraction(new MessageClientAction((String) evatResult, "Ошибка"));
            }
        } else {
            context.delayUserInteraction(new MessageClientAction("Не выбрано ни одного ЭСЧФ", "Ошибка"));
        }
    }

    private ObjectValue getServerStatusObject(DataSession session, String value, Integer evat) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ObjectValue serverStatusObject = null;
        if(value != null) {
            String id = null;
            switch (value) {
                case "NOT_FOUND":
                    id = "notFound";
                    break;
                case "DENIED":
                    id = "denied";
                    break;
                case "COMPLETED":
                    id = "completed";
                    break;
                case "COMPLETED_SIGNED":
                    id = "completedSigned";
                    break;
                case "IN_PROGRESS":
                    id = "inProgress";
                    break;
                case "CANCELLED":
                    id = "cancelled";
                    break;
                case "ERROR":
                    id = "error";
                    break;
                default:
                    ServerLoggers.importLogger.info(String.format("EVAT %s: unknown status: %s", evat, value));
            }
            serverStatusObject = id == null ? null : findProperty("nameStatic").readClasses(session, new DataObject("EVAT_EVATServerStatus." + id));
        }
        return serverStatusObject;
    }
}