package equ.clt.handler.aclas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AclasLS2Settings {

    //папка, в которой находится библиотека
    private String libraryDir;

    //имена файлов библиотек, через запятую
    private String libraryNames;

    //временная опция для тестирования распараллеливания загрузки
    private int asyncOption = 0;

    //папка для логов
    private String logDir;

    //в ID использовать pluNumber (если он есть) вместо idBarcode
    private boolean pluNumberAsPluId;

    //разделитель в цене - запятая (по умолчанию точка)
    private boolean commaDecimalSeparator;

    //Thread.sleep после каждого обращения к библиотеке
    private long sleepBetweenLibraryCalls = 0;

    //не загружать горячие клавиши
    private boolean skipLoadHotKey;

    //после очистки добавлять товар "список товаров"
    private boolean loadDefaultPLU;

    //BarcodeType для штучных товаров. По умолчанию barcodeType определяется так же, как и для весовых товаров
    private String overBarcodeTypeForPieceItems;

    public String getLibraryDir() {
        return libraryDir;
    }

    public void setLibraryDir(String libraryDir) {
        this.libraryDir = libraryDir;
    }

    public List<String> getLibraryNames() {
        List<String> result = new ArrayList<>();
        if(libraryNames != null) {
            Collections.addAll(result, libraryNames.split(","));
        }
        if(result.isEmpty())
            result.add("aclassdk");
        return result;
    }

    public void setLibraryNames(String libraryNames) {
        this.libraryNames = libraryNames;
    }

    public int getAsyncOption() {
        return asyncOption;
    }

    public void setAsyncOption(int asyncOption) {
        this.asyncOption = asyncOption;
    }

    @Deprecated
    public String getLogDir() {
        return logDir;
    }

    @Deprecated
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

    @Deprecated
    public boolean isLoadDefaultPLU() {
        return loadDefaultPLU;
    }

    @Deprecated
    public void setLoadDefaultPLU(boolean loadDefaultPLU) {
        this.loadDefaultPLU = loadDefaultPLU;
    }

    public String getOverBarcodeTypeForPieceItems() {
        return overBarcodeTypeForPieceItems;
    }

    public void setOverBarcodeTypeForPieceItems(String overBarcodeTypeForPieceItems) {
        this.overBarcodeTypeForPieceItems = overBarcodeTypeForPieceItems;
    }

    //если true, включается распараллеливание. По умолчанию false
    private boolean enableParallel;

    public boolean isEnableParallel() {
        return enableParallel;
    }

    public void setEnableParallel(boolean enableParallel) {
        this.enableParallel = enableParallel;
    }
}