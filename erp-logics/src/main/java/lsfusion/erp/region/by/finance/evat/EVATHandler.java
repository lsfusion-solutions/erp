package lsfusion.erp.region.by.finance.evat;

import by.avest.certstore.AvCertStoreProvider;
import by.avest.crypto.pkcs11.provider.ProviderFactory;
import by.avest.edoc.client.*;
import by.avest.net.tls.AvTLSProvider;
import com.google.common.base.Throwables;
import org.apache.commons.io.FileUtils;

import javax.management.modelmbean.XMLParseException;
import java.io.*;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class EVATHandler {

    private static final SimpleDateFormat sdf;
    static {
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Minsk"));
    }

    private static final String XSD_FOR_ORIGINAL_TYPE = "MNSATI_original.xsd ";
    private static final String XSD_FOR_FIXED_TYPE = "MNSATI_fixed.xsd ";
    private static final String XSD_FOR_ADDITIONAL_TYPE = "MNSATI_additional.xsd ";

    public List<List<Object>> signAndSend(Map<String, Map<Integer, byte[]>> files, String serviceUrl, String path, String exportPath, String password, int certIndex) {
        System.out.println("EVAT: client action signAndSend");
        List<List<Object>> result = new ArrayList<>();

        String xsdPath = path + "/xsd";
        File archiveDir = new File(exportPath == null ? (path + "/archive") : exportPath);

        for (Map.Entry<String, Map<Integer, byte[]>> filesEntry : files.entrySet()) {
            String unp = filesEntry.getKey();
            System.out.println(String.format("EVAT: sending %s xmls, unp %s", filesEntry.getValue().size(), unp));

            EVatService service = null;

            try {
                service = initService(serviceUrl, unp, password, certIndex);
                if (archiveDir.exists() || archiveDir.mkdirs()) {
                    for (Map.Entry<Integer, byte[]> entry : filesEntry.getValue().entrySet()) {
                        result.add(sendFile(entry.getValue(), entry.getKey(), service, archiveDir, xsdPath, serviceUrl, unp, password, certIndex, 0));
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

    private List<Object> sendFile(byte[] file, Integer evat, EVatService service, File archiveDir, String xsdPath,
                                  String serviceUrl, String unp, String password, Integer certIndex, Integer errorsCount)
            throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, AvDocException, InvalidAlgorithmParameterException, IOException {
        List<Object> result;
        try {
            System.out.println(String.format("EVAT %s: save file before sending", evat));
            File originalFile = new File(archiveDir, "EVAT" + evat + ".xml");
            FileUtils.writeByteArrayToFile(originalFile, file);

            // Создание электронного документа
            AvEDoc eDoc = service.createEDoc();

            // Загрузка электронной счет-фактуры НДС
            eDoc.getDocument().load(file);

            // Проверка счет-фактуры НДС на соответствие XSD схеме
            byte[] xsdSchema = loadXsdSchema(xsdPath, eDoc.getDocument().getXmlNodeValue("issuance/general/documentType"));
            boolean isDocumentValid = eDoc.getDocument().validateXML(xsdSchema);
            if (!isDocumentValid) {
                result = Arrays.asList((Object) evat, String.format("EVAT %s: Структура документа не отвечает XSD схеме", evat), true);
            } else {

                eDoc.sign();
                byte[] signedDocument = eDoc.getEncoded();
                File signedFile = new File(archiveDir, "EVAT" + evat + ".sgn.xml");

                // Сохранение файла с подписанным электронным документом
                writeFile(signedFile, signedDocument);

                //далее - код по отправке документа, который не проверялся на работоспособность, чтобы
                //случайно ничего никуда не отправить

                // Загрузка электронного документа на автоматизированный сервис
                // портала и получение квитанции о приёме
                AvETicket ticket = service.sendEDoc(eDoc);

                // Проверка квитанции
                if (ticket.accepted()) {
                    System.out.println(String.format("EVAT %s: SignAndSend. Ticket is accepted", evat));
                    String resultMessage = ticket.getMessage();

                    File ticketFile = new File(archiveDir, "EVAT" + evat + ".ticket.xml");
                    // Сохранение квитанции в файл
                    writeFile(ticketFile, ticket.getEncoded());

                    System.out.println("Ответ сервера проверен. Cчет/фактура принята в обработку. "
                            + "Сообщение сервера: " + resultMessage);
                    result = Arrays.asList((Object) evat, resultMessage, false);

                } else {
                    System.out.println(String.format("EVAT %s: SignAndSend. Ticket is not accepted", evat));
                    AvError err = ticket.getLastError();
                    File ticketFile = new File(archiveDir, "EVAT" + evat + ".ticket.error.xml");
                    // Сохранение квитанции в файл
                    writeFile(ticketFile, ticket.getEncoded());
                    System.out.println(err.getMessage());
                    result = Arrays.asList((Object) evat, err.getMessage(), true);
                }

                //конец непроверенного кода
                System.out.println(String.format("EVAT %s: send file finished", evat));
            }

        } catch (Exception e) {
            System.out.println(String.format("EVAT %s: Error occurred (errors count %s)", evat, errorsCount + 1));
            if(errorsCount < 5) {
                errorsCount++;
                service = initService(serviceUrl, unp, password, certIndex);
                return sendFile(file, evat, service, archiveDir, xsdPath, serviceUrl, unp, password, certIndex, errorsCount);

            } else
                return Arrays.asList((Object) evat, e.getMessage(), true);
        }
        return result;
    }

    public List<List<Object>> getStatus(Map<String, Map<Integer, String>> invoices, String serviceUrl, String password, int certIndex) {
        System.out.println("EVAT: client action getStatus");
        List<List<Object>> result = new ArrayList<>();

        URL url = getClass().getClassLoader().getResource("");
        System.out.println("EVAT: url: " + url);

        EVatService service = null;

        try {

            for(Map.Entry<String, Map<Integer, String>> entry : invoices.entrySet()) {
                String unp = entry.getKey();
                Map<Integer, String> invoicesMap = entry.getValue();

                service = initService(serviceUrl, unp, password, certIndex);

                for(Map.Entry<Integer, String> invoiceEntry : invoicesMap.entrySet()) {
                    Integer evat = invoiceEntry.getKey();
                    String invoiceNumber = invoiceEntry.getValue();
                    AvEStatus status = service.getStatus(invoiceNumber);

                    // Проверка ЭЦП электронного документа
                    boolean verified = status.verify();
                    String resultMessage = verified ? status.getMessage() : status.getLastError().getMessage();
                    String resultStatus = verified ? status.getStatus() : null;
                    System.out.println(String.format("EVAT %s: Cтатус %s, сообщение %s", invoiceNumber, resultStatus, resultMessage));
                    result.add(Arrays.asList((Object) evat, resultMessage, resultStatus));
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            disconnect(service);
        }
        return result;
    }

    public String listAndGet(String path, String serviceUrl, String unp, String password, int certIndex) {
        String result = null;

        try {

            File docFolder = new File(path + "/in");
            if (docFolder.exists() || docFolder.mkdirs()) {

                EVatService service = initService(serviceUrl, unp, password, certIndex);

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

    private EVatService initService(String serviceUrl, String unp, String password, int certIndex) throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, AvDocException, InvalidAlgorithmParameterException, KeyManagementException {
        System.out.println("EVAT: initService started");
        // Регистрация провайдера AvJceProv
        ProviderFactory.addAvUniversalProvider();
        Security.addProvider(new AvTLSProvider());
        Security.addProvider(new AvCertStoreProvider());

        // Создание экземпляра класса доступа к порталу
        EVatService service = new EVatService(serviceUrl, new CustomKeyInteractiveSelector(certIndex));
        service.login((unp == null ? "" : ("UNP=" + unp + ";")) + "PASSWORD_KEY=" + password);
        service.connect();
        System.out.println("EVAT: initService finished");
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

    //Загрузка XSD схемы из файла
    private static byte[] loadXsdSchema(String xsdFolderName, String doctype) throws Exception {
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