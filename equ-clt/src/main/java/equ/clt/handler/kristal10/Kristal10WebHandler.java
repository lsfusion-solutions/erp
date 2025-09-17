package equ.clt.handler.kristal10;

import com.google.common.base.Throwables;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import equ.api.*;
import equ.api.cashregister.*;
import equ.api.stoplist.StopListInfo;
import equ.clt.EquipmentServer;
import equ.clt.handler.HandlerUtils;
import lsfusion.base.DaemonThreadFactory;
import lsfusion.base.Pair;
import lsfusion.base.file.IOUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
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
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static equ.clt.ProcessMonitorEquipmentServer.notInterruptedTransaction;
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
    private static final Namespace feedbackNamespace = Namespace.getNamespace("feed", "http://feedback.ERPIntegration.crystals.ru/");

    private static final String goodsUrl = "SET/WSGoodsCatalogImport";
    private static final String cardsUrl = "SET/WSCardsCatalogImport";
    private static final String feedbackUrl = "SET/FeedbackWS";

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

    @Override
    public String getLogPrefix() {
        return "Kristal10Web: ";
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        boolean brandIsManufacturer = kristalSettings.getBrandIsManufacturer() != null && kristalSettings.getBrandIsManufacturer();
        boolean seasonIsCountry = kristalSettings.getSeasonIsCountry() != null && kristalSettings.getSeasonIsCountry();
        boolean idItemInMarkingOfTheGood = kristalSettings.isIdItemInMarkingOfTheGood() != null && kristalSettings.isIdItemInMarkingOfTheGood();
        boolean skipWeightPrefix = kristalSettings.getSkipWeightPrefix() != null && kristalSettings.getSkipWeightPrefix();
        boolean skipScalesInfo = kristalSettings.getSkipScalesInfo() != null && kristalSettings.getSkipScalesInfo();
        boolean useShopIndices = kristalSettings.getUseShopIndices() != null && kristalSettings.getUseShopIndices();
        boolean skipUseShopIndicesMinPrice = kristalSettings.getSkipUseShopIndicesMinPrice() != null && kristalSettings.getSkipUseShopIndicesMinPrice();
        String weightShopIndices = kristalSettings.getWeightShopIndices();
        boolean useIdItemInRestriction = kristalSettings.getUseIdItemInRestriction() != null && kristalSettings.getUseIdItemInRestriction();
        List<String> tobaccoGroups = getTobaccoGroups(kristalSettings.getTobaccoGroup());
        List<String> notGTINPrefixes = kristalSettings.getNotGTINPrefixesList();
        boolean useNumberGroupInShopIndices = kristalSettings.useNumberGroupInShopIndices();
        boolean useSectionAsDepartNumber = kristalSettings.useSectionAsDepartNumber();
        boolean exportAmountForBarcode = kristalSettings.isExportAmountForBarcode();
        boolean minusOneForEmptyVAT = kristalSettings.isMinusOneForEmptyVAT();

        boolean extendedLogs = kristalSettings.isExtendedLogs();

        Map<Long, SendTransactionBatch> result = new HashMap<>();
        Map<Long, DeleteBarcode> usedDeleteBarcodeTransactionMap = new HashMap<>();
        List<Kristal10Transaction> usedTransactionList = new ArrayList<>();

        for(TransactionCashRegisterInfo transaction : transactionList) {

            try {

                processTransactionLogger.info(getLogPrefix() + "Send Transaction # " + transaction.id);

                List<String> directoriesList = new ArrayList<>();
                for (CashRegisterInfo cashRegisterInfo : transaction.machineryInfoList) {
                    if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory)))
                        directoriesList.add(cashRegisterInfo.directory);
                }

                for (String directory : directoriesList) {

                    if(notInterruptedTransaction(transaction.id)) {

                        DeleteBarcode usedDeleteBarcodes = new DeleteBarcode(transaction.nppGroupMachinery, directory);

                        List<String> xmlList = new ArrayList<>();
                        xmlList.addAll(generateCatalogGoodsItemsXML(transaction, brandIsManufacturer, seasonIsCountry, idItemInMarkingOfTheGood, skipWeightPrefix,
                                skipScalesInfo, useShopIndices, weightShopIndices, tobaccoGroups, useNumberGroupInShopIndices, deleteBarcodeDirectoryMap.get(directory),
                                usedDeleteBarcodes, notGTINPrefixes, exportAmountForBarcode, minusOneForEmptyVAT));
                        xmlList.addAll(generateCatalogGoodsPricesXML(transaction, idItemInMarkingOfTheGood, skipWeightPrefix, useSectionAsDepartNumber, useShopIndices,
                                weightShopIndices, useNumberGroupInShopIndices));
                        xmlList.addAll(generateCatalogGoodsRestrictionsXML(transaction, idItemInMarkingOfTheGood, skipWeightPrefix, useShopIndices, skipUseShopIndicesMinPrice,
                                weightShopIndices, useIdItemInRestriction, useNumberGroupInShopIndices));

                        usedDeleteBarcodeTransactionMap.put(transaction.id, usedDeleteBarcodes);

                        String response = null;
                        List<String> tiList = new ArrayList<>();
                        for(String xml : xmlList) {
                            if(extendedLogs) {
                                processTransactionLogger.info(getLogPrefix() + " sending xml (Transaction " + transaction.id + ")");
                                logRequestFile("transaction", xml);
                            }
                            String ti = String.valueOf(Instant.now().toEpochMilli());
                            response = sendRequestGoods(directory, xml, ti);
                            if (response != null) {
                                break;
                            } else {
                                tiList.add(ti);
                                processTransactionLogger.info(getLogPrefix() + "ti " + ti + " sent");
                            }
                        }

                        if (response != null) {
                            processTransactionLogger.error(getLogPrefix() + response);
                            result.put(transaction.id, new SendTransactionBatch(new RuntimeException(response)));
                        } else {
                            usedTransactionList.add(new Kristal10Transaction(transaction.id, directory, tiList));
                        }
                    }
                }
            } catch (Exception e) {
                processTransactionLogger.error(getLogPrefix(), e);
                result.put(transaction.id, new SendTransactionBatch(e));
            }
        }

        for (Kristal10Transaction usedTransaction : usedTransactionList) {
            try {
                String response = null;
                for (String ti : usedTransaction.tiList) {
                    processTransactionLogger.info(String.format(getLogPrefix() + "Process TI %s (Transaction %s)", ti, usedTransaction.id));
                    response = getStatusMessage(usedTransaction.directory, ti);
                    if (response != null) {
                        break;
                    }
                }

                if (response != null) {
                    processTransactionLogger.error(getLogPrefix() + response);
                    result.put(usedTransaction.id, new SendTransactionBatch(new RuntimeException(response)));
                } else {
                    DeleteBarcode deleteBarcodes = usedDeleteBarcodeTransactionMap.get(usedTransaction.id);
                    if (deleteBarcodes != null) {
                        for (String b : deleteBarcodes.barcodes) {
                            Map<String, String> deleteBarcodesEntry = deleteBarcodeDirectoryMap.get(deleteBarcodes.directory);
                            deleteBarcodesEntry.remove(b);
                            deleteBarcodeDirectoryMap.put(deleteBarcodes.directory, deleteBarcodesEntry);
                        }
                    }
                    result.put(usedTransaction.id, new SendTransactionBatch(null, null, deleteBarcodes == null ? null : deleteBarcodes.nppGroupMachinery, deleteBarcodes == null ? null : deleteBarcodes.barcodes, null));
                }
            } catch (Exception e) {
                processTransactionLogger.error(getLogPrefix(), e);
                result.put(usedTransaction.id, new SendTransactionBatch(e));
            }
        }
        return result;
    }
    // ----------------- http server end ----------------- //

    private List<String> generateCatalogGoodsItemsXML(TransactionCashRegisterInfo transaction, boolean brandIsManufacturer, boolean seasonIsCountry,
                                                  boolean idItemInMarkingOfTheGood, boolean skipWeightPrefix, boolean skipScalesInfo, boolean useShopIndices,
                                                  String weightShopIndices, List<String> tobaccoGroups, boolean useNumberGroupInShopIndices,
                                                  Map<String, String> deleteBarcodeMap, DeleteBarcode usedDeleteBarcodes, List<String> notGTINPrefixes,
                                                  boolean exportAmountForBarcode, boolean minusOneForEmptyVAT) {
        processTransactionLogger.info(getLogPrefix() + "creating catalog-goods file with items and barcodes (Transaction " + transaction.id + ") - " + transaction.itemsList.size() + " items");

        List<String> xmlList = new ArrayList<>();
        int count = 0;

        Element rootElement = new Element("goods-catalog");
        Document doc = new Document(rootElement);

        for (CashRegisterItem item : transaction.itemsList) {
            JSONObject infoJSON = getExtInfo(item.info);
            JSONObject extraInfoJSON = getExtraInfo(item.extraInfo);
            if (infoJSON != null && infoJSON.optBoolean("skipGood")) {
                continue;
            }
            String shopIndices = getShopIndices(transaction, item, useNumberGroupInShopIndices, useShopIndices, weightShopIndices);
            String barcodeItem = transformBarcode(transaction, item, skipWeightPrefix);
            String idItem = idItemInMarkingOfTheGood ? item.idItem : barcodeItem;

            Element good = createGoodElement(transaction, item, idItem, barcodeItem, tobaccoGroups, skipScalesInfo, shopIndices, useShopIndices,
                    brandIsManufacturer, seasonIsCountry, minusOneForEmptyVAT, exportAmountForBarcode, deleteBarcodeMap, usedDeleteBarcodes, notGTINPrefixes,
                    infoJSON, extraInfoJSON);
            rootElement.addContent(good);

            if (++count >= 1000) {
                processTransactionLogger.info(String.format(getLogPrefix() + "created catalog-goods file with items and barcodes (Transaction %s, count %s)", transaction.id, count));
                xmlList.add(docToXMLString(doc));
                rootElement = new Element("goods-catalog");
                doc = new Document(rootElement);
                count = 0;
            }
        }
        if (count > 0) {
            processTransactionLogger.info(String.format(getLogPrefix() + "created catalog-goods file with items and barcodes (Transaction %s, count %s)", transaction.id, count));
            xmlList.add(docToXMLString(doc));
        } else {
            processTransactionLogger.info(String.format(getLogPrefix() + "not created catalog-goods file with items and barcodes (Transaction %s, count %s)", transaction.id, count));
        }
        return xmlList;
    }

    private List<String> generateCatalogGoodsPricesXML(TransactionCashRegisterInfo transaction, boolean idItemInMarkingOfTheGood, boolean skipWeightPrefix,
                                                   boolean useSectionAsDepartNumber, boolean useShopIndices, String weightShopIndices, boolean useNumberGroupInShopIndices) {
        processTransactionLogger.info(getLogPrefix() + "creating catalog-goods file with prices(Transaction " + transaction.id + ") - " + transaction.itemsList.size() + " items");

        List<String> xmlList = new ArrayList<>();
        int count = 0;

        Element rootElement = new Element("goods-catalog");
        Document doc = new Document(rootElement);

        for (CashRegisterItem item : transaction.itemsList) {
            JSONObject infoJSON = getExtInfo(item.info);
            String shopIndices = getShopIndices(transaction, item, useNumberGroupInShopIndices, useShopIndices, weightShopIndices);
            String barcodeItem = transformBarcode(transaction, item, skipWeightPrefix);
            String idItem = idItemInMarkingOfTheGood ? item.idItem : barcodeItem;

            addPriceEntryElements(rootElement, transaction, item, idItem, infoJSON, useSectionAsDepartNumber, useShopIndices ? shopIndices : null);
            if (++count >= 1000) {
                processTransactionLogger.info(String.format(getLogPrefix() + "created catalog-goods file with prices (Transaction %s, count %s)", transaction.id, count));
                xmlList.add(docToXMLString(doc));
                rootElement = new Element("goods-catalog");
                doc = new Document(rootElement);
                count = 0;
            }
        }
        if (count > 0) {
            processTransactionLogger.info(String.format(getLogPrefix() + "created catalog-goods file with prices (Transaction %s, count %s)", transaction.id, count));
            xmlList.add(docToXMLString(doc));
        }
        return xmlList;
    }

    private List<String> generateCatalogGoodsRestrictionsXML(TransactionCashRegisterInfo transaction, boolean idItemInMarkingOfTheGood, boolean skipWeightPrefix,
                                                         boolean useShopIndices, boolean skipUseShopIndicesMinPrice, String weightShopIndices, boolean useIdItemInRestriction,
                                                         boolean useNumberGroupInShopIndices) {
        processTransactionLogger.info(getLogPrefix() + "creating catalog-goods file with restrictions (Transaction " + transaction.id + ") - " + transaction.itemsList.size() + " items");

        List<String> xmlList = new ArrayList<>();
        int count = 0;

        Element rootElement = new Element("goods-catalog");
        Document doc = new Document(rootElement);

        for (CashRegisterItem item : transaction.itemsList) {
            String shopIndices = getShopIndices(transaction, item, useNumberGroupInShopIndices, useShopIndices, weightShopIndices);
            String barcodeItem = transformBarcode(transaction, item, skipWeightPrefix);
            String idItem = idItemInMarkingOfTheGood ? item.idItem : barcodeItem;

            fillRestrictionsElement(rootElement, item, idItem, barcodeItem, useIdItemInRestriction, shopIndices, useShopIndices, skipUseShopIndicesMinPrice);
            if (++count >= 1000) {
                processTransactionLogger.info(String.format(getLogPrefix() + "created catalog-goods file with restrictions (Transaction %s, count %s)", transaction.id, count));
                xmlList.add(docToXMLString(doc));
                rootElement = new Element("goods-catalog");
                doc = new Document(rootElement);
                count = 0;
            }
        }
        if (count > 0) {
            processTransactionLogger.info(String.format(getLogPrefix() + "created catalog-goods file with restrictions (Transaction %s, count %s)", transaction.id, count));
            xmlList.add(docToXMLString(doc));
        }
        return xmlList;
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList,
                                 Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        boolean useNumberGroupInShopIndices = kristalSettings.useNumberGroupInShopIndices();

        for (RequestExchange entry : requestExchangeList) {
            for (String directory : getDirectoryStockMap(entry, useNumberGroupInShopIndices).keySet()) {
                List<String> requestSalesInfoEntry = requestSalesInfoMap.getOrDefault(directory, new ArrayList<>());
                requestSalesInfoEntry.addAll(generateRequestPurchasesByParams(entry.dateFrom, entry.dateTo, getCashRegisterSet(entry, true)));
                requestSalesInfoMap.put(directory, requestSalesInfoEntry);
                succeededRequests.add(entry.requestExchange);
            }
        }
    }

    private List<String> generateRequestPurchasesByParams(LocalDate dateFrom, LocalDate dateTo, Set<CashRegisterInfo> cashRegisterSet) {
        List<String> result = new ArrayList<>();
        while(!dateFrom.isAfter(dateTo)) {
            for (CashRegisterInfo cashRegister : cashRegisterSet) {
                result.add(generateRequestPurchasesByParams(dateFrom, cashRegister.numberGroup, cashRegister.number));
            }
            dateFrom = dateFrom.plusDays(1);
        }
        return result;
    }

    private String generateRequestPurchasesByParams(LocalDate date, Integer shopNumber, Integer cashNumber) {
        Element envelopeElement = new Element("Envelope", soapenvNamespace);
        //envelopeElement.setNamespace(soapenvNamespace);
        envelopeElement.addNamespaceDeclaration(plugOperdayNamespace);

        Element headerElement = new Element("Header", soapenvNamespace);
        envelopeElement.addContent(headerElement);

        Element bodyElement = new Element("Body", soapenvNamespace);
        envelopeElement.addContent(bodyElement);

        Element getNewPurchasesByParamsElement = new Element("getPurchasesByParams", plugOperdayNamespace);
        addStringElement(getNewPurchasesByParamsElement, "dateOperDay", date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        addIntegerElement(getNewPurchasesByParamsElement, "shopNumber", shopNumber);
        addIntegerElement(getNewPurchasesByParamsElement, "cashNumber", cashNumber);
        bodyElement.addContent(getNewPurchasesByParamsElement);

        return docToXMLString(new Document(envelopeElement));
    }

    @Override
    public void finishReadingSalesInfo(Kristal10SalesBatch salesBatch) {
    }

    public List<CashDocument> parseCashDocumentXML(Document doc, boolean cashIn) {
        List<CashDocument> cashDocumentList = new ArrayList<>();

        Element rootNode = doc.getRootElement();

        List cashDocumentsList = rootNode.getChildren(cashIn ? "introduction" : "withdrawal");

        for (Object cashDocumentNode : cashDocumentsList) {

            String numberCashDocument = readStringXMLAttribute(cashDocumentNode, "number");
            String numberZReport = readStringXMLAttribute(cashDocumentNode, "shift");
            Integer numberCashRegister = readIntegerXMLAttribute(cashDocumentNode, "cash");
            Integer numberGroup = readIntegerXMLAttribute(cashDocumentNode, "shop");

            BigDecimal sumCashDocument = readBigDecimalXMLAttribute(cashDocumentNode, "amount");
            if(!cashIn)
                sumCashDocument = sumCashDocument == null ? null : sumCashDocument.negate();

            LocalDateTime dateTimeCashDocument = LocalDateTime.parse(readStringXMLAttribute(cashDocumentNode, "regtime"), DateTimeFormatter.ISO_DATE_TIME);

            String idCashDocument = numberGroup + "/" + numberCashRegister + "/" + numberCashDocument + "/" + dateTimeCashDocument.toLocalDate() + "/" +numberZReport + "/" + (cashIn ? "introduction" : "withdrawal");

            cashDocumentList.add(new CashDocument(idCashDocument, numberCashDocument, dateTimeCashDocument.toLocalDate(), dateTimeCashDocument.toLocalTime(),
                    numberGroup, numberCashRegister, numberZReport, sumCashDocument));
        }

        return cashDocumentList;
    }

    @Override
    public Pair<String, Set<String>> sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machinerySet) throws IOException {
        //из-за временного решения с весовыми товарами для этих весовых товаров стоп-листы работать не будут
        if (!stopListInfo.exclude) {
            Document doc = generateStopListXML(stopListInfo);
            for (String directory : HandlerUtils.getDirectorySet(machinerySet)) {
                processStopListLogger.info(getLogPrefix() + String.format("Send StopList # %s to url %s", stopListInfo.number, directory));
                if (!stopListInfo.stopListItemMap.isEmpty()) {
                    try {
                        String ti = String.valueOf(Instant.now().toEpochMilli());
                        String response = sendRequestGoods(directory, docToXMLString(doc), ti);
                        if (response == null) {
                            response = getStatusMessage(directory, ti);
                        }
                        if (response != null) {
                            processStopListLogger.error(getLogPrefix() + response);
                            throw new RuntimeException(getLogPrefix() + response);
                        }
                    } catch (JDOMException | InterruptedException e) {
                        processStopListLogger.error(getLogPrefix(), e);
                        throw Throwables.propagate(e);
                    }
                }
            }

        }
        return null;
    }

    private Document generateStopListXML(StopListInfo stopListInfo) {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        boolean useShopIndices = kristalSettings.getUseShopIndices() != null && kristalSettings.getUseShopIndices();
        boolean idItemInMarkingOfTheGood = kristalSettings.isIdItemInMarkingOfTheGood() != null && kristalSettings.isIdItemInMarkingOfTheGood();
        boolean skipWeightPrefix = kristalSettings.getSkipWeightPrefix() != null && kristalSettings.getSkipWeightPrefix();
        List<String> tobaccoGroups = getTobaccoGroups(kristalSettings.getTobaccoGroup());
        boolean useNumberGroupInShopIndices = kristalSettings.useNumberGroupInShopIndices();
        boolean useSectionAsDepartNumber = kristalSettings.useSectionAsDepartNumber();
        boolean minusOneForEmptyVAT = kristalSettings.isMinusOneForEmptyVAT();

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

        addStopListItems(rootElement, stopListInfo, useShopIndices, idItemInMarkingOfTheGood, skipWeightPrefix,
                tobaccoGroups, useNumberGroupInShopIndices, useSectionAsDepartNumber, minusOneForEmptyVAT);
        return doc;
    }

    @Override
    public boolean sendDeleteBarcodeInfo(DeleteBarcodeInfo deleteBarcodeInfo) {

        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        boolean idItemInMarkingOfTheGood = kristalSettings.isIdItemInMarkingOfTheGood() != null && kristalSettings.isIdItemInMarkingOfTheGood();
        boolean skipWeightPrefix = kristalSettings.getSkipWeightPrefix() != null && kristalSettings.getSkipWeightPrefix();

        if (deleteBarcodeInfo.directoryGroupMachinery != null && !deleteBarcodeInfo.barcodeList.isEmpty()) {
            String exchangeDirectory = deleteBarcodeInfo.directoryGroupMachinery + "/products/source/";
            File exchangeDirectoryFile = new File(exchangeDirectory);
            if (exchangeDirectoryFile.exists() || exchangeDirectoryFile.mkdirs()) {
                Map<String, String> deleteBarcodeSet = deleteBarcodeDirectoryMap.get(exchangeDirectory);
                if (deleteBarcodeSet == null)
                    deleteBarcodeSet = new HashMap<>();
                for (CashRegisterItem item : deleteBarcodeInfo.barcodeList) {
                    if (!deleteBarcodeSet.containsKey(item.idBarcode)) {
                        String idBarcode = transformBarcode(item.idBarcode, skipWeightPrefix);
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
                    String ti = String.valueOf(Instant.now().toEpochMilli());
                    String response = sendRequestCards(directory, docToXMLString(doc), ti);
                    if (response == null) {
                        response = getStatusMessage(directory, ti);
                    }
                    if (response != null) {
                        processStopListLogger.error(getLogPrefix() + response);
                        throw new RuntimeException(getLogPrefix() + response);
                    }
                } catch (JDOMException | InterruptedException e) {
                    processStopListLogger.error(getLogPrefix(), e);
                    throw Throwables.propagate(e);
                }
            }
        }
    }

    @Override
    public Kristal10SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {
        List<SalesInfo> salesInfoList = new ArrayList<>();
        String canceledReceipts = null;
        if(httpRequestHandler != null) {
            try {
                List<String> requestSalesInfoEntry = requestSalesInfoMap.get(directory);
                if (requestSalesInfoEntry != null && !requestSalesInfoEntry.isEmpty()) {
                    //обрабатываем запросы перезагрузки продаж
                    sendSalesLogger.info(getLogPrefix() + "sending request for directory : " + directory + ", Request: " + requestSalesInfoEntry.get(0) );
                    String response = parseResponsePurchasesByParams(sendRequest(getUrl(directory, "FiscalInfoExport"), requestSalesInfoEntry.remove(0)));
                    Document doc = xmlStringToDoc(response);
                    Pair<List<SalesInfo>, String> salesData = parseSalesInfoXML(doc, directory, cashRegisterInfoList, new HashSet<>());
                    salesInfoList.addAll(salesData.first);
                    canceledReceipts = salesData.second;
                    sendSalesLogger.info(getLogPrefix() + "found " + salesInfoList.size());
                }
            } catch (Throwable e) {
                sendSalesLogger.error(getLogPrefix() + "readSalesInfo", e);
            }
        }
        return salesInfoList.isEmpty() && canceledReceipts == null ? null : new Kristal10SalesBatch(salesInfoList, canceledReceipts, null);
    }

    private Pair<List<SalesInfo>, String> parseSalesInfoXML(Document doc, String directory, List<CashRegisterInfo> cashRegisterInfoList, Set<String> usedBarcodes) {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        String transformUPCBarcode = kristalSettings.getTransformUPCBarcode();
        boolean ignoreSalesWeightPrefix = kristalSettings.getIgnoreSalesWeightPrefix() != null && kristalSettings.getIgnoreSalesWeightPrefix();
        boolean useShopIndices = kristalSettings.getUseShopIndices() != null && kristalSettings.getUseShopIndices();
        boolean ignoreSalesDepartmentNumber = kristalSettings.getIgnoreSalesDepartmentNumber() != null && kristalSettings.getIgnoreSalesDepartmentNumber();
        boolean useNumberGroupInShopIndices = kristalSettings.useNumberGroupInShopIndices();
        String giftCardRegexp = kristalSettings.getGiftCardRegexp();
        if(giftCardRegexp == null)
            giftCardRegexp = "(?!666)\\d{3}";
        boolean useSectionAsDepartNumber = kristalSettings.useSectionAsDepartNumber();
        Set<String> customPayments = parseStringPayments(kristalSettings.getCustomPayments());
        boolean ignoreCashRegisterWithDisableSales = kristalSettings.isIgnoreCashRegisterWithDisableSales();
        boolean ignoreSalesWithoutNppGroupMachinery = kristalSettings.isIgnoreSalesWithoutNppGroupMachinery();
        boolean extendedLogs = kristalSettings.isExtendedLogs();
        String externalBankTerminalPaymentId = kristalSettings.getExternalBankTerminalPaymentId();

        Map<String, List<CashRegisterInfo>> cashRegisterByKeyMap = getCashRegisterByKeyMap(cashRegisterInfoList, useShopIndices, useNumberGroupInShopIndices, ignoreSalesDepartmentNumber);

        if(extendedLogs) {
            sendSalesLogger.info("cashRegisterByKeyMap: " + cashRegisterByKeyMap.keySet());
            logRequestFile("sales", docToXMLString(doc));
        }

        List<SalesInfo> salesInfoList = new ArrayList<>();
        Element rootNode = doc.getRootElement();
        List<Element> purchasesList = rootNode.getChildren("purchase");
        sendSalesLogger.info(getLogPrefix() + " purchase count: " + purchasesList.size());

        List<Element> canceledReceipts = new ArrayList<>();
        for (Element purchaseNode : purchasesList) {

            boolean skipReceipt = false;
            List<Element> pluginProperties = purchaseNode.getChildren("plugin-property");
            for (Element pluginProperty : pluginProperties) {
                String keyPluginProperty = pluginProperty.getAttributeValue("key");
                String valuePluginProperty = pluginProperty.getAttributeValue("value");

                if (keyPluginProperty.equals("SKNO_ANNUL") && valuePluginProperty.equals("1")) {
                    skipReceipt = true;
                }
            }

            String status = readStringXMLAttribute(purchaseNode, "status");
            if (status != null && status.equals("CANCELLED")) {
                canceledReceipts.add(purchaseNode);
                continue;
            }

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

            LocalDateTime dateTimeReceipt = LocalDateTime.parse(readStringXMLAttribute(purchaseNode, "saletime"), DateTimeFormatter.ISO_DATE_TIME);
            LocalDate dateReceipt = dateTimeReceipt.toLocalDate();
            LocalTime timeReceipt = dateTimeReceipt.toLocalTime();

            LocalDate dateZReport = LocalDate.parse(readStringXMLAttribute(purchaseNode, "operDay"), DateTimeFormatter.ISO_DATE);

            Map<String, Object> receiptExtraFields = getReceiptExtraFields(purchaseNode);

            BigDecimal sumCash = null;
            BigDecimal sumGiftCard = BigDecimal.ZERO;
            Map<String, GiftCard> sumGiftCardMap = new HashMap<>();
            List<Payment> payments = new ArrayList<>();
            List<Element> paymentsList = purchaseNode.getChildren("payments");
            for (Element paymentNode : paymentsList) {

                List<Element> paymentEntryList = paymentNode.getChildren("payment");
                for (Element paymentEntryNode : paymentEntryList) {
                    String paymentType = readStringXMLAttribute(paymentEntryNode, "typeClass");
                    if (paymentType != null) {
                        BigDecimal sum = readBigDecimalXMLAttribute(paymentEntryNode, "amount");
                        sum = (sum != null && !isSale) ? sum.negate() : sum;
                        if(customPayments.contains(paymentType)) {
                            payments.add(new Payment(paymentType, sum));
                        } else {
                            switch (paymentType) {
                                case "CashPaymentEntity":
                                    sumCash = HandlerUtils.safeAdd(sumCash, sum);
                                    break;
                                case "CashChangePaymentEntity":
                                    sumCash = HandlerUtils.safeSubtract(sumCash, sum);
                                    break;
                                case "ExternalBankTerminalPaymentEntity":
                                    if (externalBankTerminalPaymentId != null)  {
                                        payments.add(new Payment(externalBankTerminalPaymentId, sum));
                                    } else{
                                        payments.add(Payment.getCard(sum));
                                    }
                                    break;
                                case "BankCardPaymentEntity":
                                    payments.add(Payment.getCard(sum, "paymentCard", getPluginPropertyValue(paymentEntryNode, "card.number")));
                                    break;
                                case "GiftCardPaymentEntity": {
                                    pluginProperties = paymentEntryNode.getChildren("plugin-property");
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
                                    String giftCardNumber = getPluginPropertyValue(paymentEntryNode, "card.number");
                                    if (giftCardNumber != null) {
                                        GiftCard card = sumGiftCardMap.get(giftCardNumber);
                                        if (card != null)
                                            card.sum = HandlerUtils.safeAdd(card.sum, sum);
                                        else
                                            sumGiftCardMap.put(giftCardNumber, new GiftCard(sum));
                                    } else sumGiftCard = HandlerUtils.safeAdd(sumGiftCard, sum);
                                    break;
                                }
                                case "by.lwo.oplati.payment": {
                                    payments.add(new Payment(oplatiPaymentType, sum));
                                    break;
                                }
                                case "payme.service.payment": {
                                    payments.add(new Payment("payme", sum));
                                    break;
                                }
                                case "clickpass.service.payment": {
                                    payments.add(new Payment("clickpass", sum));
                                    break;
                                }
                                case "apelsinpay.payment.plugin": {
                                    payments.add(new Payment("apelsinpay", sum));
                                    break;
                                }
                                case "anorbankqr.payment.plugin": {
                                    payments.add(new Payment("anorbankqr", sum));
                                }
                            }
                        }
                    }
                }
            }

            if(sumCash != null) {
                payments.add(Payment.getCash(sumCash));
            }

            String discountCard = getDiscountCardNumber(purchaseNode);

            List<Element> positionsList = purchaseNode.getChildren("positions");
            List<SalesInfo> currentSalesInfoList = new ArrayList<>();
            BigDecimal currentPaymentSum = BigDecimal.ZERO;


            for (Element positionNode : positionsList) {

                List<Element> positionEntryList = positionNode.getChildren("position");

                int count = 1;
                String departNumber = null;
                for (Element positionEntryNode : positionEntryList) {

                    String positionDepartNumber = readStringXMLAttribute(positionEntryNode, "departNumber");

                    if (departNumber == null)
                        departNumber = positionDepartNumber;

                    String key = getCashRegisterKey(directory, numberCashRegister, ignoreSalesDepartmentNumber, departNumber, useShopIndices, shop);
                    CashRegisterInfo cashRegisterByKey = getCashRegister(cashRegisterByKeyMap, key);
                    Integer nppGroupMachinery = cashRegisterByKey != null ? cashRegisterByKey.numberGroup : null;

                    if (!ignoreSales(cashRegisterByKey, nppGroupMachinery, key, ignoreCashRegisterWithDisableSales, ignoreSalesWithoutNppGroupMachinery)) {
                        String weightCode = getWeightCode(cashRegisterByKey);
                        String idItem = readStringXMLAttribute(positionEntryNode, "goodsCode");
                        String barcode = transformUPCBarcode(readStringXMLAttribute(positionEntryNode, "barCode"), transformUPCBarcode);

                        boolean isGiftCard = false;
                        String idLot = null;
                        pluginProperties = positionEntryNode.getChildren("plugin-property");
                        for (Element pluginProperty : pluginProperties) {
                            String keyPluginProperty = pluginProperty.getAttributeValue("key");
                            String valuePluginProperty = pluginProperty.getAttributeValue("value");
                            if (notNullNorEmpty(keyPluginProperty) && notNullNorEmpty(valuePluginProperty)) {
                                if (keyPluginProperty.equals("gift.card.number")) {
                                    //обнаруживаем продажу сертификатов
                                    barcode = valuePluginProperty;
                                    isGiftCard = true;
                                }

                                if (keyPluginProperty.equals("excise-token")) {
                                    idLot = valuePluginProperty;
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
                            if (ignoreSalesWeightPrefix && barcode.length() == 13 && barcode.startsWith("22") && !barcode.startsWith("00000", 8) &&
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
                        if (startDate == null || !dateReceipt.isBefore(startDate)) {
                            String idSaleReceiptReceiptReturnDetail = null;
                            Element originalPurchase = purchaseNode.getChild("original-purchase");
                            if(originalPurchase != null) {
                                Integer numberCashRegisterOriginal = readIntegerXMLAttribute(originalPurchase, "cash");
                                String numberZReportOriginal = readStringXMLAttribute(originalPurchase, "shift");
                                Integer numberReceiptOriginal = readIntegerXMLAttribute(originalPurchase, "number");
                                LocalDate dateReceiptOriginal = LocalDate.parse(readStringXMLAttribute(originalPurchase, "saletime"), DateTimeFormatter.ISO_DATE_TIME);
                                idSaleReceiptReceiptReturnDetail = nppGroupMachinery + "_" + numberCashRegisterOriginal + "_" + numberZReportOriginal + "_"
                                        + dateReceiptOriginal.format(DateTimeFormatter.ofPattern("ddMMyyyy")) + "_" + numberReceiptOriginal;
                            }

                            if(sumGiftCard.compareTo(BigDecimal.ZERO) != 0)
                                sumGiftCardMap.put(null, new GiftCard(sumGiftCard));
                            SalesInfo salesInfo = getSalesInfo(isGiftCard, false, nppGroupMachinery, numberCashRegister, numberZReport, dateZReport, timeReceipt,
                                    numberReceipt, dateReceipt, timeReceipt, idEmployee, firstNameEmployee, lastNameEmployee, sumGiftCardMap,
                                    null, barcode, idItem, null, idSaleReceiptReceiptReturnDetail, quantity, price, sumReceiptDetail, discountPercentReceiptDetail,
                                    discountSumReceiptDetail, discountSumReceipt, discountCard, null, numberReceiptDetail, null,
                                    useSectionAsDepartNumber ? positionDepartNumber : null, skipReceipt, receiptExtraFields, null, cashRegisterByKey);

                            salesInfo.detailExtraFields = new HashMap<>();
                            if (idLot!=null) {
                                salesInfo.detailExtraFields.put("idLot", idLot);
                            }

                            currentSalesInfoList.add(salesInfo);
                        }
                    }
                    count++;
                }

            }

            addPayments(sumGiftCardMap, payments, currentPaymentSum, currentSalesInfoList);

            salesInfoList.addAll(currentSalesInfoList);
        }
        Element canceledReceiptsElement = new Element("canceledReceipts");
        for(Element canceledReceipt : canceledReceipts) {
            canceledReceipt.detach();
            canceledReceiptsElement.addContent(canceledReceipt);
        }
        return Pair.create(salesInfoList, canceledReceiptsElement.getChildren().isEmpty() ? null : elementToXMLString(canceledReceiptsElement));
    }

    private void sendPurchasesResponse(HttpExchange httpExchange, String error) throws IOException {
        sendResponse(httpExchange, "processPurchasesWithTI", ns1PurchasesNamespace, error);
    }

    private void sendCashDocumentResponse(HttpExchange httpExchange, String error, boolean introduction) throws IOException {
        if(introduction) {
            sendIntroductionsResponse(httpExchange, error);
        } else {
            sendWithdrawalsResponse(httpExchange, error);
        }
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
        sendSalesLogger.info(getLogPrefix() + "Send response: " + error);

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

    private String sendRequestGoods(String url, String xml, String ti) throws IOException, JDOMException, InterruptedException {
        return sendRequest(url, goodsUrl, xml, plugProductsNamespace, "getGoodsCatalogWithTi", "goodsCatalogXML", "getGoodsCatalogWithTiResponse", ns2ProductsNamespace, ti);
    }

    private String sendRequestCards(String url, String xml, String ti) throws IOException, JDOMException, InterruptedException {
        return sendRequest(url, cardsUrl, xml, webNamespace, "getCardsCatalogWithTi", "cardsCatalogXML", "getCardsCatalogWithTiResponse", webNamespace, ti);
    }

    private String sendRequest(String baseUrl, String url, String xml, Namespace namespace, String getCatalogTagWithTi, String catalogXMLTag, String getCatalogWithTiResponseTag, Namespace responseNamespace, String ti) throws IOException, JDOMException { //http://192.168.42.211:8090/SET-ERPIntegration

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
        addStringElement(getCatalogWithTiElement, "TI", ti);
        bodyElement.addContent(getCatalogWithTiElement);

        Document doc = new Document(envelopeElement);

        Document responseDoc = sendRequest(getUrl(baseUrl, url), docToXMLString(doc));
        return parseResponse(responseDoc, getCatalogWithTiResponseTag, responseNamespace);
    }

    private Document sendRequest(String url, String xml) throws IOException, JDOMException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("Content-Type", "text/xml");
        httpPost.setEntity(new StringEntity(xml, encoding));
        RequestConfig.Builder configBuilder = RequestConfig.custom().setConnectTimeout(5, TimeUnit.MINUTES).setConnectionRequestTimeout(5, TimeUnit.MINUTES).setResponseTimeout(5, TimeUnit.MINUTES);
        try (CloseableHttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(configBuilder.build()).build()) {
            return inputStreamToDoc(client.execute(httpPost).getEntity().getContent());
        }
    }

    private String getStatusMessage(String url, String ti) throws IOException, JDOMException, InterruptedException {
        String getStatusXML = getStatusXML(ti);
        int count = 0;
        while (true) {
            Document responseDoc = sendRequest(getUrl(url, feedbackUrl), getStatusXML);
            Pair<Integer, String> response = parseStatusResponse(responseDoc);
            if (response.first != 2) //2 = пакет в обработке
                return response.first == 3 ? null : response.second; // 3 = пакет обработан успешно
            if (++count > 600)
                return "server did not process the data";
            else
                Thread.sleep(1000);
        }
    }

    private String getUrl(String baseUrl, String url) {
        return baseUrl + (baseUrl.endsWith("/") ? "" : "/") + url;
    }

    private String getStatusXML(String ti) {
        Element envelopeElement = new Element("Envelope", soapenvNamespace);
        envelopeElement.addNamespaceDeclaration(feedbackNamespace);

        Element headerElement = new Element("Header", soapenvNamespace);
        envelopeElement.addContent(headerElement);

        Element bodyElement = new Element("Body", soapenvNamespace);
        envelopeElement.addContent(bodyElement);

        Element getPackageStatusElement = new Element("getPackageStatus", feedbackNamespace);
        Element xmlGetstatusElement = new Element("xmlGetstatus");
        Element importElement = new Element("import");
        importElement.setAttribute("ti", ti);
        xmlGetstatusElement.addContent(importElement);
        getPackageStatusElement.addContent(xmlGetstatusElement);
        bodyElement.addContent(getPackageStatusElement);

        Document doc = new Document(envelopeElement);

        return docToXMLString(doc);
    }

    private Pair<Integer, String> parseStatusResponse(Document responseDoc) {
        Element rootNode = responseDoc.getRootElement();
        Element bodyElement = rootNode.getChild("Body", soapenvNamespace);
        if(bodyElement != null) {
            Element getPackageStatusResponseElement = bodyElement.getChild("getPackageStatusResponse", feedbackNamespace);
            if(getPackageStatusResponseElement != null) {
                Element xmlGetstatusElement = getPackageStatusResponseElement.getChild("xmlGetstatus");
                if(xmlGetstatusElement != null) {
                    Element importElement = xmlGetstatusElement.getChild("import");
                    if(importElement != null) {
                        Integer status = Integer.parseInt(importElement.getAttributeValue("status"));
                        return Pair.create(status, elementToXMLString(importElement));
                    }

                }
            }
        }
        return Pair.create(-2, "Unknown error");
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

    private String parseResponsePurchasesByParams(Document responseDoc) {
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
        return new SAXBuilder().build(org.apache.commons.io.IOUtils.toInputStream(xml, encoding));
    }

    private String docToXMLString(Document doc) {
        return new XMLOutputter(Format.getPrettyFormat().setEncoding(encoding.name())).outputString(doc);
    }

    private String elementToXMLString(Element element) {
        return new XMLOutputter().outputString(element);
    }

    private class HttpRequestHandler implements HttpHandler {

        //пока рассматриваем только случай с 1 SetRetail сервером на 1 equ
        private final String sidEquipmentServer;
        private final boolean ignoreSalesWithoutNppGroupMachinery;
        private final boolean extendedLogs;

        public HttpRequestHandler() {
            Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
            sidEquipmentServer = kristalSettings.getSidEquipmentServer();
            if(sidEquipmentServer == null) {
                throw new RuntimeException(getLogPrefix() + "sidEquipmentServer option is required");
            }
            ignoreSalesWithoutNppGroupMachinery = kristalSettings.isIgnoreSalesWithoutNppGroupMachinery();
            extendedLogs = kristalSettings.isExtendedLogs();

        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            try {
                String uri = String.valueOf(httpExchange.getRequestURI());
                sendSalesLogger.info(getLogPrefix() + "HttpServer received " + uri + " from address" + httpExchange.getRemoteAddress().toString());
                boolean purchases = uri.endsWith("purchases");
                boolean introductions = uri.endsWith("introductions");
                boolean withdrawals = uri.endsWith("withdrawals");
                boolean zreports = uri.endsWith("zreports");

                if (purchases) {
                    try {
                        readSalesInfo(sidEquipmentServer, httpExchange, ignoreSalesWithoutNppGroupMachinery);
                    } catch (Exception e) {
                        sendSalesLogger.error(getLogPrefix() + "Reading SalesInfo", e);
                        sendSalesLogger.info(getLogPrefix() + "Request body was: " + new String(IOUtils.readBytesFromStream(httpExchange.getRequestBody()), encoding));
                        sendPurchasesResponse(httpExchange, e.getMessage());
                        reportEquipmentServerError(remote, sidEquipmentServer, e);
                    }
                } else if (introductions || withdrawals) {
                    try {
                        readCashDocuments(sidEquipmentServer, httpExchange, introductions, extendedLogs);
                    } catch (Exception e) {
                        sendSalesLogger.error(getLogPrefix() + "Reading CashDocuments", e);
                        sendCashDocumentResponse(httpExchange, e.getMessage(), introductions);
                        reportEquipmentServerError(remote, sidEquipmentServer, e);
                    }
                } else if (zreports) {
                    try {
                        readZReports(sidEquipmentServer, httpExchange);
                    } catch (Exception e) {
                        sendSalesLogger.error(getLogPrefix() + "Reading ZReports", e);
                        sendZReportsResponse(httpExchange, e.getMessage());
                        reportEquipmentServerError(remote, sidEquipmentServer, e);
                    }
                } else {
                    sendSalesLogger.error(getLogPrefix() + "unknown request: " + uri);
                }
            } finally {
                httpExchange.close();
            }
        }
    }

    private void readSalesInfo(String sidEquipmentServer, HttpExchange httpExchange, boolean ignoreSalesWithoutNppGroupMachinery) throws IOException, SQLException, JDOMException {
        List<CashRegisterInfo> cashRegisterInfoList = readCashRegisterInfo(sidEquipmentServer);
        Document doc = xmlStringToDoc(parseHttpRequestHandlerResponse(httpExchange, "purchases"));

        Set<String> directorySet = new HashSet<>();
        for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
            if (fitHandler(cashRegister) && !cashRegister.disableSales) {
                directorySet.add(cashRegister.directory);
            }
        }

        //assert directorySet.size() == 1
        for (String directory : directorySet) {
            Pair<List<SalesInfo>, String> salesData = parseSalesInfoXML(doc, directory, cashRegisterInfoList, new HashSet<>());
            if (!salesData.first.isEmpty() || ignoreSalesWithoutNppGroupMachinery || salesData.second != null) {
                sendSalesLogger.info(getLogPrefix() + "Sending SalesInfo: " + salesData.first.size());
                String result = remote.sendSalesInfo(salesData.first, sidEquipmentServer, directory);
                if (result != null) {
                    EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, result, directory);
                } else if (salesData.second != null) {
                    result = remote.sendExtraData(salesData.second);
                    if (result != null) {
                        EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, result, directory);
                    }
                }
                sendPurchasesResponse(httpExchange, result);
            }
        }
    }

    private void readCashDocuments(String sidEquipmentServer, HttpExchange httpExchange, boolean introductions, boolean extendedLogs) throws IOException, SQLException, JDOMException {
        Document doc = xmlStringToDoc(parseHttpRequestHandlerResponse(httpExchange, introductions ? "introductions" :  "withdrawals"));

        if(extendedLogs) {
            logRequestFile("cashDocument", docToXMLString(doc));
        }

        List<CashDocument> cashDocumentList = parseCashDocumentXML(doc, introductions);
        if (!cashDocumentList.isEmpty()) {
            sendSalesLogger.info(getLogPrefix() + "Sending CashDocuments: " + cashDocumentList.size());
            String result = remote.sendCashDocumentInfo(cashDocumentList);
            if (result != null) {
                sendSalesLogger.info(getLogPrefix() + "Send CashDocuments result: " + result);
                EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, result, null);
            }
            sendCashDocumentResponse(httpExchange, result, introductions);
        }
    }

    private void readZReports(String sidEquipmentServer, HttpExchange httpExchange) throws IOException, SQLException, JDOMException {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        boolean ignoreSalesWeightPrefix = kristalSettings.getIgnoreSalesWeightPrefix() != null && kristalSettings.getIgnoreSalesWeightPrefix();
        boolean useShopIndices = kristalSettings.getUseShopIndices() != null && kristalSettings.getUseShopIndices();
        boolean ignoreSalesDepartmentNumber = kristalSettings.getIgnoreSalesDepartmentNumber() != null && kristalSettings.getIgnoreSalesDepartmentNumber();
        boolean useNumberGroupInShopIndices = kristalSettings.useNumberGroupInShopIndices();
        boolean ignoreCashRegisterWithDisableSales = kristalSettings.isIgnoreCashRegisterWithDisableSales();
        boolean ignoreSalesWithoutNppGroupMachinery = kristalSettings.isIgnoreSalesWithoutNppGroupMachinery();
        boolean extendedLogs = kristalSettings.isExtendedLogs();

        Map<String, List<CashRegisterInfo>> cashRegisterByKeyMap = null;
        String directory = null;
        if(useShopIndices && ignoreSalesDepartmentNumber && useNumberGroupInShopIndices) {
            List<CashRegisterInfo> cashRegisterInfoList = readCashRegisterInfo(sidEquipmentServer);
            cashRegisterByKeyMap = getCashRegisterByKeyMap(cashRegisterInfoList, useShopIndices, true, true);

            for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
                if (fitHandler(cashRegister) && !cashRegister.disableSales) {
                    directory = cashRegister.directory;
                    break;
                }
            }
        }

        Map<String, List<Object>> zReportSumMap = new HashMap<>();

        Document doc = xmlStringToDoc(parseHttpRequestHandlerResponse(httpExchange, "zreports"));
        if(extendedLogs) {
            logRequestFile("zreports", docToXMLString(doc));
        }
        Element rootNode = doc.getRootElement();
        List zReportsList = rootNode.getChildren("zreport");

        for (Object zReportNode : zReportsList) {

            Integer numberCashRegister = readIntegerXMLValue(zReportNode, "cashNumber");
            Integer numberGroupCashRegister = readIntegerXMLValue(zReportNode, "shopNumber");

            String key = getCashRegisterKey(directory, numberCashRegister, true, null, true, String.valueOf(numberGroupCashRegister));
            CashRegisterInfo cashRegisterByKey = getCashRegister(cashRegisterByKeyMap, key);

            if (cashRegisterByKeyMap == null || (cashRegisterByKey != null && !ignoreSales(cashRegisterByKey, numberGroupCashRegister, key, ignoreCashRegisterWithDisableSales, ignoreSalesWithoutNppGroupMachinery))) {

                LocalDate dateZReport = LocalDate.parse(StringUtils.left(readStringXMLValue(zReportNode, "dateOperDay"), 10), DateTimeFormatter.ISO_DATE);

                String numberZReport = readStringXMLValue(zReportNode, "shiftNumber");
                String idZReport = numberGroupCashRegister + "_" + numberCashRegister + "_" + numberZReport + "_" + dateZReport.format(DateTimeFormatter.ofPattern("ddMMyyyy"));

                BigDecimal sumSale = readBigDecimalXMLValue(zReportNode, "amountByPurchaseFiscal");
                BigDecimal sumReturn = readBigDecimalXMLValue(zReportNode, "amountByReturnFiscal");
                BigDecimal kristalSum = HandlerUtils.safeSubtract(sumSale, sumReturn);
                if (kristalSum.compareTo(BigDecimal.ZERO) != 0) {
                    zReportSumMap.put(idZReport, Arrays.asList(kristalSum, numberCashRegister, numberZReport, idZReport, numberGroupCashRegister));
                }
            }
        }

        //вне зависимости от результата отправляем, что запрос обработан успешно
        sendZReportsResponse(httpExchange, null);

        try {
            //часто zreports приходит сразу после purchases - и суммы не сходятся.
            //Поэтому даём чекам время на то, чтобы обработаться
            Thread.sleep(120000); //1 minute
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Map<String, BigDecimal> baseZReportSumMap = remote.readZReportSumMap(new HashSet<>(zReportSumMap.keySet()));
        if(extendedLogs) {
            sendSalesLogger.info(getLogPrefix() + " zReportSumMap:" + StringUtils.join(zReportSumMap, ','));
            sendSalesLogger.info(getLogPrefix() + " baseZReportSumMap:" + StringUtils.join(baseZReportSumMap, ','));
        }

        ExtraCheckZReportBatch extraCheckResult = compareExtraCheckZReport(zReportSumMap, baseZReportSumMap);
        if (extraCheckResult != null) {
            if (!extraCheckResult.idZReportSet.isEmpty()) {
                remote.succeedExtraCheckZReport(extraCheckResult.idZReportSet);
            }
            if (!extraCheckResult.message.isEmpty()) {
                EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, extraCheckResult.message, null);
            }
        }
    }

    private void reportEquipmentServerError(EquipmentServerInterface remote, String sidEquipmentServer, Exception e) throws RemoteException {
        try {
            EquipmentServer.reportEquipmentServerError(remote, sidEquipmentServer, e);
        } catch (SQLException ignored) {
        }
    }

    private List<CashRegisterInfo> readCashRegisterInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        return remote.readCashRegisterInfo(sidEquipmentServer);
    }

    private static File requestDirectory = null;
    private void logRequestFile(String prefix, String request) {
        if(requestDirectory == null) {
            Enumeration appenders = Logger.getRootLogger().getAllAppenders();
            while (appenders.hasMoreElements()) {
                Appender currAppender = (Appender) appenders.nextElement();
                if (currAppender instanceof FileAppender && currAppender.getName().equals("stdout")) {
                    FileAppender fileAppender = (FileAppender) currAppender;
                    requestDirectory = new File(new File(fileAppender.getFile()).getParent() + "/requests");
                    requestDirectory.mkdirs();
                    break;
                }
            }
        }

        try {
            FileUtils.writeStringToFile(new File(requestDirectory, prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS")) + ".xml"), request, encoding);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private static class Kristal10Transaction {
        Long id;
        String directory;

        List<String> tiList;

        public Kristal10Transaction(Long id, String directory, List<String> tiList) {
            this.id = id;
            this.directory = directory;
            this.tiList = tiList;
        }
    }
}