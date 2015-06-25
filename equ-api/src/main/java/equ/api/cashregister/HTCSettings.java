package equ.api.cashregister;

import java.io.Serializable;

public class HTCSettings implements Serializable{

    private boolean makeBackup;
    private boolean useDataDirectory;

    public HTCSettings() {
    }

    public boolean isMakeBackup() {
        return makeBackup;
    }

    public void setMakeBackup(boolean makeBackup) {
        this.makeBackup = makeBackup;
    }

    public boolean isUseDataDirectory() {
        return useDataDirectory;
    }

    public void setUseDataDirectory(boolean useDataDirectory) {
        this.useDataDirectory = useDataDirectory;
    }
}
