package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalEpsonServiceInOutClientAction implements ClientAction {

    int comPort;
    int baudRate;
    BigDecimal sum;

    public FiscalEpsonServiceInOutClientAction(Integer comPort, Integer baudRate, BigDecimal sum) {
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.sum = sum;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalEpson.init();

            FiscalEpson.openPort(comPort, baudRate);
            FiscalEpson.inOut(sum.longValue());
            FiscalEpson.openDrawer();

        } catch (RuntimeException e) {
            FiscalEpson.cancelReceipt(false);
            return e.getMessage();
        } finally {
            FiscalEpson.closePort();
        }
        return null;
    }
}