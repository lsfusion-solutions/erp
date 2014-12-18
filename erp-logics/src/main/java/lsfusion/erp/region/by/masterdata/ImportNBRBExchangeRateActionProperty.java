package lsfusion.erp.region.by.masterdata;


import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.DateClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.integration.*;
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
import java.util.List;

public class ImportNBRBExchangeRateActionProperty extends ScriptingActionProperty {

    public ImportNBRBExchangeRateActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, valueClass);
    }

    public ImportNBRBExchangeRateActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected void importExchanges(Date dateFrom, Date dateTo, String shortNameCurrency, ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, IOException, JDOMException, SQLException, ParseException, SQLHandledException {


        List<Exchange> exchangesList = importExchangesFromXML(dateFrom, dateTo, shortNameCurrency);

        if (exchangesList != null) {

            ImportField typeExchangeBYRField = new ImportField(findProperty("nameTypeExchange"));
            ImportField typeExchangeForeignField = new ImportField(findProperty("nameTypeExchange"));
            ImportField currencyField = new ImportField(findProperty("shortNameCurrency"));
            ImportField homeCurrencyField = new ImportField(findProperty("shortNameCurrency"));
            ImportField rateField = new ImportField(findProperty("rateExchange"));
            ImportField foreignRateField = new ImportField(findProperty("rateExchange"));
            ImportField dateField = new ImportField(DateClass.instance);

            ImportKey<?> typeExchangeBYRKey = new ImportKey((ConcreteCustomClass) findClass("TypeExchange"),
                    findProperty("typeExchangeName").getMapping(typeExchangeBYRField));

            ImportKey<?> typeExchangeForeignKey = new ImportKey((ConcreteCustomClass) findClass("TypeExchange"),
                    findProperty("typeExchangeName").getMapping(typeExchangeForeignField));

            ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) findClass("Currency"),
                    findProperty("currencyShortName").getMapping(currencyField));

            ImportKey<?> homeCurrencyKey = new ImportKey((ConcreteCustomClass) findClass("Currency"),
                    findProperty("currencyShortName").getMapping(homeCurrencyField));

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();

            props.add(new ImportProperty(typeExchangeBYRField, findProperty("nameTypeExchange").getMapping(typeExchangeBYRKey)));
            props.add(new ImportProperty(homeCurrencyField, findProperty("currencyTypeExchange").getMapping(typeExchangeBYRKey),
                    object(findClass("Currency")).getMapping(homeCurrencyKey)));
            props.add(new ImportProperty(rateField, findProperty("rateExchange").getMapping(typeExchangeBYRKey, currencyKey, dateField)));

            props.add(new ImportProperty(typeExchangeForeignField, findProperty("nameTypeExchange").getMapping(typeExchangeForeignKey)));
            props.add(new ImportProperty(currencyField, findProperty("currencyTypeExchange").getMapping(typeExchangeForeignKey),
                    object(findClass("Currency")).getMapping(currencyKey)));
            props.add(new ImportProperty(foreignRateField, findProperty("rateExchange").getMapping(typeExchangeForeignKey, homeCurrencyKey, dateField)));

            List<List<Object>> data = new ArrayList<List<Object>>();
            for (Exchange e : exchangesList) {
                data.add(Arrays.asList((Object) "НБРБ (BYR)", "НБРБ (" + e.currencyID + ")", e.currencyID, e.homeCurrencyID, e.exchangeRate, new BigDecimal(1 / e.exchangeRate.doubleValue()), e.date));
            }
            ImportTable table = new ImportTable(Arrays.asList(typeExchangeBYRField, typeExchangeForeignField, currencyField,
                    homeCurrencyField, rateField, foreignRateField, dateField), data);

            DataSession session = context.getSession();
            IntegrationService service = new IntegrationService(session, table, Arrays.asList(typeExchangeBYRKey,
                    typeExchangeForeignKey, currencyKey, homeCurrencyKey), props);
            service.synchronize(true, false);
            //session.apply(LM.getBL());
        }
    }

    private List<Exchange> importExchangesFromXML(Date dateFrom, Date dateTo, String shortNameCurrency) throws IOException, JDOMException, ParseException {
        SAXBuilder builder = new SAXBuilder();

        List<Exchange> exchangesList = new ArrayList<Exchange>();

        Document document = builder.build(new URL("http://www.nbrb.by/Services/XmlExRatesRef.aspx").openStream());
        Element rootNode = document.getRootElement();
        List list = rootNode.getChildren("Currency");

        for (int i = 0; i < list.size(); i++) {

            Element node = (Element) list.get(i);

            String charCode = node.getChildText("CharCode");
            String id = node.getAttributeValue("Id");

            if (shortNameCurrency.equals(charCode)) {
                Document exchangeDocument = builder.build(new URL("http://www.nbrb.by/Services/XmlExRatesDyn.aspx?curId=" + id
                        + "&fromDate=" + new SimpleDateFormat("MM/dd/yyyy").format(dateFrom)
                        + "&toDate=" + new SimpleDateFormat("MM/dd/yyyy").format(dateTo)).openStream());
                Element exchangeRootNode = exchangeDocument.getRootElement();
                List exchangeList = exchangeRootNode.getChildren("Record");

                for (int j = 0; j < exchangeList.size(); j++) {

                    Element exchangeNode = (Element) exchangeList.get(j);

                    exchangesList.add(new Exchange(charCode, "BLR", new Date(DateUtils.parseDate(exchangeNode.getAttributeValue("Date"), new String[]{"MM/dd/yyyy"}).getTime()),
                            new BigDecimal(exchangeNode.getChildText("Rate"))));
                }
                if (exchangesList.size() > 0)
                    return exchangesList;
            }
        }
        return exchangesList;
    }


}