package lsfusion.erp.integration;


import java.math.BigDecimal;

public class SOTUOM {
    public String fullNameUOM;
    public String nameUOM;
    public String shortNameUOM;
    public BigDecimal netWeight;
    public BigDecimal grossWeight;

    public SOTUOM(String fullNameUOM, String nameUOM, String shortNameUOM, BigDecimal netWeight, BigDecimal grossWeight) {
        this.fullNameUOM = fullNameUOM;
        this.nameUOM = nameUOM;
        this.shortNameUOM = shortNameUOM;
        this.netWeight = netWeight;
        this.grossWeight = grossWeight;
    }
}
