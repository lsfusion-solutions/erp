package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalAbsolutCustomOperationClientAction implements ClientAction {

    int comPort;
    int baudRate;
    private int type;
    private String textTop;

    public FiscalAbsolutCustomOperationClientAction(Integer comPort, Integer baudRate, int type) {
        this(comPort, baudRate, type, null);
    }

    public FiscalAbsolutCustomOperationClientAction(Integer comPort, Integer baudRate, int type, String textTop) {
        this.type = type;
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
        this.textTop = textTop;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        try {
            FiscalAbsolut.openPort(comPort, baudRate);
            switch (type) {
                case 1:
                    FiscalAbsolut.printFiscalText(textTop);
                    FiscalAbsolut.xReport();
                    break;
                case 2:
                    //FiscalAbsolut.smenBegin();
                    FiscalAbsolut.printFiscalText(textTop);
                    FiscalAbsolut.zReport();
                    FiscalAbsolut.closePort();
                    break;
                case 4:
                    FiscalAbsolut.cancelReceipt();
                    break;
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
