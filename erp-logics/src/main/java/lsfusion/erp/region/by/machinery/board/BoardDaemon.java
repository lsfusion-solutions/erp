package lsfusion.erp.region.by.machinery.board;

import lsfusion.base.DaemonThreadFactory;
import lsfusion.erp.ERPLoggers;
import lsfusion.server.base.controller.lifecycle.LifecycleEvent;
import lsfusion.server.base.controller.manager.MonitorServer;
import lsfusion.server.base.controller.thread.ExecutorFactory;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.exec.db.controller.manager.DBManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BoardDaemon extends MonitorServer implements InitializingBean {
    protected static final Logger startLogger = ServerLoggers.startLogger;
    protected static final Logger priceCheckerLogger = ERPLoggers.priceCheckerLogger;

    protected BusinessLogics businessLogics;
    protected DBManager dbManager;
    protected LogicsInstance logicsInstance;

    protected ExecutorService daemonTasksExecutor;
    private DaemonTask daemonTask;

    public BoardDaemon(BusinessLogics businessLogics, DBManager dbManager, LogicsInstance logicsInstance) {
        super(DAEMON_ORDER);
        this.businessLogics = businessLogics;
        this.dbManager = dbManager;
        this.logicsInstance = logicsInstance;
    }

    protected abstract Callable getCallable(Socket socket);

    @Override
    public void afterPropertiesSet() {
        Assert.notNull(businessLogics, "businessLogics must be specified");
        Assert.notNull(dbManager, "dbManager must be specified");
        Assert.notNull(logicsInstance, "logicsInstance must be specified");
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        setupDaemon();
    }

    @Override
    public LogicsInstance getLogicsInstance() {
        return logicsInstance;
    }

    public abstract void setupDaemon();

    protected void setupDaemon(DBManager dbManager, String host, Integer port) throws IOException {

        if (daemonTasksExecutor != null)
            daemonTasksExecutor.shutdownNow();
        if(daemonTask != null && daemonTask.serverSocket != null) {
            daemonTask.serverSocket.close();
        }

        // аналогичный механизм в TerminalServer, но через Thread пока не принципиально
        daemonTasksExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("board-daemon"));
        daemonTask = new DaemonTask(dbManager, host, port);
        daemonTasksExecutor.submit(daemonTask);
    }

    private class DaemonTask implements Runnable {
        DBManager dbManager;
        String host;
        Integer port;
        ServerSocket serverSocket;

        public DaemonTask(DBManager dbManager, String host, Integer port) {
            this.dbManager = dbManager;
            this.host = host;
            this.port = port;
        }

        public void run() {

            ExecutorService executorService = ExecutorFactory.createMonitorThreadService(100, BoardDaemon.this);
            try {
                serverSocket = new ServerSocket(port, 1000, host == null ? Inet4Address.getByName(Inet4Address.getLocalHost().getHostAddress()) : Inet4Address.getByName(host));
            } catch (IOException e) {
                serverSocket = null;
                startLogger.error("BoardDaemon Error: ", e);
                executorService.shutdownNow();
            }
            if (serverSocket != null) {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket socket = serverSocket.accept();
                        socket.setSoTimeout(30000);
                        executorService.submit(getCallable(socket));
                    } catch (IOException e) {
                        priceCheckerLogger.error("BoardDaemon Error: ", e);
                    } catch (Throwable t) {
                        ServerLoggers.systemLogger.error("BoardDaemon Error: ", t);
                    }
                }
            }
        }
    }

    protected String formatPrice(BigDecimal price) {
        return new DecimalFormat("###,###.##").format(price.doubleValue()) + " руб.";
    }
}