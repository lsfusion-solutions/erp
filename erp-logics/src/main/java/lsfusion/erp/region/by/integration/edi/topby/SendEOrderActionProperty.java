package lsfusion.erp.region.by.integration.edi.topby;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.XMLOutputter;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;

public class SendEOrderActionProperty extends EDIActionProperty {
    private final ClassPropertyInterface eOrderInterface;

    public SendEOrderActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        eOrderInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        DataObject eOrderObject = context.getDataKeyValue(eOrderInterface);

        try {
            String loginTopBy = (String) findProperty("loginTopBy[]").read(context); //9999564564541
            String passwordTopBy = (String) findProperty("passwordTopBy[]").read(context); //9u06Av
            String hostTopBy = (String) findProperty("hostTopBy[]").read(context); //topby.by
            Integer portTopBy = (Integer) findProperty("portTopBy[]").read(context); //2011
            if (loginTopBy != null && passwordTopBy != null && hostTopBy != null && portTopBy != null) {

                Timestamp documentDateValue = (Timestamp) findProperty("dateTime[EOrder]").read(context, eOrderObject);
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, 1);
                String documentDate = formatDate(documentDateValue);
                String deliveryDate = formatDate(new Timestamp(cal.getTime().getTime()));
                String documentNumber = (String) findProperty("number[EOrder]").read(context, eOrderObject);
                String GLNSupplier = (String) findProperty("GLNSupplier[EOrder]").read(context, eOrderObject);
                String nameSupplier = (String) findProperty("nameSupplier[EOrder]").read(context, eOrderObject);
                String GLNCustomer = (String) findProperty("GLNCustomer[EOrder]").read(context, eOrderObject);
                String nameCustomer = (String) findProperty("nameCustomer[EOrder]").read(context, eOrderObject);
                String GLNCustomerStock = (String) findProperty("GLNCustomerStock[EOrder]").read(context, eOrderObject);
                String nameCustomerStock = (String) findProperty("nameCustomerStock[EOrder]").read(context, eOrderObject);

                String contentSubXML = readContentSubXML(context, eOrderObject, documentNumber, documentDate, deliveryDate,
                        GLNSupplier, nameSupplier, GLNCustomer, nameCustomer, GLNCustomerStock, nameCustomerStock);

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

                addStringElement(topNamespace, sendDocumentElement, "username", loginTopBy);
                addStringElement(topNamespace, sendDocumentElement, "password", passwordTopBy);
                addStringElement(topNamespace, sendDocumentElement, "filename", "order" + documentNumber);
                addStringElement(topNamespace, sendDocumentElement, "documentDate", documentDate);
                addStringElement(topNamespace, sendDocumentElement, "documentNumber", documentNumber);
                addStringElement(topNamespace, sendDocumentElement, "senderCode", GLNCustomer);
                addStringElement(topNamespace, sendDocumentElement, "receiverCode", GLNCustomer);
                addStringElement(topNamespace, sendDocumentElement, "deliveryPointCode", GLNCustomerStock);

                addStringElement(topNamespace, sendDocumentElement, "documentType", "ORDERS");
                addStringElement(topNamespace, sendDocumentElement, "content", contentSubXML);

                String url = String.format("http://%s:%s/DmcService", hostTopBy, portTopBy);

                String xml = new XMLOutputter().outputString(doc);
                HttpResponse httpResponse = sendRequest(hostTopBy, portTopBy, loginTopBy, passwordTopBy, url, xml, null);
                ServerLoggers.importLogger.info(String.format("SendEOrder %s request sent", documentNumber));
                RequestResult requestResult = getRequestResult(httpResponse, getResponseMessage(httpResponse), "SendDocument");
                switch (requestResult) {
                    case OK:
                        ServerLoggers.importLogger.info(String.format("SendEOrder %s: request succeeded", documentNumber));
                        context.delayUserInteraction(new MessageClientAction(String.format("Заказ %s успешно выгружен", documentNumber), "Экспорт"));
                        break;
                    case AUTHORISATION_ERROR:
                        ServerLoggers.importLogger.error(String.format("SendEOrder %s: invalid login-password", documentNumber));
                        context.delayUserInteraction(new MessageClientAction(String.format("Заказ %s не выгружен: ошибка авторизации", documentNumber), "Экспорт"));
                        break;
                    case UNKNOWN_ERROR:
                        ServerLoggers.importLogger.error(String.format("SendEOrder %s: unknown error", documentNumber));
                        context.delayUserInteraction(new MessageClientAction(String.format("Заказ %s не выгружен: неизвестная ошибка", documentNumber), "Экспорт"));
                }
            } else {
                ServerLoggers.importLogger.info("SendEOrder: не заданы имя пользователя / пароль / хост / порт");
                context.delayUserInteraction(new MessageClientAction("Заказ не выгружен: не заданы имя пользователя / пароль / хост / порт", "Экспорт"));
            }

        } catch (ScriptingErrorLog.SemanticErrorException | IOException | JDOMException e) {
            Throwables.propagate(e);
        }
    }

    private String readContentSubXML(ExecutionContext context, DataObject eOrderObject, String documentNumber, String documentDate,
                                     String deliveryDate, String GLNSupplier, String nameSupplier, String GLNCustomer, String nameCustomer,
                                     String GLNCustomerStock, String nameCustomerStock) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        Element rootElement = new Element("ORDERS");
        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        addStringElement(rootElement, "documentNumber", documentNumber);
        addStringElement(rootElement, "documentDate", documentDate);
        addStringElement(rootElement, "documentType", "9");
        addStringElement(rootElement, "buyerGLN", GLNSupplier);
        addStringElement(rootElement, "buyerName", nameSupplier);
        addStringElement(rootElement, "destinationGLN", GLNCustomerStock);
        addStringElement(rootElement, "destinationName", nameCustomerStock);
        addStringElement(rootElement, "supplierGLN", GLNCustomer);
        addStringElement(rootElement, "supplierName", nameCustomer);
        addStringElement(rootElement, "deliveryDateTimeFirst", deliveryDate);

        try (DataSession session = context.createSession()) {

            KeyExpr eOrderDetailExpr = new KeyExpr("eOrderDetail");
            ImRevMap<Object, KeyExpr> eOrderDetailKeys = MapFact.singletonRev((Object) "eOrderDetail", eOrderDetailExpr);

            QueryBuilder<Object, Object> eOrderDetailQuery = new QueryBuilder<>(eOrderDetailKeys);

            String[] eOrderDetailNames = new String[]{"idBarcode", "nameSku", "extraCodeUOMSku", "quantity", "price"};
            LCP<?>[] eOrderDetailProperties = findProperties("idBarcode[EOrderDetail]", "nameSku[EOrderDetail]",
                    "extraCodeUOMSku[EOrderDetail]", "quantity[EOrderDetail]", "price[EOrderDetail]");
            for (int j = 0; j < eOrderDetailProperties.length; j++) {
                eOrderDetailQuery.addProperty(eOrderDetailNames[j], eOrderDetailProperties[j].getExpr(eOrderDetailExpr));
            }
            eOrderDetailQuery.and(findProperty("idBarcode[EOrderDetail]").getExpr(eOrderDetailExpr).getWhere());
            eOrderDetailQuery.and(findProperty("order[EOrderDetail]").getExpr(eOrderDetailExpr).compare(eOrderObject.getExpr(), Compare.EQUALS));
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> eOrderDetailResult = eOrderDetailQuery.execute(session);

            for (int i = 0, size = eOrderDetailResult.size(); i < size; i++) {
                ImMap<Object, Object> entry = eOrderDetailResult.getValue(i);

                String barcode = trim((String) entry.get("idBarcode"));
                String nameSku = (String) entry.get("nameSku");
                String extraCodeUOMSku = trim((String) entry.get("extraCodeUOMSku"));
                BigDecimal quantity = (BigDecimal) entry.get("quantity");
                BigDecimal price = (BigDecimal) entry.get("price");

                Element lineElement = new Element("line");
                rootElement.addContent(lineElement);

                addStringElement(lineElement, "GTIN", barcode);
                addStringElement(lineElement, "fullName", nameSku);
                addBigDecimalElement(lineElement, "quantityOrdered", quantity);
                addStringElement(lineElement, "measurement", extraCodeUOMSku);
                addStringElement(lineElement, "priceElement", toStr(price, 2));
            }

            addIntegerElement(rootElement, "lineQuantity", eOrderDetailResult.size());

        }
        String xml = new XMLOutputter().outputString(doc);
        return new String(Base64.encodeBase64(xml.getBytes()));
    }

    private String formatDate(Timestamp date) {
        return date == null ? null : new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(date);
    }

    private String toStr(BigDecimal value, int fractionDigits) {
        String result = null;
        if (value != null) {
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(fractionDigits);
            df.setGroupingUsed(false);
            result = df.format(value);
        }
        return result;
    }
}