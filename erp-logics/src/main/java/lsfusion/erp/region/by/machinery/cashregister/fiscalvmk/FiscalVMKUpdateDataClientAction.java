package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalVMKUpdateDataClientAction implements ClientAction {

    String logPath;
    String ip;
    int comPort;
    int baudRate;

    public FiscalVMKUpdateDataClientAction(String logPath, String ip, Integer comPort, Integer baudRate) {
        this.logPath = logPath;
        this.ip = ip;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;

    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        FiscalVMK.init();
        try {

            FiscalVMK.openPort(logPath, ip, comPort, baudRate);
            FiscalVMK.closePort();

        } catch (RuntimeException e) {
            return FiscalVMK.getError(true);
        }
        return null;
    }
}
