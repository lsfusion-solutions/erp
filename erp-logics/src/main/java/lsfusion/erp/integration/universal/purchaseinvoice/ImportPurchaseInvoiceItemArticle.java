package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.integration.ImportField;
import lsfusion.server.integration.ImportKey;
import lsfusion.server.integration.ImportProperty;
import lsfusion.server.language.linear.LCP;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoiceItemArticle extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoiceItemArticle(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> itemKey, ImportKey<?> articleKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("ItemArticle");

        if (LM != null && itemKey != null && articleKey != null) {

            if (showField(userInvoiceDetailsList, "idItemGroup")) {
                ImportField idItemGroupField = new ImportField(LM.findProperty("id[ItemGroup]"));
                ImportKey<?> itemGroupKey = new ImportKey((CustomClass) LM.findClass("ItemGroup"),
                        LM.findProperty("itemGroup[VARSTRING[100]]").getMapping(idItemGroupField));
                keys.add(itemGroupKey);
                itemGroupKey.skipKey = true;
                props.add(new ImportProperty(idItemGroupField, LM.findProperty("itemGroup[Article]").getMapping(articleKey),
                        object(LM.findClass("ItemGroup")).getMapping(itemGroupKey), getReplaceOnlyNull(defaultColumns, "idItemGroup")));
                fields.add(idItemGroupField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idItemGroup"));
            }

            if (showField(userInvoiceDetailsList, "captionArticle")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("caption[Article]"), "captionArticle", articleKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("captionArticle"));
            }

            if (showField(userInvoiceDetailsList, "originalCaptionArticle")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("originalCaption[Article]"), "originalCaptionArticle", articleKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("originalCaptionArticle"));
            }

            if (showField(userInvoiceDetailsList, "netWeight")) {
                ImportField netWeightField = new ImportField(LM.findProperty("netWeight[Article]"));
                props.add(new ImportProperty(netWeightField, LM.findProperty("netWeight[Article]").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "netWeight")));
                fields.add(netWeightField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).netWeight);
            }

            if (showField(userInvoiceDetailsList, "grossWeight")) {
                ImportField grossWeightField = new ImportField(LM.findProperty("grossWeight[Article]"));
                props.add(new ImportProperty(grossWeightField, LM.findProperty("grossWeight[Article]").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "grossWeight")));
                fields.add(grossWeightField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).grossWeight);
            }

            if (showField(userInvoiceDetailsList, "composition")) {
                ImportField compositionField = new ImportField(LM.findProperty("composition[Item]"));
                props.add(new ImportProperty(compositionField, LM.findProperty("composition[Item]").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "composition")));
                props.add(new ImportProperty(compositionField, LM.findProperty("composition[Article]").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "composition")));
                fields.add(compositionField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("composition"));
            }

            if (showField(userInvoiceDetailsList, "originalComposition")) {
                ImportField originalCompositionField = new ImportField(LM.findProperty("originalComposition[Item]"));
                props.add(new ImportProperty(originalCompositionField, LM.findProperty("originalComposition[Item]").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "originalComposition")));
                props.add(new ImportProperty(originalCompositionField, LM.findProperty("originalComposition[Article]").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "originalComposition")));
                fields.add(originalCompositionField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("originalComposition"));
            }

            if (showField(userInvoiceDetailsList, "idColor")) {
                ImportField idColorField = new ImportField(LM.findProperty("id[Color]"));
                ImportKey<?> colorKey = new ImportKey((CustomClass) LM.findClass("Color"),
                        LM.findProperty("color[VARSTRING[100]]").getMapping(idColorField));
                keys.add(colorKey);
                props.add(new ImportProperty(idColorField, LM.findProperty("id[Color]").getMapping(colorKey), getReplaceOnlyNull(defaultColumns, "idColor")));
                props.add(new ImportProperty(idColorField, LM.findProperty("color[Item]").getMapping(itemKey),
                        object(LM.findClass("Color")).getMapping(colorKey), getReplaceOnlyNull(defaultColumns, "idColor")));
                props.add(new ImportProperty(idColorField, LM.findProperty("color[Article]").getMapping(articleKey),
                        object(LM.findClass("Color")).getMapping(colorKey), getReplaceOnlyNull(defaultColumns, "idColor")));
                fields.add(idColorField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idColor"));

                if (showField(userInvoiceDetailsList, "nameColor")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("name[Color]"), "nameColor", colorKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameColor"));
                }
            }

            if (showField(userInvoiceDetailsList, "idSize")) {
                ImportField idSizeField = new ImportField(LM.findProperty("id[Size]"));
                ImportKey<?> sizeKey = new ImportKey((CustomClass) LM.findClass("Size"),
                        LM.findProperty("size[VARSTRING[100]]").getMapping(idSizeField));
                keys.add(sizeKey);
                props.add(new ImportProperty(idSizeField, LM.findProperty("id[Size]").getMapping(sizeKey), getReplaceOnlyNull(defaultColumns, "idSize")));
                props.add(new ImportProperty(idSizeField, LM.findProperty("size[Item]").getMapping(itemKey),
                        object(LM.findClass("Size")).getMapping(sizeKey), getReplaceOnlyNull(defaultColumns, "idSize")));
                fields.add(idSizeField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idSize"));

                if (showField(userInvoiceDetailsList, "nameSize")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("name[Size]"), "nameSize", sizeKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameSize"));

                    addDataField(props, fields, defaultColumns, LM.findProperty("shortName[Size]"), "nameSize", sizeKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameSize"));
                }

                if (showField(userInvoiceDetailsList, "nameOriginalSize")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("nameOriginal[Size]"), "nameOriginalSize", sizeKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameOriginalSize"));
                }
            }

            if (showField(userInvoiceDetailsList, "idBrand")) {
                ImportField idBrandField = new ImportField(LM.findProperty("id[Brand]"));
                ImportKey<?> brandKey = new ImportKey((CustomClass) LM.findClass("Brand"),
                        LM.findProperty("brand[VARSTRING[100]]").getMapping(idBrandField));
                keys.add(brandKey);
                props.add(new ImportProperty(idBrandField, LM.findProperty("id[Brand]").getMapping(brandKey), getReplaceOnlyNull(defaultColumns, "idBrand")));
                props.add(new ImportProperty(idBrandField, LM.findProperty("brand[Article]").getMapping(articleKey),
                        object(LM.findClass("Brand")).getMapping(brandKey), getReplaceOnlyNull(defaultColumns, "idBrand")));
                props.add(new ImportProperty(idBrandField, LM.findProperty("brand[Item]").getMapping(itemKey),
                        object(LM.findClass("Brand")).getMapping(brandKey), getReplaceOnlyNull(defaultColumns, "idBrand")));
                fields.add(idBrandField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idBrand"));

                if (showField(userInvoiceDetailsList, "nameBrand")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("name[Brand]"), "nameBrand", brandKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameBrand"));
                }
            }

            if (showField(userInvoiceDetailsList, "UOMItem")) {
                ImportField idUOMField = new ImportField(LM.findProperty("id[UOM]"));
                ImportKey<?> UOMKey = new ImportKey((CustomClass) LM.findClass("UOM"),
                        LM.findProperty("UOM[VARSTRING[100]]").getMapping(idUOMField));
                keys.add(UOMKey);
                props.add(new ImportProperty(idUOMField, LM.findProperty("UOM[Article]").getMapping(articleKey),
                        object(LM.findClass("UOM")).getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                fields.add(idUOMField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("UOMItem"));
            }

            if (showField(userInvoiceDetailsList, "idManufacturer")) {
                ImportField idManufacturerField = new ImportField(LM.findProperty("id[Manufacturer]"));
                ImportKey<?> manufacturerKey = new ImportKey((CustomClass) LM.findClass("Manufacturer"),
                        LM.findProperty("manufacturer[VARSTRING[100]]").getMapping(idManufacturerField));
                keys.add(manufacturerKey);
                props.add(new ImportProperty(idManufacturerField, LM.findProperty("manufacturer[Article]").getMapping(articleKey),
                        object(LM.findClass("Manufacturer")).getMapping(manufacturerKey), getReplaceOnlyNull(defaultColumns, "idManufacturer")));
                fields.add(idManufacturerField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idManufacturer"));
            }

            ImportField sidOrigin2CountryField = new ImportField(LM.findProperty("sidOrigin2[Country]"));
            ImportField nameCountryField = new ImportField(LM.findProperty("name[Country]"));
            ImportField nameOriginCountryField = new ImportField(LM.findProperty("nameOrigin[Country]"));

            boolean showSidOrigin2Country = showField(userInvoiceDetailsList, "sidOrigin2Country");
            boolean showNameCountry = showField(userInvoiceDetailsList, "nameCountry");
            boolean showNameOriginCountry = showField(userInvoiceDetailsList, "nameOriginCountry");

            ImportField countryField = showSidOrigin2Country ? sidOrigin2CountryField :
                    (showNameCountry ? nameCountryField : (showNameOriginCountry ? nameOriginCountryField : null));
            LCP<?> countryAggr = showSidOrigin2Country ? LM.findProperty("countrySIDOrigin2[STRING[2]]") :
                    (showNameCountry ? LM.findProperty("countryName[VARISTRING[50]]") : (showNameOriginCountry ? LM.findProperty("countryOrigin[VARISTRING[50]]") : null));
            String countryReplaceField = showSidOrigin2Country ? "sidOrigin2Country" :
                    (showNameCountry ? "nameCountry" : (showNameOriginCountry ? "nameOriginCountry" : null));
            ImportKey<?> countryKey = countryField == null ? null :
                    new ImportKey((CustomClass) LM.findClass("Country"), countryAggr.getMapping(countryField));

            if (countryKey != null) {
                keys.add(countryKey);
                props.add(new ImportProperty(countryField, LM.findProperty("country[Article]").getMapping(articleKey),
                        object(LM.findClass("Country")).getMapping(countryKey), getReplaceOnlyNull(defaultColumns, countryReplaceField)));
                fields.add(countryField);
                if(showNameOriginCountry) {
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameOriginCountry"));
                } else if(showNameCountry) {
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameCountry"));
                } else if(showSidOrigin2Country) {
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("sidOrigin2Country"));
                }
            }

        }
        
    }
}
