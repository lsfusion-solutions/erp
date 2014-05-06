package lsfusion.erp.integration.universal;

import java.util.Map;

public class ImportDocumentSettings {

    private Map<String, String> stockMapping;
    private String primaryKeyType;
    private boolean checkExistence;
    private String secondaryKeyType;
    private boolean keyIsDigit;
    private Integer startRow;
    private Boolean isPosted;
    private String csvSeparator;
    private String propertyImportType;

    public ImportDocumentSettings(Map<String, String> stockMapping, String primaryKeyType, boolean checkExistence,
                                  String secondaryKeyType, boolean keyIsDigit, Integer startRow, Boolean isPosted,
                                  String csvSeparator, String propertyImportType) {
        this.stockMapping = stockMapping;
        this.primaryKeyType = primaryKeyType;
        this.checkExistence = checkExistence;
        this.secondaryKeyType = secondaryKeyType;
        this.keyIsDigit = keyIsDigit;
        this.startRow = startRow;
        this.isPosted = isPosted;
        this.csvSeparator = csvSeparator;
        this.propertyImportType = propertyImportType;
    }

    public Map<String, String> getStockMapping() {
        return stockMapping;
    }

    public String getPrimaryKeyType() {
        return primaryKeyType;
    }

    public boolean isCheckExistence() {
        return checkExistence;
    }

    public String getSecondaryKeyType() {
        return secondaryKeyType;
    }

    public boolean isKeyIsDigit() {
        return keyIsDigit;
    }

    public Integer getStartRow() {
        return startRow;
    }

    public Boolean isPosted() {
        return isPosted;
    }

    public String getCsvSeparator() {
        return csvSeparator;
    }

    public String getPropertyImportType() {
        return propertyImportType;
    }

}
