package equ.clt.handler.kristal10;

import com.google.common.base.Throwables;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.handler.HandlerUtils;
import lsfusion.base.DaemonThreadFactory;
import lsfusion.base.file.IOUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jdom.*;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static equ.clt.handler.HandlerUtils.*;

public class Kristal10WebHandler extends Kristal10DefaultHandler {

    private static final Namespace soapNamespace = Namespace.getNamespace("soap", "http://schemas.xmlsoap.org/soap/envelope/");
    private static final Namespace soapenvNamespace = Namespace.getNamespace("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
    private static final Namespace plugProductsNamespace = Namespace.getNamespace("plug", "http://plugins.products.ERPIntegration.crystals.ru/");
    private static final Namespace plugOperdayNamespace = Namespace.getNamespace("plug", "http://plugins.operday.ERPIntegration.crystals.ru/");
    private static final Namespace webNamespace = Namespace.getNamespace("web", "http://webservice.importing.plugins.cards.ERPIntegration.crystals.ru/");
    private static final Namespace ns1PurchasesNamespace = Namespace.getNamespace("ns1", "http://purchases.erpi.crystals.ru");
    private static final Namespace ns1IntroductionsNamespace = Namespace.getNamespace("ns1", "http://introductions.erpi.crystals.ru");
    private static final Namespace ns1WithdrawalsNamespace = Namespace.getNamespace("ns1", "http://withdrawals.erpi.crystals.ru");
    private static final Namespace ns1ZReportsNamespace = Namespace.getNamespace("ns1", "http://zreports.erpi.crystals.ru");
    private static final Namespace ns2ProductsNamespace = Namespace.getNamespace("ns2", "http://plugins.products.ERPIntegration.crystals.ru/");

    private final Map<String, List<String>> requestSalesInfoMap = new HashMap<>();
    // ----------------- http server ----------------- //
    private HttpRequestHandler httpRequestHandler;

    public Kristal10WebHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
        bindHttpServer();
    }

    private void bindHttpServer() {
        sendSalesLogger.info(getLogPrefix() + "Binding HttpServer");

        HttpServer httpServer = null;
        try {
            httpServer = HttpServer.create(new InetSocketAddress(8090), 0);
            httpRequestHandler = new HttpRequestHandler();
            httpServer.createContext("/", httpRequestHandler);
            httpServer.setExecutor(Executors.newFixedThreadPool(10, new DaemonThreadFactory("httpServerKristal10-daemon")));
            httpServer.start();
        } catch (Exception e) {
            if (httpServer != null)
                httpServer.stop(0);
            e.printStackTrace();
        }
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "kristal10Web";
    }

    protected String getLogPrefix() {
        return "Kristal10Web: ";
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) {
        Map<Long, SendTransactionBatch> result = new HashMap<>();
        Map<Long, DeleteBarcode> usedDeleteBarcodeTransactionMap = new HashMap<>();

        for(TransactionCashRegisterInfo transaction : transactionList) {

            try {

                processTransactionLogger.info(getLogPrefix() + "Send Transaction # " + transaction.id);

                List<String> directoriesList = new ArrayList<>();
                for (CashRegisterInfo cashRegisterInfo : transaction.machineryInfoList) {
                    if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory)))
                        directoriesList.add(cashRegisterInfo.directory);
                }

                for (String directory : directoriesList) {

                    DeleteBarcode usedDeleteBarcodes = new DeleteBarcode(transaction.nppGroupMachinery, directory);

                    List<String> xmlList = new ArrayList<>();
                    xmlList.add(docToXMLString(generateCatalogGoodsItemsXML(transaction)));
                    xmlList.add(docToXMLString(generateCatalogGoodsBarcodesXML(transaction, deleteBarcodeDirectoryMap.get(directory), usedDeleteBarcodes)));
                    xmlList.add(docToXMLString(generateCatalogGoodsPricesXML(transaction)));
                    xmlList.add(docToXMLString(generateCatalogGoodsRestrictionsXML(transaction)));

                    usedDeleteBarcodeTransactionMap.put(transaction.id, usedDeleteBarcodes);

                    String response = null;
                    for(String xml : xmlList) {
                        response = sendRequestGoods(directory, xml);
                        if(response != null)
                            break;
                    }

                    if (response != null) {
                        processTransactionLogger.error(getLogPrefix() + response);
                        result.put(transaction.id, new SendTransactionBatch(new RuntimeException(response)));
                    } else {
                        DeleteBarcode deleteBarcodes = usedDeleteBarcodeTransactionMap.get(transaction.id);
                        if (deleteBarcodes != null) {
                            for (String b : deleteBarcodes.barcodes) {
                                Map<String, String> deleteBarcodesEntry = deleteBarcodeDirectoryMap.get(deleteBarcodes.directory);
                                deleteBarcodesEntry.remove(b);
                                deleteBarcodeDirectoryMap.put(deleteBarcodes.directory, deleteBarcodesEntry);
                            }
                        }
                        result.put(transaction.id, new SendTransactionBatch(null, null, deleteBarcodes == null ? null : deleteBarcodes.nppGroupMachinery, deleteBarcodes == null ? null : deleteBarcodes.barcodes, null));
                    }
                }
            } catch (Exception e) {
                processTransactionLogger.error(getLogPrefix(), e);
                result.put(transaction.id, new SendTransactionBatch(e));
            }
        }
        return result;
    }
    // ----------------- http server end ----------------- //

    private Document generateCatalogGoodsItemsXML(TransactionCashRegisterInfo transaction) {

        processTransactionLogger.info(getLogPrefix() + "creating catalog-goods file with items (Transaction " + transaction.id + ") - " + transaction.itemsList.size() + " items");

        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        boolean brandIsManufacturer = kristalSettings != null && kristalSettings.getBrandIsManufacturer() != null && kristalSettings.getBrandIsManufacturer();
        boolean seasonIsCountry = kristalSettings != null && kristalSettings.getSeasonIsCountry() != null && kristalSettings.getSeasonIsCountry();
        boolean idItemInMarkingOfTheGood = kristalSettings != null && kristalSettings.isIdItemInMarkingOfTheGood() != null && kristalSettings.isIdItemInMarkingOfTheGood();
        boolean skipWeightPrefix = kristalSettings != null && kristalSettings.getSkipWeightPrefix() != null && kristalSettings.getSkipWeightPrefix();
        boolean skipScalesInfo = kristalSettings != null && kristalSettings.getSkipScalesInfo() != null && kristalSettings.getSkipScalesInfo();
        boolean useShopIndices = kristalSettings != null && kristalSettings.getUseShopIndices() != null && kristalSettings.getUseShopIndices();
        String weightShopIndices = kristalSettings != null ? kristalSettings.getWeightShopIndices() : null;
        List<String> tobaccoGroups = getTobaccoGroups(kristalSettings != null ? kristalSettings.getTobaccoGroup() : null);
        boolean useNumberGroupInShopIndices = kristalSettings != null && kristalSettings.useNumberGroupInShopIndices();

        Element rootElement = new Element("goods-catalog");
        Document doc = new Document(rootElement);

        String weightCode = transaction.weightCodeGroupCashRegister == null ? "21" : transaction.weightCodeGroupCashRegister;

        for (CashRegisterItemInfo item : transaction.itemsList) {
            if (!Thread.currentThread().isInterrupted()) {

                JSONObject infoJSON = item.info != null ? new JSONObject(item.info).optJSONObject("kristal10") : null;

                String shopIndices = getShopIndices(transaction, item, useNumberGroupInShopIndices, useShopIndices, weightShopIndices);

                //parent: rootElement
                Element good = new Element("good");

                String barcodeItem = transformBarcode(item.idBarcode, weightCode, item.passScalesItem, skipWeightPrefix);
                String idItem = idItemInMarkingOfTheGood ? item.idItem : barcodeItem;

                setAttribute(good, "marking-of-the-good", idItem);

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

                rootElement.addContent(good);

                if (useShopIndices)
                    addStringElement(good, "shop-indices", shopIndices);

                addStringElement(good, "name", item.name);

                if (infoJSON != null) {
                    addStringElement(good, "energy", String.valueOf(infoJSON.optBoolean("energy")));
                }

                addProductType(good, item, tobaccoGroups);

                if(item.splitItem && !item.passScalesItem) {
                    Element pluginProperty = new Element("plugin-property");
                    setAttribute(pluginProperty, "key", "precision");
                    setAttribute(pluginProperty, "value", "0.001");
                    good.addContent(pluginProperty);
                }

                int vat = item.vat == null || item.vat.intValue() == 0 ? 20 : item.vat.intValue();
                if(vat != 10 && vat != 20) {
                    vat = 20;
                }
                addStringElement(good, "vat", String.valueOf(vat));

                //parent: good
                Element group = new Element("group");
                setAttribute(group, "id", item.idItemGroup);
                addStringElement(group, "name", item.nameItemGroup);
                good.addContent(group);

                List<ItemGroup> hierarchyItemGroup = transaction.itemGroupMap.get(item.idItemGroup);
                if (hierarchyItemGroup != null)
                    addHierarchyItemGroup(group, hierarchyItemGroup.subList(1, hierarchyItemGroup.size()));

                //parent: good
                if (item.idUOM == null || item.shortNameUOM == null) {
                    String error = getLogPrefix() + "Error! UOM not specified for item with barcode " + barcodeItem;
                    processTransactionLogger.error(error);
                    throw new RuntimeException(error);
                }
                Element measureType = new Element("measure-type");
                setAttribute(measureType, "id", item.idUOM);
                addStringElement(measureType, "name", item.shortNameUOM);
                good.addContent(measureType);

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

                addStringElement(good, "delete-from-cash", "false");

            }
        }
        processTransactionLogger.info(String.format(getLogPrefix() + "created catalog-goods file with items (Transaction %s)", transaction.id));
        return doc;
    }

    private String getShopIndices(TransactionCashRegisterInfo transaction, CashRegisterItemInfo item, boolean useNumberGroupInShopIndices, boolean useShopIndices, String weightShopIndices) {
        String shopIndices = getIdDepartmentStore(transaction.nppGroupMachinery, transaction.idDepartmentStoreGroupCashRegister, useNumberGroupInShopIndices);
        if (useShopIndices && item.passScalesItem && weightShopIndices != null) {
            shopIndices += " " + weightShopIndices;
        }
        return shopIndices;
    }

    private Document generateCatalogGoodsBarcodesXML(TransactionCashRegisterInfo transaction, Map<String, String> deleteBarcodeMap, DeleteBarcode usedDeleteBarcodes) {

        processTransactionLogger.info(getLogPrefix() + "creating catalog-goods file with barcodes (Transaction " + transaction.id + ") - " + transaction.itemsList.size() + " items");

        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        boolean idItemInMarkingOfTheGood = kristalSettings != null && kristalSettings.isIdItemInMarkingOfTheGood() != null && kristalSettings.isIdItemInMarkingOfTheGood();
        boolean skipWeightPrefix = kristalSettings != null && kristalSettings.getSkipWeightPrefix() != null && kristalSettings.getSkipWeightPrefix();
        List<String> notGTINPrefixes = kristalSettings != null ? kristalSettings.getNotGTINPrefixesList() : null;

        Element rootElement = new Element("goods-catalog");
        Document doc = new Document(rootElement);

        String weightCode = transaction.weightCodeGroupCashRegister == null ? "21" : transaction.weightCodeGroupCashRegister;

        for (CashRegisterItemInfo item : transaction.itemsList) {
            if (!Thread.currentThread().isInterrupted()) {

                //parent: rootElement
                Element good = new Element("good");

                String barcodeItem = transformBarcode(item.idBarcode, weightCode, item.passScalesItem, skipWeightPrefix);
                String idItem = idItemInMarkingOfTheGood ? item.idItem : barcodeItem;

                setAttribute(good, "marking-of-the-good", idItem);

                List<String> deleteBarcodeList = new ArrayList<>();
                if(deleteBarcodeMap != null && deleteBarcodeMap.containsValue(idItem)) {
                    for(Map.Entry<String, String> entry : deleteBarcodeMap.entrySet()) {
                        if(entry.getValue().equals(idItem)){
                            deleteBarcodeList.add(entry.getKey());
                        }
                    }
                    usedDeleteBarcodes.barcodes.add(item.idBarcode);
                }

                rootElement.addContent(good);

                //parent: good
                Element barcode = new Element("bar-code");
                setAttribute(barcode, "code", barcodeItem);
                addStringElement(barcode, "default-code", "true");
                good.addContent(barcode);

                for(String deleteBarcode : deleteBarcodeList) {
                    //parent: good
                    Element deleteBarcodeElement = new Element("bar-code");
                    setAttribute(deleteBarcodeElement, "code", deleteBarcode);
                    setAttribute(deleteBarcodeElement, "deleted", true);
                    good.addContent(deleteBarcodeElement);
                }

                if (notGTINPrefixes != null) {
                    if (barcodeItem != null && barcodeItem.length() > 7) {
                        for (String notGTINPrefix : notGTINPrefixes) {
                            if (!barcodeItem.startsWith(notGTINPrefix)) {
                                barcode.setAttribute("barcode-type", "GTIN");
                                break;
                            }
                        }
                    }
                }
            }
        }
        processTransactionLogger.info(String.format(getLogPrefix() + "created catalog-goods file with barcodes (Transaction %s)", transaction.id));
        return doc;
    }

    private Document generateCatalogGoodsPricesXML(TransactionCashRegisterInfo transaction) {

        processTransactionLogger.info(getLogPrefix() + "creating catalog-goods file with prices(Transaction " + transaction.id + ") - " + transaction.itemsList.size() + " items");

        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        boolean idItemInMarkingOfTheGood = kristalSettings != null && kristalSettings.isIdItemInMarkingOfTheGood() != null && kristalSettings.isIdItemInMarkingOfTheGood();
        boolean skipWeightPrefix = kristalSettings != null && kristalSettings.getSkipWeightPrefix() != null && kristalSettings.getSkipWeightPrefix();
        boolean useSectionAsDepartNumber = kristalSettings != null && kristalSettings.useSectionAsDepartNumber();

        Element rootElement = new Element("goods-catalog");
        Document doc = new Document(rootElement);

        String weightCode = transaction.weightCodeGroupCashRegister == null ? "21" : transaction.weightCodeGroupCashRegister;

        for (CashRegisterItemInfo item : transaction.itemsList) {
            if (!Thread.currentThread().isInterrupted()) {

                JSONObject infoJSON = item.info != null ? new JSONObject(item.info).optJSONObject("kristal10") : null;

                //parent: rootElement
                Element good = new Element("good");

                String barcodeItem = transformBarcode(item.idBarcode, weightCode, item.passScalesItem, skipWeightPrefix);
                String idItem = idItemInMarkingOfTheGood ? item.idItem : barcodeItem;

                setAttribute(good, "marking-of-the-good", idItem);

                rootElement.addContent(good);

                //parent: good
                Element priceEntry = new Element("price-entry");
                Object price = item.price == null ? null : (item.price.doubleValue() == 0.0 ? "0.00" : item.price);
                setAttribute(priceEntry, "price", price);
                setAttribute(priceEntry, "deleted", "false");
                addStringElement(priceEntry, "begin-date", currentDate());
                addStringElement(priceEntry, "number", "1");
                good.addContent(priceEntry);

                //parent: priceEntry
                Element department = new Element("department");
                setAttribute(department, "number", getDepartNumber(transaction, item, useSectionAsDepartNumber));
                priceEntry.addContent(department);

                Double secondPrice = infoJSON != null ? infoJSON.optDouble("secondPrice") : null;
                String secondPriceBeginDate = infoJSON != null ? infoJSON.optString("secondBeginDate", null) : null;
                String secondPriceEndDate = infoJSON != null ? infoJSON.optString("secondEndDate", null) : null;
                boolean secondPriceDeleted = infoJSON != null && infoJSON.optBoolean("secondDeleted");
                if (secondPrice != null && !secondPrice.isNaN()) {
                    //parent: good
                    Element secondPriceEntry = new Element("price-entry");
                    setAttribute(secondPriceEntry, "price", secondPrice);
                    setAttribute(secondPriceEntry, "deleted", "false");
                    addStringElement(secondPriceEntry, "begin-date", secondPriceBeginDate != null ? secondPriceBeginDate : currentDate());
                    if(secondPriceEndDate != null) {
                        addStringElement(secondPriceEntry, "end-date", secondPriceEndDate);
                    }
                    addStringElement(secondPriceEntry, "deleted", String.valueOf(secondPriceDeleted));
                    addStringElement(secondPriceEntry, "number", "2");
                    good.addContent(secondPriceEntry);

                    //parent: priceEntry
                    Element secondDepartment = new Element("department");
                    setAttribute(secondDepartment, "number", getDepartNumber(transaction, item, useSectionAsDepartNumber));
                    secondPriceEntry.addContent(secondDepartment);
                }

                Double oldSecondPrice = infoJSON != null ? infoJSON.optDouble("oldSecondPrice") : null;
                if (oldSecondPrice != null && !oldSecondPrice.isNaN() && !oldSecondPrice.equals(secondPrice)) {
                    //parent: good
                    Element oldSecondPriceEntry = new Element("price-entry");
                    setAttribute(oldSecondPriceEntry, "price", oldSecondPrice);
                    setAttribute(oldSecondPriceEntry, "deleted", "true");
                    addStringElement(oldSecondPriceEntry, "number", "2");
                    good.addContent(oldSecondPriceEntry);

                    //parent: priceEntry
                    Element secondDepartment = new Element("department");
                    setAttribute(secondDepartment, "number", getDepartNumber(transaction, item, useSectionAsDepartNumber));
                    oldSecondPriceEntry.addContent(secondDepartment);
                }

                int zone = infoJSON != null ? infoJSON.optInt("zone") : 0;
                int countZone = infoJSON != null ? infoJSON.optInt("countZone") : 0;
                if(zone != 0 && countZone != 0) {
                    for(int i = 1; i <= countZone; i++) {
                        if (i == zone) {
                            addPriceEntryElement(good, price, false, currentDate(), null, "1", i);
                        } else {
                            addPriceEntryElement(good, 1, true, null, null, "1", i);
                        }
                    }
                }
            }
        }
        processTransactionLogger.info(String.format(getLogPrefix() + "created catalog-goods file with prices (Transaction %s)", transaction.id));
        return doc;
    }

    private Document generateCatalogGoodsRestrictionsXML(TransactionCashRegisterInfo transaction) {

        processTransactionLogger.info(getLogPrefix() + "creating catalog-goods file with restrictions (Transaction " + transaction.id + ") - " + transaction.itemsList.size() + " items");

        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        boolean idItemInMarkingOfTheGood = kristalSettings != null && kristalSettings.isIdItemInMarkingOfTheGood() != null && kristalSettings.isIdItemInMarkingOfTheGood();
        boolean skipWeightPrefix = kristalSettings != null && kristalSettings.getSkipWeightPrefix() != null && kristalSettings.getSkipWeightPrefix();
        boolean useShopIndices = kristalSettings != null && kristalSettings.getUseShopIndices() != null && kristalSettings.getUseShopIndices();
        boolean skipUseShopIndicesMinPrice = kristalSettings != null && kristalSettings.getSkipUseShopIndicesMinPrice() != null && kristalSettings.getSkipUseShopIndicesMinPrice();
        String weightShopIndices = kristalSettings != null ? kristalSettings.getWeightShopIndices() : null;
        boolean useIdItemInRestriction = kristalSettings != null && kristalSettings.getUseIdItemInRestriction() != null && kristalSettings.getUseIdItemInRestriction();
        boolean useNumberGroupInShopIndices = kristalSettings != null && kristalSettings.useNumberGroupInShopIndices();

        Element rootElement = new Element("goods-catalog");
        Document doc = new Document(rootElement);

        String weightCode = transaction.weightCodeGroupCashRegister == null ? "21" : transaction.weightCodeGroupCashRegister;

        for (CashRegisterItemInfo item : transaction.itemsList) {
            if (!Thread.currentThread().isInterrupted()) {

                String shopIndices = getShopIndices(transaction, item, useNumberGroupInShopIndices, useShopIndices, weightShopIndices);
                //parent: rootElement
                Element good = new Element("good");

                String barcodeItem = transformBarcode(item.idBarcode, weightCode, item.passScalesItem, skipWeightPrefix);
                String idItem = idItemInMarkingOfTheGood ? item.idItem : barcodeItem;

                setAttribute(good, "marking-of-the-good", idItem);

                rootElement.addContent(good);

                //parent: rootElement
                if (item.minPrice != null) {
                    Element minPriceRestriction = new Element("min-price-restriction");
                    setAttribute(minPriceRestriction, "id", "MP-" + (useIdItemInRestriction ? idItem : barcodeItem) + "-" + shopIndices);
                    setAttribute(minPriceRestriction, "subject-type", "GOOD");
                    setAttribute(minPriceRestriction, "subject-code", idItem);
                    setAttribute(minPriceRestriction, "type", "MIN_PRICE");
                    setAttribute(minPriceRestriction, "value", item.minPrice != null ? item.minPrice : BigDecimal.ZERO);
                    addStringElement(minPriceRestriction, "since-date", currentDate());
                    addStringElement(minPriceRestriction, "till-date", formatDateTime(item.restrictionToDateTime, "yyyy-MM-dd'T'HH:mm:ss", "2021-01-01T23:59:59"));
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
                addStringElement(maxDiscountRestriction, "till-date", formatDateTime(item.restrictionToDateTime, "yyyy-MM-dd'T'HH:mm:ss", "2021-01-01T23:59:59"));
                addStringElement(maxDiscountRestriction, "since-time", "00:00:00");
                addStringElement(maxDiscountRestriction, "till-time", formatDateTime(item.restrictionToDateTime, "HH:mm:ss", "23:59:59"));
                addStringElement(maxDiscountRestriction, "deleted", item.flags != null && ((item.flags & 16) == 0) ? "false" : "true");
                if (useShopIndices)
                    addStringElement(maxDiscountRestriction, "shop-indices", shopIndices);
                rootElement.addContent(maxDiscountRestriction);
            }
        }
        processTransactionLogger.info(String.format(getLogPrefix() + "created catalog-goods file with restrictions (Transaction %s)", transaction.id));
        return doc;
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList,
                                 Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        boolean useNumberGroupInShopIndices = kristalSettings != null && kristalSettings.useNumberGroupInShopIndices();

        for (RequestExchange entry : requestExchangeList) {
            for (Map.Entry<String, Set<String>> directoryStockEntry : getDirectoryStockMap(entry, useNumberGroupInShopIndices).entrySet()) {
                String directory = directoryStockEntry.getKey();
                Set<String> stockSet = new HashSet<>();//directoryStockEntry.getValue(); todo: для теста

                List<String> requestSalesInfoEntry = requestSalesInfoMap.getOrDefault(directory, new ArrayList<>());
                requestSalesInfoEntry.addAll(generateRequestPurchasesByParams(entry.dateFrom, entry.dateTo, stockSet, getCashRegisterSet(entry, false)));
                requestSalesInfoMap.put(directory, requestSalesInfoEntry);
            }
        }
    }

    private List<String> generateRequestPurchasesByParams(LocalDate dateFrom, LocalDate dateTo, Set<String> stockSet, Set<CashRegisterInfo> cashRegisterSet) {
        List<String> result = new ArrayList<>();
        while(dateFrom.compareTo(dateTo) <= 0) {
            result.addAll(generateRequestPurchasesByParams(dateFrom, stockSet, cashRegisterSet));
            dateFrom = dateFrom.plusDays(1);
        }
        return result;
    }

    private List<String> generateRequestPurchasesByParams(LocalDate date, Set<String> stockSet, Set<CashRegisterInfo> cashRegisterSet) {
        List<String> result = new ArrayList<>();
        if (stockSet.isEmpty()) {
            result.addAll(generateRequestPurchasesByParams(date, (String) null, cashRegisterSet));
        } else {
            for (String stock : stockSet) {
                result.addAll(generateRequestPurchasesByParams(date, stock, cashRegisterSet));
            }
        }
        return result;
    }

    private List<String> generateRequestPurchasesByParams(LocalDate date, String shopNumber, Set<CashRegisterInfo> cashRegisterSet) {
        List<String> result = new ArrayList<>();
        if (cashRegisterSet.isEmpty()) {
            result.add(generateRequestPurchasesByParams(date, shopNumber, (Integer) null));
        } else {
            for (CashRegisterInfo cashRegister : cashRegisterSet) {
                result.add(generateRequestPurchasesByParams(date, null, cashRegister.number));
            }
        }
        return result;
    }

    private String generateRequestPurchasesByParams(LocalDate date, String shopNumber, Integer cashNumber) {
        Element envelopeElement = new Element("Envelope", soapenvNamespace);
        //envelopeElement.setNamespace(soapenvNamespace);
        envelopeElement.addNamespaceDeclaration(plugOperdayNamespace);

        Element headerElement = new Element("Header", soapenvNamespace);
        envelopeElement.addContent(headerElement);

        Element bodyElement = new Element("Body", soapenvNamespace);
        envelopeElement.addContent(bodyElement);

        Element getNewPurchasesByParamsElement = new Element("getPurchasesByParams", plugOperdayNamespace);
        addStringElement(getNewPurchasesByParamsElement, "dateOperDay", date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        addStringElement(getNewPurchasesByParamsElement, "shopNumber", shopNumber);
        addIntegerElement(getNewPurchasesByParamsElement, "cashNumber", cashNumber);
        bodyElement.addContent(getNewPurchasesByParamsElement);

        return docToXMLString(new Document(envelopeElement));
    }

    @Override
    public void finishReadingSalesInfo(Kristal10SalesBatch salesBatch) {
        if(httpRequestHandler != null) {
            sendSalesLogger.info(getLogPrefix() + "Finish Reading started");
            try {
                List<Request> purchases = httpRequestHandler.popPurchases();
                for(Request purchase : purchases) {
                    sendPurchasesResponse(purchase.request, null);
                }
            } catch (IOException e) {
                sendSalesLogger.error(getLogPrefix(), e);
            }
        }
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) {
        List<CashDocument> cashDocumentList = new ArrayList<>();
        if(httpRequestHandler != null) {
            try {

                //обрабатываем внесения и изъятия, полученные httpServer'ом
                List<CashDocumentRequest> cashDocuments = httpRequestHandler.getCashDocuments();
                for(CashDocumentRequest cashDocument : cashDocuments) {
                    Document doc = xmlStringToDoc(cashDocument.xml);
                    cashDocumentList.addAll(parseCashDocumentXML(doc, cashRegisterInfoList, cashDocument.cashIn));
                }

            } catch (Throwable e) {
                sendSalesLogger.error(getLogPrefix() + "readSalesInfo", e);
            }
        }
        return new CashDocumentBatch(cashDocumentList, null);
    }

    public List<CashDocument> parseCashDocumentXML(Document doc, List<CashRegisterInfo> cashRegisterInfoList, boolean cashIn) {
        List<CashDocument> cashDocumentList = new ArrayList<>();

        Map<Integer, CashRegisterInfo> numberCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c) && c.number != null) {
                numberCashRegisterMap.put(c.number, c);
            }
        }

        Element rootNode = doc.getRootElement();

        List cashDocumentsList = rootNode.getChildren(cashIn ? "introduction" : "withdrawal");

        for (Object cashDocumentNode : cashDocumentsList) {

            String numberCashDocument = readStringXMLAttribute(cashDocumentNode, "number");

            Integer numberCashRegister = readIntegerXMLAttribute(cashDocumentNode, "cash");
            CashRegisterInfo cashRegister = numberCashRegisterMap.get(numberCashRegister);
            Integer numberGroup = cashRegister == null ? null : cashRegister.numberGroup;

            BigDecimal sumCashDocument = readBigDecimalXMLAttribute(cashDocumentNode, "amount");
            if(!cashIn)
                sumCashDocument = sumCashDocument == null ? null : sumCashDocument.negate();

            LocalDateTime dateTimeCashDocument = ZonedDateTime.parse(readStringXMLAttribute(cashDocumentNode, "regtime"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")).toLocalDateTime();

            cashDocumentList.add(new CashDocument(numberCashDocument, numberCashDocument, dateTimeCashDocument.toLocalDate(), dateTimeCashDocument.toLocalTime(),
                    numberGroup, numberCashRegister, null, sumCashDocument));
        }

        return cashDocumentList;
    }

    @Override
    public void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch) {
        if (httpRequestHandler != null) {
            sendSalesLogger.info(getLogPrefix() + "Finish ReadingCashDocumentInfo started");
            try {
                List<CashDocumentRequest> cashDocuments = httpRequestHandler.popCashDocuments();
                for (CashDocumentRequest cashDocument : cashDocuments) {
                    if (cashDocument.cashIn) {
                        sendIntroductionsResponse(cashDocument.request, null);
                    } else {
                        sendWithdrawalsResponse(cashDocument.request, null);
                    }
                }
            } catch (IOException e) {
                sendSalesLogger.error(getLogPrefix(), e);
            }
        }
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {
        //из-за временного решения с весовыми товарами для этих весовых товаров стоп-листы работать не будут
        if (!stopListInfo.exclude) {
            Document doc = generateStopListXML(stopListInfo);
            for (String directory : directorySet) {
                processStopListLogger.info(getLogPrefix() + String.format("Send StopList # %s to url %s", stopListInfo.number, directory));
                if (!stopListInfo.stopListItemMap.isEmpty()) {
                    try {
                        String response = sendRequestGoods(directory, docToXMLString(doc));
                        if (response != null) {
                            processStopListLogger.error(getLogPrefix() + response);
                            throw new RuntimeException(getLogPrefix() + response);
                        }
                    } catch (JDOMException e) {
                        processStopListLogger.error(getLogPrefix(), e);
                        throw Throwables.propagate(e);
                    }
                }
            }

        }
    }

    private Document generateStopListXML(StopListInfo stopListInfo) {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        boolean useShopIndices = kristalSettings == null || kristalSettings.getUseShopIndices() != null && kristalSettings.getUseShopIndices();
        boolean idItemInMarkingOfTheGood = kristalSettings == null || kristalSettings.isIdItemInMarkingOfTheGood() != null && kristalSettings.isIdItemInMarkingOfTheGood();
        boolean skipWeightPrefix = kristalSettings != null && kristalSettings.getSkipWeightPrefix() != null && kristalSettings.getSkipWeightPrefix();
        List<String> tobaccoGroups = getTobaccoGroups(kristalSettings != null ? kristalSettings.getTobaccoGroup() : null);
        boolean useNumberGroupInShopIndices = kristalSettings != null && kristalSettings.useNumberGroupInShopIndices();
        boolean useSectionAsDepartNumber = kristalSettings != null && kristalSettings.useSectionAsDepartNumber();

        if (stopListInfo.dateFrom == null || stopListInfo.timeFrom == null) {
            String error = getLogPrefix() + "Error! Start DateTime not specified for stopList " + stopListInfo.number;
            processStopListLogger.error(error);
            throw new RuntimeException(error);
        }

        if (stopListInfo.dateTo == null || stopListInfo.timeTo == null) {
            stopListInfo.dateTo = LocalDate.of(2040, 1, 1);
            stopListInfo.timeTo = LocalTime.of(23, 59, 59);
        }

        Element rootElement = new Element("goods-catalog");
        Document doc = new Document(rootElement);

        for (Map.Entry<String, ItemInfo> entry : stopListInfo.stopListItemMap.entrySet()) {
            String idBarcode = entry.getKey();
            ItemInfo item = entry.getValue();

            //parent: rootElement
            Element good = new Element("good");
            idBarcode = transformBarcode(idBarcode, null, false, skipWeightPrefix);
            setAttribute(good, "marking-of-the-good", idItemInMarkingOfTheGood ? item.idItem : idBarcode);
            addStringElement(good, "name", item.name);

            addProductType(good, item, tobaccoGroups);

            rootElement.addContent(good);

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
            Element barcode = new Element("bar-code");
            setAttribute(barcode, "code", item.idBarcode);
            addStringElement(barcode, "default-code", "true");
            good.addContent(barcode);

            boolean noPriceEntry = true;
            Set<MachineryInfo> machineryInfoSet = stopListInfo.handlerMachineryMap.get(getClass().getName());
            if (machineryInfoSet != null) {
                Set<Integer> departNumberSet = new HashSet<>();
                for (MachineryInfo machineryInfo : machineryInfoSet) {
                    if (machineryInfo instanceof CashRegisterInfo) {
                        CashRegisterInfo c = (CashRegisterInfo) machineryInfo;
                        departNumberSet.add(getDepartNumber(c.section, c.overDepartNumber != null ? c.overDepartNumber : c.numberGroup, useSectionAsDepartNumber));
                    }
                }
                noPriceEntry = departNumberSet.isEmpty();
                for (Integer departNumber : departNumberSet) {

                    //parent: good
                    Element priceEntry = new Element("price-entry");
                    setAttribute(priceEntry, "price", 1);
                    setAttribute(priceEntry, "deleted", "true");
                    addStringElement(priceEntry, "begin-date", formatDate(stopListInfo.dateFrom, "yyyy-MM-dd"));
                    addStringElement(priceEntry, "number", "1");
                    good.addContent(priceEntry);

                    //parent: priceEntry
                    Element department = new Element("department");
                    setAttribute(department, "number", departNumber);
                    priceEntry.addContent(department);
                }
            }
            if (noPriceEntry) {
                //parent: good
                Element priceEntry = new Element("price-entry");
                setAttribute(priceEntry, "price", 1);
                setAttribute(priceEntry, "deleted", "true");
                addStringElement(priceEntry, "begin-date", formatDate(stopListInfo.dateFrom, "yyyy-MM-dd"));
                addStringElement(priceEntry, "number", "1");
                good.addContent(priceEntry);
            }

            Element measureType = new Element("measure-type");
            setAttribute(measureType, "id", item.idUOM);
            addStringElement(measureType, "name", item.shortNameUOM);
            good.addContent(measureType);

            addStringElement(good, "vat", item.vat == null || item.vat.intValue() == 0 ? "20" : String.valueOf(item.vat.intValue()));

            //parent: good
            Element group = new Element("group");
            setAttribute(group, "id", item.idItemGroup);
            addStringElement(group, "name", item.nameItemGroup);
            good.addContent(group);

        }
        return doc;
    }

    @Override
    public boolean sendDeleteBarcodeInfo(DeleteBarcodeInfo deleteBarcodeInfo) {

        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        boolean idItemInMarkingOfTheGood = kristalSettings == null || kristalSettings.isIdItemInMarkingOfTheGood() != null && kristalSettings.isIdItemInMarkingOfTheGood();
        boolean skipWeightPrefix = kristalSettings != null && kristalSettings.getSkipWeightPrefix() != null && kristalSettings.getSkipWeightPrefix();

        if (deleteBarcodeInfo.directoryGroupMachinery != null && !deleteBarcodeInfo.barcodeList.isEmpty()) {
            String exchangeDirectory = deleteBarcodeInfo.directoryGroupMachinery + "/products/source/";
            File exchangeDirectoryFile = new File(exchangeDirectory);
            if (exchangeDirectoryFile.exists() || exchangeDirectoryFile.mkdirs()) {
                Map<String, String> deleteBarcodeSet = deleteBarcodeDirectoryMap.get(exchangeDirectory);
                if (deleteBarcodeSet == null)
                    deleteBarcodeSet = new HashMap<>();
                for (CashRegisterItemInfo item : deleteBarcodeInfo.barcodeList) {
                    if (!deleteBarcodeSet.containsKey(item.idBarcode)) {
                        String idBarcode = transformBarcode(item.idBarcode, null, false, skipWeightPrefix);
                        deleteBarcodeSet.put(item.idBarcode, idItemInMarkingOfTheGood ? item.idItem : idBarcode);
                    }
                }
                if (!deleteBarcodeSet.isEmpty())
                    deleteBarcodeDirectoryMap.put(deleteBarcodeInfo.directoryGroupMachinery, deleteBarcodeSet);
            }
        }
        return false;
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) throws IOException {
        if (!discountCardList.isEmpty()) {
            Document doc = generateDiscountCardXML(discountCardList, requestExchange);
            for (String directory : getDirectorySet(requestExchange)) {
                machineryExchangeLogger.info(String.format(getLogPrefix() + "Send DiscountCards to %s", directory));
                try {
                    String response = sendRequestCards(directory, docToXMLString(doc));
                    if (response != null) {
                        processStopListLogger.error(getLogPrefix() + response);
                        throw new RuntimeException(getLogPrefix() + response);
                    }
                } catch (JDOMException e) {
                    processStopListLogger.error(getLogPrefix(), e);
                    throw Throwables.propagate(e);
                }
            }
        }
    }

    private Document generateDiscountCardXML(List<DiscountCard> discountCardList, RequestExchange requestExchange) {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        Map<Double, String> discountCardPercentTypeMap = kristalSettings != null ? kristalSettings.getDiscountCardPercentTypeMap() : new HashMap<>();

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
            boolean active = requestExchange.startDate == null || (d.dateFromDiscountCard != null && d.dateFromDiscountCard.compareTo(requestExchange.startDate) >= 0);
            if(active) {
                //parent: rootElement
                Element internalCard = new Element("internal-card");
                Double percent = d.percentDiscountCard == null ? 0 : d.percentDiscountCard.doubleValue();
                String guid = discountCardPercentTypeMap.get(percent);
                if (d.numberDiscountCard != null) {
                    setAttribute(internalCard, "number", d.numberDiscountCard);
                    if(d.initialSumDiscountCard != null)
                        setAttribute(internalCard, "amount", d.initialSumDiscountCard);
                    if(d.dateToDiscountCard != null)
                        setAttribute(internalCard, "expiration-date", d.dateToDiscountCard);
                    setAttribute(internalCard, "status",
                            d.dateFromDiscountCard == null || currentDate.compareTo(d.dateFromDiscountCard) >= 0 ? "ACTIVE" : "BLOCKED");
                    setAttribute(internalCard, "deleted", "false");
                    setAttribute(internalCard, "card-type-guid", d.idDiscountCardType != null ? d.idDiscountCardType : (guid != null ? guid : "0"));

                    Element client = new Element("client");
                    setAttribute(client, "guid", d.numberDiscountCard);
                    setAttribute(client, "last-name", d.lastNameContact);
                    setAttribute(client, "first-name", d.firstNameContact);
                    setAttribute(client, "middle-name", d.middleNameContact);
                    setAttribute(client, "birth-date", formatDate(d.birthdayContact, "yyyy-MM-dd"));
                    if(d.sexContact != null)
                        setAttribute(client, "sex", d.sexContact == 0 ? "MALE" : "FEMALE");
                    setAttribute(client, "isCompleted", d.isCompleted);
                    internalCard.addContent(client);

                    rootElement.addContent(internalCard);
                }
            }
        }
        return doc;
    }

    @Override
    public Kristal10SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {
        List<SalesInfo> salesInfoList = new ArrayList<>();
        if(httpRequestHandler != null) {
            try {
                List<String> requestSalesInfoEntry = requestSalesInfoMap.get(directory);
                if (requestSalesInfoEntry != null && !requestSalesInfoEntry.isEmpty()) {
                    //сначала обрабатываем запросы перезагрузки продаж
                    String response = parseResponsePurchasesByParams(sendRequest(directory + "/FiscalInfoExport", requestSalesInfoEntry.remove(0)));
                    Document doc = xmlStringToDoc(response);
                    salesInfoList.addAll(parseSalesInfoXML(doc, directory, cashRegisterInfoList, new HashSet<>()));
                } else {
                    //обрабатываем продажи, полученные httpServer'ом
                    List<Request> purchases = httpRequestHandler.getPurchases();
                    for(Request purchase : purchases) {
                        Document doc = xmlStringToDoc(purchase.xml);
                        salesInfoList.addAll(parseSalesInfoXML(doc, directory, cashRegisterInfoList, new HashSet<>()));
                    }
                }
            } catch (Throwable e) {
                sendSalesLogger.error(getLogPrefix() + "readSalesInfo", e);
            }
        }
        return salesInfoList.isEmpty() ? null : new Kristal10SalesBatch(salesInfoList, null);
    }

    private List<SalesInfo> parseSalesInfoXML(Document doc, String directory, List<CashRegisterInfo> cashRegisterInfoList, Set<String> usedBarcodes) {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        String transformUPCBarcode = kristalSettings == null ? null : kristalSettings.getTransformUPCBarcode();
        boolean ignoreSalesWeightPrefix = kristalSettings == null || kristalSettings.getIgnoreSalesWeightPrefix() != null && kristalSettings.getIgnoreSalesWeightPrefix();
        boolean useShopIndices = kristalSettings != null && kristalSettings.getUseShopIndices() != null && kristalSettings.getUseShopIndices();
        boolean ignoreSalesDepartmentNumber = kristalSettings != null && kristalSettings.getIgnoreSalesDepartmentNumber() != null && kristalSettings.getIgnoreSalesDepartmentNumber();
        boolean useNumberGroupInShopIndices = kristalSettings != null && kristalSettings.useNumberGroupInShopIndices();
        String giftCardRegexp = kristalSettings != null ? kristalSettings.getGiftCardRegexp() : null;
        if(giftCardRegexp == null)
            giftCardRegexp = "(?!666)\\d{3}";
        boolean useSectionAsDepartNumber = kristalSettings != null && kristalSettings.useSectionAsDepartNumber();

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


        List<SalesInfo> salesInfoList = new ArrayList<>();
        Element rootNode = doc.getRootElement();
        List<Element> purchasesList = rootNode.getChildren("purchase");

        for (Element purchaseNode : purchasesList) {

            String operationType = readStringXMLAttribute(purchaseNode, "operationType");
            Boolean isSale = operationType == null || operationType.equals("true");
            Integer numberCashRegister = readIntegerXMLAttribute(purchaseNode, "cash");
            String numberZReport = readStringXMLAttribute(purchaseNode, "shift");
            Integer numberReceipt = readIntegerXMLAttribute(purchaseNode, "number");
            String idEmployee = readStringXMLAttribute(purchaseNode, "tabNumber");
            String nameEmployee = readStringXMLAttribute(purchaseNode, "userName");
            String shop = readStringXMLAttribute(purchaseNode, "shop");
            String firstNameEmployee = null;
            String lastNameEmployee = null;
            if (nameEmployee != null) {
                String[] splittedNameEmployee = nameEmployee.split(" ");
                lastNameEmployee = splittedNameEmployee[0];
                for (int i = 1; i < splittedNameEmployee.length; i++) {
                    firstNameEmployee = firstNameEmployee == null ? splittedNameEmployee[i] : (firstNameEmployee + " " + splittedNameEmployee[i]);
                }
            }
            BigDecimal discountSumReceipt = null; //пока считаем, что скидки по чеку нету //readBigDecimalXMLAttribute(purchaseNode, "discountAmount");
            //discountSumReceipt = (discountSumReceipt != null && !isSale) ? discountSumReceipt.negate() : discountSumReceipt;

            LocalDateTime dateTimeReceipt = ZonedDateTime.parse(readStringXMLAttribute(purchaseNode, "saletime"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")).toLocalDateTime();
            LocalDate dateReceipt = dateTimeReceipt.toLocalDate();
            LocalTime timeReceipt = dateTimeReceipt.toLocalTime();

            BigDecimal sumCard = BigDecimal.ZERO;
            BigDecimal sumCash = BigDecimal.ZERO;
            BigDecimal sumGiftCard = BigDecimal.ZERO;
            Map<String, GiftCard> sumGiftCardMap = new HashMap<>();
            Map<String, BigDecimal> customPaymentMap = new HashMap<>();
            List<Element> paymentsList = purchaseNode.getChildren("payments");
            for (Element paymentNode : paymentsList) {

                List<Element> paymentEntryList = paymentNode.getChildren("payment");
                for (Element paymentEntryNode : paymentEntryList) {
                    String paymentType = readStringXMLAttribute(paymentEntryNode, "typeClass");
                    if (paymentType != null) {
                        BigDecimal sum = readBigDecimalXMLAttribute(paymentEntryNode, "amount");
                        sum = (sum != null && !isSale) ? sum.negate() : sum;
                        switch (paymentType) {
                            case "CashPaymentEntity":
                                sumCash = HandlerUtils.safeAdd(sumCash, sum);
                                break;
                            case "CashChangePaymentEntity":
                                sumCash = HandlerUtils.safeSubtract(sumCash, sum);
                                break;
                            case "ExternalBankTerminalPaymentEntity":
                            case "BankCardPaymentEntity":
                                sumCard = HandlerUtils.safeAdd(sumCard, sum);
                                break;
                            case "GiftCardPaymentEntity": {
                                List<Element> pluginProperties = paymentEntryNode.getChildren("plugin-property");
                                boolean found = false;
                                String giftCardNumber = null;
                                BigDecimal giftCardPrice = null;
                                for(Element pluginProperty : pluginProperties) {
                                    String keyPluginProperty = pluginProperty.getAttributeValue("key");
                                    String valuePluginProperty = pluginProperty.getAttributeValue("value");
                                    if(notNullNorEmpty(keyPluginProperty) && notNullNorEmpty(valuePluginProperty)) {
                                        if (keyPluginProperty.equals("card.number")) {
                                            giftCardNumber = valuePluginProperty;
                                            found = true;
                                        } else if(keyPluginProperty.equals("card.amount")) {
                                            giftCardPrice = new BigDecimal(valuePluginProperty);
                                        }
                                    }
                                }
                                if(found) {
                                    sumGiftCardMap.put(giftCardNumber, new GiftCard(sum, giftCardPrice));
                                } else
                                    sumGiftCard = HandlerUtils.safeAdd(sumGiftCard, sum);
                                break;
                            }
                            case "BonusCardPaymentEntity": {
                                List<Element> pluginProperties = paymentEntryNode.getChildren("plugin-property");
                                String giftCardNumber = null;
                                for (Element pluginProperty : pluginProperties) {
                                    String keyPluginProperty = pluginProperty.getAttributeValue("key");
                                    String valuePluginProperty = pluginProperty.getAttributeValue("value");
                                    if (notNullNorEmpty(keyPluginProperty) && notNullNorEmpty(valuePluginProperty)) {
                                        if (keyPluginProperty.equals("card.number")) {
                                            giftCardNumber = valuePluginProperty;
                                        }
                                    }
                                }
                                if (giftCardNumber != null) {
                                    sumGiftCardMap.put(giftCardNumber, new GiftCard(sum));
                                } else sumGiftCard = HandlerUtils.safeAdd(sumGiftCard, sum);
                                break;
                            }
                            case "by.lwo.oplati.payment": {
                                customPaymentMap.put(oplatiPaymentType, HandlerUtils.safeAdd(customPaymentMap.get(oplatiPaymentType), sum));
                            }
                        }
                    }
                }
            }

            List<String> couponsList = new ArrayList<>();
            List<Element> cardsList = purchaseNode.getChildren("card");
            for (Element card : cardsList) {
                String type = card.getAttributeValue("type");
                if(type != null && (type.equals("COUPON_CARD") || type.equals("UNIQUE_COUPON")))
                    couponsList.add(card.getAttributeValue("number"));
            }

            String discountCard = null;
            List<Element> discountCardsList = purchaseNode.getChildren("discountCards");
            for (Object discountCardNode : discountCardsList) {
                List<Element> discountCardList = ((Element) discountCardNode).getChildren("discountCard");
                for (Object discountCardEntry : discountCardList) {
                    discountCard = ((Element) discountCardEntry).getValue();
                    if (discountCard != null && !couponsList.contains(discountCard)) {
                        discountCard = discountCard.trim();
                        if(discountCard.length() > 18)
                            discountCard = discountCard.substring(0, 18);
                        break;
                    }
                }
            }

            List<Element> positionsList = purchaseNode.getChildren("positions");
            List<SalesInfo> currentSalesInfoList = new ArrayList<>();
            BigDecimal currentPaymentSum = BigDecimal.ZERO;


            for (Object positionNode : positionsList) {

                List positionEntryList = ((Element) positionNode).getChildren("position");

                int count = 1;
                String departNumber = null;
                for (Object positionEntryNode : positionEntryList) {

                    String positionDepartNumber = readStringXMLAttribute(positionEntryNode, "departNumber");

                    if (departNumber == null)
                        departNumber = positionDepartNumber;

                    String key = directory + "_" + numberCashRegister + (ignoreSalesDepartmentNumber ? "" : ("_" + departNumber)) + (useShopIndices ? ("_" + shop) : "");

                    CashRegisterInfo cashRegisterByKey = getCashRegister(cashRegisterByKeyMap, key);
                    String weightCode = cashRegisterByKey != null ? cashRegisterByKey.weightCodeGroupCashRegister : null;
                    if (weightCode == null)
                        weightCode = "21";

                    String idItem = readStringXMLAttribute(positionEntryNode, "goodsCode");
                    String barcode = transformUPCBarcode(readStringXMLAttribute(positionEntryNode, "barCode"), transformUPCBarcode);

                    //обнаруживаем продажу сертификатов
                    boolean isGiftCard = false;
                    List<Element> pluginProperties = ((Element) positionEntryNode).getChildren("plugin-property");
                    for (Element pluginProperty : pluginProperties) {
                        String keyPluginProperty = pluginProperty.getAttributeValue("key");
                        String valuePluginProperty = pluginProperty.getAttributeValue("value");
                        if (notNullNorEmpty(keyPluginProperty) && notNullNorEmpty(valuePluginProperty)) {
                            if (keyPluginProperty.equals("gift.card.number")) {
                                barcode = valuePluginProperty;
                                isGiftCard = true;
                            }
                        }
                    }

                    if (!isGiftCard && barcode != null) {
                        Pattern pattern = Pattern.compile(giftCardRegexp);
                        Matcher matcher = pattern.matcher(barcode);
                        isGiftCard = matcher.matches();
                        if (isGiftCard) {
                            while (usedBarcodes.contains(dateTimeReceipt + "/" + count)) {
                                count++;
                            }
                            barcode = dateTimeReceipt + "/" + count;
                            usedBarcodes.add(barcode);
                        }
                    }

                    BigDecimal quantity = readBigDecimalXMLAttribute(positionEntryNode, "count");

                    //временное решение для весовых товаров
                    if(barcode != null) {
                        if (barcode.length() == 7 && barcode.startsWith("2") && ignoreSalesWeightPrefix) {
                            barcode = barcode.substring(2);
                        } else if (barcode.startsWith(weightCode) && barcode.length() == 7)
                            barcode = barcode.substring(2);


                        // временно для касс самообслуживания в виталюре
                        if (ignoreSalesWeightPrefix && barcode.length() == 13 && barcode.startsWith("22") && !barcode.substring(8, 13).equals("00000") &&
                                quantity != null && (quantity.intValue() != quantity.doubleValue() || parseWeight(barcode.substring(7, 12)) == quantity.doubleValue()))
                            barcode = barcode.substring(2, 7);
                    }

                    quantity = (quantity != null && !isSale) ? quantity.negate() : quantity;
                    BigDecimal price = readBigDecimalXMLAttribute(positionEntryNode, "cost");
                    BigDecimal sumReceiptDetail = readBigDecimalXMLAttribute(positionEntryNode, "amount");
                    sumReceiptDetail = (sumReceiptDetail != null && !isSale) ? sumReceiptDetail.negate() : sumReceiptDetail;
                    currentPaymentSum = HandlerUtils.safeAdd(currentPaymentSum, sumReceiptDetail);
                    BigDecimal discountSumReceiptDetail = readBigDecimalXMLAttribute(positionEntryNode, "discountValue");
                    BigDecimal discountPercentReceiptDetail = discountSumReceiptDetail != null && discountSumReceiptDetail.compareTo(BigDecimal.ZERO) > 0 ?
                            safeDivide(safeMultiply(discountSumReceiptDetail, 100), safeAdd(discountSumReceiptDetail, sumReceiptDetail)) : null;
                    Integer numberReceiptDetail = readIntegerXMLAttribute(positionEntryNode, "order");

                    LocalDate startDate = cashRegisterByKey != null ? cashRegisterByKey.startDate : null;
                    if (startDate == null || dateReceipt.compareTo(startDate) >= 0) {
                        Integer nppGroupMachinery = cashRegisterByKey != null ? cashRegisterByKey.numberGroup : null;
                        if (nppGroupMachinery == null) {
                            sendSalesLogger.error("not found nppGroupMachinery : " + key);
                        }

                        String idSaleReceiptReceiptReturnDetail = null;
                        Element originalPurchase = purchaseNode.getChild("original-purchase");
                        if(originalPurchase != null) {
                            Integer numberCashRegisterOriginal = readIntegerXMLAttribute(originalPurchase, "cash");
                            String numberZReportOriginal = readStringXMLAttribute(originalPurchase, "shift");
                            Integer numberReceiptOriginal = readIntegerXMLAttribute(originalPurchase, "number");
                            LocalDate dateReceiptOriginal = ZonedDateTime.parse(readStringXMLAttribute(originalPurchase, "saletime"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")).toLocalDate();
                            idSaleReceiptReceiptReturnDetail = nppGroupMachinery + "_" + numberCashRegisterOriginal + "_" + numberZReportOriginal + "_"
                                    + dateReceiptOriginal.format(DateTimeFormatter.ofPattern("ddMMyyyy")) + "_" + numberReceiptOriginal;
                        }

                        if(sumGiftCard.compareTo(BigDecimal.ZERO) != 0)
                            sumGiftCardMap.put(null, new GiftCard(sumGiftCard));
                        currentSalesInfoList.add(getSalesInfo(isGiftCard, false, nppGroupMachinery, numberCashRegister, numberZReport, dateReceipt, timeReceipt,
                                numberReceipt, dateReceipt, timeReceipt, idEmployee, firstNameEmployee, lastNameEmployee, sumCard, sumCash, sumGiftCardMap,
                                customPaymentMap, barcode, idItem, null, idSaleReceiptReceiptReturnDetail, quantity, price, sumReceiptDetail, discountPercentReceiptDetail,
                                discountSumReceiptDetail, discountSumReceipt, discountCard, numberReceiptDetail, null,
                                useSectionAsDepartNumber ? positionDepartNumber : null, false, cashRegisterByKey));
                    }
                    count++;
                }

            }

            //чит для случая, когда не указана сумма платежа. Недостающую сумму пишем в наличные.
            BigDecimal sum = HandlerUtils.safeAdd(sumCard, sumCash);
            for(GiftCard giftCard : sumGiftCardMap.values()) {
                sum = HandlerUtils.safeAdd(sum, giftCard.sum);
            }
            for(BigDecimal customPayment : customPaymentMap.values()) {
                sum = HandlerUtils.safeAdd(sum, customPayment);
            }
            if (sum == null || sum.compareTo(currentPaymentSum) < 0)
                for (SalesInfo salesInfo : currentSalesInfoList) {
                    salesInfo.sumCash = HandlerUtils.safeSubtract(HandlerUtils.safeSubtract(currentPaymentSum, sumCard), sumGiftCard);
                }

            salesInfoList.addAll(currentSalesInfoList);
        }
        return salesInfoList;
    }

    @Override
    public Map<String, List<Object>> readExtraCheckZReport(List<CashRegisterInfo> cashRegisterInfoList) {
        Map<String, List<Object>> zReportSumMap = new HashMap<>();

        Map<Integer, CashRegisterInfo> numberCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c) && c.number != null) {
                numberCashRegisterMap.put(c.number, c);
            }
        }

        try {
            //обрабатываем z-отчёты, полученные httpServer'ом
            List<Request> zReports = httpRequestHandler.popZReports();
            for (Request zReport : zReports) {

                Document doc = xmlStringToDoc(zReport.xml);
                Element rootNode = doc.getRootElement();
                List zReportsList = rootNode.getChildren("zreport");

                for (Object zReportNode : zReportsList) {

                    Integer numberCashRegister = readIntegerXMLValue(zReportNode, "cashNumber");
                    CashRegisterInfo cashRegister = numberCashRegisterMap.get(numberCashRegister);
                    Integer numberGroupCashRegister = cashRegister == null ? null : cashRegister.numberGroup;

                    LocalDate dateZReport = ZonedDateTime.parse(readStringXMLValue(zReportNode, "dateOperDay"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")).toLocalDate();

                    String numberZReport = readStringXMLValue(zReportNode, "shiftNumber");
                    String idZReport = numberGroupCashRegister + "_" + numberCashRegister + "_" + numberZReport + "_" + dateZReport.format(DateTimeFormatter.ofPattern("ddMMyyyy"));

                    BigDecimal sumSale = readBigDecimalXMLValue(zReportNode, "amountByPurchaseFiscal");
                    BigDecimal sumReturn = readBigDecimalXMLValue(zReportNode, "amountByReturnFiscal");
                    BigDecimal kristalSum = HandlerUtils.safeSubtract(sumSale, sumReturn);
                    zReportSumMap.put(idZReport, Arrays.asList(kristalSum, numberCashRegister, numberZReport, idZReport));

                }
                sendZReportsResponse(zReport.request, null);
            }

        } catch (Throwable e) {
            sendSalesLogger.error(getLogPrefix(), e);
        }

        return zReportSumMap.isEmpty() ? null : zReportSumMap;
    }

    private void sendPurchasesResponse(HttpExchange httpExchange, String error) throws IOException {
        sendResponse(httpExchange, "processPurchasesWithTI", ns1PurchasesNamespace, error);
    }

    private void sendIntroductionsResponse(HttpExchange httpExchange, String error) throws IOException {
        sendResponse(httpExchange, "processIntroductionsWithTI", ns1IntroductionsNamespace, error);
    }

    private void sendWithdrawalsResponse(HttpExchange httpExchange, String error) throws IOException {
        sendResponse(httpExchange, "processWithdrawalsWithTI", ns1WithdrawalsNamespace, error);
    }

    private void sendZReportsResponse(HttpExchange httpExchange, String error) throws IOException {
        sendResponse(httpExchange, "processZReportsWithTI", ns1ZReportsNamespace, error);
    }

    private void sendResponse(HttpExchange httpExchange, String tag, Namespace ns1Namespace, String error) throws IOException {

        Element envelopeElement = new Element("Envelope", soapNamespace);
        //envelopeElement.setNamespace(soapNamespace);

        Document doc = new Document(envelopeElement);

        Element bodyElement = new Element("Body", soapenvNamespace);
        envelopeElement.addContent(bodyElement);

        Element responseElement = new Element(tag + "Response", ns1Namespace);
        //responseElement.setNamespace(ns1Namespace);
        bodyElement.addContent(responseElement);

        addStringElement(responseElement, "return", String.valueOf(error == null));
        if(error != null) {
            addStringElement(responseElement, "errorCode", "500");
            addStringElement(responseElement, "errorMessage", error);
        }

        String response = docToXMLString(doc);
        httpExchange.sendResponseHeaders(error != null ? 500 : 200, response.getBytes().length);
        OutputStream os = httpExchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private String sendRequestGoods(String url, String xml) throws IOException, JDOMException {
        return sendRequest(url + "/SET/WSGoodsCatalogImport", xml, plugProductsNamespace, "getGoodsCatalogWithTi", "goodsCatalogXML", "getGoodsCatalogWithTiResponse", ns2ProductsNamespace);
    }

    private String sendRequestCards(String url, String xml) throws IOException, JDOMException {
        return sendRequest(url + "/SET/WSCardsCatalogImport", xml, webNamespace, "getCardsCatalogWithTi", "cardsCatalogXML", "getCardsCatalogWithTiResponse", webNamespace);
    }

    private String sendRequest(String url, String xml, Namespace namespace, String getCatalogTagWithTi, String catalogXMLTag, String getCatalogWithTiResponseTag, Namespace responseNamespace) throws IOException, JDOMException { //http://192.168.42.211:8090/SET-ERPIntegration
        Element envelopeElement = new Element("Envelope", soapenvNamespace);
        envelopeElement.addNamespaceDeclaration(namespace);

        Element headerElement = new Element("Header", soapenvNamespace);
        envelopeElement.addContent(headerElement);

        Element bodyElement = new Element("Body", soapenvNamespace);
        envelopeElement.addContent(bodyElement);

        Element getCatalogWithTiElement = new Element(getCatalogTagWithTi, namespace);
        Element catalogXML = new Element(catalogXMLTag);
        catalogXML.setContent(new CDATA(new String(Base64.encodeBase64(xml.getBytes(encoding)), encoding)));
        getCatalogWithTiElement.addContent(catalogXML);
        addStringElement(getCatalogWithTiElement, "TI", String.valueOf(Instant.now().toEpochMilli()));
        bodyElement.addContent(getCatalogWithTiElement);

        Document doc = new Document(envelopeElement);

        Document responseDoc = sendRequest(url, docToXMLString(doc));
        return parseResponse(responseDoc, getCatalogWithTiResponseTag, responseNamespace);
    }

    private Document sendRequest(String url, String xml) throws IOException, JDOMException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("Content-Type", "text/xml");
        httpPost.setEntity(new StringEntity(xml, encoding));
        HttpResponse response = HttpClientBuilder.create().build().execute(httpPost);
        return inputStreamToDoc(response.getEntity().getContent());
    }

    private String parseResponse(Document responseDoc, String getCatalogWithTiResponseTag, Namespace responseNamespace) {
        String error = "Unknown error";

        Element rootNode = responseDoc.getRootElement();
        Element bodyElement = rootNode.getChild("Body", soapenvNamespace);
        if(bodyElement != null) {
            Element getCatalogWithTiResponseElement = bodyElement.getChild(getCatalogWithTiResponseTag, responseNamespace);
            if(getCatalogWithTiResponseElement != null) {
                Element returnElement = getCatalogWithTiResponseElement.getChild("return");
                if(returnElement != null) {
                    String result = returnElement.getText();
                    if (result.equals("true")) {
                        error = null;
                    } else {
                        error = "Web-service error";
                    }
                }
            } else {
                Element faultElement = bodyElement.getChild("Fault", soapenvNamespace);
                if(faultElement != null) {
                    Element faultString = faultElement.getChild("faultstring");
                    if(faultString != null) {
                        error = faultString.getText();
                    }
                }
            }
        }
        return error;
    }

    private String parseResponsePurchasesByParams(Document responseDoc) throws IOException {
        String result = null;
        String error = null;

        Element rootNode = responseDoc.getRootElement();
        Element bodyElement = rootNode.getChild("Body", soapenvNamespace);
        if(bodyElement != null) {
            Element faultElement = bodyElement.getChild("Fault", soapenvNamespace);
            if(faultElement != null) {
                Element faultString = faultElement.getChild("faultstring");
                if(faultString != null) {
                    error = faultString.getText();
                }
            } else {
                List<Element> children = bodyElement.getChildren();
                for(Element child : children) {
                    Element returnElement = child.getChild("return");
                    if (returnElement != null) {
                        result = new String(Base64.decodeBase64(returnElement.getText()), encoding);
                    }
                }
            }

        }
        if (result == null) {
            throw new RuntimeException(error != null ? error : "unknown error");
        } else {
            return result;
        }
    }

    private String parseHttpRequestHandlerResponse(HttpExchange httpExchange, String tag) throws IOException, JDOMException {
        String result = null;

        Document responseDoc = inputStreamToDoc(httpExchange.getRequestBody());

        Element rootNode = responseDoc.getRootElement();
        Element bodyElement = rootNode.getChild("Body", soapenvNamespace);
        if(bodyElement != null) {
            List<Element> children = bodyElement.getChildren();
            for (Element child : children) {
                Element purchasesElement = child.getChild(tag);
                if (purchasesElement != null) {
                    result = new String(Base64.decodeBase64(purchasesElement.getText()), encoding);
                }
            }

        }
        return result;
    }

    private Document inputStreamToDoc(InputStream is) throws IOException, JDOMException {
        return xmlStringToDoc(new String(IOUtils.readBytesFromStream(is), encoding));
    }

    private Document xmlStringToDoc(String xml) throws JDOMException, IOException {
        processTransactionLogger.info("received xml: " + xml); //temp log
        return new SAXBuilder().build(org.apache.commons.io.IOUtils.toInputStream(xml, encoding));
    }

    private String docToXMLString(Document doc) {
        String result = new XMLOutputter(Format.getPrettyFormat().setEncoding(encoding)).outputString(doc);
        processTransactionLogger.info("sent xml: " + result); //temp log
        return result;
    }

    private class HttpRequestHandler implements HttpHandler {

        //пока рассматриваем только случай с 1 SetRetail сервером на 1 equ
        private List<Request> httpServerPurchasesList = new ArrayList<>();
        private int processPurchases;
        private List<CashDocumentRequest> httpServerCashDocumentsList = new ArrayList<>();
        private int processCashDocuments;
        private List<Request> httpServerZReportsList = new ArrayList<>();

        public List<Request> getPurchases() {
            processPurchases = httpServerPurchasesList.size();
            return httpServerPurchasesList;
        }

        public List<Request> popPurchases() {
            List<Request> purchases = httpServerPurchasesList.subList(0, processPurchases);
            httpServerPurchasesList = httpServerPurchasesList.subList(processPurchases, httpServerPurchasesList.size());
            processPurchases = 0;
            return purchases;
        }

        public List<CashDocumentRequest> getCashDocuments() {
            processCashDocuments = httpServerCashDocumentsList.size();
            return httpServerCashDocumentsList;
        }

        public List<CashDocumentRequest> popCashDocuments() {
            List<CashDocumentRequest> cashDocuments = httpServerCashDocumentsList.subList(0, processCashDocuments);
            httpServerCashDocumentsList = httpServerCashDocumentsList.subList(processCashDocuments, httpServerCashDocumentsList.size());
            processCashDocuments = 0;
            return cashDocuments;
        }

        //у z-отчётов нет finish, подтверждаем сразу
        public List<Request> popZReports() {
            List<Request> zReports = httpServerZReportsList.subList(0, httpServerZReportsList.size());
            httpServerZReportsList = new ArrayList<>();
            return zReports;
        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String uri = String.valueOf(httpExchange.getRequestURI());
            sendSalesLogger.info(getLogPrefix() + "HttpServer received " + uri);
            if(uri.endsWith("purchases")) {
                try {
                    httpServerPurchasesList.add(new Request(httpExchange, parseHttpRequestHandlerResponse(httpExchange, "purchases")));
                } catch (Exception e) {
                    sendPurchasesResponse(httpExchange, e.getMessage());
                }
            } else if(uri.endsWith("introductions")) {
                try {
                    httpServerCashDocumentsList.add(new CashDocumentRequest(httpExchange, parseHttpRequestHandlerResponse(httpExchange, "introductions"), true));
                } catch (Exception e) {
                    sendIntroductionsResponse(httpExchange, e.getMessage());
                }
            } else if(uri.endsWith("withdrawals")) {
                try {
                    httpServerCashDocumentsList.add(new CashDocumentRequest(httpExchange, parseHttpRequestHandlerResponse(httpExchange, "withdrawals"), false));
                } catch (Exception e) {
                    sendWithdrawalsResponse(httpExchange, e.getMessage());
                }
            } else if(uri.endsWith("zreports")) {
                try {
                    httpServerZReportsList.add(new Request(httpExchange, parseHttpRequestHandlerResponse(httpExchange, "zreports")));
                } catch (Exception e) {
                    sendZReportsResponse(httpExchange, e.getMessage());
                }
            } else {
                sendSalesLogger.error(getLogPrefix() + "unknown request: " + uri);
            }
        }
    }

    private class Request {
        public HttpExchange request;
        public String xml;

        public Request(HttpExchange request, String xml) {
            this.request = request;
            this.xml = xml;
        }
    }

    private class CashDocumentRequest extends Request {
        public boolean cashIn;

        public CashDocumentRequest(HttpExchange request, String xml, boolean cashIn) {
            super(request, xml);
            this.cashIn = cashIn;
        }
    }
}