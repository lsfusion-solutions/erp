package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import lsfusion.interop.action.ClientAction;

public abstract class FiscalPiritClientAction implements ClientAction {

    protected boolean isUnix;
    protected String comPort;
    protected int baudRate;
    protected String cashier;

    public FiscalPiritClientAction(boolean isUnix, String comPort, Integer baudRate, String cashier) {
        this.isUnix = isUnix;
        this.comPort = comPort == null ? "0" : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.cashier = cashier == null || cashier.isEmpty() ? "Кассир" : cashier;
    }
}