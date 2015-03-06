package equ.clt.handler.inventoryTech;

import equ.api.terminal.TerminalDocumentBatch;
import equ.api.terminal.TerminalDocumentDetail;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class InventoryTerminalDocumentBatch extends TerminalDocumentBatch{
    public Map<String, Set<Integer>> docRecordsMap;
    public InventoryTerminalDocumentBatch(List<TerminalDocumentDetail> documentDetailList, Map<String, Set<Integer>> docRecordsMap) {
        super(documentDetailList, null);
        this.docRecordsMap = docRecordsMap;
    }
}
