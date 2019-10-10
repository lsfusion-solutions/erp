package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import lsfusion.interop.action.ClientAction;

public abstract class FiscalSentoClientAction implements ClientAction {

    protected boolean isUnix;
    protected String logPath;
    protected String comPort;
    protected int baudRate;

    public FiscalSentoClientAction(boolean isUnix, String logPath, String comPort, Integer baudRate) {
        this.isUnix = isUnix;
        this.logPath = logPath;
        this.comPort = comPort == null ? "0" : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
    }
}
