package equ.clt.handler.eqs;

import com.google.common.collect.Sets;
import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.List;
import java.util.Set;

public class EQSSalesBatch extends SalesBatch<EQSSalesBatch> {
    Set<Integer> readRecordSet;
    Set<String> directorySet;

    public EQSSalesBatch(List<SalesInfo> salesInfoList, Set<Integer> readRecordSet, String directory) {
        this.salesInfoList = salesInfoList;
        this.readRecordSet = readRecordSet;
        this.directorySet = Sets.newHashSet(directory);
    }

    @Override
    public void merge(EQSSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        this.readRecordSet.addAll(mergeSalesBatch.readRecordSet);
        this.directorySet.addAll(mergeSalesBatch.directorySet);
    }
}