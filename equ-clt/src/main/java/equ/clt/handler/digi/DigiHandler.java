package equ.clt.handler.digi;

import equ.api.*;
import equ.api.scales.ScalesHandler;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class DigiHandler extends ScalesHandler {

    private final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");

    private static short cmdWrite = 0xF1;
    private static short cmdCls = 0xF2;
    private static short filePLU = 0x25;

    private FileSystemXmlApplicationContext springContext;

    public DigiHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    @Override
    public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) throws IOException {

        Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        if (!transactionList.isEmpty()) {

            DigiSettings digiSettings = springContext.containsBean("digiSettings") ? (DigiSettings) springContext.getBean("digiSettings") : null;
            Integer maxLineLength = digiSettings != null ? digiSettings.getMaxLineLength() : null;
            maxLineLength = maxLineLength == null ? 50 : maxLineLength;

            for (TransactionScalesInfo transaction : transactionList) {
                processTransactionLogger.info("Digi: Send Transaction # " + transaction.id);

                List<MachineryInfo> succeededScalesList = new ArrayList<>();
                Exception exception = null;

                if (!transaction.machineryInfoList.isEmpty()) {

                    List<ScalesInfo> enabledScalesList = getEnabledScalesList(transaction, succeededScalesList);
                    Integer errorsCount = 0;

                    processTransactionLogger.info("Digi: Starting sending to " + enabledScalesList.size() + " scales...");
                    for (ScalesInfo scales : enabledScalesList) {
                        String errors = "";

                        processTransactionLogger.info("Digi: Sending to scales " + scales.port);
                        if (scales.port != null) {
                            DataSocket socket = new DataSocket(scales.port);
                            try {

                                socket.open();

                                if (transaction.snapshot) {
                                    processTransactionLogger.info("Digi: Deleting all plu at scales " + scales.port);
                                    int reply = sendRecord(socket, cmdCls, filePLU, new byte[0]);
                                    if (reply != 0) {
                                        processTransactionLogger.error(String.format("Digi: Deleting all plu failed. Error: %s", reply));
                                        errors += String.format("Deleting all plu failed. Error: %s\n", reply);
                                    }
                                }

                                if (errors.isEmpty()) {
                                    for (ScalesItemInfo item : transaction.itemsList) {
                                        if (errorsCount < 5) {
                                            int barcode = Integer.parseInt(item.idBarcode.substring(0, 5));
                                            int pluNumber = item.pluNumber == null ? barcode : item.pluNumber;
                                            byte[] record = makeRecord(item, getWeightCode(scales), getPieceCode(scales), maxLineLength);
                                            processTransactionLogger.info(String.format("Digi: Sending item %s to scales %s", barcode, scales.port));
                                            int reply = sendRecord(socket, cmdWrite, filePLU, record);
                                            if (reply != 0) {
                                                processTransactionLogger.error(String.format("Digi: Send item %s to scales %s failed. Error: %s", barcode, scales.port, reply));
                                                errors += String.format("Send item %s to scales %s failed. Error: %s\n", scales.port, pluNumber, reply);
                                                errorsCount++;
                                            }
                                        }
                                    }
                                }

                                if (errors.isEmpty())
                                    succeededScalesList.add(scales);
                                else
                                    exception = new RuntimeException(errors);

                            } catch (Exception e) {
                                processTransactionLogger.error("Digi: ", e);
                                exception = e;
                            } finally {
                                processTransactionLogger.info("Digi: Finally disconnecting... " + scales.port);
                                try {
                                    socket.close();
                                } catch (CommunicationException e) {
                                    processTransactionLogger.info("DigiPrintHandler close port error: ", e);
                                }
                            }
                        }
                    }

                    sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(succeededScalesList, exception));
                }
            }
        } else {
            processTransactionLogger.error("Digi: Empty transaction list!");
        }
        return sendTransactionBatchMap;
    }

    private byte[] makeRecord(ScalesItemInfo item, String weightCode, String pieceCode, Integer maxLineLength) throws UnsupportedEncodingException {
        boolean hasDescription = item.description != null && !item.description.isEmpty();
        String[] splittedDescription = hasDescription ? item.description.split("@@") : null;

        String compositionMessage = splittedDescription != null ? splittedDescription[0] : null;
        boolean hasComposition = compositionMessage != null && !compositionMessage.isEmpty();
        List<String> compositionLines = new ArrayList<>();
        int compositionLength = 0;
        if(hasComposition) {
            for (String compositionLine : compositionMessage.split("\n")) {
                while (compositionLine.length() > maxLineLength) {
                    compositionLines.add(compositionLine.substring(0, maxLineLength));
                    compositionLength += maxLineLength + 1;
                    compositionLine = compositionLine.substring(maxLineLength);
                }
                compositionLines.add(compositionLine);
                compositionLength += compositionLine.length() + 1;
            }
        }

        String expiryMessage = splittedDescription != null && splittedDescription.length > 1 ? splittedDescription[1] : null;
        boolean hasExpiry = expiryMessage != null && !expiryMessage.isEmpty();
        List<String> expiryLines = new ArrayList<>();
        int expiryLength = 0;
        if(hasExpiry) {
            for (String expiryLine : expiryMessage.split("\n")) {
                while (expiryLine.length() > maxLineLength) {
                    expiryLines.add(expiryLine.substring(0, maxLineLength));
                    expiryLength += maxLineLength + 1;
                    expiryLine = expiryLine.substring(maxLineLength);
                }
                expiryLines.add(expiryLine);
                expiryLength += expiryLine.length() + 1;
            }
        }

        int length = 36 + item.name.length() +
                compositionLength + (compositionLines.isEmpty() ? 0 : compositionLines.size() * 2) +
                expiryLength + (expiryLines.isEmpty() ? 0 : expiryLines.size() * 2);

        ByteBuffer bytes = ByteBuffer.allocate(length);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        // Номер PLU, 4 bytes
        String plu = item.pluNumber != null ? String.valueOf(item.pluNumber) : item.idBarcode;
        bytes.put(getHexBytes(fillLeadingZeroes(plu, 8)));

        //Длина записи, заполняется в конце
        bytes.put((byte) 0);
        bytes.put((byte) 0);

        // 1-й байт 1-го статуса
        boolean pieceItem = item.shortNameUOM != null && item.shortNameUOM.toUpperCase().startsWith("ШТ");
        byte st1b1 = 0;
        if (pieceItem)
            st1b1 = setBit(st1b1, 0); //штучный
        st1b1 = setBit(st1b1, 2); //печатать дату продажи
        st1b1 = setBit(st1b1, 4); //печатать дату упаковки
        st1b1 = setBit(st1b1, 6); //печатать время упаковки
        bytes.put(st1b1);

        // 2-й байт 1-го статуса
        byte st1b2 = 0;
        bytes.put(st1b2);

        // 1-й байт 2-го статуса
        byte st2b1 = 0;
        st2b1 = setBit(st2b1, 0); //Формат 1-й этикетки Указан явно
        st2b1 = setBit(st2b1, 2); //Формат штрихкода Указан явно
        st2b1 = setBit(st2b1, 3); //Артикул товара Указан явно
        bytes.put(st2b1);

        // 2-й байт 2-го статуса
        byte st2b2 = 0;
        st2b2 = setBit(st2b2, 1); //Поле «Номера спец.сообщения» Есть
        st2b2 = setBit(st2b2, 2); //Поле «Номер ингредиента» Есть
        st2b2 = setBit(st2b2, 5); //Поле «Название товара» Есть
        if (hasExpiry)
            st2b2 = setBit(st2b2, 6); //Поле «Текст встроенного в PLU ингредиента» Есть
        if (hasComposition)
            st2b2 = setBit(st2b2, 7); // Поле «Текст встроенного в PLU спец. сообщения» Есть
        bytes.put(st2b2);

        // 3-й байт 2-го статуса
        byte st2b3 = 0;
        st2b3 = setBit(st2b3, 0);
        bytes.put(st2b3);

        // Цена, 4 bytes
        int price = item.price == null ? 0 : item.price.multiply(new BigDecimal(100)).intValue();
        bytes.put(getHexBytes(fillLeadingZeroes(String.valueOf(price), 8)));

        // номер формата 1-й этикетки
        bytes.put((byte) 17);

        // номер формата штрихкода
        bytes.put((byte) 5);

        // данные штрихкода, 7 bytes
        String prefix = pieceCode != null && pieceItem ? pieceCode : weightCode;
        String barcode = fillTrailingZeroes(prefix + item.idBarcode, 13) + (pieceItem ? 2 : 1);
        bytes.put(getHexBytes(barcode));

        // срок продажи в днях, 2 bytes
        bytes.put(getHexBytes(fillLeadingZeroes(String.valueOf(item.daysExpiry == null ? 0 : item.daysExpiry), 4)));

        // номер спец. сообщения
        bytes.put((byte) 0);

        // номер ингредиента
        bytes.put((byte) 0);

        //шрифт наименования
        bytes.put((byte) 4);

        //длина наименования
        bytes.put((byte) item.name.length());

        // Наименование товара
        bytes.put(getBytes(item.name));

        //если будет разбиение на строки
        //терминатор первой строки
        //bytes.put((byte) 0x0D);
        //заголовок второй строки, 2 bytes
        //bytes.put(getHexBytes("0311")); //номер шрифта и длина
        //bytes.put(getBytes("second line"));

        bytes.put((byte) 0x0C);

        //todo: макс. длина строки состава и спец.сообщения - 51 символ, хорошо бы ещё и на это смотреть
        // Состав
        if (hasComposition) {
            for (int i = 0; i < compositionLines.size(); i++) {
                bytes.put((byte) 2);
                bytes.put((byte) compositionLines.get(i).length());
                bytes.put(getBytes(compositionLines.get(i)));
                bytes.put((byte) (i == compositionLines.size() - 1 ? 0x0C : 0x0D));
            }
        }

        // Специальное сообщение
        if (hasExpiry) {
            for (int i = 0; i < expiryLines.size(); i++) {
                bytes.put((byte) 2);
                bytes.put((byte) expiryLines.get(i).length());
                bytes.put(getBytes(expiryLines.get(i)));
                bytes.put((byte) (i == expiryLines.size() - 1 ? 0x0C : 0x0D));
            }
        }

        // Контрольная сумма
        bytes.put((byte) 0);

        //Длина записи
        bytes.put(4, (byte) (length >>> 8));
        bytes.put(5, (byte) length);

        return bytes.array();
    }

    private byte setBit(byte byteValue, int pos) {
        return (byte) (byteValue | (1 << pos));
    }

//    private byte clearBit(byte byteValue, int pos) {
//        return (byte) (byteValue & ~(1 << pos));
//    }

    private String fillLeadingZeroes(String input, int length) {
        if (input == null)
            return null;
        if (input.length() > length)
            input = input.substring(0, length);
        while (input.length() < length)
            input = "0" + input;
        return input;
    }

    private String fillTrailingZeroes(String input, int length) {
        if (input == null)
            return null;
        while (input.length() < length)
            input = input + "0";
        return input;
    }

    private int sendRecord(DataSocket socket, short cmd, short file, byte[] record) throws IOException, CommunicationException {
        ByteBuffer bytes = ByteBuffer.allocate(record.length + 2);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        bytes.put((byte) cmd);
        bytes.put((byte) file);
        bytes.put(record);

        return sendCommand(socket, bytes.array());
    }

    private int sendCommand(DataSocket socket, byte[] bytes) throws IOException {
        int attempts = 0;
        while (attempts < 3) {
            try {
                socket.outputStream.write(bytes);
                return receiveReply(socket);
            } catch (CommunicationException e) {
                attempts++;
                if (attempts == 3)
                    processTransactionLogger.error("Digi SendCommand Error: ", e);
            }
        }
        return -1;
    }

    private int receiveReply(DataSocket socket) throws CommunicationException {
        try {
            byte[] buffer = new byte[10];
            socket.inputStream.read(buffer);
            return buffer[0] == 6 ? 0 : buffer[0]; //это либо байт ошибки, либо первый байт хвоста (:)
        } catch (Exception e) {
            return -1;
        }
    }

    private List<ScalesInfo> getEnabledScalesList(TransactionScalesInfo transaction, List<MachineryInfo> succeededScalesList) {
        List<ScalesInfo> enabledScalesList = new ArrayList<>();
        for (ScalesInfo scales : transaction.machineryInfoList) {
            if (scales.succeeded)
                succeededScalesList.add(scales);
            else if (scales.enabled)
                enabledScalesList.add(scales);
        }
        if (enabledScalesList.isEmpty())
            for (ScalesInfo scales : transaction.machineryInfoList) {
                if (!scales.succeeded)
                    enabledScalesList.add(scales);
            }
        return enabledScalesList;
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoList) throws IOException {
    }

    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) throws IOException {
        return "Digi";
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {
    }

    private String getWeightCode(MachineryInfo scales) {
        String weightCode = scales instanceof ScalesInfo ? ((ScalesInfo) scales).weightCodeGroupScales : null;
        return weightCode == null ? "21" : weightCode;
    }

    private String getPieceCode(MachineryInfo scales) {
        return scales instanceof ScalesInfo ? ((ScalesInfo) scales).pieceCodeGroupScales : null;
    }

    private byte[] getBytes(String value) throws UnsupportedEncodingException {
        return value.getBytes("cp866");
    }

    private byte[] getHexBytes(String value) throws UnsupportedEncodingException {
        int len = value.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(value.charAt(i), 16) << 4)
                    + Character.digit(value.charAt(i + 1), 16));
        }
        return data;
    }
}