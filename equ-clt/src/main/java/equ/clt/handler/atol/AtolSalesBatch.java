package equ.clt.handler.atol;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.List;

public class AtolSalesBatch extends SalesBatch {
    public List<String> readFiles;

    public AtolSalesBatch(List<SalesInfo> salesInfoList, List<String> readFiles) {
        this.salesInfoList = salesInfoList;
        this.readFiles = readFiles;
    }
}
