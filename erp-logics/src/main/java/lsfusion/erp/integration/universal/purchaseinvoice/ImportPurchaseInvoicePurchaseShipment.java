package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.integration.ImportField;
import lsfusion.server.integration.ImportKey;
import lsfusion.server.integration.ImportProperty;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoicePurchaseShipment extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoicePurchaseShipment(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> userInvoiceDetailKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("PurchaseShipment");

        if (LM != null && userInvoiceDetailKey != null) {

            if (showField(userInvoiceDetailsList, "expiryDate")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("expiryDate[UserInvoiceDetail]"), "expiryDate", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("expiryDate"));
            }

            if (showField(userInvoiceDetailsList, "manufactureDate")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("manufactureDate[UserInvoiceDetail]"), "manufactureDate", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("manufactureDate"));
            }

            if (showField(userInvoiceDetailsList, "shipmentPrice")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("shipmentPrice[UserInvoiceDetail]"), "shipmentPrice", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("shipmentPrice"));
            }

            if (showField(userInvoiceDetailsList, "shipmentSum")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("shipmentSum[UserInvoiceDetail]"), "shipmentSum", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("shipmentSum"));
            }

        }
        
    }
}
