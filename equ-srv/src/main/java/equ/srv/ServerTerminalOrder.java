package equ.srv;

import equ.api.terminal.TerminalOrder;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ServerTerminalOrder extends TerminalOrder {
    public String minDate1;
    public String maxDate1;
    public String vop;

    public ServerTerminalOrder(LocalDate date, String number, String supplier, String barcode, String idItem, String name,
                               BigDecimal price, BigDecimal quantity, BigDecimal minQuantity, BigDecimal maxQuantity,
                               BigDecimal minPrice, BigDecimal maxPrice, String manufacturer, String weight, String color,
                               String headField1, String headField2, String headField3, String posField1, String posField2, String posField3,
                               String minDate1, String maxDate1, String vop) {
        super(date, number, supplier, barcode, idItem, name, price, quantity, minQuantity, maxQuantity, minPrice, maxPrice, manufacturer,
                weight, color, headField1, headField2, headField3, posField1, posField2, posField3);
        this.minDate1 = minDate1;
        this.maxDate1 = maxDate1;
        this.vop = vop;
    }
}