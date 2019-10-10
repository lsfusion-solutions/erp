package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import lsfusion.interop.action.ClientActionDispatcher;


public class FiscalSentoCustomOperationClientAction extends FiscalSentoClientAction {

    int type;
    String textTop;

    public FiscalSentoCustomOperationClientAction(boolean isUnix, String logPath, String comPort, Integer baudRate, int type) {
        this(isUnix, logPath, comPort, baudRate, type, null);
    }

    public FiscalSentoCustomOperationClientAction(boolean isUnix, String logPath, String comPort, Integer baudRate, int type, String textTop) {
        super(isUnix, logPath, comPort, baudRate);
        this.type = type;
        this.textTop = textTop;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {
            FiscalSento.openPort(isUnix, logPath, comPort, baudRate);
            switch (type) {
                case 1:
                    FiscalSento.printFiscalText(textTop);
                    FiscalSento.xReport();
                    break;
                case 2:
                    FiscalSento.opensmIfClose();
                    Integer zReportNumber = FiscalSento.getZReportNumber();
                    FiscalSento.printFiscalText(textTop);
                    FiscalSento.zReport();
                    FiscalSento.closePort();
                    return zReportNumber;
                case 3:
                    FiscalSento.cancelReceipt();
                    break;
                default:
                    break;
            }
            FiscalSento.closePort();
        } catch (RuntimeException e) {
            return e.getMessage();
        }
        return null;
    }
}
