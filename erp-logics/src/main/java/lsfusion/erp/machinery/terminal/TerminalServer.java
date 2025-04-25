package lsfusion.erp.machinery.terminal;

import lsfusion.base.file.RawFileData;
import lsfusion.erp.ERPLoggers;
import lsfusion.server.base.controller.lifecycle.LifecycleEvent;
import lsfusion.server.base.controller.manager.MonitorServer;
import lsfusion.server.base.controller.thread.ExecutorFactory;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.physics.exec.db.controller.manager.DBManager;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.math.BigDecimal;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static lsfusion.erp.integration.DefaultIntegrationAction.sqlDateToLocalDate;
import static lsfusion.erp.integration.DefaultIntegrationAction.sqlTimestampToLocalDateTime;

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
    public static byte GET_PREFERENCES_NOPREFERENCES = 112;
    public static String GET_PREFERENCES_NOPREFERENCES_TEXT = "Конфигурация для ТСД не определена";
    public static byte LOT_NOT_FOUND = 104;
    public static String LOT_NOT_FOUND_TEXT = "Марка не найдена";


    public static final byte GET_USER_INFO = 4;

    @Override
    protected void onInit(LifecycleEvent event) {
        super.onInit(event);
        
        terminalHandler.init();
    }

    public static final byte TEST = 1;
    public static final byte GET_ITEM_INFO = 5;
    public static final byte SAVE_DOCUMENT = 6;
    public static final byte GET_ITEM_HTML = 7;
    public static final byte GET_ALL_BASE = 8;
//    public static final byte SAVE_PALLET = 9;
    public static final byte CHECK_ORDER = 10;//0x0A
    public static final byte GET_PREFERENCES = 11;//0x0B
    public static final byte CHANGE_ORDER_STATUS = 12;//0x0C
    public static final byte TEAMWORK_DOCUMENT = 13;//0x0D
    public static final byte SET_STOCK = 14;
    public static final byte GET_MOVES = 15;
    public static final byte GET_LOT_INFO = 16;

    private static final Logger logger = ERPLoggers.terminalLogger;
    private static final Logger priceCheckerLogger = ERPLoggers.priceCheckerLogger;

    private static ConcurrentHashMap<String, UserInfo> userMap = new ConcurrentHashMap<>();

    boolean started = false;
    boolean stopped = false;
    private DefaultTerminalHandler terminalHandler;
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

    public void setTerminalHandler(DefaultTerminalHandler terminalHandler) {
        this.terminalHandler = terminalHandler;
    }

    public DBManager getDbManager() {
        return logicsInstance.getDbManager();
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        setupDaemon();
    }

    public void setupDaemon() {
        assert terminalHandler != null;

        try {
            List<Object> hostPort = terminalHandler.readHostPort(createSession());
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

    public void startListenThread() {
        //logger.info("submitting task for socket : " + socket + " " + System.identityHashCode(socket));
        Thread listenThread = new Thread(() -> {
            while (!stopped && !listenServerSocket.isClosed()) {
                try {
                    Socket socket = listenServerSocket.accept();
                    socket.setSoTimeout(30000);
                    //logger.info("submitting task for socket : " + socket + " " + System.identityHashCode(socket));
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
            return terminalHandler.getPreferences(session, getStack(), idTerminal);
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

    private LocalDateTime parseTimestamp(String value) {
        Timestamp timestamp;
        try {
            timestamp = value == null ? null : new Timestamp(DateUtils.parseDate(value, "yyyy-MM-dd HH:mm:ss").getTime());
        } catch (Exception e) {
            logger.error("Parsing timestamp failed: " + value, e);
            timestamp = null;
        }
        return sqlTimestampToLocalDateTime(timestamp);
    }

    private LocalDate parseDate(String value) {
        Date date;
        try {
            date = value == null ? null : new Date(DateUtils.parseDate(value, "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss").getTime());
        } catch (Exception e) {
            logger.error("Parsing date failed: " + value, e);
            date = null;
        }
        return sqlDateToLocalDate(date);
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
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String escStr = Character.toString((char) esc);
        while ((b = inFromClient.readByte()) != 3) {
            os.write(b);
        }
        String result = os.toString("cp1251");
        return result.split(escStr, -1);
    }

    public String getSessionId(DataObject customUser, String login, String password, String idTerminal, String idApplication, String idStock) {
        String sessionId = String.valueOf((login + password + idTerminal).hashCode());
        userMap.put(sessionId, new UserInfo(customUser, login, idTerminal, idApplication, idStock));
        return sessionId;
    }

    protected Object readItem(UserInfo userInfo, String barcode, String vop) throws SQLException {
        return terminalHandler.readItem(createSession(), userInfo, barcode, vop);
    }

    protected String readItemHtml(String barcode, String idStock) throws SQLException {
        return terminalHandler.readItemHtml(createSession(), barcode, idStock);
    }

    protected String readLotInfo(String idLot) throws SQLException {
        return terminalHandler.readLotInfo(createSession(), idLot);
    }

    protected RawFileData readBase(UserInfo userInfo, boolean readBatch) throws SQLException {
        return terminalHandler.readBase(createSession(), userInfo, readBatch);
    }

//    protected String savePallet(UserInfo userInfo, String numberPallet, String nameBin) throws SQLException {
//        try (DataSession session = createSession()) {
//            return terminalHandler.savePallet(session, getStack(), userInfo, numberPallet, nameBin);
//        }
//    }

    protected String checkOrder(String numberOrder) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (DataSession session = createSession()) {
            return terminalHandler.checkOrder(session, getStack(), numberOrder);
        }
    }

    protected String changeStatusOrder(String vop, String status, String numberOrder) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (DataSession session = createSession()) {
            return terminalHandler.changeStatusOrder(session, getStack(), vop, status, numberOrder);
        }
    }

    public RawFileData teamWorkDocument(int idCommand, String json, UserInfo userInfo) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, IOException {
        try (DataSession session = createSession()) {
            return terminalHandler.teamWorkDocument(session, getStack(), idCommand, json, userInfo);
        }
    }

    public RawFileData getMoves(String barcode, UserInfo userInfo) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, IOException {
        try (DataSession session = createSession()) {
            return terminalHandler.getMoves(session, getStack(), barcode, userInfo);
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
                inFromClient.readByte(); //start byte
                byte id = inFromClient.readByte();
                byte command = inFromClient.readByte();

                String result = null;
                List<String> itemInfo = null;
                RawFileData fileData = null;
                byte errorCode = 0;
                String errorText = null;
                String sessionId = "";
    
                String idApplication = "";
                
                if (command != TEST) {
                    logger.info(getLogPrefix(socket) + "submitting task for socket : " + socket);
                    logger.info(getLogPrefix(socket) + String.format("Command %s", command));
                }

                switch (command) {
                    case TEST:
                        break;
                    case GET_USER_INFO:
                        try {
                            logger.info(getLogPrefix(socket) + "requested getUserInfo");
                            String[] params = readParams(inFromClient);
                            if (params.length >= 3) {
                                logger.info(getLogPrefix(socket) + "logging user " + params[0]);

                                String applicationVersion = "";
                                String idStock = "";
                                String deviceModel = "";

                                if (params.length > 3)
                                    idApplication = params[3];
                                if (params.length > 4)
                                    idStock = params[4];
                                if (params.length > 5)
                                    applicationVersion = params[5];
                                if (params.length > 6)
                                    deviceModel = params[6];

                                if (terminalHandler.isActiveTerminal(createSession(), getStack(), params[2])) {
                                    Object loginResult = terminalHandler.login(
                                            createSession(), getStack(), socket.getInetAddress().getHostAddress(), params[0], params[1], params[2], idApplication, applicationVersion, deviceModel);
                                    if (loginResult instanceof DataObject) {
                                        result = getSessionId((DataObject) loginResult, params[0], params[1], params[2], idApplication, idStock);
                                        logger.info(getLogPrefix(socket) + String.format("successfull login, idTerminal %s, idApplication '%s', applicationVersion '%s', idStock '%s'", userMap.get(result).idTerminal, userMap.get(result).idApplication, applicationVersion, idStock));
                                    } else if (loginResult instanceof String) {
                                        errorCode = LOGIN_ERROR;
                                        errorText = (String) loginResult;
                                    } else {
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
                            logger.error(getLogPrefix(socket) + "GetUserInfo Unknown error: ", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case SET_STOCK:
                        try {
                            logger.info(getLogPrefix(socket) + "requested setStock");
                            String[] params = readParams(inFromClient);
                            if (params.length >= 2) {
                                sessionId = params[0];
                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.user == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    logger.info(getLogPrefix(socket, userInfo) + "id stock: " + params[1]);
                                    userInfo.idStock = params[1];
                                }
                            } else {
                                errorCode = WRONG_PARAMETER_COUNT;
                                errorText = WRONG_PARAMETER_COUNT_TEXT;
                            }
                        } catch (Exception e) {
                            logger.error(getLogPrefix(socket) + "setStock Unknown error: ", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case GET_ITEM_INFO:
                        try {
                            logger.info(getLogPrefix(socket) + "requested GetItemInfo");
                            String[] params = readParams(inFromClient);
                            if (params.length >= 2) {
                                sessionId = params[0];
                                String barcode = params[1];
                                String vop = "";
                                if (params.length >= 3)
                                    vop = params[2];

                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.user == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    logger.info(getLogPrefix(socket, userInfo) + String.format("barcode '%s', vop '%s'", barcode, vop));
                                    Object readItemResult = readItem(userInfo, barcode, vop);
                                    if (readItemResult == null) {
                                        errorCode = ITEM_NOT_FOUND;
                                        errorText = ITEM_NOT_FOUND_TEXT;
                                    } else if (readItemResult instanceof String) {
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
                            logger.error(getLogPrefix(socket) + "GetItemInfo Unknown error: ", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case SAVE_DOCUMENT:
                        try {
                            logger.info(getLogPrefix(socket) + "requested SaveDocument");
                            List<String[]> params = readDocumentParams(inFromClient);
                            if (params.size() >= 1) {
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
                                        String dateDocument = document[1];
                                        String numberDocument = document[2];
                                        String idDocument = numberDocument + " " + dateDocument + " " + userInfo.user.object;
                                        String idTerminalDocumentType = document[3];
                                        String ana1 = formatValue(document[4]);
                                        String ana2 = formatValue(document[5]);
                                        //String ana3 = document[6];
                                        String comment = formatValue(document[7]);
                                        String parentDocument = document.length <= 8 ? null : formatValue(document[8]);
                                        if (parentDocument != null)
                                            parentDocument = parentDocument.replace("'", "");
    
                                        boolean markerFields = document.length > 10;
                                        Integer labelCount = null;
                                        String categories = null;
                                
                                        if (markerFields) {
                                            labelCount = parseInteger(document[9]);
                                            categories = formatValue(document[10]);
                                        }
                                        
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
                                                BigDecimal extraQuantityDocumentDetail = line.length <= 11 ? null : parseBigDecimal(line[11]);
                                                String batchDocumentDetail = line.length <= 12 ? null : formatValue(line[12]);
                                                String markDocumentDetail = line.length <= 13 ? null : formatValue(line[13]);
                                                String replaceDocumentDetail = line.length <= 14 ? null : formatValue(line[14]);
                                                String ana1DocumentDetail = line.length <= 15 ? null : formatValue(line[15]);
                                                String ana2DocumentDetail = line.length <= 16 ? null : formatValue(line[16]);

                                                String imageBase64 = line.length <= 17 ? null : formatValue(line[17]);
                                                RawFileData imageDocumentDetail = imageBase64 != null ? new RawFileData(Base64.decodeBase64(imageBase64)) : null;
    
                                                String unitLoad = line.length <= 18 ? null : formatValue(line[18]);
                                                
                                                List<Object> list = new ArrayList<>();
                                                list.add(idDocument);
                                                list.add(numberDocument);
                                                list.add(idTerminalDocumentType);
                                                list.add(ana1);
                                                list.add(ana2);
                                                list.add(comment);
                                                if (markerFields) {
                                                    list.add(labelCount);
                                                    list.add(categories);
                                                }
                                                list.add(idDocumentDetail);
                                                list.add(numberDocumentDetail);
                                                list.add(barcodeDocumentDetail);
                                                list.add(quantityDocumentDetail);
                                                list.add(priceDocumentDetail);
                                                list.add(commentDocumentDetail);
                                                list.add(parseTimestamp(dateDocumentDetail));
                                                list.add(parseDate(extraDate1DocumentDetail));
                                                list.add(parseDate(extraDate2DocumentDetail));
                                                list.add(extraField1DocumentDetail);
                                                list.add(extraField2DocumentDetail);
                                                list.add(extraField3DocumentDetail);
                                                list.add(parentDocument);
                                                list.add(extraQuantityDocumentDetail);
                                                list.add(batchDocumentDetail);
                                                list.add(markDocumentDetail);
                                                list.add(replaceDocumentDetail);
                                                list.add(ana1DocumentDetail);
                                                list.add(ana2DocumentDetail);
                                                list.add(imageDocumentDetail);
                                                list.add(unitLoad);
                                                
                                                terminalDocumentDetailList.add(list);
                                                
                                                //terminalDocumentDetailList.add(Arrays.asList(idDocument, numberDocument, idTerminalDocumentType,
                                                //        ana1, ana2, comment, labelCount, categories, idDocumentDetail, numberDocumentDetail, barcodeDocumentDetail, quantityDocumentDetail,
                                                //        priceDocumentDetail, commentDocumentDetail, parseTimestamp(dateDocumentDetail),
                                                //        parseDate(extraDate1DocumentDetail), parseDate(extraDate2DocumentDetail), extraField1DocumentDetail,
                                                //        extraField2DocumentDetail, extraField3DocumentDetail, parentDocument, extraQuantityDocumentDetail, batchDocumentDetail,
                                                //        markDocumentDetail, replaceDocumentDetail, ana1DocumentDetail, ana2DocumentDetail, imageDocumentDetail));
                                            }
                                        }
                                        logger.info(getLogPrefix(socket, userInfo) + "receiving document number " + document[2] + " : " + (params.size() - 1) + " record(s)");
                                        boolean emptyDocument = terminalDocumentDetailList.isEmpty();
                                        if (emptyDocument) {
                                            List<Object> list = new ArrayList<>();
                                            list.add(idDocument);
                                            list.add(numberDocument);
                                            list.add(idTerminalDocumentType);
                                            list.add(ana1);
                                            list.add(ana2);
                                            list.add(comment);
                                            if (markerFields) {
                                                list.add(labelCount);
                                                list.add(categories);
                                            }
    
                                            terminalDocumentDetailList.add(list);
    
                                            //terminalDocumentDetailList.add(Arrays.asList(idDocument, numberDocument, idTerminalDocumentType, ana1, ana2, comment, labelCount, categories));
                                        }
                                        result = importTerminalDocumentDetail(idDocument, userInfo, terminalDocumentDetailList, emptyDocument, markerFields);
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
                            logger.error(getLogPrefix(socket) + "SaveDocument Unknown error", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;

                    case TEAMWORK_DOCUMENT:
                        try {
                            logger.info(getLogPrefix(socket) + "requested TeamWorkDocument");

                            String[] params = readParams(inFromClient);
                            if (params.length > 0) {
                                sessionId = params[0];
                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.user == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    if (params.length >= 2) {
                                        int idCommand = Integer.parseInt(params[1]);
                                        String json = null;
                                        if (params.length > 2)
                                            json = params[2];
                                        logger.info(getLogPrefix(socket, userInfo) + String.format("idCommand=%d, json: '%s'", idCommand, json));
                                        fileData = teamWorkDocument(idCommand, json, userInfo);
                                    } else {
                                        errorCode = WRONG_PARAMETER_COUNT;
                                        errorText = WRONG_PARAMETER_COUNT_TEXT;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.error(getLogPrefix(socket) + "TeamWorkDocument Unknown error", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;

                    case GET_ITEM_HTML:
                        try {
                            logger.info(getLogPrefix(socket) + "requested GetItemHtml");
                            String[] params = readParams(inFromClient);
                            String idApp = "";
                            if (params.length >= 2) {
                                String barcode = params[0];
                                String idStock = params[1];
                                if (params.length >= 3)
                                    idApp = params[2];

                                priceCheckerLogger.info(getLogPrefix(socket) + String.format("barcode '%s', stock '%s', application '%s'", params[0], params[1], idApp));
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
                            priceCheckerLogger.error(getLogPrefix(socket) + "request failed: ", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case GET_LOT_INFO:
                        try {
                            logger.info(getLogPrefix(socket) + "requested GetLotInfo");
                            String[] params = readParams(inFromClient);
                            if (params.length >= 2) {
                                sessionId = params[0];
                                String idLot = params[1];

                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.user == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    priceCheckerLogger.info(getLogPrefix(socket, userInfo) + String.format("idLot '%s'", idLot));
                                    result = readLotInfo(idLot);
                                    if (result == null) {
                                        errorCode = LOT_NOT_FOUND;
                                        errorText = LOT_NOT_FOUND_TEXT;
                                    }
                                }
                            } else {
                                errorCode = WRONG_PARAMETER_COUNT;
                                errorText = WRONG_PARAMETER_COUNT_TEXT;
                            }
                        } catch (Exception e) {
                            priceCheckerLogger.error(getLogPrefix(socket) + "request failed: ", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case GET_ALL_BASE:
                        try {
                            logger.info(getLogPrefix(socket) + "requested GetAllBase");
                            String[] params = readParams(inFromClient);
                            if (params.length > 0) {
                                sessionId = params[0];
                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.user == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    boolean readBatch = (params.length > 1 && params[1].equalsIgnoreCase("1"));
                                    if (readBatch)
                                        logger.info(getLogPrefix(socket, userInfo) + "requested readBatch");
                                    fileData = readBase(userInfo, readBatch);
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
                            logger.error(getLogPrefix(socket) + "GetAllBase Unknown error: ", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
//                    case SAVE_PALLET:
//                        try {
//                            logger.info(getLogPrefix(socket) + "received pallet");
//                            String[] params = readParams(inFromClient);
//                            if (params.length >= 3) {
//                                sessionId = params[0];
//                                String numberPallet = params[1];
//                                String nameBin = params[2];
//                                UserInfo userInfo = userMap.get(sessionId);
//                                if (userInfo == null || userInfo.user == null) {
//                                    errorCode = AUTHORISATION_REQUIRED;
//                                    errorText = AUTHORISATION_REQUIRED_TEXT;
//                                } else {
//                                    logger.info(getLogPrefix(socket) + String.format("%s, idTerminal '%s', idApplication '%s'", command, userInfo.idTerminal, userInfo.idApplication));
//                                    result = savePallet(userInfo, numberPallet, nameBin);
//                                    if (result != null) {
//                                        errorCode = SAVE_PALLET_ERROR;
//                                        errorText = result;
//                                    }
//                                }
//                            } else {
//                                errorCode = WRONG_PARAMETER_COUNT;
//                                errorText = WRONG_PARAMETER_COUNT_TEXT;
//                            }
//                        } catch (Exception e) {
//                            logger.error(getLogPrefix(socket) + "SavePallet Unknown error", e);
//                            errorCode = UNKNOWN_ERROR;
//                            errorText = getUnknownErrorText(e);
//                        }
//                        break;
                    case CHECK_ORDER:
                        try {
                            logger.info(getLogPrefix(socket) + "requested CheckOrder");
                            String[] params = readParams(inFromClient);
                            if (params.length >= 2) {
                                sessionId = params[0];
                                String numberOrder = params[1];
                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.user == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    result = checkOrder(numberOrder);
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
                            logger.error(getLogPrefix(socket) + "CheckOrder Unknown error", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case CHANGE_ORDER_STATUS:
                        try {
                            logger.info(getLogPrefix(socket) + "requested ChangeOrderStatus");
                            String[] params = readParams(inFromClient);
                            if (params.length >= 4) {
                                sessionId = params[0];
                                String vop = params[1];
                                String status = params[2];
                                String numberOrder = params[3];
                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.user == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    changeStatusOrder(vop, status, numberOrder);
                                }
                            } else {
                                errorCode = WRONG_PARAMETER_COUNT;
                                errorText = WRONG_PARAMETER_COUNT_TEXT;
                            }
                        } catch (Exception e) {
                            logger.error(getLogPrefix(socket) + "ChangeOrderStatus Unknown error", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case GET_PREFERENCES:
                        try {
                            logger.info(getLogPrefix(socket) + "GetPreferences");
                            String[] params = readParams(inFromClient);
                            if (params.length >= 1) {
                                sessionId = params[0];
                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.user == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    result = getPreferences(userInfo.idTerminal);
                                    if (result == null) {
                                        errorCode = GET_PREFERENCES_NOPREFERENCES;
                                        errorText = GET_PREFERENCES_NOPREFERENCES_TEXT;
                                    }
                                }
                            } else {
                                errorCode = WRONG_PARAMETER_COUNT;
                                errorText = WRONG_PARAMETER_COUNT_TEXT;
                            }
                        } catch (Exception e) {
                            logger.error(getLogPrefix(socket) + "GetPreferences Unknown error", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    case GET_MOVES: {
                        try {
                            logger.info(getLogPrefix(socket) + "GetMoves");

                            String[] params = readParams(inFromClient);
                            if (params.length > 0) {
                                sessionId = params[0];
                                UserInfo userInfo = userMap.get(sessionId);
                                if (userInfo == null || userInfo.user == null) {
                                    errorCode = AUTHORISATION_REQUIRED;
                                    errorText = AUTHORISATION_REQUIRED_TEXT;
                                } else {
                                    if (params.length >= 2) {
                                        String barcode = params[1];
                                        logger.info(getLogPrefix(socket, userInfo) + String.format("%s, barcode='%s'", command, barcode));
                                        fileData = getMoves(barcode, userInfo);
                                        if (fileData == null) {
                                            errorCode = UNKNOWN_ERROR;
                                            errorText = UNKNOWN_ERROR_TEXT;
                                        }
                                    } else {
                                        errorCode = WRONG_PARAMETER_COUNT;
                                        errorText = WRONG_PARAMETER_COUNT_TEXT;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.error(getLogPrefix(socket) + "GetMoves Unknown error", e);
                            errorCode = UNKNOWN_ERROR;
                            errorText = getUnknownErrorText(e);
                        }
                        break;
                    }
                    default:
                        result = "unknown command";
                        errorCode = UNKNOWN_COMMAND;
                        errorText = UNKNOWN_COMMAND_TEXT;
                        break;
                }

                if (command != TEST)
                    logger.info(getLogPrefix(socket) + String.format("Command %s, error code: %s. Sending answer", command, (int) errorCode));

                if (errorText != null)
                    logger.info(getLogPrefix(socket) + "error: " + errorText);

                writeByte(outToClient, stx);
                writeByte(outToClient, id);
                writeByte(outToClient, command);
                writeByte(outToClient, errorCode);
                if (errorText != null) {
                    write(outToClient, errorText);
                    writeByte(outToClient, etx);
                } else {
                    switch (command) {
                        case GET_USER_INFO:

                            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");

                            int flags = 0;
                            String userName = "";
                            String nameStock = "";
                            String accessList = "";

                            try {
                                if (terminalHandlerLM != null) {

                                    Integer countDaysFilterBatches = (Integer) terminalHandlerLM.findProperty("countDaysFilterBatches[]").read(createSession());
                                    if (countDaysFilterBatches != null && countDaysFilterBatches > 0)
                                        flags = 1;

                                    UserInfo userInfo = userMap.get(result);

                                    if (userInfo != null && userInfo.user != null) {
                                        userName = (String) terminalHandlerLM.findProperty("name[CustomUser]").read(createSession(), userInfo.user);
                                        nameStock = (String) terminalHandlerLM.findProperty("nameStock[Employee]").read(createSession(), userInfo.user);
                                        accessList = (String) terminalHandlerLM.findProperty("accessList[Employee]").read(createSession(), userInfo.user);
                                    }
                                }
                            }
                            catch (Exception e) {
                                logger.error(getLogPrefix(socket) + "getUserInfo Unknown error", e);
                                errorText = getUnknownErrorText(e);
                                write(outToClient, errorText);
                                writeByte(outToClient, etx);
                                break;
                            }

                            if (result != null) {
                                writeBytes(outToClient, result);
                                writeByte(outToClient, esc);
                                if (StringUtils.isEmpty(idApplication))
                                    write(outToClient, String.valueOf(System.currentTimeMillis()));
                                else
                                    write(outToClient, getLogicsInstance().getBusinessLogics().topModule);
                                writeByte(outToClient, esc);
                                writeBytes(outToClient, String.valueOf(flags));
                                writeByte(outToClient, esc);
                                write(outToClient, userName);
                                writeByte(outToClient, esc);
                                write(outToClient, nameStock);
                                writeByte(outToClient, esc);
                                write(outToClient, accessList);
                            }
                            writeByte(outToClient, etx);
                            break;
                        case TEST:
                            writeByte(outToClient, etx);
                            break;
                        case GET_ITEM_INFO:
                            if (itemInfo != null) {
                                for (int i = 0; i < itemInfo.size(); i++) {
                                    write(outToClient, itemInfo.get(i));
                                    writeByte(outToClient, esc);
                                }
                            }
                            writeByte(outToClient, etx);
                            break;
                        case SAVE_DOCUMENT:
                        case GET_ITEM_HTML:
                        case GET_LOT_INFO:
//                        case SAVE_PALLET:
                        case CHECK_ORDER:
                        case CHANGE_ORDER_STATUS:
                        case SET_STOCK:
                            if (result != null) {
                                write(outToClient, result);
                            }
                            writeByte(outToClient, etx);
                            break;
                        case GET_PREFERENCES:
                            if (result != null) {
                                byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
                                write(outToClient, String.valueOf(bytes.length));
                                writeByte(outToClient, etx);
                                write(outToClient, bytes);
                            }
                            break;
                        case GET_ALL_BASE:
                        case TEAMWORK_DOCUMENT:
                        case GET_MOVES:
                            int bytes = fileData != null ? fileData.getLength() : 0;
                            write(outToClient, String.valueOf(bytes));
                            writeByte(outToClient, etx);
                            if (fileData != null)
                                write(outToClient, fileData.getBytes());
                    }
                }

                if (command != TEST)
                    logger.info(getLogPrefix(socket) + String.format("Command %s: answer sent", command));

                Thread.sleep(1000);
                return null;
            } catch (Throwable e) {
                logger.error(getLogPrefix(socket) + "Error occurred for socket " + socket + " :", e);
            } finally {
                try {
                    if (outToClient != null)
                        outToClient.close();
                    if (inFromClient != null)
                        inFromClient.close();
                } catch (IOException e) {
                    logger.error(getLogPrefix(socket) + "Error occurred: ", e);
                }
            }
            return null;
        }

    }

    protected String importTerminalDocumentDetail(String idTerminalDocument, UserInfo userInfo, List<List<Object>> terminalDocumentDetailList, boolean emptyDocument, boolean marker) throws SQLException {
        try (DataSession session = createSession()) {
            return terminalHandler.importTerminalDocument(session, getStack(), userInfo, idTerminalDocument, terminalDocumentDetailList, emptyDocument, marker);
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

    private void write(DataOutputStream outToClient, byte[] bytes) throws IOException {
        outToClient.write(bytes);
        outToClient.flush();
    }

    private void write(DataOutputStream outToClient, String value) throws IOException {
        outToClient.write((value == null ? "" : value).getBytes("cp1251"));
        outToClient.flush();
    }

    private String getUnknownErrorText(Exception e) {
        String errorText = e.toString();
        if(errorText == null)
            errorText = UNKNOWN_ERROR_TEXT;
        else if(errorText.contains("\n"))
            errorText = errorText.substring(0, errorText.indexOf('\n'));
        return errorText;
    }

    private String getLogPrefix(Socket socket, UserInfo userInfo) {
        return getLogPrefix(socket) + userInfo.login + " / " + userInfo.idTerminal + " ";
    }

    private String getLogPrefix(Socket socket) {
        return System.identityHashCode(socket)+" ";
    }
}