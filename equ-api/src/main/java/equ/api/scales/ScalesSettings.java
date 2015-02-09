package equ.api.scales;

import java.io.Serializable;

public class ScalesSettings implements Serializable{

    public boolean usePLUNumberInMessage;
    public boolean newLineNoSubstring;
    public boolean useSockets;
    public boolean allowParallel;
    public Integer advancedClearMaxPLU;

    public ScalesSettings(boolean usePLUNumberInMessage, boolean newLineNoSubstring, boolean useSockets, boolean allowParallel, Integer advancedClearMaxPLU) {
        this.usePLUNumberInMessage = usePLUNumberInMessage;
        this.newLineNoSubstring = newLineNoSubstring;
        this.useSockets = useSockets;
        this.allowParallel = allowParallel;
        this.advancedClearMaxPLU = advancedClearMaxPLU;
    }
}
