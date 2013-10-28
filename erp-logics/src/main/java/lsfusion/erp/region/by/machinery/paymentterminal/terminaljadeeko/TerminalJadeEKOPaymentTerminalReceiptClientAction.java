package lsfusion.erp.region.by.machinery.paymentterminal.terminaljadeeko;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.math.BigDecimal;


public class TerminalJadeEKOPaymentTerminalReceiptClientAction implements ClientAction {

    int comPort;
    BigDecimal sum;
    boolean isSale;
    String comment;

    public TerminalJadeEKOPaymentTerminalReceiptClientAction(Integer comPort, BigDecimal sum, boolean isSale, String comment) {
        this.comPort = comPort == null ? 1 : comPort;
        this.sum = sum == null ? BigDecimal.ZERO : sum;
        this.isSale = isSale;
        this.comment = comment == null ? "" : comment;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        TerminalJadeEKO.init();
                                   
        return TerminalJadeEKO.operation(comPort, isSale ? 0 : 1, sum, comment);
    }
}
