package lsfusion.erp.integration.universal.productionorder;

import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.ERPLoggers;
import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.erp.integration.universal.ImportDocumentAction;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.property.oraction.PropertyInterface;

import java.io.File;
import java.sql.SQLException;
import java.util.Map;

public class ImportProductionOrdersAction extends ImportDocumentAction {

    public ImportProductionOrdersAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeInternal(context);
        try {
            
            LP<PropertyInterface> isImportType = (LP<PropertyInterface>) is(findClass("ImportType"));
            ImRevMap<PropertyInterface, KeyExpr> importTypeKeys = isImportType.getMapKeys();
            KeyExpr importTypeKey = importTypeKeys.singleValue();
            QueryBuilder<PropertyInterface, Object> importTypeQuery = new QueryBuilder<>(importTypeKeys);
            importTypeQuery.addProperty("autoImportDirectoryImportType", findProperty("autoImportDirectory[ImportType]").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("startRowImportType", findProperty("startRow[ImportType]").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("isPostedImportType", findProperty("isPosted[ImportType]").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("separatorImportType", findProperty("separator[ImportType]").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionPrimaryKeyTypeImportType", findProperty("captionPrimaryKeyType[ImportType]").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionSecondaryKeyTypeImportType", findProperty("captionSecondaryKeyType[ImportType]").getExpr(context.getModifier(), importTypeKey));

            importTypeQuery.and(isImportType.getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImport[ImportType]").getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImportDirectory[ImportType]").getExpr(importTypeKey).getWhere());
            ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> importTypeResult = importTypeQuery.executeClasses(context);

            for (int i = 0, size = importTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importTypeResult.getValue(i);

                DataObject importTypeObject = importTypeResult.getKey(i).valueIt().iterator().next();

                String directory = trim((String) entryValue.get("autoImportDirectoryImportType").getValue());
                
                ObjectValue operationObject = findProperty("autoImportOperation[ImportType]").readClasses(context, (DataObject) importTypeObject);

                Map<String, ImportColumnDetail> importColumns = readImportColumns(context, importTypeObject).get(0);
                ImportDocumentSettings settings = readImportDocumentSettings(context.getSession(), importTypeObject);
                String fileExtension = settings.getFileExtension();
                
                if (directory != null && fileExtension != null) {
                    File dir = new File(directory);

                    if (dir.exists()) {

                        for (File f : dir.listFiles()) {
                            if (f.getName().toLowerCase().endsWith(fileExtension.toLowerCase())) {
                                try(ExecutionContext.NewSession newContext = context.newSession()) {
                                    try {
                                        boolean importResult = new ImportProductionOrderAction(LM).makeImport(newContext, null,
                                                importColumns, new RawFileData(f), settings, fileExtension, operationObject);

                                        if (importResult)
                                            renameImportedFile(context, f.getAbsolutePath(), "." + fileExtension);

                                    } catch (Exception e) {
                                        ERPLoggers.importLogger.error("ImportProductionOrders Error: ", e);
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