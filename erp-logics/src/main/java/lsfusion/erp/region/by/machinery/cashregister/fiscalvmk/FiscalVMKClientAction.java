package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;

public abstract class FiscalVMKClientAction implements ClientAction {

    protected boolean isUnix;
    protected String logPath;
    protected String ip;
    protected int comPort;
    protected int baudRate;

    public FiscalVMKClientAction(boolean isUnix, String logPath, String ip, Integer comPort, Integer baudRate) {
        this.isUnix = isUnix;
        this.logPath = logPath;
        this.ip = ip;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
    }
}