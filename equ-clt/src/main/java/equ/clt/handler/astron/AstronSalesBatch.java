package equ.clt.handler.astron;

import com.google.common.collect.Sets;
import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.List;
import java.util.Set;

public class AstronSalesBatch extends SalesBatch<AstronSalesBatch> {
    List<AstronHandler.AstronRecord> recordList;
    public Set<String> directorySet;

    public AstronSalesBatch(List<SalesInfo> salesInfoList, List<AstronHandler.AstronRecord> recordList, String directory) {
        this.salesInfoList = salesInfoList;
        this.recordList = recordList;
        this.directorySet = Sets.newHashSet(directory);
    }

    @Override
    public void merge(AstronSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        this.recordList.addAll(mergeSalesBatch.recordList);
        this.directorySet.addAll(mergeSalesBatch.directorySet);
    }
}