package lsfusion.erp.integration.universal;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.ImportField;
import lsfusion.server.integration.ImportKey;
import lsfusion.server.integration.ImportProperty;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ImportDocumentActionProperty extends ImportUniversalActionProperty {

    public ImportDocumentActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, valueClass);
    }

    public ImportDocumentActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected static Map<String, ImportColumnDetail> readImportColumns(DataSession session, ScriptingLogicsModule LM, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Map<String, ImportColumnDetail> importColumns = new HashMap<String, ImportColumnDetail>();

        LCP<PropertyInterface> isImportTypeDetail = (LCP<PropertyInterface>) LM.is(LM.findClassByCompoundName("ImportTypeDetail"));
        ImRevMap<PropertyInterface, KeyExpr> keys = isImportTypeDetail.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<PropertyInterface, Object> query = new QueryBuilder<PropertyInterface, Object>(keys);
        query.addProperty("staticName", LM.findLCPByCompoundOldName("staticName").getExpr(session.getModifier(), key));
        query.addProperty("staticCaption", LM.findLCPByCompoundOldName("staticCaption").getExpr(session.getModifier(), key));
        query.addProperty("replaceOnlyNullImportTypeImportTypeDetail", LM.findLCPByCompoundOldName("replaceOnlyNullImportTypeImportTypeDetail").getExpr(session.getModifier(), importTypeObject.getExpr(), key));
        query.addProperty("indexImportTypeImportTypeDetail", LM.findLCPByCompoundOldName("indexImportTypeImportTypeDetail").getExpr(session.getModifier(), importTypeObject.getExpr(), key));
        query.and(isImportTypeDetail.getExpr(key).getWhere());
        ImOrderMap<ImMap<PropertyInterface, Object>, ImMap<Object, Object>> result = query.execute(session.getSession().sql);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String[] field = ((String) entry.get("staticName")).trim().split("\\.");
            String captionProperty = (String) entry.get("staticCaption");
            captionProperty = captionProperty == null ? null : captionProperty.trim();
            boolean replaceOnlyNull = entry.get("replaceOnlyNullImportTypeImportTypeDetail") != null;
            String indexes = (String) entry.get("indexImportTypeImportTypeDetail");
            if (indexes != null) {
                String[] splittedIndexes = indexes.split("\\+");
                for (int i = 0; i < splittedIndexes.length; i++)
                    splittedIndexes[i] = splittedIndexes[i].contains("=") ? splittedIndexes[i] : splittedIndexes[i].trim();
                importColumns.put(field[field.length - 1], new ImportColumnDetail(captionProperty, indexes, splittedIndexes, replaceOnlyNull));
            }
        }
        return importColumns.isEmpty() ? null : importColumns;
    }

    protected static Map<String, String> readStockMapping(DataSession session, ScriptingLogicsModule LM, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Map<String, String> stockMapping = new HashMap<String, String>();

        KeyExpr key = new KeyExpr("stockMappingEntry");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "StockMappingEntry", key);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        
        query.addProperty("idStockMappingEntry", LM.findLCPByCompoundOldName("idStockMappingEntry").getExpr(session.getModifier(), key));
        query.addProperty("idStockStockMappingEntry", LM.findLCPByCompoundOldName("idStockStockMappingEntry").getExpr(session.getModifier(), key));
        query.and(LM.findLCPByCompoundOldName("idStockMappingEntry").getExpr(key).getWhere());
        query.and(LM.findLCPByCompoundOldName("importTypeStockMappingEntry").getExpr(key).compare(importTypeObject.getExpr(), Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session.getSession().sql);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String idStockMappingEntry = (String) entry.get("idStockMappingEntry");
            String idStockStockMappingEntry = (String) entry.get("idStockStockMappingEntry");
            stockMapping.put(idStockMappingEntry, idStockStockMappingEntry);            
        }
        return stockMapping;
    }

    public String parseKeyType(String keyType) {
        String[] primaryParts = keyType == null ? null : keyType.split("\\.");
        return primaryParts == null ? null : trim(primaryParts[primaryParts.length - 1]);
    }

    public String getKeyColumn(String keyType) {
        return (keyType == null || keyType.equals("item")) ? "idItem" : keyType.equals("barcode") ? "barcodeItem" : "idBatch";
    }
    
    public String getKeyGroupAggr(String keyType) {
        return (keyType == null || keyType.equals("item")) ? "itemId" : keyType.equals("barcode") ? "skuIdBarcode" : "skuBatchId";
    }

    protected void addDataField(List<ImportProperty<?>> props, List<ImportField> fields, Map<String, ImportColumnDetail> importColumns, String sidProperty, String nameField, ImportKey<?> key) throws ScriptingErrorLog.SemanticErrorException {
        ImportField field = new ImportField(getLCP(sidProperty));
        props.add(new ImportProperty(field, getLCP(sidProperty).getMapping(key), getReplaceOnlyNull(importColumns, nameField)));
        fields.add(field);
    }

    protected void addDataField(List<ImportProperty<?>> props, List<ImportField> fields, Map<String, ImportColumnDetail> importColumns, String sidProperty, String nameField, DataObject dataObject) throws ScriptingErrorLog.SemanticErrorException {
        ImportField field = new ImportField(getLCP(sidProperty));
        props.add(new ImportProperty(field, getLCP(sidProperty).getMapping(dataObject), getReplaceOnlyNull(importColumns, nameField)));
        fields.add(field);
    }

    protected void addDataField(ScriptingLogicsModule LM, List<ImportProperty<?>> props, List<ImportField> fields, Map<String, ImportColumnDetail> importColumns, String sidProperty, String nameField, ImportKey<?> key) throws ScriptingErrorLog.SemanticErrorException {
        ImportField field = new ImportField(LM.findLCPByCompoundOldName(sidProperty));
        props.add(new ImportProperty(field, LM.findLCPByCompoundOldName(sidProperty).getMapping(key), getReplaceOnlyNull(importColumns, nameField)));
        fields.add(field);
    }

    protected boolean checkKeyColumnValue(String keyColumn, String keyColumnValue, boolean keyIsDigit)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        return checkKeyColumnValue(keyColumn, keyColumnValue, keyIsDigit, null, null, false);
    }
    
    protected boolean checkKeyColumnValue(String keyColumn, String keyColumnValue, boolean keyIsDigit,
                                          DataSession session, String keyType, boolean checkExistence) 
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        return keyColumn != null && keyColumnValue != null && !keyColumnValue.isEmpty() && (!keyIsDigit || keyColumnValue.matches("(\\d|\\-)+")) 
                && (!checkExistence || getLCP(getKeyGroupAggr(keyType)).read(session, new DataObject(keyColumnValue)) != null);
    }
}

