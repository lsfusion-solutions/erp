package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalShtrihCustomOperationClientAction implements ClientAction {

    int type;
    int baudRate;
    int comPort;

    public FiscalShtrihCustomOperationClientAction(int type, Integer baudRate, Integer comPort) {
        this.type = type;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalShtrih.init();
            FiscalShtrih.openPort(comPort, baudRate);
            switch (type) {
                case 1:
                    FiscalShtrih.xReport();
                    break;
                case 2:
                    FiscalShtrih.zReport();
                    break;
                case 3:
                    FiscalShtrih.advancePaper();
                    break;
                case 4:
                    FiscalShtrih.cancelReceipt(true);
                    break;
                case 5:
                    FiscalShtrih.cutReceipt();
                    break;
                default:
                    break;
            }
            FiscalShtrih.closePort();
        } catch (RuntimeException e) {
            FiscalShtrih.closePort();
            return e.getMessage();
        }
        return null;
    }
}
