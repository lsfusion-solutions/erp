package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import lsfusion.interop.action.ClientActionDispatcher;


public class FiscalSentoUpdateDataClientAction extends FiscalSentoClientAction {

    public FiscalSentoUpdateDataClientAction(boolean isUnix, String logPath, String comPort, Integer baudRate) {
        super(isUnix, logPath, comPort, baudRate);
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {
        try {
            FiscalSento.openPort(isUnix, logPath, comPort, baudRate);
            FiscalSento.closePort();
        } catch (RuntimeException e) {
            return e.getMessage();
        }
        return null;
    }
}
