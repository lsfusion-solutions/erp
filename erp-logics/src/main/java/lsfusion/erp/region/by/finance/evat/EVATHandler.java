package lsfusion.erp.region.by.finance.evat;

import by.avest.certstore.AvCertStoreProvider;
import by.avest.crypto.pkcs11.provider.ProviderFactory;
import by.avest.edoc.client.*;
import by.avest.net.tls.AvTLSProvider;
import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.management.modelmbean.XMLParseException;
import java.io.*;
import java.net.URL;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.*;

public class EVATHandler {

    static Logger logger;
    static {
        try {
            logger = Logger.getLogger("evatlog");
            logger.setLevel(Level.INFO);
            FileAppender fileAppender = new FileAppender(new EnhancedPatternLayout("%d{DATE} %5p %c{1} - %m%n%throwable{1000}"),
                    "logs/evat.log");
            logger.removeAllAppenders();
            logger.addAppender(fileAppender);

        } catch (Exception ignored) {
        }
    }

    private static final SimpleDateFormat sdf;
    static {
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Minsk"));
    }

    private static final String XSD_FOR_ORIGINAL_TYPE = "MNSATI_original.xsd ";
    private static final String XSD_FOR_FIXED_TYPE = "MNSATI_fixed.xsd ";
    private static final String XSD_FOR_ADDITIONAL_TYPE = "MNSATI_additional.xsd ";

    public List<List<Object>> signAndSend(Map<String, Map<Long, List<Object>>> files, String serviceUrl, String path, String exportPath, String password, String certNumber, int certIndex) {
        logger.info("EVAT: client action signAndSend");
        List<List<Object>> result = new ArrayList<>();

        String xsdPath = path + "/xsd";
        File archiveDir = new File(exportPath == null ? (path + "/archive") : exportPath);

        for (Map.Entry<String, Map<Long, List<Object>>> filesEntry : files.entrySet()) {
            String unp = filesEntry.getKey();
            logger.info(String.format("EVAT: sending %s xmls, unp %s", filesEntry.getValue().size(), unp));

            EVatService service = null;

            try {
                service = initService(serviceUrl, unp, password, certNumber, certIndex);
                if (archiveDir.exists() || archiveDir.mkdirs()) {
                    for (Map.Entry<Long, List<Object>> entry : filesEntry.getValue().entrySet()) {
                        result.add(sendFile(entry.getValue(), entry.getKey(), service, archiveDir, xsdPath, serviceUrl, unp, password, certNumber, certIndex, 0));
                    }
                } else {
                    result.add(Arrays.asList(0, "Unable to create archive directory", true));
                }

            } catch (Exception e) {
                logger.error("Sign and send error", e);
                throw Throwables.propagate(e);
            } finally {
                disconnect(service);
            }
        }
        return result;
    }

    private List<Object> sendFile(List<Object> fileNumberEntry, Long evat, EVatService service, File archiveDir, String xsdPath,
                                  String serviceUrl, String unp, String password, String certNumber, Integer certIndex, Integer errorsCount)
            throws Exception {
        List<Object> result;
        RawFileData file = (RawFileData) fileNumberEntry.get(0);
        String number = (String) fileNumberEntry.get(1);
        try {
            logger.info(String.format("EVAT %s: save file before sending", number));
            File originalFile = new File(archiveDir, "EVAT" + evat + ".xml");
            file.write(originalFile);

            // Создание электронного документа
            AvEDoc eDoc = service.createEDoc();

            // Загрузка электронной счет-фактуры НДС
            eDoc.getDocument().load(file.getBytes());

            // Проверка счет-фактуры НДС на соответствие XSD схеме
            byte[] xsdSchema = loadXsdSchema(xsdPath, eDoc.getDocument().getXmlNodeValue("issuance/general/documentType"));
            boolean isDocumentValid = eDoc.getDocument().validateXML(xsdSchema);
            if (!isDocumentValid) {
                result = Arrays.asList(evat, String.format("EVAT %s: Структура документа не отвечает XSD схеме", number), true);
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
                    logger.info(String.format("EVAT %s: SignAndSend. Ticket is accepted", number));
                    String resultMessage = ticket.getMessage();

                    File ticketFile = new File(archiveDir, "EVAT" + evat + ".ticket.xml");
                    // Сохранение квитанции в файл
                    writeFile(ticketFile, ticket.getEncoded());

                    logger.info("Ответ сервера проверен. Cчет/фактура принята в обработку. "
                            + "Сообщение сервера: " + resultMessage);
                    result = Arrays.asList(evat, resultMessage, false);

                } else {
                    logger.info(String.format("EVAT %s: SignAndSend. Ticket is not accepted", number));
                    AvError err = ticket.getLastError();
                    File ticketFile = new File(archiveDir, "EVAT" + evat + ".ticket.error.xml");
                    // Сохранение квитанции в файл
                    writeFile(ticketFile, ticket.getEncoded());
                    logger.info(err.getMessage());
                    result = Arrays.asList(evat, err.getMessage(), true);
                }

                //конец непроверенного кода
                logger.info(String.format("EVAT %s: send file finished", number));
            }

        } catch (Exception e) {
            logger.info(String.format("EVAT %s: Error occurred (errors count %s)", number, errorsCount + 1));
            if (errorsCount < 5) {
                errorsCount++;
                service = initService(serviceUrl, unp, password, certNumber, certIndex);
                return sendFile(fileNumberEntry, evat, service, archiveDir, xsdPath, serviceUrl, unp, password, certNumber, certIndex, errorsCount);

            } else {
                logger.error("Send file error", e);
                return Arrays.asList(evat, e.getMessage(), true);
            }
        }
        return result;
    }

    public List<List<Object>> getStatus(Map<String, Map<Long, String>> invoices, String serviceUrl, String password, String certNumber, int certIndex) {
        logger.info("EVAT: client action getStatus");
        List<List<Object>> result = new ArrayList<>();

        URL url = getClass().getClassLoader().getResource("");
        logger.info("EVAT: url: " + url);

        EVatService service = null;

        try {

            for (Map.Entry<String, Map<Long, String>> entry : invoices.entrySet()) {
                String unp = entry.getKey();
                Map<Long, String> invoicesMap = entry.getValue();

                service = initService(serviceUrl, unp, password, certNumber, certIndex);

                for (Map.Entry<Long, String> invoiceEntry : invoicesMap.entrySet()) {
                    Long evat = invoiceEntry.getKey();
                    String invoiceNumber = invoiceEntry.getValue();
                    AvEStatus status = service.getStatus(invoiceNumber);

                    // Проверка ЭЦП электронного документа
                    boolean verified = status.verify();
                    String resultMessage = verified ? status.getMessage() : status.getLastError().getMessage();
                    String resultStatus = verified ? status.getStatus() : null;
                    logger.info(String.format("EVAT %s: Cтатус %s, сообщение %s", invoiceNumber, resultStatus, resultMessage));
                    result.add(Arrays.asList(evat, resultMessage, resultStatus, invoiceNumber));
                }
            }
        } catch (Exception e) {
            logger.error("Get status error", e);
            throw Throwables.propagate(e);
        } finally {
            disconnect(service);
        }
        return result;
    }

    private EVatService initService(String serviceUrl, String unp, String password, String certNumber, int certIndex) throws Exception {
        logger.info("EVAT: initService started");
        // Регистрация провайдера AvJceProv
        ProviderFactory.addAvUniversalProvider();
        Security.addProvider(new AvTLSProvider());
        Security.addProvider(new AvCertStoreProvider());

        // Создание экземпляра класса доступа к порталу
        EVatService service = new EVatService(serviceUrl, new CustomKeyInteractiveSelector(certNumber, certIndex));
        service.login((unp == null ? "" : ("UNP=" + unp + ";")) + "PASSWORD_KEY=" + password);
        service.connect();
        logger.info("EVAT: initService finished");
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
}