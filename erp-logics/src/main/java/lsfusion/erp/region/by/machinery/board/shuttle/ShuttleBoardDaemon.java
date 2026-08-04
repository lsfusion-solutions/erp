package lsfusion.erp.region.by.machinery.board.shuttle;

import lsfusion.base.DaemonThreadFactory;
import lsfusion.erp.ERPLoggers;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.base.controller.thread.ExecutorFactory;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.base.controller.lifecycle.LifecycleEvent;
import lsfusion.server.base.controller.manager.MonitorServer;
import lsfusion.server.logics.*;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.physics.exec.db.controller.manager.DBManager;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShuttleBoardDaemon extends MonitorServer implements InitializingBean {
    protected static final Logger startLogger = ServerLoggers.startLogger;
    protected static final Logger priceCheckerLogger = ERPLoggers.priceCheckerLogger;

    protected DBManager dbManager;
    protected LogicsInstance logicsInstance;

    private ScriptingLogicsModule LM;

    private ExecutorService acceptExecutor;
    private ExecutorService connectionExecutor;
    private ServerSocket serverSocket;
    private final Set<Socket> activeSockets = ConcurrentHashMap.newKeySet();
    private volatile boolean stopping;

    public ShuttleBoardDaemon(DBManager dbManager, LogicsInstance logicsInstance) {
        super(DAEMON_ORDER);
        this.dbManager = dbManager;
        this.logicsInstance = logicsInstance;
    }

    @Override
    protected void onInit(LifecycleEvent event) {
        LM = logicsInstance.getBusinessLogics().getModule("ShuttleBoard");
        Assert.notNull(LM, "can't find ShuttleBoard module");
    }

    @Override
    public void afterPropertiesSet() {
        Assert.notNull(dbManager, "dbManager must be specified");
        Assert.notNull(logicsInstance, "logicsInstance must be specified");
    }

    @Override
    public LogicsInstance getLogicsInstance() {
        return logicsInstance;
    }

    protected void setupDaemon(String host, Integer port) {
        try {
            serverSocket = new ServerSocket(port, 1000, host == null ? Inet4Address.getByName(Inet4Address.getLocalHost().getHostAddress()) : Inet4Address.getByName(host));
        } catch (IOException e) {
            priceCheckerLogger.error("Error starting " + getEventName() + " Daemon: ", e);
            return;
        }

        connectionExecutor = ExecutorFactory.createMonitorThreadService(100, this);
        acceptExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("shuttle-board-daemon"));
        acceptExecutor.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (!serverSocket.isClosed())
                    priceCheckerLogger.error("Error accepting " + getEventName() + " connection: ", e);
                continue;
            } catch (Throwable t) {
                ServerLoggers.systemLogger.error("Error accepting " + getEventName() + " connection: ", t);
                continue;
            }

            try {
                socket.setSoTimeout(30000);
                activeSockets.add(socket);
                // onStopping() sets `stopping` before sweeping activeSockets, so a socket accepted in that exact
                // window (added to the set too late to be swept) still gets caught and closed here instead of
                // being handed to an executor that may or may not still accept it
                if (stopping) {
                    activeSockets.remove(socket);
                    closeQuietly(socket);
                } else {
                    connectionExecutor.submit(() -> handleConnection(socket));
                }
            } catch (Throwable t) {
                // setSoTimeout failure or connectionExecutor rejecting the task (e.g. shutting down) - don't leak the accepted socket
                activeSockets.remove(socket);
                closeQuietly(socket);
                priceCheckerLogger.error("Error accepting " + getEventName() + " connection: ", t);
            }
        }
    }

    private void handleConnection(Socket socket) {
        try {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            String ip = socket.getInetAddress().getHostAddress();

            serveConnection(in, out, ip, this::readMessage);
        } catch (SocketTimeoutException ignored) {
        } catch (Throwable t) {
            priceCheckerLogger.error(getEventName(), t);
        } finally {
            activeSockets.remove(socket);
            closeQuietly(socket);
        }
    }

    private void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            priceCheckerLogger.error(getEventName() + " error closing socket: ", e);
        }
    }

    // package-private (not private) so the framing logic can be exercised directly by a protocol-level test
    // (readMessage needs a live DataSession/LM, so tests supply a stub BarcodeHandler instead)
    interface BarcodeHandler {
        Result handle(String barcode, String ip) throws Exception;
    }

    void serveConnection(InputStream in, OutputStream out, String ip, BarcodeHandler handler) throws IOException {
        int firstByte;
        while ((firstByte = in.read()) != -1) {
            if (firstByte != 0) {
                StringBuilder barcode = new StringBuilder();
                int b;
                while ((b = in.read()) != -1 && b != 13) // \r
                    barcode.append((char) b);
                if (b == -1) // connection closed mid-message
                    break;

                try {
                    Result result = handler.handle(barcode.toString(), ip);
                    out.write(result.bytes);
                    out.flush();
                    priceCheckerLogger.info(String.format("%s succeeded request ip %s, barcode %s, reply %s", getEventName(), ip, barcode, new String(result.bytes, 3, result.bytes.length - 3, result.charset)));
                } catch (Throwable t) {
                    // an error handling one request must not kill the persistent connection - the board retries the next barcode on the same socket
                    priceCheckerLogger.error(getEventName(), t);
                }
            } else {
                // matches the original Mina behavior: Mina delivered one messageReceived() call per underlying
                // socket read and silently dropped whatever was left unread in that buffer when firstByte == 0.
                // available() reports only bytes that already arrived as part of the same read, without blocking
                // for more, so this discards a same-read "ignored" frame the same way the old buffer-per-message
                // model did, while still letting a frame that arrives in a later, separate read start clean.
                int available = in.available();
                if (available > 0)
                    in.skip(available);
            }
        }
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        startLogger.info("Starting " + getEventName() + " Daemon");
        try (DataSession session = createSession()) {
            String host = (String) LM.findProperty("hostShuttleBoard[]").read(session);
            Integer port = (Integer) LM.findProperty("portShuttleBoard[]").read(session);
            setupDaemon(host != null ? host : "localhost", port != null ? port : 9101);
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw new RuntimeException("Error starting " + getEventName() + " Daemon: ", e);
        }
    }

    @Override
    protected void onStopping(LifecycleEvent event) {
        stopping = true;
        if (acceptExecutor != null)
            acceptExecutor.shutdownNow();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                priceCheckerLogger.error("Error stopping " + getEventName() + " Daemon: ", e);
            }
        }
        // shutdownNow() alone does not unblock threads parked in a blocking socket read, so close the sockets directly
        for (Socket socket : activeSockets)
            closeQuietly(socket);
        if (connectionExecutor != null)
            connectionExecutor.shutdownNow();
    }

    @Override
    public String getEventName() {
        return "shuttle-board";
    }

    private Result readMessage(String idBarcode, String ip) throws SQLException, UnsupportedEncodingException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
        priceCheckerLogger.info(String.format("Shuttle request ip %s, barcode %s", ip, idBarcode));
        //хак. Иногда приходит штрихкод, начинающийся с F
        if (idBarcode.startsWith("F"))
            idBarcode = idBarcode.substring(1);
        try (DataSession session = createSession()) {

            String weightPrefix = (String) LM.findProperty("weightPrefixIP").read(session, new DataObject(ip));
            String piecePrefix = (String) LM.findProperty("piecePrefixIP").read(session, new DataObject(ip));
            if (idBarcode.length() == 13 && (weightPrefix != null && idBarcode.startsWith(weightPrefix) || piecePrefix != null && idBarcode.startsWith(piecePrefix)))
                idBarcode = idBarcode.substring(2, 7);
            ObjectValue stockObject = LM.findProperty("stockIP[STRING[100]]").readClasses(session, new DataObject(ip));
            ObjectValue skuObject = LM.findProperty("skuBarcode[STRING[15]]").readClasses(session, new DataObject(idBarcode));
            String charset = (String) LM.findProperty("charsetIP[STRING[100]]").read(session, new DataObject(ip));
            if (charset == null)
                charset = "utf8";

            String error = null;
            if (skuObject instanceof NullValue)
                error = "Штрихкод не найден";
            if (stockObject instanceof NullValue)
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
        ByteBuffer bytes = ByteBuffer.allocate(12 + captionBytes.length + priceBytes.length);

        bytes.put(new byte[]{(byte) 0x1b, (byte) 0x42, (byte) 0x30}); //normal font size
        bytes.put(new byte[]{(byte) 0x1b, (byte) 0x25}); //clear screen, cursor top left
        bytes.put(captionBytes);

        bytes.put(new byte[]{(byte) 0x1b, (byte) 0x42, (byte) 0x36}); //large font size
        bytes.put(new byte[]{(byte) 0x1b, (byte) 0x2e, (byte) 0x38}); //align right bottom
        bytes.put(priceBytes);

        bytes.put((byte) 0x03); //end
        return bytes.array();
    }

    private byte[] getErrorBytes(String error, String charset) throws UnsupportedEncodingException {
        byte[] errorBytes = getTextBytes(error, 10, charset);

        ByteBuffer bytes = ByteBuffer.allocate(6 + errorBytes.length);

        bytes.put(new byte[]{(byte) 0x1b, (byte) 0x42, (byte) 0x31}); //big font size
        bytes.put(new byte[]{(byte) 0x1b, (byte) 0x25}); //clear screen, cursor top left
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

    protected String formatPrice(BigDecimal price) {
        return new DecimalFormat("###,###.##").format(price.doubleValue()) + " руб.";
    }

    static class Result {
        byte[] bytes;
        String charset;

        public Result(byte[] bytes, String charset) {
            this.bytes = bytes;
            this.charset = charset;
        }
    }
}
