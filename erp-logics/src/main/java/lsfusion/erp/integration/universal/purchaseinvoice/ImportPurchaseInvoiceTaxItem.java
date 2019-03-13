package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.integration.ImportField;
import lsfusion.server.integration.ImportKey;
import lsfusion.server.integration.ImportProperty;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoiceTaxItem extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoiceTaxItem(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props,
                           LinkedHashMap<String, ImportColumnDetail> defaultColumns, List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data,
                           ImportField valueVATUserInvoiceDetailField, ImportKey<?> itemKey, ImportKey<?> VATKey)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ScriptingLogicsModule LM = context.getBL().getModule("TaxItem");

        if (LM != null && valueVATUserInvoiceDetailField != null && itemKey != null && VATKey != null) {

            ImportField countryVATField = new ImportField(LM.findProperty("name[Country]"));
            ImportKey<?> countryVATKey = new ImportKey((ConcreteCustomClass) LM.findClass("Country"),
                    LM.findProperty("countryName[VARISTRING[50]]").getMapping(countryVATField));
            keys.add(countryVATKey);
            props.add(new ImportProperty(valueVATUserInvoiceDetailField, LM.findProperty("VAT[Item,Country]").getMapping(itemKey, countryVATKey),
                    object(LM.findClass("Range")).getMapping(VATKey), getReplaceOnlyNull(defaultColumns, "valueVAT")));
            fields.add(countryVATField);
            String defaultCountry = getDefaultCountry(context);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(defaultCountry);

        }

    }
}
