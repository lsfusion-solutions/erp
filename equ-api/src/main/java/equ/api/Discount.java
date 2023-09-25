package equ.api;

import java.io.Serializable;
import java.math.BigDecimal;

public class Discount implements Serializable {
    public String name;
    public String mode;
    public BigDecimal sum;

    public Discount(String name, String mode, BigDecimal sum) {
        this.name = name;
        this.mode = mode;
        this.sum = sum;
    }
}