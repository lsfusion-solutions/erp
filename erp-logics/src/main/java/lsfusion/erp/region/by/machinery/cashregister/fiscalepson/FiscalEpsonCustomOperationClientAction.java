package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalEpsonCustomOperationClientAction implements ClientAction {

    int type;
    int comPort;
    int baudRate;

    public FiscalEpsonCustomOperationClientAction(int type, Integer comPort, Integer baudRate) {
        this.type = type;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;

    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalEpson.init();
            FiscalEpson.openPort(comPort, baudRate);
            switch (type) {
                case 1:
                    FiscalEpson.xReport();
                    break;
                case 2:
                    FiscalEpson.zReport();
                    break;
                case 4:
                    FiscalEpson.cancelReceipt(true);
                    break;
                default:
                    break;
            }
        } catch (RuntimeException e) {
            return e.getMessage();
        } finally {
            FiscalEpson.closePort();
        }
        return null;
    }
}