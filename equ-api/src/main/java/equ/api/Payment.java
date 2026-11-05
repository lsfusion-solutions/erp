package equ.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class Payment implements Serializable {
    public String type;
    public BigDecimal sum;
    public Map<String, Object> extraFields;

    public Payment(Integer type, BigDecimal sum) {
        this(String.valueOf(type), sum);
    }

    public Payment(String type, BigDecimal sum) {
        this(type, sum, null);
    }

    public Payment(String type, BigDecimal sum, Map<String, Object> extraFields) {
        this.type = type;
        this.sum = sum;
        this.extraFields = extraFields;
    }

    public static Payment getCash(BigDecimal sum) {
        return getCash(sum, null);
    }

    public static Payment getCash(BigDecimal sum, Map<String, Object> extraFields) {
        return new Payment("cash", sum, extraFields);
    }

    public static Payment getCard(BigDecimal sum) {
        return getCard(sum, null);
    }

    public static Payment getCard(BigDecimal sum, String extraFieldKey, Object extraFieldValue) {
        Map<String, Object> extraFields = new HashMap<>();
        extraFields.put(extraFieldKey, extraFieldValue);
        return getCard(sum, extraFields);
    }

    public static Payment getCard(BigDecimal sum, Map<String, Object> extraFields) {
        return new Payment("card", sum, extraFields);
    }

    public boolean isCash() {
        return type.equals("cash");
    }

    public boolean isCard() {
        return type.equals("card");
    }
}
