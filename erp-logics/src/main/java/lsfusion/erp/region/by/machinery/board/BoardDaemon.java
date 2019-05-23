package lsfusion.erp.region.by.machinery.board;

import lsfusion.erp.ERPLoggers;
import lsfusion.base.DaemonThreadFactory;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.base.controller.thread.ExecutorFactory;
import lsfusion.server.base.controller.manager.MonitorServer;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.physics.exec.db.controller.manager.DBManager;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.language.ScriptingErrorLog;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
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

    public BoardDaemon(BusinessLogics businessLogics, DBManager dbManager, LogicsInstance logicsInstance) {
        super(DAEMON_ORDER);
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

    protected void setupDaemon(DBManager dbManager, String host, Integer port) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        if (daemonTasksExecutor != null)
            daemonTasksExecutor.shutdown();

        // аналогичный механизм в TerminalServer, но через Thread пока не принципиально
        daemonTasksExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("board-daemon"));
        daemonTasksExecutor.submit(new DaemonTask(dbManager, host, port));
    }

    private class DaemonTask implements Runnable {
        DBManager dbManager;
        String host;
        Integer port;

        public DaemonTask(DBManager dbManager, String host, Integer port) {
            this.dbManager = dbManager;
            this.host = host;
            this.port = port;
        }

        public void run() {

            ServerSocket serverSocket = null;
            ExecutorService executorService = ExecutorFactory.createMonitorThreadService(100, BoardDaemon.this);
            try {
                serverSocket = new ServerSocket(port, 1000, host == null ? Inet4Address.getByName(Inet4Address.getLocalHost().getHostAddress()) : Inet4Address.getByName(host));
            } catch (IOException e) {
                startLogger.error("BoardDaemon Error: ", e);
                executorService.shutdownNow();
            }
            if (serverSocket != null)
                while (true) {
                    try {
                        Socket socket = serverSocket.accept();
                        socket.setSoTimeout(30000);
                        executorService.submit(getCallable(socket));
                    } catch (IOException e) {
                        priceCheckerLogger.error("BoardDaemon Error: ", e);
                    }
                }
        }
    }

    protected String formatPrice(BigDecimal price) {
        return new DecimalFormat("###,###.##").format(price.doubleValue()) + " руб.";
    }
}