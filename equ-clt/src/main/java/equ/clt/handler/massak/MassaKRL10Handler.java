package equ.clt.handler.massak;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.scales.ScalesHandler;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.EquipmentServer;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static equ.clt.handler.HandlerUtils.trim;

public class MassaKRL10Handler extends ScalesHandler {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");

    private static String logPrefix = "MassaKRL10: ";

    byte notSnapshotByte = (byte) 51;
    byte snapshotByte = (byte) 1;

    protected FileSystemXmlApplicationContext springContext;

    public MassaKRL10Handler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) throws IOException {
        return "MassaKRL10";
    }

    @Override
    public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionInfoList) throws IOException {
        Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        Map<String, String> brokenPortsMap = new HashMap<>();
        if (transactionInfoList.isEmpty()) {
            processTransactionLogger.error(logPrefix + "Empty transaction list!");
        }
        for (TransactionScalesInfo transaction : transactionInfoList) {
            processTransactionLogger.info(logPrefix + "Send Transaction # " + transaction.id);

            List<MachineryInfo> succeededScalesList = new ArrayList<>();
            List<MachineryInfo> clearedScalesList = new ArrayList<>();
            Exception exception = null;
            try {

                if (!transaction.machineryInfoList.isEmpty()) {

                    List<ScalesInfo> enabledScalesList = getEnabledScalesList(transaction, succeededScalesList);
                    Map<String, List<String>> errors = new HashMap<>();
                    Set<String> ips = new HashSet<>();

                    processTransactionLogger.info(logPrefix + "Starting sending to " + enabledScalesList.size() + " scales...");
                    Collection<Callable<SendTransactionResult>> taskList = new LinkedList<>();
                    for (ScalesInfo scales : enabledScalesList) {
                        TCPPort port = new TCPPort(scales.port, 5001);
                        if (scales.port != null) {
                            String brokenPortError = brokenPortsMap.get(scales.port);
                            if (brokenPortError != null) {
                                errors.put(scales.port, Collections.singletonList(String.format("Broken ip: %s, error: %s", scales.port, brokenPortError)));
                            } else {
                                ips.add(scales.port);
                                taskList.add(new SendTransactionTask(transaction, scales, port));
                            }
                        }
                    }

                    if (!taskList.isEmpty()) {
                        ExecutorService singleTransactionExecutor = EquipmentServer.getFixedThreadPool(taskList.size(), "MassaKRL10SendTransaction");
                        List<Future<SendTransactionResult>> threadResults = singleTransactionExecutor.invokeAll(taskList);
                        for (Future<SendTransactionResult> threadResult : threadResults) {
                            if (threadResult.get().localErrors.isEmpty())
                                succeededScalesList.add(threadResult.get().scalesInfo);
                            else {
                                brokenPortsMap.put(threadResult.get().scalesInfo.port, threadResult.get().localErrors.get(0));
                                errors.put(threadResult.get().scalesInfo.port, threadResult.get().localErrors);
                            }
                            if (threadResult.get().cleared)
                                clearedScalesList.add(threadResult.get().scalesInfo);
                        }
                        singleTransactionExecutor.shutdown();
                    }
                    if (!enabledScalesList.isEmpty())
                        errorMessages(errors, ips, brokenPortsMap);

                }
            } catch (Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(clearedScalesList, succeededScalesList, exception));
        }
        return sendTransactionBatchMap;
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {
    }

    private String openPort(List<String> errors, TCPPort port, String ip, boolean transaction) {
        try {
            (transaction ? processTransactionLogger : processStopListLogger).info(logPrefix + "Connecting..." + ip);
            port.open();

            sendSetWorkMode(port);
            if(!getSetWorkModeReply(errors, port, ip))
                return "SetWorkMode failed";
        } catch (Exception e) {
            (transaction ? processTransactionLogger : processStopListLogger).error("Error: ", e);
            return e.getMessage();
        }
        return null;
    }

    private void reopenPort(TCPPort port) throws CommunicationException {
        port.close();
        port.open();
    }

    private void sendSetWorkMode(TCPPort port) throws IOException {
            clearReceiveBuffer(port);

            ByteBuffer bytes = ByteBuffer.allocate(9);

            //header, 3 bytes
            bytes.put(new byte[]{(byte) 0xF8, (byte) 0x55, (byte) 0xCE});

            //Len, 2 bytes
            bytes.putShort((short) 1);

            //CMD_TCP_SET_WORK_MODE
            bytes.put((byte) 0x91);

            //Mode (constant)
            bytes.put((byte) 0x04);

            bytes.putShort((short) getCRC16(bytes.array()));

            //Hex.encodeHexString(bytes.array());

            port.getOutputStream().write(bytes.array());
            port.getOutputStream().flush();
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

    protected void errorMessages(Map<String, List<String>> errors, Set<String> ips, Map<String, String> brokenPortsMap) {
        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder();
            for (Map.Entry<String, List<String>> entry : errors.entrySet()) {
                message.append(entry.getKey()).append(": \n");
                for (String error : entry.getValue()) {
                    message.append(error).append("\n");
                }
            }
            throw new RuntimeException(message.toString());
        } else if (ips.isEmpty() && brokenPortsMap.isEmpty())
            throw new RuntimeException(logPrefix + "No IP-addresses defined");
    }

    private byte[] receiveReply(List<String> errors, TCPPort port, String ip) throws CommunicationException {
        byte[] reply = new byte[500];
        try {
            long startTime = new Date().getTime();
            long time;
            do {
                if (port.getBisStream().available() != 0) {
                    port.getBisStream().read(reply);
                    break;
                }
                Thread.sleep(10L);
                time = (new Date()).getTime();
            } while (time - startTime <= 10000L); //10 seconds
        } catch (Exception e) {
            logError(errors, String.format(logPrefix + "IP %s receive Reply Error", ip), e);
        }

        return reply;
    }

    private boolean getResetPluFileReply(List<String> errors, TCPPort port, String ip) throws CommunicationException {
        boolean cleared = false;
        try {
            byte[] reply = receiveReply(errors, port, ip);
            if (reply != null) {
                DataInputStream stream = new DataInputStream(new ByteArrayInputStream(reply));
                byte byte0 = stream.readByte();
                byte byte1 = stream.readByte();
                byte byte2 = stream.readByte();
                if (byte0 == (byte) 0xF8 && byte1 == (byte) 0x55 && byte2 == (byte) 0xCE) {
                    short length = stream.readShort();
                    byte code = stream.readByte();
                    if (code == (byte) 0x41) {
                        int maskFile = stream.readInt();
                        if (maskFile == 16777216) { //из-за little endian, на самом деле это 1
                            cleared = true;
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return cleared;
    }

    private void clearReceiveBuffer(TCPPort port) {
        while (true) {
            try {
                if (port.getBisStream().available() > 0) {
                    port.getBisStream().read();
                    continue;
                }
            } catch (Exception ignored) {
            }
            return;
        }
    }

    private Integer getPluNumber(ItemInfo itemInfo) {
        try {
            return itemInfo.pluNumber != null ? itemInfo.pluNumber : Integer.parseInt(itemInfo.idBarcode);
        } catch (Exception e) {
            return 0;
        }
    }

    private void sendPlu(List<String> errors, TCPPort port, byte[] command, boolean snapshot) throws CommunicationException, IOException, DecoderException {
        sendPlu(errors, port, command, (short) 1, (short) 1, snapshot);
    }

    private void sendPlu(List<String> errors, TCPPort port, byte[] command, short current, short total, boolean snapshot) throws CommunicationException, IOException, DecoderException {
        try {

            ByteBuffer bytes = ByteBuffer.allocate(15 + command.length);
            bytes.order(ByteOrder.LITTLE_ENDIAN);

            //header, 3 bytes
            bytes.put(new byte[]{(byte) 0xF8, (byte) 0x55, (byte) 0xCE});

            //Len, 2 bytes
            bytes.putShort((short) (command.length + 8));

            //CMD_TCP_DFILE
            bytes.put((byte) 0x82);

            //fileType PLU
            bytes.put(snapshot ? snapshotByte : notSnapshotByte); // 1 = загрузка plu, 51 = дозагрузка plu

            //Nums - кол-во записей, 2 bytes
            bytes.putShort(total);

            //Номер текущей записи, 2 bytes
            bytes.putShort(current);

            //Длина текущей записи, 2 bytes
            bytes.putShort((short) command.length);

            bytes.put(command);

            bytes.putShort((short) getCRC16(bytes.array()));

            port.getOutputStream().write(bytes.array());
        } catch (IOException e) {
            logError(errors, String.format(logPrefix + "%s Send command exception: ", port.getAddress()), e);
        } finally {
            port.getOutputStream().flush();
        }
    }

    private void resetPluFile(List<String> errors, TCPPort port) throws CommunicationException, IOException {
        try {

            ByteBuffer bytes = ByteBuffer.allocate(12);
            bytes.order(ByteOrder.LITTLE_ENDIAN);

            //header, 3 bytes
            bytes.put(new byte[]{(byte) 0xF8, (byte) 0x55, (byte) 0xCE});

            //Len, 2 bytes
            bytes.putShort((short) 5);

            //CMD_TCP_RESET_FILES
            bytes.put((byte) 0x81);

            //MaskFile
            bytes.putInt(1);

            bytes.putShort((short) getCRC16(bytes.array()));

            port.getOutputStream().write(bytes.array());
            port.getOutputStream().flush();
        } catch (IOException e) {
            logError(errors, String.format(logPrefix + "%s Send command exception: ", port.getAddress()), e);
        }
    }

    private int getCRC16(byte[] bytes) {

        int crc = 0xFFFF;          // initial value
        int polynomial = 0x1021;   // 0001 0000 0010 0001  (0, 5, 12)

        for (byte b : bytes) {
            for (int i = 0; i < 8; i++) {
                boolean bit = ((b >> (7 - i) & 1) == 1);
                boolean c15 = ((crc >> 15 & 1) == 1);
                crc <<= 1;
                if (c15 ^ bit) crc ^= polynomial;
            }
        }
        crc &= 0xffff;
        return crc;
    }


    protected boolean clearAll(List<String> errors, TCPPort port, ScalesInfo scales) throws InterruptedException, IOException, CommunicationException {
        processTransactionLogger.info(String.format(logPrefix + "IP %s ClearAllPLU", scales.port));
        resetPluFile(errors, port);
        boolean cleared = getResetPluFileReply(errors, port, scales.port);
        if (!cleared)
            logError(errors, String.format(logPrefix + "IP %s ClearAllPLU failed", scales.port));
        return cleared;
    }

    private boolean loadAllPLU(List<String> errors, TCPPort port, List<byte[]> bytes, boolean snapshot) throws CommunicationException, IOException, DecoderException {
        clearReceiveBuffer(port);
        for(short i = 1; i <= bytes.size(); i++) {
            sendPlu(errors, port, bytes.get(i - 1), i, (short) bytes.size(), snapshot);
        }
        return getLoadPluReply(errors, port, port.getAddress(), snapshot);
    }

    private boolean loadPLU(List<String> errors, TCPPort port, ScalesItemInfo item, boolean first) throws CommunicationException, IOException, DecoderException {
        byte[] bytes = getPLUBytes(item, first);
        clearReceiveBuffer(port);
        sendPlu(errors, port, bytes, false);
        return getLoadPluReply(errors, port, port.getAddress(), false);
    }

    private byte[] getPLUBytes(ScalesItemInfo item, boolean first) throws DecoderException {
        int pluNumber = getPluNumber(item);

        byte[] firstBytes = first ? getBytes("01PC0000000001") : new byte[0]; //01PC0000000000 ?
        byte[] nameBytes = toAscii(trim(item.name, "", 248));
        byte[] descriptionBytes = toAscii(trim(item.description, "", 998));

        String idItem = trim(item.idBarcode, 15);

        int length = 37 + idItem.length() + firstBytes.length + nameBytes.length + descriptionBytes.length;
        ByteBuffer bytes = ByteBuffer.allocate(length);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        bytes.put(firstBytes);

        //ID - Идентификатор товара, уникальное значение, 4 bytes
        bytes.putInt(pluNumber);

        //Length - Длина записи, 2 bytes
        bytes.putShort((short) (length - 6));

        // DigLength - Длина числовых данных, 1 byte
        bytes.put((byte) (length - 7 - firstBytes.length - nameBytes.length - descriptionBytes.length));

        String uom = fillSpaces(item.idUOM, 5);

        //BitMask - Битовая маска, 4 bytes
        //Если параметры №5 – 17 равны нулю, они не записываются в файл, соответствующий бит в поле BitMask устанавливается в ноль.
        //1101001001110100
        //todo: 256 штучный
        int bitMask = 53872 + idItem.length(); //16 BasicUnit, 32 Price, 64 TareWeight, 512 GoodsGroupCode, 4096 BestBefore, 16384 CertificationCode, 32768 BarcodePrefix

        bytes.putInt(bitMask);

        //Code - Код товара, до 15 bytes
        bytes.put(getBytes(idItem));

        //BasicUnit - Базовая ед. измерения, 5 bytes
        bytes.put(getBytes(uom));

        //Price - Цена в копейках, 4 bytes
        int price = item.price.multiply(BigDecimal.valueOf(100)).intValue();
        bytes.putInt(price);

        //TarеWeight - Вес тары в граммах, 4 bytes
        bytes.putInt(0);

        // GoodsTypeID - Тип товара 0 весовой, 1 штучный, 1 byte
        //bytes.put((byte) (item.splitItem ? 1 : 0));

        //GoodsGroupCode Код группы товаров, 2 bytes
        short idItemGroup = item.idItemGroup == null ? 0 : Short.parseShort(item.idItemGroup);
        bytes.putShort(idItemGroup);

        //BestBefore - Дата реализации, 6 bytes
        bytes.put(((byte) 0x00)); //1-ый – ГГ (год 00 ≤ ГГ ≤ 99) 75 = 117
        bytes.put(((byte) 0x00)); //2-ой – ММ (месяц 1 ≤ ММ ≤ 12) 05
        bytes.put(((byte) 0x00)); //3-ий – ДД (день 1≤ ДД ≤31) 1E
        bytes.put(((byte) 0x00)); //4-ый – ЧЧ (часы 0 ≤ ЧЧ <24)
        bytes.put(((byte) 0x00)); //5-ый – ММ (минуты 0 ≤ ММ < 60)
        bytes.put(((byte) 0x00)); //6-ой байт – СС (секунды 0≤ СС <60)

        //CertificationCode - Код сертификации, 4 bytes
        bytes.put((byte)0xc0);
        bytes.put((byte)0xdf);
        bytes.put((byte)0x34);
        bytes.put((byte)0x35);

        //BarcodePrefix - Префикс штрихкода, 1 byte
        byte prefix = 0x17;//Integer.parseInt(item.idItem.substring(0, 2));
        bytes.put(prefix);

        //Name - Наименование товара, 2-250 bytes
        bytes.put(nameBytes);

        //Ingredients - Состав, 2-1000 bytes
        bytes.put(descriptionBytes);

        return bytes.array();
    }

    private boolean getSetWorkModeReply(List<String> errors, TCPPort port, String ip) throws CommunicationException {
        boolean result = false;
        byte[] reply = receiveReply(errors, port, ip);
        if (reply != null) {
            try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(reply))) {
                byte byte0 = stream.readByte();
                byte byte1 = stream.readByte();
                byte byte2 = stream.readByte();
                if (byte0 == (byte) 0xF8 && byte1 == (byte) 0x55 && byte2 == (byte) 0xCE) {
                    byte lengthByte1 = stream.readByte();
                    byte lengthByte2 = stream.readByte();
                    byte code = stream.readByte();
                    if (code == (byte) 0x51) { //51 ok, 54 error
                        result = true;
                    }
                }
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
        return result;
    }

    private boolean getLoadPluReply(List<String> errors, TCPPort port, String ip, boolean snapshot) throws CommunicationException {
        boolean result = false;
        byte[] reply = receiveReply(errors, port, ip);
        if (reply != null) {
            try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(reply))) {
                byte byte0 = stream.readByte();
                byte byte1 = stream.readByte();
                byte byte2 = stream.readByte();
                if (byte0 == (byte) 0xF8 && byte1 == (byte) 0x55 && byte2 == (byte) 0xCE) {
                    byte lengthByte1 = stream.readByte();
                    byte lengthByte2 = stream.readByte();
                    byte code = stream.readByte();
                    if (code == (byte) 0x42) { //42 ok, 43 error
                        int fileType = stream.read();
                        if (fileType == (snapshot ? snapshotByte : notSnapshotByte)) {
                            result = true;
                        }
                    }
                }
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
        return result;
    }

    protected void logError(List<String> errors, String errorText) {
        logError(errors, errorText, null);
    }

    protected void logError(List<String> errors, String errorText, Throwable t) {
        errors.add(errorText.replace("\u001b", "").replace("\u0000", "") + (t == null ? "" : ('\n' + t.toString())));
        processTransactionLogger.error(errorText, t);
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoList) throws IOException {
        try {
            if (!stopListInfo.stopListItemMap.isEmpty() && !stopListInfo.exclude) {
                processStopListLogger.info(logPrefix + "Starting sending StopLists to " + machineryInfoList.size() + " scales...");
                Collection<Callable<List<String>>> taskList = new LinkedList<>();
                for (MachineryInfo machinery : machineryInfoList) {
                    TCPPort port = new TCPPort(machinery.port, 1025);
                    if (machinery.port != null && machinery instanceof ScalesInfo) {
                        taskList.add(new SendStopListTask(stopListInfo, (ScalesInfo) machinery, port));
                    }
                }

                if (!taskList.isEmpty()) {
                    ExecutorService singleTransactionExecutor = EquipmentServer.getFixedThreadPool(taskList.size(), "MassaKRL10SendStopList");
                    List<Future<List<String>>> threadResults = singleTransactionExecutor.invokeAll(taskList);
                    for (Future<List<String>> threadResult : threadResults) {
                        if (!threadResult.get().isEmpty())
                            processStopListLogger.error(threadResult.get().get(0));
                        //throw new RuntimeException(threadResult.get().get(0));
                    }
                    singleTransactionExecutor.shutdown();
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    class SendTransactionTask implements Callable<SendTransactionResult> {
        TransactionScalesInfo transaction;
        ScalesInfo scales;
        TCPPort port;

        public SendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales, TCPPort port) {
            this.transaction = transaction;
            this.scales = scales;
            this.port = port;
        }

        @Override
        public SendTransactionResult call() throws Exception {
            List<String> localErrors = new ArrayList<>();
            boolean cleared = false;
            String openPortResult = openPort(localErrors, port, scales.port, true);
            if (openPortResult != null) {
                localErrors.add(openPortResult + ", transaction: " + transaction.id + ";");
            } else {

                int globalError = 0;
                try {
                    boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
                    if (needToClear) {
                        cleared = clearAll(localErrors, port, scales);
                    }

                    if (cleared || !needToClear) {
                        processTransactionLogger.info(logPrefix + "Sending items..." + scales.port);
                        if (localErrors.isEmpty()) {
                            //if (transaction.snapshot) {
                                int count = 0;
                                List<byte[]> bytes = new ArrayList<>();
                                for (ScalesItemInfo item : transaction.itemsList) {
                                    count++;
                                    if (item.idBarcode != null && item.idBarcode.length() <= 5) {
                                        processTransactionLogger.info(String.format(logPrefix + "IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
                                        byte[] pluBytes = getPLUBytes(item, count == 1);
                                        bytes.add(pluBytes);
                                    } else {
                                        processTransactionLogger.info(String.format(logPrefix + "IP %s, Transaction #%s, item #%s: incorrect barcode %s", scales.port, transaction.id, count, item.idBarcode));
                                    }
                                }
                                boolean result = loadAllPLU(localErrors, port, bytes, transaction.snapshot);
                                if (!result) {
                                    logError(localErrors, String.format(logPrefix + "IP %s, Result %s", scales.port, false));
                                }
                            /*} else {
                                int count = 0;
                                for (ScalesItemInfo item : transaction.itemsList) {
                                    count++;
                                    if (!Thread.currentThread().isInterrupted() && globalError < 5) {
                                        if (item.idBarcode != null && item.idBarcode.length() <= 5) {
                                            processTransactionLogger.info(String.format(logPrefix + "IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
                                            int attempts = 0;
                                            boolean result = false;
                                            while (!result && attempts < 3) {
                                                reopenPort(port);
                                                result = loadPLU(localErrors, port, item, count == 1);
                                                attempts++;
                                            }
                                            if (!result) {
                                                logError(localErrors, String.format(logPrefix + "IP %s, Result %s, item %s", scales.port, result, item.idItem));
                                                globalError++;
                                            }
                                        } else {
                                            processTransactionLogger.info(String.format(logPrefix + "IP %s, Transaction #%s, item #%s: incorrect barcode %s", scales.port, transaction.id, count, item.idBarcode));
                                        }
                                    } else break;
                                }
                            }*/
                        }
                        port.close();
                    }

                } catch (Exception e) {
                    logError(localErrors, String.format(logPrefix + "IP %s error, transaction %s;", scales.port, transaction.id), e);
                } finally {
                    processTransactionLogger.info(logPrefix + "Finally disconnecting..." + scales.port);
                    try {
                        port.close();
                    } catch (CommunicationException e) {
                        logError(localErrors, String.format(logPrefix + "IP %s close port error ", scales.port), e);
                    }
                }
            }
            processTransactionLogger.info(logPrefix + "Completed ip: " + scales.port);
            return new SendTransactionResult(scales, localErrors, cleared);
        }

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

    class SendStopListTask implements Callable<List<String>> {
        StopListInfo stopListInfo;
        ScalesInfo scales;
        TCPPort port;

        public SendStopListTask(StopListInfo stopListInfo, ScalesInfo scales, TCPPort port) {
            this.stopListInfo = stopListInfo;
            this.scales = scales;
            this.port = port;
        }

        @Override
        public List<String> call() throws Exception {
            List<String> localErrors = new ArrayList<>();
            String openPortResult = openPort(localErrors, port, scales.port, false);
            if (openPortResult != null) {
                localErrors.add(openPortResult);
            } else {
                int globalError = 0;
                try {

                    processStopListLogger.info(logPrefix + "Sending StopLists..." + scales.port);
                    if (localErrors.isEmpty()) {
                        int count = 0;
                        for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                            count++;
                            if (!Thread.currentThread().isInterrupted() && globalError < 5) {
                                if (item.idBarcode != null && item.idBarcode.length() <= 5) {
                                    if (!skip(item.idItem)) {
                                        processStopListLogger.info(String.format(logPrefix + "IP %s, sending StopList for item #%s (barcode %s) of %s", scales.port, count, item.idBarcode, stopListInfo.stopListItemMap.values().size()));
                                        String result = "0";//clearPLU(localErrors, port, scales, item);
                                        if (!result.equals("0")) {
                                            logError(localErrors, String.format(logPrefix + "IP %s, Result %s, item %s", scales.port, result, item.idItem));
                                            globalError++;
                                        }
                                    }
                                } else {
                                    processStopListLogger.info(String.format(logPrefix + "IP %s, item #%s: incorrect barcode %s", scales.port, count, item.idBarcode));
                                }
                            } else break;
                        }
                    }
                    port.close();

                } catch (Exception e) {
                    logError(localErrors, String.format(logPrefix + "IP %s error ", scales.port), e);
                } finally {
                    processStopListLogger.info(logPrefix + "Finally disconnecting..." + scales.port);
                    try {
                        port.close();
                    } catch (CommunicationException e) {
                        logError(localErrors, String.format(logPrefix + "IP %s close port error ", scales.port), e);
                    }
                }
            }
            processStopListLogger.info(logPrefix + "Completed ip: " + scales.port);
            return localErrors;
        }

        private boolean skip(String idItem) {
            Set<String> skuSet = stopListInfo.inGroupMachineryItemMap.get(scales.numberGroup);
            return skuSet == null || !skuSet.contains(idItem);
        }

    }

    private byte[] getBytes(String value) {
        return value.getBytes(Charset.forName("cp1251"));
    }

    private byte[] toAscii(String text) {
        if(text == null)
            text = "";
        text = text.replace("\n", "|");
        ByteBuffer bytes = ByteBuffer.allocate(text.length() + 2);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.putShort((short) (text.length()/* - lines*/));
        bytes.put(text.getBytes(Charset.forName("cp1251")));
        return bytes.array();
    }

    private String fillSpaces(String value, int length) {
        if(value == null)
            value = "";
        if (value.length() > length)
            value = value.substring(0, length);
        while (value.length() < length)
            value += " ";
        return value;
    }
}