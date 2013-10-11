package lsfusion.erp.integration;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DefaultImportActionProperty extends ScriptingActionProperty {

    public DefaultImportActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultImportActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, valueClass);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
    }

    protected void checkFileExistence(String filePath) {
        if (!(new File(filePath).exists()))
            throw new RuntimeException("Запрашиваемый файл " + filePath + " не найден");
    }

    protected List<List<Object>> initData(int size) {
        List<List<Object>> data = new ArrayList<List<Object>>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<Object>());
        }
        return data;
    }
    
    protected BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }

    protected BigDecimal safeSubtract(BigDecimal operand1, int operand2) {
        return safeSubtract(operand1, BigDecimal.valueOf(operand2));
    }

    protected BigDecimal safeSubtract(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else
            return (operand1 == null ? operand2.negate() : (operand2 == null ? operand1 : operand1.subtract((operand2))));
    }

    protected BigDecimal safeMultiply(BigDecimal operand1, int operand2) {
        return safeMultiply(operand1, BigDecimal.valueOf(operand2));
    }

    protected BigDecimal safeMultiply(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null || operand1.doubleValue() == 0 || operand2 == null || operand2.doubleValue() == 0)
            return null;
        else return operand1.multiply(operand2);
    }

    protected BigDecimal safeDivide(BigDecimal dividend, int quotient) {
        return safeDivide(dividend, BigDecimal.valueOf(quotient));
    }

    protected BigDecimal safeDivide(BigDecimal dividend, BigDecimal quotient) {
        return safeDivide(dividend, quotient, 3);
    }
    
    protected BigDecimal safeDivide(BigDecimal dividend, BigDecimal quotient, int scale) {
        if (dividend == null || dividend.doubleValue() == 0 || quotient == null || quotient.doubleValue() == 0)
            return null;
        return dividend.divide(quotient, scale, RoundingMode.HALF_UP);
    }

    protected String getSplittedValue(String[] splittedLine, int index, String defaultValue) {
        return splittedLine == null || splittedLine.length <= index ? defaultValue : splittedLine[index];
    }
    
    protected String getStringFromEntry(List<Object> entry, int index) {
        return entry == null ? null : (String) entry.get(index);
    }
    
    protected BigDecimal getBigDecimalFromEntry(List<Object> entry, int index) {
        return entry == null ? null : (BigDecimal) entry.get(index);
    }
    
    protected Date getDateFromEntry(List<Object> entry, int index) {
        return entry == null ? null : (Date) entry.get(index);
    }
    
    protected Boolean getBooleanFromEntry(List<Object> entry, int index) {
        return entry == null ? null : (Boolean) entry.get(index);
    }

    private static String[][] ownershipsList = new String[][]{
            {"ОАОТ", "Открытое акционерное общество торговое"},
            {"ОАО", "Открытое акционерное общество"},
            {"СООО", "Совместное общество с ограниченной ответственностью"},
            {"ООО", "Общество с ограниченной ответственностью"},
            {"ОДО", "Общество с дополнительной ответственностью"},
            {"ЗАО", "Закрытое акционерное общество"},
            {"ЧТУП", "Частное торговое унитарное предприятие"},
            {"ЧУТП", "Частное унитарное торговое предприятие"},
            {"ТЧУП", "Торговое частное унитарное предприятие"},
            {"ЧУП", "Частное унитарное предприятие"},
            {"РУП", "Республиканское унитарное предприятие"},
            {"РДУП", "Республиканское дочернее унитарное предприятие"},
            {"УП", "Унитарное предприятие"},
            {"ИП", "Индивидуальный предприниматель"},
            {"СПК", "Сельскохозяйственный производственный кооператив"},
            {"СП", "Совместное предприятие"}};

    protected static String[] getAndTrimOwnershipFromName(String name) {
        name = name == null ? "" : name;
        String ownershipName = "";
        String ownershipShortName = "";
        for (String[] ownership : ownershipsList) {
            if (name.contains(ownership[0] + " ") || name.contains(" " + ownership[0])) {
                ownershipName = ownership[1];
                ownershipShortName = ownership[0];
                name = name.replace(ownership[0], "");
            }
        }
        return new String[]{ownershipShortName, ownershipName, name};
    }
}