package lsfusion.erp.integration.universal;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.stock.BarcodeUtils;
import lsfusion.interop.form.property.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.classes.data.LogicalClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.builder.QueryBuilder;
import lsfusion.server.physics.dev.integration.service.ImportField;
import lsfusion.server.physics.dev.integration.service.ImportKey;
import lsfusion.server.physics.dev.integration.service.ImportProperty;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.session.DataSession;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

public abstract class ImportDocumentActionProperty extends ImportUniversalActionProperty {

    protected ScriptingLogicsModule skuImportCodeLM = null;

    public static int IMPORT_RESULT_OK = 1;
    public static int IMPORT_RESULT_EMPTY = 0;
    public static int IMPORT_RESULT_ERROR = -1;
    public static int IMPORT_RESULT_DOCUMENTS_CLOSED_DATE = -2;

    public ImportDocumentActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) {
        super(LM, valueClass);
    }

    public ImportDocumentActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    public ImportDocumentActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        skuImportCodeLM = context.getBL().getModule("SkuImportCode");
    }

    protected List<LinkedHashMap<String, ImportColumnDetail>> readImportColumns(ExecutionContext context, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        LinkedHashMap<String, ImportColumnDetail> defaultColumns = new LinkedHashMap<>();
        LinkedHashMap<String, ImportColumnDetail> customColumns = new LinkedHashMap<>();

        KeyExpr importTypeDetailExpr = new KeyExpr("importTypeDetail");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "importTypeDetail", importTypeDetailExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        String[] names = new String[] {"staticName", "staticCaption", "propertyImportTypeDetail", "nameKeyImportTypeDetail"};
        LP[] properties = findProperties("staticName[Object]", "staticCaption[Object]", "canonicalNameProp[ImportTypeDetail]", "nameKey[ImportTypeDetail]");
        for (int j = 0; j < properties.length; j++) {
            query.addProperty(names[j], properties[j].getExpr(importTypeDetailExpr));
        }
        query.addProperty("replaceOnlyNullImportTypeImportTypeDetail", findProperty("replaceOnlyNull[ImportType,ImportTypeDetail]").getExpr(importTypeObject.getExpr(), importTypeDetailExpr));
        query.addProperty("indexImportTypeImportTypeDetail", findProperty("index[ImportType,ImportTypeDetail]").getExpr(importTypeObject.getExpr(), importTypeDetailExpr));
        query.and(findProperty("index[ImportType,ImportTypeDetail]").getExpr(importTypeObject.getExpr(), importTypeDetailExpr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String staticNameProperty = trim((String) entry.get("staticName"));
            String field = getSplittedPart(staticNameProperty, "\\.", -1);
            String staticCaptionProperty = trim((String) entry.get("staticCaption"));

            String propertyCanonicalName = (String) entry.get("propertyImportTypeDetail");
            LP<?> customProp = propertyCanonicalName == null ? null : (LP<?>) context.getBL().findSafeProperty(propertyCanonicalName);
            boolean isBoolean = customProp != null && customProp.property.getType() instanceof LogicalClass;

            String keyImportTypeDetail = getSplittedPart((String) entry.get("nameKeyImportTypeDetail"), "\\.", 1);
            boolean replaceOnlyNull = entry.get("replaceOnlyNullImportTypeImportTypeDetail") != null;
            String indexes = (String) entry.get("indexImportTypeImportTypeDetail");
            if (indexes != null) {
                int openingParentheses = StringUtils.countMatches(indexes, "(");
                int closingParentheses = StringUtils.countMatches(indexes, ")");
                String[] splittedIndexes = (openingParentheses == 0 || openingParentheses != closingParentheses) ? indexes.split("\\+") : new String[] {indexes};
                for (int i = 0; i < splittedIndexes.length; i++)
                    splittedIndexes[i] = splittedIndexes[i].contains("=") ? splittedIndexes[i] : splittedIndexes[i].trim();
                if(field != null)
                    defaultColumns.put(field, new ImportColumnDetail(staticCaptionProperty, indexes, splittedIndexes, replaceOnlyNull, isBoolean));
                else if(keyImportTypeDetail != null)
                    customColumns.put(staticCaptionProperty, new ImportColumnDetail(staticCaptionProperty, indexes, splittedIndexes, replaceOnlyNull,
                            propertyCanonicalName, keyImportTypeDetail, isBoolean));
            }
        }
        return Arrays.asList(defaultColumns, customColumns);
    }

    protected Map<String, String> readStockMapping(DataSession session, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Map<String, String> stockMapping = new HashMap<>();

        KeyExpr key = new KeyExpr("stockMappingEntry");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "StockMappingEntry", key);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        
        query.addProperty("idStockMappingEntry", findProperty("id[StockMappingEntry]").getExpr(session.getModifier(), key));
        query.addProperty("idStockStockMappingEntry", findProperty("idStock[StockMappingEntry]").getExpr(session.getModifier(), key));
        query.and(findProperty("id[StockMappingEntry]").getExpr(key).getWhere());
        query.and(findProperty("importType[StockMappingEntry]").getExpr(key).compare(importTypeObject.getExpr(), Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String idStockMappingEntry = (String) entry.get("idStockMappingEntry");
            String idStockStockMappingEntry = (String) entry.get("idStockStockMappingEntry");
            stockMapping.put(idStockMappingEntry, idStockStockMappingEntry);            
        }
        return stockMapping;
    }

    public ImportDocumentSettings readImportDocumentSettings(DataSession session, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, String> stockMapping = readStockMapping(session, importTypeObject);
        String fileExtension = trim((String) findProperty("captionFileExtension[ImportType]").read(session, importTypeObject));
        String primaryKeyType = parseKeyType((String) findProperty("namePrimaryKeyType[ImportType]").read(session, importTypeObject));
        boolean checkExistence = findProperty("checkExistencePrimaryKey[ImportType]").read(session, importTypeObject) != null;
        String secondaryKeyType = parseKeyType((String) findProperty("nameSecondaryKeyType[ImportType]").read(session, importTypeObject));
        boolean keyIsDigit = findProperty("keyIsDigit[ImportType]").read(session, importTypeObject) != null;
        Integer startRow = (Integer) findProperty("startRow[ImportType]").read(session, importTypeObject);
        startRow = startRow == null ? 1 : startRow;
        Boolean isPosted = (Boolean) findProperty("isPosted[ImportType]").read(session, importTypeObject);
        String separator = formatSeparator((String) findProperty("separator[ImportType]").read(session, importTypeObject));
        String propertyImportType = trim((String) findProperty("propertyImportTypeDetail[ImportType]").read(session, importTypeObject));
        boolean multipleDocuments = findProperty("multipleDocuments[ImportType]").read(session, importTypeObject) != null;
        String countryKeyType = parseKeyType((String) findProperty("nameCountryKeyType[ImportType]").read(session, importTypeObject));
        return new ImportDocumentSettings(stockMapping, fileExtension, primaryKeyType, checkExistence, secondaryKeyType,
                keyIsDigit, startRow, isPosted, separator, propertyImportType, multipleDocuments, countryKeyType);
    }

    public String parseKeyType(String keyType) {
        String[] primaryParts = keyType == null ? null : keyType.split("\\.");
        return primaryParts == null ? null : trim(primaryParts[primaryParts.length - 1]);
    }

    public String getItemKeyColumn(String keyType) {
        return (keyType == null || keyType.equals("item")) ? "idItem" : keyType.equals("barcode") ? "barcodeItem" : keyType.equals("importCode") ? "idImportCode": "idBatch";
    }
    
    public LP getItemKeyGroupAggr(String keyType) throws ScriptingErrorLog.SemanticErrorException {
        if(keyType == null || keyType.equals("item"))
            return findProperty("item[VARSTRING[100]]");
        else if(keyType.equals("barcode"))
            return findProperty("skuBarcode[STRING[15]]");
        else if(skuImportCodeLM != null && keyType.equals("importCode"))
            return skuImportCodeLM.findProperty("skuImportCode[STRING[100]]");
        else return findProperty("skuBatch[VARSTRING[100]]");
    }

    protected void addDataField(List<ImportProperty<?>> props, List<ImportField> fields, Map<String, ImportColumnDetail> importColumns, LP sidProperty, String nameField, ImportKey<?> key) throws ScriptingErrorLog.SemanticErrorException {
        ImportField field = new ImportField(sidProperty);
        props.add(new ImportProperty(field, sidProperty.getMapping(key), getReplaceOnlyNull(importColumns, nameField)));
        fields.add(field);
    }

    protected void addDataField(List<ImportProperty<?>> props, List<ImportField> fields, Map<String, ImportColumnDetail> importColumns, LP sidProperty, String nameField, Object key) throws ScriptingErrorLog.SemanticErrorException {
        ImportField field = new ImportField(sidProperty);
        props.add(new ImportProperty(field, sidProperty.getMapping(key), getReplaceOnlyNull(importColumns, nameField)));
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
        int len = splittedValue.length;
        return index >= 0 ? (len <= index ? null : splittedValue[index]) : len < -index ? null : splittedValue[len + index];
    }
    
    protected String formatSeparator(String separator) {
        String result = trim(separator, ";");
        if(result.equals("|"))
            result = "\\" + result;
        return result;
    }

    protected void renameImportedFile(ExecutionContext context, String oldPath, String extension) {
        File importedFile = new File(oldPath);
        String newExtensionUpCase = extension.substring(0, extension.length() - 1) + "E";
        String newExtensionLowCase = extension.toLowerCase().substring(0, extension.length() - 1) + "e";
        if (importedFile.isFile()) {
            File renamedFile = oldPath.endsWith(extension) ? new File(oldPath.replace(extension, newExtensionUpCase)) :
                    (oldPath.endsWith(extension.toLowerCase()) ? new File(oldPath.replace(extension.toLowerCase(), newExtensionLowCase)) : null);
            int i = 1;
            while (renamedFile != null && renamedFile.exists()) {
                renamedFile = oldPath.endsWith(extension) ? new File(oldPath.replace(extension, "") + "(" + i + ")" + newExtensionUpCase) :
                        (oldPath.endsWith(extension.toLowerCase()) ? new File(oldPath.replace(extension.toLowerCase(), "") + "(" + i + ")" + newExtensionLowCase) : null);
                i += 1;
            }
            if (renamedFile == null || !importedFile.renameTo(renamedFile))
                context.requestUserInteraction(new MessageClientAction("Ошибка при переименовании импортированного файла " + oldPath, "Ошибка"));
        }
    }
}

