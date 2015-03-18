package equ.api;

import java.io.Serializable;

public class EquipmentServerSettings implements Serializable {
    public Integer delay;
    public Integer numberAtATime;
    public Integer sendSalesDelay;

    public EquipmentServerSettings(Integer delay, Integer numberAtATime, Integer sendSalesDelay) {
        this.delay = delay;
        this.numberAtATime = numberAtATime;
        this.sendSalesDelay = sendSalesDelay;
    }
}
