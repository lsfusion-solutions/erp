package lsfusion.erp.utils;

import lsfusion.base.ExceptionUtils;
import lsfusion.server.classes.DateClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;

public class FillDaysOffActionProperty extends ScriptingActionProperty {

    private final ClassPropertyInterface countryInterface;

    public FillDaysOffActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        countryInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            DataObject countryObject = context.getDataKeyValue(countryInterface);
            generateDates(context, countryObject);
        } catch (Exception e) {
            throw ExceptionUtils.propagate(e, SQLException.class, SQLHandledException.class);
        }
    }

    private void generateDates(ExecutionContext<ClassPropertyInterface> context, DataObject countryObject) throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        try (ExecutionContext.NewSession<ClassPropertyInterface> newContext = context.newSession()) {
            Calendar current = Calendar.getInstance();
            int currentYear = current.get(Calendar.YEAR);
            //если проставлен выходной 1 января через 2 года, пропускаем генерацию
            //DataObject countryObject = new DataObject(countryId, (ConcreteClass) BL.getModule("Country").getClass("country"));
            //if (BL.getModule("Country").getLCPByOldName("isDayOffCountryDate").read(session, countryObject, new DataObject(new java.sql.Date(new GregorianCalendar(currentYear + 2, 0, 1).getTimeInMillis()), DateClass.instance)) != null) {
            //    return;
            //}

            long wholeYearMillisecs = new GregorianCalendar(currentYear + 3, 0, 1).getTimeInMillis() - new GregorianCalendar(currentYear, 0, 1).getTimeInMillis()/*current.getTimeInMillis()*/;
            long wholeYearDays = wholeYearMillisecs / 1000 / 60 / 60 / 24;
            Calendar cal = new GregorianCalendar(currentYear, 0, 1);
            for (int i = 0; i < wholeYearDays; i++) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
                int day = cal.get(Calendar.DAY_OF_WEEK);
                if (day == 1 || day == 7) {
                    addDayOff(newContext, countryObject, cal.getTimeInMillis());
                }
            }

            for (int i = 0; i < 3; i++) {
                Calendar calendar = new GregorianCalendar(currentYear + i, 0, 1);
                int day = calendar.get(Calendar.DAY_OF_WEEK);
                if (day != 1 && day != 7)
                    addDayOff(newContext, countryObject, calendar.getTimeInMillis());
            }

            newContext.apply();
        }
    }

    private void addDayOff(ExecutionContext<ClassPropertyInterface> context, DataObject countryObject, long timeInMillis) throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        context.getBL().getModule("Country").findProperty("isDayOff[Country,DATE]").change(true, context, countryObject, new DataObject(new java.sql.Date(timeInMillis), DateClass.instance));
    }
}