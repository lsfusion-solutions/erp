package lsfusion.erp.integration.universal;

import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.classes.ValueClass;
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

import java.io.File;
import java.io.IOException;
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
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
    }

    protected static Map<String, ImportColumnDetail> readImportColumns(ExecutionContext context, ScriptingLogicsModule LM, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException {

        Map<String, ImportColumnDetail> importColumns = new HashMap<String, ImportColumnDetail>();

        LCP<?> isImportTypeDetail = LM.is(LM.findClassByCompoundName("ImportTypeDetail"));
        ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isImportTypeDetail.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        query.addProperty("staticName", LM.findLCPByCompoundOldName("staticName").getExpr(context.getModifier(), key));
        query.addProperty("staticCaption", LM.findLCPByCompoundOldName("staticCaption").getExpr(context.getModifier(), key));
        query.addProperty("replaceOnlyNullImportTypeImportTypeDetail", LM.findLCPByCompoundOldName("replaceOnlyNullImportTypeImportTypeDetail").getExpr(context.getModifier(), importTypeObject.getExpr(), key));
        query.addProperty("indexImportTypeImportTypeDetail", LM.findLCPByCompoundOldName("indexImportTypeImportTypeDetail").getExpr(context.getModifier(), importTypeObject.getExpr(), key));
        query.and(isImportTypeDetail.getExpr(key).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context.getSession().sql);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String[] field = ((String) entry.get("staticName")).trim().split("\\.");
            String captionProperty = (String) entry.get("staticCaption");
            captionProperty = captionProperty == null ? null : captionProperty.trim();
            boolean replaceOnlyNull = entry.get("replaceOnlyNullImportTypeImportTypeDetail") != null;
            String indexes = (String) entry.get("indexImportTypeImportTypeDetail");
            if (indexes != null) {
                String[] splittedIndexes = indexes.split("\\+");
                for (int i = 0; i < splittedIndexes.length; i++)
                    splittedIndexes[i] = splittedIndexes[i].trim();
                importColumns.put(field[field.length - 1], new ImportColumnDetail(captionProperty, indexes, splittedIndexes, replaceOnlyNull));
            }
        }
        return importColumns.isEmpty() ? null : importColumns;
    }

    public String parseKeyType(String keyType) {
        String[] primaryParts = keyType == null ? null : keyType.split("\\.");
        return primaryParts == null ? null : trim(primaryParts[primaryParts.length - 1]);
    }

    public String getKeyColumn(String keyType) {
        return (keyType == null || keyType.equals("item")) ? "idItem" : keyType.equals("barcode") ? "barcodeItem" : "idBatch";
    }

    public String getDBFCharset(File file) throws IOException {
        byte charsetByte = IOUtils.getFileBytes(file)[29];
        String charset;
        switch (charsetByte) {
            case (byte) 0x65:
                charset = "cp866";
                break;
            case (byte) 0xC9:
                charset = "cp1251";
                break;
            default:
                charset = "cp866";
        }
        return charset;
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
}

