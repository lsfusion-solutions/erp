package lsfusion.erp.integration;

public class LegalEntity {
    public String idLegalEntity;
    public String nameLegalEntity;
    public String addressLegalEntity;
    public String unpLegalEntity;
    public String okpoLegalEntity;
    public String phoneLegalEntity;
    public String emailLegalEntity;
    public String nameOwnership;
    public String shortNameOwnership;
    public String numberAccount;
    public String idChainStores;
    public String nameChainStores;
    public String idBank;
    public String nameCountry;
    public Boolean isSupplierLegalEntity;
    public Boolean isCompanyLegalEntity;
    public Boolean isCustomerLegalEntity;

    public LegalEntity(String idLegalEntity, String nameLegalEntity, String addressLegalEntity, String unpLegalEntity,
                       String okpoLegalEntity, String phoneLegalEntity, String emailLegalEntity, String nameOwnership,
                       String shortNameOwnership, String numberAccount, String idChainStores, String nameChainStores,
                       String idBank, String nameCountry, Boolean supplierLegalEntity, Boolean companyLegalEntity,
                       Boolean customerLegalEntity) {
        this.idLegalEntity = idLegalEntity;
        this.nameLegalEntity = nameLegalEntity;
        this.addressLegalEntity = addressLegalEntity;
        this.unpLegalEntity = unpLegalEntity;
        this.okpoLegalEntity = okpoLegalEntity;
        this.phoneLegalEntity = phoneLegalEntity;
        this.emailLegalEntity = emailLegalEntity;
        this.nameOwnership = nameOwnership;
        this.shortNameOwnership = shortNameOwnership;
        this.numberAccount = numberAccount;
        this.idChainStores = idChainStores;
        this.nameChainStores = nameChainStores;
        this.idBank = idBank;
        this.nameCountry = nameCountry;
        this.isSupplierLegalEntity = supplierLegalEntity;
        this.isCompanyLegalEntity = companyLegalEntity;
        this.isCustomerLegalEntity = customerLegalEntity;
    }

    public LegalEntity() {
    }
}
