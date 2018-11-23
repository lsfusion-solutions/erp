package equ.clt.handler.belcoopsoyuz;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BelCoopSoyuzSQLSalesBatch extends SalesBatch<BelCoopSoyuzSQLSalesBatch> {
    Map<String, Set<String>> readRecordsMap = new HashMap<>();

    public BelCoopSoyuzSQLSalesBatch(List<SalesInfo> salesInfoList, Set<String> readRecordSet, String directory) {
        this.salesInfoList = salesInfoList;
        this.readRecordsMap.put(directory, readRecordSet);
    }

    @Override
    public void merge(BelCoopSoyuzSQLSalesBatch mergeSalesBatch) {
        this.salesInfoList.addAll(mergeSalesBatch.salesInfoList);
        for (Map.Entry<String, Set<String>> entry : mergeSalesBatch.readRecordsMap.entrySet()) {
            Set<String> recordSet = readRecordsMap.get(entry.getKey());
            if (recordSet != null)
                recordSet.addAll(entry.getValue());
            else
                recordSet = entry.getValue();
            readRecordsMap.put(entry.getKey(), recordSet);
        }
    }
}