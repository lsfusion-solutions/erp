package lsfusion.erp.integration.universal.purchaseinvoice;

import jxl.read.biff.BiffException;
import lsfusion.base.file.RawFileData;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.ERPLoggers;
import lsfusion.erp.integration.universal.ImportDocumentActionProperty;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.erp.integration.universal.UniversalImportException;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.oraction.PropertyInterface;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.session.DataSession;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

public class ImportPurchaseInvoicesDirectoryAction extends ImportDocumentActionProperty {

    public ImportPurchaseInvoicesDirectoryAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public int makeImport(ExecutionContext.NewSession<ClassPropertyInterface> newContext, DataObject invoiceObject, DataObject importTypeObject, File f, String fileExtension,
                          ImportDocumentSettings settings, String staticNameImportType, String staticCaptionImportType, boolean completeIdItemAsEAN) throws ScriptingErrorLog.SemanticErrorException, IOException, ParseException, UniversalImportException, SQLHandledException, SQLException, BiffException, xBaseJException {
        return new ImportPurchaseInvoiceAction(LM).makeImport(newContext, invoiceObject,
                importTypeObject, new RawFileData(f), fileExtension, settings, staticNameImportType, staticCaptionImportType,
                completeIdItemAsEAN, false, false);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeInternal(context);
        try {

            DataSession session = context.getSession();

            LP<PropertyInterface> isImportType = (LP<PropertyInterface>) is(findClass("ImportType"));
            ImRevMap<PropertyInterface, KeyExpr> importTypeKeys = isImportType.getMapKeys();
            KeyExpr importTypeKey = importTypeKeys.singleValue();
            QueryBuilder<PropertyInterface, Object> importTypeQuery = new QueryBuilder<>(importTypeKeys);
            importTypeQuery.addProperty("autoImportDirectoryImportType", findProperty("autoImportDirectory[ImportType]").getExpr(session.getModifier(), importTypeKey));

            importTypeQuery.and(isImportType.getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImport[ImportType]").getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImportDirectory[ImportType]").getExpr(importTypeKey).getWhere());
            ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> importTypeResult = importTypeQuery.executeClasses(session);

            for (int i = 0, size = importTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importTypeResult.getValue(i);

                DataObject importTypeObject = importTypeResult.getKey(i).valueIt().iterator().next();

                String directory = trim((String) entryValue.get("autoImportDirectoryImportType").getValue());
                String staticNameImportType = (String) findProperty("staticNameImportTypeDetail[ImportType]").read(session, importTypeObject);
                String staticCaptionImportType = (String) findProperty("staticCaptionImportTypeDetail[ImportType]").read(session, importTypeObject);
                boolean completeIdItemAsEAN = findProperty("completeIdItemAsEAN[ImportType]").read(session, importTypeObject) != null;

                ImportDocumentSettings settings = readImportDocumentSettings(session, importTypeObject);
                String fileExtension = settings.getFileExtension();
                boolean multipleDocuments = settings.isMultipleDocuments();

                if (directory != null && fileExtension != null) {
                    File dir = new File(directory);

                    if (dir.exists()) {
                        File[] listFiles = dir.listFiles();
                        if (listFiles != null) {
                            for (File f : listFiles) {
                                if (f.getName().toLowerCase().endsWith(fileExtension.toLowerCase())) {
                                    try (ExecutionContext.NewSession<ClassPropertyInterface> newContext = context.newSession()) {
                                        DataObject invoiceObject = multipleDocuments ? null : newContext.addObject((ConcreteCustomClass) findClass("Purchase.UserInvoice"));
                                        try {

                                            findAction("executeLocalEvents[TEXT]").execute(newContext, new DataObject("Purchase.UserInvoice"));

                                            int importResult = makeImport(newContext, invoiceObject, importTypeObject, f, fileExtension, settings, staticNameImportType, staticCaptionImportType, completeIdItemAsEAN);

                                            if (importResult != IMPORT_RESULT_ERROR)
                                                renameImportedFile(context, f.getAbsolutePath(), "." + fileExtension);

                                        } catch (Exception e) {
                                            ERPLoggers.importLogger.error("ImportPurchaseInvoices Error: ", e);
                                        }

                                        newContext.apply();
                                    }
                                }
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