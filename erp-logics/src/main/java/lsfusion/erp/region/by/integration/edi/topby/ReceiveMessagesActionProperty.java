package lsfusion.erp.region.by.integration.edi.topby;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.http.HttpResponse;
import org.apache.xmlbeans.impl.util.Base64;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReceiveMessagesActionProperty extends EDIActionProperty {

    public ReceiveMessagesActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            String loginTopBy = (String) findProperty("loginTopBy[]").read(context); //9999564564541
            String passwordTopBy = (String) findProperty("passwordTopBy[]").read(context); //9u06Av
            String hostTopBy = (String) findProperty("hostTopBy[]").read(context); //topby.by
            Integer portTopBy = (Integer) findProperty("portTopBy[]").read(context); //2011
            if (loginTopBy != null && passwordTopBy != null && hostTopBy != null && portTopBy != null) {

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

                addStringElement(topNamespace, sendDocumentElement, "username", loginTopBy);
                addStringElement(topNamespace, sendDocumentElement, "password", passwordTopBy);

                String url = String.format("http://%s:%s/DmcService", hostTopBy, portTopBy);

                String xml = new XMLOutputter().outputString(doc);
                HttpResponse httpResponse = sendRequest(hostTopBy, portTopBy, loginTopBy, passwordTopBy, url, xml, null);
                ServerLoggers.importLogger.info("ReceiveMessages %s request sent");
                String responseMessage = getResponseMessage(httpResponse);
                RequestResult requestResult = getRequestResult(httpResponse, responseMessage, "ReceiveMessages");
                switch (requestResult) {
                    case OK:
                        importMessages(context, responseMessage);
                        break;
                    case AUTHORISATION_ERROR:
                        ServerLoggers.importLogger.error("ReceiveMessages %s: invalid login-password");
                        context.delayUserInteraction(new MessageClientAction("Заказ %s не выгружен: ошибка авторизации", "Экспорт"));
                        break;
                    case UNKNOWN_ERROR:
                        ServerLoggers.importLogger.error("ReceiveMessages %s: unknown error");
                        context.delayUserInteraction(new MessageClientAction("Заказ %s не выгружен: неизвестная ошибка", "Экспорт"));
                }
            } else {
                ServerLoggers.importLogger.info("ReceiveMessages: не заданы имя пользователя / пароль / хост / порт");
                context.delayUserInteraction(new MessageClientAction("Заказ не выгружен: не заданы имя пользователя / пароль / хост / порт", "Экспорт"));
            }

        } catch (ScriptingErrorLog.SemanticErrorException | IOException | JDOMException e) {
            Throwables.propagate(e);
        }
    }

    private void importMessages(ExecutionContext context, String responseMessage) throws JDOMException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<List<Object>> dataMessage = new ArrayList<>();
        List<List<Object>> dataOrderResponse = new ArrayList<>();
        List<List<Object>> dataDespatchAdvice = new ArrayList<>();

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
                                //String id = documentData.getChildText("Id");
                                //String documentDate = documentData.getChildText("DocumentDate");
                                //String modifiedDate = documentData.getChildText("ModifiedDate");
                                //String documentNumber = documentData.getChildText("DocumentNumber");
                                //String filename = documentData.getChildText("Filename");
                                String documentType = documentData.getChildText("DocumentType", topNamespace);
                                //String readOnWeb = documentData.getChildText("ReadOnWeb");
                                //String gotByAgent = documentData.getChildText("GotByAgent");
                                //String approvalStatus = documentData.getChildText("ApprovalStatus");
                                //String processingStatus = documentData.getChildText("ProcessingStatus");

                                switch (documentType) {
                                    case "systemMessage":
                                        List<Object> orderMessage = parseOrderMessage(subXML);
                                        if (orderMessage != null)
                                            dataMessage.add(orderMessage);
                                        break;
                                    case "ordrsp": {
                                        List<List<Object>> orderResponseMessage = parseOrderResponse(context, subXML);
                                        if (orderResponseMessage != null)
                                            dataOrderResponse.addAll(orderResponseMessage);
                                        break;
                                    }
//                                    case "desadv": {
//                                        List<Object> despatchAdviceMessage = parseDespatchAdviceMessage(subXML);
//                                        if (despatchAdviceMessage != null)
//                                            dataDespatchAdvice.add(despatchAdviceMessage);
//                                        break;
//                                    }
                                }

                            }
                        }
                    }
                }
            }
        }

        importOrderMessages(context, dataMessage);
        importOrderResponses(context, dataOrderResponse);
    }

    private List<Object> parseOrderMessage(String message) throws IOException, JDOMException {
        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(new ByteArrayInputStream(message.getBytes("utf-8")));
        Element rootNode = document.getRootElement();

        String number = rootNode.getChildText("documentNumber");
        Timestamp dateTime = parseTimestamp(rootNode.getChildText("documentDate"), "yyyy-MM-dd'T'HH:mm:ss");

        Element reference = rootNode.getChild("reference");
        String documentType = reference.getChildText("documentType");
        if (documentType.equals("ORDERS")) {
            String orderNumber = reference.getChildText("documentNumber");
            String code = reference.getChildText("code");
            String description = reference.getChildText("description");
            if (description.isEmpty()) {
                if (code.equals("1251")) {
                    description = "Сообщение прочитано получателем";
                } else if (code.equals("1252")) {
                    description = "Сообщение принято учётной системой получателя";
                }
            }
            return Arrays.asList((Object) number, dateTime, code, description, orderNumber);
        } else return null;
    }

    private boolean importOrderMessages(ExecutionContext context, List<List<Object>> data) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        boolean result = true;
        if (!data.isEmpty()) {
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

            ImportTable table = new ImportTable(fields, data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("EDI_OM");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                String message = session.applyMessage(context);
                session.popVolatileStats();

                if (message == null) {
                    ServerLoggers.importLogger.info(String.format("Import %s EOrderMessages succeeded", data.size()));
                    context.delayUserInteraction(new MessageClientAction(String.format("Загружено сообщений: %s", data.size()), "Импорт"));
                } else {
                    ServerLoggers.importLogger.info("Import EOrderMessages error: " + message);
                    context.delayUserInteraction(new MessageClientAction(message, "Ошибка"));
                    result = false;
                }
            }
        }
        return result;
    }

    private List<List<Object>> parseOrderResponse(ExecutionContext context, String orderResponse) throws IOException, JDOMException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        List<List<Object>> result = new ArrayList<>();
        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(new ByteArrayInputStream(orderResponse.getBytes("utf-8")));
        Element rootNode = document.getRootElement();

        String number = rootNode.getChildText("documentNumber");
        Timestamp dateTime = parseTimestamp(rootNode.getChildText("documentDate"), "yyyy-MM-dd'T'HH:mm:ss");
        String responseType = rootNode.getChildText("function");
        String responseTypeObject = getResponseType(context, responseType);
        String buyerGLN = rootNode.getChildText("buyerGLN");
        //String buyerName = rootNode.getChildText("buyerName");
        String destinationGLN = rootNode.getChildText("destinationGLN");
        //String destinationName = rootNode.getChildText("destinationName");
        String supplierGLN = rootNode.getChildText("supplierGLN");
        //String supplierName = rootNode.getChildText("supplierName");
        String orderNumber = rootNode.getChildText("orderNumber");
        //String orderDate = rootNode.getChildText("orderDate");
        Timestamp deliveryDateTimeSecond = parseTimestamp(rootNode.getChildText("deliveryDateTimeSecond"), "yyyy-MM-dd'T'HH:mm:ss");

        int i = 1;
        for (Object line : rootNode.getChildren("line")) {
            Element lineElement = (Element) line;
            String barcode = lineElement.getChildText("GTIN");
            String id = number + "/" + i++;
            String action = lineElement.getChildText("action");
            String actionObject = getAction(context, action);
            //String fullName = lineElement.getChildText("fullName");
            BigDecimal quantityOrdered = parseBigDecimal(lineElement.getChildText("quantityOrdered"));
            BigDecimal quantityAccepted = parseBigDecimal(lineElement.getChildText("quantityAccepted"));
            BigDecimal priceNoNDS = parseBigDecimal(lineElement.getChildText("priceNoNDS"));
            BigDecimal priceNDS = parseBigDecimal(lineElement.getChildText("priceNDS"));

            result.add(Arrays.<Object>asList(number, dateTime, responseTypeObject, supplierGLN, buyerGLN, destinationGLN, orderNumber,
                    deliveryDateTimeSecond, id, barcode, actionObject, quantityOrdered, quantityAccepted, priceNoNDS, priceNDS));
        }
        return result;
    }

    private String getResponseType(ExecutionContext context, String id) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String value = null;
        if (id != null) {
            switch (id) {
                case "4":
                    value = "changed";
                    break;
                case "27":
                    value = "cancelled";
                    break;
                case "29":
                    value = "accepted";
                    break;
            }
        }
        return value == null ? null : ("TopBy_EOrderResponseType." + value.toLowerCase());
    }

    private String getAction(ExecutionContext context, String id) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String value = null;
        if (id != null) {
            switch (id) {
                case "1":
                    value = "added";
                    break;
                case "3":
                    value = "changed";
                    break;
                case "5":
                    value = "accepted";
                    break;
                case "7":
                    value = "cancelled";
                    break;
            }
        }
        return value == null ? null : ("TopBy_EOrderResponseDetailAction." + value.toLowerCase());
    }

    private boolean importOrderResponses(ExecutionContext context, List<List<Object>> data) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        boolean result = true;
        if (!data.isEmpty()) {
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
            ImportKey<?> skuKey = new ImportKey((CustomClass) findClass("Sku"),
                    findProperty("skuBarcode[VARSTRING[15]]").getMapping(barcodeEOrderResponseDetailField));
            skuKey.skipKey = true;
            keys.add(skuKey);
            props.add(new ImportProperty(barcodeEOrderResponseDetailField, findProperty("sku[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey),
                    object(findClass("Sku")).getMapping(skuKey)));
            fields.add(barcodeEOrderResponseDetailField);

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

            ImportField priceNoNDSEOrderResponseDetailField = new ImportField(findProperty("priceNoNDS[EOrderResponseDetail]"));
            props.add(new ImportProperty(priceNoNDSEOrderResponseDetailField, findProperty("priceNoNDS[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(priceNoNDSEOrderResponseDetailField);

            ImportField priceNDSEOrderResponseDetailField = new ImportField(findProperty("priceNDS[EOrderResponseDetail]"));
            props.add(new ImportProperty(priceNDSEOrderResponseDetailField, findProperty("priceNDS[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(priceNDSEOrderResponseDetailField);

            ImportTable table = new ImportTable(fields, data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("EDI_OR");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                String message = session.applyMessage(context);
                session.popVolatileStats();

                if (message == null) {
                    ServerLoggers.importLogger.info(String.format("Import %s EOrderResponses succeeded", data.size()));
                    context.delayUserInteraction(new MessageClientAction(String.format("Загружено ответов по заказам: %s", data.size()), "Импорт"));
                } else {
                    ServerLoggers.importLogger.info("Import EOrderResponses error: " + message);
                    context.delayUserInteraction(new MessageClientAction(message, "Ошибка"));
                    result = false;
                }
            }
        }
        return result;
    }

    private Timestamp parseTimestamp(String value, String pattern) {
        try {
            return new Timestamp(new SimpleDateFormat(pattern).parse(value).getTime());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        return value == null || value.isEmpty() ? null : new BigDecimal(value);
    }
}
