package lsfusion.erp.region.by.machinery.board;

import lsfusion.interop.DaemonThreadFactory;
import lsfusion.server.ServerLoggers;
import lsfusion.server.context.ExecutorFactory;
import lsfusion.server.lifecycle.LifecycleEvent;
import lsfusion.server.lifecycle.MonitorServer;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DBManager;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BoardDaemon extends MonitorServer implements InitializingBean {
    protected static final Logger startLogger = ServerLoggers.startLogger;
    protected static final Logger logger = ServerLoggers.systemLogger;

    protected BusinessLogics businessLogics;
    protected DBManager dbManager;
    protected LogicsInstance logicsInstance;

    protected ExecutorService daemonTasksExecutor;

    public BoardDaemon(BusinessLogics businessLogics, DBManager dbManager, LogicsInstance logicsInstance) {
        this.businessLogics = businessLogics;
        this.dbManager = dbManager;
        this.logicsInstance = logicsInstance;
    }

    protected abstract Callable getCallable(Socket socket);

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(businessLogics, "businessLogics must be specified");
        Assert.notNull(dbManager, "dbManager must be specified");
        Assert.notNull(logicsInstance, "logicsInstance must be specified");
    }

    @Override
    public LogicsInstance getLogicsInstance() {
        return logicsInstance;
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        startLogger.info("Starting " + getEventName() + " Daemon.");
        try {
            setupDaemon(dbManager);
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException("Error starting " + getEventName() + " Daemon: ", e);
        }
    }

    private void setupDaemon(DBManager dbManager) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        if (daemonTasksExecutor != null)
            daemonTasksExecutor.shutdown();

        // аналогичный механизм в TerminalServer, но через Thread пока не принципиально
        daemonTasksExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("board-daemon"));
        daemonTasksExecutor.submit(new DaemonTask(dbManager));
    }

    private class DaemonTask implements Runnable {
        DBManager dbManager;

        public DaemonTask(DBManager dbManager) {
            this.dbManager = dbManager;
        }

        public void run() {

            ServerSocket serverSocket = null;
            ExecutorService executorService = ExecutorFactory.createMonitorThreadService(10, BoardDaemon.this);
            try {
                serverSocket = new ServerSocket(2004, 1000, Inet4Address.getByName(Inet4Address.getLocalHost().getHostAddress()));
            } catch (IOException e) {
                startLogger.error("BoardDaemon Error: ", e);
                executorService.shutdownNow();
            }
            if (serverSocket != null)
                while (true) {
                    try {
                        Socket socket = serverSocket.accept();
                        executorService.submit(getCallable(socket));
                    } catch (IOException e) {
                        logger.error("BoardDaemon Error: ", e);
                    }
                }
        }
    }
}