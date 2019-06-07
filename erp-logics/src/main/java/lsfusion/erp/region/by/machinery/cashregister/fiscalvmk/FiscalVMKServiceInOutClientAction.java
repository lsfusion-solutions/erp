package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalVMKServiceInOutClientAction extends FiscalVMKClientAction {
    BigDecimal sum;

    public FiscalVMKServiceInOutClientAction(boolean isUnix, String logPath, String ip, String comPort, Integer baudRate, BigDecimal sum) {
        super(isUnix, logPath, ip, comPort, baudRate);
        this.sum = sum;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {

            FiscalVMK.openPort(isUnix, logPath, ip, comPort, baudRate);

            FiscalVMK.opensmIfClose();

            if (!FiscalVMK.inOut(sum))
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
