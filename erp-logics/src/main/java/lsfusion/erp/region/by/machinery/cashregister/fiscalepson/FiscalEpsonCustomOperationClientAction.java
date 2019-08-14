package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalEpsonCustomOperationClientAction implements ClientAction {

    int type;
    int comPort;
    int baudRate;
    int offsetBefore;

    public FiscalEpsonCustomOperationClientAction(int type, Integer comPort, Integer baudRate) {
        this(type, comPort, baudRate, null);
    }

    public FiscalEpsonCustomOperationClientAction(int type, Integer comPort, Integer baudRate, Integer offsetBefore) {
        this.type = type;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.offsetBefore = offsetBefore == null ? 0 : offsetBefore;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {

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
                    return FiscalEpson.getDateTime();
                case 6:
                    FiscalEpson.synchronizeDateTime();
                    break;
                case 7:
                    return FiscalEpson.getElectronicJournalReadOffset();
                case 8:
                    return FiscalEpson.checkSKNO();
                case 9:
                    return FiscalEpson.readElectronicJournal(offsetBefore);
                case 10:
                    return FiscalEpson.isZReportOpen();
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