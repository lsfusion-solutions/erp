package lsfusion.erp.region.by.machinery.cashregister.fiscalabsolut;

import com.sun.jna.Library;
import com.sun.jna.Native;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static lsfusion.base.BaseUtils.trimToEmpty;

public class FiscalAbsolut {

    static Logger logger;
    static {
        try {
            logger = Logger.getLogger("cashRegisterLog");
            logger.setLevel(Level.INFO);
            FileAppender fileAppender = new FileAppender(new EnhancedPatternLayout("%d{DATE} %5p %c{1} - %m%n%throwable{1000}"),
                    "logs/cashregister.log");   
            logger.removeAllAppenders();
            logger.addAppender(fileAppender);
            
        } catch (Exception ignored) {
        }
    }
    static int lineLength = 30;
    
    public interface absolutDLL extends Library {

        absolutDLL absolut = Native.load("absolut", absolutDLL.class);

        Boolean Open(String port, int baud, int oper, int pwd);

        void Close();

        int LastError();

        void ErrorString(int error, byte[] buffer, int buflen);

        Boolean SmenBegin();

        Boolean BegChk();

        Boolean BegReturn();

        Boolean EndChk();

        Boolean CopyChk();

        Boolean InOut(int id, double sum);

        Boolean OutTone(int duration, int freq);

        Boolean OutScr(int row, byte[] scr);

        Boolean TextComment(byte[] comment);

        Boolean PrintComment(byte[] comment);

        Boolean FullProd(String plu, double price, double quant,
                         int dep, int group, int tax, byte[] naim, int codtyp);

        Boolean Prod(String plu, double price, double quant, int dep,
                     int group);

        Boolean NacSkd(int id, double sum, double prc);

        Boolean Oplata(int id, double sum, long code);

        Boolean Subtotal();

        Boolean VoidChk();

        Boolean VoidLast();

        Boolean VoidProd(String plu);

        Boolean PrintReport(int id);

        Boolean OpenComment();

        Boolean CloseComment();

        Boolean PrintComment(String comment);

        void GetFactoryNumber(byte[] buffer, int buflen);

        Boolean PrintBarCode(int typ, byte[] cod, int width, int height, int feed);

        Boolean SetLogPath(byte[] path);

        void LastChkInfo(byte[] buffer, int buflen);
    }

    public static String getError(boolean closePort) {
        logAction("LastError");
        Integer lastError = absolutDLL.absolut.LastError();
        int length = 255;
        byte[] lastErrorText = new byte[length];
        logAction("ErrorString");
        absolutDLL.absolut.ErrorString(lastError, lastErrorText, length);
        if (closePort)
            closePort();
        String errorText = lastError + " " + Native.toString(lastErrorText, "cp1251");
        logAction("Absolut Exception: " + errorText);
        return errorText;
    }

    public static void openPort(String logPath, int comPort, int baudRate) {
        try {
            if (logPath != null) {
                logAction("SetLogPath", logPath);
                if (!absolutDLL.absolut.SetLogPath(getBytes(logPath)))
                    checkErrors(true);
            }
            logAction("Open", "COM" + comPort, baudRate, 1, 1111);
            if (!absolutDLL.absolut.Open("COM" + comPort, baudRate, 1, 1111))
                checkErrors(true);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public static void closePort() {
        logAction("Close");
        absolutDLL.absolut.Close();
    }

    static boolean openReceipt(boolean sale) {    //0 - продажа, 1 - возврат {
        logAction("BegChk");
        boolean result = absolutDLL.absolut.BegChk();
        if (result && !sale)
            return absolutDLL.absolut.BegReturn();
        else return result;
    }

    public static boolean closeReceipt() {
        logAction("EndChk");
        return absolutDLL.absolut.EndChk();
    }

    public static Integer closeAndGetNumberReceipt(boolean useSKNO) {
        closeReceipt();
        if (useSKNO) {
            try {
                int buflen = 255;
                byte[] buffer = new byte[buflen];
                logAction("LastChkInfo");
                absolutDLL.absolut.LastChkInfo(buffer, buflen);
                checkErrors(false);
                return parseCheckNumber(Native.toString(buffer, "cp1251"));
            } catch (Exception e) {
                logger.error("FiscalAbsolut Error: ", e);
            }
        }
        return null;
    }

    private static Integer parseCheckNumber(String value) {
        try {
            String[] splitted = value != null ? value.split(",") : null;
            return splitted != null && splitted.length > 2 ? Integer.parseInt(splitted[1]) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean cancelReceipt() {
        logAction("VoidChk");
        return absolutDLL.absolut.VoidChk();
    }

    static void simpleLogAction(String msg) {
        logger.info(msg);
    }

    static boolean printBarcode(String barcode) {
        try {
            if(barcode != null)
                FiscalAbsolut.absolutDLL.absolut.PrintBarCode(1, FiscalAbsolut.getBytes(barcode), 2, 40, 5);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    static boolean printFiscalText(String msg) {
        try {
            if (msg != null && !msg.isEmpty()) {
                for (String line : msg.split("\n")) {
                    boolean result = printComment(line, false);
                    if (!result) return false;
                }
            }
            return true;
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }

    public static boolean printMultilineFiscalText(String msg) {
        if (msg != null && !msg.isEmpty()) {
            for (String line : msg.split("\n")) {
                int start = 0;
                while (start < line.length()) {
                    int end = Math.min(start + 30, line.length());
                    if (!printFiscalText(line.substring(start, end)))
                        return false;
                    start = end;
                }
            }
        }
        return true;
    }

    private static boolean printComment(String comment, boolean saveCommentOnFiscalTape) throws UnsupportedEncodingException {
        logAction(saveCommentOnFiscalTape ? "TextComment" : "PrintComment", comment);
        return saveCommentOnFiscalTape ?
                absolutDLL.absolut.TextComment(getBytes(comment)) : absolutDLL.absolut.PrintComment(getBytes(comment));
    }

    static boolean repeatReceipt() {
        logAction("Copychk");
        return absolutDLL.absolut.CopyChk();
    }

    static boolean totalCash(BigDecimal sum) {
        if (sum == null)
            return true;
        double sumValue = formatAbsPrice(sum);
        logAction("Oplata", 0, sumValue, 0);
        return absolutDLL.absolut.Oplata(0, sumValue, 0);
    }

    static boolean totalCard(BigDecimal sum) {
        if (sum == null)
            return true;
        double sumValue = formatAbsPrice(sum);
        logAction("Oplata", 3, sumValue, 0);
        return absolutDLL.absolut.Oplata(3, sumValue, 0);
    }

    static boolean totalGiftCard(BigDecimal sum) {
        if (sum == null)
            return true;
        int sumValue = formatAbsPrice(sum);
        logAction("Oplata", 1, sumValue, 0);
        return absolutDLL.absolut.Oplata(1, (double) sumValue, 0);
    }

    public static boolean total(BigDecimal sumPayment, Integer typePayment) {
        int sumPaymentValue = formatAbsPrice(sumPayment);
        logAction("Oplata", typePayment, sumPaymentValue, 0);
        if (!absolutDLL.absolut.Oplata(typePayment, sumPaymentValue, 0))
            return false;

        return true;
    }

    public static void xReport() {
        logAction("PrintReport", 10);
        absolutDLL.absolut.PrintReport(10);
    }

    public static void zReport(int type) {
        logAction("PrintReport", type);
        absolutDLL.absolut.PrintReport(type);
    }

    public static boolean inOut(BigDecimal sum) {
        double sumValue = formatPrice(sum);
        if (sumValue > 0) {
            logAction("InOut", sumValue);
            if (!absolutDLL.absolut.InOut(0, sumValue))
                checkErrors(true);
        } else {
            logAction("InOut", -sumValue);
            if (!absolutDLL.absolut.InOut(0, sumValue))
                return false;
        }
        return true;
    }

    static void displayText(ReceiptItem item) {
        try {
            String firstLine = " " + toStr(item.quantity) + "x" + toStr(item.price);
            int length = 16 - Math.min(16, firstLine.length());
            firstLine = item.name.substring(0, Math.min(length, item.name.length())) + firstLine;
            String secondLine = String.valueOf(item.sumPos);
            while (secondLine.length() < 11)
                secondLine = " " + secondLine;
            secondLine = "ИТОГ:" + secondLine;
            logAction("Outscr", firstLine, secondLine);
            if (!absolutDLL.absolut.OutScr(0, getBytes(firstLine)))
                checkErrors(true);
            if (!absolutDLL.absolut.OutScr(1, getBytes(secondLine)))
                checkErrors(true);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public static boolean registerAndDiscountItem(BigDecimal sum, BigDecimal discSum, boolean useSKNO) {
        try {
            logAction("FullProd", "11110", sum, 1, 1, 0, 0, "");
            if (absolutDLL.absolut.FullProd("11110", formatPrice(sum), 1, 1, 1, 0, getBytes(""), getCodeType(useSKNO))) {
                double discountSum = formatPrice(discSum);
                if (discountSum != 0.0) {
                    boolean discount = discountSum < 0;
                    logAction("NacSkd", discount ? 0 : 1, discSum.abs(), 0);
                    return absolutDLL.absolut.NacSkd(discount ? 0 : 1, Math.abs(discountSum), 0);
                } else return true;
            } else return false;
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }

    static boolean registerItem(ReceiptItem item, boolean saveCommentOnFiscalTape, boolean groupPaymentsByVAT, Integer maxLines, boolean useSKNO) {
        try {
            double price = formatAbsPrice(item.price);
            String barcode = item.getDigitBarcode();
            //if(item.barcode != null)
            //    printComment(item.barcode, saveCommentOnFiscalTape);
            int count = 0;
            for(String line : splitName(item.name)) {
                if(maxLines == null || maxLines == 0 || count < maxLines) {
                    printComment(line, saveCommentOnFiscalTape);
                    count++;
                }
            }
            int tax = groupPaymentsByVAT ? (item.valueVAT == 20.0 ? 1 : item.valueVAT == 10.0 ? 2 : item.valueVAT == 0.0 ? 3 : 0) : 0;
            String plu = barcode + (groupPaymentsByVAT ? (item.valueVAT == 20.0 ? "1" : item.valueVAT == 10.0 ? "2" : item.valueVAT == 0.0 ? "3" : "0") : "");
            logAction("FullProd", plu, item.price, item.quantity, item.isGiftCard ? 2 : 1, 1, tax, barcode);
            return absolutDLL.absolut.FullProd(plu, price, item.quantity, item.isGiftCard ? 2 : 1, 1, tax, getBytes(barcode), getCodeType(useSKNO));
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }

    static boolean registerItemPayment(BigDecimal sumPayment, boolean saveCommentOnFiscalTape) {
        try {
            double sum = formatPrice(sumPayment);
            printComment("ОПЛАТА", saveCommentOnFiscalTape);
            logAction("Prod", "", sum, 1.0, 1, 1);
            return absolutDLL.absolut.Prod("", sum, 1.0, 1, 1);
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }
    
    static boolean discountItem(ReceiptItem item, String numberDiscountCard) {
        double discSum = formatPrice(item.articleDiscSum - item.bonusPaid); //articleDiscSum is negative, bonusPaid is positive
        if (discSum == 0)
            return true;
        boolean discount = discSum < 0;
        logAction("NacSkd", discount ? 0 : 1, item.articleDiscSum - item.bonusPaid, 0, "discountCard: " + numberDiscountCard);
        return absolutDLL.absolut.NacSkd(discount ? 0 : 1, Math.abs(discSum), 0);
    }

    static boolean discountReceipt(ReceiptInstance receipt) {
        if (receipt.sumDisc == null)
            return true;
        boolean discount = receipt.sumDisc.compareTo(BigDecimal.ZERO) < 0;
        double sumDisc = formatAbsPrice(receipt.sumDisc);
        logAction("NacSkd", discount ? "Скидка" : "Наценка", receipt.sumDisc, discount ? 3 : 1, "discountCard: " + receipt.numberDiscountCard);
        return absolutDLL.absolut.NacSkd(discount ? 4 :5, sumDisc, 0);
    }
    
    static boolean subtotal() {
        logAction("Subtotal");
        if (!absolutDLL.absolut.Subtotal())
            return false;
        return true;
    }

    static void smenBegin() {
        logAction("SmenBegin");
        if (!absolutDLL.absolut.SmenBegin())
            checkErrors(true);
    }

    static boolean zeroReceipt(boolean useSKNO) {
        try {
            smenBegin();
            if(!openReceipt(true))
                return false;
            logAction("FullProd", "11110", 0, 1, 1, 0, 0, "");
            if(!absolutDLL.absolut.FullProd("11110", 0, 1, 1, 1, 0, getBytes(""), getCodeType(useSKNO)))
                return false;
            if (!subtotal())
                return false;
            if(!totalCash(BigDecimal.ZERO))
                return false;
            return closeReceipt();
        }catch (Exception e){
            FiscalAbsolut.cancelReceipt();
            return false;
        }
    }

    private static String toStr(double value) {
        boolean isInt = (value - (int) value) == 0;
        return isInt ? String.valueOf((int) value) : String.valueOf(value);
    }

    public static String toStr(BigDecimal value) {
        String result = null;
        if (value != null) {
            value = value.setScale(2, RoundingMode.HALF_UP);
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(2);
            df.setMinimumFractionDigits(2);
            df.setGroupingUsed(false);
            result = df.format(value).replace(",", ".");
        }
        return result;
    }

    public static int checkErrors(Boolean throwException) {
        logAction("LastError (checkErrors)");
        Integer lastError = absolutDLL.absolut.LastError();
        if (lastError != 0) {
            String errorText = lastError + " " + getErrorText(lastError);
            logAction("LastError result: " + errorText);
            if (throwException)
                throw new RuntimeException("Absolut Exception: " + errorText);
        }
        return lastError;
    }

    private static String getErrorText(Integer error) {
        switch (error) {
            case 49: return "Пользователь с указанным номером уже зарегистрирован";
            case 50: return "Неверный пароль";
            case 51: return "Неверный номер таблицы";
            case 52: return "Доступ к таблице запрещен";
            case 53: return "Умолчание не найдено";
            case 54: return "Недопустимый индекс";
            case 55: return "Неправильное поле";
            case 56: return "Таблица переполнена";
            case 57: return "Неправильная длина бинарной информации";
            case 58: return "Нельзя модифицировать поле, которое только для чтения";
            case 59: return "Недопустимое значение поля";
            case 60: return "Товар уже существует";
            case 61: return "По товару были зарегистрированы продажи";
            case 62: return "Запрос запрещен";
            case 63: return "Неверная закладка";
            case 64: return "Ключ не найден";
            case 65: return "Процедура уже исполняется";
            case 66: return "Количество товара не может быть отрицательным";
            case 1: return "Не указана цена";
            case 2: return "Не указано количество";
            case 3: return "Не указан отдел";
            case 4: return "Не указана товарная группа";
            case 255: return "Электронная контрольная лента переполнена";
            case 254: return "Чек уже открыт";
            case 253: return "Нефискальный чек уже открыт";
            case 252: return "Чек не открыт";
            case 251: return "Нефискальный чек не открыт";
            case 250: return "Неправильный номер оператора";
            case 249: return "Кассир не зарегистрирован";
            case 248: return "Оплата чека не завершена";
            case 247: return "Чек для создания копии не найден";
            case 246: return "Дневной отчет переполнен";
            case 245: return "Чек переполнен";
            case 244: return "Отрицательная сумма по чеку";
            case 243: return "Отрицательная сумма по дневному отчету";
            case 242: return "Переполнение поля записи";
            case 241: return "Вид оплаты запрещен или не существует";
            case 240: return "В чеке уже были продажи товаров";
            case 239: return "Начат внос/вынос денег";
            case 238: return "Нет такого товара";
            case 237: return "Плохая цена";
            case 236: return "Цена не может меняться";
            case 235: return "Плохое количество";
            case 234: return "Дробное количество";
            case 233: return "Переполнение суммы в long";
            case 232: return "Цена*Количество = 0";
            case 231: return "Плохой отдел";
            case 230: return "Отдел не может меняться";
            case 229: return "Плохая группа";
            case 228: return "Группа не может меняться";
            case 227: return "Товар закончился по количеству";
            case 226: return "Начата расплата по чеку";
            case 225: return "Недопустимая сумма оплаты";
            case 224: return "Использование кода клиента запрещено для данного вида оплаты";
            case 223: return "Недопустимая сумма оплаты";
            case 222: return "Наценка запрещена";
            case 221: return "Не было ни одной продажи";
            case 220: return "Плохой процент";
            case 219: return "Отрицательная сумма по товару";
            case 218: return "Нечего отменять командой VoidLast";
            case 217: return "В чеке не было продаж по коду";
            case 216: return "Отмена товара по которому есть пром.нац";
            case 215: return "Z1 отчет уже выведен и обнулен";
            case 214: return "Не обнулен Z1 отчет";
            case 213: return "Не указана нац/скидка по умолчанию";
            case 212: return "Не указан % скидки по умолчанию";
            case 211: return "Переход через дату или конец смены";
            case 210: return "Прервана печать контр. ленты";
            case 209: return "Не закрыт сейф";
            case 206: return "ef_BegPost - postal operation is opened";
            case 205: return "ef_PostCode - reserved PLU code for the postal operations";
            case 204: return "Начаты операции выплат";
            case 203: return "ef_OverTime2 Z4 report need (next month)";
            case 202: return "ef_Partion Partion postal operations started";
            case 201: return "ef_NoPartion Partion postal operations is not started";
            case 196: return "Изменилось имя или налоговая группа товара";
            case 195: return "Аппарат не в терминальном режиме";
            case 194: return "Недопустимый параметр процедуры";
            case 193: return "Недопустимый номер налога";
            case 192: return "Ошибка при работе с терминалом";
            case 191: return "Сработал сервисный таймер";
            case 190: return "Изменение даты/времени запрещено";
            case 189: return "Неправильная текущая дата";
            case 188: return "Активен режим тренировки";
            case 187: return "Электронная лента не пуста";
            case 169: return "ef_FMNotEmpty Fiscal memory is not empty";
            case 168: return "ef_FMBadNum   Bad Fiscal memory number";
            case 167: return "ef_MMCDisable MMC is disabled";
            case 166: return "ef_NoSlipPaper End of slip document occured";
            case 165: return "ef_NoFindZ1Rep Z1 report not found";
            case 164: return "ef_BegSlipChk Slip Receipt is open";
            default: return String.valueOf(error);
        }
    }

    public static int getReceiptNumber(Boolean throwException) {
        byte[] buffer = new byte[50];
        String result = Native.toString(buffer, "cp1251");
        return Integer.parseInt(result.split(",")[0]);
    }

    public static void logReceipt(ReceiptInstance receipt, Integer numberReceipt) {
        OutputStreamWriter sw = null;
        try {

            sw = new OutputStreamWriter(new FileOutputStream("logs/absolut.txt", true), StandardCharsets.UTF_8);
            String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            for(ReceiptItem item : receipt.receiptSaleList) {
                sw.write(String.format("%s|%s|1|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n", dateTime, numberReceipt,
                        trimToEmpty(item.getDigitBarcode()), item.name, toStr(item.price), item.quantity, item.sumPos, item.articleDiscSum,
                        item.isGiftCard ? "1" : "0", trim(receipt.sumDisc), trim(receipt.sumCard), trim(receipt.sumCash),
                        trim(receipt.sumGiftCard), trim(receipt.sumTotal)));
            }

            for(ReceiptItem item : receipt.receiptReturnList) {
                sw.write(String.format("%s|%s|2|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n", dateTime, numberReceipt,
                        trimToEmpty(item.getDigitBarcode()), item.name, item.price, item.quantity, item.sumPos, item.articleDiscSum,
                        item.isGiftCard ? "1" : "0", trim(receipt.sumDisc), trim(receipt.sumCard), trim(receipt.sumCash),
                        trim(receipt.sumGiftCard), trim(receipt.sumTotal)));
            }
        } catch (IOException e) {
            logger.error("FiscalAbsolut Error: ", e);
        } finally {
            if (sw != null) {
                try {
                    sw.flush();
                    sw.close();
                } catch (IOException e) {
                    logger.error("FiscalAbsolut Error: ", e);
                }
            }
        }
    }

    private static void logAction(Object... actionParams) {
        String pattern = "";
        for(Object param : actionParams)
            pattern += "%s;";
        logger.info(String.format(pattern, actionParams));
    }

    private static String trim(BigDecimal value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static byte[] getBytes(String value) throws UnsupportedEncodingException {
        return (value + "\0").getBytes("cp1251");
    }

    private static int formatAbsPrice(BigDecimal value) {
        return value == null ? 0 : (value.abs().multiply(new BigDecimal(100)).intValue());
    }

    private static int formatPrice(BigDecimal value) {
        return value == null ? 0 : value.multiply(new BigDecimal(100)).intValue();
    }

    private static double formatPrice(double value) {
        return value * 100;
    }

    private static List<String> splitName(String value) {
        List<String> result = new ArrayList<>();
        if(value != null) {
            while (value.length() > lineLength) {
                result.add(value.substring(0, lineLength));
                value = value.substring(lineLength);
            }
            result.add(value);
        }
        return result;
    }

    private static int getCodeType(boolean useSKNO) {
        return useSKNO ? 0 : -1;
    }
}

