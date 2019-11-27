package equ.clt.handler.aclas;

public class AclasLS2Settings {
    private String libraryDir;
    private String logDir;
    private boolean pluNumberAsPluId;
    private long sleepBetweenLibraryCalls;

    public String getLibraryDir() {
        return libraryDir;
    }

    public void setLibraryDir(String libraryDir) {
        this.libraryDir = libraryDir;
    }

    public String getLogDir() {
        return logDir;
    }

    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    public boolean isPluNumberAsPluId() {
        return pluNumberAsPluId;
    }

    public void setPluNumberAsPluId(boolean pluNumberAsPluId) {
        this.pluNumberAsPluId = pluNumberAsPluId;
    }

    public long getSleepBetweenLibraryCalls() {
        return sleepBetweenLibraryCalls;
    }

    public void setSleepBetweenLibraryCalls(long sleepBetweenLibraryCalls) {
        this.sleepBetweenLibraryCalls = sleepBetweenLibraryCalls;
    }
}