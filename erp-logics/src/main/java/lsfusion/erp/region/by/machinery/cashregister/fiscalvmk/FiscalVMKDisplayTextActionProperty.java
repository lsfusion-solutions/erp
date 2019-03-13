package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;

import static org.apache.commons.lang3.StringUtils.trim;

public class FiscalVMKDisplayTextActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptDetailInterface;

    public FiscalVMKDisplayTextActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptDetailInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataSession session = context.getSession();
        DataObject receiptDetailObject = context.getDataKeyValue(receiptDetailInterface);

        try {
            ObjectValue receiptObject = findProperty("receipt[ReceiptDetail]").readClasses(session, receiptDetailObject);
            boolean skipReceipt = findProperty("fiscalSkip[Receipt]").read(context.getSession(), receiptObject) != null;
            boolean ignoreDisplayText = findProperty("ignoreDisplayTextCurrentCashRegister[]").read(context) != null;
            if (!skipReceipt && !ignoreDisplayText) {

                String logPath = (String) findProperty("logPathCurrentCashRegister[]").read(context.getSession());
                String ip = (String) findProperty("ipCurrentCashRegister[]").read(context.getSession());
                Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(session);
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(session);

                String name = (String) findProperty("boardNameSku[ReceiptDetail]").read(session, receiptDetailObject);
                name = trim(name == null ? (String) findProperty("nameSku[ReceiptDetail]").read(session, receiptDetailObject) : name);
                name = name == null ? "" : name;

                String barcode = (String) findProperty("idBarcode[ReceiptDetail]").read(session, receiptDetailObject);
                BigDecimal quantityValue = (BigDecimal) findProperty("quantity[ReceiptDetail]").read(session, receiptDetailObject);
                double quantity = quantityValue == null ? 0.0 : quantityValue.doubleValue();
                BigDecimal price = (BigDecimal) findProperty("price[ReceiptDetail]").read(session, receiptDetailObject);
                BigDecimal sumValue = (BigDecimal) findProperty("sumReceiptDetail[Receipt]").read(session, (DataObject) receiptObject);
                double sum = sumValue == null ? 0 : sumValue.doubleValue();
                BigDecimal articleDiscSumValue = (BigDecimal) findProperty("discountSum[ReceiptDetail]").read(session, receiptDetailObject);
                double articleDiscSum = articleDiscSumValue == null ? 0 : articleDiscSumValue.doubleValue();
                double bonusSum = getDouble((BigDecimal) findProperty("bonusSum[ReceiptDetail]").read(session, receiptDetailObject));
                double bonusPaid = getDouble((BigDecimal) findProperty("bonusPaid[ReceiptDetail]").read(session, receiptDetailObject));

                String result = (String) context.requestUserInteraction(new FiscalVMKDisplayTextClientAction(logPath, ip, comPort, baudRate,
                        new ReceiptItem(false, price == null ? BigDecimal.ZERO : price, quantity, barcode, name, sum, articleDiscSum, bonusSum, bonusPaid)));
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
