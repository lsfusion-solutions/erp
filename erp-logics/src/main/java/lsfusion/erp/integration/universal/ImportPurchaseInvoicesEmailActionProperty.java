package lsfusion.erp.integration.universal;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;

public class ImportPurchaseInvoicesEmailActionProperty extends ImportDocumentActionProperty {

    public ImportPurchaseInvoicesEmailActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataSession session = context.getSession();

            LCP<PropertyInterface> isImportType = (LCP<PropertyInterface>) is(findClass("ImportType"));
            ImRevMap<PropertyInterface, KeyExpr> importTypeKeys = isImportType.getMapKeys();
            KeyExpr importTypeKey = importTypeKeys.singleValue();
            QueryBuilder<PropertyInterface, Object> importTypeQuery = new QueryBuilder<PropertyInterface, Object>(importTypeKeys);
            importTypeQuery.addProperty("autoImportEmailImportType", findProperty("autoImportEmailImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportAccountImportType", findProperty("autoImportAccountImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportCheckInvoiceExistenceImportType", findProperty("autoImportCheckInvoiceExistenceImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionFileExtensionImportType", findProperty("captionFileExtensionImportType").getExpr(session.getModifier(), importTypeKey));

            importTypeQuery.and(isImportType.getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImportImportType").getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImportEmailImportType").getExpr(importTypeKey).getWhere());
            ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> importTypeResult = importTypeQuery.executeClasses(session);

            for (int i = 0, size = importTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importTypeResult.getValue(i);

                DataObject importTypeObject = importTypeResult.getKey(i).valueIt().iterator().next();

                ObjectValue accountObject = entryValue.get("autoImportAccountImportType");
                ObjectValue emailObject = entryValue.get("autoImportEmailImportType");
                boolean checkInvoiceExistence = entryValue.get("autoImportCheckInvoiceExistenceImportType") instanceof DataObject;
                String emailPattern = emailObject instanceof DataObject ? ((String) ((DataObject) emailObject).object).replace("*", ".*") : null;
                String fileExtension = trim((String) entryValue.get("captionFileExtensionImportType").getValue());
                String staticNameImportType = (String) findProperty("staticNameImportTypeDetailImportType").read(session, importTypeObject);

                ImportDocumentSettings importDocumentSettings = readImportDocumentSettings(session, importTypeObject);

                if (fileExtension != null && emailObject instanceof DataObject && accountObject instanceof DataObject) {

                    KeyExpr emailExpr = new KeyExpr("email");
                    KeyExpr attachmentEmailExpr = new KeyExpr("attachmentEmail");
                    ImRevMap<Object, KeyExpr> emailKeys = MapFact.toRevMap((Object) "email", emailExpr, "attachmentEmail", attachmentEmailExpr);

                    QueryBuilder<Object, Object> emailQuery = new QueryBuilder<Object, Object>(emailKeys);
                    emailQuery.addProperty("fromAddressEmail", findProperty("fromAddressEmail").getExpr(session.getModifier(), emailExpr));
                    emailQuery.addProperty("dateTimeReceivedEmail", findProperty("dateTimeReceivedEmail").getExpr(session.getModifier(), emailExpr));
                    emailQuery.addProperty("fileAttachmentEmail", findProperty("fileAttachmentEmail").getExpr(session.getModifier(), attachmentEmailExpr));

                    emailQuery.and(findProperty("emailAttachmentEmail").getExpr(session.getModifier(), attachmentEmailExpr).compare(emailExpr, Compare.EQUALS));
                    emailQuery.and(findProperty("accountEmail").getExpr(session.getModifier(), emailExpr).compare(accountObject.getExpr(), Compare.EQUALS));
                    emailQuery.and(findProperty("notImportedAttachmentEmail").getExpr(session.getModifier(), attachmentEmailExpr).getWhere());
                    emailQuery.and(findProperty("fileAttachmentEmail").getExpr(session.getModifier(), attachmentEmailExpr).getWhere());

                    ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> emailResult = emailQuery.executeClasses(session);

                    for (int j = 0, sizej = emailResult.size(); j < sizej; j++) {
                        ImMap<Object, ObjectValue> emailEntryValue = emailResult.getValue(j);
                        ObjectValue attachmentEmailObject = emailResult.getKey(j).get("attachmentEmail");
                        Timestamp dateTimeReceivedEmail = (Timestamp) emailEntryValue.get("dateTimeReceivedEmail").getValue();
                        boolean isOld = (Calendar.getInstance().getTime().getTime() - dateTimeReceivedEmail.getTime()) > (24*60*60*1000); //старше 24 часов
                        String fromAddressEmail = (String) emailEntryValue.get("fromAddressEmail").getValue();
                        if (fromAddressEmail != null && emailPattern != null && fromAddressEmail.matches(emailPattern)) {
                            byte[] fileAttachment = BaseUtils.getFile((byte[]) emailEntryValue.get("fileAttachmentEmail").getValue());
                            DataSession currentSession = context.createSession();
                            DataObject invoiceObject = currentSession.addObject((ConcreteCustomClass) findClass("Purchase.UserInvoice"));

                            try {

                                int importResult = new ImportPurchaseInvoiceActionProperty(LM).makeImport(context,
                                        currentSession, invoiceObject, importTypeObject, fileAttachment, fileExtension,
                                        importDocumentSettings, staticNameImportType, checkInvoiceExistence);
                                if(importResult >=IMPORT_RESULT_OK)
                                    currentSession.apply(context);

                                if (importResult >= IMPORT_RESULT_OK || isOld) {
                                    DataSession postImportSession = context.createSession();
                                    findProperty("importedAttachmentEmail").change(true, postImportSession, (DataObject) attachmentEmailObject);
                                    postImportSession.apply(context);
                                }

                            } catch (Exception e) {
                                DataSession postImportSession = context.createSession();
                                findProperty("lastErrorAttachmentEmail").change(e.toString(), postImportSession, (DataObject) attachmentEmailObject);
                                postImportSession.apply(context);
                                ServerLoggers.systemLogger.error(e);
                                
                            }
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}