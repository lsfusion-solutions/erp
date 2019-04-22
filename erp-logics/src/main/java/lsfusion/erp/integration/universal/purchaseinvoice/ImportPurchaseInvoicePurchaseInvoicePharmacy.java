package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.classes.user.CustomClass;
import lsfusion.server.physics.dev.integration.service.ImportField;
import lsfusion.server.physics.dev.integration.service.ImportKey;
import lsfusion.server.physics.dev.integration.service.ImportProperty;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoicePurchaseInvoicePharmacy extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoicePurchaseInvoicePharmacy(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> userInvoiceDetailKey, String countryKeyType, boolean preImportCountries) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("PurchaseInvoicePharmacy");

        if (LM != null && userInvoiceDetailKey != null) {

            if (showField(userInvoiceDetailsList, "importCountryBatch")) {

                if(countryKeyType != null) {
                    ScriptingLogicsModule skuImportCodeLM = context.getBL().getModule("SkuImportCode");
                    ImportField sidOrigin2CountryField = new ImportField(LM.findProperty("sidOrigin2[Country]"));
                    ImportField nameCountryField = new ImportField(LM.findProperty("name[Country]"));
                    ImportField nameOriginCountryField = new ImportField(LM.findProperty("nameOrigin[Country]"));
                    ImportField countryIdImportCodeField = skuImportCodeLM != null ? new ImportField(skuImportCodeLM.findProperty("countryId[ImportCode]")) : null;

                    ImportField countryField = null;
                    ImportKey<?> importCountryKey = null;
                    switch (countryKeyType) {
                        case "sidOrigin2Country":
                            countryField = sidOrigin2CountryField;
                            importCountryKey = new ImportKey((CustomClass) LM.findClass("Country"), LM.findProperty("countrySIDOrigin2[BPSTRING[2]]").getMapping(countryField));
                            keys.add(importCountryKey);
                            props.add(new ImportProperty(countryField, LM.findProperty("sidOrigin2[Country]").getMapping(importCountryKey), getReplaceOnlyNull(defaultColumns, "importCountryBatch")));
                            break;
                        case "nameCountry":
                            countryField = nameCountryField;
                            importCountryKey = new ImportKey((CustomClass) LM.findClass("Country"), LM.findProperty("countryName[ISTRING[50]]").getMapping(countryField));
                            keys.add(importCountryKey);
                            props.add(new ImportProperty(countryField, LM.findProperty("name[Country]").getMapping(importCountryKey), getReplaceOnlyNull(defaultColumns, "importCountryBatch")));
                            break;
                        case "nameOriginCountry":
                            countryField = nameOriginCountryField;
                            importCountryKey = new ImportKey((CustomClass) LM.findClass("Country"), LM.findProperty("countryOrigin[ISTRING[50]]").getMapping(countryField));
                            keys.add(importCountryKey);
                            props.add(new ImportProperty(countryField, LM.findProperty("nameOrigin[Country]").getMapping(importCountryKey), getReplaceOnlyNull(defaultColumns, "importCountryBatch")));
                            break;
                        case "importCodeCountry":
                            if(skuImportCodeLM != null) {
                                countryField = countryIdImportCodeField;
                                importCountryKey = new ImportKey((CustomClass) LM.findClass("Country"), skuImportCodeLM.findProperty("countryIdImportCode[STRING[100]]").getMapping(countryField));
                                importCountryKey.skipKey = preImportCountries;
                                keys.add(importCountryKey);

                                ImportKey<?> importCodeKey = new ImportKey((ConcreteCustomClass) skuImportCodeLM.findClass("ImportCode"),
                                        skuImportCodeLM.findProperty("countryImportCode[STRING[100]]").getMapping(countryIdImportCodeField));
                                importCodeKey.skipKey = preImportCountries;
                                keys.add(importCodeKey);
                                props.add(new ImportProperty(countryField, skuImportCodeLM.findProperty("countryId[ImportCode]").getMapping(importCodeKey)));
                                props.add(new ImportProperty(countryField, LM.findProperty("name[Country]").getMapping(importCountryKey),true));
                                props.add(new ImportProperty(countryIdImportCodeField, skuImportCodeLM.findProperty("country[ImportCode]").getMapping(importCodeKey),
                                        object(skuImportCodeLM.findClass("Country")).getMapping(importCountryKey), getReplaceOnlyNull(defaultColumns, "importCountryBatch")));
                                break;
                            }
                    }
                    if(countryField != null) {
                        props.add(new ImportProperty(countryField, LM.findProperty("importCountry[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                                object(LM.findClass("Country")).getMapping(importCountryKey), getReplaceOnlyNull(defaultColumns, "importCountryBatch")));
                        fields.add(countryField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("importCountryBatch"));
                    }

                } else {
                    ImportField nameImportCountryField = new ImportField(LM.findProperty("name[Country]"));
                    ImportKey<?> importCountryKey = new ImportKey((ConcreteCustomClass) LM.findClass("Country"),
                            LM.findProperty("countryName[ISTRING[50]]").getMapping(nameImportCountryField));
                    keys.add(importCountryKey);
                    props.add(new ImportProperty(nameImportCountryField, LM.findProperty("name[Country]").getMapping(importCountryKey), getReplaceOnlyNull(defaultColumns, "importCountryBatch")));
                    props.add(new ImportProperty(nameImportCountryField, LM.findProperty("importCountry[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                            object(LM.findClass("Country")).getMapping(importCountryKey), getReplaceOnlyNull(defaultColumns, "importCountryBatch")));
                    fields.add(nameImportCountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("importCountryBatch"));
                }
            }

            if (showField(userInvoiceDetailsList, "seriesPharmacy")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("seriesPharmacy[UserInvoiceDetail]"), "seriesPharmacy", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("seriesPharmacy"));
            }

            if (showField(userInvoiceDetailsList, "contractPrice")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("contractPrice[UserInvoiceDetail]"), "contractPrice", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("contractPrice"));
            }

        }
        
    }
}
