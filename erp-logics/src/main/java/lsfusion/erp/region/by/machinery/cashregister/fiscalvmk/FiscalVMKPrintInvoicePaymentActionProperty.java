package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;

public class FiscalVMKPrintInvoicePaymentActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface invoiceInterface;
    private final ClassPropertyInterface paymentInterface;

    public FiscalVMKPrintInvoicePaymentActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        invoiceInterface = i.next();
        paymentInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            DataObject invoiceObject = context.getDataKeyValue(invoiceInterface);
            DataObject paymentObject = context.getDataKeyValue(paymentInterface);

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister").read(context);
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister").read(context);
            Integer placeNumber = (Integer) findProperty("nppMachineryCurrentCashRegister").read(context);
            BigDecimal maxSum = (BigDecimal) findProperty("maxSumCurrentCashRegister").read(context);

            BigDecimal sumPayment = (BigDecimal) findProperty("costOutContractLedgerInContractLedger").read(context, paymentObject, invoiceObject);
            Integer typePayment = (Integer) findProperty("fiscalTypePayment").read(context, paymentObject);

            if (sumPayment != null && typePayment != null) {
                if (maxSum != null && sumPayment.compareTo(maxSum) > 0) {
                    context.requestUserInteraction(new MessageClientAction("Сумма платежа превышает " + maxSum.intValue() + " рублей", "Ошибка!"));
                    return;
                }
            }
            
            String result = (String) context.requestUserInteraction(new FiscalVMKPrintInvoicePaymentClientAction(baudRate, comPort, placeNumber, null, sumPayment, typePayment));
            findProperty("printReceiptResult").change(result == null ? new DataObject(true) : null, context);
            
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }
}
