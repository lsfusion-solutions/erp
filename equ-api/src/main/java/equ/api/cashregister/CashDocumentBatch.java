package equ.api.cashregister;

import java.util.List;

public class CashDocumentBatch {
    public List<CashDocument> cashDocumentList;
    public List<String> readFiles;

    public CashDocumentBatch(List<CashDocument> cashDocumentList, List<String> readFiles) {
        this.cashDocumentList = cashDocumentList;
        this.readFiles = readFiles;
    }
}
