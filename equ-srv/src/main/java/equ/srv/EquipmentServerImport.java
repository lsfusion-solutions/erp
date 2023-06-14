package equ.srv;

import equ.api.GiftCard;
import equ.api.Payment;
import equ.api.SalesInfo;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.action.controller.stack.ExecutionStack;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.physics.dev.integration.service.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

public class EquipmentServerImport {

    public static void importPayments(BusinessLogics BL, DataSession session, ExecutionStack stack, List<SalesInfo> data, Set<String> ignoredIdReceipts, EquipmentServer.EquipmentServerOptions options) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ScriptingLogicsModule zReportLM = BL.getModule("ZReport");
        if (zReportLM != null) {

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField idPaymentField = new ImportField(zReportLM.findProperty("id[Payment]"));
            ImportKey<?> paymentKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ZReport.Payment"), zReportLM.findProperty("payment[STRING[100]]").getMapping(idPaymentField));
            keys.add(paymentKey);
            props.add(new ImportProperty(idPaymentField, zReportLM.findProperty("id[Payment]").getMapping(paymentKey)));
            fields.add(idPaymentField);

            ImportField idReceiptField = new ImportField(zReportLM.findProperty("id[Receipt]"));
            ImportKey<?> receiptKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("Receipt"), zReportLM.findProperty("receipt[STRING[100]]").getMapping(idReceiptField));
            keys.add(receiptKey);
            props.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receipt[Payment]").getMapping(paymentKey),
                    zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));
            fields.add(idReceiptField);

            ImportField sidTypePaymentField = new ImportField(zReportLM.findProperty("sid[PaymentType]"));
            ImportKey<?> paymentTypeKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("PaymentType"), zReportLM.findProperty("typePaymentSID[BPSTRING[10]]").getMapping(sidTypePaymentField));
            keys.add(paymentTypeKey);
            paymentTypeKey.skipKey = true;
            props.add(new ImportProperty(sidTypePaymentField, zReportLM.findProperty("paymentType[Payment]").getMapping(paymentKey),
                    zReportLM.object(zReportLM.findClass("PaymentType")).getMapping(paymentTypeKey)));
            fields.add(sidTypePaymentField);

            ImportField sumPaymentField = new ImportField(zReportLM.findProperty("sum[Payment]"));
            props.add(new ImportProperty(sumPaymentField, zReportLM.findProperty("sum[Payment]").getMapping(paymentKey)));
            fields.add(sumPaymentField);

            ImportField numberPaymentField = new ImportField(zReportLM.findProperty("number[Payment]"));
            props.add(new ImportProperty(numberPaymentField, zReportLM.findProperty("number[Payment]").getMapping(paymentKey)));
            fields.add(numberPaymentField);

            List<List<Object>> dataPayment = new ArrayList<>();
            JSONObject extraFields = new JSONObject();
            Set<String> usedPaymentIds = new HashSet<>();
            for (SalesInfo sale : data) {
                String idReceipt = EquipmentServer.getIdReceipt(sale, options);
                if (!ignoredIdReceipts.contains(idReceipt)) {
                    if (sale.sumCash != null && sale.sumCash.doubleValue() != 0) {
                        dataPayment.add(Arrays.asList(idReceipt + "1", idReceipt, "cash", sale.sumCash, 1));
                    }
                    if (sale.sumCard != null && sale.sumCard.doubleValue() != 0) {
                        dataPayment.add(Arrays.asList(idReceipt + "2", idReceipt, "card", sale.sumCard, 2));
                    }
                    if (sale.payments != null) {
                        //из-за того, что у платежей giftCard id начинаются с 3
                        int paymentNumber = 3 + (sale.sumGiftCardMap != null ? sale.sumGiftCardMap.size() : 0);

                        //backward compatibility with old ids
                        Set<String> firstPaymentsOfType = new HashSet<>();
                        for (Payment payment : sale.payments) {
                            String paymentType = payment.type;
                            boolean firstPaymentOfType = firstPaymentsOfType.add(paymentType);
                            String paymentId;
                            if (firstPaymentOfType) {
                                if (payment.isCash()) {
                                    paymentId = idReceipt + "1";
                                } else if (payment.isCard()) {
                                    paymentId = idReceipt + "2";
                                } else {
                                    paymentId = idReceipt + "_" + paymentType;
                                }
                            } else {
                                paymentId = idReceipt + "/" + paymentNumber;
                            }
                            dataPayment.add(Arrays.asList(paymentId, idReceipt, paymentType, payment.sum, paymentNumber++));

                            if (payment.extraFields != null && !usedPaymentIds.contains(paymentId)) {
                                for (Map.Entry<String, Object> extraField : payment.extraFields.entrySet()) {
                                    JSONArray dataArray = extraFields.optJSONArray(extraField.getKey());
                                    if (dataArray == null) {
                                        dataArray = new JSONArray();
                                    }
                                    JSONObject fieldReceipt = new JSONObject();
                                    fieldReceipt.put("id", paymentId);
                                    fieldReceipt.put("value", extraField.getValue());
                                    dataArray.put(fieldReceipt);

                                    extraFields.put(extraField.getKey(), dataArray);
                                }
                            }
                            usedPaymentIds.add(paymentId);
                        }
                    }
                }
            }

            if(!dataPayment.isEmpty()) {
                new IntegrationService(session, new ImportTable(fields, dataPayment), keys, props).synchronize(true);

                ScriptingLogicsModule cashRegisterLM = BL.getModule("EquipmentCashRegister");
                if (!extraFields.isEmpty() && cashRegisterLM.findProperty("executeProcessPaymentExtraFields[]").read(session) != null) {
                    cashRegisterLM.findAction("processPaymentExtraFields[STRING]").execute(session, stack, new DataObject(extraFields.toString()));
                }
            }
        }
    }

    public static void importPaymentGiftCardMultiThread(BusinessLogics BL, DataSession session, List<SalesInfo> salesInfoList, Set<String> ignoredIdReceipts, int start, int finish, EquipmentServer.EquipmentServerOptions options) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ScriptingLogicsModule giftCardLM = BL.getModule("GiftCard");
        if (giftCardLM != null) {

            List<ImportProperty<?>> paymentProperties = new ArrayList<>();
            List<ImportField> paymentFields = new ArrayList<>();
            List<ImportKey<?>> paymentKeys = new ArrayList<>();

            ImportField idPaymentField = new ImportField(giftCardLM.findProperty("id[Payment]"));
            ImportKey<?> paymentKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("PaymentGiftCard"), giftCardLM.findProperty("payment[STRING[100]]").getMapping(idPaymentField));
            paymentKeys.add(paymentKey);
            paymentProperties.add(new ImportProperty(idPaymentField, giftCardLM.findProperty("id[Payment]").getMapping(paymentKey)));
            paymentFields.add(idPaymentField);

            ImportField idReceiptField = new ImportField(giftCardLM.findProperty("id[Receipt]"));
            ImportKey<?> receiptKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("Receipt"), giftCardLM.findProperty("receipt[STRING[100]]").getMapping(idReceiptField));
            paymentKeys.add(receiptKey);
            paymentProperties.add(new ImportProperty(idReceiptField, giftCardLM.findProperty("receipt[Payment]").getMapping(paymentKey),
                    giftCardLM.object(giftCardLM.findClass("Receipt")).getMapping(receiptKey)));
            paymentFields.add(idReceiptField);

            ImportField sidTypePaymentField = new ImportField(giftCardLM.findProperty("sid[PaymentType]"));
            ImportKey<?> paymentTypeKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("PaymentType"), giftCardLM.findProperty("typePaymentSID[BPSTRING[10]]").getMapping(sidTypePaymentField));
            paymentKeys.add(paymentTypeKey);
            paymentProperties.add(new ImportProperty(sidTypePaymentField, giftCardLM.findProperty("paymentType[Payment]").getMapping(paymentKey),
                    giftCardLM.object(giftCardLM.findClass("PaymentType")).getMapping(paymentTypeKey)));
            paymentFields.add(sidTypePaymentField);

            ImportField sumPaymentField = new ImportField(giftCardLM.findProperty("sum[Payment]"));
            paymentProperties.add(new ImportProperty(sumPaymentField, giftCardLM.findProperty("sum[Payment]").getMapping(paymentKey)));
            paymentFields.add(sumPaymentField);

            ImportField numberPaymentField = new ImportField(giftCardLM.findProperty("number[Payment]"));
            paymentProperties.add(new ImportProperty(numberPaymentField, giftCardLM.findProperty("number[Payment]").getMapping(paymentKey)));
            paymentFields.add(numberPaymentField);

            ImportField idGiftCardField = new ImportField(giftCardLM.findProperty("id[GiftCard]"));
            ImportKey<?> giftCardKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("GiftCard"), giftCardLM.findProperty("giftCard[STRING[100]]").getMapping(idGiftCardField));
            giftCardKey.skipKey = options.skipGiftCardKeys;
            paymentKeys.add(giftCardKey);
            paymentProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("id[GiftCard]").getMapping(giftCardKey)));
            paymentProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("number[GiftCard]").getMapping(giftCardKey)));
            paymentProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("giftCard[PaymentGiftCard]").getMapping(paymentKey),
                    giftCardLM.object(giftCardLM.findClass("GiftCard")).getMapping(giftCardKey)));
            paymentFields.add(idGiftCardField);

            ImportField priceGiftCardField = new ImportField(giftCardLM.findProperty("price[GiftCard]"));
            paymentProperties.add(new ImportProperty(priceGiftCardField, giftCardLM.findProperty("price[GiftCard]").getMapping(giftCardKey), true));
            paymentFields.add(priceGiftCardField);

            List<List<Object>> dataPaymentGiftCard = new ArrayList<>();
            Set<String> ids = new HashSet();
            for (int j = start; j < finish; j++) {
                SalesInfo sale = salesInfoList.get(j);
                String idReceipt = EquipmentServer.getIdReceipt(sale, options);
                if (!ignoredIdReceipts.contains(idReceipt)) {
                    if (sale.sumGiftCardMap != null && !sale.sumGiftCardMap.isEmpty()) {
                        int i = 0;
                        for (Map.Entry<String, GiftCard> giftCardEntry : sale.sumGiftCardMap.entrySet()) {
                            String idPayment = idReceipt + String.valueOf(3 + i);
                            String numberGiftCard = giftCardEntry.getKey();
                            BigDecimal sumGiftCard = giftCardEntry.getValue().sum;
                            BigDecimal priceGiftCard = giftCardEntry.getValue().price;
                            if (!ids.contains(idPayment) && sumGiftCard != null) {
                                dataPaymentGiftCard.add(Arrays.asList(idPayment, idReceipt, "giftcard", sumGiftCard, 3 + i, numberGiftCard, priceGiftCard));
                                ids.add(idPayment);
                                i++;
                            }
                        }
                    }
                }
            }

            if (!dataPaymentGiftCard.isEmpty())
                new IntegrationService(session, new ImportTable(paymentFields, dataPaymentGiftCard),
                        paymentKeys, paymentProperties).synchronize(true);
        }
    }

    public static void importPaymentGiftCard(BusinessLogics BL, DataSession session, List<SalesInfo> data, Set<String> ignoredIdReceipts, EquipmentServer.EquipmentServerOptions options) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ScriptingLogicsModule giftCardLM = BL.getModule("GiftCard");
        if (giftCardLM != null) {

            List<ImportProperty<?>> paymentGiftCardProperties = new ArrayList<>();
            List<ImportField> paymentGiftCardFields = new ArrayList<>();
            List<ImportKey<?>> paymentGiftCardKeys = new ArrayList<>();

            ImportField idPaymentGiftCardField = new ImportField(giftCardLM.findProperty("id[Payment]"));
            ImportKey<?> paymentGiftCardKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("PaymentGiftCard"), giftCardLM.findProperty("payment[STRING[100]]").getMapping(idPaymentGiftCardField));
            paymentGiftCardKeys.add(paymentGiftCardKey);
            paymentGiftCardProperties.add(new ImportProperty(idPaymentGiftCardField, giftCardLM.findProperty("id[Payment]").getMapping(paymentGiftCardKey)));
            paymentGiftCardFields.add(idPaymentGiftCardField);

            ImportField idReceiptGiftCardField = new ImportField(giftCardLM.findProperty("id[Receipt]"));
            ImportKey<?> receiptGiftCardKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("Receipt"), giftCardLM.findProperty("receipt[STRING[100]]").getMapping(idReceiptGiftCardField));
            paymentGiftCardKeys.add(receiptGiftCardKey);
            paymentGiftCardProperties.add(new ImportProperty(idReceiptGiftCardField, giftCardLM.findProperty("receipt[Payment]").getMapping(paymentGiftCardKey),
                    giftCardLM.object(giftCardLM.findClass("Receipt")).getMapping(receiptGiftCardKey)));
            paymentGiftCardFields.add(idReceiptGiftCardField);

            ImportField sidTypePaymentGiftCardField = new ImportField(giftCardLM.findProperty("sid[PaymentType]"));
            ImportKey<?> paymentGiftCardTypeKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("PaymentType"), giftCardLM.findProperty("typePaymentSID[BPSTRING[10]]").getMapping(sidTypePaymentGiftCardField));
            paymentGiftCardKeys.add(paymentGiftCardTypeKey);
            paymentGiftCardProperties.add(new ImportProperty(sidTypePaymentGiftCardField, giftCardLM.findProperty("paymentType[Payment]").getMapping(paymentGiftCardKey),
                    giftCardLM.object(giftCardLM.findClass("PaymentType")).getMapping(paymentGiftCardTypeKey)));
            paymentGiftCardFields.add(sidTypePaymentGiftCardField);

            ImportField sumPaymentGiftCardField = new ImportField(giftCardLM.findProperty("sum[Payment]"));
            paymentGiftCardProperties.add(new ImportProperty(sumPaymentGiftCardField, giftCardLM.findProperty("sum[Payment]").getMapping(paymentGiftCardKey)));
            paymentGiftCardFields.add(sumPaymentGiftCardField);

            ImportField numberPaymentGiftCardField = new ImportField(giftCardLM.findProperty("number[Payment]"));
            paymentGiftCardProperties.add(new ImportProperty(numberPaymentGiftCardField, giftCardLM.findProperty("number[Payment]").getMapping(paymentGiftCardKey)));
            paymentGiftCardFields.add(numberPaymentGiftCardField);

            ImportField idGiftCardField = new ImportField(giftCardLM.findProperty("id[GiftCard]"));
            ImportKey<?> giftCardKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("GiftCard"), giftCardLM.findProperty("giftCard[STRING[100]]").getMapping(idGiftCardField));
            giftCardKey.skipKey = options.skipGiftCardKeys;
            paymentGiftCardKeys.add(giftCardKey);
            paymentGiftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("id[GiftCard]").getMapping(giftCardKey)));
            paymentGiftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("number[GiftCard]").getMapping(giftCardKey)));
            paymentGiftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("giftCard[PaymentGiftCard]").getMapping(paymentGiftCardKey),
                    giftCardLM.object(giftCardLM.findClass("GiftCard")).getMapping(giftCardKey)));
            paymentGiftCardFields.add(idGiftCardField);

            ImportField priceGiftCardField = new ImportField(giftCardLM.findProperty("price[GiftCard]"));
            paymentGiftCardProperties.add(new ImportProperty(priceGiftCardField, giftCardLM.findProperty("price[GiftCard]").getMapping(giftCardKey), true));
            paymentGiftCardFields.add(priceGiftCardField);

            List<List<Object>> dataPaymentGiftCard = new ArrayList<>();
            Set<String> ids = new HashSet();
            for (SalesInfo sale : data) {
                String idReceipt = EquipmentServer.getIdReceipt(sale, options);
                if (!ignoredIdReceipts.contains(idReceipt)) {
                    if (sale.sumGiftCardMap != null && !sale.sumGiftCardMap.isEmpty()) {
                        int i = 0;
                        for (Map.Entry<String, GiftCard> giftCardEntry : sale.sumGiftCardMap.entrySet()) {
                            String idPayment = idReceipt + String.valueOf(3 + i);
                            String numberGiftCard = giftCardEntry.getKey();
                            BigDecimal sumGiftCard = giftCardEntry.getValue().sum;
                            BigDecimal priceGiftCard = giftCardEntry.getValue().price;
                            if (!ids.contains(idPayment) && sumGiftCard != null) {
                                dataPaymentGiftCard.add(Arrays.asList(idPayment, idReceipt, "giftcard", sumGiftCard, 3 + i, numberGiftCard, priceGiftCard));
                                ids.add(idPayment);
                                i++;
                            }
                        }
                    }
                }
            }

            if (!dataPaymentGiftCard.isEmpty())
                new IntegrationService(session, new ImportTable(paymentGiftCardFields, dataPaymentGiftCard),
                        paymentGiftCardKeys, paymentGiftCardProperties).synchronize(true);
        }
    }
}
