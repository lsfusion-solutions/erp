package lsfusion.erp.region.by.finance.evat;

import by.avest.certstore.AvCertStoreProvider;
import by.avest.crypto.pkcs11.provider.ProviderFactory;
import by.avest.edoc.client.*;
import by.avest.net.tls.AvTLSProvider;
import com.google.common.base.Throwables;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.server.ServerLoggers;

import javax.management.modelmbean.XMLParseException;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.*;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class EVATClientAction implements ClientAction {

    private static final SimpleDateFormat sdf;
    static {
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Minsk"));
    }

    private static final String XSD_FOR_ORIGINAL_TYPE = "MNSATI_original.xsd ";
    private static final String XSD_FOR_FIXED_TYPE = "MNSATI_fixed.xsd ";
    private static final String XSD_FOR_ADDITIONAL_TYPE = "MNSATI_additional.xsd ";

    public Map<Integer, File> files;
    public String serviceUrl; //"https://ws.vat.gov.by:443/InvoicesWS/services/InvoicesPort?wsdl"
    public String path; //"c:/Program Files/Avest/AvJCEProv";
    public String password; //"191217635";
    public int type;

    public EVATClientAction(String serviceUrl, String path, String password, int type) {
        this(new HashMap<Integer, File>(), serviceUrl, path, password, type);
    }

    public EVATClientAction(Map<Integer, File> files, String serviceUrl, String path, String password, int type) {
        this.files = files;
        this.serviceUrl = serviceUrl;
        this.path = path;
        this.password = password;
        this.type = type;
    }

    @Override
    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        switch (type) {
            case 0:
                return signAndSend();
            case 1:
                return listAndGet();
            default:
                return null;
        }
    }

    private List<List<Object>> signAndSend() {
        List<List<Object>>  result = new ArrayList<>();

        String xsdPath = path + "/xsd";
        File archiveDir = new File(path + "/archive");

        URL url = getClass().getClassLoader().getResource("");
        if(url != null) {
            // Создание экземпляра класса доступа к порталу
            EVatService service = null;

            try {
                service = initService();

                if (archiveDir.exists() || archiveDir.mkdirs()) {

                    for (Map.Entry<Integer, File> entry : files.entrySet()) {
                        Integer evat = entry.getKey();
                        File file = entry.getValue();

                        // Создание электронного документа
                        AvEDoc eDoc = service.createEDoc();

                        // Загрузка электронной счет-фактуры НДС
                        eDoc.getDocument().load(readFile(file));

                        // Проверка счет-фактуры НДС на соответствие XSD схеме
                        byte[] xsdSchema = loadXsdSchema(xsdPath, eDoc.getDocument().getXmlNodeValue("issuance/general/documentType"));
                        boolean isDocumentValid = eDoc.getDocument().validateXML(xsdSchema);
                        if (!isDocumentValid) {
                            result.add(Arrays.asList((Object) evat, "Структура документа не отвечает XSD схеме", true));
                        } else {

                            eDoc.sign();
                            byte[] signedDocument = eDoc.getEncoded();
                                File signedFile = new File(archiveDir, file.getName() + ".sgn.xml");

                                // Сохранение файла с подписанным электронным документом
                                writeFile(signedFile, signedDocument);

                                //далее - код по отправке документа, который не проверялся на работоспособность, чтобы
                                //случайно ничего никуда не отправить

                                // Загрузка электронного документа на автоматизированный сервис
                                // портала и получение квитанции о приёме
                                AvETicket ticket = service.sendEDoc(eDoc);
                                ServerLoggers.importLogger.info("SignAndSend EVAT: Ответ сервера получен");

                                // Проверка квитанции
                                if (ticket.accepted()) {
                                    ServerLoggers.importLogger.info("SignAndSend EVAT: Ticket is accepted");
                                    String resultMessage = ticket.getMessage();

                                    File ticketFile = new File(archiveDir, file.getName() + ".ticket.xml");
                                    // Сохранение квитанции в файл
                                    writeFile(ticketFile, ticket.getEncoded());

                                    ServerLoggers.importLogger.info("Ответ сервера проверен. Cчет/фактура принята в обработку. "
                                            + "Сообщение сервера: " + resultMessage);
                                    result.add(Arrays.asList((Object) evat, resultMessage, false));

                                } else {
                                    ServerLoggers.importLogger.info("SignAndSend EVAT: Ticket is not accepted");
                                    AvError err = ticket.getLastError();
                                    File ticketFile = new File(archiveDir, file.getName() + ".ticket.error.xml");
                                    // Сохранение квитанции в файл
                                    writeFile(ticketFile, ticket.getEncoded());
                                    ServerLoggers.importLogger.error(err.getMessage());
                                    result.add(Arrays.asList((Object) evat, err.getMessage(), true));
                                }

                                //конец непроверенного кода

                        }
                    }
                } else {
                    result.add(Arrays.<Object>asList(0, "Unable to create archive directory", true));
                }

            } catch (Exception e) {
                throw Throwables.propagate(e);
            } finally {
                disconnect(service);
            }
        }
        return result;
    }

    public String listAndGet() {
        String result = null;

        try {

            File docFolder = new File(path + "/in");
            if(docFolder.exists() || docFolder.mkdirs()) {

                // Создание экземпляра класса доступа к порталу
                EVatService service = initService();

                // Чтение даты последнего запроса списка счетов-фактур на портале
                Date fromDate;
                Properties properties = new Properties();
                File file = new File(docFolder, "ListEDocuments.dat");
                if (file.exists() || file.createNewFile()) {

                    try (FileInputStream fileInput = new FileInputStream(file)) {
                        properties.load(fileInput);
                    }

                    String fromDateStr = properties.getProperty("fromDate");
                    if (fromDateStr != null) {
                        fromDate = string2Date(fromDateStr);
                    } else {
                        // Дата последнего запроса не сохранена в файл, запрашиваем за
                        // последние 365 дней
                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.DATE, -365);
                        fromDate = cal.getTime();
                    }

                    // Получение списка поступивщих счетов-фактур НДС
                    AvEList list = service.getList(fromDate);
                    // Проверка ЭЦП электронного документа
                    boolean isValid = list.verify();
                    if (isValid) {

                        for (int i = 0; i < list.getCount(); i++) {
                            String invoiceNum = list.getItemAttribute(i, "document/number");

                            // Получение электронного документа счет-фактуры по номеру
                            AvEDoc eDocXml = service.getEDoc(invoiceNum);

                            // Проверка ЭЦП электронного документа
                            isValid = eDocXml.verify();
                            if (isValid) {

                                // Сохранение электронного документа в файл
                                File docFile = new File(docFolder, "invoice-" + invoiceNum + "-verified.xml");
                                writeFile(docFile, eDocXml.getEncoded());

                                // Сохранение счет-фактуры НДС в файл
                                File issuanceFile = new File(docFolder, "invoice-" + invoiceNum + ".xml");
                                writeFile(issuanceFile, eDocXml.getDocument().getEncoded());
                            } else {
                                // Сохранение электронного документа в файл
                                writeFile(new File(docFolder, "ERROR-invoice-" + invoiceNum + ".xml"), eDocXml.getEncoded());
                                result = "Ошибка: получена некорректная счет-фактура с номером " + invoiceNum + ": " + eDocXml.getLastError().getMessage();
                            }
                        }

                    } else {
                        result = "Ошибка: получен некорректный список поступивших счетов-фактур";
                    }

                    // Сохранение даты получения списка счетов-фактур для следующего запуска
                    Date toDate = list.getToDate();
                    properties.setProperty("fromDate", date2String(toDate));
                    try (FileOutputStream out = new FileOutputStream(new File(docFolder, "ListEDocuments.dat"))) {
                        properties.store(out, "");
                    }

                    disconnect(service);

                } else {
                    result = "Unable to create ListEDocuments file";
                }
            } else {
                result = "Unable to create in directory";
            }
        } catch (Exception e) {
            result = "Ошибка: " + e.getMessage();
        }
        return result;
    }

    private EVatService initService() throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, AvDocException, InvalidAlgorithmParameterException, KeyManagementException {
        addPath(path + "/avjavasecprov-shared.jar");
        addPath(path + "/avjavasecprovintf.jar");
        addPath(path + "/avoids.jar");
        addPath(path + "/avjceprovlib-avtoken-shared.jar");

        // Регистрация провайдера AvJceProv
        ProviderFactory.addAvUniversalProvider();
        Security.addProvider(new AvTLSProvider());
        Security.addProvider(new AvCertStoreProvider());

        EVatService service = new EVatService(serviceUrl, new CustomKeyInteractiveSelector(password));
        service.login();
        service.connect();
        return service;
    }

    private void disconnect(EVatService service) {
        if (service != null) {
            try {
                // Завершение работы с сервисом
                service.disconnect();
                // Завершение авторизованной сессии
                service.logout();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    public static void addPath(String s) {
        try {
            File f = new File(s);
            URL u = f.toURL();
            URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Class urlClass = URLClassLoader.class;
            Method method = urlClass.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(urlClassLoader, u);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Загрузка XSD схемы из файла
    private static byte[] loadXsdSchema(String xsdFolderName, String doctype)
            throws Exception {
        // validate XSD schema
        File xsdFile;
        doctype = (doctype == null) ? "" : doctype;

        if (doctype.equalsIgnoreCase("ORIGINAL")
                || doctype.equalsIgnoreCase("ADD_NO_REFERENCE")) {
            xsdFile = new File(xsdFolderName, XSD_FOR_ORIGINAL_TYPE);
        } else if (doctype.equalsIgnoreCase("FIXED")) {
            xsdFile = new File(xsdFolderName, XSD_FOR_FIXED_TYPE);
        } else if (doctype.equalsIgnoreCase("ADDITIONAL")) {
            xsdFile = new File(xsdFolderName, XSD_FOR_ADDITIONAL_TYPE);
        } else {
            throw new XMLParseException("Ошибка: неизвестный тип счет-фактуры '" + doctype + "'.");
        }

        if (!xsdFile.exists() && !xsdFile.isFile()) {
            throw new Exception("Ошибка: невозможно загрузить XSD файл \"" + xsdFile.getAbsolutePath() + "\"");
        }

        return readFile(xsdFile);
    }

    //Чтение файла
    private static byte[] readFile(File file) throws IOException {
        byte[] fileData = new byte[(int) file.length()];
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            dis.readFully(fileData);
        }
        return fileData;
    }

    //Запись файла
    private static void writeFile(File file, byte[] data) throws IOException {
        try (DataOutputStream os = new DataOutputStream(new FileOutputStream(file))) {
            os.write(data);
        }
    }

    public static Date string2Date(String date) throws ParseException {
        return sdf.parse(date);
    }

    public static String date2String(Date date) {
        return sdf.format(date);
    }
}