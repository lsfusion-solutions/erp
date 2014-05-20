package equ.api.cashregister;

import equ.api.MachineryHandler;
import equ.api.SalesBatch;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class  CashRegisterHandler<S extends SalesBatch> extends MachineryHandler<TransactionCashRegisterInfo, CashRegisterInfo, S> {

    public abstract SalesBatch readSalesInfo(List<CashRegisterInfo> cashRegisterInfoList, DBSettings dbSettings) throws IOException, ParseException, ClassNotFoundException;

    public abstract String requestSalesInfo(Map<Date, Set<String>> requestSalesInfo) throws IOException, ParseException;

    public abstract void finishReadingSalesInfo(S salesBatch);

    public abstract CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet, DBSettings dbSettings) throws ClassNotFoundException;

    public abstract void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch);
   
    public abstract Set<String> requestSucceededSoftCheckInfo(Set<String> directorySet, DBSettings dbSettings) throws ClassNotFoundException, SQLException;

}
