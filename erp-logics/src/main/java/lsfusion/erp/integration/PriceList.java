package lsfusion.erp.integration;

import java.math.BigDecimal;

public class PriceList {
    public String idPriceList;
    public String idItem;
    public String idSupplier;
    public String shortNameCurrency;
    public BigDecimal pricePriceListDetail;

    public PriceList(String idPriceList, String idItem, String idSupplier, String shortNameCurrency,
                             BigDecimal pricePriceListDetail) {
        this.idPriceList = idPriceList;
        this.idItem = idItem;
        this.idSupplier = idSupplier;
        this.shortNameCurrency = shortNameCurrency;
        this.pricePriceListDetail = pricePriceListDetail;
    }
}
