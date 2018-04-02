package equ.clt.handler.astron;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.List;

public class AstronSalesBatch extends SalesBatch<AstronSalesBatch> {
    List<AstronHandler.AstronRecord> recordList;

    public AstronSalesBatch(List<SalesInfo> salesInfoList, List<AstronHandler.AstronRecord> recordList) {
        this.salesInfoList = salesInfoList;
        this.recordList = recordList;
    }

    @Override
    public void merge(AstronSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        this.recordList.addAll(mergeSalesBatch.recordList);
    }
}