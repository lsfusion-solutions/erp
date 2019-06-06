package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalVMKDisplayTextClientAction extends FiscalVMKClientAction {
    ReceiptItem receiptItem;

    public FiscalVMKDisplayTextClientAction(boolean isUnix, String logPath, String ip, Integer comPort, Integer baudRate, ReceiptItem receiptItem) {
        super(isUnix, logPath, ip, comPort, baudRate);
        this.receiptItem = receiptItem;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {

            if(FiscalVMK.safeOpenPort(logPath, ip, comPort, baudRate, 5000)) {

                FiscalVMK.displayText(receiptItem);

                FiscalVMK.closePort();

            } else {
                return "VMK: open port timeout";
            }

        } catch (RuntimeException e) {
            return FiscalVMK.getError(true);
        }
        return null;
    }
}
