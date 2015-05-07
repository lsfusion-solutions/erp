package equ.api.cashregister;

import java.io.Serializable;

public class HTCSettings implements Serializable{

    private boolean makeBackup;

    public HTCSettings() {
    }

    public boolean isMakeBackup() {
        return makeBackup;
    }

    public void setMakeBackup(boolean makeBackup) {
        this.makeBackup = makeBackup;
    }
}
