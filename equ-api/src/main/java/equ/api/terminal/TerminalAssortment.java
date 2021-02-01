package equ.api.terminal;

import java.io.Serializable;
import java.math.BigDecimal;

public class TerminalAssortment implements Serializable {
    public String idBarcode;
    public String idSupplier;
    public BigDecimal price;
    public BigDecimal minPrice;
    public BigDecimal maxPrice;
    public BigDecimal quantity;

    public TerminalAssortment(String idBarcode, String idSupplier, BigDecimal price, BigDecimal minPrice, BigDecimal maxPrice, BigDecimal quantity) {
        this.idBarcode = idBarcode;
        this.idSupplier = idSupplier;
        this.price = price;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.quantity = quantity;
    }
}
