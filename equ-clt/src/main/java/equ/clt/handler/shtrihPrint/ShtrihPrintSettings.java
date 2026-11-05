package equ.clt.handler.shtrihPrint;

import java.io.Serializable;

public class ShtrihPrintSettings implements Serializable{

    private boolean usePLUNumberInMessage;
    private boolean newLineNoSubstring;
    private boolean useSockets;
    private boolean allowParallel;
    private boolean capitalLetters;
    private Integer advancedClearMaxPLU;
    //Если true, не грузим состав
    private boolean skipDescription;

    public ShtrihPrintSettings() {}

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

    public boolean isCapitalLetters() {
        return capitalLetters;
    }

    public void setCapitalLetters(boolean capitalLetters) {
        this.capitalLetters = capitalLetters;
    }

    public Integer getAdvancedClearMaxPLU() {
        return advancedClearMaxPLU;
    }

    public void setAdvancedClearMaxPLU(Integer advancedClearMaxPLU) {
        this.advancedClearMaxPLU = advancedClearMaxPLU;
    }

    public boolean isSkipDescription() {
        return skipDescription;
    }

    public void setSkipDescription(boolean skipDescription) {
        this.skipDescription = skipDescription;
    }
}
