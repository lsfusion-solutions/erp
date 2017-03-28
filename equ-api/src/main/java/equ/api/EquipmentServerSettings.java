package equ.api;

import java.io.Serializable;
import java.sql.Time;

public class EquipmentServerSettings implements Serializable {
    public Time timeFrom;
    public Time timeTo;
    public Integer delay;
    public Integer sendSalesDelay;

    public EquipmentServerSettings(Time timeFrom, Time timeTo, Integer delay, Integer sendSalesDelay) {
        this.timeFrom = timeFrom;
        this.timeTo = timeTo;
        this.delay = delay;
        this.sendSalesDelay = sendSalesDelay;
    }
}
