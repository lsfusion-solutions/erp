package equ.clt.handler.dreamkas;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DreamkasSalesBatch extends SalesBatch<DreamkasSalesBatch> {
    Map<String, Set<Integer>> readRecordsMap = new HashMap<>();

    public DreamkasSalesBatch(List<SalesInfo> salesInfoList) {
        this.salesInfoList = salesInfoList;
    }

    @Override
    public void merge(DreamkasSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        for (Map.Entry<String, Set<Integer>> entry : mergeSalesBatch.readRecordsMap.entrySet()) {
            Set<Integer> recordSet = readRecordsMap.get(entry.getKey());
            if (recordSet != null)
                recordSet.addAll(entry.getValue());
        }
    }
}
