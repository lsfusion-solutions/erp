package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalVMKDisplayTextClientAction implements ClientAction {

    String ip;
    int comPort;
    int baudRate;
    ReceiptItem receiptItem;

    public FiscalVMKDisplayTextClientAction(String ip, Integer comPort, Integer baudRate, ReceiptItem receiptItem) {
        this.ip = ip;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.receiptItem = receiptItem;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        FiscalVMK.init();
        try {

            FiscalVMK.openPort(ip, comPort, baudRate);

            FiscalVMK.displayText(receiptItem);

            FiscalVMK.closePort();

        } catch (RuntimeException e) {
            return FiscalVMK.getError(true);
        }
        return null;
    }
}
