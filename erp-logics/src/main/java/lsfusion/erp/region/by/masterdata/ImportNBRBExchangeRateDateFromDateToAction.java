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
            Date nbrbDateFrom = (Date) findProperty("importNBRBExchangeRateDateFrom[]").read(context);
            Date nbrbDateTo = (Date) findProperty("importNBRBExchangeRateDateTo[]").read(context);


            if (nbrbDateFrom != null && nbrbDateTo != null && shortNameCurrency != null) {
                Date separationDate = new Date(2016 - 1900, 5, 30); //30.06.2016
                Date denominationDate = new Date(2016 - 1900, 6, 1); //01.07.2016
                if(nbrbDateFrom.getTime() <= separationDate.getTime() && nbrbDateTo.getTime() > separationDate.getTime()) {
                    importExchanges(nbrbDateFrom, separationDate, shortNameCurrency, context, true);
                    importExchanges(denominationDate, nbrbDateTo, shortNameCurrency, context, false);
                } else {
                    importExchanges(nbrbDateFrom, nbrbDateTo, shortNameCurrency, context,  nbrbDateFrom.getTime() < separationDate.getTime());
                }
            }

        } catch (IOException | ScriptingErrorLog.SemanticErrorException | ParseException | JSONException e) {
            throw Throwables.propagate(e);
        }

    }
}