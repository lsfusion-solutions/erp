package lsfusion.erp.integration;

import lsfusion.base.file.IOUtils;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.commons.lang3.time.DateUtils;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;

public class DefaultImportDBFActionProperty extends DefaultImportActionProperty {

    public DefaultImportDBFActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultImportDBFActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) {
        super(LM, valueClass);
    }

    public DefaultImportDBFActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected String getDBFFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        return getDBFFieldValue(importFile, fieldName, charset, (String) null);
    }
    
    protected String getDBFFieldValue(DBF importFile, String fieldName, String charset, String defaultValue) throws UnsupportedEncodingException {
        return getDBFFieldValue(importFile, fieldName, charset, false, defaultValue);
    }

    protected String getDBFFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull) throws UnsupportedEncodingException {
       return getDBFFieldValue(importFile, fieldName, charset, zeroIsNull, null); 
    }
    
    protected String getDBFFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        try {
            String result = new String(importFile.getField(fieldName).getBytes(), charset).trim();
            return result.isEmpty() || (zeroIsNull && result.equals("0")) ? defaultValue : result;
        } catch (xBaseJException e) {
            return defaultValue;
        }
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        return getDBFBigDecimalFieldValue(importFile, fieldName, charset, (String) null);
    }
    
    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset, String defaultValue) throws UnsupportedEncodingException {
        return getDBFBigDecimalFieldValue(importFile, fieldName, charset, false, defaultValue);
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull) throws UnsupportedEncodingException {
        return getDBFBigDecimalFieldValue(importFile, fieldName, charset, zeroIsNull, null);
    }
    
    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        String result = getDBFFieldValue(importFile, fieldName, charset, zeroIsNull, defaultValue);
        return (result == null || result.isEmpty() || (zeroIsNull && Double.valueOf(result).equals(0d))) ? null : new BigDecimal(result.replace(",", "."));
    }

    protected Integer getDBFIntegerFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        return getDBFIntegerFieldValue(importFile, fieldName, charset, false, null);
    }

    protected Integer getDBFIntegerFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull) throws UnsupportedEncodingException {
        return getDBFIntegerFieldValue(importFile, fieldName, charset, zeroIsNull, null);
    }
    
    protected Integer getDBFIntegerFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        String result = getDBFFieldValue(importFile, fieldName, charset, zeroIsNull, defaultValue);
        return (result == null || (zeroIsNull && Double.valueOf(result).equals(0d))) ? null : new Double(result).intValue();
    }

    protected Date getDBFDateFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException, ParseException {
        return getDBFDateFieldValue(importFile, fieldName, charset, null);
    }
    
    protected Date getDBFDateFieldValue(DBF importFile, String fieldName, String charset, java.sql.Date defaultValue) throws UnsupportedEncodingException, ParseException {
        String dateString = getDBFFieldValue(importFile, fieldName, charset, false, "");
        return dateString.isEmpty() ? defaultValue : new Date(DateUtils.parseDate(dateString, new String[]{"yyyyMMdd", "dd.MM.yyyy"}).getTime());
    }

    protected Boolean getDBFBooleanFieldValue(DBF importFile, String fieldName, String charset, Boolean defaultValue) throws UnsupportedEncodingException {
        return "T".equals(getDBFFieldValue(importFile, fieldName, charset, String.valueOf(defaultValue)));
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
}