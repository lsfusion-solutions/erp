package lsfusion.erp.region.by.integration.edi;

import lsfusion.base.file.RawFileData;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

public class SendEInvoiceCustomerActionProperty extends EDIActionProperty {
    protected final ClassPropertyInterface eInvoiceInterface;

    public SendEInvoiceCustomerActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        eInvoiceInterface = i.next();
    }

    protected void sendEInvoice(ExecutionContext context, String url, String login, String password, String host, Integer port,
                                String hostEDSService, Integer portEDSService,
                                boolean useEDSServiceForCustomer, String outputDir, String provider)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, IOException, JDOMException {
        if (context.getDbManager().isServer()) {

            String signerPathEDI = (String) findProperty("signerPathEDI[]").read(context);
            String outputEDI = (String) findProperty("outputEDI[]").read(context);
            String certificateEDI = (String) findProperty("certificateEDI[]").read(context);
            String passwordEDI = (String) findProperty("passwordEDI[]").read(context);

            if (useEDSServiceForCustomer || (signerPathEDI != null && outputEDI != null && certificateEDI != null && passwordEDI != null)) {

                List<RawFileData> xmls = new ArrayList<>();

                DataObject eInvoiceObject = context.getDataKeyValue(eInvoiceInterface);

                long currentTime = Calendar.getInstance().getTime().getTime();
                String documentNumberBLRAPN = String.valueOf(currentTime);
                String documentNumberBLRWBR = String.valueOf(currentTime + 1);
                String documentDate = new SimpleDateFormat("yyyyMMddHHmmss").format(currentTime);

                String referenceNumber = trim((String) findProperty("blrwbl[EInvoice]").read(context, eInvoiceObject));
                String invoiceNumber = trim((String) findProperty("number[EInvoice]").read(context, eInvoiceObject));
                if (referenceNumber == null)
                    referenceNumber = invoiceNumber;
                Timestamp referenceDateValue = (Timestamp) findProperty("blrwblDate[EInvoice]").read(context, eInvoiceObject);
                if(referenceDateValue == null)
                    referenceDateValue = (Timestamp) findProperty("dateTime[EInvoice]").read(context, eInvoiceObject);
                String referenceDate = new SimpleDateFormat("yyyyMMddHHmmss").format(referenceDateValue);

                String glnCustomer = (String) findProperty("glnCustomer[EInvoice]").read(context, eInvoiceObject);
                String glnCustomerStock = (String) findProperty("glnCustomerStock[EInvoice]").read(context, eInvoiceObject);

                String aliasEDSService = (String) findProperty("aliasEDSServiceCustomer[EInvoice]").read(context, eInvoiceObject);
                String passwordEDSService = (String) findProperty("passwordEDSServiceCustomer[EInvoice]").read(context, eInvoiceObject);

                boolean isCancel = findProperty("isCancel[EInvoice]").read(context, eInvoiceObject) != null;

                if (useEDSServiceForCustomer) {

                    //создаём BLRAPN и BLRWBR, подписываем и отправляем
                    String blrapn = createBLRAPNString(context, eInvoiceObject, documentNumberBLRAPN, documentDate, referenceNumber, referenceDate, glnCustomer, outputDir);
                    String blrwbr = null;
                    if (!isCancel) {
                        blrwbr = createBLRWBRString(context, eInvoiceObject, documentNumberBLRWBR, documentDate, referenceNumber, referenceDate, glnCustomer, glnCustomerStock, outputDir);
                        if(blrwbr == null)
                            return;
                    }
                    String signedBLRAPN = signDocument("BLRAPN", referenceNumber, hostEDSService, portEDSService, blrapn, aliasEDSService, passwordEDSService, charset);
                    String signedBLRWBR = signDocument("BLRWBR", referenceNumber, hostEDSService, portEDSService, blrwbr, aliasEDSService, passwordEDSService, charset);

                    if (signedBLRAPN != null && (signedBLRWBR != null || isCancel)) {
                        try (ExecutionContext.NewSession newContext = context.newSession()) {
                            sendDocument(context, url, login, password, host, port, provider, referenceNumber, generateXML(login, password, referenceNumber,
                                    documentDate, glnCustomer, glnCustomer, glnCustomerStock, new String(Base64.encodeBase64(signedBLRAPN.getBytes())), "BLRAPN"),
                                    eInvoiceObject, isCancel, isCancel, 3);
                            findProperty("blrapn[EInvoice]").change(documentNumberBLRAPN, newContext, eInvoiceObject);

                            if(signedBLRWBR != null) {
                                sendDocument(context, url, login, password, host, port, provider, invoiceNumber, generateXML(login, password, referenceNumber,
                                        documentDate, glnCustomer, glnCustomer, glnCustomerStock, new String(Base64.encodeBase64(signedBLRWBR.getBytes())), "BLRWBL"),
                                        eInvoiceObject, true, isCancel, 3);
                                findProperty("blrwbr[EInvoice]").change(documentNumberBLRWBR, newContext, eInvoiceObject);
                            }

                            newContext.apply();
                        }
                    }

                } else {

                    //создаём BLRAPN и BLRWBR
                    RawFileData blrapn = createBLRAPN(context, eInvoiceObject, documentNumberBLRAPN, documentDate, referenceNumber, referenceDate, glnCustomer);
                    if (blrapn == null)
                        return;
                    xmls.add(blrapn);
                    if (!isCancel) {
                        RawFileData blrwbr = createBLRWBR(context, eInvoiceObject, documentNumberBLRWBR, documentDate, referenceNumber, referenceDate, glnCustomer, glnCustomerStock);
                        if (blrwbr == null)
                            return;
                        xmls.add(blrwbr);
                    }

                    //Подписываем
                    Object signResult = context.requestUserInteraction(new SignEDIClientAction(xmls, signerPathEDI, outputEDI, certificateEDI, passwordEDI));

                    if (signResult instanceof List) {

                        //Отправляем
                        if (sendBLRAPN(context, url, login, password, host, port, provider, ((ArrayList) signResult).get(0), eInvoiceObject,
                                documentNumberBLRAPN, documentDate, invoiceNumber, glnCustomer, glnCustomerStock, isCancel)) {
                            try (ExecutionContext.NewSession newContext = context.newSession()) {
                                findProperty("blrapn[EInvoice]").change(documentNumberBLRAPN, newContext, eInvoiceObject);
                                if (!isCancel && sendBLRWBR(context, url, login, password, host, port, provider, ((ArrayList) signResult).get(1), eInvoiceObject, documentNumberBLRWBR, documentDate, invoiceNumber, glnCustomer, glnCustomerStock))
                                    findProperty("blrwbr[EInvoice]").change(documentNumberBLRWBR, newContext, eInvoiceObject);
                                newContext.apply();
                            }
                        }
                    } else {
                        context.delayUserInteraction(new MessageClientAction((String) signResult, "Ошибка"));
                    }
                }

            } else {
                context.delayUserInteraction(new MessageClientAction("Не указаны имя пользователя / пароль / путь SignerEDI / Директория подписанных файлов EDI", "Ошибка"));
            }

        } else {
            context.delayUserInteraction(new MessageClientAction(provider + " SendEInvoice disabled, change serverComputer() to enable", "Экспорт"));
        }
    }

    private void sendDocument(ExecutionContext context, String url, String login, String password, String host, Integer port, String provider, String invoiceNumber, String documentXML,
                              DataObject eInvoiceObject, boolean showMessages) throws IOException, JDOMException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        HttpResponse httpResponse = sendRequest(host, port, login, password, url, documentXML, null);
        RequestResult requestResult = getRequestResult(httpResponse, getResponseMessage(httpResponse), "SendDocument");
        switch (requestResult) {
            case OK:
                if (showMessages) {
                    ServerLoggers.importLogger.info(String.format("%s SendEInvoice %s request succeeded", provider, invoiceNumber));
                    findProperty("exportedCustomer[EInvoice]").change(true, context, eInvoiceObject);
                    context.delayUserInteraction(new MessageClientAction(String.format("%s Накладная %s выгружена", provider, invoiceNumber), "Экспорт"));
                    context.apply();
                }
                break;
            case AUTHORISATION_ERROR:
                ServerLoggers.importLogger.error(String.format("%s SendEInvoice %s: invalid login-password", provider, invoiceNumber));
                if(showMessages) {
                    context.delayUserInteraction(new MessageClientAction(String.format("%s Накладная %s не выгружена: ошибка авторизации", provider, invoiceNumber), "Экспорт"));
                }
                break;
            case UNKNOWN_ERROR:
                ServerLoggers.importLogger.error(String.format("%s SendEInvoice %s: unknown error", provider, invoiceNumber));
                if(showMessages) {
                    context.delayUserInteraction(new MessageClientAction(String.format("%s Накладная %s не выгружена: неизвестная ошибка", provider, invoiceNumber), "Экспорт"));
                }
        }
    }

    @Override
    public ImMap<CalcProperty, Boolean> aspectChangeExtProps() {
        try {
            return getChangeProps((CalcProperty) findProperty("exportedCustomer[EInvoice]").property);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            return null;
        }
    }

    protected RawFileData createBLRAPN(ExecutionContext context, DataObject eInvoiceObject, String documentNumber, String documentDate, String referenceNumber, String referenceDate, String glnCustomer) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        File tmpFile = null;
        try {

            String error = "";

            String deliveryNoteId = (String) findProperty("deliveryNoteNumber[EInvoice]").read(context, eInvoiceObject);
            Timestamp deliveryNoteDateTime = (Timestamp) findProperty("deliveryNoteDateTime[EInvoice]").read(context, eInvoiceObject);

            String glnSupplier = (String) findProperty("glnSupplier[EInvoice]").read(context, eInvoiceObject);

            Element rootElement = new Element("BLRAPN");
            rootElement.setAttribute("version", "0.1");
            Document doc = new Document(rootElement);
            doc.setRootElement(rootElement);

            Element messageHeaderElement = new Element("MessageHeader");
            addStringElement(messageHeaderElement, "MessageID", documentNumber);
            addStringElement(messageHeaderElement, "MsgDateTime", documentDate);
            addStringElement(messageHeaderElement, "MessageType", "BLRAPN");
            addStringElement(messageHeaderElement, "MsgSenderID", glnCustomer);
            addStringElement(messageHeaderElement, "MsgReceiverID", glnSupplier);
            rootElement.addContent(messageHeaderElement);

            Element acknowledgementElement = new Element("Acknowledgement");
            addStringElement(acknowledgementElement, "DocumentID", documentNumber); //Номер электронного подтверждения или извещения
            addIntegerElement(acknowledgementElement, "FunctionCode", 6); //Статус  подтверждения / извещения: 6  = Подтверждено
            addStringElement(acknowledgementElement, "CreationDateTime", documentDate); //Дата и время создания подтверждения или извещения в формате ГГГГДДММЧЧММСС
            addStringElement(acknowledgementElement, "DeliveryNoteID", deliveryNoteId); //Юридический номер документа, к которому относитсяподтверждение или извещение.
            addStringElement(acknowledgementElement, "DeliveryNoteDate", new SimpleDateFormat("yyyyMMdd").format(deliveryNoteDateTime)); //Юридическая дата документа, к которому относится подтверждение или извещение.

            Element referenceDocumentElement = new Element("ReferenceDocument");
            addStringElement(referenceDocumentElement, "Type", "BLRWBL");
            addStringElement(referenceDocumentElement, "ID", referenceNumber); //Номер документа, к которому относится текущее подтверждение/извещение.
            addStringElement(referenceDocumentElement, "Date", referenceDate); //Дата документа в формате ГГГГММДДЧЧММСС, к которому относится текущее подтверждение/извещение.
            acknowledgementElement.addContent(referenceDocumentElement);

            Element shipperElement = new Element("Shipper"); //грузоотправитель
            addStringElement(shipperElement, "GLN", glnSupplier); //GLN грузоотправителя / поставщика / исполнителя
            acknowledgementElement.addContent(shipperElement);

            Element receiverElement = new Element("Receiver"); //грузополучатель
            addStringElement(receiverElement, "GLN", glnCustomer); //GLN  грузополучателя / покупателя / заказчика
            acknowledgementElement.addContent(receiverElement);

            Element errorOrAcknowledgementElement = new Element("ErrorOrAcknowledgement");
            addStringElement(errorOrAcknowledgementElement, "Code", "2650"); //Код подтверждения/извещения.
            acknowledgementElement.addContent(errorOrAcknowledgementElement);

            rootElement.addContent(acknowledgementElement);

            if (error.isEmpty()) {
                tmpFile = File.createTempFile("invoice", ".xml");
                outputXml(doc, new OutputStreamWriter(new FileOutputStream(tmpFile), charset), charset);
                return new RawFileData(tmpFile);

            } else {
                context.delayUserInterfaction(new MessageClientAction(error, "Не все поля заполнены"));
                return null;
            }
        } finally {
            if (tmpFile != null && !tmpFile.delete())
                tmpFile.deleteOnExit();
        }
    }

    protected String createBLRAPNString(ExecutionContext context, DataObject eInvoiceObject, String documentNumber, String documentDate, String referenceNumber, String referenceDate, String glnCustomer, String outputDir) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String error = "";

        String deliveryNoteId = (String) findProperty("deliveryNoteNumber[EInvoice]").read(context, eInvoiceObject);
        Timestamp deliveryNoteDateTime = (Timestamp) findProperty("deliveryNoteDateTime[EInvoice]").read(context, eInvoiceObject);

        String glnSupplier = (String) findProperty("glnSupplier[EInvoice]").read(context, eInvoiceObject);

        Element rootElement = new Element("BLRAPN");
        rootElement.setAttribute("version", "0.1");
        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        Element messageHeaderElement = new Element("MessageHeader");
        addStringElement(messageHeaderElement, "MessageID", documentNumber);
        addStringElement(messageHeaderElement, "MsgDateTime", documentDate);
        addStringElement(messageHeaderElement, "MessageType", "BLRAPN");
        addStringElement(messageHeaderElement, "MsgSenderID", glnCustomer);
        addStringElement(messageHeaderElement, "MsgReceiverID", glnSupplier);
        rootElement.addContent(messageHeaderElement);

        Element acknowledgementElement = new Element("Acknowledgement");
        addStringElement(acknowledgementElement, "DocumentID", documentNumber); //Номер электронного подтверждения или извещения
        addIntegerElement(acknowledgementElement, "FunctionCode", 6); //Статус  подтверждения / извещения: 6  = Подтверждено
        addStringElement(acknowledgementElement, "CreationDateTime", documentDate); //Дата и время создания подтверждения или извещения в формате ГГГГДДММЧЧММСС
        addStringElement(acknowledgementElement, "DeliveryNoteID", deliveryNoteId); //Юридический номер документа, к которому относитсяподтверждение или извещение.
        addStringElement(acknowledgementElement, "DeliveryNoteDate", new SimpleDateFormat("yyyyMMdd").format(deliveryNoteDateTime)); //Юридическая дата документа, к которому относится подтверждение или извещение.

        Element referenceDocumentElement = new Element("ReferenceDocument");
        addStringElement(referenceDocumentElement, "Type", "BLRWBL");
        addStringElement(referenceDocumentElement, "ID", referenceNumber); //Номер документа, к которому относится текущее подтверждение/извещение.
        addStringElement(referenceDocumentElement, "Date", referenceDate); //Дата документа в формате ГГГГММДДЧЧММСС, к которому относится текущее подтверждение/извещение.
        acknowledgementElement.addContent(referenceDocumentElement);

        Element shipperElement = new Element("Shipper"); //грузоотправитель
        addStringElement(shipperElement, "GLN", glnSupplier); //GLN грузоотправителя / поставщика / исполнителя
        acknowledgementElement.addContent(shipperElement);

        Element receiverElement = new Element("Receiver"); //грузополучатель
        addStringElement(receiverElement, "GLN", glnCustomer); //GLN  грузополучателя / покупателя / заказчика
        acknowledgementElement.addContent(receiverElement);

        Element errorOrAcknowledgementElement = new Element("ErrorOrAcknowledgement");
        addStringElement(errorOrAcknowledgementElement, "Code", "2650"); //Код подтверждения/извещения.
        acknowledgementElement.addContent(errorOrAcknowledgementElement);

        rootElement.addContent(acknowledgementElement);

        if (error.isEmpty()) {
            return outputXMLString(doc, charset, outputDir, "blrapn-", false);
        } else {
            context.delayUserInterfaction(new MessageClientAction(error, "Не все поля заполнены"));
            return null;
        }
    }

    private boolean sendBLRAPN(ExecutionContext context, String url, String login, String password, String host, Integer port,
                            String provider, Object signedDocument, DataObject eInvoiceObject, String documentNumber, String documentDate, String invoiceNumber,
                            String glnCustomer, String glnCustomerStock, boolean showMessages) throws JDOMException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, IOException {
        boolean result = false;
        if(signedDocument instanceof byte[]) {
            sendDocument(context, url, login, password, host, port, provider, invoiceNumber, generateXML(login, password, documentNumber, documentDate, glnCustomer, glnCustomer, glnCustomerStock,
                    new String(Base64.encodeBase64((byte[]) signedDocument)), "BLRAPN"), eInvoiceObject, showMessages);
            result = true;
        } else {
            context.delayUserInteraction(new MessageClientAction(String.format("BLRAPN %s не подписан. Ошибка: %s", invoiceNumber, signedDocument), "Ошибка"));
        }
        return result;
    }

    protected RawFileData createBLRWBR(ExecutionContext context, DataObject eInvoiceObject, String documentNumber, String documentDate, String referenceNumber, String referenceDate, String glnCustomer, String glnCustomerStock) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        File tmpFile = null;
        try {

            String error = "";

            String deliveryNoteId = (String) findProperty("deliveryNoteNumber[EInvoice]").read(context, eInvoiceObject);
            Timestamp deliveryNoteDateTime = (Timestamp) findProperty("deliveryNoteDateTime[EInvoice]").read(context, eInvoiceObject);

            String reportId = (String) findProperty("reportId[EInvoice]").read(context, eInvoiceObject);
            //if (reportId == null)
            //    error += String.format("EOrder %s: Не задан 'Номер акта'\n", documentNumber);
            Date reportDate = (Date) findProperty("reportDate[EInvoice]").read(context, eInvoiceObject);
            //if (reportDate == null)
            //    error += String.format("EOrder %s: Не задана 'Дата составления акта'\n", documentNumber);
            String reportName = trim((String) findProperty("reportName[EInvoice]").read(context, eInvoiceObject));
            //if (reportName == null)
            //    error += String.format("EOrder %s: Не задан 'Вид акта'\n", documentNumber);

            String glnSupplier = (String) findProperty("glnSupplier[EInvoice]").read(context, eInvoiceObject);
            String nameSupplier = (String) findProperty("nameSupplier[EInvoice]").read(context, eInvoiceObject);
            String addressSupplier = (String) findProperty("addressSupplier[EInvoice]").read(context, eInvoiceObject);
            String unpSupplier = (String) findProperty("unpSupplier[EInvoice]").read(context, eInvoiceObject);

            String nameCustomer = (String) findProperty("nameCustomer[EInvoice]").read(context, eInvoiceObject);
            String addressCustomer = (String) findProperty("addressCustomer[EInvoice]").read(context, eInvoiceObject);
            String unpCustomer = (String) findProperty("unpCustomer[EInvoice]").read(context, eInvoiceObject);

            String addressCustomerStock = (String) findProperty("addressCustomerStock[EInvoice]").read(context, eInvoiceObject);
            String contactCustomerStock = (String) findProperty("contactCustomerStock[EInvoice]").read(context, eInvoiceObject);
            if (contactCustomerStock == null)
                error += String.format("EOrder %s: Не задано 'ФИО и должность лица, которое отвечает за получение груза со стороны грузополучателя'\n", documentNumber);

            String sealIDReceiver = (String) findProperty("sealIDReceiver[EInvoice]").read(context, eInvoiceObject);

            Element rootElement = new Element("BLRWBR");
            rootElement.setAttribute("version", "0.1");
            Document doc = new Document(rootElement);
            doc.setRootElement(rootElement);

            Element messageHeaderElement = new Element("MessageHeader");
            addStringElement(messageHeaderElement, "MessageID", documentNumber);
            addStringElement(messageHeaderElement, "MsgDateTime", documentDate);
            addStringElement(messageHeaderElement, "MessageType", "BLRWBR");
            addStringElement(messageHeaderElement, "MsgSenderID", glnCustomer);
            addStringElement(messageHeaderElement, "MsgReceiverID", glnSupplier);
            rootElement.addContent(messageHeaderElement);

            Element deliveryNoteElement = new Element("DeliveryNote");
            addIntegerElement(deliveryNoteElement, "DeliveryNoteType", 700); //ТТН (товарно-транспортная накладная)
            addStringElement(deliveryNoteElement, "DocumentID", documentNumber);
            addStringElement(deliveryNoteElement, "CreationDateTime", documentDate);
            addIntegerElement(deliveryNoteElement, "FunctionCode", 11); //11 = Ответ (подтверждение грузополучателем накладной)
            //addStringElement(deliveryNoteElement, "MsgReceiverID", glnSupplier);

            Element referenceDocumentElement = new Element("ReferenceDocument");
            addStringElement(referenceDocumentElement, "ID", referenceNumber);
            addStringElement(referenceDocumentElement, "Date", referenceDate);
            deliveryNoteElement.addContent(referenceDocumentElement);

            addStringElement(deliveryNoteElement, "DeliveryNoteID", deliveryNoteId);
            addStringElement(deliveryNoteElement, "DeliveryNoteDate", new SimpleDateFormat("yyyyMMdd").format(deliveryNoteDateTime));

            if(reportId != null) {
                Element reportElement = new Element("Report");
                addStringElement(reportElement, "ReportID", reportId);
                if (reportDate != null)
                    addStringElement(reportElement, "ReportDate", new SimpleDateFormat("yyyyMMdd").format(reportDate));
                addStringElement(reportElement, "ReportName", reportName);
                deliveryNoteElement.addContent(reportElement);
            }

            Element shipperElement = new Element("Shipper"); //грузоотправитель
            addStringElement(shipperElement, "GLN", glnSupplier);
            addStringElement(shipperElement, "Name", nameSupplier);
            addStringElement(shipperElement, "Address", addressSupplier);
            addStringElement(shipperElement, "VATRegistrationNumber", unpSupplier);
            deliveryNoteElement.addContent(shipperElement);

            Element receiverElement = new Element("Receiver"); //грузополучатель
            addStringElement(receiverElement, "GLN", glnCustomer);
            addStringElement(receiverElement, "Name", nameCustomer);
            addStringElement(receiverElement, "Address", addressCustomer);
            addStringElement(receiverElement, "VATRegistrationNumber", unpCustomer);
            deliveryNoteElement.addContent(receiverElement);

            Element shipToElement = new Element("ShipTo");
            addStringElement(shipToElement, "GLN", glnCustomerStock); //пункт разгрузки
            addStringElement(shipToElement, "Address", addressCustomerStock);
            addStringElement(shipToElement, "Contact", contactCustomerStock);
            deliveryNoteElement.addContent(shipToElement);

            addStringElement(deliveryNoteElement, "SealIDReceiver", sealIDReceiver);
            rootElement.addContent(deliveryNoteElement);

            if (error.isEmpty()) {
                tmpFile = File.createTempFile("evat", ".xml");
                outputXml(doc, new OutputStreamWriter(new FileOutputStream(tmpFile), charset), charset);
                return new RawFileData(tmpFile);

            } else {
                context.delayUserInterfaction(new MessageClientAction(error, "Не все поля заполнены"));
                return null;
            }
        } finally {
            if (tmpFile != null && !tmpFile.delete())
                tmpFile.deleteOnExit();
        }
    }

    protected String createBLRWBRString(ExecutionContext context, DataObject eInvoiceObject, String documentNumber, String documentDate,
                                        String referenceNumber, String referenceDate, String glnCustomer, String glnCustomerStock, String outputDir)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String error = "";

        String deliveryNoteId = (String) findProperty("deliveryNoteNumber[EInvoice]").read(context, eInvoiceObject);
        Timestamp deliveryNoteDateTime = (Timestamp) findProperty("deliveryNoteDateTime[EInvoice]").read(context, eInvoiceObject);

        String reportId = (String) findProperty("reportId[EInvoice]").read(context, eInvoiceObject);
        //if (reportId == null)
        //    error += String.format("EOrder %s: Не задан 'Номер акта'\n", documentNumber);
        Date reportDate = (Date) findProperty("reportDate[EInvoice]").read(context, eInvoiceObject);
        //if (reportDate == null)
        //    error += String.format("EOrder %s: Не задана 'Дата составления акта'\n", documentNumber);
        String reportName = trim((String) findProperty("reportName[EInvoice]").read(context, eInvoiceObject));
        //if (reportName == null)
        //    error += String.format("EOrder %s: Не задан 'Вид акта'\n", documentNumber);

        String glnSupplier = (String) findProperty("glnSupplier[EInvoice]").read(context, eInvoiceObject);
        String nameSupplier = (String) findProperty("nameSupplier[EInvoice]").read(context, eInvoiceObject);
        String addressSupplier = (String) findProperty("addressSupplier[EInvoice]").read(context, eInvoiceObject);
        String unpSupplier = (String) findProperty("unpSupplier[EInvoice]").read(context, eInvoiceObject);

        String nameCustomer = (String) findProperty("nameCustomer[EInvoice]").read(context, eInvoiceObject);
        String addressCustomer = (String) findProperty("addressCustomer[EInvoice]").read(context, eInvoiceObject);
        String unpCustomer = (String) findProperty("unpCustomer[EInvoice]").read(context, eInvoiceObject);

        String addressCustomerStock = (String) findProperty("addressCustomerStock[EInvoice]").read(context, eInvoiceObject);
        String contactCustomerStock = (String) findProperty("contactCustomerStock[EInvoice]").read(context, eInvoiceObject);
        if (contactCustomerStock == null)
            error += String.format("EOrder %s: Не задано 'ФИО и должность лица, которое отвечает за получение груза со стороны грузополучателя'\n", documentNumber);

        String sealIDReceiver = (String) findProperty("sealIDReceiver[EInvoice]").read(context, eInvoiceObject);

        Element rootElement = new Element("BLRWBR");
        rootElement.setAttribute("version", "0.1");
        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        Element messageHeaderElement = new Element("MessageHeader");
        addStringElement(messageHeaderElement, "MessageID", documentNumber);
        addStringElement(messageHeaderElement, "MsgDateTime", documentDate);
        addStringElement(messageHeaderElement, "MessageType", "BLRWBR");
        addStringElement(messageHeaderElement, "MsgSenderID", glnCustomer);
        addStringElement(messageHeaderElement, "MsgReceiverID", glnSupplier);
        rootElement.addContent(messageHeaderElement);

        Element deliveryNoteElement = new Element("DeliveryNote");
        addIntegerElement(deliveryNoteElement, "DeliveryNoteType", 700); //ТТН (товарно-транспортная накладная)
        addStringElement(deliveryNoteElement, "DocumentID", documentNumber);
        addStringElement(deliveryNoteElement, "CreationDateTime", documentDate);
        addIntegerElement(deliveryNoteElement, "FunctionCode", 11); //11 = Ответ (подтверждение грузополучателем накладной)
        //addStringElement(deliveryNoteElement, "MsgReceiverID", glnSupplier);

        Element referenceDocumentElement = new Element("ReferenceDocument");
        addStringElement(referenceDocumentElement, "ID", referenceNumber);
        addStringElement(referenceDocumentElement, "Date", referenceDate);
        deliveryNoteElement.addContent(referenceDocumentElement);

        addStringElement(deliveryNoteElement, "DeliveryNoteID", deliveryNoteId);
        addStringElement(deliveryNoteElement, "DeliveryNoteDate", new SimpleDateFormat("yyyyMMdd").format(deliveryNoteDateTime));

        if (reportId != null) {
            Element reportElement = new Element("Report");
            addStringElement(reportElement, "ReportID", reportId);
            if (reportDate != null)
                addStringElement(reportElement, "ReportDate", new SimpleDateFormat("yyyyMMdd").format(reportDate));
            addStringElement(reportElement, "ReportName", reportName);
            deliveryNoteElement.addContent(reportElement);
        }

        Element shipperElement = new Element("Shipper"); //грузоотправитель
        addStringElement(shipperElement, "GLN", glnSupplier);
        addStringElement(shipperElement, "Name", nameSupplier);
        addStringElement(shipperElement, "Address", addressSupplier);
        addStringElement(shipperElement, "VATRegistrationNumber", unpSupplier);
        deliveryNoteElement.addContent(shipperElement);

        Element receiverElement = new Element("Receiver"); //грузополучатель
        addStringElement(receiverElement, "GLN", glnCustomer);
        addStringElement(receiverElement, "Name", nameCustomer);
        addStringElement(receiverElement, "Address", addressCustomer);
        addStringElement(receiverElement, "VATRegistrationNumber", unpCustomer);
        deliveryNoteElement.addContent(receiverElement);

        Element shipToElement = new Element("ShipTo");
        addStringElement(shipToElement, "GLN", glnCustomerStock); //пункт разгрузки
        addStringElement(shipToElement, "Address", addressCustomerStock);
        addStringElement(shipToElement, "Contact", contactCustomerStock);
        deliveryNoteElement.addContent(shipToElement);

        addStringElement(deliveryNoteElement, "SealIDReceiver", sealIDReceiver);
        rootElement.addContent(deliveryNoteElement);

        if (error.isEmpty()) {
            return outputXMLString(doc, charset, outputDir, "blrwbr", false);
        } else {
            context.delayUserInterfaction(new MessageClientAction(error, "Не все поля заполнены"));
            return null;
        }
    }

    private boolean sendBLRWBR(ExecutionContext context, String url, String login, String password, String host, Integer port,
                               String provider, Object signedDocument, DataObject eInvoiceObject, String documentNumber, String documentDate, String invoiceNumber,
                               String glnCustomer, String glnCustomerStock) throws JDOMException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, IOException {
        boolean result = false;
        if(signedDocument instanceof byte[]) {
            sendDocument(context, url, login, password, host, port, provider, invoiceNumber, generateXML(login, password, documentNumber, documentDate, glnCustomer, glnCustomer, glnCustomerStock,
                    new String(Base64.encodeBase64((byte[]) signedDocument)), "BLRWBR"), eInvoiceObject, true);
            result = true;
        } else {
            context.delayUserInteraction(new MessageClientAction(String.format("BLRWBR %s не подписан. Ошибка: %s", invoiceNumber, signedDocument), "Ошибка"));
        }
        return result;
    }
}