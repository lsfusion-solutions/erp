package equ.clt.handler.kristal;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.clt.EquipmentServer;
import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import java.io.*;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class KristalHandler extends CashRegisterHandler<KristalSalesBatch> {

    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);
    
    public KristalHandler() {
    }

    @Override
    public void sendTransaction(TransactionCashRegisterInfo transactionInfo, List<CashRegisterInfo> machineryInfoList) throws IOException {

        List<String> directoriesList = new ArrayList<String>();
        for (CashRegisterInfo cashRegisterInfo : machineryInfoList) {
            if ((cashRegisterInfo.port != null) && (!directoriesList.contains(cashRegisterInfo.port.trim())))
                directoriesList.add(cashRegisterInfo.port.trim());
            if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory.trim())))
                directoriesList.add(cashRegisterInfo.directory.trim());
        }

        for (String directory : directoriesList) {

            String exchangeDirectory = directory.trim() + "\\ImpExp\\Import\\";
            
            //plu.txt
            File flagPluFile = new File(exchangeDirectory + "WAITPLU");
            if (flagPluFile.exists() || flagPluFile.createNewFile()) {

                File pluFile = new File(exchangeDirectory + "plu.txt");
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(pluFile), "windows-1251"));

                for (ItemInfo item : transactionInfo.itemsList) {
                    String idItemGroup = "0|0|0|0|0";//makeIdItemGroup(item.hierarchyItemGroup);
                    String record = "+|" + item.idBarcode + "|" + item.idBarcode + "|" + item.name + "|" +
                            (item.isWeightItem ? "кг.|" : "ШТ|") + (item.isWeightItem ? "1|" : "0|") +
                            (item.nppGroupMachinery == null ? "1" : item.nppGroupMachinery) + "|"/*section*/ +
                            item.price.intValue() + "|" + "0|"/*fixprice*/ + (item.isWeightItem ? "0.001|" : "1|") +
                            idItemGroup + "||||||1";
                    writer.println(record);
                }
                writer.close();

                if (flagPluFile.delete()) {
                    while (pluFile.exists()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw Throwables.propagate(e);
                        }
                    }
                }
            }

            //message.txt
            File flagMessageFile = new File(exchangeDirectory + "WAITMESSAGE");
            if (flagMessageFile.exists() || flagMessageFile.createNewFile()) {

                File messageFile = new File(exchangeDirectory + "message.txt");
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(messageFile), "windows-1251"));

                for (ItemInfo item : transactionInfo.itemsList) {
                    if (item.composition != null && !item.composition.equals("")) {
                        String record = "+|" + item.idBarcode + "|" + item.composition + "|||";
                        writer.println(record);
                    }
                }
                writer.close();
                if (flagMessageFile.delete()) {
                    while (messageFile.exists()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw Throwables.propagate(e);
                        }
                    }
                }
            }

            //scale.txt
            File flagScaleFile = new File(exchangeDirectory + "WAITSCALES");
            if (flagScaleFile.exists() || flagScaleFile.createNewFile()) {
                File scaleFile = new File(exchangeDirectory + "scales.txt");
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(scaleFile), "windows-1251"));

                for (ItemInfo item : transactionInfo.itemsList) {
                    String record = "+|" + item.idBarcode + "|" + item.idBarcode + "|" + "22|" + item.name + "||" +
                            "1|0|1|"/*effectiveLife & GoodLinkToScales*/ +
                            (item.composition != null ? item.idBarcode : "0")/*ingredientNumber*/ + "|" +
                            item.price.intValue();
                    writer.println(record);
                }
                writer.close();
                if (flagScaleFile.delete()) {
                    while (scaleFile.exists()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw Throwables.propagate(e);
                        }
                    }
                }
            }

            //groups.txt
            File flagGroupsFile = new File(exchangeDirectory + "WAITGROUPS");
            if (flagGroupsFile.exists() || flagGroupsFile.createNewFile()) {

                File groupsFile = new File(exchangeDirectory + "groups.txt");

                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(groupsFile), "windows-1251"));

                Set<String> numberGroupItems = new HashSet<String>();
                for (ItemInfo item : transactionInfo.itemsList) {
                    String idItemGroup = makeIdItemGroup(item.hierarchyItemGroup);
                    if (!numberGroupItems.contains(idItemGroup)) {
                        String record = "+|" + item.nameItemGroup + "|" + idItemGroup;
                        writer.println(record);
                        numberGroupItems.add(idItemGroup);
                    }

                }
                writer.close();
                if (flagGroupsFile.delete()) {
                    while (groupsFile.exists()) {
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
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {

        for (String directory : softCheckInfo.directorySet) {

            String timestamp = new SimpleDateFormat("ddMMyyyyHHmmss").format(Calendar.getInstance().getTime());
            
            String exchangeDirectory = directory + "\\ImpExp\\Import\\";

            File flagSoftFile = new File(exchangeDirectory + "WAITSOFT");
            
            Boolean flagExists = true;
            try {
                flagExists = flagSoftFile.exists() || flagSoftFile.createNewFile();
                if(!flagExists) {
                    logger.info("unable to create file " + flagSoftFile.getAbsolutePath());  
                }
            } catch(Exception e) {
                logger.info("unable to create file " + flagSoftFile.getAbsolutePath(), e);
            }
            if (flagExists) {
                File softFile = new File(exchangeDirectory + "softcheque" + timestamp + ".txt");
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(softFile), "windows-1251"));

                for (String userInvoice : softCheckInfo.invoiceSet) {
                    String record = String.format("%s|0|1|1|1", trimLeadingZeroes(userInvoice));
                    writer.println(record);
                }
                writer.close();

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
    public String requestSalesInfo(Map<java.util.Date, Set<String>> requestSalesInfo) throws IOException, ParseException {

        for (Map.Entry<java.util.Date, Set<String>> entry : requestSalesInfo.entrySet()) {

            java.util.Date dateRequestSalesInfo = entry.getKey();
            Set<String> directoriesList = entry.getValue();

            for (String directory : directoriesList) {

                String exchangeDirectory = directory + "\\export\\request\\";

                if (new File(exchangeDirectory).exists() || new File(exchangeDirectory).mkdirs()) {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exchangeDirectory + "request.xml"), "utf-8"));

                    String data = String.format("<?xml version=\"1.0\" encoding=\"windows-1251\" ?>\n" +
                            "<REPORLOAD REPORTTYPE=\"2\" >\n" +
                            "<REPORT OPERDAY=\"%s\"/>\n" +
                            "</REPORLOAD>", new SimpleDateFormat("yyyyMMdd").format(dateRequestSalesInfo));

                    writer.write(data);
                    writer.close();
                }
                return "Error: " + exchangeDirectory + " doesn't exist. Request creation failed.";
            }
        }
        return null;
    }

    @Override
    public void finishReadingSalesInfo(KristalSalesBatch salesBatch) {
        logger.info("Finish reading");
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);
            if (f.delete()) {
                logger.info("file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public Set<String> requestSucceededSoftCheckInfo() throws ClassNotFoundException, SQLException {

        Set<String> result = new HashSet<String>();

        Connection conn = null;
        try {

            String username = "sa";
            String password = "mssql";
            String ip = "192.168.0.220";
            String dbName = "SES";
            String url = String.format("jdbc:sqlserver://%s:1433;databaseName=%s;User=%s;Password=%s", ip, dbName, username, password);

            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            conn = DriverManager.getConnection(url);
            Statement statement = conn.createStatement();
            String queryString = "SELECT DocNumber FROM DocHead WHERE ShipmentState='1' AND PayState='0'";
            ResultSet rs = statement.executeQuery(queryString);
            while (rs.next()) {
                result.add(fillLeadingZeroes(rs.getString(1)));
            }                       
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null)
                conn.close();
        }
        return result;
    }

    @Override
    public SalesBatch readSalesInfo(List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException {

        Map<String, String> cashRegisterDirectories = new HashMap<String, String>();
        for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
            if ((cashRegister.directory != null) && (!cashRegisterDirectories.containsValue(cashRegister.directory)))
                cashRegisterDirectories.put(cashRegister.cashRegisterNumber, cashRegister.directory);
            if ((cashRegister.port != null) && (!cashRegisterDirectories.containsValue(cashRegister.port)))
                cashRegisterDirectories.put(cashRegister.cashRegisterNumber, cashRegister.port);
        }

        List<SalesInfo> salesInfoList = new ArrayList<SalesInfo>();
        List<String> filePathList = new ArrayList<String>();
        for (Map.Entry<String, String> entry : cashRegisterDirectories.entrySet()) {

            try {
                if (entry.getValue() != null) {

                    String exchangeDirectory = entry.getValue().trim() + "\\Export\\";

                    File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File pathname) {
                            return pathname.getName().startsWith("ReportCheque1C") && pathname.getPath().endsWith(".xml");
                        }
                    });

                    if(filesList.length==0)
                        logger.info("Kristal: No checks found in " + exchangeDirectory);
                    else
                        logger.info("Kristal: found " + filesList.length + " file(s) in " + exchangeDirectory);

                    for (File file : filesList) {
                        String fileName = file.getName();
                        logger.info("Kristal: reading " + file.getName());
                        long currentDate = Calendar.getInstance().getTime().getTime();
                        long receiptDetailDate = DateUtils.parseDate(fileName.replace("ReportCheque1C_", "").replace(".xml", ""), new String[]{"yyyyMMddHHmmss"}).getTime();
                        if ((currentDate - receiptDetailDate) > 60000) {
                            SAXBuilder builder = new SAXBuilder();

                            Document document = builder.build(file);
                            Element rootNode = document.getRootElement();
                            List daysList = rootNode.getChildren("DAY");

                            for (Object dayNode : daysList) {

                                List shopsList = ((Element) dayNode).getChildren("SHOP");

                                for (Object shopNode : shopsList) {

                                    List cashesList = ((Element) shopNode).getChildren("CASH");

                                    for (Object cashNode : cashesList) {

                                        String numberCashRegister = ((Element) cashNode).getAttributeValue("CASHNUMBER");
                                        List gangsList = ((Element) cashNode).getChildren("GANG");

                                        for (Object gangNode : gangsList) {

                                            String numberZReport = ((Element) gangNode).getAttributeValue("GANGNUMBER");
                                            List receiptsList = ((Element) gangNode).getChildren("HEAD");

                                            for (Object receiptNode : receiptsList) {

                                                Element receiptElement = (Element) receiptNode;

                                                Integer numberReceipt = Integer.parseInt((receiptElement).getAttributeValue("ID"));
                                                BigDecimal sumReceipt = new BigDecimal(receiptElement.getAttributeValue("SUMMA"));
                                                BigDecimal discountSumReceipt = new BigDecimal(receiptElement.getAttributeValue("DISCSUMM"));
                                                long dateTimeReceipt = DateUtils.parseDate(receiptElement.getAttributeValue("DATEOPERATION"), new String[]{"dd.MM.yyyy hh:mm:ss"}).getTime();
                                                Date dateReceipt = new Date(dateTimeReceipt);
                                                Time timeReceipt = new Time(dateTimeReceipt);

                                                List receiptDetailsList = (receiptElement).getChildren("POS");
                                                List paymentsList = (receiptElement).getChildren("PAY");

                                                BigDecimal sumCard = BigDecimal.ZERO;
                                                BigDecimal sumCash = BigDecimal.ZERO;
                                                for (Object paymentNode : paymentsList) {
                                                    Element paymentElement = (Element) paymentNode;
                                                    if (paymentElement.getAttributeValue("PAYTYPE").equals("0")) {
                                                        sumCash = sumCash.add(new BigDecimal(paymentElement.getAttributeValue("DOCSUMM")));
                                                    } else if (paymentElement.getAttributeValue("PAYTYPE").equals("3")) {
                                                        sumCard = sumCard.add(new BigDecimal(paymentElement.getAttributeValue("DOCSUMM")));
                                                    }
                                                }

                                                for (Object receiptDetailNode : receiptDetailsList) {

                                                    Element receiptDetailElement = (Element) receiptDetailNode;

                                                    String barcode = receiptDetailElement.getAttributeValue("CODE");
                                                    BigDecimal quantity = new BigDecimal(receiptDetailElement.getAttributeValue("QUANTITY"));
                                                    BigDecimal price = new BigDecimal(receiptDetailElement.getAttributeValue("PRICE"));
                                                    BigDecimal sumReceiptDetail = new BigDecimal(receiptDetailElement.getAttributeValue("SUMMA"));
                                                    BigDecimal discountSumReceiptDetail = new BigDecimal(receiptDetailElement.getAttributeValue("DISCSUMM"));
                                                    Integer numberReceiptDetail = Integer.parseInt(receiptDetailElement.getAttributeValue("POSNUMBER"));

                                                    salesInfoList.add(new SalesInfo(numberCashRegister, Integer.parseInt(numberCashRegister), entry.getValue().trim(),
                                                            numberZReport, numberReceipt, dateReceipt, timeReceipt, sumReceipt, sumCard, sumCash, barcode, 
                                                            quantity, price, sumReceiptDetail, discountSumReceiptDetail, discountSumReceipt, null, 
                                                            numberReceiptDetail, fileName));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            filePathList.add(file.getAbsolutePath());
                        }
                    }
                }
            } catch (JDOMException e) {
                throw Throwables.propagate(e);
            }
        }
        return new KristalSalesBatch(salesInfoList, filePathList);
    }

    private String trimLeadingZeroes (String input) {
        if(input == null)
            return null;
        while(input.startsWith("0"))
            input = input.substring(1);
        return input.trim();
    }
    
    private String fillLeadingZeroes(String input) {
        if(input == null)
            return null;
        while(input.length()<7)
            input = "0" + input;
        return input;
    }
    
    private String makeIdItemGroup(List<String> hierarchyItemGroup) {
        String idItemGroup = "";
        for (int i = hierarchyItemGroup.size(); i < 5; i++) {
            idItemGroup += "0|";
        }
        for (int i = hierarchyItemGroup.size() - 1; i >= 0; i--) {
            String id = hierarchyItemGroup.get(i);
            idItemGroup += (id == null ? "0" : id) + "|";
        }
        idItemGroup = idItemGroup.substring(0, idItemGroup.length() - 1);
        return idItemGroup;
    }
}
