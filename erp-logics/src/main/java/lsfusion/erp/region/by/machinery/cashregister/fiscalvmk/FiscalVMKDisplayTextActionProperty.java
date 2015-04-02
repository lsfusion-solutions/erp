package lsfusion.erp.region.by.machinery.cashregister.fiscalvmk;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;

public class FiscalVMKDisplayTextActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptDetailInterface;

    public FiscalVMKDisplayTextActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptDetailInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataSession session = context.getSession();
        DataObject receiptDetailObject = context.getDataKeyValue(receiptDetailInterface);

        try {
            ObjectValue receiptObject = findProperty("receiptReceiptDetail").readClasses(session, receiptDetailObject);
            boolean skipReceipt = findProperty("fiscalSkipReceipt").read(context.getSession(), receiptObject) != null;
            if (!skipReceipt) {

                Integer comPort = (Integer) findProperty("comPortCurrentCashRegister").read(session);
                Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister").read(session);

                String name = (String) findProperty("boardNameSkuReceiptDetail").read(session, receiptDetailObject);
                name = trim(name == null ? (String) findProperty("nameSkuReceiptDetail").read(session, receiptDetailObject) : name);
                name = name == null ? "" : name;

                String barcode = (String) findProperty("idBarcodeReceiptDetail").read(session, receiptDetailObject);
                BigDecimal quantityValue = (BigDecimal) findProperty("quantityReceiptDetail").read(session, receiptDetailObject);
                double quantity = quantityValue == null ? 0.0 : quantityValue.doubleValue();
                BigDecimal priceValue = (BigDecimal) findProperty("priceReceiptDetail").read(session, receiptDetailObject);
                long price = priceValue == null ? 0 : priceValue.longValue();
                BigDecimal sumValue = (BigDecimal) findProperty("sumReceiptDetailReceipt").read(session, (DataObject) receiptObject);
                long sum = sumValue == null ? 0 : sumValue.longValue();
                BigDecimal articleDiscSumValue = (BigDecimal) findProperty("discountSumReceiptDetail").read(session, receiptDetailObject);
                long articleDiscSum = articleDiscSumValue == null ? 0 : articleDiscSumValue.longValue();

                String result = (String) context.requestUserInteraction(new FiscalVMKDisplayTextClientAction(baudRate, comPort, new ReceiptItem(false, price, quantity, barcode, name, sum, articleDiscSum)));
                if (result != null)
                    context.requestUserInteraction(new MessageClientAction(result, "Ошибка"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }


    }

    protected String trim(String input) {
        return input == null ? null : input.trim();
    }
}
