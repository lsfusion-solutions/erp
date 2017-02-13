package lsfusion.erp.region.by.machinery.board.newland;

import lsfusion.erp.region.by.machinery.board.BoardDaemon;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.*;
import lsfusion.server.logics.scripted.ScriptingBusinessLogics;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.session.DataSession;
import org.apache.commons.lang.ArrayUtils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class NewLandBoardDaemon extends BoardDaemon {

    private static String charset = "utf8";
    private Map<InetAddress, String> ipMap = new HashMap<>();

    public NewLandBoardDaemon(ScriptingBusinessLogics businessLogics, DBManager dbManager, LogicsInstance logicsInstance) {
        super(businessLogics, dbManager, logicsInstance);
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

            try {

                BufferedReader inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                DataOutputStream outToClient = new DataOutputStream(socket.getOutputStream());
                String barcode = inFromClient.readLine();

                if(barcode != null) {
                    //getHostName is slow operation, so we use map
                    InetAddress inetAddress = socket.getInetAddress();
                    String ip = ipMap.get(inetAddress);
                    if(ip == null) {
                        ip = inetAddress.getHostName();
                        ipMap.put(inetAddress, ip);
                    }
                    byte[] message = readMessage(BL, barcode, ip);
                    outToClient.write(message);
                }

                //Thread.sleep(3000);
                outToClient.close();
                inFromClient.close();

                return null;
            } catch (Exception e) {
                logger.error("NewLandBoard Error: ", e);
            }
            return null;
        }

        private byte[] readMessage(BusinessLogics BL, String idBarcode, String ip) throws SQLException, UnsupportedEncodingException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
            try (DataSession session = dbManager.createSession()) {

                ObjectValue stockObject = BL.getModule("PriceChecker").findProperty("stockIP[VARSTRING[100]]").readClasses(session, new DataObject(ip));
                ObjectValue skuObject = BL.getModule("Barcode").findProperty("skuBarcode[VARSTRING[15]]").readClasses(session, new DataObject(idBarcode.substring(1)));

                String error = null;
                if(skuObject instanceof NullValue)
                    error = "Штрихкод не найден";
                if(stockObject instanceof NullValue)
                    error = "Неверные параметры сервера";

                if(error == null) {

                    String captionItem = (String) BL.getModule("Item").findProperty("name[Item]").read(session, skuObject);
                    byte[] captionBytes = getTextBytes(captionItem, 20);

                    BigDecimal price = (BigDecimal) BL.getModule("MachineryPriceTransaction").findProperty("transactionPrice[Sku,Stock]").read(session, skuObject, stockObject);
                    String priceItem = new DecimalFormat("###,###.##").format(price == null ? BigDecimal.ZERO : price.doubleValue());
                    byte[] priceBytes = priceItem.getBytes(charset);

                    ByteBuffer bytes = ByteBuffer.allocate(9 + captionBytes.length + priceBytes.length);
                    bytes.put((byte) 0x1b);
                    bytes.put((byte) 0x42);
                    bytes.put((byte) 0x30); //normal font size

                    bytes.put((byte) 0x1b);
                    bytes.put((byte) 0x25); //clear screen, cursor top left

                    bytes.put(captionBytes);

                    bytes.put((byte) 0x1b);
                    bytes.put((byte) 0x2e);
                    bytes.put((byte) 0x38); //align right bottom

                    bytes.put(priceBytes);
                    bytes.put((byte) 0x03); //end
                    return bytes.array();
                } else {

                    byte[] errorBytes = getTextBytes(error, 10);

                    ByteBuffer bytes = ByteBuffer.allocate(9 + errorBytes.length);
                    bytes.put((byte) 0x1b);
                    bytes.put((byte) 0x42);
                    bytes.put((byte) 0x31); //big font size

                    bytes.put((byte) 0x1b);
                    bytes.put((byte) 0x25); //clear screen, cursor top left

                    bytes.put((byte) 0x1b);
                    bytes.put((byte) 0x2e);
                    bytes.put((byte) 0x34); //align center

                    bytes.put(errorBytes);
                    bytes.put((byte) 0x03); //end
                    return bytes.array();
                }
            }
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
                    if(i == 6)
                        break;
                    for (byte b : line.getBytes(charset)) {
                        bytes.add(b);
                    }
                    bytes.add(carriageReturn);

                    line = word;
                    i++;

                }
            }
            if(i != 6 && !line.isEmpty()) {
                for (byte b : line.getBytes(charset)) {
                    bytes.add(b);
                }
                bytes.add(carriageReturn);
            }
            return ArrayUtils.toPrimitive(bytes.toArray(new Byte[bytes.size()]));
        }
    }
}