package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.service.ImportField;
import lsfusion.server.physics.dev.integration.service.ImportKey;
import lsfusion.server.physics.dev.integration.service.ImportProperty;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoiceTaxItem extends ImportDefaultPurchaseInvoiceAction {

    public ImportPurchaseInvoiceTaxItem(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext<ClassPropertyInterface> context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props,
                           LinkedHashMap<String, ImportColumnDetail> defaultColumns, List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data,
                           ImportField valueVATUserInvoiceDetailField, ImportKey<?> itemKey, ImportKey<?> VATKey)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ScriptingLogicsModule LM = context.getBL().getModule("TaxItem");

        if (LM != null && valueVATUserInvoiceDetailField != null && itemKey != null && VATKey != null) {
            ObjectValue defaultCountryObject = getDefaultCountryObject(context);
            props.add(new ImportProperty(valueVATUserInvoiceDetailField, LM.findProperty("VAT[Item,Country]").getMapping(itemKey, defaultCountryObject),
                    object(LM.findClass("Range")).getMapping(VATKey), getReplaceOnlyNull(defaultColumns, "valueVAT")));
        }

    }
}
