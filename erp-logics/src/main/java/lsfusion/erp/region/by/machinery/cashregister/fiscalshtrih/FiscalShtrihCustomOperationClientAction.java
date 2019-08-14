package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalShtrihCustomOperationClientAction implements ClientAction {

    int type;
    int password;
    int comPort;
    int baudRate;

    public FiscalShtrihCustomOperationClientAction(int type, int password, Integer comPort, Integer baudRate) {
        this.type = type;
        this.password = password;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;

    }

    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {
            FiscalShtrih.init();
            FiscalShtrih.openPort(password, comPort, baudRate);
            switch (type) {
                case 1:
                    FiscalShtrih.xReport();
                    break;
                case 2:
                    FiscalShtrih.zReport();
                    break;
                case 3:
                    FiscalShtrih.advancePaper(password);
                    break;
                case 4:
                    FiscalShtrih.cancelReceipt(password, true);
                    break;
                case 5:
                    FiscalShtrih.cutReceipt(password);
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
