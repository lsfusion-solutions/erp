package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalAbsolutCustomOperationClientAction implements ClientAction {

    String logPath;
    int comPort;
    int baudRate;
    private int type;
    private String textTop;
    private boolean saveCommentOnFiscalTape;
    private boolean useSKNO;

    public FiscalAbsolutCustomOperationClientAction(String logPath, Integer comPort, Integer baudRate, int type,
                                                    boolean saveCommentOnFiscalTape, boolean useSKNO) {
        this(logPath, comPort, baudRate, type, null, saveCommentOnFiscalTape, useSKNO);
    }

    public FiscalAbsolutCustomOperationClientAction(String logPath, Integer comPort, Integer baudRate, int type,
                                                    String textTop, boolean saveCommentOnFiscalTape, boolean useSKNO) {
        this.logPath = logPath;
        this.type = type;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
        this.textTop = textTop;
        this.saveCommentOnFiscalTape = saveCommentOnFiscalTape;
        this.useSKNO = useSKNO;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {

        try {
            FiscalAbsolut.openPort(logPath, comPort, baudRate);
            switch (type) {
                case 1:
                    FiscalAbsolut.printFiscalText(textTop);
                    FiscalAbsolut.xReport();
                    break;
                case 2:
                    FiscalAbsolut.printFiscalText(textTop);
                    FiscalAbsolut.zReport(0);
                    FiscalAbsolut.closePort();
                    break;
                case 3:
                    FiscalAbsolut.zReport(1);
                    FiscalAbsolut.closePort();
                    break;
                case 4:
                    FiscalAbsolut.cancelReceipt();
                    break;
                case 9:
                    if(!FiscalAbsolut.zeroReceipt(useSKNO)) {
                        String error = FiscalAbsolut.getError(true);
                        FiscalAbsolut.cancelReceipt();
                        return error;
                    }
                default:
                    break;
            }
        } catch (RuntimeException e) {
            return FiscalAbsolut.getError(true);
        } finally {
            FiscalAbsolut.closePort();
        }
        return null;
    }
}
