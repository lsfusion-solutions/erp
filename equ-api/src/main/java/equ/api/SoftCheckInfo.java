package equ.api;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public class SoftCheckInfo implements Serializable {

    public String handler;
    public Set<String> directorySet;
    public Map<String, String> invoiceMap;

    public SoftCheckInfo(String handler, Set<String> directorySet, Map<String, String> invoiceMap) {
        this.handler = handler;
        this.directorySet = directorySet;
        this.invoiceMap = invoiceMap;
    }

    public void sendSoftCheckInfo(Object handler) throws IOException {
        ((MachineryHandler) handler).sendSoftCheck(this);
    }
}
