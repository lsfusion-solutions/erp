package lsfusion.erp.region.by.machinery.cashregister.fiscalshtrih;

import java.io.Serializable;
import java.math.BigDecimal;

public class UpdateDataTaxRate implements Serializable {
    public Integer taxRateNumber;
    public BigDecimal taxRateValue;

    public UpdateDataTaxRate(Integer taxRateNumber, BigDecimal taxRateValue) {
        this.taxRateNumber = taxRateNumber;
        this.taxRateValue = taxRateValue;
    }
}
