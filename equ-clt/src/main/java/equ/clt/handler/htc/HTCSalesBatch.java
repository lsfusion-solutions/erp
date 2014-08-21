package equ.clt.handler.htc;

import equ.api.SalesBatch;
import equ.api.SalesInfo;

import java.util.List;

public class HTCSalesBatch extends SalesBatch {
    public List<String> readFiles;

    public HTCSalesBatch(List<SalesInfo> salesInfoList, List<String> readFiles) {
        this.salesInfoList = salesInfoList;
        this.readFiles = readFiles;
    }
}
