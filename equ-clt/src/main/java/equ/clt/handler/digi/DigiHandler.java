package equ.clt.handler.digi;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.EquipmentServer;
import equ.clt.handler.DefaultScalesHandler;
import lsfusion.base.ExceptionUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class DigiHandler extends DefaultScalesHandler {

    private final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    
    protected static short cmdWrite = 0xF1;
    protected static short cmdCls = 0xF2;
    protected static short filePLU = 0x25;
    protected static short fileIngredient = 0x3A;
    protected static short fileKeyAssignment = 0x41;

    //включить для вывода в лог отправляемых запросов
    private boolean debugMode = false;

    protected FileSystemXmlApplicationContext springContext;

    public DigiHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    protected String getLogPrefix() {
        return "Digi SM300: ";
    }

    protected Integer getMaxCompositionLinesCount() {
        return 9;
    }
    
    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) {
        StringBuilder groupId = new StringBuilder();
        for (MachineryInfo scales : transactionInfo.machineryInfoList) {
            groupId.append(scales.port).append(";");
        }
        return getLogPrefix() + groupId;
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) throws IOException {


        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        Map<String, String> brokenPortsMap = new HashMap<>();
        if(transactionList.isEmpty()) {
            processTransactionLogger.error(getLogPrefix() + "Empty transaction list!");
        }
        for(TransactionScalesInfo transaction : transactionList) {
            processTransactionLogger.info(getLogPrefix() + "Send Transaction # " + transaction.id);

            DigiSettings digiSettings = springContext.containsBean("digiSettings") ? (DigiSettings) springContext.getBean("digiSettings") : null;
            Integer maxLineLength = digiSettings != null ? digiSettings.getMaxLineLength() : null;
            maxLineLength = maxLineLength == null ? 50 : maxLineLength;
            Integer maxNameLength = digiSettings != null ? digiSettings.getMaxNameLength() : null;

            List<MachineryInfo> succeededScalesList = new ArrayList<>();
            List<MachineryInfo> clearedScalesList = new ArrayList<>();
            Exception exception = null;
            try {

                if (!transaction.machineryInfoList.isEmpty()) {

                    List<ScalesInfo> enabledScalesList = getEnabledScalesList(transaction, succeededScalesList);
                    Map<String, List<String>> errors = new HashMap<>();
                    Set<String> ips = new HashSet<>();

                    processTransactionLogger.info(getLogPrefix() + "Starting sending to " + enabledScalesList.size() + " scales...");
                    Collection<Callable<SendTransactionResult>> taskList = new LinkedList<>();
                    for (ScalesInfo scales : enabledScalesList) {
                        if (scales.port != null) {
                            String brokenPortError = brokenPortsMap.get(scales.port);
                            if(brokenPortError != null) {
                                errors.put(scales.port, Collections.singletonList(String.format("Broken ip: %s, error: %s", scales.port, brokenPortError)));
                            } else {
                                ips.add(scales.port);
                                taskList.add(new SendTransactionTask(transaction, scales, maxLineLength, maxNameLength));
                            }
                        }
                    }

                    if(!taskList.isEmpty()) {
                        ExecutorService singleTransactionExecutor = EquipmentServer.getFixedThreadPool(taskList.size(), "DigiSendTransaction");
                        List<Future<SendTransactionResult>> threadResults = singleTransactionExecutor.invokeAll(taskList);
                        for (Future<SendTransactionResult> threadResult : threadResults) {
                            if(threadResult.get().localErrors.isEmpty())
                                succeededScalesList.add(threadResult.get().scalesInfo);
                            else {
                                brokenPortsMap.put(threadResult.get().scalesInfo.port, threadResult.get().localErrors.get(0));
                                errors.put(threadResult.get().scalesInfo.port, threadResult.get().localErrors);
                            }
                            if(threadResult.get().cleared)
                                clearedScalesList.add(threadResult.get().scalesInfo);
                        }
                        singleTransactionExecutor.shutdown();
                    }
                    if(!enabledScalesList.isEmpty())
                        errorMessages(errors, ips, brokenPortsMap);

                }
            } catch (Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(clearedScalesList, succeededScalesList, exception));
        }
        return sendTransactionBatchMap;
    }

    protected boolean clearFile(DataSocket socket, List<String> localErrors, String port, short file) throws IOException {
        processTransactionLogger.info(getLogPrefix() + String.format("Deleting file %s at scales %s", file, port));
        int reply = sendRecord(socket, cmdCls, file, new byte[0]);
        if (reply != 0)
            logError(localErrors, String.format("Deleting file %s at scales %s failed. Error: %s\n", file, port, reply));
        return reply == 0;
    }

    protected void errorMessages(Map<String, List<String>> errors, Set<String> ips, Map<String, String> brokenPortsMap) {
        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder();
            for (Map.Entry<String, List<String>> entry : errors.entrySet()) {
                message.append(entry.getKey()).append(": \n");
                for (String error : entry.getValue()) {
                    message.append(error).append("\n");
                }
            }
            throw new RuntimeException(getLogPrefix() + message.toString());
        } else if (ips.isEmpty() && brokenPortsMap.isEmpty())
            throw new RuntimeException(getLogPrefix() + "No IP-addresses defined");
    }

    private byte[] makeRecord(ScalesItemInfo item, String weightCode, String pieceCode, Integer maxLineLength, Integer maxNameLength) throws UnsupportedEncodingException {
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

        Integer maxCompositionLinesCount = getMaxCompositionLinesCount();
        if(maxCompositionLinesCount != null && compositionLines.size() > maxCompositionLinesCount)
            compositionLines = compositionLines.subList(0, maxCompositionLinesCount);

        int itemNameLength = maxNameLength != null ? Math.min(item.name.length(), maxNameLength) : item.name.length();
        String itemName = item.name.substring(0, itemNameLength);

        int length = 36 + itemNameLength +
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
        bytes.put((byte) itemNameLength);

        // Наименование товара
        bytes.put(getBytes(itemName));

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

    private String fillTrailingZeroes(String input, int length) {
        if (input == null)
            return null;
        while (input.length() < length)
            input = input + "0";
        return input;
    }

    protected int sendRecord(DataSocket socket, short cmd, short file, byte[] record) throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(record.length + 2);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) cmd);
        bytes.put((byte) file);
        bytes.put(record);
        return sendCommand(socket, bytes.array());
    }

    protected int sendCommand(DataSocket socket, byte[] bytes) throws IOException {
        int attempts = 0;
        while (attempts < 3) {
            try {
                if(debugMode)
                    processTransactionLogger.info(Hex.encodeHexString(bytes));
                socket.outputStream.write(bytes);
                socket.outputStream.flush();
                return receiveReply(socket);
            } catch (CommunicationException e) {
                attempts++;
                if (attempts == 3)
                    processTransactionLogger.error(getLogPrefix() + "SendCommand Error: ", e);
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
            processTransactionLogger.error(getLogPrefix() + "ReceiveReply Error: ", e);
            return -1;
        }
    }

    protected List<ScalesInfo> getEnabledScalesList(TransactionScalesInfo transaction, List<MachineryInfo> succeededScalesList) {
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

    private String getWeightCode(MachineryInfo scales) {
        String weightCode = scales instanceof ScalesInfo ? ((ScalesInfo) scales).weightCodeGroupScales : null;
        return weightCode == null ? "21" : weightCode;
    }

    private String getPieceCode(MachineryInfo scales) {
        return scales instanceof ScalesInfo ? ((ScalesInfo) scales).pieceCodeGroupScales : null;
    }

    protected byte[] getBytes(String value) throws UnsupportedEncodingException {
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

    class SendTransactionTask implements Callable<SendTransactionResult> {
        TransactionScalesInfo transaction;
        ScalesInfo scales;
        int maxLineLength;
        Integer maxNameLength;

        public SendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales, int maxLineLength, Integer maxNameLength) {
            this.transaction = transaction;
            this.scales = scales;
            this.maxLineLength = maxLineLength;
            this.maxNameLength = maxNameLength;
        }

        @Override
        public SendTransactionResult call() throws Exception {
            List<String> localErrors = new ArrayList<>();
            boolean cleared = false;
            DataSocket socket = new DataSocket(scales.port);
            try {
                socket.open();
                int globalError = 0;
                boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
                if (needToClear)
                    cleared = clearFile(socket, localErrors, scales.port, filePLU);

                if (cleared || !needToClear) {
                    processTransactionLogger.info(getLogPrefix() + "Sending items..." + scales.port);
                    if (localErrors.isEmpty()) {
                        int count = 0;
                        for (ScalesItemInfo item : transaction.itemsList) {
                            count++;
                            if (!Thread.currentThread().isInterrupted() && globalError < 3) {
                                processTransactionLogger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
                                int barcode = Integer.parseInt(item.idBarcode.substring(0, 5));
                                int pluNumber = item.pluNumber == null ? barcode : item.pluNumber;
                                if(item.idBarcode.length() <= 5) {
                                    byte[] record = makeRecord(item, getWeightCode(scales), getPieceCode(scales), maxLineLength, maxNameLength);
                                    processTransactionLogger.info(String.format(getLogPrefix() + "Sending item %s to scales %s", pluNumber, scales.port));
                                    int reply = sendRecord(socket, cmdWrite, filePLU, record);
                                    if (reply != 0) {
                                        logError(localErrors, String.format(getLogPrefix() + "Send item %s to scales %s failed. Error: %s", pluNumber, scales.port, reply));
                                        globalError++;
                                    }
                                } else {
                                    processTransactionLogger.info(String.format(getLogPrefix() + "Sending item %s to scales %s failed: incorrect barcode %s", pluNumber, scales.port, item.idBarcode));
                                }
                            } else break;
                        }
                    }
                    socket.close();
                }

            } catch (Exception e) {
                logError(localErrors, String.format(getLogPrefix() + "IP %s error, transaction %s;", scales.port, transaction.id), e);
            } finally {
                processTransactionLogger.info(getLogPrefix() + "Finally disconnecting..." + scales.port);
                try {
                    socket.close();
                } catch (CommunicationException e) {
                    logError(localErrors, String.format(getLogPrefix() + "IP %s close port error ", scales.port), e);
                }
            }
            processTransactionLogger.info(getLogPrefix() + "Completed ip: " + scales.port);
            return new SendTransactionResult(scales, localErrors, cleared);
        }
    }

    protected void logError(List<String> errors, String errorText) {
        logError(errors, errorText, null);
    }

    protected void logError(List<String> errors, String errorText, Throwable t) {
        errors.add(errorText + (t == null ? "" : ('\n' + ExceptionUtils.getStackTraceString(t))));
        processTransactionLogger.error(errorText, t);
    }

    class SendTransactionResult {
        public ScalesInfo scalesInfo;
        public List<String> localErrors;
        public boolean cleared;

        public SendTransactionResult(ScalesInfo scalesInfo, List<String> localErrors, boolean cleared) {
            this.scalesInfo = scalesInfo;
            this.localErrors = localErrors;
            this.cleared = cleared;
        }
    }
}