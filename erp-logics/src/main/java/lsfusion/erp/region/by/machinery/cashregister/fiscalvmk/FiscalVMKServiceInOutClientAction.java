package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalVMKServiceInOutClientAction implements ClientAction {

    int baudRate;
    int comPort;
    BigDecimal sum;

    public FiscalVMKServiceInOutClientAction(Integer baudRate, Integer comPort, BigDecimal sum) {
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
        this.sum = sum;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalVMK.init();

            FiscalVMK.openPort(comPort, baudRate);

            FiscalVMK.opensmIfClose();

            if (!FiscalVMK.inOut(sum.longValue()))
                return "Недостаточно наличных в кассе";
            else {
                if(!FiscalVMK.openDrawer())
                    return "Не удалось открыть денежный ящик";
                FiscalVMK.closePort();
            }

        } catch (RuntimeException e) {
            return FiscalVMK.getError(true);
        }
        return null;
    }
}
