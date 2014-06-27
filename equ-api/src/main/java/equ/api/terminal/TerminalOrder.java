package equ.api.terminal;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

public class TerminalOrder implements Serializable {
    public Date date;
    public String number;
    public String supplier;
    public String barcode;
    public String name;
    public BigDecimal price;
    public BigDecimal quantity;
    public BigDecimal minQuantity;
    public BigDecimal maxQuantity;
    public BigDecimal minPrice;
    public BigDecimal maxPrice;

    public TerminalOrder(Date date, String number, String supplier, String barcode, String name, BigDecimal price, 
                         BigDecimal quantity, BigDecimal minQuantity, BigDecimal maxQuantity, BigDecimal minPrice,
                         BigDecimal maxPrice) {
        this.date = date;
        this.number = number;
        this.supplier = supplier;
        this.barcode = barcode;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.minQuantity = minQuantity;
        this.maxQuantity = maxQuantity;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }
}
