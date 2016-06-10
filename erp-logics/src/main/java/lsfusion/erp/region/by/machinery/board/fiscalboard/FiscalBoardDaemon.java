package lsfusion.erp.region.by.machinery.board.fiscalboard;

import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.DateClass;
import lsfusion.server.classes.DateTimeClass;
import lsfusion.server.context.Context;
import lsfusion.server.context.ContextAwareDaemonThreadFactory;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.lifecycle.LifecycleAdapter;
import lsfusion.server.lifecycle.LifecycleEvent;
import lsfusion.server.logics.*;
import lsfusion.server.logics.scripted.ScriptingBusinessLogics;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.session.DataSession;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.*;
import java.math.BigDecimal;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FiscalBoardDaemon extends LifecycleAdapter implements InitializingBean {
    private static final Logger logger = ServerLoggers.systemLogger;

    public ExecutorService daemonTasksExecutor;

    private BusinessLogics businessLogics;
    private DBManager dbManager;
    private Context instanceContext;
    private Integer serverPort;
    private String idPriceListType;
    private String idStock;

    public FiscalBoardDaemon(ScriptingBusinessLogics businessLogics, DBManager dbManager, LogicsInstance logicsInstance, Integer serverPort, String idPriceListType, String idStock) {
        this.businessLogics = businessLogics;
        this.dbManager = dbManager;
        this.instanceContext = logicsInstance.getContext();
        this.serverPort = serverPort;
        this.idPriceListType = idPriceListType;
        this.idStock = idStock;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(businessLogics, "businessLogics must be specified");
        Assert.notNull(dbManager, "dbManager must be specified");
        Assert.notNull(instanceContext, "logicsInstance must be specified");
        Assert.notNull(serverPort, "serverPort must be specified");
        Assert.notNull(idPriceListType, "idPriceListType must be specified");
        Assert.notNull(idStock, "idStock must be specified");
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        logger.info("Starting Board Daemon.");

        try {
            setupDaemon(dbManager);
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException("Error starting Board Daemon: ", e);
        }
    }

    public void setupDaemon(DBManager dbManager) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        if (daemonTasksExecutor != null)
            daemonTasksExecutor.shutdown();

        daemonTasksExecutor = Executors.newSingleThreadExecutor(new ContextAwareDaemonThreadFactory(instanceContext, "board-daemon"));
        daemonTasksExecutor.submit(new DaemonTask(dbManager));
    }

    class DaemonTask implements Runnable {
        DBManager dbManager;

        public DaemonTask(DBManager dbManager) {
            this.dbManager = dbManager;
        }

        public void run() {

            ServerSocket serverSocket = null;
            ExecutorService executorService = Executors.newFixedThreadPool(10);
            try {
                serverSocket = new ServerSocket(2004, 1000, Inet4Address.getByName(Inet4Address.getLocalHost().getHostAddress()));
            } catch (IOException e) {
                logger.error("FiscalBoardDaemon Error: ", e);
                executorService.shutdownNow();
            }
            if (serverSocket != null)
                while (true) {
                    try {
                        Socket socket = serverSocket.accept();
                        executorService.submit(new SocketCallable(businessLogics, socket));
                    } catch (IOException e) {
                        logger.error("FiscalBoardDaemon Error: ", e);
                    }
                }
        }
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
            
            ThreadLocalContext.set(instanceContext);
            try {

                int length = 60;
                BufferedReader inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                DataOutputStream outToClient = new DataOutputStream(socket.getOutputStream());
                String barcode = inFromClient.readLine();

                byte[] message = readMessage(BL, barcode);
                byte[] answer = new byte[length + 3];
                answer[0] = (byte) 0xAB; //command
                answer[1] = 0; //error code
                answer[2] = (byte) length; //message length
                System.arraycopy(message, 0, answer, 3, message.length);
                outToClient.write(answer);
                
                Thread.sleep(3000);
                outToClient.close();
                inFromClient.close();              
                
                return null;
            } catch (Exception e) {
                logger.error("FiscalBoard Error: ", e);
            }
            ThreadLocalContext.set(null);
            return null;
        }

        private byte[] readMessage(BusinessLogics BL, String idBarcode) throws SQLException, UnsupportedEncodingException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {
            try (DataSession session = dbManager.createSession()) {
                int textLength = 44;
                int gapLength = 8; //передаётся 2 строки по 30 символов, но показывается только по 22
                Date date = new Date(Calendar.getInstance().getTime().getTime());
                ObjectValue skuObject = BL.getModule("Barcode").findProperty("skuBarcode[STRING[15],DATE]").readClasses(session, new DataObject(idBarcode.substring(2)), new DataObject(date, DateClass.instance));
                if (skuObject instanceof NullValue) {
                    String notFound = "Штрихкод не найден";
                    while (notFound.length() < textLength)
                        notFound += " ";
                    return notFound.getBytes("cp1251");
                } else {
                    ObjectValue priceListTypeObject = BL.getModule("PriceListType").findProperty("priceListType[VARSTRING[100]]").readClasses(session, new DataObject(idPriceListType));
                    ObjectValue stockObject = BL.getModule("Stock").findProperty("stock[VARSTRING[100]]").readClasses(session, new DataObject(idStock));
                    String captionItem = (String) BL.getModule("Item").findProperty("caption[Item]").read(session, skuObject);
                    if (priceListTypeObject instanceof NullValue || stockObject instanceof NullValue) {
                        String notFound = "Неверные параметры сервера";
                        while (notFound.length() < textLength)
                            notFound += " ";
                        return notFound.getBytes("cp1251");
                    } else {
                        BigDecimal price = (BigDecimal) BL.getModule("PriceListType").findProperty("priceA[PriceListType,Sku,Stock,DATETIME]").read(session,
                                priceListTypeObject, skuObject, stockObject, new DataObject(new Timestamp(date.getTime()), DateTimeClass.instance));
                        String priceMessage = new DecimalFormat("###,###.#").format(price.doubleValue());
                        while (captionItem.length() + priceMessage.length() < (textLength - 1)) {
                            priceMessage = " " + priceMessage;
                        }
                        captionItem = captionItem.substring(0, Math.min(captionItem.length(), (textLength - priceMessage.length() - 1)));
                        String message = captionItem + " " + priceMessage;
                        String gap = "";
                        for (int i = 0; i < gapLength; i++) {
                            gap += " ";
                        }
                        return (message.substring(0, textLength / 2) + gap + message.substring(textLength / 2, textLength) + gap).getBytes("cp1251");
                    }
                }
            }
        }
    }
}
