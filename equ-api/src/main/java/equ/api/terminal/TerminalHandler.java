package equ.api.terminal;

import equ.api.MachineryHandler;
import equ.api.SalesBatch;

import java.io.IOException;
import java.util.List;

public abstract class TerminalHandler<S extends SalesBatch> extends MachineryHandler<TransactionTerminalInfo, TerminalInfo, S> {

    public abstract void saveTransactionTerminalInfo(TransactionTerminalInfo transactionInfo) throws IOException;
    
    public abstract TerminalDocumentBatch readTerminalDocumentInfo(List<TerminalInfo> machineryInfoList) throws IOException;

    public abstract void finishReadingTerminalDocumentInfo(TerminalDocumentBatch terminalDocumentBatch) throws IOException;
}
