package lsfusion.erp.region.by.machinery.board.newland;

import lsfusion.erp.region.by.machinery.board.BoardDaemon;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.lifecycle.LifecycleEvent;
import lsfusion.server.logics.*;
import lsfusion.server.logics.scripted.ScriptingBusinessLogics;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.commons.lang.ArrayUtils;
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

public class NewLandBoardDaemon extends BoardDaemon {

    private ScriptingLogicsModule LM;
    private static String charset = "utf8";
    private Map<InetAddress, String> ipMap = new HashMap<>();

    public NewLandBoardDaemon(ScriptingBusinessLogics businessLogics, DBManager dbManager, LogicsInstance logicsInstance) {
        super(businessLogics, dbManager, logicsInstance);
    }

    @Override
    protected void onInit(LifecycleEvent event) {
        LM = logicsInstance.getBusinessLogics().getModule("NewLandBoard");
        Assert.notNull(LM, "can't find NewLandBoard module");
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        startLogger.info("Starting " + getEventName() + " Daemon");
        try (DataSession session = createSession()) {
            String host = (String) LM.findProperty("hostNewLandBoard[]").read(session);
            Integer port = (Integer) LM.findProperty("portNewLandBoard[]").read(session);
            setupDaemon(dbManager, host, port != null ? port : 2005);
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw new RuntimeException("Error starting " + getEventName() + " Daemon: ", e);
        }
    }

    @Override
    public String getEventName() {
        return "newland-board";
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
                String barcode = inFromClient.readLine();

                if(barcode != null) {
                    //getHostAddress is slow operation, so we use map
                    InetAddress inetAddress = socket.getInetAddress();
                    String ip = ipMap.get(inetAddress);
                    if(ip == null) {
                        ip = inetAddress.getHostAddress();
                        ipMap.put(inetAddress, ip);
                    }
                    barcode = barcode.length() > 1 ? barcode.substring(1) : barcode;
                    byte[] message = readMessage(barcode, ip);
                    outToClient.write(message);
                    priceCheckerLogger.info(String.format("%s succeeded request ip %s, barcode %s, reply %s", getEventName(), ip, barcode, new String(message, 3, message.length - 3, charset)));
                }
                Thread.sleep(1000);
                return null;
            } catch (SocketTimeoutException ignored) {
            } catch (Exception e) {
                priceCheckerLogger.error(getEventName() + " error: ", e);
            } finally {
                try {
                    if (outToClient != null)
                        outToClient.close();
                    if (inFromClient != null)
                        inFromClient.close();
                } catch (IOException e) {
                    priceCheckerLogger.error(getEventName() + " error: ", e);
                }
            }
            return null;
        }

        private byte[] readMessage(String idBarcode, String ip) throws SQLException, UnsupportedEncodingException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
            priceCheckerLogger.info(String.format("%s request ip %s, barcode %s", getEventName(), ip, idBarcode));
            //хак. Иногда приходит штрихкод, начинающийся с F
            if(idBarcode.startsWith("F"))
                idBarcode = idBarcode.substring(1);
            try (DataSession session = createSession()) {

                String weightPrefix = (String) LM.findProperty("weightPrefixIP").read(session, new DataObject(ip));
                String piecePrefix = (String) LM.findProperty("piecePrefixIP").read(session, new DataObject(ip));
                if (idBarcode.length() == 13 && (weightPrefix != null && idBarcode.startsWith(weightPrefix) || piecePrefix != null && idBarcode.startsWith(piecePrefix)))
                    idBarcode = idBarcode.substring(2, 7);
                ObjectValue stockObject = LM.findProperty("stockIP[VARSTRING[100]]").readClasses(session, new DataObject(ip));
                ObjectValue skuObject = LM.findProperty("skuBarcode[VARSTRING[15]]").readClasses(session, new DataObject(idBarcode));

                String error = null;
                if(skuObject instanceof NullValue)
                    error = "Штрихкод не найден";
                if(stockObject instanceof NullValue)
                    error = "Неверные параметры сервера";

                if (error == null) {
                    String captionItem = (String) LM.findProperty("name[Item]").read(session, skuObject);
                    byte[] captionBytes = getTextBytes(captionItem, 20);

                    BigDecimal price = (BigDecimal) LM.findProperty("transactionPrice[Sku,Stock]").read(session, skuObject, stockObject);
                    if (price == null || price.equals(BigDecimal.ZERO)) {
                        error = "Штрихкод не найден";
                    } else {
                        return getPriceBytes(captionBytes, price);
                    }
                }
                return getErrorBytes(error);
            }
        }

        private byte[] getPriceBytes(byte[] captionBytes, BigDecimal price) throws UnsupportedEncodingException {
            byte[] priceBytes = formatPrice(price).getBytes(charset);
            ByteBuffer bytes = ByteBuffer.allocate(12 + captionBytes.length + priceBytes.length);

            bytes.put(new byte[] {(byte) 0x1b, (byte) 0x42, (byte) 0x30}); //normal font size
            bytes.put(new byte[] {(byte) 0x1b, (byte) 0x25}); //clear screen, cursor top left
            bytes.put(captionBytes);

            bytes.put(new byte[] {(byte) 0x1b, (byte) 0x42, (byte) 0x36}); //large font size
            bytes.put(new byte[] {(byte) 0x1b, (byte) 0x2e, (byte) 0x38 }); //align right bottom
            bytes.put(priceBytes);

            bytes.put((byte) 0x03); //end
            return bytes.array();
        }

        private byte[] getErrorBytes(String error) throws UnsupportedEncodingException {
            byte[] errorBytes = getTextBytes(error, 10);

            ByteBuffer bytes = ByteBuffer.allocate(9 + errorBytes.length);

            bytes.put(new byte[] {(byte) 0x1b, (byte) 0x42, (byte) 0x31}); //big font size
            bytes.put(new byte[] {(byte) 0x1b, (byte) 0x25}); //clear screen, cursor top left
            bytes.put(new byte[] {(byte) 0x1b, (byte) 0x2e, (byte) 0x34}); //align center
            bytes.put(errorBytes);

            bytes.put((byte) 0x03); //end
            return bytes.array();
        }

        private byte[] getTextBytes(String text, int lineLength) throws UnsupportedEncodingException {
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
    }
}