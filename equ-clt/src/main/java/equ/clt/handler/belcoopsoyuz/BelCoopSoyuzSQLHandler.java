package equ.clt.handler.belcoopsoyuz;

import com.google.common.base.Throwables;
import equ.api.RequestExchange;
import equ.api.SalesBatch;
import equ.api.SalesInfo;
import equ.api.SendTransactionBatch;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.CashRegisterItemInfo;
import equ.api.cashregister.TransactionCashRegisterInfo;
import equ.clt.handler.DefaultCashRegisterHandler;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static equ.clt.handler.HandlerUtils.*;

public class BelCoopSoyuzSQLHandler extends DefaultCashRegisterHandler<BelCoopSoyuzSQLSalesBatch> {

    private FileSystemXmlApplicationContext springContext;

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

    private static String logPrefix = "BelCoopSoyuz SQL: ";

    public BelCoopSoyuzSQLHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "belcoopsoyuz" + transactionInfo.nameGroupMachinery;
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) throws IOException {
        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        if (transactionList != null) {

            try {

                Class.forName("oracle.jdbc.driver.OracleDriver");

                for (TransactionCashRegisterInfo transaction : transactionList) {

                    String directory = null;
                    for (CashRegisterInfo cashRegister : transaction.machineryInfoList) {
                        if (cashRegister.directory != null) {
                            directory = cashRegister.directory;
                        }
                    }

                    if (directory == null) {
                        processTransactionLogger.error(logPrefix + "No connectionString found");
                    } else {
                        processTransactionLogger.info(String.format(logPrefix + "connecting to %s", directory));
                        Exception exception = null;
                        try (Connection conn = DriverManager.getConnection(directory)) {
                            processTransactionLogger.info(String.format(logPrefix + "transaction %s, table plu", transaction.id));
                            exportItems(conn, transaction);
                        } catch (Exception e) {
                            exception = e;
                        }
                        sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(exception));
                    }
                }
            } catch (ClassNotFoundException e) {
                throw Throwables.propagate(e);
            }
        }
        return sendTransactionBatchMap;
    }

    private void exportItems(Connection conn, TransactionCashRegisterInfo transaction) throws SQLException {
        if (transaction.itemsList != null) {
            PreparedStatement ps = null;
            try {

                conn.setAutoCommit(true);
                try (Statement truncateStatement = conn.createStatement()) {
                    truncateStatement.execute("DELETE FROM cl1_bks.l9sk34"); //на truncate не дают прав
                    //truncateStatement.execute("TRUNCATE TABLE cl1_bks.l9sk34");
                }

                conn.setAutoCommit(false);
                ps = conn.prepareStatement("MERGE INTO cl1_bks.l9sk34 dest " +
                                "USING(SELECT ? CEUNIKEY, ? CEUNIREF0, ? CEDOCCOD, ? CEOBIDE, ? CEOBMEA, ? MEOBNAM, ? NERECOST, ? NEOPPRIC, ? TEDOCACT, ? CECUCOD FROM DUAL) src " +
                                "ON (dest.CEUNIKEY = src.CEUNIKEY) " +
                                "WHEN MATCHED THEN " +
                                "UPDATE SET dest.CEUNIREF0=src.CEUNIREF0, dest.CEDOCCOD=src.CEDOCCOD, dest.CEOBIDE=src.CEOBIDE, dest.CEOBMEA=src.CEOBMEA, dest.MEOBNAM=src.MEOBNAM, dest.NERECOST=src.NERECOST, dest.NEOPPRIC=src.NEOPPRIC, dest.TEDOCACT=src.TEDOCACT, dest.CECUCOD=src.CECUCOD " +
                                "WHEN NOT MATCHED THEN " +
                                "INSERT (CEUNIKEY, CEUNIREF0, CEDOCCOD, CEOBIDE, CEOBMEA, MEOBNAM, NERECOST, NEOPPRIC, TEDOCACT, CECUCOD) " +
                                "VALUES (src.CEUNIKEY, src.CEUNIREF0, src.CEDOCCOD, src.CEOBIDE, src.CEOBMEA, src.MEOBNAM, src.NERECOST, src.NEOPPRIC, src.TEDOCACT, src.CECUCOD)"
                );

                for (CashRegisterItemInfo item : transaction.itemsList) {
                    String barcode = appendBarcode(item.idBarcode);
                    ps.setString(1, appendSpaces(barcode, 25)); //CEUNIKEY (type NCHAR 25), уникальный ключ записи в базе данных
                    String ceuniref0 = item.section != null && item.section.contains(" ") ? item.section.split(" ")[0] : null;
                    ps.setString(2, trim(ceuniref0, 25)); //CEUNIREF0, УНП предприятия
                    ps.setString(3, "ПРАЙС"); //CEDOCCOD, тип записи
                    ps.setString(4, trim(barcode, 25)); //CEOBIDE, Штрих-код товара
                    ps.setString(5, trim(item.shortNameUOM, 25)); //CEOBMEA, единица измерения
                    ps.setString(6, trim(item.name, 100)); //MEOBNAM, Наименование товара
                    ps.setDouble(7, item.price.doubleValue()); //NERECOST, роз.цена
                    ps.setDouble(8, item.price.doubleValue()); //NEOPPRIC, роз.цена
                    ps.setTimestamp(9, new java.sql.Timestamp(transaction.date.getTime() + 1000*60*5)); //TEDOCACT, дата и время вступления прайса в силу
                    ps.setString(10, item.section); //CECUCOD, секция
                    ps.addBatch();
                }

                ps.executeBatch();
                conn.commit();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            } finally {
                if (ps != null)
                    ps.close();
            }
        }
    }

    private String appendBarcode(String barcode) {
        if (barcode != null) {
            if (barcode.length() == 5)
                barcode = "22" + barcode + "00000";
            if (barcode.length() == 12) {
                try {
                    int checkSum = 0;
                    for (int i = 0; i <= 10; i = i + 2) {
                        checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i)));
                        checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i + 1))) * 3;
                    }
                    checkSum %= 10;
                    if (checkSum != 0)
                        checkSum = 10 - checkSum;
                    barcode = barcode.concat(String.valueOf(checkSum));
                } catch (Exception ignored) {
                }
            }
        }
        return barcode;
    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {
        BelCoopSoyuzSQLSalesBatch salesBatch = null;

        Map<String, CashRegisterInfo> sectionCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c) && c.section != null) {
                sectionCashRegisterMap.put(c.section, c);
            }
        }

        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
            if (directory == null) {
                sendSalesLogger.error(logPrefix + "No connectionString found");
            } else {

                Locale defaultLocale = Locale.getDefault();
                try {
                    Locale.setDefault(Locale.ENGLISH); //хак. То ли этот конкретный сервер, то ли oracle вообще хочет английскую локаль
                    //jdbc:oracle:thin:ls/ls@213.184.248.129:1521:XE
                    try (Connection conn = DriverManager.getConnection(directory)) {
                        salesBatch = readSalesInfoFromSQL(conn, sectionCashRegisterMap, directory, directory);
                    }
                } finally {
                    Locale.setDefault(defaultLocale);
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return salesBatch;
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList,
                                 Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) throws IOException, ParseException {
        for (RequestExchange entry : requestExchangeList) {
            Connection conn = null;
            Statement statement = null;
            try {
                Class.forName("com.mysql.jdbc.Driver");

                for (String directory : getDirectorySet(entry)) {
                    sendSalesLogger.info(String.format(logPrefix + "connecting to %s", directory));
                    conn = DriverManager.getConnection(directory);

                    String dateFrom = new SimpleDateFormat("yyyy-MM-dd").format(entry.dateFrom);
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(entry.dateTo);
                    cal.add(Calendar.DATE, 1);
                    sendSalesLogger.info(logPrefix + "RequestSalesInfo: dateTo is " + cal.getTime());
                    String dateTo = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
                    sendSalesLogger.info(String.format(logPrefix + "RequestSalesInfo: from %s to %s", dateFrom, entry.dateTo));

                    statement = conn.createStatement();
                    String query = String.format("UPDATE cl1_bks.a9ck07 SET CEUNIFOL = REGEXP_REPLACE(CEUNIFOL, '(.{20}).*', '\\10') WHERE TEDOCINS >= TO_DATE('%s','yyyy-MM-dd') AND TEDOCINS <= TO_DATE('%s','yyyy-MM-dd')", dateFrom, dateTo);
                    sendSalesLogger.info(logPrefix + "RequestSalesInfo: " + query);
                    statement.execute(query);
                    succeededRequests.add(entry.requestExchange);
                }
            } catch (Exception e) {
                failedRequests.put(entry.requestExchange, e);
                e.printStackTrace();
            } finally {
                try {
                    if (statement != null)
                        statement.close();
                    if (conn != null)
                        conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    private BelCoopSoyuzSQLSalesBatch readSalesInfoFromSQL(Connection conn, Map<String, CashRegisterInfo> sectionCashRegisterMap, String connectionString, String directory) {

        List<SalesInfo> salesInfoList = new ArrayList<>();
        Set<String> readRecordSet = new HashSet<>();

        try (Statement statement = conn.createStatement()) {
            String query = "SELECT CEUNIKEY, CEUNIGO, CEDOCCOD, CEDOCNUM, TEDOCINS, CEOBIDE, CEOBNAM, CEOBMEA, CEOBTYP, NEOPEXP, NEOPPRIC, NEOPSUMC, " +
                    "NEOPDEL, NEOPPDELC, NEOPSDELC, NEOPNDS, NEOPSUMCT, CEOPDEV, CEOPMAN, CESUCOD FROM cl1_bks.a9ck07 WHERE CEUNIFOL NOT LIKE '____________________1%' ORDER BY CEUNIREF0, CEDOCCOD, CEUNIKEY";
            ResultSet rs = statement.executeQuery(query);

            Map<Integer, Integer> numberReceiptDetailMap = new HashMap<>();
            List<SalesInfo> currentSalesInfoList = new ArrayList<>();
            Set<String> currentReadRecordSet = new HashSet<>();
            BigDecimal extraDiscountSum = null;
            boolean error = false;
            while (rs.next()) {

                String id = rs.getString("CEUNIKEY");
                BigDecimal sumReceiptDetail = rs.getBigDecimal("NEOPSUMCT");
                String type = rs.getString("CEOBTYP");
                if(type != null) {
                    switch (type) {
                        case "ТОВАР":
                        case "ТОВАР ВОЗВРАТ": {
                            boolean isSale = type.equals("ТОВАР");

                            Date dateReceipt = rs.getDate("TEDOCINS");
                            Time timeReceipt = rs.getTime("TEDOCINS");

                            String numberZReport = rs.getString("CEDOCNUM");
                            Integer numberReceipt = Integer.parseInt(rs.getString("CEDOCCOD"));
                            if (numberZReport != null && numberZReport.trim().equals("0")) {
                                numberReceipt = numberReceipt * 10000 + timeReceipt.getHours() * 100 + timeReceipt.getMinutes();
                            }

                            String section = rs.getString("CESUCOD");

                            CashRegisterInfo cashRegister = sectionCashRegisterMap.get(section);
                            Integer nppMachinery = cashRegister == null ? null : cashRegister.number;
                            Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;

                            if(cashRegister != null) {
                                String barcodeItem = rs.getString("CEOBIDE");
                                String idEmployee = rs.getString("CEOPDEV");
                                BigDecimal quantityReceiptDetail = rs.getBigDecimal("NEOPEXP");
                                BigDecimal priceReceiptDetail = rs.getBigDecimal("NEOPPRIC");

                                BigDecimal discountSum1ReceiptDetail = rs.getBigDecimal("NEOPSDELC");
                                BigDecimal discountSum2ReceiptDetail = rs.getBigDecimal("NEOPPDELC");
                                BigDecimal discountSumReceiptDetail = safeNegate(safeAdd(discountSum1ReceiptDetail, discountSum2ReceiptDetail));

                                Integer numberReceiptDetail = numberReceiptDetailMap.get(numberReceipt);
                                numberReceiptDetail = numberReceiptDetail == null ? 1 : (numberReceiptDetail + 1);
                                numberReceiptDetailMap.put(numberReceipt, numberReceiptDetail);

                                BigDecimal quantity = isSale ? quantityReceiptDetail : safeNegate(quantityReceiptDetail);
                                BigDecimal sum = isSale ? sumReceiptDetail : safeNegate(sumReceiptDetail);
                                currentSalesInfoList.add(new SalesInfo(false, nppGroupMachinery, nppMachinery, numberZReport, dateReceipt, timeReceipt, numberReceipt, dateReceipt, timeReceipt, idEmployee, null, null, null, null, null, barcodeItem, null, null, null, quantity, priceReceiptDetail, sum, discountSumReceiptDetail, null, null, numberReceiptDetail, null, section, cashRegister));
                                currentReadRecordSet.add(id);
                            } else {
                                sendSalesLogger.error(logPrefix + String.format("unknown section: %s (zreport %s, receipt %s)", section, numberZReport, numberReceipt));
                                error = true;
                            }
                            break;
                        }
                        case "БОНУС": {
                            extraDiscountSum = sumReceiptDetail;
                            break;
                        }
                        case "ВСЕГО":
                        case "ВОЗВРАТ": {
                            if(!error) {
                                boolean isSale = type.equals("ВСЕГО");
                                for (SalesInfo salesInfo : currentSalesInfoList) {
                                    salesInfo.sumCash = isSale ? sumReceiptDetail : safeNegate(sumReceiptDetail);
                                    salesInfo.discountSumReceipt = safeAdd(salesInfo.discountSumReceipt, extraDiscountSum);
                                    salesInfoList.add(salesInfo);
                                }
                                readRecordSet.addAll(currentReadRecordSet);
                                readRecordSet.add(id);
                            }
                            currentSalesInfoList = new ArrayList<>();
                            currentReadRecordSet = new HashSet<>();
                            extraDiscountSum = null;
                            error = false;
                            break;
                        }
                        default:
                            readRecordSet.add(id);
                    }
                } else {
                    readRecordSet.add(id);
                }
            }
            if (salesInfoList.size() > 0)
                sendSalesLogger.info(logPrefix + String.format("found %s receiptDetails", salesInfoList.size()));
        } catch (SQLException e) {
            sendSalesLogger.error(logPrefix + "failed to read sales. ConnectionString: " + connectionString, e);
            throw Throwables.propagate(e);
        }
        return new BelCoopSoyuzSQLSalesBatch(salesInfoList, readRecordSet, directory);
    }

    @Override
    public void finishReadingSalesInfo(BelCoopSoyuzSQLSalesBatch salesBatch) {
        for(Map.Entry<String, Set<String>> entry : salesBatch.readRecordsMap.entrySet()) {
            String directory = entry.getKey();
            Set<String> readRecordSet = entry.getValue();

            if (directory != null) {

                Connection conn = null;
                Statement statement = null;

                Locale defaultLocale = Locale.getDefault();
                try {
                    sendSalesLogger.info(String.format(logPrefix + "Finish Reading, connecting to %s", directory));
                    conn = DriverManager.getConnection(directory);
                    conn.setAutoCommit(false);

                    int i = 0;
                    int blockSize = 1000; //ограничение сервера
                    StringBuilder in = new StringBuilder();
                    for (String record : readRecordSet) {
                        if(i >= blockSize) {
                            statement = conn.createStatement();
                            statement.execute(String.format("UPDATE cl1_bks.a9ck07 SET CEUNIFOL = REGEXP_REPLACE(CEUNIFOL, '(.{20}).*', '\\11') WHERE CEUNIKEY IN (%s)", in.toString()));
                            in = new StringBuilder();
                            i = 0;
                        }
                        in.append(in.length() == 0 ? "" : ",").append('\'' + record + '\'');
                        i++;
                    }
                    statement = conn.createStatement();
                    statement.execute(String.format("UPDATE cl1_bks.a9ck07 SET CEUNIFOL = REGEXP_REPLACE(CEUNIFOL, '(.{20}).*', '\\11') WHERE CEUNIKEY IN (%s)", in.toString()));
                    conn.commit();

                } catch (SQLException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        Locale.setDefault(defaultLocale);
                        if (statement != null)
                            statement.close();
                        if (conn != null)
                            conn.close();
                    } catch (SQLException ignored) {
                    }
                }
            }
        }
    }

    protected String trim(String input, Integer length) {
        return input == null ? null : (length == null || length >= input.trim().length() ? input.trim() : input.trim().substring(0, length));
    }

    private String appendSpaces(String line, int length) {
        if(line == null)
            line = "";
        StringBuilder lineBuilder = new StringBuilder(line.substring(0, Math.min(line.length(), length)));
        while(lineBuilder.length() < length)
            lineBuilder.append(" ");
        return lineBuilder.toString();
    }
}
