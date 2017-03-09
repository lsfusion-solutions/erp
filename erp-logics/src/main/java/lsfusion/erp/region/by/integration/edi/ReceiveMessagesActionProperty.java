package lsfusion.erp.region.by.integration.edi;

import lsfusion.base.Pair;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
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
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReceiveMessagesActionProperty extends EDIActionProperty {

    public ReceiveMessagesActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    protected void receiveMessages(ExecutionContext context, String url, String login, String password, String host, int port, String provider, String archiveDir, boolean sendReplies)
            throws IOException, JDOMException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
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

        String xml = new XMLOutputter().outputString(doc);
        HttpResponse httpResponse = sendRequest(host, port, login, password, url, xml, null);
        ServerLoggers.importLogger.info(provider + " ReceiveMessages request sent");
        String responseMessage = getResponseMessage(httpResponse);
        RequestResult requestResult = getRequestResult(httpResponse, responseMessage, "ReceiveMessages");
        switch (requestResult) {
            case OK:
                importMessages(context, url, login, password, host, port, provider, responseMessage, archiveDir, sendReplies);
                break;
            case AUTHORISATION_ERROR:
                ServerLoggers.importLogger.error(provider + " ReceiveMessages: invalid login-password");
                context.delayUserInteraction(new MessageClientAction(provider + " Сообщения не получены: ошибка авторизации", "Экспорт"));
                break;
            case UNKNOWN_ERROR:
                ServerLoggers.importLogger.error(provider + " ReceiveMessages: unknown error");
                context.delayUserInteraction(new MessageClientAction(provider + " Сообщения не получены: неизвестная ошибка", "Экспорт"));
        }
    }

    private void importMessages(ExecutionContext context, String url, String login, String password, String host, Integer port,
                                String provider, String responseMessage, String archiveDir, boolean sendReplies)
            throws JDOMException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, Pair<String, String>> succeededMap = new HashMap<>();
        Map<String, DocumentData> messages = new HashMap<>();
        Map<String, DocumentData> orderResponses = new HashMap<>();
        Map<String, DocumentData> despatchAdvices = new HashMap<>();

        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(new ByteArrayInputStream(responseMessage.getBytes("utf-8")));
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
                        if (successful != null && Boolean.parseBoolean(successful)) {

                            Element dataElement = result.getChild("Data", topNamespace);
                            for (Object documentDataObject : dataElement.getChildren("DocumentData", topNamespace)) {
                                Element documentData = (Element) documentDataObject;

                                String subXML = new String(Base64.decode(documentData.getChildText("Data", topNamespace).getBytes()));
                                String documentType = documentData.getChildText("DocumentType", topNamespace).toLowerCase();
                                String documentId = documentData.getChildText("Id", topNamespace);

                                switch (documentType) {
                                    case "systemmessage":
                                        messages.put(documentId, parseOrderMessage(subXML, provider, documentId));
                                        break;
                                    case "ordrsp": {
                                        DocumentData orderResponse = parseOrderResponse(context, url, login, password, host, port, provider, documentId, subXML, sendReplies);
                                        if (orderResponse != null) {
                                            orderResponses.put(documentId, orderResponse);
                                        }
                                        break;
                                    }
                                    case "desadv": {
                                        DocumentData despatchAdvice = parseDespatchAdvice(context, url, login, password, host, port, provider, documentId, subXML, sendReplies);
                                        if (despatchAdvice != null)
                                            despatchAdvices.put(documentId, despatchAdvice);
                                        break;
                                    }
                                }

                                if (archiveDir != null) {
                                    try {
                                        FileUtils.writeStringToFile(new File(archiveDir + "/" + documentId), subXML);
                                    } catch (Exception e) {
                                        ServerLoggers.importLogger.error("Archive file error: ", e);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        int messagesSucceeded = 0;
        int messagesFailed = 0;
        for(Map.Entry<String, DocumentData> message : messages.entrySet()) {
            String documentId = message.getKey();
            DocumentData data = message.getValue();
            if(data.data != null) {
                String error = importOrderMessages(context, data);
                succeededMap.put(documentId, Pair.create(data.documentNumber, error));
                if (error == null) {
                    ServerLoggers.importLogger.info(String.format("%s Import EOrderMessage %s succeeded", provider, documentId));
                    messagesSucceeded++;
                } else {
                    ServerLoggers.importLogger.error(String.format("%s Import EOrderMessage %s failed: %s", provider, documentId, error));
                    messagesFailed++;
                }
            } else {
                succeededMap.put(documentId, Pair.create(data.documentNumber, String.format("%s Parsing EOrderMessage %s failed", provider, documentId)));
                messagesFailed++;
            }
        }

        int responsesSucceeded = 0;
        int responsesFailed = 0;
        for (Map.Entry<String, DocumentData> orderResponse : orderResponses.entrySet()) {
            String documentId = orderResponse.getKey();
            DocumentData data = orderResponse.getValue();
            String error = importOrderResponses(context, data);
            succeededMap.put(documentId, Pair.create(data.documentNumber, error));
            if (error == null) {
                ServerLoggers.importLogger.info(String.format("%s Import EOrderResponse %s succeeded", provider, documentId));
                responsesSucceeded++;
            } else {
                ServerLoggers.importLogger.error(String.format("%s Import EOrderResponse %s failed: %s", provider, documentId, error));
                responsesFailed++;
            }
        }

        int despatchAdvicesSucceeded = 0;
        int despatchAdvicesFailed = 0;
        for (Map.Entry<String, DocumentData> despatchAdvice : despatchAdvices.entrySet()) {
            String documentId = despatchAdvice.getKey();
            DocumentData data = despatchAdvice.getValue();
            String error = importDespatchAdvices(context, data);
            succeededMap.put(documentId, Pair.create(data.documentNumber, error));
            if (error == null) {
                ServerLoggers.importLogger.info(String.format("%s Import EOrderDespatchAdvice %s succeeded", provider, documentId));
                despatchAdvicesSucceeded++;
            } else {
                ServerLoggers.importLogger.error(String.format("%s Import EOrderDespatchAdvice %s failed: %s", provider, documentId, error));
                despatchAdvicesFailed++;
            }
        }

        String message = "";
        if(messagesSucceeded > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Загружено сообщений: %s", messagesSucceeded);
        if(messagesFailed > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Не загружено сообщений: %s", messagesFailed);

        if(responsesSucceeded > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Загружено ответов по заказам: %s", responsesSucceeded);
        if(responsesFailed > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Не загружено ответов по заказам: %s", responsesFailed);

        if(despatchAdvicesSucceeded > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Загружено уведомлений об отгрузке: %s", despatchAdvicesSucceeded);
        if(despatchAdvicesFailed > 0)
            message += (message.isEmpty() ? "" : "\n") + String.format("Не загружено уведомлений об отгрузке: %s", despatchAdvicesFailed);

        boolean succeeded = true;
        if(succeededMap.isEmpty())
            message += (message.isEmpty() ? "" : "\n") + "Не найдено новых сообщений";
        else {

            for(Map.Entry<String, Pair<String, String>> succeededEntry : succeededMap.entrySet()) {
                String documentId = succeededEntry.getKey();
                Pair<String, String> documentNumberError = succeededEntry.getValue();
                String documentNumber = documentNumberError.first;
                String error = documentNumberError.second;
                confirmDocumentReceived(context, documentId, url, login, password, host, port, provider);
                if(error != null && sendReplies)
                    succeeded = succeeded && sendRecipientError(context, url, login, password, host, port, provider, documentId, documentNumber, error);
            }

        }

        if(succeeded)
            context.delayUserInteraction(new MessageClientAction(message, "Импорт"));
    }

    private DocumentData parseOrderMessage(String message, String provider, String documentId) throws IOException, JDOMException {
        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(new ByteArrayInputStream(message.getBytes("utf-8")));
        Element rootNode = document.getRootElement();

        String documentNumber = rootNode.getChildText("documentNumber");
        Timestamp dateTime = parseTimestamp(rootNode.getChildText("documentDate"));

        Element reference = rootNode.getChild("reference");
        if (reference != null) {
            String documentType = reference.getChildText("documentType");
            if (documentType.equals("ORDERS")) {
                String orderNumber = reference.getChildText("documentNumber");
                String code = reference.getChildText("code");
                String description = reference.getChildText("description");
                if (description.isEmpty()) {
                    switch (code) {
                        case "1251":
                            description = "Сообщение прочитано получателем";
                            break;
                        case "1252":
                            description = "Сообщение принято учётной системой получателя";
                            break;
                        default:
                            description = null;
                            break;
                    }
                }
                return new DocumentData(documentNumber, Collections.singletonList(Arrays.asList((Object) documentNumber, dateTime, code, description, orderNumber)));
            } else
                ServerLoggers.importLogger.error(String.format("%s Parse Order Message %s error: incorrect documentType %s", provider, documentId, documentType));
        } else
            ServerLoggers.importLogger.error(String.format("%s Parse Order Message %s error: no reference tag", provider, documentId));
        return new DocumentData(documentNumber, null);
    }

    private String importOrderMessages(ExecutionContext context, DocumentData data) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String message = null;
        if (data != null && !data.data.isEmpty()) {
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

            ImportTable table = new ImportTable(fields, data.data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("EDI_OM");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                message = session.applyMessage(context);
                session.popVolatileStats();
            } catch (Exception e) {
                ServerLoggers.importLogger.error("ImportOrderMessages Error: ", e);
                message = e.getMessage();
            }
        }
        return message;
    }

    private DocumentData parseOrderResponse(ExecutionContext context, String url, String login, String password, String host, Integer port, String provider, String documentId, String orderResponse, boolean sendReplies) throws IOException, JDOMException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        List<List<Object>> result = new ArrayList<>();
        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(new ByteArrayInputStream(orderResponse.getBytes("utf-8")));
        Element rootNode = document.getRootElement();

        String documentNumber = rootNode.getChildText("documentNumber");
        Timestamp dateTime = parseTimestamp(rootNode.getChildText("documentDate"));
        String responseType = rootNode.getChildText("function");
        String responseTypeObject = getResponseType(responseType);
        String buyerGLN = rootNode.getChildText("buyerGLN");
        String destinationGLN = rootNode.getChildText("destinationGLN");
        String supplierGLN = rootNode.getChildText("supplierGLN");
        String orderNumber = rootNode.getChildText("orderNumber");
        Timestamp deliveryDateTimeSecond = parseTimestamp(rootNode.getChildText("deliveryDateTimeSecond"));
        String note = rootNode.getChildText("comment");

        Map<String, String> orderBarcodesMap = getOrderBarcodesMap(context, url, login, password, host, port, provider, documentId, documentNumber, orderNumber, sendReplies);

        int i = 1;
        for (Object line : rootNode.getChildren("line")) {
            Element lineElement = (Element) line;
            String GTIN = lineElement.getChildText("GTIN");
            String barcode = null;
            if(orderBarcodesMap.containsKey(GTIN)) {
                barcode = orderBarcodesMap.get(GTIN);
                GTIN = null;
            }
            String id = documentNumber + "/" + i++;
            String action = lineElement.getChildText("action");
            String actionObject = getAction(action);
            BigDecimal quantityOrdered = parseBigDecimal(lineElement.getChildText("quantityOrdered"));
            BigDecimal quantityAccepted = parseBigDecimal(lineElement.getChildText("quantityAccepted"));
            BigDecimal price = parseBigDecimal(lineElement.getChildText("priceElement"));
            BigDecimal sumNoNDS = parseBigDecimal(lineElement.getChildText("priceNoNDS"));
            BigDecimal sumNDS = parseBigDecimal(lineElement.getChildText("priceNDS"));

            result.add(Arrays.<Object>asList(documentNumber, dateTime, responseTypeObject, note, supplierGLN, buyerGLN, destinationGLN, orderNumber,
                    deliveryDateTimeSecond, id, barcode, GTIN, actionObject, quantityOrdered, quantityAccepted, price, sumNoNDS, sumNDS));
        }
        return new DocumentData(documentNumber, result);
    }

    private String getResponseType(String id) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String value = id == null ? null : id.equals("4") ? "changed" : id.equals("27") ? "cancelled" : id.equals("29") ? "accepted" : null;
        return value == null ? null : ("EDI_EOrderResponseType." + value.toLowerCase());
    }

    private String getAction(String id) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String value = id == null ? null : id.equals("1") ? "added" : id.equals("3") ? "changed" : id.equals("5") ? "accepted" : id.equals("7") ? "cancelled" : null;
        return value == null ? null : ("EDI_EOrderResponseDetailAction." + value.toLowerCase());
    }

    private String importOrderResponses(ExecutionContext context, DocumentData data) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String message = null;
        if (data != null && !data.data.isEmpty()) {
            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField numberEOrderResponseField = new ImportField(findProperty("number[EOrderResponse]"));
            ImportKey<?> eOrderResponseKey = new ImportKey((CustomClass) findClass("EOrderResponse"),
                    findProperty("eOrderResponse[VARSTRING[24]]").getMapping(numberEOrderResponseField));
            keys.add(eOrderResponseKey);
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
                    findProperty("legalEntityGLN[VARSTRING[13]]").getMapping(GLNSupplierEOrderResponseField));
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
                    findProperty("stockGLN[VARSTRING[13]]").getMapping(GLNCustomerStockEOrderResponseField));
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

            ImportField idEOrderResponseDetailField = new ImportField(findProperty("id[EOrderResponseDetail]"));
            ImportKey<?> eOrderResponseDetailKey = new ImportKey((CustomClass) findClass("EOrderResponseDetail"),
                    findProperty("eOrderResponseDetail[VARSTRING[100]]").getMapping(idEOrderResponseDetailField));
            keys.add(eOrderResponseDetailKey);
            props.add(new ImportProperty(numberEOrderField, findProperty("orderResponse[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey),
                    object(findClass("EOrderResponse")).getMapping(eOrderResponseKey)));
            props.add(new ImportProperty(idEOrderResponseDetailField, findProperty("id[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(idEOrderResponseDetailField);

            ImportField barcodeEOrderResponseDetailField = new ImportField(findProperty("id[Barcode]"));
            ImportKey<?> skuBarcodeKey = new ImportKey((CustomClass) findClass("Sku"),
                    findProperty("skuBarcode[VARSTRING[15]]").getMapping(barcodeEOrderResponseDetailField));
            skuBarcodeKey.skipKey = true;
            keys.add(skuBarcodeKey);
            props.add(new ImportProperty(barcodeEOrderResponseDetailField, findProperty("sku[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey),
                    object(findClass("Sku")).getMapping(skuBarcodeKey)));
            fields.add(barcodeEOrderResponseDetailField);

            ImportField GTINEOrderResponseDetailField = new ImportField(findProperty("id[Barcode]"));
            ImportKey<?> skuGTINKey = new ImportKey((CustomClass) findClass("Sku"),
                    findProperty("skuGTIN[VARSTRING[15]]").getMapping(GTINEOrderResponseDetailField));
            skuGTINKey.skipKey = true;
            keys.add(skuGTINKey);
            props.add(new ImportProperty(GTINEOrderResponseDetailField, findProperty("sku[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey),
                    object(findClass("Sku")).getMapping(skuGTINKey)));
            fields.add(GTINEOrderResponseDetailField);

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

            ImportTable table = new ImportTable(fields, data.data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("EDI_OR");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                message = session.applyMessage(context);
                session.popVolatileStats();
            } catch (Exception e) {
                ServerLoggers.importLogger.error("ImportOrderResponses Error: ", e);
                message = e.getMessage();
            }
        }
        return message;
    }

    private DocumentData parseDespatchAdvice(ExecutionContext context, String url, String login, String password, String host, Integer port, String provider, String documentId, String orderResponse, boolean sendReplies) throws IOException, JDOMException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        List<List<Object>> result = new ArrayList<>();
        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(new ByteArrayInputStream(orderResponse.getBytes("utf-8")));
        Element rootNode = document.getRootElement();

        String documentNumber = rootNode.getChildText("documentNumber");
        Timestamp dateTime = parseTimestamp(rootNode.getChildText("documentDate"));
        String deliveryNoteNumber = rootNode.getChildText("deliveryNoteNumber");
        Timestamp deliveryNoteDateTime = parseTimestamp(rootNode.getChildText("deliveryNoteDate"));
        String buyerGLN = rootNode.getChildText("buyerGLN");
        String destinationGLN = rootNode.getChildText("destinationGLN");
        String supplierGLN = rootNode.getChildText("supplierGLN");
        String orderNumber = rootNode.getChildText("orderNumber");
        Timestamp deliveryDateTimeFirst = parseTimestamp(rootNode.getChildText("deliveryDateTimeFirst"));
        String note = nullIfEmpty(rootNode.getChildText("comment"));

        Map<String, String> orderBarcodesMap = getOrderBarcodesMap(context, url, login, password, host, port, provider, documentId, documentNumber, orderNumber, sendReplies);

        int i = 1;
        for (Object line : rootNode.getChildren("line")) {
            Element lineElement = (Element) line;
            String GTIN = lineElement.getChildText("GTIN");
            String barcode = null;
            if(orderBarcodesMap.containsKey(GTIN)) {
                barcode = orderBarcodesMap.get(GTIN);
                GTIN = null;
            }

            String id = documentNumber + "/" + i++;
            BigDecimal quantityOrdered = parseBigDecimal(lineElement.getChildText("quantityOrdered"));
            BigDecimal quantityDespatch = parseBigDecimal(lineElement.getChildText("quantityDespatch"));
            BigDecimal valueVAT = parseBigDecimal(lineElement.getChildText("vat"));
            BigDecimal lineItemPrice = parseBigDecimal(lineElement.getChildText("lineItemPrice"));
            BigDecimal lineItemAmountWithoutCharges = parseBigDecimal(lineElement.getChildText("lineItemAmountWithoutCharges"));
            BigDecimal lineItemAmount = parseBigDecimal(lineElement.getChildText("lineItemAmount"));
            BigDecimal lineItemAmountCharges = parseBigDecimal(lineElement.getChildText("lineItemAmountCharges"));
            result.add(Arrays.<Object>asList(documentNumber, dateTime, deliveryNoteNumber, deliveryNoteDateTime, note, supplierGLN, buyerGLN, destinationGLN, orderNumber,
                    deliveryDateTimeFirst, id, barcode, GTIN, quantityOrdered, quantityDespatch, valueVAT, lineItemPrice, lineItemAmountWithoutCharges,
                    lineItemAmount, lineItemAmountCharges));
        }
        return new DocumentData(documentNumber, result);
    }

    private String importDespatchAdvices(ExecutionContext context, DocumentData data) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String message = null;
        if (data != null && !data.data.isEmpty()) {
            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField numberEOrderDespatchAdviceField = new ImportField(findProperty("number[EOrderDespatchAdvice]"));
            ImportKey<?> eOrderDespatchAdviceKey = new ImportKey((CustomClass) findClass("EOrderDespatchAdvice"),
                    findProperty("eOrderDespatchAdvice[VARSTRING[24]]").getMapping(numberEOrderDespatchAdviceField));
            keys.add(eOrderDespatchAdviceKey);
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
                    findProperty("legalEntityGLN[VARSTRING[13]]").getMapping(GLNSupplierEOrderDespatchAdviceField));
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
                    findProperty("stockGLN[VARSTRING[13]]").getMapping(GLNCustomerStockEOrderDespatchAdviceField));
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

            ImportField barcodeEOrderDespatchAdviceDetailField = new ImportField(findProperty("id[Barcode]"));
            ImportKey<?> skuBarcodeKey = new ImportKey((CustomClass) findClass("Sku"),
                    findProperty("skuBarcode[VARSTRING[15]]").getMapping(barcodeEOrderDespatchAdviceDetailField));
            skuBarcodeKey.skipKey = true;
            keys.add(skuBarcodeKey);
            props.add(new ImportProperty(barcodeEOrderDespatchAdviceDetailField, findProperty("sku[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey),
                    object(findClass("Sku")).getMapping(skuBarcodeKey)));
            fields.add(barcodeEOrderDespatchAdviceDetailField);

            ImportField GTINEOrderDespatchAdviceDetailField = new ImportField(findProperty("id[Barcode]"));
            ImportKey<?> skuGTINKey = new ImportKey((CustomClass) findClass("Sku"),
                    findProperty("skuGTIN[VARSTRING[15]]").getMapping(GTINEOrderDespatchAdviceDetailField));
            skuGTINKey.skipKey = true;
            keys.add(skuGTINKey);
            props.add(new ImportProperty(GTINEOrderDespatchAdviceDetailField, findProperty("sku[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey),
                    object(findClass("Sku")).getMapping(skuGTINKey)));
            fields.add(GTINEOrderDespatchAdviceDetailField);

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

            ImportTable table = new ImportTable(fields, data.data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("EDI_DA");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                message = session.applyMessage(context);
                session.popVolatileStats();
            } catch (Exception e) {
                ServerLoggers.importLogger.error("ImportDespatchAdvice Error: ", e);
                message = e.getMessage();
            }
        }
        return message;
    }

    private void confirmDocumentReceived(ExecutionContext context, String documentId, String url, String login, String password,
                                          String host, Integer port, String provider) throws IOException, JDOMException {

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

        String xml = new XMLOutputter().outputString(doc);
        HttpResponse httpResponse = sendRequest(host, port, login, password, url, xml, null);
        ServerLoggers.importLogger.info(String.format("%s ConfirmDocumentReceived document %s: request sent", provider, documentId));
        RequestResult requestResult = getRequestResult(httpResponse, getResponseMessage(httpResponse), "ConfirmDocumentReceived");
        switch (requestResult) {
            case OK:
                ServerLoggers.importLogger.info(String.format("%s ConfirmDocumentReceived document %s: request succeeded", provider, documentId));
                break;
            case AUTHORISATION_ERROR:
                ServerLoggers.importLogger.error(String.format("%s ConfirmDocumentReceived document %s: invalid login-password", provider, documentId));
                context.delayUserInteraction(new MessageClientAction(String.format("%s Документ %s не помечен как обработанный: ошибка авторизации", provider, documentId), "Экспорт"));
                break;
            case UNKNOWN_ERROR:
                ServerLoggers.importLogger.error(String.format("%s ConfirmDocumentReceived document %s: unknown error", provider, documentId));
                context.delayUserInteraction(new MessageClientAction(String.format("%s Документ %s не помечен как обработанный", provider, documentId), "Экспорт"));
        }
    }

    protected boolean sendRecipientError(ExecutionContext context, String url, String login, String password, String host, Integer port, String provider, String documentId, String documentNumber, String error) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, IOException, JDOMException {
        boolean succeeded = false;
        String currentDate = formatDate(new Timestamp(Calendar.getInstance().getTime().getTime()));
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
        addStringElement(topNamespace, sendDocumentElement, "documentNumber", "error_" + documentId);

        addStringElement(topNamespace, sendDocumentElement, "documentType", "SYSTEMMESSAGE");
        addStringElement(topNamespace, sendDocumentElement, "content", contentSubXML);

        String xml = new XMLOutputter().outputString(doc);
        HttpResponse httpResponse = sendRequest(host, port, login, password, url, xml, null);
        ServerLoggers.importLogger.info(String.format("%s RecipientError %s request sent", provider, documentId));
        RequestResult requestResult = getRequestResult(httpResponse, getResponseMessage(httpResponse), "SendDocument");
        switch (requestResult) {
            case OK:
                succeeded = true;
                break;
            case AUTHORISATION_ERROR:
                ServerLoggers.importLogger.error(String.format("%s RecipientError %s: invalid login-password", provider, documentId));
                context.delayUserInteraction(new MessageClientAction(String.format("%s Сообщение об ошибке %s не выгружено: ошибка авторизации", provider, documentId), "Экспорт"));
                break;
            case UNKNOWN_ERROR:
                ServerLoggers.importLogger.error(String.format("%s RecipientError %s: unknown error", provider, documentId));
                context.delayUserInteraction(new MessageClientAction(String.format("%s Сообщение об ошибке %s не выгружено: неизвестная ошибка", provider, documentId), "Экспорт"));
        }
        return succeeded;
    }

    private String getErrorSubXML(String documentId, String documentNumber, String error) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        Element rootElement = new Element("SYSTEMMESSAGE");
        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        addStringElement(rootElement, "documentNumber", "error_" + documentId);

        Element referenceElement = new Element("Reference");
        addStringElement(referenceElement, "documentNumber", documentNumber);
        addStringElement(referenceElement, "code", "1450");
        addStringElement(referenceElement, "description", error);
        rootElement.addContent(referenceElement);

        String xml = new XMLOutputter().outputString(doc);
        return new String(org.apache.commons.codec.binary.Base64.encodeBase64(xml.getBytes()));
    }

    private Map<String, String> getOrderBarcodesMap(ExecutionContext context, String url, String login, String password, String host, Integer port,
                                                    String provider, String documentId, String documentNumber, String orderNumber, boolean sendReplies)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, IOException, JDOMException {
        Map<String, String> orderBarcodesMap = new HashMap<>();
        if (orderNumber != null) {
            if (findProperty("numberOrder[EOrderDetail]").read(context, new DataObject(orderNumber)) == null && sendReplies) {
                sendRecipientError(context, url, login, password, host, port, provider, documentId, documentNumber, String.format("Заказ %s не найден)", orderNumber));
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
        try {
            return new Timestamp(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(value).getTime());
        } catch (Exception e) {
            return null;
        }
    }

    private class DocumentData {
        String documentNumber;
        List<List<Object>> data;

        public DocumentData(String documentNumber, List<List<Object>> data) {
            this.documentNumber = documentNumber;
            this.data = data;
        }
    }
}
