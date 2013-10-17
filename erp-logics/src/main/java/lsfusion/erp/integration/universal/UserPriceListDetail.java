package lsfusion.erp.integration.universal;


import lsfusion.server.logics.DataObject;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.Map;

public class UserPriceListDetail {
    public String idUserPriceListDetail;
    public String idUserPriceList;
    public String idItem;
    public String barcodeItem;
    public String articleItem;
    public String captionItem;
    public String idUOMItem;
    public Date date;
    /*public DataObject priceTypeObject;
    public BigDecimal price;*/
    public Map<DataObject, BigDecimal> prices;


    public UserPriceListDetail(String idUserPriceListDetail, String idUserPriceList, String idItem, String barcodeItem,
                               String articleItem, String captionItem, String idUOMItem, Date date, 
                               Map<DataObject, BigDecimal> prices/*, DataObject priceTypeObject, BigDecimal price*/) {
        this.idUserPriceListDetail = idUserPriceListDetail;
        this.idUserPriceList = idUserPriceList;
        this.idItem = idItem;
        this.barcodeItem = barcodeItem;
        this.articleItem = articleItem;
        this.captionItem = captionItem;
        this.idUOMItem = idUOMItem;
        this.date = date;
        this.prices = prices;
/*        this.priceTypeObject = priceTypeObject;
        this.price = price;*/
    }
}
