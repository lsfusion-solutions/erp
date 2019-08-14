package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalEpsonPrintCopyReceiptClientAction implements ClientAction {
    int comPort;
    int baudRate;
    Integer electronicJournalReadOffset;
    Integer electronicJournalReadSize;
    Integer sessionNumber;

    public FiscalEpsonPrintCopyReceiptClientAction(int comPort, int baudRate, Integer electronicJournalReadOffset, Integer electronicJournalReadSize, Integer sessionNumber) {
        this.comPort = comPort;
        this.baudRate = baudRate;
        this.electronicJournalReadOffset = electronicJournalReadOffset;
        this.electronicJournalReadSize = electronicJournalReadSize;
        this.sessionNumber = sessionNumber;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {
            FiscalEpson.init();

            FiscalEpson.openPort(comPort, baudRate);

            FiscalEpson.printReceiptCopy(electronicJournalReadOffset, electronicJournalReadSize, sessionNumber);

        } catch (RuntimeException e) {
            FiscalEpson.cancelReceipt(false);
            return e.getMessage();
        } finally {
            FiscalEpson.closePort();
        }
        return null;
    }
}