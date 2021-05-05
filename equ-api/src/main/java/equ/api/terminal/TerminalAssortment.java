package equ.api.terminal;

import java.io.Serializable;
import java.math.BigDecimal;

public class TerminalAssortment implements Serializable {
    public String idBarcode;
    public String idSupplier;
    public BigDecimal price;

    public TerminalAssortment(String idBarcode, String idSupplier, BigDecimal price) {
        this.idBarcode = idBarcode;
        this.idSupplier = idSupplier;
        this.price = price;
    }
}
