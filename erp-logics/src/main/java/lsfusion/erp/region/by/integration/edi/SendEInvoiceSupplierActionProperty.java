package lsfusion.erp.region.by.integration.edi;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImCol;
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
import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringEscapeUtils;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

public class SendEInvoiceSupplierActionProperty extends EDIActionProperty {
    protected final ClassPropertyInterface eInvoiceInterface;

    public SendEInvoiceSupplierActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        eInvoiceInterface = i.next();
    }

    protected void sendEInvoice(ExecutionContext context, String url, String login, String password, String host, Integer port, String provider,
                                String outputDir, String hostEDSService, Integer portEDSService)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if (context.getDbManager().isServer()) {

            outputDir = outputDir == null ? null : (outputDir + "/sent");
            if (hostEDSService != null && portEDSService != null) {


                DataObject eInvoiceObject = context.getDataKeyValue(eInvoiceInterface);

                long currentTime = Calendar.getInstance().getTime().getTime();
                String documentNumber = String.valueOf(currentTime);
                String documentDate = new SimpleDateFormat("yyyyMMddHHmmss").format(currentTime);

                String referenceNumber = trim((String) findProperty("number[EInvoice]").read(context, eInvoiceObject));

                String aliasEDSService = (String) findProperty("aliasEDSServiceSupplier[EInvoice]").read(context, eInvoiceObject);
                String passwordEDSService = (String) findProperty("passwordEDSServiceSupplier[EInvoice]").read(context, eInvoiceObject);

                String glnSupplier = (String) findProperty("glnSupplier[EInvoice]").read(context, eInvoiceObject);
                String glnCustomer = (String) findProperty("glnCustomer[EInvoice]").read(context, eInvoiceObject);
                String glnCustomerStock = (String) findProperty("glnCustomerStock[EInvoice]").read(context, eInvoiceObject);
                boolean isCancel = findProperty("isCancel[EInvoice]").read(context, eInvoiceObject) != null;

                try {
                    //создаём BLRWBL, подписываем и отправляем
                    String blrwbl = createBLRWBL(context, eInvoiceObject, outputDir, documentNumber, documentDate, glnSupplier, glnCustomer, glnCustomerStock, isCancel);
                    String signedBLRWBL = signDocument("BLRWBL", referenceNumber, hostEDSService, portEDSService, blrwbl, aliasEDSService, passwordEDSService, charset);
                    if (signedBLRWBL != null) {
                        sendDocument(context, url, login, password, host, port, provider, referenceNumber, generateXML(login, password, referenceNumber,
                                documentDate, glnSupplier, glnCustomer, glnCustomerStock, new String(Base64.encodeBase64(signedBLRWBL.getBytes())), "BLRWBL"),
                                eInvoiceObject, true, isCancel, 1);
                        try (ExecutionContext.NewSession newContext = context.newSession()) {
                            findProperty("blrwbl[EInvoice]").change(documentNumber, newContext, eInvoiceObject);
                            findProperty("blrwblDate[EInvoice]").change(new Timestamp(currentTime), newContext, eInvoiceObject);
                            newContext.apply();
                        }
                    }
                } catch (Exception e) {
                    ServerLoggers.importLogger.error(String.format("%s SendEInvoice error", provider), e);
                    throw Throwables.propagate(e);
                }

            } else {
                ServerLoggers.importLogger.info(provider + " SendEInvoice: не заданы параметры EDSService");
                context.delayUserInteraction(new MessageClientAction(provider + " Заказ не выгружен: не заданы параметры EDSService", "Экспорт"));
            }

        } else {
            context.delayUserInteraction(new MessageClientAction(provider + " SendEInvoice disabled, change serverComputer() to enable", "Экспорт"));
        }
    }

    @Override
    public ImMap<CalcProperty, Boolean> aspectChangeExtProps() {
        try {
            return getChangeProps(findProperty("exportedSupplier[EInvoice]").property, findProperty("importedCustomer[EInvoice]").property, findProperty("importedSupplier[EInvoice]").property);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            return null;
        }
    }

    protected String createBLRWBL(ExecutionContext context, DataObject eInvoiceObject, String outputDir, String documentNumber,
                                  String documentDate, String glnSupplier, String glnCustomer, String glnCustomerStock, boolean isCancel)
            throws IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        File tmpFile = null;
        try {

            String error = "";

            String deliveryNoteId = (String) findProperty("deliveryNoteNumber[EInvoice]").read(context, eInvoiceObject);
            Timestamp deliveryNoteDateTime = (Timestamp) findProperty("deliveryNoteDateTime[EInvoice]").read(context, eInvoiceObject);
            String contractId = (String) findProperty("contractId[EInvoice]").read(context, eInvoiceObject);
            if(contractId == null)
                error += "Не заполнено поле 'Номер Договора (или другого документа, служащего основанием отпуска)'\n";
            Date contractDate = (Date) findProperty("contractDate[EInvoice]").read(context, eInvoiceObject);
            if(contractDate == null)
                error += "Не заполнено поле 'Дата договора в формате ГГГГММДД (или другого документа, служащего основанием отпуска)'\n";
            String waybillId = (String) findProperty("waybillId[EInvoice]").read(context, eInvoiceObject);
            if(waybillId == null)
                error += "Не заполнено поле 'Номер путевого листа'\n";

            String nameSupplier = (String) findProperty("nameSupplier[EInvoice]").read(context, eInvoiceObject);
            String addressSupplier = (String) findProperty("addressSupplier[EInvoice]").read(context, eInvoiceObject);
            String unpSupplier = (String) findProperty("unpSupplier[EInvoice]").read(context, eInvoiceObject);
            String contactSupplier = (String) findProperty("contactSupplier[EInvoice]").read(context, eInvoiceObject);
            if(contactSupplier == null)
                error += "Не заполнено поле 'Отпуск разрешил (должность и ФИО)'\n";

            String nameCustomer = (String) findProperty("nameCustomer[EInvoice]").read(context, eInvoiceObject);
            String addressCustomer = (String) findProperty("addressCustomer[EInvoice]").read(context, eInvoiceObject);
            String unpCustomer = (String) findProperty("unpCustomer[EInvoice]").read(context, eInvoiceObject);

            String glnFreightPayer = (String) findProperty("glnFreightPayer[EInvoice]").read(context, eInvoiceObject);
            String nameFreightPayer = (String) findProperty("nameFreightPayer[EInvoice]").read(context, eInvoiceObject);
            String addressFreightPayer = (String) findProperty("addressFreightPayer[EInvoice]").read(context, eInvoiceObject);
            String unpFreightPayer = (String) findProperty("unpFreightPayer[EInvoice]").read(context, eInvoiceObject);

            String glnSupplierStock = (String) findProperty("glnSupplierStock[EInvoice]").read(context, eInvoiceObject);
            if(glnSupplierStock == null)
                error += "Не заполнено поле 'GLN пункта погрузки'\n";
            String addressSupplierStock = (String) findProperty("addressSupplierStock[EInvoice]").read(context, eInvoiceObject);
            if(addressSupplierStock == null)
                error += "Не заполнено поле 'Адрес пункта погрузки'\n";
            String contactSupplierStock = (String) findProperty("contactSupplierStock[EInvoice]").read(context, eInvoiceObject);
            if(contactSupplierStock == null)
                error += "Не заполнено поле 'ФИО и должность лица, которое отвечает за передачу груза со стороны грузоотправителя'\n";

            String addressCustomerStock = (String) findProperty("addressCustomerStock[EInvoice]").read(context, eInvoiceObject);
            if(addressCustomerStock == null)
                error += "Не заполнено поле 'Адрес пункта разгрузки (Адрес склада покупателя)' \n";

            String transportContact = (String) findProperty("transportContact[EInvoice]").read(context, eInvoiceObject);
            if(transportContact == null)
                error += "Не заполнено поле 'ФИО водителя'\n";
            String deliveryContact = (String) findProperty("deliveryContact[EInvoice]").read(context, eInvoiceObject);
            if(deliveryContact == null)
                error += "Не заполнено поле 'Товар к перевозке принял (должность и ФИО)'\n";
            String proxyID = (String) findProperty("proxyID[EInvoice]").read(context, eInvoiceObject);
            Date proxyDate = (Date) findProperty("proxyDate[EInvoice]").read(context, eInvoiceObject);
            String partyIssuingProxyName = (String) findProperty("partyIssuingProxyName[EInvoice]").read(context, eInvoiceObject);

            String quantityTrip = (String) findProperty("quantityTrip[EInvoice]").read(context, eInvoiceObject);
            String transportOwnerName = (String) findProperty("transportOwnerName[EInvoice]").read(context, eInvoiceObject);
            String transportID = (String) findProperty("transportID[EInvoice]").read(context, eInvoiceObject);
            if(transportID == null)
                error += "Не заполнено поле 'Марка и гос. номер автомобиля'\n";
            String trailerID = (String) findProperty("trailerID[EInvoice]").read(context, eInvoiceObject);
            String sealID = (String) findProperty("sealIDReceiver[EInvoice]").read(context, eInvoiceObject);
            String currency = (String) findProperty("currency[EInvoice]").read(context, eInvoiceObject);
            if(currency == null)
                error += "Не заполнено поле '3-х буквенный код валюты, в которой указаны ценовые значения'\n";

            BigDecimal totalAmountWithoutCharges = (BigDecimal) findProperty("totalAmountWithoutCharges[EInvoice]").read(context, eInvoiceObject);
            BigDecimal totalAmountCharges = (BigDecimal) findProperty("totalAmountCharges[EInvoice]").read(context, eInvoiceObject);
            BigDecimal totalAmount = (BigDecimal) findProperty("totalAmount[EInvoice]").read(context, eInvoiceObject);
            Integer totalLineItem = (Integer) findProperty("totalLineItem[EInvoice]").read(context, eInvoiceObject);
            BigDecimal totalLineItemQuantity = (BigDecimal) findProperty("totalLineItemQuantity[EInvoice]").read(context, eInvoiceObject);
            BigDecimal totalGrossWeight = (BigDecimal) findProperty("totalGrossWeight[EInvoice]").read(context, eInvoiceObject);
            if(totalGrossWeight == null)
                error += "Не заполнено поле 'Всего масса груза в тоннах'\n";
            BigDecimal totalDespatchUnitQuantity = (BigDecimal) findProperty("totalDespatchUnitQuantity[EInvoice]").read(context, eInvoiceObject);
            BigDecimal totalAmountExcise = (BigDecimal) findProperty("totalAmountExcise[EInvoice]").read(context, eInvoiceObject);

            Element rootElement = new Element("BLRWBL");
            rootElement.setAttribute("version", "0.1");
            Document doc = new Document(rootElement);
            doc.setRootElement(rootElement);

            Element messageHeaderElement = new Element("MessageHeader");
            addStringElement(messageHeaderElement, "MessageID", documentNumber);
            addStringElement(messageHeaderElement, "MsgDateTime", documentDate);
            addStringElement(messageHeaderElement, "MessageType", "BLRWBL");
            addStringElement(messageHeaderElement, "MsgSenderID", glnSupplier);
            addStringElement(messageHeaderElement, "MsgReceiverID", glnCustomer);
            rootElement.addContent(messageHeaderElement);

            Element deliveryNoteElement = new Element("DeliveryNote");
            addIntegerElement(deliveryNoteElement, "DeliveryNoteType", 700); //ТТН (товарно-транспортная накладная)
            addStringElement(deliveryNoteElement, "DocumentID", documentNumber);
            addStringElement(deliveryNoteElement, "CreationDateTime", documentDate);
            addIntegerElement(deliveryNoteElement, "FunctionCode", isCancel ? 1 : 9); //9 =  Оригинал (накладная, которая формируется), 1 = Отмена (отмена ранее созданной накладной)

            addStringElement(deliveryNoteElement, "DeliveryNoteID", deliveryNoteId);
            addStringElement(deliveryNoteElement, "DeliveryNoteDate", formatDate(deliveryNoteDateTime));
            addStringElement(deliveryNoteElement, "ContractID", contractId);
            addStringElement(deliveryNoteElement, "ContractDate", formatDate(contractDate));
            addStringElement(deliveryNoteElement, "WaybillID", waybillId);

            Element shipperElement = new Element("Shipper"); //грузоотправитель
            addStringElement(shipperElement, "GLN", glnSupplier);
            addStringElement(shipperElement, "Name", nameSupplier);
            addStringElement(shipperElement, "Address", addressSupplier);
            addStringElement(shipperElement, "VATRegistrationNumber", unpSupplier);
            addStringElement(shipperElement, "Contact", contactSupplier);
            deliveryNoteElement.addContent(shipperElement);

            Element receiverElement = new Element("Receiver"); //грузополучатель
            addStringElement(receiverElement, "GLN", glnCustomer);
            addStringElement(receiverElement, "Name", nameCustomer);
            addStringElement(receiverElement, "Address", addressCustomer);
            addStringElement(receiverElement, "VATRegistrationNumber", unpCustomer);
            deliveryNoteElement.addContent(receiverElement);

            if(nameFreightPayer != null && addressFreightPayer != null && unpFreightPayer != null) {
                Element freightPayerElement = new Element("FreightPayer");
                addStringElement(freightPayerElement, "GLN", glnFreightPayer);
                addStringElement(freightPayerElement, "Name", nameFreightPayer);
                addStringElement(freightPayerElement, "Address", addressFreightPayer);
                addStringElement(freightPayerElement, "VATRegistrationNumber", unpFreightPayer);
                deliveryNoteElement.addContent(freightPayerElement);
            }

            Element shipFromElement = new Element("ShipFrom"); //пункт позгрузки
            addStringElement(shipFromElement, "GLN", glnSupplierStock);
            addStringElement(shipFromElement, "Address", addressSupplierStock);
            addStringElement(shipFromElement, "Contact", contactSupplierStock);
            deliveryNoteElement.addContent(shipFromElement);

            Element shipToElement = new Element("ShipTo"); //пункт разгрузки
            addStringElement(shipToElement, "GLN", glnCustomerStock);
            addStringElement(shipToElement, "Address", addressCustomerStock);
            deliveryNoteElement.addContent(shipToElement);

            Element carrierElement = new Element("Carrier");
            addStringElement(carrierElement, "TransportContact", transportContact);
            addStringElement(carrierElement, "DeliveryContact", deliveryContact);
            addStringElement(carrierElement, "ProxyID", proxyID);
            addStringElement(carrierElement, "ProxyDate", formatDate(proxyDate));
            addStringElement(carrierElement, "PartyIssuingProxyName", partyIssuingProxyName);
            deliveryNoteElement.addContent(carrierElement);

            addStringElement(deliveryNoteElement, "QuantityTrip", quantityTrip);
            addStringElement(deliveryNoteElement, "TransportOwnerName", transportOwnerName);
            addStringElement(deliveryNoteElement, "TransportID", transportID);
            addStringElement(deliveryNoteElement, "TrailerID", trailerID);
            addStringElement(deliveryNoteElement, "SealID", sealID);
            addStringElement(deliveryNoteElement, "Currency", currency);

            Element despatchAdviceLogisticUnitLineItemElement = new Element("DespatchAdviceLogisticUnitLineItem");

            for (EInvoiceDetail detail : getEInvoiceDetailList(context, eInvoiceObject)) {
                Element lineItemElement = new Element("LineItem");
                addIntegerElement(lineItemElement, "LineItemNumber", detail.lineItemNumber);
                addStringElement(lineItemElement, "LineItemID", detail.lineItemID);
                addStringElement(lineItemElement, "LineItemBuyerID", detail.lineItemBuyerID);
                addStringElement(lineItemElement, "LineItemName", StringEscapeUtils.escapeXml(detail.lineItemName));
                if(detail.grossWeightValue != null && detail.grossWeightValue.compareTo(BigDecimal.ZERO) > 0) {
                    addBigDecimalElement(lineItemElement, "GrossWeightValue", detail.grossWeightValue);
                } else {
                    error += "Не заполнено поле 'Масса груза'\n";
                    break;
                }
                addBigDecimalElement(lineItemElement, "QuantityDespatched", detail.quantityDespatched);
                if(detail.lineItemQuantityUOM != null) {
                    addStringElement(lineItemElement, "LineItemQuantityUOM", detail.lineItemQuantityUOM);
                } else {
                    error += "Не заполнено поле 'Международный 3-х буквенный код единицы измерения из справочника ОКРБ 008-95 Единицы измерения и счета'\n";
                    break;
                }
                if(detail.despatchUnitQuantityDespatched != null) {
                    addBigDecimalElement(lineItemElement, "DespatchUnitQuantityDespatched", detail.despatchUnitQuantityDespatched);
                } else {
                    error += "Не заполнено поле 'Количество в грузовых местах'\n";
                    break;
                }
                addBigDecimalElement(lineItemElement, "TaxRate", detail.valueVAT);
                addStringElement(lineItemElement, "AdditionalInformation", detail.additionalInformation);
                addBigDecimalElement(lineItemElement, "LineItemAmountWithoutCharges", detail.lineItemAmountWithoutCharges);
                addBigDecimalElement(lineItemElement, "LineItemAmountCharges", detail.lineItemAmountCharges);
                addBigDecimalElement(lineItemElement, "LineItemAmount", detail.lineItemAmount);
                addBigDecimalElement(lineItemElement, "LineItemPrice", detail.lineItemPrice);
                addBigDecimalElement(lineItemElement, "LineItemAmountExcise", detail.lineItemAmountExcise);
                despatchAdviceLogisticUnitLineItemElement.addContent(lineItemElement);
            }

            deliveryNoteElement.addContent(despatchAdviceLogisticUnitLineItemElement);

            Element totalElement = new Element("Total");
            addBigDecimalElement(totalElement, "TotalAmountWithoutCharges", totalAmountWithoutCharges);
            addBigDecimalElement(totalElement, "TotalAmountCharges", totalAmountCharges);
            addBigDecimalElement(totalElement, "TotalAmount", totalAmount);
            addIntegerElement(totalElement, "TotalLineItem", totalLineItem);
            addBigDecimalElement(totalElement, "TotalLineItemQuantity", totalLineItemQuantity);
            addBigDecimalElement(totalElement, "TotalGrossWeight", totalGrossWeight);
            addBigDecimalElement(totalElement, "TotalDespatchUnitQuantity", totalDespatchUnitQuantity);
            addBigDecimalElement(totalElement, "TotalAmountExcise", totalAmountExcise);
            deliveryNoteElement.addContent(totalElement);

            rootElement.addContent(deliveryNoteElement);

            if (error.isEmpty()) {
                tmpFile = File.createTempFile("evat", ".xml");
                return outputXMLString(doc, charset, outputDir, "blrwbl-", false);
            } else {
                context.delayUserInterfaction(new MessageClientAction(error, "Не все поля заполнены"));
                return null;
            }
        } finally {
            if (tmpFile != null && !tmpFile.delete())
                tmpFile.deleteOnExit();
        }
    }

    private List<EInvoiceDetail> getEInvoiceDetailList(ExecutionContext context, DataObject eInvoiceObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<EInvoiceDetail> result = new ArrayList<>();
        KeyExpr eInvoiceDetailExpr = new KeyExpr("eInvoiceDetail");
        ImRevMap<Object, KeyExpr> eInvoiceDetailKeys = MapFact.singletonRev((Object) "eInvoiceDetail", eInvoiceDetailExpr);

        QueryBuilder<Object, Object> eInvoiceDetailQuery = new QueryBuilder<>(eInvoiceDetailKeys);
        String[] eInvoiceDetailNames = new String[]{"lineItemID", "lineItemBuyerID", "lineItemName", "grossWeightValue", "quantityDespatched", "lineItemQuantityUOM",
                "despatchUnitQuantityDespatched", "valueVAT", "additionalInformation", "lineItemAmountWithoutCharges", "lineItemAmountCharges",
                "lineItemAmount", "lineItemPrice", "lineItemAmountExcise"};
        LCP[] eInvoiceDetailProperties = findProperties("lineItemID[EInvoiceDetail]", "lineItemBuyerID[EInvoiceDetail]", "lineItemName[EInvoiceDetail]", "grossWeightValue[EInvoiceDetail]",
                "quantityDespatched[EInvoiceDetail]", "lineItemQuantityUOM[EInvoiceDetail]", "despatchUnitQuantityDespatched[EInvoiceDetail]",
                "valueVAT[EInvoiceDetail]", "additionalInformation[EInvoiceDetail]", "lineItemAmountWithoutCharges[EInvoiceDetail]",
                "lineItemAmountCharges[EInvoiceDetail]", "lineItemAmount[EInvoiceDetail]", "lineItemPrice[EInvoiceDetail]", "lineItemAmountExcise[EInvoiceDetail]");
        for (int i = 0; i < eInvoiceDetailProperties.length; i++) {
            eInvoiceDetailQuery.addProperty(eInvoiceDetailNames[i], eInvoiceDetailProperties[i].getExpr(context.getModifier(), eInvoiceDetailExpr));
        }
        eInvoiceDetailQuery.and(findProperty("lineItemID[EInvoiceDetail]").getExpr(context.getModifier(), eInvoiceDetailExpr).getWhere());
        eInvoiceDetailQuery.and(findProperty("eInvoice[EInvoiceDetail]").getExpr(context.getModifier(), eInvoiceDetailExpr).compare(eInvoiceObject.getExpr(), Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> eInvoiceDetailResult = eInvoiceDetailQuery.execute(context);
        ImCol<ImMap<Object, Object>> resultValues = eInvoiceDetailResult.values();
        for (int i = 0; i < resultValues.size(); i++) {
            ImMap<Object, Object> entry = resultValues.get(i);
            String lineItemID = (String) entry.get("lineItemID");
            String lineItemBuyerID = (String) entry.get("lineItemBuyerID");
            String lineItemName = (String) entry.get("lineItemName");
            BigDecimal grossWeightValue = (BigDecimal) entry.get("grossWeightValue");
            BigDecimal quantityDespatched = (BigDecimal) entry.get("quantityDespatched");
            String lineItemQuantityUOM = (String) entry.get("lineItemQuantityUOM");
            BigDecimal despatchUnitQuantityDespatched = (BigDecimal) entry.get("despatchUnitQuantityDespatched");
            BigDecimal valueVAT = (BigDecimal) entry.get("valueVAT");
            String additionalInformation = (String) entry.get("additionalInformation");
            BigDecimal lineItemAmountWithoutCharges = (BigDecimal) entry.get("lineItemAmountWithoutCharges");
            BigDecimal lineItemAmountCharges = (BigDecimal) entry.get("lineItemAmountCharges");
            BigDecimal lineItemAmount = (BigDecimal) entry.get("lineItemAmount");
            BigDecimal lineItemPrice = (BigDecimal) entry.get("lineItemPrice");
            BigDecimal lineItemAmountExcise = (BigDecimal) entry.get("lineItemAmountExcise");

            result.add(new EInvoiceDetail(i + 1, lineItemID, lineItemBuyerID, lineItemName, grossWeightValue, quantityDespatched, lineItemQuantityUOM, despatchUnitQuantityDespatched,
                    valueVAT, additionalInformation, lineItemAmountWithoutCharges, lineItemAmountCharges, lineItemAmount, lineItemPrice, lineItemAmountExcise));
        }
        return result;
    }

    private String formatDate(java.util.Date date) {
        return date == null ? null : new SimpleDateFormat("yyyyMMdd").format(date);
    }

    private class EInvoiceDetail {
        private Integer lineItemNumber;
        private String lineItemID;
        private String lineItemBuyerID;
        private String lineItemName;
        private BigDecimal grossWeightValue;
        private BigDecimal quantityDespatched;
        private String lineItemQuantityUOM;
        private BigDecimal despatchUnitQuantityDespatched;
        private BigDecimal valueVAT;
        private String additionalInformation;
        private BigDecimal lineItemAmountWithoutCharges;
        private BigDecimal lineItemAmountCharges;
        private BigDecimal lineItemAmount;
        private BigDecimal lineItemPrice;
        private BigDecimal lineItemAmountExcise;

        public EInvoiceDetail(Integer lineItemNumber, String lineItemID, String lineItemBuyerID, String lineItemName, BigDecimal grossWeightValue,
                              BigDecimal quantityDespatched, String lineItemQuantityUOM, BigDecimal despatchUnitQuantityDespatched, BigDecimal valueVAT,
                              String additionalInformation, BigDecimal lineItemAmountWithoutCharges, BigDecimal lineItemAmountCharges,
                              BigDecimal lineItemAmount, BigDecimal lineItemPrice, BigDecimal lineItemAmountExcise) {
            this.lineItemNumber = lineItemNumber;
            this.lineItemID = lineItemID;
            this.lineItemBuyerID = lineItemBuyerID;
            this.lineItemName = lineItemName;
            this.grossWeightValue = grossWeightValue;
            this.quantityDespatched = quantityDespatched;
            this.lineItemQuantityUOM = lineItemQuantityUOM;
            this.despatchUnitQuantityDespatched = despatchUnitQuantityDespatched;
            this.valueVAT = valueVAT;
            this.additionalInformation = additionalInformation;
            this.lineItemAmountWithoutCharges = lineItemAmountWithoutCharges;
            this.lineItemAmountCharges = lineItemAmountCharges;
            this.lineItemAmount = lineItemAmount;
            this.lineItemPrice = lineItemPrice;
            this.lineItemAmountExcise = lineItemAmountExcise;
        }
    }
}