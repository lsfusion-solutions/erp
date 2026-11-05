package equ.api.cashregister;

import java.io.Serializable;
import java.math.BigDecimal;

public class PromotionSum implements Serializable {
    public boolean isStop;
    public String idPromotionSum;
    public BigDecimal sum;
    public BigDecimal percent;

    public PromotionSum(boolean isStop, String idPromotionSum, BigDecimal sum, BigDecimal percent) {
        this.isStop = isStop;
        this.idPromotionSum = idPromotionSum;
        this.sum = sum;
        this.percent = percent;
    }
}
