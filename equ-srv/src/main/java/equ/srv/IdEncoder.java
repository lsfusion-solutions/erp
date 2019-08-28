package equ.srv;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

public class IdEncoder {
    public IdEncoder() {
        this(defaultDatePosition, -1);
    }

    public IdEncoder(int timePosition) {
        this(defaultDatePosition, timePosition);
    }

    public IdEncoder(int datePosition, int timePosition) {
        this.datePosition = datePosition;
        this.timePosition = timePosition;
    }

    private int datePosition;
    private int timePosition;
    private static int defaultDatePosition = 3;

    public String encode(String id) {
        id = encodeDateAndTime(id);
        return Arrays.stream(id.split("_"))
                .map(Integer::valueOf)
                .map(IntBaseXEncoder::encode)
                .collect(Collectors.joining());
    }

    public String decode(String s) {
        String withUnderscores = insertUnderscores(s);
        String res = Arrays.stream(withUnderscores.split("_"))
                .map(IntBaseXEncoder::decode)
                .map(String::valueOf)
                .collect(Collectors.joining("_"));
        return decodeDateAndTime(res);
    }

    private static String insertUnderscores(String s) {
        StringBuilder t = new StringBuilder();
        for (int i = 0; i < s.length(); ++i) {
            char ch = s.charAt(i);
            if (i > 0 && IntBaseXEncoder.isStartingCharacter(ch)) {
                t.append("_");
            }
            t.append(ch);
        }
        return t.toString();
    }

    private static int startingYear = 1970;
    private static int daysShift = (startingYear - 1970) * 365;

    private String encodeDateAndTime(String s) {
        String[] arr = s.split("_");

        arr[datePosition] = encodeDate(arr[datePosition]);
        if (timePosition >= 0) {
            arr[timePosition] = encodeTime(arr[timePosition]);
        }
        return String.join("_", arr);
    }

    // format: ddMMyyyy 
    private String encodeDate(String date) {
        LocalDate localDate = LocalDate.of(Integer.valueOf(date.substring(4)), Integer.valueOf(date.substring(2,4)), Integer.valueOf(date.substring(0, 2)));
        return String.valueOf(localDate.toEpochDay() - daysShift);
    }

    // format: HH:mm:ss 
    private String encodeTime(String time) {
        LocalTime localTime = LocalTime.of(Integer.valueOf(time.substring(0, 2)), Integer.valueOf(time.substring(3, 5)), Integer.valueOf(time.substring(6)));
        return String.valueOf(localTime.toSecondOfDay());
    }

    private String decodeDateAndTime(String s) {
        String[] arr = s.split("_");
        arr[datePosition] = decodeDate(arr[datePosition]);
        if (timePosition >= 0) {
            arr[timePosition] = decodeTime(arr[timePosition]);
        }
        return String.join("_", arr);
    }

    private String decodeDate(String date) {
        LocalDate localDate = LocalDate.ofEpochDay(Long.valueOf(date) + daysShift);
        return localDate.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
    }

    private String decodeTime(String time) {
        LocalTime localTime = LocalTime.ofSecondOfDay(Long.valueOf(time));
        return localTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private static class IntBaseXEncoder {
        private static final String Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"#$%&'()*+,-./:;<=>?@[\\]^`{|}~";
        private static final int baseAlphabetSize = Alphabet.length() / 2;

        private static int charToInt(char ch) {
            return Alphabet.indexOf(ch);
        }

        private static char intToChar(int i) {
            return Alphabet.charAt(i);
        }

        public static String encode(int n) {
            if (n == 0) return String.valueOf(intToChar(baseAlphabetSize));

            StringBuilder b = new StringBuilder();

            while (n > 0) {
                b.append(intToChar(n % baseAlphabetSize));
                n /= baseAlphabetSize;
            }

            b.setCharAt(b.length() - 1, intToChar(baseAlphabetSize + charToInt(b.charAt(b.length() - 1))));
            return b.reverse().toString();
        }

        public static int decode(String s) {
            int res = charToInt(s.charAt(0)) - baseAlphabetSize;

            for (int i = 1; i < s.length(); ++i) {
                res *= baseAlphabetSize;
                res += charToInt(s.charAt(i));
            }
            return res;
        }

        public static boolean isStartingCharacter(char ch) {
            return charToInt(ch) >= baseAlphabetSize;
        }
    }
}
