package equ.api;

import equ.api.cashregister.CashierTime;

import java.util.List;

public abstract class SalesBatch<S extends SalesBatch> {
    public List<SalesInfo> salesInfoList;
    public List<CashierTime> cashierTimeList;
    public String extraData;

    public abstract void merge(S mergeSalesBatch);
}
