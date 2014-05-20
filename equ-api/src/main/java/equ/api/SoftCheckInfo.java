package equ.api;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Set;

public class SoftCheckInfo implements Serializable {

    public String handler;
    public BigDecimal sumUserInvoice;
    public Set<String> directorySet;
    public Set<String> invoiceSet;

    public SoftCheckInfo(String handler, BigDecimal sumUserInvoice, Set<String> directorySet, Set<String> invoiceSet) {
        this.handler = handler;
        this.sumUserInvoice = sumUserInvoice;
        this.directorySet = directorySet;
        this.invoiceSet = invoiceSet;
    }

    public void sendSoftCheckInfo(Object handler) throws IOException {
        ((MachineryHandler) handler).sendSoftCheck(this);
    }
}
