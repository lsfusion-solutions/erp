package equ.clt.handler.eqs;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class EQSHandler extends DefaultCashRegisterHandler<EQSSalesBatch> {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

    private FileSystemXmlApplicationContext springContext;

    public EQSHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "eqs";
    }

    @Override
    public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) throws IOException {

        Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        if (transactionList != null) {

            try {

                Class.forName("com.mysql.jdbc.Driver");

                EQSSettings EQSSettings = springContext.containsBean("eqsSettings") ? (EQSSettings) springContext.getBean("eqsSettings") : null;
                String connectionString = EQSSettings == null ? null : EQSSettings.getConnectionString(); //"jdbc:mysql://192.168.42.42/eqs"
                String user = EQSSettings == null ? null : EQSSettings.getUser(); //"root";
                String password = EQSSettings == null ? null : EQSSettings.getPassword(); //""

                if (connectionString == null) {
                    processTransactionLogger.error("No importConnectionString in EQSSettings found");
                } else {
                    for (TransactionCashRegisterInfo transaction : transactionList) {

                        Connection conn = DriverManager.getConnection(connectionString, user, password);

                        Exception exception = null;
                        try {
                            processTransactionLogger.info(String.format("EQS: transaction %s, table plu", transaction.id));
                            exportItems(conn, transaction);
                        } catch (Exception e) {
                            exception = e;
                        } finally {
                            if (conn != null)
                                conn.close();
                        }
                        sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(exception));
                    }
                }
            } catch (ClassNotFoundException | SQLException e) {
                throw Throwables.propagate(e);
            }
        }
        return sendTransactionBatchMap;
    }

    private void exportItems(Connection conn, TransactionCashRegisterInfo transaction) throws SQLException {
        if (transaction.itemsList != null) {
            conn.setAutoCommit(false);
            PreparedStatement ps = null;
            try {
                ps = conn.prepareStatement(
                        "INSERT INTO plu (store, barcode, art, description, department, grp, flags, price, exp, weight, piece, text, cancelled, updecr)" +
                                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE" +
                                " description=VALUES(description), department=VALUES(department), grp=VALUES(grp), flags=VALUES(flags)," +
                                " price=VALUES(price), exp=VALUES(exp), weight=VALUES(weight), piece=VALUES(piece), text=VALUES(text)," +
                                " cancelled=VALUES(cancelled), updecr=VALUES(updecr)");

                for (CashRegisterItemInfo item : transaction.itemsList) {
                    ps.setString(1, HandlerUtils.trim(item.idDepartmentStore, 10)); //store, код торговой точки
                    ps.setString(2, HandlerUtils.trim(item.idBarcode, 20)); //barcode, Штрих-код товара
                    ps.setString(3, HandlerUtils.trim(item.idItem, 20)); //art, Артикул
                    ps.setString(4, HandlerUtils.trim(item.name, 50)); //description, Наименование товара
                    ps.setInt(5, 1); //department, Номер отдела
                    ps.setString(6, HandlerUtils.trim(item.idItemGroup, 10)); //grp, Код группы товара
                    ps.setInt(7, item.splitItem ? 1 : 0); //flags, Флаги - бит 0 - разрешение дробного количества
                    ps.setBigDecimal(8, item.price == null ? BigDecimal.ZERO : item.price); //price, Цена товара
                    ps.setDate(9, item.expiryDate); //exp, Срок годности
                    ps.setInt(10, item.splitItem ? 1 : 0); //weight, Флаг весового товара (1 – весовой, 0 – нет)
                    ps.setInt(11, item.splitItem ? 0 : 1); //weight, Флаг штучного товара (1 – штучный, 0 – нет)
                    ps.setString(12, item.description); //text, Текст состава
                    ps.setInt(13, 0); //cancelled, Флаг блокировки товара. 1 – заблокирован, 0 – нет
                    ps.setLong(14, 4294967295L); //UpdEcr, Флаг обновления* КСА
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

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {
        if (!stopListInfo.exclude) {
            EQSSettings EQSSettings = springContext.containsBean("eqsSettings") ? (EQSSettings) springContext.getBean("eqsSettings") : null;
            String connectionString = EQSSettings == null ? null : EQSSettings.getConnectionString();
            String user = EQSSettings == null ? null : EQSSettings.getUser();
            String password = EQSSettings == null ? null : EQSSettings.getPassword();

            if (connectionString != null) {
                Connection conn = null;
                PreparedStatement ps = null;
                try {
                    conn = DriverManager.getConnection(connectionString, user, password);

                    conn.setAutoCommit(false);
                    processStopListLogger.info("EQS: executing stopLists, table pricelist_var");

                    ps = conn.prepareStatement(
                            "INSERT INTO plu (store, barcode, art, cancelled, updecr)" +
                                    " VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE" +
                                    " cancelled=VALUES(cancelled), updecr=VALUES(updecr)");
                    for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                        if (item.idBarcode != null) {
                            for (String idStock : stopListInfo.idStockSet) {
                                ps.setString(1, HandlerUtils.trim(idStock, 10)); //store, код торговой точки
                                ps.setString(2, HandlerUtils.trim(item.idBarcode, 20)); //barcode, Штрих-код товара
                                ps.setString(3, HandlerUtils.trim(item.idItem, 20)); //art, Артикул
                                ps.setInt(4, stopListInfo.exclude ? 0 : 1); //cancelled, Флаг блокировки товара. 1 – заблокирован, 0 – нет
                                ps.setLong(5, 4294967295L); //UpdEcr, Флаг обновления* КСА
                                ps.addBatch();
                            }
                        }
                    }
                    ps.executeBatch();
                    conn.commit();

                } catch (SQLException e) {
                    processStopListLogger.error("EQS:", e);
                    e.printStackTrace();
                } finally {
                    try {
                        if (ps != null)
                            ps.close();
                        if (conn != null)
                            conn.close();
                    } catch (SQLException e) {
                        processStopListLogger.error("EQS:", e);
                    }
                }
            }
        }
    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException {

        EQSSalesBatch salesBatch = null;

        Map<Integer, CashRegisterInfo> machineryMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.handlerModel != null && c.handlerModel.endsWith("EQSHandler")) {
                if (c.number != null && c.numberGroup != null)
                    machineryMap.put(c.number, c);
            }
        }

        try {

            Class.forName("com.mysql.jdbc.Driver");

            EQSSettings EQSSettings = springContext.containsBean("eqsSettings") ? (EQSSettings) springContext.getBean("eqsSettings") : null;
            String connectionString = EQSSettings == null ? null : EQSSettings.getConnectionString();
            String user = EQSSettings == null ? null : EQSSettings.getUser();
            String password = EQSSettings == null ? null : EQSSettings.getPassword();

            if (connectionString == null) {
                processTransactionLogger.error("No exportConnectionString in EQSSettings found");
            } else {

                Connection conn = null;

                try {
                    conn = DriverManager.getConnection(connectionString, user, password);

                    salesBatch = readSalesInfoFromSQL(conn, machineryMap);

                } finally {
                    if (conn != null)
                        conn.close();
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return salesBatch;
    }

    private EQSSalesBatch readSalesInfoFromSQL(Connection conn, Map<Integer, CashRegisterInfo> machineryMap) throws SQLException {

        List<SalesInfo> salesInfoList = new ArrayList<>();

        Set<Integer> readRecordSet = new HashSet<>();

        Statement statement = null;
        try {
            statement = conn.createStatement();
            String query = "SELECT type, ecr, doc, barcode, code, qty, price, amount, discount, department, flags, date, id, zreport, payment FROM history WHERE new = 1";
            ResultSet rs = statement.executeQuery(query);

            int position = 0;
            List<SalesInfo> currentSalesInfoList = new ArrayList<>();
            Set<Integer> currentReadRecordSet = new HashSet<>();
            while (rs.next()) {

                Integer id = rs.getInt(13);
                currentReadRecordSet.add(id);
                Integer type = rs.getInt(1); //type, Тип операции: 1. 2. Закрытие смены 3. Внесение 4. Выдача 5. Открытие чека 6. Регистрация 7. Оплата 8. Закрытие чека
                Integer numberReceipt = rs.getInt(3); //doc, Номер чека
                switch (type) {
                    case 1: //Открытие смены
                    case 2: //Закрытие смены
                    case 3: //Внесение
                    case 4: //Выдача
                        break;
                    case 5: //Открытие чека
                        position = 0;
                        currentSalesInfoList = new ArrayList<>();
                        break;
                    case 6: //Регистрация
                        position++;

                        Integer cash_id = rs.getInt(2); //ecr, Номер КСА

                        CashRegisterInfo cashRegister = machineryMap.get(cash_id);
                        Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;

                        String numberZReport = rs.getString(14); //zReport

                        String idBarcode = rs.getString(4); //barcode, Штрих-код товара
                        String idItem = String.valueOf(rs.getLong(5)); //code, Код товара
                        BigDecimal totalQuantity = rs.getBigDecimal(6); //qty, Количество
                        BigDecimal price = rs.getBigDecimal(7); //price, Цена
                        BigDecimal sum = rs.getBigDecimal(8); //amount, Сумма
                        BigDecimal discountSum = HandlerUtils.safeAbs(rs.getBigDecimal(9)); //discount, Сумма скидки/наценки
                        sum = HandlerUtils.safeSubtract(sum, HandlerUtils.safeNegate(rs.getBigDecimal(9)));
                        String idSection = rs.getString(10); //department, Номер отдела

                        Integer flags = rs.getInt(11); //flags, Флаги: bit 0 - Возврат bit 1 - Скидка/Наценка (при любой скидке этот бит всегда = 1) bit 2 - Сторнирование/Коррекция

                        boolean isSale = !getBit(flags, 0);
                        boolean isReturn = getBit(flags, 0);
                        Date dateReceipt = rs.getDate(12); // r.date
                        Time timeReceipt = rs.getTime(12); //r.date

                        //временные логи
                        if (discountSum != null && discountSum.doubleValue() != 0.0) {
                            sendSalesLogger.info(String.format("Данные в базе: flag %s, isReturn %s, barcode %s, amount %s, discount %s", flags, isReturn, rs.getString(4), rs.getBigDecimal(8), rs.getBigDecimal(9)));
                        }

                        totalQuantity = isSale ? totalQuantity : isReturn ? totalQuantity.negate() : null;

                        boolean isDiscount = getBit(flags, 1);
                        boolean discountRecord = idBarcode.isEmpty() && isDiscount;
                        if(discountRecord) {
                            for(SalesInfo s : currentSalesInfoList) {
                                s.discountSumReceipt = discountSum;
                            }
                        } else {
                            currentSalesInfoList.add(new SalesInfo(false, nppGroupMachinery, cash_id, numberZReport,
                                    dateReceipt, timeReceipt, numberReceipt, dateReceipt, timeReceipt, null,
                                    null, null, null, null, (BigDecimal) null, idBarcode, idItem, null, null, totalQuantity,
                                    price, isSale ? sum : sum.negate(), discountSum, null, null,
                                    position, null, idSection));
                        }
                        break;
                    case 7: //Оплата
                        BigDecimal sumPayment = rs.getBigDecimal(8); //amount, Сумма
                        Integer typePayment = rs.getInt(15); //Payment, Номер оплаты
                        for (SalesInfo salesInfo : currentSalesInfoList) {
                            if (typePayment == 0)
                                salesInfo.sumCash = HandlerUtils.safeAdd(salesInfo.sumCash, sumPayment);
                            else if (typePayment == 1)
                                salesInfo.sumCard = HandlerUtils.safeAdd(salesInfo.sumCard, sumPayment);
                            else if (typePayment == 2) {
                                BigDecimal sumGiftCard = salesInfo.sumGiftCardMap.get(null);
                                salesInfo.sumGiftCardMap.put(null, HandlerUtils.safeAdd(sumGiftCard, sumPayment));
                            }
                            else
                                salesInfo.sumCash = HandlerUtils.safeAdd(salesInfo.sumCash, sumPayment);
                        }
                        break;
                    case 8: //Закрытие чека
                        salesInfoList.addAll(currentSalesInfoList);
                        readRecordSet.addAll(currentReadRecordSet);
                        currentReadRecordSet = new HashSet<>();
                        break;
                }
            }
            if (salesInfoList.size() > 0)
                sendSalesLogger.info(String.format("EQS: found %s records", salesInfoList.size()));
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
        return new EQSSalesBatch(salesInfoList, readRecordSet);
    }

    boolean getBit(int n, int k) {
        return ((n >> k) & 1) == 1;
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<String> directorySet,
                                 Set<Integer> succeededRequests, Map<Integer, String> failedRequests, Map<Integer, String> ignoredRequests) throws IOException, ParseException {
        EQSSettings EQSSettings = springContext.containsBean("eqsSettings") ? (EQSSettings) springContext.getBean("eqsSettings") : null;
        String connectionString = EQSSettings == null ? null : EQSSettings.getConnectionString();
        String user = EQSSettings == null ? null : EQSSettings.getUser();
        String password = EQSSettings == null ? null : EQSSettings.getPassword();

        if (connectionString != null) {
            Connection conn = null;
            Statement statement = null;
            try {
                conn = DriverManager.getConnection(connectionString, user, password);

                for (RequestExchange entry : requestExchangeList) {
                    try {
                        if (entry.isSalesInfoExchange()) {
                            String dateFrom = new SimpleDateFormat("yyyy-MM-dd").format(entry.dateFrom);
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(entry.dateTo);
                            cal.add(Calendar.DATE, 1);
                            String dateTo = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
                            sendSalesLogger.info(String.format("EQS RequestSalesInfo: from %s to %s", dateFrom, entry.dateTo));

                            statement = conn.createStatement();
                            statement.execute(String.format("UPDATE history SET new = 1 WHERE date >= '%s' AND date <='%s'", dateFrom, dateTo));
                            succeededRequests.add(entry.requestExchange);
                        }
                    } catch (SQLException e) {
                        failedRequests.put(entry.requestExchange, e.getMessage());
                        e.printStackTrace();
                    }
                }

            } catch (SQLException e) {
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

    @Override
    public void finishReadingSalesInfo(EQSSalesBatch salesBatch) {

        EQSSettings EQSSettings = springContext.containsBean("eqsSettings") ? (EQSSettings) springContext.getBean("eqsSettings") : null;
        String connectionString = EQSSettings == null ? null : EQSSettings.getConnectionString();
        String user = EQSSettings == null ? null : EQSSettings.getUser();
        String password = EQSSettings == null ? null : EQSSettings.getPassword();

        if (connectionString != null && salesBatch.readRecordSet != null) {

            Connection conn = null;
            PreparedStatement ps = null;

            try {
                conn = DriverManager.getConnection(connectionString, user, password);

                conn.setAutoCommit(false);
                ps = conn.prepareStatement("UPDATE history SET new = 0 WHERE id = ?");
                for (Integer record : salesBatch.readRecordSet) {
                    ps.setInt(1, record); //id
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();

            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (ps != null)
                        ps.close();
                    if (conn != null)
                        conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }
}