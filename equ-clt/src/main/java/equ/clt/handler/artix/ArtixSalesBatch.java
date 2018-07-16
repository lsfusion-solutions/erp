package equ.clt.handler.artix;

import equ.api.SalesBatch;
import equ.api.SalesInfo;
import equ.api.cashregister.CashierTime;

import java.util.List;
import java.util.Set;

public class ArtixSalesBatch extends SalesBatch<ArtixSalesBatch> {
    public Set<String> readFiles;

    public ArtixSalesBatch(List<SalesInfo> salesInfoList, List<CashierTime> cashierTimeList, Set<String> readFiles) {
        this.salesInfoList = salesInfoList;
        this.cashierTimeList = cashierTimeList;
        this.readFiles = readFiles;
    }

    @Override
    public void merge(ArtixSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        this.cashierTimeList.addAll(mergeSalesBatch.cashierTimeList);
        this.readFiles.addAll(mergeSalesBatch.readFiles);
    }
}