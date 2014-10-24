package equ.clt.handler.kristal10;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Kristal10Handler extends CashRegisterHandler<Kristal10SalesBatch> {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSaleslogger");
    
    String weightPrefix = "21";

    public Kristal10Handler() {
    }

    @Override
    public List<MachineryInfo> sendTransaction(TransactionCashRegisterInfo transactionInfo, List<CashRegisterInfo> machineryInfoList) throws IOException {

        processTransactionLogger.info("Kristal: Send Transaction # " + transactionInfo.id);

        List<String> directoriesList = new ArrayList<String>();
        for (CashRegisterInfo cashRegisterInfo : machineryInfoList) {
            if ((cashRegisterInfo.port != null) && (!directoriesList.contains(cashRegisterInfo.port.trim())))
                directoriesList.add(cashRegisterInfo.port.trim());
            if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory.trim())))
                directoriesList.add(cashRegisterInfo.directory.trim());
        }

        for (String directory : directoriesList) {

            String exchangeDirectory = directory.trim() + "/products/source/";
            
            if(!new File(exchangeDirectory).exists())
                new File(exchangeDirectory).mkdirs();
            
            //catalog-goods.xml
            processTransactionLogger.info("Kristal: creating catalog-goods file");

            Element rootElement = new Element("goods-catalog");
            Document doc = new Document(rootElement);
            doc.setRootElement(rootElement);

            for (CashRegisterItemInfo item : transactionInfo.itemsList) {

                //parent: rootElement
                Element good = new Element("good");
                //временное решение для весовых товаров
                String barcodeItem = item.splitItem ? (weightPrefix + item.idBarcode) : item.idBarcode;
                setAttribute(good, "marking-of-the-good", item.idBarcode);
                rootElement.addContent(good);

                addStringElement(good, "name", item.name);

                //parent: good
                Element barcode = new Element("bar-code");
                setAttribute(barcode, "code", barcodeItem);
                addStringElement(barcode, "default-code", "true");
                good.addContent(barcode);

                String productType;
                if (item.passScalesItem)
                    productType = item.splitItem ? "ProductWeightEntity" : "ProductPieceWeightEntity";
                else
                    productType = "ProductPieceEntity";
                addStringElement(good, "product-type", productType);

                //parent: good
                Element priceEntry = new Element("price-entry");
                setAttribute(priceEntry, "price", item.price == null ? null : item.price.intValue());
                setAttribute(priceEntry, "deleted", "false");
                addStringElement(priceEntry, "begin-date", "2001-01-01T00:00:00");
                addStringElement(priceEntry, "number", "1");
                good.addContent(priceEntry);

                addStringElement(good, "vat", "20");

                //parent: priceEntry
                Element department = new Element("department");
                setAttribute(department, "number", transactionInfo.nppGroupCashRegister);
                addStringElement(department, "name", transactionInfo.nameGroupCashRegister == null ? "Отдел" : transactionInfo.nameGroupCashRegister);
                priceEntry.addContent(department);

                //parent: good
                Element group = new Element("group");
                setAttribute(group, "id", item.idItemGroup);
                addStringElement(group, "name", item.nameItemGroup);
                good.addContent(group);

                List<ItemGroup> hierarchyItemGroup = transactionInfo.itemGroupMap.get(item.idItemGroup);
                if(hierarchyItemGroup != null)
                    addHierarchyItemGroup(group, hierarchyItemGroup.subList(1, hierarchyItemGroup.size()));

                //parent: good
                if (item.idUOM == null || item.shortNameUOM == null) {
                    String error = "Kristal: Error! UOM not specified for item with barcode " + barcodeItem;
                    processTransactionLogger.error(error);
                    throw Throwables.propagate(new RuntimeException(error));
                }
                Element measureType = new Element("measure-type");
                setAttribute(measureType, "id", item.idUOM);
                addStringElement(measureType, "name", item.shortNameUOM);
                good.addContent(measureType);

                addStringElement(good, "delete-from-cash", "false");


            }

            String filePath = exchangeDirectory + "//" + makeGoodsFilePath() + ".xml";
            XMLOutputter xmlOutput = new XMLOutputter();
            xmlOutput.setFormat(Format.getPrettyFormat().setEncoding("windows-1251"));
            PrintWriter fw = new PrintWriter(
                                new OutputStreamWriter(
                                    new FileOutputStream(filePath), "windows-1251"));
            xmlOutput.output(doc, fw);
            fw.close();
            
            waitForDeletion(new File(filePath));
        }
        return null;
    }

    private void waitForDeletion(File file) {
        int count = 0;
        while (file.exists()) {
            try {
                count++;
                if(count>=60) {
                    processTransactionLogger.info(String.format("Kristal: still waiting for deletion of file %s", file.getAbsolutePath()));
                    count = 0;
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
    }
    
    private String makeGoodsFilePath() {
        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy'T'HH-mm-ss");
        Calendar cal = Calendar.getInstance();
        return "catalog-goods_" + dateFormat.format(cal.getTime());
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
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {
    }

    @Override
    public String requestSalesInfo(List<RequestExchange> requestExchangeList) throws IOException, ParseException {
        for (RequestExchange entry : requestExchangeList) {
            if(entry.isSalesInfoExchange()) {
                sendSalesLogger.info("Kristal: creating request files");
                for (String directory : entry.directorySet) {

                    String dateFrom = new SimpleDateFormat("dd.MM.yyyy").format(entry.dateFrom);
                    String dateTo = new SimpleDateFormat("dd.MM.yyyy").format(entry.dateTo);

                    String exchangeDirectory = directory + "/reports/source/";

                    if (new File(exchangeDirectory).exists() || new File(exchangeDirectory).mkdirs()) {
                        Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exchangeDirectory + "reports.request"), "windows-1251"));
                        writer.write(String.format("dateRange: %s-%s\nreport: purchases", dateFrom, dateTo));
                        writer.close();
                    } else
                        return "Error: " + exchangeDirectory + " doesn't exist. Request creation failed.";
                }
            }
        }
        return null;
    }

    @Override
    public void finishReadingSalesInfo(Kristal10SalesBatch salesBatch) {
        sendSalesLogger.info("Kristal: Finish Reading started");
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);

            try {
                File successDir = new File(f.getParent() + "/success/");
                if (successDir.exists() || successDir.mkdirs())
                    FileCopyUtils.copy(f, new File(f.getParent() + "/success/" + f.getName()));
            } catch (IOException e) {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be copied to success files", e);
            }
            
            if (f.delete()) {
                sendSalesLogger.info("Kristal: file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public Map<String, Timestamp> requestSucceededSoftCheckInfo(Set<String> directorySet) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public String checkZReportSum(Map<String, BigDecimal> zReportSumMap, List<String> idCashRegisterList) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException {
        Set<String> directorySet = new HashSet<String>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.directory != null && c.handlerModel.endsWith("Kristal10Handler"))
                directorySet.add(c.directory);
        }

        List<CashDocument> cashDocumentList = new ArrayList<CashDocument>();
        List<String> readFiles = new ArrayList<String>();
        for (String directory : directorySet) {

            String exchangeDirectory = directory + "/reports/";

            File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return (pathname.getName().startsWith("cash_in") || pathname.getName().startsWith("cash_out")) && pathname.getPath().endsWith(".xml");
                }
            });

            if (filesList == null || filesList.length == 0)
                sendSalesLogger.info("Kristal: No cash documents found in " + exchangeDirectory);
            else {
                sendSalesLogger.info("Kristal: found " + filesList.length + " file(s) in " + exchangeDirectory);

                for (File file : filesList) {
                    try {
                        String fileName = file.getName();
                        sendSalesLogger.info("Kristal: reading " + fileName);
                        if (isFileLocked(file)) {
                            sendSalesLogger.info("Kristal: " + fileName + " is locked");
                        } else {
                            SAXBuilder builder = new SAXBuilder();

                            Document document = builder.build(file);
                            Element rootNode = document.getRootElement();

                            boolean cashIn = file.getName().startsWith("cash_in");

                            List cashDocumentsList = rootNode.getChildren(cashIn ? "introduction" : "withdrawal");

                            for (Object cashDocumentNode : cashDocumentsList) {

                                String numberCashDocument = readStringXMLAttribute(cashDocumentNode, "number");
                                Integer numberCashRegister = readIntegerXMLAttribute(cashDocumentNode, "cash");
                                BigDecimal sumCashDocument = readBigDecimalXMLAttribute(cashDocumentNode, "amount");
                                if(!cashIn)
                                    sumCashDocument = sumCashDocument == null ? null : sumCashDocument.negate();

                                long dateTimeCashDocument = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(readStringXMLAttribute(cashDocumentNode, "regtime")).getTime();
                                Date dateCashDocument = new Date(dateTimeCashDocument);
                                Time timeCashDocument = new Time(dateTimeCashDocument);

                                cashDocumentList.add(new CashDocument(numberCashDocument, dateCashDocument, timeCashDocument,
                                        numberCashRegister, sumCashDocument));
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
        sendSalesLogger.info("Kristal: Finish ReadingCashDocumentInfo started");
        for (String readFile : cashDocumentBatch.readFiles) {
            File f = new File(readFile);
            if (f.delete()) {
                sendSalesLogger.info("Kristal: file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {
        //из-за временного решения с весовыми товарами для этих весовых товаров стоп-листы работать не будут
        processStopListLogger.info("Kristal: Send StopList # " + stopListInfo.number);

        for (String directory : directorySet) {

            String exchangeDirectory = directory.trim() + "/products/source/";

            if(!new File(exchangeDirectory).exists())
                new File(exchangeDirectory).mkdirs();

            Element rootElement = new Element("goods-catalog");
            Document doc = new Document(rootElement);
            doc.setRootElement(rootElement);

            for (String idBarcode : stopListInfo.stopListItemList) {
                
                //parent: rootElement
                Element saleDeniedRestriction = new Element("sale-denied-restriction");
                setAttribute(saleDeniedRestriction, "id", stopListInfo.number + idBarcode);
                setAttribute(saleDeniedRestriction, "subject-type", "GOOD");
                setAttribute(saleDeniedRestriction, "subject-code", idBarcode);
                setAttribute(saleDeniedRestriction, "type", "SALE_DENIED");
                setAttribute(saleDeniedRestriction, "value", true);
                rootElement.addContent(saleDeniedRestriction);
                
                //parent: saleDeniedRestriction
                if(stopListInfo.dateFrom == null || stopListInfo.timeFrom == null) {
                    String error = "Kristal: Error! Start DateTime not specified for stopList " + stopListInfo.number;
                    processStopListLogger.error(error);
                    throw Throwables.propagate(new RuntimeException(error));
                }
                if(stopListInfo.dateTo == null || stopListInfo.timeTo == null) {
                    String error = "Kristal: Error! End DateTime not specified for stopList " + stopListInfo.number;
                    processStopListLogger.error(error);
                    throw Throwables.propagate(new RuntimeException(error));
                }
                addStringElement(saleDeniedRestriction, "since-date", formatDate(stopListInfo.dateFrom));
                addStringElement(saleDeniedRestriction, "till-date", formatDate(stopListInfo.dateTo));
                addStringElement(saleDeniedRestriction, "since-time", formatTime(stopListInfo.timeFrom));
                addStringElement(saleDeniedRestriction, "till-time", formatTime(stopListInfo.timeTo));
                addStringElement(saleDeniedRestriction, "deleted", "false");

            }

            XMLOutputter xmlOutput = new XMLOutputter();
            xmlOutput.setFormat(Format.getPrettyFormat().setEncoding("windows-1251"));
            PrintWriter fw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(exchangeDirectory + "//" + makeGoodsFilePath() + ".xml"), "windows-1251"));
            xmlOutput.output(doc, fw);
            fw.close();
            
            //чит для избежания ситуации, совпадения имён у двух файлов ограничений продаж (в основе имени - текущее время с точностью до секунд)
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, Set<String> directory) throws IOException {
    }

    @Override
    public void sendPromotionInfo(PromotionInfo promotionInfo, Set<String> directory) throws IOException {        
    }

    private String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(date);
    }

    private String formatTime(Time time) {
        return new SimpleDateFormat("HH:mm:ss").format(time);
    }

    @Override
    public SalesBatch readSalesInfo(List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException, ClassNotFoundException {

        Set<String> directorySet = new HashSet<String>();
        Map<String, Integer> directoryGroupCashRegisterMap = new HashMap<String, Integer>();
        Map<String, Date> directoryStartDateMap = new HashMap<String, Date>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.directory != null && c.handlerModel.endsWith("Kristal10Handler"))
                directorySet.add(c.directory);
            if (c.directory != null && c.number != null && c.numberGroup != null)
                directoryGroupCashRegisterMap.put(c.directory + "_" + c.number, c.numberGroup);
            if (c.directory != null && c.number != null && c.startDate != null)
                directoryStartDateMap.put(c.directory + "_" + c.number, c.startDate);
        }

        List<SalesInfo> salesInfoList = new ArrayList<SalesInfo>();
        List<String> filePathList = new ArrayList<String>();
        for (String directory : directorySet) {

            String exchangeDirectory = directory + "/reports/";

            File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getName().startsWith("purchases") && pathname.getPath().endsWith(".xml");
                }
            });

            if (filesList == null || filesList.length == 0)
                sendSalesLogger.info("Kristal: No checks found in " + exchangeDirectory);
            else {
                sendSalesLogger.info("Kristal: found " + filesList.length + " file(s) in " + exchangeDirectory);

                for (File file : filesList) {
                    try {
                        String fileName = file.getName();
                        sendSalesLogger.info("Kristal: reading " + fileName);
                        if (isFileLocked(file)) {
                            sendSalesLogger.info("Kristal: " + fileName + " is locked");
                        } else {
                            SAXBuilder builder = new SAXBuilder();

                            Document document = builder.build(file);
                            Element rootNode = document.getRootElement();
                            List purchasesList = rootNode.getChildren("purchase");

                            for (Object purchaseNode : purchasesList) {

                                String operationType = readStringXMLAttribute(purchaseNode, "operationType");
                                Boolean isSale = operationType == null || operationType.equals("true");
                                Integer numberCashRegister = readIntegerXMLAttribute(purchaseNode, "cash");
                                String numberZReport = readStringXMLAttribute(purchaseNode, "shift");
                                Integer numberReceipt = readIntegerXMLAttribute(purchaseNode, "number");
                                String idEmployee = readStringXMLAttribute(purchaseNode, "tabNumber");
                                String nameEmployee = readStringXMLAttribute(purchaseNode, "userName");
                                String firstNameEmployee = null;
                                String lastNameEmployee = null;
                                if(nameEmployee != null) {
                                    String[] splittedNameEmployee = nameEmployee.split(" ");
                                    lastNameEmployee = splittedNameEmployee[0];
                                    for (int i = 1; i< splittedNameEmployee.length; i++) {
                                        firstNameEmployee = firstNameEmployee == null ? splittedNameEmployee[i] : (firstNameEmployee + " " + splittedNameEmployee[i]);
                                    }
                                }
                                BigDecimal discountSumReceipt = readBigDecimalXMLAttribute(purchaseNode, "discountAmount");
                                //discountSumReceipt = (discountSumReceipt != null && !isSale) ? discountSumReceipt.negate() : discountSumReceipt;

                                long dateTimeReceipt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX").parse(readStringXMLAttribute(purchaseNode, "saletime")).getTime();
                                Date dateReceipt = new Date(dateTimeReceipt);
                                Time timeReceipt = new Time(dateTimeReceipt);

                                BigDecimal sumCard = BigDecimal.ZERO;
                                BigDecimal sumCash = BigDecimal.ZERO;
                                BigDecimal sumGiftCard = BigDecimal.ZERO;
                                List paymentsList = ((Element) purchaseNode).getChildren("payments");
                                for (Object paymentNode : paymentsList) {

                                    List paymentEntryList = ((Element) paymentNode).getChildren("payment");
                                    for (Object paymentEntryNode : paymentEntryList) {
                                        String paymentType = readStringXMLAttribute(paymentEntryNode, "typeClass");
                                        if (paymentType != null) {
                                            BigDecimal sum = readBigDecimalXMLAttribute(paymentEntryNode, "amount");
                                            sum = (sum != null && !isSale) ? sum.negate() : sum;
                                            if (paymentType.equals("CashPaymentEntity")) {
                                                sumCash = safeAdd(sumCash, sum);
                                            } else if (paymentType.equals("CashChangePaymentEntity")) {
                                                sumCash = safeSubtract(sumCash, sum);
                                            } else if (paymentType.equals("ExternalBankTerminalPaymentEntity")) {
                                                sumCard = safeAdd(sumCard, sum);
                                            } else if (paymentType.equals("GiftCardPaymentEntity")) {
                                                sumGiftCard = safeAdd(sumGiftCard, sum);
                                            }
                                        }
                                    }
                                }

                                List positionsList = ((Element) purchaseNode).getChildren("positions");
                                List<SalesInfo> currentSalesInfoList = new ArrayList<SalesInfo>();
                                BigDecimal currentPaymentSum = BigDecimal.ZERO;


                                for (Object positionNode : positionsList) {

                                    List positionEntryList = ((Element) positionNode).getChildren("position");

                                    for (Object positionEntryNode : positionEntryList) {

                                        String barcode = readStringXMLAttribute(positionEntryNode, "barCode");
                                        //временное решение для весовых товаров
                                        if(barcode != null && barcode.startsWith(weightPrefix))
                                            barcode = barcode.substring(2);
                                        BigDecimal quantity = readBigDecimalXMLAttribute(positionEntryNode, "count");
                                        quantity = (quantity != null && !isSale) ? quantity.negate() : quantity;
                                        BigDecimal price = readBigDecimalXMLAttribute(positionEntryNode, "cost");
                                        BigDecimal sumReceiptDetail = readBigDecimalXMLAttribute(positionEntryNode, "amount");
                                        sumReceiptDetail = (sumReceiptDetail != null && !isSale) ? sumReceiptDetail.negate() : sumReceiptDetail;
                                        currentPaymentSum = safeAdd(currentPaymentSum, sumReceiptDetail);
                                        BigDecimal discountSumReceiptDetail = readBigDecimalXMLAttribute(positionEntryNode, "discountValue");
                                        //discountSumReceiptDetail = (discountSumReceiptDetail != null && !isSale) ? discountSumReceiptDetail.negate() : discountSumReceiptDetail; 
                                        Integer numberReceiptDetail = readIntegerXMLAttribute(positionEntryNode, "order");

                                        Date startDate = directoryStartDateMap.get(directory + "_" + numberCashRegister);
                                        if (startDate == null || dateReceipt.compareTo(startDate) >= 0)
                                            currentSalesInfoList.add(new SalesInfo(directoryGroupCashRegisterMap.get(directory + "_" + numberCashRegister), numberCashRegister,
                                                    numberZReport, numberReceipt, dateReceipt, timeReceipt, idEmployee, firstNameEmployee, lastNameEmployee, sumCard, sumCash,
                                                    sumGiftCard, barcode, null, quantity, price, sumReceiptDetail, discountSumReceiptDetail, discountSumReceipt, null, 
                                                    numberReceiptDetail, fileName));
                                    }

                                }

                                //чит для случая, когда не указана сумма платежа. Недостающую сумму пишем в наличные.
                                BigDecimal sum = safeAdd(safeAdd(sumCard, sumCash), sumGiftCard);
                                if (sum == null || sum.compareTo(currentPaymentSum) < 0)
                                    for (SalesInfo salesInfo : currentSalesInfoList) {
                                        salesInfo.sumCash = safeSubtract(safeSubtract(currentPaymentSum, sumCard), sumGiftCard);
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
        }
        return (salesInfoList.isEmpty() && filePathList.isEmpty()) ? null :
                new Kristal10SalesBatch(salesInfoList, filePathList);
    }

    @Override
    public ExtraCheckZReportBatch extraCheckZReportSum(List<CashRegisterInfo> cashRegisterInfoList, Map<String, BigDecimal> zReportSumMap) {

        String message = "";
        List<String> idZReportList = new ArrayList<String>();
        
        Set<String> directorySet = new HashSet<String>();
        Map<String, Integer> directoryGroupCashRegisterMap = new HashMap<String, Integer>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.directory != null && c.handlerModel.endsWith("Kristal10Handler"))
                directorySet.add(c.directory);
            if (c.directory != null && c.number != null && c.numberGroup != null)
                directoryGroupCashRegisterMap.put(c.directory + "_" + c.number, c.numberGroup);
        }

        for (String directory : directorySet) {

            String exchangeDirectory = directory + "/reports/";

            File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getName().startsWith("zreports") && pathname.getPath().endsWith(".xml");
                }
            });

            if (filesList != null && filesList.length > 0) {
                sendSalesLogger.info("Kristal: found " + filesList.length + " z-report(s) in " + exchangeDirectory);

                for (File file : filesList) {
                    try {
                        String fileName = file.getName();
                        sendSalesLogger.info("Kristal: reading " + fileName);
                        if (isFileLocked(file)) {
                            sendSalesLogger.info("Kristal: " + fileName + " is locked");
                        } else {
                            SAXBuilder builder = new SAXBuilder();

                            Document document = builder.build(file);
                            Element rootNode = document.getRootElement();
                            List zReportsList = rootNode.getChildren("zreport");

                            for (Object zReportNode : zReportsList) {

                                Integer numberCashRegister = readIntegerXMLValue(zReportNode, "cashNumber");
                                Integer numberGroupCashRegister = directoryGroupCashRegisterMap.get(directory + "_" + numberCashRegister);
                                String numberZReport = readStringXMLValue(zReportNode, "shiftNumber");
                                String idZReport = numberGroupCashRegister + "_" + numberCashRegister + "_" + numberZReport;

                                BigDecimal sumSale = readBigDecimalXMLValue(zReportNode, "amountByPurchaseFiscal");
                                BigDecimal sumReturn = readBigDecimalXMLValue(zReportNode, "amountByReturnFiscal");
                                BigDecimal kristalSum = safeSubtract(sumSale, sumReturn);
                                BigDecimal fusionSum = zReportSumMap.get(idZReport);
                                
                                if(fusionSum == null || kristalSum == null || fusionSum.doubleValue() != kristalSum.doubleValue())
                                    message += String.format("CashRegister %s. \nZReport %s checksum failed: %s(fusion) != %s(kristal);\n",
                                            numberCashRegister, numberZReport, fusionSum, kristalSum);
                                else 
                                    idZReportList.add(idZReport);
                            }
                            File successDir = new File(file.getParent() + "/success/");
                            if (successDir.exists() || successDir.mkdirs())
                                FileCopyUtils.copy(file, new File(file.getParent() + "/success/" + file.getName()));
                            file.delete();
                        }
                    } catch (Throwable e) {
                        sendSalesLogger.error("File: " + file.getAbsolutePath(), e);
                    }                
                }
            }
        }
        return idZReportList.isEmpty() && message.isEmpty() ? null : new ExtraCheckZReportBatch(idZReportList, message);
    }

    protected BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }

    protected BigDecimal safeSubtract(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else
            return (operand1 == null ? operand2.negate() : (operand2 == null ? operand1 : operand1.subtract((operand2))));
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
            sendSalesLogger.error(e);
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
            sendSalesLogger.error(e);
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
            sendSalesLogger.error(e);
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
            sendSalesLogger.error(e);
            return null;
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
}
