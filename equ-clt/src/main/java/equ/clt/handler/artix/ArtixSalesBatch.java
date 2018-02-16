package equ.clt.handler.artix;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.List;

public class ArtixSalesBatch extends SalesBatch<ArtixSalesBatch> {
    public List<String> readFiles;

    public ArtixSalesBatch(List<SalesInfo> salesInfoList, List<String> readFiles) {
        this.salesInfoList = salesInfoList;
        this.readFiles = readFiles;
    }

    @Override
    public void merge(ArtixSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        this.readFiles.addAll(mergeSalesBatch.readFiles);
    }
}