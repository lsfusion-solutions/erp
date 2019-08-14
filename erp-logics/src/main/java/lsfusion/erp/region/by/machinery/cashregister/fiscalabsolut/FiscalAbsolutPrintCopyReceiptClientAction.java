package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalAbsolutPrintCopyReceiptClientAction implements ClientAction {

    String logPath;
    int comPort;
    int baudRate;

    FiscalAbsolutPrintCopyReceiptClientAction(String logPath, Integer comPort, Integer baudRate) {
        this.logPath = logPath;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {

            FiscalAbsolut.openPort(logPath, comPort, baudRate);
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
