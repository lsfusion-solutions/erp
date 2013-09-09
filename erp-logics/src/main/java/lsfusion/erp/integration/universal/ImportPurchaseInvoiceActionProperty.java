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
                startRow = startRow == null ? 1 : startRow;

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

                Map<String, String> importColumns = readImportColumns(context, importTypeObject);

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

    public void importUserInvoices(ExecutionContext context, DataObject userInvoiceObject, Map<String, String> importColumns,
                                   byte[] file, String fileExtension, Integer startRow, String csvSeparator, String itemKeyType,
                                   DataObject operationObject, DataObject supplierObject, DataObject supplierStockObject,
                                   DataObject customerObject, DataObject customerStockObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException {

        List<PurchaseInvoiceDetail> userInvoiceDetailsList;

        if (fileExtension.equals("DBF"))
            userInvoiceDetailsList = importUserInvoicesFromDBF(context, file, importColumns, startRow, (Integer) userInvoiceObject.object);
        else if (fileExtension.equals("XLS"))
            userInvoiceDetailsList = importUserInvoicesFromXLS(context, file, importColumns, startRow, (Integer) userInvoiceObject.object);
        else if (fileExtension.equals("XLSX"))
            userInvoiceDetailsList = importUserInvoicesFromXLSX(context, file, importColumns, startRow, (Integer) userInvoiceObject.object);
        else if (fileExtension.equals("CSV"))
            userInvoiceDetailsList = importUserInvoicesFromCSV(context, file, importColumns, startRow, csvSeparator, (Integer) userInvoiceObject.object);
        else
            userInvoiceDetailsList = null;

        if (userInvoiceDetailsList != null) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(userInvoiceDetailsList.size());

            if (showField(userInvoiceDetailsList, "numberUserInvoice")) {
                ImportField numberUserInvoiceField = new ImportField(LM.findLCPByCompoundName("numberObject"));
                props.add(new ImportProperty(numberUserInvoiceField, LM.findLCPByCompoundName("numberObject").getMapping(userInvoiceObject)));
                fields.add(numberUserInvoiceField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).numberUserInvoice);
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
                props.add(new ImportProperty(supplierObject, LM.findLCPByCompoundName("Sale.supplierUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty(supplierObject, LM.findLCPByCompoundName("Sale.supplierUserInvoice").getMapping(userInvoiceObject)));
            }

            if (supplierStockObject != null) {
                props.add(new ImportProperty(supplierStockObject, LM.findLCPByCompoundName("Sale.supplierStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty(supplierStockObject, LM.findLCPByCompoundName("Sale.supplierStockUserInvoice").getMapping(userInvoiceObject)));
            }

            if (customerObject != null) {
                props.add(new ImportProperty(customerObject, LM.findLCPByCompoundName("Sale.customerUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty(customerObject, LM.findLCPByCompoundName("Sale.customerUserInvoice").getMapping(userInvoiceObject)));
            }

            if (customerStockObject != null) {
                props.add(new ImportProperty(customerStockObject, LM.findLCPByCompoundName("Sale.customerStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty(customerStockObject, LM.findLCPByCompoundName("Sale.customerStockUserInvoice").getMapping(userInvoiceObject)));
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
                ImportField numberComplianceField = new ImportField(LM.findLCPByCompoundName("numberObject"));
                ImportKey<?> complianceKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Compliance"),
                        LM.findLCPByCompoundName("complianceId").getMapping(numberComplianceField));
                keys.add(complianceKey);
                props.add(new ImportProperty(numberComplianceField, LM.findLCPByCompoundName("numberObject").getMapping(complianceKey)));
                props.add(new ImportProperty(numberComplianceField, LM.findLCPByCompoundName("idCompliance").getMapping(complianceKey)));
                props.add(new ImportProperty(numberComplianceField, LM.findLCPByCompoundName("complianceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName("Compliance")).getMapping(complianceKey)));
                fields.add(numberComplianceField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).numberCompliance);
            }

            if (showField(userInvoiceDetailsList, "numberDeclaration")) {
                ImportField numberDeclarationField = new ImportField(LM.findLCPByCompoundName("numberObject"));
                ImportKey<?> declarationKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Declaration"),
                        LM.findLCPByCompoundName("declarationId").getMapping(numberDeclarationField));
                keys.add(declarationKey);
                props.add(new ImportProperty(numberDeclarationField, LM.findLCPByCompoundName("numberObject").getMapping(declarationKey)));
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

            if (purchaseInvoicePharmacyLM != null && showField(userInvoiceDetailsList, "seriesPharmacy")) {
                ImportField seriesPharmacyUserInvoiceDetailField = new ImportField(purchaseInvoicePharmacyLM.findLCPByCompoundName("Purchase.seriesPharmacyUserInvoiceDetail"));
                props.add(new ImportProperty(seriesPharmacyUserInvoiceDetailField, purchaseInvoicePharmacyLM.findLCPByCompoundName("Purchase.seriesPharmacyUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(seriesPharmacyUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).seriesPharmacy);
            }

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.getSession();
            session.sql.pushVolatileStats(null);
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context.getBL());
            session.sql.popVolatileStats(null);
            session.close();

        }
    }

    private List<PurchaseInvoiceDetail> importUserInvoicesFromXLS(ExecutionContext context, byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer userInvoiceObject) throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<PurchaseInvoiceDetail> purchaseInvoiceDetailList = new ArrayList<PurchaseInvoiceDetail>();

        HSSFWorkbook Wb = new HSSFWorkbook(new ByteArrayInputStream(importFile));

        HSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {
            String numberUserInvoice = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("numberDocument")), null);
            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("barcodeItem")), null));
            String idBatch = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("idBatch")), null);
            String idItem = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("idItem")), null);
            String captionItem = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("captionItem")), null);
            String UOMItem = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("UOMItem")), null);
            String manufacturerItem = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("manufacturerItem")), null);
            String countryItem = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("countryItem")), null);
            String idCustomerStock = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("idCustomerStock")), null);
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(context, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(context, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(context, customerObject));
            BigDecimal quantity = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("quantity")), null);
            BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("price")), null);
            BigDecimal sum = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("sum")), null);
            BigDecimal valueVAT = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("valueVAT")), null);
            BigDecimal sumVAT = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("sumVAT")), null);
            BigDecimal invoiceSum = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("invoiceSum")), null);
            BigDecimal manufacturingPrice = getXLSBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("manufacturingPrice")), null);
            String compliance = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("compliance")), null);
            String declaration = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("declaration")), null);
            Date expiryDate = getXLSDateFieldValue(sheet, i, getColumnNumber(importColumns.get("expiryDate")), null);
            String pharmacyPriceGroupItem = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("pharmacyPriceGroupItem")), null);
            String seriesPharmacy = getXLSFieldValue(sheet, i, getColumnNumber(importColumns.get("seriesPharmacy")), null);


            purchaseInvoiceDetailList.add(new PurchaseInvoiceDetail(numberUserInvoice, idUserInvoiceDetail, barcodeItem,
                    idBatch, idItem, captionItem, UOMItem, manufacturerItem, countryItem, idCustomer, idCustomerStock, quantity,
                    price, sum, VATifAllowed(valueVAT), sumVAT, invoiceSum, manufacturingPrice, compliance, declaration,
                    expiryDate, pharmacyPriceGroupItem, seriesPharmacy));
        }

        return purchaseInvoiceDetailList;
    }

    private List<PurchaseInvoiceDetail> importUserInvoicesFromCSV(ExecutionContext context, byte[] importFile, Map<String, String> importColumns,
                                                                  Integer startRow, String csvSeparator, Integer userInvoiceObject)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<PurchaseInvoiceDetail> purchaseInvoiceDetailList = new ArrayList<PurchaseInvoiceDetail>();

        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile)));
        String line;
        int count = 0;

        while ((line = br.readLine()) != null) {

            count++;

            if (count >= startRow) {

                String[] values = line.split(csvSeparator);

                String numberUserInvoice = getCSVFieldValue(values, getColumnNumber(importColumns.get("numberDocument")), null);
                String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + count;
                String barcodeItem = BarcodeUtils.convertBarcode12To13(getCSVFieldValue(values, getColumnNumber(importColumns.get("barcodeItem")), null));
                String idBatch = getCSVFieldValue(values, getColumnNumber(importColumns.get("idBatch")), null);
                String idItem = getCSVFieldValue(values, getColumnNumber(importColumns.get("idItem")), null);
                String captionItem = getCSVFieldValue(values, getColumnNumber(importColumns.get("captionItem")), null);
                String UOMItem = getCSVFieldValue(values, getColumnNumber(importColumns.get("UOMItem")), null);
                String manufacturerItem = getCSVFieldValue(values, getColumnNumber(importColumns.get("manufacturerItem")), null);
                String countryItem = getCSVFieldValue(values, getColumnNumber(importColumns.get("countryItem")), null);
                String idCustomerStock = getCSVFieldValue(values, getColumnNumber(importColumns.get("idCustomerStock")), null);
                ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(context, new DataObject(idCustomerStock));
                ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(context, (DataObject) customerStockObject));
                String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(context, customerObject));
                BigDecimal quantity = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("quantity")), null);
                BigDecimal price = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("price")), null);
                BigDecimal sum = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("sum")), null);
                BigDecimal valueVAT = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("valueVAT")), null);
                BigDecimal sumVAT = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("sumVAT")), null);
                BigDecimal invoiceSum = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("invoiceSum")), null);
                BigDecimal manufacturingPrice = getCSVBigDecimalFieldValue(values, getColumnNumber(importColumns.get("manufacturingPrice")), null);
                String compliance = getCSVFieldValue(values, getColumnNumber(importColumns.get("compliance")), null);
                String declaration = getCSVFieldValue(values, getColumnNumber(importColumns.get("declaration")), null);
                Date expiryDate = getCSVDateFieldValue(values, getColumnNumber(importColumns.get("expiryDate")), null);
                String pharmacyPriceGroupItem = getCSVFieldValue(values, getColumnNumber(importColumns.get("pharmacyPriceGroupItem")), null);
                String seriesPharmacy = getCSVFieldValue(values, getColumnNumber(importColumns.get("seriesPharmacy")), null);


                purchaseInvoiceDetailList.add(new PurchaseInvoiceDetail(numberUserInvoice, idUserInvoiceDetail, barcodeItem,
                        idBatch, idItem, captionItem, UOMItem, manufacturerItem, countryItem, idCustomer, idCustomerStock, quantity,
                        price, sum, VATifAllowed(valueVAT), sumVAT, invoiceSum, manufacturingPrice, compliance,
                        declaration, expiryDate, pharmacyPriceGroupItem, seriesPharmacy));

            }
        }

        return purchaseInvoiceDetailList;
    }

    private List<PurchaseInvoiceDetail> importUserInvoicesFromXLSX(ExecutionContext context, byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer userInvoiceObject) throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<PurchaseInvoiceDetail> purchaseInvoiceDetailList = new ArrayList<PurchaseInvoiceDetail>();

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {

            String numberUserInvoice = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("numberDocument")), null);
            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("barcodeItem")), null));
            String idBatch = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("idBatch")), null);
            String idItem = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("idItem")), null);
            String captionItem = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("captionItem")), null);
            String UOMItem = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("UOMItem")), null);
            String manufacturerItem = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("manufacturerItem")), null);
            String countryItem = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("countryItem")), null);
            String idCustomerStock = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("idCustomerStock")), null);
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(context, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(context, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(context, customerObject));
            BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("quantity")), null);
            BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("price")), null);
            BigDecimal sum = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("sum")), null);
            BigDecimal valueVAT = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("valueVAT")), null);
            BigDecimal sumVAT = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("sumVAT")), null);
            BigDecimal invoiceSum = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("invoiceSum")), null);
            BigDecimal manufacturingPrice = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumber(importColumns.get("manufacturingPrice")), null);
            String compliance = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("compliance")), null);
            String declaration = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("declaration")), null);
            Date expiryDate = getXLSXDateFieldValue(sheet, i, getColumnNumber(importColumns.get("expiryDate")), null);
            String pharmacyPriceGroupItem = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("pharmacyPriceGroupItem")), null);
            String seriesPharmacy = getXLSXFieldValue(sheet, i, getColumnNumber(importColumns.get("seriesPharmacy")), null);


            purchaseInvoiceDetailList.add(new PurchaseInvoiceDetail(numberUserInvoice, idUserInvoiceDetail, barcodeItem,
                    idBatch, idItem, captionItem, UOMItem, manufacturerItem, countryItem, idCustomer, idCustomerStock, quantity,
                    price, sum, VATifAllowed(valueVAT), sumVAT, invoiceSum, manufacturingPrice, compliance, declaration,
                    expiryDate, pharmacyPriceGroupItem, seriesPharmacy));
        }

        return purchaseInvoiceDetailList;
    }

    private List<PurchaseInvoiceDetail> importUserInvoicesFromDBF(ExecutionContext context, byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer userInvoiceObject) throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<PurchaseInvoiceDetail> purchaseInvoiceDetailList = new ArrayList<PurchaseInvoiceDetail>();

        File tempFile = File.createTempFile("dutiesTNVED", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);


        DBF file = new DBF(tempFile.getPath());

        int totalRecordCount = file.getRecordCount();

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String numberUserInvoice = getDBFFieldValue(file, importColumns.get("numberDocument"), "cp866", null);
            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getDBFFieldValue(file, importColumns.get("barcodeItem"), "cp866", null));
            String idBatch = getDBFFieldValue(file, importColumns.get("idBatch"), "cp866", null);
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"), "cp866", null);
            String captionItem = getDBFFieldValue(file, importColumns.get("captionItem"), "cp866", null);
            String UOMItem = getDBFFieldValue(file, importColumns.get("UOMItem"), "cp866", null);
            String manufacturerItem = getDBFFieldValue(file, importColumns.get("manufacturerItem"), "cp866", null);
            String countryItem = getDBFFieldValue(file, importColumns.get("countryItem"), "cp866", null);
            String idCustomerStock = getDBFFieldValue(file, importColumns.get("idCustomerStock"), "cp866", null);
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(context, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(context, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(context, customerObject));
            BigDecimal quantity = getDBFBigDecimalFieldValue(file, importColumns.get("quantity"), "cp866", null);
            BigDecimal price = getDBFBigDecimalFieldValue(file, importColumns.get("price"), "cp866", null);
            BigDecimal sum = getDBFBigDecimalFieldValue(file, importColumns.get("sum"), "cp866", null);
            BigDecimal valueVAT = getDBFBigDecimalFieldValue(file, importColumns.get("valueVAT"), "cp866", null);
            BigDecimal sumVAT = getDBFBigDecimalFieldValue(file, importColumns.get("sumVAT"), "cp866", null);
            BigDecimal invoiceSum = getDBFBigDecimalFieldValue(file, importColumns.get("inoiceSum"), "cp866", null);
            BigDecimal manufacturingPrice = getDBFBigDecimalFieldValue(file, importColumns.get("manufacturingPrice"), "cp866", null);
            String compliance = getDBFFieldValue(file, importColumns.get("compliance"), "cp866", null);
            String declaration = getDBFFieldValue(file, importColumns.get("declaration"), "cp866", null);
            Date expiryDate = getDBFDateFieldValue(file, importColumns.get("expiryDate"), "cp866", null);
            String pharmacyPriceGroup = getDBFFieldValue(file, importColumns.get("pharmacyPriceGroupItem"), "cp866", null);
            String seriesPharmacy = getDBFFieldValue(file, importColumns.get("seriesPharmacy"), "cp866", null);

            purchaseInvoiceDetailList.add(new PurchaseInvoiceDetail(numberUserInvoice, idUserInvoiceDetail, barcodeItem,
                    idBatch, idItem, captionItem, UOMItem, manufacturerItem, countryItem, idCustomer, idCustomerStock, quantity,
                    price, sum, VATifAllowed(valueVAT), sumVAT, invoiceSum, manufacturingPrice, compliance, declaration,
                    expiryDate, pharmacyPriceGroup, seriesPharmacy));
        }

        file.close();

        return purchaseInvoiceDetailList;
    }

    private List<List<Object>> initData(int size) {
        List<List<Object>> data = new ArrayList<List<Object>>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<Object>());
        }
        return data;
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

