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
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class CL5000JHandler extends DefaultScalesHandler {

    private final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");

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
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) throws IOException {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        if (transactionList.isEmpty()) {
            processTransactionLogger.error("CL5000: Empty transaction list!");
        }
        for (TransactionScalesInfo transaction : transactionList) {
            processTransactionLogger.info("CL5000: Send Transaction # " + transaction.id);

            List<MachineryInfo> succeededScalesList = new ArrayList<>();
            Exception exception = null;

            if (!transaction.machineryInfoList.isEmpty()) {

                List<ScalesInfo> enabledScalesList = getEnabledScalesList(transaction, succeededScalesList);
                String errors = "";
                Integer errorsCount = 0;

                processTransactionLogger.info("CL5000: Starting sending to " + enabledScalesList.size() + " scales...");
                for (ScalesInfo scales : enabledScalesList) {

                    processTransactionLogger.info("CL5000: Sending to scales " + scales.port);
                    if(scales.port != null) {
                        DataSocket socket = new DataSocket(scales.port, 20304);
                        try {

                            socket.open();

                            short weightCode = getWeightCode(scales);
                            if (transaction.snapshot) {
                                processTransactionLogger.info("CL5000: Deleting all plu at scales " + scales.port);
                                int reply = deleteAllPlu(socket);
                                if(reply != 0)
                                    errors += String.format("Deleting all plu failed. Error: %s\n", getErrorMessage(reply));
                            }

                            if(errors.isEmpty()) {
                                for (ScalesItemInfo item : transaction.itemsList) {
                                    if(errorsCount < 5) {
                                        int barcode = Integer.parseInt(item.idBarcode.substring(0, 5));
                                        int pluNumber = item.pluNumber == null ? barcode : item.pluNumber;
                                        processTransactionLogger.info(String.format("CL5000: Sending item %s to scales %s", barcode, scales.port));
                                        //TODO: временно extraPercent не передаём - тестируем сначала на MassaK (не забыть убрать после отмашки)
                                        BigDecimal extraPercent = null;//item.extraPercent;
                                        int reply = sendItem(socket, weightCode, pluNumber, barcode, item.name,
                                        item.price == null ? 0 : item.price.multiply(BigDecimal.valueOf(100)).intValue(),
                                                HandlerUtils.trim(item.description, null, descriptionLength - 1), extraPercent);
                                        if (reply != 0) {
                                            errors += String.format("Send item %s failed. Error: %s\n", pluNumber, getErrorMessage(reply));
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
                            exception = e;
                        } finally {
                            processTransactionLogger.info("CL5000: Finally disconnecting... " + scales.port);
                            try {
                                socket.close();
                            } catch (CommunicationException e) {
                                processTransactionLogger.info("CL5000PrintHandler close port error: ", e);
                            }
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

    private int sendItem(DataSocket socket, short weightCode, int pluNumber, int barcode, String name, int price, String description, BigDecimal extraPercent) throws IOException, CommunicationException {
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
        bytes.putInt(0); //sell by date
        bytes.put((byte) 0); //sell by time
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

    private int deleteAllPlu(DataSocket socket) throws IOException, CommunicationException {
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

    private int deletePlu(DataSocket socket, short weightCode, int pluNumber) throws IOException, CommunicationException {
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
        int attempts = 0;
        while (attempts < 3) {
            try {
                socket.outputStream.write(bytes);
                return receiveReply(socket);
            } catch (CommunicationException e) {
                attempts++;
                if (attempts == 3)
                    processTransactionLogger.error("CL5000 SendCommand Error: ", e);
            }
        }
        return -1;
    }

    private int receiveReply(DataSocket socket) throws CommunicationException {
        try {
            byte[] buffer = new byte[255];
            socket.inputStream.read(buffer);
            return buffer[14] == 58 ? 0 : buffer[14]; //это либо байт ошибки, либо первый байт хвоста (:)
        } catch (Exception e) {
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
            processStopListLogger.info("CL5000: Send StopList # " + stopListInfo.number);
            if (!machineryInfoList.isEmpty()) {
                for (MachineryInfo scales : machineryInfoList) {
                    if (scales.port != null) {
                        DataSocket socket = new DataSocket(scales.port, 20304);
                        try {
                            processStopListLogger.info("CL5000: Sending StopList to scale " + scales.port);
                            socket.open();
                            short weightCode = getWeightCode(scales);
                            for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                                int barcode = Integer.parseInt(item.idBarcode.substring(0, 5));
                                int pluNumber = item.pluNumber == null ? barcode : item.pluNumber;
                                processStopListLogger.error(String.format("CL5000: Sending StopList - Deleting item %s at scales %s", pluNumber, scales.port));
                                int reply = deletePlu(socket, weightCode, pluNumber);
                                if (reply != 0)
                                    processStopListLogger.error(String.format("CL5000: Failed to delete item %s at scales %s", getErrorMessage(pluNumber), scales.port));
                            }

                        } catch (Exception e) {
                            processStopListLogger.error(String.format("CL5000: Send StopList %s to scales %s error", stopListInfo.number, scales.port), e);
                        } finally {
                            processStopListLogger.info("CL5000: Finally disconnecting..." + scales.port);
                            try {
                                socket.close();
                            } catch (CommunicationException e) {
                                processStopListLogger.info("CL5000 close port error: ", e);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) throws IOException {
        String groupId = "";
        for (MachineryInfo scales : transactionInfo.machineryInfoList) {
            groupId += scales.port + ";";
        }
        return "CL5000J" + groupId;
    }

    private byte[] getBytes(String value) throws UnsupportedEncodingException {
        return value.getBytes("cp1251");
    }

    private String getErrorMessage(int errorNumber) {
        switch(errorNumber) {
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