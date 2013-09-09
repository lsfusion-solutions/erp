package lsfusion.erp.integration.universal;

import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

public class ImportSaleOrdersActionProperty extends ScriptingActionProperty {

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
            importTypeQuery.addProperty("captionImportTypeFileExtensionImportType", getLCP("captionImportTypeFileExtensionImportType").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("startRowImportType", getLCP("startRowImportType").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("separatorImportType", getLCP("separatorImportType").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionImportKeyTypeImportType", getLCP("captionImportKeyTypeImportType").getExpr(context.getModifier(), importTypeKey));

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

                String directory = (String) entryValue.get("autoImportDirectoryImportType").getValue();
                String fileExtension = (String) entryValue.get("captionImportTypeFileExtensionImportType").getValue();
                Integer startRow = (Integer) entryValue.get("startRowImportType").getValue();
                startRow = startRow == null ? 1 : startRow;
                String csvSeparator = (String) entryValue.get("separatorImportType").getValue();
                String captionImportKeyTypeImportType = (String) entryValue.get("captionImportKeyTypeImportType").getValue();

                ObjectValue operation = LM.findLCPByCompoundName("autoImportOperationImportType").readClasses(context, (DataObject) importTypeObject);
                DataObject operationObject = operation instanceof NullValue ? null : (DataObject) operation;
                ObjectValue autoImportSupplier = entryValue.get("autoImportSupplierImportType");
                DataObject autoImportSupplierObject = autoImportSupplier instanceof NullValue ? null : (DataObject) autoImportSupplier;
                ObjectValue autoImportSupplierStock = entryValue.get("autoImportSupplierStockImportType");
                DataObject autoImportSupplierStockObject = autoImportSupplierStock instanceof NullValue ? null : (DataObject) autoImportSupplierStock;
                ObjectValue autoImportCustomer = entryValue.get("autoImportCustomerImportType");
                DataObject autoImportCustomerObject = autoImportCustomer instanceof NullValue ? null : (DataObject) autoImportCustomer;
                ObjectValue autoImportCustomerStock = entryValue.get("autoImportCustomerStockImportType");
                DataObject autoImportCustomerStockObject = autoImportCustomerStock instanceof NullValue ? null : (DataObject) autoImportCustomerStock;

                Map<String, String> importColumns = new HashMap<String, String>();

                LCP<?> isImportTypeDetail = LM.is(getClass("ImportTypeDetail"));
                ImRevMap<Object, KeyExpr> importColumnsKeys = (ImRevMap<Object, KeyExpr>) isImportTypeDetail.getMapKeys();
                KeyExpr importColumnsKey = importColumnsKeys.singleValue();
                QueryBuilder<Object, Object> importColumnsQuery = new QueryBuilder<Object, Object>(importColumnsKeys);
                importColumnsQuery.addProperty("staticName", getLCP("staticName").getExpr(context.getModifier(), importColumnsKey));
                importColumnsQuery.addProperty("indexImportTypeImportTypeDetail", getLCP("indexImportTypeImportTypeDetail").getExpr(context.getModifier(), importTypeObject.getExpr(), importColumnsKey));
                importColumnsQuery.and(isImportTypeDetail.getExpr(importColumnsKey).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = importColumnsQuery.execute(context.getSession().sql);

                for (ImMap<Object, Object> importColumnsEntry : result.valueIt()) {

                    String[] field = ((String) importColumnsEntry.get("staticName")).trim().split("\\.");
                    String index = (String) importColumnsEntry.get("indexImportTypeImportTypeDetail");
                    if (index != null)
                        importColumns.put(field[field.length - 1], index.trim());
                }

                if (directory != null && fileExtension != null) {
                    File dir = new File(directory.trim());

                    if (dir.exists()) {

                        for (File f : dir.listFiles()) {
                            if (f.getName().endsWith(fileExtension.trim().toLowerCase())) {
                                DataObject orderObject = context.addObject((ConcreteCustomClass) LM.findClassByCompoundName("Sale.UserOrder"));
                                new ImportSaleOrderActionProperty(LM).importOrders(context, orderObject, importColumns,
                                        IOUtils.getFileBytes(f), fileExtension.trim(), startRow,
                                        csvSeparator == null ? null : csvSeparator.trim(), captionImportKeyTypeImportType,
                                        operationObject, autoImportSupplierObject, autoImportSupplierStockObject,
                                        autoImportCustomerObject, autoImportCustomerStockObject);
                            }

                            renameImportedFile(context, f.getAbsolutePath(), "." + fileExtension.trim());
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (xBaseJException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (BiffException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    protected void renameImportedFile(ExecutionContext context, String oldPath, String extension) {
        File importedFile = new File(oldPath);
        String newExtension = extension.substring(0, extension.length()-1) + "e";
        if (importedFile.isFile() && oldPath.endsWith(extension)) {
            File renamedFile = new File(oldPath.replace(extension, newExtension));
            if (!importedFile.renameTo(renamedFile))
                context.requestUserInteraction(new MessageClientAction("Ошибка при переименовании импортированного файла " + oldPath, "Ошибка"));
        }
    }
}