package equ.api;

import java.io.Serializable;
import java.sql.Time;

public class EquipmentServerSettings implements Serializable {
    public Time timeFrom;
    public Time timeTo;
    public Integer delay;
    public Integer numberAtATime;
    public Integer sendSalesDelay;

    public EquipmentServerSettings(Time timeFrom, Time timeTo, Integer delay, Integer numberAtATime, Integer sendSalesDelay) {
        this.timeFrom = timeFrom;
        this.timeTo = timeTo;
        this.delay = delay;
        this.numberAtATime = numberAtATime;
        this.sendSalesDelay = sendSalesDelay;
    }
}
