package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.erp.integration.universal.PurchaseInvoiceDetail;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.integration.ImportField;
import lsfusion.server.integration.ImportKey;
import lsfusion.server.integration.ImportProperty;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoicePurchaseDeclaration extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoicePurchaseDeclaration(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> userInvoiceDetailKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("PurchaseDeclaration");
        
        if(LM != null && userInvoiceDetailKey != null) {
            
            if (showField(userInvoiceDetailsList, "declaration")) {
                ImportField numberDeclarationField = new ImportField(LM.findProperty("numberDeclaration"));
                ImportKey<?> declarationKey = new ImportKey((ConcreteCustomClass) LM.findClass("Declaration"),
                        LM.findProperty("declarationId").getMapping(numberDeclarationField));
                keys.add(declarationKey);
                props.add(new ImportProperty(numberDeclarationField, LM.findProperty("numberDeclaration").getMapping(declarationKey), getReplaceOnlyNull(defaultColumns, "numberDeclaration")));
                props.add(new ImportProperty(numberDeclarationField, LM.findProperty("idDeclaration").getMapping(declarationKey), getReplaceOnlyNull(defaultColumns, "numberDeclaration")));
                props.add(new ImportProperty(numberDeclarationField, LM.findProperty("declarationUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        object(LM.findClass("Declaration")).getMapping(declarationKey), getReplaceOnlyNull(defaultColumns, "numberDeclaration")));
                fields.add(numberDeclarationField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("declaration"));
            }
            
        }
        
    }
}
