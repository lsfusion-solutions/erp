package lsfusion.erp.region.by.machinery.board.shuttle;

import lsfusion.erp.ERPLoggers;
import lsfusion.server.ServerLoggers;
import lsfusion.server.context.ExecutorFactoryThreadInfo;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.lifecycle.LifecycleEvent;
import lsfusion.server.lifecycle.MonitorServer;
import lsfusion.server.logics.*;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;
import org.apache.mina.core.buffer.AbstractIoBuffer;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class ShuttleBoardDaemon extends MonitorServer implements InitializingBean {
    protected static final Logger startLogger = ServerLoggers.startLogger;
    protected static final Logger priceCheckerLogger = ERPLoggers.priceCheckerLogger;

    protected DBManager dbManager;
    protected LogicsInstance logicsInstance;

    private ScriptingLogicsModule LM;

    public ShuttleBoardDaemon(DBManager dbManager, LogicsInstance logicsInstance) {
        super(HIGH_DAEMON_ORDER);
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
            IoAcceptor acceptor = new NioSocketAcceptor();
            acceptor.setHandler(new ShuttleHandler());
            acceptor.getSessionConfig().setReadBufferSize(15);
            acceptor.bind(new InetSocketAddress(host, port));
        } catch (IOException e) {
            priceCheckerLogger.error("Error starting " + getEventName() + " Daemon: ", e);
        }
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        startLogger.info("Starting " + getEventName() + " Daemon");
        try (DataSession session = dbManager.createSession()) {
            String host = (String) LM.findProperty("hostShuttleBoard[]").read(session);
            Integer port = (Integer) LM.findProperty("portShuttleBoard[]").read(session);
            setupDaemon(host != null ? host : "localhost", port != null ? port : 9101);
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw new RuntimeException("Error starting " + getEventName() + " Daemon: ", e);
        }
    }

    @Override
    public String getEventName() {
        return "shuttle-board";
    }

    public class ShuttleHandler extends IoHandlerAdapter {

        @Override
        public void exceptionCaught(IoSession session, Throwable cause) {
            priceCheckerLogger.error(getEventName(), cause);
        }

        @Override
        public void messageReceived(IoSession session, Object message) throws Exception {
            ThreadLocalContext.aspectBeforeMonitor(ShuttleBoardDaemon.this, ExecutorFactoryThreadInfo.instance);
            byte firstByte = ((AbstractIoBuffer) message).get();

            if (firstByte != 0) {

                StringBuilder barcode = new StringBuilder();
                int b;
                while ((b = ((AbstractIoBuffer) message).get()) != 13) // /r
                    barcode.append((char) b);

                InetAddress inetAddress = ((InetSocketAddress) session.getRemoteAddress()).getAddress();
                String ip = inetAddress.getHostAddress();

                Result result = readMessage(barcode.toString(), ip);
                session.write(IoBuffer.wrap(result.bytes));
                priceCheckerLogger.info(String.format("%s succeeded request ip %s, barcode %s, reply %s", getEventName(), ip, barcode.toString(), new String(result.bytes, 3, result.bytes.length - 3, result.charset)));
            }
            ThreadLocalContext.aspectAfterMonitor(ExecutorFactoryThreadInfo.instance);
        }

        private Result readMessage(String idBarcode, String ip) throws SQLException, UnsupportedEncodingException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
            priceCheckerLogger.info(String.format("Shuttle request ip %s, barcode %s", ip, idBarcode));
            //хак. Иногда приходит штрихкод, начинающийся с F
            if (idBarcode.startsWith("F"))
                idBarcode = idBarcode.substring(1);
            try (DataSession session = dbManager.createSession()) {

                String weightPrefix = (String) LM.findProperty("weightPrefixIP").read(session, new DataObject(ip));
                String piecePrefix = (String) LM.findProperty("piecePrefixIP").read(session, new DataObject(ip));
                if (idBarcode.length() == 13 && (weightPrefix != null && idBarcode.startsWith(weightPrefix) || piecePrefix != null && idBarcode.startsWith(piecePrefix)))
                    idBarcode = idBarcode.substring(2, 7);
                ObjectValue stockObject = LM.findProperty("stockIP[VARSTRING[100]]").readClasses(session, new DataObject(ip));
                ObjectValue skuObject = LM.findProperty("skuBarcode[VARSTRING[15]]").readClasses(session, new DataObject(idBarcode));
                String charset = (String) LM.findProperty("charsetIP[VARSTRING[100]]").read(session, new DataObject(ip));
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