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
    ScriptingLogicsModule purhcaseShipmentBoxLM;
    
    public ImportPurchaseInvoiceActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Purchase.UserInvoice"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        userInvoiceInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {

            DataSession session = context.getSession();

            this.purchaseManufacturingPriceLM = (ScriptingLogicsModule) context.getBL().getModule("PurchaseManufacturingPrice");
            this.itemPharmacyByLM = (ScriptingLogicsModule) context.getBL().getModule("ItemPharmacyBy");
            this.purchaseInvoicePharmacyLM = (ScriptingLogicsModule) context.getBL().getModule("PurchaseInvoicePharmacy");
            this.itemArticleLM = (ScriptingLogicsModule) context.getBL().getModule("ItemArticle");
            this.customsGroupArticleLM = (ScriptingLogicsModule) context.getBL().getModule("CustomsGroupArticle");
            this.purhcaseShipmentBoxLM = (ScriptingLogicsModule) context.getBL().getModule("PurchaseShipmentBox");
            
            DataObject userInvoiceObject = context.getDataKeyValue(userInvoiceInterface);

            ObjectValue importTypeObject = LM.findLCPByCompoundName("importTypeUserInvoice").readClasses(session, userInvoiceObject);

            if (!(importTypeObject instanceof NullValue)) {

                String fileExtension = trim((String) LM.findLCPByCompoundName("captionFileExtensionImportType").read(session, importTypeObject));

                String primaryKeyType = parseKeyType((String) LM.findLCPByCompoundName("namePrimaryKeyTypeImportType").read(session, importTypeObject));
                String secondaryKeyType = parseKeyType((String) LM.findLCPByCompoundName("nameSecondaryKeyTypeImportType").read(session, importTypeObject));

                String csvSeparator = trim((String) LM.findLCPByCompoundName("separatorImportType").read(session, importTypeObject));
                csvSeparator = csvSeparator == null ? ";" : csvSeparator.trim();
                Integer startRow = (Integer) LM.findLCPByCompoundName("startRowImportType").read(session, importTypeObject);
                startRow = startRow == null || startRow.equals(0) ? 1 : startRow;

                ObjectValue operationObject = LM.findLCPByCompoundName("autoImportOperationImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierObject = LM.findLCPByCompoundName("autoImportSupplierImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierStockObject = LM.findLCPByCompoundName("autoImportSupplierStockImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerObject = LM.findLCPByCompoundName("autoImportCustomerImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerStockObject = LM.findLCPByCompoundName("autoImportCustomerStockImportType").readClasses(session, (DataObject) importTypeObject);

                Map<String, String[]> importColumns = readImportColumns(context, LM, importTypeObject);

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            List<List<PurchaseInvoiceDetail>> userInvoiceDetailsList = importUserInvoicesFromFile(session,
                                    (Integer) userInvoiceObject.object, importColumns, file, fileExtension, startRow, csvSeparator,
                                    primaryKeyType, secondaryKeyType);

                            if (userInvoiceDetailsList != null && userInvoiceDetailsList.size() >= 1)
                                importUserInvoices(userInvoiceDetailsList.get(0), session, userInvoiceObject,
                                        primaryKeyType, operationObject, supplierObject, supplierStockObject,
                                        customerObject, customerStockObject);

                            if (userInvoiceDetailsList != null && userInvoiceDetailsList.size() >= 2)
                                importUserInvoices(userInvoiceDetailsList.get(1), session, userInvoiceObject,
                                        secondaryKeyType, operationObject, supplierObject, supplierStockObject,
                                        customerObject, customerStockObject);

                            session.apply(context.getBL());
                            session.close();

                            LM.findLAPByCompoundName("formRefresh").execute(context);
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

    public void importUserInvoices(List<PurchaseInvoiceDetail> userInvoiceDetailsList, DataSession session, DataObject userInvoiceObject,
                                   String keyType, ObjectValue operationObject, ObjectValue supplierObject,
                                   ObjectValue supplierStockObject, ObjectValue customerObject, ObjectValue customerStockObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException {


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

            if (operationObject instanceof DataObject)
                props.add(new ImportProperty((DataObject) operationObject, LM.findLCPByCompoundName("Purchase.operationUserInvoice").getMapping(userInvoiceObject)));

            if (supplierObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierObject, LM.findLCPByCompoundName("Purchase.supplierUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty((DataObject) supplierObject, LM.findLCPByCompoundName("Purchase.supplierUserInvoice").getMapping(userInvoiceObject)));
            }

            if (supplierStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierStockObject, LM.findLCPByCompoundName("Purchase.supplierStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty((DataObject) supplierStockObject, LM.findLCPByCompoundName("Purchase.supplierStockUserInvoice").getMapping(userInvoiceObject)));
            }

            if (customerObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerObject, LM.findLCPByCompoundName("Purchase.customerUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty((DataObject) customerObject, LM.findLCPByCompoundName("Purchase.customerUserInvoice").getMapping(userInvoiceObject)));
            }

            if (customerStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerStockObject, LM.findLCPByCompoundName("Purchase.customerStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty((DataObject) customerStockObject, LM.findLCPByCompoundName("Purchase.customerStockUserInvoice").getMapping(userInvoiceObject)));
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

            if (purhcaseShipmentBoxLM != null && showField(userInvoiceDetailsList, "idBox")) {
                ImportField idBoxField = new ImportField(purhcaseShipmentBoxLM.findLCPByCompoundName("idBox"));
                ImportKey<?> boxKey = new ImportKey((ConcreteCustomClass) purhcaseShipmentBoxLM.findClassByCompoundName("Box"),
                        purhcaseShipmentBoxLM.findLCPByCompoundName("boxId").getMapping(idBoxField));
                keys.add(boxKey);
                props.add(new ImportProperty(idBoxField, purhcaseShipmentBoxLM.findLCPByCompoundName("idBox").getMapping(boxKey)));
                props.add(new ImportProperty(idBoxField, purhcaseShipmentBoxLM.findLCPByCompoundName("boxUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        purhcaseShipmentBoxLM.object(purhcaseShipmentBoxLM.findClassByCompoundName("Box")).getMapping(boxKey)));
                fields.add(idBoxField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idBox);

                if (showField(userInvoiceDetailsList, "nameBox")) {
                    ImportField nameBoxField = new ImportField(purhcaseShipmentBoxLM.findLCPByCompoundName("nameBox"));
                    props.add(new ImportProperty(nameBoxField, purhcaseShipmentBoxLM.findLCPByCompoundName("nameBox").getMapping(boxKey)));
                    fields.add(nameBoxField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameBox);
                }
            }
            
            ImportField dataIndexUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("dataIndexUserInvoiceDetail"));
            props.add(new ImportProperty(dataIndexUserInvoiceDetailField, LM.findLCPByCompoundName("dataIndexUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(dataIndexUserInvoiceDetailField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).dataIndex);

            ImportField idItemField = new ImportField(LM.findLCPByCompoundName("idItem"));
            fields.add(idItemField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idItem);

            String iGroupAggr = (keyType == null || keyType.equals("item")) ? "itemId" : keyType.equals("barcode") ? "skuIdBarcode" : "skuBatchId";
            ImportField iField = (keyType == null || keyType.equals("item")) ? idItemField : keyType.equals("barcode") ? idBarcodeSkuField : idBatchField;
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

            if (showField(userInvoiceDetailsList, "originalCaptionItem")) {
                ImportField originalCaptionItemField = new ImportField(LM.findLCPByCompoundName("originalCaptionItem"));
                props.add(new ImportProperty(originalCaptionItemField, LM.findLCPByCompoundName("originalCaptionItem").getMapping(itemKey)));
                fields.add(originalCaptionItemField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).originalCaptionItem);
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

                if (showField(userInvoiceDetailsList, "nameManufacturer")) {
                    ImportField nameManufacturerField = new ImportField(LM.findLCPByCompoundName("nameManufacturer"));
                    props.add(new ImportProperty(nameManufacturerField, LM.findLCPByCompoundName("nameManufacturer").getMapping(manufacturerKey)));
                    fields.add(nameManufacturerField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameManufacturer);
                }
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

                if (showField(userInvoiceDetailsList, "nameOriginCountry")) {
                    ImportField nameOriginCountryField = new ImportField(LM.findLCPByCompoundName("nameOriginCountry"));
                    props.add(new ImportProperty(nameOriginCountryField, LM.findLCPByCompoundName("nameOriginCountry").getMapping(countryKey)));
                    fields.add(nameOriginCountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameOriginCountry);
                }
            } else if (showField(userInvoiceDetailsList, "nameOriginCountry")) {
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

                if (showField(userInvoiceDetailsList, "dateCompliance")) {
                    ImportField dateComplianceField = new ImportField(LM.findLCPByCompoundName("dateCompliance"));
                    props.add(new ImportProperty(dateComplianceField, LM.findLCPByCompoundName("dateCompliance").getMapping(complianceKey)));
                    fields.add(dateComplianceField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).dateCompliance);
                }
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

            if (showField(userInvoiceDetailsList, "manufactureDate")) {
                ImportField manufactureDateUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("manufactureDateUserInvoiceDetail"));
                props.add(new ImportProperty(manufactureDateUserInvoiceDetailField, LM.findLCPByCompoundName("manufactureDateUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(manufactureDateUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).manufactureDate);
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

            if (purchaseInvoicePharmacyLM != null) {
                
                if (showField(userInvoiceDetailsList, "nameImportCountry")) {
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

                if (showField(userInvoiceDetailsList, "seriesPharmacy")) {
                    ImportField seriesPharmacyUserInvoiceDetailField = new ImportField(purchaseInvoicePharmacyLM.findLCPByCompoundName("Purchase.seriesPharmacyUserInvoiceDetail"));
                    props.add(new ImportProperty(seriesPharmacyUserInvoiceDetailField, purchaseInvoicePharmacyLM.findLCPByCompoundName("Purchase.seriesPharmacyUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(seriesPharmacyUserInvoiceDetailField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).seriesPharmacy);
                }

                if (showField(userInvoiceDetailsList, "contractPrice")) {
                    ImportField contractPriceUserInvoiceDetailField = new ImportField(purchaseInvoicePharmacyLM.findLCPByCompoundName("contractPriceUserInvoiceDetail"));
                    props.add(new ImportProperty(contractPriceUserInvoiceDetailField, purchaseInvoicePharmacyLM.findLCPByCompoundName("contractPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(contractPriceUserInvoiceDetailField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).contractPrice);
                }
            }
            if (showField(userInvoiceDetailsList, "sumNetWeight")) {
                ImportField sumNetWeightField = new ImportField(LM.findLCPByCompoundName("sumNetWeightUserInvoiceDetail"));
                props.add(new ImportProperty(sumNetWeightField, LM.findLCPByCompoundName("sumNetWeightUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(sumNetWeightField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).sumNetWeight);
            }

            if (showField(userInvoiceDetailsList, "sumGrossWeight")) {
                ImportField sumGrossWeightField = new ImportField(LM.findLCPByCompoundName("sumGrossWeightUserInvoiceDetail"));
                props.add(new ImportProperty(sumGrossWeightField, LM.findLCPByCompoundName("sumGrossWeightUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(sumGrossWeightField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).sumGrossWeight);
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

                if (showField(userInvoiceDetailsList, "idItemGroup")) {
                    ImportField idItemGroupField = new ImportField(LM.findLCPByCompoundName("idItemGroup"));
                    ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("ItemGroup"),
                            LM.findLCPByCompoundName("itemGroupId").getMapping(idItemGroupField));
                    keys.add(itemGroupKey);
                    props.add(new ImportProperty(idItemGroupField, LM.findLCPByCompoundName("itemGroupItem").getMapping(itemKey),
                            LM.object(LM.findClassByCompoundName("ItemGroup")).getMapping(itemGroupKey)));
                    if (itemArticleLM != null)
                        props.add(new ImportProperty(idItemGroupField, itemArticleLM.findLCPByCompoundName("itemGroupArticle").getMapping(articleKey),
                                itemArticleLM.object(itemArticleLM.findClassByCompoundName("ItemGroup")).getMapping(itemGroupKey)));
                    fields.add(idItemGroupField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idItemGroup);
                }

                if (showField(userInvoiceDetailsList, "captionArticle")) {
                    ImportField captionArticleField = new ImportField(itemArticleLM.findLCPByCompoundName("captionArticle"));
                    props.add(new ImportProperty(captionArticleField, itemArticleLM.findLCPByCompoundName("captionArticle").getMapping(articleKey)));
                    fields.add(captionArticleField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).captionArticle);
                }

                if (showField(userInvoiceDetailsList, "originalCaptionArticle")) {
                    ImportField originalCaptionArticleField = new ImportField(itemArticleLM.findLCPByCompoundName("originalCaptionArticle"));
                    props.add(new ImportProperty(originalCaptionArticleField, itemArticleLM.findLCPByCompoundName("originalCaptionArticle").getMapping(articleKey)));
                    fields.add(originalCaptionArticleField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).originalCaptionArticle);
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

                if (itemArticleLM != null && showField(userInvoiceDetailsList, "idSize")) {
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

                    if (showField(userInvoiceDetailsList, "nameOriginalSize")) {
                        ImportField nameOriginalSizeField = new ImportField(itemArticleLM.findLCPByCompoundName("nameOriginalSize"));
                        props.add(new ImportProperty(nameOriginalSizeField, itemArticleLM.findLCPByCompoundName("nameOriginalSize").getMapping(sizeKey)));
                        fields.add(nameOriginalSizeField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameOriginalSize);
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

                if (showField(userInvoiceDetailsList, "idBrand")) {
                    ImportField idBrandField = new ImportField(itemArticleLM.findLCPByCompoundName("idBrand"));
                    ImportKey<?> brandKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Brand"),
                            itemArticleLM.findLCPByCompoundName("brandId").getMapping(idBrandField));
                    keys.add(brandKey);
                    props.add(new ImportProperty(idBrandField, itemArticleLM.findLCPByCompoundName("idBrand").getMapping(brandKey)));
                    props.add(new ImportProperty(idBrandField, itemArticleLM.findLCPByCompoundName("brandArticle").getMapping(articleKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Brand")).getMapping(brandKey)));
                    props.add(new ImportProperty(idBrandField, itemArticleLM.findLCPByCompoundName("brandItem").getMapping(itemKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Brand")).getMapping(brandKey)));
                    fields.add(idBrandField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idBrand);

                    if (showField(userInvoiceDetailsList, "nameBrand")) {
                        ImportField nameBrandField = new ImportField(itemArticleLM.findLCPByCompoundName("nameBrand"));
                        props.add(new ImportProperty(nameBrandField, itemArticleLM.findLCPByCompoundName("nameBrand").getMapping(brandKey)));
                        fields.add(nameBrandField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameBrand);
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

            session.sql.pushVolatileStats(null);
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.sql.popVolatileStats(null);
        }
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromFile(DataSession session, Integer userInvoiceObject, Map<String, String[]> importColumns,
                                                                         byte[] file, String fileExtension, Integer startRow, String csvSeparator, String primaryKeyType,
                                                                         String secondaryKeyType)
            throws SQLException, xBaseJException, ScriptingErrorLog.SemanticErrorException, ParseException, IOException, BiffException {

        List<List<PurchaseInvoiceDetail>> userInvoiceDetailsList;

        String primaryKeyColumn = getKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getKeyColumn(secondaryKeyType);

        if (fileExtension.equals("DBF"))
            userInvoiceDetailsList = importUserInvoicesFromDBF(session, file, importColumns, primaryKeyColumn, secondaryKeyColumn, startRow, userInvoiceObject);
        else if (fileExtension.equals("XLS"))
            userInvoiceDetailsList = importUserInvoicesFromXLS(session, file, importColumns, primaryKeyColumn, secondaryKeyColumn, startRow, userInvoiceObject);
        else if (fileExtension.equals("XLSX"))
            userInvoiceDetailsList = importUserInvoicesFromXLSX(session, file, importColumns, primaryKeyColumn, secondaryKeyColumn, startRow, userInvoiceObject);
        else if (fileExtension.equals("CSV"))
            userInvoiceDetailsList = importUserInvoicesFromCSV(session, file, importColumns, primaryKeyColumn, secondaryKeyColumn, startRow, csvSeparator, userInvoiceObject);
        else
            userInvoiceDetailsList = null;

        return userInvoiceDetailsList;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromXLS(DataSession session, byte[] importFile, Map<String, String[]> importColumns,
                                                                        String primaryKeyColumn, String secondaryKeyColumn, Integer startRow, Integer userInvoiceObject)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<PurchaseInvoiceDetail>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<PurchaseInvoiceDetail>();

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
            Integer dataIndex = Integer.parseInt(getXLSFieldValue(sheet, i, importColumns.get("dataIndex"), String.valueOf(primaryList.size() + secondaryList.size() + 1)));
            String idItem = getXLSFieldValue(sheet, i, importColumns.get("idItem"));
            String idItemGroup = getXLSFieldValue(sheet, i, importColumns.get("idItemGroup"));
            String captionItem = getXLSFieldValue(sheet, i, importColumns.get("captionItem"));
            String originalCaptionItem = getXLSFieldValue(sheet, i, importColumns.get("originalCaptionItem"));
            String UOMItem = getXLSFieldValue(sheet, i, importColumns.get("UOMItem"));
            String idManufacturer = getXLSFieldValue(sheet, i, importColumns.get("idManufacturer"));
            String nameManufacturer = getXLSFieldValue(sheet, i, importColumns.get("nameManufacturer"));
            String nameCountry = getXLSFieldValue(sheet, i, importColumns.get("nameCountry"));
            nameCountry = nameCountry == null ? null : nameCountry.replace("*", "").trim().toUpperCase();
            String nameOriginCountry = getXLSFieldValue(sheet, i, importColumns.get("nameOriginCountry"));
            nameOriginCountry = nameOriginCountry == null ? null : nameOriginCountry.replace("*", "").trim().toUpperCase();
            String importCountryBatch = getXLSFieldValue(sheet, i, importColumns.get("importCountryBatch"));
            String idCustomerStock = getXLSFieldValue(sheet, i, importColumns.get("idCustomerStock"));
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("quantity"));
            BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("price"));
            BigDecimal sum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("sum"));
            BigDecimal valueVAT = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("valueVAT"));
            BigDecimal sumVAT = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("manufacturingPrice"));
            String contractPrice = getXLSFieldValue(sheet, i, importColumns.get("contractPrice"));
            String numberCompliance = getXLSFieldValue(sheet, i, importColumns.get("numberCompliance"));
            Date dateCompliance = getXLSDateFieldValue(sheet, i, importColumns.get("dateCompliance"));
            String declaration = getXLSFieldValue(sheet, i, importColumns.get("declaration"));
            Date expiryDate = getXLSDateFieldValue(sheet, i, importColumns.get("expiryDate"));
            Date manufactureDate = getXLSDateFieldValue(sheet, i, importColumns.get("manufactureDate"));
            String pharmacyPriceGroupItem = getXLSFieldValue(sheet, i, importColumns.get("pharmacyPriceGroupItem"));
            String seriesPharmacy = getXLSFieldValue(sheet, i, importColumns.get("seriesPharmacy"));
            String idArticle = getXLSFieldValue(sheet, i, importColumns.get("idArticle"));
            String captionArticle = getXLSFieldValue(sheet, i, importColumns.get("captionArticle"));
            String originalCaptionArticle = getXLSFieldValue(sheet, i, importColumns.get("originalCaptionArticle"));
            String idColor = getXLSFieldValue(sheet, i, importColumns.get("idColor"));
            String nameColor = getXLSFieldValue(sheet, i, importColumns.get("nameColor"));
            String idCollection = getXLSFieldValue(sheet, i, importColumns.get("idCollection"));
            String nameCollection = getXLSFieldValue(sheet, i, importColumns.get("nameCollection"));
            String idSize = getXLSFieldValue(sheet, i, importColumns.get("idSize"));
            String nameSize = getXLSFieldValue(sheet, i, importColumns.get("nameSize"));
            String nameOriginalSize = getXLSFieldValue(sheet, i, importColumns.get("nameOriginalSize"));
            String idSeasonYear = getXLSFieldValue(sheet, i, importColumns.get("idSeasonYear"));
            String idSeason = getXLSFieldValue(sheet, i, importColumns.get("idSeason"));
            String nameSeason = getXLSFieldValue(sheet, i, importColumns.get("nameSeason"));
            String idBrand = getXLSFieldValue(sheet, i, importColumns.get("idBrand"));
            String nameBrand = getXLSFieldValue(sheet, i, importColumns.get("nameBrand"));
            String idBox = getXLSFieldValue(sheet, i, importColumns.get("idBox"));
            String nameBox = getXLSFieldValue(sheet, i, importColumns.get("nameBox"));
            String idTheme = getXLSFieldValue(sheet, i, importColumns.get("idTheme"));
            String nameTheme = getXLSFieldValue(sheet, i, importColumns.get("nameTheme"));
            BigDecimal netWeight = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("netWeight"));
            BigDecimal netWeightSum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("netWeightSum"));
            netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
            BigDecimal grossWeight = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("grossWeight"));
            BigDecimal grossWeightSum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("grossWeightSum"));
            grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;
            String composition = getXLSFieldValue(sheet, i, importColumns.get("composition"));
            String originalComposition = getXLSFieldValue(sheet, i, importColumns.get("originalComposition"));

            PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(numberDocument, dateDocument, currencyDocument,
                    idUserInvoiceDetail, barcodeItem, idBatch, dataIndex, idItem, idItemGroup, originalCustomsGroupItem,
                    captionItem, originalCaptionItem, UOMItem, idManufacturer, nameManufacturer, nameCountry, nameOriginCountry,
                    importCountryBatch, idCustomer, idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT),
                    sumVAT, invoiceSum, manufacturingPrice, contractPrice, numberCompliance, dateCompliance, declaration,
                    expiryDate, manufactureDate, pharmacyPriceGroupItem, seriesPharmacy, idArticle, captionArticle,
                    originalCaptionArticle, idColor, nameColor, idCollection, nameCollection, idSize, nameSize,
                    nameOriginalSize, idSeasonYear, idSeason, nameSeason, idBrand, nameBrand, idBox, nameBox, idTheme,
                    nameTheme, netWeight, netWeightSum, grossWeight, grossWeightSum, composition, originalComposition);

            String primaryKeyColumnValue = getXLSFieldValue(sheet, i, importColumns.get(primaryKeyColumn));
            String secondaryKeyColumnValue = getXLSFieldValue(sheet, i, importColumns.get(secondaryKeyColumn));
            if (primaryKeyColumnValue != null && !primaryKeyColumnValue.isEmpty())
                primaryList.add(purchaseInvoiceDetail);
            else if (secondaryKeyColumnValue != null && !secondaryKeyColumnValue.isEmpty())
                primaryList.add(purchaseInvoiceDetail);
        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromCSV(DataSession session, byte[] importFile, Map<String, String[]> importColumns,
                                                                        String primaryKeyColumn, String secondaryKeyColumn, Integer startRow, String csvSeparator, Integer userInvoiceObject)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<PurchaseInvoiceDetail>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<PurchaseInvoiceDetail>();

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
                Integer dataIndex = Integer.parseInt(getCSVFieldValue(values, importColumns.get("idItem"), String.valueOf(primaryList.size() + secondaryList.size() + 1)));
                String idItem = getCSVFieldValue(values, importColumns.get("idItem"));
                String idItemGroup = getCSVFieldValue(values, importColumns.get("idItemGroup"));
                String originalCustomsGroupItem = getCSVFieldValue(values, importColumns.get("originalCustomsGroupItem"));
                String captionItem = getCSVFieldValue(values, importColumns.get("captionItem"));
                String originalCaptionItem = getCSVFieldValue(values, importColumns.get("originalCaptionItem"));
                String UOMItem = getCSVFieldValue(values, importColumns.get("UOMItem"));
                String idManufacturer = getCSVFieldValue(values, importColumns.get("idManufacturer"));
                String nameManufacturer = getCSVFieldValue(values, importColumns.get("nameManufacturer"));
                String nameCountry = getCSVFieldValue(values, importColumns.get("nameCountry"));
                nameCountry = nameCountry == null ? null : nameCountry.replace("*", "").trim().toUpperCase();
                String nameOriginCountry = getCSVFieldValue(values, importColumns.get("nameOriginCountry"));
                nameOriginCountry = nameOriginCountry == null ? null : nameOriginCountry.replace("*", "").trim().toUpperCase();
                String importCountryBatch = getCSVFieldValue(values, importColumns.get("importCountryBatch"));
                String idCustomerStock = getCSVFieldValue(values, importColumns.get("idCustomerStock"));
                ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(session, new DataObject(idCustomerStock));
                ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
                String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(session, customerObject));
                BigDecimal quantity = getCSVBigDecimalFieldValue(values, importColumns.get("quantity"));
                BigDecimal price = getCSVBigDecimalFieldValue(values, importColumns.get("price"));
                BigDecimal sum = getCSVBigDecimalFieldValue(values, importColumns.get("sum"));
                BigDecimal valueVAT = getCSVBigDecimalFieldValue(values, importColumns.get("valueVAT"));
                BigDecimal sumVAT = getCSVBigDecimalFieldValue(values, importColumns.get("sumVAT"));
                BigDecimal invoiceSum = getCSVBigDecimalFieldValue(values, importColumns.get("invoiceSum"));
                BigDecimal manufacturingPrice = getCSVBigDecimalFieldValue(values, importColumns.get("manufacturingPrice"));
                String contractPrice = getCSVFieldValue(values, importColumns.get("contractPrice"));
                String numberCompliance = getCSVFieldValue(values, importColumns.get("numberCompliance"));
                Date dateCompliance = getCSVDateFieldValue(values, importColumns.get("dateCompliance"));
                String declaration = getCSVFieldValue(values, importColumns.get("declaration"));
                Date expiryDate = getCSVDateFieldValue(values, importColumns.get("expiryDate"));
                Date manufactureDate = getCSVDateFieldValue(values, importColumns.get("manufactureDate"));
                String pharmacyPriceGroupItem = getCSVFieldValue(values, importColumns.get("pharmacyPriceGroupItem"));
                String seriesPharmacy = getCSVFieldValue(values, importColumns.get("seriesPharmacy"));
                String idArticle = getCSVFieldValue(values, importColumns.get("idArticle"));
                String captionArticle = getCSVFieldValue(values, importColumns.get("captionArticle"));
                String originalCaptionArticle = getCSVFieldValue(values, importColumns.get("originalCaptionArticle"));
                String idColor = getCSVFieldValue(values, importColumns.get("idColor"));
                String nameColor = getCSVFieldValue(values, importColumns.get("nameColor"));
                String idCollection = getCSVFieldValue(values, importColumns.get("idCollection"));
                String nameCollection = getCSVFieldValue(values, importColumns.get("nameCollection"));
                String idSize = getCSVFieldValue(values, importColumns.get("idSize"));
                String nameSize = getCSVFieldValue(values, importColumns.get("nameSize"));
                String nameOriginalSize = getCSVFieldValue(values, importColumns.get("nameOriginalSize"));
                String idSeasonYear = getCSVFieldValue(values, importColumns.get("idSeasonYear"));
                String idSeason = getCSVFieldValue(values, importColumns.get("idSeason"));
                String nameSeason = getCSVFieldValue(values, importColumns.get("nameSeason"));
                String idBrand = getCSVFieldValue(values, importColumns.get("idBrand"));
                String nameBrand = getCSVFieldValue(values, importColumns.get("nameBrand"));
                String idBox = getCSVFieldValue(values, importColumns.get("idBox"));
                String nameBox = getCSVFieldValue(values, importColumns.get("nameBox"));
                String idTheme = getCSVFieldValue(values, importColumns.get("idTheme"));
                String nameTheme = getCSVFieldValue(values, importColumns.get("nameTheme"));
                BigDecimal netWeight = getCSVBigDecimalFieldValue(values, importColumns.get("netWeight"));
                BigDecimal netWeightSum = getCSVBigDecimalFieldValue(values, importColumns.get("netWeightSum"));
                netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
                BigDecimal grossWeight = getCSVBigDecimalFieldValue(values, importColumns.get("grossWeight"));
                BigDecimal grossWeightSum = getCSVBigDecimalFieldValue(values, importColumns.get("grossWeight"));
                grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;
                String composition = getCSVFieldValue(values, importColumns.get("composition"));
                String originalComposition = getCSVFieldValue(values, importColumns.get("originalComposition"));

                PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(numberDocument, dateDocument,
                        currencyDocument, idUserInvoiceDetail, barcodeItem, idBatch, dataIndex, idItem, idItemGroup,
                        originalCustomsGroupItem, captionItem, originalCaptionItem, UOMItem, idManufacturer, 
                        nameManufacturer, nameCountry, nameOriginCountry, importCountryBatch, idCustomer, 
                        idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT, invoiceSum,
                        manufacturingPrice, contractPrice, numberCompliance, dateCompliance, declaration, expiryDate,
                        manufactureDate, pharmacyPriceGroupItem, seriesPharmacy, idArticle, captionArticle, 
                        originalCaptionArticle, idColor, nameColor, idCollection, nameCollection, idSize, nameSize, 
                        nameOriginalSize, idSeasonYear, idSeason, nameSeason, idBrand, nameBrand, idBox, nameBox, 
                        idTheme, nameTheme, netWeight, netWeightSum, grossWeight, grossWeightSum, composition,
                        originalComposition);

                String primaryKeyColumnValue = getCSVFieldValue(values, importColumns.get(primaryKeyColumn));
                String secondaryKeyColumnValue = getCSVFieldValue(values, importColumns.get(secondaryKeyColumn));
                if (primaryKeyColumn != null && !primaryKeyColumnValue.isEmpty())
                    primaryList.add(purchaseInvoiceDetail);
                else if (secondaryKeyColumn != null && !secondaryKeyColumnValue.isEmpty())
                    secondaryList.add(purchaseInvoiceDetail);

            }
        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromXLSX(DataSession session, byte[] importFile, Map<String, String[]> importColumns,
                                                                         String primaryKeyColumn, String secondaryKeyColumn, Integer startRow, Integer userInvoiceObject)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<PurchaseInvoiceDetail>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<PurchaseInvoiceDetail>();

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {

            String numberDocument = getXLSXFieldValue(sheet, i, importColumns.get("numberDocument"));
            Date dateDocument = getXLSXDateFieldValue(sheet, i, importColumns.get("dateDocument"));
            String currencyDocument = getXLSXFieldValue(sheet, i, importColumns.get("currencyDocument"));
            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getXLSXFieldValue(sheet, i, importColumns.get("barcodeItem")));
            String idBatch = getXLSXFieldValue(sheet, i, importColumns.get("idBatch"));
            Integer dataIndex = Integer.parseInt(getXLSXFieldValue(sheet, i, importColumns.get("idItem"), String.valueOf(primaryList.size() + secondaryList.size() + 1)));
            String idItem = getXLSXFieldValue(sheet, i, importColumns.get("idItem"));
            String idItemGroup = getXLSXFieldValue(sheet, i, importColumns.get("idItemGroup"));
            String originalCustomsGroupItem = getXLSXFieldValue(sheet, i, importColumns.get("originalCustomsGroupItem"));
            String captionItem = getXLSXFieldValue(sheet, i, importColumns.get("captionItem"));
            String originalCaptionItem = getXLSXFieldValue(sheet, i, importColumns.get("originalCaptionItem"));
            String UOMItem = getXLSXFieldValue(sheet, i, importColumns.get("UOMItem"));
            String idManufacturer = getXLSXFieldValue(sheet, i, importColumns.get("idManufacturer"));
            String nameManufacturer = getXLSXFieldValue(sheet, i, importColumns.get("nameManufacturer"));
            String nameCountry = getXLSXFieldValue(sheet, i, importColumns.get("nameCountry"));
            nameCountry = nameCountry == null ? null : nameCountry.replace("*", "").trim().toUpperCase();
            String nameOriginCountry = getXLSXFieldValue(sheet, i, importColumns.get("nameOriginCountry"));
            nameOriginCountry = nameOriginCountry == null ? null : nameOriginCountry.replace("*", "").trim().toUpperCase();
            String importCountryBatch = getXLSXFieldValue(sheet, i, importColumns.get("importCountryBatch"));
            String idCustomerStock = getXLSXFieldValue(sheet, i, importColumns.get("idCustomerStock"));
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("quantity"));
            BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("price"));
            BigDecimal sum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("sum"));
            BigDecimal valueVAT = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("valueVAT"));
            BigDecimal sumVAT = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("manufacturingPrice"));
            String contractPrice = getXLSXFieldValue(sheet, i, importColumns.get("contractPrice"));
            String numberCompliance = getXLSXFieldValue(sheet, i, importColumns.get("numberCompliance"));
            Date dateCompliance = getXLSXDateFieldValue(sheet, i, importColumns.get("dateCompliance"));
            String declaration = getXLSXFieldValue(sheet, i, importColumns.get("declaration"));
            Date expiryDate = getXLSXDateFieldValue(sheet, i, importColumns.get("expiryDate"));
            Date manufactureDate = getXLSXDateFieldValue(sheet, i, importColumns.get("manufactureDate"));
            String pharmacyPriceGroupItem = getXLSXFieldValue(sheet, i, importColumns.get("pharmacyPriceGroupItem"));
            String seriesPharmacy = getXLSXFieldValue(sheet, i, importColumns.get("seriesPharmacy"));
            String idArticle = getXLSXFieldValue(sheet, i, importColumns.get("idArticle"));
            String captionArticle = getXLSXFieldValue(sheet, i, importColumns.get("captionArticle"));
            String originalCaptionArticle = getXLSXFieldValue(sheet, i, importColumns.get("originalCaptionArticle"));
            String idColor = getXLSXFieldValue(sheet, i, importColumns.get("idColor"));
            String nameColor = getXLSXFieldValue(sheet, i, importColumns.get("nameColor"));
            String idCollection = getXLSXFieldValue(sheet, i, importColumns.get("idCollection"));
            String nameCollection = getXLSXFieldValue(sheet, i, importColumns.get("nameCollection"));
            String idSize = getXLSXFieldValue(sheet, i, importColumns.get("idSize"));
            String nameSize = getXLSXFieldValue(sheet, i, importColumns.get("nameSize"));
            String nameOriginalSize = getXLSXFieldValue(sheet, i, importColumns.get("nameOriginalSize"));
            String idSeasonYear = getXLSXFieldValue(sheet, i, importColumns.get("idSeasonYear"));
            String idSeason = getXLSXFieldValue(sheet, i, importColumns.get("idSeason"));
            String nameSeason = getXLSXFieldValue(sheet, i, importColumns.get("nameSeason"));
            String idBrand = getXLSXFieldValue(sheet, i, importColumns.get("idBrand"));
            String nameBrand = getXLSXFieldValue(sheet, i, importColumns.get("nameBrand"));
            String idBox = getXLSXFieldValue(sheet, i, importColumns.get("idBox"));
            String nameBox = getXLSXFieldValue(sheet, i, importColumns.get("nameBox"));
            String idTheme = getXLSXFieldValue(sheet, i, importColumns.get("idTheme"));
            String nameTheme = getXLSXFieldValue(sheet, i, importColumns.get("nameTheme"));
            BigDecimal netWeight = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("netWeight"));
            BigDecimal netWeightSum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("netWeightSum"));
            netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
            BigDecimal grossWeight = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("grossWeight"));
            BigDecimal grossWeightSum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("grossWeightSum"));
            grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;
            String composition = getXLSXFieldValue(sheet, i, importColumns.get("composition"));
            String originalComposition = getXLSXFieldValue(sheet, i, importColumns.get("originalComposition"));

            PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(numberDocument, dateDocument,
                    currencyDocument, idUserInvoiceDetail, barcodeItem, idBatch, dataIndex, idItem, idItemGroup,
                    originalCustomsGroupItem, captionItem, originalCaptionItem, UOMItem, idManufacturer,
                    nameManufacturer, nameCountry, nameOriginCountry, importCountryBatch, idCustomer, idCustomerStock,
                    quantity, price, sum, VATifAllowed(valueVAT), sumVAT, invoiceSum, manufacturingPrice, contractPrice, 
                    numberCompliance, dateCompliance, declaration, expiryDate, manufactureDate, pharmacyPriceGroupItem,
                    seriesPharmacy, idArticle, captionArticle, originalCaptionArticle, idColor, nameColor, idCollection,
                    nameCollection, idSize, nameSize, nameOriginalSize, idSeasonYear, idSeason, nameSeason, idBrand,
                    nameBrand, idBox, nameBox, idTheme, nameTheme, netWeight, netWeightSum, grossWeight, grossWeightSum, 
                    composition, originalComposition);

            String primaryKeyColumnValue = getXLSXFieldValue(sheet, i, importColumns.get(primaryKeyColumn));
            String secondaryKeyColumnValue = getXLSXFieldValue(sheet, i, importColumns.get(secondaryKeyColumn));
            if (primaryKeyColumnValue != null && !primaryKeyColumnValue.isEmpty())
                primaryList.add(purchaseInvoiceDetail);
            else if (secondaryKeyColumnValue != null && !secondaryKeyColumnValue.isEmpty())
                primaryList.add(purchaseInvoiceDetail);
        }

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromDBF(DataSession session, byte[] importFile, Map<String, String[]> importColumns,
                                                                        String primaryKeyColumn, String secondaryKeyColumn, Integer startRow, Integer userInvoiceObject)
            throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<PurchaseInvoiceDetail>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<PurchaseInvoiceDetail>();

        File tempFile = File.createTempFile("dutiesTNVED", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);


        DBF file = new DBF(tempFile.getPath());
        String charset = getDBFCharset(tempFile);

        int totalRecordCount = file.getRecordCount();

        for (int i = 0; i < startRow - 1; i++) {
            file.read();
        }

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String numberDocument = getDBFFieldValue(file, importColumns.get("numberDocument"), charset);
            Date dateDocument = getDBFDateFieldValue(file, importColumns.get("dateDocument"), charset);
            String currencyDocument = getDBFFieldValue(file, importColumns.get("currencyDocument"), charset);
            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getDBFFieldValue(file, importColumns.get("barcodeItem"), charset));
            String idBatch = getDBFFieldValue(file, importColumns.get("idBatch"), charset);
            Integer dataIndex = getDBFBigDecimalFieldValue(file, importColumns.get("dataIndex"), charset, String.valueOf(primaryList.size() + secondaryList.size() + 1)).intValue();
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"), charset);
            String idItemGroup = getDBFFieldValue(file, importColumns.get("idItemGroup"), charset);
            String originalCustomsGroupItem = getDBFFieldValue(file, importColumns.get("originalCustomsGroupItem"), charset);
            String captionItem = getDBFFieldValue(file, importColumns.get("captionItem"), charset);
            String originalCaptionItem = getDBFFieldValue(file, importColumns.get("originalCaptionItem"), charset);
            String UOMItem = getDBFFieldValue(file, importColumns.get("UOMItem"), charset);
            String idManufacturer = getDBFFieldValue(file, importColumns.get("idManufacturer"), charset);
            String nameManufacturer = getDBFFieldValue(file, importColumns.get("nameManufacturer"), charset);
            String nameCountry = getDBFFieldValue(file, importColumns.get("nameCountry"), charset);
            nameCountry = nameCountry == null ? null : nameCountry.replace("*", "").trim().toUpperCase();
            String nameOriginCountry = getDBFFieldValue(file, importColumns.get("nameOriginCountry"), charset);
            nameOriginCountry = nameOriginCountry == null ? null : nameOriginCountry.replace("*", "").trim().toUpperCase();
            String importCountryBatch = getDBFFieldValue(file, importColumns.get("importCountryBatch"), charset);
            String idCustomerStock = getDBFFieldValue(file, importColumns.get("idCustomerStock"), charset);
            ObjectValue customerStockObject = idCustomerStock == null ? null : LM.findLCPByCompoundName("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : LM.findLCPByCompoundName("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : LM.findLCPByCompoundName("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getDBFBigDecimalFieldValue(file, importColumns.get("quantity"));
            BigDecimal price = getDBFBigDecimalFieldValue(file, importColumns.get("price"));
            BigDecimal sum = getDBFBigDecimalFieldValue(file, importColumns.get("sum"));
            BigDecimal valueVAT = getDBFBigDecimalFieldValue(file, importColumns.get("valueVAT"));
            BigDecimal sumVAT = getDBFBigDecimalFieldValue(file, importColumns.get("sumVAT"));
            BigDecimal invoiceSum = getDBFBigDecimalFieldValue(file, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getDBFBigDecimalFieldValue(file, importColumns.get("manufacturingPrice"));
            String numberCompliance = getDBFFieldValue(file, importColumns.get("numberCompliance"), charset);
            String contractPrice = getDBFFieldValue(file, importColumns.get("contractPrice"), charset);
            Date dateCompliance = getDBFDateFieldValue(file, importColumns.get("dateCompliance"), charset);
            String declaration = getDBFFieldValue(file, importColumns.get("declaration"), charset);
            Date expiryDate = getDBFDateFieldValue(file, importColumns.get("expiryDate"), charset);
            Date manufactureDate = getDBFDateFieldValue(file, importColumns.get("manufactureDate"), charset);
            String pharmacyPriceGroup = getDBFFieldValue(file, importColumns.get("pharmacyPriceGroupItem"), charset);
            String seriesPharmacy = getDBFFieldValue(file, importColumns.get("seriesPharmacy"), charset);
            String idArticle = getDBFFieldValue(file, importColumns.get("idArticle"), charset);
            String captionArticle = getDBFFieldValue(file, importColumns.get("captionArticle"), charset);
            String originalCaptionArticle = getDBFFieldValue(file, importColumns.get("originalCaptionArticle"), charset);
            String idColor = getDBFFieldValue(file, importColumns.get("idColor"), charset);
            String nameColor = getDBFFieldValue(file, importColumns.get("nameColor"), charset);
            String idCollection = getDBFFieldValue(file, importColumns.get("idCollection"), charset);
            String nameCollection = getDBFFieldValue(file, importColumns.get("nameCollection"), charset);
            String idSize = getDBFFieldValue(file, importColumns.get("idSize"), charset);
            String nameSize = getDBFFieldValue(file, importColumns.get("nameSize"), charset);
            String nameOriginalSize = getDBFFieldValue(file, importColumns.get("nameOriginalSize"), charset);
            String idSeasonYear = getDBFFieldValue(file, importColumns.get("idSeasonYear"), charset);
            String idSeason = getDBFFieldValue(file, importColumns.get("idSeason"), charset);
            String nameSeason = getDBFFieldValue(file, importColumns.get("nameSeason"), charset);
            String idBrand = getDBFFieldValue(file, importColumns.get("idBrand"), charset);
            String nameBrand = getDBFFieldValue(file, importColumns.get("nameBrand"), charset);
            String idBox = getDBFFieldValue(file, importColumns.get("idBox"), charset);
            String nameBox = getDBFFieldValue(file, importColumns.get("nameBox"), charset);
            String idTheme = getDBFFieldValue(file, importColumns.get("idTheme"), charset);
            String nameTheme = getDBFFieldValue(file, importColumns.get("nameTheme"), charset);
            BigDecimal netWeight = getDBFBigDecimalFieldValue(file, importColumns.get("netWeight"));
            BigDecimal netWeightSum = getDBFBigDecimalFieldValue(file, importColumns.get("netWeightSum"));
            netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
            BigDecimal grossWeight = getDBFBigDecimalFieldValue(file, importColumns.get("grossWeight"));
            BigDecimal grossWeightSum = getDBFBigDecimalFieldValue(file, importColumns.get("grossWeightSum"));
            grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;
            String composition = getDBFFieldValue(file, importColumns.get("composition"), charset);
            String originalComposition = getDBFFieldValue(file, importColumns.get("originalComposition"), charset);

            PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(numberDocument, dateDocument, currencyDocument,
                    idUserInvoiceDetail, barcodeItem, idBatch, dataIndex, idItem, idItemGroup, originalCustomsGroupItem,
                    captionItem, originalCaptionItem, UOMItem, idManufacturer, nameManufacturer, nameCountry, nameOriginCountry,
                    importCountryBatch, idCustomer, idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT),
                    sumVAT, invoiceSum, manufacturingPrice, contractPrice, numberCompliance, dateCompliance, declaration,
                    expiryDate, manufactureDate, pharmacyPriceGroup, seriesPharmacy, idArticle, captionArticle, 
                    originalCaptionArticle, idColor, nameColor, idCollection, nameCollection, idSize, nameSize, 
                    nameOriginalSize, idSeasonYear, idSeason, nameSeason, idBrand, nameBrand, idBox, nameBox, idTheme,
                    nameTheme, netWeight, netWeightSum, grossWeight, grossWeightSum, composition, originalComposition);

            String primaryKeyColumnValue = getDBFFieldValue(file, importColumns.get(primaryKeyColumn));
            String secondaryKeyColumnValue = getDBFFieldValue(file, importColumns.get(secondaryKeyColumn));
            if (primaryKeyColumnValue != null && !primaryKeyColumnValue.isEmpty())
                primaryList.add(purchaseInvoiceDetail);
            else if (secondaryKeyColumnValue != null && !secondaryKeyColumnValue.isEmpty())
                secondaryList.add(purchaseInvoiceDetail);
        }

        file.close();

        return Arrays.asList(primaryList, secondaryList);
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

