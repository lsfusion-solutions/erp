package lsfusion.erp.integration.universal;

import lsfusion.server.logics.DataObject;

import java.util.Map;

public class ImportColumns {

    private Map<String, String[]> columns;
    private Map<DataObject, String[]> priceColumns;
    private String quantityAdjustmentColumn;
    private Integer dateRow;
    private Integer dateColumn;
    private DataObject operationObject;
    private DataObject stockObject;
    private DataObject defaultItemGroupObject;

    public ImportColumns(Map<String, String[]> columns, Map<DataObject, String[]> priceColumns, String quantityAdjustmentColumn,
                         Integer dateRow, Integer dateColumn, DataObject operationObject, DataObject stockObject,
                         DataObject defaultItemGroupObject) {
        this.columns = columns;
        this.priceColumns = priceColumns;
        this.quantityAdjustmentColumn = quantityAdjustmentColumn;
        this.dateRow = dateRow;
        this.dateColumn = dateColumn;
        this.operationObject = operationObject;
        this.stockObject = stockObject;
        this.defaultItemGroupObject = defaultItemGroupObject;
    }

    public Map<String, String[]> getColumns() {
        return columns;
    }

    public Map<DataObject, String[]> getPriceColumns() {
        return priceColumns;
    }

    public String getQuantityAdjustmentColumn() {
        return quantityAdjustmentColumn;
    }

    public Integer getIntQuantityAdjustmentColumn() {
        return quantityAdjustmentColumn == null ? null : (Integer.parseInt(quantityAdjustmentColumn) - 1);
    }

    public Integer getDateRow() {
        return dateRow == null ? null : (dateRow - 1);
    }

    public Integer getDateColumn() {
        return dateColumn == null ? null : (dateColumn - 1);
    }

    public DataObject getOperationObject() {
        return operationObject;
    }

    public DataObject getStockObject() {
        return stockObject;
    }

    public DataObject getDefaultItemGroupObject() {
        return defaultItemGroupObject;
    }
}
