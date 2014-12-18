package lsfusion.erp.region.ru.masterdata;

import lsfusion.server.classes.ConcreteClass;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.DateClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.commons.lang.time.DateUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ImportCBRFExchangeRateActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface currencyInterface;

    public ImportCBRFExchangeRateActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        currencyInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataObject currencyObject = context.getDataKeyValue(currencyInterface);

            String extraSIDCurrency = (String) findProperty("extraSIDCurrency").read(context, currencyObject);
            Date cbrfDateFrom = (Date) findProperty("importCBRFExchangeRateDateFrom").read(context);
            Date cbrfDateTo = (Date) findProperty("importCBRFExchangeRateDateTo").read(context);

            if (cbrfDateFrom != null && cbrfDateTo != null && extraSIDCurrency != null)
                importExchanges(cbrfDateFrom, cbrfDateTo, extraSIDCurrency, context);

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JDOMException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

    }

    private void importExchanges(Date dateFrom, Date dateTo, String extraSIDCurrency, ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, IOException, JDOMException, SQLException, ParseException, SQLHandledException {


        List<Exchange> exchangesList = importExchangesFromXML(dateFrom, dateTo, extraSIDCurrency, context);

        if (exchangesList != null) {

            ImportField typeExchangeRUField = new ImportField(findProperty("nameTypeExchange"));
            ImportField typeExchangeForeignField = new ImportField(findProperty("nameTypeExchange"));
            ImportField currencyField = new ImportField(findProperty("shortNameCurrency"));
            ImportField homeCurrencyField = new ImportField(findProperty("shortNameCurrency"));
            ImportField rateField = new ImportField(findProperty("rateExchange"));
            ImportField foreignRateField = new ImportField(findProperty("rateExchange"));
            ImportField dateField = new ImportField(DateClass.instance);

            ImportKey<?> typeExchangeRUKey = new ImportKey((ConcreteCustomClass) findClass("TypeExchange"),
                    findProperty("typeExchangeName").getMapping(typeExchangeRUField));

            ImportKey<?> typeExchangeForeignKey = new ImportKey((ConcreteCustomClass) findClass("TypeExchange"),
                    findProperty("typeExchangeName").getMapping(typeExchangeForeignField));

            ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) findClass("Currency"),
                    findProperty("currencyShortName").getMapping(currencyField));

            ImportKey<?> homeCurrencyKey = new ImportKey((ConcreteCustomClass) findClass("Currency"),
                    findProperty("currencyShortName").getMapping(homeCurrencyField));

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();

            props.add(new ImportProperty(typeExchangeRUField, findProperty("nameTypeExchange").getMapping(typeExchangeRUKey)));
            props.add(new ImportProperty(homeCurrencyField, findProperty("currencyTypeExchange").getMapping(typeExchangeRUKey),
                    object(findClass("Currency")).getMapping(homeCurrencyKey)));
            props.add(new ImportProperty(rateField, findProperty("rateExchange").getMapping(typeExchangeRUKey, currencyKey, dateField)));

            props.add(new ImportProperty(typeExchangeForeignField, findProperty("nameTypeExchange").getMapping(typeExchangeForeignKey)));
            props.add(new ImportProperty(currencyField, findProperty("currencyTypeExchange").getMapping(typeExchangeForeignKey),
                    object(findClass("Currency")).getMapping(currencyKey)));
            props.add(new ImportProperty(foreignRateField, findProperty("rateExchange").getMapping(typeExchangeForeignKey, homeCurrencyKey, dateField)));

            List<List<Object>> data = new ArrayList<List<Object>>();
            for (Exchange e : exchangesList) {
                data.add(Arrays.asList((Object) "ЦБРФ (RUB)", "ЦБРФ (" + e.currencyID + ")", e.currencyID, e.homeCurrencyID, e.exchangeRate, new BigDecimal(1 / e.exchangeRate.doubleValue()), e.date));
            }
            ImportTable table = new ImportTable(Arrays.asList(typeExchangeRUField, typeExchangeForeignField, currencyField,
                    homeCurrencyField, rateField, foreignRateField, dateField), data);

            DataSession session = context.getSession();
            IntegrationService service = new IntegrationService(session, table, Arrays.asList(typeExchangeRUKey,
                    typeExchangeForeignKey, currencyKey, homeCurrencyKey), props);
            service.synchronize(true, false);
        }
    }

    private List<Exchange> importExchangesFromXML(Date dateFrom, Date dateTo, String extraSIDCurrency, ExecutionContext context) throws IOException, JDOMException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        SAXBuilder builder = new SAXBuilder();

        List<Exchange> exchangesList = new ArrayList<Exchange>();

        Document document = builder.build(new URL("http://www.cbr.ru/scripts/XML_val.asp?d=0").openStream());
        Element rootNode = document.getRootElement();
        List list = rootNode.getChildren("Item");

        for (int i = 0; i < list.size(); i++) {

            Element node = (Element) list.get(i);

            String id = node.getAttributeValue("ID");

            if (extraSIDCurrency.equals(id)) {
                Document exchangeDocument = builder.build(new URL("http://www.cbr.ru/scripts/XML_dynamic.asp?date_req1="
                        + new SimpleDateFormat("dd/MM/yyyy").format(dateFrom)
                        + "&date_req2=" + new SimpleDateFormat("dd/MM/yyyy").format(dateTo)
                        + "&VAL_NM_RQ=" + id).openStream());
                Element exchangeRootNode = exchangeDocument.getRootElement();
                List exchangeList = exchangeRootNode.getChildren("Record");

                String shortNameCurrency = (String) findProperty("shortNameCurrency").read(context, new DataObject(findProperty("currencyExtraSID").read(context, new DataObject(extraSIDCurrency)), (ConcreteClass) findClass("Currency")));

                for (int j = 0; j < exchangeList.size(); j++) {

                    Element exchangeNode = (Element) exchangeList.get(j);

                    BigDecimal value = new BigDecimal(Double.valueOf(exchangeNode.getChildText("Value").replace(",", ".")) / Double.valueOf(exchangeNode.getChildText("Nominal")));

                    exchangesList.add(new Exchange(shortNameCurrency, "RUB",
                            new Date(DateUtils.parseDate(exchangeNode.getAttributeValue("Date"), new String[]{"dd.MM.yyyy"}).getTime()),
                            value));
                }
                if (exchangesList.size() > 0)
                    return exchangesList;
            }
        }
        return exchangesList;
    }


}