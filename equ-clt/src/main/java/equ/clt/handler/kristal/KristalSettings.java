package equ.clt.handler.kristal;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trim;

public class KristalSettings implements Serializable{

    public String sqlUsername;
    public String sqlPassword;
    public Map<String, String> sqlHost;
    public String sqlPort;
    public String sqlDBName;
    private Boolean useIdItem;
    private Integer lastDaysCashDocument;
    private String importPrefixPath;
    private String exportPrefixPath;
    private Boolean noMessageAndScaleFiles;
    private String transformUPCBarcode; //12to13 or 13to12
    private Boolean useCheckNumber;
    private Integer maxFilesCount;
    private Integer importGroupType; // 0 - no, 1 - classic, 2 - objValue, 3 - newType
    private Boolean noRestriction;
    private Boolean deleteSuccessfulFiles;
    private String idPositionCashier;
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
