package lsfusion.erp.region.by.integration.edi;

import lsfusion.base.FileData;
import lsfusion.base.RawFileData;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.DynamicFormatFileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.xmlbeans.impl.util.Base64;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

import static lsfusion.base.BaseUtils.trimToNull;

public class ReceiveMessagesActionProperty extends EDIActionProperty {

    public ReceiveMessagesActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    protected void receiveMessages(ExecutionContext context, String url, String login, String password, String host, int port,
                                   String provider, String archiveDir, boolean disableConfirmation, boolean receiveSupplierMessages,
                                   boolean sendReplies, boolean invoices)
            throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException, IOException {
        receiveMessages(context, url, login, password, host, port, null, null, provider, archiveDir,
                disableConfirmation, receiveSupplierMessages, sendReplies, invoices);
    }

    protected void receiveMessages(ExecutionContext context, String url, String login, String password, String host, int port,
                                   String hostEDSService, Integer portEDSService,
                                   String provider, String archiveDir, boolean disableConfirmation, boolean receiveSupplierMessages,
                                   boolean sendReplies, boolean invoices)
            throws IOException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {

            String xml = getReceiveMessagesRequest(login, password);

            HttpResponse httpResponse = sendRequest(host, port, login, password, url, xml, null);
            ServerLoggers.importLogger.info(provider + " ReceiveMessages request sent");
            String responseMessage = getResponseMessage(httpResponse);
            try {

                DataObject fileObject = new DataObject(new FileData(new RawFileData(responseMessage.getBytes(StandardCharsets.UTF_8)), "xml"), DynamicFormatFileClass.get());
                findAction("importRequestResult[FILE, INTEGER, TEXT]").execute(context, fileObject, new DataObject(httpResponse.getStatusLine().getStatusCode()), new DataObject("GetDocumentsResponse"));
                String error = (String)findProperty("requestError[]").read(context);

                if(error == null) {
                    importMessages(context, url, login, password, host, port, hostEDSService, portEDSService,
                            provider, responseMessage, archiveDir, disableConfirmation, receiveSupplierMessages, sendReplies, invoices);
                } else {
                    ServerLoggers.importLogger.error(provider + " ReceiveMessages: " + error);
                    context.delayUserInteraction(new MessageClientAction(provider + " Сообщения не получены: " + error, "Импорт"));
                }
            } catch (JDOMException e) {
                ServerLoggers.importLogger.error(provider + " ReceiveMessages: invalid response", e);
                context.delayUserInteraction(new MessageClientAction(provider + " Сообщения не получены: некорректный ответ сервера", "Импорт"));
            }
    }

    private String getReceiveMessagesRequest(String login, String password) {
        Element rootElement = new Element("Envelope", soapenvNamespace);
        rootElement.setNamespace(soapenvNamespace);
        rootElement.addNamespaceDeclaration(soapenvNamespace);
        rootElement.addNamespaceDeclaration(topNamespace);

        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        //parent: rootElement
        Element headerElement = new Element("Header", soapenvNamespace);
        rootElement.addContent(headerElement);

        //parent: rootElement
        Element bodyElement = new Element("Body", soapenvNamespace);
        rootElement.addContent(bodyElement);

        //parent: bodyElement
        Element sendDocumentElement = new Element("GetDocuments", topNamespace);
        bodyElement.addContent(sendDocumentElement);

        addStringElement(topNamespace, sendDocumentElement, "username", login);
        addStringElement(topNamespace, sendDocumentElement, "password", password);

        return new XMLOutputter().outputString(doc);
    }

    private void importMessages(ExecutionContext context, String url, String login, String password, String host, Integer port,
                                String hostEDSService, Integer portEDSService,
                                String provider, String responseMessage, String archiveDir, boolean disableConfirmation,
                                boolean receiveSupplierMessages, boolean sendReplies, boolean invoices)
            throws IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, JDOMException {

        Map<String, String> systemMessageMap = new HashMap<>();
        Map<String, String> ordrspMap = new HashMap<>();
        Map<String, String> desadvMap = new HashMap<>();
        Map<String, String> blrwblMap = new HashMap<>();
        Map<String, String> blrwbrMap = new HashMap<>();
        Map<String, String> blrapnMap = new HashMap<>();
        Map<String, String> ordersMap = new HashMap<>();

        Document document = new SAXBuilder().build(new ByteArrayInputStream(responseMessage.getBytes(StandardCharsets.UTF_8)));
        Element rootNode = document.getRootElement();
        Namespace ns = rootNode.getNamespace();
        if (ns != null) {
            Element body = rootNode.getChild("Body", ns);
            if (body != null) {
                Element response = body.getChild("GetDocumentsResponse", topNamespace);
                if (response != null) {
                    Element result = response.getChild("GetDocumentsResult", topNamespace);
                    if (result != null) {
                        String successful = result.getChildText("Succesful", topNamespace);
                        if (Boolean.parseBoolean(successful)) {

                            Element dataElement = result.getChild("Data", topNamespace);
                            for (Object documentDataObject : dataElement.getChildren("DocumentData", topNamespace)) {
                                Element documentData = (Element) documentDataObject;

                                String subXML = new String(Base64.decode(documentData.getChildText("Data", topNamespace).getBytes()));
                                String documentType = documentData.getChildText("DocumentType", topNamespace).toLowerCase();
                                String documentId = documentData.getChildText("Id", topNamespace);

                                switch (documentType) {
                                    case "systemmessage":
                                        systemMessageMap.put(documentId, subXML);
                                        break;
                                    case "ordrsp":
                                        ordrspMap.put(documentId, subXML);
                                        break;
                                    case "desadv":
                                        desadvMap.put(documentId, subXML);
                                        break;
                                    case "blrwbl":
                                        blrwblMap.put(documentId, subXML);
                                        break;
                                    case "blrwbr":
                                        blrwbrMap.put(documentId, subXML);
                                        break;
                                    case "blrapn":
                                        blrapnMap.put(documentId, subXML);
                                        break;
                                    case "orders":
                                        ordersMap.put(documentId, subXML);
                                        break;

                                }

                                archiveDocument(archiveDir, documentId, subXML);
                            }

                            importData(context, url, login, password, host, port, hostEDSService, portEDSService, provider, archiveDir,
                                    systemMessageMap, ordrspMap, desadvMap, blrwblMap, blrwbrMap, blrapnMap, ordersMap,
                                    invoices, receiveSupplierMessages, disableConfirmation, sendReplies);

                        } else {
                            String message = result.getChildText("Message", topNamespace);
                            String errorCode = result.getChildText("ErrorCode", topNamespace);
                            context.delayUserInteraction(new MessageClientAction(String.format("Error %s: %s", errorCode, message), "Ошибка"));
                        }
                    }
                }
            }
        }
    }

    private void importData(ExecutionContext context, String url, String login, String password, String host, Integer port,
                            String hostEDSService, Integer portEDSService, String provider, String archiveDir,
                            Map<String, String> systemMessageMap, Map<String, String> ordrspMap, Map<String, String> desadvMap,
                            Map<String, String> blrwblMap, Map<String, String> blrwbrMap, Map<String, String> blrapnMap, Map<String, String> ordersMap,
                            boolean invoices, boolean receiveSupplierMessages, boolean disableConfirmation, boolean sendReplies)
            throws ScriptingErrorLog.SemanticErrorException, IOException, JDOMException, SQLException, SQLHandledException {
        int sendRecipientErrorFailed = 0;

        //Parse and import systemmessage
        List<InvoiceMessage> invoiceSystemMessageList = new ArrayList<>();
        Map<String, DocumentData> orderMessages = new HashMap<>();
        for(Map.Entry<String, String> systemMessageEntry : systemMessageMap.entrySet()) {
            String documentId = systemMessageEntry.getKey();
            String subXML = systemMessageEntry.getValue();
            try {
                if (invoices) {
                    if (receiveSupplierMessages) {
                        InvoiceMessage invoiceSystemMessage = parseInvoiceSystemMessage(context, subXML, documentId);
                        if (invoiceSystemMessage != null) {
                            invoiceSystemMessageList.add(invoiceSystemMessage);
                        }
                    }
                } else {
                    DocumentData orderMessage = parseOrderMessage(subXML, provider, documentId, receiveSupplierMessages);
                    if (orderMessage != null) {
                        orderMessages.put(documentId, orderMessage);
                    }
                }
            } catch (JDOMException e) {
                ServerLoggers.importLogger.error(String.format("%s Parse Message %s error: ", provider, documentId), e);
            }
        }

        int orderMessagesSucceeded = 0;
        int orderMessagesFailed = 0;
        for (Map.Entry<String, DocumentData> message : orderMessages.entrySet()) {
            String documentId = message.getKey();
            DocumentData data = message.getValue();
            if(data.skip) {
                confirmDocumentReceived(context, documentId, url, login, password, host, port, provider, archiveDir, disableConfirmation);
                orderMessagesFailed++;
            } else if (data.firstData != null) {
                String error = importOrderMessages(context, data);
                if (error == null) {
                    confirmDocumentReceived(context, documentId, url, login, password, host, port, provider, archiveDir, disableConfirmation);
                    ServerLoggers.importLogger.info(String.format("%s Import EOrderMessage %s succeeded", provider, documentId));
                    orderMessagesSucceeded++;
                } else {
                    if (!sendRecipientError(context, url, login, password, host, port, provider, archiveDir, documentId, data.documentNumber, error, disableConfirmation, sendReplies))
                        sendRecipientErrorFailed++;
                    ServerLoggers.importLogger.error(String.format("%s Import EOrderMessage %s failed: %s", provider, documentId, error));
                    orderMessagesFailed++;
                }
            } else {
                if (!sendRecipientError(context, url, login, password, host, port, provider, archiveDir, documentId,
                        data.documentNumber, String.format("%s Parsing EOrderMessage %s failed", provider, documentId), disableConfirmation, sendReplies))
                    sendRecipientErrorFailed++;
                orderMessagesFailed++;
            }
        }

        //Parse and import ordrsp
        int responsesSucceeded = 0;
        int responsesFailed = 0;
        if (!invoices) {
            for (Map.Entry<String, String> ordrspEntry : ordrspMap.entrySet()) {
                String documentId = ordrspEntry.getKey();
                String subXML = ordrspEntry.getValue();
                try {
                    DocumentData data = parseOrderResponse(subXML, context, url, login, password,
                            host, port, provider, archiveDir, documentId, sendReplies, disableConfirmation);

                    String error = importOrderResponses(context, data);
                    if (error == null) {
                        confirmDocumentReceived(context, documentId, url, login, password, host, port, provider, archiveDir, disableConfirmation);
                        ServerLoggers.importLogger.info(String.format("%s Import EOrderResponse %s succeeded", provider, documentId));
                        responsesSucceeded++;
                    } else {
                        if (!sendRecipientError(context, url, login, password, host, port, provider, archiveDir, documentId, data.documentNumber, error, disableConfirmation, sendReplies))
                            sendRecipientErrorFailed++;
                        ServerLoggers.importLogger.error(String.format("%s Import EOrderResponse %s failed: %s", provider, documentId, error));
                        responsesFailed++;
                    }

                } catch (JDOMException e) {
                    ServerLoggers.importLogger.error(String.format("%s Parse Message %s error: ", provider, documentId), e);
                }
            }
        }

        //Parse and import desadv
        int despatchAdvicesSucceeded = 0;
        int despatchAdvicesFailed = 0;
        if(!invoices) {
            for(Map.Entry<String, String> desadvEntry : desadvMap.entrySet()) {
                String documentId = desadvEntry.getKey();
                String subXML = desadvEntry.getValue();
                try {
                    DocumentData data = parseDespatchAdvice(subXML, context, url, login, password,
                            host, port, provider, archiveDir, documentId, sendReplies, disableConfirmation);

                    String error = importDespatchAdvices(context, data);
                    if (error == null) {
                        confirmDocumentReceived(context, documentId, url, login, password, host, port, provider, archiveDir, disableConfirmation);
                        ServerLoggers.importLogger.info(String.format("%s Import EOrderDespatchAdvice %s succeeded", provider, documentId));
                        despatchAdvicesSucceeded++;
                    } else {
                        if (!sendRecipientError(context, url, login, password, host, port, provider, archiveDir, documentId, data.documentNumber, error, disableConfirmation, sendReplies))
                            sendRecipientErrorFailed++;
                        ServerLoggers.importLogger.error(String.format("%s Import EOrderDespatchAdvice %s failed: %s", provider, documentId, error));
                        despatchAdvicesFailed++;
                    }

                } catch (JDOMException e) {
                    ServerLoggers.importLogger.error(String.format("%s Parse Message %s error: ", provider, documentId), e);
                }
            }
        }

        //Parse and import blrwbl
        int eInvoicesSucceeded = 0;
        int eInvoicesFailed = 0;
        if(invoices) {
            for (Map.Entry<String, String> blrwblEntry : blrwblMap.entrySet()) {
                String documentId = blrwblEntry.getKey();
                String subXML = blrwblEntry.getValue();
                try {
                    BLRWBL blrwbl = parseBLRWBL(subXML, documentId);

                    String error = importBLRWBL(context, blrwbl);
                    if (error == null) {
                        ServerLoggers.importLogger.info(String.format("%s Import EInvoice %s succeeded", provider, blrwbl.documentId));
                        confirmDocumentReceived(context, blrwbl.documentId, url, login, password, host, port, provider, archiveDir, disableConfirmation);
                        eInvoicesSucceeded++;
                    } else {
                        ServerLoggers.importLogger.error(String.format("%s Import EInvoice %s failed: %s", provider, blrwbl.documentId, error));
                        if(!sendRecipientError(context, url, login, password, host, port, provider, archiveDir, blrwbl.documentId, blrwbl.documentNumber, error, disableConfirmation, sendReplies))
                            sendRecipientErrorFailed++;
                        eInvoicesFailed++;
                    }

                } catch (JDOMException e) {
                    ServerLoggers.importLogger.error(String.format("%s Parse Message %s error: ", provider, documentId), e);
                }
            }
        }

        //Parse and import blrapn
        int invoiceMessagesSucceeded = 0;
        int invoiceMessagesFailed = 0;
        if(invoices) {
            for (Map.Entry<String, String> blrapnEntry : blrapnMap.entrySet()) {
                String documentId = blrapnEntry.getKey();
                String subXML = blrapnEntry.getValue();
                try {

                    DocumentData data = parseInvoiceMessage(context, subXML, provider, documentId, receiveSupplierMessages);
                    if (data.firstData != null) {
                        String error = importInvoiceMessages(context, data.firstData);
                        if (error == null) {
                            confirmDocumentReceived(context, documentId, url, login, password, host, port, provider, archiveDir, disableConfirmation);
                            ServerLoggers.importLogger.info(String.format("%s Import EInvoiceMessage %s succeeded", provider, documentId));
                            invoiceMessagesSucceeded++;
                        } else {
                            if (!sendRecipientError(context, url, login, password, host, port, provider, archiveDir, documentId, data.documentNumber, error, disableConfirmation, sendReplies))
                                sendRecipientErrorFailed++;
                            ServerLoggers.importLogger.error(String.format("%s Import EInvoiceMessage %s failed: %s", provider, documentId, error));
                            invoiceMessagesFailed++;
                        }
                    } else {
                        if (!sendRecipientError(context, url, login, password, host, port, provider, archiveDir, documentId, data.documentNumber,
                                String.format("%s Parsing EInvoiceMessage %s failed", provider, documentId), disableConfirmation, sendReplies))
                            sendRecipientErrorFailed++;
                        invoiceMessagesFailed++;
                    }

                } catch (JDOMException e) {
                    ServerLoggers.importLogger.error(String.format("%s Parse Message %s error: ", provider, documentId), e);
                }
            }
        }

        for (InvoiceMessage message : invoiceSystemMessageList) {
            String error = importInvoiceSystemMessage(context, message);
            if (error == null) {
                ServerLoggers.importLogger.info(String.format("%s Import EInvoiceMessage %s succeeded", provider, message.documentId));
                confirmDocumentReceived(context, message.documentId, url, login, password, host, port, provider, archiveDir, disableConfirmation);
                invoiceMessagesSucceeded++;
            } else {
                ServerLoggers.importLogger.error(String.format("%s Import EInvoiceMessage %s failed: %s", provider, message.documentId, error));
                if(!sendRecipientError(context, url, login, password, host, port, provider, archiveDir, message.documentId, message.documentNumber, error, disableConfirmation, sendReplies))
                    sendRecipientErrorFailed++;
                invoiceMessagesFailed++;
            }
        }

        //Parse blrwbr, create, sign and send blrapn
        int blrwbrCount = 0;
        if(invoices && receiveSupplierMessages && !disableConfirmation) {
            for (Map.Entry<String, String> blrwbrEntry : blrwbrMap.entrySet()) {
                String documentId = blrwbrEntry.getKey();
                String subXML = blrwbrEntry.getValue();
                try {
                    BLRWBR blrwbr = parseBLRWBR(subXML, documentId);

                    //создаём BLRAPN и подписываем
                    ObjectValue eInvoiceObject = findProperty("eInvoiceDeliveryNoteNumber[VARSTRING[28]]").readClasses(context, new DataObject(blrwbr.deliveryNoteNumber));
                    if (eInvoiceObject instanceof DataObject) {
                        String aliasEDSService = (String) findProperty("aliasEDSServiceSupplier[EInvoice]").read(context, eInvoiceObject);
                        String passwordEDSService = (String) findProperty("passwordEDSServiceSupplier[EInvoice]").read(context, eInvoiceObject);
                        String invoiceNumber = trim((String) findProperty("number[EInvoice]").read(context, eInvoiceObject));
                        String glnSupplier = (String) findProperty("glnSupplier[EInvoice]").read(context, eInvoiceObject);
                        String glnCustomer = (String) findProperty("glnCustomer[EInvoice]").read(context, eInvoiceObject);
                        String glnCustomerStock = (String) findProperty("glnCustomerStock[EInvoice]").read(context, eInvoiceObject);
                        boolean isCancel = findProperty("isCancel[EInvoice]").read(context, eInvoiceObject) != null;
                        String blrapn = createBLRAPN(context, (DataObject) eInvoiceObject, archiveDir, blrwbr.documentNumberBLRAPN, blrwbr.documentDate, blrwbr.documentId,
                                blrwbr.creationDateTime, glnSupplier, glnCustomer);
                        String signedBLRAPN = signDocument("BLRAPN", invoiceNumber, hostEDSService, portEDSService, blrapn, aliasEDSService, passwordEDSService, charset);
                        //Отправляем
                        if (signedBLRAPN != null) {
                            if (sendDocument(context, url, login, password, host, port, provider, invoiceNumber, generateXML(login, password, invoiceNumber,
                                    blrwbr.documentDate, glnSupplier, glnCustomer, glnCustomerStock,
                                    new String(org.apache.commons.codec.binary.Base64.encodeBase64(signedBLRAPN.getBytes())), "BLRAPN"),
                                    (DataObject) eInvoiceObject, true, isCancel, 4)) {
                                confirmDocumentReceived(context, blrwbr.docId, url, login, password, host, port, provider, archiveDir, disableConfirmation);
                                blrwbrCount++;
                            }
                        }
                    }

                } catch (JDOMException e) {
                    ServerLoggers.importLogger.error(String.format("%s Parse Message %s error: ", provider, documentId), e);
                }
            }
        }

        String message = "";


        //Import orders
        if(!invoices && receiveSupplierMessages) {
            int supplierOrdersSucceeded = 0;
            int supplierOrdersFailed = 0;
            for (Map.Entry<String, String> blrapnEntry : ordersMap.entrySet()) {
                String documentId = blrapnEntry.getKey();
                String subXML = blrapnEntry.getValue();
                String error;
                String documentNumber = null;
                try {

                    findAction("importSaleUserOrderEDI[FILE]").execute(context, new DataObject(new FileData(new RawFileData(subXML.getBytes(StandardCharsets.UTF_8)), "xml"), DynamicFormatFileClass.get()));
                    error = trimToNull((String) findProperty("importSaleUserOrderEDIError[]").read(context));

                    if(error == null) {
                        confirmDocumentReceived(context, documentId, url, login, password, host, port, provider, archiveDir, disableConfirmation);
                        supplierOrdersSucceeded++;
                        ServerLoggers.importLogger.info(String.format("%s Import Order %s succeeded", provider, documentId));
                    } else {
                        documentNumber = (String) findProperty("documentNumber[]").read(context);
                        ServerLoggers.importLogger.error(String.format("%s Import Order %s failed: %s", provider, documentId, error));
                    }
                } catch (Exception e) {
                    error = e.getMessage();
                    ServerLoggers.importLogger.error(String.format("%s Parse Order %s error: ", provider, documentId), e);
                }
                if(error != null) {
                    if (documentNumber != null && !sendRecipientError(context, url, login, password, host, port, provider, archiveDir, documentId, documentNumber, error, disableConfirmation, sendReplies))
                        sendRecipientErrorFailed++;
                    supplierOrdersFailed++;
                }
            }

            if (supplierOrdersSucceeded > 0)
                message += (message.isEmpty() ? "" : "\n") + String.format("Загружено заказов поставщика: %s", supplierOrdersSucceeded);
            if (supplierOrdersFailed > 0)
                message += (message.isEmpty() ? "" : "\n") + String.format("Не загружено заказов поставщика: %s", supplierOrdersFailed);
        }

        if (orderMessagesSucceeded > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Загружено сообщений по заказам: %s", orderMessagesSucceeded);
        if (orderMessagesFailed > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Не загружено сообщений по заказам: %s", orderMessagesFailed);

        if (responsesSucceeded > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Загружено ответов по заказам: %s", responsesSucceeded);
        if (responsesFailed > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Не загружено ответов по заказам: %s", responsesFailed);

        if (despatchAdvicesSucceeded > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Загружено уведомлений об отгрузке: %s", despatchAdvicesSucceeded);
        if (despatchAdvicesFailed > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Не загружено уведомлений об отгрузке: %s", despatchAdvicesFailed);

        if (eInvoicesSucceeded > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Загружено электронных накладных: %s", eInvoicesSucceeded);
        if (eInvoicesFailed > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Не загружено электронных накладных: %s", eInvoicesFailed);

        if (invoiceMessagesSucceeded > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Загружено сообщений по накладным: %s", invoiceMessagesSucceeded);
        if (invoiceMessagesFailed > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Не загружено сообщений по накладным: %s", invoiceMessagesFailed);

        if(blrwbrCount > 0) {
            message += (message.isEmpty() ? "" : "\n") + String.format("Отвечено на %s BLRWBR", blrwbrCount);
        }

        if (sendRecipientErrorFailed == 0)
            context.delayUserInteraction(new MessageClientAction(message.isEmpty() ? "Не найдено новых сообщений" : message, "Импорт"));
    }

    private DocumentData parseOrderMessage(String subXML, String provider, String documentId, boolean receiveSupplierMessages) throws IOException, JDOMException {
        Element rootNode = new SAXBuilder().build(new ByteArrayInputStream(subXML.getBytes(StandardCharsets.UTF_8))).getRootElement();
        String documentNumber = trim(rootNode.getChildText("documentNumber"));
        Timestamp dateTime = parseTimestamp(rootNode.getChildText("documentDate"));

        Element reference = rootNode.getChild("reference");
        if (reference != null) {
            String documentType = reference.getChildText("documentType");
            if (documentType != null) {
                switch (documentType) {
                    case "ORDERS":
                        String orderNumber = trim(reference.getChildText("documentNumber"));
                        String code = reference.getChildText("code");
                        String description = getDescriptionByCode(reference.getChildText("description"), code);
                        return new DocumentData(documentNumber, Collections.singletonList(Arrays.asList((Object) documentNumber, dateTime, code, description, orderNumber)), null);
                    case "BLRWBR":
                    case "BLRAPN":
                        ServerLoggers.importLogger.error(String.format("%s Parse Order Message %s skipped for documentType %s", provider, documentId, documentType));
                        return new DocumentData(documentNumber, null, null, true);
                    case "BLRWBL":
                    case "SYSTEMMESSAGE":
                        if(receiveSupplierMessages)
                            return null;
                        else {
                            ServerLoggers.importLogger.error(String.format("%s Parse Order Message %s error: incorrect documentType %s", provider, documentId, documentType));
                            break;
                        }
                    default:
                        ServerLoggers.importLogger.error(String.format("%s Parse Order Message %s error: incorrect documentType %s", provider, documentId, documentType));
                        break;
                }
            } else
                ServerLoggers.importLogger.error(String.format("%s Parse Order Message %s error: no documentType tag", provider, documentId));
        } else
            ServerLoggers.importLogger.error(String.format("%s Parse Order Message %s error: no reference tag", provider, documentId));
        return new DocumentData(documentNumber, null, null);
    }

    private String importOrderMessages(ExecutionContext context, DocumentData data) throws ScriptingErrorLog.SemanticErrorException {
        String message = null;
        List<List<Object>> importData = data == null ? null : data.firstData;
        if (importData != null && !importData.isEmpty()) {
            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField numberEOrderMessage = new ImportField(findProperty("number[EOrderMessage]"));
            ImportKey<?> eOrderMessageKey = new ImportKey((CustomClass) findClass("EOrderMessage"),
                    findProperty("eOrderMessage[VARSTRING[24]]").getMapping(numberEOrderMessage));
            keys.add(eOrderMessageKey);
            props.add(new ImportProperty(numberEOrderMessage, findProperty("number[EOrderMessage]").getMapping(eOrderMessageKey)));
            fields.add(numberEOrderMessage);

            ImportField dateTimeEOrderMessage = new ImportField(findProperty("dateTime[EOrderMessage]"));
            props.add(new ImportProperty(dateTimeEOrderMessage, findProperty("dateTime[EOrderMessage]").getMapping(eOrderMessageKey)));
            fields.add(dateTimeEOrderMessage);

            ImportField codeEOrderMessage = new ImportField(findProperty("code[EOrderMessage]"));
            props.add(new ImportProperty(codeEOrderMessage, findProperty("code[EOrderMessage]").getMapping(eOrderMessageKey)));
            fields.add(codeEOrderMessage);

            ImportField descriptionEOrderMessage = new ImportField(findProperty("description[EOrderMessage]"));
            props.add(new ImportProperty(descriptionEOrderMessage, findProperty("description[EOrderMessage]").getMapping(eOrderMessageKey)));
            fields.add(descriptionEOrderMessage);

            ImportField numberEOrderField = new ImportField(findProperty("number[EOrder]"));
            ImportKey<?> eOrderKey = new ImportKey((CustomClass) findClass("EOrder"),
                    findProperty("eOrder[VARSTRING[28]]").getMapping(numberEOrderField));
            eOrderKey.skipKey = true;
            keys.add(eOrderKey);
            props.add(new ImportProperty(numberEOrderField, findProperty("eOrder[EOrderMessage]").getMapping(eOrderMessageKey),
                    object(findClass("EOrder")).getMapping(eOrderKey)));
            fields.add(numberEOrderField);

            ImportTable table = new ImportTable(fields, importData);

            try (ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                message = newContext.applyMessage();
            } catch (Exception e) {
                ServerLoggers.importLogger.error("ImportOrderMessages Error: ", e);
                message = e.getMessage();
            }
        }
        return message;
    }

    private DocumentData parseOrderResponse(String subXML, ExecutionContext context, String url, String login, String password, String host,
                                            Integer port, String provider, String archiveDir, String documentId, boolean sendReplies, boolean disableConfirmation)
            throws IOException, JDOMException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        Element rootNode = new SAXBuilder().build(new ByteArrayInputStream(subXML.getBytes(StandardCharsets.UTF_8))).getRootElement();
        List<List<Object>> firstData = new ArrayList<>();
        List<List<Object>> secondData = new ArrayList<>();

        String documentNumber = trim(rootNode.getChildText("documentNumber"));
        Timestamp dateTime = parseTimestamp(rootNode.getChildText("documentDate"));
        String responseType = rootNode.getChildText("function");
        String responseTypeObject = getResponseType(responseType);
        String buyerGLN = rootNode.getChildText("buyerGLN");
        String destinationGLN = rootNode.getChildText("destinationGLN");
        String supplierGLN = rootNode.getChildText("supplierGLN");
        String orderNumber = trim(rootNode.getChildText("orderNumber"));
        Timestamp deliveryDateTimeFirst = parseTimestamp(rootNode.getChildText("deliveryDateTimeFirst"));
        Timestamp deliveryDateTimeSecond = parseTimestamp(rootNode.getChildText("deliveryDateTimeSecond"));
        String note = rootNode.getChildText("comment");

        Map<String, String> orderBarcodesMap = getOrderBarcodesMap(context, url, login, password, host, port, provider,
                archiveDir, documentId, documentNumber, orderNumber, sendReplies, disableConfirmation);

        for (Object line : rootNode.getChildren("line")) {
            Element lineElement = (Element) line;
            String dataGTIN = trim(lineElement.getChildText("GTIN"));
            String GTIN;
            String barcode;
            if (orderBarcodesMap.containsKey(dataGTIN)) {
                barcode = orderBarcodesMap.get(dataGTIN);
                GTIN = null;
            } else {
                barcode = null;
                GTIN = dataGTIN;
            }
            String id = supplierGLN + "/" + documentNumber + "/" + orderNumber;
            String idDetail = id + "/" + dataGTIN;
            String action = lineElement.getChildText("action");
            String actionObject = getAction(action);
            BigDecimal quantityOrdered = parseBigDecimal(lineElement.getChildText("quantityOrdered"));
            BigDecimal quantityAccepted = parseBigDecimal(lineElement.getChildText("quantityAccepted"));
            BigDecimal price = parseBigDecimal(lineElement.getChildText("priceElement"));
            BigDecimal sumNoNDS = parseBigDecimal(lineElement.getChildText("priceNoNDS"));
            BigDecimal sumNDS = parseBigDecimal(lineElement.getChildText("priceNDS"));

            if (barcode != null)
                firstData.add(Arrays.<Object>asList(id, documentNumber, dateTime, responseTypeObject, note, supplierGLN, buyerGLN, destinationGLN, orderNumber,
                        deliveryDateTimeFirst, deliveryDateTimeSecond, idDetail, barcode, dataGTIN, actionObject, quantityOrdered, quantityAccepted, price,
                        sumNoNDS, sumNDS));
            else
                secondData.add(Arrays.<Object>asList(id, documentNumber, dateTime, responseTypeObject, note, supplierGLN, buyerGLN, destinationGLN, orderNumber,
                        deliveryDateTimeFirst, deliveryDateTimeSecond, idDetail, GTIN, dataGTIN, actionObject, quantityOrdered, quantityAccepted, price,
                        sumNoNDS, sumNDS));
        }
        return new DocumentData(documentNumber, firstData, secondData);
    }

    private String getResponseType(String id) {
        String value = id == null ? null : id.equals("4") ? "changed" : id.equals("27") ? "cancelled" : id.equals("29") ? "accepted" : null;
        return value == null ? null : ("EDI_EOrderResponseType." + value.toLowerCase());
    }

    private String getAction(String id) {
        String value = id == null ? null : id.equals("1") ? "added" : id.equals("3") ? "changed" : id.equals("5") ? "accepted" : id.equals("7") ? "cancelled" : null;
        return value == null ? null : ("EDI_EOrderResponseDetailAction." + value.toLowerCase());
    }

    private String importOrderResponses(ExecutionContext context, DocumentData data) throws ScriptingErrorLog.SemanticErrorException {
        String message = importOrderResponses(context, data, true);
        return message == null ? importOrderResponses(context, data, false) : message;
    }

    private String importOrderResponses(ExecutionContext context, DocumentData data, boolean first) throws ScriptingErrorLog.SemanticErrorException {
        String message = null;
        List<List<Object>> importData = data == null ? null : (first ? data.firstData : data.secondData);
        if (importData != null && !importData.isEmpty()) {
            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField idEOrderResponseField = new ImportField(findProperty("id[EOrderResponse]"));
            ImportKey<?> eOrderResponseKey = new ImportKey((CustomClass) findClass("EOrderResponse"),
                    findProperty("eOrderResponse[VARSTRING[100]]").getMapping(idEOrderResponseField));
            keys.add(eOrderResponseKey);
            props.add(new ImportProperty(idEOrderResponseField, findProperty("id[EOrderResponse]").getMapping(eOrderResponseKey)));
            fields.add(idEOrderResponseField);

            ImportField numberEOrderResponseField = new ImportField(findProperty("number[EOrderResponse]"));
            props.add(new ImportProperty(numberEOrderResponseField, findProperty("number[EOrderResponse]").getMapping(eOrderResponseKey)));
            fields.add(numberEOrderResponseField);

            ImportField dateTimeEOrderResponseField = new ImportField(findProperty("dateTime[EOrderResponse]"));
            props.add(new ImportProperty(dateTimeEOrderResponseField, findProperty("dateTime[EOrderResponse]").getMapping(eOrderResponseKey)));
            fields.add(dateTimeEOrderResponseField);

            ImportField responseTypeField = new ImportField(findProperty("staticName[Object]"));
            ImportKey<?> responseTypeKey = new ImportKey((CustomClass) findClass("EOrderResponseType"),
                    findProperty("nameStatic[STRING[250]]").getMapping(responseTypeField));
            keys.add(responseTypeKey);
            props.add(new ImportProperty(responseTypeField, findProperty("responseType[EOrderResponse]").getMapping(eOrderResponseKey),
                    object(findClass("EOrderResponseType")).getMapping(responseTypeKey)));
            fields.add(responseTypeField);

            ImportField noteEOrderResponseField = new ImportField(findProperty("note[EOrderResponse]"));
            props.add(new ImportProperty(noteEOrderResponseField, findProperty("note[EOrderResponse]").getMapping(eOrderResponseKey)));
            fields.add(noteEOrderResponseField);

            ImportField GLNSupplierEOrderResponseField = new ImportField(findProperty("GLN[LegalEntity]"));
            ImportKey<?> supplierKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                    findProperty("legalEntityStockGLN[VARSTRING[13]]").getMapping(GLNSupplierEOrderResponseField));
            supplierKey.skipKey = true;
            keys.add(supplierKey);
            props.add(new ImportProperty(GLNSupplierEOrderResponseField, findProperty("supplier[EOrderResponse]").getMapping(eOrderResponseKey),
                    object(findClass("LegalEntity")).getMapping(supplierKey)));
            fields.add(GLNSupplierEOrderResponseField);

            ImportField GLNCustomerEOrderResponseField = new ImportField(findProperty("GLN[LegalEntity]"));
            ImportKey<?> customerKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                    findProperty("legalEntityGLN[VARSTRING[13]]").getMapping(GLNCustomerEOrderResponseField));
            customerKey.skipKey = true;
            keys.add(customerKey);
            props.add(new ImportProperty(GLNCustomerEOrderResponseField, findProperty("customer[EOrderResponse]").getMapping(eOrderResponseKey),
                    object(findClass("LegalEntity")).getMapping(customerKey)));
            fields.add(GLNCustomerEOrderResponseField);

            ImportField GLNCustomerStockEOrderResponseField = new ImportField(findProperty("GLN[Stock]"));
            ImportKey<?> customerStockKey = new ImportKey((CustomClass) findClass("Stock"),
                    findProperty("companyStockGLN[VARSTRING[13]]").getMapping(GLNCustomerStockEOrderResponseField));
            customerStockKey.skipKey = true;
            keys.add(customerStockKey);
            props.add(new ImportProperty(GLNCustomerStockEOrderResponseField, findProperty("customerStock[EOrderResponse]").getMapping(eOrderResponseKey),
                    object(findClass("Stock")).getMapping(customerStockKey)));
            fields.add(GLNCustomerStockEOrderResponseField);

            ImportField numberEOrderField = new ImportField(findProperty("number[EOrder]"));
            ImportKey<?> eOrderKey = new ImportKey((CustomClass) findClass("EOrder"),
                    findProperty("eOrder[VARSTRING[28]]").getMapping(numberEOrderField));
            eOrderKey.skipKey = true;
            keys.add(eOrderKey);
            props.add(new ImportProperty(numberEOrderField, findProperty("eOrder[EOrderResponse]").getMapping(eOrderResponseKey),
                    object(findClass("EOrder")).getMapping(eOrderKey)));
            fields.add(numberEOrderField);

            ImportField deliveryDateTimeEOrderResponseField = new ImportField(findProperty("deliveryDateTime[EOrderResponse]"));
            props.add(new ImportProperty(deliveryDateTimeEOrderResponseField, findProperty("deliveryDateTime[EOrderResponse]").getMapping(eOrderResponseKey)));
            fields.add(deliveryDateTimeEOrderResponseField);

            ImportField deliveryDateTimeSecondEOrderResponseField = new ImportField(findProperty("deliveryDateTimeSecond[EOrderResponse]"));
            props.add(new ImportProperty(deliveryDateTimeSecondEOrderResponseField, findProperty("deliveryDateTimeSecond[EOrderResponse]").getMapping(eOrderResponseKey)));
            fields.add(deliveryDateTimeSecondEOrderResponseField);

            ImportField idEOrderResponseDetailField = new ImportField(findProperty("id[EOrderResponseDetail]"));
            ImportKey<?> eOrderResponseDetailKey = new ImportKey((CustomClass) findClass("EOrderResponseDetail"),
                    findProperty("eOrderResponseDetail[VARSTRING[100]]").getMapping(idEOrderResponseDetailField));
            keys.add(eOrderResponseDetailKey);
            props.add(new ImportProperty(numberEOrderField, findProperty("orderResponse[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey),
                    object(findClass("EOrderResponse")).getMapping(eOrderResponseKey)));
            props.add(new ImportProperty(idEOrderResponseDetailField, findProperty("id[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(idEOrderResponseDetailField);

            if (first) {
                ImportField barcodeEOrderResponseDetailField = new ImportField(findProperty("id[Barcode]"));
                ImportKey<?> skuBarcodeKey = new ImportKey((CustomClass) findClass("Sku"),
                        findProperty("skuBarcode[VARSTRING[15]]").getMapping(barcodeEOrderResponseDetailField));
                skuBarcodeKey.skipKey = true;
                keys.add(skuBarcodeKey);
                props.add(new ImportProperty(barcodeEOrderResponseDetailField, findProperty("sku[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey),
                        object(findClass("Sku")).getMapping(skuBarcodeKey), true));
                fields.add(barcodeEOrderResponseDetailField);
            } else {
                ImportField GTINEOrderResponseDetailField = new ImportField(findProperty("id[Barcode]"));
                ImportKey<?> skuGTINKey = new ImportKey((CustomClass) findClass("Sku"),
                        findProperty("skuGTIN[VARSTRING[15]]").getMapping(GTINEOrderResponseDetailField));
                skuGTINKey.skipKey = true;
                keys.add(skuGTINKey);
                props.add(new ImportProperty(GTINEOrderResponseDetailField, findProperty("sku[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey),
                        object(findClass("Sku")).getMapping(skuGTINKey), true));
                fields.add(GTINEOrderResponseDetailField);
            }

            ImportField dataGTINEOrderResponseDetailField = new ImportField(findProperty("dataGTIN[EOrderResponseDetail]"));
            props.add(new ImportProperty(dataGTINEOrderResponseDetailField, findProperty("dataGTIN[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(dataGTINEOrderResponseDetailField);

            ImportField actionField = new ImportField(findProperty("staticName[Object]"));
            ImportKey<?> actionKey = new ImportKey((CustomClass) findClass("EOrderResponseDetailAction"),
                    findProperty("nameStatic[STRING[250]]").getMapping(actionField));
            keys.add(actionKey);
            props.add(new ImportProperty(actionField, findProperty("action[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey),
                    object(findClass("EOrderResponseDetailAction")).getMapping(actionKey)));
            fields.add(actionField);

            ImportField quantityOrderedEOrderResponseDetailField = new ImportField(findProperty("quantityOrdered[EOrderResponseDetail]"));
            props.add(new ImportProperty(quantityOrderedEOrderResponseDetailField, findProperty("quantityOrdered[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(quantityOrderedEOrderResponseDetailField);

            ImportField quantityAcceptedEOrderResponseDetailField = new ImportField(findProperty("quantityAccepted[EOrderResponseDetail]"));
            props.add(new ImportProperty(quantityAcceptedEOrderResponseDetailField, findProperty("quantityAccepted[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(quantityAcceptedEOrderResponseDetailField);

            ImportField priceEOrderResponseDetailField = new ImportField(findProperty("price[EOrderResponseDetail]"));
            props.add(new ImportProperty(priceEOrderResponseDetailField, findProperty("price[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(priceEOrderResponseDetailField);

            ImportField sumNoNDSEOrderResponseDetailField = new ImportField(findProperty("sumNoNDS[EOrderResponseDetail]"));
            props.add(new ImportProperty(sumNoNDSEOrderResponseDetailField, findProperty("sumNoNDS[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(sumNoNDSEOrderResponseDetailField);

            ImportField sumNDSEOrderResponseDetailField = new ImportField(findProperty("sumNDS[EOrderResponseDetail]"));
            props.add(new ImportProperty(sumNDSEOrderResponseDetailField, findProperty("sumNDS[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(sumNDSEOrderResponseDetailField);

            ImportTable table = new ImportTable(fields, importData);

            try (ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                message = newContext.applyMessage();
            } catch (Exception e) {
                ServerLoggers.importLogger.error("ImportOrderResponses Error: ", e);
                message = e.getMessage();
            }
        }
        return message;
    }

    private DocumentData parseDespatchAdvice(String subXML, ExecutionContext context, String url, String login, String password, String host,
                                             Integer port, String provider, String archiveDir, String documentId, boolean sendReplies, boolean disableConfirmation)
            throws IOException, JDOMException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        Element rootNode = new SAXBuilder().build(new ByteArrayInputStream(subXML.getBytes(StandardCharsets.UTF_8))).getRootElement();
        List<List<Object>> firstData = new ArrayList<>();
        List<List<Object>> secondData = new ArrayList<>();

        String documentNumber = trim(rootNode.getChildText("documentNumber"));
        Timestamp dateTime = parseTimestamp(rootNode.getChildText("documentDate"));
        String deliveryNoteNumber = rootNode.getChildText("deliveryNoteNumber");
        Timestamp deliveryNoteDateTime = parseTimestamp(rootNode.getChildText("deliveryNoteDate"));
        String buyerGLN = rootNode.getChildText("buyerGLN");
        String destinationGLN = rootNode.getChildText("destinationGLN");
        String supplierGLN = rootNode.getChildText("supplierGLN");
        String orderNumber = trim(rootNode.getChildText("orderNumber"));
        Timestamp deliveryDateTimeFirst = parseTimestamp(rootNode.getChildText("deliveryDateTimeFirst"));
        String note = nullIfEmpty(rootNode.getChildText("comment"));

        Map<String, String> orderBarcodesMap = getOrderBarcodesMap(context, url, login, password, host, port, provider,
                archiveDir, documentId, documentNumber, orderNumber, sendReplies, disableConfirmation);

        int i = 1;
        for (Object line : rootNode.getChildren("line")) {
            Element lineElement = (Element) line;
            String dataGTIN = trim(lineElement.getChildText("GTIN"));
            String GTIN;
            String barcode;
            if (orderBarcodesMap.containsKey(dataGTIN)) {
                barcode = orderBarcodesMap.get(dataGTIN);
                GTIN = null;
            } else {
                barcode = null;
                GTIN = dataGTIN;
            }

            String id = supplierGLN + "/" + documentNumber + "/" + orderNumber;
            String idDetail = id + "/" + i++;
            BigDecimal quantityOrdered = parseBigDecimal(lineElement.getChildText("quantityOrdered"));
            BigDecimal quantityDespatch = parseBigDecimal(lineElement.getChildText("quantityDespatch"));
            BigDecimal valueVAT = parseBigDecimal(lineElement.getChildText("vat"));
            BigDecimal lineItemPrice = parseBigDecimal(lineElement.getChildText("lineItemPrice"));
            BigDecimal lineItemAmountWithoutCharges = parseBigDecimal(lineElement.getChildText("lineItemAmountWithoutCharges"));
            BigDecimal lineItemAmount = parseBigDecimal(lineElement.getChildText("lineItemAmount"));
            BigDecimal lineItemAmountCharges = parseBigDecimal(lineElement.getChildText("lineItemAmountCharges"));
            if (barcode != null)
                firstData.add(Arrays.<Object>asList(id, documentNumber, dateTime, deliveryNoteNumber, deliveryNoteDateTime, note, supplierGLN, buyerGLN, destinationGLN, orderNumber,
                        deliveryDateTimeFirst, idDetail, barcode, dataGTIN, quantityOrdered, quantityDespatch, valueVAT, lineItemPrice, lineItemAmountWithoutCharges,
                        lineItemAmount, lineItemAmountCharges));
            else
                secondData.add(Arrays.<Object>asList(id, documentNumber, dateTime, deliveryNoteNumber, deliveryNoteDateTime, note, supplierGLN, buyerGLN, destinationGLN, orderNumber,
                        deliveryDateTimeFirst, idDetail, GTIN, dataGTIN, quantityOrdered, quantityDespatch, valueVAT, lineItemPrice, lineItemAmountWithoutCharges,
                        lineItemAmount, lineItemAmountCharges));
        }
        return new DocumentData(documentNumber, firstData, secondData);
    }

    private String importDespatchAdvices(ExecutionContext context, DocumentData data) throws ScriptingErrorLog.SemanticErrorException {
        String message = importDespatchAdvices(context, data, true);
        return message == null ? importDespatchAdvices(context, data, false) : message;
    }

    private String importDespatchAdvices(ExecutionContext context, DocumentData data, boolean first) throws ScriptingErrorLog.SemanticErrorException {
        String message = null;
        List<List<Object>> importData = data == null ? null : (first ? data.firstData : data.secondData);
        if (importData != null && !importData.isEmpty()) {
            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField idEOrderDespatchAdviceField = new ImportField(findProperty("id[EOrderDespatchAdvice]"));
            ImportKey<?> eOrderDespatchAdviceKey = new ImportKey((CustomClass) findClass("EOrderDespatchAdvice"),
                    findProperty("eOrderDespatchAdvice[VARSTRING[100]]").getMapping(idEOrderDespatchAdviceField));
            keys.add(eOrderDespatchAdviceKey);
            props.add(new ImportProperty(idEOrderDespatchAdviceField, findProperty("id[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey)));
            fields.add(idEOrderDespatchAdviceField);

            ImportField numberEOrderDespatchAdviceField = new ImportField(findProperty("number[EOrderDespatchAdvice]"));
            props.add(new ImportProperty(numberEOrderDespatchAdviceField, findProperty("number[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey)));
            fields.add(numberEOrderDespatchAdviceField);

            ImportField dateTimeEOrderDespatchAdviceField = new ImportField(findProperty("dateTime[EOrderDespatchAdvice]"));
            props.add(new ImportProperty(dateTimeEOrderDespatchAdviceField, findProperty("dateTime[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey)));
            fields.add(dateTimeEOrderDespatchAdviceField);

            ImportField deliveryNoteNumberEOrderDespatchAdviceField = new ImportField(findProperty("deliveryNoteNumber[EOrderDespatchAdvice]"));
            props.add(new ImportProperty(deliveryNoteNumberEOrderDespatchAdviceField, findProperty("deliveryNoteNumber[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey)));
            fields.add(deliveryNoteNumberEOrderDespatchAdviceField);

            ImportField deliveryNoteDateTimeEOrderDespatchAdviceField = new ImportField(findProperty("deliveryNoteDateTime[EOrderDespatchAdvice]"));
            props.add(new ImportProperty(deliveryNoteDateTimeEOrderDespatchAdviceField, findProperty("deliveryNoteDateTime[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey)));
            fields.add(deliveryNoteDateTimeEOrderDespatchAdviceField);

            ImportField noteEOrderDespatchAdviceField = new ImportField(findProperty("note[EOrderDespatchAdvice]"));
            props.add(new ImportProperty(noteEOrderDespatchAdviceField, findProperty("note[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey)));
            fields.add(noteEOrderDespatchAdviceField);

            ImportField GLNSupplierEOrderDespatchAdviceField = new ImportField(findProperty("GLN[LegalEntity]"));
            ImportKey<?> supplierKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                    findProperty("legalEntityStockGLN[VARSTRING[13]]").getMapping(GLNSupplierEOrderDespatchAdviceField));
            supplierKey.skipKey = true;
            keys.add(supplierKey);
            props.add(new ImportProperty(GLNSupplierEOrderDespatchAdviceField, findProperty("supplier[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey),
                    object(findClass("LegalEntity")).getMapping(supplierKey)));
            fields.add(GLNSupplierEOrderDespatchAdviceField);

            ImportField GLNCustomerEOrderDespatchAdviceField = new ImportField(findProperty("GLN[LegalEntity]"));
            ImportKey<?> customerKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                    findProperty("legalEntityGLN[VARSTRING[13]]").getMapping(GLNCustomerEOrderDespatchAdviceField));
            customerKey.skipKey = true;
            keys.add(customerKey);
            props.add(new ImportProperty(GLNCustomerEOrderDespatchAdviceField, findProperty("customer[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey),
                    object(findClass("LegalEntity")).getMapping(customerKey)));
            fields.add(GLNCustomerEOrderDespatchAdviceField);

            ImportField GLNCustomerStockEOrderDespatchAdviceField = new ImportField(findProperty("GLN[Stock]"));
            ImportKey<?> customerStockKey = new ImportKey((CustomClass) findClass("Stock"),
                    findProperty("companyStockGLN[VARSTRING[13]]").getMapping(GLNCustomerStockEOrderDespatchAdviceField));
            customerStockKey.skipKey = true;
            keys.add(customerStockKey);
            props.add(new ImportProperty(GLNCustomerStockEOrderDespatchAdviceField, findProperty("customerStock[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey),
                    object(findClass("Stock")).getMapping(customerStockKey)));
            fields.add(GLNCustomerStockEOrderDespatchAdviceField);

            ImportField numberEOrderField = new ImportField(findProperty("number[EOrder]"));
            ImportKey<?> eOrderKey = new ImportKey((CustomClass) findClass("EOrder"),
                    findProperty("eOrder[VARSTRING[28]]").getMapping(numberEOrderField));
            eOrderKey.skipKey = true;
            keys.add(eOrderKey);
            props.add(new ImportProperty(numberEOrderField, findProperty("eOrder[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey),
                    object(findClass("EOrder")).getMapping(eOrderKey)));
            fields.add(numberEOrderField);

            ImportField deliveryDateTimeEOrderDespatchAdviceField = new ImportField(findProperty("deliveryDateTime[EOrderDespatchAdvice]"));
            props.add(new ImportProperty(deliveryDateTimeEOrderDespatchAdviceField, findProperty("deliveryDateTime[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey)));
            fields.add(deliveryDateTimeEOrderDespatchAdviceField);

            ImportField idEOrderDespatchAdviceDetailField = new ImportField(findProperty("id[EOrderDespatchAdviceDetail]"));
            ImportKey<?> eOrderDespatchAdviceDetailKey = new ImportKey((CustomClass) findClass("EOrderDespatchAdviceDetail"),
                    findProperty("eOrderDespatchAdviceDetail[VARSTRING[100]]").getMapping(idEOrderDespatchAdviceDetailField));
            keys.add(eOrderDespatchAdviceDetailKey);
            props.add(new ImportProperty(numberEOrderField, findProperty("orderDespatchAdvice[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey),
                    object(findClass("EOrderDespatchAdvice")).getMapping(eOrderDespatchAdviceKey)));
            props.add(new ImportProperty(idEOrderDespatchAdviceDetailField, findProperty("id[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(idEOrderDespatchAdviceDetailField);

            if (first) {
                ImportField barcodeEOrderDespatchAdviceDetailField = new ImportField(findProperty("id[Barcode]"));
                ImportKey<?> skuBarcodeKey = new ImportKey((CustomClass) findClass("Sku"),
                        findProperty("skuBarcode[VARSTRING[15]]").getMapping(barcodeEOrderDespatchAdviceDetailField));
                skuBarcodeKey.skipKey = true;
                keys.add(skuBarcodeKey);
                props.add(new ImportProperty(barcodeEOrderDespatchAdviceDetailField, findProperty("sku[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey),
                        object(findClass("Sku")).getMapping(skuBarcodeKey), true));
                fields.add(barcodeEOrderDespatchAdviceDetailField);
            } else {
                ImportField GTINEOrderDespatchAdviceDetailField = new ImportField(findProperty("id[Barcode]"));
                ImportKey<?> skuGTINKey = new ImportKey((CustomClass) findClass("Sku"),
                        findProperty("skuGTIN[VARSTRING[15]]").getMapping(GTINEOrderDespatchAdviceDetailField));
                skuGTINKey.skipKey = true;
                keys.add(skuGTINKey);
                props.add(new ImportProperty(GTINEOrderDespatchAdviceDetailField, findProperty("sku[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey),
                        object(findClass("Sku")).getMapping(skuGTINKey), true));
                fields.add(GTINEOrderDespatchAdviceDetailField);
            }

            ImportField dataGTINEOrderDespatchAdviceDetailField = new ImportField(findProperty("dataGTIN[EOrderDespatchAdviceDetail]"));
            props.add(new ImportProperty(dataGTINEOrderDespatchAdviceDetailField, findProperty("dataGTIN[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(dataGTINEOrderDespatchAdviceDetailField);

            ImportField quantityOrderedEOrderDespatchAdviceDetailField = new ImportField(findProperty("quantityOrdered[EOrderDespatchAdviceDetail]"));
            props.add(new ImportProperty(quantityOrderedEOrderDespatchAdviceDetailField, findProperty("quantityOrdered[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(quantityOrderedEOrderDespatchAdviceDetailField);

            ImportField quantityDespatchEOrderDespatchAdviceDetailField = new ImportField(findProperty("quantityDespatch[EOrderDespatchAdviceDetail]"));
            props.add(new ImportProperty(quantityDespatchEOrderDespatchAdviceDetailField, findProperty("quantityDespatch[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(quantityDespatchEOrderDespatchAdviceDetailField);

            ImportField valueVATEOrderDespatchAdviceDetailField = new ImportField(findProperty("valueVAT[EOrderDespatchAdviceDetail]"));
            props.add(new ImportProperty(valueVATEOrderDespatchAdviceDetailField, findProperty("valueVAT[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(valueVATEOrderDespatchAdviceDetailField);

            ImportField lineItemPriceEOrderDespatchAdviceDetailField = new ImportField(findProperty("lineItemPrice[EOrderDespatchAdviceDetail]"));
            props.add(new ImportProperty(lineItemPriceEOrderDespatchAdviceDetailField, findProperty("lineItemPrice[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(lineItemPriceEOrderDespatchAdviceDetailField);

            ImportField lineItemAmountWithoutChargesEOrderDespatchAdviceDetailField = new ImportField(findProperty("lineItemAmountWithoutCharges[EOrderDespatchAdviceDetail]"));
            props.add(new ImportProperty(lineItemAmountWithoutChargesEOrderDespatchAdviceDetailField, findProperty("lineItemAmountWithoutCharges[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(lineItemAmountWithoutChargesEOrderDespatchAdviceDetailField);

            ImportField lineItemAmountEOrderDespatchAdviceDetailField = new ImportField(findProperty("lineItemAmount[EOrderDespatchAdviceDetail]"));
            props.add(new ImportProperty(lineItemAmountEOrderDespatchAdviceDetailField, findProperty("lineItemAmount[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(lineItemAmountEOrderDespatchAdviceDetailField);

            ImportField lineItemAmountChargesEOrderDespatchAdviceDetailField = new ImportField(findProperty("lineItemAmountCharges[EOrderDespatchAdviceDetail]"));
            props.add(new ImportProperty(lineItemAmountChargesEOrderDespatchAdviceDetailField, findProperty("lineItemAmountCharges[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(lineItemAmountChargesEOrderDespatchAdviceDetailField);

            ImportTable table = new ImportTable(fields, importData);

            try (ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                message = newContext.applyMessage();
            } catch (Exception e) {
                ServerLoggers.importLogger.error("ImportDespatchAdvice Error: ", e);
                message = e.getMessage();
            }
        }
        return message;
    }

    private BLRWBL parseBLRWBL(String subXML, String documentId) throws IOException, JDOMException {
        Element rootNode = new SAXBuilder().build(new ByteArrayInputStream(subXML.getBytes(StandardCharsets.UTF_8))).getRootElement();
        List<List<Object>> data = new ArrayList<>();

        Element messageHeaderElement = rootNode.getChild("MessageHeader");
        String documentNumber = messageHeaderElement.getChildText("MessageID");

        Element deliveryNoteElement = rootNode.getChild("DeliveryNote");
        String deliveryNoteNumber = deliveryNoteElement.getChildText("DeliveryNoteID");
        Timestamp dateTime = parseTimestamp(deliveryNoteElement.getChildText("CreationDateTime"), "yyyyMMddHHmmss");
        String functionCode = deliveryNoteElement.getChildText("FunctionCode");
        Boolean isCancel = functionCode != null && functionCode.equals("1") ? true : null;

        Element shipperElement = deliveryNoteElement.getChild("Shipper");
        String supplierGLN = shipperElement.getChildText("GLN");

        Element receiverElement = deliveryNoteElement.getChild("Receiver");
        String customerGLN = receiverElement.getChildText("GLN");

        Element shipToElement = deliveryNoteElement.getChild("ShipTo");
        String customerStockGLN = shipToElement.getChildText("GLN");

        Element despatchAdviceLogisticUnitLineItemElement = deliveryNoteElement.getChild("DespatchAdviceLogisticUnitLineItem");

        String id = supplierGLN + "/" + deliveryNoteNumber + (isCancel != null ? "_cancel" : "");
        for (Object line : despatchAdviceLogisticUnitLineItemElement.getChildren("LineItem")) {
            Element lineElement = (Element) line;
            Integer lineItemNumber = Integer.parseInt(lineElement.getChildText("LineItemNumber"));
            String lineItemID = lineElement.getChildText("LineItemID");
            String lineItemBuyerID = lineElement.getChildText("LineItemBuyerID");
            String lineItemName = lineElement.getChildText("LineItemName");

            String idDetail = id + "/" + lineItemNumber;
            BigDecimal quantityDespatched = parseBigDecimal(lineElement.getChildText("QuantityDespatched"));
            BigDecimal valueVAT = parseBigDecimal(lineElement.getChildText("TaxRate"));
            BigDecimal lineItemPrice = parseBigDecimal(lineElement.getChildText("LineItemPrice"));
            BigDecimal lineItemAmountWithoutCharges = parseBigDecimal(lineElement.getChildText("LineItemAmountWithoutCharges"));
            BigDecimal lineItemAmount = parseBigDecimal(lineElement.getChildText("LineItemAmount"));
            BigDecimal lineItemAmountCharges = parseBigDecimal(lineElement.getChildText("LineItemAmountCharges"));
            if (lineItemID != null || lineItemBuyerID != null)
                data.add(Arrays.<Object>asList(idDetail, lineItemID, lineItemBuyerID, lineItemName,
                        quantityDespatched, valueVAT, lineItemPrice, lineItemAmountWithoutCharges,
                        lineItemAmount, lineItemAmountCharges));
        }
        return new BLRWBL(documentId, id, documentNumber, dateTime, deliveryNoteNumber, isCancel, supplierGLN, customerGLN, customerStockGLN, data);
    }

    private String importBLRWBL(ExecutionContext context, BLRWBL blrwbl) {
        String message = null;
        List<List<Object>> importData = blrwbl.detailList;
        if (importData != null && !importData.isEmpty()) {
            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            try (ExecutionContext.NewSession newContext = context.newSession()) {

                ObjectValue eInvoiceObject = findProperty("eInvoiceDeliveryNoteNumberIsCancel[VARSTRING[28], INTEGER]").readClasses(newContext, new DataObject(blrwbl.deliveryNoteNumber), new DataObject(blrwbl.isCancel != null ? 1 : 0));
                if (eInvoiceObject instanceof NullValue) {
                    eInvoiceObject = newContext.addObject((ConcreteCustomClass) findClass("EInvoice"));

                    findProperty("importedCustomer[EInvoice]").change(true, newContext, (DataObject) eInvoiceObject);
                    findProperty("id[EInvoice]").change(blrwbl.id, newContext, (DataObject) eInvoiceObject);
                    findProperty("number[EInvoice]").change(blrwbl.documentNumber, newContext, (DataObject) eInvoiceObject);
                    findProperty("dateTime[EInvoice]").change(blrwbl.dateTime, newContext, (DataObject) eInvoiceObject);
                    findProperty("deliveryNoteDateTime[EInvoice]").change(blrwbl.dateTime, newContext, (DataObject) eInvoiceObject);
                    findProperty("deliveryNoteNumber[EInvoice]").change(blrwbl.deliveryNoteNumber, newContext, (DataObject) eInvoiceObject);
                    findProperty("isCancel[EInvoice]").change(blrwbl.isCancel, newContext, (DataObject) eInvoiceObject);

                    ObjectValue supplierObject = findProperty("legalEntityStockGLN[VARSTRING[13]]").readClasses(newContext, new DataObject(blrwbl.supplierGLN));
                    findProperty("supplier[EInvoice]").change(supplierObject, newContext, (DataObject) eInvoiceObject);

                    ObjectValue customerObject = findProperty("legalEntityGLN[VARSTRING[13]]").readClasses(newContext, new DataObject(blrwbl.customerGLN));
                    findProperty("customer[EInvoice]").change(customerObject, newContext, (DataObject) eInvoiceObject);

                    ObjectValue customerStockObject = findProperty("companyStockGLN[VARSTRING[13]]").readClasses(newContext, new DataObject(blrwbl.customerStockGLN));
                    findProperty("customerStock[EInvoice]").change(customerStockObject, newContext, (DataObject) eInvoiceObject);

                    ImportField idEInvoiceDetailField = new ImportField(findProperty("id[EInvoiceDetail]"));
                    ImportKey<?> eInvoiceDetailKey = new ImportKey((CustomClass) findClass("EInvoiceDetail"),
                            findProperty("eInvoiceDetail[VARSTRING[100]]").getMapping(idEInvoiceDetailField));
                    keys.add(eInvoiceDetailKey);
                    props.add(new ImportProperty(idEInvoiceDetailField, findProperty("eInvoice[EInvoiceDetail]").getMapping(eInvoiceDetailKey),
                            object(findClass("EInvoice")).getMapping(eInvoiceObject)));
                    props.add(new ImportProperty(idEInvoiceDetailField, findProperty("id[EInvoiceDetail]").getMapping(eInvoiceDetailKey)));
                    fields.add(idEInvoiceDetailField);

                    ImportField lineItemIDEInvoiceDetailField = new ImportField(findProperty("lineItemID[EInvoiceDetail]"));
                    props.add(new ImportProperty(lineItemIDEInvoiceDetailField, findProperty("lineItemID[EInvoiceDetail]").getMapping(eInvoiceDetailKey)));
                    fields.add(lineItemIDEInvoiceDetailField);

                    ImportField lineItemBuyerIDEInvoiceDetailField = new ImportField(findProperty("lineItemBuyerID[EInvoiceDetail]"));
                    props.add(new ImportProperty(lineItemBuyerIDEInvoiceDetailField, findProperty("lineItemBuyerID[EInvoiceDetail]").getMapping(eInvoiceDetailKey)));
                    fields.add(lineItemBuyerIDEInvoiceDetailField);

                    ImportField lineItemNameEInvoiceDetailField = new ImportField(findProperty("lineItemName[EInvoiceDetail]"));
                    props.add(new ImportProperty(lineItemNameEInvoiceDetailField, findProperty("lineItemName[EInvoiceDetail]").getMapping(eInvoiceDetailKey)));
                    fields.add(lineItemNameEInvoiceDetailField);

                    ImportField quantityDespatchedEInvoiceDetailField = new ImportField(findProperty("quantityDespatched[EInvoiceDetail]"));
                    props.add(new ImportProperty(quantityDespatchedEInvoiceDetailField, findProperty("quantityDespatched[EInvoiceDetail]").getMapping(eInvoiceDetailKey)));
                    fields.add(quantityDespatchedEInvoiceDetailField);

                    ImportField valueVATEInvoiceDetailField = new ImportField(findProperty("valueVAT[EInvoiceDetail]"));
                    props.add(new ImportProperty(valueVATEInvoiceDetailField, findProperty("valueVAT[EInvoiceDetail]").getMapping(eInvoiceDetailKey)));
                    fields.add(valueVATEInvoiceDetailField);

                    ImportField lineItemPriceEInvoiceDetailField = new ImportField(findProperty("lineItemPrice[EInvoiceDetail]"));
                    props.add(new ImportProperty(lineItemPriceEInvoiceDetailField, findProperty("lineItemPrice[EInvoiceDetail]").getMapping(eInvoiceDetailKey)));
                    fields.add(lineItemPriceEInvoiceDetailField);

                    ImportField lineItemAmountWithoutChargesEInvoiceDetailField = new ImportField(findProperty("lineItemAmountWithoutCharges[EInvoiceDetail]"));
                    props.add(new ImportProperty(lineItemAmountWithoutChargesEInvoiceDetailField, findProperty("lineItemAmountWithoutCharges[EInvoiceDetail]").getMapping(eInvoiceDetailKey)));
                    fields.add(lineItemAmountWithoutChargesEInvoiceDetailField);

                    ImportField lineItemAmountEInvoiceDetailField = new ImportField(findProperty("lineItemAmount[EInvoiceDetail]"));
                    props.add(new ImportProperty(lineItemAmountEInvoiceDetailField, findProperty("lineItemAmount[EInvoiceDetail]").getMapping(eInvoiceDetailKey)));
                    fields.add(lineItemAmountEInvoiceDetailField);

                    ImportField lineItemAmountChargesEInvoiceDetailField = new ImportField(findProperty("lineItemAmountCharges[EInvoiceDetail]"));
                    props.add(new ImportProperty(lineItemAmountChargesEInvoiceDetailField, findProperty("lineItemAmountCharges[EInvoiceDetail]").getMapping(eInvoiceDetailKey)));
                    fields.add(lineItemAmountChargesEInvoiceDetailField);

                    ImportTable table = new ImportTable(fields, importData);

                    IntegrationService service = new IntegrationService(newContext, table, keys, props);
                    service.synchronize(true, false);
                    message = newContext.applyMessage();
                } else {
                    findProperty("importedCustomer[EInvoice]").change(true, newContext, (DataObject) eInvoiceObject);
                    message = newContext.applyMessage();
                }
            } catch (Exception e) {
                ServerLoggers.importLogger.error("ImportEInvoice Error: ", e);
                message = "ImportEInvoice Error: " + e.getMessage();
            }
        }
        return message;
    }

    private InvoiceMessage parseInvoiceSystemMessage(ExecutionContext context, String subXML, String documentId) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, IOException, JDOMException {
        Element rootNode = new SAXBuilder().build(new ByteArrayInputStream(subXML.getBytes(StandardCharsets.UTF_8))).getRootElement();
        String documentNumber = trim(rootNode.getChildText("documentNumber"));
        Timestamp dateTime = parseTimestamp(rootNode.getChildText("documentDate"));

        Element reference = rootNode.getChild("reference");
        if (reference != null) {
            String documentType = reference.getChildText("documentType");
            if (documentType != null && (documentType.equals("BLRWBL") || documentType.equals("SYSTEMMESSAGE"))) {
                String invoiceNumber = (String) findProperty("deliveryNoteNumberEInvoiceBlrwbl[VARSTRING[28]]").read(context, new DataObject(trim(reference.getChildText("documentNumber"))));
                String code = reference.getChildText("code");
                String description = getDescriptionByCode(reference.getChildText("description"), code);

                return new InvoiceMessage(documentId, documentNumber, dateTime, code, description, invoiceNumber);
            }
        }
        return null;
    }

    private BLRWBR parseBLRWBR(String subXML, String docId) throws IOException, JDOMException {
        Element rootNode = new SAXBuilder().build(new ByteArrayInputStream(subXML.getBytes(StandardCharsets.UTF_8))).getRootElement();
        String documentNumber = rootNode.getChild("MessageHeader").getChildText("MessageID");
        Element deliveryNoteElement = rootNode.getChild("DeliveryNote");
        String documentId = deliveryNoteElement.getChildText("DocumentID");
        String creationDateTime = deliveryNoteElement.getChildText("CreationDateTime");
        String deliveryNoteNumber = deliveryNoteElement.getChildText("DeliveryNoteID");

        String functionCode = deliveryNoteElement.getChildText("FunctionCode");
        Boolean isCancel = functionCode != null && functionCode.equals("1") ? true : null;

        long currentTime = Calendar.getInstance().getTime().getTime();
        String documentNumberBLRAPN = String.valueOf(currentTime);
        String documentDate = new SimpleDateFormat("yyyyMMddHHmmss").format(currentTime);

        return new BLRWBR(docId, documentId, creationDateTime, documentNumber, deliveryNoteNumber, documentNumberBLRAPN, documentDate, isCancel);
    }

    private DocumentData parseInvoiceMessage(ExecutionContext context, String subXML, String provider, String documentId, boolean receiveSupplierMessages) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, IOException, JDOMException {
        Element rootNode = new SAXBuilder().build(new ByteArrayInputStream(subXML.getBytes(StandardCharsets.UTF_8))).getRootElement();
        Element acknowledgementElement = rootNode.getChild("Acknowledgement");

        String documentNumber = acknowledgementElement.getChildText("DocumentID");
        Timestamp dateTime = parseTimestamp(acknowledgementElement.getChildText("CreationDateTime"), "yyyyMMddHHmmss");

        Element referenceDocumentElement = acknowledgementElement.getChild("ReferenceDocument");
        if (referenceDocumentElement != null) {
            String type = referenceDocumentElement.getChildText("Type");
            if (type != null && (type.equals("BLRAPN") || type.equals("BLRWBR") || (type.equals("BLRWBL") && receiveSupplierMessages))) {

                String invoiceNumber = referenceDocumentElement.getChildText("ID");
                if (type.equals("BLRAPN"))
                    invoiceNumber = (String) findProperty("deliveryNoteNumberEInvoiceBlrapn[VARSTRING[28]]").read(context, new DataObject(invoiceNumber));
                else if (type.equals("BLRWBR"))
                    invoiceNumber = (String) findProperty("deliveryNoteNumberEInvoiceBlrwbr[VARSTRING[28]]").read(context, new DataObject(invoiceNumber));
                else if (type.equals("BLRWBL") && receiveSupplierMessages)
                    invoiceNumber = (String) findProperty("deliveryNoteNumberEInvoiceBlrwbl[VARSTRING[28]]").read(context, new DataObject(invoiceNumber));

                Element errorOrAcknowledgementElement = acknowledgementElement.getChild("ErrorOrAcknowledgement");
                if (errorOrAcknowledgementElement != null) {
                    String code = errorOrAcknowledgementElement.getChildText("Code");
                    String description = getDescriptionByCode(errorOrAcknowledgementElement.getChildText("Description"), code);
                    return new DocumentData(documentNumber, Collections.singletonList(Arrays.asList((Object) documentNumber, dateTime, code, description, invoiceNumber)), null);
                }
            }
        } else
            ServerLoggers.importLogger.error(String.format("%s Parse Invoice Message %s error: no reference tag", provider, documentId));
        return new DocumentData(documentNumber, null, null);
    }

    private String importInvoiceMessages(ExecutionContext context, List<List<Object>> importData) throws ScriptingErrorLog.SemanticErrorException {
        String message = null;
        if (!importData.isEmpty()) {
            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField numberEInvoiceMessageField = new ImportField(findProperty("number[EInvoiceMessage]"));
            ImportKey<?> eInvoiceMessageKey = new ImportKey((CustomClass) findClass("EInvoiceMessage"),
                    findProperty("eInvoiceMessage[VARSTRING[24]]").getMapping(numberEInvoiceMessageField));
            keys.add(eInvoiceMessageKey);
            props.add(new ImportProperty(numberEInvoiceMessageField, findProperty("number[EInvoiceMessage]").getMapping(eInvoiceMessageKey)));
            fields.add(numberEInvoiceMessageField);

            ImportField dateTimeEInvoiceMessageField = new ImportField(findProperty("dateTime[EInvoiceMessage]"));
            props.add(new ImportProperty(dateTimeEInvoiceMessageField, findProperty("dateTime[EInvoiceMessage]").getMapping(eInvoiceMessageKey)));
            fields.add(dateTimeEInvoiceMessageField);

            ImportField codeEInvoiceMessageField = new ImportField(findProperty("code[EInvoiceMessage]"));
            props.add(new ImportProperty(codeEInvoiceMessageField, findProperty("code[EInvoiceMessage]").getMapping(eInvoiceMessageKey)));
            fields.add(codeEInvoiceMessageField);

            ImportField descriptionEInvoiceMessageField = new ImportField(findProperty("description[EInvoiceMessage]"));
            props.add(new ImportProperty(descriptionEInvoiceMessageField, findProperty("description[EInvoiceMessage]").getMapping(eInvoiceMessageKey)));
            fields.add(descriptionEInvoiceMessageField);

            ImportField deliveryNoteNumberField = new ImportField(findProperty("deliveryNoteNumber[EInvoice]"));
            ImportKey<?> eInvoiceKey = new ImportKey((CustomClass) findClass("EInvoice"),
                    findProperty("eInvoiceDeliveryNoteNumber[VARSTRING[28]]").getMapping(deliveryNoteNumberField));
            eInvoiceKey.skipKey = true;
            keys.add(eInvoiceKey);
            props.add(new ImportProperty(deliveryNoteNumberField, findProperty("eInvoice[EInvoiceMessage]").getMapping(eInvoiceMessageKey),
                    object(findClass("EInvoice")).getMapping(eInvoiceKey)));
            fields.add(deliveryNoteNumberField);

            ImportTable table = new ImportTable(fields, importData);

            try (ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                message = newContext.applyMessage();
            } catch (Exception e) {
                ServerLoggers.importLogger.error("ImportInvoiceMessages Error: ", e);
                message = e.getMessage();
            }
        }
        return message;
    }

    private String importInvoiceSystemMessage(ExecutionContext context, InvoiceMessage invoiceMessage) {
        String message = null;
        if (invoiceMessage != null && invoiceMessage.invoiceNumber != null) {

            try (ExecutionContext.NewSession newContext = context.newSession()) {

                ObjectValue eInvoiceObject = findProperty("eInvoiceDeliveryNoteNumber[VARSTRING[24]]").readClasses(newContext, new DataObject(invoiceMessage.invoiceNumber));
                if (eInvoiceObject instanceof DataObject) {

                    ObjectValue eInvoiceMessageObject = findProperty("eInvoiceMessage[VARSTRING[24]]").readClasses(newContext, new DataObject(invoiceMessage.documentNumber));
                    if (eInvoiceMessageObject instanceof NullValue) {
                        eInvoiceMessageObject = newContext.addObject((ConcreteCustomClass) findClass("EInvoiceMessage"));
                        findProperty("number[EInvoiceMessage]").change(invoiceMessage.documentNumber, newContext, (DataObject) eInvoiceMessageObject);
                    }
                    findProperty("dateTime[EInvoiceMessage]").change(invoiceMessage.dateTime, newContext, (DataObject) eInvoiceMessageObject);
                    findProperty("code[EInvoiceMessage]").change(invoiceMessage.code, newContext, (DataObject) eInvoiceMessageObject);
                    findProperty("description[EInvoiceMessage]").change(invoiceMessage.description, newContext, (DataObject) eInvoiceMessageObject);
                    findProperty("eInvoice[EInvoiceMessage]").change(eInvoiceObject, newContext, (DataObject) eInvoiceMessageObject);

                    message = newContext.applyMessage();
                }
            } catch (Exception e) {
                ServerLoggers.importLogger.error("importInvoiceSystemMessage Error: ", e);
                message = e.getMessage();
            }
        }
        return message;
    }

    private void confirmDocumentReceived(ExecutionContext context, String documentId, String url, String login, String password,
                                         String host, Integer port, String provider, String archiveDir, boolean disableConfirmation) throws IOException, JDOMException {
        if(!disableConfirmation) {

            String xml = generateConfirmDocumentXML(documentId, login, password);

            HttpResponse httpResponse = sendRequest(host, port, login, password, url, xml, null);
            ServerLoggers.importLogger.info(String.format("%s ConfirmDocumentReceived document %s: request sent", provider, documentId));
            RequestResult requestResult = getRequestResult(httpResponse, getResponseMessage(httpResponse), archiveDir, "ConfirmDocumentReceived");
            switch (requestResult) {
                case OK:
                    ServerLoggers.importLogger.info(String.format("%s ConfirmDocumentReceived document %s: request succeeded", provider, documentId));
                    break;
                case AUTHORISATION_ERROR:
                    ServerLoggers.importLogger.error(String.format("%s ConfirmDocumentReceived document %s: invalid login-password", provider, documentId));
                    context.delayUserInteraction(new MessageClientAction(String.format("%s Документ %s не помечен как обработанный: ошибка авторизации", provider, documentId), "Импорт"));
                    break;
                case UNKNOWN_ERROR:
                    ServerLoggers.importLogger.error(String.format("%s ConfirmDocumentReceived document %s: unknown error", provider, documentId));
                    context.delayUserInteraction(new MessageClientAction(String.format("%s Документ %s не помечен как обработанный", provider, documentId), "Импорт"));
            }
        }
    }

    private String generateConfirmDocumentXML(String documentId, String login, String password) {
            Element rootElement = new Element("Envelope", soapenvNamespace);
            rootElement.setNamespace(soapenvNamespace);
            rootElement.addNamespaceDeclaration(soapenvNamespace);
            rootElement.addNamespaceDeclaration(topNamespace);

            Document doc = new Document(rootElement);
            doc.setRootElement(rootElement);

            //parent: rootElement
            Element headerElement = new Element("Header", soapenvNamespace);
            rootElement.addContent(headerElement);

            //parent: rootElement
            Element bodyElement = new Element("Body", soapenvNamespace);
            rootElement.addContent(bodyElement);

            //parent: bodyElement
            Element confirmDocumentReceivedElement = new Element("ConfirmDocumentReceived", topNamespace);
            bodyElement.addContent(confirmDocumentReceivedElement);

            addStringElement(topNamespace, confirmDocumentReceivedElement, "username", login);
            addStringElement(topNamespace, confirmDocumentReceivedElement, "password", password);
            addStringElement(topNamespace, confirmDocumentReceivedElement, "documentId", documentId);

            return new XMLOutputter().outputString(doc);
    }

    protected boolean sendRecipientError(ExecutionContext context, String url, String login, String password, String host, Integer port, String provider, String archiveDir,
                                         String documentId, String documentNumber, String error, boolean disableConfirmation, boolean sendReplies) throws IOException, JDOMException {
        if(!disableConfirmation && sendReplies) {
            boolean succeeded = false;

            String xml = generateRecipientErrorXML(login, password, documentId, documentNumber, error);

            HttpResponse httpResponse = sendRequest(host, port, login, password, url, xml, null);
            ServerLoggers.importLogger.info(String.format("%s RecipientError %s request sent", provider, documentId));
            RequestResult requestResult = getRequestResult(httpResponse, getResponseMessage(httpResponse), archiveDir, "SendDocument");
            switch (requestResult) {
                case OK:
                    succeeded = true;
                    break;
                case AUTHORISATION_ERROR:
                    ServerLoggers.importLogger.error(String.format("%s RecipientError %s: invalid login-password", provider, documentId));
                    context.delayUserInteraction(new MessageClientAction(String.format("%s Сообщение об ошибке %s не выгружено: ошибка авторизации", provider, documentId), "Импорт"));
                    break;
                case UNKNOWN_ERROR:
                    ServerLoggers.importLogger.error(String.format("%s RecipientError %s: unknown error", provider, documentId));
                    context.delayUserInteraction(new MessageClientAction(String.format("%s Сообщение об ошибке %s не выгружено: неизвестная ошибка", provider, documentId), "Импорт"));
            }
            return succeeded;
        } else {
            return true;
        }
    }

    private String generateRecipientErrorXML(String login, String password, String documentId, String documentNumber, String error) {
        String currentDate = formatTimestamp(new Timestamp(Calendar.getInstance().getTime().getTime()));
        String contentSubXML = getErrorSubXML(documentId, documentNumber, error);

        Element rootElement = new Element("Envelope", soapenvNamespace);
        rootElement.setNamespace(soapenvNamespace);
        rootElement.addNamespaceDeclaration(soapenvNamespace);
        rootElement.addNamespaceDeclaration(topNamespace);

        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        //parent: rootElement
        Element headerElement = new Element("Header", soapenvNamespace);
        rootElement.addContent(headerElement);

        //parent: rootElement
        Element bodyElement = new Element("Body", soapenvNamespace);
        rootElement.addContent(bodyElement);

        //parent: bodyElement
        Element sendDocumentElement = new Element("SendDocument", topNamespace);
        bodyElement.addContent(sendDocumentElement);

        addStringElement(topNamespace, sendDocumentElement, "username", login);
        addStringElement(topNamespace, sendDocumentElement, "password", password);
        addStringElement(topNamespace, sendDocumentElement, "documentDate", currentDate);
        addStringElement(topNamespace, sendDocumentElement, "documentNumber", documentId);

        addStringElement(topNamespace, sendDocumentElement, "documentType", "SYSTEMMESSAGE");
        addStringElement(topNamespace, sendDocumentElement, "content", contentSubXML);

        return new XMLOutputter().outputString(doc);
    }

    private String getErrorSubXML(String documentId, String documentNumber, String error) {
        Element rootElement = new Element("SYSTEMMESSAGE");
        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        addStringElement(rootElement, "documentNumber", documentId);

        Element referenceElement = new Element("reference");
        addStringElement(referenceElement, "documentNumber", documentNumber);
        addStringElement(referenceElement, "code", "1450");
        addStringElement(referenceElement, "description", error);
        rootElement.addContent(referenceElement);

        String xml = new XMLOutputter().outputString(doc);
        return new String(org.apache.commons.codec.binary.Base64.encodeBase64(xml.getBytes()));
    }

    private Map<String, String> getOrderBarcodesMap(ExecutionContext context, String url, String login, String password, String host, Integer port,
                                                    String provider, String archiveDir, String documentId, String documentNumber, String orderNumber,
                                                    boolean sendReplies, boolean disableConfirmation)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, IOException, JDOMException {
        Map<String, String> orderBarcodesMap = new HashMap<>();
        if (orderNumber != null) {
            if (findProperty("eOrder[VARSTRING[28]]").read(context, new DataObject(orderNumber)) == null) {
                sendRecipientError(context, url, login, password, host, port, provider, archiveDir, documentId, documentNumber, String.format("Заказ %s не найден)", orderNumber), disableConfirmation, sendReplies);
            }

            KeyExpr orderDetailExpr = new KeyExpr("EOrderDetail");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "eOrderDetail", orderDetailExpr);

            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            String[] names = new String[]{"idBarcode", "GTINBarcode"};
            LCP[] properties = findProperties("idBarcode[EOrderDetail]", "GTINBarcode[EOrderDetail]");
            for (int i = 0; i < properties.length; i++) {
                query.addProperty(names[i], properties[i].getExpr(context.getModifier(), orderDetailExpr));
            }
            query.and(findProperty("numberOrder[EOrderDetail]").getExpr(context.getModifier(), orderDetailExpr).compare(new DataObject(orderNumber), Compare.EQUALS));
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);
            for (ImMap<Object, Object> entry : result.values()) {
                String idBarcode = (String) entry.get("idBarcode");
                String GTINBarcode = (String) entry.get("GTINBarcode");
                orderBarcodesMap.put(GTINBarcode, idBarcode);
            }
        }
        return orderBarcodesMap;
    }

    private Timestamp parseTimestamp(String value) {
        return parseTimestamp(value, "yyyy-MM-dd'T'HH:mm:ss");
    }

    private Timestamp parseTimestamp(String value, String pattern) {
        try {
            return new Timestamp(new SimpleDateFormat(pattern).parse(value).getTime());
        } catch (Exception e) {
            return null;
        }
    }

    private String getDescriptionByCode(String description, String code) {
        if (description == null || description.isEmpty()) {
            switch (code) {
                case "1251":
                    return "Сообщение прочитано получателем";
                case "1252":
                    return "Сообщение принято учётной системой получателя";
                case "2650":
                    return "Извещение о прочтении";
                default:
                    return null;
            }
        } else
            return description;
    }

    private String createBLRAPN(ExecutionContext context, DataObject eInvoiceObject, String outputDir, String documentNumber, String documentDate,
                                String referenceNumber, String referenceDate, String glnSupplier, String glnCustomer)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        outputDir = outputDir == null ? null : (outputDir + "/sent");

        String error = "";

        String deliveryNoteId = (String) findProperty("deliveryNoteNumber[EInvoice]").read(context, eInvoiceObject);
        Timestamp deliveryNoteDateTime = (Timestamp) findProperty("deliveryNoteDateTime[EInvoice]").read(context, eInvoiceObject);

        Element rootElement = new Element("BLRAPN");
        rootElement.setAttribute("version", "0.1");
        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        Element messageHeaderElement = new Element("MessageHeader");
        addStringElement(messageHeaderElement, "MessageID", documentNumber);
        addStringElement(messageHeaderElement, "MsgDateTime", documentDate);
        addStringElement(messageHeaderElement, "MessageType", "BLRAPN");
        addStringElement(messageHeaderElement, "MsgSenderID", glnSupplier);
        addStringElement(messageHeaderElement, "MsgReceiverID", glnCustomer);
        rootElement.addContent(messageHeaderElement);

        Element acknowledgementElement = new Element("Acknowledgement");
        addStringElement(acknowledgementElement, "DocumentID", documentNumber); //Номер электронного подтверждения или извещения
        addIntegerElement(acknowledgementElement, "FunctionCode", 6); //Статус  подтверждения / извещения: 6  = Подтверждено
        addStringElement(acknowledgementElement, "CreationDateTime", documentDate); //Дата и время создания подтверждения или извещения в формате ГГГГДДММЧЧММСС
        addStringElement(acknowledgementElement, "DeliveryNoteID", deliveryNoteId); //Юридический номер документа, к которому относитсяподтверждение или извещение.
        addStringElement(acknowledgementElement, "DeliveryNoteDate", deliveryNoteDateTime != null ? new SimpleDateFormat("yyyyMMdd").format(deliveryNoteDateTime) : null); //Юридическая дата документа, к которому относится подтверждение или извещение.

        Element referenceDocumentElement = new Element("ReferenceDocument");
        addStringElement(referenceDocumentElement, "Type", "BLRWBR");
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

    private void archiveDocument(String archiveDir, String documentId, String subXML) {
        if (archiveDir != null) {
            try {
                FileUtils.writeStringToFile(new File(archiveDir + "/received/" + documentId), subXML);
            } catch (Exception e) {
                ServerLoggers.importLogger.error("Archive file error: ", e);
            }
        }
    }

    private class DocumentData {
        String documentNumber;
        List<List<Object>> firstData;
        List<List<Object>> secondData;
        boolean skip;

        public DocumentData(String documentNumber, List<List<Object>> firstData, List<List<Object>> secondData) {
            this(documentNumber, firstData, secondData, false);
        }

        public DocumentData(String documentNumber, List<List<Object>> firstData, List<List<Object>> secondData, boolean skip) {
            this.documentNumber = documentNumber;
            this.firstData = firstData;
            this.secondData = secondData;
            this.skip = skip;
        }
    }

    private class BLRWBR {
        String docId; //возможно, они всегда одинаковые - проверить
        String documentId;
        String creationDateTime;
        String documentNumber;
        String deliveryNoteNumber;
        String documentNumberBLRAPN;
        String documentDate;
        Boolean isCancel;

        public BLRWBR(String docId, String documentId, String creationDateTime, String documentNumber, String deliveryNoteNumber,
                      String documentNumberBLRAPN, String documentDate, Boolean isCancel) {
            this.docId = docId;
            this.documentId = documentId;
            this.creationDateTime = creationDateTime;
            this.documentNumber = documentNumber;
            this.deliveryNoteNumber = deliveryNoteNumber;
            this.documentNumberBLRAPN = documentNumberBLRAPN;
            this.documentDate = documentDate;
            this.isCancel = isCancel;
        }
    }

    private class BLRWBL {
        String documentId;
        String id;
        String documentNumber;
        Timestamp dateTime;
        String deliveryNoteNumber;
        Boolean isCancel;
        String supplierGLN;
        String customerGLN;
        String customerStockGLN;
        List<List<Object>> detailList;

        public BLRWBL(String documentId, String id, String documentNumber, Timestamp dateTime, String deliveryNoteNumber, Boolean isCancel,
                      String supplierGLN, String customerGLN, String customerStockGLN, List<List<Object>> detailList) {
            this.documentId = documentId;
            this.id = id;
            this.documentNumber = documentNumber;
            this.dateTime = dateTime;
            this.deliveryNoteNumber = deliveryNoteNumber;
            this.isCancel = isCancel;
            this.supplierGLN = supplierGLN;
            this.customerGLN = customerGLN;
            this.customerStockGLN = customerStockGLN;
            this.detailList = detailList;
        }
    }

    private class InvoiceMessage {
        String documentId;
        String documentNumber;
        Timestamp dateTime;
        String code;
        String description;
        String invoiceNumber;

        public InvoiceMessage(String documentId, String documentNumber, Timestamp dateTime, String code, String description, String invoiceNumber) {
            this.documentId = documentId;
            this.documentNumber = documentNumber;
            this.dateTime = dateTime;
            this.code = code;
            this.description = description;
            this.invoiceNumber = invoiceNumber;
        }
    }
}
