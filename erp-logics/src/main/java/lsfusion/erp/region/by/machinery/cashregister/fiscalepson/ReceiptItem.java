package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import java.io.Serializable;
import java.math.BigDecimal;

public class ReceiptItem implements Serializable {
    public Boolean isGiftCard;
    public BigDecimal price;
    public BigDecimal quantity;
    public String barcode;
    public String name;
    public BigDecimal sumPos;
    public BigDecimal discount;
    public String vatString;

    public ReceiptItem(Boolean isGiftCard, BigDecimal price, BigDecimal quantity, String barcode, String name,
                       BigDecimal sumPos, BigDecimal discount, String vatString) {
        this.isGiftCard = isGiftCard;
        this.price = price;
        this.quantity = quantity;
        this.barcode = barcode;
        this.name = name;
        this.sumPos = sumPos;
        this.discount = discount;
        this.vatString = vatString;
    }
}