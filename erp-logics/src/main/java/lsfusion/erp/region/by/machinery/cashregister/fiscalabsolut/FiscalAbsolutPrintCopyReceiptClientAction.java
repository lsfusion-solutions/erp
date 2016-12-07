package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalAbsolutPrintCopyReceiptClientAction implements ClientAction {

    int comPort;
    int baudRate;

    FiscalAbsolutPrintCopyReceiptClientAction(Integer comPort, Integer baudRate) {
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {

            FiscalAbsolut.openPort(comPort, baudRate);
            if (!FiscalAbsolut.repeatReceipt()) {
                String error = FiscalAbsolut.getError(false);
                FiscalAbsolut.cancelReceipt();
                return error;
            }
            return null;
        } catch (RuntimeException e) {
            FiscalAbsolut.cancelReceipt();
            return FiscalAbsolut.getError(true);
        } finally {
            FiscalAbsolut.closePort();
        }
    }
}
