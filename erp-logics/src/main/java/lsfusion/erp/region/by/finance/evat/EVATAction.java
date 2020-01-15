package lsfusion.erp.region.by.finance.evat;

import lsfusion.base.ExceptionUtils;
import lsfusion.erp.ERPLoggers;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class EVATAction extends GenerateXMLEVATAction {

    private final ClassPropertyInterface typeInterface;

    public EVATAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        typeInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            ERPLoggers.importLogger.info("EVAT: action started");
            Integer type = (Integer) context.getDataKeyValue(typeInterface).getValue();
            if (type != null) {
                String serviceUrl = (String) findProperty("serviceUrlEVAT[]").read(context);
                if(serviceUrl == null)
                    serviceUrl = "https://ws.vat.gov.by:443/InvoicesWS/services/InvoicesPort?wsdl";
                String pathEVAT = (String) findProperty("pathEVAT[]").read(context);
                String exportPathEVAT = (String) findProperty("exportPathEVAT[]").read(context);
                String passwordEVAT = (String) findProperty("passwordEVAT[]").read(context);
                Integer certIndex = (Integer) findProperty("certIndexEVAT[]").read(context);
                if(certIndex == null)
                    certIndex = 0;
                boolean useActiveX = findProperty("useActiveXEVAT[]").read(context) != null;
                if (pathEVAT != null || useActiveX) {
                    if (passwordEVAT != null || useActiveX) {
                        switch (type) {
                            case 0:
                                ERPLoggers.importLogger.info("EVAT: sendAndSign called");
                                sendAndSign(serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, certIndex, useActiveX, type, context);
                                break;
                            case 1:
                                ERPLoggers.importLogger.info("EVAT: getStatus called");
                                getStatus(serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, certIndex, useActiveX, type, context);
                                break;
                        }
                    } else {
                        context.delayUserInteraction(new MessageClientAction("Не указан пароль", "Ошибка"));
                    }
                } else {
                    context.delayUserInteraction(new MessageClientAction("Не указан путь к jar и xsd", "Ошибка"));
                }
            }
        } catch (Exception e) {
            throw ExceptionUtils.propagate(e, SQLException.class, SQLHandledException.class);
        }
    }

    private void sendAndSign(String serviceUrl, String pathEVAT, String exportPathEVAT, String passwordEVAT, Integer certIndex, boolean useActiveX, Integer type, ExecutionContext<ClassPropertyInterface> context) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        ERPLoggers.importLogger.info("EVAT: generateXMLs started");
        Map<String, Map<Long, List<Object>>> files = generateXMLs(context);
        if (!(files.isEmpty())) {
            Object evatResult = context.requestUserInteraction(new EVATClientAction(files, null, serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, certIndex, useActiveX, type));
            String error = "";
            if(evatResult instanceof List) {
                List<List<Object>> result = (List<List<Object>>) evatResult;
                if (!result.isEmpty()) {
                    for (List<Object> entry : result) {
                        ERPLoggers.importLogger.info("EVAT: reading result started");
                        Long evat = (Long) entry.get(0);
                        String message = (String) entry.get(1);
                        Boolean isError = (Boolean) entry.get(2);
                        if (isError)
                            error += message + "\n";
                        try (ExecutionContext.NewSession newContext = context.newSession()) {
                            DataObject evatObject = new DataObject(evat, (ConcreteCustomClass) findClass("EVAT"));
                            findProperty("result[EVAT]").change(message, newContext, evatObject);
                            findProperty("exported[EVAT]").change(isError ? null : true, newContext, evatObject);
                            String applyResult = newContext.applyMessage();
                            if (applyResult != null)
                                ERPLoggers.importLogger.info("EVAT: apply result: " + applyResult);
                        }
                        ERPLoggers.importLogger.info("EVAT: reading result finished");
                    }
                }
            } else {
                error = (String) evatResult;
            }
            if (error.isEmpty())
                context.delayUserInteraction(new MessageClientAction("Выгрузка завершена успешно", "EVAT"));
            else
                context.delayUserInteraction(new MessageClientAction(error, "Ошибка"));
        }
    }

    private void getStatus(String serviceUrl, String pathEVAT, String exportPathEVAT, String passwordEVAT, Integer certIndex, boolean useActiveX, Integer type, ExecutionContext<ClassPropertyInterface> context) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        Map<String, Map<Long, String>> invoices = getInvoices(context);
        if (!(invoices.isEmpty())) {
            ERPLoggers.importLogger.info("EVAT : start checking status " + invoices.keySet());
            Object evatResult = context.requestUserInteraction(new EVATClientAction(null, invoices, serviceUrl, pathEVAT, exportPathEVAT, passwordEVAT, certIndex, useActiveX, type));
            if(evatResult instanceof List) {
                List<List<Object>> result = (List<List<Object>>) evatResult;
                String resultMessage = "";
                if (!result.isEmpty()) {
                    for (List<Object> entry : result) {
                        Long evat = (Long) entry.get(0);
                        String message = (String) entry.get(1);
                        String status = (String) entry.get(2);
                        String number = (String) entry.get(3);
                        ERPLoggers.importLogger.info(String.format("EVAT %s: settings status started", number));
                        resultMessage += String.format("EVAT %s: %s\n", number, message);
                        try (ExecutionContext.NewSession newContext = context.newSession()) {
                            DataObject evatObject = new DataObject(evat, (ConcreteCustomClass) findClass("EVAT"));
                            findProperty("statusServerStatus[EVAT]").change(getServerStatusObject(status, number), newContext, evatObject);
                            findProperty("result[EVAT]").change(message, newContext, evatObject);
                            String applyResult = newContext.applyMessage();
                            if (applyResult != null)
                                resultMessage += String.format("EVAT %s: %s\n", number, applyResult);
                        }
                        ERPLoggers.importLogger.info(String.format("EVAT %s: settings status finished", number));
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

    private ObjectValue getServerStatusObject(String value, String number) throws ScriptingErrorLog.SemanticErrorException {
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
                case "IN_PROGRESS_ERROR":
                    id = "inProgressError";
                    break;
                case "CANCELLED":
                    id = "cancelled";
                    break;
                case "ON_AGREEMENT":
                    id = "onAgreement";
                    break;
                case "ON_AGREEMENT_CANCEL":
                    id = "onAgreementCancel";
                    break;
                case "ERROR":
                    id = "error";
                    break;
                default:
                    ERPLoggers.importLogger.info(String.format("EVAT %s: unknown status: %s", number, value));
            }
            serverStatusObject = id == null ? NullValue.instance : ((ConcreteCustomClass) findClass("EVAT.EVATServerStatus")).getDataObject(id);
        }
        return serverStatusObject;
    }
}