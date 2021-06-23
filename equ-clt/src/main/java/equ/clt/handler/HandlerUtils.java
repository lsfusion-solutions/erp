package equ.clt.handler;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class HandlerUtils {

    public static BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }

    public static BigDecimal safeSubtract(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else
            return (operand1 == null ? operand2.negate() : (operand2 == null ? operand1 : operand1.subtract((operand2))));
    }

    public static BigDecimal safeMultiply(BigDecimal operand1, int operand2) {
        return safeMultiply(operand1, BigDecimal.valueOf(operand2));
    }

    public static BigDecimal safeMultiply(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null || operand1.doubleValue() == 0 || operand2 == null || operand2.doubleValue() == 0)
            return null;
        else return operand1.multiply(operand2);
    }

    public static BigDecimal safeDivide(BigDecimal dividend, int quotient) {
        return safeDivide(dividend, BigDecimal.valueOf(quotient));
    }

    public static BigDecimal safeDivide(BigDecimal dividend, BigDecimal quotient) {
        return safeDivide(dividend, quotient, 3);
    }

    public static BigDecimal safeDivide(BigDecimal dividend, BigDecimal quotient, int scale) {
        if (dividend == null || quotient == null || quotient.doubleValue() == 0)
            return null;
        return dividend.divide(quotient, scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal safeNegate(BigDecimal operand) {
        return operand == null ? null : operand.negate();
    }

    public static BigDecimal safeAbs(BigDecimal operand) {
        return operand == null ? null : operand.abs();
    }

    public static String trim(String input, Integer length) {
        return trim(input, null, length);
    }

    public static String trim(String input, String defaultValue, Integer length) {
        return input == null ? defaultValue : (length == null || length >= input.trim().length() ? input.trim() : input.trim().substring(0, length));
    }

    public static BigDecimal getJSONBigDecimal(String json, String objectKey, String valueKey) {
        return getJSONBigDecimal(getJSONObject(json, objectKey), valueKey);
    }

    public static JSONObject getJSONObject(String json, String objectKey) {
        return json != null ? new JSONObject(json).optJSONObject(objectKey) : null;
    }

    public static BigDecimal getJSONBigDecimal(JSONObject jsonObject, String valueKey) {
        return jsonObject != null && jsonObject.has(valueKey) ? jsonObject.getBigDecimal(valueKey) : null;
    }
}