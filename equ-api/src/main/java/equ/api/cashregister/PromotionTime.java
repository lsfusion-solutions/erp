package equ.api.cashregister;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Time;

public class PromotionTime implements Serializable {
    public boolean isStop;
    public String day;
    public Time beginTime;
    public Time endTime;
    public BigDecimal percent;

    public PromotionTime(boolean isStop, String day, Time beginTime, Time endTime, BigDecimal percent) {
        this.isStop = isStop;
        this.day = day;
        this.beginTime = beginTime;
        this.endTime = endTime;
        this.percent = percent;
    }
}
