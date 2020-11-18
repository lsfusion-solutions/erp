package equ.clt.handler.massak;

import com.google.common.base.Throwables;
import com.google.common.io.LittleEndianDataInputStream;
import equ.api.ItemInfo;
import equ.api.MachineryInfo;
import equ.api.stoplist.StopListInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.handler.MultithreadScalesHandler;
import equ.clt.handler.TCPPort;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.Pair;
import org.apache.commons.codec.binary.Hex;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static equ.clt.handler.HandlerUtils.trim;

public class MassaKRL10Handler extends MultithreadScalesHandler {

    byte notSnapshotItemByte = (byte) 101;
    byte snapshotItemByte = (byte) 1;
    byte notSnapshotPluByte = (byte) 105;
    byte snapshotPluByte = (byte) 5;

    //включить для вывода в лог отправляемых запросов
    private boolean debugMode = false;

    protected FileSystemXmlApplicationContext springContext;

    public MassaKRL10Handler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    @Override
    protected String getLogPrefix() {
        return "MassaKRL10: ";
    }

    private String openPort(List<String> errors, TCPPort port, String ip) {
        try {
            processTransactionLogger.info(getLogPrefix() + "Connecting..." + ip);
            port.open();

            sendSetWorkMode(port);
            if (!getSetWorkModeReply(errors, port, ip))
                return "SetWorkMode failed";
        } catch (Exception e) {
            processTransactionLogger.error("Error: ", e);
            return e.getMessage();
        }
        return null;
    }

    private void reopenPort(TCPPort port) throws CommunicationException, IOException {
        port.close();
        port.open();
        sendSetWorkMode(port);
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

        if(debugMode)
            processTransactionLogger.info("SetWorkMode: " + Hex.encodeHexString(bytes.array()));
        port.getOutputStream().write(bytes.array());
        port.getOutputStream().flush();
    }

    private boolean getSetWorkModeReply(List<String> errors, TCPPort port, String ip) {
        boolean result = false;
        byte[] reply = receiveReply(errors, port, ip);
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
        return result;
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

    private byte[] receiveReply(List<String> errors, TCPPort port, String ip) {
        byte[] reply = new byte[500];
        try {
            long startTime = System.currentTimeMillis();
            long time;
            do {
                if (port.getBisStream().available() != 0) {
                    port.getBisStream().read(reply);
                    break;
                }
                Thread.sleep(10L);
                time = System.currentTimeMillis();
            } while (time - startTime <= 10000L); //10 seconds
        } catch (Exception e) {
            logError(errors, String.format(getLogPrefix() + "IP %s receive Reply Error", ip), e);
        }

        return reply;
    }

    private boolean getResetFilesReply(List<String> errors, TCPPort port, String ip) {
        boolean cleared = false;
        try {
            byte[] reply = receiveReply(errors, port, ip);
            LittleEndianDataInputStream stream = new LittleEndianDataInputStream(new ByteArrayInputStream(reply));
            byte byte0 = stream.readByte();
            byte byte1 = stream.readByte();
            byte byte2 = stream.readByte();
            if (byte0 == (byte) 0xF8 && byte1 == (byte) 0x55 && byte2 == (byte) 0xCE) {
                short length = stream.readShort();
                byte code = stream.readByte();
                if (code == (byte) 0x41) {
                    int maskFile = stream.readInt();
                    if (maskFile == 17) {
                        cleared = true;
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

    private void sendCommand(List<String> errors, TCPPort port, byte[] command, short current, short total, byte commandFileType) throws IOException {
        try {

            ByteBuffer bytes = ByteBuffer.allocate(15 + command.length);
            bytes.order(ByteOrder.LITTLE_ENDIAN);

            //header, 3 bytes
            bytes.put(new byte[]{(byte) 0xF8, (byte) 0x55, (byte) 0xCE});

            //Len, 2 bytes
            bytes.putShort((short) (command.length + 8));

            //CMD_TCP_DFILE
            bytes.put((byte) 0x82);

            //fileType Item
            bytes.put(commandFileType);

            //Nums - кол-во записей, 2 bytes
            bytes.putShort(total);

            //Номер текущей записи, 2 bytes
            bytes.putShort(current);

            //Длина текущей записи, 2 bytes
            bytes.putShort((short) command.length);

            bytes.put(command);

            bytes.putShort((short) getCRC16(bytes.array()));

            if(debugMode)
                processTransactionLogger.info("Send File: " + Hex.encodeHexString(bytes.array()));

            port.getOutputStream().write(bytes.array());
        } catch (IOException e) {
            logError(errors, String.format(getLogPrefix() + "%s Send command exception: ", port.getAddress()), e);
        } finally {
            port.getOutputStream().flush();
        }
    }

    private void resetFiles(List<String> errors, TCPPort port) {
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
            bytes.putInt(17); //16 plu, 1 item

            bytes.putShort((short) getCRC16(bytes.array()));

            if(debugMode)
                processTransactionLogger.info("Reset files: " + Hex.encodeHexString(bytes.array()));

            port.getOutputStream().write(bytes.array());
            port.getOutputStream().flush();
        } catch (IOException e) {
            logError(errors, String.format(getLogPrefix() + "%s Send command exception: ", port.getAddress()), e);
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


    protected boolean clearAll(List<String> errors, TCPPort port, ScalesInfo scales) {
        processTransactionLogger.info(String.format(getLogPrefix() + "IP %s Clear Files", scales.port));
        resetFiles(errors, port);
        boolean cleared = getResetFilesReply(errors, port, scales.port);
        if (!cleared)
            logError(errors, String.format(getLogPrefix() + "IP %s Clear Files failed", scales.port));
        return cleared;
    }

    private boolean loadItem(List<String> errors, TCPPort port, ScalesItemInfo item, Integer nameLineLength, String barcodePrefix, short current, short total, boolean first, byte commandFileType) throws IOException {
        byte[] bytes = getItemBytes(item, nameLineLength, barcodePrefix, first);
        clearReceiveBuffer(port);
        sendCommand(errors, port, bytes, current, total, commandFileType);
        return getCommandReply(errors, port, port.getAddress(), commandFileType);
    }

    private byte[] getItemBytes(ScalesItemInfo item, Integer nameLineLength, String barcodePrefix, boolean first) {
        byte[] firstBytes = first ? getBytes("01PC0000000001") : new byte[0];
        //потенциально длина со знаками переноса строк ("|") может превысить максимум

        String name;
        String description;
        if(item.description != null && item.description.contains("@@")) {
            String[] splitted = item.description.split("@@");
            name = splitted[1];
            description = splitted[0];
        } else {
            name = item.name;
            description = item.description;
        }

        byte[] nameBytes = toAscii(trim(name, "", 248), nameLineLength);
        byte[] descriptionBytes = toAscii(trim(description, "", 998), nameLineLength);

        String idItem = trim(item.idBarcode, 15);

        int length = 39 + idItem.length() + firstBytes.length + nameBytes.length + descriptionBytes.length;
        ByteBuffer bytes = ByteBuffer.allocate(length);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        bytes.put(firstBytes);

        //ID - Идентификатор товара, уникальное значение, 4 bytes
        bytes.putInt(getPluNumber(item));

        //Length - Длина записи, 2 bytes
        bytes.putShort((short) (length - firstBytes.length - 6));

        // DigLength - Длина числовых данных, 1 byte
        bytes.put((byte) (length - 7 - firstBytes.length - nameBytes.length - descriptionBytes.length));

        //BitMask - Битовая маска, 4 bytes
        //Если параметры №5 – 17 равны нулю, они не записываются в файл, соответствующий бит в поле BitMask устанавливается в ноль.
        //16 BasicUnit, 32 Price, 256 GoodsTypeID, 512 GoodsGroupCode, 1024 AdditionPercent, 4096 BestBefore, 8192 ShelfLife, 16384 CertificationCode, 32768 BarcodePrefix
        int bitMask = 63280 + idItem.length();
        bytes.putInt(bitMask);

        //Code - Код товара, до 15 bytes
        bytes.put(getBytes(idItem));

        //BasicUnit - Базовая ед. измерения, 5 bytes
        bytes.put(getBytes(fillSpaces(item.idUOM, 5)));

        //Price - Цена в копейках, 4 bytes
        int price = item.price.multiply(BigDecimal.valueOf(100)).intValue();
        bytes.putInt(price);

        //TarеWeight - Вес тары в граммах, 4 bytes, 64 in bitMask
        //bytes.putInt(0);

        //GoodsTypeID - Тип товара 0 весовой, 1 штучный, 1 byte
        bytes.put((byte) (item.splitItem ? 0 : 1));

        //GoodsGroupCode - Код группы товаров, 2 bytes
        short idItemGroup = 0; //item.idItemGroup == null ? 0 : Short.parseShort(item.idItemGroup);
        bytes.putShort(idItemGroup);

        // AdditionPercent - Процент содержания примеси в товаре (используется при заморозке) От 0 до 99, 1 byte
        byte additionPercent = (byte) (item.extraPercent == null ? 0 : item.extraPercent.intValue());
        bytes.put(additionPercent);

        //BestBefore - Дата реализации, 6 bytes
        bytes.put(((byte) 0x00)); //1-ый – ГГ (год 00 ≤ ГГ ≤ 99) 75 = 117
        bytes.put(((byte) 0x00)); //2-ой – ММ (месяц 1 ≤ ММ ≤ 12) 05
        bytes.put(((byte) 0x00)); //3-ий – ДД (день 1≤ ДД ≤31) 1E
        bytes.put(((byte) 0x00)); //4-ый – ЧЧ (часы 0 ≤ ЧЧ <24)
        bytes.put(((byte) 0x00)); //5-ый – ММ (минуты 0 ≤ ММ < 60)
        bytes.put(((byte) 0x00)); //6-ой байт – СС (секунды 0≤ СС <60)

        //ShelfLife - Срок годности в минутах, 4 bytes
        int shelfLife = item.hoursExpiry != null ? item.hoursExpiry * 60 : 0;
        bytes.putInt(shelfLife);

        //CertificationCode - Код сертификации, 4 bytes, на самом деле - это дробная часть AdditionPercent
        String fractionalAdditionPercent = String.valueOf(item.extraPercent == null ? 0 : item.extraPercent.remainder(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).intValue());
        bytes.put(getBytes(fillSpaces(fractionalAdditionPercent, 4)));

        //BarcodePrefix - Префикс штрихкода, 1 byte
        byte prefix = barcodePrefix == null ? 0x17 : Byte.parseByte(barcodePrefix);
        bytes.put(prefix);

        //Name - Наименование товара, 2-250 bytes
        bytes.put(nameBytes);

        //Ingredients - Состав, 2-1000 bytes
        bytes.put(descriptionBytes);

        return bytes.array();
    }

    private boolean getCommandReply(List<String> errors, TCPPort port, String ip, byte commandFileType) {
        boolean result = false;
        byte[] reply = receiveReply(errors, port, ip);
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(reply))) {
            byte byte0 = stream.readByte();
            byte byte1 = stream.readByte();
            byte byte2 = stream.readByte();
            if (byte0 == (byte) 0xF8 && byte1 == (byte) 0x55 && byte2 == (byte) 0xCE) {
                byte lengthByte1 = stream.readByte();
                byte lengthByte2 = stream.readByte();
                byte code = stream.readByte();
                if (code == (byte) 0x42) { //42 ok, 43 error
                    byte fileType = stream.readByte();
                    if (fileType == commandFileType) {
                        result = true;
                    }
                }
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return result;
    }

    private boolean loadPLU(List<String> errors, TCPPort port, ScalesItemInfo item, short current, short total, boolean first, byte commandFileType) throws IOException {
        byte[] bytes = getPLUBytes(item, first);
        clearReceiveBuffer(port);
        sendCommand(errors, port, bytes, current, total, commandFileType);
        return getCommandReply(errors, port, port.getAddress(), commandFileType);
    }

    private byte[] getPLUBytes(ScalesItemInfo item, boolean first) {
        byte[] firstBytes = first ? getBytes("05PC0000000001") : new byte[0];

        int length = 25 + firstBytes.length;
        ByteBuffer bytes = ByteBuffer.allocate(length);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        bytes.put(firstBytes);

        //ID - Идентификатор, уникальное значение, 4 bytes
        bytes.putInt(getPluNumber(item));

        //Length - Длина записи, 2 bytes
        bytes.putShort((short) (length - firstBytes.length - 6));

        //Code - номер PLU, 6 bytes
        bytes.putInt(getPluNumber(item));
        bytes.put((byte) 0x00);
        bytes.put((byte) 0x00);

        //GoodsID - Идентификатор товара, 4 bytes
        bytes.putInt(getPluNumber(item));

        //BasicUnit - Базовая ед. измерения, 5 bytes
        bytes.put(getBytes(fillSpaces(item.idUOM, 5)));

        // ConversionFactor - Коэффициент пересчета, В тысячных долях (1000 - коэффициент пересчета равен 1), 4 bytes
        bytes.putInt(1000);

        return bytes.array();
    }

    protected void logError(List<String> errors, String errorText) {
        logError(errors, errorText, null);
    }

    protected void logError(List<String> errors, String errorText, Throwable t) {
        errors.add(errorText.replace("\u001b", "").replace("\u0000", "") + (t == null ? "" : ('\n' + ExceptionUtils.getStackTraceString(t))));
        processTransactionLogger.error(errorText, t);
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoList) {
        //todo: Единственный пока способ реализации стоп-листов - считывать из весов все товары,
        //удалять ненужные и загружать назад.
        /*try {
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
        }*/
    }

    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        MassaKRL10Settings massaKRL10Settings = springContext.containsBean("massaKRL10Settings") ? (MassaKRL10Settings) springContext.getBean("massaKRL10Settings") : null;
        Integer nameLineLength = massaKRL10Settings != null ? massaKRL10Settings.getNameLineLength() : null;
        return new MassaKRL10SendTransactionTask(transaction, scales, nameLineLength);
    }

    class MassaKRL10SendTransactionTask extends SendTransactionTask {
        Integer nameLineLength;

        public MassaKRL10SendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales, Integer nameLineLength) {
            super(transaction, scales);
            this.nameLineLength = nameLineLength;
        }

        @Override
        protected Pair<List<String>, Boolean> run() {
            List<String> localErrors = new ArrayList<>();
            boolean cleared = false;
            TCPPort port = new TCPPort(scales.port, 5001);
            String openPortResult = openPort(localErrors, port, scales.port);
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
                        processTransactionLogger.info(getLogPrefix() + "Sending items..." + scales.port);
                        if (localErrors.isEmpty()) {
                            int count = 0;
                            for (ScalesItemInfo item : transaction.itemsList) {
                                count++;
                                if (!Thread.currentThread().isInterrupted() && globalError < 5) {
                                    if (item.idBarcode != null && item.idBarcode.length() <= 5) {
                                        processTransactionLogger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
                                        int attempts = 0;
                                        boolean result = false;
                                        while (!result && attempts < 3) {
                                            if (attempts > 0)
                                                reopenPort(port);
                                            short current = (short) (transaction.snapshot ? count : 1);
                                            short total = (short) (transaction.snapshot ? transaction.itemsList.size() : 1);
                                            result = loadItem(localErrors, port, item, nameLineLength, scales.weightCodeGroupScales, current, total, count == 1 && transaction.snapshot,
                                                    transaction.snapshot ? snapshotItemByte : notSnapshotItemByte);
                                            attempts++;
                                        }
                                        if (!result) {
                                            logError(localErrors, String.format(getLogPrefix() + "IP %s, send failed, item %s", scales.port, item.idItem));
                                            globalError++;
                                        }
                                    } else {
                                        processTransactionLogger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, item #%s: incorrect barcode %s", scales.port, transaction.id, count, item.idBarcode));
                                    }
                                } else break;
                            }
                        }

                        processTransactionLogger.info(getLogPrefix() + "Sending plu..." + scales.port);
                        if (localErrors.isEmpty()) {
                            int count = 0;
                            for (ScalesItemInfo item : transaction.itemsList) {
                                count++;
                                if (!Thread.currentThread().isInterrupted() && globalError < 5) {
                                    if (item.idBarcode != null && item.idBarcode.length() <= 5) {
                                        processTransactionLogger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, sending plu #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
                                        int attempts = 0;
                                        boolean result = false;
                                        while (!result && attempts < 3) {
                                            if (attempts > 0)
                                                reopenPort(port);
                                            short current = (short) (transaction.snapshot ? count : 1);
                                            short total = (short) (transaction.snapshot ? transaction.itemsList.size() : 1);
                                            result = loadPLU(localErrors, port, item, current, total, count == 1 && transaction.snapshot,
                                                    transaction.snapshot ? snapshotPluByte : notSnapshotPluByte);
                                            attempts++;
                                        }
                                        if (!result) {
                                            logError(localErrors, String.format(getLogPrefix() + "IP %s, send failed, plu %s", scales.port, item.idItem));
                                            globalError++;
                                        }
                                    } else {
                                        processTransactionLogger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, plu #%s: incorrect barcode %s", scales.port, transaction.id, count, item.idBarcode));
                                    }
                                } else break;
                            }
                        }

                        port.close();
                    }

                } catch (Exception e) {
                    logError(localErrors, String.format(getLogPrefix() + "IP %s error, transaction %s;", scales.port, transaction.id), e);
                } finally {
                    processTransactionLogger.info(getLogPrefix() + "Finally disconnecting..." + scales.port);
                    try {
                        port.close();
                    } catch (CommunicationException e) {
                        logError(localErrors, String.format(getLogPrefix() + "IP %s close port error ", scales.port), e);
                    }
                }
            }
            processTransactionLogger.info(getLogPrefix() + "Completed ip: " + scales.port);
            return Pair.create(localErrors, cleared);
        }
    }

    private byte[] getBytes(String value) {
        return value.getBytes(Charset.forName("cp1251"));
    }

    private byte[] toAscii(String text, Integer nameLineLength) {
        if (text == null)
            text = "";
        text = text.replace("\n", "|");
        if(!text.contains("|") && (nameLineLength != null && text.length() > nameLineLength)) {
                int start = 0;
                String newText = "";
                while(start < text.length()) {
                    int finish = Math.min(start + nameLineLength, text.length());
                    newText += (newText.isEmpty() ? "" : "|") + text.substring(start, finish);
                    start = finish;
                }
                text = newText;
        }

        ByteBuffer bytes = ByteBuffer.allocate(text.length() + 2);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.putShort((short) (text.length()/* - lines*/));
        bytes.put(text.getBytes(Charset.forName("cp1251")));
        return bytes.array();
    }

    private String fillSpaces(String value, int length) {
        if (value == null)
            value = "";
        if (value.length() > length)
            value = value.substring(0, length);
        while (value.length() < length)
            value += " ";
        return value;
    }
}