package equ.clt.handler.kristal10;

import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static equ.clt.handler.HandlerUtils.safeAdd;
import static equ.clt.handler.HandlerUtils.safeSubtract;
import static lsfusion.base.BaseUtils.nvl;
import static lsfusion.base.BaseUtils.trimToNull;

public abstract class Kristal10DefaultHandler extends DefaultCashRegisterHandler<Kristal10SalesBatch, CashDocumentBatch> {

    protected static Map<String, Map<String, String>> deleteBarcodeDirectoryMap = new HashMap<>();
    protected FileSystemXmlApplicationContext springContext;
    String encoding = "utf-8";

    public Kristal10DefaultHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    abstract String getLogPrefix();

    protected static String removeZeroes(String value) {
        if(value != null) {
            while(value.startsWith("0")) {
                value = value.substring(1);
            }
        }
        return value;
    }

    protected void fillGoodElement(Element good, TransactionCashRegisterInfo transaction, CashRegisterItem item, String barcodeItem,
                                   List<String> tobaccoGroups, boolean skipScalesInfo, String shopIndices, boolean useShopIndices,
                                   boolean brandIsManufacturer, boolean seasonIsCountry, JSONObject infoJSON) {

        addStringElement(good, "name", item.name.replace("«",  "\"").replace("»", "\""));

        addStringElement(good, "vat", item.vat == null || item.vat.intValue() == 0 ? "20" : String.valueOf(item.vat.intValue()));

        addProductType(good, item, tobaccoGroups);

        addStringElement(good, "delete-from-cash", "false");

        List<ItemGroup> hierarchyItemGroup = transaction.itemGroupMap.get(item.extIdItemGroup);
        if (hierarchyItemGroup != null) {
            ItemGroup firstElementInHierarchy = hierarchyItemGroup.get(0);
            //parent: good
            Element group = new Element("group");
            setAttribute(group, "id", firstElementInHierarchy.idItemGroup);
            addStringElement(group, "name", firstElementInHierarchy.nameItemGroup);
            good.addContent(group);

            addHierarchyItemGroup(group, hierarchyItemGroup.subList(1, hierarchyItemGroup.size()));
        }

        if (item.idUOM == null || item.shortNameUOM == null) {
            String error = getLogPrefix() + "Error! UOM not specified for item with barcode " + barcodeItem;
            processTransactionLogger.error(error);
            throw new RuntimeException(error);
        } else {
            Element measureType = new Element("measure-type");
            setAttribute(measureType, "id", item.idUOM);
            addStringElement(measureType, "name", item.shortNameUOM);
            good.addContent(measureType);
        }

        if(!skipScalesInfo) {
            //<plugin-property key="plu-number" value="4">
            Element extraPluginProperty = new Element("plugin-property");
            setAttribute(extraPluginProperty, "key", "plu-number");
            setAttribute(extraPluginProperty, "value", removeZeroes(item.idBarcode));
            good.addContent(extraPluginProperty);

            //<plugin-property value="1" key="composition"/>
            if (item.expiryDate != null) {
                Element expiryDateProperty = new Element("plugin-property");
                setAttribute(expiryDateProperty, "key", "composition");
                setAttribute(expiryDateProperty, "value", "Годен до: " + formatDate(item.expiryDate, "dd.MM.yyyy") + " ");
                good.addContent(expiryDateProperty);
            }
        }

        if (useShopIndices) {
            addStringElement(good, "shop-indices", shopIndices);
        }

        if (brandIsManufacturer) {
            //parent: good
            Element manufacturer = new Element("manufacturer");
            setAttribute(manufacturer, "id", item.idBrand);
            addStringElement(manufacturer, "name", item.nameBrand);
            good.addContent(manufacturer);
        }

        if (seasonIsCountry) {
            //parent: good
            Element country = new Element("country");
            setAttribute(country, "id", item.idSeason);
            addStringElement(country, "name", item.nameSeason);
            good.addContent(country);
        }

        if (infoJSON != null) {
            String ntin = trimToNull(infoJSON.optString("ntin"));
            if(ntin != null) {
                Element pluginProperty = new Element("plugin-property");
                setAttribute(pluginProperty, "key", "uz-ffd-spic");
                setAttribute(pluginProperty, "value", ntin);
                good.addContent(pluginProperty);
            }

            addStringElement(good, "energy", String.valueOf(infoJSON.optBoolean("energy")));

            if (infoJSON.has("age")) {
                addIntegerElement(good, "age-limit", infoJSON.getInt("age"));
            }

            if (infoJSON.has("weight")) {
                addStringElement(good, "weight", infoJSON.getString("weight"));
            }
        }
    }

    protected Element getBarcodeElement(CashRegisterItem item, String barcodeItem, String idItem, boolean exportAmountForBarcode) {
        Element barcodeElement = new Element("bar-code");
        setAttribute(barcodeElement, "code", barcodeItem);
        addStringElement(barcodeElement, "default-code", (item.mainBarcode != null && !item.mainBarcode.equals(item.idBarcode)) ? "false" : "true");
        setAttribute(barcodeElement, "marking-of-the-good", idItem);
        if (exportAmountForBarcode && item.amountBarcode != null && BigDecimal.ONE.compareTo(item.amountBarcode) != 0) {
            addBigDecimalElement(barcodeElement, "count", item.amountBarcode);
        }
        return barcodeElement;
    }

    protected static void addProductType(Element good, ItemInfo item, List<String> tobaccoGroups) {
        String productType;
        if(item.idItemGroup != null && tobaccoGroups != null && tobaccoGroups.contains(item.idItemGroup))
            productType = "ProductCiggyEntity";
        else if (item.passScalesItem)
            productType = item.splitItem && !isPieceUOM(item) ? "ProductWeightEntity" : "ProductPieceWeightEntity";
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

    protected static void addBigDecimalElement(Element parent, String id, BigDecimal value) {
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

    protected static void addPriceEntryElements(Element parentElement, TransactionCashRegisterInfo transaction, CashRegisterItem item,
                                                String idItem, JSONObject infoJSON, boolean useSectionAsDepartNumber, String shopIndices) {
        Object price = item.price == null ? null : (item.price.doubleValue() == 0.0 ? "0.00" : item.price);
        addPriceEntryElement(parentElement, idItem, price, false, shopIndices, currentDate(), null, 1, getDepartNumber(transaction, item, useSectionAsDepartNumber), false);

        if(infoJSON != null) {
            Double secondPrice = infoJSON.optDouble("secondPrice");
            String secondPriceBeginDate = infoJSON.optString("secondBeginDate", null);
            String secondPriceEndDate = infoJSON.optString("secondEndDate", null);
            boolean secondPriceDeleted = infoJSON.optBoolean("secondDeleted");
            if (!secondPrice.isNaN()) {
                int numberForSecondPrice = infoJSON.has("numberForSecondPrice") ? infoJSON.getInt("numberForSecondPrice") : 2;
                Integer departNumberForSecondPrice = infoJSON.has("departNumberForSecondPrice") ? infoJSON.getInt("departNumberForSecondPrice") : getDepartNumber(transaction, item, useSectionAsDepartNumber);
                addExtraPriceEntryElement(parentElement, idItem, secondPrice, secondPriceDeleted, shopIndices, secondPriceBeginDate, secondPriceEndDate, numberForSecondPrice, departNumberForSecondPrice);
            }

            Double oldSecondPrice = infoJSON.optDouble("oldSecondPrice");
            if (!oldSecondPrice.isNaN() && !oldSecondPrice.equals(secondPrice)) {
                addPriceEntryElement(parentElement, null, oldSecondPrice, true, null, null, 2, getDepartNumber(transaction, item, useSectionAsDepartNumber));
            }

            JSONArray extraPrices = infoJSON.optJSONArray("extraPrices");
            if (extraPrices != null && !extraPrices.isEmpty()) {
                for (int i = 0; i < extraPrices.length(); i++) {
                    JSONObject extraPrice = extraPrices.getJSONObject(i);
                    addExtraPriceEntryElement(parentElement, idItem, extraPrice.getDouble("price"), extraPrice.optBoolean("deleted"), shopIndices,
                            extraPrice.getString("beginDate"), extraPrice.optString("endDate"), extraPrice.getInt("number"),
                            extraPrice.getInt("departmentNumber"));
                }
            }

            int zone = infoJSON.optInt("zone");
            int countZone = infoJSON.optInt("countZone");
            if (zone != 0 && countZone != 0) {
                for (int i = 1; i <= countZone; i++) {
                    if (i == zone) {
                        addPriceEntryElement(parentElement, idItem, price, false, currentDate(), null, 1, i);
                    } else {
                        addPriceEntryElement(parentElement, idItem, 1, true, null, null, 1, i);
                    }
                }
            }
        }
    }

    public static void addPriceEntryElement(Element parent, String barcode, Object price, boolean deleted, String beginDate, String endDate, int number, Object departmentNumber) {
        addPriceEntryElement(parent, barcode, price, deleted, null, beginDate, endDate, number, departmentNumber, false);
    }

    private static void addExtraPriceEntryElement(Element parent, String barcode, Double price, boolean deleted, String shopIndices, String beginDate, String endDate, int number, Integer departmentNumber) {
        addPriceEntryElement(parent, barcode, price, deleted, shopIndices, beginDate != null ? beginDate : currentDate(), endDate, number, departmentNumber, true);
    }

    private static void addPriceEntryElement(Element parent, String barcode, Object price, boolean deleted, String shopIndices, String beginDate, String endDate, int number, Object departmentNumber, boolean extra) {
        Element priceEntry = new Element("price-entry");
        setAttribute(priceEntry, "marking-of-the-good", barcode);
        setAttribute(priceEntry, "price", price);
        setAttribute(priceEntry, "deleted", deleted);
        addStringElement(priceEntry, "shop-indices", shopIndices);
        addStringElement(priceEntry, "begin-date", beginDate);
        addStringElement(priceEntry, "end-date", endDate);
        if(extra) {
            addStringElement(priceEntry, "deleted", String.valueOf(deleted));
        }
        addIntegerElement(priceEntry, "number", number);

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

                    JSONObject infoJSON = getExtInfo(d.extInfo);
                    if(infoJSON != null && infoJSON.optBoolean("sendchecktoemail")) {
                        setAttribute(client, "receipt-feedback", "BY_EMAIL");
                    }

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

    protected JSONObject getExtInfo(String extInfo) {
        return extInfo != null && !extInfo.isEmpty() ? new JSONObject(extInfo).optJSONObject("kristal10") : null;
    }

    private boolean isActiveDiscountCard(RequestExchange r, DiscountCard d) {
        return r.startDate == null || (d.dateFromDiscountCard != null && d.dateFromDiscountCard.compareTo(r.startDate) >= 0);
    }

    protected static String currentDate() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00";
    }

    protected static String transformBarcode(TransactionCashRegisterInfo transaction, CashRegisterItem item, boolean skipPrefix) {
        return transformBarcode(item.idBarcode, isPieceUOM(item) ? nvl(transaction.pieceCodeGroupCashRegister, "") : nvl(transaction.weightCodeGroupCashRegister, "21"), item.passScalesItem, skipPrefix);
    }

    protected static String transformBarcode(String idBarcode, boolean skipPrefix) {
        return transformBarcode(idBarcode, null, false, skipPrefix);
    }

    private static String transformBarcode(String idBarcode, String prefix, boolean passScalesItem, boolean skipPrefix) {
        return passScalesItem && idBarcode.length() <= 6 && prefix != null && !skipPrefix ? (prefix + idBarcode) : idBarcode;
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
            Integer numberGroupCashRegister = (Integer) valuesHandler.get(4);

            BigDecimal sumBase = baseZReportSumMap.get(idZReportHandler);

            if (sumHandler == null || sumBase == null || sumHandler.doubleValue() != sumBase.doubleValue())
                message.append(String.format("GroupCashRegister %s. CashRegister %s. \nZReport %s checksum failed: %s(fusion) != %s(kristal);\n",
                        numberGroupCashRegister, numberCashRegister, numberZReport, sumBase, sumHandler));
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

    protected String getDiscountCardNumber(Element purchaseNode) {
        List<String> couponsList = new ArrayList<>();
        List<Element> cardsList = purchaseNode.getChildren("card");
        for (Element card : cardsList) {
            String type = card.getAttributeValue("type");
            if(type != null && (type.equals("COUPON_CARD") || type.equals("UNIQUE_COUPON")))
                couponsList.add(removeSpaces(card.getAttributeValue("number")));
        }

        List<Element> discountCardsList = purchaseNode.getChildren("discountCards");
        for (Element discountCardNode : discountCardsList) {
            List<Element> discountCardList = discountCardNode.getChildren("discountCard");
            for (Element discountCardEntry : discountCardList) {
                String discountCard = discountCardEntry.getValue();
                if (discountCard != null && !couponsList.contains(discountCard)) {
                    return HandlerUtils.trim(removeSpaces(discountCard), 18);
                }
            }
        }
        return null;
    }

    private String removeSpaces(String value) {
        return value != null ? value.replace(" ", "") : null;
    }

    protected String getWeightCode(CashRegisterInfo cashRegisterByKey) {
        String weightCode = cashRegisterByKey != null ? cashRegisterByKey.weightCodeGroupCashRegister : null;
        return weightCode != null ? weightCode : "21";
    }

    protected void addPayments(Map<String, GiftCard> sumGiftCardMap, List<Payment> payments, BigDecimal currentPaymentSum, List<SalesInfo> currentSalesInfoList)  {
        //чит для случая, когда не указана сумма платежа. Недостающую сумму пишем в наличные.
        BigDecimal paymentSum = payments.stream().map(payment -> payment.sum).reduce(BigDecimal.ZERO, BigDecimal::add);
        for(GiftCard giftCard : sumGiftCardMap.values()) {
            paymentSum = safeAdd(paymentSum, giftCard.sum);
        }

        if (paymentSum.compareTo(currentPaymentSum) < 0) {
            payments.add(Payment.getCash(safeSubtract(currentPaymentSum, paymentSum)));
        }

        for (SalesInfo salesInfo : currentSalesInfoList) {
            salesInfo.payments = payments;
        }
    }

    protected Map<String, Object> getReceiptExtraFields(Element purchaseNode) {
        Map<String, Object> receiptExtraFields = new HashMap<>();
        receiptExtraFields.put("uid", getPluginPropertyValue(purchaseNode, "iKassa_document_uid"));
        receiptExtraFields.put("fiscalNumber", getPluginPropertyValue(purchaseNode, "FISCAL_DOC_ID"));
        return receiptExtraFields;
    }

    protected String getPluginPropertyValue(Element parentElement, String pluginPropertyKey) {
        String pluginPropertyValue = null;
        for (Element pluginProperty : (List<Element>) parentElement.getChildren("plugin-property")) {
            String key = pluginProperty.getAttributeValue("key");
            String value = pluginProperty.getAttributeValue("value");
            if (key != null && key.equals(pluginPropertyKey)) {
                pluginPropertyValue = value;
            }
        }
        return pluginPropertyValue;
    }

    protected boolean ignoreSales(CashRegisterInfo cashRegisterByKey, Integer nppGroupMachinery, String key, boolean ignoreCashRegisterWithDisableSales, boolean ignoreSalesWithoutNppGroupMachinery) {
        boolean noNppGroupMachinery = false;
        if (nppGroupMachinery == null) {
            if(ignoreSalesWithoutNppGroupMachinery) {
                noNppGroupMachinery = true;
            } else {
                sendSalesLogger.error("not found nppGroupMachinery : " + key);
            }
        }

        boolean ignoreSales = cashRegisterByKey != null && cashRegisterByKey.disableSales && ignoreCashRegisterWithDisableSales;
        return noNppGroupMachinery || ignoreSales;
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
