package equ.clt.handler.ukm4mysql;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.List;

public class UKM4MySQLSalesBatch extends SalesBatch {
    public List<String> readFiles;

    public UKM4MySQLSalesBatch(List<SalesInfo> salesInfoList, List<String> readFiles) {
        this.salesInfoList = salesInfoList;
        this.readFiles = readFiles;
    }
}
