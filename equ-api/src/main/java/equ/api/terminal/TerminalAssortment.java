package equ.api.terminal;

import java.io.Serializable;

public class TerminalAssortment implements Serializable {
    public String idBarcode;
    public String idSupplier;
    
    public TerminalAssortment(String idBarcode, String idSupplier) {
        this.idBarcode = idBarcode;
        this.idSupplier = idSupplier;
    }
}
