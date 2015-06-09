package equ.srv.terminal;

import lsfusion.server.context.LogicsInstanceContext;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.lifecycle.LifecycleAdapter;
import lsfusion.server.lifecycle.LifecycleEvent;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DBManager;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TerminalServer extends LifecycleAdapter {

    public static byte WRONG_PARAMETER_COUNT = 101;
    public static byte UNKNOWN_ERROR = 102;
    public static byte LOGIN_ERROR = 103;
    public static byte ITEM_NOT_FOUND = 104;
    public static byte PROCESS_DOCUMENT_ERROR = 105;
    public static byte AUTHORISATION_REQUIRED = 106;
    public static byte UNKNOWN_COMMAND = 111;

    private static final Logger logger = Logger.getLogger("TerminalLogger");

    private static ConcurrentHashMap<String, DataObject> userMap = new ConcurrentHashMap<>();

    boolean started = false;
    boolean stopped = false;
    private TerminalHandlerInterface terminalHandlerInterface;
    private LogicsInstance logicsInstance;
    private LogicsInstanceContext logicsInstanceContext;

    protected BusinessLogics BL;
    protected ScriptingLogicsModule terminalLM;

    protected static byte stx = 0x02;
    protected static byte etx = 0x03;
    protected byte esc = 0x1B;
    private String host;
    private Integer port;

    public void setLogicsInstance(LogicsInstance logicsInstance) {
        this.logicsInstance = logicsInstance;
    }

    public LogicsInstance getLogicsInstance() {
        return logicsInstance;
    }

    public void setTerminalHandlerInterface(TerminalHandlerInterface terminalHandlerInterface) {
        this.terminalHandlerInterface = terminalHandlerInterface;
    }

    public DBManager getDbManager() {
        return logicsInstance.getDbManager();
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        if (getDbManager().isServer()) {
            assert terminalHandlerInterface != null;
            assert host != null;
            assert port != null;
            logicsInstanceContext = getLogicsInstance().getContext();
            BL = logicsInstance.getBusinessLogics();
            terminalLM = getLogicsInstance().getBusinessLogics().getModule("Terminal");
            logger.info("Binding Terminal Server.");
            started = true;
            listenToPort();
        }
    }

    @Override
    protected void onStopping(LifecycleEvent event) {
        stopped = true;
        if (started) {
            logger.info("Stopping Terminal Server.");
            try {
                ThreadLocalContext.set(null);
            } catch (Exception e) {
                throw new RuntimeException("Error stopping Terminal Server: ", e);
            }
        }
    }

    public TerminalServer() {

        //listenToPort();

    }

    public void listenToPort() {
        final ExecutorService executorService = Executors.newFixedThreadPool(10, new TerminalThreadFactory("terminal"));
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port, 1000, Inet4Address.getByName(host)); //2004, "192.168.42.142"

        } catch (IOException e) {
            logger.error(e);
            executorService.shutdownNow();
        }

        if (serverSocket != null) {
            final ServerSocket finalServerSocket = serverSocket;
            Thread thread = new Thread(new Runnable() {
                public void run() {
                    while (!stopped) {
                        try {
                            Socket socket = finalServerSocket.accept();
                            executorService.submit(new SocketCallable(socket));
                        } catch (IOException e) {
                            logger.error(e);
                        }
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
        }
    }

    protected String trim(String input) {
        return input == null ? null : input.trim();
    }

    protected DataSession createSession() throws SQLException {
        if (ThreadLocalContext.get() == null)
            ThreadLocalContext.set(logicsInstanceContext);
        return getDbManager().createSession();
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Integer getPort() {
        return port;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public class SocketCallable implements Callable {
        private Socket socket;

        public SocketCallable(Socket socket) {
            this.socket = socket;
        }

        public Object call() {

            DataInputStream inFromClient = null;
            DataOutputStream outToClient = null;
            try {
                inFromClient = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                outToClient = new DataOutputStream(socket.getOutputStream());
                //Thread.sleep(2000);
                byte start = inFromClient.readByte();
                byte id = inFromClient.readByte();
                byte command = inFromClient.readByte();

                String result = null;
                List<String> itemInfo = null;
                byte error = 0;
                String sessionId;
                //DataObject user = null;
                switch (command) {
                    case 4:  //getUserInfo
                        try {
                            logger.info("requested getUserInfo");
                            String[] params = readParams(inFromClient);
                            if(params != null && params.length == 3) {
                                logger.info("logging user " + params[0]);
                                result = getSessionId(params[0], params[1], params[2]);
                                if (result == null)
                                    error = LOGIN_ERROR;
                            } else {
                                error = WRONG_PARAMETER_COUNT;
                            }
                        } catch (Exception e) {
                            logger.error(e);
                            error = UNKNOWN_ERROR;
                        }
                        break;
                    case 5:  //getItemInfo
                        try {
                            logger.info("requested getItemInfo");
                            String[] params = readParams(inFromClient);
                            if(params != null && params.length == 2) {
                                logger.info("requested barcode " + params[1]);
                                sessionId = params[0];
                                String barcode = params[1];
                                DataObject user = userMap.get(sessionId);
                                if (user == null)
                                    error = AUTHORISATION_REQUIRED;
                                else {
                                    itemInfo = readItem(user, barcode);
                                    if (itemInfo == null)
                                        error = ITEM_NOT_FOUND;
                                }
                            } else {
                                error = WRONG_PARAMETER_COUNT;
                            }
                        } catch (Exception e) {
                            logger.error(e);
                            error = UNKNOWN_ERROR;
                        }
                        break;
                    case 6:
                        try {
                            logger.info("received document");
                            List<String[]> params = readDocumentParams(inFromClient);
                            if(params != null && params.size() >= 1) {
                                String[] document = params.get(0);
                                if(document.length < 7) {
                                    error = WRONG_PARAMETER_COUNT;
                                } else {
                                    List<List<Object>> terminalDocumentDetailList = new ArrayList<>();
                                    String sessionIdDocument = document[0];
                                    DataObject user = userMap.get(sessionIdDocument);
                                    if (user == null)
                                        error = AUTHORISATION_REQUIRED;
                                    else {
                                        logger.info("receiving document number " + document[2]);
                                        String dateDocument = document[1];
                                        String numberDocument = document[2];
                                        String idDocument = numberDocument + " " + dateDocument;
                                        //String ana1 = document[3];
                                        //String ana2 = document[4];
                                        //String ana3 = document[5];
                                        //String comment = document[6];
                                        for (int i = 1; i < params.size(); i++) {
                                            String[] line = params.get(i);
                                            if (line.length < 5) {
                                                error = WRONG_PARAMETER_COUNT;
                                            } else {
                                                String barcodeDocumentDetail = line[0];
                                                BigDecimal quantityDocumentDetail = parseBigDecimal(line[1]);
                                                BigDecimal priceDocumentDetail = parseBigDecimal(line[2]);
                                                //String numberDocumentDetail = line[3];
                                                String idDocumentDetail = idDocument + i;
                                                //String commentLine = line[4];
                                                terminalDocumentDetailList.add(Arrays.asList((Object) idDocument, numberDocument,
                                                        idDocumentDetail, String.valueOf(i), barcodeDocumentDetail, quantityDocumentDetail,
                                                        priceDocumentDetail));
                                            }
                                        }
                                        boolean emptyDocument = terminalDocumentDetailList.isEmpty();
                                        if(emptyDocument)
                                            terminalDocumentDetailList.add(Arrays.asList((Object) idDocument, numberDocument));
                                        result = importTerminalDocumentDetail(idDocument, user, terminalDocumentDetailList, emptyDocument);
                                        if (result != null)
                                            error = PROCESS_DOCUMENT_ERROR;
                                    }
                                }
                            } else {
                                error = WRONG_PARAMETER_COUNT;
                            }
                        } catch (Exception e) {
                            logger.error(e);
                            error = UNKNOWN_ERROR;
                        }
                        break;
                    default:
                        result = "unknown command";
                        error = UNKNOWN_COMMAND;
                        break;
                }

                logger.info("error code: " + (int) error);
                outToClient.writeByte(stx);
                outToClient.flush();
                outToClient.writeByte(id);
                outToClient.flush();
                outToClient.writeByte(command);
                outToClient.flush();
                outToClient.writeByte(error);
                outToClient.flush();

                if (result != null) {
                    outToClient.writeBytes(result);
                    outToClient.flush();
                } else if (itemInfo != null) {
                    for (int i = 0; i < 8; i++) {
                        if (itemInfo.size() > i) {
                            outToClient.write(itemInfo.get(i).getBytes("cp1251"));
                            outToClient.flush();
                        }
                        outToClient.writeByte(esc);
                        outToClient.flush();
                    }
                }

                outToClient.writeByte(etx);
                outToClient.flush();

                Thread.sleep(1000);
                return null;
            } catch (Exception e) {
                logger.error(e);
            } finally {
                try {
                    if(outToClient != null)
                        outToClient.close();
                    if(inFromClient !=null)
                        inFromClient.close();
                    //socket.close();
                } catch (IOException e) {
                    logger.error(e);
                }
            }
            return null;
        }

    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            return value == null || value.isEmpty() ? null : new BigDecimal(value);
        } catch (Exception e) {
            logger.error(e);
            return null;
        }
    }

    private List<String[]> readDocumentParams(DataInputStream inFromClient) throws IOException {
        byte b;
        String escStr = Character.toString((char) esc);
        List<String[]> result = new ArrayList<>();
        String line = "";
        while ((b = inFromClient.readByte()) != 3) {
            if (b == (char) 13) {
                result.add(line.isEmpty() ? null : line.split(escStr, -1));
                line = "";
            }
            else
                line += (char) b;
        }
        result.add(line.isEmpty() ? null : line.split(escStr, -1));
        return result;
    }

    private String[] readParams(DataInputStream inFromClient) throws IOException {
        byte b;
        String escStr = Character.toString((char) esc);
        String result = "";
        while ((b = inFromClient.readByte()) != 3) {
            result += (char) b;
        }
        return result.isEmpty() ? null : result.split(escStr, -1);
    }

    private static class TerminalThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public TerminalThreadFactory(String threadNamePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = s != null
                    ? s.getThreadGroup()
                    : Thread.currentThread().getThreadGroup();
            this.namePrefix = "pool-" + poolNumber.getAndIncrement() + "-" + threadNamePrefix + "-";
        }

        public Thread newThread(Runnable runnable) {
            Thread t = newThreadInstance(group, runnable, namePrefix + threadNumber.getAndIncrement(), 0);
            if (!t.isDaemon()) {
                t.setDaemon(true);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }

        protected Thread newThreadInstance(ThreadGroup group, Runnable r, String name, int stackSize) {
            return new Thread(group, r, name, stackSize);
        }
    }

    public String getSessionId(String login, String password, String idTerminal) throws RemoteException, SQLException {
        DataObject userObject = terminalHandlerInterface.getUserObject(createSession(), login, password);
        if(userObject != null) {
            String sessionId = String.valueOf((login + password + idTerminal).hashCode());
            userMap.put(sessionId, userObject);
            return sessionId;
        }
        return null;
    }

    protected List<String> readItem(DataObject user, String barcode) throws RemoteException, SQLException {
        return terminalHandlerInterface.readItem(createSession(), user, barcode);
    }

    protected String importTerminalDocumentDetail(String idTerminalDocument, DataObject userObject, List<List<Object>> terminalDocumentDetailList, boolean emptyDocument) throws RemoteException, SQLException {
        return terminalHandlerInterface.importTerminalDocument(createSession(), userObject, idTerminalDocument, terminalDocumentDetailList, emptyDocument);
    }
}