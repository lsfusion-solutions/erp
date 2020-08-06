package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import java.io.Serializable;
import java.math.BigDecimal;

public class InvoiceDetail implements Serializable {
    String name;
    BigDecimal price;
    BigDecimal quantity;
    BigDecimal sum;

    public InvoiceDetail(String name, BigDecimal price, BigDecimal quantity, BigDecimal sum) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.sum = sum;
    }
}