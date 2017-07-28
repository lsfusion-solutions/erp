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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static equ.clt.handler.HandlerUtils.*;

public class EQSHandler extends DefaultCashRegisterHandler<EQSSalesBatch> {

    protected final static Logger machineryExchangeLogger = Logger.getLogger("MachineryExchangeLogger");
    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

    private static String logPrefix = "EQS: ";

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

                EQSSettings eqsSettings = springContext.containsBean("eqsSettings") ? (EQSSettings) springContext.getBean("eqsSettings") : null;
                boolean appendBarcode = eqsSettings != null && eqsSettings.getAppendBarcode() != null && eqsSettings.getAppendBarcode();
                boolean skipIdDepartmentStore = eqsSettings != null && eqsSettings.getSkipIdDepartmentStore() != null && eqsSettings.getSkipIdDepartmentStore();

                for (TransactionCashRegisterInfo transaction : transactionList) {

                    String directory = null;
                    for (CashRegisterInfo cashRegister : transaction.machineryInfoList) {
                        if (cashRegister.directory != null) {
                            directory = cashRegister.directory;
                        }
                    }
                    EQSConnectionString params = new EQSConnectionString(directory);

                    if (params.connectionString == null) {
                        processTransactionLogger.error(logPrefix + "No connectionString in EQSSettings found");
                    } else {
                        processTransactionLogger.info(String.format(logPrefix + "connecting to %s", params.connectionString));
                        Connection conn = DriverManager.getConnection(params.connectionString, params.user, params.password);
                        Exception exception = null;
                        try {
                            processTransactionLogger.info(String.format(logPrefix + "transaction %s, table plu", transaction.id));
                            exportItems(conn, transaction, appendBarcode, skipIdDepartmentStore);
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

    private void exportItems(Connection conn, TransactionCashRegisterInfo transaction, boolean appendBarcode, boolean skipIdDepartmentStore) throws SQLException {
        if (transaction.itemsList != null) {
            PreparedStatement ps = null;
            try {
                if(transaction.snapshot) {
                    conn.setAutoCommit(true);
                    Statement truncateStatement = conn.createStatement();
                    if(skipIdDepartmentStore) {
                        truncateStatement.execute("TRUNCATE plu");
                    } else {
                        //todo: добавить idDepartmentStoreGroupCashRegister в TransactionCashRegisterInfo
                        String idDepartmentStore = null;
                        for (CashRegisterItemInfo item : transaction.itemsList) {
                            if(idDepartmentStore == null)
                                idDepartmentStore = item.idDepartmentStore;
                        }

                        if(idDepartmentStore != null)
                            truncateStatement.execute("DELETE FROM plu WHERE store='"+idDepartmentStore+"'");
                    }
                }
                conn.setAutoCommit(false);
                ps = conn.prepareStatement(
                        "INSERT INTO plu (store, barcode, art, description, department, grp, flags, price, exp, weight, piece, text, cancelled, updecr)" +
                                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE" +
                                " description=VALUES(description), department=VALUES(department), grp=VALUES(grp), flags=VALUES(flags)," +
                                " price=VALUES(price), exp=VALUES(exp), weight=VALUES(weight), piece=VALUES(piece), text=VALUES(text)," +
                                " cancelled=VALUES(cancelled), updecr=VALUES(updecr)");

                for (CashRegisterItemInfo item : transaction.itemsList) {
                    ps.setString(1, skipIdDepartmentStore ? "" : trim(item.idDepartmentStore, 10)); //store, код торговой точки
                    ps.setString(2, removeCheckDigitFromBarcode(trim(item.idBarcode, 20), appendBarcode)); //barcode, Штрих-код товара
                    ps.setString(3, trim(item.idItem, 20)); //art, Артикул
                    ps.setString(4, trim(item.name, 50)); //description, Наименование товара
                    ps.setInt(5, 1); //department, Номер отдела
                    ps.setString(6, trim(item.idItemGroup, 10)); //grp, Код группы товара
                    //если lsf flag 16 не установлен, то пишем флаг 32
                    ps.setInt(7, (item.flags != null && ((item.flags & 16) == 0) ? 32 : 0) + (item.splitItem ? 1 : 0)); //flags, Флаги - бит 0 - разрешение дробного количества
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

            EQSSettings eqsSettings = springContext.containsBean("eqsSettings") ? (EQSSettings) springContext.getBean("eqsSettings") : null;
            boolean appendBarcode = eqsSettings != null && eqsSettings.getAppendBarcode() != null && eqsSettings.getAppendBarcode();

            for (String directory : directorySet) {

                EQSConnectionString params = new EQSConnectionString(directory);

                if (params.connectionString != null) {
                    Connection conn = null;
                    PreparedStatement ps = null;
                    try {
                        processStopListLogger.info(String.format(logPrefix + "connecting to %s", params.connectionString));
                        conn = DriverManager.getConnection(params.connectionString, params.user, params.password);

                        conn.setAutoCommit(false);
                        processStopListLogger.info(logPrefix + "executing stopLists, table pricelist_var");

                        ps = conn.prepareStatement(
                                "INSERT INTO plu (store, barcode, art, cancelled, updecr)" +
                                        " VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE" +
                                        " cancelled=VALUES(cancelled), updecr=VALUES(updecr)");
                        for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                            if (item.idBarcode != null) {
                                for (String idStock : stopListInfo.idStockSet) {
                                    ps.setString(1, trim(idStock, 10)); //store, код торговой точки
                                    ps.setString(2, removeCheckDigitFromBarcode(trim(item.idBarcode, 20), appendBarcode)); //barcode, Штрих-код товара
                                    ps.setString(3, trim(item.idItem, 20)); //art, Артикул
                                    ps.setInt(4, stopListInfo.exclude ? 0 : 1); //cancelled, Флаг блокировки товара. 1 – заблокирован, 0 – нет
                                    ps.setLong(5, 4294967295L); //UpdEcr, Флаг обновления* КСА
                                    ps.addBatch();
                                }
                            }
                        }
                        ps.executeBatch();
                        conn.commit();

                    } catch (SQLException e) {
                        processStopListLogger.error(logPrefix, e);
                        e.printStackTrace();
                    } finally {
                        try {
                            if (ps != null)
                                ps.close();
                            if (conn != null)
                                conn.close();
                        } catch (SQLException e) {
                            processStopListLogger.error(logPrefix, e);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) throws IOException {
        for(String directory : requestExchange.directoryStockMap.keySet()) {

            EQSConnectionString params = new EQSConnectionString(directory);

            if (params.connectionString != null) {
                Connection conn = null;
                PreparedStatement ps = null;
                try {
                    machineryExchangeLogger.info(String.format(logPrefix + "connecting to %s", params.connectionString));
                    conn = DriverManager.getConnection(params.connectionString, params.user, params.password);
                    conn.setAutoCommit(false);

                    ps = conn.prepareStatement(
                            "INSERT INTO customers (code, description, discount, updecr)" +
                                    " VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE" +
                                    " description=VALUES(description), discount=VALUES(discount), updecr=VALUES(updecr)");

                    for (DiscountCard card : discountCardList) {
                        if(card.idDiscountCard != null) {
                            ps.setString(1, trim(card.idDiscountCard, 20)); //code
                            String name = (card.lastNameContact == null ? "" : (card.lastNameContact + " "))
                                    + (card.firstNameContact == null ? "" : (card.firstNameContact + " "))
                                    + (card.middleNameContact == null ? "" : card.middleNameContact);
                            ps.setString(2, trim(name, 50)); //description
                            ps.setInt(3, card.percentDiscountCard == null ? 0 : -card.percentDiscountCard.intValue()); //discount
                            ps.setLong(4, 4294967295L); //UpdEcr, Флаг обновления* КСА
                            ps.addBatch();
                        }
                    }

                    ps.executeBatch();
                    conn.commit();

                } catch (SQLException e) {
                    machineryExchangeLogger.error(logPrefix, e);
                    e.printStackTrace();
                } finally {
                    try {
                        if (ps != null)
                            ps.close();
                        if (conn != null)
                            conn.close();
                    } catch (SQLException e) {
                        machineryExchangeLogger.error(logPrefix, e);
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
                if (c.number != null && c.numberGroup != null && c.directory != null && c.directory.equals(directory))
                    machineryMap.put(c.number, c);
            }
        }

        try {

            Class.forName("com.mysql.jdbc.Driver");
            EQSConnectionString params = new EQSConnectionString(directory);

            if (params.connectionString == null) {
                sendSalesLogger.error(logPrefix + "No connectionString in EQSSettings found");
            } else {

                Connection conn = null;

                try {
                    sendSalesLogger.info(String.format(logPrefix + "connecting to %s", params.connectionString));
                    conn = DriverManager.getConnection(params.connectionString, params.user, params.password);
                    sendSalesLogger.info(String.format(logPrefix + "connected to %s", params.connectionString));

                    salesBatch = readSalesInfoFromSQL(conn, machineryMap, params.connectionString, directory);

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

    private EQSSalesBatch readSalesInfoFromSQL(Connection conn, Map<Integer, CashRegisterInfo> machineryMap, String connectionString, String directory) throws SQLException {

        List<SalesInfo> salesInfoList = new ArrayList<>();

        EQSSettings eqsSettings = springContext.containsBean("eqsSettings") ? (EQSSettings) springContext.getBean("eqsSettings") : null;
        boolean appendBarcode = eqsSettings != null && eqsSettings.getAppendBarcode() != null && eqsSettings.getAppendBarcode();
        String giftCardRegexp = eqsSettings != null ? eqsSettings.getGiftCardRegexp() : null;

        Set<Integer> readRecordSet = new HashSet<>();

        Statement statement = null;
        try {
            statement = conn.createStatement();
            String query = "SELECT type, ecr, doc, barcode, code, qty, price, amount, discount, department, flags, date, id," +
                    " zreport, payment, customer, `change` FROM history WHERE new = 1 ORDER BY ecr, id";
            ResultSet rs = statement.executeQuery(query);

            sendSalesLogger.info(logPrefix + "readSales query executed");

            int position = 0;
            List<SalesInfo> currentSalesInfoList = new ArrayList<>();
            Map<String, SalesInfo> saleReturnMap = new HashMap<>();
            Set<Integer> currentReadRecordSet = new HashSet<>();
            while (rs.next()) {

                Integer id = rs.getInt(13);
                Integer type = rs.getInt(1); //type, Тип операции: 1. 2. Закрытие смены 3. Внесение 4. Выдача 5. Открытие чека 6. Регистрация 7. Оплата 8. Закрытие чека

                Date dateReceipt = rs.getDate(12); // r.date
                Time timeReceipt = rs.getTime(12); //r.date
                String numberZReport = rs.getString(14); //zReport
                Integer numberReceipt = rs.getInt(3); //doc, Номер чека
                if(numberZReport != null && numberZReport.trim().equals("0")) {
                    numberReceipt = numberReceipt * 10000 + timeReceipt.getHours() * 100 + timeReceipt.getMinutes();
                }
                switch (type) {
                    case 1: //Открытие смены
                    case 2: //Закрытие смены
                    case 3: //Внесение
                    case 4: //Выдача
                        readRecordSet.add(id);
                        break;
                    case 5: //Открытие чека
                        position = 0;
                        currentSalesInfoList = new ArrayList<>();
                        currentReadRecordSet = new HashSet<>();
                        saleReturnMap = new HashMap<>();
                        currentReadRecordSet.add(id);
                        break;
                    case 6: //Регистрация
                        currentReadRecordSet.add(id);
                    
                        position++;

                        Integer cash_id = rs.getInt(2); //ecr, Номер КСА

                        CashRegisterInfo cashRegister = machineryMap.get(cash_id);
                        Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;

                        String idBarcode = appendCheckDigitToBarcode(rs.getString(4), 7, appendBarcode); //barcode, Штрих-код товара
                        String idItem = String.valueOf(rs.getLong(5)); //code, Код товара
                        BigDecimal totalQuantity = rs.getBigDecimal(6); //qty, Количество
                        BigDecimal price = rs.getBigDecimal(7); //price, Цена
                        BigDecimal sum = rs.getBigDecimal(8); //amount, Сумма
                        BigDecimal discountSum = HandlerUtils.safeAbs(rs.getBigDecimal(9)); //discount, Сумма скидки/наценки
                        sum = HandlerUtils.safeAdd(sum, rs.getBigDecimal(9)); //discountSum is negative
                        String idSection = rs.getString(10); //department, Номер отдела

                        Integer flags = rs.getInt(11); //flags, Флаги: bit 0 - Возврат bit 1 - Скидка/Наценка (при любой скидке этот бит всегда = 1) bit 2 - Сторнирование/Коррекция
                        //0 - продажа, 1 - возврат, 4 - возврат со сторнированием/коррекцией

                        //тут порядок обратный
                        boolean isSale = !getBit(flags, 2);
                        boolean isReturn = getBit(flags, 2);

                        String discountCard = trim(rs.getString(16), null, 18); //r.customer
                        if (discountCard != null && discountCard.isEmpty())
                            discountCard = null;

                        boolean isGiftCard = false;
                        if (giftCardRegexp != null && idBarcode != null) {
                            Pattern pattern = Pattern.compile(giftCardRegexp);
                            Matcher matcher = pattern.matcher(idBarcode);
                            isGiftCard = matcher.matches();
                        }

                        boolean isDiscount = getBit(flags, 1);
                        boolean discountRecord = (idBarcode == null || idBarcode.isEmpty()) && isDiscount;
                        if (discountRecord) {

                            BigDecimal totalSum = BigDecimal.ZERO;
                            for (SalesInfo s : currentSalesInfoList) {
                                totalSum = safeAdd(totalSum, s.sumReceiptDetail);
                            }

                            BigDecimal remainSum = discountSum;
                            int i = 1;
                            for (SalesInfo s : currentSalesInfoList) {
                                if(i < currentSalesInfoList.size()) {
                                    BigDecimal extraDiscount = getExtraDiscount(totalSum, discountSum, s.sumReceiptDetail);
                                    s.sumReceiptDetail = safeSubtract(s.sumReceiptDetail, extraDiscount);
                                    s.discountSumReceiptDetail = safeAdd(s.discountSumReceiptDetail, extraDiscount);
                                    remainSum = safeSubtract(remainSum, extraDiscount);
                                    i++;
                                } else {
                                    s.sumReceiptDetail = safeSubtract(s.sumReceiptDetail, remainSum);
                                    s.discountSumReceiptDetail = safeAdd(s.discountSumReceiptDetail, remainSum);
                                }
                            }
                        } else {
                            SalesInfo salesInfo = new SalesInfo(isGiftCard, nppGroupMachinery, cash_id, numberZReport,
                                    dateReceipt, timeReceipt, numberReceipt, dateReceipt, timeReceipt, null,
                                    null, null, null, null, (BigDecimal) null, idBarcode, idItem, null, null, totalQuantity,
                                    price, sum, discountSum, null, discountCard,
                                    position, null, idSection);
                            //не слишком красивый хак, распознаём ситуации с продажей и последующей отменой строки
                            //(на самом деле так кассиры узнают цену). "Аннигилируем" эти две строки.
                            SalesInfo saleReturnEntry = saleReturnMap.get(idBarcode);
                            if (saleReturnEntry != null && saleReturnEntry.quantityReceiptDetail.add(totalQuantity).compareTo(BigDecimal.ZERO) == 0) {
                                saleReturnMap.remove(idBarcode);
                                currentSalesInfoList.remove(saleReturnEntry);
                            } else {
                                saleReturnMap.put(idBarcode, salesInfo);
                                currentSalesInfoList.add(salesInfo);
                            }
                        }
                        break;
                    case 7: //Оплата
                        currentReadRecordSet.add(id);

                        BigDecimal sumPayment = rs.getBigDecimal(8); //amount, Сумма
                        Integer typePayment = rs.getInt(15); //Payment, Номер оплаты
                        for (SalesInfo salesInfo : currentSalesInfoList) {
                            if (typePayment == 0)
                                salesInfo.sumCash = HandlerUtils.safeAdd(salesInfo.sumCash, sumPayment);
                            else if (typePayment == 1)
                                salesInfo.sumCard = HandlerUtils.safeAdd(salesInfo.sumCard, sumPayment);
                            else if (typePayment == 2) {
                                GiftCard sumGiftCard = salesInfo.sumGiftCardMap.get(null);
                                salesInfo.sumGiftCardMap.put(null, new GiftCard(HandlerUtils.safeAdd(sumGiftCard.sum, sumPayment)));
                            } 
                            else if (typePayment == 3)
                                salesInfo.sumCard = HandlerUtils.safeAdd(salesInfo.sumCard, sumPayment);
                            else
                                salesInfo.sumCash = HandlerUtils.safeAdd(salesInfo.sumCash, sumPayment);
                        }
                        break;
                    case 8: //Закрытие чека
                        currentReadRecordSet.add(id);

                        BigDecimal change = rs.getBigDecimal(17);
                        if (change != null && !change.equals(BigDecimal.ZERO)) {
                            for (SalesInfo salesInfo : currentSalesInfoList) {
                                salesInfo.sumCash = HandlerUtils.safeSubtract(salesInfo.sumCash, change);
                            }
                        }
                        salesInfoList.addAll(currentSalesInfoList);
                        readRecordSet.addAll(currentReadRecordSet);

                        currentSalesInfoList = new ArrayList<>();
                        currentReadRecordSet = new HashSet<>();
                        break;
                }
            }
            if (salesInfoList.size() > 0)
                sendSalesLogger.info(logPrefix + String.format("found %s records", salesInfoList.size()));
        } catch (SQLException e) {
            sendSalesLogger.error(logPrefix + "failed to read sales. ConnectionString: " + connectionString, e);
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
        return new EQSSalesBatch(salesInfoList, readRecordSet, directory);
    }

    private BigDecimal getExtraDiscount(BigDecimal totalSum, BigDecimal discountSum, BigDecimal sumReceiptDetail) {
        if(totalSum != null && discountSum != null && sumReceiptDetail != null)
            return safeDivide(BigDecimal.valueOf(safeMultiply(safeMultiply(discountSum, safeDivide(sumReceiptDetail, totalSum)), 100).intValue()), 100);
        return null;
    }

    boolean getBit(int n, int k) {
        return ((n >> k) & 1) == 1;
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<String> directorySet,
                                 Set<Integer> succeededRequests, Map<Integer, String> failedRequests, Map<Integer, String> ignoredRequests) throws IOException, ParseException {
        for (RequestExchange entry : requestExchangeList) {
            Connection conn = null;
            Statement statement = null;
            try {
                for (String directory : entry.directoryStockMap.keySet()) {
                    if (directorySet.contains(directory)) {

                        EQSConnectionString params = new EQSConnectionString(directory);
                        if (params.connectionString != null) {
                            sendSalesLogger.info(String.format(logPrefix + "connecting to %s", params.connectionString));
                            conn = DriverManager.getConnection(params.connectionString, params.user, params.password);

                            String dateFrom = new SimpleDateFormat("yyyy-MM-dd").format(entry.dateFrom);
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(entry.dateTo);
                            cal.add(Calendar.DATE, 1);
                            String dateTo = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
                            sendSalesLogger.info(String.format(logPrefix + "RequestSalesInfo: from %s to %s", dateFrom, entry.dateTo));

                            statement = conn.createStatement();
                            statement.execute(String.format("UPDATE history SET new = 1 WHERE date >= '%s' AND date <='%s'", dateFrom, dateTo));
                            succeededRequests.add(entry.requestExchange);
                        }
                    }
                }
            } catch (SQLException e) {
                failedRequests.put(entry.requestExchange, e.getMessage());
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

        for(String directory : salesBatch.directorySet) {

            EQSConnectionString params = new EQSConnectionString(directory);

            if (params.connectionString != null && salesBatch.readRecordSet != null) {

                Connection conn = null;
                PreparedStatement ps = null;

                try {
                    sendSalesLogger.info(String.format(logPrefix + "connecting to %s", params.connectionString));
                    conn = DriverManager.getConnection(params.connectionString, params.user, params.password);

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

    private String appendCheckDigitToBarcode(String barcode, Integer minLength, boolean appendBarcode) {
        if(appendBarcode) {
            if (barcode == null || (minLength != null && barcode.length() < minLength))
                return null;

            try {
                if (barcode.length() == 11) {
                    return appendEAN13("0" + barcode).substring(1, 13);
                } else if (barcode.length() == 12) {
                    return appendEAN13(barcode);
                } else if (barcode.length() == 7) {  //EAN-8
                    int checkSum = 0;
                    for (int i = 0; i <= 6; i = i + 2) {
                        checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i))) * 3;
                        checkSum += i == 6 ? 0 : Integer.valueOf(String.valueOf(barcode.charAt(i + 1)));
                    }
                    checkSum %= 10;
                    if (checkSum != 0)
                        checkSum = 10 - checkSum;
                    return barcode.concat(String.valueOf(checkSum));
                } else
                    return barcode;
            } catch (Exception e) {
                return barcode;
            }
        } else
            return barcode;
    }

    private String removeCheckDigitFromBarcode(String barcode, boolean appendBarcode) {
        if (appendBarcode && barcode != null && (barcode.length() == 13 || barcode.length() == 12 || barcode.length() == 8)) {
            return barcode.substring(0, barcode.length() - 1);
        } else
            return barcode;
    }

    private String appendEAN13(String barcode) {
        int checkSum = 0;
        for (int i = 0; i <= 10; i = i + 2) {
            checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i)));
            checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i + 1))) * 3;
        }
        checkSum %= 10;
        if (checkSum != 0)
            checkSum = 10 - checkSum;
        return barcode.concat(String.valueOf(checkSum));
    }
}