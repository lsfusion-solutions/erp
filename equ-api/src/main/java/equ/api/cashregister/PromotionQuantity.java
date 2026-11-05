package equ.api.cashregister;

import java.io.Serializable;
import java.math.BigDecimal;

public class PromotionQuantity implements Serializable {
    public boolean isStop;
    public String idPromotionQuantity;
    public String idItem;
    public String barcodeItem;
    public BigDecimal quantity;
    public BigDecimal percent;

    public PromotionQuantity(boolean isStop, String idPromotionQuantity, String idItem, String barcodeItem, BigDecimal quantity, BigDecimal percent) {
        this.isStop = isStop;
        this.idPromotionQuantity = idPromotionQuantity;
        this.idItem = idItem;
        this.barcodeItem = barcodeItem;
        this.quantity = quantity;
        this.percent = percent;
    }
}
