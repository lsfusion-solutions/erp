package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import java.io.Serializable;
import java.math.BigDecimal;

public class ReceiptItem implements Serializable {
    public BigDecimal price;
    public BigDecimal quantity;
    public String barcode;
    public String name;
    public BigDecimal articleDiscSum;
    public String idLot;
    public String tailLot;
    public BigDecimal valueVAT;

    public ReceiptItem(BigDecimal price, BigDecimal quantity, String barcode, String name, BigDecimal articleDiscSum, String idLot, String tailLot, BigDecimal valueVAT) {
        this.price = price;
        this.quantity = quantity;
        this.barcode = barcode;
        this.name = name;
        this.articleDiscSum = articleDiscSum;
        this.idLot = idLot;
        this.tailLot = tailLot;
        this.valueVAT = valueVAT;
    }
}