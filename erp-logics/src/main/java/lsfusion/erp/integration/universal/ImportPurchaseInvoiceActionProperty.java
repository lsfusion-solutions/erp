package lsfusion.erp.integration.universal;

import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
import lsfusion.erp.stock.BarcodeUtils;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class ImportPurchaseInvoiceActionProperty extends ImportDocumentActionProperty {
    private final ClassPropertyInterface userInvoiceInterface;

    // Опциональные модули
    ScriptingLogicsModule purchaseManufacturingPriceLM;
    ScriptingLogicsModule itemPharmacyByLM;
    ScriptingLogicsModule purchaseInvoicePharmacyLM;
    ScriptingLogicsModule itemArticleLM;
    ScriptingLogicsModule customsGroupArticleLM;

    public ImportPurchaseInvoiceActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Purchase.UserInvoice"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        userInvoiceInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {

            this.purchaseManufacturingPriceLM = (ScriptingLogicsModule) context.getBL().getModule("PurchaseManufacturingPrice");
            this.itemPharmacyByLM = (ScriptingLogicsModule) context.getBL().getModule("ItemPharmacyBy");
            this.purchaseInvoicePharmacyLM = (ScriptingLogicsModule) context.getBL().getModule("PurchaseInvoicePharmacy");
            this.itemArticleLM = (ScriptingLogicsModule) context.getBL().getModule("ItemArticle");
            this.customsGroupArticleLM = (ScriptingLogicsModule) context.getBL().getModule("CustomsGroupArticle");

            DataObject userInvoiceObject = context.getDataKeyValue(userInvoiceInterface);

            ObjectValue importTypeObject = LM.findLCPByCompoundName("importTypeUserInvoice").readClasses(context, userInvoiceObject);

            if (!(importTypeObject instanceof NullValue)) {

                String fileExtension = (String) LM.findLCPByCompoundName("captionImportTypeFileExtensionImportType").read(context, importTypeObject);
                String itemKeyType = (String) LM.findLCPByCompoundName("nameImportKeyTypeImportType").read(context, importTypeObject);
                String[] parts = itemKeyType == null ? null : itemKeyType.split("\\.");
                itemKeyType = parts == null ? null : parts[parts.length - 1].trim();
                String csvSeparator = (String) LM.findLCPByCompoundName("separatorImportType").read(context, importTypeObject);
                csvSeparator = csvSeparator == null ? ";" : csvSeparator;
                Integer startRow = (Integer) LM.findLCPByCompoundName("startRowImportType").read(context, importTypeObject);
                startRow = startRow == null || startRow.equals(0) ? 1 : startRow;

                ObjectValue operation = LM.findLCPByCompoundName("autoImportOperationImportType").readClasses(context, (DataObject) importTypeObject);
                DataObject operationObject = operation instanceof NullValue ? null : (DataObject) operation;

                ObjectValue supplier = LM.findLCPByCompoundName("autoImportSupplierImportType").readClasses(context, (DataObject) importTypeObject);
                DataObject supplierObject = supplier instanceof NullValue ? null : (DataObject) supplier;
                ObjectValue supplierStock = LM.findLCPByCompoundName("autoImportSupplierStockImportType").readClasses(context, (DataObject) importTypeObject);
                DataObject supplierStockObject = supplierStock instanceof NullValue ? null : (DataObject) supplierStock;
                ObjectValue customer = LM.findLCPByCompoundName("autoImportCustomerImportType").readClasses(context, (DataObject) importTypeObject);
                DataObject customerObject = customer instanceof NullValue ? null : (DataObject) customer;
                ObjectValue customerStock = LM.findLCPByCompoundName("autoImportCustomerStockImportType").readClasses(context, (DataObject) importTypeObject);
                DataObject customerStockObject = customerStock instanceof NullValue ? null : (DataObject) customerStock;

                Map<String, String[]> importColumns = readImportColumns(context, LM, importTypeObject);

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension.trim() + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            importUserInvoices(context, userInvoiceObject, importColumns, file, fileExtension.trim(), startRow,
                                    csvSeparator, itemKeyType, operationObject, supplierObject, supplierStockObject,
                                    customerObject, customerStockObject);

                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (xBaseJException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (BiffException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public void importUserInvoices(ExecutionContext context, DataObject userInvoiceObject, Map<String, String[]> importColumns,
                                   byte[] file, String fileExtension, Integer startRow, String csvSeparator, String itemKeyType,
                                   DataObject operationObject, DataObject supplierObject, DataObject supplierStockObject,
                                   DataObject customerObject, DataObject customerStockObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException {

        List<PurchaseInvoiceDetail> userInvoiceDetailsList;

        String keyColumn = (itemKeyType == null || itemKeyType.equals("item")) ? "idItem" : itemKeyType.equals("barcode") ? "barcodeItem" : "idBatch";

        if (fileExtension.equals("DBF"))
            userInvoiceDetailsList = importUserInvoicesFromDBF(context, file, importColumns, keyColumn, startRow, (Integer) userInvoiceObject.object);
        else if (fileExtension.equals("XLS"))
            userInvoiceDetailsList = importUserInvoicesFromXLS(context, file, importColumns, keyColumn, startRow, (Integer) userInvoiceObject.object);
        else if (fileExtension.equals("XLSX"))
            userInvoiceDetailsList = importUserInvoicesFromXLSX(context, file, importColumns, keyColumn, startRow, (Integer) userInvoiceObject.object);
        else if (fileExtension.equals("CSV"))
            userInvoiceDetailsList = importUserInvoicesFromCSV(context, file, importColumns, keyColumn, startRow, csvSeparator, (Integer) userInvoiceObject.object);
        else
            userInvoiceDetailsList = null;

        if (userInvoiceDetailsList != null) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(userInvoiceDetailsList.size());

            if (showField(userInvoiceDetailsList, "numberUserInvoice")) {
                ImportField numberUserInvoiceField = new ImportField(LM.findLCPByCompoundName("numberUserInvoice"));
                props.add(new ImportProperty(numberUserInvoiceField, LM.findLCPByCompoundName("numberUserInvoice").getMapping(userInvoiceObject)));
                fields.add(numberUserInvoiceField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).numberUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "dateUserInvoice")) {
                ImportField dateUserInvoiceField = new ImportField(LM.findLCPByCompoundName("dateUserInvoice"));
                props.add(new ImportProperty(dateUserInvoiceField, LM.findLCPByCompoundName("dateUserInvoice").getMapping(userInvoiceObject)));
                fields.add(dateUserInvoiceField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).dateUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "currencyUserInvoice")) {
                ImportField shortNameCurrencyField = new ImportField(LM.findLCPByCompoundName("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Currency"),
                        LM.findLCPByCompoundName("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, LM.findLCPByCompoundName("currencyUserInvoice").getMapping(userInvoiceObject),
                        LM.object(LM.findClassByCompoundName("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).currencyUserInvoice);
            }

            ImportField idUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("idUserInvoiceDetail"));
            ImportKey<?> userInvoiceDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Purchase.UserInvoiceDetail"),
                    LM.findLCPByCompoundName("userInvoiceDetailId").getMapping(idUserInvoiceDetailField));
            keys.add(userInvoiceDetailKey);
            props.add(new ImportProperty(idUserInvoiceDetailField, LM.findLCPByCompoundName("idUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            props.add(new ImportProperty(userInvoiceObject, LM.findLCPByCompoundName("Purchase.userInvoiceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(idUserInvoiceDetailField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idUserInvoiceDetail);

            if (operationObject != null)
                props.add(new ImportProperty(operationObject, LM.findLCPByCompoundName("Purchase.operationUserInvoice").getMapping(userInvoiceObject)));

            if (supplierObject != null) {
                props.add(new ImportProperty(supplierObject, LM.findLCPByCompoundName("Purchase.supplierUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty(supplierObject, LM.findLCPByCompoundName("Purchase.supplierUserInvoice").getMapping(userInvoiceObject)));
            }

            if (supplierStockObject != null) {
                props.add(new ImportProperty(supplierStockObject, LM.findLCPByCompoundName("Purchase.supplierStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty(supplierStockObject, LM.findLCPByCompoundName("Purchase.supplierStockUserInvoice").getMapping(userInvoiceObject)));
            }

            if (customerObject != null) {
                props.add(new ImportProperty(customerObject, LM.findLCPByCompoundName("Purchase.customerUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty(customerObject, LM.findLCPByCompoundName("Purchase.customerUserInvoice").getMapping(userInvoiceObject)));
            }

            if (customerStockObject != null) {
                props.add(new ImportProperty(customerStockObject, LM.findLCPByCompoundName("Purchase.customerStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty(customerStockObject, LM.findLCPByCompoundName("Purchase.customerStockUserInvoice").getMapping(userInvoiceObject)));
            }

            ImportField idBarcodeSkuField = new ImportField(LM.findLCPByCompoundName("idBarcodeSku"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Barcode"),
                    LM.findLCPByCompoundName("extBarcodeId").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idBarcodeSkuField, LM.findLCPByCompoundName("idBarcode").getMapping(barcodeKey)));
            props.add(new ImportProperty(idBarcodeSkuField, LM.findLCPByCompoundName("extIdBarcode").getMapping(barcodeKey)));
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idBarcodeSku);

            ImportField idBatchField = new ImportField(LM.findLCPByCompoundName("idBatch"));
            ImportKey<?> batchKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Batch"),
                    LM.findLCPByCompoundName("batchId").getMapping(idBatchField));
            props.add(new ImportProperty(idBatchField, LM.findLCPByCompoundName("idBatch").getMapping(batchKey)));
            props.add(new ImportProperty(idBatchField, LM.findLCPByCompoundName("idBatchUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            props.add(new ImportProperty(idBatchField, LM.findLCPByCompoundName("batchUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                    LM.object(LM.findClassByCompoundName("Batch")).getMapping(batchKey)));
            fields.add(idBatchField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idBatch);

            ImportField idItemField = new ImportField(LM.findLCPByCompoundName("idItem"));
            fields.add(idItemField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idItem);

            String iGroupAggr = (itemKeyType == null || itemKeyType.equals("item")) ? "itemId" : itemKeyType.equals("barcode") ? "skuIdBarcode" : "skuBatchId";
            ImportField iField = (itemKeyType == null || itemKeyType.equals("item")) ? idItemField : itemKeyType.equals("barcode") ? idBarcodeSkuField : idBatchField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Item"),
                    LM.findLCPByCompoundName(iGroupAggr).getMapping(iField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, LM.findLCPByCompoundName("idItem").getMapping(itemKey)));
            props.add(new ImportProperty(iField, LM.findLCPByCompoundName("Purchase.skuInvoiceDetail").getMapping(userInvoiceDetailKey),
                    LM.object(LM.findClassByCompoundName("Sku")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, LM.findLCPByCompoundName("skuBarcode").getMapping(barcodeKey),
                    LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));

            if (showField(userInvoiceDetailsList, "idItemGroup")) {
                ImportField idItemGroupField = new ImportField(LM.findLCPByCompoundName("idItemGroup"));
                ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("ItemGroup"),
                        LM.findLCPByCompoundName("itemGroupId").getMapping(idItemGroupField));
                keys.add(itemGroupKey);
                props.add(new ImportProperty(idItemGroupField, LM.findLCPByCompoundName("itemGroupItem").getMapping(itemKey),
                        LM.object(LM.findClassByCompoundName("ItemGroup")).getMapping(itemGroupKey)));
                fields.add(idItemGroupField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idItemGroup);
            }

            if (showField(userInvoiceDetailsList, "captionItem")) {
                ImportField captionItemField = new ImportField(LM.findLCPByCompoundName("captionItem"));
                props.add(new ImportProperty(captionItemField, LM.findLCPByCompoundName("captionItem").getMapping(itemKey)));
                fields.add(captionItemField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).captionItem);
            }

            if (showField(userInvoiceDetailsList, "idUOM")) {
                ImportField idUOMField = new ImportField(LM.findLCPByCompoundName("idUOM"));
                ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UOM"),
                        LM.findLCPByCompoundName("UOMId").getMapping(idUOMField));
                keys.add(UOMKey);
                props.add(new ImportProperty(idUOMField, LM.findLCPByCompoundName("UOMItem").getMapping(itemKey),
                        LM.object(LM.findClassByCompoundName("UOM")).getMapping(UOMKey)));
                fields.add(idUOMField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idUOM);
            }

            if (showField(userInvoiceDetailsList, "idManufacturer")) {
                ImportField idManufacturerField = new ImportField(LM.findLCPByCompoundName("idManufacturer"));
                ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Manufacturer"),
                        LM.findLCPByCompoundName("manufacturerId").getMapping(idManufacturerField));
                keys.add(manufacturerKey);
                props.add(new ImportProperty(idManufacturerField, LM.findLCPByCompoundName("idManufacturer").getMapping(manufacturerKey)));
                props.add(new ImportProperty(idManufacturerField, LM.findLCPByCompoundName("manufacturerItem").getMapping(itemKey),
                        LM.object(LM.findClassByCompoundName("Manufacturer")).getMapping(manufacturerKey)));
                fields.add(idManufacturerField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idManufacturer);
            }

            if (showField(userInvoiceDetailsList, "nameCountry")) {
                ImportField nameCountryField = new ImportField(LM.findLCPByCompoundName("nameCountry"));
                ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Country"),
                        LM.findLCPByCompoundName("countryName").getMapping(nameCountryField));
                keys.add(countryKey);
                props.add(new ImportProperty(nameCountryField, LM.findLCPByCompoundName("nameCountry").getMapping(countryKey)));
                props.add(new ImportProperty(nameCountryField, LM.findLCPByCompoundName("countryItem").getMapping(itemKey),
                        LM.object(LM.findClassByCompoundName("Country")).getMapping(countryKey)));
                fields.add(nameCountryField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).nameCountry);

                ImportField nameOriginCountryField = new ImportField(LM.findLCPByCompoundName("nameOriginCountry"));
                props.add(new ImportProperty(nameOriginCountryField, LM.findLCPByCompoundName("nameOriginCountry").getMapping(countryKey)));
                fields.add(nameOriginCountryField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).nameOriginCountry);
            }

            if (showField(userInvoiceDetailsList, "nameOriginCountry")) {
                ImportField nameOriginCountryField = new ImportField(LM.findLCPByCompoundName("nameOriginCountry"));
                ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Country"),
                        LM.findLCPByCompoundName("countryNameOrigin").getMapping(nameOriginCountryField));
                keys.add(countryKey);
                props.add(new ImportProperty(nameOriginCountryField, LM.findLCPByCompoundName("nameOriginCountry").getMapping(countryKey)));
                props.add(new ImportProperty(nameOriginCountryField, LM.findLCPByCompoundName("countryItem").getMapping(itemKey),
                        LM.object(LM.findClassByCompoundName("Country")).getMapping(countryKey)));
                fields.add(nameOriginCountryField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).nameOriginCountry);

                ImportField nameCountryField = new ImportField(LM.findLCPByCompoundName("nameCountry"));
                props.add(new ImportProperty(nameCountryField, LM.findLCPByCompoundName("nameCountry").getMapping(countryKey)));
                fields.add(nameCountryField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).nameCountry);
            }

            if (showField(userInvoiceDetailsList, "idCustomer")) {
                ImportField idCustomerField = new ImportField(LM.findLCPByCompoundName("idLegalEntity"));
                ImportKey<?> customerKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        LM.findLCPByCompoundName("legalEntityId").getMapping(idCustomerField));
                keys.add(customerKey);
                props.add(new ImportProperty(idCustomerField, LM.findLCPByCompoundName("Purchase.customerUserInvoice").getMapping(userInvoiceObject),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(customerKey)));
                fields.add(idCustomerField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idCustomer);
            }

            if (showField(userInvoiceDetailsList, "idCustomerStock")) {
                ImportField idCustomerStockField = new ImportField(LM.findLCPByCompoundName("idStock"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Stock"),
                        LM.findLCPByCompoundName("stockId").getMapping(idCustomerStockField));
                keys.add(customerStockKey);
                props.add(new ImportProperty(idCustomerStockField, LM.findLCPByCompoundName("Purchase.customerStockUserInvoice").getMapping(userInvoiceObject),
                        LM.object(LM.findClassByCompoundName("Stock")).getMapping(customerStockKey)));
                fields.add(idCustomerStockField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idCustomerStock);
            }

            if (showField(userInvoiceDetailsList, "quantity")) {
                ImportField quantityUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.quantityUserInvoiceDetail"));
                props.add(new ImportProperty(quantityUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.quantityUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(quantityUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).quantity);
            }

            if (showField(userInvoiceDetailsList, "price")) {
                ImportField priceUserInvoiceDetail = new ImportField(LM.findLCPByCompoundName("Purchase.priceUserInvoiceDetail"));
                props.add(new ImportProperty(priceUserInvoiceDetail, LM.findLCPByCompoundName("Purchase.priceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(priceUserInvoiceDetail);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).price);
            }

            if (showField(userInvoiceDetailsList, "sum")) {
                ImportField sumUserInvoiceDetail = new ImportField(LM.findLCPByCompoundName("Purchase.sumUserInvoiceDetail"));
                props.add(new ImportProperty(sumUserInvoiceDetail, LM.findLCPByCompoundName("Purchase.sumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(sumUserInvoiceDetail);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).sum);
            }

            if (showField(userInvoiceDetailsList, "valueVAT")) {
                ImportField valueVATUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.valueVATUserInvoiceDetail"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Range"),
                        LM.findLCPByCompoundName("valueCurrentVATDefaultValue").getMapping(valueVATUserInvoiceDetailField));
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.VATUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName("Range")).getMapping(VATKey)));
                fields.add(valueVATUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).valueVAT);
            }

            if (showField(userInvoiceDetailsList, "sumVAT")) {
                ImportField VATSumUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.VATSumUserInvoiceDetail"));
                props.add(new ImportProperty(VATSumUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.VATSumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(VATSumUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).sumVAT);
            }

            if (showField(userInvoiceDetailsList, "invoiceSum")) {
                ImportField invoiceSumUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.invoiceSumUserInvoiceDetail"));
                props.add(new ImportProperty(invoiceSumUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.invoiceSumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(invoiceSumUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).invoiceSum);
            }

            if (showField(userInvoiceDetailsList, "numberCompliance")) {
                ImportField numberComplianceField = new ImportField(LM.findLCPByCompoundName("numberCompliance"));
                ImportKey<?> complianceKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Compliance"),
                        LM.findLCPByCompoundName("complianceId").getMapping(numberComplianceField));
                keys.add(complianceKey);
                props.add(new ImportProperty(numberComplianceField, LM.findLCPByCompoundName("numberCompliance").getMapping(complianceKey)));
                props.add(new ImportProperty(numberComplianceField, LM.findLCPByCompoundName("idCompliance").getMapping(complianceKey)));
                props.add(new ImportProperty(numberComplianceField, LM.findLCPByCompoundName("complianceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName("Compliance")).getMapping(complianceKey)));
                fields.add(numberComplianceField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).numberCompliance);
            }

            if (showField(userInvoiceDetailsList, "numberDeclaration")) {
                ImportField numberDeclarationField = new ImportField(LM.findLCPByCompoundName("numberDeclaration"));
                ImportKey<?> declarationKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Declaration"),
                        LM.findLCPByCompoundName("declarationId").getMapping(numberDeclarationField));
                keys.add(declarationKey);
                props.add(new ImportProperty(numberDeclarationField, LM.findLCPByCompoundName("numberDeclaration").getMapping(declarationKey)));
                props.add(new ImportProperty(numberDeclarationField, LM.findLCPByCompoundName("idDeclaration").getMapping(declarationKey)));
                props.add(new ImportProperty(numberDeclarationField, LM.findLCPByCompoundName("declarationUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName("Declaration")).getMapping(declarationKey)));
                fields.add(numberDeclarationField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).numberDeclaration);
            }

            if (showField(userInvoiceDetailsList, "expiryDate")) {
                ImportField expiryDateUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("expiryDateUserInvoiceDetail"));
                props.add(new ImportProperty(expiryDateUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.expiryDateUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(expiryDateUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).expiryDate);
            }

            if ((purchaseManufacturingPriceLM != null) && showField(userInvoiceDetailsList, "manufacturingPrice")) {
                ImportField manufacturingPriceUserInvoiceDetailField = new ImportField(purchaseManufacturingPriceLM.findLCPByCompoundName("Purchase.manufacturingPriceUserInvoiceDetail"));
                props.add(new ImportProperty(manufacturingPriceUserInvoiceDetailField, purchaseManufacturingPriceLM.findLCPByCompoundName("Purchase.manufacturingPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(manufacturingPriceUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).manufacturingPrice);
            }

            if (itemPharmacyByLM != null && showField(userInvoiceDetailsList, "idPharmacyPriceGroup")) {
                ImportField idPharmacyPriceGroupField = new ImportField(itemPharmacyByLM.findLCPByCompoundName("idPharmacyPriceGroup"));
                ImportKey<?> pharmacyPriceGroupKey = new ImportKey((ConcreteCustomClass) itemPharmacyByLM.findClassByCompoundName("PharmacyPriceGroup"),
                        itemPharmacyByLM.findLCPByCompoundName("pharmacyPriceGroupId").getMapping(idPharmacyPriceGroupField));
                keys.add(pharmacyPriceGroupKey);
                props.add(new ImportProperty(idPharmacyPriceGroupField, itemPharmacyByLM.findLCPByCompoundName("idPharmacyPriceGroup").getMapping(pharmacyPriceGroupKey)));
                props.add(new ImportProperty(idPharmacyPriceGroupField, itemPharmacyByLM.findLCPByCompoundName("namePharmacyPriceGroup").getMapping(pharmacyPriceGroupKey)));
                props.add(new ImportProperty(idPharmacyPriceGroupField, itemPharmacyByLM.findLCPByCompoundName("pharmacyPriceGroupItem").getMapping(itemKey),
                        LM.object(itemPharmacyByLM.findClassByCompoundName("PharmacyPriceGroup")).getMapping(pharmacyPriceGroupKey)));
                fields.add(idPharmacyPriceGroupField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idPharmacyPriceGroup);
            }

            if (purchaseInvoicePharmacyLM != null && showField(userInvoiceDetailsList, "nameImportCountry")) {
                ImportField nameImportCountryField = new ImportField(purchaseInvoicePharmacyLM.findLCPByCompoundName("nameCountry"));
                ImportKey<?> importCountryKey = new ImportKey((ConcreteCustomClass) purchaseInvoicePharmacyLM.findClassByCompoundName("Country"),
                        purchaseInvoicePharmacyLM.findLCPByCompoundName("countryName").getMapping(nameImportCountryField));
                keys.add(importCountryKey);
                props.add(new ImportProperty(nameImportCountryField, purchaseInvoicePharmacyLM.findLCPByCompoundName("nameCountry").getMapping(importCountryKey)));
                props.add(new ImportProperty(nameImportCountryField, purchaseInvoicePharmacyLM.findLCPByCompoundName("importCountryUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(purchaseInvoicePharmacyLM.findClassByCompoundName("Country")).getMapping(importCountryKey)));
                fields.add(nameImportCountryField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).nameImportCountry);
            }

            if (purchaseInvoicePharmacyLM != null && showField(userInvoiceDetailsList, "seriesPharmacy")) {
                ImportField seriesPharmacyUserInvoiceDetailField = new ImportField(purchaseInvoicePharmacyLM.findLCPByCompoundName("Purchase.seriesPharmacyUserInvoiceDetail"));
                props.add(new ImportProperty(seriesPharmacyUserInvoiceDetailField, purchaseInvoicePharmacyLM.findLCPByCompoundName("Purchase.seriesPharmacyUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(seriesPharmacyUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).seriesPharmacy);
            }

            if ((itemArticleLM != null)) {

                ImportField idArticleField = new ImportField(itemArticleLM.findLCPByCompoundName("idArticle"));
                ImportKey<?> articleKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Article"),
                        itemArticleLM.findLCPByCompoundName("articleId").getMapping(idArticleField));
                keys.add(articleKey);
                props.add(new ImportProperty(idArticleField, itemArticleLM.findLCPByCompoundName("idArticle").getMapping(articleKey)));
                props.add(new ImportProperty(idArticleField, itemArticleLM.findLCPByCompoundName("articleItem").getMapping(itemKey),
                        LM.object(itemArticleLM.findClassByCompoundName("Article")).getMapping(articleKey)));
                fields.add(idArticleField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idArticle);

                if (showField(userInvoiceDetailsList, "captionArticle")) {
                    ImportField captionArticleField = new ImportField(itemArticleLM.findLCPByCompoundName("captionArticle"));
                    props.add(new ImportProperty(captionArticleField, itemArticleLM.findLCPByCompoundName("captionArticle").getMapping(articleKey)));
                    fields.add(captionArticleField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).captionArticle);
                }

                if (showField(userInvoiceDetailsList, "netWeight")) {
                    ImportField netWeightField = new ImportField(itemArticleLM.findLCPByCompoundName("netWeightItem"));
                    props.add(new ImportProperty(netWeightField, itemArticleLM.findLCPByCompoundName("netWeightItem").getMapping(itemKey)));
                    props.add(new ImportProperty(netWeightField, itemArticleLM.findLCPByCompoundName("netWeightArticle").getMapping(articleKey)));
                    fields.add(netWeightField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).netWeight);
                }

                if (showField(userInvoiceDetailsList, "grossWeight")) {
                    ImportField grossWeightField = new ImportField(itemArticleLM.findLCPByCompoundName("grossWeightItem"));
                    props.add(new ImportProperty(grossWeightField, itemArticleLM.findLCPByCompoundName("grossWeightItem").getMapping(itemKey)));
                    props.add(new ImportProperty(grossWeightField, itemArticleLM.findLCPByCompoundName("grossWeightArticle").getMapping(articleKey)));
                    fields.add(grossWeightField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).grossWeight);
                }

                if (showField(userInvoiceDetailsList, "composition")) {
                    ImportField compositionField = new ImportField(itemArticleLM.findLCPByCompoundName("compositionItem"));
                    props.add(new ImportProperty(compositionField, itemArticleLM.findLCPByCompoundName("compositionItem").getMapping(itemKey)));
                    props.add(new ImportProperty(compositionField, itemArticleLM.findLCPByCompoundName("compositionArticle").getMapping(articleKey)));
                    fields.add(compositionField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).composition);
                }

                if (showField(userInvoiceDetailsList, "originalComposition")) {
                    ImportField originalCompositionField = new ImportField(itemArticleLM.findLCPByCompoundName("originalCompositionItem"));
                    props.add(new ImportProperty(originalCompositionField, itemArticleLM.findLCPByCompoundName("originalCompositionItem").getMapping(itemKey)));
                    props.add(new ImportProperty(originalCompositionField, itemArticleLM.findLCPByCompoundName("originalCompositionArticle").getMapping(articleKey)));
                    fields.add(originalCompositionField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).originalComposition);
                }

                if (customsGroupArticleLM != null && showField(userInvoiceDetailsList, "originalCustomsGroupItem")) {
                    ImportField originalCustomsGroupItemField = new ImportField(customsGroupArticleLM.findLCPByCompoundName("originalCustomsGroupItem"));
                    props.add(new ImportProperty(originalCustomsGroupItemField, customsGroupArticleLM.findLCPByCompoundName("originalCustomsGroupItem").getMapping(itemKey)));
                    props.add(new ImportProperty(originalCustomsGroupItemField, customsGroupArticleLM.findLCPByCompoundName("originalCustomsGroupArticle").getMapping(articleKey)));
                    fields.add(originalCustomsGroupItemField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).originalCustomsGroupItem);
                }

                if (showField(userInvoiceDetailsList, "idColor")) {
                    ImportField idColorField = new ImportField(itemArticleLM.findLCPByCompoundName("idColor"));
                    ImportKey<?> colorKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Color"),
                            itemArticleLM.findLCPByCompoundName("colorId").getMapping(idColorField));
                    keys.add(colorKey);
                    props.add(new ImportProperty(idColorField, itemArticleLM.findLCPByCompoundName("idColor").getMapping(colorKey)));
                    props.add(new ImportProperty(idColorField, itemArticleLM.findLCPByCompoundName("colorItem").getMapping(itemKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Color")).getMapping(colorKey)));
                    props.add(new ImportProperty(idColorField, itemArticleLM.findLCPByCompoundName("colorArticle").getMapping(articleKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Color")).getMapping(colorKey)));
                    fields.add(idColorField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idColor);

                    if (showField(userInvoiceDetailsList, "nameColor")) {
                        ImportField nameColorField = new ImportField(itemArticleLM.findLCPByCompoundName("nameColor"));
                        props.add(new ImportProperty(nameColorField, itemArticleLM.findLCPByCompoundName("nameColor").getMapping(colorKey)));
                        fields.add(nameColorField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameColor);
                    }
                }

                if (showField(userInvoiceDetailsList, "idCollection")) {
                    ImportField idCollectionField = new ImportField(itemArticleLM.findLCPByCompoundName("idCollection"));
                    ImportKey<?> collectionKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Collection"),
                            itemArticleLM.findLCPByCompoundName("collectionId").getMapping(idCollectionField));
                    keys.add(collectionKey);
                    props.add(new ImportProperty(idCollectionField, itemArticleLM.findLCPByCompoundName("idCollection").getMapping(collectionKey)));
                    props.add(new ImportProperty(idCollectionField, itemArticleLM.findLCPByCompoundName("collectionArticle").getMapping(articleKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Collection")).getMapping(collectionKey)));
                    fields.add(idCollectionField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idCollection);

                    if (showField(userInvoiceDetailsList, "nameCollection")) {
                        ImportField nameCollectionField = new ImportField(itemArticleLM.findLCPByCompoundName("nameCollection"));
                        props.add(new ImportProperty(nameCollectionField, itemArticleLM.findLCPByCompoundName("nameCollection").getMapping(collectionKey)));
                        fields.add(nameCollectionField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameCollection);
                    }
                }

                if (showField(userInvoiceDetailsList, "idSize")) {
                    ImportField idSizeField = new ImportField(itemArticleLM.findLCPByCompoundName("idSize"));
                    ImportKey<?> sizeKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Size"),
                            itemArticleLM.findLCPByCompoundName("sizeId").getMapping(idSizeField));
                    keys.add(sizeKey);
                    props.add(new ImportProperty(idSizeField, itemArticleLM.findLCPByCompoundName("idSize").getMapping(sizeKey)));
                    props.add(new ImportProperty(idSizeField, itemArticleLM.findLCPByCompoundName("sizeItem").getMapping(itemKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Size")).getMapping(sizeKey)));
                    fields.add(idSizeField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idSize);

                    if (showField(userInvoiceDetailsList, "nameSize")) {
                        ImportField nameSizeField = new ImportField(itemArticleLM.findLCPByCompoundName("nameSize"));
                        props.add(new ImportProperty(nameSizeField, itemArticleLM.findLCPByCompoundName("nameSize").getMapping(sizeKey)));
                        props.add(new ImportProperty(nameSizeField, itemArticleLM.findLCPByCompoundName("shortNameSize").getMapping(sizeKey)));
                        fields.add(nameSizeField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameSize);
                    }
                }

                if (showField(userInvoiceDetailsList, "idSeasonYear")) {
                    ImportField idSeasonYearField = new ImportField(itemArticleLM.findLCPByCompoundName("idSeasonYear"));
                    ImportKey<?> seasonYearKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("SeasonYear"),
                            itemArticleLM.findLCPByCompoundName("seasonYearId").getMapping(idSeasonYearField));
                    keys.add(seasonYearKey);
                    props.add(new ImportProperty(idSeasonYearField, itemArticleLM.findLCPByCompoundName("idSeasonYear").getMapping(seasonYearKey)));
                    props.add(new ImportProperty(idSeasonYearField, itemArticleLM.findLCPByCompoundName("nameSeasonYear").getMapping(seasonYearKey)));
                    props.add(new ImportProperty(idSeasonYearField, itemArticleLM.findLCPByCompoundName("seasonYearArticle").getMapping(articleKey),
                            LM.object(itemArticleLM.findClassByCompoundName("SeasonYear")).getMapping(seasonYearKey)));
                    props.add(new ImportProperty(idSeasonYearField, itemArticleLM.findLCPByCompoundName("seasonYearItem").getMapping(itemKey),
                            LM.object(itemArticleLM.findClassByCompoundName("SeasonYear")).getMapping(seasonYearKey)));
                    fields.add(idSeasonYearField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idSeasonYear);
                }

                if (showField(userInvoiceDetailsList, "idSeason")) {
                    ImportField idSeasonField = new ImportField(itemArticleLM.findLCPByCompoundName("idSeason"));
                    ImportKey<?> seasonKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Season"),
                            itemArticleLM.findLCPByCompoundName("seasonId").getMapping(idSeasonField));
                    keys.add(seasonKey);
                    props.add(new ImportProperty(idSeasonField, itemArticleLM.findLCPByCompoundName("idSeason").getMapping(seasonKey)));
                    props.add(new ImportProperty(idSeasonField, itemArticleLM.findLCPByCompoundName("seasonArticle").getMapping(articleKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Season")).getMapping(seasonKey)));
                    props.add(new ImportProperty(idSeasonField, itemArticleLM.findLCPByCompoundName("seasonItem").getMapping(itemKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Season")).getMapping(seasonKey)));
                    fields.add(idSeasonField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idSeason);

                    if (showField(userInvoiceDetailsList, "nameSeason")) {
                        ImportField nameSeasonField = new ImportField(itemArticleLM.findLCPByCompoundName("nameSeason"));
                        props.add(new ImportProperty(nameSeasonField, itemArticleLM.findLCPByCompoundName("nameSeason").getMapping(seasonKey)));
                        fields.add(nameSeasonField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameSeason);
                    }
                }

                /*if (showField(userInvoiceDetailsList, "idTheme")) {
                    ImportField idThemeField = new ImportField(itemArticleLM.findLCPByCompoundName("idTheme"));
                    ImportKey<?> themeKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Theme"),
                            itemArticleLM.findLCPByCompoundName("themeId").getMapping(idThemeField));
                    keys.add(themeKey);
                    props.add(new ImportProperty(idThemeField, itemArticleLM.findLCPByCompoundName("idTheme").getMapping(themeKey)));
                    props.add(new ImportProperty(idThemeField, itemArticleLM.findLCPByCompoundName("themeItem").getMapping(itemKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Theme")).getMapping(themeKey)));
                    props.add(new ImportProperty(idThemeField, itemArticleLM.findLCPByCompoundName("themeArticle").getMapping(articleKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Theme")).getMapping(themeKey)));
                    fields.add(idThemeField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idTheme);

                    ImportField nameThemeField = new ImportField(itemArticleLM.findLCPByCompoundName("nameTheme"));
                    props.add(new ImportProperty(nameThemeField, itemArticleLM.findLCPByCompoundName("nameTheme").getMapping(themeKey)));
                    fields.add(nameThemeField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameTheme);
                }*/
            }

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.getSession();
            session.sql.pushVolatileStats(null);
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context.getBL());
            session.sql.popVolatileStats(null);
            session.close();

            LM.findLAPByCompoundName("formRefresh").execute(context);
        }
    }

    private List<PurchaseInvoiceDetail> importUserInvoicesFromXLS(ExecutionContext context, byte[] importFile, Map<String, String[]> importColumns,
                                                                  String keyColumn, Integer startRow, Integer userInvoiceObject)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<PurchaseInvoiceDetail> purchaseInvoiceDetailList = new ArrayList<PurchaseInvoiceDetail>();

        HSSFWorkbook Wb = new HSSFWorkbook(new ByteArrayInputStream(importFile));

        HSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {
            String numberDocument = getXLSFieldValue(sheet, i, importColumns.get("numberDocument"));
            Date dateDocument = getXLSDateFieldValue(sheet, i, importColumns.get("dateDocument"));
            String currencyDocument = getXLSFieldValue(sheet, i, importColumns.get("currencyDocument"));
            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getXLSFieldValue(sheet, i, importColumns.get("barcodeItem")));
            String originalCustomsGroupItem = getXLSFieldValue(sheet, i, importColumns.get("originalCustomsGroupItem"));
            String idBatch = getXLSFieldValue(sheet, i, importColumns.get("idBatch"));
            String idItem = getXLSFieldValue(sheet, i, importColumns.get("idItem"));
            String idItemGroup = getXLSFieldValue(sheet, i, importColumns.get("idItemGroup"));
            String captionItem = getXLSFieldValue(sheet, i, importColumns.get("captionItem"));
            String UOMItem = getXLSFieldValue(sheet, i, importColumns.get("UOMItem"));
            String manufacturerItem = getXLSFieldValue(sheet, i, importColumns.get("manufacturerItem"));
            String nameCountry = getXLSFieldValue(sheet, i, importColumns.get("nameCountry"));
            nameCountry = nameCountry == null ? null : nameCountry.replace("*", "").trim().toUpperCase();
            String nameOriginCountry = getXLSFieldValue(sheet, i, importColumns.get("nameOriginCountry"));
            nameOriginCountry = nameOriginCountry == null ? null : nameOriginCountry.replace("*", "").trim().toUpperCase();
            String importCountryBatch = getXLSFieldValue(sheet, i, importColumns.get("importCountryBatch"));
            String idCustomerStock = getXLSFieldValue(sheet, i, importColumns.get("idCustomerStock"));
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(context, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(context, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(context, customerObject));
            BigDecimal quantity = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("quantity"));
            BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("price"));
            BigDecimal sum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("sum"));
            BigDecimal valueVAT = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("valueVAT"));
            BigDecimal sumVAT = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("manufacturingPrice"));
            String compliance = getXLSFieldValue(sheet, i, importColumns.get("compliance"));
            String declaration = getXLSFieldValue(sheet, i, importColumns.get("declaration"));
            Date expiryDate = getXLSDateFieldValue(sheet, i, importColumns.get("expiryDate"));
            String pharmacyPriceGroupItem = getXLSFieldValue(sheet, i, importColumns.get("pharmacyPriceGroupItem"));
            String seriesPharmacy = getXLSFieldValue(sheet, i, importColumns.get("seriesPharmacy"));
            String idArticle = getXLSFieldValue(sheet, i, importColumns.get("idArticle"));
            String captionArticle = getXLSFieldValue(sheet, i, importColumns.get("captionArticle"));
            String idColor = getXLSFieldValue(sheet, i, importColumns.get("idColor"));
            String nameColor = getXLSFieldValue(sheet, i, importColumns.get("nameColor"));
            String idCollection = getXLSFieldValue(sheet, i, importColumns.get("idCollection"));
            String nameCollection = getXLSFieldValue(sheet, i, importColumns.get("nameCollection"));
            String idSize = getXLSFieldValue(sheet, i, importColumns.get("idSize"));
            String nameSize = getXLSFieldValue(sheet, i, importColumns.get("nameSize"));
            String idSeasonYear = getXLSFieldValue(sheet, i, importColumns.get("idSeasonYear"));
            String idSeason = getXLSFieldValue(sheet, i, importColumns.get("idSeason"));
            String nameSeason = getXLSFieldValue(sheet, i, importColumns.get("nameSeason"));
            String idTheme = getXLSFieldValue(sheet, i, importColumns.get("idTheme"));
            String nameTheme = getXLSFieldValue(sheet, i, importColumns.get("nameTheme"));
            BigDecimal netWeight = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("netWeight"));
            BigDecimal grossWeight = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("grossWeight"));
            String composition = getXLSFieldValue(sheet, i, importColumns.get("composition"));
            String originalComposition = getXLSFieldValue(sheet, i, importColumns.get("originalComposition"));

            String keyColumnValue = getXLSFieldValue(sheet, i, importColumns.get(keyColumn));
            if (keyColumnValue != null && !keyColumnValue.isEmpty())
                purchaseInvoiceDetailList.add(new PurchaseInvoiceDetail(numberDocument, dateDocument, currencyDocument,
                        idUserInvoiceDetail, barcodeItem, idBatch, idItem, idItemGroup, originalCustomsGroupItem, captionItem,
                        UOMItem, manufacturerItem, nameCountry, nameOriginCountry, importCountryBatch, idCustomer,
                        idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT, invoiceSum,
                        manufacturingPrice, compliance, declaration, expiryDate, pharmacyPriceGroupItem, seriesPharmacy,
                        idArticle, captionArticle, idColor, nameColor, idCollection, nameCollection, idSize, nameSize,
                        idSeasonYear, idSeason, nameSeason, idTheme, nameTheme, netWeight, grossWeight, composition,
                        originalComposition));
        }

        return purchaseInvoiceDetailList;
    }

    private List<PurchaseInvoiceDetail> importUserInvoicesFromCSV(ExecutionContext context, byte[] importFile, Map<String, String[]> importColumns,
                                                                  String keyColumn, Integer startRow, String csvSeparator, Integer userInvoiceObject)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<PurchaseInvoiceDetail> purchaseInvoiceDetailList = new ArrayList<PurchaseInvoiceDetail>();

        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile)));
        String line;
        int count = 0;

        while ((line = br.readLine()) != null) {

            count++;

            if (count >= startRow) {

                String[] values = line.split(csvSeparator);

                String numberDocument = getCSVFieldValue(values, importColumns.get("numberDocument"));
                Date dateDocument = getCSVDateFieldValue(values, importColumns.get("dateDocument"));
                String currencyDocument = getCSVFieldValue(values, importColumns.get("currencyDocument"));
                String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + count;
                String barcodeItem = BarcodeUtils.convertBarcode12To13(getCSVFieldValue(values, importColumns.get("barcodeItem")));
                String idBatch = getCSVFieldValue(values, importColumns.get("idBatch"));
                String idItem = getCSVFieldValue(values, importColumns.get("idItem"));
                String idItemGroup = getCSVFieldValue(values, importColumns.get("idItemGroup"));
                String originalCustomsGroupItem = getCSVFieldValue(values, importColumns.get("originalCustomsGroupItem"));
                String captionItem = getCSVFieldValue(values, importColumns.get("captionItem"));
                String UOMItem = getCSVFieldValue(values, importColumns.get("UOMItem"));
                String manufacturerItem = getCSVFieldValue(values, importColumns.get("manufacturerItem"));
                String nameCountry = getCSVFieldValue(values, importColumns.get("nameCountry"));
                nameCountry = nameCountry == null ? null : nameCountry.replace("*", "").trim().toUpperCase();
                String nameOriginCountry = getCSVFieldValue(values, importColumns.get("nameOriginCountry"));
                nameOriginCountry = nameOriginCountry == null ? null : nameOriginCountry.replace("*", "").trim().toUpperCase();
                String importCountryBatch = getCSVFieldValue(values, importColumns.get("importCountryBatch"));
                String idCustomerStock = getCSVFieldValue(values, importColumns.get("idCustomerStock"));
                ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(context, new DataObject(idCustomerStock));
                ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(context, (DataObject) customerStockObject));
                String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(context, customerObject));
                BigDecimal quantity = getCSVBigDecimalFieldValue(values, importColumns.get("quantity"));
                BigDecimal price = getCSVBigDecimalFieldValue(values, importColumns.get("price"));
                BigDecimal sum = getCSVBigDecimalFieldValue(values, importColumns.get("sum"));
                BigDecimal valueVAT = getCSVBigDecimalFieldValue(values, importColumns.get("valueVAT"));
                BigDecimal sumVAT = getCSVBigDecimalFieldValue(values, importColumns.get("sumVAT"));
                BigDecimal invoiceSum = getCSVBigDecimalFieldValue(values, importColumns.get("invoiceSum"));
                BigDecimal manufacturingPrice = getCSVBigDecimalFieldValue(values, importColumns.get("manufacturingPrice"));
                String compliance = getCSVFieldValue(values, importColumns.get("compliance"));
                String declaration = getCSVFieldValue(values, importColumns.get("declaration"));
                Date expiryDate = getCSVDateFieldValue(values, importColumns.get("expiryDate"));
                String pharmacyPriceGroupItem = getCSVFieldValue(values, importColumns.get("pharmacyPriceGroupItem"));
                String seriesPharmacy = getCSVFieldValue(values, importColumns.get("seriesPharmacy"));
                String idArticle = getCSVFieldValue(values, importColumns.get("idArticle"));
                String captionArticle = getCSVFieldValue(values, importColumns.get("captionArticle"));
                String idColor = getCSVFieldValue(values, importColumns.get("idColor"));
                String nameColor = getCSVFieldValue(values, importColumns.get("nameColor"));
                String idCollection = getCSVFieldValue(values, importColumns.get("idCollection"));
                String nameCollection = getCSVFieldValue(values, importColumns.get("nameCollection"));
                String idSize = getCSVFieldValue(values, importColumns.get("idSize"));
                String nameSize = getCSVFieldValue(values, importColumns.get("nameSize"));
                String idSeasonYear = getCSVFieldValue(values, importColumns.get("idSeasonYear"));
                String idSeason = getCSVFieldValue(values, importColumns.get("idSeason"));
                String nameSeason = getCSVFieldValue(values, importColumns.get("nameSeason"));
                String idTheme = getCSVFieldValue(values, importColumns.get("idTheme"));
                String nameTheme = getCSVFieldValue(values, importColumns.get("nameTheme"));
                BigDecimal netWeight = getCSVBigDecimalFieldValue(values, importColumns.get("netWeight"));
                BigDecimal grossWeight = getCSVBigDecimalFieldValue(values, importColumns.get("grossWeight"));
                String composition = getCSVFieldValue(values, importColumns.get("composition"));
                String originalComposition = getCSVFieldValue(values, importColumns.get("originalComposition"));

                String keyColumnValue = getCSVFieldValue(values, importColumns.get(keyColumn));
                if (keyColumnValue != null && !keyColumnValue.isEmpty())
                    purchaseInvoiceDetailList.add(new PurchaseInvoiceDetail(numberDocument, dateDocument, currencyDocument,
                            idUserInvoiceDetail, barcodeItem, idBatch, idItem, idItemGroup, originalCustomsGroupItem, captionItem,
                            UOMItem, manufacturerItem, nameCountry, nameOriginCountry, importCountryBatch, idCustomer,
                            idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT, invoiceSum,
                            manufacturingPrice, compliance, declaration, expiryDate, pharmacyPriceGroupItem,
                            seriesPharmacy, idArticle, captionArticle, idColor, nameColor, idCollection, nameCollection,
                            idSize, nameSize, idSeasonYear, idSeason, nameSeason, idTheme, nameTheme, netWeight,
                            grossWeight, composition, originalComposition));

            }
        }

        return purchaseInvoiceDetailList;
    }

    private List<PurchaseInvoiceDetail> importUserInvoicesFromXLSX(ExecutionContext context, byte[] importFile, Map<String, String[]> importColumns,
                                                                   String keyColumn, Integer startRow, Integer userInvoiceObject)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<PurchaseInvoiceDetail> purchaseInvoiceDetailList = new ArrayList<PurchaseInvoiceDetail>();

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {

            String numberDocument = getXLSXFieldValue(sheet, i, importColumns.get("numberDocument"));
            Date dateDocument = getXLSXDateFieldValue(sheet, i, importColumns.get("dateDocument"));
            String currencyDocument = getXLSXFieldValue(sheet, i, importColumns.get("currencyDocument"));
            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getXLSXFieldValue(sheet, i, importColumns.get("barcodeItem")));
            String idBatch = getXLSXFieldValue(sheet, i, importColumns.get("idBatch"));
            String idItem = getXLSXFieldValue(sheet, i, importColumns.get("idItem"));
            String idItemGroup = getXLSXFieldValue(sheet, i, importColumns.get("idItemGroup"));
            String originalCustomsGroupItem = getXLSXFieldValue(sheet, i, importColumns.get("originalCustomsGroupItem"));
            String captionItem = getXLSXFieldValue(sheet, i, importColumns.get("captionItem"));
            String UOMItem = getXLSXFieldValue(sheet, i, importColumns.get("UOMItem"));
            String manufacturerItem = getXLSXFieldValue(sheet, i, importColumns.get("manufacturerItem"));
            String nameCountry = getXLSXFieldValue(sheet, i, importColumns.get("nameCountry"));
            nameCountry = nameCountry == null ? null : nameCountry.replace("*", "").trim().toUpperCase();
            String nameOriginCountry = getXLSXFieldValue(sheet, i, importColumns.get("nameOriginCountry"));
            nameOriginCountry = nameOriginCountry == null ? null : nameOriginCountry.replace("*", "").trim().toUpperCase();
            String importCountryBatch = getXLSXFieldValue(sheet, i, importColumns.get("importCountryBatch"));
            String idCustomerStock = getXLSXFieldValue(sheet, i, importColumns.get("idCustomerStock"));
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(context, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(context, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(context, customerObject));
            BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("quantity"));
            BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("price"));
            BigDecimal sum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("sum"));
            BigDecimal valueVAT = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("valueVAT"));
            BigDecimal sumVAT = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("manufacturingPrice"));
            String compliance = getXLSXFieldValue(sheet, i, importColumns.get("compliance"));
            String declaration = getXLSXFieldValue(sheet, i, importColumns.get("declaration"));
            Date expiryDate = getXLSXDateFieldValue(sheet, i, importColumns.get("expiryDate"));
            String pharmacyPriceGroupItem = getXLSXFieldValue(sheet, i, importColumns.get("pharmacyPriceGroupItem"));
            String seriesPharmacy = getXLSXFieldValue(sheet, i, importColumns.get("seriesPharmacy"));
            String idArticle = getXLSXFieldValue(sheet, i, importColumns.get("idArticle"));
            String captionArticle = getXLSXFieldValue(sheet, i, importColumns.get("captionArticle"));
            String idColor = getXLSXFieldValue(sheet, i, importColumns.get("idColor"));
            String nameColor = getXLSXFieldValue(sheet, i, importColumns.get("nameColor"));
            String idCollection = getXLSXFieldValue(sheet, i, importColumns.get("idCollection"));
            String nameCollection = getXLSXFieldValue(sheet, i, importColumns.get("nameCollection"));
            String idSize = getXLSXFieldValue(sheet, i, importColumns.get("idSize"));
            String nameSize = getXLSXFieldValue(sheet, i, importColumns.get("nameSize"));
            String idSeasonYear = getXLSXFieldValue(sheet, i, importColumns.get("idSeasonYear"));
            String idSeason = getXLSXFieldValue(sheet, i, importColumns.get("idSeason"));
            String nameSeason = getXLSXFieldValue(sheet, i, importColumns.get("nameSeason"));
            String idTheme = getXLSXFieldValue(sheet, i, importColumns.get("idTheme"));
            String nameTheme = getXLSXFieldValue(sheet, i, importColumns.get("nameTheme"));
            BigDecimal netWeight = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("netWeight"));
            BigDecimal grossWeight = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("grossWeight"));
            String composition = getXLSXFieldValue(sheet, i, importColumns.get("composition"));
            String originalComposition = getXLSXFieldValue(sheet, i, importColumns.get("originalComposition"));

            String keyColumnValue = getXLSXFieldValue(sheet, i, importColumns.get(keyColumn));
            if (keyColumnValue != null && !keyColumnValue.isEmpty())
                purchaseInvoiceDetailList.add(new PurchaseInvoiceDetail(numberDocument, dateDocument, currencyDocument,
                        idUserInvoiceDetail, barcodeItem, idBatch, idItem, idItemGroup, originalCustomsGroupItem, captionItem,
                        UOMItem, manufacturerItem, nameCountry, nameOriginCountry, importCountryBatch, idCustomer,
                        idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT, invoiceSum,
                        manufacturingPrice, compliance, declaration, expiryDate, pharmacyPriceGroupItem, seriesPharmacy,
                        idArticle, captionArticle, idColor, nameColor, idCollection, nameCollection, idSize, nameSize,
                        idSeasonYear, idSeason, nameSeason, idTheme, nameTheme, netWeight, grossWeight, composition,
                        originalComposition));
        }

        return purchaseInvoiceDetailList;
    }

    private List<PurchaseInvoiceDetail> importUserInvoicesFromDBF(ExecutionContext context, byte[] importFile, Map<String, String[]> importColumns,
                                                                  String keyColumn, Integer startRow, Integer userInvoiceObject)
            throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<PurchaseInvoiceDetail> purchaseInvoiceDetailList = new ArrayList<PurchaseInvoiceDetail>();

        File tempFile = File.createTempFile("dutiesTNVED", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);


        DBF file = new DBF(tempFile.getPath());

        int totalRecordCount = file.getRecordCount();

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String numberDocument = getDBFFieldValue(file, importColumns.get("numberDocument"));
            Date dateDocument = getDBFDateFieldValue(file, importColumns.get("dateDocument"));
            String currencyDocument = getDBFFieldValue(file, importColumns.get("currencyDocument"));
            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getDBFFieldValue(file, importColumns.get("barcodeItem")));
            String idBatch = getDBFFieldValue(file, importColumns.get("idBatch"));
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"));
            String idItemGroup = getDBFFieldValue(file, importColumns.get("idItemGroup"));
            String originalCustomsGroupItem = getDBFFieldValue(file, importColumns.get("originalCustomsGroupItem"));
            String captionItem = getDBFFieldValue(file, importColumns.get("captionItem"));
            String UOMItem = getDBFFieldValue(file, importColumns.get("UOMItem"));
            String manufacturerItem = getDBFFieldValue(file, importColumns.get("manufacturerItem"));
            String nameCountry = getDBFFieldValue(file, importColumns.get("nameCountry"));
            nameCountry = nameCountry == null ? null : nameCountry.replace("*", "").trim().toUpperCase();
            String nameOriginCountry = getDBFFieldValue(file, importColumns.get("nameOriginCountry"));
            nameOriginCountry = nameOriginCountry == null ? null : nameOriginCountry.replace("*", "").trim().toUpperCase();
            String importCountryBatch = getDBFFieldValue(file, importColumns.get("importCountryBatch"));
            String idCustomerStock = getDBFFieldValue(file, importColumns.get("idCustomerStock"));
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(context, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(context, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(context, customerObject));
            BigDecimal quantity = getDBFBigDecimalFieldValue(file, importColumns.get("quantity"));
            BigDecimal price = getDBFBigDecimalFieldValue(file, importColumns.get("price"));
            BigDecimal sum = getDBFBigDecimalFieldValue(file, importColumns.get("sum"));
            BigDecimal valueVAT = getDBFBigDecimalFieldValue(file, importColumns.get("valueVAT"));
            BigDecimal sumVAT = getDBFBigDecimalFieldValue(file, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getDBFBigDecimalFieldValue(file, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getDBFBigDecimalFieldValue(file, importColumns.get("manufacturingPrice"));
            String compliance = getDBFFieldValue(file, importColumns.get("compliance"));
            String declaration = getDBFFieldValue(file, importColumns.get("declaration"));
            Date expiryDate = getDBFDateFieldValue(file, importColumns.get("expiryDate"));
            String pharmacyPriceGroup = getDBFFieldValue(file, importColumns.get("pharmacyPriceGroupItem"));
            String seriesPharmacy = getDBFFieldValue(file, importColumns.get("seriesPharmacy"));
            String idArticle = getDBFFieldValue(file, importColumns.get("idArticle"));
            String captionArticle = getDBFFieldValue(file, importColumns.get("captionArticle"));
            String idColor = getDBFFieldValue(file, importColumns.get("idColor"));
            String nameColor = getDBFFieldValue(file, importColumns.get("nameColor"));
            String idCollection = getDBFFieldValue(file, importColumns.get("idCollection"));
            String nameCollection = getDBFFieldValue(file, importColumns.get("nameCollection"));
            String idSize = getDBFFieldValue(file, importColumns.get("idSize"));
            String nameSize = getDBFFieldValue(file, importColumns.get("nameSize"));
            String idSeasonYear = getDBFFieldValue(file, importColumns.get("idSeasonYear"));
            String idSeason = getDBFFieldValue(file, importColumns.get("idSeason"));
            String nameSeason = getDBFFieldValue(file, importColumns.get("nameSeason"));
            String idTheme = getDBFFieldValue(file, importColumns.get("idTheme"));
            String nameTheme = getDBFFieldValue(file, importColumns.get("nameTheme"));
            BigDecimal netWeight = getDBFBigDecimalFieldValue(file, importColumns.get("netWeight"));
            BigDecimal grossWeight = getDBFBigDecimalFieldValue(file, importColumns.get("grossWeight"));
            String composition = getDBFFieldValue(file, importColumns.get("composition"));
            String originalComposition = getDBFFieldValue(file, importColumns.get("originalComposition"));

            String keyColumnValue = getDBFFieldValue(file, importColumns.get(keyColumn));
            if (keyColumnValue != null && !keyColumnValue.isEmpty())
                purchaseInvoiceDetailList.add(new PurchaseInvoiceDetail(numberDocument, dateDocument, currencyDocument,
                        idUserInvoiceDetail, barcodeItem, idBatch, idItem, idItemGroup, originalCustomsGroupItem, captionItem,
                        UOMItem, manufacturerItem, nameCountry, nameOriginCountry, importCountryBatch, idCustomer,
                        idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT, invoiceSum,
                        manufacturingPrice, compliance, declaration, expiryDate, pharmacyPriceGroup, seriesPharmacy,
                        idArticle, captionArticle, idColor, nameColor, idCollection, nameCollection, idSize, nameSize,
                        idSeasonYear, idSeason, nameSeason, idTheme, nameTheme, netWeight, grossWeight, composition,
                        originalComposition));
        }

        file.close();

        return purchaseInvoiceDetailList;
    }

    private Boolean showField(List<PurchaseInvoiceDetail> data, String fieldName) {
        try {
            Field field = PurchaseInvoiceDetail.class.getField(fieldName);

            for (int i = 0; i < data.size(); i++) {
                if (field.get(data.get(i)) != null)
                    return true;
            }
        } catch (NoSuchFieldException e) {
            return true;
        } catch (IllegalAccessException e) {
            return true;
        }
        return false;
    }
}

