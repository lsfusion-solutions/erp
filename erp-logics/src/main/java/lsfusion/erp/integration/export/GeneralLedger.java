package lsfusion.erp.integration.export;

import java.math.BigDecimal;
import java.time.LocalDate;

public class GeneralLedger {
    public LocalDate dateGeneralLedger;
    public String numberGeneralLedger;
    public String seriesGeneralLedger;
    public String descriptionGeneralLedger;
    public String idOperationGeneralLedger;
    public String idDebitGeneralLedger;
    public String idCreditGeneralLedger;
    public String anad1;
    public String anad2;
    public String anad3;
    public String anad4;
    public String anak1;
    public String anak2;
    public String anak3;
    public String anak4;
    public BigDecimal quantityGeneralLedger;
    public BigDecimal sumGeneralLedger;

    public GeneralLedger(LocalDate dateGeneralLedger, String numberGeneralLedger, String seriesGeneralLedger, String descriptionGeneralLedger,
                         String idOperationGeneralLedger, String idDebitGeneralLedger, String idCreditGeneralLedger,
                         String anad1, String anad2, String anad3, String anad4,
                         String anak1, String anak2, String anak3, String anak4,
                         BigDecimal quantityGeneralLedger, BigDecimal sumGeneralLedger) {
        this.dateGeneralLedger = dateGeneralLedger;
        this.numberGeneralLedger = numberGeneralLedger;
        this.seriesGeneralLedger = seriesGeneralLedger;
        this.descriptionGeneralLedger = descriptionGeneralLedger;
        this.idOperationGeneralLedger = idOperationGeneralLedger;
        this.idDebitGeneralLedger = idDebitGeneralLedger;
        this.idCreditGeneralLedger = idCreditGeneralLedger;
        this.anad1 = anad1;
        this.anad2 = anad2;
        this.anad3 = anad3;
        this.anad4 = anad4;
        this.anak1 = anak1;
        this.anak2 = anak2;
        this.anak3 = anak3;
        this.anak4 = anak4;
        this.quantityGeneralLedger = quantityGeneralLedger;
        this.sumGeneralLedger = sumGeneralLedger;
    }
}
