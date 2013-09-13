package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalShtrihPrintReceiptClientAction implements ClientAction {
    int baudRate;
    int comPort;
    Boolean isReturn;
    ReceiptInstance receipt;
    
    public FiscalShtrihPrintReceiptClientAction(Integer baudRate, Integer comPort, Boolean isReturn, ReceiptInstance receipt) {
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
        this.isReturn = isReturn;
        this.receipt = receipt;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalShtrih.init();

            FiscalShtrih.openPort(comPort, baudRate);
            FiscalShtrih.printReceipt(receipt, !isReturn);
            FiscalShtrih.closePort();

        } catch (RuntimeException e) {
            FiscalShtrih.cancelReceipt(false);
            FiscalShtrih.closePort();
            return e.getMessage();
        }

        return null;
    }
}
