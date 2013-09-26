package lsfusion.erp.region.by.machinery.cashregister.fiscalcasbi;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalCasbiCustomOperationClientAction implements ClientAction {

    int type;
    int comPort;
    int baudRate;


    public FiscalCasbiCustomOperationClientAction(int type, Integer comPort, Integer baudRate) {
        this.type = type;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalCasbi.init();
            FiscalCasbi.openPort(comPort, baudRate);
            switch (type) {
                case 1:
                    FiscalCasbi.xReport();
                    break;
                case 2:
                    FiscalCasbi.closeKL();
                    break;
                case 3:
                    FiscalCasbi.advancePaper();
                    break;
                case 4:
                    FiscalCasbi.cancelReceipt();
                    break;
                case 5:
                    FiscalCasbi.zReport();
                    break;
                default:
                    break;
            }
            FiscalCasbi.closePort();
        } catch (RuntimeException e) {
            return FiscalCasbi.getError(true);
        }
        return null;
    }
}
