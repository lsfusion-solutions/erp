package equ.api;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class CashRegisterHandler<S extends SalesBatch> extends MachineryHandler<TransactionCashRegisterInfo, CashRegisterInfo, S> {

    public abstract SalesBatch readSalesInfo(List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException;

    public abstract String requestSalesInfo(Map<Date, Set<String>> requestSalesInfo) throws IOException, ParseException;

    public abstract void finishReadingSalesInfo(S salesBatch);

}
