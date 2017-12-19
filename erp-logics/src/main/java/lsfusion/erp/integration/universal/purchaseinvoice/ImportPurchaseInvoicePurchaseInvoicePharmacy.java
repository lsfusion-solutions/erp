package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.integration.ImportField;
import lsfusion.server.integration.ImportKey;
import lsfusion.server.integration.ImportProperty;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoicePurchaseInvoicePharmacy extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoicePurchaseInvoicePharmacy(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> userInvoiceDetailKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("PurchaseInvoicePharmacy");

        if (LM != null && userInvoiceDetailKey != null) {

            if (showField(userInvoiceDetailsList, "importCountryBatch")) {
                ImportField nameImportCountryField = new ImportField(LM.findProperty("name[Country]"));
                ImportKey<?> importCountryKey = new ImportKey((ConcreteCustomClass) LM.findClass("Country"),
                        LM.findProperty("countryName[VARISTRING[50]]").getMapping(nameImportCountryField));
                keys.add(importCountryKey);
                props.add(new ImportProperty(nameImportCountryField, LM.findProperty("name[Country]").getMapping(importCountryKey), getReplaceOnlyNull(defaultColumns, "importCountryBatch")));
                props.add(new ImportProperty(nameImportCountryField, LM.findProperty("importCountry[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                        object(LM.findClass("Country")).getMapping(importCountryKey), getReplaceOnlyNull(defaultColumns, "importCountryBatch")));
                fields.add(nameImportCountryField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("importCountryBatch"));
            }

            if (showField(userInvoiceDetailsList, "seriesPharmacy")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("seriesPharmacy[UserInvoiceDetail]"), "seriesPharmacy", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("seriesPharmacy"));
            }

            if (showField(userInvoiceDetailsList, "contractPrice")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("contractPrice[UserInvoiceDetail]"), "contractPrice", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("contractPrice"));
            }

        }
        
    }
}
