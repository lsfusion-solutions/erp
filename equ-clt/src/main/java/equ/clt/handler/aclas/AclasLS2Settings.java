package equ.clt.handler.aclas;

public class AclasLS2Settings {

    //папка, в которой находится библиотека
    private String libraryDir;

    //папка для логов
    private String logDir;

    //в ID использовать pluNumber (если он есть) вместо idBarcode
    private boolean pluNumberAsPluId;

    //разделитель в цене - запятая (по умолчанию точка)
    private boolean commaDecimalSeparator;

    //Thread.sleep после каждого обращения к библиотеке
    private long sleepBetweenLibraryCalls;

    //не загружать горячие клавиши
    private boolean skipLoadHotKey;

    //после очистки добавлять товар "список товаров"
    private boolean loadDefaultPLU;

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

    public boolean isCommaDecimalSeparator() {
        return commaDecimalSeparator;
    }

    public void setCommaDecimalSeparator(boolean commaDecimalSeparator) {
        this.commaDecimalSeparator = commaDecimalSeparator;
    }

    public long getSleepBetweenLibraryCalls() {
        return sleepBetweenLibraryCalls;
    }

    public void setSleepBetweenLibraryCalls(long sleepBetweenLibraryCalls) {
        this.sleepBetweenLibraryCalls = sleepBetweenLibraryCalls;
    }

    public boolean isSkipLoadHotKey() {
        return skipLoadHotKey;
    }

    public void setSkipLoadHotKey(boolean skipLoadHotKey) {
        this.skipLoadHotKey = skipLoadHotKey;
    }

    public boolean isLoadDefaultPLU() {
        return loadDefaultPLU;
    }

    public void setLoadDefaultPLU(boolean loadDefaultPLU) {
        this.loadDefaultPLU = loadDefaultPLU;
    }
}