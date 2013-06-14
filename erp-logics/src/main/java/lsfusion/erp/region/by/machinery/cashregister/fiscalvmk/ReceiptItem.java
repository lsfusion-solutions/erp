package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import java.io.Serializable;
import java.math.BigDecimal;

public class ReceiptItem implements Serializable {
    public Long price;
    public BigDecimal quantity;
    public String barCode;
    public String name;
    public Long sumPos;
    public BigDecimal articleDisc;
    public BigDecimal articleDiscSum;
    public Integer taxNumber;
    public Integer group;

    public ReceiptItem(Long price, BigDecimal quantity, String barCode, String name, Long sumPos,
                       BigDecimal articleDisc, BigDecimal articleDiscSum, Integer taxNumber, Integer group) {
        this.price = price;
        this.quantity = quantity;
        this.barCode = barCode;
        this.name = name;
        this.sumPos = sumPos;
        this.articleDisc = articleDisc;
        this.articleDiscSum = articleDiscSum;
        this.taxNumber = taxNumber;
        this.group = group;
    }
}
