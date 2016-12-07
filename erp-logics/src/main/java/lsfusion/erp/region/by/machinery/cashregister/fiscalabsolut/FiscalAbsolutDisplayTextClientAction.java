package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalAbsolutDisplayTextClientAction implements ClientAction {

    int comPort;
    int baudRate;
    private ReceiptItem receiptItem;

    FiscalAbsolutDisplayTextClientAction(Integer comPort, Integer baudRate, ReceiptItem receiptItem) {
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.receiptItem = receiptItem;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {

            FiscalAbsolut.openPort(comPort, baudRate);

            FiscalAbsolut.displayText(receiptItem);

        } catch (RuntimeException e) {
            return FiscalAbsolut.getError(true);
        } finally {
            FiscalAbsolut.closePort();
        }
        return null;
    }
}
