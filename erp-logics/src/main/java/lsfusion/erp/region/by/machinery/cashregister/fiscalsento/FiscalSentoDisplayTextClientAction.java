package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import lsfusion.interop.action.ClientActionDispatcher;


public class FiscalSentoDisplayTextClientAction extends FiscalSentoClientAction {
    ReceiptItem receiptItem;

    public FiscalSentoDisplayTextClientAction(boolean isUnix, String logPath, String comPort, Integer baudRate, ReceiptItem receiptItem) {
        super(isUnix, logPath, comPort, baudRate);
        this.receiptItem = receiptItem;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {

            if(FiscalSento.safeOpenPort(isUnix, logPath, comPort, baudRate, 5000)) {
                FiscalSento.displayText(receiptItem);
            } else {
                return "sento: open port timeout";
            }

        } catch (RuntimeException e) {
            return e.getMessage();
        } finally {
            FiscalSento.closePort();
        }
        return null;
    }
}
