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
import java.util.Map;

public class ImportSaleOrdersActionProperty extends ImportDocumentActionProperty {

    public ImportSaleOrdersActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
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
            importTypeQuery.addProperty("autoImportDirectoryImportType", findProperty("autoImportDirectoryImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionFileExtensionImportType", findProperty("captionFileExtensionImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("startRowImportType", findProperty("startRowImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("isPostedImportType", findProperty("isPostedImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("separatorImportType", findProperty("separatorImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionPrimaryKeyTypeImportType", findProperty("captionPrimaryKeyTypeImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionSecondaryKeyTypeImportType", findProperty("captionSecondaryKeyTypeImportType").getExpr(session.getModifier(), importTypeKey));

            importTypeQuery.addProperty("autoImportSupplierImportType", findProperty("autoImportSupplierImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportSupplierStockImportType", findProperty("autoImportSupplierStockImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportCustomerImportType", findProperty("autoImportCustomerImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportCustomerStockImportType", findProperty("autoImportCustomerStockImportType").getExpr(session.getModifier(), importTypeKey));

            importTypeQuery.and(isImportType.getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImportImportType").getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImportDirectoryImportType").getExpr(importTypeKey).getWhere());
            ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> importTypeResult = importTypeQuery.executeClasses(session);

            for (int i = 0, size = importTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importTypeResult.getValue(i);

                DataObject importTypeObject = importTypeResult.getKey(i).valueIt().iterator().next();

                String directory = trim((String) entryValue.get("autoImportDirectoryImportType").getValue());
                String fileExtension = trim((String) entryValue.get("captionFileExtensionImportType").getValue());
                Integer startRow = (Integer) entryValue.get("startRowImportType").getValue();
                startRow = startRow == null ? 1 : startRow;
                Boolean isPosted = (Boolean) entryValue.get("isPostedImportType").getValue();
                String csvSeparator = trim((String) findProperty("separatorImportType").read(session, importTypeObject));
                csvSeparator = csvSeparator == null ? ";" : csvSeparator;
                String primaryKeyType = parseKeyType((String) findProperty("namePrimaryKeyTypeImportType").read(session, importTypeObject));
                boolean checkExistence = findProperty("checkExistencePrimaryKeyImportType").read(session, importTypeObject) != null;
                String secondaryKeyType = parseKeyType((String) findProperty("nameSecondaryKeyTypeImportType").read(session, importTypeObject));
                boolean keyIsDigit = findProperty("keyIsDigitImportType").read(session, importTypeObject) != null;
                
                ObjectValue operationObject = findProperty("autoImportOperationImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierObject = entryValue.get("autoImportSupplierImportType");
                ObjectValue supplierStockObject = entryValue.get("autoImportSupplierStockImportType");
                ObjectValue customerObject = entryValue.get("autoImportCustomerImportType");
                ObjectValue customerStockObject = entryValue.get("autoImportCustomerStockImportType");

                Map<String, ImportColumnDetail> importColumns = readImportColumns(session, importTypeObject).get(0);

                if (directory != null && fileExtension != null) {
                    File dir = new File(directory);

                    if (dir.exists()) {

                        for (File f : dir.listFiles()) {
                            if (f.getName().toLowerCase().endsWith(fileExtension.toLowerCase())) {
                                DataSession currentSession = context.createSession();
                                DataObject orderObject = currentSession.addObject((ConcreteCustomClass) findClass("Sale.UserOrder"));

                                try {

                                    boolean importResult = new ImportSaleOrderActionProperty(LM).makeImport(context.getBL(), currentSession, orderObject,
                                            importColumns, IOUtils.getFileBytes(f), fileExtension, startRow, isPosted, 
                                            csvSeparator, primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, operationObject, supplierObject,
                                            supplierStockObject, customerObject, customerStockObject);                                                                                                        

                                    if (importResult)
                                        renameImportedFile(context, f.getAbsolutePath(), "." + fileExtension);

                                } catch (Exception e) {
                                    ServerLoggers.systemLogger.error(e);
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