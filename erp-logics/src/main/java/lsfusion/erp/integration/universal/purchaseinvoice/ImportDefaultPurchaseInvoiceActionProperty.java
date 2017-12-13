package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportDocumentActionProperty;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.logics.scripted.ScriptingModuleErrorLog;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class ImportDefaultPurchaseInvoiceActionProperty extends ImportDocumentActionProperty {

    public ImportDefaultPurchaseInvoiceActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingModuleErrorLog.SemanticError {
        super(LM, valueClass);
    }

    public ImportDefaultPurchaseInvoiceActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingModuleErrorLog.SemanticError {
        super(LM, classes);
    }

    public ImportDefaultPurchaseInvoiceActionProperty(ScriptingLogicsModule LM) throws ScriptingModuleErrorLog.SemanticError {
        super(LM);
    }

    protected Boolean showField(List<PurchaseInvoiceDetail> data, String fieldName) {
        try {
            
            boolean found = false;
            Field fieldValues = PurchaseInvoiceDetail.class.getField("fieldValues");
            for (PurchaseInvoiceDetail entry : data) {
                Map<String, Object> values = (Map<String, Object>) fieldValues.get(entry);
                if(!found) {
                    if (values.containsKey(fieldName))
                        found = true;
                    else
                        break;
                }
                if (values.get(fieldName) != null)
                    return true;
            }
            
            if(!found) {
                Field field = PurchaseInvoiceDetail.class.getField(fieldName);

                for (PurchaseInvoiceDetail entry : data) {
                    if (field.get(entry) != null)
                        return true;
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return true;
        }
        return false;
    }
    
}
