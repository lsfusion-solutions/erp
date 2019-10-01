package equ.clt.handler;

import org.xBaseJ.DBF;
import org.xBaseJ.Util;
import org.xBaseJ.fields.Field;
import org.xBaseJ.fields.NumField;
import org.xBaseJ.xBaseJException;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.text.DecimalFormat;

import static equ.clt.handler.HandlerUtils.trim;

public class DBFUtils {

    public static void putField(DBF dbfFile, Field field, String value, int length, boolean append) throws xBaseJException {
        value = value == null ? "null" : trim(value, length);
        while (value.length() < length)
            value += " ";
        if (append)
            dbfFile.getField(field.getName()).put(value);
        else
            field.put(value);
    }

    public static void putNumField(DBF dbfFile, NumField2 field, BigDecimal value, boolean append) throws xBaseJException {
        if(value != null)
            putNumField(dbfFile, field, value.doubleValue(), append);
    }

    public static void putNumField(DBF dbfFile, NumField2 field, double value, boolean append) throws xBaseJException {
        if (append)
            putDouble((NumField) dbfFile.getField(field.getName()), value);
        else
            field.put(value);
    }

    //from NumField2
    public static void putDouble(NumField field, double inDouble) throws xBaseJException {
        String inString;

        StringBuilder formatString = new StringBuilder(field.getLength());
        for (int j = 0; j < field.getLength(); j++) {
            formatString.append("#");
        }
        if (field.getDecimalPositionCount() > 0) {
            formatString.setCharAt(field.getDecimalPositionCount(), '.');
        }

        DecimalFormat df = new DecimalFormat(formatString.toString());
        //df.setRoundingMode(RoundingMode.UNNECESSARY);
        inString = df.format(inDouble).trim();
        inString = inString.replace(NumField2.decimalSeparator, '.');

        if (inString.length() > field.Length) {
            throw new xBaseJException("Field length too long; inDouble=" + inString + " (maxLength=" + field.Length + " / format=" + formatString + ")");
        }

        int i;

        //-- fill database
        byte[] b;
        try {
            b = inString.getBytes(DBF.encodedType);
        } catch (UnsupportedEncodingException uee) {
            b = inString.getBytes();
        }

        for (i = 0; i < b.length; i++) {
            field.buffer[i] = b[i];
        }

        byte fill;
        if (Util.fieldFilledWithSpaces()) {
            fill = (byte) ' ';
        } else {
            fill = 0;
        }

        for (i = inString.length(); i < field.Length; i++) {
            field.buffer[i] = fill;
        }
    }

    public static String getDBFFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        return getDBFFieldValue(importFile, fieldName, charset, true);
    }

    public static String getDBFFieldValue(DBF importFile, String fieldName, String charset, boolean trim) throws UnsupportedEncodingException {
        try {
            String result = new String(importFile.getField(fieldName).getBytes(), charset);
            if(trim)
                result = result.trim();
            return result.isEmpty() ? null : result;
        } catch (xBaseJException e) {
            return null;
        }
    }
}