package equ.clt.handler.belcoopsoyuz;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.List;
import java.util.Map;

public class BelCoopSoyuzSalesBatch extends SalesBatch<equ.clt.handler.belcoopsoyuz.BelCoopSoyuzSalesBatch> {
    public Map<String, Boolean> readFiles;

    public BelCoopSoyuzSalesBatch(List<SalesInfo> salesInfoList, Map<String, Boolean> readFiles) {
        this.salesInfoList = salesInfoList;
        this.readFiles = readFiles;
    }

    @Override
    public void merge(equ.clt.handler.belcoopsoyuz.BelCoopSoyuzSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        this.readFiles.putAll(mergeSalesBatch.readFiles);
    }
}