package equ.clt.handler.eqs;

import equ.api.SalesBatch;
import equ.api.SalesInfo;
import lsfusion.base.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EQSSalesBatch extends SalesBatch<EQSSalesBatch> {
    Set<Integer> readRecordSet;

    public EQSSalesBatch(List<SalesInfo> salesInfoList, Set<Integer> readRecordSet) {
        this.salesInfoList = salesInfoList;
        this.readRecordSet = readRecordSet;
    }

    @Override
    public void merge(EQSSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        this.readRecordSet.addAll(mergeSalesBatch.readRecordSet);
    }
}