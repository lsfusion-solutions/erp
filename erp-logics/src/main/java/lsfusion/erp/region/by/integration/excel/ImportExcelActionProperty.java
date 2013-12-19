package lsfusion.erp.region.by.integration.excel;

import lsfusion.erp.integration.DefaultImportActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;

public class ImportExcelActionProperty extends DefaultImportActionProperty {

    public ImportExcelActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    protected static String parseString(String value) throws ParseException {
        return value.isEmpty() ? null : value;
    }

    protected static BigDecimal parseBigDecimal(String value) throws ParseException {
        return value.isEmpty() ? null : BigDecimal.valueOf(NumberFormat.getInstance().parse(value).doubleValue());
    }

    protected static Boolean parseBoolean(String value) {
        return value.equals("1") ? true : null;
    }
}