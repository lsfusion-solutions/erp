package equ.api.cashregister;

import equ.api.*;
import equ.api.stoplist.StopListInfo;
import lsfusion.base.Pair;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class CashRegisterHandler<S extends SalesBatch, C extends CashDocumentBatch> extends MachineryHandler<TransactionCashRegisterInfo, CashRegisterInfo, S> {

    public abstract Pair<String, Set<String>> sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoSet) throws IOException;

    public abstract boolean sendDeleteBarcodeInfo(DeleteBarcodeInfo deleteBarcodeInfo);

    public abstract void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) throws IOException;

    public abstract void sendCashierInfoList(List<CashierInfo> cashierInfoList, RequestExchange requestExchange) throws IOException;

    public abstract List<CashierTime> requestCashierTime(RequestExchange requestExchange, List<MachineryInfo> cashRegisterInfoList) throws ClassNotFoundException, SQLException;

    public abstract void sendPromotionInfo(PromotionInfo promotionInfo, RequestExchange requestExchange) throws IOException;
    
    public abstract SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException;

    public abstract void requestSalesInfo(List<RequestExchange> requestExchangeList,
                                          Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) throws IOException;

    public abstract void finishReadingSalesInfo(S salesBatch);

    public abstract CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList) throws ClassNotFoundException;

    public abstract void finishReadingCashDocumentInfo(C cashDocumentBatch);

    public abstract void prereadFiles(List<CashRegisterInfo> cashRegisterList);

    public abstract Map<String, LocalDateTime> requestSucceededSoftCheckInfo() throws ClassNotFoundException, SQLException;
    
    public abstract List<List<Object>> checkZReportSum(Map<String, List<Object>> zReportSumMap, List<List<Object>> cashRegisterList) throws ClassNotFoundException, SQLException;

    public abstract Map<String, List<Object>> readExtraCheckZReport(List<CashRegisterInfo> cashRegisterInfoList);

    public abstract ExtraCheckZReportBatch compareExtraCheckZReport(Map<String, List<Object>> handlerZReportSumMap, Map<String, BigDecimal> baseZReportSumMap);
}
