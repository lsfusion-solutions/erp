package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.service.ImportField;
import lsfusion.server.physics.dev.integration.service.ImportKey;
import lsfusion.server.physics.dev.integration.service.ImportProperty;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoicePurchaseDeclaration extends ImportDefaultPurchaseInvoiceAction {

    public ImportPurchaseInvoicePurchaseDeclaration(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext<ClassPropertyInterface> context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> userInvoiceDetailKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("PurchaseDeclaration");
        
        if(LM != null && userInvoiceDetailKey != null) {
            
            if (showField(userInvoiceDetailsList, "declaration")) {
                ImportField numberDeclarationField = new ImportField(LM.findProperty("number[Declaration]"));
                ImportKey<?> declarationKey = new ImportKey((ConcreteCustomClass) LM.findClass("Declaration"),
                        LM.findProperty("declaration[STRING[100]]").getMapping(numberDeclarationField));
                keys.add(declarationKey);
                props.add(new ImportProperty(numberDeclarationField, LM.findProperty("number[Declaration]").getMapping(declarationKey), getReplaceOnlyNull(defaultColumns, "numberDeclaration")));
                props.add(new ImportProperty(numberDeclarationField, LM.findProperty("id[Declaration]").getMapping(declarationKey), getReplaceOnlyNull(defaultColumns, "numberDeclaration")));
                props.add(new ImportProperty(numberDeclarationField, LM.findProperty("declaration[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                        object(LM.findClass("Declaration")).getMapping(declarationKey), getReplaceOnlyNull(defaultColumns, "numberDeclaration")));
                fields.add(numberDeclarationField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("declaration"));
            }
            
        }
        
    }
}
