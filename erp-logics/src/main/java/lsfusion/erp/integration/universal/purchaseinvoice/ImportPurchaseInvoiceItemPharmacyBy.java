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

public class ImportPurchaseInvoiceItemPharmacyBy extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoiceItemPharmacyBy(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> itemKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("ItemPharmacyBy");

        if (LM != null && itemKey != null) {

            if (showField(userInvoiceDetailsList, "pharmacyPriceGroupItem")) {
                ImportField idPharmacyPriceGroupField = new ImportField(LM.findProperty("idPharmacyPriceGroup"));
                ImportKey<?> pharmacyPriceGroupKey = new ImportKey((ConcreteCustomClass) LM.findClass("PharmacyPriceGroup"),
                        LM.findProperty("pharmacyPriceGroupId").getMapping(idPharmacyPriceGroupField));
                keys.add(pharmacyPriceGroupKey);
                props.add(new ImportProperty(idPharmacyPriceGroupField, LM.findProperty("idPharmacyPriceGroup").getMapping(pharmacyPriceGroupKey), getReplaceOnlyNull(defaultColumns, "idPharmacyPriceGroup")));
                props.add(new ImportProperty(idPharmacyPriceGroupField, LM.findProperty("namePharmacyPriceGroup").getMapping(pharmacyPriceGroupKey), getReplaceOnlyNull(defaultColumns, "idPharmacyPriceGroup")));
                props.add(new ImportProperty(idPharmacyPriceGroupField, LM.findProperty("pharmacyPriceGroupItem").getMapping(itemKey),
                        object(LM.findClass("PharmacyPriceGroup")).getMapping(pharmacyPriceGroupKey), getReplaceOnlyNull(defaultColumns, "idPharmacyPriceGroup")));
                fields.add(idPharmacyPriceGroupField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("pharmacyPriceGroupItem"));
            }

        }
        
    }
}
