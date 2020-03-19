package lsfusion.erp.region.by.masterdata;

import com.google.common.base.Throwables;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import org.json.JSONException;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Iterator;

public class ImportNBRBExchangeRateDateFromDateToAction extends ImportNBRBExchangeRateAction {
    private final ClassPropertyInterface currencyInterface;

    public ImportNBRBExchangeRateDateFromDateToAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        currencyInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataObject currencyObject = context.getDataKeyValue(currencyInterface);

            String shortNameCurrency = (String) findProperty("shortName[Currency]").read(context, currencyObject);
            LocalDate nbrbDateFrom = getLocalDate(findProperty("importNBRBExchangeRateDateFrom[]").read(context));
            LocalDate nbrbDateTo = getLocalDate(findProperty("importNBRBExchangeRateDateTo[]").read(context));

            if (nbrbDateFrom != null && nbrbDateTo != null && shortNameCurrency != null) {
                importExchanges(nbrbDateFrom, nbrbDateTo, shortNameCurrency, context);

            }

        } catch (IOException | ScriptingErrorLog.SemanticErrorException | ParseException | JSONException e) {
            throw Throwables.propagate(e);
        }

    }
}