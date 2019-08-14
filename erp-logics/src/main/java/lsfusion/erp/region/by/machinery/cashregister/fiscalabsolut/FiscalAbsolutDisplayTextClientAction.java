package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalAbsolutDisplayTextClientAction implements ClientAction {

    String logPath;
    int comPort;
    int baudRate;
    private ReceiptItem receiptItem;

    FiscalAbsolutDisplayTextClientAction(String logPath, Integer comPort, Integer baudRate, ReceiptItem receiptItem) {
        this.logPath = logPath;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.receiptItem = receiptItem;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {

            FiscalAbsolut.openPort(logPath, comPort, baudRate);

            FiscalAbsolut.displayText(receiptItem);

        } catch (RuntimeException e) {
            return FiscalAbsolut.getError(true);
        } finally {
            FiscalAbsolut.closePort();
        }
        return null;
    }
}
