package equ.clt.handler.ukm4mysql;

import equ.api.cashregister.CashDocument;
import equ.api.cashregister.CashDocumentBatch;

import java.util.List;
import java.util.Map;

public class UKM4MySQLCashDocumentBatch extends CashDocumentBatch {

    Map<String, List<CashDocument>> directoryListCashDocumentMap;

    public UKM4MySQLCashDocumentBatch(List<CashDocument> cashDocumentList, Map<String, List<CashDocument>> directoryListCashDocumentMap) {
        super(cashDocumentList, null);
        this.directoryListCashDocumentMap = directoryListCashDocumentMap;
    }
}
