package lsfusion.erp.integration.universal;

import lsfusion.server.logics.DataObject;

import java.util.Map;

public class ImportColumns {

    private Map<String, ImportColumnDetail> columns;
    private Map<DataObject, String[]> priceColumns;
    private String quantityAdjustmentColumn;
    private Integer dateRow;
    private Integer dateColumn;
    private DataObject operationObject;
    private DataObject companyObject;
    private DataObject stockObject;
    private DataObject defaultItemGroupObject;

    public ImportColumns(Map<String, ImportColumnDetail> columns, Map<DataObject, String[]> priceColumns, String quantityAdjustmentColumn,
                         Integer dateRow, Integer dateColumn, DataObject operationObject, DataObject companyObject, DataObject stockObject,
                         DataObject defaultItemGroupObject) {
        this.columns = columns;
        this.priceColumns = priceColumns;
        this.quantityAdjustmentColumn = quantityAdjustmentColumn;
        this.dateRow = dateRow;
        this.dateColumn = dateColumn;
        this.operationObject = operationObject;
        this.companyObject = companyObject;
        this.stockObject = stockObject;
        this.defaultItemGroupObject = defaultItemGroupObject;
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

    public Integer getDateRow() {
        return dateRow == null ? null : (dateRow - 1);
    }

    public String getDateColumn() {
        return dateColumn == null ? null : String.valueOf(dateColumn);
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
}
