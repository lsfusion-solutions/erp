package equ.api.cashregister;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CashRegisterItemBatch implements Serializable {
    public String idBatch;
    public LocalDate expiryDate;
    public String seriesPharmacy;
    public String nameManufacturer;
    public BigDecimal price;
    public String nameSubstance;
    public BigDecimal balance;
    public LocalDateTime balanceDate;
    public String countryCode;
    public String countryName;
    public Integer flag;

    public CashRegisterItemBatch(String idBatch, LocalDate expiryDate, String seriesPharmacy, String nameManufacturer,
                                 BigDecimal price, String nameSubstance, BigDecimal balance, LocalDateTime balanceDate,
                                 String countryCode, String countryName, Integer flag) {
        this.idBatch = idBatch;
        this.expiryDate = expiryDate;
        this.seriesPharmacy = seriesPharmacy;
        this.nameManufacturer = nameManufacturer;
        this.price = price;
        this.nameSubstance = nameSubstance;
        this.balance = balance;
        this.balanceDate = balanceDate;
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.flag = flag;
    }
}