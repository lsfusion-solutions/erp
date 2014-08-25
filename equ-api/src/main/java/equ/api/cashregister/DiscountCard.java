package equ.api.cashregister;

import java.io.Serializable;
import java.math.BigDecimal;

public class DiscountCard implements Serializable {
    public String numberDiscountCard;
    public String nameDiscountCard;
    public BigDecimal percentDiscountCard;
    
    public DiscountCard(String numberDiscountCard, String nameDiscountCard, BigDecimal percentDiscountCard) {
        this.numberDiscountCard = numberDiscountCard;
        this.nameDiscountCard = nameDiscountCard;
        this.percentDiscountCard = percentDiscountCard;
    }
    
}
