package lsfusion.erp.region.by.machinery.cashregister.fiscalcasbi;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalCasbiDisplayTextClientAction implements ClientAction {

    int comPort;
    int baudRate;
    ReceiptItem item;
    

    public FiscalCasbiDisplayTextClientAction(Integer comPort, Integer baudRate, ReceiptItem item) {
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.item = item;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        FiscalCasbi.init();
        try {

            FiscalCasbi.openPort(comPort, baudRate);

            FiscalCasbi.displayText(item);

            FiscalCasbi.closePort();

        } catch (RuntimeException e) {
            return FiscalCasbi.getError(true);
        }
        return null;
    }
}
