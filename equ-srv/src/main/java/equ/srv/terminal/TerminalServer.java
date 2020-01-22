package equ.srv.terminal;

import equ.srv.EquipmentLoggers;
import lsfusion.base.file.RawFileData;
import lsfusion.server.base.controller.lifecycle.LifecycleEvent;
import lsfusion.server.base.controller.manager.MonitorServer;
import lsfusion.server.base.controller.thread.ExecutorFactory;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.physics.exec.db.controller.manager.DBManager;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.math.BigDecimal;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static lsfusion.base.BaseUtils.trimToNull;

public class TerminalServer extends MonitorServer {

    public static byte WRONG_PARAMETER_COUNT = 101;
    public static String WRONG_PARAMETER_COUNT_TEXT = "Неверное кол-во параметров";
    public static byte UNKNOWN_ERROR = 102;
    public static String UNKNOWN_ERROR_TEXT = "Неизвестная ошибка";
    public static byte LOGIN_ERROR = 103;
    public static String LOGIN_ERROR_TEXT = "Пользователь не найден или неверный пароль";
    public static byte ITEM_NOT_FOUND = 104;
    public static String ITEM_NOT_FOUND_TEXT = "Товар не найден";
    public static byte PROCESS_DOCUMENT_ERROR = 105;
    public static String PROCESS_DOCUMENT_ERROR_TEXT = "Ошибка при сохранении строки документа";
    public static byte AUTHORISATION_REQUIRED = 106;
    public static String AUTHORISATION_REQUIRED_TEXT = "Необходима повторная ON-LINE авторизация";
    public static byte NOT_ACTIVE_TERMINAL = 107;
    public static String NOT_ACTIVE_TERMINAL_TEXT = "Терминал %s не зарегистрирован или заблокирован";
    public static byte GET_ALL_BASE_ERROR = 108;
    public static String GET_ALL_BASE_ERROR_TEXT = "Ошибка при формировании базы";
    public static byte SAVE_PALLET_ERROR = 109;
    public static byte GET_ITEM_INFO_ERROR = 110;
    public static byte UNKNOWN_COMMAND = 111;
    public static String UNKNOWN_COMMAND_TEXT = "Неизвестный запрос";

    public static final byte GET_USER_INFO = 4;

    @Override
    protected void onInit(LifecycleEvent event) {
        super.onInit(event);
        
        terminalHandlerInterface.init();
    }

    public static final byte GET_ITEM_INFO = 5;
    public static final byte SAVE_DOCUMENT = 6;
    public static final byte GET_ITEM_HTML = 7;
    public static final byte GET_ALL_BASE = 8;
    public static final byte SAVE_PALLET = 9;
    public static final byte CHECK_ORDER = 10;//0x0A
    public static final byte GET_PREFERENCES = 11;//0x0B

    private static final Logger logger = EquipmentLoggers.terminalLogger;
    private static final Logger priceCheckerLogger = EquipmentLoggers.priceCheckerLogger;

    private static ConcurrentHashMap<String, UserInfo> userMap = new ConcurrentHashMap<>();

    boolean started = false;
    boolean stopped = false;
    private TerminalHandlerInterface terminalHandlerInterface;
    private LogicsInstance logicsInstance;

    protected static byte stx = 0x02;
    protected static byte etx = 0x03;
    protected byte esc = 0x1B;

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
        setupDaemon();
    }

    public void setupDaemon() {
        if (getDbManager().isServer()) {
            assert terminalHandlerInterface != null;

            try {
                List<Object> hostPort = terminalHandlerInterface.readHostPort(createSession());
                if (hostPort.size() >= 2 && hostPort.get(0) != null && hostPort.get(1) != null) {
                    String host = (String) hostPort.get(0);
                    Integer port = (Integer) hostPort.get(1);
                    logger.info("Binding Terminal Server.");
                    started = true;
                    listenToPort(host, port);
                } else {
                    logger.info("Terminal Server disabled, no host/port settings found");
                }
            } catch (SQLException e) {
                logger.error("Error reading Terminal Server settings");
            }
        } else {
            logger.info("Terminal Server disabled, change serverComputer() to enable");
        }
    }

    @Override
    protected void onStopping(LifecycleEvent event) {
        stopped = true;
        if (started) {
            logger.info("Stopping Terminal Server.");
        }
    }

    public TerminalServer() {
        super(DAEMON_ORDER);
    }

    @Override
    public String getEventName() {
        return "terminal";
    }

    private ServerSocket listenServerSocket;
    private ExecutorService listenExecutorService;
    private Thread listenThread;
    public void listenToPort(String host, Integer port) {
        try {
            if (listenServerSocket != null)
                listenServerSocket.close();
            if(listenExecutorService != null)
                listenExecutorService.shutdownNow();

            listenServerSocket = new ServerSocket(port, 1000, Inet4Address.getByName(host)); //2004, "192.168.42.142"
            listenExecutorService = ExecutorFactory.createMonitorThreadService(100, this);

            // аналогичный механизм в ShtrihBoardDaemon, но через Executor пока не принципиально
            startListenThread();
        } catch (IOException e) {
            logger.error(String.format("Error occurred while listening to host %s, port %s: ", host, port), e);
        }
    }

    public void restartListenThread() {
        if(listenThread != null) {
            listenThread.interrupt();
            listenThread = null;
        }
        startListenThread();
    }

    public void startListenThread() {
        listenThread = new Thread(() -> {
            while (!stopped && !listenServerSocket.isClosed()) {
                try {
                    Socket socket = listenServerSocket.accept();
                    socket.setSoTimeout(30000);
                    logger.info("submitting task for socket : " + socket + " " + System.identityHashCode(socket));
                    listenExecutorService.submit(new SocketCallable(socket));
                } catch (IOException e) {
                    logger.error("Error occurred while submitting socket: ", e);
                }
            }
        });
        listenThread.setDaemon(true);
        listenThread.start();
    }

    protected String getPreferences(String idTerminal) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (DataSession session = createSession()) {
            return terminalHandlerInterface.getPreferences(session, getStack(), idTerminal);
        }
    }

    private String formatValue(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private String fillZeroes(int i) {
        String value = String.valueOf(i);
        while (value.length() < 4)
            value = "0" + value;
        return value;
    }

    private Timestamp parseTimestamp(String value) {
        Timestamp timestamp;
        try {
            timestamp = value == null ? null : new Timestamp(DateUtils.parseDate(value, "yyyy-MM-dd HH:mm:ss").getTime());
        } catch (Exception e) {
            logger.error("Parsing timestamp failed: " + value, e);
            timestamp = null;
        }
        return timestamp;
    }

    private Date parseDate(String value) {
        Date date;
        try {
            date = value == null ? null : new Date(DateUtils.parseDate(value, "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss").getTime());
        } catch (Exception e) {
            logger.error("Parsing date failed: " + value, e);
            date = null;
        }
        return date;
    }


    private BigDecimal parseBigDecimal(String value) {
        try {
            return value == null || value.isEmpty() ? null : new BigDecimal(value);
        } catch (Exception e) {
            logger.error("Error occurred while parsing numeric value: ", e);
            return null;
        }
    }

    private Integer parseInteger(String value) {
        try {
            return value == null || value.isEmpty() || value.equals("0") ? null : Integer.parseInt(value);
        } catch (Exception e) {
            logger.error("Error occurred while parsing integer value: ", e);
            return null;
        }
    }

    private List<String[]> readDocumentParams(DataInputStream inFromClient) throws IOException {
        byte b;
        String escStr = Character.toString((char) esc);
        List<String[]> result = new ArrayList<>();
        List<Byte> line = new ArrayList<>();
        while ((b = inFromClient.readByte()) != 3) {
            if (b == (char) 13) {
                result.add(bytesToString(line).split(escStr, -1));
                line = new ArrayList();
            } else
                line.add(b);
        }
        result.add(bytesToString(line).split(escStr, -1));
        return result;
    }

    private String bytesToString(List<Byte> bytes) throws UnsupportedEncodingException {
        String result = "";
        if (!bytes.isEmpty()) {
            ByteBuffer lineBytes = ByteBuffer.allocate(bytes.size());
            for (byte lb : bytes)
                lineBytes.put(lb);
            result = new String(lineBytes.array(), "cp1251");
        }
        return result;
    }

    private String[] readParams(DataInputStream inFromClient) throws IOException {
        byte b;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String escStr = Character.toString((char) esc);
        while ((b = inFromClient.readByte()) != 3) {
            baos.write(b);
        }
        String result = baos.toString("cp1251");
        return result.split(escStr, -1);
    }

    public String login(String login, String password, String idTerminal) throws SQLException {
        DataObject userObject = terminalHandlerInterface.login(createSession(), getStack(), login, password, idTerminal);
        if (userObject != null) {
            String sessionId = String.valueOf((login + password + idTerminal).hashCode());
            userMap.put(sessionId, new UserInfo(userObject, idTerminal));
            return sessionId;
        }
        return null;
    }

    protected Object readItem(DataObject user, String barcode, String bin) throws SQLException {
        return terminalHandlerInterface.readItem(createSession(), user, barcode, bin);
    }

    protected String readItemHtml(String barcode, String idStock) throws SQLException {
        return terminalHandlerInterface.readItemHtml(createSession(), barcode, idStock);
    }

    protected RawFileData readBase(DataObject userObject) throws SQLException {
        return terminalHandlerInterface.readBase(createSession(), userObject);
    }

    protected String savePallet(DataObject user, String numberPallet, String nameBin) throws SQLException {
        try (DataSession session = createSession()) {
            return terminalHandlerInterface.savePallet(session, getStack(), user, numberPallet, nameBin);
        }
    }

    protected String checkOrder(DataObject user, String numberOrder) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (DataSession session = createSession()) {
            return terminalHandlerInterface.checkOrder(session, getStack(), user, numberOrder);
        }
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
                RawFileData fileData = null;
                byte errorCode = 0;
                String errorText = null;
                String sessionId;
                switch (command) {
                    case GET_USER_INFO:
                        try {
                            logger.info("requested getUserInfo");
                            String[] params = readParams(inFromClient);
                            if (params.length == 3) {
                                logger.info("logging user " + params[0]);
                                if (terminalHandlerInterface.isActiveTerminal(createSession(), getStack(), params[2])) {
                                    result = login(params[0], params[1], params[2]);
                                    if (result == null) {
                                        errorCode = LOGIN_ERROR;
                                        errorText = LOGIN_ERROR_TEXT;
                                    }
                                } else {
                                    errorCode = NOT_ACTIVE_TERMINAL;
                                    errorText = String.format(NOT_ACTIVE_TERMINAL_TEXT, params[2]);
                                }
                            } else {
                                errorCode = WRONG_PARAMETER_COUNT;
                                errorText = WRONG_PARAMETER_COUNT_TEXT;
                            }
                        } catch (Exception e) {
                            logger.error("GetUserInfo Unknown error: ", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case GET_ITEM_INFO:
                        try {
                            logger.info("requested getItemInfo");
                            String[] params = readParams(inFromClient);
                            if (params.length >= 2) {
                                logger.info("requested barcode " + params[1]);
                                sessionId = params[0];
                                String barcode = params[1];
                                String bin = params.length == 3 ? trimToNull(params[2]) : null;
                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.user == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    Object readItemResult = readItem(userInfo.user, barcode, bin);
                                    if (readItemResult == null) {
                                        errorCode = ITEM_NOT_FOUND;
                                        errorText = ITEM_NOT_FOUND_TEXT;
                                    } else if(readItemResult instanceof String) {
                                        errorCode = GET_ITEM_INFO_ERROR;
                                        errorText = (String) readItemResult;
                                    } else {
                                        itemInfo = (List<String>) readItemResult;
                                    }
                                }
                            } else {
                                errorCode = WRONG_PARAMETER_COUNT;
                                errorText = WRONG_PARAMETER_COUNT_TEXT;
                            }
                        } catch (Exception e) {
                            logger.error("GetItemInfo Unknown error: ", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case SAVE_DOCUMENT:
                        try {
                            logger.info("received document");
                            List<String[]> params = readDocumentParams(inFromClient);
                            if (params != null && params.size() >= 1) {
                                String[] document = params.get(0);
                                if (document.length < 8) {//todo: пока parentDocument не считаем обязательным
                                    errorCode = WRONG_PARAMETER_COUNT;
                                    errorText = WRONG_PARAMETER_COUNT_TEXT;
                                } else {
                                    List<List<Object>> terminalDocumentDetailList = new ArrayList<>();
                                    String sessionIdDocument = document[0];
                                    UserInfo userInfo = userMap.get(sessionIdDocument);
                                    if (userInfo == null || userInfo.user == null) {
                                        errorCode = AUTHORISATION_REQUIRED;
                                        errorText = AUTHORISATION_REQUIRED_TEXT;
                                    } else {
                                        logger.info("receiving document number " + document[2]);
                                        String dateDocument = document[1];
                                        String numberDocument = document[2];
                                        String idDocument = numberDocument + " " + dateDocument;
                                        String idTerminalDocumentType = document[3];
                                        String ana1 = formatValue(document[4]);
                                        String ana2 = formatValue(document[5]);
                                        //String ana3 = document[6];
                                        String comment = formatValue(document[7]);
                                        String parentDocument = document.length <= 8 ? null : formatValue(document[8]);
                                        if(parentDocument != null)
                                            parentDocument = parentDocument.replace("'", "");
                                        for (int i = 1; i < params.size(); i++) {
                                            String[] line = params.get(i);
                                            if (line.length < 5) {
                                                errorCode = WRONG_PARAMETER_COUNT;
                                                errorText = WRONG_PARAMETER_COUNT_TEXT;
                                            } else {
                                                String barcodeDocumentDetail = line[0];
                                                BigDecimal quantityDocumentDetail = parseBigDecimal(line[1]);
                                                BigDecimal priceDocumentDetail = parseBigDecimal(line[2]);
                                                Integer numberDocumentDetail = parseInteger(line[3]);
                                                String idDocumentDetail = idDocument + fillZeroes(i);
                                                String commentDocumentDetail = formatValue(line[4]);
                                                String dateDocumentDetail = line.length <= 5 ? null : formatValue(line[5]);
                                                String extraDate1DocumentDetail = line.length <= 6 ? null : formatValue(line[6]);
                                                String extraDate2DocumentDetail = line.length <= 7 ? null : formatValue(line[7]);
                                                String extraField1DocumentDetail = line.length <= 8 ? null : formatValue(line[8]);
                                                String extraField2DocumentDetail = line.length <= 9 ? null : formatValue(line[9]);
                                                String extraField3DocumentDetail = line.length <= 10 ? null : formatValue(line[10]);
                                                terminalDocumentDetailList.add(Arrays.asList(idDocument, numberDocument, idTerminalDocumentType,
                                                        ana1, ana2, comment, idDocumentDetail, numberDocumentDetail, barcodeDocumentDetail, quantityDocumentDetail,
                                                        priceDocumentDetail, commentDocumentDetail, parseTimestamp(dateDocumentDetail),
                                                        parseDate(extraDate1DocumentDetail), parseDate(extraDate2DocumentDetail), extraField1DocumentDetail,
                                                        extraField2DocumentDetail, extraField3DocumentDetail, parentDocument));
                                            }
                                        }
                                        logger.info("receiving document number " + document[2] + " : " + (params.size() - 1) + " record(s)");
                                        boolean emptyDocument = terminalDocumentDetailList.isEmpty();
                                        if (emptyDocument)
                                            terminalDocumentDetailList.add(Arrays.asList(idDocument, numberDocument, idTerminalDocumentType, ana1, ana2, comment));
                                        result = importTerminalDocumentDetail(idDocument, userInfo.user, userInfo.idTerminal, terminalDocumentDetailList, emptyDocument);
                                        if (result != null) {
                                            errorCode = PROCESS_DOCUMENT_ERROR;
                                            errorText = PROCESS_DOCUMENT_ERROR_TEXT + ": " + result;
                                        }
                                    }
                                }
                            } else {
                                errorCode = WRONG_PARAMETER_COUNT;
                                errorText = WRONG_PARAMETER_COUNT_TEXT;
                            }
                        } catch (Exception e) {
                            logger.error("SaveDocument Unknown error", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case GET_ITEM_HTML:
                        try {
                            logger.info("requested getItemHtml");
                            String[] params = readParams(inFromClient);
                            if (params.length == 2) {
                                priceCheckerLogger.info(String.format("requested barcode '%s', stock '%s'", params[0], params[1]));
                                String barcode = params[0];
                                String idStock = params[1];
                                result = readItemHtml(barcode, idStock);
                                if (result == null) {
                                    errorCode = ITEM_NOT_FOUND;
                                    errorText = ITEM_NOT_FOUND_TEXT;
                                }
                            } else {
                                errorCode = WRONG_PARAMETER_COUNT;
                                errorText = WRONG_PARAMETER_COUNT_TEXT;
                            }
                        } catch (Exception e) {
                            priceCheckerLogger.error("request failed: ", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case GET_ALL_BASE:
                        try {
                            logger.info("requested getAllBase");
                            String[] params = readParams(inFromClient);
                            if (params.length == 1) {
                                sessionId = params[0];
                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.user == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    fileData = readBase(userInfo.user);
                                    if (fileData == null) {
                                        errorCode = GET_ALL_BASE_ERROR;
                                        errorText = GET_ALL_BASE_ERROR_TEXT;
                                    }
                                }
                            } else {
                                errorCode = WRONG_PARAMETER_COUNT;
                                errorText = WRONG_PARAMETER_COUNT_TEXT;
                            }
                        } catch (Exception e) {
                            logger.error("GetAllBase Unknown error: ", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case SAVE_PALLET:
                        try {
                            logger.info("received pallet");
                            String[] params = readParams(inFromClient);
                            if (params.length >= 3) {
                                sessionId = params[0];
                                String numberPallet = params[1];
                                String nameBin = params[2];
                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.user == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    result = savePallet(userInfo.user, numberPallet, nameBin);
                                    if (result != null) {
                                        errorCode = SAVE_PALLET_ERROR;
                                        errorText = result;
                                    }
                                }
                            } else {
                                errorCode = WRONG_PARAMETER_COUNT;
                                errorText = WRONG_PARAMETER_COUNT_TEXT;
                            }
                        } catch (Exception e) {
                            logger.error("SavePallet Unknown error", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case CHECK_ORDER:
                        try {
                            logger.info("checkOrder");
                            String[] params = readParams(inFromClient);
                            if (params.length >= 2) {
                                sessionId = params[0];
                                String numberOrder = params[1];
                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.user == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    result = checkOrder(userInfo.user, numberOrder);
                                    if (result == null) {
                                        errorCode = UNKNOWN_ERROR;
                                        errorText = UNKNOWN_ERROR_TEXT;
                                    }
                                }
                            } else {
                                errorCode = WRONG_PARAMETER_COUNT;
                                errorText = WRONG_PARAMETER_COUNT_TEXT;
                            }
                        } catch (Exception e) {
                            logger.error("CheckOrder Unknown error", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case GET_PREFERENCES:
                        try {
                            logger.info("getPreferences");
                            String[] params = readParams(inFromClient);
                            if (params.length >= 1) {
                                sessionId = params[0];
                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.idTerminal == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    result = getPreferences(userInfo.idTerminal);
                                    if (result == null) {
                                        errorCode = UNKNOWN_ERROR;
                                        errorText = UNKNOWN_ERROR_TEXT;
                                    }
                                }
                            } else {
                                errorCode = WRONG_PARAMETER_COUNT;
                                errorText = WRONG_PARAMETER_COUNT_TEXT;
                            }
                        } catch (Exception e) {
                            logger.error("GetPreferences Unknown error", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    default:
                        result = "unknown command";
                        errorCode = UNKNOWN_COMMAND;
                        errorText = UNKNOWN_COMMAND_TEXT;
                        break;
                }

                logger.info(String.format("Command %s, error code: %s. Sending answer", command, (int) errorCode));
                if (errorText != null)
                    logger.info("error: " + errorText);
                writeByte(outToClient, stx);
                writeByte(outToClient, id);
                writeByte(outToClient, command);
                writeByte(outToClient, errorCode);
                if (errorText != null) {
                    write(outToClient, errorText);
                } else {
                    switch (command) {
                        case GET_USER_INFO:
                            if (result != null) {
                                writeBytes(outToClient, result);
                                writeByte(outToClient, esc);
                                write(outToClient, String.valueOf(System.currentTimeMillis()));
                            }
                            break;
                        case GET_ITEM_INFO:
                            if (itemInfo != null) {
                                for (int i = 0; i < 14; i++) {
                                    if (itemInfo.size() > i) {
                                        write(outToClient, itemInfo.get(i));
                                    }
                                    writeByte(outToClient, esc);
                                }
                            }
                            break;
                        case SAVE_DOCUMENT:
                        case GET_ITEM_HTML:
                        case SAVE_PALLET:
                        case CHECK_ORDER:
                        case GET_PREFERENCES:
                            if (result != null) {
                                write(outToClient, result);
                            }
                            break;
                        case GET_ALL_BASE:
                            if (fileData != null) {
                                byte[] fileBytes = fileData.getBytes();
                                write(outToClient, String.valueOf(fileBytes.length));
                                writeByte(outToClient, etx);
                                write(outToClient, fileBytes);
                            }
                    }
                }

                if (fileData == null)
                    writeByte(outToClient, etx);
                logger.info(String.format("Command %s: answer sent", command));
                Thread.sleep(1000);
                return null;
            } catch (Throwable e) {
                logger.error("Error occurred: ", e);
            } finally {
                try {
                    if (outToClient != null)
                        outToClient.close();
                    if (inFromClient != null)
                        inFromClient.close();
                } catch (IOException e) {
                    logger.error("Error occurred: ", e);
                }
            }
            return null;
        }

    }

    protected String importTerminalDocumentDetail(String idTerminalDocument, DataObject userObject, String idTerminal, List<List<Object>> terminalDocumentDetailList, boolean emptyDocument) throws SQLException {
        try (DataSession session = createSession()) {
            return terminalHandlerInterface.importTerminalDocument(session, getStack(), userObject, idTerminal, idTerminalDocument, terminalDocumentDetailList, emptyDocument);
        }
    }

    private void writeByte(DataOutputStream outToClient, byte b) throws IOException {
        outToClient.writeByte(b);
        outToClient.flush();
    }

    private void writeBytes(DataOutputStream outToClient, String s) throws IOException {
        outToClient.writeBytes(s);
        outToClient.flush();
    }

    /*private void writeChars(DataOutputStream outToClient, String s) throws IOException {
        outToClient.writeChars(s);
        outToClient.flush();
    }*/

    private void write(DataOutputStream outToClient, byte[] bytes) throws IOException {
        outToClient.write(bytes);
        outToClient.flush();
    }

    private void write(DataOutputStream outToClient, String value) throws IOException {
        outToClient.write((value == null ? "" : value).getBytes("cp1251"));
        outToClient.flush();
    }

    private String getUnknownErrorText(Exception e) {
        String errorText = e.getMessage();
        if(errorText == null)
            errorText = UNKNOWN_ERROR_TEXT;
        else if(errorText.contains("\n"))
            errorText = errorText.substring(0, errorText.indexOf('\n'));
        return errorText;
    }

    private class UserInfo {
        DataObject user;
        String idTerminal;

        public UserInfo(DataObject user, String idTerminal) {
            this.user = user;
            this.idTerminal = idTerminal;
        }
    }
}