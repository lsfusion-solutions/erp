package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import java.io.Serializable;
import java.math.BigDecimal;

public class ReceiptItem implements Serializable {
    public boolean isGiftCard;
    public BigDecimal price;
    public double quantity;
    private String barcode;
    public String name;
    public double sumPos;
    public double articleDiscSum;
    public double bonusSum;
    public double bonusPaid;
    public double valueVAT;

    public ReceiptItem(boolean isGiftCard, BigDecimal price, double quantity, String barcode, String name, double sumPos,
                       double articleDiscSum, double bonusSum, double bonusPaid, double valueVAT) {
        this.isGiftCard = isGiftCard;
        this.price = price;
        this.quantity = quantity;
        this.barcode = barcode;
        this.name = name;
        this.sumPos = sumPos;
        this.articleDiscSum = articleDiscSum;
        this.bonusSum = bonusSum;
        this.bonusPaid = bonusPaid;
        this.valueVAT = valueVAT;
    }

    //Absolut не умеет работать со штрихкодами, содержащими буквы, поэтому все не-цифры заменяем на 0
    public String getDigitBarcode() {
        return barcode != null ? barcode.replaceAll("[^0-9]", "0") : null;
    }
}
