package equ.clt.handler.cas;

import equ.api.ItemInfo;
import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.StopListInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.handler.DefaultScalesHandler;
import equ.clt.handler.HandlerUtils;
import equ.clt.handler.ScalesSettings;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;

public class CL5000JHandler extends DefaultScalesHandler {

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
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) throws IOException {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        ScalesSettings settings = springContext.containsBean("CL5000JSettings") ? (ScalesSettings) springContext.getBean("CL5000JSettings") : null;
        int priceMultiplier = settings == null || settings.getPriceMultiplier() == null ? 100 : settings.getPriceMultiplier();

        if (transactionList.isEmpty()) {
            processTransactionLogger.error(getLogPrefix() + "Empty transaction list!");
        }
        for (TransactionScalesInfo transaction : transactionList) {
            processTransactionLogger.info(getLogPrefix() + "Send Transaction # " + transaction.id);

            List<MachineryInfo> succeededScalesList = new ArrayList<>();
            Exception exception = null;

            if (!transaction.machineryInfoList.isEmpty()) {

                List<ScalesInfo> enabledScalesList = getEnabledScalesList(transaction, succeededScalesList);
                String errors = "";
                Integer errorsCount = 0;

                processTransactionLogger.info(getLogPrefix() + "Starting sending to " + enabledScalesList.size() + " scales...");
                for (ScalesInfo scales : enabledScalesList) {

                    processTransactionLogger.info(getLogPrefix() + "Sending to scales " + scales.port);
                    if(scales.port != null) {
                        DataSocket socket = new DataSocket(scales.port, 20304);
                        try {

                            socket.open();

                            short weightCode = getWeightCode(scales);
                            if (transaction.snapshot) {
                                processTransactionLogger.info(getLogPrefix() + "Deleting all plu at scales " + scales.port);
                                int reply = deleteAllPlu(socket);
                                if(reply != 0) {
                                    errors += String.format("Deleting all plu failed. Error: %s\n", getErrorMessage(reply));
                                } else {
                                    String errorTouch = deleteTouchSpeedKeys(socket);
                                    if(errorTouch != null)
                                        errors += String.format("Deleting touch speed keys failed. Error: %s\n", errorTouch);
                                    }
                            }

                            if(errors.isEmpty()) {
                                for (ScalesItemInfo item : transaction.itemsList) {
                                    if(errorsCount < 5) {
                                        int barcode = getBarcode(item);
                                        int pluNumber = getPluNumber(item.pluNumber, barcode);
                                        processTransactionLogger.info(String.format(getLogPrefix() + "Sending item %s to scales %s", barcode, scales.port));
                                        //TODO: временно extraPercent не передаём - тестируем сначала на MassaK (не забыть убрать после отмашки)
                                        BigDecimal extraPercent = null;//item.extraPercent;
                                        int reply = sendItem(socket, item, weightCode, pluNumber, barcode, item.name,
                                        item.price == null ? 0 : item.price.multiply(BigDecimal.valueOf(priceMultiplier)).intValue(),
                                                HandlerUtils.trim(item.description, null, descriptionLength - 1), extraPercent);
                                        if (reply != 0) {
                                            errors += String.format("Send item %s failed. Error: %s\n", pluNumber, getErrorMessage(reply));
                                            errorsCount++;
                                        } else {
                                            String errorTouch = sendTouchPlu(socket, pluNumber);
                                            if (errorTouch != null) {
                                                errors += String.format("Send item touch %s failed. Error: %s\n", pluNumber, errorTouch);
                                                errorsCount++;
                                            }
                                        }
                                    }
                                }

                                if(errors.isEmpty()) {
                                    String errorTouch = sendTouchSpeedKeys(socket, transaction.itemsList);
                                    if (errorTouch != null) {
                                        errors += String.format("Send speed keys failed. Error: %s\n", errorTouch);
                                        errorsCount++;
                                    }
                                }
                            }

                            if (errors.isEmpty())
                                succeededScalesList.add(scales);
                            else
                                exception = new RuntimeException(errors);

                        } catch (Exception e) {
                            exception = e;
                        } finally {
                            processTransactionLogger.info(getLogPrefix() + "Finally disconnecting... " + scales.port);
                            socket.close();
                        }
                    }
                }

                sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(succeededScalesList, exception));
            }
        }
        return sendTransactionBatchMap;
    }

    private short getWeightCode(MachineryInfo scales) {
        try {
            String weightCode = scales instanceof ScalesInfo ? ((ScalesInfo) scales).weightCodeGroupScales : null;
            return weightCode == null ? 1 : Short.parseShort(weightCode);
        } catch (Exception e) {
            return 1;
        }
    }

    private int sendItem(DataSocket socket, ScalesItemInfo item, short weightCode, int pluNumber, int barcode, String name, int price, String description, BigDecimal extraPercent) throws IOException {
        int descriptionLength = description == null ? 0 : (description.length() + 1);
        ByteBuffer bytes = ByteBuffer.allocate(160 + descriptionLength);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        //header (10 bytes)
        bytes.put(getBytes("W"));
        bytes.put(getBytes("L"));
        bytes.putInt(0); //address
        bytes.put(getBytes(","));
        bytes.putShort((short) (147 + descriptionLength)); //data length
        bytes.put(getBytes(":"));

        //body (147 bytes + description (max 300 bytes)
        bytes.putShort(weightCode); //departmentNumber 2 bytes
        bytes.putInt(pluNumber); //pluNumber 4 bytes
        bytes.put((byte) 1); //pluType
        bytes.put(getBytes(fillSpaces(substr(name, 0, 28), 40))); //firstLine
        bytes.put(getBytes(fillSpaces(substr(name, 28, 56), 40)));//secondLine
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
        bytes.put((byte) (extraPercent == null ? 0 : extraPercent.intValue()));//tare number, исп. только в cl5000d
        bytes.putShort((short) 0); //barcode number
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

    protected String sendTouchPlu(DataSocket socket, int pluNumber) throws IOException {
        return null;
    }

    protected String sendTouchSpeedKeys(DataSocket socket, List<ScalesItemInfo> itemsList) throws IOException {
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
                processTransactionLogger.error(getLogPrefix() + "receive reply error", e);
                future.cancel(true);
                result = -2;
            }
            return result;
        } catch (Exception e) {
            processTransactionLogger.error(getLogPrefix() + "receive reply error", e);
            return -1;
        }
    }

    private String fillSpaces(Object value, int length) {
        String result = value == null ? "" : String.valueOf(value);
        if (result.length() > length)
            result = result.substring(0, length);
        while (result.length() < length)
            result += " ";
        return result;
    }

    private String substr(String value, int from, int to) {
        return value == null || value.length() < from ? null : value.substring(from, Math.min(to, value.length()));
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

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoList) throws IOException {

        if (stopListInfo != null && !stopListInfo.exclude) {
            processStopListLogger.info(getLogPrefix() + "Send StopList # " + stopListInfo.number);
            if (!machineryInfoList.isEmpty()) {
                for (MachineryInfo scales : machineryInfoList) {
                    if (scales.port != null) {
                        DataSocket socket = new DataSocket(scales.port, 20304);
                        try {
                            processStopListLogger.info(getLogPrefix() + "Sending StopList to scale " + scales.port);
                            socket.open();
                            short weightCode = getWeightCode(scales);
                            for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                                int pluNumber = getPluNumber(item.pluNumber, getBarcode(item));
                                processStopListLogger.error(String.format(getLogPrefix() + "Sending StopList - Deleting item %s at scales %s", pluNumber, scales.port));
                                int reply = deletePlu(socket, weightCode, pluNumber);
                                if (reply != 0)
                                    processStopListLogger.error(String.format(getLogPrefix() + "Failed to delete item %s at scales %s", getErrorMessage(pluNumber), scales.port));
                            }

                        } catch (Exception e) {
                            processStopListLogger.error(String.format(getLogPrefix() + "Send StopList %s to scales %s error", stopListInfo.number, scales.port), e);
                        } finally {
                            processStopListLogger.info(getLogPrefix() + "Finally disconnecting..." + scales.port);
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
}