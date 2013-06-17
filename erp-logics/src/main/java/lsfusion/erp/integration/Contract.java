package lsfusion.erp.integration;


import java.util.Date;

public class Contract {
    public String idUserContractSku;
    public String idSupplier;
    public String idCustomer;
    public String numberContract;
    public Date dateFromContract;
    public Date dateToContract;
    public String shortNameCurrency;


    public Contract(String idUserContractSku, String idSupplier, String idCustomer, String numberContract,
                    Date dateFromContract, Date dateToContract, String shortNameCurrency) {
        this.idUserContractSku = idUserContractSku;
        this.idSupplier = idSupplier;
        this.idCustomer = idCustomer;
        this.numberContract = numberContract;
        this.dateFromContract = dateFromContract;
        this.dateToContract = dateToContract;
        this.shortNameCurrency = shortNameCurrency;
    }
}
