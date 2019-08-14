package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalShtrihServiceInOutClientAction implements ClientAction {

    int password;
    int comPort;
    int baudRate;
    BigDecimal sum;

    public FiscalShtrihServiceInOutClientAction(int password, Integer comPort, Integer baudRate, BigDecimal sum) {
        this.password = password;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.sum = sum;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {
            FiscalShtrih.init();

            FiscalShtrih.openPort(password, comPort, baudRate);
            FiscalShtrih.inOut(password, sum.longValue());
            FiscalShtrih.openDrawer(password);
            FiscalShtrih.closePort();

        } catch (RuntimeException e) {
            FiscalShtrih.closePort();
            return e.getMessage();
        }
        return null;
    }
}
