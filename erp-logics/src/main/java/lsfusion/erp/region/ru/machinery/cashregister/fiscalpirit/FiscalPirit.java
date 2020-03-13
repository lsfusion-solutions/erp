package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import com.google.common.base.Throwables;
import jssc.SerialPort;
import jssc.SerialPortException;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.spongycastle.util.encoders.Hex;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class FiscalPirit {

    private static byte dataDelimiter = 0x1C;

    private static short packetId = 0x27;

    private static Charset charset = Charset.forName("cp866");

    static Logger logger;

    static {
        try {
            logger = Logger.getLogger("cashRegisterLog");
            logger.setLevel(Level.INFO);
            FileAppender fileAppender = new FileAppender(new EnhancedPatternLayout("%d{DATE} %5p %c{1} - %m%n%throwable{1000}"), "logs/cashregister.log");
            logger.removeAllAppenders();
            logger.addAppender(fileAppender);

        } catch (Exception ignored) {
        }
    }

    public static SerialPort openPort(String comPort, Integer baudRate, boolean isUnix) {
        try {
            SerialPort serialPort = new SerialPort((isUnix ? "/dev/tty" : "COM") + comPort);
            serialPort.openPort();
            serialPort.setParams(baudRate, 8, 1, 0);
            return serialPort;
        } catch (Exception e) {
            throw new RuntimeException("Open com-port failed: " + e.getMessage(), e);
        }
    }

    public static void closePort(SerialPort serialPort) {
        try {
            if (serialPort != null) {
                serialPort.closePort();
            }
        } catch (SerialPortException e) {
            throw new RuntimeException("Close com-port failed");
        }
    }

    public static void preparePrint(SerialPort serialPort) {
        checkConnection(serialPort);
        if(getStatusFlag(serialPort, 1, 0)) { //1-ый байт, 0-ой бит - Не была вызвана функция “Начало работы”
            startKKT(serialPort);
        }
    }

    public static Integer printReceipt(SerialPort serialPort, String cashier, ReceiptInstance receipt, List<ReceiptItem> receiptList,
                                       Integer giftCardDepartment, Integer giftCardPaymentType, Integer saleGiftCardPaymentType, boolean sale) {
        openZReportIfClosed(serialPort, cashier);
        openDocumentCommand(serialPort, cashier, sale ? "2" : "3");

        for (ReceiptItem item : receiptList) {
            registerItemCommand(serialPort, item, giftCardDepartment);
        }

        subtotalCommand(serialPort);

        if (receipt.sumDisc != null) {
            discountReceiptCommand(serialPort, receipt.sumDisc);
        }

        if (receipt.sumGiftCard != null) {
            totalCommand(serialPort, receipt.sumGiftCard, giftCardPaymentType != null ? String.valueOf(giftCardPaymentType) : "2");
        }
        if (receipt.sumCard != null) {
            totalCommand(serialPort, receipt.sumCard, "1");
        }
        if (receipt.sumCash != null) {
            totalCommand(serialPort, receipt.sumCash, "0");
        }
        if (receipt.sumPrepayment != null) { //если saleGiftCardPaymentType не задан, считаем наличными
            totalCommand(serialPort, receipt.sumPrepayment, saleGiftCardPaymentType != null ? String.valueOf(saleGiftCardPaymentType) : "0");
        }

        closeDocumentCommand(serialPort);

        return getReceiptNumber(serialPort);
    }

    public static void cancelDocument(SerialPort serialPort) {
        cancelDocumentCommand(serialPort);
    }

    public static void xReport(SerialPort serialPort) {
        xReportCommand(serialPort);
    }

    public static int zReport(SerialPort serialPort, String cashier) {
        openZReportIfClosed(serialPort, cashier);
        zReportCommand(serialPort, cashier);
        return getZReportNumber(serialPort);
    }

    public static void advancePaper(SerialPort serialPort) {
        advancePaperCommand(serialPort);
    }

    public static void inOut(SerialPort serialPort, String cashier, BigDecimal sum) {
        openZReportIfClosed(serialPort, cashier);
        openDocumentCommand(serialPort, cashier, sum.compareTo(BigDecimal.ZERO) > 0 ? "4" : "5");
        inOutDocumentCommand(serialPort, sum.abs());
        closeDocumentCommand(serialPort);
    }

    private static void openZReportIfClosed(SerialPort serialPort, String cashier) {
        if(!getStatusFlag(serialPort, 1, 2)) { // 1-ый байт, 2 бит - открыта ли смена
            openZReportCommand(serialPort, cashier);
        }
    }

    private static boolean getStatusFlag(SerialPort serialPort, int byteIndex, int bitIndex) {
        PiritReply reply = checkStatusFlagsCommand(serialPort);
        List<byte[]> statusFlags = splitData(reply.data);
        Integer flagByte = Integer.parseInt(new String((statusFlags.get(byteIndex))));
        return BigInteger.valueOf(flagByte).testBit(bitIndex);
    }

    private static int getZReportNumber(SerialPort serialPort) {
        PiritReply reply = getReceiptStatusCommand(serialPort);
        String[] receiptNumber = new String(splitData(reply.data).get(2)).split("\\.");
        return Integer.parseInt(receiptNumber[0]);
    }

    private static int getReceiptNumber(SerialPort serialPort) {
        PiritReply reply = getReceiptStatusCommand(serialPort);
        String[] receiptNumber = new String(splitData(reply.data).get(2)).split("\\.");
        return Integer.parseInt(receiptNumber[receiptNumber.length > 1 ? 1 : 0]);
    }

    private static void checkConnection(SerialPort serialPort) {
        byte reply = checkConnectionCommand(serialPort);
        if (reply != 0x06) {
            throw new RuntimeException("No connection");
        }
    }

    private static void startKKT(SerialPort serialPort) {
        Date currentDate = Calendar.getInstance().getTime();
        String date = new SimpleDateFormat("ddMMyy").format(currentDate);
        String time = new SimpleDateFormat("HHmmss").format(currentDate);

        PiritReply reply = startKKTCommand(serialPort, date, time);
        switch (reply.error) {
            case 0x00:
            case 0x0b://todo: время на кассе отличается больше чем на 8 минут, рекомендуется закрыть смену и синхронизировать время
                break;
            case 0x0c:
                throw new RuntimeException("Переданное текущее время меньше времени последней фискальной операции");
            default:
                throw new RuntimeException(reply.getErrorText());
        }
    }

    //---------------------------------------------------------------------------

    private static byte checkConnectionCommand(SerialPort serialPort) {
        return sendBytesToPort(serialPort, new byte[] {(byte) 0x05}, true)[0];
    }

    private static void advancePaperCommand(SerialPort serialPort) {
        sendBytesToPort(serialPort, new byte[] {(byte) 0x0A}, false);
    }

    private static PiritReply checkStatusFlagsCommand(SerialPort serialPort) {
        return sendCommand(serialPort, "00", "Запрос флагов статуса ККТ", true);
    }

    private static PiritReply getReceiptStatusCommand(SerialPort serialPort) {
        return sendCommand(serialPort, "03", "Запрос данных по чеку", joinData("2"), true);
    }

    private static PiritReply startKKTCommand(SerialPort serialPort, String date, String time) {
        return sendCommand(serialPort, "10", "Начало работы с ККТ", joinData(date, time), false);
    }

    private static void xReportCommand(SerialPort serialPort) {
        sendCommand(serialPort, "20", "X-отчёт", true);
    }

    private static void zReportCommand(SerialPort serialPort, String cashier) {
        sendCommand(serialPort, "21", "Z-отчёт", joinData(cashier), true);
    }

    private static void openZReportCommand(SerialPort serialPort, String cashier) {
        sendCommand(serialPort, "23", "Открыть смену", joinData(cashier), true);
    }

    private static void openDocumentCommand(SerialPort serialPort, String cashier, String type) {
        sendCommand(serialPort, "30", "Открыть документ", joinData(type, "", cashier, "", "", "", ""), true);
    }

    private static void closeDocumentCommand(SerialPort serialPort) {
        sendCommand(serialPort, "31", "Завершить документ", joinData("", "", "", "", "", "", "", "", ""), true);
    }

    private static void cancelDocumentCommand(SerialPort serialPort) {
        sendCommand(serialPort, "32", "Аннулировать документ", false);
    }

    private static void registerItemCommand(SerialPort serialPort, ReceiptItem item, Integer giftCardDepartment) {
        String department = giftCardDepartment != null ? String.valueOf(giftCardDepartment) : "0";
        sendCommand(serialPort, "42", "Добавить товарную позицию", joinData(trim(item.name, 256), item.barcode,
                formatBigDecimal(item.quantity), formatBigDecimal(safeAdd(item.price, item.articleDiscSum)), "0", "0",
                department, "0", "", formatBigDecimal(safeNegate(item.articleDiscSum)), "4", "1", "BLR", "0"), true);
    }

    private static void subtotalCommand(SerialPort serialPort) {
        sendCommand(serialPort, "44", "Подытог", true);
    }

    private static void discountReceiptCommand(SerialPort serialPort, BigDecimal sumDisc) {
        sendCommand(serialPort, "45", "Скидка на чек", joinData("1", "Скидка на чек", formatBigDecimal(sumDisc)), true);
    }

    private static void totalCommand(SerialPort serialPort, BigDecimal sum, String type) {
        sendCommand(serialPort, "47", "Оплата", joinData(type, formatBigDecimal(sum.abs())), true);
    }

    private static void inOutDocumentCommand(SerialPort serialPort, BigDecimal sum) {
        sendCommand(serialPort, "48", "Внесение / изъятие суммы", joinData("", formatBigDecimal(sum)), true);
    }

    //---------------------------------------------------------------------------

    private static PiritReply sendCommand(SerialPort serialPort, String commandID, String commandDescription, boolean throwException) {
        return sendCommand(serialPort, commandID, commandDescription, new byte[]{}, throwException);
    }

    private static PiritReply sendCommand(SerialPort serialPort, String commandID, String commandDescription, byte[] data, boolean throwException) {
        logger.info(String.format("Command %s (%s), data %s", commandID, commandDescription, Hex.toHexString(data)));
        ByteBuffer command = ByteBuffer.allocate(11 + data.length);
        command.put((byte) 0x02); //stx, 1 byte
        command.put("PIRI".getBytes()); //Пароль связи, 4 bytes

        command.put((byte) packetId++); //ID пакета, 1 byte
        if(packetId > 0xF0) {
            packetId = 0x20;
        }

        assert commandID.length() == 2;
        command.put(commandID.getBytes()); //Код команды, 2 bytes
        command.put(data);
        command.put((byte) 0x03); //etx, 1 byte

        String crc = getCRC(command.array());
        command.put(crc.getBytes()); //crc, 2 bytes
        byte[] reply = sendBytesToPort(serialPort, command.array(), true);
        logger.info("Reply: " + Hex.toHexString(reply));

        PiritReply result = new PiritReply(Hex.decode(Arrays.copyOfRange(reply, 4, 6))[0], Arrays.copyOfRange(reply, 6, reply.length - 3));
        if (result.error != 0 && throwException) {
            throw new RuntimeException(String.format("Command %s, result: %s", commandID, result.getErrorText()));
        } else {
            return result;
        }

    }

    private static byte[] sendBytesToPort(SerialPort serialPort, byte[] command, boolean hasResponse) {
        try {
            final Future<byte[]> future = Executors.newSingleThreadExecutor().submit(() -> {
                serialPort.writeBytes(command);
                if(hasResponse) {
                    byte[] result;
                    while ((result = serialPort.readBytes()) == null) {
                        Thread.sleep(100);
                    }
                    return result;
                } else {
                    return null;
                }
            });

            byte[] result;
            try {
                result = future.get(5000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw Throwables.propagate(e);
            }
            return result;
        } catch (InterruptedException | ExecutionException e) {
            throw Throwables.propagate(new RuntimeException("No response (timeout)"));
        }
    }

    private static String getCRC(byte[] bytes) {
        byte result = 0;
        for (int i = 1; i < bytes.length - 2; i++) {
            result = (byte) (result ^ bytes[i]);
        }
        String hex = Integer.toHexString(result >= 0 ? result: (result+256));
        return (hex.length() < 2 ? "0" : "") + hex;
    }

    private static byte[] joinData(String... params) {
        int dataLength = 0;
        for (String a : params) {
            dataLength += a.length();
        }
        ByteBuffer data = ByteBuffer.allocate(dataLength + params.length);
        for (String param : params) {
            data.put(param.getBytes(charset));
            data.put(dataDelimiter);
        }
        return data.array();
    }

    private static List<byte[]> splitData(byte[] array) {
        List<byte[]> byteArrays = new LinkedList<>();
        int begin = 0;
        for (int i = 0; i < array.length; i++) {
            if (array[i] == dataDelimiter) {
                byteArrays.add(Arrays.copyOfRange(array, begin, i));
                begin = i + 1;
            }
        }
        byteArrays.add(Arrays.copyOfRange(array, begin, array.length));
        return byteArrays;
    }

    private static String trim(String input, Integer length) {
        return input == null ? null : (length == null || length >= input.trim().length() ? input.trim() : input.trim().substring(0, length));
    }

    private static String formatBigDecimal(BigDecimal value) {
        return value == null ? "" : String.valueOf(value).replace(",", ".");
    }

    private static BigDecimal safeNegate(BigDecimal operand) {
        return operand == null ? null : operand.negate();
    }

    private static BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }

    private static class PiritReply {
        byte error;
        byte[] data;

        private PiritReply(byte error, byte[] data) {
            this.error = error;
            this.data = data;
        }

        private String getErrorText() {
            switch (error) {
                case 1:
                    return "Не была вызвана функция 'Начало работы'";
                case 3:
                    return "Некорректный формат или параметр команды";
                default:
                    return "Неизвестная ошибка " + error;
            }
        }

    }
}

