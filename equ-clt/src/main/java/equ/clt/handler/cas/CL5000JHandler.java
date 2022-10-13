package equ.clt.handler.cas;

import equ.api.ItemInfo;
import equ.api.MachineryInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import equ.api.stoplist.StopListInfo;
import equ.clt.handler.HandlerUtils;
import equ.clt.handler.MultithreadScalesHandler;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.file.RawFileData;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static lsfusion.base.BaseUtils.nvl;

public class CL5000JHandler extends MultithreadScalesHandler {

    protected final static Logger casLogger = Logger.getLogger("CASLogger");

    private FileSystemXmlApplicationContext springContext;
    private int descriptionLength;

    public CL5000JHandler(FileSystemXmlApplicationContext springContext) {
        this(springContext, 300);
    }

    public CL5000JHandler(FileSystemXmlApplicationContext springContext, int descriptionLength) {
        this.springContext = springContext;
        this.descriptionLength = descriptionLength;
    }

    @Override
    protected String getLogPrefix() {
        return "CL5000J: ";
    }

    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        return new CLSendTransactionTask(transaction, scales);
    }

    class CLSendTransactionTask extends SendTransactionTask {
        private Integer priceMultiplier;
        private boolean useWeightCodeInBarcodeNumber;
        private Integer maxNameLength;

        public CLSendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
            super(transaction, scales);
            initSettings();
        }

        @Override
        protected SendTransactionResult run() throws Exception {
            List<String> localErrors = new ArrayList<>();

            boolean cleared = false;
            DataSocket socket = getDataSocket(scales.port);

            try {
                socket.open();

                int globalError = 0;
                boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
                if (needToClear) {
                    List<String> clearErrors = clearData(socket);
                    localErrors.addAll(clearErrors);
                    cleared = clearErrors.isEmpty();
                }

                if (cleared || !needToClear) {
                    casLogger.info(getLogPrefix() + "Sending items..." + scales.port);

                    short weightCode = getWeightCode(scales);
                    short pieceCode = getPieceCode(scales);

                    if (localErrors.isEmpty()) {
                        int count = 0;
                        for (ScalesItem item : transaction.itemsList) {
                            count++;
                            if (!Thread.currentThread().isInterrupted() && globalError < 5) {

                                int barcode = getBarcode(item);
                                int pluNumber = getPluNumber(item.pluNumber, barcode);
                                casLogger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
                                int reply = sendItem(socket, item, weightCode, pieceCode, pluNumber, barcode, item.name,
                                        item.price == null ? 0 : item.price.multiply(BigDecimal.valueOf(priceMultiplier)).intValue(),
                                        HandlerUtils.trim(item.description, null, descriptionLength - 1), useWeightCodeInBarcodeNumber, maxNameLength);
                                if (reply != 0) {
                                    localErrors.add(getLogPrefix() + String.format("Send item %s failed. Error: %s\n", pluNumber, getErrorMessage(reply)));
                                    globalError++;
                                } else {
                                    String errorTouch = sendTouchPlu(socket, item.itemImage, pluNumber);
                                    if (errorTouch != null) {
                                        localErrors.add(getLogPrefix() + String.format("Send item touch %s failed. Error: %s\n", pluNumber, errorTouch));
                                        globalError++;
                                    }
                                }

                            } else break;
                        }

                        if (localErrors.isEmpty()) {
                            String errorTouch = sendTouchSpeedKeys(socket, transaction.itemsList);
                            if (errorTouch != null) {
                                localErrors.add(String.format("Send speed keys failed. Error: %s\n", errorTouch));
                            }
                        }

                    }
                    socket.close();
                }

            } catch (Exception e) {
                logError(localErrors, String.format(getLogPrefix() + "IP %s error, transaction %s;", scales.port, transaction.id), e);
            } finally {
                casLogger.info(getLogPrefix() + "Finally disconnecting..." + scales.port);
                socket.close();
            }

            casLogger.info(getLogPrefix() + "Completed ip: " + scales.port);
            return new SendTransactionResult(scales, localErrors, cleared);
        }

        protected void initSettings() {
            CASSettings settings = springContext.containsBean("casSettings") ? (CASSettings) springContext.getBean("casSettings") : new CASSettings();
            priceMultiplier = nvl(settings.getPriceMultiplier(), 100);
            useWeightCodeInBarcodeNumber = settings.isUseWeightCodeInBarcodeNumber();
            maxNameLength = nvl(settings.getMaxNameLength(), 28);
        }

        protected List<String> clearData(DataSocket socket) throws IOException {
            List<String> errors = new ArrayList<>();
            casLogger.info(getLogPrefix() + "Deleting all plu at scales " + scales.port);
            int reply = deleteAllPlu(socket);
            if (reply != 0) {
                errors.add(String.format("Deleting all plu failed. Error: %s\n", getErrorMessage(reply)));
            } else {
                String errorTouch = deleteTouchSpeedKeys(socket);
                if (errorTouch != null)
                    errors.add(String.format("Deleting touch speed keys failed. Error: %s\n", errorTouch));
            }
            return errors;
        }

        private int sendItem(DataSocket socket, ScalesItem item, short weightCode, short pieceCode, int pluNumber, int barcode,
                             String name, int price, String description, boolean useWeightCodeInBarcodeNumber, Integer maxNameLength) throws IOException {
            int descriptionLength = description == null ? 0 : (description.length() + 1);
            ByteBuffer bytes = ByteBuffer.allocate(160 + descriptionLength);
            bytes.order(ByteOrder.LITTLE_ENDIAN);

            boolean isWeight = isWeight(item, 2);

            //header (10 bytes)
            bytes.put(getBytes("W"));
            bytes.put(getBytes("L"));
            bytes.putInt(0); //address
            bytes.put(getBytes(","));
            bytes.putShort((short) (147 + descriptionLength)); //data length
            bytes.put(getBytes(":"));

            //body (147 bytes + description (max 300 bytes)
            bytes.putShort(useWeightCodeInBarcodeNumber ? (short) 1 : weightCode); //departmentNumber 2 bytes
            bytes.putInt(pluNumber); //pluNumber 4 bytes
            bytes.put((byte) (isWeight ? 1 : 2)); //pluType (1 весовой, 2 штучный)
            bytes.put(getBytes(fillSpaces(substr(name, 0, maxNameLength), 40))); //firstLine
            bytes.put(getBytes(fillSpaces(substr(name, maxNameLength, maxNameLength * 2), 40)));//secondLine
            bytes.put(getBytes(fillSpaces("", 5)));//thirdLine
            bytes.putShort((short) 0); //groupNumber
            bytes.putShort((short) 0); //labelNumber
            bytes.putShort((short) 0); //aux labelNumber
            bytes.putShort((short) 0); //origin number
            bytes.put((byte) 0); //unit weight number
            bytes.putInt((byte) 0); //fixed weight number
            bytes.putInt(barcode); //itemcode
            bytes.putShort((short) 0); //quantity
            bytes.put((byte) 0); //quantity symbol number
            bytes.put((byte) 0); //use fixed price type
            bytes.putInt(price); //unit price
            bytes.putInt(0); //special price
            bytes.putInt(0); //tare weight
            BigDecimal extraPercent = item.extraPercent; //пока отключен
            bytes.put((byte) (extraPercent == null ? 0 : extraPercent.intValue()));//tare number, исп. только в cl5000d
            bytes.putShort(useWeightCodeInBarcodeNumber ? (isWeight ? weightCode : pieceCode) : (short) 0); //barcode number
            bytes.putShort((short) 0); //aux barcode number
            bytes.putShort((short) 0); //produced date
            bytes.putShort((short) 0); //packed date
            bytes.put((byte) 0); //packed time
            bytes.putInt(item.daysExpiry != null ? item.daysExpiry + 1 : 0); //sell by date
            bytes.put((byte) (item.hoursExpiry != null ? item.hoursExpiry : 0)); //sell by time
            bytes.putShort((short) 0); //message number
            bytes.putShort((short) 0); //reserved 1
            bytes.putShort((short) 0); //reserved 2
            bytes.put((byte) 0);// sale message number

            if(description != null)
                bytes.put(getBytes(description + "\0"));

            //tail (3 bytes)
            bytes.put(getBytes(":"));
            bytes.put(calculateCheckSum(bytes.array())); //checksum
            bytes.put((byte) 13); //cr

            return sendCommand(socket, bytes.array());
        }

        private String fillSpaces(String value, int length) {
            String result = value == null ? "" : value;
            if (result.length() > length)
                result = result.substring(0, length);
            while (result.length() < length)
                result += " ";
            return result;
        }

        private String substr(String value, int from, int to) {
            return value == null || value.length() < from ? null : value.substring(from, Math.min(to, value.length()));
        }

        protected void logError(List<String> errors, String errorText, Throwable t) {
            errors.add(errorText + (t == null ? "" : ('\n' + ExceptionUtils.getStackTraceString(t))));
            casLogger.error(errorText, t);
        }
    }

    private short getWeightCode(MachineryInfo scales) {
        try {
            String weightCode = scales instanceof ScalesInfo ? ((ScalesInfo) scales).weightCodeGroupScales : null;
            return weightCode == null ? 1 : Short.parseShort(weightCode);
        } catch (Exception e) {
            return 1;
        }
    }

    private short getPieceCode(MachineryInfo scales) {
        try {
            String pieceCode = scales instanceof ScalesInfo ? ((ScalesInfo) scales).pieceCodeGroupScales : null;
            return pieceCode == null ? 1 : Short.parseShort(pieceCode);
        } catch (Exception e) {
            return 1;
        }
    }

    protected String sendTouchPlu(DataSocket socket, RawFileData itemImage, int pluNumber) throws IOException {
        return null;
    }

    protected String sendTouchSpeedKeys(DataSocket socket, List<ScalesItem> itemsList) throws IOException {
        return null;
    }

    protected String deleteTouchSpeedKeys(DataSocket socket) throws IOException {
        return null;
    }

    private int deleteAllPlu(DataSocket socket) throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(19);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        //header (10 bytes)
        bytes.put(getBytes("W"));
        bytes.put(getBytes("L"));
        bytes.putInt(0); //address
        bytes.put(getBytes(","));
        bytes.putShort((short) 6); //data length
        bytes.put(getBytes(":"));

        //body (6 bytes)
        bytes.putShort((short) 0); //departmentNumber 2 bytes
        bytes.putInt(0); //pluNumber 4 bytes

        //tail (3 bytes)
        bytes.put(getBytes(":"));
        bytes.put(calculateCheckSum(bytes.array())); //checksum
        bytes.put((byte) 13); //cr

        return sendCommand(socket, bytes.array());
    }

    private int deletePlu(DataSocket socket, short weightCode, int pluNumber) throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(19);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        //header (10 bytes)
        bytes.put(getBytes("W"));
        bytes.put(getBytes("L"));
        bytes.putInt(0); //address
        bytes.put(getBytes(","));
        bytes.putShort((short) 6); //data length
        bytes.put(getBytes(":"));

        //body (6 bytes)
        bytes.putShort(weightCode); //departmentNumber 2 bytes
        bytes.putInt(pluNumber); //pluNumber 4 bytes

        //tail (3 bytes)
        bytes.put(getBytes(":"));
        bytes.put(calculateCheckSum(bytes.array())); //checksum
        bytes.put((byte) 13); //cr

        return sendCommand(socket, bytes.array());
    }


    private byte calculateCheckSum(byte[] bytes) {
        byte result = 0;
        for (int i = 2; i < bytes.length; i++) {
            result += bytes[i];
        }
        return result;
    }

    private int sendCommand(DataSocket socket, byte[] bytes) throws IOException {
        socket.outputStream.write(bytes);
        return receiveReply(socket);
    }

    private int receiveReply(DataSocket socket) {
        try {
            final Future<Byte> future = Executors.newSingleThreadExecutor().submit((Callable) () -> {
                byte[] buffer = new byte[255];
                socket.inputStream.read(buffer);
                return buffer[14] == 58 ? 0 : buffer[14]; //это либо байт ошибки, либо первый байт хвоста (:)
            });

            byte result;
            try {
                result = future.get(30000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                casLogger.error(getLogPrefix() + "receive reply error", e);
                future.cancel(true);
                result = -2;
            }
            return result;
        } catch (Exception e) {
            casLogger.error(getLogPrefix() + "receive reply error", e);
            return -1;
        }
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoList) throws IOException {
        CASSettings settings = springContext.containsBean("casSettings") ? (CASSettings) springContext.getBean("casSettings") : new CASSettings();
        boolean disableStopLists = settings.isDisableStopLists();
        if (stopListInfo != null && !stopListInfo.exclude && !disableStopLists) {
            casLogger.info(getLogPrefix() + "Send StopList # " + stopListInfo.number);
            if (!machineryInfoList.isEmpty()) {
                for (MachineryInfo scales : machineryInfoList) {
                    if (scales.port != null) {
                        DataSocket socket = getDataSocket(scales.port);
                        try {
                            casLogger.info(getLogPrefix() + "Sending StopList to scale " + scales.port);
                            socket.open();
                            short weightCode = getWeightCode(scales);
                            for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                                int pluNumber = getPluNumber(item.pluNumber, getBarcode(item));
                                casLogger.error(String.format(getLogPrefix() + "Sending StopList - Deleting item %s at scales %s", pluNumber, scales.port));
                                int reply = deletePlu(socket, weightCode, pluNumber);
                                if (reply != 0)
                                    casLogger.error(String.format(getLogPrefix() + "Failed to delete item %s at scales %s", getErrorMessage(pluNumber), scales.port));
                            }

                        } catch (Exception e) {
                            casLogger.error(String.format(getLogPrefix() + "Send StopList %s to scales %s error", stopListInfo.number, scales.port), e);
                        } finally {
                            casLogger.info(getLogPrefix() + "Finally disconnecting..." + scales.port);
                            socket.close();
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) {
        String groupId = "";
        for (MachineryInfo scales : transactionInfo.machineryInfoList) {
            groupId += scales.port + ";";
        }
        return getLogPrefix() + groupId;
    }

    protected byte[] getBytes(String value) throws UnsupportedEncodingException {
        return value.getBytes("cp1251");
    }

    protected int getBarcode(ItemInfo item) {
        return Integer.parseInt(item.idBarcode.substring(0, 5));
    }

    protected int getPluNumber(Integer plu, int barcode) {
        return plu == null ? barcode : plu;
    }

    private String getErrorMessage(int errorNumber) {
        switch(errorNumber) {
            case -2: return "TimeoutException. Check logs";
            case -1: return "Exception occurred. Check logs";
            case 82: return "number is over";
            case 84: return "Header, tail or CK fail";
            case 88: return "Direct message full";
            case 89: return "Memory full";
            case 95: return "Unknown data type";
            case 97: return "Data struct fail";
            case 98: return "Data isn’t exist";
            case 99: return "Data end";
            default: return "Unknown error " + errorNumber;
        }
    }

    private DataSocket getDataSocket(String address) {
        String[] hostPort = address.split(":");
        return hostPort.length == 1 ? new DataSocket(address, 20304) : new DataSocket(hostPort[0], Integer.parseInt(hostPort[1]));
    }
}