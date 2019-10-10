package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import lsfusion.interop.action.ClientActionDispatcher;


public class FiscalSentoPrintCopyReceiptClientAction extends FiscalSentoClientAction {

    public FiscalSentoPrintCopyReceiptClientAction(boolean isUnix, String logPath, String comPort, Integer baudRate) {
        super(isUnix, logPath, comPort, baudRate);
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {
            FiscalSento.openPort(isUnix, logPath, comPort, baudRate);
            FiscalSento.repeatReceipt();
            return null;
        } catch (RuntimeException e) {
            FiscalSento.cancelReceipt();
            return e.getMessage();
        } finally {
            FiscalSento.closePort();
        }
    }
}
