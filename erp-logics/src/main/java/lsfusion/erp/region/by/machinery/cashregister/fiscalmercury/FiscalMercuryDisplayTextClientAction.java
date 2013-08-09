package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalMercuryDisplayTextClientAction implements ClientAction {

    int baudRate;
    int comPort;
    ReceiptItem receiptItem;

    public FiscalMercuryDisplayTextClientAction(Integer baudRate, Integer comPort, ReceiptItem receiptItem) {
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
        this.receiptItem = receiptItem;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        FiscalMercury.init(comPort, baudRate);
        try {

            FiscalMercury.displayText(receiptItem);

            FiscalMercury.closePort();

        } catch (RuntimeException e) {
        }
        return null;
    }
}
