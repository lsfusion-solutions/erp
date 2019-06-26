package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.physics.dev.integration.service.ImportField;
import lsfusion.server.physics.dev.integration.service.ImportKey;
import lsfusion.server.physics.dev.integration.service.ImportProperty;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoiceCustomsGroupArticle extends ImportDefaultPurchaseInvoiceAction {

    public ImportPurchaseInvoiceCustomsGroupArticle(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> itemKey, ImportKey<?> articleKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("CustomsGroupArticle");

        if (LM != null && itemKey != null && articleKey != null) {

            if (showField(userInvoiceDetailsList, "originalCustomsGroupItem")) {
                ImportField originalCustomsGroupItemField = new ImportField(LM.findProperty("originalCustomsGroup[Item]"));
                props.add(new ImportProperty(originalCustomsGroupItemField, LM.findProperty("originalCustomsGroup[Item]").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "originalCustomsGroupItem")));
                props.add(new ImportProperty(originalCustomsGroupItemField, LM.findProperty("originalCustomsGroup[Article]").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "originalCustomsGroupItem")));
                fields.add(originalCustomsGroupItemField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("originalCustomsGroupItem"));
            }

        }
        
    }
}
