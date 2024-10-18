package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import jssc.SerialPort;
import lsfusion.interop.action.ClientActionDispatcher;

import java.math.BigDecimal;


public class FiscalPiritServiceInOutClientAction extends FiscalPiritClientAction {
    BigDecimal sum;

    Integer versionPirit;

    public FiscalPiritServiceInOutClientAction(boolean isUnix, String comPort, Integer baudRate, String cashier, BigDecimal sum, Integer versionPirit) {
        super(isUnix, comPort, baudRate, cashier);
        this.sum = sum;
        this.versionPirit = versionPirit;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {

        SerialPort serialPort = null;
        try {
            serialPort = FiscalPirit.openPort(comPort, baudRate, isUnix);
            FiscalPirit.preparePrint(serialPort);
            FiscalPirit.inOut(serialPort, cashier, sum, versionPirit);
        } catch (RuntimeException e) {
            return e.getMessage() != null ? e.getMessage() : e.toString();
        } finally {
            FiscalPirit.closePort(serialPort);
        }
        return null;
    }
}