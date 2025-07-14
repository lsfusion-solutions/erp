package equ.clt.handler.kristal10;

import equ.api.*;
import equ.api.cashregister.*;
import equ.api.stoplist.StopListInfo;
import equ.api.stoplist.StopListItem;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
    Charset encoding = StandardCharsets.UTF_8;

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

    protected Element createGoodElement(TransactionCashRegisterInfo transaction, CashRegisterItem item, String idItem, String barcodeItem,
                                     List<String> tobaccoGroups, boolean skipScalesInfo, String shopIndices, boolean useShopIndices,
                                     boolean brandIsManufacturer, boolean seasonIsCountry, boolean minusOneForEmptyVAT, boolean exportAmountForBarcode,
                                     Map<String, String> deleteBarcodeMap, DeleteBarcode usedDeleteBarcodes, List<String> notGTINPrefixes,
                                     JSONObject infoJSON, JSONObject extraInfoJSON) {

        Element good = new Element("good");

        setAttribute(good, "marking-of-the-good", idItem);

        addStringElement(good, "name", item.name.replace("«",  "\"").replace("»", "\""));

        addVATElement(good, item, minusOneForEmptyVAT);

        boolean deleteFromCash = false;
        if (infoJSON != null && infoJSON.has("deleteFromCash")) {
            deleteFromCash = infoJSON.getBoolean("deleteFromCash");
        }
        addStringElement(good, "delete-from-cash", String.valueOf(deleteFromCash));

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

        addPluginPropertyElement(good, "precision", (item.splitItem || item.passScalesItem) ? "0.001" : "1.0");

        if(!skipScalesInfo) {
            addPluginPropertyElement(good, "plu-number", removeZeroes(item.idBarcode));
            if (item.expiryDate != null) {
//                addPluginPropertyElement(good, "composition", "Годен до: " + formatDate(item.expiryDate, "dd.MM.yyyy") + " ");
                addPluginPropertyElement(good, "best-before", formatDate(item.expiryDate, "yyyy-MM-dd"));
            }
            if (item.daysExpiry != null) {
                addPluginPropertyElement(good, "use-by-date", item.daysExpiry);
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

        boolean isProductSetApiEntity = false;

        if (infoJSON != null) {
            String ntin = trimToNull(infoJSON.optString("ntin"));
            if (ntin != null) {
                addPluginPropertyElement(good, "uz-ffd-spic", ntin);
            }

            addStringElement(good, "energy", String.valueOf(infoJSON.optBoolean("energy")));

            if (infoJSON.has("age")) {
                addIntegerElement(good, "age-limit", infoJSON.getInt("age"));
            }

            if (infoJSON.has("weight")) {
                addIntegerElement(good, "weight", infoJSON.getInt("weight"));
            }

            if (infoJSON.has("plugin_id")) {
                isProductSetApiEntity = true;
                addPluginPropertyElement(good, "plugin_id", infoJSON.getString("plugin_id"));
            }

            if (infoJSON.has("need_tare")) {
                addPluginPropertyElement(good, "need_tare", infoJSON.optBoolean("need_tare"));
            }
        }

        if (extraInfoJSON != null) {
            Boolean ukz = getUKZ(extraInfoJSON);
            if (ukz != null && ukz) {
                addPluginPropertyElement(good, "by-need-scan-ukz", "true");
            }

            if (extraInfoJSON.has("lottype")) {
                String lotType = extraInfoJSON.getString("lottype");
                if (lotType != null && !lotType.equals("ukz"))
                    addStringElement(good, "mark-type", getMarkType(lotType));
            }
        }

        addProductType(good, item, tobaccoGroups, infoJSON, isProductSetApiEntity);

        good.addContent(createBarcodeElement(good, item, idItem, barcodeItem, exportAmountForBarcode, deleteBarcodeMap, usedDeleteBarcodes, notGTINPrefixes, infoJSON, extraInfoJSON));

        return good;
    }

    private String getMarkType(String lotType) {
        switch (lotType) {
            case "shoes":
                return "FOOTWEAR";
            case "perfumery":
                return "PERFUMES";
            case "clothes":
                return "LIGHT_INDUSTRY";
            case "milk":
                return "MILK";
            case "tires":
                return "TYRES";
            case "photo":
                return "PHOTO";
            case "bike":
                return "BICYCLES";
            case "antiseptics":
                return "ANTISEPTIC";
            case "veterinaryMedicines":
                return "MEDICAL_DEVICES";
            case "water":
                return "WATER";
            case "animalFeed":
                return "PET_FOOD";
            case "juice":
                return "WATER_AND_BEVERAGES";
            case "preserves":
                return "CANNED_FOOD";
            case "dietarySupplements":
                return "DIETARYSUP";
            case "caviar":
                return "CAVIAR";
            case "oilFat":
                return "OIL";
            case "cosmetics":
                return "COSMETICS_AND_HOUSEHOLD_CHEMICALS";
            case "grocery":
                return "GROCERIES";
            default:
                return lotType;
        }
    }

    protected void addStopListItems(Element parent, StopListInfo stopListInfo,
                                    boolean useShopIndices, boolean idItemInMarkingOfTheGood,
                                    boolean skipWeightPrefix, List<String> tobaccoGroups,
                                    boolean useNumberGroupInShopIndices, boolean useSectionAsDepartNumber,
                                    boolean minusOneForEmptyVAT) {
        for (Map.Entry<String, StopListItem> entry : stopListInfo.stopListItemMap.entrySet()) {
            String idBarcode = entry.getKey();
            ItemInfo item = entry.getValue();

            //parent: rootElement
            Element good = new Element("good");
            idBarcode = transformBarcode(idBarcode, skipWeightPrefix);
            setAttribute(good, "marking-of-the-good", idItemInMarkingOfTheGood ? item.idItem : idBarcode);
            addStringElement(good, "name", item.name.replace("«",  "\"").replace("»", "\""));

            addProductType(good, item, tobaccoGroups, null, false);

            if (useShopIndices) {
                StringBuilder shopIndices = new StringBuilder();
                Set<MachineryInfo> machineryInfoSet = stopListInfo.handlerMachineryMap.get(getClass().getName());
                if (machineryInfoSet != null) {
                    Set<String> stockSet = new HashSet<>();
                    for (MachineryInfo machineryInfo : machineryInfoSet) {
                        if (machineryInfo instanceof CashRegisterInfo)
                            stockSet.add(getIdDepartmentStore(machineryInfo.numberGroup, ((CashRegisterInfo) machineryInfo).section, useNumberGroupInShopIndices));
                    }
                    for (String idStock : stockSet) {
                        shopIndices.append(idStock).append(" ");
                    }
                }
                shopIndices = new StringBuilder((shopIndices.length() == 0) ? shopIndices.toString() : shopIndices.substring(0, shopIndices.length() - 1));
                addStringElement(good, "shop-indices", shopIndices.toString());
            }

            //parent: good
            Element barcode = createBarcodeElement(item.idBarcode, "true");
            good.addContent(barcode);

            boolean noPriceEntry = true;
            Set<MachineryInfo> machineryInfoSet = stopListInfo.handlerMachineryMap.get(getClass().getName());
            if (machineryInfoSet != null) {
                Set<Integer> departNumberSet = new HashSet<>();
                for (MachineryInfo machineryInfo : machineryInfoSet) {
                    if (machineryInfo instanceof CashRegisterInfo) {
                        CashRegisterInfo c = (CashRegisterInfo) machineryInfo;
                        JSONObject infoJSON = getExtInfo(item.info);
                        String section = infoJSON != null ? infoJSON.optString("section") : null;
                        departNumberSet.add(getDepartNumber(section, c.overDepartNumber != null ? c.overDepartNumber : c.numberGroup, useSectionAsDepartNumber));
                    }
                }
                noPriceEntry = departNumberSet.isEmpty();
                for (Integer departNumber : departNumberSet) {
                    addPriceEntryElement(good, null, 1, true, formatDate(stopListInfo.dateFrom, "yyyy-MM-dd"), null, 1, departNumber);
                }
            }
            if (noPriceEntry) {
                addPriceEntryElement(good, null, 1, true, formatDate(stopListInfo.dateFrom, "yyyy-MM-dd"), null, 1, null);
            }

            Element measureType = new Element("measure-type");
            setAttribute(measureType, "id", item.idUOM);
            addStringElement(measureType, "name", item.shortNameUOM);
            good.addContent(measureType);

            addVATElement(good, item, minusOneForEmptyVAT);

            //parent: good
            Element group = new Element("group");
            setAttribute(group, "id", item.idItemGroup);
            addStringElement(group, "name", item.nameItemGroup);
            good.addContent(group);

            parent.addContent(good);
        }
    }

    protected String getShopIndices(TransactionCashRegisterInfo transaction, CashRegisterItem item, boolean useNumberGroupInShopIndices, boolean useShopIndices, String weightShopIndices) {
        String shopIndices = getIdDepartmentStore(transaction.nppGroupMachinery, transaction.idDepartmentStoreGroupCashRegister, useNumberGroupInShopIndices);
        if (useShopIndices && item.passScalesItem && weightShopIndices != null) {
            shopIndices += " " + weightShopIndices;
        }
        return shopIndices;
    }

    private Element createBarcodeElement(Element good, CashRegisterItem item, String idItem, String barcodeItem, boolean exportAmountForBarcode,
                                      Map<String, String> deleteBarcodeMap, DeleteBarcode usedDeleteBarcodes, List<String> notGTINPrefixes,
                                      JSONObject infoJSON, JSONObject extraInfoJSON) {
        Element barcodeElement = createBarcodeElement(barcodeItem,  (item.mainBarcode != null && !item.mainBarcode.equals(item.idBarcode)) ? "false" : "true");
        if (exportAmountForBarcode && item.amountBarcode != null && BigDecimal.ONE.compareTo(item.amountBarcode) != 0) {
            addBigDecimalElement(barcodeElement, "count", item.amountBarcode);
        }
        if (infoJSON != null) {
            String uzFfdPackageCode = infoJSON.optString("uzFfdPackageCode");
            if (notNullNorEmpty(uzFfdPackageCode))
                addPluginPropertyElement(barcodeElement, "uzFfdPackageCode", uzFfdPackageCode);
        }

        if (extraInfoJSON != null) {
            Boolean ukz = getUKZ(extraInfoJSON);
            if (ukz != null) {
                setAttribute(barcodeElement, "marked", !ukz);
            }
            if(extraInfoJSON.has("gtin")) {
                String gtin = extraInfoJSON.getString("gtin");
                if(!gtin.equals(barcodeItem)) {
                    Element gtinBarcode = createBarcodeElement(gtin, null);
                    gtinBarcode.setAttribute("barcode-type", "GTIN");
                    good.addContent(gtinBarcode);
                }
            }
        }

        List<String> deleteBarcodeList = new ArrayList<>();
        if(deleteBarcodeMap != null && deleteBarcodeMap.containsValue(idItem)) {
            for(Map.Entry<String, String> entry : deleteBarcodeMap.entrySet()) {
                if(entry.getValue().equals(idItem)){
                    deleteBarcodeList.add(entry.getKey());
                }
            }
            usedDeleteBarcodes.barcodes.add(item.idBarcode);
        }

        for(String deleteBarcode : deleteBarcodeList) {
            Element deleteBarcodeElement = createBarcodeElement(deleteBarcode, null);
            setAttribute(deleteBarcodeElement, "deleted", true);
            good.addContent(deleteBarcodeElement);
        }

        if (notGTINPrefixes != null) {
            if (barcodeItem != null && barcodeItem.length() > 7) {
                for (String notGTINPrefix : notGTINPrefixes) {
                    if (!barcodeItem.startsWith(notGTINPrefix)) {
                        barcodeElement.setAttribute("barcode-type", "GTIN");
                        break;
                    }
                }
            }
        }

        return barcodeElement;
    }

    private Element createBarcodeElement(String code, String defaultCode) {
        Element barcodeElement = new Element("bar-code");
        setAttribute(barcodeElement, "code", code);
        if(defaultCode != null) {
            addStringElement(barcodeElement, "default-code", defaultCode);
        }
        return barcodeElement;
    }

    private Boolean getUKZ(JSONObject extraInfoJSON) {
        Boolean ukz = null;
        if (extraInfoJSON.has("ukz")) {
            ukz = extraInfoJSON.getBoolean("ukz");
        } else if (extraInfoJSON.has("lottype")) {
            ukz = extraInfoJSON.getString("lottype").equals("ukz");
        }
        return ukz;
    }

    protected void fillRestrictionsElement(Element rootElement, CashRegisterItem item, String idItem, String barcodeItem,
                                           boolean useIdItemInRestriction, String shopIndices, boolean useShopIndices, boolean skipUseShopIndicesMinPrice) {
        //parent: rootElement
        if (item.minPrice != null) {
            Element minPriceRestriction = new Element("min-price-restriction");
            setAttribute(minPriceRestriction, "id", "MP-" + (useIdItemInRestriction ? idItem : barcodeItem) + "-" + shopIndices);
            setAttribute(minPriceRestriction, "subject-type", "GOOD");
            setAttribute(minPriceRestriction, "subject-code", idItem);
            setAttribute(minPriceRestriction, "type", "MIN_PRICE");
            setAttribute(minPriceRestriction, "value", item.minPrice != null ? item.minPrice : BigDecimal.ZERO);
            addStringElement(minPriceRestriction, "since-date", currentDate());
            addStringElement(minPriceRestriction, "till-date", formatDateTime(item.restrictionToDateTime, "yyyy-MM-dd'T'HH:mm:ss", "2051-01-01T23:59:59"));
            addStringElement(minPriceRestriction, "since-time", "00:00:00");
            addStringElement(minPriceRestriction, "till-time", formatDateTime(item.restrictionToDateTime, "HH:mm:ss", "23:59:59"));
            addStringElement(minPriceRestriction, "deleted", item.minPrice.compareTo(BigDecimal.ZERO) != 0 ? "false" : "true");
            addStringElement(minPriceRestriction, "days-of-week", "MO TU WE TH FR SA SU");
            if (useShopIndices && !skipUseShopIndicesMinPrice)
                addStringElement(minPriceRestriction, "shop-indices", shopIndices);
            rootElement.addContent(minPriceRestriction);
        }

        //parent: rootElement
        Element maxDiscountRestriction = new Element("max-discount-restriction");
        setAttribute(maxDiscountRestriction, "id", useIdItemInRestriction ? idItem : barcodeItem);
        setAttribute(maxDiscountRestriction, "subject-type", "GOOD");
        setAttribute(maxDiscountRestriction, "subject-code", idItem);
        setAttribute(maxDiscountRestriction, "type", "MAX_DISCOUNT_PERCENT");
        setAttribute(maxDiscountRestriction, "value", "0");
        addStringElement(maxDiscountRestriction, "since-date", currentDate());
        addStringElement(maxDiscountRestriction, "till-date", formatDateTime(item.restrictionToDateTime, "yyyy-MM-dd'T'HH:mm:ss", "2051-01-01T23:59:59"));
        addStringElement(maxDiscountRestriction, "since-time", "00:00:00");
        addStringElement(maxDiscountRestriction, "till-time", formatDateTime(item.restrictionToDateTime, "HH:mm:ss", "23:59:59"));
        addStringElement(maxDiscountRestriction, "deleted", item.flags != null && ((item.flags & 16) == 0) ? "false" : "true");
        if (useShopIndices)
            addStringElement(maxDiscountRestriction, "shop-indices", shopIndices);
        rootElement.addContent(maxDiscountRestriction);
    }

    protected static void addProductType(Element good, ItemInfo item, List<String> tobaccoGroups, JSONObject infoJSON, boolean isProductSetApiEntity) {
        String productType;
        if(infoJSON != null && infoJSON.has("product-type")) {
            productType = infoJSON.getString("product-type");
        } else if(isProductSetApiEntity) {
            productType = "ProductSetApiEntity";
        } else if(item.idItemGroup != null && tobaccoGroups != null && tobaccoGroups.contains(item.idItemGroup))
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

    protected static void addVATElement(Element good, ItemInfo item, boolean minusOneForEmptyVAT) {
        addStringElement(good, "vat", item.vat == null || item.vat.intValue() == 0 ? (minusOneForEmptyVAT ? "-1" : "20") : String.valueOf(item.vat.intValue()));
    }

    protected static void addPluginPropertyElement(Element parent, String key, Object value) {
        if (value != null) {
            Element pluginProperty = new Element("plugin-property");
            setAttribute(pluginProperty, "key", key);
            setAttribute(pluginProperty, "value", value);
            parent.addContent(pluginProperty);
        }
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
        if (infoJSON == null || !infoJSON.optBoolean("skipPriceEntry")) {
            Object price = item.price == null ? null : (item.price.doubleValue() == 0.0 ? "0.00" : item.price);
            addPriceEntryElement(parentElement, idItem, price, false, shopIndices, currentDate(), null, 1, getDepartNumber(transaction, item, useSectionAsDepartNumber), false);
        }

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
        return getExtInfo(extInfo, "kristal10");
    }

    protected JSONObject getExtraInfo(String extraInfo) {
        return extraInfo != null && !extraInfo.isEmpty() ? new JSONObject(extraInfo) : null;
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
        Set<String> idZReportSet = new HashSet<>();

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
                idZReportSet.add(idZReport);
        }
        return idZReportSet.isEmpty() && (message.length() == 0) ? null : new ExtraCheckZReportBatch(idZReportSet, message.toString());
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

    protected String getCashRegisterKey(String directory, Integer numberCashRegister, boolean ignoreSalesDepartmentNumber, String departNumber, boolean useShopIndices, String shop) {
        return directory + "_" + numberCashRegister + (ignoreSalesDepartmentNumber ? "" : ("_" + departNumber)) + (useShopIndices ? ("_" + shop) : "");
    }

    protected Map<String, List<CashRegisterInfo>>  getCashRegisterByKeyMap(List<CashRegisterInfo> cashRegisterInfoList, boolean useShopIndices, boolean useNumberGroupInShopIndices, boolean ignoreSalesDepartmentNumber) {
        Map<String, List<CashRegisterInfo>> cashRegisterByKeyMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.directory != null && c.number != null) {
                String idDepartmentStore = getIdDepartmentStore(c.numberGroup, c.idDepartmentStore, useNumberGroupInShopIndices);
                String key = c.directory + "_" + c.number + (ignoreSalesDepartmentNumber ? "" : ("_" + c.overDepartNumber)) + (useShopIndices ? ("_" + idDepartmentStore) : "");

                List<CashRegisterInfo> keyCashRegisterList = cashRegisterByKeyMap.getOrDefault(key, new ArrayList<>());
                keyCashRegisterList.add(c);
                cashRegisterByKeyMap.put(key, keyCashRegisterList);
            }
        }
        return cashRegisterByKeyMap;
    }

    protected CashRegisterInfo getCashRegister(Map<String, List<CashRegisterInfo>> cashRegisterMap, String key) {
        //ищем кассу без disableSales. Если все с disableSales, берём первую
        CashRegisterInfo cashRegister = null;
        if (cashRegisterMap != null) {
            List<CashRegisterInfo> cashRegisterList = cashRegisterMap.get(key);
            if (cashRegisterList != null) {
                for (CashRegisterInfo c : cashRegisterList) {
                    if (!c.disableSales) {
                        cashRegister = c;
                        break;
                    }
                }
                if (cashRegister == null) {
                    cashRegister = cashRegisterList.get(0);
                }
            }
        }
        return cashRegister;
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
