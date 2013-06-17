package lsfusion.erp.integration;


import java.math.BigDecimal;

public class PriceListStore {
    public String idUserPriceList;
    public String idItem;
    public String idSupplier;
    public String idDepartmentStore;
    public String shortNameCurrency;
    public BigDecimal pricePriceListDetail;
    public Boolean inPriceList;
    public Boolean inPriceListStock;

    public PriceListStore(String idUserPriceList, String idItem, String idSupplier, String idDepartmentStore,
                          String shortNameCurrency, BigDecimal pricePriceListDetail, Boolean inPriceList,
                          Boolean inPriceListStock) {
        this.idUserPriceList = idUserPriceList;
        this.idItem = idItem;
        this.idSupplier = idSupplier;
        this.idDepartmentStore = idDepartmentStore;
        this.shortNameCurrency = shortNameCurrency;
        this.pricePriceListDetail = pricePriceListDetail;
        this.inPriceList = inPriceList;
        this.inPriceListStock = inPriceListStock;
    }
}
