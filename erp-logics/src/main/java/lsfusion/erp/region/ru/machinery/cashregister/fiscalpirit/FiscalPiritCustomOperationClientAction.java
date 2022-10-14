package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import jssc.SerialPort;
import lsfusion.interop.action.ClientActionDispatcher;

public class FiscalPiritCustomOperationClientAction extends FiscalPiritClientAction {
    int type;

    public FiscalPiritCustomOperationClientAction(boolean isUnix, String comPort, Integer baudRate, String cashier, int type) {
        super(isUnix, comPort, baudRate, cashier);
        this.type = type;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {

        SerialPort serialPort = null;
        try {
            serialPort = FiscalPirit.openPort(comPort, baudRate, isUnix);
            FiscalPirit.preparePrint(serialPort);
            switch (type) {
                case 1:
                    FiscalPirit.xReport(serialPort);
                    break;
                case 2:
                    return FiscalPirit.zReport(serialPort, cashier);
                case 3:
                    FiscalPirit.advancePaper(serialPort);
                    break;
                case 4:
                    FiscalPirit.cancelDocument(serialPort);
                    break;
                case 5:
                    FiscalPirit.openZReportIfClosed(serialPort, cashier);
                    break;
                case 6:
                    //do nothing, preparePrint is enough
                    break;
                case 7:
                    return FiscalPirit.getCashSum(serialPort);
                default:
                    break;
            }
        } catch (RuntimeException e) {
            return e.getMessage() != null ? e.getMessage() : e.toString();
        } finally {
            FiscalPirit.closePort(serialPort);
        }
        return null;
    }
}