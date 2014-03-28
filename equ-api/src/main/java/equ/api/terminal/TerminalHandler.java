package equ.api.terminal;

import equ.api.MachineryHandler;
import equ.api.SalesBatch;
import equ.api.TransactionInfo;

import java.io.IOException;

public abstract class TerminalHandler<S extends SalesBatch> extends MachineryHandler<TransactionTerminalInfo, TerminalInfo, S> {

    public abstract void saveTransactionInfo(TransactionInfo transactionInfo) throws IOException;

    public abstract void sendTransactionInfo(String directory, String dbPath) throws IOException;
}
