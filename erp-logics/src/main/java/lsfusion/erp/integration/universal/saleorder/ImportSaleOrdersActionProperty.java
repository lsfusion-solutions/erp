package lsfusion.erp.integration.universal.saleorder;

import lsfusion.base.file.RawFileData;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.erp.integration.universal.ImportDocumentActionProperty;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
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

    public ImportSaleOrdersActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeCustom(context);
        try {

            DataSession session = context.getSession();
            
            LCP<PropertyInterface> isImportType = (LCP<PropertyInterface>) is(findClass("ImportType"));
            ImRevMap<PropertyInterface, KeyExpr> importTypeKeys = isImportType.getMapKeys();
            KeyExpr importTypeKey = importTypeKeys.singleValue();
            QueryBuilder<PropertyInterface, Object> importTypeQuery = new QueryBuilder<>(importTypeKeys);
            importTypeQuery.addProperty("autoImportDirectoryImportType", findProperty("autoImportDirectory[ImportType]").getExpr(session.getModifier(), importTypeKey));

            importTypeQuery.addProperty("autoImportSupplierImportType", findProperty("autoImportSupplier[ImportType]").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportSupplierStockImportType", findProperty("autoImportSupplierStock[ImportType]").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportCustomerImportType", findProperty("autoImportCustomer[ImportType]").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportCustomerStockImportType", findProperty("autoImportCustomerStock[ImportType]").getExpr(session.getModifier(), importTypeKey));

            importTypeQuery.and(isImportType.getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImport[ImportType]").getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImportDirectory[ImportType]").getExpr(importTypeKey).getWhere());
            ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> importTypeResult = importTypeQuery.executeClasses(session);

            for (int i = 0, size = importTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importTypeResult.getValue(i);

                DataObject importTypeObject = importTypeResult.getKey(i).valueIt().iterator().next();

                String directory = trim((String) entryValue.get("autoImportDirectoryImportType").getValue());           
                
                ObjectValue operationObject = findProperty("autoImportOperation[ImportType]").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierObject = entryValue.get("autoImportSupplierImportType");
                ObjectValue supplierStockObject = entryValue.get("autoImportSupplierStockImportType");
                ObjectValue customerObject = entryValue.get("autoImportCustomerImportType");
                ObjectValue customerStockObject = entryValue.get("autoImportCustomerStockImportType");

                Map<String, ImportColumnDetail> importColumns = readImportColumns(context, importTypeObject).get(0);
                ImportDocumentSettings settings = readImportDocumentSettings(session, importTypeObject);
                String fileExtension = settings.getFileExtension();

                if (directory != null && fileExtension != null) {
                    File dir = new File(directory);

                    if (dir.exists()) {
                        File[] files = dir.listFiles();
                        if(files != null) {
                            for (File f : files) {
                                if (f.getName().toLowerCase().endsWith(fileExtension.toLowerCase())) {
                                    try (ExecutionContext.NewSession newContext = context.newSession()) {
                                        DataObject orderObject = newContext.addObject((ConcreteCustomClass) findClass("Sale.UserOrder"));
                                        try {

                                            boolean importResult = new ImportSaleOrderActionProperty(LM).makeImport(
                                                    newContext, orderObject, importColumns, new RawFileData(f), settings,
                                                    fileExtension, operationObject, supplierObject, supplierStockObject, customerObject,
                                                    customerStockObject);

                                            if (importResult)
                                                renameImportedFile(context, f.getAbsolutePath(), "." + fileExtension);

                                        } catch (Exception e) {
                                            ServerLoggers.importLogger.error("ImportSaleOrders Error, file " + f.getAbsolutePath(), e);
                                        }
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