package equ.api.scales;

import java.io.Serializable;

public class ScalesSettings implements Serializable{

    public boolean usePLUNumberInMessage;
    public boolean newLineNoSubstring;
    public boolean useSockets;

    public ScalesSettings(boolean usePLUNumberInMessage, boolean newLineNoSubstring, boolean useSockets) {
        this.usePLUNumberInMessage = usePLUNumberInMessage;
        this.newLineNoSubstring = newLineNoSubstring;
        this.useSockets = useSockets;
    }
}
