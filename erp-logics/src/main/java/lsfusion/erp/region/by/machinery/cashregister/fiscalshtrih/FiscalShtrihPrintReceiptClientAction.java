package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;


public class FiscalShtrihPrintReceiptClientAction implements ClientAction {
    int password;
    int comPort;
    int baudRate;
    Boolean isReturn;
    ReceiptInstance receipt;
    
    public FiscalShtrihPrintReceiptClientAction(int password, Integer comPort, Integer baudRate, Boolean isReturn, ReceiptInstance receipt) {
        this.password = password;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.isReturn = isReturn;
        this.receipt = receipt;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {
            FiscalShtrih.init();

            FiscalShtrih.openPort(password, comPort, baudRate);
            FiscalShtrih.printReceipt(password, receipt, !isReturn);
            FiscalShtrih.closePort();

        } catch (RuntimeException e) {
            FiscalShtrih.cancelReceipt(password, false);
            FiscalShtrih.closePort();
            return e.getMessage();
        }

        return null;
    }
}
