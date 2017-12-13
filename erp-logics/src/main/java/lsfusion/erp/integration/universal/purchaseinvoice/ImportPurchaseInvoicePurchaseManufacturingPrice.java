package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.integration.ImportField;
import lsfusion.server.integration.ImportKey;
import lsfusion.server.integration.ImportProperty;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.logics.scripted.ScriptingModuleErrorLog;

import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoicePurchaseManufacturingPrice extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoicePurchaseManufacturingPrice(ScriptingLogicsModule LM) throws ScriptingModuleErrorLog.SemanticError {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> userInvoiceDetailKey) throws ScriptingModuleErrorLog.SemanticError {
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
