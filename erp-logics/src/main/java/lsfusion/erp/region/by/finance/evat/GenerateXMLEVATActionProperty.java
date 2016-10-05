package lsfusion.erp.region.by.finance.evat;

import com.google.common.base.Throwables;
import lsfusion.base.BaseUtils;
import lsfusion.base.IOUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultExportXMLActionProperty;
import lsfusion.interop.Compare;
import lsfusion.interop.action.ExportFileClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

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
        generateXML(context, evatObject, true);
    }

    protected Map<String, Map<Integer, String>> getInvoices(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, Map<Integer, String>> evatMap = new HashMap<>();
        KeyExpr evatExpr = new KeyExpr("evat");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "evat", evatExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("unp", findProperty("unpSupplier[EVAT]").getExpr(context.getModifier(), evatExpr));
        query.addProperty("number", findProperty("number[EVAT]").getExpr(context.getModifier(), evatExpr));
        query.and(findProperty("in[EVAT]").getExpr(context.getModifier(), evatExpr).getWhere());
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(context);
        for (int i = 0; i < result.values().size(); i++) {
            DataObject evatObject = result.getKey(i).get("evat");
            String unp = (String) result.getValue(i).get("unp").getValue();
            String number = (String) result.getValue(i).get("number").getValue();
            if (unp != null && number != null) {
                Map<Integer, String> invoiceNumbers = evatMap.get(unp);
                if (invoiceNumbers == null)
                    invoiceNumbers = new HashMap<>();
                invoiceNumbers.put((Integer) evatObject.getValue(), getFullNumber(unp, number));
                evatMap.put(unp, invoiceNumbers);
            }
        }
        return evatMap;
    }

    protected Map<String, Map<Integer, byte[]>> generateXMLs(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, Map<Integer, byte[]>> files = new HashMap<>();
        KeyExpr evatExpr = new KeyExpr("evat");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "evat", evatExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("unp", findProperty("unpSupplier[EVAT]").getExpr(context.getModifier(), evatExpr));
        query.and(findProperty("in[EVAT]").getExpr(context.getModifier(), evatExpr).getWhere());
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(context);
        for (int i = 0; i < result.values().size(); i++) {
            DataObject evatObject = result.getKey(i).get("evat");
            String unp = (String) result.getValue(i).get("unp").getValue();
            if(unp != null) {
                Map<Integer, byte[]> filesEntry = files.get(unp);
                if(filesEntry == null)
                    filesEntry = new HashMap<>();
                filesEntry.put((Integer) evatObject.getValue(), generateXML(context, evatObject, false));
                files.put(unp, filesEntry);
            }
        }
        return files;
    }

    protected byte[] generateXML(ExecutionContext context, DataObject evatObject, boolean choosePath) {
        File tmpFile = null;
        try {

            String status = getLastPart((String) findProperty("nameStatus[EVAT]").read(context, evatObject));

            String unpSender = trim((String) findProperty("unpSender[EVAT]").read(context, evatObject));

            String number = trim((String) findProperty("number[EVAT]").read(context, evatObject), "");
            String documentNumber = getFullNumber(unpSender, number);

            Namespace xmlns = Namespace.getNamespace("http://www.w3schools.com");
            Namespace xs = Namespace.getNamespace("xs", "http://www.w3.org/2001/XMLSchema");
            Namespace xsi = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");

            Element rootElement = new Element("issuance");
            rootElement.setNamespace(xmlns);
            rootElement.addNamespaceDeclaration(xs);
            rootElement.addNamespaceDeclaration(xsi);
            setAttribute(rootElement, "sender", unpSender);
            Document doc = new Document(rootElement);
            doc.setRootElement(rootElement);

            Namespace namespace = rootElement.getNamespace();

            rootElement.addContent(createGeneralElement(context, evatObject, status, documentNumber, namespace));

            switch (status) {
                case "original":
                case "fixed":
                case "additionalNoRef":
                    rootElement.addContent(createProviderElement(context, evatObject, namespace));
                    rootElement.addContent(createRecipientElement(context, evatObject, namespace));
                    boolean skipDeliveryCondition = findProperty("skipDeliveryCondition[EVAT]").read(context, evatObject) != null;
                    if(!skipDeliveryCondition) {
                        rootElement.addContent(createSenderReceiverElement(context, evatObject, namespace));
                        rootElement.addContent(createDeliveryConditionElement(context, evatObject, namespace));
                    }
                    rootElement.addContent(createRosterElement(context, evatObject, namespace));
                    break;
                case "additional":
                    rootElement.addContent(createRosterElement(context, evatObject, namespace));
                case "cancelled":
                    break;
            }

            tmpFile = File.createTempFile("evat", "xml");
            outputXml(doc, new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"), "UTF-8");
            byte[] fileData = IOUtils.getFileBytes(tmpFile);
            if(choosePath)
                context.delayUserInterfaction(new ExportFileClientAction(documentNumber + ".xml", fileData));
            return fileData;

        } catch (IOException | ScriptingErrorLog.SemanticErrorException | SQLException | SQLHandledException e) {
            throw Throwables.propagate(e);
        } finally {
            if (choosePath && tmpFile != null && !tmpFile.delete())
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
    private Element createGeneralElement(ExecutionContext context, DataObject evatObject, String status, String documentNumber, Namespace namespace) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Element generalElement = new Element("general");

        String number = trim((String) findProperty("number[EVAT]").read(context, evatObject));
        String invoice = trim((String) findProperty("invoice[EVAT]").read(context, evatObject));
        String dateIssuance = formatDate(new Date(System.currentTimeMillis()));
        String dateTransaction = formatDate((Date) findProperty("date[EVAT]").read(context, evatObject));
        String dateCancelled = formatDate((Date) findProperty("dateCancelled[EVAT]").read(context, evatObject));

        switch (status) {
            case "original":
                addStringElement(namespace, generalElement, "number", documentNumber);
                addStringElement(namespace, generalElement, "dateIssuance", dateIssuance);
                addStringElement(namespace, generalElement, "dateTransaction", dateTransaction);
                addStringElement(namespace, generalElement, "documentType", "ORIGINAL");
                break;
            case "fixed":
                addStringElement(namespace, generalElement, "dateIssuance", dateIssuance);
                addStringElement(namespace, generalElement, "dateTransaction", dateTransaction);
                addStringElement(namespace, generalElement, "documentType", "FIXED");
                addStringElement(namespace, generalElement, "invoice", invoice);
                addStringElement(namespace, generalElement, "dateCancelled", dateCancelled);
                break;
            case "additional":
                boolean sendToRecipient = findProperty("sendToRecipient[EVAT]").read(context, evatObject) != null;

                addStringElement(namespace, generalElement, "number", number);
                addStringElement(namespace, generalElement, "dateIssuance", dateIssuance);
                addStringElement(namespace, generalElement, "dateTransaction", dateTransaction);
                addStringElement(namespace, generalElement, "documentType", "ADDITIONAL");
                addStringElement(namespace, generalElement, "invoice", invoice);
                addBooleanElement(namespace, generalElement, "sendToRecipient", sendToRecipient);
                break;
            case "additionalNoRef":
                addStringElement(namespace, generalElement, "number", number);
                addStringElement(namespace, generalElement, "dateIssuance", dateIssuance);
                addStringElement(namespace, generalElement, "dateTransaction", dateTransaction);
                addStringElement(namespace, generalElement, "documentType", "ADD_NO_REFERENCE");
                break;
            case "cancelled":
                addStringElement(namespace, generalElement, "invoice", invoice);
                addStringElement(namespace, generalElement, "documentType", "ORIGINAL");
                addStringElement(namespace, generalElement, "dateCancelled", dateCancelled);
                break;
        }
        generalElement.setNamespace(namespace);
        return generalElement;
    }

    //parent: rootElement
    private Element createProviderElement(ExecutionContext context, DataObject evatObject, Namespace namespace) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        String legalEntityStatus = trim((String) findProperty("nameLegalEntityStatusSupplier[EVAT]").read(context, evatObject));
        boolean dependentPerson = findProperty("dependentPersonSupplier[EVAT]").read(context, evatObject) != null;
        boolean residentsOfOffshore = findProperty("residentsOfOffshoreSupplier[EVAT]").read(context, evatObject) != null;
        boolean specialDealGoods = findProperty("specialDealGoodsSupplier[EVAT]").read(context, evatObject) != null;
        boolean bigCompany = findProperty("bigCompanySupplier[EVAT]").read(context, evatObject) != null;
        String countryCode = trim((String) findProperty("countryCodeSupplier[EVAT]").read(context, evatObject));
        String unp = trim((String) findProperty("unpSupplier[EVAT]").read(context, evatObject));
        String name = trim((String) findProperty("nameSupplier[EVAT]").read(context, evatObject));
        Integer branchCodeSupplier = (Integer) findProperty("branchCodeSupplier[EVAT]").read(context, evatObject);
        String address = trim((String) findProperty("addressSupplier[EVAT]").read(context, evatObject));
        //String numberInvoicePrincipal = trim((String) findProperty("numberInvoicePrincipal[EVAT]").read(context, evatObject));
        //String dateInvoicePrincipal = formatDate((Date) findProperty("dateInvoicePrincipal[EVAT]").read(context, evatObject));
        //String numberInvoiceVendor = trim((String) findProperty("numberInvoiceVendor[EVAT]").read(context, evatObject));
        //String dateInvoiceVendor = formatDate((Date) findProperty("dateInvoiceVendor[EVAT]").read(context, evatObject));
        String declaration = trim((String) findProperty("declarationSupplier[EVAT]").read(context, evatObject));
        String dateRelease = formatDate((Date) findProperty("dateReleaseSupplier[EVAT]").read(context, evatObject));
        String dateActualExport = formatDate((Date) findProperty("dateActualExportSupplier[EVAT]").read(context, evatObject));
        String numberTaxes = trim((String) findProperty("numberTaxesSupplier[EVAT]").read(context, evatObject));
        String dateTaxes = formatDate((Date) findProperty("dateTaxesSupplier[EVAT]").read(context, evatObject));

        Element providerElement = new Element("provider");
        addStringElement(namespace, providerElement, "providerStatus",  getProviderStatus(legalEntityStatus, "SELLER"));
        addBooleanElement(namespace, providerElement, "dependentPerson", dependentPerson);
        addBooleanElement(namespace, providerElement, "residentsOfOffshore", residentsOfOffshore);
        addBooleanElement(namespace, providerElement, "specialDealGoods", specialDealGoods);
        addBooleanElement(namespace, providerElement, "bigCompany", bigCompany);
        addStringElement(namespace, providerElement, "countryCode", countryCode);
        addStringElement(namespace, providerElement, "unp", unp);
        addIntegerElement(namespace, providerElement, "branchCode", branchCodeSupplier);
        addStringElement(namespace, providerElement, "name", name);
        addStringElement(namespace, providerElement, "address", address);
        //с ними не проходит
        //providerElement.addContent(createNumberDateElement("principal", numberInvoicePrincipal, dateInvoicePrincipal, namespace));
        //providerElement.addContent(createNumberDateElement("vendor", numberInvoiceVendor, dateInvoiceVendor, namespace));
        addStringElement(namespace, providerElement, "declaration", declaration);
        addStringElement(namespace, providerElement, "dateRelease", dateRelease);
        addStringElement(namespace, providerElement, "dateActualExport", dateActualExport);
        if(numberTaxes != null)
            providerElement.addContent(createNumberDateElement("taxes", numberTaxes, dateTaxes, namespace));
        providerElement.setNamespace(namespace);
        return providerElement;
    }

    //parent: rootElement
    private Element createRecipientElement(ExecutionContext context, DataObject evatObject, Namespace namespace) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        String legalEntityStatus = trim((String) findProperty("nameLegalEntityStatusCustomer[EVAT]").read(context, evatObject));
        boolean dependentPerson = findProperty("dependentPersonCustomer[EVAT]").read(context, evatObject) != null;
        boolean residentsOfOffshore = findProperty("residentsOfOffshoreCustomer[EVAT]").read(context, evatObject) != null;
        boolean specialDealGoods = findProperty("specialDealGoodsCustomer[EVAT]").read(context, evatObject) != null;
        boolean bigCompany = findProperty("bigCompanyCustomer[EVAT]").read(context, evatObject) != null;
        String countryCode = trim((String) findProperty("countryCodeCustomer[EVAT]").read(context, evatObject));
        String unp = trim((String) findProperty("unpCustomer[EVAT]").read(context, evatObject));
        String name = trim((String) findProperty("nameCustomer[EVAT]").read(context, evatObject));
        Integer branchCodeCustomer = (Integer) findProperty("branchCodeCustomer[EVAT]").read(context, evatObject);
        String address = trim((String) findProperty("addressCustomer[EVAT]").read(context, evatObject));
        String declaration = trim((String) findProperty("declarationCustomer[EVAT]").read(context, evatObject));
        String numberTaxes = trim((String) findProperty("numberTaxesCustomer[EVAT]").read(context, evatObject));
        String dateTaxes = formatDate((Date) findProperty("dateTaxesCustomer[EVAT]").read(context, evatObject));
        String dateImport = formatDate((Date) findProperty("dateImportCustomer[EVAT]").read(context, evatObject));

        Element recipientElement = new Element("recipient");
        addStringElement(namespace, recipientElement, "recipientStatus", getProviderStatus(legalEntityStatus, "CUSTOMER"));
        addBooleanElement(namespace, recipientElement, "dependentPerson", dependentPerson);
        addBooleanElement(namespace, recipientElement, "residentsOfOffshore", residentsOfOffshore);
        addBooleanElement(namespace, recipientElement, "specialDealGoods", specialDealGoods);
        addBooleanElement(namespace, recipientElement, "bigCompany", bigCompany);
        addStringElement(namespace, recipientElement, "countryCode", countryCode);
        addStringElement(namespace, recipientElement, "unp", unp);
        addIntegerElement(namespace, recipientElement, "branchCode", branchCodeCustomer);
        addStringElement(namespace, recipientElement, "name", name);
        addStringElement(namespace, recipientElement, "address", address);
        addStringElement(namespace, recipientElement, "declaration", declaration);
        if(numberTaxes != null)
            recipientElement.addContent(createNumberDateElement("taxes", numberTaxes, dateTaxes, namespace));
        addStringElement(namespace, recipientElement, "dateImport", dateImport);
        recipientElement.setNamespace(namespace);
        return recipientElement;
    }

    //parent: rootElement
    private Element createSenderReceiverElement(ExecutionContext context, DataObject evatObject, Namespace namespace) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String countryCodeSupplier = trim((String) findProperty("countryCodeSupplier[EVAT]").read(context, evatObject));
        String unpSupplier = trim((String) findProperty("unpSupplier[EVAT]").read(context, evatObject));
        String nameSupplier = trim((String) findProperty("nameSupplier[EVAT]").read(context, evatObject));
        String addressSupplier = trim((String) findProperty("addressSupplier[EVAT]").read(context, evatObject));

        String countryCodeCustomer = trim((String) findProperty("countryCodeCustomer[EVAT]").read(context, evatObject));
        String unpCustomer = trim((String) findProperty("unpCustomer[EVAT]").read(context, evatObject));
        String nameCustomer = trim((String) findProperty("nameCustomer[EVAT]").read(context, evatObject));
        String addressCustomer = trim((String) findProperty("addressCustomer[EVAT]").read(context, evatObject));

        Element senderReceiverElement = new Element("senderReceiver");
        Element consignorsElement = new Element("consignors", namespace);
        consignorsElement.addContent(createLegalEntityElement("consignor", countryCodeSupplier, unpSupplier, nameSupplier, addressSupplier, namespace));
        senderReceiverElement.addContent(consignorsElement);
        Element consigneesElement = new Element("consignees", namespace);
        consigneesElement.addContent(createLegalEntityElement("consignee", countryCodeCustomer, unpCustomer, nameCustomer, addressCustomer, namespace));
        senderReceiverElement.addContent(consigneesElement);
        senderReceiverElement.setNamespace(namespace);
        return senderReceiverElement;
    }

    //parent: rootElement
    private Element createDeliveryConditionElement(ExecutionContext context, DataObject evatObject, Namespace namespace) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        String contractNumber = trim((String) findProperty("numberContract[EVAT]").read(context, evatObject));
        String contractDate = formatDate((Date) findProperty("dateContract[EVAT]").read(context, evatObject));
        String date = formatDate((Date) findProperty("date[EVAT]").read(context, evatObject));
        Integer codeDocType = (Integer) findProperty("codeDocType[EVAT]").read(context, evatObject);
        String valueDocType = trim((String) findProperty("valueDocType[EVAT]").read(context, evatObject));
        String blankCode = trim((String) findProperty("blankCodeDoc[EVAT]").read(context, evatObject));
        String series = trim((String) findProperty("seriesDoc[EVAT]").read(context, evatObject));
        String number = trim((String) findProperty("numberDoc[EVAT]").read(context, evatObject));
        String description = trim((String) findProperty("descriptionDoc[EVAT]").read(context, evatObject));

        Element deliveryConditionElement = new Element("deliveryCondition");
        Element contractElement = createNumberDateElement("contract", contractNumber, contractDate, namespace);
        Element documentsElement = new Element("documents", namespace);
        Element documentElement = new Element("document", namespace);
        Element docTypeElement = new Element("docType", namespace);
        addIntegerElement(namespace, docTypeElement, "code", codeDocType);
        addStringElement(namespace, docTypeElement, "value", valueDocType);
        documentElement.addContent(docTypeElement);
        addStringElement(namespace, documentElement, "date", date);
        addStringElement(namespace, documentElement, "blankCode", blankCode);
        addStringElement(namespace, documentElement, "seria", series);
        addStringElement(namespace, documentElement, "number", number);
        documentsElement.addContent(documentElement);
        contractElement.addContent(documentsElement);
        deliveryConditionElement.addContent(contractElement);
        addStringElement(namespace, deliveryConditionElement, "description", description);
        deliveryConditionElement.setNamespace(namespace);
        return deliveryConditionElement;
    }

    //parent: rootElement
    private Element createRosterElement(ExecutionContext context, DataObject evatObject, Namespace namespace) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        BigDecimal totalSum = (BigDecimal) findProperty("totalSum[EVAT]").read(context, evatObject);
        BigDecimal totalExciseSum = (BigDecimal) findProperty("totalExciseSum[EVAT]").read(context, evatObject);
        BigDecimal totalVATSum = (BigDecimal) findProperty("totalVATSum[EVAT]").read(context, evatObject);
        BigDecimal totalSumWithVAT = (BigDecimal) findProperty("totalSumWithVAT[EVAT]").read(context, evatObject);

        Element rosterElement = new Element("roster");
        rosterElement.setAttribute("totalCostVat", bigDecimalToString(totalSumWithVAT, "0"));
        rosterElement.setAttribute("totalExcise", bigDecimalToString(totalExciseSum, "0"));
        rosterElement.setAttribute("totalVat", bigDecimalToString(totalVATSum, "0"));
        rosterElement.setAttribute("totalCost", bigDecimalToString(totalSum, "0"));

        KeyExpr evatDetailExpr = new KeyExpr("evatDetail");
        ImRevMap<Object, KeyExpr> evatDetailKeys = MapFact.singletonRev((Object) "evatDetail", evatDetailExpr);

        QueryBuilder<Object, Object> evatDetailQuery = new QueryBuilder<>(evatDetailKeys);
        String[] evatDetailNames = new String[]{"objValue", "name", "code", "shortNameUOM", "codeOced",
                "quantity", "price", "sum", "exciseSum", "vatRate", "vatSum", "sumWithVAT", "nameDescriptionType"};
        LCP[] evatDetailProperties = findProperties("objValue[EVATDetail]", "name[EVATDetail]", "code[EVATDetail]", "shortNameUOM[EVATDetail]",
                "codeOced[EVATDetail]", "quantity[EVATDetail]", "price[EVATDetail]", "sum[EVATDetail]", "exciseSum[EVATDetail]",
                "vatRate[EVATDetail]", "vatSum[EVATDetail]", "sumWithVAT[EVATDetail]", "nameDescriptionType[EVATDetail]");
        for (int i = 0; i < evatDetailProperties.length; i++) {
            evatDetailQuery.addProperty(evatDetailNames[i], evatDetailProperties[i].getExpr(evatDetailExpr));
        }
        evatDetailQuery.and(findProperty("evat[EVATDetail]").getExpr(evatDetailExpr).compare(evatObject.getExpr(), Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> evatDetailResults = evatDetailQuery.execute(context, MapFact.singletonOrder((Object) "objValue", false));
        for (int i = 0, size = evatDetailResults.size(); i < size; i++) {
            ImMap<Object, Object> entry = evatDetailResults.getValue(i);
            String name = trim((String) entry.get("name"));
            String code = trim((String) entry.get("code"));
            //String shortNameUOM = trim((String) entry.get("shortNameUOM")); //должен быть Integer
            Integer codeOced = (Integer) entry.get("codeOced");
            BigDecimal quantity = (BigDecimal) entry.get("quantity");
            BigDecimal price = (BigDecimal) entry.get("price");
            BigDecimal sum = (BigDecimal) entry.get("sum");
            BigDecimal exciseSum = (BigDecimal) entry.get("exciseSum");
            BigDecimal vatRate = (BigDecimal) entry.get("vatRate");
            BigDecimal vatSum = (BigDecimal) entry.get("vatSum");
            BigDecimal sumWithVat = (BigDecimal) entry.get("sumWithVAT");

            String description = getDescription(getLastPart(trim((String) entry.get("nameDescriptionType"))));

            Element rosterItemElement = new Element("rosterItem", namespace);
            addStringElement(namespace, rosterItemElement, "number", getString(i + 1));
            addStringElement(namespace, rosterItemElement, "name", name);
            addStringElement(namespace, rosterItemElement, "code", code);
            addStringElement(namespace, rosterItemElement, "code_oced", getString(codeOced));
            //addStringElement(namespace, rosterItemElement, "units", shortNameUOM);
            addBigDecimalElement(namespace, rosterItemElement, "count", quantity);
            addStringElement(namespace, rosterItemElement, "price", bigDecimalToString(price));
            addStringElement(namespace, rosterItemElement, "cost", bigDecimalToString(sum));
            addStringElement(namespace, rosterItemElement, "summaExcise", bigDecimalToString(exciseSum));
            Element vatElement = new Element("vat", namespace);
            addStringElement(namespace, vatElement, "rate", bigDecimalToString(vatRate));
            addStringElement(namespace, vatElement, "rateType", vatRate != null && vatRate.compareTo(BigDecimal.ZERO) == 0 ? "ZERO" : "DECIMAL");
            addStringElement(namespace, vatElement, "summaVat", bigDecimalToString(vatSum));
            rosterItemElement.addContent(vatElement);
            addStringElement(namespace, rosterItemElement, "costVat", bigDecimalToString(sumWithVat));
            Element descriptionsElement = new Element("descriptions", namespace);
            addStringElement(namespace, descriptionsElement, "description", description);
            rosterItemElement.addContent(descriptionsElement);
            rosterElement.addContent(rosterItemElement);
        }
        rosterElement.setNamespace(namespace);
        return rosterElement;
    }

    private Element createNumberDateElement(String name, String numberInvoice, String dateInvoice, Namespace namespace) {
        Element element = new Element(name);
        addStringElement(namespace, element, "number", numberInvoice);
        addStringElement(namespace, element, "date", dateInvoice);
        element.setNamespace(namespace);
        return element;
    }

    private Element createLegalEntityElement(String name, String countryCode, String unp, String nameLegalEntity, String address, Namespace namespace) {
        Element element = new Element(name);
        addStringElement(namespace, element, "countryCode", countryCode);
        addStringElement(namespace, element, "unp", unp);
        addStringElement(namespace, element, "name", nameLegalEntity);
        addStringElement(namespace, element, "address", address);
        element.setNamespace(namespace);
        return element;
    }

    private String formatDate(Date date) {
        return date == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(date);
    }

    private String getString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String bigDecimalToString(BigDecimal value) {
        return bigDecimalToString(value, null);
    }

    private String bigDecimalToString(BigDecimal value, String defaultValue) {
        return value == null ? defaultValue : BaseUtils.bigDecimalToString("##0.####", value).replace(",", ".");
    }

    private String getFullNumber(String unp, String number) {
        while (number.length() < 10)
            number = "0" + number;
        Integer year = Calendar.getInstance().getTime().getYear() + 1900;
        return unp + "-" + year + "-" + number;
    }

    private String getProviderStatus(String status, String defaultStatus) {
        if (status != null) {
            if (status.endsWith("seller"))
                return "SELLER";
            else if (status.endsWith("consignor"))
                return "CONSIGNOR";
            else if (status.endsWith("commissionaire"))
                return "COMMISSIONAIRE";
            else if (status.endsWith("taxDeductionPayer"))
                return "TAX_DEDUCTION_PAYER";
            else if (status.endsWith("trustee"))
                return "TRUSTEE";
            else if (status.endsWith("foreignOrganization"))
                return "FOREIGN_ORGANIZATION";
            else if (status.endsWith("agent"))
                return "AGENT";
            else if (status.endsWith("developer"))
                return "DEVELOPER";
            else if (status.endsWith("tornoversOnSalePayer"))
                return "TURNOVERS_ON_SALE_PAYER";
        }
        return defaultStatus;
    }
}