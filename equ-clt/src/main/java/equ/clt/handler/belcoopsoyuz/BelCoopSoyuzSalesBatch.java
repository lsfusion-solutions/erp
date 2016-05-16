package equ.clt.handler.belcoopsoyuz;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.List;

public class BelCoopSoyuzSalesBatch extends SalesBatch<equ.clt.handler.belcoopsoyuz.BelCoopSoyuzSalesBatch> {
    public List<String> readFiles;

    public BelCoopSoyuzSalesBatch(List<SalesInfo> salesInfoList, List<String> readFiles) {
        this.salesInfoList = salesInfoList;
        this.readFiles = readFiles;
    }

    @Override
    public void merge(equ.clt.handler.belcoopsoyuz.BelCoopSoyuzSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        this.readFiles.addAll(mergeSalesBatch.readFiles);
    }
}