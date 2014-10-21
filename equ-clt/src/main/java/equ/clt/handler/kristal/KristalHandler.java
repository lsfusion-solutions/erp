package equ.clt.handler.kristal;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.EquipmentServer;
import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class KristalHandler extends CashRegisterHandler<KristalSalesBatch> {

    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);
    
    static Logger requestExchangeLogger;
    static {
        try {
            requestExchangeLogger = Logger.getLogger("RequestExchangeLogger");
            requestExchangeLogger.setLevel(Level.INFO);
            FileAppender fileAppender = new FileAppender(new PatternLayout("%d{DATE} %5p %c{1} - %m%n"), "logs/requestExchange.log");
            requestExchangeLogger.removeAllAppenders();
            requestExchangeLogger.addAppender(fileAppender);

        } catch (Exception ignored) {
        }
    }   

    private FileSystemXmlApplicationContext springContext;
    
    public KristalHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    @Override
    public List<MachineryInfo> sendTransaction(TransactionCashRegisterInfo transactionInfo, List<CashRegisterInfo> machineryInfoList) throws IOException {

        logger.info("Kristal: Send Transaction # " + transactionInfo.id);

        List<String> directoriesList = new ArrayList<String>();
        for (CashRegisterInfo cashRegisterInfo : machineryInfoList) {
            if ((cashRegisterInfo.port != null) && (!directoriesList.contains(cashRegisterInfo.port.trim())))
                directoriesList.add(cashRegisterInfo.port.trim());
            if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory.trim())))
                directoriesList.add(cashRegisterInfo.directory.trim());
        }

        for (String directory : directoriesList) {

            String exchangeDirectory = directory.trim() + "/ImpExp/Import/";

            if (!new File(exchangeDirectory).exists())
                new File(exchangeDirectory).mkdirs();

            //plu.txt
            File pluFile = new File(exchangeDirectory + "plu.txt");
            File flagPluFile = new File(exchangeDirectory + "WAITPLU");
            if (pluFile.exists() && flagPluFile.exists()) {
                throw new RuntimeException(String.format("file %s already exists. Maybe there are some problems with server", flagPluFile.getAbsolutePath()));
            } else if (flagPluFile.createNewFile()) {
                logger.info("Kristal: creating PLU file");
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(pluFile), "windows-1251"));

                for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                    String idItemGroup = "0|0|0|0|0";//makeIdItemGroup(item.hierarchyItemGroup);
                    String record = "+|" + item.idBarcode + "|" + item.idBarcode + "|" + item.name + "|" +
                            (item.passScalesItem && item.splitItem ? "кг.|" : "ШТ|") + (item.passScalesItem ? "1|" : "0|") +
                            (transactionInfo.nppGroupCashRegister == null ? "1" : transactionInfo.nppGroupCashRegister) + "|"/*section*/ +
                            item.price.intValue() + "|" + "0|"/*fixprice*/ + (item.splitItem ? "0.001|" : "1|") +
                            idItemGroup + "|" + (item.vat == null ? "0" : item.vat) + "|0";
                    writer.println(record);
                }
                writer.close();

                logger.info("Kristal: waiting for deletion of PLU file");
                waitForDeletion(pluFile, flagPluFile);
            } else {
                throw new RuntimeException(String.format("file %s can not be created. Maybe there are some problems with server", flagPluFile.getAbsolutePath()));
            }

            //message.txt
            boolean messageEmpty = true;
            for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                if (item.description != null && !item.description.equals("")) {
                    messageEmpty = false;
                    break;
                }
            }
            if (!messageEmpty) {
                File messageFile = new File(exchangeDirectory + "message.txt");
                File flagMessageFile = new File(exchangeDirectory + "WAITMESSAGE");
                if (messageFile.exists() && flagMessageFile.exists()) {
                    throw new RuntimeException(String.format("file %s already exists. Maybe there are some problems with server", flagMessageFile.getAbsolutePath()));
                } else if (flagMessageFile.createNewFile()) {
                    logger.info("Kristal: creating MESSAGE file");
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(messageFile), "windows-1251"));

                    for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                        if (item.description != null && !item.description.equals("")) {
                            String record = "+|" + item.idBarcode + "|" + item.description + "|||";
                            writer.println(record);
                        }
                    }
                    writer.close();
                    logger.info("Kristal: waiting for deletion of MESSAGE file");
                    waitForDeletion(messageFile, flagMessageFile);
                } else {
                    throw new RuntimeException(String.format("file %s can not be created. Maybe there are some problems with server", flagMessageFile.getAbsolutePath()));
                }
            }

            //scale.txt
            boolean scalesEmpty = true;
            for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                if (item.passScalesItem) {
                    scalesEmpty = false;
                    break;
                }
            }
            if (!scalesEmpty) {
                File scaleFile = new File(exchangeDirectory + "scales.txt");
                File flagScaleFile = new File(exchangeDirectory + "WAITSCALES");
                if (scaleFile.exists() && flagScaleFile.exists()) {
                    throw new RuntimeException(String.format("file %s already exists. Maybe there are some problems with server", flagScaleFile.getAbsolutePath()));
                } else if (flagScaleFile.createNewFile()) {
                    logger.info("Kristal: creating SCALES file");
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(scaleFile), "windows-1251"));

                    for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                        if (item.passScalesItem) {
                            String record = "+|" + item.idBarcode + "|" + item.idBarcode + "|" + "22|" + item.name + "||" +
                                    "1|0|1|"/*effectiveLife & GoodLinkToScales*/ +
                                    (item.description != null ? item.idBarcode : "0")/*ingredientNumber*/ + "|" +
                                    item.price.intValue();
                            writer.println(record);
                        }
                    }
                    writer.close();
                    logger.info("Kristal: waiting for deletion of SCALES file");
                    waitForDeletion(scaleFile, flagScaleFile);

                } else {
                    throw new RuntimeException(String.format("file %s can not be created. Maybe there are some problems with server", flagScaleFile.getAbsolutePath()));
                }
            }

            //groups.txt
            if (transactionInfo.snapshot) {
                File groupsFile = new File(exchangeDirectory + "groups.txt");
                File flagGroupsFile = new File(exchangeDirectory + "WAITGROUPS");
                if (groupsFile.exists() && flagGroupsFile.exists()) {
                    throw new RuntimeException(String.format("file %s already exists. Maybe there are some problems with server", flagGroupsFile.getAbsolutePath()));
                } else if (flagGroupsFile.createNewFile()) {
                    logger.info("Kristal: creating GROUPS file");
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(groupsFile), "windows-1251"));

                    Set<String> numberGroupItems = new HashSet<String>();
                    for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                        List<ItemGroup> hierarchyItemGroup = transactionInfo.itemGroupMap.get(item.idItemGroup);
                        hierarchyItemGroup = hierarchyItemGroup == null ? new ArrayList<ItemGroup>() : Lists.reverse(hierarchyItemGroup);
                        for (int i = 0; i < hierarchyItemGroup.size(); i++) {
                            String idItemGroup = makeIdItemGroup(hierarchyItemGroup.subList(0, hierarchyItemGroup.size() - i));
                            if (!numberGroupItems.contains(idItemGroup)) {
                                String record = "+|" + hierarchyItemGroup.get(hierarchyItemGroup.size() - 1 - i).nameItemGroup + "|" + idItemGroup;
                                writer.println(record);
                                numberGroupItems.add(idItemGroup);
                            }
                        }
                    }
                    writer.close();
                    logger.info("Kristal: waiting for deletion of GROUPS file");
                    waitForDeletion(groupsFile, flagGroupsFile);

                } else {
                    throw new RuntimeException(String.format("file %s can not be created. Maybe there are some problems with server", flagGroupsFile.getAbsolutePath()));
                }
            }
        }
        return null;
    }
    
    private void waitForDeletion(File file, File flagFile) {
        if (flagFile.delete()) {
            int count = 0;
            while (file.exists()) {
                try {
                    count++;
                    if(count>=60)
                        throw Throwables.propagate(new RuntimeException(String.format("file %s has been created but not processed by server", file.getAbsolutePath())));                  
                    else
                        Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw Throwables.propagate(e);
                }
            }
        }
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {

        for (String directory : softCheckInfo.directorySet) {

            String timestamp = new SimpleDateFormat("ddMMyyyyHHmmss").format(Calendar.getInstance().getTime());

            String exchangeDirectory = directory + "/ImpExp/Import/";

            File flagSoftFile = new File(exchangeDirectory + "WAITSOFT");

            Boolean flagExists = true;
            try {
                flagExists = flagSoftFile.exists() || flagSoftFile.createNewFile();
                if (!flagExists) {
                    logger.info("Kristal: unable to create file " + flagSoftFile.getAbsolutePath());
                }
            } catch (Exception e) {
                logger.info("Kristal: unable to create file " + flagSoftFile.getAbsolutePath(), e);
            }
            if (flagExists) {
                File softFile = new File(exchangeDirectory + "softcheque" + timestamp + ".txt");
                logger.info("Kristal: creating " + softFile.getName() + " file");
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(softFile), "windows-1251"));
                
                String logRecord = "softcheque data: ";
                for (Map.Entry<String, SoftCheckInvoice> userInvoice : softCheckInfo.invoiceMap.entrySet()) {
                    logRecord += userInvoice.getKey() + ";";
                    String record = String.format("%s|0|1|1|1", trimLeadingZeroes(userInvoice.getKey()));
                    writer.println(record);
                }
                logger.info(logRecord);
                writer.close();

                logger.info("Kristal: waiting for deletion of WAITSOFT file");
                if (flagSoftFile.delete()) {
                    while (softFile.exists()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw Throwables.propagate(e);
                        }
                    }
                }
            }
        }
    }

    @Override
    public String requestSalesInfo(List<RequestExchange> requestExchangeList) throws IOException, ParseException {

        for (RequestExchange entry : requestExchangeList) {
            if(entry.isSalesInfoExchange()) {
                logger.info("Kristal: creating request files");
                for (String directory : entry.directorySet) {

                    String dateFrom = new SimpleDateFormat("yyyyMMdd").format(entry.dateFrom);

                    Calendar cal = Calendar.getInstance();
                    cal.setTime(entry.dateTo);
                    cal.add(Calendar.DATE, 1);
                    String dateTo = new SimpleDateFormat("yyyyMMdd").format(cal.getTime());

                    String exchangeDirectory = directory + "/export/request/";

                    if (new File(exchangeDirectory).exists() || new File(exchangeDirectory).mkdirs()) {
                        Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exchangeDirectory + "request.xml"), "utf-8"));

                        String data = String.format("<?xml version=\"1.0\" encoding=\"windows-1251\" ?>\n" +
                                "<REPORLOAD REPORTTYPE=\"2\" >\n" +
                                "<REPORT DATEBEGIN=\"%s\" DATEEND=\"%s\"/>\n" +
                                "</REPORLOAD>", dateFrom, dateTo);

                        writer.write(data);
                        writer.close();
                    } else
                        return "Error: " + exchangeDirectory + " doesn't exist. Request creation failed.";
                }
            }
        }
        return null;
    }

    @Override
    public void finishReadingSalesInfo(KristalSalesBatch salesBatch) {
        logger.info("Kristal: Finish Reading started");
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
                logger.info("Kristal: file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public Map<String, Timestamp> requestSucceededSoftCheckInfo(Set<String> directorySet) throws ClassNotFoundException, SQLException {
       
        logger.info("Kristal: requesting succeeded SoftCheckInfo");

        DBSettings kristalSettings = (DBSettings) springContext.getBean("kristalSettings");

        Map<String, Timestamp> result = new HashMap<String, Timestamp>();

        for (int i = 0; i < kristalSettings.sqlHost.length; i++) {
            Connection conn = null;
            try {
                String url = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;User=%s;Password=%s",
                        kristalSettings.sqlHost[i].trim(), kristalSettings.sqlPort, kristalSettings.sqlDBName, kristalSettings.sqlUsername, kristalSettings.sqlPassword);
                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                conn = DriverManager.getConnection(url);
                Statement statement = conn.createStatement();
                String queryString = "SELECT DocNumber, DateTimePosting FROM DocHead WHERE ShipmentState='1' AND PayState='0'";
                ResultSet rs = statement.executeQuery(queryString);
                while (rs.next()) {
                    result.put(fillLeadingZeroes(rs.getString(1)), rs.getTimestamp(2));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (conn != null)
                    conn.close();
            }
        }
        return result;
    }

    @Override
    public String checkZReportSum(Map<String, BigDecimal> zReportSumMap, List<String> idCashRegisterList) throws ClassNotFoundException, SQLException {
        String idCashRegisters = "";
        for(String idCashRegister : idCashRegisterList)
            idCashRegisters += idCashRegister + ",";
        idCashRegisters = idCashRegisters.isEmpty() ? idCashRegisters : idCashRegisters.substring(0, idCashRegisters.length() - 1);
        requestExchangeLogger.info("Kristal: checking zReports sum, CashRegisters: " + idCashRegisters);

        String result = "";
        
        DBSettings kristalSettings = (DBSettings) springContext.getBean("kristalSettings");

        for (int i = 0; i < kristalSettings.sqlHost.length; i++) {
            Connection conn = null;
            try {
                String url = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;User=%s;Password=%s",
                        kristalSettings.sqlHost[i].trim(), kristalSettings.sqlPort, kristalSettings.sqlDBName, kristalSettings.sqlUsername, kristalSettings.sqlPassword);
                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                conn = DriverManager.getConnection(url);
                Statement statement = conn.createStatement();
                String queryString = "SELECT GangNumber, CashNumber, Summa, GangDateStart FROM OperGang WHERE CashNumber IN (" + idCashRegisters + ")";
                ResultSet rs = statement.executeQuery(queryString);
                while (rs.next()) {
                    String numberZReport = String.valueOf(rs.getInt(1));
                    if (zReportSumMap.containsKey(numberZReport)) {
                        String numberCashRegister = String.valueOf(rs.getInt(2));
                        BigDecimal fusionSum = zReportSumMap.get(numberZReport);
                        double kristalSum = rs.getDouble(3);
                        Date date = rs.getDate(4);
                        if (fusionSum == null || fusionSum.doubleValue() != kristalSum)
                            result += String.format("%s. CashRegister %s. \nZReport %s checksum failed: %s(fusion) != %s(kristal);\n", 
                                    date, numberCashRegister, numberZReport, fusionSum, kristalSum);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                if (conn != null)
                    conn.close();
            }
        }
        if(result.isEmpty())
            requestExchangeLogger.info("No errors");
        else
            requestExchangeLogger.error(result);
        return result.isEmpty() ? null : result;
    }

    @Override
    public ExtraCheckZReportBatch extraCheckZReportSum(List<CashRegisterInfo> cashRegisterInfoList, Map<String, BigDecimal> zReportSumMap) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException {

        DBSettings kristalSettings = (DBSettings) springContext.getBean("kristalSettings");
        
        List<CashDocument> result = new ArrayList<CashDocument>();

        for (int i = 0; i < kristalSettings.sqlHost.length; i++) {
            Connection conn = null;
            try {
                String url = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;User=%s;Password=%s",
                        kristalSettings.sqlHost[i].trim(), kristalSettings.sqlPort, kristalSettings.sqlDBName, kristalSettings.sqlUsername, kristalSettings.sqlPassword);
                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                conn = DriverManager.getConnection(url);
                Statement statement = conn.createStatement();
                String queryString = "SELECT Ck_Number, Ck_Date, Ck_Summa, CashNumber FROM OperGangMoney WHERE Taken='1'";
                ResultSet rs = statement.executeQuery(queryString);
                while (rs.next()) {
                    String number = rs.getString("Ck_Number");
                    Timestamp dateTime = rs.getTimestamp("Ck_Date");
                    Date date = new Date(dateTime.getTime());
                    Time time = new Time(dateTime.getTime());
                    BigDecimal sum = rs.getBigDecimal("Ck_Summa");
                    Integer numberCashRegister = rs.getInt("CashNumber");
                    if (!cashDocumentSet.contains(number))
                        result.add(new CashDocument(number, date, time, numberCashRegister, sum));
                }
            } catch (SQLException e) {
                logger.error(e);
            } finally {
                try {
                    if (conn != null)
                        conn.close();
                } catch (SQLException e) {
                    logger.error(e);
                }
            }
        }
        if (result.size() == 0)
            logger.info("Kristal: no CashDocuments found");
        else
            logger.info(String.format("Kristal: found %s CashDocument(s)", result.size()));
        return new CashDocumentBatch(result, null);
    }

    @Override
    public void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch) {
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {
        
    }

    @Override
    public SalesBatch readSalesInfo(List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException, ClassNotFoundException {

        Map<String, Integer> directoryGroupCashRegisterMap = new HashMap<String, Integer>();
        Map<String, Date> directoryStartDateMap = new HashMap<String, Date>();
        Map<String, Boolean> directoryNotDetailedMap = new HashMap<String, Boolean>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.handlerModel != null && c.directory != null && c.handlerModel.endsWith("KristalHandler")) {
                directoryNotDetailedMap.put(c.directory, c.notDetailed);
                if (c.number != null && c.numberGroup != null)
                    directoryGroupCashRegisterMap.put(c.directory + "_" + c.number, c.numberGroup);
                if (c.number != null && c.startDate != null)
                    directoryStartDateMap.put(c.directory + "_" + c.number, c.startDate);
            }
        }

        List<SalesInfo> salesInfoList = new ArrayList<SalesInfo>();
        List<String> filePathList = new ArrayList<String>();
        for (Map.Entry<String, Boolean> entry : directoryNotDetailedMap.entrySet()) {
            String directory = entry.getKey();
            final Boolean notDetailed = entry.getValue();

            String exchangeDirectory = directory + "/Export/";

            File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname != null && pathname.getName() != null && pathname.getPath() != null && pathname.getName().startsWith(notDetailed ? "ReportGang1C" : "ReportCheque1C") && pathname.getPath().endsWith(".xml");
                }
            });

            File[] deletingFilesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname != null && pathname.getName() != null && pathname.getPath() != null && pathname.getName().startsWith(notDetailed ? "ReportCheque1C" : "ReportGang1C") && pathname.getPath().endsWith(".xml");
                }
            });

            if (deletingFilesList != null) {
                for (File file : deletingFilesList) {
                    filePathList.add(file.getAbsolutePath());
                }
            }
                

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
                            List daysList = rootNode.getChildren("DAY");

                            for (Object dayNode : daysList) {

                                List shopsList = ((Element) dayNode).getChildren("SHOP");

                                for (Object shopNode : shopsList) {

                                    List cashesList = ((Element) shopNode).getChildren("CASH");

                                    for (Object cashNode : cashesList) {

                                        Integer numberCashRegister = readIntegerXMLAttribute((Element) cashNode, "CASHNUMBER");
                                        List gangsList = ((Element) cashNode).getChildren("GANG");

                                        if (notDetailed) {
                                            
                                            //not detailed
                                            
                                            for (Object gangNode : gangsList) {

                                                String numberZReport = ((Element) gangNode).getAttributeValue("GANGNUMBER");
                                                                                               
                                                Integer numberReceipt = readIntegerXMLAttribute((Element) gangNode, "CHEQUENUMBERFIRST");//всё пишем по номеру первого чека
                                                
                                                BigDecimal discountSumReceipt = readBigDecimalXMLAttribute((Element) gangNode, "DISCSUMM");
                                                long dateTimeReceipt = DateUtils.parseDate(((Element)gangNode).getAttributeValue("GANGDATESTART"), new String[]{"dd.MM.yyyy HH:mm:ss"}).getTime();
                                                Date dateReceipt = new Date(dateTimeReceipt);
                                                Time timeReceipt = new Time(dateTimeReceipt);

                                                List receiptDetailsList = ((Element)gangNode).getChildren("GOOD");
                                                List paymentsList = ((Element)gangNode).getChildren("PAYMENT");

                                                BigDecimal sumCard = BigDecimal.ZERO;
                                                BigDecimal sumCash = BigDecimal.ZERO;
                                                for (Object paymentNode : paymentsList) {
                                                    Element paymentElement = (Element) paymentNode;
                                                    if (paymentElement.getAttributeValue("PAYMENTTYPE").equals("Наличный расчет")) {
                                                        sumCash = safeAdd(sumCash, readBigDecimalXMLAttribute(paymentElement, "SUMMASALE"));
                                                    } else if (paymentElement.getAttributeValue("PAYMENTTYPE").equals("Безналичный слип")) {
                                                        sumCard = safeAdd(sumCard, readBigDecimalXMLAttribute(paymentElement, "SUMMASALE"));
                                                    }
                                                }

                                                List<SalesInfo> currentSalesInfoList = new ArrayList<SalesInfo>();
                                                BigDecimal currentPaymentSum = BigDecimal.ZERO;
                                                Integer numberReceiptDetail = 0;
                                                for (Object receiptDetailNode : receiptDetailsList) {

                                                    Element receiptDetailElement = (Element) receiptDetailNode;

                                                    String barcode = receiptDetailElement.getAttributeValue("CODE");
                                                    BigDecimal quantity = readBigDecimalXMLAttribute(receiptDetailElement, "QUANTITY");
                                                    BigDecimal price = readBigDecimalXMLAttribute(receiptDetailElement, "PRICE");
                                                    BigDecimal sumReceiptDetail = readBigDecimalXMLAttribute(receiptDetailElement, "SUMMA");
                                                    currentPaymentSum = safeAdd(currentPaymentSum, sumReceiptDetail);
                                                    numberReceiptDetail++;
                                                    
                                                    Date startDate = directoryStartDateMap.get(directory + "_" + numberCashRegister);
                                                    if (dateReceipt == null || startDate == null || dateReceipt.compareTo(startDate) >= 0)
                                                        currentSalesInfoList.add(new SalesInfo(directoryGroupCashRegisterMap.get(directory + "_" + numberCashRegister), numberCashRegister,
                                                                numberZReport, numberReceipt, dateReceipt, timeReceipt, null, null, null, sumCard, sumCash, null, barcode,
                                                                null, quantity, price, sumReceiptDetail, null, discountSumReceipt, null, numberReceiptDetail, fileName));
                                                }

                                                //чит для случая, когда не указана сумма платежа. Недостающую сумму пишем в наличные.
                                                BigDecimal sum = safeAdd(sumCard, sumCash);
                                                if (sum == null || sum.compareTo(currentPaymentSum) < 0)
                                                    for (SalesInfo salesInfo : currentSalesInfoList) {
                                                        salesInfo.sumCash = safeSubtract(currentPaymentSum, sumCard);
                                                    }

                                                salesInfoList.addAll(currentSalesInfoList);
                                            }
                                        } else {
                                            
                                            //detailed

                                            for (Object gangNode : gangsList) {

                                                String numberZReport = ((Element) gangNode).getAttributeValue("GANGNUMBER");
                                                List receiptsList = ((Element) gangNode).getChildren("HEAD");

                                                for (Object receiptNode : receiptsList) {

                                                    Element receiptElement = (Element) receiptNode;

                                                    Integer numberReceipt = readIntegerXMLAttribute(receiptElement, "ID");
                                                    BigDecimal discountSumReceipt = readBigDecimalXMLAttribute(receiptElement, "DISCSUMM");
                                                    long dateTimeReceipt = DateUtils.parseDate(receiptElement.getAttributeValue("DATEOPERATION"), new String[]{"dd.MM.yyyy HH:mm:ss"}).getTime();
                                                    Date dateReceipt = new Date(dateTimeReceipt);
                                                    Time timeReceipt = new Time(dateTimeReceipt);

                                                    List receiptDetailsList = (receiptElement).getChildren("POS");
                                                    List paymentsList = (receiptElement).getChildren("PAY");

                                                    BigDecimal sumCard = BigDecimal.ZERO;
                                                    BigDecimal sumCash = BigDecimal.ZERO;
                                                    for (Object paymentNode : paymentsList) {
                                                        Element paymentElement = (Element) paymentNode;
                                                        if (paymentElement.getAttributeValue("PAYTYPE").equals("0")) {
                                                            sumCash = safeAdd(sumCash, readBigDecimalXMLAttribute(paymentElement, "DOCSUMM"));
                                                        } else if (paymentElement.getAttributeValue("PAYTYPE").equals("3")) {
                                                            sumCard = safeAdd(sumCard, readBigDecimalXMLAttribute(paymentElement, "DOCSUMM"));
                                                        }
                                                    }

                                                    List<SalesInfo> currentSalesInfoList = new ArrayList<SalesInfo>();
                                                    BigDecimal currentPaymentSum = BigDecimal.ZERO;
                                                    for (Object receiptDetailNode : receiptDetailsList) {

                                                        Element receiptDetailElement = (Element) receiptDetailNode;

                                                        String barcode = receiptDetailElement.getAttributeValue("CODE");
                                                        BigDecimal quantity = readBigDecimalXMLAttribute(receiptDetailElement, "QUANTITY");
                                                        BigDecimal price = readBigDecimalXMLAttribute(receiptDetailElement, "PRICE");
                                                        BigDecimal sumReceiptDetail = readBigDecimalXMLAttribute(receiptDetailElement, "SUMMA");
                                                        currentPaymentSum = safeAdd(currentPaymentSum, sumReceiptDetail);
                                                        BigDecimal discountSumReceiptDetail = readBigDecimalXMLAttribute(receiptDetailElement, "DISCSUMM");
                                                        Integer numberReceiptDetail = readIntegerXMLAttribute(receiptDetailElement, "POSNUMBER");

                                                        Date startDate = directoryStartDateMap.get(directory + "_" + numberCashRegister);
                                                        if (dateReceipt == null || startDate == null || dateReceipt.compareTo(startDate) >= 0)
                                                            currentSalesInfoList.add(new SalesInfo(directoryGroupCashRegisterMap.get(directory + "_" + numberCashRegister), numberCashRegister,
                                                                    numberZReport, numberReceipt, dateReceipt, timeReceipt, null, null, null, sumCard, sumCash, null, barcode,
                                                                    null, quantity, price, sumReceiptDetail, discountSumReceiptDetail, discountSumReceipt, null,
                                                                    numberReceiptDetail, fileName));
                                                    }

                                                    //чит для случая, когда не указана сумма платежа. Недостающую сумму пишем в наличные.
                                                    BigDecimal sum = safeAdd(sumCard, sumCash);
                                                    if (sum == null || sum.compareTo(currentPaymentSum) < 0)
                                                        for (SalesInfo salesInfo : currentSalesInfoList) {
                                                            salesInfo.sumCash = safeSubtract(currentPaymentSum, sumCard);
                                                        }

                                                    salesInfoList.addAll(currentSalesInfoList);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        logger.error("File: " + file.getAbsolutePath(), e);
                    }
                    filePathList.add(file.getAbsolutePath());
                }
            }
        }
        return (salesInfoList.isEmpty() && filePathList.isEmpty()) ? null :
                new KristalSalesBatch(salesInfoList, filePathList);
    }

    private String trimLeadingZeroes(String input) {
        if (input == null)
            return null;
        while (input.startsWith("0"))
            input = input.substring(1);
        return input.trim();
    }

    private String fillLeadingZeroes(String input) {
        if (input == null)
            return null;
        while (input.length() < 7)
            input = "0" + input;
        return input;
    }

    private String makeIdItemGroup(List<ItemGroup> hierarchyItemGroup) {
        String idItemGroup = "";
        for (int i = 0; i < hierarchyItemGroup.size(); i++) {
            String id = hierarchyItemGroup.get(i).idItemGroup;
            idItemGroup += (id == null ? "0" : id) + "|";
        }
        for (int i = hierarchyItemGroup.size(); i < 5; i++) {
            idItemGroup += "0|";
        }
        idItemGroup = idItemGroup.substring(0, idItemGroup.length() - 1);
        return idItemGroup;
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

    private BigDecimal readBigDecimalXMLAttribute(Element element, String field) {
        if (element == null)
            return null;
        String value = element.getAttributeValue(field);
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

    private Integer readIntegerXMLAttribute(Element element, String field) {
        if (element == null)
            return null;
        String value = element.getAttributeValue(field);
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
