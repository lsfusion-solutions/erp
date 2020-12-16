package lsfusion.erp.region.by.machinery.cashregister.fiscalsento;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;

import static org.apache.commons.lang3.StringUtils.trim;

public class FiscalSentoDisplayTextAction extends InternalAction {
    private final ClassPropertyInterface receiptDetailInterface;

    public FiscalSentoDisplayTextAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        receiptDetailInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {

        DataObject receiptDetailObject = context.getDataKeyValue(receiptDetailInterface);

        try {
            ObjectValue receiptObject = findProperty("receipt[ReceiptDetail]").readClasses(context, receiptDetailObject);
            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context, receiptObject) != null;
            boolean ignoreDisplayText = findProperty("ignoreDisplayTextCurrentCashRegister[]").read(context) != null;
            if (!skipReceipt && !ignoreDisplayText) {

                String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
                String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);

                String name = (String) findProperty("boardNameSku[ReceiptDetail]").read(context, receiptDetailObject);
                name = trim(name == null ? (String) findProperty("nameSku[ReceiptDetail]").read(context, receiptDetailObject) : name);
                name = name == null ? "" : name;

                String barcode = (String) findProperty("idBarcode[ReceiptDetail]").read(context, receiptDetailObject);
                double quantity = getDouble((BigDecimal) findProperty("quantity[ReceiptDetail]").read(context, receiptDetailObject));
                BigDecimal price = (BigDecimal) findProperty("price[ReceiptDetail]").read(context, receiptDetailObject);
                double sum = getDouble((BigDecimal) findProperty("sumReceiptDetail[Receipt]").read(context, (DataObject) receiptObject));
                double articleDiscSum = getDouble((BigDecimal) findProperty("discountSum[ReceiptDetail]").read(context, receiptDetailObject));

                String result = (String) context.requestUserInteraction(new FiscalSentoDisplayTextClientAction(false, logPath, comPort, baudRate,
                        new ReceiptItem(false, price == null ? BigDecimal.ZERO : price, quantity, barcode, name, sum, articleDiscSum, null)));
                if (result != null) {
                    ServerLoggers.systemLogger.error("FiscalSentoDisplayText Error: " + result);
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
                }
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }

    private double getDouble(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }
}
