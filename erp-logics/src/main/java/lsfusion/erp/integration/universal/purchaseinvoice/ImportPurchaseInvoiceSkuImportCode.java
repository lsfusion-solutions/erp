package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.physics.dev.integration.service.ImportField;
import lsfusion.server.physics.dev.integration.service.ImportKey;
import lsfusion.server.physics.dev.integration.service.ImportProperty;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoiceSkuImportCode extends ImportDefaultPurchaseInvoiceAction {

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
                        LM.findProperty("manufacturerImportCode[STRING[100]]").getMapping(manufacturerIdImportCodeField));
                keys.add(importCodeKey);
                ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) LM.findClass("Manufacturer"),
                        LM.findProperty("manufacturerIdImportCode[STRING[100]]").getMapping(manufacturerIdImportCodeField));
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
                    ImportField nameManufacturerField = new ImportField(LM.findProperty("name[Manufacturer]"));
                    props.add(new ImportProperty(nameManufacturerField, LM.findProperty("name[Manufacturer]").getMapping(manufacturerKey), true));
                    fields.add(nameManufacturerField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameManufacturer"));
                }
            }

            if (showField(userInvoiceDetailsList, "importCodeUOM")) {
                ImportField UOMIdImportCodeField = new ImportField(LM.findProperty("UOMId[ImportCode]"));
                ImportKey<?> importCodeKey = new ImportKey((ConcreteCustomClass) LM.findClass("ImportCode"),
                        LM.findProperty("UOMImportCode[STRING[100]]").getMapping(UOMIdImportCodeField));
                keys.add(importCodeKey);
                ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) LM.findClass("UOM"),
                        LM.findProperty("UOMIdImportCode[STRING[100]]").getMapping(UOMIdImportCodeField));
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