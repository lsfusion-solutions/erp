package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalVMKCustomOperationClientAction implements ClientAction {

    String ip;
    int comPort;
    int baudRate;
    int type;
    String textTop;
    String denominationStage;

    public FiscalVMKCustomOperationClientAction(String ip, Integer comPort, Integer baudRate, int type) {
        this(ip, comPort, baudRate, type, null);
    }

    public FiscalVMKCustomOperationClientAction(String ip, Integer comPort, Integer baudRate, int type, String denominationStage) {
        this(ip, comPort, baudRate, type, null, denominationStage);
    }

    public FiscalVMKCustomOperationClientAction(String ip, Integer comPort, Integer baudRate, int type, String textTop, String denominationStage) {
        this.type = type;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
        this.ip = ip;
        this.textTop = textTop;
        this.denominationStage = denominationStage;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        synchronized (FiscalVMK.lock) {
            try {
                FiscalVMK.init();
                FiscalVMK.openPort(ip, comPort, baudRate);
                switch (type) {
                    case 1:
                        FiscalVMK.printFiscalText(textTop);
                        FiscalVMK.xReport();
                        break;
                    case 2:
                        FiscalVMK.opensmIfClose();
                        Integer zReportNumber = FiscalVMK.getZReportNumber(true);
                        FiscalVMK.printFiscalText(textTop);
                        FiscalVMK.zReport();
                        FiscalVMK.closePort();
                        return zReportNumber;
                    case 3:
                        FiscalVMK.advancePaper(3);
                        break;
                    case 4:
                        FiscalVMK.cancelReceipt();
                        break;
                    case 5:
                        return FiscalVMK.getCashSum(true, denominationStage);
                    case 6:
                        FiscalVMK.opensmIfClose();
                        FiscalVMK.printFiscalText(textTop);
                        FiscalVMK.zReport();
                        break;
                    case 7:
                        Integer zReportNumber2 = FiscalVMK.getZReportNumber(true);
                        FiscalVMK.closePort();
                        return zReportNumber2;
                    case 8:
                        FiscalVMK.closePort();
                        break;
                    default:
                        break;
                }
                FiscalVMK.closePort();
            } catch (RuntimeException e) {
                return FiscalVMK.getError(true);
            }
            return null;
        }
    }
}
