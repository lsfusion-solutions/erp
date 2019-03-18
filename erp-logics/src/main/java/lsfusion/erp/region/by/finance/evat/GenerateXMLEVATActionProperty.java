package lsfusion.erp.region.by.finance.evat;

import com.google.common.base.Throwables;
import lsfusion.base.BaseUtils;
import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.WriteClientAction;
import lsfusion.erp.integration.DefaultExportXMLActionProperty;
import lsfusion.interop.form.property.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenerateXMLEVATActionProperty extends DefaultExportXMLActionProperty {

    private final ClassPropertyInterface evatInterface;

    public GenerateXMLEVATActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        evatInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        DataObject evatObject = context.getDataKeyValue(evatInterface);
        sendEVAT(context, evatObject);
    }

    private void sendEVAT(ExecutionContext context, DataObject evatObject) {
        generateXML(context, evatObject, true, false);
    }

    Map<String, Map<Long, String>> getInvoices(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, Map<Long, String>> evatMap = new HashMap<>();
        KeyExpr evatExpr = new KeyExpr("evat");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "evat", evatExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("unp", findProperty("unpSender[EVAT]").getExpr(context.getModifier(), evatExpr));
        query.addProperty("exportNumber", findProperty("exportNumber[EVAT]").getExpr(context.getModifier(), evatExpr));
        query.and(findProperty("in[EVAT]").getExpr(context.getModifier(), evatExpr).getWhere());
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(context);
        for (int i = 0; i < result.values().size(); i++) {
            DataObject evatObject = result.getKey(i).get("evat");
            String unp = (String) result.getValue(i).get("unp").getValue();
            String number = (String) result.getValue(i).get("exportNumber").getValue();
            if (unp != null && number != null) {
                Map<Long, String> invoiceNumbers = evatMap.get(unp);
                if (invoiceNumbers == null)
                    invoiceNumbers = new HashMap<>();
                invoiceNumbers.put((Long) evatObject.getValue(), number);
                evatMap.put(unp, invoiceNumbers);
            }
        }
        return evatMap;
    }

    Map<String, Map<Long, List<Object>>> generateXMLs(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, Map<Long, List<Object>>> files = new HashMap<>();
        KeyExpr evatExpr = new KeyExpr("evat");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "evat", evatExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("unp", findProperty("unpSender[EVAT]").getExpr(context.getModifier(), evatExpr));
        query.and(findProperty("in[EVAT]").getExpr(context.getModifier(), evatExpr).getWhere());
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(context);
        if (!result.isEmpty()) {
            for (int i = 0; i < result.values().size(); i++) {
                DataObject evatObject = result.getKey(i).get("evat");
                String unp = (String) result.getValue(i).get("unp").getValue();
                if (unp != null) {
                    Map<Long, List<Object>> filesEntry = files.get(unp);
                    if (filesEntry == null)
                        filesEntry = new HashMap<>();
                    List<Object> xml = generateXML(context, evatObject, false, false);
                    if (xml != null) {
                        filesEntry.put((Long) evatObject.getValue(), xml);
                        files.put(unp, filesEntry);
                    }
                }
            }
        } else {
            context.delayUserInteraction(new MessageClientAction("Не выбрано ни одного ЭСЧФ", "Ошибка"));
        }
        return files;
    }

    protected List<Object> generateXML(ExecutionContext context, DataObject evatObject, boolean choosePath, boolean saveToLocal) {
        File tmpFile = null;
        try {

            String error = "";

            String status = getLastPart((String) findProperty("nameStatus[EVAT]").read(context, evatObject));

            String unpSender = trim((String) findProperty("unpSender[EVAT]").read(context, evatObject));

            //String number = trim((String) findProperty("number[EVAT]").read(context, evatObject), "");
            String documentNumber = trim((String) findProperty("exportNumber[EVAT]").read(context, evatObject), "");

            String addressSupplier = trim((String) findProperty("shippingAddressConsignor[EVAT]").read(context, evatObject));
//            if (addressSupplier == null)
//                error += String.format("EVAT %s: Не задан пункт погрузки\n", number);
            String addressCustomer = trim((String) findProperty("shippingAddressConsignee[EVAT]").read(context, evatObject));
//            if (addressCustomer == null)
//                error += String.format("EVAT %s: Не задан пункт отгрузки\n", number);

            String invoice = trim((String) findProperty("invoice[EVAT]").read(context, evatObject));
            String dateCancelled = formatDate((Date) findProperty("dateCancelled[EVAT]").read(context, evatObject));
            boolean allowZeroVAT = findProperty("allowZeroVAT[EVAT]").read(context, evatObject) != null;
            boolean exportProviderTaxes = findProperty("exportProviderTaxes[EVAT]").read(context, evatObject) != null;

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

            rootElement.addContent(createGeneralElement(context, evatObject, status, documentNumber, invoice, dateCancelled, namespace));

            switch (status) {
                case "fixed":
                    if(invoice == null)
                        error += String.format("EVAT %s: Не задано поле 'К ЭСЧФ'\n", documentNumber);
                    else {
                        Pattern pattern = Pattern.compile("\\d{9}-\\d{4}-\\d{10}");
                        Matcher matcher = pattern.matcher(invoice);
                        if(!matcher.matches()) {
                            error += String.format("EVAT %s: поле 'К ЭСЧФ' должно быть формата d{9}-d{4}-d{10}\n", documentNumber);
                        }
                    }
                    if(dateCancelled == null)
                        error += String.format("EVAT %s: Не задано поле 'Дата аннулирования'\n", documentNumber);
                case "original":
                case "additionalNoRef":
                    String declarationSupplier = trim((String) findProperty("declarationSupplier[EVAT]").read(context, evatObject));
//                    if(declarationSupplier == null)
//                        error += String.format("EVAT %s: Не заданы Реквизиты заявления о ввозе товаров и уплате косвенных налогов для поставщика\n", number);
                    String declarationCustomer = trim((String) findProperty("declarationCustomer[EVAT]").read(context, evatObject));
//                    if(declarationCustomer == null)
//                        error += String.format("EVAT %s: Не заданы Реквизиты заявления о ввозе товаров и уплате косвенных налогов для покупателя\n", number);
                    rootElement.addContent(createProviderElement(context, evatObject, declarationSupplier, namespace, exportProviderTaxes));
                    rootElement.addContent(createRecipientElement(context, evatObject, declarationCustomer, namespace));
                    boolean skipDeliveryCondition = findProperty("skipDeliveryCondition[EVAT]").read(context, evatObject) != null;
                    if(!skipDeliveryCondition) {
                        String numberDoc = trim((String) findProperty("numberDoc[EVAT]").read(context, evatObject));
                        if(numberDoc == null)
                            error += String.format("EVAT %s: Не задан номер условия поставки", documentNumber);
                        rootElement.addContent(createSenderReceiverElement(context, evatObject, addressSupplier, addressCustomer, namespace));
                        rootElement.addContent(createDeliveryConditionElement(context, evatObject, numberDoc, namespace));
                    }
                    rootElement.addContent(createRosterElement(context, evatObject, namespace, allowZeroVAT));
                    break;
                case "additional":
                    rootElement.addContent(createRosterElement(context, evatObject, namespace, allowZeroVAT));
                case "cancelled":
                    break;
            }

            if (error.isEmpty()) {
                tmpFile = File.createTempFile("evat", "xml");
                outputXml(doc, new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8"), "UTF-8");
                RawFileData fileData = new RawFileData(tmpFile);
                if (choosePath)
                    context.delayUserInterfaction(new WriteClientAction(fileData, documentNumber, "xml", false, true));
                if (saveToLocal) findProperty("generatedXML[]").change(new FileData(fileData, "xml"), context);
                return Arrays.asList((Object) fileData, documentNumber);

            } else {
                context.delayUserInterfaction(new MessageClientAction(error, "Не все поля заполнены"));
                return null;
            }

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
    private Element createGeneralElement(ExecutionContext context, DataObject evatObject, String status, String documentNumber, String invoice, String dateCancelled, Namespace namespace) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Element generalElement = new Element("general");

        String dateIssuance = formatDate(new Date(System.currentTimeMillis()));
        String dateTransaction = formatDate((Date) findProperty("date[EVAT]").read(context, evatObject));

        switch (status) {
            case "original":
                addStringElement(namespace, generalElement, "number", documentNumber);
                addStringElement(namespace, generalElement, "dateIssuance", dateIssuance);
                addStringElement(namespace, generalElement, "dateTransaction", dateTransaction);
                addStringElement(namespace, generalElement, "documentType", "ORIGINAL");
                break;
            case "fixed":
                addStringElement(namespace, generalElement, "number", documentNumber);
                addStringElement(namespace, generalElement, "dateIssuance", dateIssuance);
                addStringElement(namespace, generalElement, "dateTransaction", dateTransaction);
                addStringElement(namespace, generalElement, "documentType", "FIXED");
                addStringElement(namespace, generalElement, "invoice", invoice);
                addStringElement(namespace, generalElement, "dateCancelled", dateCancelled);
                break;
            case "additional":
                boolean sendToRecipient = findProperty("sendToRecipient[EVAT]").read(context, evatObject) != null;

                addStringElement(namespace, generalElement, "number", documentNumber);
                addStringElement(namespace, generalElement, "dateIssuance", dateIssuance);
                addStringElement(namespace, generalElement, "dateTransaction", dateTransaction);
                addStringElement(namespace, generalElement, "documentType", "ADDITIONAL");
                addStringElement(namespace, generalElement, "invoice", invoice);
                addBooleanElement(namespace, generalElement, "sendToRecipient", sendToRecipient);
                break;
            case "additionalNoRef":
                addStringElement(namespace, generalElement, "number", documentNumber);
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
    private Element createProviderElement(ExecutionContext context, DataObject evatObject, String declaration, Namespace namespace, boolean exportProviderTaxes) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

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
        if(numberTaxes != null && exportProviderTaxes) {
            providerElement.addContent(createNumberDateElement("taxes", numberTaxes, dateTaxes, namespace));
        }
        providerElement.setNamespace(namespace);
        return providerElement;
    }

    //parent: rootElement
    private Element createRecipientElement(ExecutionContext context, DataObject evatObject, String declarationCustomer, Namespace namespace) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        String legalEntityStatus = trim((String) findProperty("nameLegalEntityStatusCustomer[EVAT]").read(context, evatObject));
        boolean dependentPerson = findProperty("dependentPersonCustomer[EVAT]").read(context, evatObject) != null;
        boolean residentsOfOffshore = findProperty("residentsOfOffshoreCustomer[EVAT]").read(context, evatObject) != null;
        boolean specialDealGoods = findProperty("specialDealGoodsCustomer[EVAT]").read(context, evatObject) != null;
        boolean bigCompany = findProperty("bigCompanyCustomer[EVAT]").read(context, evatObject) != null;
        boolean noCustomer = findProperty("noCustomer[EVAT]").read(context, evatObject) != null;
        String countryCode = trim((String) findProperty("countryCodeCustomer[EVAT]").read(context, evatObject));
        String unp = trim((String) findProperty("unpCustomer[EVAT]").read(context, evatObject));
        String name = trim((String) findProperty("nameCustomer[EVAT]").read(context, evatObject));
        Integer branchCodeCustomer = (Integer) findProperty("branchCodeCustomer[EVAT]").read(context, evatObject);
        String address = trim((String) findProperty("addressCustomer[EVAT]").read(context, evatObject));
        //String numberTaxes = trim((String) findProperty("numberTaxesCustomer[EVAT]").read(context, evatObject));
        //String dateTaxes = formatDate((Date) findProperty("dateTaxesCustomer[EVAT]").read(context, evatObject));
        String dateImport = formatDate((Date) findProperty("dateImportCustomer[EVAT]").read(context, evatObject));

        Element recipientElement = new Element("recipient");
        addStringElement(namespace, recipientElement, "recipientStatus", getProviderStatus(legalEntityStatus, "CUSTOMER"));
        addBooleanElement(namespace, recipientElement, "dependentPerson", dependentPerson);
        addBooleanElement(namespace, recipientElement, "residentsOfOffshore", residentsOfOffshore);
        addBooleanElement(namespace, recipientElement, "specialDealGoods", specialDealGoods);
        addBooleanElement(namespace, recipientElement, "bigCompany", bigCompany);
        if (!noCustomer) {
            addStringElement(namespace, recipientElement, "countryCode", countryCode);
            addStringElement(namespace, recipientElement, "unp", unp);
            addIntegerElement(namespace, recipientElement, "branchCode", branchCodeCustomer);
            addStringElement(namespace, recipientElement, "name", name);
            addStringElement(namespace, recipientElement, "address", address);
            addStringElement(namespace, recipientElement, "declaration", declarationCustomer);
            //Поле 21."Реквизиты заявления о ввозе товаров и уплате косвенных налогов" должно быть пустым (Правило-31)
            //if (numberTaxes != null)
            //    recipientElement.addContent(createNumberDateElement("taxes", numberTaxes, dateTaxes, namespace));
            addStringElement(namespace, recipientElement, "dateImport", dateImport);
        }
        recipientElement.setNamespace(namespace);
        return recipientElement;
    }

    //parent: rootElement
    private Element createSenderReceiverElement(ExecutionContext context, DataObject evatObject, String addressSupplier, String addressCustomer, Namespace namespace) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String countryCodeSupplier = trim((String) findProperty("countryCodeConsignor[EVAT]").read(context, evatObject));
        String unpSupplier = trim((String) findProperty("unpConsignor[EVAT]").read(context, evatObject));
        String nameSupplier = trim((String) findProperty("consignor[EVAT]").read(context, evatObject));

        String countryCodeCustomer = trim((String) findProperty("countryCodeConsignee[EVAT]").read(context, evatObject));
        String unpCustomer = trim((String) findProperty("unpConsignee[EVAT]").read(context, evatObject));
        String nameCustomer = trim((String) findProperty("consignee[EVAT]").read(context, evatObject));

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
    private Element createDeliveryConditionElement(ExecutionContext context, DataObject evatObject, String numberDoc, Namespace namespace) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        String contractNumber = trim((String) findProperty("numberContract[EVAT]").read(context, evatObject));
        String contractDate = formatDate((Date) findProperty("dateContract[EVAT]").read(context, evatObject));
        String date = formatDate((Date) findProperty("dateDoc[EVAT]").read(context, evatObject));
        Integer codeDocType = (Integer) findProperty("codeDocType[EVAT]").read(context, evatObject);
        String valueDocType = trim((String) findProperty("valueDocType[EVAT]").read(context, evatObject));
        String blankCode = trim((String) findProperty("blankCodeDoc[EVAT]").read(context, evatObject));
        String series = trim((String) findProperty("seriesDoc[EVAT]").read(context, evatObject));
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
        addStringElement(namespace, documentElement, "number", numberDoc);
        documentsElement.addContent(documentElement);
        contractElement.addContent(documentsElement);
        deliveryConditionElement.addContent(contractElement);
        addStringElement(namespace, deliveryConditionElement, "description", description);
        deliveryConditionElement.setNamespace(namespace);
        return deliveryConditionElement;
    }

    //parent: rootElement
    private Element createRosterElement(ExecutionContext context, DataObject evatObject, Namespace namespace, boolean allowZeroVAT) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

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
        String[] evatDetailNames = new String[]{"objValue", "name", "code", "evatCodeUOM", "codeOced",
                "quantity", "price", "sum", "exciseSum", "vatRate", "vatSum", "sumWithVAT", "nameDescriptionType"};
        LP[] evatDetailProperties = findProperties("objValue[EVATDetail]", "name[EVATDetail]", "code[EVATDetail]", "evatCodeUOM[EVATDetail]",
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
            Integer evatCodeUOM = (Integer) entry.get("evatCodeUOM");
            Integer codeOced = (Integer) entry.get("codeOced");
            BigDecimal quantity = (BigDecimal) entry.get("quantity");
            BigDecimal price = (BigDecimal) entry.get("price");
            BigDecimal sum = (BigDecimal) entry.get("sum");
            BigDecimal exciseSum = (BigDecimal) entry.get("exciseSum");
            BigDecimal vatRate = (BigDecimal) entry.get("vatRate");
            if(vatRate == null)
                vatRate = BigDecimal.ZERO;
            BigDecimal vatSum = (BigDecimal) entry.get("vatSum");
            BigDecimal sumWithVat = (BigDecimal) entry.get("sumWithVAT");

            String description = getDescription(getLastPart(trim((String) entry.get("nameDescriptionType"))));

            Element rosterItemElement = new Element("rosterItem", namespace);
            addStringElement(namespace, rosterItemElement, "number", getString(i + 1));
            addStringElement(namespace, rosterItemElement, "name", name);
            addStringElement(namespace, rosterItemElement, "code", code);
            addStringElement(namespace, rosterItemElement, "code_oced", getString(codeOced));
            addIntegerElement(namespace, rosterItemElement, "units", evatCodeUOM);
            addBigDecimalElement(namespace, rosterItemElement, "count", quantity);
            addStringElement(namespace, rosterItemElement, "price", bigDecimalToString(price));
            addStringElement(namespace, rosterItemElement, "cost", bigDecimalToString(sum));
            addStringElement(namespace, rosterItemElement, "summaExcise", bigDecimalToString(exciseSum));
            Element vatElement = new Element("vat", namespace);
            addStringElement(namespace, vatElement, "rate", bigDecimalToString(vatRate));
            addStringElement(namespace, vatElement, "rateType", getRateType(vatRate, allowZeroVAT));
            addStringElement(namespace, vatElement, "summaVat", bigDecimalToString(vatSum));
            rosterItemElement.addContent(vatElement);
            addStringElement(namespace, rosterItemElement, "costVat", bigDecimalToString(sumWithVat));
            if(description != null) {
                Element descriptionsElement = new Element("descriptions", namespace);
                addStringElement(namespace, descriptionsElement, "description", description);
                rosterItemElement.addContent(descriptionsElement);
            }
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
        addStringElement(namespace, element, "unp", unp == null ? "" : unp);
        addStringElement(namespace, element, "name", nameLegalEntity == null ? "" : nameLegalEntity);
        addStringElement(namespace, element, "address", address == null ? "" : address);
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

    private String getRateType(BigDecimal vatRate, boolean allowZeroVAT) {
        String result = null;
        if(vatRate != null) {
            if(vatRate.compareTo(BigDecimal.ZERO) == 0)
                result = allowZeroVAT ? "ZERO" : "NO_VAT";
            else if(vatRate.compareTo(BigDecimal.valueOf(10)) == 0 || vatRate.compareTo(BigDecimal.valueOf(20)) == 0)
                result = "DECIMAL";
            else
                result = "CALCULATED";
        }
        return result;
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
            else if (status.endsWith("customer"))
                return "CUSTOMER";
            else if (status.endsWith("consumer"))
                return "CONSUMER";
            else if (status.endsWith("taxDeductionRecipient"))
                return "TAX_DEDUCTION_RECIPIENT";
            else if (status.endsWith("foreignOrganizationBuyer"))
                return "FOREIGN_ORGANIZATION_BUYER";
            else if (status.endsWith("turnoversOnSaleRecipient"))
                return "TURNOVERS_ON_SALE_RECIPIENT";
        }
        return defaultStatus;
    }
}