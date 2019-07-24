package equ.clt.handler.mettlerToledo;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.EquipmentServer;
import equ.clt.handler.DefaultScalesHandler;
import equ.clt.handler.TCPPort;
import lsfusion.base.ExceptionUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static equ.clt.handler.HandlerUtils.safeMultiply;

public class MettlerToledoHandler extends DefaultScalesHandler {

    private static byte stx = 0x02;
    private static byte ack = 0x06;

    private static short pluID = 207;
    private static short extraTextID = 209;

    private final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");

    private FileSystemXmlApplicationContext springContext;

    public MettlerToledoHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) {
        StringBuilder groupId = new StringBuilder();
        for (MachineryInfo scales : transactionInfo.machineryInfoList) {
            groupId.append(scales.port).append(";");
        }
        return getLogPrefix() + groupId;
    }

    private String getLogPrefix() {
        return "MettlerToledo: ";
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionInfoList) throws IOException {
        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        Map<String, String> brokenPortsMap = new HashMap<>();
        if(transactionInfoList.isEmpty()) {
            processTransactionLogger.error(getLogPrefix() + "Empty transaction list!");
        }
        for(TransactionScalesInfo transaction : transactionInfoList) {
            processTransactionLogger.info(getLogPrefix() + "Send Transaction # " + transaction.id);

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
                                taskList.add(new SendTransactionTask(transaction, scales));
                            }
                        }
                    }

                    if(!taskList.isEmpty()) {
                        ExecutorService singleTransactionExecutor = EquipmentServer.getFixedThreadPool(taskList.size(), "ToledoSendTransaction");
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

    private List<ScalesInfo> getEnabledScalesList(TransactionScalesInfo transaction, List<MachineryInfo> succeededScalesList) {
        List<ScalesInfo> enabledScalesList = new ArrayList<>();
        for (ScalesInfo scales : transaction.machineryInfoList) {
            if(scales.succeeded)
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

    private void errorMessages(Map<String, List<String>> errors, Set<String> ips, Map<String, String> brokenPortsMap) {
        if (!errors.isEmpty()) {
            String message = "";
            for (Map.Entry<String, List<String>> entry : errors.entrySet()) {
                message += entry.getKey() + ": \n";
                for (String error : entry.getValue()) {
                    message += error + "\n";
                }
            }
            throw new RuntimeException(message);
        } else if (ips.isEmpty() && brokenPortsMap.isEmpty())
            throw new RuntimeException(getLogPrefix() + "No IP-addresses defined");
    }

    private boolean receiveReply(TCPPort port) throws IOException {
        byte[] result = new byte[500];

        try {
            long startTime = new Date().getTime();

            long time;
            do {
                if(port.getBisStream().available() != 0) {
                    port.getBisStream().read(result);
                    return result[0] == ack; //0x02 is ok, 0x15 is 'Ошибка CRC', 66h is 'Есть ответ'
                }

                Thread.sleep(10L);
                time = (new Date()).getTime();
            } while(time - startTime <= 10000L); //10 seconds

            return false;
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private boolean clearPlu(TCPPort port) throws IOException {
        sendCommand(port, getClearBytes(), pluID, (short) 8);
        return receiveReply(port);
    }

    private boolean clearExtraText(TCPPort port) throws IOException {
        sendCommand(port, getClearBytes(), extraTextID, (short) 8);
        return receiveReply(port);
    }

    private boolean loadPLU(TCPPort port, ScalesItemInfo item) throws IOException {
        sendCommand(port, getLoadPLUBytes(item), pluID, (short) 0);
        return receiveReply(port);
    }

    private boolean loadExtraText(TCPPort port, ScalesItemInfo item) throws IOException {
        if (item.description != null && !item.description.isEmpty()) {
            sendCommand(port, getLoadExtraTextBytes(item), extraTextID, (short) 0);
            return receiveReply(port);
        } else return true;
    }

    private byte[] getClearBytes() {
        return ByteBuffer.allocate(45).array(); //45 empty bytes
    }

    private byte[] getLoadPLUBytes(ScalesItemInfo item) {
        ByteBuffer bytes = ByteBuffer.allocate(68);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        int pluNumber = getPluNumber(item);

        //Номер  PLU, 4 bytes
        bytes.putInt(pluNumber);

        //EAN код, 13 bytes
        String idBarcode = item.idBarcode;
        bytes.put(fillLeadingZeroes(idBarcode, 13).getBytes());

        //Описание артикула, 28 bytes
        byte[] nameBytes = fillTrailingSpaces(item.name, 28).getBytes(Charset.forName("cp866"));
        bytes.put(nameBytes);

        //Не используется, 1 byte
        bytes.put((byte) 0);

        //Цена (копейки), 4 bytes
        BigDecimal price = safeMultiply(item.price, 100);
        bytes.putInt(price != null ? price.intValue() : 0);

        //Налоговая ставка, 1 byte
        bytes.put((byte) 0);

        //Номер тары, 1 byte
        bytes.put((byte) 0);

        //Не используется, 2 bytes
        bytes.putShort((short) 0);

        //Фиксированный вес(г), 2 bytes
        bytes.putShort((short) 0);

        //Не используется, 2 bytes
        bytes.putShort((short) 0);

        //Номер товарной группы, 2 bytes
        bytes.putShort((short) 0);

        //Флаг артикула, 2 bytes
        bytes.putShort((short) (item.passScalesItem && item.splitItem ? 0 : 1));

        //Срок годности(дни), 2 bytes
        bytes.putShort(item.daysExpiry != null ? item.daysExpiry.shortValue() : 0);

        //Срок реализации(дни), 2 bytes
        bytes.putShort(item.daysExpiry != null ? item.daysExpiry.shortValue() : 0);

        //Номер дополнительного текста, 2 bytes
        bytes.putShort(getExtraTextNumber(pluNumber));

        return bytes.array();
    }

    private byte[] getLoadExtraTextBytes(ScalesItemInfo item) {
        ByteBuffer bytes = ByteBuffer.allocate(202);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        //Номер дополнительного текста, 2 bytes
        bytes.putShort(getExtraTextNumber(getPluNumber(item)));

        //Дополнительный текст, 200 bytes
        bytes.put(fillTrailingSpaces(item.description, 200).getBytes(Charset.forName("cp866")));

        return bytes.array();
    }

    private int getPluNumber(ScalesItemInfo item) {
        return item.pluNumber != null ? item.pluNumber : Integer.parseInt(item.idBarcode);
    }

    private short getExtraTextNumber(int pluNumber) {
        return (short) (pluNumber <= 2000 ? pluNumber : 0);
    }

    private void logError(List<String> errors, String errorText) {
        logError(errors, errorText, null);
    }

    private void logError(List<String> errors, String errorText, Throwable t) {
        errors.add(errorText + (t == null ? "" : ('\n' + ExceptionUtils.getStackTraceString(t))));
        processTransactionLogger.error(errorText, t);
    }

    private void sendCommand(TCPPort port, byte[] commandBytes, short commandId, short ctl) throws IOException {

        short pageCount = 1;
        short pageLength = (short) commandBytes.length;

        short totalLength = (short) (pageLength * pageCount + 8);

        byte cmd = 0;

        short dpt = 1;

        byte dev = 0;

        ByteBuffer bytes = ByteBuffer.allocate(totalLength + 9);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        bytes.put(stx);//1 byte, Стартовый байт
        bytes.putShort(totalLength);//2 bytes, Размер команды
        bytes.putShort(pageCount);//2 bytes, Количество страниц в команде
        bytes.putShort(pageLength);//2 bytes, Размер страницы
        bytes.put(cmd);//1 byte, Команда / Ответ
        bytes.putShort(commandId);//2 bytes, Код команды
        bytes.putShort(ctl);//2 bytes, Управляющее поле
        bytes.putShort(dpt);//2 bytes, Номер отдела
        bytes.put(dev);//1 byte// , Номер весов, При записи всегда 0

        bytes.put(commandBytes);//68 bytes

        bytes.order(ByteOrder.BIG_ENDIAN);
        bytes.putShort((short) getCRC16(bytes.array(), 1, bytes.position()));//2 bytes

        port.getOutputStream().write(bytes.array());
        port.getOutputStream().flush();
    }

    class SendTransactionTask implements Callable<SendTransactionResult> {
        TransactionScalesInfo transaction;
        ScalesInfo scales;

        public SendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
            this.transaction = transaction;
            this.scales = scales;
        }

        @Override
        public SendTransactionResult call() throws Exception {
            List<String> localErrors = new ArrayList<>();
            boolean cleared = false;
            TCPPort port = new TCPPort(scales.port, 3001);
            try {
                port.open();

                int globalError = 0;
                try {
                    boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
                    if (needToClear) {
                        cleared = clearPlu(port) && clearExtraText(port);
                    }
                    if (cleared || !needToClear) {
                        processTransactionLogger.info(getLogPrefix() + "Sending items..." + scales.port);
                        int count = 0;
                        for (ScalesItemInfo item : transaction.itemsList) {
                            count++;
                            if (!Thread.currentThread().isInterrupted() && globalError < 5) {
                                if (item.idBarcode != null && item.idBarcode.length() <= 5) {
                                    processTransactionLogger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
                                    int attempts = 0;
                                    Boolean result = null;
                                    while ((result == null || !result) && attempts < 3) {
                                        result = loadPLU(port, item) && loadExtraText(port, item);
                                        attempts++;
                                    }
                                    if (!result) {
                                        logError(localErrors, String.format(getLogPrefix() + "IP %s, Result %s, item %s", scales.port, result, item.idItem));
                                        globalError++;
                                    }
                                } else {
                                    processTransactionLogger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, item #%s: incorrect barcode %s", scales.port, transaction.id, count, item.idBarcode));
                                }
                            } else break;
                        }
                    }
                } catch (Exception e) {
                    logError(localErrors, String.format(getLogPrefix() + "IP %s error, transaction %s;", scales.port, transaction.id), e);
                }
            } catch (Exception e) {
                logError(localErrors, String.format(getLogPrefix() + "IP %s error, transaction %s;", scales.port, transaction.id), e);
            } finally {
                port.close();
            }
            processTransactionLogger.info(getLogPrefix() + "Completed ip: " + scales.port);
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

    int[] tableCRC16 =
            {
                0x0000, 0x1021, 0x2042, 0x3063, 0x4084, 0x50A5, 0x60C6, 0x70E7,
                0x8108, 0x9129, 0xA14A, 0xB16B, 0xC18C, 0xD1AD, 0xE1CE, 0xF1EF,
                0x1231, 0x0210, 0x3273, 0x2252, 0x52B5, 0x4294, 0x72F7, 0x62D6,
                0x9339, 0x8318, 0xB37B, 0xA35A, 0xD3BD, 0xC39C, 0xF3FF, 0xE3DE,
                0x2462, 0x3443, 0x0420, 0x1401, 0x64E6, 0x74C7, 0x44A4, 0x5485,
                0xA56A, 0xB54B, 0x8528, 0x9509, 0xE5EE, 0xF5CF, 0xC5AC, 0xD58D,
                0x3653, 0x2672, 0x1611, 0x0630, 0x76D7, 0x66F6, 0x5695, 0x46B4,
                0xB75B, 0xA77A, 0x9719, 0x8738, 0xF7DF, 0xE7FE, 0xD79D, 0xC7BC,
                0x48C4, 0x58E5, 0x6886, 0x78A7, 0x0840, 0x1861, 0x2802, 0x3823,
                0xC9CC, 0xD9ED, 0xE98E, 0xF9AF, 0x8948, 0x9969, 0xA90A, 0xB92B,
                0x5AF5, 0x4AD4, 0x7AB7, 0x6A96, 0x1A71, 0x0A50, 0x3A33, 0x2A12,
                0xDBFD, 0xCBDC, 0xFBBF, 0xEB9E, 0x9B79, 0x8B58, 0xBB3B, 0xAB1A,
                0x6CA6, 0x7C87, 0x4CE4, 0x5CC5, 0x2C22, 0x3C03, 0x0C60, 0x1C41,
                0xEDAE, 0xFD8F, 0xCDEC, 0xDDCD, 0xAD2A, 0xBD0B, 0x8D68, 0x9D49,
                0x7E97, 0x6EB6, 0x5ED5, 0x4EF4, 0x3E13, 0x2E32, 0x1E51, 0x0E70,
                0xFF9F, 0xEFBE, 0xDFDD, 0xCFFC, 0xBF1B, 0xAF3A, 0x9F59, 0x8F78,
                0x9188, 0x81A9, 0xB1CA, 0xA1EB, 0xD10C, 0xC12D, 0xF14E, 0xE16F,
                0x1080, 0x00A1, 0x30C2, 0x20E3, 0x5004, 0x4025, 0x7046, 0x6067,
                0x83B9, 0x9398, 0xA3FB, 0xB3DA, 0xC33D, 0xD31C, 0xE37F, 0xF35E,
                0x02B1, 0x1290, 0x22F3, 0x32D2, 0x4235, 0x5214, 0x6277, 0x7256,
                0xB5EA, 0xA5CB, 0x95A8, 0x8589, 0xF56E, 0xE54F, 0xD52C, 0xC50D,
                0x34E2, 0x24C3, 0x14A0, 0x0481, 0x7466, 0x6447, 0x5424, 0x4405,
                0xA7DB, 0xB7FA, 0x8799, 0x97B8, 0xE75F, 0xF77E, 0xC71D, 0xD73C,
                0x26D3, 0x36F2, 0x0691, 0x16B0, 0x6657, 0x7676, 0x4615, 0x5634,
                0xD94C, 0xC96D, 0xF90E, 0xE92F, 0x99C8, 0x89E9, 0xB98A, 0xA9AB,
                0x5844, 0x4865, 0x7806, 0x6827, 0x18C0, 0x08E1, 0x3882, 0x28A3,
                0xCB7D, 0xDB5C, 0xEB3F, 0xFB1E, 0x8BF9, 0x9BD8, 0xABBB, 0xBB9A,
                0x4A75, 0x5A54, 0x6A37, 0x7A16, 0x0AF1, 0x1AD0, 0x2AB3, 0x3A92,
                0xFD2E, 0xED0F, 0xDD6C, 0xCD4D, 0xBDAA, 0xAD8B, 0x9DE8, 0x8DC9,
                0x7C26, 0x6C07, 0x5C64, 0x4C45, 0x3CA2, 0x2C83, 0x1CE0, 0x0CC1,
                0xEF1F, 0xFF3E, 0xCF5D, 0xDF7C, 0xAF9B, 0xBFBA, 0x8FD9, 0x9FF8,
                0x6E17, 0x7E36, 0x4E55, 0x5E74, 0x2E93, 0x3EB2, 0x0ED1, 0x1EF0
            };

    public int getCRC16(byte[] bytes, int s, int e) {
        int crc = 0x0000;

        for (int i = s; i < e; i++) {
            crc = ((crc << 8) ^ tableCRC16[((crc >>> 8) ^ (bytes[i] & 0xFF)) & 0xFF]);
        }
        return crc & 0xFFFF;
    }
}