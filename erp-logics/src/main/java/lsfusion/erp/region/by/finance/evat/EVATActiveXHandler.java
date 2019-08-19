package lsfusion.erp.region.by.finance.evat;

import com.google.common.base.Throwables;
import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;
import lsfusion.base.file.RawFileData;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.management.modelmbean.XMLParseException;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class EVATActiveXHandler {

    static Logger logger;
    static Dispatch service = null;

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

    private static final String XSD_FOR_ORIGINAL_TYPE = "MNSATI_original.xsd ";
    private static final String XSD_FOR_FIXED_TYPE = "MNSATI_fixed.xsd ";
    private static final String XSD_FOR_ADDITIONAL_TYPE = "MNSATI_additional.xsd ";

    public List<List<Object>> signAndSend(Map<String, Map<Long, List<Object>>> files, String serviceUrl, String path, String exportPath) {
        logger.info("EVAT: client action signAndSend");
        List<List<Object>> result = new ArrayList<>();

        String xsdPath = path == null ? null : (path + "/xsd");
        String archivePath = exportPath == null ? (path == null ? null : (path + "/archive")) : exportPath;
        File archiveDir = archivePath == null ? null : new File(archivePath);

        for (Map.Entry<String, Map<Long, List<Object>>> filesEntry : files.entrySet()) {
            String unp = filesEntry.getKey();
            logger.info(String.format("EVAT: sending %s xmls, unp %s", filesEntry.getValue().size(), unp));

            try {
                initService(serviceUrl);
                if(service != null) {
                    if (archiveDir == null || archiveDir.exists() || archiveDir.mkdirs()) {
                        for (Map.Entry<Long, List<Object>> entry : filesEntry.getValue().entrySet()) {
                            result.add(sendFile(entry.getValue(), entry.getKey(), service, archiveDir, xsdPath));
                        }
                    } else {
                        result.add(Arrays.asList(0, "Unable to create archive directory", true));
                    }
                } else {
                    result.add(Arrays.asList(0, "Unable to connect or login", true));
                }

            } catch (Exception e) {
                disconnect(service);
                service = null;
                logger.error("Sign and send error", e);
                throw Throwables.propagate(e);
            }

        }
        return result;
    }

    private List<Object> sendFile(List<Object> fileNumberEntry, Long evat, Dispatch service, File archiveDir, String xsdPath) {
        List<Object> result = null;
        RawFileData file = (RawFileData) fileNumberEntry.get(0);
        String number = (String) fileNumberEntry.get(1);
        File originalFile = null;
        try {
            logger.info(String.format("EVAT %s: save file before sending", number));
            originalFile = archiveDir == null ? File.createTempFile("EVAT", ".xml") : new File(archiveDir, "EVAT" + evat + ".xml");
            file.write(originalFile);

            // Создание электронного документа
            Dispatch invVatXml = Dispatch.get(service, "createEDoc").toDispatch();

            // Загрузка электронной счет-фактуры НДС
            Dispatch document = Dispatch.get(invVatXml, "Document").toDispatch();
            if(Dispatch.call(document, "LoadFromFile", originalFile.getAbsolutePath()).getInt() == 0) {
                // Проверка счет-фактуры НДС на соответствие XSD схеме
                //if (validateXML(document, xsdPath)) {

                    if (Dispatch.call(invVatXml, "Sign", 0).getInt() == 0) {
                        if(archiveDir != null)
                            Dispatch.call(invVatXml, "SaveToFile", archiveDir + "/" + "EVAT" + evat + ".sgn.xml");

                        // Загрузка электронного документа на автоматизированный сервис
                        // портала и получение квитанции о приёме
                        Variant res = Dispatch.call(service, "SendEDoc", invVatXml);

                        // Проверка квитанции
                        if (res.getInt() == 0) {
                            logger.info("Ответ сервера проверен. Cчет/фактура принята в обработку. Сообщение сервера: Accepted");
                            result = Arrays.asList(evat, "Accepted", false);
                        } else {
                            logger.info(String.format("EVAT %s: SignAndSend. Ticket is not accepted", number));
                            Variant err = Dispatch.call(service, "LastError");
                            logger.info(err);
                            result = Arrays.asList(evat, err, true);
                        }
                        logger.info(String.format("EVAT %s: send file finished", number));
                    }
                //} else {
                //    result = Arrays.asList((Object) evat, String.format("EVAT %s: Структура документа не отвечает XSD схеме", number), true);
                //}
            }
            if (result == null) {
                Variant err = Dispatch.call(service, "LastError");
                logger.info(err);
                result = Arrays.asList(evat, err, true);
            }

        } catch (Exception e) {
            logger.info(String.format("EVAT %s: Error occurred", number));
            disconnect(service);
            logger.error("Send file error", e);
            return Arrays.asList(evat, e.getMessage(), true);
        } finally {
            if(archiveDir == null && originalFile!= null && !originalFile.delete())
                originalFile.deleteOnExit();
        }
        return result;
    }

    public List<List<Object>> getStatus(Map<String, Map<Long, String>> invoices, String serviceUrl) {
        logger.info("EVAT: client action getStatus");
        List<List<Object>> result = new ArrayList<>();

        URL url = getClass().getClassLoader().getResource("");
        logger.info("EVAT: url: " + url);

        try {

            for (Map.Entry<String, Map<Long, String>> entry : invoices.entrySet()) {
                Map<Long, String> invoicesMap = entry.getValue();

                initService(serviceUrl);
                if(service != null) {

                    for (Map.Entry<Long, String> invoiceEntry : invoicesMap.entrySet()) {
                        Long evat = invoiceEntry.getKey();
                        String invoiceNumber = invoiceEntry.getValue();
                        Dispatch status = Dispatch.call(service, "GetStatus", invoiceNumber).toDispatch();

                        // Проверка ЭЦП электронного документа
                        boolean verified = Dispatch.call(status, "Verify").getInt() == 0;
                        String resultMessage = verified ? Dispatch.call(status, "Message").getString() : Dispatch.call(service, "LastError").getString();
                        String resultStatus = verified ? Dispatch.call(status, "Status").getString() : null;
                        logger.info(String.format("EVAT %s: Статус %s, сообщение %s", invoiceNumber, resultStatus, resultMessage));
                        result.add(Arrays.asList(evat, resultMessage, resultStatus, invoiceNumber));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Get status error", e);
            disconnect(service);
            service = null;
            throw Throwables.propagate(e);
        }
        return result;
    }

    private void initService(String serviceUrl) {
        if (service == null) {
            logger.info("EVAT: initService started");

            ActiveXComponent evatActiveXComponent = new ActiveXComponent("EInvVatService.Connector");
            service = evatActiveXComponent.getObject();

            if (Dispatch.call(service, "Login", ""/*, (unp == null ? "" : ("UNP=" + unp + ";")) + "PASSWORD_KEY=" + password*/, 0/*0x40*/).getInt() == 0) {
                //Авторизация успешна
                if (Dispatch.call(service, "Connect", serviceUrl).getInt() == 0) {
                    logger.info("EVAT: initService finished");
                } else {
                    logger.info("EVAT: connect failed (" + Dispatch.call(service, "LastError") + ")");
                    throw new RuntimeException("EVAT: connect failed (" + Dispatch.call(service, "LastError") + ")");
                }
            } else {
                logger.info("EVAT: login failed (" + Dispatch.call(service, "LastError") + ")");
                throw new RuntimeException("EVAT: login failed (" + Dispatch.call(service, "LastError") + ")");
            }
        }
    }

    private void disconnect(Dispatch service) {
        if (service != null) {
            // Завершение работы с сервисом
            Dispatch.call(service, "Disconnect");
            // Завершение авторизованной сессии
            Dispatch.call(service, "Logout");
        }
    }


    private boolean validateXML(Dispatch document, String xsdPath) throws Exception {
        return xsdPath == null ||
                Dispatch.call(document, "ValidateXML", loadXsdSchema(xsdPath, Dispatch.call(document, "GetXmlNodeValue", "issuance/general/documentType")), 0).getInt() == 0;
    }


    //Загрузка XSD схемы из файла
    private static String loadXsdSchema(String xsdFolderName, Variant doctype) throws Exception {
        // validate XSD schema
        String xsdFile;
        String doctypeValue = (doctype.getString() == null) ? "" : doctype.getString();

        if (doctypeValue.equalsIgnoreCase("ORIGINAL")
                || doctypeValue.equalsIgnoreCase("ADD_NO_REFERENCE")) {
            xsdFile = xsdFolderName + "/" + XSD_FOR_ORIGINAL_TYPE;
        } else if (doctypeValue.equalsIgnoreCase("FIXED")) {
            xsdFile = xsdFolderName + "/" + XSD_FOR_FIXED_TYPE;
        } else if (doctypeValue.equalsIgnoreCase("ADDITIONAL")) {
            xsdFile = xsdFolderName + XSD_FOR_ADDITIONAL_TYPE;
        } else {
            throw new XMLParseException("Ошибка: неизвестный тип счет-фактуры '" + doctype + "'.");
        }
        return xsdFile;
    }

}