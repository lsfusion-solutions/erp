package equ.api.cashregister;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class DBSettings implements Serializable{

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

    public DBSettings(String sqlUsername, String sqlPassword, String sqlHost, String sqlPort, String sqlDBName) {
        this.sqlUsername = sqlUsername;
        this.sqlPassword = sqlPassword;       
        this.sqlHost = new HashMap<>();
        if(!sqlHost.isEmpty()) {
            String[] hosts = sqlHost.split(",");
            for (String host : hosts) {
                String[] entry = host.split("->");
                this.sqlHost.put(entry[0], entry.length >= 2 ? entry[1] : entry[0]);
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
}
