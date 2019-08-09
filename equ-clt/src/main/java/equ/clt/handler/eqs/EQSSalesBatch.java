package equ.clt.handler.eqs;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EQSSalesBatch extends SalesBatch<EQSSalesBatch> {
    Map<String, Set<Integer>> readRecordsMap = new HashMap<>();

    public EQSSalesBatch(List<SalesInfo> salesInfoList, Set<Integer> readRecordSet, String directory) {
        this.salesInfoList = salesInfoList;
        this.readRecordsMap.put(directory, readRecordSet);
    }

    @Override
    public void merge(EQSSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        for (Map.Entry<String, Set<Integer>> entry : mergeSalesBatch.readRecordsMap.entrySet()) {
            Set<Integer> recordSet = readRecordsMap.get(entry.getKey());
            if (recordSet != null)
                recordSet.addAll(entry.getValue());
            else
                recordSet = entry.getValue();
            readRecordsMap.put(entry.getKey(), recordSet);
        }
    }
}