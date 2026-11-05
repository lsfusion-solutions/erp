package equ.clt.handler.ukm4mysql;

import com.google.common.collect.Sets;
import equ.api.SalesBatch;
import equ.api.SalesInfo;
import lsfusion.base.Pair;

import java.util.List;
import java.util.Set;

public class UKM4MySQLSalesBatch extends SalesBatch<UKM4MySQLSalesBatch> {
    Set<Pair<Integer, Integer>> receiptSet;
    public Set<String> directorySet;

    UKM4MySQLSalesBatch(List<SalesInfo> salesInfoList, Set<Pair<Integer, Integer>> receiptSet, String directory) {
        this.salesInfoList = salesInfoList;
        this.receiptSet = receiptSet;
        this.directorySet = Sets.newHashSet(directory);
    }

    @Override
    public void merge(UKM4MySQLSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        this.receiptSet.addAll(mergeSalesBatch.receiptSet);
        this.directorySet.addAll(mergeSalesBatch.directorySet);
    }
}
