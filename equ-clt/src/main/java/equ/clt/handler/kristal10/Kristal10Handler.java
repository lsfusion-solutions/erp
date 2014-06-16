package equ.clt.handler.kristal10;

import com.google.common.base.Throwables;
import equ.api.SalesBatch;
import equ.api.SalesInfo;
import equ.api.SoftCheckInfo;
import equ.api.cashregister.*;
import equ.clt.EquipmentServer;
import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.*;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Kristal10Handler extends CashRegisterHandler<Kristal10SalesBatch> {

    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);

    public Kristal10Handler() {
    }

    @Override
    public void sendTransaction(TransactionCashRegisterInfo transactionInfo, List<CashRegisterInfo> machineryInfoList) throws IOException {

        logger.info("Kristal: Send Transaction # " + transactionInfo.id);

        List<String> directoriesList = new ArrayList<String>();
        for (CashRegisterInfo cashRegisterInfo : machineryInfoList) {
            if ((cashRegisterInfo.port != null) && (!directoriesList.contains(cashRegisterInfo.port.trim())))
                directoriesList.add(cashRegisterInfo.port.trim());
            if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory.trim())))
                directoriesList.add(cashRegisterInfo.directory.trim());
        }

        for (String directory : directoriesList) {

            String exchangeDirectory = directory.trim() + "\\products\\source\\";
            
            if(!new File(exchangeDirectory).exists())
                new File(exchangeDirectory).mkdirs();
            
            //catalog-goods.xml
            logger.info("Kristal: creating catalog-goods file");

            Element rootElement = new Element("goods-catalog");
            Document doc = new Document(rootElement);
            doc.setRootElement(rootElement);

            for (CashRegisterItemInfo item : transactionInfo.itemsList) {

                //parent: rootElement
                Element good = new Element("good");
                setAttribute(good, "marking-of-the-good", item.idBarcode);
                rootElement.addContent(good);

                addStringElement(good, "name", item.name);

                //parent: good
                Element barcode = new Element("bar-code");
                setAttribute(barcode, "code", item.idBarcode);
                addStringElement(barcode, "default-code", "true");
                good.addContent(barcode);

                String productType = item.isWeightItem ? "ProductWeightEntity" : (item.passScalesItem ? "ProductPieceWeightEntity" : "ProductPieceEntity");
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
                addStringElement(department, "name", "Отдел");
                priceEntry.addContent(department);

                //parent: good
                Element group = new Element("group");
                setAttribute(group, "id", item.hierarchyItemGroup.get(0).idItemGroup);
                addStringElement(group, "name", item.hierarchyItemGroup.get(0).nameItemGroup);
                good.addContent(group);

                addHierarchyItemGroup(group, item.hierarchyItemGroup.subList(1, item.hierarchyItemGroup.size()));

                //parent: good
                if (item.idUOM == null || item.shortNameUOM == null) {
                    String error = "Kristal: Error! UOM not specified for item with barcode " + item.idBarcode;
                    logger.error(error);
                    throw Throwables.propagate(new RuntimeException(error));
                }
                Element measureType = new Element("measure-type");
                setAttribute(measureType, "id", item.idUOM);
                addStringElement(measureType, "name", item.shortNameUOM);
                good.addContent(measureType);

                addStringElement(good, "delete-from-cash", "false");


            }

            DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy'T'HH-mm-ss");
            Calendar cal = Calendar.getInstance();
            String fileName = "catalog-goods_" + dateFormat.format(cal.getTime());

            XMLOutputter xmlOutput = new XMLOutputter();
            xmlOutput.setFormat(Format.getPrettyFormat().setEncoding("windows-1251"));
            PrintWriter fw = new PrintWriter(
                                new OutputStreamWriter(
                                    new FileOutputStream(exchangeDirectory + "//" + fileName + ".xml"), "windows-1251"));
            xmlOutput.output(doc, fw);
            fw.close();
        }
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
    public String requestSalesInfo(Map<Date, Set<String>> requestSalesInfo) throws IOException, ParseException {
        for (Map.Entry<Date, Set<String>> entry : requestSalesInfo.entrySet()) {

            Date dateRequestSalesInfo = entry.getKey();
            Set<String> directoriesList = entry.getValue();
            logger.info("Kristal: creating request files");
            for (String directory : directoriesList) {

                String exchangeDirectory = directory + "\\reports\\source\\";

                if (new File(exchangeDirectory).exists() || new File(exchangeDirectory).mkdirs()) {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exchangeDirectory + "reports.request"), "windows-1251"));

                    String data = String.format("date: %s\nreport: purchases", new SimpleDateFormat("dd.MM.yyyy").format(dateRequestSalesInfo));

                    writer.write(data);
                    writer.close();
                } else
                    return "Error: " + exchangeDirectory + " doesn't exist. Request creation failed.";
            }
        }
        return null;
    }

    @Override
    public void finishReadingSalesInfo(Kristal10SalesBatch salesBatch) {
        logger.info("Kristal: Finish Reading started");
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);
            if (f.delete()) {
                logger.info("Kristal: file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public Map<String, Date> requestSucceededSoftCheckInfo(Set<String> directorySet, DBSettings dbSettings) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet, DBSettings dbSettings) throws ClassNotFoundException {
        Set<String> directorySet = new HashSet<String>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.directory != null && c.handlerModel.endsWith("Kristal10Handler"))
                directorySet.add(c.directory);
        }

        List<CashDocument> cashDocumentList = new ArrayList<CashDocument>();
        List<String> readFiles = new ArrayList<String>();
        for (String directory : directorySet) {

            String exchangeDirectory = directory + "\\Reports\\";

            File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return (pathname.getName().startsWith("cash_in") || pathname.getName().startsWith("cash_out")) && pathname.getPath().endsWith(".xml");
                }
            });

            if (filesList == null || filesList.length == 0)
                logger.info("Kristal: No cash documents found in " + exchangeDirectory);
            else {
                logger.info("Kristal: found " + filesList.length + " file(s) in " + exchangeDirectory);

                for (File file : filesList) {
                    try {
                        String fileName = file.getName();
                        logger.info("Kristal: reading " + fileName);
                        if (isFileLocked(file)) {
                            logger.info("Kristal: " + fileName + " is locked");
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
                        logger.error("File: " + file.getAbsolutePath(), e);
                    }
                }
            }
        }
        return new CashDocumentBatch(cashDocumentList, readFiles);
    }

    @Override
    public void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch) {
        logger.info("Kristal: Finish ReadingCashDocumentInfo started");
        for (String readFile : cashDocumentBatch.readFiles) {
            File f = new File(readFile);
            if (f.delete()) {
                logger.info("Kristal: file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public SalesBatch readSalesInfo(List<CashRegisterInfo> cashRegisterInfoList, DBSettings dbSettings) throws IOException, ParseException, ClassNotFoundException {

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

            String exchangeDirectory = directory + "\\Reports\\";

            File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getName().startsWith("purchases") && pathname.getPath().endsWith(".xml");
                }
            });

            if (filesList == null || filesList.length == 0)
                logger.info("Kristal: No checks found in " + exchangeDirectory);
            else {
                logger.info("Kristal: found " + filesList.length + " file(s) in " + exchangeDirectory);

                for (File file : filesList) {
                    try {
                        String fileName = file.getName();
                        logger.info("Kristal: reading " + fileName);
                        if (isFileLocked(file)) {
                            logger.info("Kristal: " + fileName + " is locked");
                        } else {
                            SAXBuilder builder = new SAXBuilder();

                            Document document = builder.build(file);
                            Element rootNode = document.getRootElement();
                            List purchasesList = rootNode.getChildren("purchase");

                            for (Object purchaseNode : purchasesList) {

                                Integer numberCashRegister = readIntegerXMLAttribute(purchaseNode, "cash");
                                String numberZReport = readStringXMLAttribute(purchaseNode, "shift");
                                Integer numberReceipt = readIntegerXMLAttribute(purchaseNode, "number");
                                BigDecimal discountSumReceipt = readBigDecimalXMLAttribute(purchaseNode, "discountAmount");

                                long dateTimeReceipt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX").parse(readStringXMLAttribute(purchaseNode, "saletime")).getTime();
                                Date dateReceipt = new Date(dateTimeReceipt);
                                Time timeReceipt = new Time(dateTimeReceipt);

                                BigDecimal sumCard = BigDecimal.ZERO;
                                BigDecimal sumCash = BigDecimal.ZERO;
                                List paymentsList = ((Element) purchaseNode).getChildren("payments");
                                for (Object paymentNode : paymentsList) {

                                    List paymentEntryList = ((Element) paymentNode).getChildren("payment");
                                    for (Object paymentEntryNode : paymentEntryList) {
                                        String paymentType = readStringXMLAttribute(paymentEntryNode, "typeClass");
                                        if (paymentType != null) {
                                            BigDecimal sum = readBigDecimalXMLAttribute(paymentEntryNode, "amount");
                                            if (paymentType.equals("CashPaymentEntity")) {
                                                sumCash = safeAdd(sumCash, sum);
                                            } else if (paymentType.equals("CashChangePaymentEntity")) {
                                                sumCash = safeSubtract(sumCash, sum);
                                            } else if (paymentType.equals("ExternalBankTerminalPaymentEntity")) {
                                                sumCard = safeAdd(sumCard, sum);
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
                                        BigDecimal quantity = readBigDecimalXMLAttribute(positionEntryNode, "count");
                                        BigDecimal price = readBigDecimalXMLAttribute(positionEntryNode, "cost");
                                        BigDecimal sumReceiptDetail = readBigDecimalXMLAttribute(positionEntryNode, "amount");
                                        currentPaymentSum = safeAdd(currentPaymentSum, sumReceiptDetail);
                                        BigDecimal discountSumReceiptDetail = readBigDecimalXMLAttribute(positionEntryNode, "discountValue");
                                        Integer numberReceiptDetail = readIntegerXMLAttribute(positionEntryNode, "order");

                                        Date startDate = directoryStartDateMap.get(directory + "_" + numberCashRegister);
                                        if (startDate == null || dateReceipt.compareTo(startDate) >= 0)
                                            currentSalesInfoList.add(new SalesInfo(directoryGroupCashRegisterMap.get(directory + "_" + numberCashRegister), numberCashRegister,
                                                    numberZReport, numberReceipt, dateReceipt, timeReceipt, sumCard, sumCash, barcode,
                                                    quantity, price, sumReceiptDetail, discountSumReceiptDetail, discountSumReceipt, null,
                                                    numberReceiptDetail, fileName));
                                    }

                                }

                                //чит для случая, когда не указана сумма платежа. Недостающую сумму пишем в наличные.
                                BigDecimal sum = safeAdd(sumCard, sumCash);
                                if (sum == null || sum.compareTo(currentPaymentSum) < 0)
                                    for (SalesInfo salesInfo : currentSalesInfoList) {
                                        salesInfo.sumCash = safeSubtract(currentPaymentSum, sumCard);
                                    }

                                salesInfoList.addAll(currentSalesInfoList);
                            }
                            filePathList.add(file.getAbsolutePath());
                        }
                    } catch (Throwable e) {
                        logger.error("File: " + file.getAbsolutePath(), e);
                    }
                }
            }
        }
        return (salesInfoList.isEmpty() && filePathList.isEmpty()) ? null :
                new Kristal10SalesBatch(salesInfoList, filePathList);
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

    private String readStringXMLAttribute(Object element, String field) {
        if (element == null || !(element instanceof Element))
            return null;
        String value = ((Element) element).getAttributeValue(field);
        if (value == null || value.isEmpty()) {
            logger.error("Attribute " + field + " is empty");
            return null;
        }
        return value;
    }

    private BigDecimal readBigDecimalXMLAttribute(Object element, String field) {
        if (element == null || !(element instanceof Element))
            return null;
        String value = ((Element) element).getAttributeValue(field);
        if (value == null || value.isEmpty()) {
            logger.error("Attribute " + field + " is empty");
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            logger.error(e);
            return null;
        }
    }

    private Integer readIntegerXMLAttribute(Object element, String field) {
        if (element == null || !(element instanceof Element))
            return null;
        String value = ((Element) element).getAttributeValue(field);
        if (value == null || value.isEmpty()) {
            logger.error("Attribute " + field + " is empty");
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            logger.error(e);
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
            logger.info(e);
            isLocked = true;
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (Exception e) {
                    logger.info(e);
                    isLocked = true;
                }
            }
            if (channel != null)
                try {
                    channel.close();
                } catch (IOException e) {
                    logger.info(e);
                    isLocked = true;
                }
        }
        return isLocked;
    }
}
