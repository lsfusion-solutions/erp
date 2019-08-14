package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalVMKCustomOperationClientAction extends FiscalVMKClientAction {

    int type;
    String textTop;

    public FiscalVMKCustomOperationClientAction(boolean isUnix, String logPath, String ip, String comPort, Integer baudRate, int type) {
        this(isUnix, logPath, ip, comPort, baudRate, type, null);
    }

    public FiscalVMKCustomOperationClientAction(boolean isUnix, String logPath, String ip, String comPort, Integer baudRate, int type, String textTop) {
        super(isUnix, logPath, ip, comPort, baudRate);
        this.type = type;
        this.textTop = textTop;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {
            FiscalVMK.openPort(isUnix, logPath, ip, comPort, baudRate);
            switch (type) {
                case 1:
                    FiscalVMK.printFiscalText(textTop);
                    FiscalVMK.xReport();
                    break;
                case 2:
                    FiscalVMK.opensmIfClose();
                    Integer zReportNumber = FiscalVMK.getZReportNumber();
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
                    return FiscalVMK.getCashSum();
                case 6:
                    FiscalVMK.opensmIfClose();
                    FiscalVMK.printFiscalText(textTop);
                    FiscalVMK.zReport();
                    break;
                case 7:
                    Integer zReportNumber2 = FiscalVMK.getZReportNumber();
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
