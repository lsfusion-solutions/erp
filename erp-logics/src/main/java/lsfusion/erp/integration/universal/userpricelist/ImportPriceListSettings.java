package lsfusion.erp.integration.universal.userpricelist;

import lsfusion.server.data.value.DataObject;

public class ImportPriceListSettings {

    private String fileExtension;
    private String quantityAdjustmentColumn;
    private DataObject operationObject;
    private DataObject companyObject;
    private DataObject stockObject;
    private DataObject defaultItemGroupObject;
    private String itemKeyType;
    private String separator;
    private Integer startRow;
    private Boolean isPosted;
    private boolean doNotCreateItems;
    private boolean checkExistence;
    private boolean barcodeMaybeUPC;
    private String checkColumn;

    public ImportPriceListSettings(String fileExtension, String quantityAdjustmentColumn, DataObject operationObject, DataObject companyObject,
                                   DataObject stockObject, DataObject defaultItemGroupObject, String itemKeyType, String separator, Integer startRow,
                                   Boolean isPosted, boolean doNotCreateItems, boolean checkExistence, boolean barcodeMaybeUPC, String checkColumn) {
        this.fileExtension = fileExtension; 
        this.quantityAdjustmentColumn = quantityAdjustmentColumn;
        this.operationObject = operationObject;
        this.companyObject = companyObject;
        this.stockObject = stockObject;
        this.defaultItemGroupObject = defaultItemGroupObject;
        this.itemKeyType = itemKeyType;
        this.separator = separator;
        this.startRow = startRow;
        this.isPosted = isPosted;
        this.doNotCreateItems = doNotCreateItems;
        this.checkExistence = checkExistence;
        this.barcodeMaybeUPC = barcodeMaybeUPC;
        this.checkColumn = checkColumn;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public String getQuantityAdjustmentColumn() {
        return quantityAdjustmentColumn;
    }

    public DataObject getOperationObject() {
        return operationObject;
    }

    public DataObject getCompanyObject() {
        return companyObject;
    }

    public DataObject getStockObject() {
        return stockObject;
    }

    public DataObject getDefaultItemGroupObject() {
        return defaultItemGroupObject;
    }

    public String getItemKeyType() {
        return itemKeyType;
    }

    public String getSeparator() {
        return separator;
    }

    public Integer getStartRow() {
        return startRow;
    }

    public Boolean getIsPosted() {
        return isPosted;
    }

    public boolean isDoNotCreateItems() {
        return doNotCreateItems;
    }

    public boolean isCheckExistence() {
        return checkExistence;
    }

    public boolean isBarcodeMaybeUPC() {
        return barcodeMaybeUPC;
    }

    public String getCheckColumn() {
        return checkColumn;
    }
}
