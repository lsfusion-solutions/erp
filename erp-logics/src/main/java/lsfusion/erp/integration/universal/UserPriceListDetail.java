package lsfusion.erp.integration.universal;


import java.math.BigDecimal;
import java.sql.Date;

public class UserPriceListDetail {
    public String idUserPriceListDetail;
    public String idUserPriceList;
    public String idItem;
    public String barcodeItem;
    public String articleItem;
    public String captionItem;
    public String idUOMItem;
    public Date date;
    public String priceType;
    public BigDecimal price;


    public UserPriceListDetail(String idUserPriceListDetail, String idUserPriceList, String idItem, String barcodeItem,
                               String articleItem, String captionItem, String idUOMItem, Date date, String priceType,
                               BigDecimal price) {
        this.idUserPriceListDetail = idUserPriceListDetail;
        this.idUserPriceList = idUserPriceList;
        this.idItem = idItem;
        this.barcodeItem = barcodeItem;
        this.articleItem = articleItem;
        this.captionItem = captionItem;
        this.idUOMItem = idUOMItem;
        this.date = date;
        this.priceType = priceType;
        this.price = price;
    }
}
