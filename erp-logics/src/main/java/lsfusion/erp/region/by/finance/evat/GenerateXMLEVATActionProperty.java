package lsfusion.erp.region.by.finance.evat;

import com.google.common.base.Throwables;
import lsfusion.base.IOUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultExportXMLActionProperty;
import lsfusion.interop.action.ExportFileClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Iterator;

public class GenerateXMLEVATActionProperty extends DefaultExportXMLActionProperty {

    private final ClassPropertyInterface evatInterface;

    public GenerateXMLEVATActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        evatInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        DataObject evatObject = context.getDataKeyValue(evatInterface);
        sendEVAT(context, evatObject);
    }

    private void sendEVAT(ExecutionContext context, DataObject evatObject) {
        generateXML(context, evatObject);
    }

    private void generateXML(ExecutionContext context, DataObject evatObject) {
        File tmpFile = null;
        try {

            String status = getLastPart((String) findProperty("nameStatus[EVAT]").read(context, evatObject));

            String unpSender = trim((String) findProperty("unpSender[EVAT]").read(context, evatObject));

            Element rootElement = new Element("issuance");
            setAttribute(rootElement, "sender", unpSender);
            Document doc = new Document(rootElement);
            doc.setRootElement(rootElement);

            rootElement.addContent(createGeneralElement(context, evatObject, status));

            switch (status) {
                case "original":
                case "fixed":
                case "additionalNoRef":
                    rootElement.addContent(createProviderElement(context, evatObject));
                    rootElement.addContent(createRecipientElement(context, evatObject));
                    rootElement.addContent(createSenderReceiverElement(context, evatObject));
                    rootElement.addContent(createDeliveryConditionElement(context, evatObject));
                    rootElement.addContent(createRosterElement(context, evatObject));
                    break;
                case "additional":
                    rootElement.addContent(createRosterElement(context, evatObject));
                case "cancelled":
                    break;
            }

            tmpFile = File.createTempFile("evat", "xml");
            outputXml(doc, new OutputStreamWriter(new FileOutputStream(tmpFile), "utf-8"), "utf-8");
            context.delayUserInterfaction(new ExportFileClientAction("evat.xml", IOUtils.getFileBytes(tmpFile)));

        } catch (IOException | ScriptingErrorLog.SemanticErrorException | SQLException | SQLHandledException e) {
            throw Throwables.propagate(e);
        } finally {
            if (tmpFile != null && !tmpFile.delete())
                tmpFile.deleteOnExit();
        }
    }

    private String getLastPart(String value) {
        if(value != null) {
            String[] split = trim(value).split("\\.");
            return split[split.length - 1];
        } else return null;
    }

    private String getDescription(String value) {
        String result = null;
        if (value != null) {
            switch (value) {
                case "deductionInFull":
                    result = "DEDUCTION_IN_FULL";
                    break;
                case "vatExcemption":
                    result = "VAT_EXEMPTION";
                    break;
                case "outsideRB":
                    result = "OUTSIDE_RB";
                    break;
                case "importVAT":
                    result = "IMPORT_VAT";
                    break;
            }
        }
        return result;
    }

    //parent: rootElement
    private Element createGeneralElement(ExecutionContext context, DataObject evatObject, String status) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Element generalElement = new Element("general");

        String number = trim((String)findProperty("number[EVAT]").read(context, evatObject));
        String invoice = trim((String)findProperty("invoice[EVAT]").read(context, evatObject));
        String dateIssuance = formatDate(new Date(System.currentTimeMillis()));
        String dateTransaction = formatDate((Date)findProperty("date[EVAT]").read(context, evatObject));
        String dateCancelled = formatDate((Date)findProperty("dateCancelled[EVAT]").read(context, evatObject));

        switch (status) {
            case "original":
                addStringElement(generalElement, "number", number);
                addStringElement(generalElement, "dateIssuance", dateIssuance);
                addStringElement(generalElement, "dateTransaction", dateTransaction);
                addStringElement(generalElement, "documentType", "ORIGINAL");
                break;
            case "fixed":
                addStringElement(generalElement, "dateIssuance", dateIssuance);
                addStringElement(generalElement, "dateTransaction", dateTransaction);
                addStringElement(generalElement, "documentType", "FIXED");
                addStringElement(generalElement, "invoice", invoice);
                addStringElement(generalElement, "dateCancelled", dateCancelled);
                break;
            case "additional":
                boolean sendToRecipient = findProperty("sendToRecipient[EVAT]").read(context, evatObject) != null;

                addStringElement(generalElement, "number", number);
                addStringElement(generalElement, "dateIssuance", dateIssuance);
                addStringElement(generalElement, "dateTransaction", dateTransaction);
                addStringElement(generalElement, "documentType", "ADDITIONAL");
                addStringElement(generalElement, "invoice", invoice);
                addBooleanElement(generalElement, "sendToRecipient", sendToRecipient);
                break;
            case "additionalNoRef":
                addStringElement(generalElement, "number", number);
                addStringElement(generalElement, "dateIssuance", dateIssuance);
                addStringElement(generalElement, "dateTransaction", dateTransaction);
                addStringElement(generalElement, "documentType", "ADD_NO_REFERENCE");
                break;
            case "cancelled":
                addStringElement(generalElement, "invoice", invoice);
                addStringElement(generalElement, "documentType", "ORIGINAL");
                addStringElement(generalElement, "dateCancelled", dateCancelled);
                break;
        }
        return generalElement;
    }

    //parent: rootElement
    private Element createProviderElement(ExecutionContext context, DataObject evatObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        boolean dependentPerson = findProperty("dependentPersonSupplier[EVAT]").read(context, evatObject) != null;
        boolean residentsOfOffshore = findProperty("residentsOfOffshoreSupplier[EVAT]").read(context, evatObject) != null;
        boolean specialDealGoods = findProperty("specialDealGoodsSupplier[EVAT]").read(context, evatObject) != null;
        boolean bigCompany = findProperty("bigCompanySupplier[EVAT]").read(context, evatObject) != null;
        String countryCode = trim((String) findProperty("countryCodeSupplier[EVAT]").read(context, evatObject));
        String unp = trim((String) findProperty("unpSupplier[EVAT]").read(context, evatObject));
        String name = trim((String) findProperty("nameSupplier[EVAT]").read(context, evatObject));
        String idSupplier = trim((String) findProperty("idSupplier[EVAT]").read(context, evatObject));
        String address = trim((String) findProperty("addressSupplier[EVAT]").read(context, evatObject));
        String numberInvoicePrincipal = trim((String) findProperty("numberInvoicePrincipal[EVAT]").read(context, evatObject));
        String dateInvoicePrincipal = formatDate((Date) findProperty("dateInvoicePrincipal[EVAT]").read(context, evatObject));
        String numberInvoiceVendor = trim((String) findProperty("numberInvoiceVendor[EVAT]").read(context, evatObject));
        String dateInvoiceVendor = formatDate((Date) findProperty("dateInvoiceVendor[EVAT]").read(context, evatObject));
        String declaration = trim((String) findProperty("declarationSupplier[EVAT]").read(context, evatObject));
        String dateRelease = formatDate((Date) findProperty("dateReleaseSupplier[EVAT]").read(context, evatObject));
        String dateActualExport = formatDate((Date) findProperty("dateActualExportSupplier[EVAT]").read(context, evatObject));
        String numberTaxes = trim((String) findProperty("numberTaxesSupplier[EVAT]").read(context, evatObject));
        String dateTaxes = formatDate((Date) findProperty("dateTaxesSupplier[EVAT]").read(context, evatObject));

        Element providerElement = new Element("provider");
        addStringElement(providerElement, "providerStatus", "SELLER");
        addBooleanElement(providerElement, "dependentPerson", dependentPerson);
        addBooleanElement(providerElement, "residentsOfOffshore", residentsOfOffshore);
        addBooleanElement(providerElement, "specialDealGoods", specialDealGoods);
        addBooleanElement(providerElement, "bigCompany", bigCompany);
        addStringElement(providerElement, "countryCode", countryCode);
        addStringElement(providerElement, "unp", unp);
        addStringElement(providerElement, "branchCode", idSupplier);
        addStringElement(providerElement, "name", name);
        addStringElement(providerElement, "address", address);
        providerElement.addContent(createNumberDateElement("principal", numberInvoicePrincipal, dateInvoicePrincipal));
        providerElement.addContent(createNumberDateElement("vendor", numberInvoiceVendor, dateInvoiceVendor));
        addStringElement(providerElement, "declaration", declaration);
        addStringElement(providerElement, "dateRelease", dateRelease);
        addStringElement(providerElement, "dateActualExport", dateActualExport);
        providerElement.addContent(createNumberDateElement("taxes", numberTaxes, dateTaxes));
        return providerElement;
    }

    //parent: rootElement
    private Element createRecipientElement(ExecutionContext context, DataObject evatObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        boolean dependentPerson = findProperty("dependentPersonCustomer[EVAT]").read(context, evatObject) != null;
        boolean residentsOfOffshore = findProperty("residentsOfOffshoreCustomer[EVAT]").read(context, evatObject) != null;
        boolean specialDealGoods = findProperty("specialDealGoodsCustomer[EVAT]").read(context, evatObject) != null;
        boolean bigCompany = findProperty("bigCompanyCustomer[EVAT]").read(context, evatObject) != null;
        String countryCode = trim((String) findProperty("countryCodeCustomer[EVAT]").read(context, evatObject));
        String unp = trim((String) findProperty("unpCustomer[EVAT]").read(context, evatObject));
        String name = trim((String) findProperty("nameCustomer[EVAT]").read(context, evatObject));
        String idCustomer = trim((String) findProperty("idCustomer[EVAT]").read(context, evatObject));
        String address = trim((String) findProperty("addressCustomer[EVAT]").read(context, evatObject));
        String declaration = trim((String) findProperty("declarationCustomer[EVAT]").read(context, evatObject));
        String numberTaxes = trim((String) findProperty("numberTaxesCustomer[EVAT]").read(context, evatObject));
        String dateTaxes = formatDate((Date) findProperty("dateTaxesCustomer[EVAT]").read(context, evatObject));
        String dateImport = formatDate((Date) findProperty("dateImportCustomer[EVAT]").read(context, evatObject));

        Element recipientElement = new Element("recipient");
        addStringElement(recipientElement, "recipientStatus", "CUSTOMER");
        addBooleanElement(recipientElement, "dependentPerson", dependentPerson);
        addBooleanElement(recipientElement, "residentsOfOffshore", residentsOfOffshore);
        addBooleanElement(recipientElement, "specialDealGoods", specialDealGoods);
        addBooleanElement(recipientElement, "bigCompany", bigCompany);
        addStringElement(recipientElement, "countryCode", countryCode);
        addStringElement(recipientElement, "unp", unp);
        addStringElement(recipientElement, "branchCode", idCustomer);
        addStringElement(recipientElement, "name", name);
        addStringElement(recipientElement, "address", address);
        addStringElement(recipientElement, "declaration", declaration);
        recipientElement.addContent(createNumberDateElement("taxes", numberTaxes, dateTaxes));
        addStringElement(recipientElement, "dateImport", dateImport);
        return recipientElement;
    }

    //parent: rootElement
    private Element createSenderReceiverElement(ExecutionContext context, DataObject evatObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String countryCodeSupplier = trim((String) findProperty("countryCodeSupplier[EVAT]").read(context, evatObject));
        String unpSupplier = trim((String) findProperty("unpSupplier[EVAT]").read(context, evatObject));
        String nameSupplier = trim((String) findProperty("nameSupplier[EVAT]").read(context, evatObject));
        String addressSupplier = trim((String) findProperty("addressSupplier[EVAT]").read(context, evatObject));

        String countryCodeCustomer = trim((String) findProperty("countryCodeCustomer[EVAT]").read(context, evatObject));
        String unpCustomer = trim((String) findProperty("unpCustomer[EVAT]").read(context, evatObject));
        String nameCustomer = trim((String) findProperty("nameCustomer[EVAT]").read(context, evatObject));
        String addressCustomer = trim((String) findProperty("addressCustomer[EVAT]").read(context, evatObject));

        Element senderReceiverElement = new Element("senderReceiver");
        Element consignorsElement = new Element("consignors");
        consignorsElement.addContent(createLegalEntityElement("consignor", countryCodeSupplier, unpSupplier, nameSupplier, addressSupplier));
        senderReceiverElement.addContent(consignorsElement);
        Element consigneesElement = new Element("consignees");
        consigneesElement.addContent(createLegalEntityElement("consignee", countryCodeCustomer, unpCustomer, nameCustomer, addressCustomer));
        senderReceiverElement.addContent(consigneesElement);
        return senderReceiverElement;
    }

    //parent: rootElement
    private Element createDeliveryConditionElement(ExecutionContext context, DataObject evatObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        String contractNumber = trim((String) findProperty("numberContract[EVAT]").read(context, evatObject));
        String contractDate = formatDate((Date) findProperty("dateContract[EVAT]").read(context, evatObject));
        String codeDocType = trim((String) findProperty("codeDocType[EVAT]").read(context, evatObject));
        String valueDocType = trim((String) findProperty("valueDocType[EVAT]").read(context, evatObject));
        String blankCode = trim((String) findProperty("blankCodeDoc[EVAT]").read(context, evatObject));
        String series = trim((String) findProperty("seriesDoc[EVAT]").read(context, evatObject));
        String number = trim((String) findProperty("numberDoc[EVAT]").read(context, evatObject));
        String description = trim((String) findProperty("descriptionDoc[EVAT]").read(context, evatObject));

        Element deliveryConditionElement = new Element("deliveryCondition");
        Element contractElement = createNumberDateElement("contract", contractNumber, contractDate);
        Element documentsElement = new Element("documents");
        Element documentElement = new Element("document");
        Element docTypeElement = new Element("docType");
        addStringElement(docTypeElement, "code", codeDocType);
        addStringElement(docTypeElement, "value", valueDocType);
        documentElement.addContent(docTypeElement);
        addStringElement(documentElement, "date", contractDate);
        addStringElement(documentElement, "blankCode", blankCode);
        addStringElement(documentElement, "seria", series);
        addStringElement(documentElement, "number", number);
        documentsElement.addContent(documentElement);
        contractElement.addContent(documentsElement);
        deliveryConditionElement.addContent(contractElement);
        addStringElement(deliveryConditionElement, "description", description);
        return deliveryConditionElement;
    }

    //parent: rootElement
    private Element createRosterElement(ExecutionContext context, DataObject evatObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        BigDecimal totalSum = (BigDecimal) findProperty("totalSum[EVAT]").read(context, evatObject);
        BigDecimal totalExciseSum = (BigDecimal) findProperty("totalExciseSum[EVAT]").read(context, evatObject);
        BigDecimal totalVATSum = (BigDecimal) findProperty("totalVATSum[EVAT]").read(context, evatObject);
        BigDecimal totalSumWithVAT = (BigDecimal) findProperty("totalSumWithVAT[EVAT]").read(context, evatObject);

        Element rosterElement = new Element("roster");
        rosterElement.setAttribute("totalCostVat", getString(totalSumWithVAT));
        rosterElement.setAttribute("totalExcise", getString(totalExciseSum));
        rosterElement.setAttribute("totalVat", getString(totalVATSum));
        rosterElement.setAttribute("totalCost", getString(totalSum));

        KeyExpr itemExpr = new KeyExpr("item");
        ImRevMap<Object, KeyExpr> itemKeys = MapFact.singletonRev((Object) "item", itemExpr);

        QueryBuilder<Object, Object> itemQuery = new QueryBuilder<>(itemKeys);
        String[] itemNames = new String[]{"name", "idBarcode", "shortNameUOM"};
        LCP[] itemProperties = findProperties("name[Item]", "idBarcode[Item]", "shortNameUOM[Item]");
        for (int i = 0; i < itemProperties.length; i++) {
            itemQuery.addProperty(itemNames[i], itemProperties[i].getExpr(itemExpr));
        }
        String[] itemEVATNames = new String[]{"codeOCed", "quantity", "price", "sum", "exciseSum",
                "vatRate", "vatSum", "sumWithVAT", "nameDescriptionType"};
        LCP[] itemEVATProperties = findProperties("codeOced[Item, EVAT]", "quantity[Item, EVAT]", "price[Item, EVAT]", "sum[Item, EVAT]", "exciseSum[Item, EVAT]",
                "vatRate[Item, EVAT]", "vatSum[Item, EVAT]", "sumWithVAT[Item, EVAT]", "nameDescriptionType[Item, EVAT]");
        for (int i = 0; i < itemEVATProperties.length; i++) {
            itemQuery.addProperty(itemEVATNames[i], itemEVATProperties[i].getExpr(itemExpr, evatObject.getExpr()));
        }
        itemQuery.and(findProperty("in[Item, EVAT]").getExpr(itemExpr, evatObject.getExpr()).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> itemResult = itemQuery.execute(context);
        int count = 0;
        for (ImMap<Object, Object> entry : itemResult.values()) {
            count++;
            String name = trim((String) entry.get("name"));
            String idBarcode = trim((String) entry.get("idBarcode"));
            String shortNameUOM = trim((String) entry.get("shortNameUOM")); //должен быть Integer
            Integer codeOced = (Integer) entry.get("codeOced");
            BigDecimal quantity = (BigDecimal) entry.get("quantity");
            BigDecimal price = (BigDecimal) entry.get("price");
            BigDecimal sum = (BigDecimal) entry.get("sum");
            BigDecimal exciseSum = (BigDecimal) entry.get("exciseSum");
            BigDecimal vatRate = (BigDecimal) entry.get("vatRate");
            BigDecimal vatSum = (BigDecimal) entry.get("vatSum");
            BigDecimal sumWithVat = (BigDecimal) entry.get("sumWithVAT");

            String description = getDescription(getLastPart(trim((String) entry.get("nameDescriptionType"))));

            Element rosterItemElement = new Element("rosterItem");
            addStringElement(rosterItemElement, "number", getString(count));
            addStringElement(rosterItemElement, "name", name);
            addStringElement(rosterItemElement, "code", idBarcode);
            addStringElement(rosterItemElement, "code_oced", getString(codeOced));
            addStringElement(rosterItemElement, "units", shortNameUOM);
            addBigDecimalElement(rosterItemElement, "count", quantity);
            addBigDecimalElement(rosterItemElement, "price", price);
            addBigDecimalElement(rosterItemElement, "cost", sum);
            addBigDecimalElement(rosterItemElement, "summaExcise", exciseSum);
            Element vatElement = new Element("vat");
            addBigDecimalElement(vatElement, "rate", vatRate);
            addStringElement(vatElement, "rateType", vatRate != null && vatRate.equals(BigDecimal.ZERO) ? "ZERO" : "DECIMAL");
            addBigDecimalElement(vatElement, "summaVat", vatSum);
            rosterItemElement.addContent(vatElement);
            addBigDecimalElement(rosterItemElement, "costVat", sumWithVat);
            Element descriptionsElement = new Element("descriptions");
            addStringElement(descriptionsElement, "description", description);
            rosterItemElement.addContent(descriptionsElement);
            rosterElement.addContent(rosterItemElement);
        }
        return rosterElement;
    }

    private Element createNumberDateElement(String name, String numberInvoice, String dateInvoice) {
        Element element = new Element(name);
        addStringElement(element, "number", numberInvoice);
        addStringElement(element, "date", dateInvoice);
        return element;
    }

    private Element createLegalEntityElement(String name, String countryCode, String unp, String nameLegalEntity, String address) {
        Element element = new Element(name);
        addStringElement(element, "countryCode", countryCode);
        addStringElement(element, "unp", unp);
        addStringElement(element, "name", nameLegalEntity);
        addStringElement(element, "address", address);
        return element;
    }

    private String formatDate(Date date) {
        return date == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(date);
    }

    private String getString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}