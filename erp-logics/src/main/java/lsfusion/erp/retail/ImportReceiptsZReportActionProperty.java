package lsfusion.erp.retail;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ImportReceiptsZReportActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface zReportInterface;

    // Опциональные модули
    ScriptingLogicsModule POSVostrovLM;
    ScriptingLogicsModule zReportRetailCRMLM;
    ScriptingLogicsModule zReportDiscountCardLM;

    public ImportReceiptsZReportActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClass("ZReport"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        zReportInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        DataSession session = context.getSession();

        this.POSVostrovLM = context.getBL().getModule("POSVostrov");
        this.zReportRetailCRMLM = context.getBL().getModule("ZReportRetailCRM");
        this.zReportDiscountCardLM = context.getBL().getModule("ZReportDiscountCard");
        
        try {
            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, "Файлы XML", "xml");
            DataObject zReportObject = context.getDataKeyValue(zReportInterface);
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {

                List<List<Object>> dataSale = new ArrayList<List<Object>>();
                List<List<Object>> dataReturn = new ArrayList<List<Object>>();
                List<List<Object>> dataPayment = new ArrayList<List<Object>>();

                List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());
                for (byte[] file : fileList) {
                    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                    Document doc = dBuilder.parse(new ByteArrayInputStream(file));
                    doc.getDocumentElement().normalize();

                    String numberZReport = (String) findProperty("numberZReport").read(session, zReportObject);
                    String numberCashRegisterZReport = (String) findProperty("numberCashRegisterZReport").read(session, zReportObject);

                    Integer numberReceipt = 0;
                    KeyExpr receiptExpr = new KeyExpr("receipt");
                    ImRevMap<Object, KeyExpr> receiptKeys = MapFact.singletonRev((Object)"receipt", receiptExpr);
                    QueryBuilder<Object, Object> receiptQuery = new QueryBuilder<Object, Object>(receiptKeys);
                    receiptQuery.addProperty("numberReceipt", findProperty("numberReceipt").getExpr(context.getModifier(), receiptExpr));
                    receiptQuery.and(findProperty("zReportReceipt").getExpr(context.getModifier(), receiptQuery.getMapExprs().get("receipt")).compare(zReportObject.getExpr(), Compare.EQUALS));
                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> receiptResult = receiptQuery.execute(session);
                    for (ImMap<Object, Object> receiptRows : receiptResult.valueIt()) {
                        Integer number = (Integer) receiptRows.get("numberReceipt");
                        if (number != null && number > numberReceipt)
                            numberReceipt = number;
                    }

                    NodeList receiptList = doc.getElementsByTagName("receipt");

                    for (int receiptIndex = 0; receiptIndex < receiptList.getLength(); receiptIndex++) {

                        Node receipt = receiptList.item(receiptIndex);
                        if (receipt.getNodeType() == Node.ELEMENT_NODE) {
                            Element receiptElement = (Element) receipt;

                            numberReceipt++;
                            BigDecimal discountSumReceipt = (BigDecimal) getTagValue("discountSumReceipt", receiptElement, 2);
                            String seriesNumberDiscountCard = (String) getTagValue("numberDiscountCardReceipt", receiptElement, 0);
                            String noteReceipt = (String) getTagValue("noteReceipt", receiptElement, 0);
                            
                            Long dateTimeValue = (Long) getTagValue("dateTimeReceipt", receiptElement, 3);
                            Date dateReceipt = dateTimeValue == null ? null : new Date(dateTimeValue);
                            Time timeReceipt = dateTimeValue == null ? null : new Time(dateTimeValue);
                            
                            String isInvoiceReceiptValue = (String) getTagValue("isInvoiceReceipt", receiptElement, 0);
                            Boolean isInvoiceReceipt = (isInvoiceReceiptValue != null && isInvoiceReceiptValue.equals("ДА")) ? true : null;
                            
                            NodeList paymentList = receiptElement.getElementsByTagName("payment");
                            for (int paymentIndex = 0; paymentIndex < paymentList.getLength(); paymentIndex++) {
                                Node payment = paymentList.item(paymentIndex);
                                if (payment.getNodeType() == Node.ELEMENT_NODE) {
                                    Element paymentElement = (Element) payment;
                                    BigDecimal sumPayment = (BigDecimal) getTagValue("sumPayment", paymentElement, 2);
                                    String namePaymentMeansPayment = (String) getTagValue("namePaymentMeansPayment", paymentElement, 0);
                                    String sidPaymentTypePayment = (String) getTagValue("sidPaymentTypePayment", paymentElement, 0);
                                    dataPayment.add(Arrays.<Object>asList(numberZReport, numberCashRegisterZReport, paymentIndex + 1,
                                            numberReceipt, sidPaymentTypePayment, sumPayment, namePaymentMeansPayment));
                                }
                            }
                            NodeList receiptDetailList = receiptElement.getElementsByTagName("receiptDetail");
                            for (int receiptDetailIndex = 0; receiptDetailIndex < receiptDetailList.getLength(); receiptDetailIndex++) {
                                Node receiptDetail = receiptDetailList.item(receiptDetailIndex);
                                if (receiptDetail.getNodeType() == Node.ELEMENT_NODE) {
                                    Element receiptDetailElement = (Element) receiptDetail;
                                    String typeReceiptDetail = (String) getTagValue("typeReceiptDetail", receiptDetailElement, 0);
                                    boolean isReturn = typeReceiptDetail.trim().equals("Возврат");
                                    BigDecimal priceReceiptDetail = (BigDecimal) getTagValue("priceReceiptDetail", receiptDetailElement, 2);
                                    BigDecimal quantityReceiptSaleDetail = isReturn ? null : (BigDecimal) getTagValue("quantityReceiptDetail", receiptDetailElement, 2);
                                    BigDecimal quantityReceiptReturnDetail = isReturn ? (BigDecimal) getTagValue("quantityReceiptDetail", receiptDetailElement, 2) : null;
                                    String idBarcodeReceiptDetail = (String) getTagValue("idBarcodeReceiptDetail", receiptDetailElement, 0);
                                    BigDecimal sumReceiptDetail = (BigDecimal) getTagValue("sumReceiptDetail", receiptDetailElement, 2);
                                    BigDecimal discountSumReceiptDetail = (BigDecimal) getTagValue("discountSumReceiptDetail", receiptDetailElement, 2);
                                    BigDecimal discountPercentReceiptSaleDetail = (BigDecimal) getTagValue("discountPercentReceiptDetail", receiptDetailElement, 2);
                                    Integer numberReceiptDetail = (Integer) getTagValue("numberReceiptDetail", receiptDetailElement, 1);

                                    NodeList promotionConditionList = receiptElement.getElementsByTagName("promotionCondition");
                                    for (int promotionConditionIndex = 0; promotionConditionIndex < promotionConditionList.getLength(); promotionConditionIndex++) {
                                        Node promotionCondition = promotionConditionList.item(promotionConditionIndex);
                                        if (promotionCondition.getNodeType() == Node.ELEMENT_NODE) {
                                            Element promotionConditionElement = (Element) promotionCondition;
                                            String idPromotionCondition = (String) getTagValue("idPromotionCondition", promotionConditionElement, 0);
                                            BigDecimal quantityReceiptDetailPromotionCondition = (BigDecimal) getTagValue("quantityReceiptDetailPromotionCondition", promotionConditionElement, 2);
                                            BigDecimal promotionSumReceiptDetailPromotionCondition = (BigDecimal) getTagValue("promotionSumReceiptDetailPromotionCondition", promotionConditionElement, 2);

                                            if (quantityReceiptReturnDetail != null) {
                                                dataReturn.add(Arrays.<Object>asList(numberCashRegisterZReport, numberZReport,
                                                        numberReceipt, numberReceiptDetail, dateReceipt, idBarcodeReceiptDetail, 
                                                        timeReceipt, quantityReceiptReturnDetail, priceReceiptDetail, sumReceiptDetail,
                                                        discountSumReceiptDetail, discountSumReceipt, seriesNumberDiscountCard, noteReceipt,
                                                        isInvoiceReceipt));
                                            }
                                            
                                            else
                                                dataSale.add(Arrays.<Object>asList(numberCashRegisterZReport, numberZReport,
                                                        numberReceipt, numberReceiptDetail, dateReceipt, idBarcodeReceiptDetail,
                                                        timeReceipt, quantityReceiptSaleDetail, priceReceiptDetail, sumReceiptDetail,
                                                        discountSumReceiptDetail, discountPercentReceiptSaleDetail, discountSumReceipt,
                                                        seriesNumberDiscountCard, noteReceipt, idPromotionCondition, 
                                                        quantityReceiptDetailPromotionCondition, promotionSumReceiptDetailPromotionCondition,
                                                        isInvoiceReceipt));
                                        }
                                    }
                                    
                                    if(promotionConditionList.getLength()==0) {
                                    if (quantityReceiptReturnDetail != null)
                                        dataReturn.add(Arrays.<Object>asList(numberCashRegisterZReport, numberZReport, 
                                                numberReceipt, numberReceiptDetail, dateReceipt, idBarcodeReceiptDetail,
                                                timeReceipt, quantityReceiptReturnDetail, priceReceiptDetail, sumReceiptDetail,
                                                discountSumReceiptDetail, discountSumReceipt, seriesNumberDiscountCard, noteReceipt,
                                                isInvoiceReceipt));
                                    else
                                        dataSale.add(Arrays.<Object>asList(numberCashRegisterZReport, numberZReport, 
                                                numberReceipt, numberReceiptDetail, dateReceipt, idBarcodeReceiptDetail,
                                                timeReceipt, quantityReceiptSaleDetail, priceReceiptDetail, sumReceiptDetail,
                                                discountSumReceiptDetail, discountPercentReceiptSaleDetail, discountSumReceipt,
                                                seriesNumberDiscountCard, noteReceipt, null, null, null, isInvoiceReceipt));
                                    }
                                }
                            }
                        }
                    }
                }

                List<ImportProperty<?>> saleProperties = new ArrayList<ImportProperty<?>>();
                List<ImportProperty<?>> returnProperties = new ArrayList<ImportProperty<?>>();
                List<ImportProperty<?>> paymentProperties = new ArrayList<ImportProperty<?>>();
                
                List<ImportField> saleImportFields = new ArrayList<ImportField>();
                List<ImportField> returnImportFields = new ArrayList<ImportField>();
                List<ImportField> paymentImportFields = new ArrayList<ImportField>();

                List<ImportKey<?>> saleKeys = new ArrayList<ImportKey<?>>();
                List<ImportKey<?>> returnKeys = new ArrayList<ImportKey<?>>();
                List<ImportKey<?>> paymentKeys = new ArrayList<ImportKey<?>>();
                
                ImportField numberCashRegisterField = new ImportField(findProperty("nppMachinery"));
                ImportField numberZReportField = new ImportField(findProperty("numberZReport"));
                ImportField numberReceiptField = new ImportField(findProperty("numberReceipt"));
                ImportField numberReceiptDetailField = new ImportField(findProperty("numberReceiptDetail"));
                
                ImportKey<?> cashRegisterKey = new ImportKey((ConcreteCustomClass) findClass("CashRegister"), findProperty("cashRegisterNumber").getMapping(numberCashRegisterField));
                saleKeys.add(cashRegisterKey);
                returnKeys.add(cashRegisterKey);
                paymentKeys.add(cashRegisterKey);
                
                ImportKey<?> zReportKey = new ImportKey((ConcreteCustomClass) findClass("ZReport"), findProperty("zReportNumberCashRegister").getMapping(numberZReportField, numberCashRegisterField));
                saleKeys.add(zReportKey);
                returnKeys.add(zReportKey);
                
                ImportKey<?> receiptKey = new ImportKey((ConcreteCustomClass) findClass("Receipt"), findProperty("receiptZReportNumberCashRegister").getMapping(numberZReportField, numberReceiptField, numberCashRegisterField));
                saleKeys.add(receiptKey);
                returnKeys.add(receiptKey);
                paymentKeys.add(receiptKey);
                
                ImportKey<?> receiptSaleDetailKey = new ImportKey((ConcreteCustomClass) findClass("ReceiptSaleDetail"), findProperty("receiptDetailZReportReceiptNumberCashRegister").getMapping(numberZReportField, numberReceiptField, numberReceiptDetailField, numberCashRegisterField));
                saleKeys.add(receiptSaleDetailKey);
                
                ImportKey<?> receiptReturnDetailKey = new ImportKey((ConcreteCustomClass) findClass("ReceiptReturnDetail"), findProperty("receiptDetailZReportReceiptNumberCashRegister").getMapping(numberZReportField, numberReceiptField, numberReceiptDetailField, numberCashRegisterField));
                returnKeys.add(receiptReturnDetailKey);
                
                saleProperties.add(new ImportProperty(numberCashRegisterField, findProperty("cashRegisterZReport").getMapping(zReportKey),
                        object(findClass("CashRegister")).getMapping(cashRegisterKey)));
                saleProperties.add(new ImportProperty(numberZReportField, findProperty("numberZReport").getMapping(zReportKey)));
                saleProperties.add(new ImportProperty(numberZReportField, findProperty("zReportReceipt").getMapping(receiptKey),
                        object(findClass("ZReport")).getMapping(zReportKey)));
                saleProperties.add(new ImportProperty(numberReceiptField, findProperty("numberReceipt").getMapping(receiptKey)));
                saleProperties.add(new ImportProperty(numberReceiptField, findProperty("receiptReceiptDetail").getMapping(receiptSaleDetailKey),
                        object(findClass("Receipt")).getMapping(receiptKey)));
                saleProperties.add(new ImportProperty(numberReceiptDetailField, findProperty("numberReceiptDetail").getMapping(receiptSaleDetailKey)));
                
                returnProperties.add(new ImportProperty(numberCashRegisterField, findProperty("cashRegisterZReport").getMapping(zReportKey),
                        object(findClass("CashRegister")).getMapping(cashRegisterKey)));
                returnProperties.add(new ImportProperty(numberZReportField, findProperty("numberZReport").getMapping(zReportKey)));
                returnProperties.add(new ImportProperty(numberZReportField, findProperty("zReportReceipt").getMapping(receiptKey),
                        object(findClass("ZReport")).getMapping(zReportKey)));                
                returnProperties.add(new ImportProperty(numberReceiptField, findProperty("numberReceipt").getMapping(receiptKey)));                
                returnProperties.add(new ImportProperty(numberReceiptField, findProperty("receiptReceiptDetail").getMapping(receiptReturnDetailKey),
                        object(findClass("Receipt")).getMapping(receiptKey)));               
                returnProperties.add(new ImportProperty(numberReceiptDetailField, findProperty("numberReceiptDetail").getMapping(receiptReturnDetailKey)));
                
                saleImportFields.add(numberCashRegisterField);
                saleImportFields.add(numberZReportField);
                saleImportFields.add(numberReceiptField);
                saleImportFields.add(numberReceiptDetailField);
                
                returnImportFields.add(numberCashRegisterField);               
                returnImportFields.add(numberZReportField);                
                returnImportFields.add(numberReceiptField);               
                returnImportFields.add(numberReceiptDetailField);
                              
                paymentImportFields.add(numberZReportField);
                paymentImportFields.add(numberCashRegisterField);

                                
                ImportField dateField = new ImportField(findProperty("dateReceipt"));
                ImportField idBarcodeReceiptDetailField = new ImportField(findProperty("idBarcodeReceiptDetail"));                
                ImportKey<?> skuKey = new ImportKey((CustomClass) findClass("Sku"), findProperty("skuBarcodeIdDate").getMapping(idBarcodeReceiptDetailField, dateField));
                saleKeys.add(skuKey);
                returnKeys.add(skuKey);
                
                saleProperties.add(new ImportProperty(dateField, findProperty("dateZReport").getMapping(zReportKey)));
                saleProperties.add(new ImportProperty(dateField, findProperty("dateReceipt").getMapping(receiptKey)));
                saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, findProperty("idBarcodeReceiptDetail").getMapping(receiptSaleDetailKey)));
                saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, findProperty("skuReceiptSaleDetail").getMapping(receiptSaleDetailKey),
                        object(findClass("Sku")).getMapping(skuKey)));
                returnProperties.add(new ImportProperty(dateField, findProperty("dateZReport").getMapping(zReportKey)));
                returnProperties.add(new ImportProperty(dateField, findProperty("dateReceipt").getMapping(receiptKey)));
                returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, findProperty("idBarcodeReceiptDetail").getMapping(receiptReturnDetailKey)));
                returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, findProperty("skuReceiptReturnDetail").getMapping(receiptReturnDetailKey),
                        object(findClass("Sku")).getMapping(skuKey)));
                saleImportFields.add(dateField);
                saleImportFields.add(idBarcodeReceiptDetailField);
                returnImportFields.add(dateField);
                returnImportFields.add(idBarcodeReceiptDetailField);

                
                ImportField timeField = new ImportField(findProperty("timeReceipt"));
                saleProperties.add(new ImportProperty(timeField, findProperty("timeZReport").getMapping(zReportKey)));
                saleProperties.add(new ImportProperty(timeField, findProperty("timeReceipt").getMapping(receiptKey)));
                returnProperties.add(new ImportProperty(timeField, findProperty("timeZReport").getMapping(zReportKey)));
                returnProperties.add(new ImportProperty(timeField, findProperty("timeReceipt").getMapping(receiptKey)));
                saleImportFields.add(timeField);
                returnImportFields.add(timeField);

                ImportField quantityReceiptSaleDetailField = new ImportField(findProperty("quantityReceiptSaleDetail"));
                saleProperties.add(new ImportProperty(quantityReceiptSaleDetailField, findProperty("quantityReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                saleImportFields.add(quantityReceiptSaleDetailField);

                ImportField quantityReceiptReturnDetailField = new ImportField(findProperty("quantityReceiptReturnDetail"));
                returnProperties.add(new ImportProperty(quantityReceiptReturnDetailField, findProperty("quantityReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                returnImportFields.add(quantityReceiptReturnDetailField);

                ImportField priceReceiptSaleDetailField = new ImportField(findProperty("priceReceiptSaleDetail"));
                saleProperties.add(new ImportProperty(priceReceiptSaleDetailField, findProperty("priceReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                saleImportFields.add(priceReceiptSaleDetailField);

                ImportField priceReceiptReturnDetailField = new ImportField(findProperty("priceReceiptReturnDetail"));
                returnProperties.add(new ImportProperty(priceReceiptReturnDetailField, findProperty("priceReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                returnImportFields.add(priceReceiptReturnDetailField);
                
                ImportField sumReceiptSaleDetailField = new ImportField(findProperty("sumReceiptSaleDetail"));
                saleProperties.add(new ImportProperty(sumReceiptSaleDetailField, findProperty("sumReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                saleImportFields.add(sumReceiptSaleDetailField);

                ImportField retailSumReceiptReturnDetailField = new ImportField(findProperty("sumReceiptReturnDetail"));
                returnProperties.add(new ImportProperty(retailSumReceiptReturnDetailField, findProperty("sumReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                returnImportFields.add(retailSumReceiptReturnDetailField);
                
                ImportField discountSumReceiptSaleDetailField = new ImportField(findProperty("discountSumReceiptSaleDetail"));
                saleProperties.add(new ImportProperty(discountSumReceiptSaleDetailField, findProperty("discountSumReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                saleImportFields.add(discountSumReceiptSaleDetailField);

                ImportField discountSumReceiptReturnDetailField = new ImportField(findProperty("discountSumReceiptReturnDetail"));
                returnProperties.add(new ImportProperty(discountSumReceiptReturnDetailField, findProperty("discountSumReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                returnImportFields.add(discountSumReceiptReturnDetailField);

                ImportField discountPercentReceiptSaleDetailField = new ImportField(findProperty("discountPercentReceiptSaleDetail"));
                saleProperties.add(new ImportProperty(discountPercentReceiptSaleDetailField, findProperty("discountPercentReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                saleImportFields.add(discountPercentReceiptSaleDetailField);

                ImportField discountSumReturnReceiptField = new ImportField(findProperty("discountSumReturnReceipt"));
                returnProperties.add(new ImportProperty(discountSumReturnReceiptField, findProperty("discountSumReturnReceipt").getMapping(receiptKey)));
                returnImportFields.add(discountSumReturnReceiptField);

                ImportField discountSumSaleReceiptField = new ImportField(findProperty("discountSumSaleReceipt"));
                saleProperties.add(new ImportProperty(discountSumSaleReceiptField, findProperty("discountSumSaleReceipt").getMapping(receiptKey)));
                saleImportFields.add(discountSumSaleReceiptField);

                if (zReportDiscountCardLM != null) {

                    ImportField seriesNumberDiscountCardField = new ImportField(zReportDiscountCardLM.findProperty("seriesNumberDiscountCard"));
                    ImportKey<?> discountCardKey = new ImportKey((ConcreteCustomClass) zReportDiscountCardLM.findClass("DiscountCard"), zReportDiscountCardLM.findProperty("discountCardSeriesNumber").getMapping(seriesNumberDiscountCardField, dateField));
                    saleKeys.add(discountCardKey);
                    returnKeys.add(discountCardKey);
                    
                    saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("seriesNumberDiscountCard").getMapping(discountCardKey)));
                    saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("discountCardReceipt").getMapping(receiptKey),
                            object(zReportDiscountCardLM.findClass("DiscountCard")).getMapping(discountCardKey)));
                    returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("seriesNumberDiscountCard").getMapping(discountCardKey)));
                    returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("discountCardReceipt").getMapping(receiptKey),
                            object(zReportDiscountCardLM.findClass("DiscountCard")).getMapping(discountCardKey)));
                    saleImportFields.add(seriesNumberDiscountCardField);
                    returnImportFields.add(seriesNumberDiscountCardField);

                }
                
                ImportField noteReceiptField = new ImportField(findProperty("noteReceipt"));
                saleProperties.add(new ImportProperty(noteReceiptField, findProperty("noteReceipt").getMapping(receiptKey)));
                returnProperties.add(new ImportProperty(noteReceiptField, findProperty("noteReceipt").getMapping(receiptKey)));
                saleImportFields.add(noteReceiptField);
                returnImportFields.add(noteReceiptField);

                if (zReportRetailCRMLM != null) {

                    ImportField idPromotionConditionField = new ImportField(zReportRetailCRMLM.findProperty("idPromotionCondition"));
                    ImportKey<?> promotionConditionKey = new ImportKey((ConcreteCustomClass) zReportRetailCRMLM.findClass("PromotionCondition"),
                            zReportRetailCRMLM.findProperty("promotionConditionId").getMapping(idPromotionConditionField));
                    saleKeys.add(promotionConditionKey);
                    saleImportFields.add(idPromotionConditionField);


                    ImportField quantityReceiptSaleDetailPromotionConditionField = new ImportField(zReportRetailCRMLM.findProperty("quantityReceiptSaleDetailPromotionCondition"));
                    saleProperties.add(new ImportProperty(quantityReceiptSaleDetailPromotionConditionField, zReportRetailCRMLM.findProperty("quantityReceiptSaleDetailPromotionCondition").getMapping(receiptSaleDetailKey, promotionConditionKey)));
                    saleImportFields.add(quantityReceiptSaleDetailPromotionConditionField);

                    ImportField promotionSumReceiptSaleDetailPromotionConditionField = new ImportField(zReportRetailCRMLM.findProperty("promotionSumReceiptSaleDetailPromotionCondition"));
                    saleProperties.add(new ImportProperty(promotionSumReceiptSaleDetailPromotionConditionField, zReportRetailCRMLM.findProperty("promotionSumReceiptSaleDetailPromotionCondition").getMapping(receiptSaleDetailKey, promotionConditionKey)));
                    saleImportFields.add(promotionSumReceiptSaleDetailPromotionConditionField);

                }
                
                if(POSVostrovLM != null) {
                    ImportField isInvoiceReceiptField = new ImportField(POSVostrovLM.findProperty("isInvoiceReceipt"));
                    saleProperties.add(new ImportProperty(isInvoiceReceiptField, POSVostrovLM.findProperty("isInvoiceReceipt").getMapping(receiptKey)));
                    returnProperties.add(new ImportProperty(isInvoiceReceiptField, POSVostrovLM.findProperty("isInvoiceReceipt").getMapping(receiptKey)));
                    saleImportFields.add(isInvoiceReceiptField);
                    returnImportFields.add(isInvoiceReceiptField);
                }

                ImportField numberPaymentField = new ImportField(findProperty("numberPayment"));
                ImportKey<?> paymentKey = new ImportKey((ConcreteCustomClass) findClass("Payment"), findProperty("paymentZReportReceiptNumberCashRegister").getMapping(numberZReportField, numberReceiptField, numberPaymentField, numberCashRegisterField));
                paymentKeys.add(paymentKey);
                paymentProperties.add(new ImportProperty(numberPaymentField, findProperty("numberPayment").getMapping(paymentKey)));
                paymentImportFields.add(numberPaymentField);
                
                paymentProperties.add(new ImportProperty(numberReceiptField, findProperty("receiptPayment").getMapping(paymentKey),
                        object(findClass("Receipt")).getMapping(receiptKey)));
                paymentImportFields.add(numberReceiptField);
                
                ImportField sidTypePaymentField = new ImportField(findProperty("sidPaymentType"));
                ImportKey<?> paymentTypeKey = new ImportKey((ConcreteCustomClass) findClass("PaymentType"), findProperty("typePaymentSID").getMapping(sidTypePaymentField));
                paymentKeys.add(paymentTypeKey);
                paymentProperties.add(new ImportProperty(sidTypePaymentField, findProperty("paymentTypePayment").getMapping(paymentKey),
                        object(findClass("PaymentType")).getMapping(paymentTypeKey)));
                paymentImportFields.add(sidTypePaymentField);
                
                ImportField sumPaymentField = new ImportField(findProperty("sumPayment"));
                paymentProperties.add(new ImportProperty(sumPaymentField, findProperty("sumPayment").getMapping(paymentKey)));
                paymentImportFields.add(sumPaymentField);
                
                ImportField paymentMeansPaymentField = new ImportField(findProperty("staticCaption"));
                paymentProperties.add(new ImportProperty(paymentMeansPaymentField, findProperty("paymentMeansPayment").getMapping(paymentKey)));
                paymentImportFields.add(paymentMeansPaymentField);

                new IntegrationService(session, new ImportTable(saleImportFields, dataSale), saleKeys, saleProperties).synchronize(true);

                new IntegrationService(session, new ImportTable(returnImportFields, dataReturn), returnKeys, returnProperties).synchronize(true);

                new IntegrationService(session, new ImportTable(paymentImportFields, dataPayment), paymentKeys, paymentProperties).synchronize(true);
            }
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object getTagValue(String sTag, Element eElement, int type) {
        Node elem = eElement.getElementsByTagName(sTag).item(0);
        if (elem == null)
            return null;
        else {
            NodeList nlList = elem.getChildNodes();
            Node nValue = nlList.item(0);
            String value = nValue.getNodeValue();
            switch (type) {
                case 0:
                    return value;
                case 1:
                    return Integer.parseInt(value);
                case 2:
                    return new BigDecimal(value);
                case 3:
                    return Long.parseLong(value);
                default:
                    return value.trim();
            }
        }
    }
}