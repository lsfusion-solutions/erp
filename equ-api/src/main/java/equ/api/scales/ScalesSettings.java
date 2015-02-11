package equ.api.scales;

import java.io.Serializable;

public class ScalesSettings implements Serializable{

    private boolean usePLUNumberInMessage;
    private boolean newLineNoSubstring;
    private boolean useSockets;
    private boolean allowParallel;
    private Integer advancedClearMaxPLU;

    public ScalesSettings() {}
    
    //конструктор оставлен для обратной совместимости
    public ScalesSettings(boolean usePLUNumberInMessage, boolean newLineNoSubstring, boolean useSockets, boolean allowParallel, Integer advancedClearMaxPLU) {
        this.usePLUNumberInMessage = usePLUNumberInMessage;
        this.newLineNoSubstring = newLineNoSubstring;
        this.useSockets = useSockets;
        this.allowParallel = allowParallel;
        this.advancedClearMaxPLU = advancedClearMaxPLU;
    }

    public boolean isUsePLUNumberInMessage() {
        return usePLUNumberInMessage;
    }

    public void setUsePLUNumberInMessage(boolean usePLUNumberInMessage) {
        this.usePLUNumberInMessage = usePLUNumberInMessage;
    }

    public boolean isNewLineNoSubstring() {
        return newLineNoSubstring;
    }

    public void setNewLineNoSubstring(boolean newLineNoSubstring) {
        this.newLineNoSubstring = newLineNoSubstring;
    }

    public boolean isUseSockets() {
        return useSockets;
    }

    public void setUseSockets(boolean useSockets) {
        this.useSockets = useSockets;
    }

    public boolean isAllowParallel() {
        return allowParallel;
    }

    public void setAllowParallel(boolean allowParallel) {
        this.allowParallel = allowParallel;
    }

    public Integer getAdvancedClearMaxPLU() {
        return advancedClearMaxPLU;
    }

    public void setAdvancedClearMaxPLU(Integer advancedClearMaxPLU) {
        this.advancedClearMaxPLU = advancedClearMaxPLU;
    }
}
