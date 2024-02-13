package equ.clt.handler.kristal10;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.List;

public class Kristal10SalesBatch extends SalesBatch<Kristal10SalesBatch> {
    public List<String> readFiles;

    public Kristal10SalesBatch(List<SalesInfo> salesInfoList, String extraData, List<String> readFiles) {
        this.salesInfoList = salesInfoList;
        this.extraData = extraData;
        this.readFiles = readFiles;
    }


    @Override
    public void merge(Kristal10SalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        //now extraData is not merged, so data can be lost
        if(this.readFiles != null && mergeSalesBatch.readFiles != null) {
            this.readFiles.addAll(mergeSalesBatch.readFiles);
        }
    }
}
