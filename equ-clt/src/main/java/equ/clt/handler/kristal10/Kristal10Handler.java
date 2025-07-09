package equ.clt.handler.kristal10;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.api.stoplist.StopListInfo;
import equ.clt.handler.HandlerUtils;
import lsfusion.base.Pair;
import lsfusion.base.file.RawFileData;
import lsfusion.base.file.WriteUtils;
import org.apache.commons.io.FileUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Date;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static equ.clt.EquipmentServer.sqlDateToLocalDate;
import static equ.clt.EquipmentServer.sqlTimeToLocalTime;
import static equ.clt.handler.HandlerUtils.*;
import static lsfusion.base.BaseUtils.nvl;

public class Kristal10Handler extends Kristal10DefaultHandler {

    public Kristal10Handler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "kristal10";
    }

    @Override
    public String getLogPrefix() {
        return "Kristal10: ";
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) {

        Map<File, Long> fileMap = new HashMap<>();
        Map<Long, Exception> failedTransactionMap = new HashMap<>();
        Set<Long> emptyTransactionSet = new HashSet<>();

        Map<Long, DeleteBarcode> usedDeleteBarcodeTransactionMap = new HashMap<>();

        for(TransactionCashRegisterInfo transaction : transactionList) {

            try {

                processTransactionLogger.info(getLogPrefix() + "Send Transaction # " + transaction.id);

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
                String sftpPath = kristalSettings.getSftpPath();
                List<String> sftpDepartmentStoresList = kristalSettings.getSftpDepartmentStoresList();
                boolean exportAmountForBarcode = kristalSettings.isExportAmountForBarcode();
                boolean minusOneForEmptyVAT = kristalSettings.isMinusOneForEmptyVAT();

                List<String> directoriesList = new ArrayList<>();
                for (CashRegisterInfo cashRegisterInfo : transaction.machineryInfoList) {
                    if ((cashRegisterInfo.port != null) && (!directoriesList.contains(cashRegisterInfo.port)))
                        directoriesList.add(cashRegisterInfo.port);
                    if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory)))
                        directoriesList.add(cashRegisterInfo.directory);
                }

                if(directoriesList.isEmpty())
                    emptyTransactionSet.add(transaction.id);

                for (String directory : directoriesList) {

                    String exchangeDirectory = directory + "/products/source/";

                    if (!new File(exchangeDirectory).exists()) {
                        processTransactionLogger.info(getLogPrefix() + "exchange directory not found, trying to create: " + exchangeDirectory);
                        if(!new File(exchangeDirectory).mkdir() && !new File(exchangeDirectory).mkdirs())
                            processTransactionLogger.info(getLogPrefix() + "exchange directory not found, failed to create: " + exchangeDirectory);
                    }

                    //catalog-goods.xml
                    processTransactionLogger.info(getLogPrefix() + "creating catalog-goods file (Transaction " + transaction.id + ") - " + transaction.itemsList.size() + " items");

                    Element rootElement = new Element("goods-catalog");
                    Document doc = new Document(rootElement);
                    doc.setRootElement(rootElement);

                    Map<String, String> deleteBarcodeMap = deleteBarcodeDirectoryMap.get(directory);
                    DeleteBarcode usedDeleteBarcodes = new DeleteBarcode(transaction.nppGroupMachinery, directory);

                    for (CashRegisterItem item : transaction.itemsList) {
                        JSONObject infoJSON = getExtInfo(item.info);
                        JSONObject extraInfoJSON = getExtraInfo(item.extraInfo);
                        String shopIndices = getShopIndices(transaction, item, useNumberGroupInShopIndices, useShopIndices, weightShopIndices);
                        String barcodeItem = transformBarcode(transaction, item, skipWeightPrefix);
                        String idItem = idItemInMarkingOfTheGood ? item.idItem : barcodeItem;

                        fillRestrictionsElement(rootElement, item, idItem, barcodeItem, useIdItemInRestriction, shopIndices, useShopIndices, skipUseShopIndicesMinPrice);

                        //parent: rootElement
                        Element good = createGoodElement(transaction, item, idItem, barcodeItem, tobaccoGroups, skipScalesInfo, shopIndices, useShopIndices,
                                brandIsManufacturer, seasonIsCountry, minusOneForEmptyVAT, exportAmountForBarcode, deleteBarcodeMap, usedDeleteBarcodes, notGTINPrefixes,
                                infoJSON, extraInfoJSON);
                        rootElement.addContent(good);

                        addPriceEntryElements(good, transaction, item, null, infoJSON, useSectionAsDepartNumber, null);
                    }
                    usedDeleteBarcodeTransactionMap.put(transaction.id, usedDeleteBarcodes);
                    processTransactionLogger.info(String.format(getLogPrefix() + "created catalog-goods file (Transaction %s)", transaction.id));
                    File file = makeExportFile(exchangeDirectory, "catalog-goods");

                    File tempFile = File.createTempFile("catalog-goods", "xml");
                    try {
                        exportXML(doc, tempFile);

                        //copy to ftp
                        if(sftpPath != null && sftpDepartmentStoresList.contains(transaction.idDepartmentStoreGroupCashRegister)) {
                            try {
                                if (!sftpPath.startsWith("sftp")){
                                    File copyFile = makeExportFile(sftpPath, "catalog-goods");
                                    FileUtils.copyFile(tempFile, copyFile);
                                } else {
                                    WriteUtils.storeFileToSFTP(sftpPath + "/" + file.getName(), new RawFileData(tempFile), null);
                                }
                            } catch (Exception e) {
                                processTransactionLogger.error(getLogPrefix() + "sftp error", e);
                            }
                        }

                        //copy to exchange directory
                        FileUtils.copyFile(tempFile, file);

                    } finally {
                        safeDelete(tempFile);
                    }

                    processTransactionLogger.info(String.format(getLogPrefix() + "output catalog-goods file (Transaction %s)", transaction.id));

                    fileMap.put(file, transaction.id);
                }
            } catch (Exception e) {
                processTransactionLogger.error(getLogPrefix(), e);
                failedTransactionMap.put(transaction.id, e);
            }
        }
        processTransactionLogger.info(String.format(getLogPrefix() + "starting to wait for deletion %s files", fileMap.size()));
        return waitForDeletion(fileMap, failedTransactionMap, emptyTransactionSet, usedDeleteBarcodeTransactionMap);
    }

    private Map<Long, SendTransactionBatch> waitForDeletion(Map<File, Long> filesMap, Map<Long, Exception> failedTransactionMap,
                                                               Set<Long> emptyTransactionSet, Map<Long, DeleteBarcode> usedDeleteBarcodeTransactionMap) {
        int count = 0;
        Map<Long, SendTransactionBatch> result = new HashMap<>();
        while (!Thread.currentThread().isInterrupted() && !filesMap.isEmpty()) {
            try {
                Map<File, Long> nextFilesMap = new HashMap<>();
                count++;
                if (count >= 180) {
                    processTransactionLogger.info("Kristal10 (wait for deletion): timeout");
                    break;
                }
                for (Map.Entry<File, Long> entry : filesMap.entrySet()) {
                    File file = entry.getKey();
                    Long idTransaction = entry.getValue();
                    if (file.exists())
                        nextFilesMap.put(file, idTransaction);
                    else {
                        processTransactionLogger.info(String.format("Kristal10 (wait for deletion): file %s has been deleted", file.getAbsolutePath()));
                        DeleteBarcode deleteBarcodes = usedDeleteBarcodeTransactionMap.get(idTransaction);
                        if(deleteBarcodes != null) {
                            for(String b : deleteBarcodes.barcodes) {
                                Map<String, String> deleteBarcodesEntry = deleteBarcodeDirectoryMap.get(deleteBarcodes.directory);
                                deleteBarcodesEntry.remove(b);
                                deleteBarcodeDirectoryMap.put(deleteBarcodes.directory, deleteBarcodesEntry);
                            }
                        }
                        result.put(idTransaction, new SendTransactionBatch(null, null,
                                deleteBarcodes == null ? null : deleteBarcodes.nppGroupMachinery,
                                deleteBarcodes == null ? null : deleteBarcodes.barcodes,
                                failedTransactionMap.get(idTransaction)));
                        failedTransactionMap.remove(idTransaction);
                    }
                }
                filesMap = nextFilesMap;
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }

        for(Map.Entry<File, Long> file : filesMap.entrySet()) {
            processTransactionLogger.info(String.format("Kristal10 (wait for deletion): file %s has NOT been deleted", file.getKey().getAbsolutePath()));
            result.put(file.getValue(), new SendTransactionBatch(new RuntimeException(String.format(getLogPrefix() + "file %s has been created but not processed by server", file.getKey().getAbsolutePath()))));
        }
        for(Map.Entry<Long, Exception> entry : failedTransactionMap.entrySet()) {
            result.put(entry.getKey(), new SendTransactionBatch(entry.getValue()));
        }
        for(Long emptyTransaction : emptyTransactionSet) {
            result.put(emptyTransaction, new SendTransactionBatch(null));
        }
        return result;
    }

    private synchronized File makeExportFile(String exchangeDirectory, String prefix) {
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy'T'HH-mm-ss");
        File file = new File(exchangeDirectory + "//" + prefix + "_" + LocalDateTime.now().format(dateFormat) + ".xml");
        //чит для избежания ситуации, совпадения имён у двух файлов (в основе имени - текущее время с точностью до секунд)
        while(file.exists()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            file = new File(exchangeDirectory + "//" + "catalog-goods_" + LocalDateTime.now().format(dateFormat) + ".xml");
        }
        return file;
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList,
                                 Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) throws IOException {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        boolean useNumberGroupInShopIndices = kristalSettings.useNumberGroupInShopIndices();

        for (RequestExchange entry : requestExchangeList) {
            int count = 0;
            String requestResult = null;

            for (Map.Entry<String, Set<String>> directoryStockEntry : getDirectoryStockMap(entry, useNumberGroupInShopIndices).entrySet()) {
                String directory = directoryStockEntry.getKey();
                Set<String> stockSet = directoryStockEntry.getValue();

                machineryExchangeLogger.info(getLogPrefix() + "creating request files for directory : " + directory);
                String dateFrom = entry.dateFrom.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                String dateTo = entry.dateTo.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

                String exchangeDirectory = directory + "/reports/source/";

                if (new File(exchangeDirectory).exists() || new File(exchangeDirectory).mkdirs()) {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get(exchangeDirectory + "reports.request")), encoding));
                    if (!stockSet.isEmpty()) {
                        StringBuilder shopsRange = new StringBuilder();
                        for (String stock : stockSet) {
                            shopsRange.append((shopsRange.length() == 0) ? "" : ",").append(stock);
                        }
                        if (stockSet.size() == 1)
                            writer.write(String.format("shop: %s\n", shopsRange));
                        else
                            writer.write(String.format("shopsRange: %s\n", shopsRange));
                    }
                    Set<CashRegisterInfo> cashRegisterSet = getCashRegisterSet(entry, false);
                    if(!cashRegisterSet.isEmpty()) {
                        StringBuilder cashesRange = new StringBuilder();
                        for (CashRegisterInfo cashRegister : cashRegisterSet) {
                            cashesRange.append((cashesRange.length() == 0) ? "" : ",").append(cashRegister.number);
                        }
                        writer.write(String.format("%s: %s\n", cashRegisterSet.size() == 1 ? "cash" : "cashesRange", cashesRange));
                    }
                    writer.write(String.format("dateRange: %s-%s\nreport: purchases", dateFrom, dateTo));
                    writer.close();
                } else
                    requestResult = "Error: " + exchangeDirectory + " doesn't exist. Request creation failed.";
                count++;
            }
            if (count > 0) {
                if (requestResult == null)
                    succeededRequests.add(entry.requestExchange);
                else
                    failedRequests.put(entry.requestExchange, new RuntimeException(requestResult));
            } else
                succeededRequests.add(entry.requestExchange);
        }
    }

    @Override
    public void finishReadingSalesInfo(Kristal10SalesBatch salesBatch) {
        sendSalesLogger.info(getLogPrefix() + "Finish Reading started");
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        Integer cleanOldFilesDays = kristalSettings.getCleanOldFilesDays();
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);

            Calendar calendar = Calendar.getInstance();
            String directory = f.getParent() + "/success-" + new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime()) + "/";
            if(cleanOldFilesDays != null) {
                calendar.add(Calendar.DATE, -cleanOldFilesDays);
                String oldDirectory = f.getParent() + "/success-" + new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime()) + "/";
                File oldDir = new File(oldDirectory);
                File[] files = oldDir.listFiles();
                if(files != null) {
                    for (File file : files) {
                        safeDelete(file);
                    }
                }
                safeDelete(oldDir);
            }
            if (new File(directory).exists() || new File(directory).mkdirs())
                copyWithTimeout(f, new File(directory + f.getName()));

            sendSalesLogger.info(getLogPrefix() + "deleting file " + readFile);
            forceDelete(f);
        }
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList) {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        boolean ignoreFileLocks = kristalSettings.getIgnoreFileLock() != null && kristalSettings.getIgnoreFileLock();

        Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
        Set<String> directorySet = new HashSet<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c) && c.directory != null && c.number != null) {
                directoryCashRegisterMap.put(c.directory + "_" + c.number, c);
                directorySet.add(c.directory);
            }
        }

        List<CashDocument> cashDocumentList = new ArrayList<>();
        List<String> readFiles = new ArrayList<>();
        for (String directory : directorySet) {

            String exchangeDirectory = directory + "/reports/";

            File[] filesList = new File(exchangeDirectory).listFiles(pathname -> (pathname.getName().startsWith("cash_in") || pathname.getName().startsWith("cash_out")) && pathname.getPath().endsWith(".xml"));

            if (filesList == null || filesList.length == 0)
                sendSalesLogger.info(getLogPrefix() + "No cash documents found in " + exchangeDirectory);
            else {
                sendSalesLogger.info(getLogPrefix() + "found " + filesList.length + " file(s) in " + exchangeDirectory);

                for (File file : filesList) {
                    try {
                        String fileName = file.getName();
                        sendSalesLogger.info(getLogPrefix() + "reading " + fileName);
                        if (!ignoreFileLocks && isFileLocked(file)) {
                            sendSalesLogger.info(getLogPrefix() + fileName + " is locked");
                        } else {
                            SAXBuilder builder = new SAXBuilder();

                            Document document = builder.build(file);
                            Element rootNode = document.getRootElement();

                            boolean cashIn = file.getName().startsWith("cash_in");

                            List<Element> cashDocumentsList = rootNode.getChildren(cashIn ? "introduction" : "withdrawal");

                            for (Element cashDocumentNode : cashDocumentsList) {

                                String numberCashDocument = readStringXMLAttribute(cashDocumentNode, "number");

                                Integer numberCashRegister = readIntegerXMLAttribute(cashDocumentNode, "cash");
                                CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory + "_" + numberCashRegister);
                                Integer numberGroup = cashRegister == null ? null : cashRegister.numberGroup;

                                BigDecimal sumCashDocument = readBigDecimalXMLAttribute(cashDocumentNode, "amount");
                                if(!cashIn)
                                    sumCashDocument = sumCashDocument == null ? null : sumCashDocument.negate();

                                long dateTimeCashDocument = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(readStringXMLAttribute(cashDocumentNode, "regtime")).getTime();
                                Date dateCashDocument = new Date(dateTimeCashDocument);
                                Time timeCashDocument = new Time(dateTimeCashDocument);

                                cashDocumentList.add(new CashDocument(numberCashDocument, numberCashDocument, sqlDateToLocalDate(dateCashDocument), sqlTimeToLocalTime(timeCashDocument),
                                        numberGroup, numberCashRegister, null, sumCashDocument));
                            }
                            readFiles.add(file.getAbsolutePath());
                        }
                    } catch (Throwable e) {
                        sendSalesLogger.error("File: " + file.getAbsolutePath(), e);
                    }
                }
            }
        }
        return new CashDocumentBatch(cashDocumentList, readFiles);
    }

    @Override
    public void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch) {
        sendSalesLogger.info(getLogPrefix() + "Finish ReadingCashDocumentInfo started");
        for (String readFile : cashDocumentBatch.readFiles) {
            sendSalesLogger.info(getLogPrefix() + "deleting file " + readFile);
            forceDelete(new File(readFile));
        }
    }

    @Override
    public Pair<String, Set<String>> sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machinerySet) throws IOException {
        Set<String> directorySet = HandlerUtils.getDirectorySet(machinerySet);
        //из-за временного решения с весовыми товарами для этих весовых товаров стоп-листы работать не будут
        processStopListLogger.info(getLogPrefix() + "Send StopList # " + stopListInfo.number + " to " + directorySet.size() + " directories.");

        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        boolean useShopIndices = kristalSettings.getUseShopIndices() != null && kristalSettings.getUseShopIndices();
        boolean idItemInMarkingOfTheGood = kristalSettings.isIdItemInMarkingOfTheGood() != null && kristalSettings.isIdItemInMarkingOfTheGood();
        boolean skipWeightPrefix = kristalSettings.getSkipWeightPrefix() != null && kristalSettings.getSkipWeightPrefix();
        List<String> tobaccoGroups = getTobaccoGroups(kristalSettings.getTobaccoGroup());
        boolean useNumberGroupInShopIndices = kristalSettings.useNumberGroupInShopIndices();
        boolean useSectionAsDepartNumber = kristalSettings.useSectionAsDepartNumber();
        boolean minusOneForEmptyVAT = kristalSettings.isMinusOneForEmptyVAT();

        for (String directory : directorySet) {
            processStopListLogger.info(getLogPrefix() + " start sending to " + directory);

            if (stopListInfo.dateFrom == null || stopListInfo.timeFrom == null) {
                String error = getLogPrefix() + "Error! Start DateTime not specified for stopList " + stopListInfo.number;
                processStopListLogger.error(error);
                throw new RuntimeException(error);
            }

            if (stopListInfo.dateTo == null || stopListInfo.timeTo == null) {
                stopListInfo.dateTo = LocalDate.of(2040, 1, 1);
                stopListInfo.timeTo = LocalTime.of(23, 59, 59);
            }

            String exchangeDirectory = directory + "/products/source/";

            if (!new File(exchangeDirectory).exists())
                new File(exchangeDirectory).mkdirs();

            Element rootElement = new Element("goods-catalog");
            Document doc = new Document(rootElement);
            doc.setRootElement(rootElement);

            if (!stopListInfo.exclude) {
                processStopListLogger.info(getLogPrefix() + " found " + stopListInfo.stopListItemMap.size() + " items");
                addStopListItems(rootElement, stopListInfo, useShopIndices, idItemInMarkingOfTheGood, skipWeightPrefix,
                        tobaccoGroups, useNumberGroupInShopIndices, useSectionAsDepartNumber, minusOneForEmptyVAT);

                if (!stopListInfo.stopListItemMap.isEmpty()) {
                    File file = makeExportFile(exchangeDirectory, "catalog-goods_stoplist");
                    processStopListLogger.info(getLogPrefix() + " start writing " + stopListInfo.number + " to " + file.getAbsolutePath());
                    exportXML(doc, file);
                }
            }
        }
        return null;
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
        machineryExchangeLogger.info(getLogPrefix() + "sendDiscountCardList started");
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        String discountCardDirectory = kristalSettings.getDiscountCardDirectory();
        String discountCardFileName = kristalSettings.getDiscountCardFileName();
        if (!discountCardList.isEmpty()) {
            Document doc = generateDiscountCardXML(discountCardList, requestExchange);
            for (String directory : getDirectorySet(requestExchange)) {
                String exchangeDirectory = directory + nvl(discountCardDirectory, "/products/source/");
                if (new File(exchangeDirectory).exists() || new File(exchangeDirectory).mkdirs()) {
                    machineryExchangeLogger.info(String.format(getLogPrefix() + "Send DiscountCards to %s", exchangeDirectory));
                    exportXML(doc, makeExportFile(exchangeDirectory, nvl(discountCardFileName, "catalog-goods")));
                }
            }
        }
    }

    @Override
    public Kristal10SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {

        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        String transformUPCBarcode = kristalSettings.getTransformUPCBarcode();
        Integer maxFilesCount = kristalSettings.getMaxFilesCount();
        boolean ignoreSalesWeightPrefix = kristalSettings.getIgnoreSalesWeightPrefix() != null && kristalSettings.getIgnoreSalesWeightPrefix();
        boolean useShopIndices = kristalSettings.getUseShopIndices() != null && kristalSettings.getUseShopIndices();
        boolean ignoreSalesDepartmentNumber = kristalSettings.getIgnoreSalesDepartmentNumber() != null && kristalSettings.getIgnoreSalesDepartmentNumber();
        boolean ignoreFileLocks = kristalSettings.getIgnoreFileLock() != null && kristalSettings.getIgnoreFileLock();
        boolean useNumberGroupInShopIndices = kristalSettings.useNumberGroupInShopIndices();
        String giftCardRegexp = kristalSettings.getGiftCardRegexp();
        if(giftCardRegexp == null)
            giftCardRegexp = "(?!666)\\d{3}";
        boolean useSectionAsDepartNumber = kristalSettings.useSectionAsDepartNumber();
        Set<String> customPayments = parseStringPayments(kristalSettings.getCustomPayments());
        boolean ignoreCashRegisterWithDisableSales = kristalSettings.isIgnoreCashRegisterWithDisableSales();
        boolean ignoreSalesWithoutNppGroupMachinery = kristalSettings.isIgnoreSalesWithoutNppGroupMachinery();

        Map<String, List<CashRegisterInfo>> cashRegisterByKeyMap = getCashRegisterByKeyMap(cashRegisterInfoList, useShopIndices, useNumberGroupInShopIndices, ignoreSalesDepartmentNumber);

        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<String> filePathList = new ArrayList<>();

        String exchangeDirectory = directory + "/reports/";

        File[] filesList = new File(exchangeDirectory).listFiles(pathname -> pathname.getName().startsWith("purchases") && pathname.getPath().endsWith(".xml"));
        
        //Set<String> ids = new HashSet<>();
        Set<String> usedBarcodes = new HashSet<>();
        if (filesList == null || filesList.length == 0)
            sendSalesLogger.info(getLogPrefix() + "No checks found in " + exchangeDirectory);
        else {
            if(maxFilesCount == null)
                sendSalesLogger.info(String.format(getLogPrefix() + "found %s file(s) in %s", filesList.length, exchangeDirectory));
            else
                sendSalesLogger.info(String.format(getLogPrefix() + "found %s file(s) in %s, will read %s file(s)", filesList.length, exchangeDirectory, Math.min(filesList.length, maxFilesCount)));

            Arrays.sort(filesList, Comparator.comparingLong(File::lastModified));

            int filesCount = 0;
            for (File file : filesList) {
                filesCount++;
                if(maxFilesCount != null && maxFilesCount < filesCount)
                    break;
                try {
                    String fileName = file.getName();
                    sendSalesLogger.info(getLogPrefix() + "reading " + fileName);
                    if (!ignoreFileLocks && isFileLocked(file)) {
                        sendSalesLogger.info(getLogPrefix() + fileName + " is locked");
                    } else {
                        SAXBuilder builder = new SAXBuilder();

                        Document document = builder.build(file);
                        Element rootNode = document.getRootElement();
                        List<Element> purchasesList = rootNode.getChildren("purchase");

                        for (Element purchaseNode : purchasesList) {

                            String status = readStringXMLAttribute(purchaseNode, "status");
                            if (status != null && status.equals("CANCELLED")) {
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

                            LocalDateTime dateTimeReceipt = LocalDateTime.parse(readStringXMLAttribute(purchaseNode, "saletime"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
                            LocalDate dateReceipt = dateTimeReceipt.toLocalDate();
                            LocalTime timeReceipt = dateTimeReceipt.toLocalTime();

                            LocalDate dateZReport = LocalDate.parse(readStringXMLAttribute(purchaseNode, "operDay"), DateTimeFormatter.ofPattern("yyyy-MM-ddXXX"));

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
                                                    payments.add(Payment.getCard(sum));
                                                    break;
                                                case "BankCardPaymentEntity":
                                                    payments.add(Payment.getCard(sum, "paymentCard", getPluginPropertyValue(paymentEntryNode, "card.number")));
                                                    break;
                                                case "GiftCardPaymentEntity": {
                                                    List<Element> pluginProperties = paymentEntryNode.getChildren("plugin-property");
                                                    boolean found = false;
                                                    String giftCardNumber = null;
                                                    BigDecimal giftCardPrice = null;
                                                    for (Element pluginProperty : pluginProperties) {
                                                        String keyPluginProperty = pluginProperty.getAttributeValue("key");
                                                        String valuePluginProperty = pluginProperty.getAttributeValue("value");
                                                        if (notNullNorEmpty(keyPluginProperty) && notNullNorEmpty(valuePluginProperty)) {
                                                            if (keyPluginProperty.equals("card.number")) {
                                                                giftCardNumber = valuePluginProperty;
                                                                found = true;
                                                            } else if (keyPluginProperty.equals("card.amount")) {
                                                                giftCardPrice = new BigDecimal(valuePluginProperty);
                                                            }
                                                        }
                                                    }
                                                    if (found) {
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

                                        //обнаруживаем продажу сертификатов
                                        boolean isGiftCard = false;
                                        List<Element> pluginProperties = positionEntryNode.getChildren("plugin-property");
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
                                        if (startDate == null || dateReceipt.compareTo(startDate) >= 0) {

                                            String idSaleReceiptReceiptReturnDetail = null;
                                            Element originalPurchase = purchaseNode.getChild("original-purchase");
                                            if(originalPurchase != null) {
                                                Integer numberCashRegisterOriginal = readIntegerXMLAttribute(originalPurchase, "cash");
                                                String numberZReportOriginal = readStringXMLAttribute(originalPurchase, "shift");
                                                Integer numberReceiptOriginal = readIntegerXMLAttribute(originalPurchase, "number");
                                                Date dateReceiptOriginal = new Date(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(readStringXMLAttribute(originalPurchase, "saletime")).getTime());
                                                idSaleReceiptReceiptReturnDetail = nppGroupMachinery + "_" + numberCashRegisterOriginal + "_" + numberZReportOriginal + "_"
                                                        + new SimpleDateFormat("ddMMyyyy").format(dateReceiptOriginal) + "_" + numberReceiptOriginal;
                                            }

    //                                        String id = nppGroupMachinery + "_" + numberCashRegister + "_" + numberZReport + "_" + new SimpleDateFormat("ddMMyyyy").format(dateReceipt) + "_" + numberReceipt + "_" + numberReceiptDetail;
    //                                        if (ids.contains(id)) {
    //                                            sendSalesLogger.error("found duplicate key : " + id);
    //                                        } else {
    //                                            ids.add(id);
    //                                        }

                                            if(sumGiftCard.compareTo(BigDecimal.ZERO) != 0)
                                                sumGiftCardMap.put(null, new GiftCard(sumGiftCard));
                                            currentSalesInfoList.add(getSalesInfo(isGiftCard, false, nppGroupMachinery, numberCashRegister, numberZReport, dateZReport, timeReceipt,
                                                    numberReceipt, dateReceipt, timeReceipt, idEmployee, firstNameEmployee, lastNameEmployee, sumGiftCardMap,
                                                    null, barcode, idItem, null, idSaleReceiptReceiptReturnDetail, quantity, price, sumReceiptDetail, discountPercentReceiptDetail,
                                                    discountSumReceiptDetail, discountSumReceipt, discountCard, null, numberReceiptDetail, fileName,
                                                    useSectionAsDepartNumber ? positionDepartNumber : null, false, receiptExtraFields, null, cashRegisterByKey));
                                        }
                                    }
                                    count++;
                                }

                            }

                            addPayments(sumGiftCardMap, payments, currentPaymentSum, currentSalesInfoList);

                            salesInfoList.addAll(currentSalesInfoList);
                        }
                        filePathList.add(file.getAbsolutePath());
                    }
                } catch (Throwable e) {
                    sendSalesLogger.error("File: " + file.getAbsolutePath(), e);
                }
            }
        }
        return (salesInfoList.isEmpty() && filePathList.isEmpty()) ? null :
                new Kristal10SalesBatch(salesInfoList, null, filePathList);
    }

    @Override
    public Map<String, List<Object>> readExtraCheckZReport(List<CashRegisterInfo> cashRegisterInfoList) {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : new Kristal10Settings();
        boolean ignoreFileLocks = kristalSettings.getIgnoreFileLock() != null && kristalSettings.getIgnoreFileLock();

        Map<String, List<Object>> zReportSumMap = new HashMap<>();

        Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c) && c.directory != null && c.number != null) {
                directoryCashRegisterMap.put(c.directory + "_" + c.number, c);
            }
        }

        for (String directory : directoryCashRegisterMap.keySet()) {

            String exchangeDirectory = directory + "/reports/";

            File[] filesList = new File(exchangeDirectory).listFiles(pathname -> pathname.getName().startsWith("zreports") && pathname.getPath().endsWith(".xml"));

            if (filesList != null && filesList.length > 0) {
                sendSalesLogger.info(getLogPrefix() + "found " + filesList.length + " z-report(s) in " + exchangeDirectory);

                for (File file : filesList) {
                    try {
                        String fileName = file.getName();
                        sendSalesLogger.info(getLogPrefix() + "reading " + fileName);
                        if (!ignoreFileLocks && isFileLocked(file)) {
                            sendSalesLogger.info(getLogPrefix() + fileName + " is locked");
                        } else {
                            SAXBuilder builder = new SAXBuilder();

                            Document document = builder.build(file);
                            Element rootNode = document.getRootElement();
                            List<Element> zReportsList = rootNode.getChildren("zreport");

                            for (Element zReportNode : zReportsList) {

                                Integer numberCashRegister = readIntegerXMLValue(zReportNode, "cashNumber");
                                CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory + "_" + numberCashRegister);
                                Integer numberGroupCashRegister = cashRegister == null ? null : cashRegister.numberGroup;

                                String numberZReport = readStringXMLValue(zReportNode, "shiftNumber");
                                String idZReport = numberGroupCashRegister + "_" + numberCashRegister + "_" + numberZReport;

                                BigDecimal sumSale = readBigDecimalXMLValue(zReportNode, "amountByPurchaseFiscal");
                                BigDecimal sumReturn = readBigDecimalXMLValue(zReportNode, "amountByReturnFiscal");
                                BigDecimal kristalSum = HandlerUtils.safeSubtract(sumSale, sumReturn);
                                zReportSumMap.put(idZReport, Arrays.asList(kristalSum, numberCashRegister, numberZReport, idZReport, numberGroupCashRegister));

                            }
                            String dir = file.getParent() + "/success-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "/";
                            File successDir = new File(dir);
                            if (successDir.exists() || successDir.mkdirs())
                                copyWithTimeout(file, new File(dir + file.getName()));
                            safeDelete(file);
                        }
                    } catch (Throwable e) {
                        sendSalesLogger.error("File: " + file.getAbsolutePath(), e);
                    }
                }
            }
        }
        return zReportSumMap.isEmpty() ? null : zReportSumMap;
    }

    private void exportXML(Document doc, File file) throws IOException {
        XMLOutputter xmlOutput = new XMLOutputter();
        xmlOutput.setFormat(Format.getPrettyFormat().setEncoding(encoding.name()));
        PrintWriter fw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(file.toPath()), encoding));
        xmlOutput.output(doc, fw);
        fw.close();
    }
}
