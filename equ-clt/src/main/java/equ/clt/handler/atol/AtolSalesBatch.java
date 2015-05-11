package equ.clt.handler.atol;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.List;
import java.util.Map;

public class AtolSalesBatch extends SalesBatch<AtolSalesBatch> {
    public Map<String, Boolean> readFiles;

    public AtolSalesBatch(List<SalesInfo> salesInfoList, Map<String, Boolean> readFiles) {
        this.salesInfoList = salesInfoList;
        this.readFiles = readFiles;
    }

    @Override
    public void merge(AtolSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        this.readFiles.putAll(mergeSalesBatch.readFiles);
    }
}
