package equ.clt.handler.kristal10;

import equ.api.ItemGroup;
import equ.api.ItemInfo;
import equ.api.RequestExchange;
import equ.api.cashregister.*;
import equ.clt.handler.DefaultCashRegisterHandler;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public abstract class Kristal10DefaultHandler extends DefaultCashRegisterHandler<Kristal10SalesBatch> {

    protected static Map<String, Map<String, String>> deleteBarcodeDirectoryMap = new HashMap<>();
    protected FileSystemXmlApplicationContext springContext;
    String encoding = "utf-8";

    public Kristal10DefaultHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    protected static String removeZeroes(String value) {
        if(value != null) {
            while(value.startsWith("0")) {
                value = value.substring(1);
            }
        }
        return value;
    }

    protected static void addProductType(Element good, ItemInfo item, List<String> tobaccoGroups) {
        String productType;
        if(item.idItemGroup != null && tobaccoGroups != null && tobaccoGroups.contains(item.idItemGroup))
            productType = "ProductCiggyEntity";
        else if (item.passScalesItem)
            productType = item.splitItem ? "ProductWeightEntity" : "ProductPieceWeightEntity";
        else
            productType = (item.flags == null || ((item.flags & 256) == 0)) ? "ProductPieceEntity" : "ProductSpiritsEntity";
        addStringElement(good, "product-type", productType);
    }

    protected static Integer getDepartNumber(TransactionCashRegisterInfo transaction, CashRegisterItem item, boolean useSectionAsDepartNumber) {
        return getDepartNumber(item.section, transaction.departmentNumberGroupCashRegister, useSectionAsDepartNumber);
    }

    protected static Integer getDepartNumber(String section, Integer departNumber, boolean useSectionAsDepartNumber) {
        return useSectionAsDepartNumber && section != null ? Integer.parseInt(section.split(",")[0].split("\\|")[0]) : departNumber;
    }

    protected static void addIntegerElement(Element parent, String id, Integer value) {
        if (value != null)
            parent.addContent(new Element(id).setText(String.valueOf(value)));
    }

    protected static void addStringElement(Element parent, String id, String value) {
        if (value != null)
            parent.addContent(new Element(id).setText(value));
    }

    protected static void setAttribute(Element element, String id, Object value) {
        if (value != null)
            element.setAttribute(new Attribute(id, String.valueOf(value)));
    }

    protected static void addHierarchyItemGroup(Element parent, List<ItemGroup> hierarchyItemGroup) {
        if (!hierarchyItemGroup.isEmpty()) {
            Element element = new Element("parent-group");
            setAttribute(element, "id", hierarchyItemGroup.get(0).idItemGroup);
            addStringElement(element, "name", hierarchyItemGroup.get(0).nameItemGroup);
            parent.addContent(element);
            addHierarchyItemGroup(element, hierarchyItemGroup.subList(1, hierarchyItemGroup.size()));
        }
    }

    protected static void addPriceEntryElement(Element parent, String barcode, Object price, boolean deleted, String beginDate, String endDate, String number, Object departmentNumber) {
        Element priceEntry = new Element("price-entry");
        setAttribute(priceEntry, "marking-of-the-good", barcode);
        setAttribute(priceEntry, "price", price);
        setAttribute(priceEntry, "deleted", deleted);
        addStringElement(priceEntry, "begin-date", beginDate);
        addStringElement(priceEntry, "end-date", endDate);
        addStringElement(priceEntry, "number", number);

        if(departmentNumber != null) {
            //parent: priceEntry
            Element department = new Element("department");
            setAttribute(department, "number", departmentNumber);
            priceEntry.addContent(department);
        }

        parent.addContent(priceEntry);
    }

    protected static String formatDate(LocalDate date, String format) {
        return date == null ? null : date.format(DateTimeFormatter.ofPattern(format));
    }

    protected static String formatDateTime(LocalDateTime dateTime, String format, String defaultValue) {
        return dateTime == null ? defaultValue : dateTime.format(DateTimeFormatter.ofPattern(format));
    }

    protected Document generateDiscountCardXML(List<DiscountCard> discountCardList, RequestExchange requestExchange) {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        Map<Double, String> discountCardPercentTypeMap = kristalSettings.getDiscountCardPercentTypeMap();
        boolean exportSegments = kristalSettings.isExportSegments();

        Element rootElement = new Element("cards-catalog");
        Document doc = new Document(rootElement);

        LocalDate currentDate = LocalDate.now();

        for (Map.Entry<Double, String> discountCardType : discountCardPercentTypeMap.entrySet()) {
            //parent: rootElement
            Element internalCard = new Element("internal-card-type");
            setAttribute(internalCard, "guid", discountCardType.getValue());
            setAttribute(internalCard, "name", discountCardType.getKey() + "%");
            setAttribute(internalCard, "personalized", "false");
            setAttribute(internalCard, "percentage-discount", discountCardType.getKey());
            setAttribute(internalCard, "deleted", "false");

            rootElement.addContent(internalCard);
        }

        for (DiscountCard d : discountCardList) {
            if (isActiveDiscountCard(requestExchange, d)) {
                //parent: rootElement
                Element internalCard = new Element("internal-card");
                Double percent = d.percentDiscountCard == null ? 0 : d.percentDiscountCard.doubleValue();
                String guid = discountCardPercentTypeMap.get(percent);
                if (d.numberDiscountCard != null) {
                    setAttribute(internalCard, "number", d.numberDiscountCard);
                    if (d.initialSumDiscountCard != null) setAttribute(internalCard, "amount", d.initialSumDiscountCard);
                    if (d.dateToDiscountCard != null) setAttribute(internalCard, "expiration-date", d.dateToDiscountCard);
                    setAttribute(internalCard, "status", d.dateFromDiscountCard == null || currentDate.compareTo(d.dateFromDiscountCard) >= 0 ? "ACTIVE" : "BLOCKED");
                    setAttribute(internalCard, "deleted", "false");
                    setAttribute(internalCard, "card-type-guid", d.idDiscountCardType != null ? d.idDiscountCardType : (guid != null ? guid : "0"));

                    Element client = new Element("client");
                    setAttribute(client, "guid", d.numberDiscountCard);
                    setAttribute(client, "last-name", d.lastNameContact);
                    setAttribute(client, "first-name", d.firstNameContact);
                    setAttribute(client, "middle-name", d.middleNameContact);
                    setAttribute(client, "birth-date", formatDate(d.birthdayContact, "yyyy-MM-dd"));
                    if (d.sexContact != null) {
                        setAttribute(client, "sex", d.sexContact == 0 ? "MALE" : "FEMALE");
                    }
                    setAttribute(client, "isCompleted", d.isCompleted);
                    internalCard.addContent(client);

                    rootElement.addContent(internalCard);
                }
            }
        }

        if (exportSegments) {
            //parent: rootElement
            Element targetCustomerGroups = new Element("TargetCustomersGroups");
            //parent: targetCustomerGroups
            Element targetCustomerGroup = new Element("TargetCustomersGroup");
            setAttribute(targetCustomerGroup, "code", "SET1644931927302");
            setAttribute(targetCustomerGroup, "name", "Социальные");
            for (DiscountCard d : discountCardList) {
                if (isActiveDiscountCard(requestExchange, d) && isSocial(d)) {
                    //parent: targetCustomerGroup
                    Element cust = new Element("cust");
                    cust.setAttribute("guid", d.numberDiscountCard);
                    targetCustomerGroup.addContent(cust);
                }
            }
            targetCustomerGroups.addContent(targetCustomerGroup);
            rootElement.addContent(targetCustomerGroups);
        }

        return doc;
    }

    private boolean isSocial(DiscountCard d) {
        JSONObject infoJSON = getExtInfo(d.extInfo);
        if(infoJSON != null) {
            return infoJSON.optBoolean("isSocial");
        }
        return false;
    }

    private JSONObject getExtInfo(String extInfo) {
        return extInfo != null ? new JSONObject(extInfo).optJSONObject("kristal10") : null;
    }

    private boolean isActiveDiscountCard(RequestExchange r, DiscountCard d) {
        return r.startDate == null || (d.dateFromDiscountCard != null && d.dateFromDiscountCard.compareTo(r.startDate) >= 0);
    }

    protected static String currentDate() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00";
    }

    protected static String transformBarcode(String idBarcode, String weightCode, boolean passScalesItem, boolean skipWeightPrefix) {
        //временное решение для весовых товаров
        return passScalesItem && idBarcode.length() <= 6 && weightCode != null && !skipWeightPrefix ? (weightCode + idBarcode) : idBarcode;
    }

    @Override
    public ExtraCheckZReportBatch compareExtraCheckZReport(Map<String, List<Object>> handlerZReportSumMap, Map<String, BigDecimal> baseZReportSumMap) {

        StringBuilder message = new StringBuilder();
        List<String> idZReportList = new ArrayList<>();

        for (Map.Entry<String, List<Object>> kristalEntry : handlerZReportSumMap.entrySet()) {

            String idZReportHandler = kristalEntry.getKey();
            List<Object> valuesHandler = kristalEntry.getValue();
            BigDecimal sumHandler = (BigDecimal) valuesHandler.get(0);
            Integer numberCashRegister = (Integer) valuesHandler.get(1);
            String numberZReport = (String) valuesHandler.get(2);
            String idZReport = (String) valuesHandler.get(3);

            BigDecimal sumBase = baseZReportSumMap.get(idZReportHandler);

            if (sumHandler == null || sumBase == null || sumHandler.doubleValue() != sumBase.doubleValue())
                message.append(String.format("CashRegister %s. \nZReport %s checksum failed: %s(fusion) != %s(kristal);\n",
                        numberCashRegister, numberZReport, sumBase, sumHandler));
            else
                idZReportList.add(idZReport);
        }
        return idZReportList.isEmpty() && (message.length() == 0) ? null : new ExtraCheckZReportBatch(idZReportList, message.toString());
    }

    protected List<String> getTobaccoGroups (String tobaccoGroup) {
        List<String> tobaccoGroups = new ArrayList<>();
        if (tobaccoGroup != null)
            Collections.addAll(tobaccoGroups, tobaccoGroup.split(","));
        return tobaccoGroups;
    }

    protected String transformUPCBarcode(String idBarcode, String transformUPCBarcode) {
        if(idBarcode != null && transformUPCBarcode != null) {
            if(transformUPCBarcode.equals("13to12") && idBarcode.length() == 13 && idBarcode.startsWith("0"))
                idBarcode = idBarcode.substring(1);
            else if(transformUPCBarcode.equals("12to13") && idBarcode.length() == 12)
                idBarcode += "0";

        }
        return idBarcode;
    }

    protected String readStringXMLValue(Object element, String field) {
        if (!(element instanceof Element))
            return null;
        String value = ((Element) element).getChildText(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        return value;
    }

    protected String readStringXMLAttribute(Object element, String field) {
        if (!(element instanceof Element))
            return null;
        String value = ((Element) element).getAttributeValue(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        return value;
    }

    protected BigDecimal readBigDecimalXMLValue(Object element, String field) {
        if (!(element instanceof Element))
            return null;
        String value = ((Element) element).getChildText(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            sendSalesLogger.error("Kristal10 Error: ", e);
            return null;
        }
    }

    protected BigDecimal readBigDecimalXMLAttribute(Object element, String field) {
        if (!(element instanceof Element))
            return null;
        String value = ((Element) element).getAttributeValue(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            sendSalesLogger.error("Kristal10 Error: ", e);
            return null;
        }
    }

    protected Integer readIntegerXMLValue(Object element, String field) {
        if (!(element instanceof Element))
            return null;
        String value = ((Element) element).getChildText(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            sendSalesLogger.error("Kristal10 Error: ", e);
            return null;
        }
    }

    protected Integer readIntegerXMLAttribute(Object element, String field) {
        if (!(element instanceof Element))
            return null;
        String value = ((Element) element).getAttributeValue(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            sendSalesLogger.error("Kristal10 Error: ", e);
            return null;
        }
    }

    protected double parseWeight(String value) {
        try {
            return (double) Integer.parseInt(value) / 1000;
        } catch (Exception e) {
            return 0.0;
        }
    }

    protected boolean notNullNorEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    protected CashRegisterInfo getCashRegister(Map<String, List<CashRegisterInfo>> cashRegisterMap, String key) {
        //ищем кассу без disableSales. Если все с disableSales, берём первую
        CashRegisterInfo cashRegister = null;
        List<CashRegisterInfo> cashRegisterList = cashRegisterMap.get(key);
        if(cashRegisterList != null) {
            for(CashRegisterInfo c : cashRegisterList) {
                if(!c.disableSales) {
                    cashRegister = c;
                    break;
                }
            }
            if(cashRegister == null) {
                cashRegister = cashRegisterList.get(0);
            }
        }
        return  cashRegister;
    }

    protected Set<String> parseStringPayments(String payments) {
        Set<String> paymentsSet = new HashSet<>();
        try {
            if (payments != null && !payments.isEmpty()) {
                for (String payment : payments.split(",")) {
                    paymentsSet.add(payment.trim());
                }
            }
        } catch (Exception e) {
            sendSalesLogger.error("invalid payment settings: " + payments);
        }
        return paymentsSet;
    }

    public class DeleteBarcode {
        Integer nppGroupMachinery;
        String directory;
        Set<String> barcodes;

        public DeleteBarcode(Integer nppGroupMachinery, String directory) {
            this.nppGroupMachinery = nppGroupMachinery;
            this.directory = directory;
            this.barcodes = new HashSet<>();
        }
    }
}
