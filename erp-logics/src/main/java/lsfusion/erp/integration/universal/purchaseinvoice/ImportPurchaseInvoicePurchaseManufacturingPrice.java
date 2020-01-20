package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.service.ImportField;
import lsfusion.server.physics.dev.integration.service.ImportKey;
import lsfusion.server.physics.dev.integration.service.ImportProperty;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoicePurchaseManufacturingPrice extends ImportDefaultPurchaseInvoiceAction {

    public ImportPurchaseInvoicePurchaseManufacturingPrice(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext<ClassPropertyInterface> context, List<ImportField> fields, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> userInvoiceDetailKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("PurchaseManufacturingPrice");

        if (LM != null && userInvoiceDetailKey != null) {

            if (showField(userInvoiceDetailsList, "manufacturingPrice")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("manufacturingPrice[UserInvoiceDetail]"), "manufacturingPrice", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("manufacturingPrice"));
            }

        }
        
    }
}
