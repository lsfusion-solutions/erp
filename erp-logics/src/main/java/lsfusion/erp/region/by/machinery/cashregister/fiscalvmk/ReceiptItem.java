package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import java.io.Serializable;

public class ReceiptItem implements Serializable {
    public boolean isGiftCard;
    public long price;
    public double quantity;
    public String barcode;
    public String name;
    public long sumPos;
    public long articleDiscSum;

    public ReceiptItem(boolean isGiftCard, long price, double quantity, String barcode, String name, long sumPos,
                       long articleDiscSum) {
        this.isGiftCard = isGiftCard;
        this.price = price;
        this.quantity = quantity;
        this.barcode = barcode;
        this.name = name;
        this.sumPos = sumPos;
        this.articleDiscSum = articleDiscSum;
    }
}
