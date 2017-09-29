package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalEpsonCustomOperationClientAction implements ClientAction {

    int type;
    int comPort;
    int baudRate;
    long maxDesync;

    public FiscalEpsonCustomOperationClientAction(int type, Integer comPort, Integer baudRate) {
        this(type, comPort, baudRate, null);
    }

    public FiscalEpsonCustomOperationClientAction(int type, Integer comPort, Integer baudRate, Long maxDesync) {
        this.type = type;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.maxDesync = maxDesync == null ? 0 : maxDesync;
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
                case 3:
                    FiscalEpson.electronicJournal();
                    break;
                case 4:
                    FiscalEpson.cancelReceipt(true);
                    break;
                case 5:
                    FiscalEpson.synchronizeDateTime(maxDesync);
                    break;
                case 6:
                    return FiscalEpson.checkSKNO();
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