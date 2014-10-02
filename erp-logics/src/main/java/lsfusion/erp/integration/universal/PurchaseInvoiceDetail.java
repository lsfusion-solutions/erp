package lsfusion.erp.integration.universal;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class PurchaseInvoiceDetail {
    public LinkedHashMap<String, String> customValues;
    
    public Map<String, Object> fieldValues;
    
    public Boolean isPosted;
    public String idUserInvoice;
    public String numberUserInvoice;
    public String idUserInvoiceDetail;
    public BigDecimal quantity;
    public BigDecimal netWeight;
    public BigDecimal sumNetWeight;
    public BigDecimal grossWeight;
    public BigDecimal sumGrossWeight;

    public PurchaseInvoiceDetail(LinkedHashMap<String, String> customValues, Map<String, Object> fieldValues, 
                                 Boolean isPosted, String idUserInvoice, String numberUserInvoice, String idUserInvoiceDetail,
                                 BigDecimal quantity, BigDecimal netWeight, BigDecimal sumNetWeight, 
                                 BigDecimal grossWeight, BigDecimal sumGrossWeight) {
        this.customValues = customValues;
        this.fieldValues = fieldValues;
        this.isPosted = isPosted;
        this.idUserInvoice = idUserInvoice;
        this.numberUserInvoice = numberUserInvoice;
        this.idUserInvoiceDetail = idUserInvoiceDetail;
        this.quantity = quantity;
        this.netWeight = netWeight;
        this.sumNetWeight = sumNetWeight;
        this.grossWeight = grossWeight;
        this.sumGrossWeight = sumGrossWeight;
    }
    
    public Object getFieldValue(String field) {
        assert fieldValues.containsKey(field);
        return fieldValues.get(field);
    }
}
