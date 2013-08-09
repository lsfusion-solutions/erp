package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalMercuryServiceInOutClientAction implements ClientAction {

    int baudRate;
    int comPort;
    BigDecimal sum;

    public FiscalMercuryServiceInOutClientAction(Integer baudRate, Integer comPort, BigDecimal sum) {
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
        this.sum = sum;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {

            int type = sum.longValue()>0 ? FiscalMercury.CASH_IN : FiscalMercury.CASH_OUT;
            FiscalMercury.init(comPort, baudRate);

            if (!FiscalMercury.inOut(comPort, baudRate, type, sum.longValue()))
                return "Недостаточно наличных в кассе";
            else {
                if(!FiscalMercury.openDrawer(comPort, baudRate))
                    return "Не удалось открыть денежный ящик";
                FiscalMercury.closePort();
            }

        } catch (RuntimeException e) {
        }
        return null;
    }
}
