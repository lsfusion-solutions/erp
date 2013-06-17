package lsfusion.erp.integration;

public class Store extends LegalEntity {

    public String idStore;
    public String storeType;

    public Store(String idStore, String nameLegalEntity, String addressLegalEntity, String idLegalEntity,
                 String storeType, String idChainStores) {
        this.idStore = idStore;
        this.nameLegalEntity = nameLegalEntity;
        this.addressLegalEntity = addressLegalEntity;
        this.idLegalEntity = idLegalEntity;
        this.storeType = storeType;
        this.idChainStores = idChainStores;
    }
}