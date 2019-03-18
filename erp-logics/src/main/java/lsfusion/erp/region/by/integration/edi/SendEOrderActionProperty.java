package lsfusion.erp.region.by.integration.edi;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.form.property.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.builder.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.property.Property;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.XMLOutputter;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Iterator;

public class SendEOrderActionProperty extends EDIActionProperty {
    protected final ClassPropertyInterface eOrderInterface;

    public SendEOrderActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        eOrderInterface = i.next();
    }

    protected void sendEOrder(ExecutionContext context, String url, String login, String password, String host, Integer port, String outputDir, String provider) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, IOException, JDOMException {
        if(context.getDbManager().isServer()) {
            DataObject eOrderObject = context.getDataKeyValue(eOrderInterface);

            Timestamp documentDateValue = (Timestamp) findProperty("sendDateTime[EOrder]").read(context, eOrderObject);
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 1);
            String documentDate = formatTimestamp(documentDateValue);
            Timestamp deliveryDateValue = (Timestamp) findProperty("shipmentDateTime[EOrder]").read(context, eOrderObject);
            Timestamp currentDateValue = new Timestamp(cal.getTime().getTime());
            String deliveryDate = formatTimestamp(deliveryDateValue != null ? (deliveryDateValue.getTime() > currentDateValue.getTime() ? deliveryDateValue : currentDateValue) : currentDateValue);
            String documentNumber = (String) findProperty("number[EOrder]").read(context, eOrderObject);

            String error = "";

            String GLNSupplierStock = (String) findProperty("GLNSupplierStock[EOrder]").read(context, eOrderObject);
            if (GLNSupplierStock == null)
                error += String.format("EOrder %s: Не задан GLN склада поставщика\n", documentNumber);
            String nameSupplier = (String) findProperty("nameSupplier[EOrder]").read(context, eOrderObject);
            String GLNCustomer = (String) findProperty("GLNCustomer[EOrder]").read(context, eOrderObject);
            if (GLNCustomer == null)
                error += String.format("EOrder %s: Не задан GLN покупателя\n", documentNumber);
            String nameCustomer = (String) findProperty("nameCustomer[EOrder]").read(context, eOrderObject);
            String GLNCustomerStock = (String) findProperty("GLNCustomerStock[EOrder]").read(context, eOrderObject);
            if (GLNCustomerStock == null)
                error += String.format("EOrder %s: Не задан GLN склада покупателя", documentNumber);
            String nameCustomerStock = (String) findProperty("nameCustomerStock[EOrder]").read(context, eOrderObject);
            String note = (String) findProperty("note[EOrder]").read(context, eOrderObject);
            boolean isCancel = findProperty("isCancel[EOrder]").read(context, eOrderObject) != null;

            if (error.isEmpty()) {
                String contentSubXML = readContentSubXML(context, eOrderObject, documentNumber, documentDate, deliveryDate,
                        GLNSupplierStock, nameSupplier, nameCustomer, GLNCustomer, GLNCustomerStock, nameCustomerStock, note,
                        isCancel, outputDir);

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
                addStringElement(topNamespace, sendDocumentElement, "filename", "order" + documentNumber);
                addStringElement(topNamespace, sendDocumentElement, "documentDate", documentDate);
                addStringElement(topNamespace, sendDocumentElement, "documentNumber", documentNumber);
                addStringElement(topNamespace, sendDocumentElement, "senderCode", GLNCustomer);
                addStringElement(topNamespace, sendDocumentElement, "receiverCode", GLNCustomer);
                addStringElement(topNamespace, sendDocumentElement, "deliveryPointCode", GLNCustomerStock);

                addStringElement(topNamespace, sendDocumentElement, "documentType", "ORDERS");
                addStringElement(topNamespace, sendDocumentElement, "content", contentSubXML);

                String xml = new XMLOutputter().outputString(doc);
                HttpResponse httpResponse = sendRequest(host, port, login, password, url, xml, null);
                ServerLoggers.importLogger.info(String.format("%s SendEOrder %s request sent", provider, documentNumber));
                RequestResult requestResult = getRequestResult(httpResponse, getResponseMessage(httpResponse), "SendDocument");
                switch (requestResult) {
                    case OK:
                        if(isCancel) {
                            findProperty("exportedCanceled[EOrder]").change(true, context, eOrderObject);
                        } else {
                            findProperty("exported[EOrder]").change(true, context, eOrderObject);
                        }

                        ServerLoggers.importLogger.info(String.format("%s SendEOrder %s request succeeded", provider, documentNumber));
                        context.delayUserInteraction(new MessageClientAction(String.format("%s Заказ %s выгружен", provider, documentNumber), "Экспорт"));
                        break;
                    case AUTHORISATION_ERROR:
                        ServerLoggers.importLogger.error(String.format("%s SendEOrder %s: invalid login-password", provider, documentNumber));
                        context.delayUserInteraction(new MessageClientAction(String.format("%s Заказ %s не выгружен: ошибка авторизации", provider, documentNumber), "Экспорт"));
                        break;
                    case UNKNOWN_ERROR:
                        ServerLoggers.importLogger.error(String.format("%s SendEOrder %s: unknown error", provider, documentNumber));
                        context.delayUserInteraction(new MessageClientAction(String.format("%s Заказ %s не выгружен: неизвестная ошибка", provider, documentNumber), "Экспорт"));
                }
            } else {
                ServerLoggers.importLogger.info(provider + " SendEOrder: Не все поля заполнены. " + error);
                context.delayUserInterfaction(new MessageClientAction(error, provider + " Заказ не выгружен: Не все поля заполнены"));
            }
        } else {
            context.delayUserInteraction(new MessageClientAction(provider + " SendEOrder disabled, change serverComputer() to enable", "Экспорт"));
        }
    }

    private String readContentSubXML(ExecutionContext context, DataObject eOrderObject, String documentNumber, String documentDate,
                                     String deliveryDate, String GLNSupplierStock, String nameSupplier, String nameCustomer,
                                     String GLNCustomer, String GLNCustomerStock, String nameCustomerStock, String note, boolean isCancel,
                                     String outputDir)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        Element rootElement = new Element("ORDERS");
        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        addStringElement(rootElement, "documentNumber", documentNumber);
        addStringElement(rootElement, "documentDate", documentDate);
        addStringElement(rootElement, "documentType", isCancel ? "1" : "9"); //9 =  Оригинал, 1 = Отмена
        addStringElement(rootElement, "buyerGLN", GLNCustomer);
        addStringElement(rootElement, "buyerName", nameCustomer);
        addStringElement(rootElement, "destinationGLN", GLNCustomerStock);
        addStringElement(rootElement, "destinationName", nameCustomerStock);
        addStringElement(rootElement, "supplierGLN", GLNSupplierStock);
        addStringElement(rootElement, "supplierName", nameSupplier);
        addStringElement(rootElement, "deliveryDateTimeFirst", deliveryDate);

        KeyExpr eOrderDetailExpr = new KeyExpr("eOrderDetail");
        ImRevMap<Object, KeyExpr> eOrderDetailKeys = MapFact.singletonRev((Object) "eOrderDetail", eOrderDetailExpr);

        QueryBuilder<Object, Object> eOrderDetailQuery = new QueryBuilder<>(eOrderDetailKeys);

        String[] eOrderDetailNames = new String[]{"GTINBarcode", "idSku", "nameSku", "extraCodeUOMSku", "quantity", "price", "valueVAT"};
        LP<?>[] eOrderDetailProperties = findProperties("GTINBarcode[EOrderDetail]", "idSku[EOrderDetail]", "nameSku[EOrderDetail]",
                "extraCodeUOMSku[EOrderDetail]", "overQuantity[EOrderDetail]", "price[EOrderDetail]", "valueVAT[EOrderDetail]");
        for (int j = 0; j < eOrderDetailProperties.length; j++) {
            eOrderDetailQuery.addProperty(eOrderDetailNames[j], eOrderDetailProperties[j].getExpr(context.getModifier(), eOrderDetailExpr));
        }
        eOrderDetailQuery.and(findProperty("GTINBarcode[EOrderDetail]").getExpr(context.getModifier(), eOrderDetailExpr).getWhere());
        eOrderDetailQuery.and(findProperty("order[EOrderDetail]").getExpr(context.getModifier(), eOrderDetailExpr).compare(eOrderObject.getExpr(), Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> eOrderDetailResult = eOrderDetailQuery.execute(context);

        for (int i = 0, size = eOrderDetailResult.size(); i < size; i++) {
            ImMap<Object, Object> entry = eOrderDetailResult.getValue(i);

            BigDecimal quantity = (BigDecimal) entry.get("quantity");
            if(quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {

                String barcode = trim((String) entry.get("GTINBarcode"));
                String idSku = (String) entry.get("idSku");
                String nameSku = (String) entry.get("nameSku");
                String extraCodeUOMSku = trim((String) entry.get("extraCodeUOMSku"));

                BigDecimal price = (BigDecimal) entry.get("price");
                BigDecimal valueVAT = (BigDecimal) entry.get("valueVAT");

                Element lineElement = new Element("line");
                rootElement.addContent(lineElement);

                addStringElement(lineElement, "GTIN", barcode);
                addStringElement(lineElement, "IDBuyer", idSku);
                addStringElement(lineElement, "fullName", nameSku);
                addBigDecimalElement(lineElement, "quantityOrdered", quantity);
                addStringElement(lineElement, "measurement", extraCodeUOMSku);
                addStringElement(lineElement, "priceElement", toStr(price, 2));
                addStringElement(lineElement, "tax", toStr(valueVAT, 2));

            }
        }

        addIntegerElement(rootElement, "lineQuantity", eOrderDetailResult.size());
        addStringElement(rootElement, "comment", note);

        String xml = new XMLOutputter().outputString(doc);
        if (outputDir != null) {
            try {
                FileUtils.writeStringToFile(new File(outputDir + "/" + documentNumber + (isCancel ? "c" : "")), xml);
            } catch (Exception e) {
                ServerLoggers.importLogger.error("Archive file error: ", e);
            }
        }
        return new String(Base64.encodeBase64(xml.getBytes()));
    }

    private String toStr(BigDecimal value, int fractionDigits) {
        String result = null;
        if (value != null) {
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(fractionDigits);
            df.setGroupingUsed(false);
            result = df.format(value).replace(",", ".");
        }
        return result;
    }

    @Override
    public ImMap<Property, Boolean> aspectChangeExtProps() {
        try {
            return getChangeProps(findProperty("exported[EOrder]").property, findProperty("exportedCanceled[EOrder]").property);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            return null;
        }
    }
}