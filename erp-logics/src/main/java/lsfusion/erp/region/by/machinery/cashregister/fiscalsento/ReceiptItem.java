package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import java.io.Serializable;
import java.math.BigDecimal;

public class ReceiptItem implements Serializable {
    public boolean isGiftCard;
    public BigDecimal price;
    public double quantity;
    public String barcode;
    public String name;
    public double sumPos;
    public double articleDiscSum;
    public String numberSection;

    public ReceiptItem(boolean isGiftCard, BigDecimal price, double quantity, String barcode, String name, double sumPos,
                       double articleDiscSum, String numberSection) {
        this.isGiftCard = isGiftCard;
        this.price = price;
        this.quantity = quantity;
        this.barcode = barcode;
        this.name = name;
        this.sumPos = sumPos;
        this.articleDiscSum = articleDiscSum;
        this.numberSection = numberSection;
    }
}
