package equ.clt.handler.aclas;

import equ.api.*;
import equ.api.scales.ScalesHandler;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.EquipmentServer;
import lsfusion.base.ExceptionUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class AclasHandler extends ScalesHandler {

    private static String logPrefix = "Aclas: ";

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");

    protected FileSystemXmlApplicationContext springContext;

    public AclasHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) {
        return "aclas";
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionInfoList) throws IOException {
        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        Map<String, String> brokenPortsMap = new HashMap<>();
        if(transactionInfoList.isEmpty()) {
            processTransactionLogger.error(logPrefix + "Empty transaction list!");
        }
        for(TransactionScalesInfo transaction : transactionInfoList) {
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
                        ExecutorService singleTransactionExecutor = EquipmentServer.getFixedThreadPool(taskList.size(), "AclasSendTransaction");
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

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) {
    }

    protected List<ScalesInfo> getEnabledScalesList(TransactionScalesInfo transaction, List<MachineryInfo> succeededScalesList) {
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

    protected void errorMessages(Map<String, List<String>> errors, Set<String> ips, Map<String, String> brokenPortsMap) {
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
            throw new RuntimeException(logPrefix + "No IP-addresses defined");
    }

    public static boolean receiveReply(UDPPort port) throws IOException {
        try {
            byte[] response = port.receiveCommand(200);
            return response[0] == 0x02; //0x02 is ok, 0x52 is fail
        } catch (SocketTimeoutException e) {
            processTransactionLogger.error(logPrefix, e);
            return false;
        }
    }

    public static String receiveScaleStatus(UDPPort port) throws IOException {
        byte[] response = port.receiveCommand(259);
        return new String(Arrays.copyOfRange(response, 1, 7), "cp866"); //0x02 is ok, 0x52 is fail
    }

    private byte[] getHexBytes(String value) {
        int len = value.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(value.charAt(i), 16) << 4)
                    + Character.digit(value.charAt(i + 1), 16));
        }
        return data;
    }

    protected String fillLeadingZeroes(Object input, int length) {
        if (input == null)
            return null;
        if(!(input instanceof String))
            input = String.valueOf(input);
        if (((String) input).length() > length)
            input = ((String) input).substring(0, length);
        while (((String) input).length() < length)
            input = "0" + input;
        return (String) input;
    }

    private boolean connect(UDPPort udpPort) throws CommunicationException, IOException {
        sendCommand(udpPort, (byte) 0x0e, new byte[]{0x01, 0x00}, getConnectBytes256());
        return receiveReply(udpPort);
    }

    private boolean disconnect(UDPPort udpPort) throws CommunicationException, IOException {
        sendCommand(udpPort, (byte) 0x0e, new byte[]{0x02, 0x00}, getDisconnectBytes256());
        return receiveReply(udpPort);
    }

    private String getScaleStatus(UDPPort udpPort) throws CommunicationException, IOException {
        sendCommand(udpPort, (byte) 0x0e, new byte[]{0x03, 0x00}, getScaleStatusBytes256());
        return receiveScaleStatus(udpPort);
    }

    private boolean clearData(UDPPort udpPort) throws CommunicationException, IOException {
        sendCommand(udpPort, (byte) 0x0e, new byte[]{0x04, 0x00}, getClearDataBytes256(1));
        boolean result = receiveReply(udpPort);
        if (result) {
            sendCommand(udpPort, (byte) 0x0e, new byte[]{0x04, 0x00}, getClearDataBytes256(2));
            result = receiveReply(udpPort);
            if (result) {
                sendCommand(udpPort, (byte) 0x0e, new byte[]{0x04, 0x00}, getClearDataBytes256(3));
                result = receiveReply(udpPort);
            }
        }
        return result;
    }

    private boolean loadPLU(UDPPort udpPort, ScalesItemInfo item) throws CommunicationException, IOException {
        ByteBuffer addressBytes = ByteBuffer.allocate(2);
        addressBytes.order(ByteOrder.LITTLE_ENDIAN);
        addressBytes.putShort(Short.parseShort(item.idBarcode));//2 bytes

        sendCommand(udpPort, (byte) 0x0b, addressBytes.array(), getPLUBytes256(item));
        return receiveReply(udpPort);
    }

    private byte[] getConnectBytes256() {
        ByteBuffer bytes = ByteBuffer.allocate(256);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        //scale id, 4 bytes
        bytes.put(new byte[]{0x01, 0x00, 0x00, 0x00});

        //current date-time, 6 bytes
        String dateTime = new SimpleDateFormat("yymmddhhmmss").format(Calendar.getInstance().getTime());
        bytes.put(getHexBytes(dateTime));
        return bytes.array();
    }

    private byte[] getDisconnectBytes256() {
        ByteBuffer bytes = ByteBuffer.allocate(256);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        //scale id, 4 bytes
        bytes.put(new byte[]{0x01, 0x00, 0x00, 0x00});

        return bytes.array();
    }

    private byte[] getScaleStatusBytes256() {
        ByteBuffer bytes = ByteBuffer.allocate(/*256*/2);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        //2 bytes
        bytes.put((byte) 0xff);
        bytes.put((byte) 0x0f);

        return bytes.array();
    }


    private byte[] getClearDataBytes256(int step) {
        ByteBuffer bytes = ByteBuffer.allocate(4);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        if(step == 1) {
            //When 0..1 is 0x400,2..3 is 0x400+(4096 / 4 -1),the datas will be kept as the low in front while the high in back
            bytes.putShort((short) 1024/*400*/);
            bytes.putShort((short) 2047/*1423*/);
        } else if (step == 2){
            //when 0...1 is 0x200, 2...3 is 0x200+(1024 / 4 -1),then the datas are kept as the low in front while the high in back
            bytes.putShort((short) 512/*200*/);
            bytes.putShort((short) 767/*455*/);
        } else { //step == 3
            //(if the edition is LB1.00 from scale status, 0..1 0x300 2..3 0x300+196)
            //(If the edition is not LB1.00 from scale status, 0..1 0x138 2..3 0x138+196)
            //datas are kept. And the low is in front while the high in back
            bytes.putShort((short) 768/*300*/);
            bytes.putShort((short) 964/*496*/);
        }
        return bytes.array();
    }

    private byte[] getPLUBytes256(ScalesItemInfo item) {
        ByteBuffer bytes = ByteBuffer.allocate(256);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        boolean newAttempt = true;
        if(!newAttempt) {


            // PLU Name, 10 bytes
            String pluName = "testtestte";
            bytes.put(getHexBytes(pluName));

            // Department Id, 1 byte
            byte departmentId = 1;
            bytes.put(departmentId);

            // UnitPrice, 4 bytes
            int price = 1234;
            bytes.put(getHexBytes(fillLeadingZeroes(String.valueOf(price), 4)));

            // Reserve, 1 byte
            bytes.put((byte) 0x00);

            // Barcode, 7 bytes
            //bytes.put(new byte[] {0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});

            String prefix = "21";//pieceCode != null && pieceItem ? pieceCode : weightCode;
            boolean pieceItem = false;
            String barcode = fillTrailingZeroes(prefix + item.idBarcode, 13) + (pieceItem ? 2 : 1);
            bytes.put(getHexBytes(barcode));

            // Stock, 4 bytes
            String stock = "1234";
            bytes.put(getHexBytes(fillLeadingZeroes(stock, 4)));

            // Reserve, 5 bytes
            bytes.put(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00});


        } else {

            // PLU Name, 36 bytes
            String pluName = "testtesttesttesttesttesttesttesttest";
            bytes.put(getHexBytes(pluName));

            //lfcode
            String barcode = fillTrailingZeroes(item.idBarcode, 6);
            bytes.put(getHexBytes(barcode));

            //multilable
            bytes.put((byte) 0);

            //rebate
            bytes.put((byte) 0);

            // Department Id, 1 byte
            byte departmentId = 1;
            bytes.put(departmentId);

            //barcodeType
            bytes.put((byte) 1);

            // UnitPrice, 4 bytes
            int price = 1234;
            bytes.put(getHexBytes(fillLeadingZeroes(String.valueOf(price), 4)));

            //weightUnit
            bytes.put((byte) 1);

            //shelftime
            bytes.putShort((short) 1);

            //message1
            bytes.put((byte) 0);

            //message2
            bytes.put((byte) 0);

            //package weight
            bytes.put(new byte[] {(byte) 0, (byte) 0, (byte) 0});

            //package type
            bytes.put((byte) 0);

            //tolerance
            bytes.put((byte) 0);

            //tare
            bytes.put(new byte[] {(byte) 0, (byte) 0, (byte) 0});

            //code
            bytes.put(new byte[] {(byte) 1, (byte) 0, (byte) 0, (byte) 0, (byte) 0});

        }
        return bytes.array();
    }

    private byte[] getCommandBytes259(byte[] commandBytes256) {
        ByteBuffer bytes = ByteBuffer.allocate(259);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        byte[] controlSumBytes = getControlSum(commandBytes256);

        bytes.put((byte) 0x03); //1 byte, stx
        bytes.put(commandBytes256); //256 bytes
        bytes.put(controlSumBytes); //2 bytes

        return bytes.array();
    }

    private byte[] getCommandBytes300(byte[] commandBytes259) {
        ByteBuffer bytes = ByteBuffer.allocate(300);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        //total 297 bytes
        for (int i = 0; i < 37; i = i + 7) {
            bytes.put(appendEightsByte(Arrays.copyOfRange(commandBytes259, i, i + 7)));
        }

        byte[] totalBytes = getTotalBytes(commandBytes259);
        bytes.put(totalBytes); //2 bytes

        bytes.put((byte) (totalBytes[0] + totalBytes[1])); //300th byte
        return bytes.array();
    }

    private byte[] getCommandBytes302(byte[] commandBytes256) {

        byte[] commandBytes259 = getCommandBytes259(commandBytes256);
        byte[] commandBytes300 = getCommandBytes300(commandBytes259);

        ByteBuffer bytes = ByteBuffer.allocate(302);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        //first byte (stx) skip
        for(int i = 1; i < 300; i++) {
            commandBytes300[i] = setBit(commandBytes300[i], 7);
        }

        bytes.put(commandBytes300);
        bytes.put((byte) 0x04); //etx
        bytes.put((byte) 0xff); //finish pack
        return bytes.array();
    }

    private byte[] getControlSum(byte[] command) {
        short sum = 0;
        for (byte b : command) {
            sum += b;
        }

        ByteBuffer b = ByteBuffer.allocate(2);
        b.putShort(sum);

        return b.array();
    }

    private byte[] appendEightsByte(byte[] sevenBytes) {
        byte eightByte = 0;
        for (int i = 0; i < 7; i++) {
            Byte b = sevenBytes[i];
            if (getBit(b, 7)) {
                eightByte = setBit(eightByte, i);
            }
        }
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.put(sevenBytes);
        buffer.put(eightByte);
        return buffer.array();
    }

    private byte[] getTotalBytes(byte[] connectionBytes259) {
        byte min1 = (byte) 255;
        byte min2 = (byte) 255;
        for (byte b : connectionBytes259) {
            if (b < min1) {
                min1 = b;
            } else if (b < min2) {
                min2 = b;
            }
        }
        return new byte[]{min1, min2};
    }

    boolean getBit(byte n, int k) {
        return ((n >> k) & 1) == 1;
    }

    private byte setBit(byte byteValue, int pos) {
        return (byte) (byteValue | (1 << pos));
    }


    protected void logError(List<String> errors, String errorText) {
        logError(errors, errorText, null);
    }

    protected void logError(List<String> errors, String errorText, Throwable t) {
        errors.add(errorText.replace("\u001b", "").replace("\u0000", "") + (t == null ? "" : ('\n' + ExceptionUtils.getStackTraceString(t))));
        processTransactionLogger.error(errorText, t);
    }

    private String fillTrailingZeroes(String input, int length) {
        if (input == null)
            return null;
        while (input.length() < length)
            input = input + "0";
        return input;
    }

    private void sendCommand(UDPPort udpPort, byte command, byte[] address, byte[] commandBytes256) throws CommunicationException {
        ByteBuffer bytes = ByteBuffer.allocate(commandBytes256.length + 3/*305*/);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        bytes.put(command);//1 byte
        bytes.put(address);//2 bytes
        bytes.put(/*getCommandBytes302(*/commandBytes256/*)*/); //302 bytes

        udpPort.sendCommand(bytes.array());
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoList) {
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
            UDPPort udpPort = new UDPPort(scales.port, 5001, 2000);
            try {
                udpPort.open();

                int globalError = 0;
                try {
                    if (connect(udpPort)) {
                        boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
                        if (needToClear) {
                            cleared = clearData(udpPort);
                        }

                        if (cleared || !needToClear) {
                            processTransactionLogger.info(logPrefix + "Sending items..." + scales.port);
                            if (localErrors.isEmpty()) {
                                String ascCode = getScaleStatus(udpPort);
                                int count = 0;
                                for (ScalesItemInfo item : transaction.itemsList) {
                                    count++;
                                    if (!Thread.currentThread().isInterrupted() && globalError < 5) {
                                        if (item.idBarcode != null && item.idBarcode.length() <= 5) {
                                            processTransactionLogger.info(String.format(logPrefix + "IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
                                            int attempts = 0;
                                            Boolean result = null;
                                            while ((result == null || !result) && attempts < 3) {
                                                result = loadPLU(udpPort, item);
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


                            }
                        }
                    } else {
                        logError(localErrors, String.format(logPrefix + "IP %s, failed to connect", scales.port));
                    }
                } catch (Exception e) {
                    logError(localErrors, String.format(logPrefix + "IP %s error, transaction %s;", scales.port, transaction.id), e);
                } finally {
                    processTransactionLogger.info(logPrefix + "Finally disconnecting..." + scales.port);
                    disconnect(udpPort);
                }
            } catch (Exception e) {
                logError(localErrors, String.format(logPrefix + "IP %s error, transaction %s;", scales.port, transaction.id), e);
            } finally {
                udpPort.close();
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

    private static class UDPPort {
        private DatagramSocket socket = null;
        private InetAddress ipAddress;
        private int ipPort;
        private int timeout;

        public UDPPort(String ipAddress, int ipPort, int timeout) throws UnknownHostException {
            this.ipAddress = InetAddress.getByName(ipAddress);
            this.ipPort = ipPort;
            this.timeout = timeout;
        }

        public void open() throws CommunicationException {
            try {
                socket = new DatagramSocket();
                socket.setSoTimeout(timeout);
            } catch (Exception e) {
                throw new CommunicationException(e.toString());
            }
        }

        public void close() throws CommunicationException {
            try {
                if(socket != null) {
                    socket.close();
                    socket = null;
                }
            } catch (Exception e) {
                throw new CommunicationException(e.toString());
            }
        }

        public void sendCommand(byte[] bytes) throws CommunicationException {
            try {
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, ipAddress, ipPort);
                socket.send(packet);
            } catch (IOException e) {
                throw new CommunicationException("Send command exception " + e);
            }
        }

        public byte[] receiveCommand(int responseLength) throws IOException {
            byte[] response = new byte[responseLength];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            socket.receive(packet);
            return packet.getData();
        }

        public String toString() {
            return ipAddress + ", " + ipPort;
        }

        protected void finalize() throws Throwable {
            try {
                close();
            } finally {
                super.finalize();
            }
        }

    }
}