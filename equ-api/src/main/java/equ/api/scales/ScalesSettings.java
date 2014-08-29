package equ.api.scales;

import java.io.Serializable;

public class ScalesSettings implements Serializable{

    public boolean usePLUNumberInMessage;
    public boolean newLineNoSubstring;

    public ScalesSettings(boolean usePLUNumberInMessage, boolean newLineNoSubstring) {
        this.usePLUNumberInMessage = usePLUNumberInMessage;
        this.newLineNoSubstring = newLineNoSubstring;
    }
}
