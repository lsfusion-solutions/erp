package equ.api;

import java.io.Serializable;
import java.math.BigDecimal;

public class GiftCard implements Serializable {
    public BigDecimal sum;
    public BigDecimal price;

    public GiftCard(BigDecimal sum) {
        this(sum, null);
    }

    public GiftCard(BigDecimal sum, BigDecimal price) {
        this.sum = sum;
        this.price = price;
    }
}