package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalEpsonPrintReceiptClientAction implements ClientAction {
    int comPort;
    int baudRate;
    Boolean isReturn;
    ReceiptInstance receipt;
    
    public FiscalEpsonPrintReceiptClientAction(Integer comPort, Integer baudRate, Boolean isReturn, ReceiptInstance receipt) {
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.isReturn = isReturn;
        this.receipt = receipt;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalEpson.init();

            FiscalEpson.openPort(comPort, baudRate);
            FiscalEpson.printReceipt(receipt, !isReturn);

        } catch (RuntimeException e) {
            FiscalEpson.cancelReceipt(false);
            return e.getMessage();
        } finally {
            FiscalEpson.closePort();
        }

        return null;
    }
}