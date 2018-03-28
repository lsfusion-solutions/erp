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
                ImportKey<?> importCodeKey = new ImportKey((ConcreteCustomClass) LM.findClass("ImportCode"),
                        LM.findProperty("manufacturerImportCode[VARSTRING[100]]").getMapping(manufacturerIdImportCodeField));
                keys.add(importCodeKey);
                ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) LM.findClass("Manufacturer"),
                        LM.findProperty("manufacturerIdImportCode[VARSTRING[100]]").getMapping(manufacturerIdImportCodeField));
                keys.add(manufacturerKey);
                props.add(new ImportProperty(manufacturerIdImportCodeField, LM.findProperty("manufacturerId[ImportCode]").getMapping(importCodeKey)));
                props.add(new ImportProperty(manufacturerIdImportCodeField, LM.findProperty("manufacturer[ImportCode]").getMapping(importCodeKey),
                        object(LM.findClass("Manufacturer")).getMapping(manufacturerKey), getReplaceOnlyNull(defaultColumns, "importCodeManufacturer")));
                props.add(new ImportProperty(manufacturerIdImportCodeField, LM.findProperty("manufacturer[Item]").getMapping(itemKey),
                        object(LM.findClass("Manufacturer")).getMapping(manufacturerKey), getReplaceOnlyNull(defaultColumns, "importCodeManufacturer")));
                fields.add(manufacturerIdImportCodeField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("importCodeManufacturer"));

                if (showField(userInvoiceDetailsList, "nameManufacturer")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("name[Manufacturer]"), "nameManufacturer", manufacturerKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameManufacturer"));
                }
            }

            if (showField(userInvoiceDetailsList, "importCodeCountry")) {
                ImportField countryIdImportCodeField = new ImportField(LM.findProperty("countryId[ImportCode]"));
                ImportKey<?> importCodeKey = new ImportKey((ConcreteCustomClass) LM.findClass("ImportCode"),
                        LM.findProperty("countryImportCode[VARSTRING[100]]").getMapping(countryIdImportCodeField));
                keys.add(importCodeKey);
                ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) LM.findClass("Country"),
                        LM.findProperty("countryIdImportCode[VARSTRING[100]]").getMapping(countryIdImportCodeField));
                keys.add(countryKey);
                props.add(new ImportProperty(countryIdImportCodeField, LM.findProperty("countryId[ImportCode]").getMapping(importCodeKey)));
                props.add(new ImportProperty(countryIdImportCodeField, LM.findProperty("country[ImportCode]").getMapping(importCodeKey),
                        object(LM.findClass("Country")).getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "importCodeCountry")));
                props.add(new ImportProperty(countryIdImportCodeField, LM.findProperty("country[Item]").getMapping(itemKey),
                        object(LM.findClass("Country")).getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "importCodeCountry")));
                fields.add(countryIdImportCodeField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("importCodeCountry"));

                if (showField(userInvoiceDetailsList, "nameCountry")) {
                    ImportField nameCountryField = new ImportField(LM.findProperty("name[Country]"));
                    props.add(new ImportProperty(nameCountryField, LM.findProperty("name[Country]").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "nameCountry")));
                    fields.add(nameCountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameCountry"));
                }
            }

            if (showField(userInvoiceDetailsList, "importCodeUOM")) {
                ImportField UOMIdImportCodeField = new ImportField(LM.findProperty("UOMId[ImportCode]"));
                ImportKey<?> importCodeKey = new ImportKey((ConcreteCustomClass) LM.findClass("ImportCode"),
                        LM.findProperty("UOMImportCode[VARSTRING[100]]").getMapping(UOMIdImportCodeField));
                keys.add(importCodeKey);
                ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) LM.findClass("UOM"),
                        LM.findProperty("UOMIdImportCode[VARSTRING[100]]").getMapping(UOMIdImportCodeField));
                keys.add(UOMKey);
                props.add(new ImportProperty(UOMIdImportCodeField, LM.findProperty("UOMId[ImportCode]").getMapping(importCodeKey)));
                props.add(new ImportProperty(UOMIdImportCodeField, LM.findProperty("UOM[ImportCode]").getMapping(importCodeKey),
                        object(LM.findClass("UOM")).getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "importCodeUOM")));
                props.add(new ImportProperty(UOMIdImportCodeField, LM.findProperty("UOM[Item]").getMapping(itemKey),
                        object(LM.findClass("UOM")).getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "importCodeUOM")));
                fields.add(UOMIdImportCodeField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("importCodeUOM"));
            }

        }
        
    }
}