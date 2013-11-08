package lsfusion.erp.integration.universal;

import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.File;
import java.sql.SQLException;
import java.util.Map;

public class ImportSaleOrdersActionProperty extends ImportDocumentActionProperty {

    public ImportSaleOrdersActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {

            LCP<?> isImportType = LM.is(getClass("ImportType"));
            ImRevMap<Object, KeyExpr> importTypeKeys = (ImRevMap<Object, KeyExpr>) isImportType.getMapKeys();
            KeyExpr importTypeKey = importTypeKeys.singleValue();
            QueryBuilder<Object, Object> importTypeQuery = new QueryBuilder<Object, Object>(importTypeKeys);
            importTypeQuery.addProperty("autoImportDirectoryImportType", getLCP("autoImportDirectoryImportType").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionFileExtensionImportType", getLCP("captionFileExtensionImportType").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("startRowImportType", getLCP("startRowImportType").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("separatorImportType", getLCP("separatorImportType").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionPrimaryKeyTypeImportType", getLCP("captionPrimaryKeyTypeImportType").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionSecondaryKeyTypeImportType", getLCP("captionSecondaryKeyTypeImportType").getExpr(context.getModifier(), importTypeKey));

            importTypeQuery.addProperty("autoImportSupplierImportType", getLCP("autoImportSupplierImportType").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportSupplierStockImportType", getLCP("autoImportSupplierStockImportType").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportCustomerImportType", getLCP("autoImportCustomerImportType").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportCustomerStockImportType", getLCP("autoImportCustomerStockImportType").getExpr(context.getModifier(), importTypeKey));

            importTypeQuery.and(isImportType.getExpr(importTypeKey).getWhere());
            importTypeQuery.and(getLCP("autoImportImportType").getExpr(importTypeKey).getWhere());
            importTypeQuery.and(getLCP("autoImportDirectoryImportType").getExpr(importTypeKey).getWhere());
            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> importTypeResult = importTypeQuery.executeClasses(context);

            for (int i = 0, size = importTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importTypeResult.getValue(i);

                DataObject importTypeObject = importTypeResult.getKey(i).valueIt().iterator().next();

                String directory = trim((String) entryValue.get("autoImportDirectoryImportType").getValue());
                String fileExtension = trim((String) entryValue.get("captionFileExtensionImportType").getValue());
                Integer startRow = (Integer) entryValue.get("startRowImportType").getValue();
                startRow = startRow == null ? 1 : startRow;
                String csvSeparator = trim((String) LM.findLCPByCompoundName("separatorImportType").read(context, importTypeObject));
                csvSeparator = csvSeparator == null ? ";" : csvSeparator;
                String primaryKeyType = parseKeyType((String) LM.findLCPByCompoundName("namePrimaryKeyTypeImportType").read(context, importTypeObject));
                String secondaryKeyType = parseKeyType((String) LM.findLCPByCompoundName("nameSecondaryKeyTypeImportType").read(context, importTypeObject));

                ObjectValue operationObject = LM.findLCPByCompoundName("autoImportOperationImportType").readClasses(context, (DataObject) importTypeObject);
                ObjectValue supplierObject = entryValue.get("autoImportSupplierImportType");
                ObjectValue supplierStockObject = entryValue.get("autoImportSupplierStockImportType");
                ObjectValue customerObject = entryValue.get("autoImportCustomerImportType");
                ObjectValue customerStockObject = entryValue.get("autoImportCustomerStockImportType");

                Map<String, String[]> importColumns = ImportSaleOrderActionProperty.readImportColumns(context, LM, importTypeObject);

                if (directory != null && fileExtension != null) {
                    File dir = new File(directory);

                    if (dir.exists()) {

                        for (File f : dir.listFiles()) {
                            if (f.getName().toLowerCase().endsWith(fileExtension.toLowerCase())) {
                                DataObject orderObject = context.addObject((ConcreteCustomClass) LM.findClassByCompoundName("Sale.UserOrder"));

                                try {

                                    boolean importResult = new ImportSaleOrderActionProperty(LM).makeImport(context, orderObject,
                                            importColumns, IOUtils.getFileBytes(f), fileExtension, startRow, csvSeparator,
                                            primaryKeyType, secondaryKeyType, operationObject, supplierObject,
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