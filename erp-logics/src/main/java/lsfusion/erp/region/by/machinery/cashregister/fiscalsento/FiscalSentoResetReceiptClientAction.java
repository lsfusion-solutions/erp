package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import lsfusion.interop.action.ClientActionDispatcher;

public class FiscalSentoResetReceiptClientAction extends FiscalSentoClientAction {
    Integer documentNumberReceipt;
    
    public FiscalSentoResetReceiptClientAction(boolean isUnix, String logPath, String comPort, Integer baudRate, Integer documentNumberReceipt) {
        super(isUnix, logPath, comPort, baudRate);
        this.documentNumberReceipt = documentNumberReceipt;
    }
    
    public Object dispatch(ClientActionDispatcher dispatcher) {
    
        try {
            FiscalSento.openPort(isUnix, logPath, comPort, baudRate);
            FiscalSento.annulDocument(documentNumberReceipt);
        } catch (RuntimeException e) {
            return e.getMessage();
        } finally {
            FiscalSento.closePort();
        }
        return null;
    }
}