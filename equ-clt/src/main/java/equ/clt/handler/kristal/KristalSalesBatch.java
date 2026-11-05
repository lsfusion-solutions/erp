package equ.clt.handler.kristal;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.List;

public class KristalSalesBatch extends SalesBatch<KristalSalesBatch> {
    public List<String> readFiles;
    public List<String> errorFiles;

    public KristalSalesBatch(List<SalesInfo> salesInfoList, List<String> readFiles, List<String> errorFiles) {
        this.salesInfoList = salesInfoList;
        this.readFiles = readFiles;
        this.errorFiles = errorFiles;
    }

    @Override
    public void merge(KristalSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        this.readFiles.addAll(mergeSalesBatch.readFiles);
        this.errorFiles.addAll(mergeSalesBatch.errorFiles);
    }
}
