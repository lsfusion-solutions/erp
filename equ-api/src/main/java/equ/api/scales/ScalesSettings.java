package equ.api.scales;

import java.io.Serializable;

public class ScalesSettings implements Serializable{

    public boolean usePLUNumberInMessage;
    
    public ScalesSettings(boolean usePLUNumberInMessage) {
        this.usePLUNumberInMessage = usePLUNumberInMessage;
    }
}
