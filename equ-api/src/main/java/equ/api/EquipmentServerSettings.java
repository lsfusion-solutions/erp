package equ.api;

import java.io.Serializable;
import java.time.LocalTime;

public class EquipmentServerSettings implements Serializable {
    public LocalTime timeFrom;
    public LocalTime timeTo;
    public Integer delay;
    public Integer sendSalesDelay;

    public EquipmentServerSettings(LocalTime timeFrom, LocalTime timeTo, Integer delay, Integer sendSalesDelay) {
        this.timeFrom = timeFrom;
        this.timeTo = timeTo;
        this.delay = delay;
        this.sendSalesDelay = sendSalesDelay;
    }
}
