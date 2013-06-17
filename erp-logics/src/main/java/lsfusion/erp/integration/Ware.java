package lsfusion.erp.integration;


import java.math.BigDecimal;

public class Ware {
    public String idWare;
    public String nameWare;
    public BigDecimal priceWare;

    public Ware(String idWare, String nameWare, BigDecimal priceWare) {
        this.idWare = idWare;
        this.nameWare = nameWare;
        this.priceWare = priceWare;
    }
}
