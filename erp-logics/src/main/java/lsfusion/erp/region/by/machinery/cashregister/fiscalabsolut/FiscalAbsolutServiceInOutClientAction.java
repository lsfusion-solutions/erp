package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalAbsolutServiceInOutClientAction implements ClientAction {

    String logPath;
    int comPort;
    int baudRate;
    BigDecimal sum;

    FiscalAbsolutServiceInOutClientAction(String logPath, Integer comPort, Integer baudRate, BigDecimal sum) {
        this.logPath = logPath;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.sum = sum;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) {

        boolean opened = false;
        try {

            if(sum != null) {
                FiscalAbsolut.openPort(logPath, comPort, baudRate);

                FiscalAbsolut.smenBegin();

                opened = FiscalAbsolut.openReceipt(true);

                if (opened) {
                    if (!FiscalAbsolut.inOut(sum)) {
                        FiscalAbsolut.cancelReceipt();
                        return FiscalAbsolut.getError(true);
                    } else {
                        FiscalAbsolut.closeReceipt();
                    }
                } else
                    return FiscalAbsolut.getError(true);
            }
        } catch (RuntimeException e) {
            if(opened) {
                FiscalAbsolut.cancelReceipt();
                return FiscalAbsolut.getError(true);
            } else {
                return e.getMessage();
            }
        }
        finally {
            FiscalAbsolut.closePort();
        }
        return null;
    }
}
