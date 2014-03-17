package equ.api;

import java.io.Serializable;

public class EquipmentServerSettings implements Serializable {
    public Integer delay;
    public Integer numberAtATime;

    public EquipmentServerSettings(Integer delay, Integer numberAtATime) {
        this.delay = delay;
        this.numberAtATime = numberAtATime;
    }
}
