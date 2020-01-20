package lsfusion.erp.region.by.masterdata;

import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.data.time.DateClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.service.*;
import org.apache.commons.lang3.time.DateUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImportNBRBExchangeRateAction extends DefaultIntegrationAction {

    public ImportNBRBExchangeRateAction(ScriptingLogicsModule LM, ValueClass valueClass) {
        super(LM, valueClass);
    }

    public ImportNBRBExchangeRateAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected void importExchanges(Date dateFrom, Date dateTo, String shortNameCurrency, ExecutionContext<ClassPropertyInterface> context, boolean denominate) throws ScriptingErrorLog.SemanticErrorException, IOException, SQLException, ParseException, SQLHandledException, JSONException {


        List<Exchange> exchangesList = importExchangesFromXMLDenominated(dateFrom, dateTo, shortNameCurrency, denominate);

        ImportField typeExchangeBYRField = new ImportField(findProperty("name[TypeExchange]"));
        ImportField typeExchangeForeignField = new ImportField(findProperty("name[TypeExchange]"));
        ImportField currencyField = new ImportField(findProperty("shortName[Currency]"));
        ImportField homeCurrencyField = new ImportField(findProperty("shortName[Currency]"));
        ImportField rateField = new ImportField(findProperty("rate[TypeExchange,Currency,DATE]"));
        ImportField foreignRateField = new ImportField(findProperty("rate[TypeExchange,Currency,DATE]"));
        ImportField dateField = new ImportField(DateClass.instance);

        ImportKey<?> typeExchangeBYRKey = new ImportKey((ConcreteCustomClass) findClass("TypeExchange"),
                findProperty("typeExchange[ISTRING[50]]").getMapping(typeExchangeBYRField));

        ImportKey<?> typeExchangeForeignKey = new ImportKey((ConcreteCustomClass) findClass("TypeExchange"),
                findProperty("typeExchange[ISTRING[50]]").getMapping(typeExchangeForeignField));

        ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) findClass("Currency"),
                findProperty("currencyShortName[BPSTRING[3]]").getMapping(currencyField));

        ImportKey<?> homeCurrencyKey = new ImportKey((ConcreteCustomClass) findClass("Currency"),
                findProperty("currencyShortName[BPSTRING[3]]").getMapping(homeCurrencyField));

        List<ImportProperty<?>> props = new ArrayList<>();

        props.add(new ImportProperty(typeExchangeBYRField, findProperty("name[TypeExchange]").getMapping(typeExchangeBYRKey)));
        props.add(new ImportProperty(homeCurrencyField, findProperty("currency[TypeExchange]").getMapping(typeExchangeBYRKey),
                object(findClass("Currency")).getMapping(homeCurrencyKey)));
        props.add(new ImportProperty(rateField, findProperty("rate[TypeExchange,Currency,DATE]").getMapping(typeExchangeBYRKey, currencyKey, dateField)));

        props.add(new ImportProperty(typeExchangeForeignField, findProperty("name[TypeExchange]").getMapping(typeExchangeForeignKey)));
        props.add(new ImportProperty(currencyField, findProperty("currency[TypeExchange]").getMapping(typeExchangeForeignKey),
                object(findClass("Currency")).getMapping(currencyKey)));
        props.add(new ImportProperty(foreignRateField, findProperty("rate[TypeExchange,Currency,DATE]").getMapping(typeExchangeForeignKey, homeCurrencyKey, dateField)));

        List<List<Object>> data = new ArrayList<>();
        for (Exchange e : exchangesList) {
            data.add(Arrays.asList("НБРБ (BYR)", "НБРБ (" + e.currencyID + ")", e.currencyID, e.homeCurrencyID, e.exchangeRate, new BigDecimal(1 / e.exchangeRate.doubleValue()), e.date));
        }
        ImportTable table = new ImportTable(Arrays.asList(typeExchangeBYRField, typeExchangeForeignField, currencyField,
                homeCurrencyField, rateField, foreignRateField, dateField), data);

        IntegrationService service = new IntegrationService(context, table, Arrays.asList(typeExchangeBYRKey,
                typeExchangeForeignKey, currencyKey, homeCurrencyKey), props);
        service.synchronize(true, false);
        //session.apply(LM.getBL());
    }

    private List<Exchange> importExchangesFromXMLDenominated(Date dateFrom, Date dateTo, String shortNameCurrency, boolean denominate) throws IOException, ParseException, JSONException {

        List<Exchange> exchangesList = new ArrayList<>();

        JSONArray document = readJsonFromUrl("http://www.nbrb.by/API/ExRates/Currencies");
        for (int i = 0; i < document.length(); i++) {
            JSONObject jsonObject = document.getJSONObject(i);

            String charCode = jsonObject.getString("Cur_Abbreviation");

            if (shortNameCurrency.equals(charCode)) {
                String id = String.valueOf(jsonObject.getInt("Cur_ID"));
                BigDecimal scale = jsonObject.getBigDecimal("Cur_Scale");

                JSONArray exchangeDocument = readJsonFromUrl("http://www.nbrb.by/API/ExRates/Rates/Dynamics/" + id
                        + "?startDate=" + new SimpleDateFormat("MM/dd/yyyy").format(dateFrom)
                        + "&endDate=" + new SimpleDateFormat("MM/dd/yyyy").format(dateTo));

                for (int j = 0; j < exchangeDocument.length(); j++) {

                    JSONObject exchangeNode = exchangeDocument.getJSONObject(j);

                    BigDecimal rate = exchangeNode.getBigDecimal("Cur_OfficialRate");
                    if(denominate)
                        rate = safeDivide(rate, 10000);

                    exchangesList.add(new Exchange(charCode, "BYN", new Date(DateUtils.parseDate(exchangeNode.getString("Date"), "yyyy-MM-dd'T'HH:mm:ss").getTime()),
                            safeDivide(rate, scale, 6)));
                }
                if (exchangesList.size() > 0)
                    return exchangesList;
            }
        }
        return exchangesList;
    }

    public JSONArray readJsonFromUrl(String url) throws IOException, JSONException {
        try (InputStream is = new URL(url).openStream()) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String jsonText = readAll(rd);
            return new JSONArray(jsonText);
        }
    }

    private String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }
}