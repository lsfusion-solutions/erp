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

public class ImportPurchaseInvoiceSkuImportCode extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoiceSkuImportCode(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, ImportKey<?> itemKey, List<List<Object>> data) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("SkuImportCode");
        
        if(LM != null) {

            if (showField(userInvoiceDetailsList, "importCodeManufacturer")) {
                ImportField manufacturerIdImportCodeField = new ImportField(LM.findProperty("manufacturerId[ImportCode]"));
                ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) LM.findClass("Manufacturer"),
                        LM.findProperty("manufacturerIdImportCode[VARSTRING[100]]").getMapping(manufacturerIdImportCodeField));
                keys.add(manufacturerKey);
                props.add(new ImportProperty(manufacturerIdImportCodeField, LM.findProperty("manufacturer[Item]").getMapping(itemKey),
                        object(LM.findClass("Manufacturer")).getMapping(manufacturerKey), getReplaceOnlyNull(defaultColumns, "importCodeManufacturer")));
                fields.add(manufacturerIdImportCodeField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("importCodeManufacturer"));
            }

            if (showField(userInvoiceDetailsList, "importCodeCountry")) {
                ImportField countryIdImportCodeField = new ImportField(LM.findProperty("countryId[ImportCode]"));
                ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) LM.findClass("Country"),
                        LM.findProperty("countryIdImportCode[VARSTRING[100]]").getMapping(countryIdImportCodeField));
                keys.add(countryKey);
                props.add(new ImportProperty(countryIdImportCodeField, LM.findProperty("country[Item]").getMapping(itemKey),
                        object(LM.findClass("Country")).getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "importCodeCountry")));
                fields.add(countryIdImportCodeField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("importCodeCountry"));
            }

            if (showField(userInvoiceDetailsList, "importCodeUOM")) {
                ImportField UOMIdImportCodeField = new ImportField(LM.findProperty("UOMId[ImportCode]"));
                ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) LM.findClass("UOM"),
                        LM.findProperty("UOMIdImportCode[VARSTRING[100]]").getMapping(UOMIdImportCodeField));
                keys.add(UOMKey);
                props.add(new ImportProperty(UOMIdImportCodeField, LM.findProperty("UOM[Item]").getMapping(itemKey),
                        object(LM.findClass("UOM")).getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "importCodeUOM")));
                fields.add(UOMIdImportCodeField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("importCodeUOM"));
            }

        }
        
    }
}