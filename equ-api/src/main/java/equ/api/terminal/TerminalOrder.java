package equ.api.terminal;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TerminalOrder implements Serializable {
    public LocalDate date;
    public LocalDate dateShipment;
    public String number;
    public String supplier;
    public String barcode;
    public String idItem;
    public String name;
    public BigDecimal price;
    public BigDecimal quantity;
    public BigDecimal minQuantity;
    public BigDecimal maxQuantity;
    public BigDecimal minPrice;
    public BigDecimal maxPrice;
    public String manufacturer;
    public String weight;
    public String color;
    public String headField1;
    public String headField2;
    public String headField3;
    public String posField1;
    public String posField2;
    public String posField3;

    public TerminalOrder(LocalDate date, LocalDate dateShipment, String number, String supplier, String barcode, String idItem, String name, BigDecimal price,
                         BigDecimal quantity, BigDecimal minQuantity, BigDecimal maxQuantity, BigDecimal minPrice,
                         BigDecimal maxPrice, String manufacturer, String weight, String color,
                         String headField1, String headField2, String headField3, String posField1, String posField2, String posField3) {
        this.date = date;
        this.dateShipment = dateShipment;
        this.number = number;
        this.supplier = supplier;
        this.barcode = barcode;
        this.idItem = idItem;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.minQuantity = minQuantity;
        this.maxQuantity = maxQuantity;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.manufacturer = manufacturer;
        this.weight = weight;
        this.color = color;
        this.headField1 = headField1;
        this.headField2 = headField2;
        this.headField3 = headField3;
        this.posField1 = posField1;
        this.posField2 = posField2;
        this.posField3 = posField3;
    }
}
