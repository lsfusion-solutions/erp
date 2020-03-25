package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import java.io.Serializable;
import java.math.BigDecimal;

public class ReceiptItem implements Serializable {
    public BigDecimal price;
    public double quantity;
    public String barcode;
    public String name;
    public double sumPos;
    public double articleDiscSum;
    public Integer numberSection;

    public ReceiptItem(BigDecimal price, double quantity, String barcode, String name, double sumPos,
                       double articleDiscSum, Integer numberSection) {
        this.price = price;
        this.quantity = quantity;
        this.barcode = barcode;
        this.name = name;
        this.sumPos = sumPos;
        this.articleDiscSum = articleDiscSum;
        this.numberSection = numberSection;
    }
}
