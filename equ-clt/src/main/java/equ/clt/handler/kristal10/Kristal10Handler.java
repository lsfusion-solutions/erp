package equ.clt.handler.kristal10;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static equ.clt.handler.HandlerUtils.safeAdd;
import static equ.clt.handler.HandlerUtils.safeDivide;
import static equ.clt.handler.HandlerUtils.safeMultiply;

public class Kristal10Handler extends DefaultCashRegisterHandler<Kristal10SalesBatch> {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");
    protected final static Logger machineryExchangeLogger = Logger.getLogger("MachineryExchangeLogger");

    private static Map<String, Map<String, String>> deleteBarcodeDirectoryMap = new HashMap<>();

    String encoding = "utf-8";

    private FileSystemXmlApplicationContext springContext;

    public Kristal10Handler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "kristal10";
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) throws IOException {

        Map<File, Long> fileMap = new HashMap<>();
        Map<Long, Exception> failedTransactionMap = new HashMap<>();
        Set<Long> emptyTransactionSet = new HashSet<>();

        Map<Long, DeleteBarcode> usedDeleteBarcodeTransactionMap = new HashMap<>();

        for(TransactionCashRegisterInfo transaction : transactionList) {

            try {

                processTransactionLogger.info("Kristal10: Send Transaction # " + transaction.id);

                Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
                boolean brandIsManufacturer = kristalSettings != null && kristalSettings.getBrandIsManufacturer() != null && kristalSettings.getBrandIsManufacturer();
                boolean seasonIsCountry = kristalSettings != null && kristalSettings.getSeasonIsCountry() != null && kristalSettings.getSeasonIsCountry();
                boolean idItemInMarkingOfTheGood = kristalSettings != null && kristalSettings.isIdItemInMarkingOfTheGood() != null && kristalSettings.isIdItemInMarkingOfTheGood();
                boolean skipWeightPrefix = kristalSettings != null && kristalSettings.getSkipWeightPrefix() != null && kristalSettings.getSkipWeightPrefix();
                boolean skipScalesInfo = kristalSettings != null && kristalSettings.getSkipScalesInfo() != null && kristalSettings.getSkipScalesInfo();
                boolean useShopIndices = kristalSettings != null && kristalSettings.getUseShopIndices() != null && kristalSettings.getUseShopIndices();
                boolean skipUseShopIndicesMinPrice = kristalSettings != null && kristalSettings.getSkipUseShopIndicesMinPrice() != null && kristalSettings.getSkipUseShopIndicesMinPrice();
                String weightShopIndices = kristalSettings != null ? kristalSettings.getWeightShopIndices() : null;
                boolean useIdItemInRestriction = kristalSettings != null && kristalSettings.getUseIdItemInRestriction() != null && kristalSettings.getUseIdItemInRestriction();
                List<String> tobaccoGroups = getTobaccoGroups(kristalSettings != null ? kristalSettings.getTobaccoGroup() : null);
                List<String> notGTINPrefixes = kristalSettings != null ? kristalSettings.getNotGTINPrefixesList() : null;
                boolean useNumberGroupInShopIndices = kristalSettings != null && kristalSettings.getUseNumberGroupInShopIndices() != null && kristalSettings.getUseNumberGroupInShopIndices();

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
                        processTransactionLogger.info("Kristal10: exchange directory not found, trying to create: " + exchangeDirectory);
                        if(!new File(exchangeDirectory).mkdir() && !new File(exchangeDirectory).mkdirs())
                            processTransactionLogger.info("Kristal10: exchange directory not found, failed to create: " + exchangeDirectory);
                    }

                    //catalog-goods.xml
                    processTransactionLogger.info("Kristal10: creating catalog-goods file (Transaction " + transaction.id + ") - " + transaction.itemsList.size() + " items");

                    Element rootElement = new Element("goods-catalog");
                    Document doc = new Document(rootElement);
                    doc.setRootElement(rootElement);

                    String weightCode = transaction.weightCodeGroupCashRegister == null ? "21" : transaction.weightCodeGroupCashRegister;

                    Map<String, String> deleteBarcodeMap = deleteBarcodeDirectoryMap.get(directory);
                    DeleteBarcode usedDeleteBarcodes = new DeleteBarcode(transaction.nppGroupMachinery, directory);

                    for (CashRegisterItemInfo item : transaction.itemsList) {
                        if (!Thread.currentThread().isInterrupted()) {

                            String shopIndices = useNumberGroupInShopIndices ? String.valueOf(transaction.nppGroupMachinery) : transaction.idDepartmentStoreGroupCashRegister;
                            if (useShopIndices && item.passScalesItem && weightShopIndices != null) {
                                shopIndices += " " + weightShopIndices;
                            }
                            //parent: rootElement
                            Element good = new Element("good");

                            String barcodeItem = transformBarcode(item.idBarcode, weightCode, item.passScalesItem, skipWeightPrefix);
                            String idItem = idItemInMarkingOfTheGood ? item.idItem : barcodeItem;

                            setAttribute(good, "marking-of-the-good", idItem);

                            boolean deleteBarcode = deleteBarcodeMap != null && deleteBarcodeMap.containsValue(idItem);
                            if(deleteBarcode)
                                usedDeleteBarcodes.barcodes.add(item.idBarcode);

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

                            if (useShopIndices)
                                addStringElement(good, "shop-indices", shopIndices);

                            addStringElement(good, "name", item.name);

                            //parent: good
                            Element barcode = new Element("bar-code");
                            setAttribute(barcode, "code", barcodeItem);
                            addStringElement(barcode, "default-code", "true");
                            if(deleteBarcode)
                                setAttribute(barcode, "deleted", true);
                            good.addContent(barcode);

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

                            addProductType(good, item, tobaccoGroups);

                            if(item.splitItem && !item.passScalesItem) {
                                Element pluginProperty = new Element("plugin-property");
                                setAttribute(pluginProperty, "key", "precision");
                                setAttribute(pluginProperty, "value", "0.001");
                                good.addContent(pluginProperty);
                            }

                            //parent: good
                            Element priceEntry = new Element("price-entry");
                            Object price = item.price == null ? null : (item.price.doubleValue() == 0.0 ? "0.00" : item.price);
                            setAttribute(priceEntry, "price", price);
                            setAttribute(priceEntry, "deleted", "false");
                            addStringElement(priceEntry, "begin-date", currentDate());
                            addStringElement(priceEntry, "number", "1");
                            good.addContent(priceEntry);

                            int vat = item.vat == null || item.vat.intValue() == 0 ? 20 : item.vat.intValue();
                            if(vat != 10 && vat != 20) {
                                vat = 20;
                            }
                            addStringElement(good, "vat", String.valueOf(vat));

                            //parent: priceEntry
                            Element department = new Element("department");
                            setAttribute(department, "number", transaction.departmentNumberGroupCashRegister);
//                            addStringElement(department, "name", transaction.nameGroupMachinery == null ? "Отдел" : transaction.nameGroupMachinery);
                            priceEntry.addContent(department);

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
                                String error = "Kristal10: Error! UOM not specified for item with barcode " + barcodeItem;
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
                    usedDeleteBarcodeTransactionMap.put(transaction.id, usedDeleteBarcodes);
                    processTransactionLogger.info(String.format("Kristal10: created catalog-goods file (Transaction %s)", transaction.id));
                    File file = makeExportFile(exchangeDirectory, "catalog-goods");
                    XMLOutputter xmlOutput = new XMLOutputter();
                    xmlOutput.setFormat(Format.getPrettyFormat().setEncoding(encoding));
                    PrintWriter fw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
                    xmlOutput.output(doc, fw);
                    fw.close();
                    processTransactionLogger.info(String.format("Kristal10: output catalog-goods file (Transaction %s)", transaction.id));

                    fileMap.put(file, transaction.id);
                }
            } catch (Exception e) {
                processTransactionLogger.error("Kristal10: ", e);
                failedTransactionMap.put(transaction.id, e);
            }
        }
        processTransactionLogger.info(String.format("Kristal10: starting to wait for deletion %s files", fileMap.size()));
        return waitForDeletion(fileMap, failedTransactionMap, emptyTransactionSet, usedDeleteBarcodeTransactionMap);
    }

    private String removeZeroes(String value) {
        if(value != null) {
            while(value.startsWith("0")) {
                value = value.substring(1);
            }
        }
        return value;
    }

    private void addProductType(Element good, ItemInfo item, List<String> tobaccoGroups) {
        String productType;
        if(item.idItemGroup != null && tobaccoGroups != null && tobaccoGroups.contains(item.idItemGroup))
            productType = "ProductCiggyEntity";
        else if (item.passScalesItem)
            productType = item.splitItem ? "ProductWeightEntity" : "ProductPieceWeightEntity";
        else
            productType = (item.flags == null || ((item.flags & 256) == 0)) ? "ProductPieceEntity" : "ProductSpiritsEntity";
        addStringElement(good, "product-type", productType);
    }

    private List<String> getTobaccoGroups (String tobaccoGroup) {
        List<String> tobaccoGroups = new ArrayList<>();
        if (tobaccoGroup != null)
            Collections.addAll(tobaccoGroups, tobaccoGroup.split(","));
        return tobaccoGroups;
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
            result.put(file.getValue(), new SendTransactionBatch(new RuntimeException(String.format("Kristal10: file %s has been created but not processed by server", file.getKey().getAbsolutePath()))));
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
        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy'T'HH-mm-ss");
        File file = new File(exchangeDirectory + "//" + prefix + "_" + dateFormat.format(Calendar.getInstance().getTime()) + ".xml");
        //чит для избежания ситуации, совпадения имён у двух файлов (в основе имени - текущее время с точностью до секунд)
        while(file.exists()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            file = new File(exchangeDirectory + "//" + "catalog-goods_" + dateFormat.format(Calendar.getInstance().getTime()) + ".xml");
        }
        return file;
    }

    private void addStringElement(Element parent, String id, String value) {
        if (value != null)
            parent.addContent(new Element(id).setText(value));
    }

    private void setAttribute(Element element, String id, Object value) {
        if (value != null)
            element.setAttribute(new Attribute(id, String.valueOf(value)));
    }

    private void addHierarchyItemGroup(Element parent, List<ItemGroup> hierarchyItemGroup) {
        if (!hierarchyItemGroup.isEmpty()) {
            Element element = new Element("parent-group");
            setAttribute(element, "id", hierarchyItemGroup.get(0).idItemGroup);
            addStringElement(element, "name", hierarchyItemGroup.get(0).nameItemGroup);
            parent.addContent(element);
            addHierarchyItemGroup(element, hierarchyItemGroup.subList(1, hierarchyItemGroup.size()));
        }
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList,
                                 Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) throws IOException, ParseException {
        for (RequestExchange entry : requestExchangeList) {
            int count = 0;
            String requestResult = null;

            for (Map.Entry<String, Set<String>> directoryStockEntry : getDirectoryStockMap(entry).entrySet()) {
                String directory = directoryStockEntry.getKey();
                Set<String> stockSet = directoryStockEntry.getValue();

                sendSalesLogger.info("Kristal10: creating request files for directory : " + directory);
                String dateFrom = new SimpleDateFormat("dd.MM.yyyy").format(entry.dateFrom);
                String dateTo = new SimpleDateFormat("dd.MM.yyyy").format(entry.dateTo);

                String exchangeDirectory = directory + "/reports/source/";

                if (new File(exchangeDirectory).exists() || new File(exchangeDirectory).mkdirs()) {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exchangeDirectory + "reports.request"), encoding));
                    if (!stockSet.isEmpty()) {
                        StringBuilder shopsRange = new StringBuilder();
                        for (String stock : stockSet) {
                            shopsRange.append((shopsRange.length() == 0) ? "" : ",").append(stock);
                        }
                        if (stockSet.size() == 1)
                            writer.write(String.format("shop: %s\n", shopsRange.toString()));
                        else
                            writer.write(String.format("shopsRange: %s\n", shopsRange.toString()));
                    }
                    Set<CashRegisterInfo> cashRegisterSet = getCashRegisterSet(entry, false);
                    if(!cashRegisterSet.isEmpty()) {
                        StringBuilder cashesRange = new StringBuilder();
                        for (CashRegisterInfo cashRegister : cashRegisterSet) {
                            cashesRange.append((cashesRange.length() == 0) ? "" : ",").append(cashRegister.number);
                        }
                        writer.write(String.format("%s: %s\n", cashRegisterSet.size() == 1 ? "cash" : "cashesRange", cashesRange.toString()));
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
        sendSalesLogger.info("Kristal10: Finish Reading started");
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        Integer cleanOldFilesDays = kristalSettings == null ? null : kristalSettings.getCleanOldFilesDays();
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);

            try {
                Calendar calendar = Calendar.getInstance();
                String directory = f.getParent() + "/success-" + new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime()) + "/";
                if(cleanOldFilesDays != null) {
                    calendar.add(Calendar.DATE, -cleanOldFilesDays);
                    String oldDirectory = f.getParent() + "/success-" + new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime()) + "/";
                    File oldDir = new File(oldDirectory);
                    File[] files = oldDir.listFiles();
                    if(files != null) {
                        for (File file : files) {
                            if (!file.delete())
                                file.deleteOnExit();
                        }
                    }
                    if(!oldDir.delete())
                        oldDir.deleteOnExit();
                }
                if (new File(directory).exists() || new File(directory).mkdirs())
                    FileCopyUtils.copy(f, new File(directory + f.getName()));
            } catch (IOException e) {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be copied to success files", e);
            }

            if (f.delete()) {
                sendSalesLogger.info("Kristal10: file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        boolean ignoreFileLocks = kristalSettings != null && kristalSettings.getIgnoreFileLock() != null && kristalSettings.getIgnoreFileLock();

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

            File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return (pathname.getName().startsWith("cash_in") || pathname.getName().startsWith("cash_out")) && pathname.getPath().endsWith(".xml");
                }
            });

            if (filesList == null || filesList.length == 0)
                sendSalesLogger.info("Kristal10: No cash documents found in " + exchangeDirectory);
            else {
                sendSalesLogger.info("Kristal10: found " + filesList.length + " file(s) in " + exchangeDirectory);

                for (File file : filesList) {
                    try {
                        String fileName = file.getName();
                        sendSalesLogger.info("Kristal10: reading " + fileName);
                        if (!ignoreFileLocks && isFileLocked(file)) {
                            sendSalesLogger.info("Kristal10: " + fileName + " is locked");
                        } else {
                            SAXBuilder builder = new SAXBuilder();

                            Document document = builder.build(file);
                            Element rootNode = document.getRootElement();

                            boolean cashIn = file.getName().startsWith("cash_in");

                            List cashDocumentsList = rootNode.getChildren(cashIn ? "introduction" : "withdrawal");

                            for (Object cashDocumentNode : cashDocumentsList) {

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

                                cashDocumentList.add(new CashDocument(numberCashDocument, numberCashDocument, dateCashDocument, timeCashDocument,
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
        sendSalesLogger.info("Kristal10: Finish ReadingCashDocumentInfo started");
        for (String readFile : cashDocumentBatch.readFiles) {
            File f = new File(readFile);
            if (f.delete()) {
                sendSalesLogger.info("Kristal10: file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {

        //из-за временного решения с весовыми товарами для этих весовых товаров стоп-листы работать не будут
        processStopListLogger.info("Kristal10: Send StopList # " + stopListInfo.number);

        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        boolean useShopIndices = kristalSettings == null || kristalSettings.getUseShopIndices() != null && kristalSettings.getUseShopIndices();
        boolean idItemInMarkingOfTheGood = kristalSettings == null || kristalSettings.isIdItemInMarkingOfTheGood() != null && kristalSettings.isIdItemInMarkingOfTheGood();
        boolean skipWeightPrefix = kristalSettings != null && kristalSettings.getSkipWeightPrefix() != null && kristalSettings.getSkipWeightPrefix();
        List<String> tobaccoGroups = getTobaccoGroups(kristalSettings != null ? kristalSettings.getTobaccoGroup() : null);

        for (String directory : directorySet) {

            if (stopListInfo.dateFrom == null || stopListInfo.timeFrom == null) {
                String error = "Kristal10: Error! Start DateTime not specified for stopList " + stopListInfo.number;
                processStopListLogger.error(error);
                throw new RuntimeException(error);
            }

            if (stopListInfo.dateTo == null || stopListInfo.timeTo == null) {
                stopListInfo.dateTo = new Date(2040 - 1900, 0, 1);
                stopListInfo.timeTo = new Time(23, 59, 59);
            }

            String exchangeDirectory = directory + "/products/source/";

            if (!new File(exchangeDirectory).exists())
                new File(exchangeDirectory).mkdirs();

            Element rootElement = new Element("goods-catalog");
            Document doc = new Document(rootElement);
            doc.setRootElement(rootElement);

            if (!stopListInfo.exclude) {
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
                                    stockSet.add(((CashRegisterInfo) machineryInfo).section);
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
                        Set<Integer> nppGroupMachinerySet = new HashSet<>();
                        for (MachineryInfo machineryInfo : machineryInfoSet) {
                            if (machineryInfo instanceof CashRegisterInfo)
                                nppGroupMachinerySet.add(((CashRegisterInfo) machineryInfo).overDepartNumber != null ? ((CashRegisterInfo) machineryInfo).overDepartNumber : ((CashRegisterInfo) machineryInfo).numberGroup);
                        }
                        noPriceEntry = nppGroupMachinerySet.isEmpty();
                        for (Integer number : nppGroupMachinerySet) {

                            //parent: good
                            Element priceEntry = new Element("price-entry");
                            setAttribute(priceEntry, "price", 1);
                            setAttribute(priceEntry, "deleted", "true");
                            addStringElement(priceEntry, "begin-date", formatDate(stopListInfo.dateFrom, "yyyy-MM-dd"));
                            addStringElement(priceEntry, "number", "1");
                            good.addContent(priceEntry);

                            //parent: priceEntry
                            Element department = new Element("department");
                            setAttribute(department, "number", number);
                            priceEntry.addContent(department);
                        }
                    }
                    if(noPriceEntry) {
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

                if (!stopListInfo.stopListItemMap.isEmpty()) {
                    XMLOutputter xmlOutput = new XMLOutputter();
                    xmlOutput.setFormat(Format.getPrettyFormat().setEncoding(encoding));

                    PrintWriter fw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(makeExportFile(exchangeDirectory, "catalog-goods")), encoding));
                    xmlOutput.output(doc, fw);
                    fw.close();
                }
            }
        }
    }

    @Override
    public boolean sendDeleteBarcodeInfo(DeleteBarcodeInfo deleteBarcodeInfo) throws IOException {

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

        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        Map<Double, String> discountCardPercentTypeMap = kristalSettings != null ? kristalSettings.getDiscountCardPercentTypeMap() : new HashMap<>();
        String discountCardDirectory = kristalSettings != null ? kristalSettings.getDiscountCardDirectory() : null;

        if (!discountCardList.isEmpty()) {
            for (String directory : getDirectorySet(requestExchange)) {

                String exchangeDirectory = directory + (discountCardDirectory != null ? discountCardDirectory : "/products/source/");
                if (new File(exchangeDirectory).exists() || new File(exchangeDirectory).mkdirs()) {
                    machineryExchangeLogger.info(String.format("Kristal10: Send DiscountCards to %s", exchangeDirectory));

                    Element rootElement = new Element("cards-catalog");
                    Document doc = new Document(rootElement);
                    doc.setRootElement(rootElement);

                    Date currentDate = new Date(Calendar.getInstance().getTime().getTime());

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
                                        d.dateFromDiscountCard == null || currentDate.compareTo(d.dateFromDiscountCard) > 0 ? "ACTIVE" : "BLOCKED");
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
//                                setAttribute(client, "city", d.cityContact);
//                                setAttribute(client, "street", d.streetContact);
//                                setAttribute(client, "mobile-phone", d.phoneContact);
//                                setAttribute(client, "email", d.emailContact);
//                                if(d.agreeSubscribeContact)
//                                    setAttribute(client, "send-by-email", true);
                                setAttribute(client, "isCompleted", d.isCompleted);
                                internalCard.addContent(client);

                                rootElement.addContent(internalCard);
                            }
                        }
                    }
                    exportXML(doc, exchangeDirectory, "catalog-cards");
                }
            }
        }
    }

    private String formatDate(Date date, String format) {
        return date == null ? null : new SimpleDateFormat(format).format(date);
    }


    private String formatDateTime(Timestamp dateTime, String format, String defaultValue) {
        return dateTime == null ? defaultValue : new SimpleDateFormat(format).format(dateTime);
    }

    private String currentDate() {
        return new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime()) + "T00:00:00";
    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException, ClassNotFoundException {

        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        String transformUPCBarcode = kristalSettings == null ? null : kristalSettings.getTransformUPCBarcode();
        Integer maxFilesCount = kristalSettings == null ? null : kristalSettings.getMaxFilesCount();
        boolean ignoreSalesWeightPrefix = kristalSettings == null || kristalSettings.getIgnoreSalesWeightPrefix() != null && kristalSettings.getIgnoreSalesWeightPrefix();
        boolean useShopIndices = kristalSettings != null && kristalSettings.getUseShopIndices() != null && kristalSettings.getUseShopIndices();
        boolean ignoreSalesDepartmentNumber = kristalSettings != null && kristalSettings.getIgnoreSalesDepartmentNumber() != null && kristalSettings.getIgnoreSalesDepartmentNumber();
        boolean ignoreFileLocks = kristalSettings != null && kristalSettings.getIgnoreFileLock() != null && kristalSettings.getIgnoreFileLock();
        boolean useNumberGroupInShopIndices = kristalSettings != null && kristalSettings.getUseNumberGroupInShopIndices() != null && kristalSettings.getUseNumberGroupInShopIndices();
        String giftCardRegexp = kristalSettings != null ? kristalSettings.getGiftCardRegexp() : null;
        if(giftCardRegexp == null)
            giftCardRegexp = "(?!666)\\d{3}";

        Map<String, Integer> directoryDepartNumberGroupCashRegisterMap = new HashMap<>();
        Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
        Map<String, String> directoryWeightCodeMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.directory != null) {
                String idDepartmentStore = useNumberGroupInShopIndices ? String.valueOf(c.numberGroup) : c.idDepartmentStore;
                String key = c.directory + "_" + c.number + (ignoreSalesDepartmentNumber ? "" : ("_" + c.overDepartNumber)) + (useShopIndices ? ("_" + idDepartmentStore) : "");
                directoryDepartNumberGroupCashRegisterMap.put(key, c.numberGroup);
                if (c.number != null)
                    directoryCashRegisterMap.put(c.directory + "_" + c.number, c);
                if (c.weightCodeGroupCashRegister != null)
                    directoryWeightCodeMap.put(key, c.weightCodeGroupCashRegister);
            }
        }

        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<String> filePathList = new ArrayList<>();

        String exchangeDirectory = directory + "/reports/";

        File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().startsWith("purchases") && pathname.getPath().endsWith(".xml");
            }
        });
        
        Set<String> ids = new HashSet<>();
        Set<String> usedBarcodes = new HashSet<>();
        if (filesList == null || filesList.length == 0)
            sendSalesLogger.info("Kristal10: No checks found in " + exchangeDirectory);
        else {
            if(maxFilesCount == null)
                sendSalesLogger.info(String.format("Kristal10: found %s file(s) in %s", filesList.length, exchangeDirectory));
            else
                sendSalesLogger.info(String.format("Kristal10: found %s file(s) in %s, will read %s file(s)", filesList.length, exchangeDirectory, Math.min(filesList.length, maxFilesCount)));

            Arrays.sort(filesList, new Comparator<File>() {
                public int compare(File f1, File f2) {
                    return Long.compare(f1.lastModified(), f2.lastModified());
                }
            });

            int filesCount = 0;
            for (File file : filesList) {
                filesCount++;
                if(maxFilesCount != null && maxFilesCount < filesCount)
                    break;
                try {
                    String fileName = file.getName();
                    sendSalesLogger.info("Kristal10: reading " + fileName);
                    if (!ignoreFileLocks && isFileLocked(file)) {
                        sendSalesLogger.info("Kristal10: " + fileName + " is locked");
                    } else {
                        SAXBuilder builder = new SAXBuilder();

                        Document document = builder.build(file);
                        Element rootNode = document.getRootElement();
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

                            long dateTimeReceipt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"/*.SSSX"*/).parse(readStringXMLAttribute(purchaseNode, "saletime")).getTime();
                            Date dateReceipt = new Date(dateTimeReceipt);
                            Time timeReceipt = new Time(dateTimeReceipt);

                            CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory + "_" + numberCashRegister);
                            Date startDate = cashRegister == null ? null : cashRegister.startDate;

                            BigDecimal sumCard = BigDecimal.ZERO;
                            BigDecimal sumCash = BigDecimal.ZERO;
                            BigDecimal sumGiftCard = BigDecimal.ZERO;
                            Map<String, GiftCard> sumGiftCardMap = new HashMap<>();
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
                            List discountCardsList = purchaseNode.getChildren("discountCards");
                            for (Object discountCardNode : discountCardsList) {
                                List discountCardList = ((Element) discountCardNode).getChildren("discountCard");
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

                            List positionsList = purchaseNode.getChildren("positions");
                            List<SalesInfo> currentSalesInfoList = new ArrayList<>();
                            BigDecimal currentPaymentSum = BigDecimal.ZERO;


                            for (Object positionNode : positionsList) {

                                List positionEntryList = ((Element) positionNode).getChildren("position");

                                int count = 1;
                                String departNumber = null;
                                for (Object positionEntryNode : positionEntryList) {

                                    if (departNumber == null)
                                        departNumber = readStringXMLAttribute(positionEntryNode, "departNumber");

                                    String key = directory + "_" + numberCashRegister + (ignoreSalesDepartmentNumber ? "" : ("_" + departNumber)) + (useShopIndices ? ("_" + shop) : "");

                                    String weightCode = directoryWeightCodeMap.containsKey(key) ? directoryWeightCodeMap.get(key) : "21";

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

                                    if (startDate == null || dateReceipt.compareTo(startDate) >= 0) {
                                        Integer nppGroupMachinery = directoryDepartNumberGroupCashRegisterMap.get(key);
                                        nppGroupMachinery = (nppGroupMachinery != null || useShopIndices) ? nppGroupMachinery : (cashRegister == null ? null : cashRegister.numberGroup);
                                        if (nppGroupMachinery == null) {
                                            sendSalesLogger.error("not found nppGroupMachinery : " + key + " { " + directoryDepartNumberGroupCashRegisterMap.toString() + " } : " + directory + "_" + numberCashRegister);
                                        }

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
                                        currentSalesInfoList.add(new SalesInfo(isGiftCard, nppGroupMachinery, numberCashRegister, numberZReport, dateReceipt, timeReceipt,
                                                numberReceipt, dateReceipt, timeReceipt, idEmployee, firstNameEmployee, lastNameEmployee, sumCard, sumCash, sumGiftCardMap,
                                                barcode, idItem, null, idSaleReceiptReceiptReturnDetail, quantity, price, sumReceiptDetail, discountPercentReceiptDetail,
                                                discountSumReceiptDetail, discountSumReceipt, discountCard, numberReceiptDetail, fileName, null, false, cashRegister));
                                    }
                                    count++;
                                }

                            }

                            //чит для случая, когда не указана сумма платежа. Недостающую сумму пишем в наличные.
                            BigDecimal sum = HandlerUtils.safeAdd(sumCard, sumCash);
                            for(GiftCard giftCard : sumGiftCardMap.values()) {
                                sum = HandlerUtils.safeAdd(sum, giftCard.sum);
                            }
                            if (sum == null || sum.compareTo(currentPaymentSum) < 0)
                                for (SalesInfo salesInfo : currentSalesInfoList) {
                                    salesInfo.sumCash = HandlerUtils.safeSubtract(HandlerUtils.safeSubtract(currentPaymentSum, sumCard), sumGiftCard);
                                }

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
                new Kristal10SalesBatch(salesInfoList, filePathList);
    }

    @Override
    public Map<String, List<Object>> readExtraCheckZReport(List<CashRegisterInfo> cashRegisterInfoList) {
        Kristal10Settings kristalSettings = springContext.containsBean("kristal10Settings") ? (Kristal10Settings) springContext.getBean("kristal10Settings") : null;
        boolean ignoreFileLocks = kristalSettings != null && kristalSettings.getIgnoreFileLock() != null && kristalSettings.getIgnoreFileLock();

        Map<String, List<Object>> zReportSumMap = new HashMap<>();

        Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c) && c.directory != null && c.number != null) {
                directoryCashRegisterMap.put(c.directory + "_" + c.number, c);
            }
        }

        for (String directory : directoryCashRegisterMap.keySet()) {

            String exchangeDirectory = directory + "/reports/";

            File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getName().startsWith("zreports") && pathname.getPath().endsWith(".xml");
                }
            });

            if (filesList != null && filesList.length > 0) {
                sendSalesLogger.info("Kristal10: found " + filesList.length + " z-report(s) in " + exchangeDirectory);

                for (File file : filesList) {
                    try {
                        String fileName = file.getName();
                        sendSalesLogger.info("Kristal10: reading " + fileName);
                        if (!ignoreFileLocks && isFileLocked(file)) {
                            sendSalesLogger.info("Kristal10: " + fileName + " is locked");
                        } else {
                            SAXBuilder builder = new SAXBuilder();

                            Document document = builder.build(file);
                            Element rootNode = document.getRootElement();
                            List zReportsList = rootNode.getChildren("zreport");

                            for (Object zReportNode : zReportsList) {

                                Integer numberCashRegister = readIntegerXMLValue(zReportNode, "cashNumber");
                                CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory + "_" + numberCashRegister);
                                Integer numberGroupCashRegister = cashRegister == null ? null : cashRegister.numberGroup;

                                String numberZReport = readStringXMLValue(zReportNode, "shiftNumber");
                                String idZReport = numberGroupCashRegister + "_" + numberCashRegister + "_" + numberZReport;

                                BigDecimal sumSale = readBigDecimalXMLValue(zReportNode, "amountByPurchaseFiscal");
                                BigDecimal sumReturn = readBigDecimalXMLValue(zReportNode, "amountByReturnFiscal");
                                BigDecimal kristalSum = HandlerUtils.safeSubtract(sumSale, sumReturn);
                                zReportSumMap.put(idZReport, Arrays.asList((Object) kristalSum, numberCashRegister, numberZReport, idZReport));

                            }
                            String dir = file.getParent() + "/success-" + new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime()) + "/";
                            File successDir = new File(dir);
                            if (successDir.exists() || successDir.mkdirs())
                                FileCopyUtils.copy(file, new File(dir + file.getName()));
                            if(!file.delete())
                                file.deleteOnExit();
                        }
                    } catch (Throwable e) {
                        sendSalesLogger.error("File: " + file.getAbsolutePath(), e);
                    }
                }
            }
        }
        return zReportSumMap.isEmpty() ? null : zReportSumMap;
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

    private File exportXML(Document doc, String exchangeDirectory, String prefix) throws IOException {
        File file = makeExportFile(exchangeDirectory, prefix);
        XMLOutputter xmlOutput = new XMLOutputter();
        xmlOutput.setFormat(Format.getPrettyFormat().setEncoding(encoding));
        PrintWriter fw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
        xmlOutput.output(doc, fw);
        fw.close();
        return file;
    }

    private String transformBarcode(String idBarcode, String weightCode, boolean passScalesItem, boolean skipWeightPrefix) {
        //временное решение для весовых товаров
        return passScalesItem && idBarcode.length() <= 6 && weightCode != null && !skipWeightPrefix ? (weightCode + idBarcode) : idBarcode;
    }

    private String transformUPCBarcode(String idBarcode, String transformUPCBarcode) {
        if(idBarcode != null && transformUPCBarcode != null) {
            if(transformUPCBarcode.equals("13to12") && idBarcode.length() == 13 && idBarcode.startsWith("0"))
                idBarcode = idBarcode.substring(1);
            else if(transformUPCBarcode.equals("12to13") && idBarcode.length() == 12)
                idBarcode += "0";

        }
        return idBarcode;
    }

    private String readStringXMLValue(Object element, String field) {
        if (element == null || !(element instanceof Element))
            return null;
        String value = ((Element) element).getChildText(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        return value;
    }

    private String readStringXMLAttribute(Object element, String field) {
        if (element == null || !(element instanceof Element))
            return null;
        String value = ((Element) element).getAttributeValue(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        return value;
    }

    private BigDecimal readBigDecimalXMLValue(Object element, String field) {
        if (element == null || !(element instanceof Element))
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

    private BigDecimal readBigDecimalXMLAttribute(Object element, String field) {
        if (element == null || !(element instanceof Element))
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

    private Integer readIntegerXMLValue(Object element, String field) {
        if (element == null || !(element instanceof Element))
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

    private Integer readIntegerXMLAttribute(Object element, String field) {
        if (element == null || !(element instanceof Element))
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

    private double parseWeight(String value) {
        try {
            return (double) Integer.parseInt(value) / 1000;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static boolean isFileLocked(File file) {
        boolean isLocked = false;
        FileChannel channel = null;
        FileLock lock = null;
        try {
            channel = new RandomAccessFile(file, "rw").getChannel();
            lock = channel.tryLock();
            if (lock == null)
                isLocked = true;
        } catch (Exception e) {
            sendSalesLogger.info(e);
            isLocked = true;
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (Exception e) {
                    sendSalesLogger.info(e);
                    isLocked = true;
                }
            }
            if (channel != null)
                try {
                    channel.close();
                } catch (IOException e) {
                    sendSalesLogger.info(e);
                    isLocked = true;
                }
        }
        return isLocked;
    }

    protected boolean notNullNorEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    private class DeleteBarcode {
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
