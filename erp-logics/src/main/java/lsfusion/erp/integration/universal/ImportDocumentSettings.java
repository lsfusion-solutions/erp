package lsfusion.erp.integration.universal;

import java.util.Map;

public class ImportDocumentSettings {

    private Map<String, String> stockMapping;
    private String fileExtension;
    private String primaryKeyType;
    private boolean checkExistence;
    private String secondaryKeyType;
    private boolean keyIsDigit;
    private Integer startRow;
    private Boolean isPosted;
    private String separator;
    private String propertyImportType;
    private boolean multipleDocuments;
    private String countryKeyType;

    public ImportDocumentSettings(Map<String, String> stockMapping, String fileExtension, String primaryKeyType, 
                                  boolean checkExistence, String secondaryKeyType, boolean keyIsDigit, Integer startRow, 
                                  Boolean isPosted, String separator, String propertyImportType, boolean multipleDocuments,
                                  String countryKeyType) {
        this.stockMapping = stockMapping;
        this.fileExtension = fileExtension;
        this.primaryKeyType = primaryKeyType;
        this.checkExistence = checkExistence;
        this.secondaryKeyType = secondaryKeyType;
        this.keyIsDigit = keyIsDigit;
        this.startRow = startRow;
        this.isPosted = isPosted;
        this.separator = separator;
        this.propertyImportType = propertyImportType;
        this.multipleDocuments = multipleDocuments;
        this.countryKeyType = countryKeyType;
    }

    public Map<String, String> getStockMapping() {
        return stockMapping;
    }

    public String getFileExtension() {
        return fileExtension;
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

    public String getSeparator() {
        return separator;
    }

    public String getPropertyImportType() {
        return propertyImportType;
    }

    public boolean isMultipleDocuments() {
        return multipleDocuments;
    }

    public String getCountryKeyType() {
        return countryKeyType;
    }
}
