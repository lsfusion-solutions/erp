package equ.api;

import java.util.List;

public abstract class SalesBatch<S extends SalesBatch> {
    public List<SalesInfo> salesInfoList;

    public abstract void merge(S mergeSalesBatch);
}
