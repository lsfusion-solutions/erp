package equ.api.terminal;

import equ.api.MachineryHandler;
import equ.api.MachineryInfo;
import equ.api.SalesBatch;

import java.io.IOException;
import java.util.List;

public abstract class TerminalHandler<S extends SalesBatch> extends MachineryHandler<TransactionTerminalInfo, TerminalInfo, S> {

    public abstract void sendTerminalOrderList(List<TerminalOrder> terminalOrderList, MachineryInfo machinery);

    public abstract void saveTransactionTerminalInfo(TransactionTerminalInfo transactionInfo);
    
    public abstract TerminalDocumentBatch readTerminalDocumentInfo(List<TerminalInfo> machineryInfoList);

    public abstract void finishReadingTerminalDocumentInfo(TerminalDocumentBatch terminalDocumentBatch);
}
