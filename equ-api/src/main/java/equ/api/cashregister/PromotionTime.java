package equ.api.cashregister;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Time;

public class PromotionTime implements Serializable {
    public boolean isStop;
    public String idPromotionTime;
    public String day;
    public Integer numberDay;
    public Time beginTime;
    public Time endTime;
    public BigDecimal percent;

    public PromotionTime(boolean isStop, String idPromotionTime, String day, Integer numberDay, Time beginTime, Time endTime, BigDecimal percent) {
        this.isStop = isStop;
        this.idPromotionTime = idPromotionTime;
        this.day = day;
        this.numberDay = numberDay;
        this.beginTime = beginTime;
        this.endTime = endTime;
        this.percent = percent;
    }
}
