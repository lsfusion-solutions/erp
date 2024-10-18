package lsfusion.erp.region.ru.machinery.cashregister.fiscalpirit;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import jssc.SerialPort;

@SuppressWarnings("DuplicatedCode")
public class VikiPrint {
    public static final String COM_PORT = "COM11";
    private static final Pattern LOG_PATTERN = Pattern.compile("\\p{Print}");
    private static final Charset ENCODING = Charset.forName("cp866");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyy");
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyHHmmss");
    private static final byte ENQ = 0x05;
    private static final byte ACK = 0x06;
    private static final char STX = 0x02;
    private static final char ETX = 0x03;
    private static final char FS = 0x1C;
    private static final String PASSWORD = "PIRI";
    private static int PACKET_ID = 0x20;

    public static void main(String[] args) throws Exception {
        SerialPort port = new SerialPort(COM_PORT);
        try {
            port.openPort();
            port.purgePort(SerialPort.PURGE_TXCLEAR | SerialPort.PURGE_RXCLEAR);
            while (port.getInputBufferBytesCount() > 0) {
                port.readBytes();
            }

            System.out.println("Проверка связи с ККТ");
            checkConnection(port);
            System.out.println();

            Object[] responseData;

            System.out.println("Обмен информацией с ФН");
            responseData = VikiPrint.executeCommand(port, 0x78, 22);
            System.out.printf("Текущая версия ФФД: %s%n", Integer.parseInt((String) responseData[1]) == 4 ? "1.2" : "1.05");
            System.out.printf("Максимальная поддерживаемая версия ФФД: %s%n", Integer.parseInt((String) responseData[2]) == 4 ? "1.2" : "1.05");
            System.out.println();

            System.out.println("Запрос состояния печатающего устройства");
            responseData = executeCommand(port, 0x04);
            System.out.printf("Статус печатающего устройства: %s%n", responseData[0]);
            System.out.println();

            System.out.println("Чтение даты/времени ККТ");
            responseData = executeCommand(port, 0x13);
            System.out.printf("Дата: %s%n", responseData[0]);
            System.out.printf("Время: %s%n", responseData[1]);
            System.out.println();

            System.out.println("Запрос флагов статуса ККТ");
            responseData = executeCommand(port, 0x00);
            int fatalStatus = Integer.parseInt((String) responseData[0]);
            int status = Integer.parseInt((String) responseData[1]);
            int docStatus = Integer.parseInt((String) responseData[2]);
            System.out.printf("Статус фатального состояния ККТ: %s%n", fatalStatus);
            System.out.printf("Статус текущих флагов ККТ: %s%n", status);
            System.out.printf("Статус документа: %s%n", docStatus);
            if ((status & (1L)) != 0) { // Не выполнена команда “Начало работы”
                LocalDateTime now = LocalDateTime.now();
                String date = now.format(DATE_FORMATTER);
                String time = now.format(TIME_FORMATTER);
                VikiPrint.executeCommand(port, 0x10, date, time); // Начало работы с ККТ (0x10)
            }
            if ((docStatus & 0x1F) != 0) { // Открыт документ
                VikiPrint.executeCommand(port, 0x32); // Аннулировать документ (0x32)
            }
            if ((status & (1L << 2)) == 0) { // Смена не открыта
                VikiPrint.executeCommand(port, 0x23, "Администратор"); // Открыть смену (0x23)
            } else if ((status & (1L << 3)) != 0) { // 24 часа истекли
                VikiPrint.executeCommand(port, 0x21, "Администратор"); // Сформировать отчет о закрытии смены (0x21)
                VikiPrint.executeCommand(port, 0x23, "Администратор"); // Открыть смену (0x23)
            }
            System.out.println();

            System.out.println("Запрос сведений о ККТ");
            responseData = executeCommand(port, 0x02, 3);
            System.out.printf("ИНН: %s%n", responseData[1]);
            responseData = executeCommand(port, 0x02, 1);
            System.out.printf("Заводской номер ККТ: %s%n", responseData[1]);
            System.out.println();

            System.out.println("Печать сервисного чека");
            executeCommand(port, 0x30, 1, 1);
            executeCommand(port, 0x40, "Текст");
            executeCommand(port, 0x31, 2);
            System.out.println();

            System.out.println("Продажа штучного и весового товара (Обычный режим формирования документа)");
            executeCommand(port, 0x30, 2, 1, "Петров", "", 0, "");
            executeCommand(port, 0x42, "Сахар", "", 1, 100, 4, "", "", "");
            executeCommandPacket(port, 0x42, "Сахар", "", "1.111", "100", "4", "", "", "", "11");
            executeCommand(port, 0x44);
            executeCommand(port, 0x47, 0, 1000);
            responseData = executeCommand(port, 0x31, 2);
            System.out.printf("ФД: %s%n", responseData[3]);
            System.out.printf("ФП: %s%n", responseData[4]);
            System.out.printf("Номер смены: %s%n", responseData[5]);
            System.out.printf("Номер документа в смене: %s%n", responseData[6]);
            System.out.printf("Дата документа: %s%n", responseData[7]);
            System.out.printf("Время документа: %s%n", responseData[8]);
            System.out.println();

            System.out.println("Продажа штучного и весового товара (Пакетный режим формирования документа)");
            executeCommandPacket(port, 0x30, 2 | 16, 1, "Петров", "", 0, "");
            executeCommandPacket(port, 0x42, "Сахар", "", 1, 100, 4, "", "", "");
            executeCommandPacket(port, 0x42, "Сахар", "", "1.111", "100", "4", "", "", "", "11");
            executeCommandPacket(port, 0x44);
            executeCommandPacket(port, 0x47, 0, 1000);
            responseData = executeCommand(port, 0x31, 2);
            System.out.printf("ФД: %s%n", responseData[3]);
            System.out.printf("ФП: %s%n", responseData[4]);
            System.out.printf("Номер смены: %s%n", responseData[5]);
            System.out.printf("Номер документа в смене: %s%n", responseData[6]);
            System.out.printf("Дата документа: %s%n", responseData[7]);
            System.out.printf("Время документа: %s%n", responseData[8]);
            System.out.println();

/*
            Закрытие архива ФН
            responseData = executeCommand(port, 0x71, "Петров");
            System.out.printf("ФД: %s%n", responseData[0]);
            System.out.printf("ФП: %s%n", responseData[1]);
            System.out.printf("Дата документа: %s%n", responseData[2]);
            System.out.printf("Время документа: %s%n", responseData[3]);
*/

        } finally {
            port.closePort();
        }
    }

    public static void checkConnection(SerialPort port) throws Exception {
        port.writeByte(ENQ);
        System.out.printf("==> %s%n", ENQ);
        while (port.getInputBufferBytesCount() <= 0) {
            Thread.yield();
        }
        byte[] bytes = port.readBytes();
        System.out.printf("<~~ %s%n", toString(bytes));
        for (byte[] response : splitPackets(bytes)) {
            System.out.printf("<== %s%n", toString(response));
            if (response[0] != ACK) {
                parseResponse(response);
            }
        }
    }

    //Синхронная работа
    public static Object[] executeCommand(SerialPort port, int command, Object... parameters) throws Exception {
        return execute(port, command, false, parameters);
    }

    //Пакетная работа
    public static void executeCommandPacket(SerialPort port, int command, Object... parameters) throws Exception {
        execute(port, command, true, parameters);
    }

    //Отправка пакета в порт принтера, получение и разбор ответа
    private static Object[] execute(SerialPort port, int command, boolean packetMode, Object... parameters) throws Exception {
        Object[] result = new Object[0];

        byte[] request = makeRequest(command, parameters);
        System.out.printf("==> %s%n", toString(request));
        port.writeBytes(request);
        if (packetMode) {
            return result;
        }

        int responsePacketId = 0;
        while (responsePacketId < PACKET_ID - 1) {
            while (port.getInputBufferBytesCount() <= 0) {
                Thread.yield();
            }
            byte[] bytes = port.readBytes();
            List<byte[]> splitPackets = splitPackets(bytes);
            if (splitPackets.size() > 1) {
                System.out.printf("<~~ %s%n", toString(bytes));
            }
            for (byte[] response : splitPackets) {
                System.out.printf("<== %s%n", toString(response));
                Object[] responseData = parseResponse(response);
                responsePacketId = (int) responseData[0];
                int errorCode = (int) responseData[2];
                if (errorCode != 0) {
                    if (errorCode == 0x01 || errorCode == 0x03) {
                        throw new Exception((String) executeCommand(port, 0x06, 1)[2]);
                    }
                    throw new Exception(String.format("Ошибка 0x%s", toHexString(errorCode)));
                }

                result = new Object[responseData.length - 4];
                System.arraycopy(responseData, 3, result, 0, responseData.length - 4);
            }
        }
        return result;
    }

    //Разбиение полученных байтов по пакетам
    private static List<byte[]> splitPackets(byte[] response) {
        List<byte[]> result = new ArrayList<>();
        List<Byte> part = new ArrayList<>();
        part.add(response[0]);
        for (int i = 1; i < response.length; i++) {
            if (response[i] != STX) {
                part.add(response[i]);
            }
            if (response[i] == STX || i == response.length - 1) {
                result.add(ArrayUtils.toPrimitive(part.toArray(new Byte[0])));
                part.clear();
                part.add(response[i]);
            }
        }
        return result;
    }

    //Формирование пакета команды со стороны КП
    private static byte[] makeRequest(int command, Object... parameters) {
        StringBuilder strPacket = new StringBuilder()
            .append(STX)                                    // STX
            .append(PASSWORD)                               // Пароль связи
            .append((char) PACKET_ID++)                     // ID пакета
            .append(toHexString(command));                  // Код команды
        if (parameters.length == 0) {                       //
            strPacket.append(FS);                           //
        } else {                                            //
            for (Object param : parameters) {               //
                String value = param.toString();
                StringBuilder sb = new StringBuilder();
                for (char c : value.toCharArray()) {
                    sb.append(c >= 32 ? c : ("$" + String.format("%02X", (int) c)));
                }
                strPacket.append(sb).append(FS);         // Данные
            }                                               //
        }                                                   //
        strPacket.append(ETX);                              // ETX
        int crc = calculateCrc(strPacket.toString());
        strPacket.append(toHexString(crc));                 // CRC

        return strPacket
            .toString()
            .getBytes(ENCODING);
    }

    //Парсинг пакета ответа со стороны КП
    private static Object[] parseResponse(byte[] response) throws Exception {
        String data = new String(response, ENCODING);
        int packetId = data.charAt(1);                              // ID пакета
        int command = Integer.parseInt(data.substring(2, 4), 16);   // Код команды
        int errorCode = Integer.parseInt(data.substring(4, 6), 16); // Код ошибки
        String dataPart = data.substring(6, data.indexOf(ETX));     // Данные
        String dataForCRCPart = data.substring(0, data.indexOf(ETX) + 1);
        String crcPart = data.substring(data.indexOf(ETX) + 1);
        int crc = Integer.parseInt(crcPart, 16);
        if (crc != calculateCrc(dataForCRCPart)) {
            throw new Exception("Wrong CRC");
        }

        Object[] dataArray = Arrays.stream(dataPart.split(String.valueOf(FS))).toArray();
        Object[] result = new Object[3 + dataArray.length + 1];
        result[0] = packetId;
        result[1] = command;
        result[2] = errorCode;
        System.arraycopy(dataArray, 0, result, 3, dataArray.length);
        result[result.length - 1] = crc;
        return result;
    }

    //Расчет контрольной суммы пакета
    private static int calculateCrc(String string) {
        byte[] rawPacket = string.getBytes(ENCODING);
        int crc = 0;
        for (int i = 1; i < rawPacket.length; i++) {
            crc = (crc ^ rawPacket[i]) & 0xFF;
        }
        return crc;
    }

    //Красивая запись данных пакета для логирования
    private static String toString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (char c : new String(bytes, ENCODING).toCharArray()) {
            if (c > 255 || LOG_PATTERN.matcher(String.valueOf(c)).matches()) {
                sb.append(c);
            } else {
                sb.append('{').append(toHexString(c)).append('}');
            }
        }
        return sb.toString();
    }

    //Понятное преобразование 16-тиричного представления числа
    private static String toHexString(int value) {
        return StringUtils.leftPad(Integer.toHexString(value & 0xFF).toUpperCase(), 2, '0');
    }
}