package lsfusion.erp.region.by.machinery.cashregister.fiscalmercury;

import java.io.Serializable;
import java.math.BigDecimal;

public class ReceiptItem implements Serializable {
    public Boolean isGiftCard;
    public BigDecimal price;
    public BigDecimal quantity;
    public String barcode;
    public String name;
    public BigDecimal sumPos;
    public BigDecimal articleDiscSum;

    public ReceiptItem(Boolean isGiftCard, BigDecimal price, BigDecimal quantity, String barcode, String name,
                       BigDecimal sumPos, BigDecimal articleDiscSum) {
        this.isGiftCard = isGiftCard;
        this.price = price;
        this.quantity = quantity;
        this.barcode = barcode;
        this.name = name;
        this.sumPos = sumPos;
        this.articleDiscSum = articleDiscSum;
    }
}
