package equ.api.cashregister;

import java.io.Serializable;
import java.math.BigDecimal;

public class PromotionSum implements Serializable {
    public boolean isStop;
    public BigDecimal sum;
    public BigDecimal percent;

    public PromotionSum(boolean isStop, BigDecimal sum, BigDecimal percent) {
        this.isStop = isStop;
        this.sum = sum;
        this.percent = percent;
    }
}
