package lsfusion.erp.region.by.machinery.paymentterminal.terminaljadeeko;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;

public class TerminalJadeEKO {

    public interface jadeEKODLL extends Library {

        jadeEKODLL jadeEKO = Native.load("jadeEKO", jadeEKODLL.class);

        boolean operation(byte[] port, byte[] un, byte[] req, byte[] resp);

        int lasterror();

        void errorstring(int err, byte[] buf);

        void un(byte[] buf);

        void generateun(byte[] buf);
    }

    static void init() {

        try {
            System.loadLibrary("jadeEKO");
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static String operation(int comPort, int type, BigDecimal sum, String comment) throws RuntimeException, UnsupportedEncodingException {
        String query = type + "," + toStr(sum) + ",," + comment;
        byte[] un = new byte[255];
        jadeEKODLL.jadeEKO.generateun(un);
        byte[] response = new byte[255];
        jadeEKODLL.jadeEKO.operation(getBytes("COM" + comPort), un, getBytes(query), response);
        String error = checkErrors();
        if (error != null)
            return error;
        String responseString = Native.toString(response, "cp1251");

        switch (responseString.charAt(0)) {
            case '0':
                return "Операция не выполнена"; //кнопка cancel или недостаточно средств
            case '1':
                return "Не завершена предыдущая операция";
            case '2':
                return null; //"Операция выполнена
            case '3':
                return "Операция, выполняемая кассиром, не завершена";
            default:
                return responseString;
        }
    }

    public static String checkErrors() throws RuntimeException {
        int lastError = jadeEKODLL.jadeEKO.lasterror();
        if (lastError != 0)
            return getError();
        return null;
    }

    public static String getError() {
        int lastError = jadeEKODLL.jadeEKO.lasterror();
        int length = 255;
        byte[] lastErrorText = new byte[length];
        jadeEKODLL.jadeEKO.errorstring(lastError, lastErrorText);
        return Native.toString(lastErrorText, "cp1251");
    }

    private static byte[] getBytes(String input) throws UnsupportedEncodingException {
        return (input + "\0").getBytes("cp1251");
    }

    private static String toStr(BigDecimal value) {
        return (value == null) ? "0" : String.valueOf(value.intValue());
    }
}

