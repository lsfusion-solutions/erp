package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.integration.ImportField;
import lsfusion.server.integration.ImportKey;
import lsfusion.server.integration.ImportProperty;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoicePurchaseCompliance extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoicePurchaseCompliance(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> userInvoiceDetailKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("PurchaseCompliance");
        
        if(LM != null && userInvoiceDetailKey != null) {
            
            if (showField(userInvoiceDetailsList, "numberCompliance")) {
                ImportField numberComplianceField = new ImportField(LM.findProperty("number[Compliance]"));
                ImportKey<?> complianceKey = new ImportKey((ConcreteCustomClass) LM.findClass("Compliance"),
                        LM.findProperty("compliance[STRING[100]]").getMapping(numberComplianceField));
                keys.add(complianceKey);
                props.add(new ImportProperty(numberComplianceField, LM.findProperty("number[Compliance]").getMapping(complianceKey), getReplaceOnlyNull(defaultColumns, "numberCompliance")));
                props.add(new ImportProperty(numberComplianceField, LM.findProperty("compliance[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                        object(LM.findClass("Compliance")).getMapping(complianceKey), getReplaceOnlyNull(defaultColumns, "numberCompliance")));
                fields.add(numberComplianceField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("numberCompliance"));


                if (showField(userInvoiceDetailsList, "dateCompliance")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("date[Compliance]"), "dateCompliance", complianceKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("dateCompliance"));

                    addDataField(props, fields, defaultColumns, LM.findProperty("fromDate[Compliance]"), "dateCompliance", complianceKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("dateCompliance"));
                }
            }
            
        }
        
    }
}
