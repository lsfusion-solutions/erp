package lsfusion.erp.integration;


import java.math.BigDecimal;

public class PriceListStore extends PriceList {
    public String idDepartmentStore;
    
    public PriceListStore(String idPriceList, String idItem, String idSupplier, String shortNameCurrency, 
                          BigDecimal pricePriceListDetail, String idDepartmentStore) {
        super(idPriceList, idItem, idSupplier, shortNameCurrency, pricePriceListDetail);
        this.idDepartmentStore = idDepartmentStore;
    }
}
