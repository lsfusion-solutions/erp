package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import lsfusion.interop.action.ClientAction;

public abstract class FiscalPiritClientAction implements ClientAction {

    protected String comPort;
    protected int baudRate;
    protected String cashier;

    public FiscalPiritClientAction(String comPort, Integer baudRate, String cashier) {
        this.comPort = comPort == null ? "0" : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.cashier = cashier == null || cashier.isEmpty() ? "Кассир" : cashier;
    }
}