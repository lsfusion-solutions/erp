package equ.api.cashregister;

import equ.api.MachineryHandler;
import equ.api.RequestExchange;
import equ.api.SalesBatch;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class  CashRegisterHandler<S extends SalesBatch> extends MachineryHandler<TransactionCashRegisterInfo, CashRegisterInfo, S> {

    public abstract void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException;
    
    public abstract SalesBatch readSalesInfo(List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException, ClassNotFoundException;

    public abstract String requestSalesInfo(List<RequestExchange> requestExchangeList) throws IOException, ParseException;

    public abstract void finishReadingSalesInfo(S salesBatch);

    public abstract CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException;

    public abstract void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch);
   
    public abstract Map<String, Timestamp> requestSucceededSoftCheckInfo(Set<String> directorySet) throws ClassNotFoundException, SQLException;
    
    public abstract String checkZReportSum(Map<String, BigDecimal> zReportSumMap, List<String> idCashRegisterList) throws ClassNotFoundException, SQLException;

    public abstract ExtraCheckZReportBatch extraCheckZReportSum(List<CashRegisterInfo> cashRegisterInfoList, Map<String, BigDecimal> zReportSumMap) throws ClassNotFoundException, SQLException;
}
