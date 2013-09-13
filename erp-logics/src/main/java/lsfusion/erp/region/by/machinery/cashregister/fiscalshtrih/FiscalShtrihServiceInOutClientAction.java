package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalShtrihServiceInOutClientAction implements ClientAction {

    int baudRate;
    int comPort;
    BigDecimal sum;

    public FiscalShtrihServiceInOutClientAction(Integer baudRate, Integer comPort, BigDecimal sum) {
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
        this.sum = sum;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalShtrih.init();

            FiscalShtrih.openPort(comPort, baudRate);
            FiscalShtrih.inOut(sum.longValue());
            FiscalShtrih.openDrawer();
            FiscalShtrih.closePort();

        } catch (RuntimeException e) {
            FiscalShtrih.closePort();
            return e.getMessage();
        }
        return null;
    }
}
