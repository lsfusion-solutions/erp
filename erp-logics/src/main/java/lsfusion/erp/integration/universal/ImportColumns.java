package lsfusion.erp.integration.universal;

import lsfusion.server.logics.DataObject;

import java.util.Map;

public class ImportColumns {

    private Map<String, ImportColumnDetail> columns;
    private Map<DataObject, String[]> priceColumns;
    private String quantityAdjustmentColumn;
    private DataObject operationObject;
    private DataObject companyObject;
    private DataObject stockObject;
    private DataObject defaultItemGroupObject;
    private String fileExtension;
    private String itemKeyType;
    private String csvSeparator;
    private Integer startRow;
    private Boolean isPosted;
    private Boolean doNotCreateItems;
    private Boolean barcodeMaybeUPC;

    public ImportColumns(Map<String, ImportColumnDetail> columns, Map<DataObject, String[]> priceColumns, String quantityAdjustmentColumn,
                         DataObject operationObject, DataObject companyObject, DataObject stockObject, DataObject defaultItemGroupObject,
                         String fileExtension, String itemKeyType, String csvSeparator, Integer startRow, Boolean isPosted, 
                         Boolean doNotCreateItems, Boolean barcodeMaybeUPC) {
        this.columns = columns;
        this.priceColumns = priceColumns;
        this.quantityAdjustmentColumn = quantityAdjustmentColumn;
        this.operationObject = operationObject;
        this.companyObject = companyObject;
        this.stockObject = stockObject;
        this.defaultItemGroupObject = defaultItemGroupObject;
        this.fileExtension = fileExtension;
        this.itemKeyType = itemKeyType;
        this.csvSeparator = csvSeparator;
        this.startRow = startRow;
        this.isPosted = isPosted;
        this.doNotCreateItems = doNotCreateItems;
        this.barcodeMaybeUPC = barcodeMaybeUPC;
    }

    public Map<String, ImportColumnDetail> getColumns() {
        return columns;
    }

    public Map<DataObject, String[]> getPriceColumns() {
        return priceColumns;
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
    
    public String getFileExtension() {
        return fileExtension;
    }
    
    public String getItemKeyType() {
        return itemKeyType;
    }
    
    public String getCsvSeparator() {
        return csvSeparator;
    }

    public Integer getStartRow() {
        return startRow;
    }
    
    public Boolean getIsPosted() {
        return isPosted;
    }

    public Boolean getDoNotCreateItems() {
        return doNotCreateItems;
    }

    public Boolean getBarcodeMaybeUPC() {
        return barcodeMaybeUPC;
    }
}
