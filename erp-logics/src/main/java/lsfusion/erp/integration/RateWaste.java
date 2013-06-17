package lsfusion.erp.integration;


import java.math.BigDecimal;

public class RateWaste {
    public String idRateWaste;
    public String nameRateWaste;
    public BigDecimal percentWriteOffRate;
    public String nameCountry;

    public RateWaste(String idRateWaste, String nameRateWaste, BigDecimal percentWriteOffRate, String nameCountry) {
        this.idRateWaste = idRateWaste;
        this.nameRateWaste = nameRateWaste;
        this.percentWriteOffRate = percentWriteOffRate;
        this.nameCountry = nameCountry;
    }
}
