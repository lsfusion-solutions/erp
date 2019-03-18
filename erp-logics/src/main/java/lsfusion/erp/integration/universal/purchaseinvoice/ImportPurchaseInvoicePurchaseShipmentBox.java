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

public class ImportPurchaseInvoicePurchaseShipmentBox extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoicePurchaseShipmentBox(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> userInvoiceDetailKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("PurchaseShipmentBox");
        
        if(LM != null && userInvoiceDetailKey != null) {

            if (showField(userInvoiceDetailsList, "idBox")) {
                ImportField idBoxField = new ImportField(LM.findProperty("id[Box]"));
                ImportKey<?> boxKey = new ImportKey((ConcreteCustomClass) LM.findClass("Box"),
                        LM.findProperty("box[VARSTRING[100]]").getMapping(idBoxField));
                keys.add(boxKey);
                props.add(new ImportProperty(idBoxField, LM.findProperty("id[Box]").getMapping(boxKey), getReplaceOnlyNull(defaultColumns, "idBox")));
                props.add(new ImportProperty(idBoxField, LM.findProperty("box[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClass("Box")).getMapping(boxKey), getReplaceOnlyNull(defaultColumns, "idBox")));
                fields.add(idBoxField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idBox"));

                if (showField(userInvoiceDetailsList, "nameBox")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("name[Box]"), "nameBox", boxKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameBox"));
                }
            }
            
        }
        
    }
}
