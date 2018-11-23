package equ.clt.handler.belcoopsoyuz;

import com.google.common.base.Throwables;
import equ.api.SalesBatch;
import equ.api.SalesInfo;
import equ.api.SendTransactionBatch;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.TransactionCashRegisterInfo;
import equ.clt.handler.DefaultCashRegisterHandler;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.util.*;

import static equ.clt.handler.HandlerUtils.safeAdd;
import static equ.clt.handler.HandlerUtils.safeNegate;

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
        //todo
        return new HashMap<>();
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
                sendSalesLogger.error(logPrefix + "No connectionString in BelcoopsoyuzSettings found");
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

    private BelCoopSoyuzSQLSalesBatch readSalesInfoFromSQL(Connection conn, Map<String, CashRegisterInfo> sectionCashRegisterMap, String connectionString, String directory) {

        List<SalesInfo> salesInfoList = new ArrayList<>();
        Set<String> readRecordSet = new HashSet<>();

        try (Statement statement = conn.createStatement()) {
            String query = "SELECT CEUNIKEY, CEUNIREF0, CEUNIGO, CEDOCCOD, CEDOCNUM, TEDOCINS, CEOBIDE, CEOBNAM, CEOBMEA, CEOBTYP, NEOPEXP, NEOPPRIC, NEOPSUMC, " +
                    "NEOPDEL, NEOPPDELC, NEOPSDELC, NEOPNDS, NEOPSUMCT, CEOPDEV, CEOPMAN, CESUCOD FROM cl1_bks.a9ck07 WHERE CEUNIFOL != '0000000000000000000000001' ORDER BY CEUNIREF0, CEDOCCOD, CEUNIKEY";
            ResultSet rs = statement.executeQuery(query);

            Map<Integer, Integer> numberReceiptDetailMap = new HashMap<>();
            List<SalesInfo> currentSalesInfoList = new ArrayList<>();
            Set<String> currentReadRecordSet = new HashSet<>();
            BigDecimal extraDiscountSum = null;
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
                            currentSalesInfoList.add(new SalesInfo(false, nppGroupMachinery, nppMachinery, numberZReport, dateReceipt, timeReceipt,
                                    numberReceipt, dateReceipt, timeReceipt, idEmployee, null, null, null, null, null, barcodeItem, null, null, null,
                                    quantity, priceReceiptDetail, sum, discountSumReceiptDetail, null, null, numberReceiptDetail, null, section, cashRegister));
                            currentReadRecordSet.add(id);
                            break;
                        }
                        case "БОНУС": {
                            extraDiscountSum = sumReceiptDetail;
                            break;
                        }
                        case "ВСЕГО":
                        case "ВОЗВРАТ": {
                            boolean isSale = type.equals("ВСЕГО");
                            for (SalesInfo salesInfo : currentSalesInfoList) {
                                salesInfo.sumCash = isSale ? sumReceiptDetail : safeNegate(sumReceiptDetail);
                                salesInfo.discountSumReceipt = safeAdd(salesInfo.discountSumReceipt, extraDiscountSum);
                                salesInfoList.add(salesInfo);
                            }
                            currentSalesInfoList = new ArrayList<>();
                            readRecordSet.addAll(currentReadRecordSet);
                            currentReadRecordSet = new HashSet<>();
                            extraDiscountSum = null;
                            currentReadRecordSet.add(id);
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
                    conn = DriverManager.getConnection(directory);
                    conn.setAutoCommit(false);

                    int i = 0;
                    int blockSize = 100000; //TODO: пока что ошибка ORA-01031: insufficient privileges
                    StringBuilder in = new StringBuilder();
                    for (String record : readRecordSet) {
                        if(i >= blockSize) {
                            statement = conn.createStatement();
                            statement.execute(String.format("UPDATE cl1_bks.a9ck07 SET CEUNIFOL = '0000000000000000000000001' WHERE CEUNIKEY IN (%s)", in.toString()));
                            in = new StringBuilder();
                            i = 0;
                        }
                        in.append(in.length() == 0 ? "" : ",").append('\'' + record + '\'');
                        i++;
                    }
                    statement = conn.createStatement();
                    statement.execute(String.format("UPDATE cl1_bks.a9ck07 SET CEUNIFOL = '0000000000000000000000001' WHERE CEUNIKEY IN (%s)", in.toString()));
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
}
