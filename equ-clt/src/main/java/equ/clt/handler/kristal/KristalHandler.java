package equ.clt.handler.kristal;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.*;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static equ.clt.EquipmentServer.*;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class KristalHandler extends DefaultCashRegisterHandler<KristalSalesBatch> {
    
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

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        List<String> directoriesList = getDirectoriesList(transactionInfo.machineryInfoList);
        return "kristal" + (directoriesList.isEmpty() ? "" : directoriesList.get(0));
    }

    protected List<String> getDirectoriesList(List<CashRegisterInfo> machineryInfoList) {
        List<String> directoriesList = new ArrayList<>();
        for (CashRegisterInfo machinery : machineryInfoList) {
            if (machinery.directory != null && !directoriesList.contains(machinery.directory))
                directoriesList.add(machinery.directory);
        }
        return directoriesList;
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionInfoList) {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        for(TransactionCashRegisterInfo transactionInfo : transactionInfoList) {

            Exception exception = null;
            try {

                processTransactionLogger.info("Kristal: Send Transaction # " + transactionInfo.id);

                KristalSettings kristalSettings = springContext.containsBean("kristalSettings") ? (KristalSettings) springContext.getBean("kristalSettings") : null;
                boolean useIdItem = kristalSettings != null && kristalSettings.getUseIdItem() != null && kristalSettings.getUseIdItem();
                boolean noMessageAndScaleFiles = kristalSettings != null && kristalSettings.getNoMessageAndScaleFiles() != null && kristalSettings.getNoMessageAndScaleFiles();
                String importPrefixPath = kristalSettings != null ? kristalSettings.getImportPrefixPath() : null;
                Integer importGroupType = kristalSettings != null ? kristalSettings.getImportGroupType() : null;
                boolean noRestriction = kristalSettings != null && kristalSettings.getNoRestriction() != null && kristalSettings.getNoRestriction();
                boolean zeroesInItemGroup = kristalSettings != null && kristalSettings.isZeroesInItemGroup();

                Map<String, String> directoriesMap = new HashMap<>();
                for (CashRegisterInfo cashRegisterInfo : transactionInfo.machineryInfoList) {
                    if ((cashRegisterInfo.port != null) && (!directoriesMap.containsKey(cashRegisterInfo.port)))
                        directoriesMap.put(cashRegisterInfo.port, cashRegisterInfo.weightCodeGroupCashRegister);
                    if ((cashRegisterInfo.directory != null) && (!directoriesMap.containsKey(cashRegisterInfo.directory)))
                        directoriesMap.put(cashRegisterInfo.directory, cashRegisterInfo.weightCodeGroupCashRegister);
                }

                for (Map.Entry<String, String> entry : directoriesMap.entrySet()) {
                    String directory = entry.getKey();
                    String weightPrefix = entry.getValue() != null ? entry.getValue() : "22";

                    String exchangeDirectory = directory + (importPrefixPath == null ? "/ImpExp/Import/" : importPrefixPath);

                    makeDirsIfNeeded(exchangeDirectory);

                    //plu.txt
                    File pluFile = new File(exchangeDirectory + "plu.txt");
                    File flagPluFile = new File(exchangeDirectory + "WAITPLU");
                    if (pluFile.exists() && flagPluFile.exists() && !flagPluFile.delete()) {
                        throw new RuntimeException(existFilesMessage(pluFile, flagPluFile));
                    } else if (flagPluFile.createNewFile()) {
                        processTransactionLogger.info(String.format("Kristal: creating PLU file (Transaction #%s)", transactionInfo.id));
                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(pluFile), "windows-1251"));

                        Integer departmentNumber = transactionInfo.departmentNumberGroupCashRegister == null ? 1 : transactionInfo.departmentNumberGroupCashRegister;

                        for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                List<ItemGroup> hierarchyItemGroup = transactionInfo.itemGroupMap.get(item.idItemGroup);
                                String idItemGroup = zeroesInItemGroup ? "1|00|00|00|00"
                                        : importGroupType == null || importGroupType.equals(0) ? "0|0|0|0|0"
                                        : importGroupType.equals(1) ? hierarchyItemGroup == null ? "0|0|0|0|0" : makeIdItemGroup(hierarchyItemGroup, false)
                                        : importGroupType.equals(2) ? String.valueOf(item.itemGroupObject)
                                        : importGroupType.equals(3) ? hierarchyItemGroup == null ? "0|0|0|0|0" : makeIdItemGroup(hierarchyItemGroup.subList(0, Math.min(hierarchyItemGroup.size(), 2)), true) : "";
                                boolean isWeightItem = item.passScalesItem && item.splitItem;
                                Object code = useIdItem ? item.idItem : item.idBarcode;
                                String barcode = (isWeightItem ? weightPrefix : "") + (item.idBarcode == null ? "" : item.idBarcode);

                                int vat = item.vat == null ? 0 : item.vat.intValue();
                                if(vat != 10 && vat != 20) {
                                    vat = 0;
                                }
                                String record = "+|" + code + "|" + barcode + "|" + item.name + "|" +
                                        (isWeightItem ? "кг.|" : "ШТ|") + (item.passScalesItem ? "1|" : "0|") +
                                        departmentNumber + "|"/*section*/ + item.price + "|" + "0|"/*fixprice*/ +
                                        (item.splitItem ? "0.001|" : "1|") + idItemGroup + "|" + vat + "|0";
                                writer.println(record);
                            }
                        }
                        writer.close();

                        processTransactionLogger.info(String.format("Kristal: waiting for deletion of PLU file %s (Transaction #%s)", pluFile.getAbsolutePath(), transactionInfo.id));
                        waitForDeletion(pluFile, flagPluFile);
                    } else {
                        throw new RuntimeException(cantCreateFileMessage(flagPluFile));
                    }

                    if(!noRestriction) {
                        //restriction.txt
                        File restrictionFile = new File(exchangeDirectory + "restriction.txt");
                        File flagRestrictionFile = new File(exchangeDirectory + "WAITRESTRICTION");
                        if (restrictionFile.exists() && flagRestrictionFile.exists() && !flagRestrictionFile.delete()) {
                            throw new RuntimeException(existFilesMessage(restrictionFile, flagRestrictionFile));
                        } else if (flagRestrictionFile.createNewFile()) {
                            processTransactionLogger.info(String.format("Kristal: creating Restriction file (Transaction #%s)", transactionInfo.id));
                            PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(restrictionFile), "windows-1251"));

                            for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                                if (!Thread.currentThread().isInterrupted()) {
                                    //boolean isWeightItem = item.passScalesItem && item.splitItem;
                                    Object code = useIdItem ? item.idItem : item.idBarcode;
                                    //String barcode = (isWeightItem ? weightPrefix : "") + (item.idBarcode == null ? "" : item.idBarcode);
                                    boolean forbid = item.flags != null && ((item.flags & 16) == 0);
                                    String record = (forbid ? "+" : "-") + "|" + code + "|" + code + "|" + "20010101" + "|" + "20210101";
                                    writer.println(record);
                                }
                            }
                            writer.close();

                            processTransactionLogger.info(String.format("Kristal: waiting for deletion of Restriction file %s (Transaction #%s)", restrictionFile.getAbsolutePath(), transactionInfo.id));
                            waitForDeletion(restrictionFile, flagRestrictionFile);
                        } else {
                            throw new RuntimeException(cantCreateFileMessage(flagRestrictionFile));
                        }
                    }

                    if(!noMessageAndScaleFiles) {
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
                            if (messageFile.exists() && flagMessageFile.exists() && !flagMessageFile.delete()) {
                                throw new RuntimeException(existFilesMessage(messageFile, flagMessageFile));
                            } else if (flagMessageFile.createNewFile()) {
                                processTransactionLogger.info(String.format("Kristal: creating MESSAGE file (Transaction #%s)", transactionInfo.id));
                                PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(messageFile), "windows-1251"));

                                for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                                    if (!Thread.currentThread().isInterrupted()) {
                                        if (item.description != null && !item.description.equals("")) {
                                            String record = "+|" + item.idBarcode + "|" + item.description.replace("\n", " ") + "|||";
                                            writer.println(record);
                                        }
                                    }
                                }
                                writer.close();
                                processTransactionLogger.info(String.format("Kristal: waiting for deletion of MESSAGE file %s (Transaction #%s)", messageFile.getAbsolutePath(), transactionInfo.id));
                                waitForDeletion(messageFile, flagMessageFile);
                            } else {
                                throw new RuntimeException(cantCreateFileMessage(flagMessageFile));
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
                            if (scaleFile.exists() && flagScaleFile.exists() && !flagScaleFile.delete()) {
                                throw new RuntimeException(existFilesMessage(scaleFile, flagScaleFile));
                            } else if (flagScaleFile.createNewFile()) {
                                processTransactionLogger.info(String.format("Kristal: creating SCALES file (Transaction #%s)", transactionInfo.id));
                                PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(scaleFile), "windows-1251"));

                                for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                                    if (!Thread.currentThread().isInterrupted() && item.passScalesItem) {
                                        String messageNumber = (item.description != null ? item.idBarcode : "0");
                                        Object pluNumber = item.pluNumber != null ? item.pluNumber : item.idBarcode;
                                        String code = useIdItem ? item.idItem : item.idBarcode;
                                        if(code.length() <= 5) { //only weight codes
                                            String record = "+|" + pluNumber + "|" + code + "|" + weightPrefix + "|" + item.name + "||" +
                                                    (item.daysExpiry == null ? "0" : item.daysExpiry) + "|1|"/*GoodLinkToScales*/ + messageNumber + "|" +
                                                    item.price;
                                            writer.println(record);
                                        }
                                    }
                                }
                                writer.close();
                                processTransactionLogger.info(String.format("Kristal: waiting for deletion of SCALES file %s (Transaction #%s)", scaleFile.getAbsolutePath(), transactionInfo.id));
                                waitForDeletion(scaleFile, flagScaleFile);

                            } else {
                                throw new RuntimeException(cantCreateFileMessage(flagScaleFile));
                            }
                        }
                    }

                    //groups.txt
                    if (transactionInfo.snapshot && importGroupType != null && !importGroupType.equals(0)) {
                        File groupsFile = new File(exchangeDirectory + "groups.txt");
                        File flagGroupsFile = new File(exchangeDirectory + "WAITGROUPS");
                        if (groupsFile.exists() && flagGroupsFile.exists() && !flagGroupsFile.delete()) {
                            throw new RuntimeException(existFilesMessage(groupsFile, flagGroupsFile));
                        } else if (flagGroupsFile.createNewFile()) {
                            processTransactionLogger.info(String.format("Kristal: creating GROUPS file (Transaction #%s)", transactionInfo.id));
                            PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(groupsFile), "windows-1251"));

                            Set<String> numberGroupItems = new HashSet<>();
                            for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                                if (!Thread.currentThread().isInterrupted()) {
                                    List<ItemGroup> hierarchyItemGroup = transactionInfo.itemGroupMap.get(item.idItemGroup);
                                    hierarchyItemGroup = hierarchyItemGroup == null ? new ArrayList<>() : Lists.reverse(hierarchyItemGroup);
                                    for (int i = hierarchyItemGroup.size() - 1; i >= 0; i--) {
                                        String idItemGroup = importGroupType.equals(1) ?
                                                makeIdItemGroup(hierarchyItemGroup.subList(0, hierarchyItemGroup.size() - i), false, true)
                                                : importGroupType.equals(2) ? String.valueOf(item.itemGroupObject)
                                                : importGroupType.equals(3) ? makeIdItemGroup(hierarchyItemGroup.subList(1, Math.min(hierarchyItemGroup.size() - i, 3)), true, true) : "";
                                        if (!numberGroupItems.contains(idItemGroup)) {
                                            String record = String.format("+|%s|%s", hierarchyItemGroup.get(hierarchyItemGroup.size() - 1 - i).nameItemGroup, idItemGroup);
                                            writer.println(record);
                                            numberGroupItems.add(idItemGroup);
                                        }
                                    }
                                }
                            }
                            writer.close();
                            processTransactionLogger.info(String.format("Kristal: waiting for deletion of GROUPS file %s (Transaction #%s)", groupsFile.getAbsolutePath(), transactionInfo.id));
                            waitForDeletion(groupsFile, flagGroupsFile);

                        } else {
                            throw new RuntimeException(cantCreateFileMessage(flagGroupsFile));
                        }
                    }
                }
            } catch (Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(transactionInfo.id, new SendTransactionBatch(exception));
        }
        return sendTransactionBatchMap;
    }
    
    private void waitForDeletion(File file, File flagFile) {
        if (flagFile.delete()) {
            int count = 0;
            while (!Thread.currentThread().isInterrupted() && file.exists()) {
                try {
                    count++;
                    if(count>=180)
                        throw new RuntimeException(createdButNotProcessedMessage(file));
                    else
                        Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw Throwables.propagate(e);
                }
            }
        }
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList,
                                 Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) throws IOException {

        KristalSettings kristalSettings = springContext.containsBean("kristalSettings") ? (KristalSettings) springContext.getBean("kristalSettings") : null;
        String exportPrefixPath = kristalSettings != null ? kristalSettings.getExportPrefixPath() : null;

        for (RequestExchange entry : requestExchangeList) {
            int count = 0;
            String requestResult = null;
            for (String directory : getDirectorySet(entry)) {

                machineryExchangeLogger.info("Kristal: creating request files for directory : " + directory);

                String dateFrom = entry.dateFrom.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String dateTo = entry.dateTo.plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

                String exchangeDirectory = directory + (exportPrefixPath == null ? "/Export" : exportPrefixPath) + "/request/";

                if (makeDirsIfNeeded(exchangeDirectory)) {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exchangeDirectory + "request.xml"), StandardCharsets.UTF_8));

                    String data = String.format("<?xml version=\"1.0\" encoding=\"windows-1251\" ?>\n" +
                            "<REPORLOAD REPORTTYPE=\"2\" >\n" +
                            "<REPORT DATEBEGIN=\"%s\" DATEEND=\"%s\"/>\n" +
                            "</REPORLOAD>", dateFrom, dateTo);

                    writer.write(data);
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
            }
        }
    }

    @Override
    public void finishReadingSalesInfo(KristalSalesBatch salesBatch) {
        sendSalesLogger.info("Kristal: Finish Reading started");
        KristalSettings kristalSettings = springContext.containsBean("kristalSettings") ? (KristalSettings) springContext.getBean("kristalSettings") : null;
        boolean deleteSuccessfulFiles = kristalSettings != null && kristalSettings.getDeleteSuccessfulFiles() != null && kristalSettings.getDeleteSuccessfulFiles();

        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);

            if (!deleteSuccessfulFiles) {
                try {
                    if (makeDirsIfNeeded(f.getParent() + "/success/")) {
                        String directory = f.getParent() + "/success/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "/";
                        if (makeDirsIfNeeded(directory))
                            FileCopyUtils.copy(f, new File(directory + f.getName()));
                    }
                } catch (IOException e) {
                    throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be copied to success files", e);
                }
            }

            if (f.delete()) {
                sendSalesLogger.info("Kristal: file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }

        for (String errorFile : salesBatch.errorFiles) {
            File f = new File(errorFile);
            try {
                if (makeDirsIfNeeded(f.getParent() + "/error/"))
                    FileCopyUtils.copy(f, new File(f.getParent() + "/error/" + f.getName()));
            } catch (IOException e) {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be copied to error files", e);
            }

            if (f.delete()) {
                sendSalesLogger.info("Kristal: file " + errorFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public Map<String, LocalDateTime> requestSucceededSoftCheckInfo(List<String> directoryList) throws ClassNotFoundException, SQLException {

        softCheckLogger.info("Kristal: requesting succeeded SoftCheckInfo");

        KristalSettings kristalSettings = springContext.containsBean("kristalSettings") ? (KristalSettings) springContext.getBean("kristalSettings") : null;

        Map<String, LocalDateTime> result = new HashMap<>();
        //result.put("888888", new Timestamp(Calendar.getInstance().getTime().getTime()));
        //return result;

        if(kristalSettings == null) {
            softCheckLogger.error("No kristalSettings found");
        } else {
            for (String sqlHost : kristalSettings.sqlHost.values()) {
                Connection conn = null;
                try {

                    softCheckLogger.info("Kristal: connection to " + sqlHost);

                    String url = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;User=%s;Password=%s",
                            sqlHost, kristalSettings.sqlPort, kristalSettings.sqlDBName, kristalSettings.sqlUsername, kristalSettings.sqlPassword);
                    Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                    conn = DriverManager.getConnection(url);
                    Statement statement = conn.createStatement();
                    String queryString = "SELECT DocNumber, DateTimePosting FROM DocHead WHERE StatusNotUsed='1' AND PayState='1' AND Cash_Code IS NOT NULL";
                    ResultSet rs = statement.executeQuery(queryString);
                    int count = 0;
                    while (rs.next()) {
                        result.put(fillLeadingZeroes(rs.getString(1)), sqlTimestampToLocalDateTime(rs.getTimestamp(2)));
                        count++;
                    }

                    softCheckLogger.info("Kristal: found " + count + " SoftCheckInfo");

                } catch (SQLException e) {
                    softCheckLogger.error("Kristal SoftCheck: ", e);
                } finally {
                    if (conn != null)
                        conn.close();
                }
            }
        }
        return result;
    }

    @Override
    public List<CashierTime> requestCashierTime(RequestExchange requestExchange, List<MachineryInfo> cashRegisterInfoList) throws ClassNotFoundException, SQLException {

        machineryExchangeLogger.info("Kristal: requesting CashierTime");

        Map<String, Integer> directoryGroupCashRegisterMap = new HashMap<>();
        for (MachineryInfo c : cashRegisterInfoList) {
            if (c.number != null && c.numberGroup != null && c.directory != null) {
                directoryGroupCashRegisterMap.put(c.directory.toLowerCase() + "_" + c.number, c.numberGroup);
            }
        }

        KristalSettings kristalSettings = springContext.containsBean("kristalSettings") ? (KristalSettings) springContext.getBean("kristalSettings") : null;

        List<CashierTime> result = new ArrayList<>();

        if (kristalSettings == null) {
            machineryExchangeLogger.error("Kristal CashierTime: No kristalSettings found");
        } else {
            for (Map.Entry<String, String> sqlHostEntry : kristalSettings.sqlHost.entrySet()) {
                Connection conn = null;
                try {

                    String dir = sqlHostEntry.getKey();
                    String sqlHost = sqlHostEntry.getValue();
                    machineryExchangeLogger.info("Kristal CashierTime: connection to " + sqlHost);

                    String url = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;User=%s;Password=%s",
                            sqlHost, kristalSettings.sqlPort, kristalSettings.sqlDBName, kristalSettings.sqlUsername, kristalSettings.sqlPassword);
                    Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                    conn = DriverManager.getConnection(url);

                    int start = 1;
                    int limit = 10000;
                    Integer lastCount = null;
                    while (lastCount == null || lastCount == limit) {
                        List<CashierTime> cashierTimeList = readCashierTime(conn, directoryGroupCashRegisterMap, requestExchange, dir, start, limit);
                        result.addAll(cashierTimeList);
                        lastCount = cashierTimeList.size();
                        start += lastCount;
                    }
                    result.addAll(readCashierTimeZReport(conn, directoryGroupCashRegisterMap, requestExchange, dir));

                    Timestamp dateTo = null;
                    for (int i = result.size() - 1; i >= 0; i--) {
                        CashierTime ct = result.get(i);
                        if (ct.logOffCashier == null) {
                            ct.logOffCashier = sqlTimestampToLocalDateTime(getLogOffTime(conn, ct.numberCashier, ct.numberCashRegister, localDateTimeToSqlTimestamp(ct.logOnCashier), dateTo));
                        }
                        ct.idCashierTime = ct.numberCashier + "/" + ct.numberCashRegister + "/" + ct.logOnCashier.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")) + "/" + ct.logOffCashier.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")) + "/" + (ct.isZReport != null ? "1" : "0");
                        dateTo = localDateTimeToSqlTimestamp(ct.logOnCashier);
                    }

                    machineryExchangeLogger.info(String.format("Kristal CashierTime: server %s, found %s entries", sqlHost, result.size()));
                } catch (SQLException e) {
                    machineryExchangeLogger.error("Kristal CashierTime: ", e);
                } finally {
                    if (conn != null)
                        conn.close();
                }
            }
        }
        return result;
    }

    private List<CashierTime> readCashierTime(Connection conn, Map<String, Integer> directoryGroupCashRegisterMap, RequestExchange requestExchange, String dir, int start, int limit) throws SQLException {
        List<CashierTime> result = new ArrayList<>();
        try (Statement statement = conn.createStatement()) {

            String where1 = "";
            if (requestExchange.dateFrom != null)
                where1 = "WHERE " + "LogOn >= '" + requestExchange.dateFrom + "'";
            if (requestExchange.dateTo != null)
                where1 += (where1.isEmpty() ? "WHERE " : " AND ") + " LogOn <= '" + requestExchange.dateTo + "'";
            String where2 = "";
            if(requestExchange.dateFrom != null)
                where2 += " AND LogOn >= '" + requestExchange.dateFrom + "'";
            if(requestExchange.dateTo != null)
                where2 += " AND LogOn <= '" + requestExchange.dateTo + "'";

            String queryString = String.format("SELECT CashierTabNumber, CashNumber,  LogOn, LogOff FROM (" +
                    "SELECT CashierTabNumber, CashNumber,  LogOn, LogOff, ROW_NUMBER() OVER (ORDER BY ID) AS RowNum " +
                    "FROM CashierWorkTime %s) AS MyDerivedTable WHERE (MyDerivedTable.RowNum BETWEEN %s AND %s)%s",
                    where1, start, start + limit - 1, where2);
            machineryExchangeLogger.info("Kristal CashierTime: Executing query " + queryString);
            ResultSet rs = statement.executeQuery(queryString);
            while (rs.next()) {

                String numberCashier = rs.getString(1);
                Integer numberCashRegister = rs.getInt(2);
                Timestamp timeFrom = rs.getTimestamp(3);
                Timestamp timeTo = rs.getTimestamp(4);
                result.add(new CashierTime(null, numberCashier, numberCashRegister,
                        directoryGroupCashRegisterMap.get(dir + "_" + numberCashRegister), sqlTimestampToLocalDateTime(timeFrom), sqlTimestampToLocalDateTime(timeTo), null));
            }
        }
        return result;
    }

    private List<CashierTime> readCashierTimeZReport(Connection conn, Map<String, Integer> directoryGroupCashRegisterMap, RequestExchange requestExchange, String dir) throws SQLException {
        List<CashierTime> result = new ArrayList<>();
        try (Statement statement = conn.createStatement()) {
            String queryString = "SELECT CashNumber, GangDateStart, GangDateStop FROM OperGang";
            if(requestExchange.dateFrom != null || requestExchange.dateTo != null)
                queryString += " WHERE";
            if(requestExchange.dateFrom != null)
                queryString += " GangDateStart >= '" + requestExchange.dateFrom + "'";
            if(requestExchange.dateTo != null)
                queryString += " AND GangDateStart <= '" + requestExchange.dateTo + "'";
            machineryExchangeLogger.info("Kristal CashierTime: Executing query " + queryString);
            ResultSet rs = statement.executeQuery(queryString);
            while (rs.next()) {
                Integer numberCashRegister = rs.getInt(1);
                Timestamp timeFrom = rs.getTimestamp(2);
                Timestamp timeTo = rs.getTimestamp(3);
                if (timeTo != null)
                    result.add(new CashierTime(null, null, numberCashRegister,
                            directoryGroupCashRegisterMap.get(dir + "_" + numberCashRegister), sqlTimestampToLocalDateTime(timeFrom), sqlTimestampToLocalDateTime(timeTo), true));
            }
        }
        return result;
    }

    //берём max (время последнего чека, время ребута между началом этой сессии и началом следующей)
    //если оба null, берём время начала следующей сессии
    private Timestamp getLogOffTime(Connection conn, String numberCashier, Integer numberCashRegister, Timestamp dateFrom, Timestamp dateTo) throws SQLException {
        Timestamp lastCheque = null;

        Statement statement = conn.createStatement();
        String queryString = "SELECT MAX(dateOperation) FROM ChequeHead WHERE cassir = '" + numberCashier +
                "' AND dateOperation >= {ts '" + dateFrom + "'}" + (dateTo == null ? "" : " AND dateOperation <= {ts '" + dateTo + "'}");
        machineryExchangeLogger.info("Kristal CashierTime: Executing query " + queryString);
        ResultSet rs = statement.executeQuery(queryString);
        if (rs.next())
            lastCheque = rs.getTimestamp(1);

        Timestamp lastReboot = null;
        statement = conn.createStatement();
        machineryExchangeLogger.info("Kristal CashierTime: Executing query " + queryString);
        queryString = "SELECT MAX(DateOccure) FROM ErrorLog WHERE DeviceId = " + numberCashRegister + " AND Code = 1019" +
                " AND DateOccure >= {ts '" + dateFrom + "'}" + (dateTo == null ? "" : " AND DateOccure <= {ts '" + dateTo + "'}");
        rs = statement.executeQuery(queryString);
        if (rs.next())
            lastReboot = rs.getTimestamp(1);

        return lastCheque != null ? (lastReboot != null ? (lastCheque.compareTo(lastReboot) > 0  ? lastCheque : lastReboot) : lastCheque) : (lastReboot != null ? lastReboot : dateTo);
    }

    @Override
    public List<List<Object>> checkZReportSum(Map<String, List<Object>> zReportSumMap, List<List<Object>> cashRegisterList) throws ClassNotFoundException, SQLException {
        List<List<Object>> result = new ArrayList<>();
        
        KristalSettings kristalSettings = springContext.containsBean("kristalSettings") ? (KristalSettings) springContext.getBean("kristalSettings") : null;
        if(kristalSettings == null) {
            requestExchangeLogger.error("No kristalSettings found");
        } else {

            for (Map.Entry<String, String> sqlHostEntry : kristalSettings.sqlHost.entrySet()) {
                String dir = sqlHostEntry.getKey();
                String host = sqlHostEntry.getValue();

                Connection conn = null;
                try {

                    StringBuilder nppCashRegisters = new StringBuilder();
                    for (List<Object> cashRegisterEntry : cashRegisterList) {
                        Integer nppCashRegister = cashRegisterEntry.size() >= 1 ? (Integer) cashRegisterEntry.get(0) : null;
                        String directory = cashRegisterEntry.size() >= 2 ? (String) cashRegisterEntry.get(1) : null;
                        if (directory != null) {
                            directory = directory.toLowerCase();
                            if (directory.contains(dir) || dir.equals(host)) //dir.equals(host) - old host format, without dir
                                nppCashRegisters.append(nppCashRegister).append(",");
                        }
                    }
                    nppCashRegisters = new StringBuilder((nppCashRegisters.length() == 0) ? nppCashRegisters.toString() : nppCashRegisters.substring(0, nppCashRegisters.length() - 1));
                    if (nppCashRegisters.length() > 0) {
                        requestExchangeLogger.info("Kristal: checking zReports sum, CashRegisters: " + nppCashRegisters);

                        String url = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;User=%s;Password=%s",
                                host, kristalSettings.sqlPort, kristalSettings.sqlDBName, kristalSettings.sqlUsername, kristalSettings.sqlPassword);
                        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                        conn = DriverManager.getConnection(url);
                        Statement statement = conn.createStatement();
                        String queryString = "SELECT GangNumber, CashNumber, Summa, GangDateStart FROM OperGang WHERE CashNumber IN (" + nppCashRegisters + ")";
                        ResultSet rs = statement.executeQuery(queryString);
                        while (rs.next()) {
                            String numberZReport = String.valueOf(rs.getInt(1));
                            Integer nppCashRegister = rs.getInt(2);
                            String key = numberZReport + "/" + nppCashRegister;
                            if (zReportSumMap.containsKey(key)) {
                                List<Object> fusionEntry = zReportSumMap.get(key);
                                BigDecimal fusionSum = (BigDecimal) fusionEntry.get(0);
                                fusionSum = fusionSum == null ? null : fusionSum.setScale(2, RoundingMode.HALF_UP);
                                LocalDate fusionDate = (LocalDate) fusionEntry.get(1);
                                String nameDepartmentStore = (String) fusionEntry.get(2);
                                BigDecimal kristalSum = BigDecimal.valueOf(rs.getDouble(3)).setScale(2, RoundingMode.HALF_UP);
                                LocalDate kristalDate = sqlDateToLocalDate(rs.getDate(4));
                                if (fusionSum == null || fusionSum.compareTo(kristalSum) != 0) {
                                    if (kristalDate.compareTo(fusionDate) == 0) {
                                        result.add(Arrays.asList(nppCashRegister,
                                                String.format("ZReport %s (store %s, date %s).\nChecksum failed: %s(fusion) != %s(kristal);\n", numberZReport, nameDepartmentStore, kristalDate, fusionSum, kristalSum)));
                                        requestExchangeLogger.error(String.format("%s. Store %s. CashRegister %s. ZReport %s checksum failed: %s(fusion) != %s(kristal);", kristalDate, nameDepartmentStore, nppCashRegister, numberZReport, fusionSum, kristalSum));
                                    } else {
                                        requestExchangeLogger.error(String.format("Not equal dates for CashRegister %s, ZReport %s (%s(fusion) and %s (kristal);", nppCashRegister, numberZReport, fusionDate, kristalDate));
                                    }
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null)
                        conn.close();
                }
            }
        }
        if(result.isEmpty())
            requestExchangeLogger.info("No errors");
        return result.isEmpty() ? null : result;
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException {

        KristalSettings kristalSettings = springContext.containsBean("kristalSettings") ? (KristalSettings) springContext.getBean("kristalSettings") : null;
        Integer lastDaysCashDocument = kristalSettings != null ? kristalSettings.getLastDaysCashDocument() : null;

        List<CashDocument> result = new ArrayList<>();

        if (kristalSettings == null) {
            sendSalesLogger.info("No kristalSettings found");
        } else {
            for (Map.Entry<String, String> sqlHostEntry : kristalSettings.sqlHost.entrySet()) {
                Connection conn = null;
                try {

                    String dir = sqlHostEntry.getKey();
                    String host = sqlHostEntry.getValue();

                    Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
                    for (CashRegisterInfo c : cashRegisterInfoList) {
                        if (c.number != null && c.numberGroup != null && c.directory != null) {
                            String directory = c.directory.toLowerCase();
                            if (directory.contains(dir) || dir.equals(host)) {
                                directoryCashRegisterMap.put(dir + "_" + c.number, c);
                            }
                        }
                    }

                    String url = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;User=%s;Password=%s",
                            host, kristalSettings.sqlPort, kristalSettings.sqlDBName, kristalSettings.sqlUsername, kristalSettings.sqlPassword);
                    Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                    sendSalesLogger.info("Kristal CashDocument connection: " + url);

                    conn = DriverManager.getConnection(url);
                    Statement statement = conn.createStatement();
                    String queryString = "SELECT Ck_Number, Ck_Date, Ck_Summa, CashNumber, Ck_NSmena FROM OperGangMoney WHERE Taken='1'";
                    if (lastDaysCashDocument != null) {
                        queryString += " AND Ck_Date >='" + LocalDate.now().minusDays(lastDaysCashDocument).format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "'";
                    }
                    ResultSet rs = statement.executeQuery(queryString);
                    while (rs.next()) {
                        String number = rs.getString("Ck_Number");
                        Integer ckNSmena = rs.getInt("Ck_NSmena");
                        Timestamp dateTime = rs.getTimestamp("Ck_Date");
                        Date date = new Date(dateTime.getTime());
                        Time time = new Time(dateTime.getTime());

                        Integer nppMachinery = rs.getInt("CashNumber");
                        CashRegisterInfo cashRegister = directoryCashRegisterMap.get(dir + "_" + nppMachinery);
                        if (cashRegister != null) {
                            BigDecimal sum = rs.getBigDecimal("Ck_Summa");
                            String idCashDocument = host + "/" + nppMachinery + "/" + number + "/" + ckNSmena;
                            if (!cashDocumentSet.contains(idCashDocument))
                                result.add(new CashDocument(idCashDocument, number, sqlDateToLocalDate(date), sqlTimeToLocalTime(time), cashRegister.numberGroup, nppMachinery, null, sum));
                        }
                    }
                } catch (SQLException e) {
                    sendSalesLogger.error("Kristal Error: ", e);
                } finally {
                    try {
                        if (conn != null)
                            conn.close();
                    } catch (SQLException e) {
                        sendSalesLogger.error("Kristal Error: ", e);
                    }
                }
            }
            if (result.size() == 0)
                sendSalesLogger.info("Kristal: no CashDocuments found");
            else
                sendSalesLogger.info(String.format("Kristal: found %s CashDocument(s)", result.size()));
        }
        return new CashDocumentBatch(result, null);
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {

        if (!stopListInfo.exclude) {
            processStopListLogger.info("Kristal: Send StopList # " + stopListInfo.number);

            KristalSettings kristalSettings = springContext.containsBean("kristalSettings") ? (KristalSettings) springContext.getBean("kristalSettings") : null;
            boolean useIdItem = kristalSettings == null || kristalSettings.getUseIdItem() != null && kristalSettings.getUseIdItem();
            String importPrefixPath = kristalSettings != null ? kristalSettings.getImportPrefixPath() : null;

            for (String directory : directorySet) {

                String exchangeDirectory = directory + (importPrefixPath == null ? "/ImpExp/Import/" : importPrefixPath);
                makeDirsIfNeeded(exchangeDirectory);

                //stopList.txt
                File stopListFile = new File(exchangeDirectory + "stoplist.txt");
                File flagStopListFile = new File(exchangeDirectory + "WAITSTOPLIST");
                if (flagStopListFile.exists() && !flagStopListFile.delete())
                    flagStopListFile.deleteOnExit();
                if (stopListFile.exists() && flagStopListFile.exists()) {
                    throw new RuntimeException(existFilesMessage(stopListFile, flagStopListFile));
                } else {
                    if (flagStopListFile.createNewFile()) {
                        processStopListLogger.info("Kristal: creating STOPLIST file");
                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(stopListFile), "windows-1251"));

                        for (Map.Entry<String, ItemInfo> item : stopListInfo.stopListItemMap.entrySet()) {
                            String idBarcode = item.getKey();
                            String code = useIdItem ? item.getValue().idItem : idBarcode;
                            String record = (stopListInfo.exclude ? "+" : "-") + "|" + code + "|" + idBarcode;
                            writer.println(record);
                        }
                        writer.close();

                        processStopListLogger.info("Kristal: waiting for deletion of STOPLIST file " + stopListFile.getAbsolutePath());
                        waitForDeletion(stopListFile, flagStopListFile);
                    } else {
                        throw new RuntimeException(cantCreateFileMessage(flagStopListFile));
                    }
                }
            }
        }
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) throws IOException {
        machineryExchangeLogger.info("Kristal: Send DiscountCardList");

        KristalSettings kristalSettings = springContext.containsBean("kristalSettings") ? (KristalSettings) springContext.getBean("kristalSettings") : null;
        String importPrefixPath = kristalSettings != null ? kristalSettings.getImportPrefixPath() : null;

        for (String directory : getDirectorySet(requestExchange)) {
            String exchangeDirectory = directory + (importPrefixPath == null ? "/ImpExp/Import/" : importPrefixPath);
            makeDirsIfNeeded(exchangeDirectory);

            //discountCard.txt
            File discCardFile = new File(exchangeDirectory + "disccard.txt");
            File flagDiscCardFile = new File(exchangeDirectory + "WAITDISCCARD");
            if (discCardFile.exists() && flagDiscCardFile.exists() && !flagDiscCardFile.delete()) {
                throw new RuntimeException(existFilesMessage(discCardFile, flagDiscCardFile));
            } else if (flagDiscCardFile.createNewFile()) {
                machineryExchangeLogger.info("Kristal: creating DISCCARD file " + discCardFile.getAbsolutePath());
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(discCardFile), "windows-1251"));

                for (DiscountCard card : discountCardList) {
                    boolean active = requestExchange.startDate == null || (card.dateFromDiscountCard != null && card.dateFromDiscountCard.compareTo(requestExchange.startDate) >= 0);
                    if (active) {
                        String record = String.format("+|%s|%s|1|%s|%s|3", trimToEmpty(card.numberDiscountCard), trimToEmpty(card.nameDiscountCard),
                                card.percentDiscountCard == null ? 0 : card.percentDiscountCard.intValue(), formatCardNumber(card.idDiscountCard));
                        writer.println(record);
                    }
                }
                writer.close();

                machineryExchangeLogger.info("Kristal: waiting for deletion of DISCCARD file " + discCardFile.getAbsolutePath());
                waitForDeletion(discCardFile, flagDiscCardFile);
            } else {
                throw new RuntimeException(cantCreateFileMessage(flagDiscCardFile));
            }
        }
    }

    String formatCardNumber(String value) {
        value = trimToEmpty(value);
        while (value.startsWith("0"))
            value = value.substring(1);
        return value;
    }

    @Override
    public void sendCashierInfoList(List<CashierInfo> cashierInfoList, RequestExchange requestExchange) throws IOException {
        machineryExchangeLogger.info("Kristal: Send CashierInfoList");

        KristalSettings kristalSettings = springContext.containsBean("kristalSettings") ? (KristalSettings) springContext.getBean("kristalSettings") : null;
        String importPrefixPath = kristalSettings != null ? kristalSettings.getImportPrefixPath() : null;
        String idPositionCashier = kristalSettings != null ? kristalSettings.getIdPositionCashier() : null;

        for (Map.Entry<String, Set<String>> entry : getDirectoryStockMap(requestExchange).entrySet()) {
            String directory = entry.getKey();
            Set<String> stockSet = entry.getValue();

            String exchangeDirectory = directory + (importPrefixPath == null ? "/ImpExp/Import/" : importPrefixPath);
            makeDirsIfNeeded(exchangeDirectory);

            //cashier.txt
            File cashierFile = new File(exchangeDirectory + "cashier.txt");
            File flagCashierFile = new File(exchangeDirectory + "WAITCASHIER");
            if (cashierFile.exists() && flagCashierFile.exists() && !flagCashierFile.delete()) {
                throw new RuntimeException(existFilesMessage(cashierFile, flagCashierFile));
            } else if (flagCashierFile.createNewFile()) {
                machineryExchangeLogger.info("Kristal: creating CASHIER file " + cashierFile.getAbsolutePath());
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(cashierFile), "windows-1251"));

                for (CashierInfo cashier : cashierInfoList) {
                    if((idPositionCashier == null || idPositionCashier.equals(cashier.idPosition)) && stockSet.contains(cashier.idStock)) {
                        String record = String.format("+|%s|%s|%s|0|:::::::::::::::::::::::::", cashier.numberCashier, cashier.nameCashier, cashier.numberCashier);
                        writer.println(record);
                    }
                }
                writer.close();

                machineryExchangeLogger.info("Kristal: waiting for deletion of CASHIER file " + cashierFile.getAbsolutePath());
                waitForDeletion(cashierFile, flagCashierFile);
            } else {
                throw new RuntimeException(cantCreateFileMessage(flagCashierFile));
            }
        }
    }

    @Override
    public KristalSalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {

        KristalSettings kristalSettings = springContext.containsBean("kristalSettings") ? (KristalSettings) springContext.getBean("kristalSettings") : null;
        String exportPrefixPath = kristalSettings != null ? kristalSettings.getExportPrefixPath() : null;
        boolean useIdItem = kristalSettings != null && kristalSettings.getUseIdItem() != null && kristalSettings.getUseIdItem();
        String transformUPCBarcode = kristalSettings == null ? null : kristalSettings.getTransformUPCBarcode();
        boolean useCheckNumber = kristalSettings != null && kristalSettings.getUseCheckNumber() != null && kristalSettings.getUseCheckNumber();
        Integer maxFilesCount = kristalSettings == null ? null : kristalSettings.getMaxFilesCount();

        Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
        Map<String, Boolean> directoryNotDetailedMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c) && c.directory != null) {
                directoryNotDetailedMap.put(c.directory, c.notDetailed);
                if (c.number != null)
                    directoryCashRegisterMap.put(c.directory + "_" + c.number, c);
            }
        }

        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<String> readFiles = new ArrayList<>();
        List<String> errorFiles = new ArrayList<>();
        final Boolean notDetailed = directoryNotDetailedMap.get(directory);

        String exchangeDirectory = directory + (exportPrefixPath == null ? "/Export/" : exportPrefixPath);

        File[] filesList = new File(exchangeDirectory).listFiles(pathname -> pathname != null && pathname.getName().startsWith(notDetailed ? "ReportGang1C" : "ReportCheque1C") && pathname.getPath().endsWith(".xml"));

        File[] deletingFilesList = new File(exchangeDirectory).listFiles(pathname -> pathname != null && pathname.getName().startsWith(notDetailed ? "ReportCheque1C" : "ReportGang1C") && pathname.getPath().endsWith(".xml"));

        if (deletingFilesList != null) {
            for (File file : deletingFilesList) {
                readFiles.add(file.getAbsolutePath());
            }
        }


        if (filesList == null || filesList.length == 0)
            sendSalesLogger.info("Kristal: No checks found in " + exchangeDirectory);
        else {
            if(maxFilesCount == null)
                sendSalesLogger.info(String.format("Kristal: found %s file(s) in %s", filesList.length, exchangeDirectory));
            else
                sendSalesLogger.info(String.format("Kristal: found %s file(s) in %s, will read %s file(s)", filesList.length, exchangeDirectory, Math.min(filesList.length, maxFilesCount)));

            int filesCount = 0;
            for (File file : filesList) {
                filesCount++;
                if(maxFilesCount != null && maxFilesCount < filesCount)
                    break;
                try {
                    if(file.length() == 0) {
                        //file is empty
                        if(!file.delete())
                            file.deleteOnExit();
                        continue;
                    }
                    String fileName = file.getName();
                    sendSalesLogger.info("Kristal: reading " + fileName);
                    if (isFileLocked(file)) {
                        sendSalesLogger.info("Kristal: " + fileName + " is locked");
                    } else {

                        Sales sales = readSalesFile(file, fileName, directoryCashRegisterMap, directory, transformUPCBarcode, notDetailed, useIdItem, useCheckNumber);
                        if (!sales.failed) {
                            salesInfoList.addAll(sales.salesInfoList);
                            readFiles.add(file.getAbsolutePath());
                        } else {
                            sendSalesLogger.error(String.format("Exception while reading file %s, retry in 1 second", fileName));
                            Thread.sleep(1000);
                            sales = readSalesFile(file, fileName, directoryCashRegisterMap, directory, transformUPCBarcode, notDetailed, useIdItem, useCheckNumber);
                            salesInfoList.addAll(sales.salesInfoList);
                            if (!sales.failed)
                                readFiles.add(file.getAbsolutePath());
                            else
                                errorFiles.add(file.getAbsolutePath());
                        }

                    }
                } catch (Throwable e) {
                    errorFiles.add(file.getAbsolutePath());
                    sendSalesLogger.error("File: " + file.getAbsolutePath(), e);
                }
            }
        }
        return (salesInfoList.isEmpty() && readFiles.isEmpty() && errorFiles.isEmpty() ? null :
                new KristalSalesBatch(salesInfoList, readFiles, errorFiles));
    }

    private Sales readSalesFile(File file, String fileName, Map<String, CashRegisterInfo> directoryCashRegisterMap, String directory,
                                 String transformUPCBarcode, boolean notDetailed, boolean useIdItem, boolean useCheckNumber) {
        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        boolean failed = false;

        try {
            SAXBuilder builder = new SAXBuilder();

            Document document;
            try (FileReader fileReader = new FileReader(file)) {
                document = builder.build(fileReader);
            }
            Element rootNode = document.getRootElement();
            List<Element> daysList = rootNode.getChildren("DAY");

            for (Object dayNode : daysList) {

                List<Element> shopsList = ((Element) dayNode).getChildren("SHOP");

                for (Object shopNode : shopsList) {

                    List<Element> cashesList = ((Element) shopNode).getChildren("CASH");

                    for (Object cashNode : cashesList) {

                        Integer numberCashRegister = readIntegerXMLAttribute((Element) cashNode, "CASHNUMBER");
                        List<Element> gangsList = ((Element) cashNode).getChildren("GANG");

                        if (notDetailed) {

                            //not detailed

                            for (Object gangNode : gangsList) {

                                String numberZReport = ((Element) gangNode).getAttributeValue("GANGNUMBER");

                                Integer numberReceipt = readIntegerXMLAttribute((Element) gangNode, "CHEQUENUMBERFIRST");//всё пишем по номеру первого чека

                                CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory + "_" + numberCashRegister);
                                String weightCode = cashRegister == null ? null : cashRegister.weightCodeGroupCashRegister;
                                LocalDate startDate = cashRegister == null ? null : cashRegister.startDate;

                                long dateTimeReceipt = DateUtils.parseDate(((Element) gangNode).getAttributeValue("GANGDATESTART"), "dd.MM.yyyy HH:mm:ss").getTime();
                                LocalDate dateReceipt = sqlDateToLocalDate(new Date(dateTimeReceipt));
                                LocalTime timeReceipt = sqlTimeToLocalTime(new Time(dateTimeReceipt));

                                BigDecimal discountSumReceipt = readBigDecimalXMLAttribute((Element) gangNode, "DISCSUMM");

                                List<Element> receiptDetailsList = ((Element) gangNode).getChildren("GOOD");
                                List<Element> paymentsList = ((Element) gangNode).getChildren("PAYMENT");

                                BigDecimal sumCard = BigDecimal.ZERO;
                                BigDecimal sumCash = BigDecimal.ZERO;
                                for (Object paymentNode : paymentsList) {
                                    Element paymentElement = (Element) paymentNode;
                                    if (paymentElement.getAttributeValue("PAYMENTTYPE").equals("Наличный расчет")) {
                                        sumCash = HandlerUtils.safeAdd(sumCash, readBigDecimalXMLAttribute(paymentElement, "SUMMASALE"));
                                    } else if (paymentElement.getAttributeValue("PAYMENTTYPE").equals("Безналичный слип")) {
                                        sumCard = HandlerUtils.safeAdd(sumCard, readBigDecimalXMLAttribute(paymentElement, "SUMMASALE"));
                                    }
                                }

                                List<SalesInfo> currentSalesInfoList = new ArrayList<>();
                                BigDecimal currentPaymentSum = BigDecimal.ZERO;
                                Integer numberReceiptDetail = 0;
                                for (Object receiptDetailNode : receiptDetailsList) {

                                    Element receiptDetailElement = (Element) receiptDetailNode;

                                    String barcode = transformUPCBarcode(receiptDetailElement.getAttributeValue("BARCODE"), transformUPCBarcode);
                                    if (barcode != null && weightCode != null && (barcode.length() == 13 || barcode.length() == 7) && barcode.startsWith(weightCode))
                                        barcode = barcode.substring(2, 7);
                                    String idItem = useIdItem ? receiptDetailElement.getAttributeValue("CODE") : null;
                                    BigDecimal quantity = readBigDecimalXMLAttribute(receiptDetailElement, "QUANTITY");
                                    BigDecimal price = readBigDecimalXMLAttribute(receiptDetailElement, "PRICE");
                                    BigDecimal sumReceiptDetail = readBigDecimalXMLAttribute(receiptDetailElement, "SUMMA");
                                    currentPaymentSum = HandlerUtils.safeAdd(currentPaymentSum, sumReceiptDetail);
                                    numberReceiptDetail++;

                                    if (dateReceipt == null || startDate == null || dateReceipt.compareTo(startDate) >= 0) {
                                        Integer nppGroupMachinery = getNppGroupMachinery(directoryCashRegisterMap, directory, numberCashRegister);
                                        if (nppGroupMachinery == null) {
                                            String error = String.format("Kristal: nppGroupMachinery not found. directory : %s, numberCashRegister %s, file %s", directory, numberCashRegister, file);
                                            if(!errors.contains(error)) {
                                                errors.add(error);
                                                sendSalesLogger.error(error);
                                            }
                                            failed = true;
                                        } else
                                            currentSalesInfoList.add(getSalesInfo(nppGroupMachinery, numberCashRegister, numberZReport, dateReceipt, timeReceipt,
                                                    numberReceipt, dateReceipt, timeReceipt, null, null, sumCard, sumCash, null, null, barcode, idItem, null,
                                                    null, quantity, price, sumReceiptDetail, null, discountSumReceipt, null, numberReceiptDetail, fileName,
                                                    null, null, cashRegister));
                                    }
                                }

                                //чит для случая, когда не указана сумма платежа. Недостающую сумму пишем в наличные.
                                BigDecimal sum = HandlerUtils.safeAdd(sumCard, sumCash);
                                if (sum == null || sum.compareTo(currentPaymentSum) != 0)
                                    for (SalesInfo salesInfo : currentSalesInfoList) {
                                        salesInfo.sumCash = HandlerUtils.safeSubtract(currentPaymentSum, sumCard);
                                    }

                                salesInfoList.addAll(currentSalesInfoList);
                            }
                        } else {

                            //detailed

                            for (Object gangNode : gangsList) {

                                String numberZReport = ((Element) gangNode).getAttributeValue("GANGNUMBER");
                                List<Element> receiptsList = ((Element) gangNode).getChildren("HEAD");

                                for (Object receiptNode : receiptsList) {

                                    Element receiptElement = (Element) receiptNode;

                                    Integer numberReceipt = readIntegerXMLAttribute(receiptElement, useCheckNumber ? "CK_NUMBER" : "ID");
                                    //BigDecimal discountSumReceipt = readBigDecimalXMLAttribute(receiptElement, "DISCSUMM");
                                    long dateTimeReceipt = DateUtils.parseDate(receiptElement.getAttributeValue("DATEOPERATION"), "dd.MM.yyyy HH:mm:ss").getTime();
                                    LocalDate dateReceipt = sqlDateToLocalDate(new Date(dateTimeReceipt));
                                    LocalTime timeReceipt = sqlTimeToLocalTime(new Time(dateTimeReceipt));
                                    String idEmployee = receiptElement.getAttributeValue("CASSIR");

                                    CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory + "_" + numberCashRegister);
                                    String weightCode = cashRegister == null ? null : cashRegister.weightCodeGroupCashRegister;
                                    LocalDate startDate = cashRegister == null ? null : cashRegister.startDate;

                                    List<Element> receiptDetailsList = (receiptElement).getChildren("POS");
                                    List<Element> paymentsList = (receiptElement).getChildren("PAY");

                                    BigDecimal sumCard = BigDecimal.ZERO;
                                    BigDecimal sumCash = BigDecimal.ZERO;
                                    for (Object paymentNode : paymentsList) {
                                        Element paymentElement = (Element) paymentNode;
                                        String payment = paymentElement.getAttributeValue("PAYTYPE");
                                        if (payment.equals("0")) {
                                            sumCash = HandlerUtils.safeAdd(sumCash, readBigDecimalXMLAttribute(paymentElement, "DOCSUMM"));
                                        } else if (payment.equals("1") || payment.equals("3")) {
                                            sumCard = HandlerUtils.safeAdd(sumCard, readBigDecimalXMLAttribute(paymentElement, "DOCSUMM"));
                                        }
                                    }

                                    List<SalesInfo> currentSalesInfoList = new ArrayList<>();
                                    BigDecimal currentPaymentSum = BigDecimal.ZERO;
                                    for (Object receiptDetailNode : receiptDetailsList) {

                                        Element receiptDetailElement = (Element) receiptDetailNode;

                                        String barcode;
                                        String idItem = null;
                                        if (useIdItem) {
                                            barcode = transformUPCBarcode(receiptDetailElement.getAttributeValue("BARCODE"), transformUPCBarcode);
                                            idItem = receiptDetailElement.getAttributeValue("CODE");
                                        } else
                                            barcode = transformUPCBarcode(receiptDetailElement.getAttributeValue("CODE"), transformUPCBarcode);
                                        if (barcode != null && weightCode != null && (barcode.length() == 13 || barcode.length() == 7) && barcode.startsWith(weightCode))
                                            barcode = barcode.substring(2, 7);
                                        BigDecimal quantity = readBigDecimalXMLAttribute(receiptDetailElement, "QUANTITY");
                                        BigDecimal price = readBigDecimalXMLAttribute(receiptDetailElement, "PRICEWITHOUTDISC");
                                        BigDecimal sumReceiptDetail = readBigDecimalXMLAttribute(receiptDetailElement, "SUMMA");
                                        currentPaymentSum = HandlerUtils.safeAdd(currentPaymentSum, sumReceiptDetail);
                                        BigDecimal discountSumReceiptDetail = readBigDecimalXMLAttribute(receiptDetailElement, "DISCSUMM");
                                        discountSumReceiptDetail = (discountSumReceiptDetail != null && quantity != null && quantity.compareTo(BigDecimal.ZERO) < 0) ? discountSumReceiptDetail.negate() : discountSumReceiptDetail;
                                        Integer numberReceiptDetail = readIntegerXMLAttribute(receiptDetailElement, "POSNUMBER");

                                        String discountCard = null;
                                        List<Element> discountCardList = receiptDetailElement.getChildren("DSC");
                                        for (Object card : discountCardList) {
                                            if (discountCard == null || discountCard.isEmpty())
                                                discountCard = ((Element) card).getAttributeValue("CARDNUMBER");
                                        }
                                        if (discountCard != null && discountCard.isEmpty())
                                            discountCard = null;

                                        if (dateReceipt == null || startDate == null || dateReceipt.compareTo(startDate) >= 0) {
                                            Integer nppGroupMachinery = getNppGroupMachinery(directoryCashRegisterMap, directory, numberCashRegister);
                                            if (nppGroupMachinery == null) {
                                                String error = String.format("Kristal: nppGroupMachinery not found. directory : %s, numberCashRegister %s, file %s", directory, numberCashRegister, file);
                                                if(!errors.contains(error)) {
                                                    errors.add(error);
                                                    sendSalesLogger.error(error);
                                                }
                                                failed = true;
                                            } else
                                                currentSalesInfoList.add(getSalesInfo(nppGroupMachinery, numberCashRegister, numberZReport, dateReceipt, timeReceipt,
                                                        numberReceipt, dateReceipt, timeReceipt, idEmployee, null, sumCard, sumCash, null, null, barcode, idItem, null,
                                                        null, quantity, price, sumReceiptDetail, discountSumReceiptDetail, null, discountCard, numberReceiptDetail,
                                                        fileName, null, null, cashRegister));
                                        }
                                    }

                                    //чит для случая, когда не указана сумма платежа. Недостающую сумму пишем в наличные.
                                    BigDecimal sum = HandlerUtils.safeAdd(sumCard, sumCash);
                                    if (sum == null || sum.compareTo(currentPaymentSum) != 0)
                                        for (SalesInfo salesInfo : currentSalesInfoList) {
                                            salesInfo.sumCash = HandlerUtils.safeSubtract(currentPaymentSum, sumCard);
                                        }

                                    salesInfoList.addAll(currentSalesInfoList);
                                }
                            }
                        }
                    }
                }
            }
        } catch (ParseException | IOException | JDOMException e) {
            sendSalesLogger.error("Kristal: error reading file " + file.getAbsolutePath(), e);
            failed = true;
        }
        return new Sales(salesInfoList, failed);
    }

    private Integer getNppGroupMachinery(Map<String, CashRegisterInfo> directoryGroupCashRegisterMap, String directory, Integer numberCashRegister) {
        CashRegisterInfo result = directoryGroupCashRegisterMap.get(directory + "_" + numberCashRegister);
        if(result == null)
            result = directoryGroupCashRegisterMap.get(directory.toUpperCase() + "_" + numberCashRegister);
        if(result == null)
            result = directoryGroupCashRegisterMap.get(directory.toLowerCase() + "_" + numberCashRegister);
        return result == null ? null : result.numberGroup;
    }

    private boolean makeDirsIfNeeded(String directory) {
        return new File(directory).exists() || new File(directory).mkdirs();
    }

    private String existFilesMessage(File file1, File file2) {
        return String.format("Kristal: files %s and %s already exists. Maybe there are some problems with server", file1.getAbsolutePath(), file2.getAbsolutePath());
    }

    private String cantCreateFileMessage(File file) {
        return String.format("Kristal: file %s can not be created. Maybe there are some problems with server", file.getAbsolutePath());
    }

    private String createdButNotProcessedMessage(File file) {
        return String.format("Kristal: file %s has been created but not processed by server", file.getAbsolutePath());
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

    private String fillLeadingZeroes(String input) {
        if (input == null)
            return null;
        while (input.length() < 7)
            input = "0" + input;
        return input;
    }

    private String makeIdItemGroup(List<ItemGroup> hierarchyItemGroup, boolean type3) {
        return makeIdItemGroup(hierarchyItemGroup, type3, false);
    }

    private String makeIdItemGroup(List<ItemGroup> hierarchyItemGroup, boolean type3, boolean reverse) {
        StringBuilder idItemGroup = new StringBuilder();
        int delta = 0;
        if (type3) {
            for (int i = hierarchyItemGroup.size() - 1; i >=0 ; i--) {
                String id = hierarchyItemGroup.get(i).idItemGroup;
                if (id == null) id = "0";
                String[] splitted = id.split("_");
                idItemGroup.append(splitted[(Math.min(splitted.length, 2) - 1)]).append("|");
            }
        } else { //от самой общей группы до самой конкретной
            if(reverse) {
                for (int i = 0; i < Math.min(hierarchyItemGroup.size(), 5); i++) {
                    String id = hierarchyItemGroup.get(i).idItemGroup;
                    if (id == null) id = "0";
                    if(!id.equals("000000000"))
                        idItemGroup.append(id).append("|");
                    else
                        delta++;
                }
            } else {
                for (int i = Math.min(hierarchyItemGroup.size(), 5) - 1; i >= 0; i--) {
                    String id = hierarchyItemGroup.get(i).idItemGroup;
                    if (id == null) id = "0";
                    if (!id.equals("000000000"))
                        idItemGroup.append(id).append("|");
                    else
                        delta++;
                }
            }
        }
        for (int i = (hierarchyItemGroup.size() - delta); i < 5; i++) {
            idItemGroup.append("0|");
        }
        if(type3 && (idItemGroup.length() == 0)) {
            idItemGroup = new StringBuilder("0|0|0|0|0");
        } else
            idItemGroup = new StringBuilder(idItemGroup.substring(0, idItemGroup.length() - 1));
        return idItemGroup.toString();
    }

    private BigDecimal readBigDecimalXMLAttribute(Element element, String field) {
        if (element == null)
            return null;
        String value = element.getAttributeValue(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            sendSalesLogger.error("Kristal Error: ", e);
            return null;
        }
    }

    private Integer readIntegerXMLAttribute(Element element, String field) {
        if (element == null)
            return null;
        String value = element.getAttributeValue(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            sendSalesLogger.error("Kristal Error: ", e);
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

    private class Sales {
        List<SalesInfo> salesInfoList;
        boolean failed;

        Sales(List<SalesInfo> salesInfoList, boolean failed) {
            this.salesInfoList = salesInfoList;
            this.failed = failed;
        }
    }
}
