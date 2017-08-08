package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class FiscalVMKServiceInOutClientAction implements ClientAction {

    String logPath;
    String ip;
    int comPort;
    int baudRate;
    BigDecimal sum;

    public FiscalVMKServiceInOutClientAction(String logPath, String ip, Integer comPort, Integer baudRate, BigDecimal sum) {
        this.logPath = logPath;
        this.ip = ip;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.sum = sum;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalVMK.init();

            FiscalVMK.openPort(logPath, ip, comPort, baudRate);

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
