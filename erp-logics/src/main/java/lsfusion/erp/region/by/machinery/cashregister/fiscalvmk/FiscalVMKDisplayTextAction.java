package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.session.DataSession;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;

import static org.apache.commons.lang3.StringUtils.trim;

public class FiscalVMKDisplayTextAction extends InternalAction {
    private final ClassPropertyInterface receiptDetailInterface;

    public FiscalVMKDisplayTextAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptDetailInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {

        DataObject receiptDetailObject = context.getDataKeyValue(receiptDetailInterface);

        try {
            ObjectValue receiptObject = findProperty("receipt[ReceiptDetail]").readClasses(context, receiptDetailObject);
            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context, receiptObject) != null;
            boolean ignoreDisplayText = findProperty("ignoreDisplayTextCurrentCashRegister[]").read(context) != null;
            if (!skipReceipt && !ignoreDisplayText) {

                boolean isUnix = findProperty("isUnix[]").read(context) != null;
                String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context);
                String ip = (String) findProperty("ipCurrentCashRegister[]").read(context);
                String comPort = (String) findProperty("stringComPortCurrentCashRegister[]").read(context);
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context);

                String name = (String) findProperty("boardNameSku[ReceiptDetail]").read(context, receiptDetailObject);
                name = trim(name == null ? (String) findProperty("nameSku[ReceiptDetail]").read(context, receiptDetailObject) : name);
                name = name == null ? "" : name;

                String barcode = (String) findProperty("idBarcode[ReceiptDetail]").read(context, receiptDetailObject);
                BigDecimal quantityValue = (BigDecimal) findProperty("quantity[ReceiptDetail]").read(context, receiptDetailObject);
                double quantity = quantityValue == null ? 0.0 : quantityValue.doubleValue();
                BigDecimal price = (BigDecimal) findProperty("price[ReceiptDetail]").read(context, receiptDetailObject);
                BigDecimal sumValue = (BigDecimal) findProperty("sumReceiptDetail[Receipt]").read(context, (DataObject) receiptObject);
                double sum = sumValue == null ? 0 : sumValue.doubleValue();
                BigDecimal articleDiscSumValue = (BigDecimal) findProperty("discountSum[ReceiptDetail]").read(context, receiptDetailObject);
                double articleDiscSum = articleDiscSumValue == null ? 0 : articleDiscSumValue.doubleValue();
                double bonusSum = getDouble((BigDecimal) findProperty("bonusSum[ReceiptDetail]").read(context, receiptDetailObject));
                double bonusPaid = getDouble((BigDecimal) findProperty("bonusPaid[ReceiptDetail]").read(context, receiptDetailObject));

                String result = (String) context.requestUserInteraction(new FiscalVMKDisplayTextClientAction(isUnix, logPath, ip, comPort, baudRate,
                        new ReceiptItem(false, false, price == null ? BigDecimal.ZERO : price, quantity, barcode, name, sum, articleDiscSum, bonusSum, bonusPaid)));
                if (result != null) {
                    ServerLoggers.systemLogger.error("FiscalVMKDisplayText Error: " + result);
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
