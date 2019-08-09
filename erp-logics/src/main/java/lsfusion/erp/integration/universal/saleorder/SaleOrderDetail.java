package lsfusion.erp.integration.universal.saleorder;

import java.util.Map;

public class SaleOrderDetail {
    public Map<String, Object> fieldValues;
    public Boolean isPosted;

    public SaleOrderDetail(Map<String, Object> fieldValues, Boolean isPosted) {
        this.fieldValues = fieldValues;
        this.isPosted = isPosted;
    }

    public Object getFieldValue(String field) {
        assert fieldValues.containsKey(field);
        return fieldValues.get(field);
    }
}
