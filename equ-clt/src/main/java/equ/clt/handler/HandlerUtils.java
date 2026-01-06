package equ.clt.handler;

import com.google.common.base.Throwables;
import equ.api.MachineryInfo;
import org.json.JSONObject;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

import static lsfusion.base.BaseUtils.nvl;

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

    public static JSONObject getJSONObject(String json, String objectKey) {
        return json != null ? new JSONObject(json).optJSONObject(objectKey) : null;
    }

    public static String appendSpaces(Object value, int length) {
        return append(value, ' ', length);
    }

    public static String fillSpaces(int length) {
        return append(null, ' ', length);
    }

    public static String fillZeroes(int length) {
        return append(null, '0', length);
    }

    private static String append(Object value, Character c, int length) {
        String result = value == null ? "" : String.valueOf(value);
        if (result.length() > length) result = result.substring(0, length);
        while (result.length() < length) {
            result += c;
        }
        return result;
    }

    public static String prependZeroes(Object value, int length) {
        return prepend(value, '0', length);
    }

    private static String prepend(Object value, Character c, int length) {
        String result = value == null ? "" : String.valueOf(value);
        if (result.length() > length) result = result.substring(0, length);
        while (result.length() < length) {
            result = c + result;
        }
        return result;
    }

    public static void copyWithTimeout(File sourceFile, File destinationFile) {
        copyWithTimeout(sourceFile, destinationFile, false);
    }
    public static void copyWithTimeout(File sourceFile, File destinationFile, boolean deleteSourceFile) {
        copyWithTimeout(sourceFile, destinationFile, 60000, deleteSourceFile);
    }
    public static void copyWithTimeout(File sourceFile, File destinationFile, long timeout, boolean deleteSourceFile) {
        final Future future = Executors.newSingleThreadExecutor().submit(() -> {
            try {
                FileCopyUtils.copy(sourceFile, destinationFile);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        });

        try {
            future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            future.cancel(true);
            throw new RuntimeException(String.format("Failed to copy file from %s to %s: %s", sourceFile.getAbsolutePath(), destinationFile.getAbsolutePath(), nvl(e.getMessage(), e.toString())), e);
        } finally {
            if (deleteSourceFile) {
                safeDelete(sourceFile);
            }
        }
    }

    public static void safeDelete(File file) {
        if (file != null && !file.delete()) {
            file.deleteOnExit();
        }
    }

    public static void forceDelete(File file) {
        if (file != null && !file.delete()) {
            throw new RuntimeException("The file " + file.getAbsolutePath() + " can not be deleted");
        }
    }

    public static Set<String> getDirectorySet(Set<MachineryInfo> machineryInfoSet) {
        Set<String> directorySet = new HashSet<>();
        for(MachineryInfo machinery : machineryInfoSet) {
            if(machinery.directory != null)
                directorySet.add(machinery.directory);
        }
        return directorySet;
    }
}