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

public class ImportPurchaseInvoiceItemFashion extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoiceItemFashion(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> itemKey, ImportKey<?> articleKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("ItemFashion");

        if (LM != null && itemKey != null && articleKey != null) {

            if (showField(userInvoiceDetailsList, "idSeason")) {
                ImportField idSeasonField = new ImportField(LM.findProperty("idSeason"));
                ImportKey<?> seasonKey = new ImportKey((ConcreteCustomClass) LM.findClass("Season"),
                        LM.findProperty("seasonId").getMapping(idSeasonField));
                keys.add(seasonKey);
                props.add(new ImportProperty(idSeasonField, LM.findProperty("idSeason").getMapping(seasonKey), getReplaceOnlyNull(defaultColumns, "idSeason")));
                props.add(new ImportProperty(idSeasonField, LM.findProperty("seasonArticle").getMapping(articleKey),
                        object(LM.findClass("Season")).getMapping(seasonKey), getReplaceOnlyNull(defaultColumns, "idSeason")));
                props.add(new ImportProperty(idSeasonField, LM.findProperty("seasonItem").getMapping(itemKey),
                        object(LM.findClass("Season")).getMapping(seasonKey), getReplaceOnlyNull(defaultColumns, "idSeason")));
                fields.add(idSeasonField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idSeason"));

                if (showField(userInvoiceDetailsList, "nameSeason")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("nameSeason"), "nameSeason", seasonKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameSeason"));
                }
            }

            if (showField(userInvoiceDetailsList, "idCollection")) {
                ImportField idCollectionField = new ImportField(LM.findProperty("idCollection"));
                ImportKey<?> collectionKey = new ImportKey((ConcreteCustomClass) LM.findClass("Collection"),
                        LM.findProperty("collectionId").getMapping(idCollectionField));
                keys.add(collectionKey);
                props.add(new ImportProperty(idCollectionField, LM.findProperty("idCollection").getMapping(collectionKey), getReplaceOnlyNull(defaultColumns, "idCollection")));
                props.add(new ImportProperty(idCollectionField, LM.findProperty("collectionArticle").getMapping(articleKey),
                        object(LM.findClass("Collection")).getMapping(collectionKey), getReplaceOnlyNull(defaultColumns, "idCollection")));
                fields.add(idCollectionField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idCollection"));

                if (showField(userInvoiceDetailsList, "nameCollection")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("nameCollection"), "nameCollection", collectionKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameCollection"));
                }
            }

            if (showField(userInvoiceDetailsList, "idSeasonYear")) {
                ImportField idSeasonYearField = new ImportField(LM.findProperty("idSeasonYear"));
                ImportKey<?> seasonYearKey = new ImportKey((ConcreteCustomClass) LM.findClass("SeasonYear"),
                        LM.findProperty("seasonYearId").getMapping(idSeasonYearField));
                keys.add(seasonYearKey);
                props.add(new ImportProperty(idSeasonYearField, LM.findProperty("idSeasonYear").getMapping(seasonYearKey), getReplaceOnlyNull(defaultColumns, "idSeasonYear")));
                props.add(new ImportProperty(idSeasonYearField, LM.findProperty("nameSeasonYear").getMapping(seasonYearKey), getReplaceOnlyNull(defaultColumns, "idSeasonYear")));
                props.add(new ImportProperty(idSeasonYearField, LM.findProperty("seasonYearArticle").getMapping(articleKey),
                        object(LM.findClass("SeasonYear")).getMapping(seasonYearKey), getReplaceOnlyNull(defaultColumns, "idSeasonYear")));
                props.add(new ImportProperty(idSeasonYearField, LM.findProperty("seasonYearItem").getMapping(itemKey),
                        object(LM.findClass("SeasonYear")).getMapping(seasonYearKey), getReplaceOnlyNull(defaultColumns, "idSeasonYear")));
                fields.add(idSeasonYearField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idSeasonYear"));
            }

        }
        
    }
}
