package equ.srv;

import equ.api.terminal.TerminalOrder;
import lsfusion.base.file.RawFileData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ServerTerminalOrder extends TerminalOrder {
    public String minDate1;
    public String maxDate1;
    public String vop;
    public List<String> extraBarcodeList;
    public RawFileData image;

    public ServerTerminalOrder(LocalDate date, LocalDate dateShipment, String number, String supplier, String barcode, String idItem, String name,
                               BigDecimal price, BigDecimal quantity, BigDecimal minQuantity, BigDecimal maxQuantity,
                               BigDecimal minPrice, BigDecimal maxPrice, String manufacturer, String weight, String color,
                               String headField1, String headField2, String headField3, String posField1, String posField2, String posField3,
                               String minDate1, String maxDate1, String vop, List<String> extraBarcodeList, RawFileData image) {
        super(date, dateShipment, number, supplier, barcode, idItem, name, price, quantity, minQuantity, maxQuantity, minPrice, maxPrice, manufacturer,
                weight, color, headField1, headField2, headField3, posField1, posField2, posField3);
        this.minDate1 = minDate1;
        this.maxDate1 = maxDate1;
        this.vop = vop;
        this.extraBarcodeList = extraBarcodeList;
        this.image = image;
    }
}