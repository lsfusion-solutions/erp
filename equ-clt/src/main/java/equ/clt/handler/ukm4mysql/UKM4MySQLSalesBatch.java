package equ.clt.handler.ukm4mysql;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.List;

public class UKM4MySQLSalesBatch extends SalesBatch {

    public UKM4MySQLSalesBatch(List<SalesInfo> salesInfoList) {
        this.salesInfoList = salesInfoList;
    }
}
