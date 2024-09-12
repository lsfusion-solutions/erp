package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalEpsonCustomOperationClientAction implements ClientAction {

    int type;
    int comPort;
    int baudRate;
    int offsetBefore;
    String currencyCode;
    Boolean version116;

    public FiscalEpsonCustomOperationClientAction(int type, Integer comPort, Integer baudRate) {
        // для совместимости с новой версией передаются 3 параметра в конце, их значение не важно
        this(type, comPort, baudRate, null, null,false);
    }


    public FiscalEpsonCustomOperationClientAction(int type, Integer comPort, Integer baudRate, Integer offsetBefore, String currencyCode, boolean version116) {
        this.type = type;
        this.comPort = comPort == null ? 0 : comPort;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.offsetBefore = offsetBefore == null ? 0 : offsetBefore;
        this.currencyCode = currencyCode == null ? "" : currencyCode;
        this.version116 = version116;
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
                    return FiscalEpson.zReport();
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
                    return FiscalEpson.getElectronicJournalReadOffset(); // печать эл. журнал
                case 8:
                    return FiscalEpson.checkSKNO();
                case 9:
                    return FiscalEpson.readElectronicJournal(offsetBefore, version116); // эл. журнал на экран
                case 10:
                    return FiscalEpson.isZReportOpen();
                case 11:
                    return FiscalEpson.cashSum(currencyCode);
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