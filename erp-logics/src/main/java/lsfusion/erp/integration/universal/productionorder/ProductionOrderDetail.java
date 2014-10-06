package lsfusion.erp.integration.universal.productionorder;

import java.util.Map;

public class ProductionOrderDetail {
    public Map<String, Object> fieldValues;
    public Boolean isPosted;
    public String idOrder;
    public String numberOrder;
    public String idProductDetail;

    public ProductionOrderDetail(Map<String, Object> fieldValues, Boolean isPosted, String idOrder, String numberOrder,
                                 String idProductDetail) {
        this.fieldValues = fieldValues;
        this.isPosted = isPosted;
        this.idOrder = idOrder;
        this.numberOrder = numberOrder;
        this.idProductDetail = idProductDetail;
    }

    public Object getFieldValue(String field) {
        assert fieldValues.containsKey(field);
        return fieldValues.get(field);
    }
}
