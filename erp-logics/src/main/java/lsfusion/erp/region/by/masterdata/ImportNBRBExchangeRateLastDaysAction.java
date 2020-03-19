package lsfusion.erp.region.by.masterdata;

import com.google.common.base.Throwables;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.json.JSONException;

import java.io.IOException;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Iterator;

public class ImportNBRBExchangeRateLastDaysAction extends ImportNBRBExchangeRateAction {
    private final ClassPropertyInterface currencyInterface;

    public ImportNBRBExchangeRateLastDaysAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        currencyInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataObject currencyObject = context.getDataKeyValue(currencyInterface);

            String shortNameCurrency = (String) findProperty("shortName[Currency]").read(context, currencyObject);
            Integer days = (Integer) findProperty("importNBRBExchangeRateDaysCount[]").read(context);
            if(days != null && days > 0) {
                if (shortNameCurrency != null) {
                    LocalDate dateFrom = LocalDate.now().minusDays(days);
                    LocalDate dateTo = LocalDate.now().plusDays(1);
                    importExchanges(dateFrom, dateTo, shortNameCurrency, context);

                }
            } else {
                throw new RuntimeException("Параметр 'Кол-во дней' должен быть задан и больше 0");
            }

        } catch (IOException | ScriptingErrorLog.SemanticErrorException | ParseException | JSONException e) {
            throw Throwables.propagate(e);
        }

    }
}