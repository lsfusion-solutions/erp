package lsfusion.erp.integration;


import java.time.LocalDate;

public class Contract {
    public String idUserContractSku;
    public String idSupplier;
    public String idCustomer;
    public String numberContract;
    public LocalDate dateFromContract;
    public LocalDate dateToContract;
    public String shortNameCurrency;
    public String idPaymentCondition;
    public Integer countDays;
    public Boolean bankingDays;


    public Contract(String idUserContractSku, String idSupplier, String idCustomer, String numberContract,
                    LocalDate dateFromContract, LocalDate dateToContract, String shortNameCurrency, String idPaymentCondition,
                    Integer countDays, Boolean bankingDays) {
        this.idUserContractSku = idUserContractSku;
        this.idSupplier = idSupplier;
        this.idCustomer = idCustomer;
        this.numberContract = numberContract;
        this.dateFromContract = dateFromContract;
        this.dateToContract = dateToContract;
        this.shortNameCurrency = shortNameCurrency;
        this.idPaymentCondition = idPaymentCondition;
        this.countDays = countDays;
        this.bankingDays = bankingDays;
    }
}
