package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;


public class FiscalMercuryUpdateDataClientAction implements ClientAction {

    int baudRate;
    int comPort;

    public FiscalMercuryUpdateDataClientAction(Integer baudRate, Integer comPort) {
        this.baudRate = baudRate == null ? 0 : baudRate;
        this.comPort = comPort == null ? 0 : comPort;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {
        //пока ничего не делаем
        return null;
    }
}
