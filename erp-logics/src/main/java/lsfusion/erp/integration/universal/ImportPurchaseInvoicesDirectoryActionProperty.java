package lsfusion.erp.integration.universal;

import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.action.MessageClientAction;
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

import java.io.File;
import java.sql.SQLException;

public class ImportPurchaseInvoicesDirectoryActionProperty extends ImportDocumentActionProperty {

    public ImportPurchaseInvoicesDirectoryActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataSession session = context.getSession();

            LCP<PropertyInterface> isImportType = (LCP<PropertyInterface>) LM.is(getClass("ImportType"));
            ImRevMap<PropertyInterface, KeyExpr> importTypeKeys = isImportType.getMapKeys();
            KeyExpr importTypeKey = importTypeKeys.singleValue();
            QueryBuilder<PropertyInterface, Object> importTypeQuery = new QueryBuilder<PropertyInterface, Object>(importTypeKeys);
            importTypeQuery.addProperty("autoImportDirectoryImportType", getLCP("autoImportDirectoryImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionFileExtensionImportType", getLCP("captionFileExtensionImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("startRowImportType", getLCP("startRowImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("isPostedImportType", getLCP("isPostedImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("separatorImportType", getLCP("separatorImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionPrimaryKeyTypeImportType", getLCP("captionPrimaryKeyTypeImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionSecondaryKeyTypeImportType", getLCP("captionSecondaryKeyTypeImportType").getExpr(session.getModifier(), importTypeKey));

            importTypeQuery.and(isImportType.getExpr(importTypeKey).getWhere());
            importTypeQuery.and(getLCP("autoImportImportType").getExpr(importTypeKey).getWhere());
            importTypeQuery.and(getLCP("autoImportDirectoryImportType").getExpr(importTypeKey).getWhere());
            ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> importTypeResult = importTypeQuery.executeClasses(session);

            for (int i = 0, size = importTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importTypeResult.getValue(i);

                DataObject importTypeObject = importTypeResult.getKey(i).valueIt().iterator().next();

                String directory = trim((String) entryValue.get("autoImportDirectoryImportType").getValue());
                String fileExtension = trim((String) entryValue.get("captionFileExtensionImportType").getValue());
                Integer startRow = (Integer) entryValue.get("startRowImportType").getValue();
                startRow = startRow == null ? 1 : startRow;
                Boolean isPosted = (Boolean) entryValue.get("isPostedImportType").getValue();
                String csvSeparator = trim((String) getLCP("separatorImportType").read(session, importTypeObject));
                csvSeparator = csvSeparator == null ? ";" : csvSeparator;
                String primaryKeyType = parseKeyType((String) getLCP("namePrimaryKeyTypeImportType").read(session, importTypeObject));
                boolean checkExistence = getLCP("checkExistencePrimaryKeyImportType").read(session, importTypeObject) != null;
                String secondaryKeyType = parseKeyType((String) getLCP("nameSecondaryKeyTypeImportType").read(session, importTypeObject));
                boolean keyIsDigit = getLCP("keyIsDigitImportType").read(session, importTypeObject) != null;

                if (directory != null && fileExtension != null) {
                    File dir = new File(directory);

                    if (dir.exists()) {
                        File[] listFiles = dir.listFiles();
                        if (listFiles != null) {
                            for (File f : listFiles) {
                                if (f.getName().toLowerCase().endsWith(fileExtension.toLowerCase())) {
                                    DataSession currentSession = context.createSession();
                                    DataObject invoiceObject = currentSession.addObject((ConcreteCustomClass) getClass("Purchase.UserInvoice"));

                                    try {

                                        boolean importResult = new ImportPurchaseInvoiceActionProperty(LM).makeImport(context, currentSession, invoiceObject,
                                                importTypeObject, IOUtils.getFileBytes(f), fileExtension, startRow, isPosted, csvSeparator, primaryKeyType,
                                                checkExistence, secondaryKeyType, keyIsDigit);

                                        if (importResult)
                                            renameImportedFile(context, f.getAbsolutePath(), "." + fileExtension);

                                    } catch (Exception e) {
                                        ServerLoggers.systemLogger.error(e);
                                    }
                                    
                                    currentSession.apply(context);
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

    protected void renameImportedFile(ExecutionContext context, String oldPath, String extension) {
        File importedFile = new File(oldPath);
        String newExtensionUpCase = extension.substring(0, extension.length() - 1) + "E";
        String newExtensionLowCase = extension.toLowerCase().substring(0, extension.length() - 1) + "e";
        if (importedFile.isFile()) {
            File renamedFile = oldPath.endsWith(extension) ? new File(oldPath.replace(extension, newExtensionUpCase)) :
                    (oldPath.endsWith(extension.toLowerCase()) ? new File(oldPath.replace(extension.toLowerCase(), newExtensionLowCase)) : null);
            if (renamedFile != null && !importedFile.renameTo(renamedFile))
                context.requestUserInteraction(new MessageClientAction("Ошибка при переименовании импортированного файла " + oldPath, "Ошибка"));
        }
    }
}