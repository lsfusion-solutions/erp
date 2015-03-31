package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.integration.ImportField;
import lsfusion.server.integration.ImportKey;
import lsfusion.server.integration.ImportProperty;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoiceItemArticle extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoiceItemArticle(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> itemKey, ImportKey<?> articleKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("ItemArticle");

        if (LM != null && itemKey != null && articleKey != null) {

            if (showField(userInvoiceDetailsList, "idItemGroup")) {
                ImportField idItemGroupField = new ImportField(LM.findProperty("idItemGroup"));
                ImportKey<?> itemGroupKey = new ImportKey((CustomClass) LM.findClass("ItemGroup"),
                        LM.findProperty("itemGroupId").getMapping(idItemGroupField));
                keys.add(itemGroupKey);
                itemGroupKey.skipKey = true;
                props.add(new ImportProperty(idItemGroupField, LM.findProperty("itemGroupArticle").getMapping(articleKey),
                        object(LM.findClass("ItemGroup")).getMapping(itemGroupKey), getReplaceOnlyNull(defaultColumns, "idItemGroup")));
                fields.add(idItemGroupField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idItemGroup"));
            }

            if (showField(userInvoiceDetailsList, "captionArticle")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("captionArticle"), "captionArticle", articleKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("captionArticle"));
            }

            if (showField(userInvoiceDetailsList, "originalCaptionArticle")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("originalCaptionArticle"), "originalCaptionArticle", articleKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("originalCaptionArticle"));
            }

            if (showField(userInvoiceDetailsList, "netWeight")) {
                ImportField netWeightField = new ImportField(LM.findProperty("netWeightItem"));
                props.add(new ImportProperty(netWeightField, LM.findProperty("netWeightItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "netWeight")));
                props.add(new ImportProperty(netWeightField, LM.findProperty("netWeightArticle").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "netWeight")));
                fields.add(netWeightField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).netWeight);
            }

            if (showField(userInvoiceDetailsList, "grossWeight")) {
                ImportField grossWeightField = new ImportField(LM.findProperty("grossWeightItem"));
                props.add(new ImportProperty(grossWeightField, LM.findProperty("grossWeightItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "grossWeight")));
                props.add(new ImportProperty(grossWeightField, LM.findProperty("grossWeightArticle").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "grossWeight")));
                fields.add(grossWeightField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).grossWeight);
            }

            if (showField(userInvoiceDetailsList, "composition")) {
                ImportField compositionField = new ImportField(LM.findProperty("compositionItem"));
                props.add(new ImportProperty(compositionField, LM.findProperty("compositionItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "composition")));
                props.add(new ImportProperty(compositionField, LM.findProperty("compositionArticle").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "composition")));
                fields.add(compositionField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("composition"));
            }

            if (showField(userInvoiceDetailsList, "originalComposition")) {
                ImportField originalCompositionField = new ImportField(LM.findProperty("originalCompositionItem"));
                props.add(new ImportProperty(originalCompositionField, LM.findProperty("originalCompositionItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "originalComposition")));
                props.add(new ImportProperty(originalCompositionField, LM.findProperty("originalCompositionArticle").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "originalComposition")));
                fields.add(originalCompositionField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("originalComposition"));
            }

            if (showField(userInvoiceDetailsList, "idColor")) {
                ImportField idColorField = new ImportField(LM.findProperty("idColor"));
                ImportKey<?> colorKey = new ImportKey((CustomClass) LM.findClass("Color"),
                        LM.findProperty("colorId").getMapping(idColorField));
                keys.add(colorKey);
                props.add(new ImportProperty(idColorField, LM.findProperty("idColor").getMapping(colorKey), getReplaceOnlyNull(defaultColumns, "idColor")));
                props.add(new ImportProperty(idColorField, LM.findProperty("colorItem").getMapping(itemKey),
                        object(LM.findClass("Color")).getMapping(colorKey), getReplaceOnlyNull(defaultColumns, "idColor")));
                props.add(new ImportProperty(idColorField, LM.findProperty("colorArticle").getMapping(articleKey),
                        object(LM.findClass("Color")).getMapping(colorKey), getReplaceOnlyNull(defaultColumns, "idColor")));
                fields.add(idColorField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idColor"));

                if (showField(userInvoiceDetailsList, "nameColor")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("nameColor"), "nameColor", colorKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameColor"));
                }
            }

            if (showField(userInvoiceDetailsList, "idSize")) {
                ImportField idSizeField = new ImportField(LM.findProperty("idSize"));
                ImportKey<?> sizeKey = new ImportKey((CustomClass) LM.findClass("Size"),
                        LM.findProperty("sizeId").getMapping(idSizeField));
                keys.add(sizeKey);
                props.add(new ImportProperty(idSizeField, LM.findProperty("idSize").getMapping(sizeKey), getReplaceOnlyNull(defaultColumns, "idSize")));
                props.add(new ImportProperty(idSizeField, LM.findProperty("sizeItem").getMapping(itemKey),
                        object(LM.findClass("Size")).getMapping(sizeKey), getReplaceOnlyNull(defaultColumns, "idSize")));
                fields.add(idSizeField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idSize"));

                if (showField(userInvoiceDetailsList, "nameSize")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("nameSize"), "nameSize", sizeKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameSize"));

                    addDataField(props, fields, defaultColumns, LM.findProperty("shortNameSize"), "nameSize", sizeKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameSize"));
                }

                if (showField(userInvoiceDetailsList, "nameOriginalSize")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("nameOriginalSize"), "nameOriginalSize", sizeKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameOriginalSize"));
                }
            }

            if (showField(userInvoiceDetailsList, "idBrand")) {
                ImportField idBrandField = new ImportField(LM.findProperty("idBrand"));
                ImportKey<?> brandKey = new ImportKey((CustomClass) LM.findClass("Brand"),
                        LM.findProperty("brandId").getMapping(idBrandField));
                keys.add(brandKey);
                props.add(new ImportProperty(idBrandField, LM.findProperty("idBrand").getMapping(brandKey), getReplaceOnlyNull(defaultColumns, "idBrand")));
                props.add(new ImportProperty(idBrandField, LM.findProperty("brandArticle").getMapping(articleKey),
                        object(LM.findClass("Brand")).getMapping(brandKey), getReplaceOnlyNull(defaultColumns, "idBrand")));
                props.add(new ImportProperty(idBrandField, LM.findProperty("brandItem").getMapping(itemKey),
                        object(LM.findClass("Brand")).getMapping(brandKey), getReplaceOnlyNull(defaultColumns, "idBrand")));
                fields.add(idBrandField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idBrand"));

                if (showField(userInvoiceDetailsList, "nameBrand")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("nameBrand"), "nameBrand", brandKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameBrand"));
                }
            }

            if (showField(userInvoiceDetailsList, "UOMItem")) {
                ImportField idUOMField = new ImportField(LM.findProperty("idUOM"));
                ImportKey<?> UOMKey = new ImportKey((CustomClass) LM.findClass("UOM"),
                        LM.findProperty("UOMId").getMapping(idUOMField));
                keys.add(UOMKey);
                props.add(new ImportProperty(idUOMField, LM.findProperty("UOMArticle").getMapping(articleKey),
                        object(LM.findClass("UOM")).getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                fields.add(idUOMField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("UOMItem"));
            }

            if (showField(userInvoiceDetailsList, "idManufacturer")) {
                ImportField idManufacturerField = new ImportField(LM.findProperty("idManufacturer"));
                ImportKey<?> manufacturerKey = new ImportKey((CustomClass) LM.findClass("Manufacturer"),
                        LM.findProperty("manufacturerId").getMapping(idManufacturerField));
                keys.add(manufacturerKey);
                props.add(new ImportProperty(idManufacturerField, LM.findProperty("manufacturerArticle").getMapping(articleKey),
                        object(LM.findClass("Manufacturer")).getMapping(manufacturerKey), getReplaceOnlyNull(defaultColumns, "idManufacturer")));
                fields.add(idManufacturerField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idManufacturer"));
            }

            ImportField sidOrigin2CountryField = new ImportField(LM.findProperty("sidOrigin2Country"));
            ImportField nameCountryField = new ImportField(LM.findProperty("nameCountry"));
            ImportField nameOriginCountryField = new ImportField(LM.findProperty("nameOriginCountry"));

            boolean showSidOrigin2Country = showField(userInvoiceDetailsList, "sidOrigin2Country");
            boolean showNameCountry = showField(userInvoiceDetailsList, "nameCountry");
            boolean showNameOriginCountry = showField(userInvoiceDetailsList, "nameOriginCountry");

            ImportField countryField = showSidOrigin2Country ? sidOrigin2CountryField :
                    (showNameCountry ? nameCountryField : (showNameOriginCountry ? nameOriginCountryField : null));
            LCP<?> countryAggr = showSidOrigin2Country ? LM.findProperty("countrySIDOrigin2") :
                    (showNameCountry ? LM.findProperty("countryName") : (showNameOriginCountry ? LM.findProperty("countryNameOrigin") : null));
            String countryReplaceField = showSidOrigin2Country ? "sidOrigin2Country" :
                    (showNameCountry ? "nameCountry" : (showNameOriginCountry ? "nameOriginCountry" : null));
            ImportKey<?> countryKey = countryField == null ? null :
                    new ImportKey((CustomClass) LM.findClass("Country"), countryAggr.getMapping(countryField));

            if (countryKey != null) {
                keys.add(countryKey);
                props.add(new ImportProperty(countryField, LM.findProperty("countryArticle").getMapping(articleKey),
                        object(LM.findClass("Country")).getMapping(countryKey), getReplaceOnlyNull(defaultColumns, countryReplaceField)));
                fields.add(countryField);
            }

        }
        
    }
}
