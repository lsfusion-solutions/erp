package equ.clt.handler.rbs;

import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.handler.MultithreadScalesHandler;
import equ.clt.handler.TCPPort;
import lsfusion.base.ExceptionUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;

import static equ.clt.ProcessMonitorEquipmentServer.notInterruptedTransaction;

public class RBS4010ButtonHandler extends MultithreadScalesHandler {
    
    private static class Result {
        protected String message;
        
        Result(String message) {
            this.message = message;
        }
        
        @Override
        public String toString() {
            return "message: " + (StringUtils.isEmpty(message) ? "null" : message);
        }
        
        static class Success extends Result {
            public Success() {
                super(null);
            }
        }
        
        static class Error extends Result {
            public Error(String message) {
                super(message);
            }
        }
    
        static class Data extends Result {
            Object result;
        
            public Data(Object result) {
                super(null);
                this.result = result;
            }
        }
        
    }
    
    private class Packet {
        int timeout = 10000;
        byte[] prefix;
        byte result;
        ByteBuffer data;
    }
    
    private static final byte[] prefix_fe = new byte[] {(byte)0xff, (byte)0xfe};
    private static final byte[] prefix_fa = new byte[] {(byte)0xff, (byte)0xfa};
    private static final byte[] prefix_fb = new byte[] {(byte)0xff, (byte)0xfb};
    private static final byte[] suffix = new byte[] {(byte)0xff, (byte)0xee};
    
    private TCPPort port;
    private RBS4010ButtonSettings settings;
    
    protected FileSystemXmlApplicationContext springContext;
    
    public RBS4010ButtonHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }
    
    private static String errorString(int code) {
        if (code == 0x15)
            return "Ошибка 0x15";
        else
            return "Неизвестная ошибка";
    }
    
    @Override
    protected String getLogPrefix() {
        return "RBS4010Button: ";
    }
    
    private Result sendPacket(Packet packet) {
    
        ByteBuffer bytes = ByteBuffer.allocate(packet.prefix.length + suffix.length + packet.data.capacity());
        bytes.put(packet.prefix);
        bytes.put(packet.data.array());
        bytes.put(suffix);
        
        try {
            processTransactionLogger.info(getLogPrefix() + String.format("ip: %s >> %s", port.getAddress(), Hex.encodeHexString(bytes.array())));
        
            port.getOutputStream().write(bytes.array());
            port.getOutputStream().flush();
        } catch (IOException e) {
            return new Result.Error(e.getMessage());
        }
    
        return readPacket(packet);
    }
    
    private Result readPacket(Packet packet) {
        
        try {
            port.setSoTimeout(packet.timeout);
        } catch (Exception e) {
            return new Result.Error(e.getMessage());
        }
        
        byte[] b = new byte[1];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        long started = System.currentTimeMillis();
    
        while (true) {
        
            try {
                int bytes = port.getInputStream().read(b, 0, 1);
            
                long elapsed = System.currentTimeMillis() - started;
            
                if (elapsed > packet.timeout) {
                    return new Result.Error("Socket read timeout");
                }
            
                if (bytes > 0) {
                
                    started = System.currentTimeMillis();
                
                    baos.write(b);
                    
                    if (checkBuffer(baos)) {
    
                        byte[] buf = baos.toByteArray();
                        int length = buf[2];
                        if (length == 1)
                            packet.result = buf[4];
                        else {
                            packet.data = ByteBuffer.allocate(length);
                            packet.data.put(buf,4, length);
                        }
                        
                        baos.reset();
    
                        if (packet.result != 0x06)
                            return new Result.Error(String.format("Error: %02X", packet.result));
                        else
                            return new Result.Success();
                    }
                }
            } catch (IOException e) {
                processTransactionLogger.error(getLogPrefix() + String.format("ip %s, error: %s", port.getAddress(), e.getMessage()));
                return new Result.Error(e.getMessage());
            }
        }
    }
    
    private boolean checkBuffer(ByteArrayOutputStream baos) {
        
        // ff fe 01 00 06 ff ee
        
        byte[] buffer = baos.toByteArray();
        
        if (buffer.length == 0)
            return false;
        
        if (buffer[0] != (byte)0xff) {
            baos.reset();
            return false;
        }
    
        if (buffer.length < 7)
            return false;
        
        return buffer[1] == (byte)0xfe && buffer[buffer.length - 2] == (byte)0xff && buffer[buffer.length -1] == (byte)0xee;
    }
    
    private Result clearProducts() {
    
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("url", "delete");
        jsonObject.put("type", "product");
    
        String jsonString = jsonObject.toString();
        
        Packet packet = new Packet();
        packet.prefix = prefix_fe;
        byte[] jsonBytes = jsonString.getBytes();
        packet.data = ByteBuffer.allocate(2 + jsonBytes.length);
        packet.data.order(ByteOrder.LITTLE_ENDIAN);
        packet.data.putShort((short)jsonBytes.length);
        packet.data.put(jsonBytes);
    
        return sendPacket(packet);
    }
    
    
    private Result sendProduct(ScalesItem item) {
        
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("url", "post");
        jsonObject.put("type", "product");
        
        JSONObject body = new JSONObject();
        String pluNUmber = getPluNumber(item);
        body.put("PLUNumber", pluNUmber);
        body.put("ProductType", 0);
        if (item.name != null) {
            String name1 = item.name;
            name1 = name1.replace("\"", "'");
            body.put("Name1", name1);
        }
        body.put("ProductCode", pluNUmber);
        body.put("UnitPrice", item.price);
        //body.put("TareID", "");
        body.put("PackedDaysID", 1); // 0: Manual or 1: Print Date or 2: Production Date or 3: Packed Date
        //body.put("PackedDate", "");
        //body.put("PackedDayscount", "");
        body.put("UseByDaysID", 0); // 0: Manual or 1: Print Date or 2: Production Date or 3: Packed Date
        //product.sellByDate = item.expiryDate == null ? null : item.expiryDate.format(DateTimeFormatter.ofPattern("dd-MM-yy")); //? Дата срока годности. Формат "DD-MM-YY"
        body.put("UseByDate", item.expiryDate == null ? null : item.expiryDate.format(DateTimeFormatter.ofPattern("dd-MM-yy")));
        //body.put("UseByDaysCount", "");
        //body.put("ProductionDaysID", "");
        //body.put("ProductionDate", "");
        //body.put("ProductionDayscount", "");
        //body.put("PriceUnit", "");
        //body.put("Quantity", "");
        //body.put("PrintFormat", "");
        if (item.description != null) {
            String extraText1 = item.description;
            extraText1 = extraText1.replace("\"", "'");
            body.put("ExtraText1", extraText1);
        }
        //body.put("GroupID", "");
        //body.put("DepartmentID", "");
        
        jsonObject.put("body", body);
        
        String jsonString = jsonObject.toString();
        
        Packet packet = new Packet();
        packet.prefix = prefix_fe;
        byte[] jsonBytes = jsonString.getBytes();
        packet.data = ByteBuffer.allocate(2 + jsonBytes.length);
        packet.data.order(ByteOrder.LITTLE_ENDIAN);
        packet.data.putShort((short)jsonBytes.length);
        packet.data.put(jsonBytes);
        
        return sendPacket(packet);
    }
    
    
    private String getPluNumber(ScalesItem item) {
        return item.pluNumber != null ? String.valueOf(item.pluNumber) : item.idBarcode != null ? item.idBarcode : "";
    }
    
    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        return new RBS4010ButtonSendTransactionTask(transaction, scales);
    }
    
    private static boolean interrupted = false; //прерываем загрузку в рамках одной транзакции. Устанавливается при interrupt exception и сбрасывается при release
    
    class RBS4010ButtonSendTransactionTask extends SendTransactionTask {
        public RBS4010ButtonSendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
            super(transaction, scales);
            initSettings();
        }
        
        @Override
        protected SendTransactionResult run() {
            
            String[] hostPort = scales.port.split(":");
            port = hostPort.length == 1 ? new TCPPort(scales.port, 2000) : new TCPPort(hostPort[0], Integer.parseInt(hostPort[1]));
    
            String error = null;
            boolean cleared = false;
            
            try {

                processTransactionLogger.info(getLogPrefix() + String.format("Connect, ip %s, transaction %s", scales.port, transaction.id));
                port.open();
    
                Result result = new Result.Success();
    
                boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
                if (needToClear) {
                    result = clearProducts();
                    cleared = result instanceof Result.Error;
                }
    
                if (cleared || !needToClear) {
                    processTransactionLogger.info(getLogPrefix() + String.format("transaction %s, ip %s, sending %s items...", transaction.id, scales.port, transaction.itemsList.size()));
    
                    if (result instanceof Result.Success) {
    
                        Packet packet = new Packet();
                        packet.prefix = prefix_fa;
                        packet.data = ByteBuffer.allocate(2 + 3 + "PRODUCT".length());
                        packet.data.order(ByteOrder.LITTLE_ENDIAN);
                        packet.data.putShort((short)transaction.itemsList.size());
                        packet.data.put((byte)0x00);
                        packet.data.put((byte)0x00);
                        packet.data.put((byte)0x00);
                        packet.data.put("PRODUCT".getBytes());
                        
                        result = sendPacket(packet);
                        if (result instanceof Result.Error) {
                            error = result.message;
                            processTransactionLogger.error(getLogPrefix() + String.format("ip %s, error: %s", scales.port, error));
                        }
                        else {
                            
                            int count = 0;
    
                            for (ScalesItem item : transaction.itemsList) {
                                count++;
        
                                if (notInterruptedTransaction(transaction.id)) {
                                    processTransactionLogger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
            
                                    result = sendProduct(item);
            
                                    if (result instanceof Result.Error) {
                                        error = result.message;
                                        processTransactionLogger.error(getLogPrefix() + String.format("ip %s, item idBarcode: %s, error: %s", scales.port, item.idBarcode,  error));
                                        break;
                                    }
                                } else break;
                            }
    
                            packet = new Packet();
                            packet.prefix = prefix_fb;
                            packet.data = ByteBuffer.allocate(2);
                            packet.data.put((byte)0x00);
                            packet.data.put((byte)0x00);
    
                            result = sendPacket(packet);
                            if (result instanceof Result.Error) {
                                error = result.message;
                                processTransactionLogger.error(getLogPrefix() + String.format("ip %s, error: %s", scales.port, error));
                            }
                        }
                    }
                }

                processTransactionLogger.info(getLogPrefix() + String.format("Disconnect, ip %s, transaction %s", scales.port, transaction.id));
                port.close();
            }
            catch (Throwable t) {
                interrupted = t instanceof InterruptedException;
                error = String.format(getLogPrefix() + "ip %s error, transaction %s: %s", scales.port, transaction.id, ExceptionUtils.getStackTraceString(t));
                processTransactionLogger.error(error);
            }
            finally {
                try {
                    processTransactionLogger.info(getLogPrefix() + String.format("Disconnect, ip %s, transaction %s", scales.port, transaction.id));
                    port.close();
                } catch (CommunicationException ignored) {}
            }

            processTransactionLogger.info(getLogPrefix() + "Completed ip: " + scales.port);
            return new SendTransactionResult(scales, error != null ? Collections.singletonList(error) : new ArrayList<>(), interrupted, cleared);
        }
    
        protected void initSettings() {
            settings = springContext.containsBean("rbsSettings") ? (RBS4010ButtonSettings) springContext.getBean("rbsSettings") : new RBS4010ButtonSettings();
        }
    }
}
