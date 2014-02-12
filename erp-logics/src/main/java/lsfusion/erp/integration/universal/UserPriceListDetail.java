package lsfusion.erp.integration.universal;


import lsfusion.server.logics.DataObject;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.Map;

public class UserPriceListDetail {
    public Boolean isPosted;
    public String idUserPriceListDetail;
    public String idUserPriceList;
    public String idItem;
    public String idItemGroup;
    public String barcodeItem;
    public String extraBarcodeItem;
    public String barcodePack;
    public BigDecimal amountPack;
    public String articleItem;
    public String captionItem;
    public String idUOMItem;
    public Map<DataObject, BigDecimal> prices;
    public BigDecimal quantityAdjustment;
    public Date dateFrom;
    public Date dateTo;
    public BigDecimal valueVAT;
    public String countryVAT;
    public Date date;


    public UserPriceListDetail(Boolean isPosted, String idUserPriceListDetail, String idUserPriceList, String idItem, 
                               String idItemGroup, String barcodeItem, String extraBarcodeItem, String barcodePack, 
                               BigDecimal amountPack, String articleItem, String captionItem, String idUOMItem, 
                               Map<DataObject, BigDecimal> prices, BigDecimal quantityAdjustment, Date dateFrom,
                               Date dateTo, BigDecimal valueVAT, String countryVAT, Date date) {
        this.isPosted = isPosted;
        this.idUserPriceListDetail = idUserPriceListDetail;
        this.idUserPriceList = idUserPriceList;
        this.idItem = idItem;
        this.idItemGroup = idItemGroup;
        this.barcodeItem = barcodeItem;
        this.extraBarcodeItem = extraBarcodeItem;
        this.barcodePack = barcodePack;
        this.amountPack = amountPack;
        this.articleItem = articleItem;
        this.captionItem = captionItem;
        this.idUOMItem = idUOMItem;
        this.prices = prices;
        this.quantityAdjustment = quantityAdjustment;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.valueVAT = valueVAT;
        this.countryVAT = countryVAT;
        this.date = date;
    }
}
