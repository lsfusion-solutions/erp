package lsfusion.erp.region.by.machinery.cashregister.fiscalcasbi;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalCasbiServiceInOutClientAction implements ClientAction {

    int comPort;
    int baudRate;
    BigDecimal sum;

    public FiscalCasbiServiceInOutClientAction(Integer comPort, Integer baudRate, BigDecimal sum) {
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;

        this.sum = sum;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalCasbi.init();

            FiscalCasbi.openPort(comPort, baudRate);

            FiscalCasbi.inOut(sum.longValue());
            FiscalCasbi.openDrawer();
            FiscalCasbi.closePort();


        } catch (RuntimeException e) {
            return FiscalCasbi.getError(true);
        }
        return null;
    }
}
