package equ.srv;

import equ.api.SalesInfo;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.integration.*;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.util.*;

public class EquipmentServerImport {

    public static void importPayment(BusinessLogics BL, DataSession session, List<SalesInfo> data, Date startDate, Boolean timeId) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ScriptingLogicsModule zReportLM = BL.getModule("ZReport");
        if (zReportLM != null) {

            List<ImportProperty<?>> paymentProperties = new ArrayList<>();

            ImportField idPaymentField = new ImportField(zReportLM.findProperty("id[Payment]"));
            ImportField idReceiptField = new ImportField(zReportLM.findProperty("id[Receipt]"));
            ImportField sidTypePaymentField = new ImportField(zReportLM.findProperty("sid[PaymentType]"));
            ImportField sumPaymentField = new ImportField(zReportLM.findProperty("sum[Payment]"));
            ImportField numberPaymentField = new ImportField(zReportLM.findProperty("number[Payment]"));

            ImportKey<?> paymentKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ZReport.Payment"), zReportLM.findProperty("payment[VARSTRING[100]]").getMapping(idPaymentField));
            ImportKey<?> paymentTypeKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("PaymentType"), zReportLM.findProperty("typePaymentSID[STRING[10]]").getMapping(sidTypePaymentField));
            ImportKey<?> receiptKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("Receipt"), zReportLM.findProperty("receipt[VARSTRING[100]]").getMapping(idReceiptField));

            paymentProperties.add(new ImportProperty(idPaymentField, zReportLM.findProperty("id[Payment]").getMapping(paymentKey)));
            paymentProperties.add(new ImportProperty(sumPaymentField, zReportLM.findProperty("sum[Payment]").getMapping(paymentKey)));
            paymentProperties.add(new ImportProperty(numberPaymentField, zReportLM.findProperty("number[Payment]").getMapping(paymentKey)));
            paymentProperties.add(new ImportProperty(sidTypePaymentField, zReportLM.findProperty("paymentType[Payment]").getMapping(paymentKey),
                    zReportLM.object(zReportLM.findClass("PaymentType")).getMapping(paymentTypeKey)));
            paymentProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receipt[Payment]").getMapping(paymentKey),
                    zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

            List<List<Object>> dataPayment = new ArrayList<>();
            for (SalesInfo sale : data) {
                String idReceipt = sale.getIdReceipt(startDate, timeId);
                if (sale.sumCash != null && sale.sumCash.doubleValue() != 0) {
                    dataPayment.add(Arrays.<Object>asList(idReceipt + "1", idReceipt, "cash", sale.sumCash, 1));
                }
                if (sale.sumCard != null && sale.sumCard.doubleValue() != 0) {
                    dataPayment.add(Arrays.<Object>asList(idReceipt + "2", idReceipt, "card", sale.sumCard, 2));
                }
            }

            if (!dataPayment.isEmpty())
                new IntegrationService(session, new ImportTable(Arrays.asList(idPaymentField, idReceiptField, sidTypePaymentField,
                        sumPaymentField, numberPaymentField), dataPayment), Arrays.asList(paymentKey, paymentTypeKey, receiptKey),
                        paymentProperties).synchronize(true);
        }
    }

    public static void importPaymentGiftCard(BusinessLogics BL, DataSession session, List<SalesInfo> data, Date startDate, Boolean timeId) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ScriptingLogicsModule giftCardLM = BL.getModule("GiftCard");
        if (giftCardLM != null) {

            List<ImportProperty<?>> paymentGiftCardProperties = new ArrayList<>();

            ImportField idPaymentGiftCardField = new ImportField(giftCardLM.findProperty("id[Payment]"));
            ImportField idReceiptGiftCardField = new ImportField(giftCardLM.findProperty("id[Receipt]"));
            ImportField sidTypePaymentGiftCardField = new ImportField(giftCardLM.findProperty("sid[PaymentType]"));
            ImportField sumPaymentGiftCardField = new ImportField(giftCardLM.findProperty("sum[Payment]"));
            ImportField numberPaymentGiftCardField = new ImportField(giftCardLM.findProperty("number[Payment]"));
            ImportField idGiftCardField = new ImportField(giftCardLM.findProperty("id[GiftCard]"));

            ImportKey<?> paymentGiftCardKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("PaymentGiftCard"), giftCardLM.findProperty("payment[VARSTRING[100]]").getMapping(idPaymentGiftCardField));
            ImportKey<?> paymentGiftCardTypeKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("PaymentType"), giftCardLM.findProperty("typePaymentSID[STRING[10]]").getMapping(sidTypePaymentGiftCardField));
            ImportKey<?> receiptGiftCardKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("Receipt"), giftCardLM.findProperty("receipt[VARSTRING[100]]").getMapping(idReceiptGiftCardField));
            ImportKey<?> giftCardKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("GiftCard"), giftCardLM.findProperty("giftCard[VARSTRING[100]]").getMapping(idGiftCardField));

            paymentGiftCardProperties.add(new ImportProperty(idPaymentGiftCardField, giftCardLM.findProperty("id[Payment]").getMapping(paymentGiftCardKey)));
            paymentGiftCardProperties.add(new ImportProperty(sumPaymentGiftCardField, giftCardLM.findProperty("sum[Payment]").getMapping(paymentGiftCardKey)));
            paymentGiftCardProperties.add(new ImportProperty(numberPaymentGiftCardField, giftCardLM.findProperty("number[Payment]").getMapping(paymentGiftCardKey)));
            paymentGiftCardProperties.add(new ImportProperty(sidTypePaymentGiftCardField, giftCardLM.findProperty("paymentType[Payment]").getMapping(paymentGiftCardKey),
                    giftCardLM.object(giftCardLM.findClass("PaymentType")).getMapping(paymentGiftCardTypeKey)));
            paymentGiftCardProperties.add(new ImportProperty(idReceiptGiftCardField, giftCardLM.findProperty("receipt[Payment]").getMapping(paymentGiftCardKey),
                    giftCardLM.object(giftCardLM.findClass("Receipt")).getMapping(receiptGiftCardKey)));

            //потом убрать создание
            paymentGiftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("id[GiftCard]").getMapping(giftCardKey)));
            paymentGiftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("number[GiftCard]").getMapping(giftCardKey)));
            paymentGiftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("giftCard[PaymentGiftCard]").getMapping(paymentGiftCardKey),
                    giftCardLM.object(giftCardLM.findClass("GiftCard")).getMapping(giftCardKey)));

            List<List<Object>> dataPaymentGiftCard = new ArrayList<>();
            Set<String> ids = new HashSet();
            for (SalesInfo sale : data) {
                String idReceipt = sale.getIdReceipt(startDate, timeId);
                if (sale.sumGiftCardMap != null && !sale.sumGiftCardMap.isEmpty()) {
                    int i = 0;
                    for (Map.Entry<String, BigDecimal> giftCardEntry : sale.sumGiftCardMap.entrySet()) {
                        String idPayment = idReceipt + String.valueOf(3 + i);
                        if(!ids.contains(idPayment)) {
                            dataPaymentGiftCard.add(Arrays.<Object>asList(idPayment, idReceipt, "giftcard", giftCardEntry.getValue(), 3 + i, giftCardEntry.getKey()));
                            ids.add(idPayment);
                            i++;
                        }
                    }
                }
            }

            if (!dataPaymentGiftCard.isEmpty())
                new IntegrationService(session, new ImportTable(Arrays.asList(idPaymentGiftCardField, idReceiptGiftCardField, sidTypePaymentGiftCardField,
                        sumPaymentGiftCardField, numberPaymentGiftCardField, idGiftCardField), dataPaymentGiftCard),
                        Arrays.asList(paymentGiftCardKey, paymentGiftCardTypeKey, receiptGiftCardKey, giftCardKey),
                        paymentGiftCardProperties).synchronize(true);
        }
    }
}
