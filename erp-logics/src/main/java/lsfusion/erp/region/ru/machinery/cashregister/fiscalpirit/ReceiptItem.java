package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import java.io.Serializable;
import java.math.BigDecimal;

public class ReceiptItem implements Serializable {
    public BigDecimal price;
    public BigDecimal quantity;
    public String barcode;
    public String name;
    public BigDecimal articleDiscSum;
    public String gtinLot;
    public String seriesLot;

    public ReceiptItem(BigDecimal price, BigDecimal quantity, String barcode, String name, BigDecimal articleDiscSum, String gtinLot, String seriesLot) {
        this.price = price;
        this.quantity = quantity;
        this.barcode = barcode;
        this.name = name;
        this.articleDiscSum = articleDiscSum;
        this.gtinLot = gtinLot;
        this.seriesLot = seriesLot;
    }
}