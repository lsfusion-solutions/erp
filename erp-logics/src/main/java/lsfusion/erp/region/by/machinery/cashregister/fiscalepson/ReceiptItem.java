package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import java.io.Serializable;
import java.math.BigDecimal;

public class ReceiptItem implements Serializable {
    public Boolean isGiftCard;
    public BigDecimal price;
    public BigDecimal quantity;
    public boolean useBlisters;
    public BigDecimal blisterPrice;
    public BigDecimal blisterQuantity;
    public String barcode;
    public String name;
    public BigDecimal sumPos;
    public BigDecimal discount;
    public String vatString;
    public Integer section;

    public ReceiptItem(Boolean isGiftCard, BigDecimal price, BigDecimal quantity, boolean useBlisters, BigDecimal blisterPrice, BigDecimal blisterQuantity,
                       String barcode, String name, BigDecimal sumPos, BigDecimal discount, String vatString, Integer section) {
        this.isGiftCard = isGiftCard;
        this.price = price;
        this.quantity = quantity;
        this.useBlisters = useBlisters;
        this.blisterPrice = blisterPrice;
        this.blisterQuantity = blisterQuantity;
        this.barcode = barcode;
        this.name = name;
        this.sumPos = sumPos;
        this.discount = discount;
        this.vatString = vatString;
        this.section = section;
    }
}