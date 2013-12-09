package lsfusion.erp.region.by.machinery.board.fiscalboard;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.LogMessageClientAction;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.WrapperContext;
import lsfusion.server.classes.DateClass;
import lsfusion.server.classes.DateTimeClass;
import lsfusion.server.context.Context;
import lsfusion.server.context.ContextAwareDaemonThreadFactory;
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
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.Calendar;
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
            setupDaemon(dbManager.createSession());
        } catch (SQLException e) {
            throw new RuntimeException("Error starting Board Daemon: ", e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException("Error starting Board Daemon: ", e);
        }
    }

    public void setupDaemon(DataSession session) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        if (daemonTasksExecutor != null)
            daemonTasksExecutor.shutdown();

        daemonTasksExecutor = Executors.newSingleThreadExecutor(new ContextAwareDaemonThreadFactory(new SchedulerContext(), "board-daemon"));
        daemonTasksExecutor.submit(new DaemonTask(session));
    }

    class DaemonTask implements Runnable {
        DataSession session;

        public DaemonTask(DataSession session) {
            this.session = session;
        }

        public void run() {

            ServerSocket socket = null;
            try {
                socket = new ServerSocket(serverPort, 1000, Inet4Address.getByName(Inet4Address.getLocalHost().getHostAddress()));
            } catch (IOException e) {
                logger.error(e);
            }

            int length = 60;
            if (socket != null) {
                while (true) {
                    try {
                        Socket connectionSocket = socket.accept();
                        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
                        String barcode = inFromClient.readLine();

                        byte[] message = readMessage(session, barcode);
                        byte[] answer = new byte[length + 3];
                        answer[0] = (byte) 0xAB; //command
                        answer[1] = 0; //error code
                        answer[2] = (byte) length; //message length
                        System.arraycopy(message, 0, answer, 3, message.length);
                        outToClient.write(answer);
                    } catch (IOException e) {
                        logger.error(e);
                    } catch (SQLException e) {
                        logger.error(e);
                    }
                }
            }

        }

        private byte[] readMessage(DataSession session, String idBarcode) throws SQLException, UnsupportedEncodingException {
            int length = 44;
            int gapLength = 8; //передаётся 2 строки по 30 символов, но показывается только по 22
            Date date = new Date(Calendar.getInstance().getTime().getTime());
            ObjectValue skuObject = businessLogics.getModule("Barcode").getLCPByOldName("skuBarcodeIdDate").readClasses(session, new DataObject(idBarcode.substring(2)), new DataObject(date, DateClass.instance));
            if (skuObject instanceof NullValue) {
                String notFound = "Штрихкод не найден";
                while (notFound.length() < length)
                    notFound += " ";
                return notFound.getBytes("cp1251");
            } else {
                ObjectValue priceListTypeObject = businessLogics.getModule("PriceListType").getLCPByOldName("priceListTypeId").readClasses(session, new DataObject(idPriceListType));
                ObjectValue stockObject = businessLogics.getModule("Stock").getLCPByOldName("stockId").readClasses(session, new DataObject(idStock));
                String captionItem = (String) businessLogics.getModule("Item").getLCPByOldName("captionItem").read(session, skuObject);
                if (priceListTypeObject instanceof NullValue || stockObject instanceof NullValue) {
                    String notFound = "Неверные параметры сервера";
                    while (notFound.length() < length)
                        notFound += " ";
                    return notFound.getBytes("cp1251");
                } else {
                    BigDecimal price = (BigDecimal) businessLogics.getModule("PriceListType").getLCPByOldName("priceAPriceListTypeSkuStockDateTime").read(session,
                            priceListTypeObject, skuObject, stockObject, new DataObject(new Timestamp(date.getTime()), DateTimeClass.instance));
                    String priceMessage = new DecimalFormat("###,###.#").format(price.doubleValue());
                    while (captionItem.length() + priceMessage.length() < (length - 1)) {
                        priceMessage = " " + priceMessage;
                    }
                    captionItem = captionItem.substring(0, Math.min(captionItem.length(), (length - priceMessage.length() - 1)));
                    String message = captionItem + " " + priceMessage;
                    String gap = "";
                    for (int i = 0; i < gapLength; i++) {
                        gap += " ";
                    }
                    return (message.substring(0, length / 2) + gap + message.substring(length / 2, length) + gap).getBytes("cp1251");
                }
            }
        }
    }

    public class SchedulerContext extends WrapperContext {
        public SchedulerContext() {
            super(instanceContext);
        }

        @Override
        public void delayUserInteraction(ClientAction action) {
            String message = null;
            if (action instanceof LogMessageClientAction) {
                message = ((LogMessageClientAction) action).message;
            } else if (action instanceof MessageClientAction) {
                message = ((MessageClientAction) action).message;
            }
            if (message != null) {
                //можно записывать в лог
            }
            super.delayUserInteraction(action);
        }
    }
}
