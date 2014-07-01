package lsfusion.erp.integration.universal;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.stock.BarcodeUtils;
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
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;
import java.util.*;

public abstract class ImportDocumentActionProperty extends ImportUniversalActionProperty {

    public static int IMPORT_RESULT_OK = 1;
    public static int IMPORT_RESULT_EMPTY = 0;
    public static int IMPORT_RESULT_ERROR = -1;

    public ImportDocumentActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, valueClass);
    }

    public ImportDocumentActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected static List<LinkedHashMap<String, ImportColumnDetail>> readImportColumns(DataSession session, ScriptingLogicsModule LM, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        LinkedHashMap<String, ImportColumnDetail> defaultColumns = new LinkedHashMap<String, ImportColumnDetail>();
        LinkedHashMap<String, ImportColumnDetail> customColumns = new LinkedHashMap<String, ImportColumnDetail>();

        KeyExpr importTypeDetailExpr = new KeyExpr("importTypeDetail");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "importTypeDetail", importTypeDetailExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        String[] properties = new String[] {"staticName", "staticCaption", "propertyImportTypeDetail", "nameKeyImportTypeDetail"};
        for(String property : properties) {
            query.addProperty(property, LM.findLCPByCompoundOldName(property).getExpr(importTypeDetailExpr));
        }
        query.addProperty("replaceOnlyNullImportTypeImportTypeDetail", LM.findLCPByCompoundOldName("replaceOnlyNullImportTypeImportTypeDetail").getExpr(importTypeObject.getExpr(), importTypeDetailExpr));
        query.addProperty("indexImportTypeImportTypeDetail", LM.findLCPByCompoundOldName("indexImportTypeImportTypeDetail").getExpr(importTypeObject.getExpr(), importTypeDetailExpr));
        query.and(LM.findLCPByCompoundOldName("staticName").getExpr(importTypeDetailExpr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String[] field = ((String) entry.get("staticName")).trim().split("\\.");
            String captionProperty = (String) entry.get("staticCaption");
            captionProperty = captionProperty == null ? null : captionProperty.trim();
            String propertyImportTypeDetail = (String) entry.get("propertyImportTypeDetail");
            String moduleName = getSplittedPart(propertyImportTypeDetail, "\\.", 0);
            String sidProperty = getSplittedPart(propertyImportTypeDetail, "\\.", 1);
            String keyImportTypeDetail = getSplittedPart((String) entry.get("nameKeyImportTypeDetail"), "\\.", 1);
            boolean replaceOnlyNull = entry.get("replaceOnlyNullImportTypeImportTypeDetail") != null;
            String indexes = (String) entry.get("indexImportTypeImportTypeDetail");
            if (indexes != null) {
                String[] splittedIndexes = indexes.split("\\+");
                for (int i = 0; i < splittedIndexes.length; i++)
                    splittedIndexes[i] = splittedIndexes[i].contains("=") ? splittedIndexes[i] : splittedIndexes[i].trim();
                defaultColumns.put(field[field.length - 1], new ImportColumnDetail(captionProperty, indexes, splittedIndexes, replaceOnlyNull));
                if(keyImportTypeDetail != null)
                    customColumns.put(field[field.length - 1], new ImportColumnDetail(captionProperty, indexes, splittedIndexes, replaceOnlyNull,
                            moduleName, sidProperty, keyImportTypeDetail));
            }
        }
        return Arrays.asList(defaultColumns, customColumns);
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
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String idStockMappingEntry = (String) entry.get("idStockMappingEntry");
            String idStockStockMappingEntry = (String) entry.get("idStockStockMappingEntry");
            stockMapping.put(idStockMappingEntry, idStockStockMappingEntry);            
        }
        return stockMapping;
    }

    public ImportDocumentSettings readImportDocumentSettings(DataSession session, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, String> stockMapping = readStockMapping(session, LM, importTypeObject);
        String primaryKeyType = parseKeyType((String) getLCP("namePrimaryKeyTypeImportType").read(session, importTypeObject));
        boolean checkExistence = getLCP("checkExistencePrimaryKeyImportType").read(session, importTypeObject) != null;
        String secondaryKeyType = parseKeyType((String) getLCP("nameSecondaryKeyTypeImportType").read(session, importTypeObject));
        boolean keyIsDigit = getLCP("keyIsDigitImportType").read(session, importTypeObject) != null;
        Integer startRow = (Integer) getLCP("startRowImportType").read(session, importTypeObject);
        startRow = startRow == null ? 1 : startRow;
        Boolean isPosted = (Boolean) getLCP("isPostedImportType").read(session, importTypeObject);
        String csvSeparator = trim((String) getLCP("separatorImportType").read(session, importTypeObject), ";");
        String propertyImportType = trim((String) getLCP("propertyImportTypeDetailImportType").read(session, importTypeObject));
        return new ImportDocumentSettings(stockMapping, primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, csvSeparator, propertyImportType);
    }

    public String parseKeyType(String keyType) {
        String[] primaryParts = keyType == null ? null : keyType.split("\\.");
        return primaryParts == null ? null : trim(primaryParts[primaryParts.length - 1]);
    }

    public String getItemKeyColumn(String keyType) {
        return (keyType == null || keyType.equals("item")) ? "idItem" : keyType.equals("barcode") ? "barcodeItem" : "idBatch";
    }
    
    public LCP getItemKeyGroupAggr(String keyType) throws ScriptingErrorLog.SemanticErrorException {
        return getLCP((keyType == null || keyType.equals("item")) ? "itemId" : keyType.equals("barcode") ? "skuIdBarcode" : "skuBatchId");
    }

    protected void addDataField(List<ImportProperty<?>> props, List<ImportField> fields, Map<String, ImportColumnDetail> importColumns, LCP sidProperty, String nameField, ImportKey<?> key) throws ScriptingErrorLog.SemanticErrorException {
        ImportField field = new ImportField(sidProperty);
        props.add(new ImportProperty(field, sidProperty.getMapping(key), getReplaceOnlyNull(importColumns, nameField)));
        fields.add(field);
    }

    protected void addDataField(List<ImportProperty<?>> props, List<ImportField> fields, Map<String, ImportColumnDetail> importColumns, LCP sidProperty, String nameField, DataObject dataObject) throws ScriptingErrorLog.SemanticErrorException {
        ImportField field = new ImportField(sidProperty);
        props.add(new ImportProperty(field, sidProperty.getMapping(dataObject), getReplaceOnlyNull(importColumns, nameField)));
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
        if(keyColumn != null && keyColumn.equals("barcodeItem"))
            keyColumnValue = BarcodeUtils.appendCheckDigitToBarcode(keyColumnValue, 7);
        return keyColumn != null && keyColumnValue != null && !keyColumnValue.isEmpty() && (!keyIsDigit || keyColumnValue.matches("(\\d|\\-)+")) 
                && (!checkExistence || getItemKeyGroupAggr(keyType).read(session, new DataObject(keyColumnValue)) != null);
    }
    
    protected static String getSplittedPart(String value, String splitPattern, int index) {
        if(value == null) return null;
        String[] splittedValue = value.trim().split(splitPattern);
        return splittedValue.length <= index ? null : splittedValue[index];
    }
}

