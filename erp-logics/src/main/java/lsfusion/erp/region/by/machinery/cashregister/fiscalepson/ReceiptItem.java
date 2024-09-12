package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import java.io.Serializable;
import java.math.BigDecimal;

public class ReceiptItem implements Serializable {
    public boolean isGiftCard;
    public boolean isCommission;
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
    public String comment;

    public BigDecimal bonusPaid;
    public Integer skuType;
    public String idLot;
    public String tailLot;

    public ReceiptItem(boolean isGiftCard, boolean isCommission, BigDecimal price, BigDecimal quantity, boolean useBlisters,
                       BigDecimal blisterPrice, BigDecimal blisterQuantity, String barcode, String name, BigDecimal sumPos,
                       BigDecimal discount, BigDecimal bonusPaid, String vatString, Integer section, String comment,
                       Integer skuType, String idLot, String tailLot) {
        this.isGiftCard = isGiftCard;
        this.isCommission = isCommission;
        this.price = price;
        this.quantity = quantity;
        this.useBlisters = useBlisters;
        this.blisterPrice = blisterPrice;
        this.blisterQuantity = blisterQuantity;
        this.barcode = barcode;
        this.name = name;
        this.sumPos = sumPos;
        this.discount = discount;
        this.bonusPaid = bonusPaid;
        this.vatString = vatString;
        this.section = section;
        this.comment = comment;
        this.skuType = skuType;
        this.idLot = idLot;
        this.tailLot = tailLot;
    }
}