package lsfusion.erp.integration;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DefaultImportActionProperty extends DefaultIntegrationActionProperty {

    public DefaultImportActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultImportActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) {
        super(LM, valueClass);
    }

    public DefaultImportActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected Time defaultTimeFrom = new Time(0, 0, 0);
    protected Time defaultTimeTo = new Time(23, 59, 59);
    protected Time noonTime = new Time(12, 0, 0);
    protected Date defaultDateFrom = new Date(2001 - 1900, 0, 1);

    private List<Double> allowedVAT = Arrays.asList(0.0, 9.09, 16.67, 10.0, 20.0, 24.0);
    protected BigDecimal VATifAllowed(BigDecimal VAT) {
        return VAT == null || !allowedVAT.contains(VAT.doubleValue()) ? null : VAT;
    }

    protected List<List<Object>> initData(int size) {
        List<List<Object>> data = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<>());
        }
        return data;
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
            {"ИООО", "Иностранное общество с ограниченной ответственностью"},
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
        name = name == null ? "" : name.trim();
        String ownershipName = "";
        String ownershipShortName = "";
        for (String[] ownership : ownershipsList) {
            if (name.contains(ownership[0] + " ") || name.contains(" " + ownership[0])) {
                ownershipName = ownership[1];
                ownershipShortName = ownership[0];
                name = name.replace(ownership[0], "");
            }
        }
        return new String[]{ownershipShortName, ownershipName, name.trim()};
    }
}