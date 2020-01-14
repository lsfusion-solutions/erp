package equ.clt.handler.kristal;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trim;

public class KristalSettings implements Serializable{

    //Параметр User для подключения к БД
    public String sqlUsername;

    //Параметр Password для подключения к БД
    public String sqlPassword;

    //Map директорий на Параметр Host для подключения к БД
    public Map<String, String> sqlHost;

    //Параметр Port для подключения к БД
    public String sqlPort;

    //Параметр databaseName для подключения к БД
    public String sqlDBName;

    //Если true, то в качестве идентификатора товара используется idItem, а не idBarcode
    private Boolean useIdItem;

    //Если задано, то readCashDocumentInfo читает только CashDocument не старше указанного кол-ва дней.
    //По умолчанию читаются все CashDocument
    private Integer lastDaysCashDocument;

    //Путь к папке импорта внутри директории обмена. По умолчанию - /ImpExp/Import/
    private String importPrefixPath;

    //Путь к папке экспорта внутри директории обмена. По умолчанию - /Export/
    private String exportPrefixPath;

    //Если true, то файлы message.txt и scale.txt не выгружаются
    private Boolean noMessageAndScaleFiles;

    //В readSalesInfo трансформирует UPC штрихкод
    //если равен "13to12", длина ШК = 13 и ШК начинается с "0", то отрезает этот "0"
    //если равен "12to13" и длина ШК = 12, добавляет в начало "0"
    private String transformUPCBarcode;

    //Если true, номер чека читается из поля CK_NUMBER, иначе из ID
    private Boolean useCheckNumber;

    //Максимальное количество считываемых файлов реализации. Если не задано, берутся все найденные файлы
    private Integer maxFilesCount;

    //Формат выгрузки idItemGroup: не задано или 0 - no, 1 - classic, 2 - objValue, 3 - newType
    private Integer importGroupType;

    //Если true, то файл restriction.txt не выгружается
    private Boolean noRestriction;

    //Если true, то успешно принятые файлы реализации удаляются. Иначе - сохраняются в подпапке success
    private Boolean deleteSuccessfulFiles;

    //Выгрузка в cashier.txt только данных с совпадающим idCashier с заданным. Если не задано, выгружается всё
    private String idPositionCashier;

    //Если задано, то idItemGroup всегда равно 1|00|00|00|00
    private boolean zeroesInItemGroup;

    public KristalSettings(String sqlUsername, String sqlPassword, String sqlHost, String sqlPort, String sqlDBName) {
        this.sqlUsername = sqlUsername;
        this.sqlPassword = sqlPassword;       
        this.sqlHost = new HashMap<>();
        if(!sqlHost.isEmpty()) {
            String[] hosts = sqlHost.split(",");
            for (String host : hosts) {
                String[] entry = host.split("->");
                String dir = trim(entry[0]);
                if(dir != null) {
                    this.sqlHost.put(dir.toLowerCase(), trim(entry.length >= 2 ? entry[1] : entry[0]));
                }
            }
        }
        this.sqlPort = sqlPort;
        this.sqlDBName = sqlDBName;
    }

    public void setUseIdItem(Boolean useIdItem) {
        this.useIdItem = useIdItem;
    }

    public Boolean getUseIdItem() {
        return useIdItem;
    }

    public Integer getLastDaysCashDocument() {
        return lastDaysCashDocument;
    }

    public void setLastDaysCashDocument(Integer lastDaysCashDocument) {
        this.lastDaysCashDocument = lastDaysCashDocument;
    }

    public String getImportPrefixPath() {
        return importPrefixPath;
    }

    public void setImportPrefixPath(String importPrefixPath) {
        this.importPrefixPath = importPrefixPath;
    }

    public String getExportPrefixPath() {
        return exportPrefixPath;
    }

    public void setExportPrefixPath(String exportPrefixPath) {
        this.exportPrefixPath = exportPrefixPath;
    }

    public void setNoMessageAndScaleFiles(Boolean noMessageAndScaleFiles) {
        this.noMessageAndScaleFiles = noMessageAndScaleFiles;
    }

    public Boolean getNoMessageAndScaleFiles() {
        return noMessageAndScaleFiles;
    }

    public void setTransformUPCBarcode(String transformUPCBarcode) {
        this.transformUPCBarcode = transformUPCBarcode;
    }

    public String getTransformUPCBarcode() {
        return transformUPCBarcode;
    }

    public void setUseCheckNumber(Boolean useCheckNumber) {
        this.useCheckNumber = useCheckNumber;
    }

    public Boolean getUseCheckNumber() {
        return useCheckNumber;
    }

    public Integer getMaxFilesCount() {
        return maxFilesCount;
    }

    public void setMaxFilesCount(Integer maxFilesCount) {
        this.maxFilesCount = maxFilesCount;
    }

    public Integer getImportGroupType() {
        return importGroupType;
    }

    public void setImportGroupType(Integer importGroupType) {
        this.importGroupType = importGroupType;
    }

    public Boolean getNoRestriction() {
        return noRestriction;
    }

    public void setNoRestriction(Boolean noRestriction) {
        this.noRestriction = noRestriction;
    }

    public Boolean getDeleteSuccessfulFiles() {
        return deleteSuccessfulFiles;
    }

    public void setDeleteSuccessfulFiles(Boolean deleteSuccessfulFiles) {
        this.deleteSuccessfulFiles = deleteSuccessfulFiles;
    }

    public String getIdPositionCashier() {
        return idPositionCashier;
    }

    public void setIdPositionCashier(String idPositionCashier) {
        this.idPositionCashier = idPositionCashier;
    }

    public boolean isZeroesInItemGroup() {
        return zeroesInItemGroup;
    }

    public void setZeroesInItemGroup(boolean zeroesInItemGroup) {
        this.zeroesInItemGroup = zeroesInItemGroup;
    }
}
