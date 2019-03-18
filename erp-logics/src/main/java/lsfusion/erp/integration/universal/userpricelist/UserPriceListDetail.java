package lsfusion.erp.integration.universal.userpricelist;


import lsfusion.server.data.value.DataObject;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class UserPriceListDetail {
    public LinkedHashMap<String, String> customValues;
    public Map<String, Object> fieldValues;
    public Boolean isPosted;
    public String idUserPriceListDetail;
    public String idItem;
    public String barcodeItem;
    public String extIdPackBarcode;
    public String packBarcode;
    public Map<DataObject, BigDecimal> prices;
    public BigDecimal quantityAdjustment;
    public Date dateUserPriceList;
    public Date dateFrom;
    public Date dateVAT;


    public UserPriceListDetail(LinkedHashMap<String, String> customValues, Map<String, Object> fieldValues, 
                               Boolean isPosted, String idUserPriceListDetail, String idItem, 
                               String barcodeItem, String extIdPackBarcode, String packBarcode, 
                               Map<DataObject, BigDecimal> prices, BigDecimal quantityAdjustment,
                               Date dateUserPriceList, Date dateFrom, Date dateVAT) {
        this.customValues = customValues;
        this.fieldValues = fieldValues;
        this.isPosted = isPosted;
        this.idUserPriceListDetail = idUserPriceListDetail;
        this.idItem = idItem;
        this.barcodeItem = barcodeItem;
        this.extIdPackBarcode = extIdPackBarcode;
        this.packBarcode = packBarcode;
        this.prices = prices;
        this.quantityAdjustment = quantityAdjustment;
        this.dateUserPriceList = dateUserPriceList;
        this.dateFrom = dateFrom;
        this.dateVAT = dateVAT;
    }

    public Object getFieldValue(String field) {
        assert fieldValues.containsKey(field);
        return fieldValues.get(field);
    }
}
