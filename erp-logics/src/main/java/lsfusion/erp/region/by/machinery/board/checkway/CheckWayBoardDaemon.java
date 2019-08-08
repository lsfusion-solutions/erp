package lsfusion.erp.region.by.machinery.board.checkway;

import lsfusion.erp.region.by.machinery.board.BoardDaemon;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.base.controller.lifecycle.LifecycleEvent;
import lsfusion.server.logics.*;
import lsfusion.server.language.ScriptingBusinessLogics;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.physics.exec.db.controller.manager.DBManager;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.util.Assert;

import java.io.*;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class CheckWayBoardDaemon extends BoardDaemon {

    private ScriptingLogicsModule LM;
    private Map<InetAddress, String> ipMap = new HashMap<>();

    public CheckWayBoardDaemon(ScriptingBusinessLogics businessLogics, DBManager dbManager, LogicsInstance logicsInstance) {
        super(businessLogics, dbManager, logicsInstance);
    }

    @Override
    protected void onInit(LifecycleEvent event) {
        LM = logicsInstance.getBusinessLogics().getModule("CheckWayBoard");
        Assert.notNull(LM, "can't find CheckWayBoard module");
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        startLogger.info("Starting " + getEventName() + " Daemon");
        try (DataSession session = createSession()) {
            String host = (String) LM.findProperty("hostCheckWayBoard[]").read(session);
            Integer port = (Integer) LM.findProperty("portCheckWayBoard[]").read(session);
            setupDaemon(dbManager, host, port != null ? port : 9102);
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw new RuntimeException("Error starting " + getEventName() + " Daemon: ", e);
        }
    }

    @Override
    public String getEventName() {
        return "checkway-board";
    }

    @Override
    protected Callable getCallable(Socket socket) {
        return new SocketCallable(businessLogics, socket);
    }

    public class SocketCallable implements Callable {

        private BusinessLogics BL;
        private Socket socket;

        public SocketCallable(BusinessLogics BL, Socket socket) {
            this.BL = BL;
            this.socket = socket;
        }

        @Override
        public Object call() {

            DataInputStream inFromClient = null;
            DataOutputStream outToClient = null;
            try {

                inFromClient = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                outToClient = new DataOutputStream(socket.getOutputStream());
                byte firstByte = inFromClient.readByte();

                if(firstByte != 0) {

                    StringBuilder barcode = new StringBuilder();
                    byte b;
                    while((b = inFromClient.readByte()) != 13) // /r
                        barcode.append((char) b);

                    //getHostAddress is slow operation, so we use map
                    InetAddress inetAddress = socket.getInetAddress();
                    String ip = ipMap.get(inetAddress);
                    if(ip == null) {
                        ip = inetAddress.getHostAddress();
                        ipMap.put(inetAddress, ip);
                    }
                    Result result = readMessage(barcode.toString(), ip);
                    outToClient.write(result.bytes);
                    priceCheckerLogger.info(String.format("%s succeeded request ip %s, barcode %s, reply %s", getEventName(), ip, barcode.toString(), new String(result.bytes, 3, result.bytes.length - 3, result.charset)));
                }
                Thread.sleep(1000);
                return null;
            } catch (SocketTimeoutException ignored) {
            } catch (Exception e) {
                e.printStackTrace();
                priceCheckerLogger.error("CheckWayBoard Error: ", e);
            } finally {
                try {
                    if (outToClient != null)
                        outToClient.close();
                    if (inFromClient != null)
                        inFromClient.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    priceCheckerLogger.error("CheckWayBoard Error occurred: ", e);
                }
            }
            return null;
        }

        private Result readMessage(String idBarcode, String ip) throws SQLException, UnsupportedEncodingException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
            priceCheckerLogger.info(String.format("CheckWay request ip %s, barcode %s", ip, idBarcode));
            //хак. Иногда приходит штрихкод, начинающийся с F
            if(idBarcode.startsWith("F"))
                idBarcode = idBarcode.substring(1);
            try (DataSession session = createSession()) {

                String weightPrefix = (String) LM.findProperty("weightPrefixIP").read(session, new DataObject(ip));
                String piecePrefix = (String) LM.findProperty("piecePrefixIP").read(session, new DataObject(ip));
                if (idBarcode.length() == 13 && (weightPrefix != null && idBarcode.startsWith(weightPrefix) || piecePrefix != null && idBarcode.startsWith(piecePrefix)))
                    idBarcode = idBarcode.substring(2, 7);
                ObjectValue stockObject = LM.findProperty("stockIP[STRING[100]]").readClasses(session, new DataObject(ip));
                ObjectValue skuObject = LM.findProperty("skuBarcode[STRING[15]]").readClasses(session, new DataObject(idBarcode));
                String charset = (String) LM.findProperty("charsetIP[STRING[100]]").read(session, new DataObject(ip));
                if(charset == null)
                    charset = "utf8";

                String error = null;
                if(skuObject instanceof NullValue)
                    error = "Штрихкод не найден";
                if(stockObject instanceof NullValue)
                    error = "Неверные параметры сервера";

                if (error == null) {
                    String captionItem = (String) LM.findProperty("name[Item]").read(session, skuObject);
                    byte[] captionBytes = getTextBytes(captionItem, 20, charset);

                    BigDecimal price = (BigDecimal) LM.findProperty("transactionPrice[Sku,Stock]").read(session, skuObject, stockObject);
                    if (price == null || price.equals(BigDecimal.ZERO)) {
                        error = "Штрихкод не найден";
                    } else {
                        return new Result(getPriceBytes(captionBytes, price, charset), charset);
                    }
                }
                return new Result(getErrorBytes(error, charset), charset);
            }
        }

        private byte[] getPriceBytes(byte[] captionBytes, BigDecimal price, String charset) throws UnsupportedEncodingException {
            byte[] priceBytes = formatPrice(price).getBytes(charset);
            ByteBuffer bytes = ByteBuffer.allocate(18 + captionBytes.length + priceBytes.length);

            bytes.put(new byte[] {(byte) 0x1b, (byte) 0x2e, (byte) 0x30, (byte) 0x1b, (byte) 0x42, (byte) 0x30, (byte) 0x30}); //normal font size
            bytes.put(new byte[] {(byte) 0x1b, (byte) 0x27, (byte) 0x30, (byte) 0x30}); //clear screen, cursor top left

            bytes.put(captionBytes);

            bytes.put(new byte[] {(byte) 0x1b, (byte) 0x42, (byte) 0x31}); //large font size
            bytes.put(new byte[] {(byte) 0x1b, (byte) 0x2e, (byte) 0x37}); //align right bottom
            bytes.put(priceBytes);

            bytes.put((byte) 0x03); //end
            return bytes.array();
        }

        private byte[] getErrorBytes(String error, String charset) throws UnsupportedEncodingException {
            byte[] errorBytes = getTextBytes(error, 10, charset);

            ByteBuffer bytes = ByteBuffer.allocate(12 + errorBytes.length);

            bytes.put(new byte[] {(byte) 0x1b, (byte) 0x2e, (byte) 0x30, (byte) 0x1b, (byte) 0x42, (byte) 0x30, (byte) 0x30}); //normal font size
            bytes.put(new byte[] {(byte) 0x1b, (byte) 0x27, (byte) 0x30, (byte) 0x30}); //clear screen, cursor top left
            bytes.put(errorBytes);

            bytes.put((byte) 0x03); //end
            return bytes.array();
        }

        private byte[] getTextBytes(String text, int lineLength, String charset) throws UnsupportedEncodingException {
            List<Byte> bytes = new ArrayList<>();
            String[] words = text.split(" ");
            String line = "";
            byte carriageReturn = 0x0d;
            int i = 0;
            for (String word : words) {
                String separator = line.isEmpty() ? "" : " ";
                if ((line.length() + word.length() + separator.length()) <= lineLength)
                    line += separator + word;
                else {
                    if (i == 6)
                        break;
                    for (byte b : line.getBytes(charset)) {
                        bytes.add(b);
                    }
                    bytes.add(carriageReturn);

                    line = word;
                    i++;

                }
            }
            if (i != 6 && !line.isEmpty()) {
                for (byte b : line.getBytes(charset)) {
                    bytes.add(b);
                }
                bytes.add(carriageReturn);
            }
            return ArrayUtils.toPrimitive(bytes.toArray(new Byte[bytes.size()]));
        }

        private class Result {
            byte[] bytes;
            String charset;

            public Result(byte[] bytes, String charset) {
                this.bytes = bytes;
                this.charset = charset;
            }
        }
    }
}