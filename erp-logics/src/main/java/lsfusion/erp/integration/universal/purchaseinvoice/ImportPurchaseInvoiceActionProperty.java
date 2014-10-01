package lsfusion.erp.integration.universal.purchaseinvoice;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import lsfusion.base.BaseUtils;
import lsfusion.base.IOUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.universal.*;
import lsfusion.erp.stock.BarcodeUtils;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.*;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ImportPurchaseInvoiceActionProperty extends ImportDefaultPurchaseInvoiceActionProperty {
    private final ClassPropertyInterface userInvoiceInterface;

    String defaultCountry = "БЕЛАРУСЬ";

    public ImportPurchaseInvoiceActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClass("Purchase.UserInvoice"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        userInvoiceInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataSession session = context.getSession();

            DataObject userInvoiceObject = context.getDataKeyValue(userInvoiceInterface);

            ObjectValue importTypeObject = findProperty("importTypeUserInvoice").readClasses(session, userInvoiceObject);

            if (!(importTypeObject instanceof NullValue)) {

                ObjectValue operationObject = findProperty("autoImportOperationImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierObject = findProperty("autoImportSupplierImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierStockObject = findProperty("autoImportSupplierStockImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerObject = findProperty("autoImportCustomerImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerStockObject = findProperty("autoImportCustomerStockImportType").readClasses(session, (DataObject) importTypeObject);

                String nameFieldImportType = trim((String) findProperty("staticNameImportTypeDetailImportType").read(session, importTypeObject));
                String[] splittedFieldImportType = nameFieldImportType == null ? null : nameFieldImportType.split("\\.");
                String fieldImportType = splittedFieldImportType == null ? null : splittedFieldImportType[splittedFieldImportType.length - 1];
                
                List<LinkedHashMap<String, ImportColumnDetail>> importColumns = readImportColumns(session, importTypeObject);
                Set<String> purchaseInvoiceSet = getPurchaseInvoiceSet(session, false);

                ImportDocumentSettings importSettings = readImportDocumentSettings(session, importTypeObject);
                String fileExtension = importSettings.getFileExtension();
                
                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            List<List<PurchaseInvoiceDetail>> userInvoiceDetailData = importUserInvoicesFromFile(context, session,
                                    userInvoiceObject, importColumns.get(0), importColumns.get(1), purchaseInvoiceSet, false,
                                    file, fileExtension, importSettings, fieldImportType);

                            if (userInvoiceDetailData != null && userInvoiceDetailData.size() >= 1)
                                importUserInvoices(userInvoiceDetailData.get(0), context, session, importColumns.get(0), 
                                        importColumns.get(1), userInvoiceObject, importSettings.getPrimaryKeyType(),
                                        operationObject, supplierObject, supplierStockObject, customerObject,
                                        customerStockObject);
                            
                            if (userInvoiceDetailData != null && userInvoiceDetailData.size() >= 2)
                                importUserInvoices(userInvoiceDetailData.get(1), context, session, importColumns.get(0),
                                        importColumns.get(1), userInvoiceObject, importSettings.getSecondaryKeyType(),
                                        operationObject, supplierObject, supplierStockObject, customerObject,
                                        customerStockObject);

                            findProperty("originalInvoice").change(new DataObject(BaseUtils.mergeFileAndExtension(file, fileExtension.getBytes()), DynamicFormatFileClass.get(false, true)), context, userInvoiceObject);
                            
                            session.apply(context);

                            findAction("formRefresh").execute(context);
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
        } catch (UniversalImportException e) {
            e.printStackTrace();
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), e.getTitle()));
        }
    }
    
    public int makeImport(ExecutionContext<ClassPropertyInterface> context, DataSession session, DataObject userInvoiceObject,
                              DataObject importTypeObject, byte[] file, String fileExtension, ImportDocumentSettings importSettings,
                              String staticNameImportType, boolean checkInvoiceExistence)
            throws SQLHandledException, ParseException, UniversalImportException, IOException, SQLException, BiffException, 
            xBaseJException, ScriptingErrorLog.SemanticErrorException {
        
        List<LinkedHashMap<String, ImportColumnDetail>> importColumns = readImportColumns(session, importTypeObject);
        Set<String> purchaseInvoiceSet = getPurchaseInvoiceSet(session, checkInvoiceExistence);

        ObjectValue operationObject = findProperty("autoImportOperationImportType").readClasses(context, (DataObject) importTypeObject);
        ObjectValue supplierObject = findProperty("autoImportSupplierImportType").readClasses(context, (DataObject) importTypeObject);
        ObjectValue supplierStockObject = findProperty("autoImportSupplierStockImportType").readClasses(context, (DataObject) importTypeObject);
        ObjectValue customerObject = findProperty("autoImportCustomerImportType").readClasses(context, (DataObject) importTypeObject);
        ObjectValue customerStockObject = findProperty("autoImportCustomerStockImportType").readClasses(context, (DataObject) importTypeObject);

        List<List<PurchaseInvoiceDetail>> userInvoiceDetailData = importUserInvoicesFromFile(context, session,
                userInvoiceObject, importColumns.get(0), importColumns.get(1), purchaseInvoiceSet, checkInvoiceExistence, file, fileExtension,
                importSettings, staticNameImportType);

        int result1 = (userInvoiceDetailData == null || userInvoiceDetailData.size() < 1) ? IMPORT_RESULT_EMPTY :
            importUserInvoices(userInvoiceDetailData.get(0), context, session, importColumns.get(0), importColumns.get(1),
                    userInvoiceObject, importSettings.getPrimaryKeyType(), operationObject, supplierObject,
                    supplierStockObject, customerObject, customerStockObject);

        int result2 = (userInvoiceDetailData == null || userInvoiceDetailData.size() < 2) ? IMPORT_RESULT_EMPTY :
            importUserInvoices(userInvoiceDetailData.get(1), context, session, importColumns.get(0), importColumns.get(1),
                    userInvoiceObject, importSettings.getSecondaryKeyType(), operationObject, supplierObject,
                    supplierStockObject, customerObject, customerStockObject);
        
        return (result1==IMPORT_RESULT_ERROR || result2==IMPORT_RESULT_ERROR) ? IMPORT_RESULT_ERROR : (result1 + result2);
    }

    public int importUserInvoices(List<PurchaseInvoiceDetail> userInvoiceDetailsList, ExecutionContext context, DataSession session,
                                  LinkedHashMap<String, ImportColumnDetail> defaultColumns, LinkedHashMap<String, ImportColumnDetail> customColumns,
                                  DataObject userInvoiceObject, String keyType, ObjectValue operationObject,
                                  ObjectValue supplierObject, ObjectValue supplierStockObject, ObjectValue customerObject,
                                  ObjectValue customerStockObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException, SQLHandledException {


        if (userInvoiceDetailsList != null && !userInvoiceDetailsList.isEmpty()) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(userInvoiceDetailsList.size());

            if (showField(userInvoiceDetailsList, "idUserInvoice")) {
                ImportField idUserInvoiceField = new ImportField(findProperty("idUserInvoice"));
                props.add(new ImportProperty(idUserInvoiceField, findProperty("idUserInvoice").getMapping(userInvoiceObject), getReplaceOnlyNull(defaultColumns, "idUserInvoice")));
                fields.add(idUserInvoiceField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "numberUserInvoice")) {
                    addDataField(props, fields, defaultColumns, findProperty("numberUserInvoice"), "numberUserInvoice", userInvoiceObject);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).numberUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "dateUserInvoice")) {
                addDataField(props, fields, defaultColumns, findProperty("dateUserInvoice"), "dateUserInvoice", userInvoiceObject);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).dateUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "timeUserInvoice")) {
                addDataField(props, fields, defaultColumns, findProperty("timeUserInvoice"), "timeUserInvoice", userInvoiceObject);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).timeUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "currencyUserInvoice")) {
                ImportField shortNameCurrencyField = new ImportField(findProperty("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) findClass("Currency"),
                        findProperty("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, findProperty("currencyUserInvoice").getMapping(userInvoiceObject),
                            object(findClass("Currency")).getMapping(currencyKey), getReplaceOnlyNull(defaultColumns, "currencyUserInvoice")));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).currencyUserInvoice);
            }

            ImportField idUserInvoiceDetailField = new ImportField(findProperty("idUserInvoiceDetail"));
            ImportKey<?> userInvoiceDetailKey = new ImportKey((ConcreteCustomClass) findClass("Purchase.UserInvoiceDetail"),
                    findProperty("userInvoiceDetailId").getMapping(idUserInvoiceDetailField));
            keys.add(userInvoiceDetailKey);
            props.add(new ImportProperty(idUserInvoiceDetailField, findProperty("idUserInvoiceDetail").getMapping(userInvoiceDetailKey), getReplaceOnlyNull(defaultColumns, "idUserInvoiceDetail")));
            props.add(new ImportProperty(userInvoiceObject, findProperty("Purchase.userInvoiceUserInvoiceDetail").getMapping(userInvoiceDetailKey), getReplaceOnlyNull(defaultColumns, "idUserInvoiceDetail")));
            fields.add(idUserInvoiceDetailField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idUserInvoiceDetail);

            if (operationObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) operationObject, findProperty("Purchase.operationUserInvoice").getMapping(userInvoiceObject)));
            }

            if (supplierObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierObject, findProperty("Purchase.supplierUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) supplierObject, findProperty("Purchase.supplierUserInvoice").getMapping(userInvoiceObject)));
            }

            if (showField(userInvoiceDetailsList, "idSupplier")) {

                ImportField idSupplierField = new ImportField(findProperty("idLegalEntity"));
                ImportKey<?> supplierKey = new ImportKey((ConcreteCustomClass) findClass("LegalEntity"),
                        findProperty("legalEntityId").getMapping(idSupplierField));
                keys.add(supplierKey);
                props.add(new ImportProperty(idSupplierField, findProperty("supplierUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        object(findClass("LegalEntity")).getMapping(supplierKey), getReplaceOnlyNull(defaultColumns, "idSupplier")));
                    props.add(new ImportProperty(idSupplierField, findProperty("supplierUserInvoice").getMapping(userInvoiceObject),
                            object(findClass("LegalEntity")).getMapping(supplierKey), getReplaceOnlyNull(defaultColumns, "idSupplier")));
                fields.add(idSupplierField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idSupplier);

            }

            if (showField(userInvoiceDetailsList, "idSupplierStock")) {

                ImportField idSupplierStockField = new ImportField(findProperty("idStock"));
                ImportKey<?> supplierStockKey = new ImportKey((CustomClass) findClass("Stock"),
                        findProperty("stockId").getMapping(idSupplierStockField));
                keys.add(supplierStockKey);
                props.add(new ImportProperty(idSupplierStockField, findProperty("supplierStockUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        object(findClass("Stock")).getMapping(supplierStockKey), getReplaceOnlyNull(defaultColumns, "idSupplierStock")));
                    props.add(new ImportProperty(idSupplierStockField, findProperty("supplierStockUserInvoice").getMapping(userInvoiceObject),
                            object(findClass("Stock")).getMapping(supplierStockKey), getReplaceOnlyNull(defaultColumns, "idSupplierStock")));
                fields.add(idSupplierStockField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idSupplierStock);

            }

            if (supplierStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierStockObject, findProperty("Purchase.supplierStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) supplierStockObject, findProperty("Purchase.supplierStockUserInvoice").getMapping(userInvoiceObject)));
            }

            if (customerObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerObject, findProperty("Purchase.customerUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) customerObject, findProperty("Purchase.customerUserInvoice").getMapping(userInvoiceObject)));
            }

            if (customerStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerStockObject, findProperty("Purchase.customerStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) customerStockObject, findProperty("Purchase.customerStockUserInvoice").getMapping(userInvoiceObject)));
            }

            ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcodeSku"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idBarcodeSkuField, findProperty("idBarcode").getMapping(barcodeKey), getReplaceOnlyNull(defaultColumns, "barcodeItem")));
            props.add(new ImportProperty(idBarcodeSkuField, findProperty("extIdBarcode").getMapping(barcodeKey), getReplaceOnlyNull(defaultColumns, "barcodeItem")));
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idBarcodeSku);

            ImportField idBatchField = new ImportField(findProperty("idBatch"));
            ImportKey<?> batchKey = new ImportKey((CustomClass) findClass("Batch"),
                    findProperty("batchId").getMapping(idBatchField));
            props.add(new ImportProperty(idBatchField, findProperty("idBatch").getMapping(batchKey), getReplaceOnlyNull(defaultColumns, "idBatch")));
            props.add(new ImportProperty(idBatchField, findProperty("idBatchUserInvoiceDetail").getMapping(userInvoiceDetailKey), getReplaceOnlyNull(defaultColumns, "idBatch")));
            fields.add(idBatchField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idBatch);

            new ImportPurchaseInvoicePurchaseShipmentBox(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);

            ImportField dataIndexUserInvoiceDetailField = new ImportField(findProperty("dataIndexUserInvoiceDetail"));
            props.add(new ImportProperty(dataIndexUserInvoiceDetailField, findProperty("dataIndexUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(dataIndexUserInvoiceDetailField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).dataIndex);

            ImportField idItemField = new ImportField(findProperty("idItem"));
            fields.add(idItemField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idItem);

            String replaceField = (keyType == null || keyType.equals("item")) ? "idItem" : keyType.equals("barcode") ? "barcodeItem" : "idBatch";
            LCP iGroupAggr = getItemKeyGroupAggr(keyType);
            ImportField iField = (keyType == null || keyType.equals("item")) ? idItemField : keyType.equals("barcode") ? idBarcodeSkuField : idBatchField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    iGroupAggr.getMapping(iField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, findProperty("idItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "idItem")));
            props.add(new ImportProperty(iField, findProperty("Purchase.skuInvoiceDetail").getMapping(userInvoiceDetailKey),
                    object(findClass("Sku")).getMapping(itemKey), getReplaceOnlyNull(defaultColumns, replaceField)));
            props.add(new ImportProperty(iField, findProperty("skuBarcode").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey), getReplaceOnlyNull(defaultColumns, replaceField)));

            if (showField(userInvoiceDetailsList, "captionItem")) {
                addDataField(props, fields, defaultColumns, findProperty("captionItem"), "captionItem", itemKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).captionItem);
            }

            if (showField(userInvoiceDetailsList, "originalCaptionItem")) {
                addDataField(props, fields, defaultColumns, findProperty("originalCaptionItem"), "originalCaptionItem", itemKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).originalCaptionItem);
            }

            if (showField(userInvoiceDetailsList, "idUOM")) {
                ImportField idUOMField = new ImportField(findProperty("idUOM"));
                ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) findClass("UOM"),
                        findProperty("UOMId").getMapping(idUOMField));
                keys.add(UOMKey);
                props.add(new ImportProperty(idUOMField, findProperty("idUOM").getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                props.add(new ImportProperty(idUOMField, findProperty("nameUOM").getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                props.add(new ImportProperty(idUOMField, findProperty("shortNameUOM").getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                props.add(new ImportProperty(idUOMField, findProperty("UOMItem").getMapping(itemKey),
                        object(findClass("UOM")).getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                fields.add(idUOMField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idUOM);
            }

            if (showField(userInvoiceDetailsList, "idManufacturer")) {
                ImportField idManufacturerField = new ImportField(findProperty("idManufacturer"));
                ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) findClass("Manufacturer"),
                        findProperty("manufacturerId").getMapping(idManufacturerField));
                keys.add(manufacturerKey);
                props.add(new ImportProperty(idManufacturerField, findProperty("idManufacturer").getMapping(manufacturerKey), getReplaceOnlyNull(defaultColumns, "idManufacturer")));
                props.add(new ImportProperty(idManufacturerField, findProperty("manufacturerItem").getMapping(itemKey),
                        object(findClass("Manufacturer")).getMapping(manufacturerKey), getReplaceOnlyNull(defaultColumns, "idManufacturer")));
                fields.add(idManufacturerField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idManufacturer);

                if (showField(userInvoiceDetailsList, "nameManufacturer")) {
                    addDataField(props, fields, defaultColumns, findProperty("nameManufacturer"), "nameManufacturer", manufacturerKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameManufacturer);
                }
            }

            ImportField sidOrigin2CountryField = new ImportField(findProperty("sidOrigin2Country"));
            ImportField nameCountryField = new ImportField(findProperty("nameCountry"));
            ImportField nameOriginCountryField = new ImportField(findProperty("nameOriginCountry"));

            boolean showSidOrigin2Country = showField(userInvoiceDetailsList, "sidOrigin2Country");
            boolean showNameCountry = showField(userInvoiceDetailsList, "nameCountry");
            boolean showNameOriginCountry = showField(userInvoiceDetailsList, "nameOriginCountry");

            ImportField countryField = showSidOrigin2Country ? sidOrigin2CountryField :
                    (showNameCountry ? nameCountryField : (showNameOriginCountry ? nameOriginCountryField : null));
            LCP<?> countryAggr = showSidOrigin2Country ? findProperty("countrySIDOrigin2") :
                    (showNameCountry ? findProperty("countryName") : (showNameOriginCountry ? findProperty("countryNameOrigin") : null));
            String countryReplaceField = showSidOrigin2Country ? "sidOrigin2Country" :
                    (showNameCountry ? "nameCountry" : (showNameOriginCountry ? "nameOriginCountry" : null));
            ImportKey<?> countryKey = countryField == null ? null : 
                    new ImportKey((ConcreteCustomClass) findClass("Country"), countryAggr.getMapping(countryField));

            if (countryKey != null) {
                keys.add(countryKey);

                props.add(new ImportProperty(countryField, findProperty("countryItem").getMapping(itemKey),
                        object(findClass("Country")).getMapping(countryKey), getReplaceOnlyNull(defaultColumns, countryReplaceField)));

                if (showSidOrigin2Country) {
                    props.add(new ImportProperty(sidOrigin2CountryField, findProperty("sidOrigin2Country").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "sidOrigin2Country")));
                    fields.add(sidOrigin2CountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).sidOrigin2Country);
                }
                if (showNameCountry) {
                    props.add(new ImportProperty(nameCountryField, findProperty("nameCountry").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "nameCountry")));
                    fields.add(nameCountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameCountry);
                }
                if (showNameOriginCountry) {
                    props.add(new ImportProperty(nameOriginCountryField, findProperty("nameOriginCountry").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "nameOriginCountry")));
                    fields.add(nameOriginCountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameOriginCountry);
                }
            }

            if (showField(userInvoiceDetailsList, "idCustomer")) {
                ImportField idCustomerField = new ImportField(findProperty("idLegalEntity"));
                ImportKey<?> customerKey = new ImportKey((ConcreteCustomClass) findClass("LegalEntity"),
                        findProperty("legalEntityId").getMapping(idCustomerField));
                keys.add(customerKey);
                    props.add(new ImportProperty(idCustomerField, findProperty("Purchase.customerUserInvoice").getMapping(userInvoiceObject),
                            object(findClass("LegalEntity")).getMapping(customerKey), getReplaceOnlyNull(defaultColumns, "idBCustomer")));
                fields.add(idCustomerField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idCustomer);
            }

            if (showField(userInvoiceDetailsList, "idCustomerStock")) {
                ImportField idCustomerStockField = new ImportField(findProperty("idStock"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) findClass("Stock"),
                        findProperty("stockId").getMapping(idCustomerStockField));
                keys.add(customerStockKey);
                    props.add(new ImportProperty(idCustomerStockField, findProperty("Purchase.customerStockUserInvoice").getMapping(userInvoiceObject),
                            object(findClass("Stock")).getMapping(customerStockKey), getReplaceOnlyNull(defaultColumns, "idCustomerStock")));
                fields.add(idCustomerStockField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idCustomerStock);
            }

            if (showField(userInvoiceDetailsList, "quantity")) {
                addDataField(props, fields, defaultColumns, findProperty("Purchase.quantityUserInvoiceDetail"), "quantity", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).quantity);
            }

            if (showField(userInvoiceDetailsList, "price")) {
                addDataField(props, fields, defaultColumns, findProperty("Purchase.priceUserInvoiceDetail"), "price", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).price);
            }

            if (showField(userInvoiceDetailsList, "sum")) {
                addDataField(props, fields, defaultColumns, findProperty("Purchase.sumUserInvoiceDetail"), "sum", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).sum);
            }

            if (showField(userInvoiceDetailsList, "valueVAT")) {
                ImportField valueVATUserInvoiceDetailField = new ImportField(findProperty("Purchase.valueVATUserInvoiceDetail"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) findClass("Range"),
                        findProperty("valueCurrentVATDefaultValue").getMapping(valueVATUserInvoiceDetailField));
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATUserInvoiceDetailField, findProperty("Purchase.VATUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        object(findClass("Range")).getMapping(VATKey), getReplaceOnlyNull(defaultColumns, "valueVAT")));
                fields.add(valueVATUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).valueVAT);

                ImportField dateField = new ImportField(DateClass.instance);
                props.add(new ImportProperty(dateField, findProperty("dataDateBarcode").getMapping(barcodeKey), true));
                fields.add(dateField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).dateVAT);

                new ImportPurchaseInvoiceTaxItem(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, valueVATUserInvoiceDetailField, itemKey, VATKey);
                
            }

            if (showField(userInvoiceDetailsList, "sumVAT")) {
                addDataField(props, fields, defaultColumns, findProperty("Purchase.VATSumUserInvoiceDetail"), "sumVAT", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).sumVAT);
            }

            if (showField(userInvoiceDetailsList, "invoiceSum")) {
                addDataField(props, fields, defaultColumns, findProperty("Purchase.invoiceSumUserInvoiceDetail"), "invoiceSum", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).invoiceSum);
            }

            new ImportPurchaseInvoicePurchaseDeclaration(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);

            new ImportPurchaseInvoicePurchaseShipment(LM).makeImport(context, fields, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);
            
            new ImportPurchaseInvoicePurchaseManufacturingPrice(LM).makeImport(context, fields, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);               

            new ImportPurchaseInvoiceItemPharmacyBy(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, itemKey);
            
            new ImportPurchaseInvoicePurchaseInvoicePharmacy(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);
            
            if (showField(userInvoiceDetailsList, "rateExchange")) {
                addDataField(props, fields, defaultColumns, findProperty("rateExchangeUserInvoiceDetail"), "rateExchange", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).rateExchange);
            }

            new ImportPurchaseInvoicePurchaseDeclarationDetail(LM).makeImport(context, fields, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);            
            
            if (showField(userInvoiceDetailsList, "isPosted")) {
                    addDataField(props, fields, defaultColumns, findProperty("isPostedUserInvoice"), "isPosted", userInvoiceObject);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).isPosted);
            }

            if (showField(userInvoiceDetailsList, "idItemGroup")) {
                ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
                ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) findClass("ItemGroup"),
                        findProperty("itemGroupId").getMapping(idItemGroupField));
                keys.add(itemGroupKey);
                props.add(new ImportProperty(idItemGroupField, findProperty("idItemGroup").getMapping(itemGroupKey), getReplaceOnlyNull(defaultColumns, "idItemGroup")));
                props.add(new ImportProperty(idItemGroupField, findProperty("nameItemGroup").getMapping(itemGroupKey), true));
                props.add(new ImportProperty(idItemGroupField, findProperty("itemGroupItem").getMapping(itemKey),
                        object(findClass("ItemGroup")).getMapping(itemGroupKey), getReplaceOnlyNull(defaultColumns, "idItemGroup")));
                fields.add(idItemGroupField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idItemGroup);
            }
            
            ScriptingLogicsModule itemArticleLM = context.getBL().getModule("ItemArticle");
            ImportKey<?> articleKey = null;
            if ((itemArticleLM != null)) {

                ImportField idArticleField = new ImportField(itemArticleLM.findProperty("idArticle"));
                articleKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClass("Article"),
                        itemArticleLM.findProperty("articleId").getMapping(idArticleField));
                keys.add(articleKey);
                props.add(new ImportProperty(idArticleField, itemArticleLM.findProperty("idArticle").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "idArticle")));
                props.add(new ImportProperty(idArticleField, itemArticleLM.findProperty("articleItem").getMapping(itemKey),
                        object(itemArticleLM.findClass("Article")).getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "idArticle")));
                fields.add(idArticleField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idArticle);

                new ImportPurchaseInvoiceItemArticle(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, itemKey, articleKey);                               

                new ImportPurchaseInvoiceCustomsGroupArticle(LM).makeImport(context, fields, props, defaultColumns, userInvoiceDetailsList, data, itemKey, articleKey);

                new ImportPurchaseInvoiceItemFashion(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, itemKey, articleKey);
                
            }
            
            new ImportPurchaseInvoicePurchaseCompliance(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);

            for (Map.Entry<String, ImportColumnDetail> entry : customColumns.entrySet()) {
                ImportColumnDetail customColumn = entry.getValue();
                ScriptingLogicsModule customModuleLM = context.getBL().getModule(customColumn.moduleName);
                if (customModuleLM != null) {
                    ImportField customField = new ImportField(customModuleLM.findProperty(customColumn.property));
                    ImportKey<?> customKey = null;
                    if (customColumn.key.equals("item"))
                        customKey = itemKey;
                    else if (customColumn.key.equals("article"))
                        customKey = articleKey;
                    else if (customColumn.key.equals("documentDetail"))
                        customKey = userInvoiceDetailKey;
                    if (customKey != null) {
                        props.add(new ImportProperty(customField, customModuleLM.findProperty(customColumn.property).getMapping(customKey), getReplaceOnlyNull(customColumns, entry.getKey())));
                        fields.add(customField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).customValues.get(entry.getKey()));
                    } else if(customColumn.key.equals("document")) {
                        props.add(new ImportProperty(customField, customModuleLM.findProperty(customColumn.property).getMapping(userInvoiceObject), getReplaceOnlyNull(customColumns, entry.getKey())));
                        fields.add(customField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).customValues.get(entry.getKey()));
                    }
                }
            }

            ImportTable table = new ImportTable(fields, data);

            session.pushVolatileStats("PIA_UI");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.popVolatileStats();
            return IMPORT_RESULT_OK;
        }   else return IMPORT_RESULT_EMPTY;
    }

    protected List<List<PurchaseInvoiceDetail>> importUserInvoicesFromFile(ExecutionContext context, DataSession session, DataObject userInvoiceObject,
                                                                           Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns, 
                                                                           Set<String> purchaseInvoiceSet, boolean checkInvoiceExistence,
                                                                           byte[] file, String fileExtension, ImportDocumentSettings importSettings,
                                                                           String staticNameImportType)
            throws ParseException, UniversalImportException, IOException, SQLException, xBaseJException, ScriptingErrorLog.SemanticErrorException, BiffException, SQLHandledException {

        List<List<PurchaseInvoiceDetail>> userInvoiceDetailsList;

        if (fileExtension.equals("DBF"))
            userInvoiceDetailsList = importUserInvoicesFromDBF(context, session, file, defaultColumns, customColumns, 
                    purchaseInvoiceSet, checkInvoiceExistence, importSettings, userInvoiceObject, staticNameImportType);
        else if (fileExtension.equals("XLS"))
            userInvoiceDetailsList = importUserInvoicesFromXLS(context, session, file, defaultColumns, customColumns,
                    purchaseInvoiceSet, checkInvoiceExistence, importSettings, userInvoiceObject, staticNameImportType);
        else if (fileExtension.equals("XLSX"))
            userInvoiceDetailsList = importUserInvoicesFromXLSX(context, session, file, defaultColumns, customColumns, 
                    purchaseInvoiceSet, checkInvoiceExistence, importSettings, userInvoiceObject, staticNameImportType);
        else if (fileExtension.equals("CSV") || fileExtension.equals("TXT"))
            userInvoiceDetailsList = importUserInvoicesFromCSV(context, session, file, defaultColumns, customColumns, 
                    purchaseInvoiceSet, checkInvoiceExistence, importSettings, userInvoiceObject, staticNameImportType);
        else
            userInvoiceDetailsList = null;

        return userInvoiceDetailsList;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromXLS(ExecutionContext context, DataSession session, byte[] importFile, 
                                                                        Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                        Set<String> purchaseInvoiceSet, boolean checkInvoiceExistence,
                                                                        ImportDocumentSettings importSettings, DataObject userInvoiceObject,
                                                                        String staticNameImportType)
            throws IOException, BiffException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<PurchaseInvoiceDetail>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<PurchaseInvoiceDetail>();

        String primaryKeyColumn = getItemKeyColumn(importSettings.getPrimaryKeyType());
        String secondaryKeyColumn = getItemKeyColumn(importSettings.getSecondaryKeyType());

        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding("cp1251");
        Workbook wb = null;
        try {
            wb = Workbook.getWorkbook(new ByteArrayInputStream(importFile), ws);
        } catch (Exception e) {
            context.requestUserInteraction(new MessageClientAction("Файл неизвестного либо устаревшего формата", "Ошибка при открытии файла"));
        }
        if (wb != null) {
            Sheet sheet = wb.getSheet(0);

            Date currentDateDocument = getCurrentDateDocument(session, userInvoiceObject);
            currentTimestamp = getCurrentTimestamp();

            for (int i = importSettings.getStartRow() - 1; i < sheet.getRows(); i++) {
                String numberDocument = getXLSFieldValue(sheet, i, defaultColumns.get("numberDocument"));
                String idDocument = getXLSFieldValue(sheet, i, defaultColumns.get("idDocument"), numberDocument);
                Date dateDocument = getXLSDateFieldValue(sheet, i, defaultColumns.get("dateDocument"));
                Time timeDocument = getXLSTimeFieldValue(sheet, i, defaultColumns.get("timeDocument"));
                String idSupplier = getXLSFieldValue(sheet, i, defaultColumns.get("idSupplier"));
                String idSupplierStock = getXLSFieldValue(sheet, i, defaultColumns.get("idSupplierStock"));
                String currencyDocument = getXLSFieldValue(sheet, i, defaultColumns.get("currencyDocument"));
                String idUserInvoiceDetail = makeIdUserInvoiceDetail(idDocument, userInvoiceObject, i);
                String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSFieldValue(sheet, i, defaultColumns.get("barcodeItem")), 7);
                String originalCustomsGroupItem = getXLSFieldValue(sheet, i, defaultColumns.get("originalCustomsGroupItem"));
                String idBatch = getXLSFieldValue(sheet, i, defaultColumns.get("idBatch"));
                BigDecimal dataIndexValue = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("dataIndex"));
                Integer dataIndex = dataIndexValue == null ? (primaryList.size() + secondaryList.size() + 1) : dataIndexValue.intValue();
                String idItem = getXLSFieldValue(sheet, i, defaultColumns.get("idItem"));
                String idItemGroup = getXLSFieldValue(sheet, i, defaultColumns.get("idItemGroup"));
                String captionItem = getXLSFieldValue(sheet, i, defaultColumns.get("captionItem"));
                String originalCaptionItem = getXLSFieldValue(sheet, i, defaultColumns.get("originalCaptionItem"));
                String UOMItem = getXLSFieldValue(sheet, i, defaultColumns.get("UOMItem"));
                String idManufacturer = getXLSFieldValue(sheet, i, defaultColumns.get("idManufacturer"));
                String nameManufacturer = getXLSFieldValue(sheet, i, defaultColumns.get("nameManufacturer"));
                String sidOrigin2Country = getXLSFieldValue(sheet, i, defaultColumns.get("sidOrigin2Country"));
                String nameCountry = modifyNameCountry(getXLSFieldValue(sheet, i, defaultColumns.get("nameCountry")));
                String nameOriginCountry = modifyNameCountry(getXLSFieldValue(sheet, i, defaultColumns.get("nameOriginCountry")));
                String importCountryBatch = getXLSFieldValue(sheet, i, defaultColumns.get("importCountryBatch"));
                String idCustomerStock = getXLSFieldValue(sheet, i, defaultColumns.get("idCustomerStock"));
                idCustomerStock = importSettings.getStockMapping().containsKey(idCustomerStock) ? importSettings.getStockMapping().get(idCustomerStock) : idCustomerStock;
                ObjectValue customerStockObject = idCustomerStock == null ? null : findProperty("stockId").readClasses(session, new DataObject(idCustomerStock));
                ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : findProperty("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
                String idCustomer = (String) (customerObject == null ? null : findProperty("idLegalEntity").read(session, customerObject));
                BigDecimal quantity = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("quantity"));
                BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("price"));
                if (price != null && price.compareTo(new BigDecimal("100000000000")) > 0)
                    price = null;
                BigDecimal sum = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("sum"));
                BigDecimal valueVAT = parseVAT(getXLSFieldValue(sheet, i, defaultColumns.get("valueVAT")));
                BigDecimal sumVAT = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("sumVAT"));
                Date dateVAT = dateDocument == null ? currentDateDocument : dateDocument;
                BigDecimal invoiceSum = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("invoiceSum"));
                BigDecimal manufacturingPrice = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("manufacturingPrice"));
                String contractPrice = getXLSFieldValue(sheet, i, defaultColumns.get("contractPrice"));
                BigDecimal shipmentPrice = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("shipmentPrice"));
                BigDecimal shipmentSum = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("shipmentSum"));
                BigDecimal rateExchange = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("rateExchange"));
                String numberCompliance = getXLSFieldValue(sheet, i, defaultColumns.get("numberCompliance"));
                Date dateCompliance = getXLSDateFieldValue(sheet, i, defaultColumns.get("dateCompliance"));
                String declaration = getXLSFieldValue(sheet, i, defaultColumns.get("declaration"));
                Date expiryDate = getXLSDateFieldValue(sheet, i, defaultColumns.get("expiryDate"), true);
                Date manufactureDate = getXLSDateFieldValue(sheet, i, defaultColumns.get("manufactureDate"));
                String pharmacyPriceGroupItem = getXLSFieldValue(sheet, i, defaultColumns.get("pharmacyPriceGroupItem"));
                String seriesPharmacy = getXLSFieldValue(sheet, i, defaultColumns.get("seriesPharmacy"));
                String idArticle = getXLSFieldValue(sheet, i, defaultColumns.get("idArticle"));
                String captionArticle = getXLSFieldValue(sheet, i, defaultColumns.get("captionArticle"));
                String originalCaptionArticle = getXLSFieldValue(sheet, i, defaultColumns.get("originalCaptionArticle"));
                String idColor = getXLSFieldValue(sheet, i, defaultColumns.get("idColor"));
                String nameColor = getXLSFieldValue(sheet, i, defaultColumns.get("nameColor"));
                String idCollection = getXLSFieldValue(sheet, i, defaultColumns.get("idCollection"));
                String nameCollection = getXLSFieldValue(sheet, i, defaultColumns.get("nameCollection"));
                String idSize = getXLSFieldValue(sheet, i, defaultColumns.get("idSize"));
                String nameSize = getXLSFieldValue(sheet, i, defaultColumns.get("nameSize"));
                String nameOriginalSize = getXLSFieldValue(sheet, i, defaultColumns.get("nameOriginalSize"));
                String idSeasonYear = getXLSFieldValue(sheet, i, defaultColumns.get("idSeasonYear"));
                String idSeason = getXLSFieldValue(sheet, i, defaultColumns.get("idSeason"));
                String nameSeason = getXLSFieldValue(sheet, i, defaultColumns.get("nameSeason"));
                String idBrand = getXLSFieldValue(sheet, i, defaultColumns.get("idBrand"));
                String nameBrand = getXLSFieldValue(sheet, i, defaultColumns.get("nameBrand"));
                String idBox = getXLSFieldValue(sheet, i, defaultColumns.get("idBox"));
                String nameBox = getXLSFieldValue(sheet, i, defaultColumns.get("nameBox"));
                String idTheme = getXLSFieldValue(sheet, i, defaultColumns.get("idTheme"));
                String nameTheme = getXLSFieldValue(sheet, i, defaultColumns.get("nameTheme"));
                BigDecimal netWeight = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("netWeight"));
                BigDecimal netWeightSum = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("netWeightSum"));
                netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
                BigDecimal grossWeight = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("grossWeight"));
                BigDecimal grossWeightSum = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("grossWeightSum"));
                grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;
                String composition = getXLSFieldValue(sheet, i, defaultColumns.get("composition"));
                String originalComposition = getXLSFieldValue(sheet, i, defaultColumns.get("originalComposition"));

                LinkedHashMap<String, String> customValues = new LinkedHashMap<String, String>();
                for(Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                    customValues.put(column.getKey(), getXLSFieldValue(sheet, i, column.getValue()));
                }

                if(checkInvoice(purchaseInvoiceSet, idDocument, checkInvoiceExistence)) {
                    PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(customValues, importSettings.isPosted(),
                            idDocument, numberDocument, dateDocument, timeDocument, idSupplier, idSupplierStock, 
                            currencyDocument, idUserInvoiceDetail, barcodeItem, idBatch, dataIndex, idItem, idItemGroup,
                            originalCustomsGroupItem, captionItem, originalCaptionItem, UOMItem, idManufacturer,
                            nameManufacturer, sidOrigin2Country, nameCountry, nameOriginCountry, importCountryBatch,
                            idCustomer, idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT, dateVAT,
                            defaultCountry, invoiceSum, manufacturingPrice, contractPrice, shipmentPrice, shipmentSum,
                            rateExchange, numberCompliance, dateCompliance, declaration, expiryDate, manufactureDate,
                            pharmacyPriceGroupItem, seriesPharmacy, idArticle, captionArticle, originalCaptionArticle,
                            idColor, nameColor, idCollection, nameCollection, idSize, nameSize, nameOriginalSize,
                            idSeasonYear, idSeason, nameSeason, idBrand, nameBrand, idBox, nameBox, idTheme, nameTheme,
                            netWeight, netWeightSum, grossWeight, grossWeightSum, composition, originalComposition);

                    String primaryKeyColumnValue = getXLSFieldValue(sheet, i, defaultColumns.get(primaryKeyColumn));
                    String secondaryKeyColumnValue = getXLSFieldValue(sheet, i, defaultColumns.get(secondaryKeyColumn));
                    if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, importSettings.isKeyIsDigit(), session, importSettings.getPrimaryKeyType(), importSettings.isCheckExistence()))
                        primaryList.add(purchaseInvoiceDetail);
                    else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, importSettings.isKeyIsDigit()))
                        primaryList.add(purchaseInvoiceDetail);
                }
            }
            currentTimestamp = null;

            return checkArticles(context, session, importSettings.getPropertyImportType(), staticNameImportType, primaryList, secondaryList) ? Arrays.asList(primaryList, secondaryList) : null;
        } else
            return null;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromCSV(ExecutionContext context, DataSession session, byte[] importFile, 
                                                                        Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                        Set<String> purchaseInvoiceSet, boolean checkInvoiceExistence,
                                                                        ImportDocumentSettings importSettings, DataObject userInvoiceObject, String staticNameImportType)
            throws IOException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<PurchaseInvoiceDetail>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<PurchaseInvoiceDetail>();

        String primaryKeyColumn = getItemKeyColumn(importSettings.getPrimaryKeyType());
        String secondaryKeyColumn = getItemKeyColumn(importSettings.getSecondaryKeyType());
        
        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile), "cp1251"));
        String line;


        Date currentDateDocument = getCurrentDateDocument(session, userInvoiceObject);
        currentTimestamp = getCurrentTimestamp();

        List<String[]> valuesList = new ArrayList<String[]>();
        while ((line = br.readLine()) != null) {
                valuesList.add(line.split(importSettings.getSeparator()));
        }

        for (int count = importSettings.getStartRow(); count <= valuesList.size(); count++) {
            
            String numberDocument = getCSVFieldValue(valuesList, defaultColumns.get("numberDocument"), count);
            String idDocument = getCSVFieldValue(valuesList, defaultColumns.get("idDocument"), count, numberDocument);
            Date dateDocument = getCSVDateFieldValue(valuesList, defaultColumns.get("dateDocument"), count);
            Time timeDocument = getCSVTimeFieldValue(valuesList, defaultColumns.get("timeDocument"), count);
            String idSupplier = getCSVFieldValue(valuesList, defaultColumns.get("idSupplier"), count);
            String idSupplierStock = getCSVFieldValue(valuesList, defaultColumns.get("idSupplierStock"), count);
            String currencyDocument = getCSVFieldValue(valuesList, defaultColumns.get("currencyDocument"), count);
            String idUserInvoiceDetail = makeIdUserInvoiceDetail(idDocument, userInvoiceObject, count);
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getCSVFieldValue(valuesList, defaultColumns.get("barcodeItem"), count), 7);
            String idBatch = getCSVFieldValue(valuesList, defaultColumns.get("idBatch"), count);
            BigDecimal dataIndexValue = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("dataIndex"), count);
            Integer dataIndex = dataIndexValue == null ? (primaryList.size() + secondaryList.size() + 1) : dataIndexValue.intValue();
            String idItem = getCSVFieldValue(valuesList, defaultColumns.get("idItem"), count);
            String idItemGroup = getCSVFieldValue(valuesList, defaultColumns.get("idItemGroup"), count);
            String originalCustomsGroupItem = getCSVFieldValue(valuesList, defaultColumns.get("originalCustomsGroupItem"), count);
            String captionItem = getCSVFieldValue(valuesList, defaultColumns.get("captionItem"), count);
            String originalCaptionItem = getCSVFieldValue(valuesList, defaultColumns.get("originalCaptionItem"), count);
            String UOMItem = getCSVFieldValue(valuesList, defaultColumns.get("UOMItem"), count);
            String idManufacturer = getCSVFieldValue(valuesList, defaultColumns.get("idManufacturer"), count);
            String nameManufacturer = getCSVFieldValue(valuesList, defaultColumns.get("nameManufacturer"), count);
            String sidOrigin2Country = getCSVFieldValue(valuesList, defaultColumns.get("sidOrigin2Country"), count);
            String nameCountry = modifyNameCountry(getCSVFieldValue(valuesList, defaultColumns.get("nameCountry"), count));
            String nameOriginCountry = modifyNameCountry(getCSVFieldValue(valuesList, defaultColumns.get("nameOriginCountry"), count));
            String importCountryBatch = getCSVFieldValue(valuesList, defaultColumns.get("importCountryBatch"), count);
            String idCustomerStock = getCSVFieldValue(valuesList, defaultColumns.get("idCustomerStock"), count);
            idCustomerStock = importSettings.getStockMapping().containsKey(idCustomerStock) ? importSettings.getStockMapping().get(idCustomerStock) : idCustomerStock;
            ObjectValue customerStockObject = idCustomerStock == null ? null : findProperty("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : findProperty("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : findProperty("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("quantity"), count);
            BigDecimal price = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("price"), count);
            if (price != null && price.compareTo(new BigDecimal("100000000000")) > 0)
                price = null;
            BigDecimal sum = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("sum"), count);
            BigDecimal valueVAT = parseVAT(getCSVFieldValue(valuesList, defaultColumns.get("valueVAT"), count));
            BigDecimal sumVAT = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("sumVAT"), count);
            Date dateVAT = dateDocument == null ? currentDateDocument : dateDocument;
            BigDecimal invoiceSum = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("invoiceSum"), count);
            BigDecimal manufacturingPrice = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("manufacturingPrice"), count);
            String contractPrice = getCSVFieldValue(valuesList, defaultColumns.get("contractPrice"), count);
            BigDecimal shipmentPrice = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("shipmentPrice"), count);
            BigDecimal shipmentSum = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("shipmentSum"), count);
            BigDecimal rateExchange = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("rateExchange"), count);
            String numberCompliance = getCSVFieldValue(valuesList, defaultColumns.get("numberCompliance"), count);
            Date dateCompliance = getCSVDateFieldValue(valuesList, defaultColumns.get("dateCompliance"), count);
            String declaration = getCSVFieldValue(valuesList, defaultColumns.get("declaration"), count);
            Date expiryDate = getCSVDateFieldValue(valuesList, defaultColumns.get("expiryDate"), count, true);
            Date manufactureDate = getCSVDateFieldValue(valuesList, defaultColumns.get("manufactureDate"), count);
            String pharmacyPriceGroupItem = getCSVFieldValue(valuesList, defaultColumns.get("pharmacyPriceGroupItem"), count);
            String seriesPharmacy = getCSVFieldValue(valuesList, defaultColumns.get("seriesPharmacy"), count);
            String idArticle = getCSVFieldValue(valuesList, defaultColumns.get("idArticle"), count);
            String captionArticle = getCSVFieldValue(valuesList, defaultColumns.get("captionArticle"), count);
            String originalCaptionArticle = getCSVFieldValue(valuesList, defaultColumns.get("originalCaptionArticle"), count);
            String idColor = getCSVFieldValue(valuesList, defaultColumns.get("idColor"), count);
            String nameColor = getCSVFieldValue(valuesList, defaultColumns.get("nameColor"), count);
            String idCollection = getCSVFieldValue(valuesList, defaultColumns.get("idCollection"), count);
            String nameCollection = getCSVFieldValue(valuesList, defaultColumns.get("nameCollection"), count);
            String idSize = getCSVFieldValue(valuesList, defaultColumns.get("idSize"), count);
            String nameSize = getCSVFieldValue(valuesList, defaultColumns.get("nameSize"), count);
            String nameOriginalSize = getCSVFieldValue(valuesList, defaultColumns.get("nameOriginalSize"), count);
            String idSeasonYear = getCSVFieldValue(valuesList, defaultColumns.get("idSeasonYear"), count);
            String idSeason = getCSVFieldValue(valuesList, defaultColumns.get("idSeason"), count);
            String nameSeason = getCSVFieldValue(valuesList, defaultColumns.get("nameSeason"), count);
            String idBrand = getCSVFieldValue(valuesList, defaultColumns.get("idBrand"), count);
            String nameBrand = getCSVFieldValue(valuesList, defaultColumns.get("nameBrand"), count);
            String idBox = getCSVFieldValue(valuesList, defaultColumns.get("idBox"), count);
            String nameBox = getCSVFieldValue(valuesList, defaultColumns.get("nameBox"), count);
            String idTheme = getCSVFieldValue(valuesList, defaultColumns.get("idTheme"), count);
            String nameTheme = getCSVFieldValue(valuesList, defaultColumns.get("nameTheme"), count);
            BigDecimal netWeight = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("netWeight"), count);
            BigDecimal netWeightSum = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("netWeightSum"), count);
            netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
            BigDecimal grossWeight = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("grossWeight"), count);
            BigDecimal grossWeightSum = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("grossWeight"), count);
            grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;
            String composition = getCSVFieldValue(valuesList, defaultColumns.get("composition"), count);
            String originalComposition = getCSVFieldValue(valuesList, defaultColumns.get("originalComposition"), count);

            LinkedHashMap<String, String> customValues = new LinkedHashMap<String, String>();
            for(Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                customValues.put(column.getKey(), getCSVFieldValue(valuesList, column.getValue(), count));
            }

            if(checkInvoice(purchaseInvoiceSet, idDocument, checkInvoiceExistence)) {
                PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(customValues, importSettings.isPosted(), idDocument,
                        numberDocument, dateDocument, timeDocument, idSupplier, idSupplierStock, currencyDocument, 
                        idUserInvoiceDetail, barcodeItem, idBatch, dataIndex, idItem, idItemGroup, originalCustomsGroupItem,
                        captionItem, originalCaptionItem, UOMItem, idManufacturer, nameManufacturer, sidOrigin2Country, 
                        nameCountry, nameOriginCountry, importCountryBatch, idCustomer, idCustomerStock, quantity, price,
                        sum, VATifAllowed(valueVAT), sumVAT, dateVAT, defaultCountry, invoiceSum, manufacturingPrice,
                        contractPrice, shipmentPrice, shipmentSum, rateExchange, numberCompliance, dateCompliance,
                        declaration, expiryDate, manufactureDate, pharmacyPriceGroupItem, seriesPharmacy, idArticle,
                        captionArticle, originalCaptionArticle, idColor, nameColor, idCollection, nameCollection, idSize,
                        nameSize, nameOriginalSize, idSeasonYear, idSeason, nameSeason, idBrand, nameBrand, idBox, nameBox,
                        idTheme, nameTheme, netWeight, netWeightSum, grossWeight, grossWeightSum, composition,
                        originalComposition);

                String primaryKeyColumnValue = getCSVFieldValue(valuesList, defaultColumns.get(primaryKeyColumn), count);
                String secondaryKeyColumnValue = getCSVFieldValue(valuesList, defaultColumns.get(secondaryKeyColumn), count);
                if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, importSettings.isKeyIsDigit(), session,
                        importSettings.getPrimaryKeyType(), importSettings.isCheckExistence()))
                    primaryList.add(purchaseInvoiceDetail);
                else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, importSettings.isKeyIsDigit()))
                    secondaryList.add(purchaseInvoiceDetail);
            }
        }
        currentTimestamp = null;

        return checkArticles(context, session, importSettings.getPropertyImportType(), staticNameImportType, primaryList, secondaryList) ? Arrays.asList(primaryList, secondaryList) : null;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromXLSX(ExecutionContext context, DataSession session, byte[] importFile, 
                                                                         Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns, 
                                                                         Set<String> purchaseInvoiceSet, boolean checkInvoiceExistence,
                                                                         ImportDocumentSettings importSettings, DataObject userInvoiceObject,
                                                                         String staticNameImportType)
            throws IOException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<PurchaseInvoiceDetail>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<PurchaseInvoiceDetail>();

        String primaryKeyColumn = getItemKeyColumn(importSettings.getPrimaryKeyType());
        String secondaryKeyColumn = getItemKeyColumn(importSettings.getSecondaryKeyType());

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        Date currentDateDocument = getCurrentDateDocument(session, userInvoiceObject);
        currentTimestamp = getCurrentTimestamp();

        for (int i = importSettings.getStartRow() - 1; i <= sheet.getLastRowNum(); i++) {

            String numberDocument = getXLSXFieldValue(sheet, i, defaultColumns.get("numberDocument"));
            String idDocument = getXLSXFieldValue(sheet, i, defaultColumns.get("idDocument"), numberDocument);
            Date dateDocument = getXLSXDateFieldValue(sheet, i, defaultColumns.get("dateDocument"));
            Time timeDocument = getXLSXTimeFieldValue(sheet, i, defaultColumns.get("timeDocument"));
            String idSupplier = getXLSXFieldValue(sheet, i, defaultColumns.get("idSupplier"));
            String idSupplierStock = getXLSXFieldValue(sheet, i, defaultColumns.get("idSupplierStock"));
            String currencyDocument = getXLSXFieldValue(sheet, i, defaultColumns.get("currencyDocument"));
            String idUserInvoiceDetail = makeIdUserInvoiceDetail(idDocument, userInvoiceObject, i);
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSXFieldValue(sheet, i, defaultColumns.get("barcodeItem")), 7);
            String idBatch = getXLSXFieldValue(sheet, i, defaultColumns.get("idBatch"));
            BigDecimal dataIndexValue = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("dataIndex"));
            Integer dataIndex = dataIndexValue == null ? (primaryList.size() + secondaryList.size() + 1) : dataIndexValue.intValue();
            String idItem = getXLSXFieldValue(sheet, i, defaultColumns.get("idItem"));
            String idItemGroup = getXLSXFieldValue(sheet, i, defaultColumns.get("idItemGroup"));
            String originalCustomsGroupItem = getXLSXFieldValue(sheet, i, defaultColumns.get("originalCustomsGroupItem"));
            String captionItem = getXLSXFieldValue(sheet, i, defaultColumns.get("captionItem"));
            String originalCaptionItem = getXLSXFieldValue(sheet, i, defaultColumns.get("originalCaptionItem"));
            String UOMItem = getXLSXFieldValue(sheet, i, defaultColumns.get("UOMItem"));
            String idManufacturer = getXLSXFieldValue(sheet, i, defaultColumns.get("idManufacturer"));
            String nameManufacturer = getXLSXFieldValue(sheet, i, defaultColumns.get("nameManufacturer"));
            String sidOrigin2Country = getXLSXFieldValue(sheet, i, defaultColumns.get("sidOrigin2Country"));
            String nameCountry = modifyNameCountry(getXLSXFieldValue(sheet, i, defaultColumns.get("nameCountry")));
            String nameOriginCountry = modifyNameCountry(getXLSXFieldValue(sheet, i, defaultColumns.get("nameOriginCountry")));
            String importCountryBatch = getXLSXFieldValue(sheet, i, defaultColumns.get("importCountryBatch"));
            String idCustomerStock = getXLSXFieldValue(sheet, i, defaultColumns.get("idCustomerStock"));
            idCustomerStock = importSettings.getStockMapping().containsKey(idCustomerStock) ? importSettings.getStockMapping().get(idCustomerStock) : idCustomerStock;
            ObjectValue customerStockObject = idCustomerStock == null ? null : findProperty("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : findProperty("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : findProperty("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("quantity"));
            BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("price"));
            if (price != null && price.compareTo(new BigDecimal("100000000000")) > 0)
                price = null;
            BigDecimal sum = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("sum"));
            BigDecimal valueVAT = parseVAT(getXLSXFieldValue(sheet, i, defaultColumns.get("valueVAT")));
            BigDecimal sumVAT = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("sumVAT"));
            Date dateVAT = dateDocument == null ? currentDateDocument : dateDocument;
            BigDecimal invoiceSum = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("manufacturingPrice"));
            String contractPrice = getXLSXFieldValue(sheet, i, defaultColumns.get("contractPrice"));
            BigDecimal shipmentPrice = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("shipmentPrice"));
            BigDecimal shipmentSum = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("shipmentSum"));
            BigDecimal rateExchange = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("rateExchange"));
            String numberCompliance = getXLSXFieldValue(sheet, i, defaultColumns.get("numberCompliance"));
            Date dateCompliance = getXLSXDateFieldValue(sheet, i, defaultColumns.get("dateCompliance"));
            String declaration = getXLSXFieldValue(sheet, i, defaultColumns.get("declaration"));
            Date expiryDate = getXLSXDateFieldValue(sheet, i, defaultColumns.get("expiryDate"), true);
            Date manufactureDate = getXLSXDateFieldValue(sheet, i, defaultColumns.get("manufactureDate"));
            String pharmacyPriceGroupItem = getXLSXFieldValue(sheet, i, defaultColumns.get("pharmacyPriceGroupItem"));
            String seriesPharmacy = getXLSXFieldValue(sheet, i, defaultColumns.get("seriesPharmacy"));
            String idArticle = getXLSXFieldValue(sheet, i, defaultColumns.get("idArticle"));
            String captionArticle = getXLSXFieldValue(sheet, i, defaultColumns.get("captionArticle"));
            String originalCaptionArticle = getXLSXFieldValue(sheet, i, defaultColumns.get("originalCaptionArticle"));
            String idColor = getXLSXFieldValue(sheet, i, defaultColumns.get("idColor"));
            String nameColor = getXLSXFieldValue(sheet, i, defaultColumns.get("nameColor"));
            String idCollection = getXLSXFieldValue(sheet, i, defaultColumns.get("idCollection"));
            String nameCollection = getXLSXFieldValue(sheet, i, defaultColumns.get("nameCollection"));
            String idSize = getXLSXFieldValue(sheet, i, defaultColumns.get("idSize"));
            String nameSize = getXLSXFieldValue(sheet, i, defaultColumns.get("nameSize"));
            String nameOriginalSize = getXLSXFieldValue(sheet, i, defaultColumns.get("nameOriginalSize"));
            String idSeasonYear = getXLSXFieldValue(sheet, i, defaultColumns.get("idSeasonYear"));
            String idSeason = getXLSXFieldValue(sheet, i, defaultColumns.get("idSeason"));
            String nameSeason = getXLSXFieldValue(sheet, i, defaultColumns.get("nameSeason"));
            String idBrand = getXLSXFieldValue(sheet, i, defaultColumns.get("idBrand"));
            String nameBrand = getXLSXFieldValue(sheet, i, defaultColumns.get("nameBrand"));
            String idBox = getXLSXFieldValue(sheet, i, defaultColumns.get("idBox"));
            String nameBox = getXLSXFieldValue(sheet, i, defaultColumns.get("nameBox"));
            String idTheme = getXLSXFieldValue(sheet, i, defaultColumns.get("idTheme"));
            String nameTheme = getXLSXFieldValue(sheet, i, defaultColumns.get("nameTheme"));
            BigDecimal netWeight = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("netWeight"));
            BigDecimal netWeightSum = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("netWeightSum"));
            netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
            BigDecimal grossWeight = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("grossWeight"));
            BigDecimal grossWeightSum = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("grossWeightSum"));
            grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;
            String composition = getXLSXFieldValue(sheet, i, defaultColumns.get("composition"));
            String originalComposition = getXLSXFieldValue(sheet, i, defaultColumns.get("originalComposition"));

            LinkedHashMap<String, String> customValues = new LinkedHashMap<String, String>();
            for(Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                customValues.put(column.getKey(), getXLSXFieldValue(sheet, i, column.getValue()));
            }

            if(checkInvoice(purchaseInvoiceSet, idDocument, checkInvoiceExistence)) {
                PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(customValues, importSettings.isPosted(), idDocument,
                        numberDocument, dateDocument, timeDocument, idSupplier, idSupplierStock, currencyDocument, 
                        idUserInvoiceDetail, barcodeItem, idBatch, dataIndex, idItem, idItemGroup, originalCustomsGroupItem,
                        captionItem, originalCaptionItem, UOMItem, idManufacturer, nameManufacturer, sidOrigin2Country, 
                        nameCountry, nameOriginCountry, importCountryBatch, idCustomer, idCustomerStock, quantity, price, 
                        sum, VATifAllowed(valueVAT), sumVAT, dateVAT, defaultCountry, invoiceSum, manufacturingPrice,
                        contractPrice, shipmentPrice, shipmentSum, rateExchange, numberCompliance, dateCompliance,
                        declaration, expiryDate, manufactureDate, pharmacyPriceGroupItem, seriesPharmacy, idArticle,
                        captionArticle, originalCaptionArticle, idColor, nameColor, idCollection, nameCollection, idSize,
                        nameSize, nameOriginalSize, idSeasonYear, idSeason, nameSeason, idBrand, nameBrand, idBox, nameBox,
                        idTheme, nameTheme, netWeight, netWeightSum, grossWeight, grossWeightSum, composition,
                        originalComposition);

                String primaryKeyColumnValue = getXLSXFieldValue(sheet, i, defaultColumns.get(primaryKeyColumn));
                String secondaryKeyColumnValue = getXLSXFieldValue(sheet, i, defaultColumns.get(secondaryKeyColumn));
                if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, importSettings.isKeyIsDigit(),
                        session, importSettings.getPrimaryKeyType(), importSettings.isCheckExistence()))
                    primaryList.add(purchaseInvoiceDetail);
                else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, importSettings.isKeyIsDigit()))
                    primaryList.add(purchaseInvoiceDetail);
            }
        }
        currentTimestamp = null;

        return checkArticles(context, session, importSettings.getPropertyImportType(), staticNameImportType, primaryList, secondaryList) ? Arrays.asList(primaryList, secondaryList) : null;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromDBF(ExecutionContext context, DataSession session, byte[] importFile, 
                                                                        Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                        Set<String> purchaseInvoiceSet, boolean checkInvoiceExistence,
                                                                        ImportDocumentSettings importSettings, DataObject userInvoiceObject,
                                                                        String staticNameImportType)
            throws IOException, xBaseJException, UniversalImportException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<PurchaseInvoiceDetail>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<PurchaseInvoiceDetail>();

        String primaryKeyColumn = getItemKeyColumn(importSettings.getPrimaryKeyType());
        String secondaryKeyColumn = getItemKeyColumn(importSettings.getSecondaryKeyType());
        
        File tempFile = File.createTempFile("purchaseInvoice", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);

        DBF file = new DBF(tempFile.getPath());
        String charset = getDBFCharset(tempFile);

        int totalRecordCount = file.getRecordCount();

        Date currentDateDocument = getCurrentDateDocument(session, userInvoiceObject);        
        currentTimestamp = getCurrentTimestamp();
        for (int i = 0; i < importSettings.getStartRow() - 1; i++) {
            file.read();
        }

        for (int i = importSettings.getStartRow() - 1; i < totalRecordCount; i++) {

            file.read();

            String numberDocument = getDBFFieldValue(file, defaultColumns.get("numberDocument"), i, charset);
            String idDocument = getDBFFieldValue(file, defaultColumns.get("idDocument"), i, charset, numberDocument);
            Date dateDocument = getDBFDateFieldValue(file, defaultColumns.get("dateDocument"), i, charset);
            Time timeDocument = getDBFTimeFieldValue(file, defaultColumns.get("timeDocument"), i, charset);
            String idSupplier = getDBFFieldValue(file, defaultColumns.get("idSupplier"), i, charset);
            String idSupplierStock = getDBFFieldValue(file, defaultColumns.get("idSupplierStock"), i, charset);
            String currencyDocument = getDBFFieldValue(file, defaultColumns.get("currencyDocument"), i, charset);
            String idUserInvoiceDetail = makeIdUserInvoiceDetail(idDocument, userInvoiceObject, i);
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getDBFFieldValue(file, defaultColumns.get("barcodeItem"), i, charset), 7);
            String idBatch = getDBFFieldValue(file, defaultColumns.get("idBatch"), i, charset);
            BigDecimal dataIndexValue = getDBFBigDecimalFieldValue(file, defaultColumns.get("dataIndex"), i, charset, new BigDecimal(primaryList.size() + secondaryList.size() + 1));
            Integer dataIndex = dataIndexValue == null ? null : dataIndexValue.intValue();
            String idItem = getDBFFieldValue(file, defaultColumns.get("idItem"), i, charset);
            String idItemGroup = getDBFFieldValue(file, defaultColumns.get("idItemGroup"), i, charset);
            String originalCustomsGroupItem = getDBFFieldValue(file, defaultColumns.get("originalCustomsGroupItem"), i, charset);
            String captionItem = getDBFFieldValue(file, defaultColumns.get("captionItem"), i, charset);
            String originalCaptionItem = getDBFFieldValue(file, defaultColumns.get("originalCaptionItem"), i, charset);
            String UOMItem = getDBFFieldValue(file, defaultColumns.get("UOMItem"), i, charset);
            String idManufacturer = getDBFFieldValue(file, defaultColumns.get("idManufacturer"), i, charset);
            String nameManufacturer = getDBFFieldValue(file, defaultColumns.get("nameManufacturer"), i, charset);
            String sidOrigin2Country = getDBFFieldValue(file, defaultColumns.get("sidOrigin2Country"), i, charset);
            String nameCountry = modifyNameCountry(getDBFFieldValue(file, defaultColumns.get("nameCountry"), i, charset));
            String nameOriginCountry = modifyNameCountry(getDBFFieldValue(file, defaultColumns.get("nameOriginCountry"), i, charset));
            String importCountryBatch = getDBFFieldValue(file, defaultColumns.get("importCountryBatch"), i, charset);
            String idCustomerStock = getDBFFieldValue(file, defaultColumns.get("idCustomerStock"), i, charset);
            idCustomerStock = importSettings.getStockMapping().containsKey(idCustomerStock) ? importSettings.getStockMapping().get(idCustomerStock) : idCustomerStock;
            ObjectValue customerStockObject = idCustomerStock == null ? null : findProperty("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : findProperty("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : findProperty("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getDBFBigDecimalFieldValue(file, defaultColumns.get("quantity"), i, charset);
            BigDecimal price = getDBFBigDecimalFieldValue(file, defaultColumns.get("price"), i, charset);
            if (price != null && price.compareTo(new BigDecimal("100000000000")) > 0)
                price = null;
            BigDecimal sum = getDBFBigDecimalFieldValue(file, defaultColumns.get("sum"), i, charset);
            BigDecimal valueVAT = parseVAT(getDBFFieldValue(file, defaultColumns.get("valueVAT"), i, charset));
            BigDecimal sumVAT = getDBFBigDecimalFieldValue(file, defaultColumns.get("sumVAT"), i, charset);
            Date dateVAT = dateDocument == null ? currentDateDocument : dateDocument;
            BigDecimal invoiceSum = getDBFBigDecimalFieldValue(file, defaultColumns.get("invoiceSum"), i, charset);
            BigDecimal manufacturingPrice = getDBFBigDecimalFieldValue(file, defaultColumns.get("manufacturingPrice"), i, charset);
            String numberCompliance = getDBFFieldValue(file, defaultColumns.get("numberCompliance"), i, charset);
            String contractPrice = getDBFFieldValue(file, defaultColumns.get("contractPrice"), i, charset);
            BigDecimal shipmentPrice = getDBFBigDecimalFieldValue(file, defaultColumns.get("shipmentPrice"), i, charset);
            BigDecimal shipmentSum = getDBFBigDecimalFieldValue(file, defaultColumns.get("shipmentSum"), i, charset);
            BigDecimal rateExchange = getDBFBigDecimalFieldValue(file, defaultColumns.get("rateExchange"), i, charset);
            Date dateCompliance = getDBFDateFieldValue(file, defaultColumns.get("dateCompliance"), i, charset);
            String declaration = getDBFFieldValue(file, defaultColumns.get("declaration"), i, charset);
            Date expiryDate = getDBFDateFieldValue(file, defaultColumns.get("expiryDate"), i, charset, true);
            Date manufactureDate = getDBFDateFieldValue(file, defaultColumns.get("manufactureDate"), i, charset);
            String pharmacyPriceGroup = getDBFFieldValue(file, defaultColumns.get("pharmacyPriceGroupItem"), i, charset);
            String seriesPharmacy = getDBFFieldValue(file, defaultColumns.get("seriesPharmacy"), i, charset);
            String idArticle = getDBFFieldValue(file, defaultColumns.get("idArticle"), i, charset);
            String captionArticle = getDBFFieldValue(file, defaultColumns.get("captionArticle"), i, charset);
            String originalCaptionArticle = getDBFFieldValue(file, defaultColumns.get("originalCaptionArticle"), i, charset);
            String idColor = getDBFFieldValue(file, defaultColumns.get("idColor"), i, charset);
            String nameColor = getDBFFieldValue(file, defaultColumns.get("nameColor"), i, charset);
            String idCollection = getDBFFieldValue(file, defaultColumns.get("idCollection"), i, charset);
            String nameCollection = getDBFFieldValue(file, defaultColumns.get("nameCollection"), i, charset);
            String idSize = getDBFFieldValue(file, defaultColumns.get("idSize"), i, charset);
            String nameSize = getDBFFieldValue(file, defaultColumns.get("nameSize"), i, charset);
            String nameOriginalSize = getDBFFieldValue(file, defaultColumns.get("nameOriginalSize"), i, charset);
            String idSeasonYear = getDBFFieldValue(file, defaultColumns.get("idSeasonYear"), i, charset);
            String idSeason = getDBFFieldValue(file, defaultColumns.get("idSeason"), i, charset);
            String nameSeason = getDBFFieldValue(file, defaultColumns.get("nameSeason"), i, charset);
            String idBrand = getDBFFieldValue(file, defaultColumns.get("idBrand"), i, charset);
            String nameBrand = getDBFFieldValue(file, defaultColumns.get("nameBrand"), i, charset);
            String idBox = getDBFFieldValue(file, defaultColumns.get("idBox"), i, charset);
            String nameBox = getDBFFieldValue(file, defaultColumns.get("nameBox"), i, charset);
            String idTheme = getDBFFieldValue(file, defaultColumns.get("idTheme"), i, charset);
            String nameTheme = getDBFFieldValue(file, defaultColumns.get("nameTheme"), i, charset);
            BigDecimal netWeight = getDBFBigDecimalFieldValue(file, defaultColumns.get("netWeight"), i, charset);
            BigDecimal netWeightSum = getDBFBigDecimalFieldValue(file, defaultColumns.get("netWeightSum"), i, charset);
            netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
            BigDecimal grossWeight = getDBFBigDecimalFieldValue(file, defaultColumns.get("grossWeight"), i, charset);
            BigDecimal grossWeightSum = getDBFBigDecimalFieldValue(file, defaultColumns.get("grossWeightSum"), i, charset);
            grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;
            String composition = getDBFFieldValue(file, defaultColumns.get("composition"), i, charset);
            String originalComposition = getDBFFieldValue(file, defaultColumns.get("originalComposition"), i, charset);

            LinkedHashMap<String, String> customValues = new LinkedHashMap<String, String>();
            for(Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                customValues.put(column.getKey(), getDBFFieldValue(file, column.getValue(), i, charset));
            }

            if(checkInvoice(purchaseInvoiceSet, idDocument, checkInvoiceExistence)) {
                PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(customValues, importSettings.isPosted(), idDocument,
                        numberDocument, dateDocument, timeDocument, idSupplier, idSupplierStock, currencyDocument, 
                        idUserInvoiceDetail, barcodeItem, idBatch, dataIndex, idItem, idItemGroup, originalCustomsGroupItem,
                        captionItem, originalCaptionItem, UOMItem, idManufacturer, nameManufacturer, sidOrigin2Country,
                        nameCountry, nameOriginCountry, importCountryBatch, idCustomer, idCustomerStock, quantity, price,
                        sum, VATifAllowed(valueVAT), sumVAT, dateVAT, defaultCountry, invoiceSum, manufacturingPrice,
                        contractPrice, shipmentPrice, shipmentSum, rateExchange, numberCompliance, dateCompliance,
                        declaration, expiryDate, manufactureDate, pharmacyPriceGroup, seriesPharmacy, idArticle,
                        captionArticle, originalCaptionArticle, idColor, nameColor, idCollection, nameCollection, idSize,
                        nameSize, nameOriginalSize, idSeasonYear, idSeason, nameSeason, idBrand, nameBrand, idBox, nameBox,
                        idTheme, nameTheme, netWeight, netWeightSum, grossWeight, grossWeightSum, composition,
                        originalComposition);

                String primaryKeyColumnValue = getDBFFieldValue(file, defaultColumns.get(primaryKeyColumn), i, charset);
                String secondaryKeyColumnValue = getDBFFieldValue(file, defaultColumns.get(secondaryKeyColumn), i, charset);
                if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, importSettings.isKeyIsDigit(), session,
                        importSettings.getPrimaryKeyType(), importSettings.isCheckExistence()))
                    primaryList.add(purchaseInvoiceDetail);
                else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, importSettings.isKeyIsDigit()))
                    secondaryList.add(purchaseInvoiceDetail);
            }
        }

        currentTimestamp = null;
        file.close();
        tempFile.delete();
       
        return checkArticles(context, session, importSettings.getPropertyImportType(), staticNameImportType, primaryList, secondaryList) ? Arrays.asList(primaryList, secondaryList) : null;
    }

    private boolean checkInvoice(Set<String> invoiceSet, String idInvoice, boolean checkInvoiceExistence) {
        return !checkInvoiceExistence || !invoiceSet.contains(idInvoice);
    }

    private boolean checkArticles(ExecutionContext context, DataSession session, String propertyImportType,
                                  String staticNameImportType, List<PurchaseInvoiceDetail> primaryList, List<PurchaseInvoiceDetail> secondaryList)
            throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        if (propertyImportType != null && propertyImportType.contains(".")) {
            String sidProperty = getSplittedPart(propertyImportType, "\\.", 1);
            ScriptingLogicsModule moduleLM = context.getBL().getModule(getSplittedPart(propertyImportType, "\\.", 0));

            if (moduleLM != null) {
                List<Object> articles = getArticlesMap(session, moduleLM, sidProperty);
                Set<String> articleSet = articles == null ? null : (Set<String>) articles.get(0);
                Map<String, String> articlePropertyMap = articles == null ? null : (Map<String, String>) articles.get(1);
                Map<String, Object[]> duplicateArticles = new HashMap<String, Object[]>();
                primaryList.addAll(secondaryList);
                for (PurchaseInvoiceDetail invoiceDetail : primaryList) {
                    String oldPropertyArticle = articlePropertyMap.get(invoiceDetail.idArticle);
                    Object propertyValue = getField(invoiceDetail, staticNameImportType);
                    if (propertyValue != null && oldPropertyArticle != null && !oldPropertyArticle.equals(propertyValue) 
                            && !(propertyValue.toString().contains(oldPropertyArticle)) && !duplicateArticles.containsKey(invoiceDetail.idArticle)) {
                        duplicateArticles.put(invoiceDetail.idArticle, new Object[]{oldPropertyArticle, propertyValue});
                    }
                }

                HashMap<String, String> overridingArticles = null;
                if (!duplicateArticles.isEmpty()) {
                    overridingArticles = (HashMap<String, String>) context.requestUserInteraction(new ImportPreviewClientAction(duplicateArticles, articleSet));
                    if (overridingArticles == null)
                        return false;
                }

                if (overridingArticles != null) {
                    for (Map.Entry<String, String> entry : overridingArticles.entrySet()) {
                        moduleLM.findProperty("idArticle").change(entry.getValue(), context, (DataObject) moduleLM.findProperty("articleId").readClasses(context, new DataObject(entry.getKey())));
                    }
                }
            }
        }
        return true;
    }
    
    private List<Object> getArticlesMap(DataSession session, ScriptingLogicsModule LM, String sidProperty) 
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {        
        
        Set<String> articleSet = new HashSet<String>();
        Map<String, String> articlePropertyMap = new HashMap<String, String>();

        KeyExpr articleExpr = new KeyExpr("Article");
        ImRevMap<Object, KeyExpr> articleKeys = MapFact.singletonRev((Object) "Article", articleExpr);

        QueryBuilder<Object, Object> articleQuery = new QueryBuilder<Object, Object>(articleKeys);
        articleQuery.addProperty("idArticle", LM.findProperty("idArticle").getExpr(session.getModifier(), articleExpr));
        articleQuery.addProperty(sidProperty, LM.findProperty(sidProperty).getExpr(session.getModifier(), articleExpr));
        articleQuery.and(LM.findProperty("idArticle").getExpr(session.getModifier(), articleExpr).getWhere());
        
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> articleResult = articleQuery.execute(session);

        for (ImMap<Object, Object> entry : articleResult.values()) {

            String idArticle = (String) entry.get("idArticle");
            String property = (String) entry.get(sidProperty);
            if(property != null)
                articlePropertyMap.put(idArticle, property);
            articleSet.add(idArticle);           
        }
        return Arrays.asList(articleSet, articlePropertyMap);
    }

    protected Set<String> getPurchaseInvoiceSet(DataSession session, boolean checkInvoiceExistence) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if(!checkInvoiceExistence)
            return null;

        Set<String> purchaseInvoiceSet = new HashSet<String>();

        KeyExpr key = new KeyExpr("purchase.invoice");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "Purchase.Invoice", key);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);

        query.addProperty("Purchase.idUserInvoice", findProperty("Purchase.idUserInvoice").getExpr(session.getModifier(), key));
        query.and(findProperty("Purchase.idUserInvoice").getExpr(key).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String idUserInvoice = (String) entry.get("Purchase.idUserInvoice");
            if(idUserInvoice != null)
                purchaseInvoiceSet.add(idUserInvoice);
        }
        return purchaseInvoiceSet;
    }

    private Object getField(PurchaseInvoiceDetail purchaseInvoiceDetail, String fieldName) {
        try {
            Field field = PurchaseInvoiceDetail.class.getField(fieldName);
            return field.get(purchaseInvoiceDetail);
        } catch (NoSuchFieldException e) {
            return purchaseInvoiceDetail.customValues.get(fieldName);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private String makeIdUserInvoiceDetail(String idDocument, DataObject userInvoiceObject, int i) {
        return (idDocument != null ? idDocument : (userInvoiceObject == null ? "" : String.valueOf(userInvoiceObject.object))) + i;
    }

    private String modifyNameCountry(String nameCountry) {
        return nameCountry == null ? null : trim(nameCountry.replace("*", "")).toUpperCase();
    }
    
    private String getCurrentTimestamp() {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Calendar.getInstance().getTime());
    }
    
    private Date getCurrentDateDocument(DataSession session, DataObject userInvoiceObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Date defaultDate = new Date(Calendar.getInstance().getTime().getTime());
        Date currentDateDocument = userInvoiceObject == null ? defaultDate :
                (Date) findProperty("Purchase.dateUserInvoice").read(session, userInvoiceObject);
        return currentDateDocument == null ? defaultDate : currentDateDocument;
    }
}

