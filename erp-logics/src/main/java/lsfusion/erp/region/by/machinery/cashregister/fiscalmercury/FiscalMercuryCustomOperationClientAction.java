package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalMercuryCustomOperationClientAction implements ClientAction {

    int type;
    int baudRate;
    int comPort;

    public FiscalMercuryCustomOperationClientAction(int type, Integer baudRate, Integer comPort) {
        this.type = type;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalMercury.init(comPort, baudRate);
            switch (type) {
                case 1:
                    FiscalMercury.xReport(comPort, baudRate);
                    break;
                case 2:
                    FiscalMercury.zReport(comPort, baudRate);
                    break;
                case 3:
                    FiscalMercury.advancePaper();
                    break;
                case 4:
                    FiscalMercury.cancelReceipt();
                    break;
                default:
                    break;
            }
            //FiscalMercury.closePort();
        } catch (RuntimeException e) {
        }
        return null;
    }
}
