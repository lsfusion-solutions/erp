package equ.clt.handler;

import equ.api.*;
import equ.api.cashregister.*;
import equ.api.stoplist.StopListInfo;
import lsfusion.base.Pair;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

public abstract class DefaultCashRegisterHandler<S extends SalesBatch, C extends CashDocumentBatch> extends CashRegisterHandler<S, C> {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");
    protected final static Logger machineryExchangeLogger = Logger.getLogger("MachineryExchangeLogger");
    protected final static Logger softCheckLogger = Logger.getLogger("SoftCheckLogger");
    protected final static Logger deleteBarcodeLogger = Logger.getLogger("DeleteBarcodeLogger");

    protected static String oplatiPaymentType = "oplati";
    protected static String salaryPaymentType = "salary";

    protected JSONObject getExtInfo(String extInfo, String id) {
        return extInfo != null && !extInfo.isEmpty() ? new JSONObject(extInfo).optJSONObject(id) : null;
    }

    protected Set<CashRegisterInfo> getCashRegisterSet(RequestExchange requestExchange, boolean extra) {
        Set<CashRegisterInfo> cashRegisterSet = new HashSet<>();
        for (CashRegisterInfo cashRegister : requestExchange.cashRegisterSet) {
            if (fitHandler(cashRegister))
                cashRegisterSet.add(cashRegister);
        }
        if (extra) {
            for (CashRegisterInfo cashRegister : requestExchange.extraCashRegisterSet) {
                if (fitHandler(cashRegister))
                    cashRegisterSet.add(cashRegister);
            }
        }
        return cashRegisterSet;
    }

    public Set<String> getDirectorySet(RequestExchange requestExchange) {
        Set<String> directorySet = new HashSet<>();
        for (CashRegisterInfo cashRegister : join(requestExchange.cashRegisterSet, requestExchange.extraCashRegisterSet)) {
            if (fitHandler(cashRegister) && cashRegister.directory != null)
                directorySet.add(cashRegister.directory);
        }
        return directorySet;
    }

    public Map<String, Set<String>> getDirectoryStockMap(RequestExchange requestExchange) {
        return getDirectoryStockMap(requestExchange, false);
    }

    public Map<String, Set<String>> getDirectoryStockMap(RequestExchange requestExchange, boolean useNumberGroupInShopIndices) {
        Map<String, Set<String>> directoryStockMap = new HashMap<>();
        for (CashRegisterInfo cashRegister : join(requestExchange.cashRegisterSet, requestExchange.extraCashRegisterSet)) {
            if (fitHandler(cashRegister) && cashRegister.directory != null) {
                Set<String> stockSet = directoryStockMap.get(cashRegister.directory);
                if (stockSet == null)
                    stockSet = new HashSet<>();
                String idStock = getIdDepartmentStore(cashRegister.numberGroup, cashRegister.idDepartmentStore, useNumberGroupInShopIndices);
                if (idStock != null)
                    stockSet.add(idStock);
                if(!useNumberGroupInShopIndices && requestExchange.idStock != null)
                    stockSet.add(requestExchange.idStock);
                directoryStockMap.put(cashRegister.directory, stockSet);
            }
        }
        return directoryStockMap;
    }

    protected String getIdDepartmentStore(Integer numberGroup, String idDepartmentStore, boolean useNumberGroupInShopIndices) {
        return useNumberGroupInShopIndices ? String.valueOf(numberGroup) : idDepartmentStore;
    }

    public Map<String, Set<CashRegisterInfo>> getDirectoryCashRegisterMap(RequestExchange requestExchange) {
        Map<String, Set<CashRegisterInfo>> directoryCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo cashRegister : join(requestExchange.cashRegisterSet, requestExchange.extraCashRegisterSet)) {
            if (fitHandler(cashRegister) && cashRegister.directory != null) {
                Set<CashRegisterInfo> stockSet = directoryCashRegisterMap.getOrDefault(cashRegister.directory, new HashSet<>());
                stockSet.add(cashRegister);
                directoryCashRegisterMap.put(cashRegister.directory, stockSet);
            }
        }
        return directoryCashRegisterMap;
    }

    Set<CashRegisterInfo> join(Set<CashRegisterInfo> set1, Set<CashRegisterInfo> set2) {
        Set<CashRegisterInfo> result = new HashSet<>(set1);
        result.addAll(set2);
        return result;
    }

    @Override
    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return null;
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionInfoList) {
        return null;
    }

    @Override
    public Pair<String, Set<String>> sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machinerySet) throws IOException {
        return null;
    }

    @Override
    public boolean sendDeleteBarcodeInfo(DeleteBarcodeInfo deleteBarcodeInfo) {
        return true;
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) throws IOException {

    }

    @Override
    public void sendCashierInfoList(List<CashierInfo> cashierInfoList, RequestExchange requestExchange) throws IOException {

    }

    @Override
    public List<CashierTime> requestCashierTime(RequestExchange requestExchange, List<MachineryInfo> cashRegisterInfoList) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public void sendPromotionInfo(PromotionInfo promotionInfo, RequestExchange requestExchange) throws IOException {

    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException {
        return null;
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) throws IOException {

    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList) throws ClassNotFoundException {
        return null;
    }

    @Override
    public void finishReadingCashDocumentInfo(C cashDocumentBatch) {
    }

    protected Set<Integer> parsePayments(String payments) {
        Set<Integer> paymentsSet = new HashSet<>();
        try {
            if (payments != null && !payments.isEmpty()) {
                for (String payment : payments.split(",")) {
                    paymentsSet.add(Integer.parseInt(payment.trim()));
                }
            }
        } catch (Exception e) {
            sendSalesLogger.error("invalid payment settings: " + payments);
        }
        return paymentsSet;
    }

    //Atol, Belcoopsoyuz, HTC, Kristal, Maxishop, UKM4
    public static SalesInfo getSalesInfo(Integer nppGroupMachinery, Integer nppMachinery, String numberZReport, LocalDate dateZReport, LocalTime timeZReport, Integer numberReceipt, LocalDate dateReceipt, LocalTime timeReceipt, String idEmployee, String firstNameContact, List<Payment> payments, String barcodeItem,
                                     String idItem, Long itemObject, String idSaleReceiptReceiptReturnDetail, BigDecimal quantityReceiptDetail,
                                     BigDecimal priceReceiptDetail, BigDecimal sumReceiptDetail, BigDecimal discountSumReceiptDetail,
                                     BigDecimal discountSumReceipt, String seriesNumberDiscountCard, Integer numberReceiptDetail, String filename,
                                     String idSection, Map<String, Object> receiptExtraFields, Map<String, Object> receiptDetailExtraFields, CashRegisterInfo cashRegisterInfo) {
        Map<String, GiftCard> sumGiftCardMap = new HashMap<>();
        sumGiftCardMap.put(null, new GiftCard(null));
        return  new SalesInfo(false, false, nppGroupMachinery, nppMachinery, numberZReport, dateZReport, timeZReport, numberReceipt, dateReceipt, timeReceipt,
                idEmployee, firstNameContact, null, sumGiftCardMap, payments, barcodeItem, idItem, itemObject,
                idSaleReceiptReceiptReturnDetail, quantityReceiptDetail, priceReceiptDetail, sumReceiptDetail, null, discountSumReceiptDetail,
                discountSumReceipt, seriesNumberDiscountCard, null, numberReceiptDetail, filename, idSection, false, receiptExtraFields, receiptDetailExtraFields, cashRegisterInfo);
    }

    @Override
    public List<List<Object>> checkZReportSum(Map<String, List<Object>> zReportSumMap, List<List<Object>> cashRegisterList) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public Map<String, List<Object>> readExtraCheckZReport(List<CashRegisterInfo> cashRegisterInfoList) {
        return null;
    }

    @Override
    public ExtraCheckZReportBatch compareExtraCheckZReport(Map<String, List<Object>> handlerZReportSumMap, Map<String, BigDecimal> baseZReportSumMap) {
        return null;
    }

    //Artix, Astron, Dreamkas, EQS, Kristal10, UKM4MySQL
    public static SalesInfo getSalesInfo(boolean isGiftCard, boolean isReturnGiftCard, Integer nppGroupMachinery, Integer nppMachinery, String numberZReport, LocalDate dateZReport,
                                         LocalTime timeZReport, Integer numberReceipt, LocalDate dateReceipt, LocalTime timeReceipt, String idEmployee, String firstNameContact,
                                         String lastNameContact, Map<String, GiftCard> sumGiftCardMap, List<Payment> payments,
                                         String barcodeItem, String idItem, Long itemObject, String idSaleReceiptReceiptReturnDetail, BigDecimal quantityReceiptDetail,
                                         BigDecimal priceReceiptDetail, BigDecimal sumReceiptDetail, BigDecimal discountPercentReceiptDetail, BigDecimal discountSumReceiptDetail,
                                         BigDecimal discountSumReceipt, String seriesNumberDiscountCard, List<Discount> discounts, Integer numberReceiptDetail, String filename,
                                         String idSection, boolean skipReceipt, Map<String, Object> receiptExtraFields, Map<String, Object> receiptDetailExtraFields, CashRegisterInfo cashRegisterInfo) {
        if ((quantityReceiptDetail != null && quantityReceiptDetail.compareTo(BigDecimal.valueOf(1000000)) > 0) ||
            (priceReceiptDetail != null && priceReceiptDetail.compareTo(BigDecimal.valueOf(1000000)) > 0) ||
            (sumReceiptDetail != null && sumReceiptDetail.compareTo(BigDecimal.valueOf(1000000)) > 0) ||
            (discountSumReceiptDetail != null && discountSumReceiptDetail.compareTo(BigDecimal.valueOf(1000000)) > 0) ||
            (discountSumReceipt != null && discountSumReceipt.compareTo(BigDecimal.valueOf(1000000)) > 0))
            sendSalesLogger.error("Too big value for quantity or sum for detail : " + nppMachinery + " / " + nppMachinery + " / " + " receipt number " + numberReceipt + " idItem " + idItem);
        return new SalesInfo(isGiftCard, isReturnGiftCard, nppGroupMachinery, nppMachinery, numberZReport, dateZReport, timeZReport, numberReceipt, dateReceipt, timeReceipt,
                idEmployee, firstNameContact, lastNameContact, sumGiftCardMap, payments, barcodeItem, idItem, itemObject, idSaleReceiptReceiptReturnDetail, quantityReceiptDetail,
                priceReceiptDetail, sumReceiptDetail, discountPercentReceiptDetail, discountSumReceiptDetail, discountSumReceipt, seriesNumberDiscountCard, discounts,
                numberReceiptDetail, filename, idSection, skipReceipt, receiptExtraFields, receiptDetailExtraFields, cashRegisterInfo);
    }

    @Override
    public void prereadFiles(List<CashRegisterInfo> cashRegisterList) {
    }

    @Override
    public Map<String, LocalDateTime> requestSucceededSoftCheckInfo() throws ClassNotFoundException, SQLException {
        return null;
    }

    protected static boolean isPieceUOM(ItemInfo item) {
        return item.shortNameUOM != null && item.shortNameUOM.toUpperCase().startsWith("ШТ");
    }

    protected boolean isFileLocked(File file) {
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
