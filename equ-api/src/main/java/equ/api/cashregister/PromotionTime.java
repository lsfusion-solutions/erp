package equ.api.cashregister;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalTime;

public class PromotionTime implements Serializable {
    public boolean isStop;
    public String idPromotionTime;
    public String day;
    public Integer numberDay;
    public LocalTime beginTime;
    public LocalTime endTime;
    public BigDecimal percent;

    public PromotionTime(boolean isStop, String idPromotionTime, String day, Integer numberDay, LocalTime beginTime, LocalTime endTime, BigDecimal percent) {
        this.isStop = isStop;
        this.idPromotionTime = idPromotionTime;
        this.day = day;
        this.numberDay = numberDay;
        this.beginTime = beginTime;
        this.endTime = endTime;
        this.percent = percent;
    }
}
