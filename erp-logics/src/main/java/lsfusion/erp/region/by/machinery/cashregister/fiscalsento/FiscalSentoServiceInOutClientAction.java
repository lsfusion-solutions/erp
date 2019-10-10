package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import lsfusion.interop.action.ClientActionDispatcher;

import java.math.BigDecimal;


public class FiscalSentoServiceInOutClientAction extends FiscalSentoClientAction {
    BigDecimal sum;

    public FiscalSentoServiceInOutClientAction(boolean isUnix, String logPath, String comPort, Integer baudRate, BigDecimal sum) {
        super(isUnix, logPath, comPort, baudRate);
        this.sum = sum;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {

            FiscalSento.openPort(isUnix, logPath, comPort, baudRate);

            FiscalSento.opensmIfClose();

            FiscalSento.inOut(sum);

            FiscalSento.openDrawer();

        } catch (RuntimeException e) {
            return e.getMessage();
        } finally {
            FiscalSento.closePort();
        }
        return null;
    }
}
