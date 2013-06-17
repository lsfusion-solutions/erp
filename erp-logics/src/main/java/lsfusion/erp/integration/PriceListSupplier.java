package lsfusion.erp.integration;

import java.math.BigDecimal;

public class PriceListSupplier {
    public String idUserPriceList;
    public String idItem;
    public String idSupplier;
    public String shortNameCurrency;
    public BigDecimal pricePriceListDetail;
    public Boolean inPriceList;

    public PriceListSupplier(String idUserPriceList, String idItem, String idSupplier, String shortNameCurrency,
                             BigDecimal pricePriceListDetail, Boolean inPriceList) {
        this.idUserPriceList = idUserPriceList;
        this.idItem = idItem;
        this.idSupplier = idSupplier;
        this.shortNameCurrency = shortNameCurrency;
        this.pricePriceListDetail = pricePriceListDetail;
        this.inPriceList = inPriceList;
    }
}
