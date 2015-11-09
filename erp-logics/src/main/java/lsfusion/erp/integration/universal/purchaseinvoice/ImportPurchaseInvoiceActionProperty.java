package lsfusion.erp.integration.universal.purchaseinvoice;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import lsfusion.base.BaseUtils;
import lsfusion.base.IOUtils;
import lsfusion.base.Pair;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.erp.integration.universal.ImportPreviewClientAction;
import lsfusion.erp.integration.universal.UniversalImportException;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ImportPurchaseInvoiceActionProperty extends ImportDefaultPurchaseInvoiceActionProperty {
    private final ClassPropertyInterface userInvoiceInterface;

    public ImportPurchaseInvoiceActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        this(LM, LM.findClass("Purchase.UserInvoice"));
    }
    
    public ImportPurchaseInvoiceActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        userInvoiceInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataSession session = context.getSession();

            DataObject userInvoiceObject = context.getDataKeyValue(userInvoiceInterface);

            ObjectValue importTypeObject = findProperty("importTypeUserInvoice").readClasses(session, userInvoiceObject);

            if (importTypeObject instanceof DataObject) {

                ObjectValue operationObject = findProperty("autoImportOperationImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierObject = findProperty("autoImportSupplierImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierStockObject = findProperty("autoImportSupplierStockImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerObject = findProperty("autoImportCustomerImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerStockObject = findProperty("autoImportCustomerStockImportType").readClasses(session, (DataObject) importTypeObject);
                boolean checkInvoiceExistence = findProperty("autoImportCheckInvoiceExistenceImportType").read(session, (DataObject) importTypeObject) != null;

                String staticCaptionImportType = trim((String) findProperty("staticCaptionImportTypeDetailImportType").read(session, importTypeObject));
                String nameFieldImportType = trim((String) findProperty("staticNameImportTypeDetailImportType").read(session, importTypeObject));
                String[] splittedFieldImportType = nameFieldImportType == null ? null : nameFieldImportType.split("\\.");
                String staticNameImportType = splittedFieldImportType == null ? null : splittedFieldImportType[splittedFieldImportType.length - 1];
                
                List<LinkedHashMap<String, ImportColumnDetail>> importColumns = readImportColumns(session, importTypeObject);
                Set<String> purchaseInvoiceSet = getPurchaseInvoiceSet(session, checkInvoiceExistence);

                ImportDocumentSettings importSettings = readImportDocumentSettings(session, importTypeObject);
                String fileExtension = importSettings.getFileExtension();
                
                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            List<List<PurchaseInvoiceDetail>> userInvoiceDetailData = importUserInvoicesFromFile(context, session,
                                    userInvoiceObject, importColumns.get(0), importColumns.get(1), purchaseInvoiceSet, checkInvoiceExistence,
                                    file, fileExtension, importSettings, staticNameImportType, staticCaptionImportType);

                            if (userInvoiceDetailData != null && userInvoiceDetailData.size() >= 1) {
                                Pair<Integer, DataObject> result = importUserInvoices(userInvoiceDetailData.get(0), context, session, importColumns.get(0),
                                        importColumns.get(1), userInvoiceObject, importSettings.getPrimaryKeyType(),
                                        operationObject, supplierObject, supplierStockObject, customerObject,
                                        customerStockObject);
                                if(userInvoiceObject == null && result.second != null)
                                    userInvoiceObject = result.second;
                            }
                            
                            if (userInvoiceDetailData != null && userInvoiceDetailData.size() >= 2) {
                                Pair<Integer, DataObject> result = importUserInvoices(userInvoiceDetailData.get(1), context, session, importColumns.get(0),
                                        importColumns.get(1), userInvoiceObject, importSettings.getSecondaryKeyType(),
                                        operationObject, supplierObject, supplierStockObject, customerObject,
                                        customerStockObject);
                                if(userInvoiceObject == null && result.second != null)
                                    userInvoiceObject = result.second;
                            }

                            if(userInvoiceObject != null)
                                findProperty("originalInvoice").change(new DataObject(BaseUtils.mergeFileAndExtension(file, fileExtension.getBytes()), DynamicFormatFileClass.get(false, true)), context, userInvoiceObject);
                            
                            session.apply(context);

                            findAction("formRefresh").execute(context);
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | IOException | ParseException | BiffException | xBaseJException e) {
            throw new RuntimeException(e);
        } catch (UniversalImportException e) {
            e.printStackTrace();
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), e.getTitle()));
        }
    }
    
    public int makeImport(ExecutionContext<ClassPropertyInterface> context, DataSession session, DataObject userInvoiceObject,
                              DataObject importTypeObject, byte[] file, String fileExtension, ImportDocumentSettings importSettings,
                              String staticNameImportType, String staticCaptionImportType, boolean checkInvoiceExistence)
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
                importSettings, staticNameImportType, staticCaptionImportType);

        Integer result1 = (userInvoiceDetailData == null || userInvoiceDetailData.size() < 1) ? IMPORT_RESULT_EMPTY :
            importUserInvoices(userInvoiceDetailData.get(0), context, session, importColumns.get(0), importColumns.get(1),
                    userInvoiceObject, importSettings.getPrimaryKeyType(), operationObject, supplierObject,
                    supplierStockObject, customerObject, customerStockObject).first;

        Integer result2 = (userInvoiceDetailData == null || userInvoiceDetailData.size() < 2) ? IMPORT_RESULT_EMPTY :
            importUserInvoices(userInvoiceDetailData.get(1), context, session, importColumns.get(0), importColumns.get(1),
                    userInvoiceObject, importSettings.getSecondaryKeyType(), operationObject, supplierObject,
                    supplierStockObject, customerObject, customerStockObject).first;
        
        return (result1==IMPORT_RESULT_ERROR || result2==IMPORT_RESULT_ERROR) ? IMPORT_RESULT_ERROR : (result1 + result2);
    }

    public Pair<Integer, DataObject> importUserInvoices(List<PurchaseInvoiceDetail> userInvoiceDetailsList, ExecutionContext context, DataSession session,
                                  LinkedHashMap<String, ImportColumnDetail> defaultColumns, LinkedHashMap<String, ImportColumnDetail> customColumns,
                                  DataObject userInvoiceObject, String keyType, ObjectValue operationObject,
                                  ObjectValue supplierObject, ObjectValue supplierStockObject, ObjectValue customerObject,
                                  ObjectValue customerStockObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException, SQLHandledException {


        if (notNullNorEmpty(userInvoiceDetailsList)) {

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(userInvoiceDetailsList.size());

            ImportKey<?> userInvoiceKey = null;
            ImportField idUserInvoiceField = null;
            boolean multipleInvoices = userInvoiceObject == null && showField(userInvoiceDetailsList, "idUserInvoice");
            if (showField(userInvoiceDetailsList, "idUserInvoice")) {
                idUserInvoiceField = new ImportField(findProperty("idUserInvoice"));
                if(userInvoiceObject == null) {
                    userInvoiceKey = new ImportKey((CustomClass) findClass("UserInvoice"),
                            findProperty("userInvoiceId").getMapping(idUserInvoiceField));
                    keys.add(userInvoiceKey);
                }
                props.add(new ImportProperty(idUserInvoiceField, findProperty("idUserInvoice").getMapping(userInvoiceObject != null ? userInvoiceObject : userInvoiceKey)));
                fields.add(idUserInvoiceField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idUserInvoice);
            } else if(userInvoiceObject == null) {
                userInvoiceObject = session.addObject((ConcreteCustomClass) findClass("Purchase.UserInvoice"));
            }
            Object invoiceKey = multipleInvoices ? userInvoiceKey : userInvoiceObject;

            if (showField(userInvoiceDetailsList, "numberUserInvoice")) {
                    addDataField(props, fields, defaultColumns, findProperty("numberUserInvoice"), "numberDocument", invoiceKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).numberUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "dateDocument")) {
                addDataField(props, fields, defaultColumns, findProperty("dateUserInvoice"), "dateDocument", invoiceKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("dateDocument"));
            }

            if (showField(userInvoiceDetailsList, "timeDocument")) {
                addDataField(props, fields, defaultColumns, findProperty("timeUserInvoice"), "timeDocument", invoiceKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("timeDocument"));
            }

            if (showField(userInvoiceDetailsList, "currencyDocument")) {
                ImportField shortNameCurrencyField = new ImportField(findProperty("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((CustomClass) findClass("Currency"),
                        findProperty("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, findProperty("currencyUserInvoice").getMapping(invoiceKey),
                            object(findClass("Currency")).getMapping(currencyKey), getReplaceOnlyNull(defaultColumns, "currencyDocument")));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("currencyDocument"));
            }

            ImportField idUserInvoiceDetailField = new ImportField(findProperty("idUserInvoiceDetail"));
            ImportKey<?> userInvoiceDetailKey = new ImportKey((CustomClass) findClass("Purchase.UserInvoiceDetail"),
                    findProperty("userInvoiceDetailId").getMapping(idUserInvoiceDetailField));
            keys.add(userInvoiceDetailKey);
            props.add(new ImportProperty(idUserInvoiceDetailField, findProperty("idUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            if (multipleInvoices)
                props.add(new ImportProperty(idUserInvoiceField, findProperty("Purchase.userInvoiceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        object(findClass("Purchase.UserInvoice")).getMapping(userInvoiceKey)));
            else
                props.add(new ImportProperty(userInvoiceObject, findProperty("Purchase.userInvoiceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(idUserInvoiceDetailField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idUserInvoiceDetail);

            if (operationObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) operationObject, findProperty("Purchase.operationUserInvoice").getMapping(invoiceKey)));
            }

            if (supplierObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierObject, findProperty("Purchase.supplierUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) supplierObject, findProperty("Purchase.supplierUserInvoice").getMapping(invoiceKey)));
            }

            if (showField(userInvoiceDetailsList, "idSupplier")) {

                ImportField idSupplierField = new ImportField(findProperty("idLegalEntity"));
                ImportKey<?> supplierKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                        findProperty("legalEntityId").getMapping(idSupplierField));
                keys.add(supplierKey);
                props.add(new ImportProperty(idSupplierField, findProperty("supplierUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        object(findClass("LegalEntity")).getMapping(supplierKey), getReplaceOnlyNull(defaultColumns, "idSupplier")));
                    props.add(new ImportProperty(idSupplierField, findProperty("supplierUserInvoice").getMapping(invoiceKey),
                            object(findClass("LegalEntity")).getMapping(supplierKey), getReplaceOnlyNull(defaultColumns, "idSupplier")));
                fields.add(idSupplierField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idSupplier"));

            }

            if (showField(userInvoiceDetailsList, "idSupplierStock")) {

                ImportField idSupplierStockField = new ImportField(findProperty("idStock"));
                ImportKey<?> supplierStockKey = new ImportKey((CustomClass) findClass("Stock"),
                        findProperty("stockId").getMapping(idSupplierStockField));
                keys.add(supplierStockKey);
                props.add(new ImportProperty(idSupplierStockField, findProperty("supplierStockUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        object(findClass("Stock")).getMapping(supplierStockKey), getReplaceOnlyNull(defaultColumns, "idSupplierStock")));
                    props.add(new ImportProperty(idSupplierStockField, findProperty("supplierStockUserInvoice").getMapping(invoiceKey),
                            object(findClass("Stock")).getMapping(supplierStockKey), getReplaceOnlyNull(defaultColumns, "idSupplierStock")));
                fields.add(idSupplierStockField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idSupplierStock"));

            }

            if (supplierStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierStockObject, findProperty("Purchase.supplierStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) supplierStockObject, findProperty("Purchase.supplierStockUserInvoice").getMapping(invoiceKey)));
            }

            if (customerObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerObject, findProperty("Purchase.customerUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) customerObject, findProperty("Purchase.customerUserInvoice").getMapping(invoiceKey)));
            }

            if (customerStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerStockObject, findProperty("Purchase.customerStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) customerStockObject, findProperty("Purchase.customerStockUserInvoice").getMapping(invoiceKey)));
            }

            ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcodeSku"));
            ImportKey<?> barcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idBarcodeSkuField, findProperty("idBarcode").getMapping(barcodeKey), getReplaceOnlyNull(defaultColumns, "barcodeItem")));
            props.add(new ImportProperty(idBarcodeSkuField, findProperty("extIdBarcode").getMapping(barcodeKey), getReplaceOnlyNull(defaultColumns, "barcodeItem")));
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("barcodeItem"));

            ImportField idBatchField = new ImportField(findProperty("idBatch"));
            ImportKey<?> batchKey = new ImportKey((CustomClass) findClass("Batch"),
                    findProperty("batchId").getMapping(idBatchField));
            props.add(new ImportProperty(idBatchField, findProperty("idBatch").getMapping(batchKey)));
            props.add(new ImportProperty(idBatchField, findProperty("idBatchUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(idBatchField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idBatch"));

            ImportField dataIndexUserInvoiceDetailField = new ImportField(findProperty("dataIndexUserInvoiceDetail"));
            props.add(new ImportProperty(dataIndexUserInvoiceDetailField, findProperty("dataIndexUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(dataIndexUserInvoiceDetailField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("dataIndex"));

            ImportField idItemField = new ImportField(findProperty("idItem"));
            fields.add(idItemField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idItem"));

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
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("captionItem"));
            }

            if (showField(userInvoiceDetailsList, "originalCaptionItem")) {
                addDataField(props, fields, defaultColumns, findProperty("originalCaptionItem"), "originalCaptionItem", itemKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("originalCaptionItem"));
            }

            if (showField(userInvoiceDetailsList, "netWeight")) {
                addDataField(props, fields, defaultColumns, findProperty("netWeightItem"), "netWeight", itemKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).netWeight);
            }

            if (showField(userInvoiceDetailsList, "grossWeight")) {
                addDataField(props, fields, defaultColumns, findProperty("grossWeightItem"), "grossWeight", itemKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).grossWeight);
            }

            if (showField(userInvoiceDetailsList, "sumNetWeight")) {
                addDataField(props, fields, defaultColumns, findProperty("sumNetWeightUserInvoiceDetail"), "sumNetWeight", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).sumNetWeight);
            }

            if (showField(userInvoiceDetailsList, "sumGrossWeight")) {
                addDataField(props, fields, defaultColumns, findProperty("sumGrossWeightUserInvoiceDetail"), "sumGrossWeight", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).sumGrossWeight);
            }

            if (showField(userInvoiceDetailsList, "UOMItem")) {
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
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("UOMItem"));
            }

            if (showField(userInvoiceDetailsList, "idManufacturer")) {
                ImportField idManufacturerField = new ImportField(LM.findProperty("idManufacturer"));
                ImportKey<?> manufacturerKey = new ImportKey((CustomClass) LM.findClass("Manufacturer"),
                        LM.findProperty("manufacturerId").getMapping(idManufacturerField));
                keys.add(manufacturerKey);
                props.add(new ImportProperty(idManufacturerField, LM.findProperty("idManufacturer").getMapping(manufacturerKey), getReplaceOnlyNull(defaultColumns, "idManufacturer")));
                props.add(new ImportProperty(idManufacturerField, LM.findProperty("manufacturerItem").getMapping(itemKey),
                        object(LM.findClass("Manufacturer")).getMapping(manufacturerKey), getReplaceOnlyNull(defaultColumns, "idManufacturer")));
                fields.add(idManufacturerField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idManufacturer"));

                if (showField(userInvoiceDetailsList, "nameManufacturer")) {
                    addDataField(props, fields, defaultColumns, LM.findProperty("nameManufacturer"), "nameManufacturer", manufacturerKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameManufacturer"));
                }
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

                props.add(new ImportProperty(countryField, LM.findProperty("countryItem").getMapping(itemKey),
                        object(LM.findClass("Country")).getMapping(countryKey), getReplaceOnlyNull(defaultColumns, countryReplaceField)));

                if (showSidOrigin2Country) {
                    props.add(new ImportProperty(sidOrigin2CountryField, LM.findProperty("sidOrigin2Country").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "sidOrigin2Country")));
                    fields.add(sidOrigin2CountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("sidOrigin2Country"));
                }
                if (showNameCountry) {
                    props.add(new ImportProperty(nameCountryField, LM.findProperty("nameCountry").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "nameCountry")));
                    fields.add(nameCountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameCountry"));
                }
                if (showNameOriginCountry) {
                    props.add(new ImportProperty(nameOriginCountryField, LM.findProperty("nameOriginCountry").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "nameOriginCountry")));
                    fields.add(nameOriginCountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameOriginCountry"));
                }
            }

            if (showField(userInvoiceDetailsList, "idCustomer")) {
                ImportField idCustomerField = new ImportField(findProperty("idLegalEntity"));
                ImportKey<?> customerKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                        findProperty("legalEntityId").getMapping(idCustomerField));
                keys.add(customerKey);
                    props.add(new ImportProperty(idCustomerField, findProperty("Purchase.customerUserInvoice").getMapping(invoiceKey),
                            object(findClass("LegalEntity")).getMapping(customerKey), getReplaceOnlyNull(defaultColumns, "idBCustomer")));
                fields.add(idCustomerField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idCustomer"));
            }

            if (showField(userInvoiceDetailsList, "idCustomerStock")) {
                ImportField idCustomerStockField = new ImportField(findProperty("idStock"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) findClass("Stock"),
                        findProperty("stockId").getMapping(idCustomerStockField));
                keys.add(customerStockKey);
                    props.add(new ImportProperty(idCustomerStockField, findProperty("Purchase.customerStockUserInvoice").getMapping(invoiceKey),
                            object(findClass("Stock")).getMapping(customerStockKey), getReplaceOnlyNull(defaultColumns, "idCustomerStock")));
                fields.add(idCustomerStockField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idCustomerStock"));
            }

            if (showField(userInvoiceDetailsList, "quantity")) {
                addDataField(props, fields, defaultColumns, findProperty("Purchase.quantityUserInvoiceDetail"), "quantity", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).quantity);
            }

            if (showField(userInvoiceDetailsList, "price")) {
                addDataField(props, fields, defaultColumns, findProperty("Purchase.priceUserInvoiceDetail"), "price", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("price"));
            }

            if (showField(userInvoiceDetailsList, "sum")) {
                addDataField(props, fields, defaultColumns, findProperty("Purchase.sumUserInvoiceDetail"), "sum", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("sum"));
            }

            if (showField(userInvoiceDetailsList, "valueVAT")) {
                ImportField valueVATUserInvoiceDetailField = new ImportField(findProperty("Purchase.valueVATUserInvoiceDetail"));
                ImportKey<?> VATKey = new ImportKey((CustomClass) findClass("Range"),
                        findProperty("valueCurrentVATDefaultValue").getMapping(valueVATUserInvoiceDetailField));
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATUserInvoiceDetailField, findProperty("Purchase.VATUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        object(findClass("Range")).getMapping(VATKey), getReplaceOnlyNull(defaultColumns, "valueVAT")));
                fields.add(valueVATUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("valueVAT"));

                ImportField dateField = new ImportField(DateClass.instance);
                props.add(new ImportProperty(dateField, findProperty("dataDateBarcode").getMapping(barcodeKey), true));
                fields.add(dateField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("dateVAT"));

                new ImportPurchaseInvoiceTaxItem(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, valueVATUserInvoiceDetailField, itemKey, VATKey);
                
            }

            if (showField(userInvoiceDetailsList, "sumVAT")) {
                addDataField(props, fields, defaultColumns, findProperty("Purchase.VATSumUserInvoiceDetail"), "sumVAT", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("sumVAT"));
            }

            if (showField(userInvoiceDetailsList, "invoiceSum")) {
                addDataField(props, fields, defaultColumns, findProperty("Purchase.invoiceSumUserInvoiceDetail"), "invoiceSum", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("invoiceSum"));
            }

            new ImportPurchaseInvoicePurchaseShipmentBox(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);

            new ImportPurchaseInvoicePurchaseDeclaration(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);

            new ImportPurchaseInvoicePurchaseShipment(LM).makeImport(context, fields, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);
            
            new ImportPurchaseInvoicePurchaseManufacturingPrice(LM).makeImport(context, fields, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);               

            new ImportPurchaseInvoiceItemPharmacyBy(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, itemKey);
            
            new ImportPurchaseInvoicePurchaseInvoicePharmacy(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);

            new ImportPurchaseInvoicePurchaseCompliance(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);
            
            if (showField(userInvoiceDetailsList, "rateExchange")) {
                addDataField(props, fields, defaultColumns, findProperty("rateExchangeUserInvoiceDetail"), "rateExchange", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("rateExchange"));
            }
            
            if (showField(userInvoiceDetailsList, "isPosted")) {
                    addDataField(props, fields, defaultColumns, findProperty("isPostedUserInvoice"), "isPosted", invoiceKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).isPosted);
            }

            if (showField(userInvoiceDetailsList, "idItemGroup")) {
                ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
                ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                        findProperty("itemGroupId").getMapping(idItemGroupField));
                keys.add(itemGroupKey);
                props.add(new ImportProperty(idItemGroupField, findProperty("idItemGroup").getMapping(itemGroupKey), getReplaceOnlyNull(defaultColumns, "idItemGroup")));
                props.add(new ImportProperty(idItemGroupField, findProperty("nameItemGroup").getMapping(itemGroupKey), true));
                props.add(new ImportProperty(idItemGroupField, findProperty("itemGroupItem").getMapping(itemKey),
                        object(findClass("ItemGroup")).getMapping(itemGroupKey), getReplaceOnlyNull(defaultColumns, "idItemGroup")));
                fields.add(idItemGroupField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idItemGroup"));
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
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idArticle"));

                new ImportPurchaseInvoiceItemArticle(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, itemKey, articleKey);                               

                new ImportPurchaseInvoiceCustomsGroupArticle(LM).makeImport(context, fields, props, defaultColumns, userInvoiceDetailsList, data, itemKey, articleKey);

                new ImportPurchaseInvoiceItemFashion(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, itemKey, articleKey);
                
            }

            for (Map.Entry<String, ImportColumnDetail> entry : customColumns.entrySet()) {
                ImportColumnDetail customColumn = entry.getValue();
                LCP<?> customProp = (LCP<?>) context.getBL().findSafeProperty(customColumn.propertyCanonicalName);
                if (customProp != null) {
                    ImportField customField = new ImportField(customProp);
                    ImportKey<?> customKey = null;
                    switch (customColumn.key) {
                        case "item":
                            customKey = itemKey;
                            break;
                        case "article":
                            customKey = articleKey;
                            break;
                        case "documentDetail":
                            customKey = userInvoiceDetailKey;
                            break;
                    }
                    if (customKey != null) {
                        props.add(new ImportProperty(customField, customProp.getMapping(customKey), getReplaceOnlyNull(customColumns, entry.getKey())));
                        fields.add(customField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).customValues.get(entry.getKey()));
                    } else if(customColumn.key.equals("document")) {
                        props.add(new ImportProperty(customField, customProp.getMapping(invoiceKey), getReplaceOnlyNull(customColumns, entry.getKey())));
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
            return Pair.create(IMPORT_RESULT_OK, userInvoiceObject);
        }   else return Pair.create(IMPORT_RESULT_EMPTY, userInvoiceObject);
    }

    protected List<List<PurchaseInvoiceDetail>> importUserInvoicesFromFile(ExecutionContext context, DataSession session, DataObject userInvoiceObject,
                                                                           Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns, 
                                                                           Set<String> purchaseInvoiceSet, boolean checkInvoiceExistence,
                                                                           byte[] file, String fileExtension, ImportDocumentSettings importSettings,
                                                                           String staticNameImportType, String staticCaptionImportType)
            throws ParseException, UniversalImportException, IOException, SQLException, xBaseJException, ScriptingErrorLog.SemanticErrorException, BiffException, SQLHandledException {

        List<List<PurchaseInvoiceDetail>> userInvoiceDetailsList;

        //as in ImportDocument.lsf, CLASS ImportTypeDetail
        List<String> stringFields = Arrays.asList("idSupplier", "idSupplierStock", "currencyDocument", "idItem", "idItemGroup",
                "barcodeItem", "originalCustomsGroupItem", "idBatch", "idBox", "nameBox", "captionItem", "originalCaptionItem", 
                "UOMItem", "idManufacturer", "nameManufacturer", "sidOrigin2Country", "nameCountry", "nameOriginCountry", 
                "importCountryBatch", "idCustomerStock", "contractPrice", "pharmacyPriceGroupItem", "valueVAT", "seriesPharmacy", 
                "numberCompliance", "declaration", "idArticle", "captionArticle", "originalCaptionArticle", "idColor",
                "nameColor", "idTheme", "nameTheme", "composition", "originalComposition", "idSize", "nameSize", "nameOriginalSize",
                "idCollection", "nameCollection", "idSeasonYear", "idSeason", "nameSeason", "idBrand", "nameBrand");
        
        List<String> bigDecimalFields = Arrays.asList("dataIndex", "price", "manufacturingPrice", "shipmentPrice", 
                "shipmentSum", "rateExchange", "sum",  "sumVAT", "invoiceSum");

        List<String> dateFields = Arrays.asList("dateDocument", "manufactureDate", "dateCompliance", "expiryDate");

        List<String> timeFields = Collections.singletonList("timeDocument");

        switch (fileExtension) {
            case "DBF":
                userInvoiceDetailsList = importUserInvoicesFromDBF(context, session, file, defaultColumns, customColumns,
                        stringFields, bigDecimalFields, dateFields, timeFields, purchaseInvoiceSet, checkInvoiceExistence,
                        importSettings, userInvoiceObject, staticNameImportType, staticCaptionImportType);
                break;
            case "XLS":
                userInvoiceDetailsList = importUserInvoicesFromXLS(context, session, file, defaultColumns, customColumns,
                        stringFields, bigDecimalFields, dateFields, timeFields, purchaseInvoiceSet, checkInvoiceExistence,
                        importSettings, userInvoiceObject, staticNameImportType, staticCaptionImportType);
                break;
            case "XLSX":
                userInvoiceDetailsList = importUserInvoicesFromXLSX(context, session, file, defaultColumns, customColumns,
                        stringFields, bigDecimalFields, dateFields, timeFields, purchaseInvoiceSet, checkInvoiceExistence,
                        importSettings, userInvoiceObject, staticNameImportType, staticCaptionImportType);
                break;
            case "CSV":
            case "TXT":
                userInvoiceDetailsList = importUserInvoicesFromCSV(context, session, file, defaultColumns, customColumns,
                        stringFields, bigDecimalFields, dateFields, timeFields, purchaseInvoiceSet, checkInvoiceExistence,
                        importSettings, userInvoiceObject, staticNameImportType, staticCaptionImportType);
                break;
            default:
                userInvoiceDetailsList = null;
                break;
        }

        return userInvoiceDetailsList;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromXLS(ExecutionContext context, DataSession session, byte[] importFile, 
                                                                        Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                        List<String> stringFields, List<String> bigDecimalFields,  List<String> dateFields, List<String> timeFields,
                                                                        Set<String> purchaseInvoiceSet, 
                                                                        boolean checkInvoiceExistence, ImportDocumentSettings importSettings, DataObject userInvoiceObject,
                                                                        String staticNameImportType, String staticCaptionImportType)
            throws IOException, BiffException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<>();

        String primaryKeyColumn = getItemKeyColumn(importSettings.getPrimaryKeyType());
        String secondaryKeyColumn = getItemKeyColumn(importSettings.getSecondaryKeyType());

        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding("cp1251");
        Workbook wb = null;
        try {
            wb = Workbook.getWorkbook(new ByteArrayInputStream(importFile), ws);
        } catch (Exception e) {
            String error = "Файл неизвестного либо устаревшего формата";
            context.requestUserInteraction(new MessageClientAction(error, "Ошибка при открытии файла"));
            throw new RuntimeException(error, e);
        }
        Sheet sheet = wb.getSheet(0);

        Date currentDateDocument = getCurrentDateDocument(session, userInvoiceObject);
        currentTimestamp = getCurrentTimestamp();

        for (int i = importSettings.getStartRow() - 1; i < sheet.getRows(); i++) {

            Map<String, Object> fieldValues = new HashMap<>();
            for (String field : stringFields) {
                String value = getXLSFieldValue(sheet, i, defaultColumns.get(field));
                switch (field) {
                    case "nameCountry":
                    case "nameOriginCountry":
                        fieldValues.put(field, modifyNameCountry(value));
                        break;
                    case "valueVAT":
                        fieldValues.put(field, VATifAllowed(parseVAT(value)));
                        break;
                    case "barcodeItem":
                        fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7));
                        break;
                    case "idCustomerStock":
                        value = importSettings.getStockMapping().containsKey(value) ? importSettings.getStockMapping().get(value) : value;
                        fieldValues.put("idCustomerStock", value);
                        fieldValues.put("idCustomer", readIdCustomer(session, value));
                        break;
                    default:
                        fieldValues.put(field, value);
                        break;
                }
            }

            for (String field : bigDecimalFields) {
                ImportColumnDetail column = defaultColumns.get(field);
                BigDecimal value = getXLSBigDecimalFieldValue(sheet, i, column);
                switch (field) {
                    case "sumVAT":
                        fieldValues.put(field, value == null && column != null ? BigDecimal.ZERO : value);
                        break;
                    case "dataIndex":
                        fieldValues.put(field, value == null ? (primaryList.size() + secondaryList.size() + 1) : value.intValue());
                        break;
                    case "price":
                        fieldValues.put(field, value != null && value.compareTo(new BigDecimal("100000000000")) > 0 ? null : value);
                        break;
                    default:
                        fieldValues.put(field, value);
                        break;
                }
            }

            for (String field : dateFields) {
                switch (field) {
                    case "dateDocument":
                        Date dateDocument = getXLSDateFieldValue(sheet, i, defaultColumns.get(field));
                        Date dateVAT = dateDocument == null ? currentDateDocument : dateDocument;
                        fieldValues.put(field, dateDocument);
                        fieldValues.put("dateVAT", dateVAT);
                        break;
                    case "expiryDate":
                        fieldValues.put(field, getXLSDateFieldValue(sheet, i, defaultColumns.get(field), true));
                        break;
                    default:
                        fieldValues.put(field, getXLSDateFieldValue(sheet, i, defaultColumns.get(field)));
                        break;
                }
            }

            for(String field : timeFields) {
                fieldValues.put(field, getXLSTimeFieldValue(sheet, i, defaultColumns.get(field)));
            }

            String numberDocument = getXLSFieldValue(sheet, i, defaultColumns.get("numberDocument"));
            String idDocument = getXLSFieldValue(sheet, i, defaultColumns.get("idDocument"), numberDocument);
            String idUserInvoiceDetail = makeIdUserInvoiceDetail(idDocument, userInvoiceObject, i);
            BigDecimal quantity = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("quantity"));
            BigDecimal netWeight = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("netWeight"));
            BigDecimal netWeightSum = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("netWeightSum"));
            netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
            BigDecimal grossWeight = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("grossWeight"));
            BigDecimal grossWeightSum = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get("grossWeightSum"));
            grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;

            LinkedHashMap<String, String> customValues = new LinkedHashMap<>();
            for(Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                customValues.put(column.getKey(), getXLSFieldValue(sheet, i, column.getValue()));
            }

            if(checkInvoice(purchaseInvoiceSet, idDocument, checkInvoiceExistence)) {
                PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(customValues, fieldValues, importSettings.isPosted(),
                        idDocument, numberDocument, idUserInvoiceDetail, quantity, netWeight, netWeightSum,
                        grossWeight, grossWeightSum);

                String primaryKeyColumnValue = getXLSFieldValue(sheet, i, defaultColumns.get(primaryKeyColumn));
                String secondaryKeyColumnValue = getXLSFieldValue(sheet, i, defaultColumns.get(secondaryKeyColumn));
                if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, importSettings.isKeyIsDigit(), session, importSettings.getPrimaryKeyType(), importSettings.isCheckExistence()))
                    primaryList.add(purchaseInvoiceDetail);
                else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, importSettings.isKeyIsDigit()))
                    secondaryList.add(purchaseInvoiceDetail);
            }
        }
        currentTimestamp = null;

        return checkArticles(context, session, importSettings.getPropertyImportType(), staticNameImportType, staticCaptionImportType,
                primaryList, secondaryList) ? Arrays.asList(primaryList, secondaryList) : null;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromCSV(ExecutionContext context, DataSession session, byte[] importFile, 
                                                                        Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                        List<String> stringFields, List<String> bigDecimalFields,  List<String> dateFields, List<String> timeFields,
                                                                        Set<String> purchaseInvoiceSet, 
                                                                        boolean checkInvoiceExistence, ImportDocumentSettings importSettings, DataObject userInvoiceObject, 
                                                                        String staticNameImportType, String staticCaptionImportType)
            throws IOException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<>();

        String primaryKeyColumn = getItemKeyColumn(importSettings.getPrimaryKeyType());
        String secondaryKeyColumn = getItemKeyColumn(importSettings.getSecondaryKeyType());
        
        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile), "cp1251"));
        String line;


        Date currentDateDocument = getCurrentDateDocument(session, userInvoiceObject);
        currentTimestamp = getCurrentTimestamp();

        List<String[]> valuesList = new ArrayList<>();
        while ((line = br.readLine()) != null) {
                valuesList.add(line.split(importSettings.getSeparator()));
        }

        for (int count = importSettings.getStartRow(); count <= valuesList.size(); count++) {

            Map<String, Object> fieldValues = new HashMap<>();
            for (String field : stringFields) {
                String value = getCSVFieldValue(valuesList, defaultColumns.get(field), count);
                switch (field) {
                    case "nameCountry":
                    case "nameOriginCountry":
                        fieldValues.put(field, modifyNameCountry(value));
                        break;
                    case "valueVAT":
                        fieldValues.put(field, VATifAllowed(parseVAT(value)));
                        break;
                    case "barcodeItem":
                        fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value));
                        break;
                    case "idCustomerStock":
                        value = importSettings.getStockMapping().containsKey(value) ? importSettings.getStockMapping().get(value) : value;
                        fieldValues.put("idCustomerStock", value);
                        fieldValues.put("idCustomer", readIdCustomer(session, value));
                        break;
                    default:
                        fieldValues.put(field, value);
                        break;
                }
            }

            for (String field : bigDecimalFields) {
                ImportColumnDetail column = defaultColumns.get(field);
                BigDecimal value = getCSVBigDecimalFieldValue(valuesList, column, count);
                switch (field) {
                    case "sumVAT":
                        fieldValues.put(field, value == null && column != null ? BigDecimal.ZERO : value);
                        break;
                    case "dataIndex":
                        fieldValues.put(field, value == null ? (primaryList.size() + secondaryList.size() + 1) : value.intValue());
                        break;
                    case "price":
                        fieldValues.put(field, value != null && value.compareTo(new BigDecimal("100000000000")) > 0 ? null : value);
                        break;
                    default:
                        fieldValues.put(field, value);
                        break;
                }
            }

            for(String field : dateFields) {
                switch (field) {
                    case "dateDocument":
                        Date dateDocument = getCSVDateFieldValue(valuesList, defaultColumns.get(field), count);
                        Date dateVAT = dateDocument == null ? currentDateDocument : dateDocument;
                        fieldValues.put(field, dateDocument);
                        fieldValues.put("dateVAT", dateVAT);
                        break;
                    case "expiryDate":
                        fieldValues.put(field, getCSVDateFieldValue(valuesList, defaultColumns.get(field), count, true));
                        break;
                    default:
                        fieldValues.put(field, getCSVDateFieldValue(valuesList, defaultColumns.get(field), count));
                        break;
                }
            }

            for(String field : timeFields) {
                fieldValues.put(field, getCSVTimeFieldValue(valuesList, defaultColumns.get(field), count));
            }
            
            String numberDocument = getCSVFieldValue(valuesList, defaultColumns.get("numberDocument"), count);
            String idDocument = getCSVFieldValue(valuesList, defaultColumns.get("idDocument"), count, numberDocument);
            String idUserInvoiceDetail = makeIdUserInvoiceDetail(idDocument, userInvoiceObject, count);
            BigDecimal quantity = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("quantity"), count);
            BigDecimal netWeight = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("netWeight"), count);
            BigDecimal netWeightSum = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("netWeightSum"), count);
            netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
            BigDecimal grossWeight = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("grossWeight"), count);
            BigDecimal grossWeightSum = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get("grossWeight"), count);
            grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;

            LinkedHashMap<String, String> customValues = new LinkedHashMap<>();
            for(Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                customValues.put(column.getKey(), getCSVFieldValue(valuesList, column.getValue(), count));
            }

            if(checkInvoice(purchaseInvoiceSet, idDocument, checkInvoiceExistence)) {
                PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(customValues, fieldValues, importSettings.isPosted(), 
                        idDocument, numberDocument, idUserInvoiceDetail, quantity, netWeight, netWeightSum, grossWeight, grossWeightSum);

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

        return checkArticles(context, session, importSettings.getPropertyImportType(), staticNameImportType, staticCaptionImportType, 
                primaryList, secondaryList) ? Arrays.asList(primaryList, secondaryList) : null;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromXLSX(ExecutionContext context, DataSession session, byte[] importFile, 
                                                                         Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns, 
                                                                         List<String> stringFields, List<String> bigDecimalFields,  List<String> dateFields, List<String> timeFields,
                                                                         Set<String> purchaseInvoiceSet, 
                                                                         boolean checkInvoiceExistence, ImportDocumentSettings importSettings, DataObject userInvoiceObject,
                                                                         String staticNameImportType, String staticCaptionImportType)
            throws IOException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<>();

        String primaryKeyColumn = getItemKeyColumn(importSettings.getPrimaryKeyType());
        String secondaryKeyColumn = getItemKeyColumn(importSettings.getSecondaryKeyType());

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        Date currentDateDocument = getCurrentDateDocument(session, userInvoiceObject);
        currentTimestamp = getCurrentTimestamp();

        for (int i = importSettings.getStartRow() - 1; i <= sheet.getLastRowNum(); i++) {

            Map<String, Object> fieldValues = new HashMap<>();
            for (String field : stringFields) {
                String value = getXLSXFieldValue(sheet, i, defaultColumns.get(field));
                switch (field) {
                    case "nameCountry":
                    case "nameOriginCountry":
                        fieldValues.put(field, modifyNameCountry(value));
                        break;
                    case "valueVAT":
                        fieldValues.put(field, VATifAllowed(parseVAT(value)));
                        break;
                    case "barcodeItem":
                        fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7));
                        break;
                    case "idCustomerStock":
                        value = importSettings.getStockMapping().containsKey(value) ? importSettings.getStockMapping().get(value) : value;
                        fieldValues.put("idCustomerStock", value);
                        fieldValues.put("idCustomer", readIdCustomer(session, value));
                        break;
                    default:
                        fieldValues.put(field, value);
                        break;
                }
            }

            for (String field : bigDecimalFields) {
                ImportColumnDetail column = defaultColumns.get(field);
                BigDecimal value = getXLSXBigDecimalFieldValue(sheet, i, column);
                switch (field) {
                    case "sumVAT":
                        fieldValues.put(field, value == null && column != null ? BigDecimal.ZERO : value);
                        break;
                    case "dataIndex":
                        fieldValues.put(field, value == null ? (primaryList.size() + secondaryList.size() + 1) : value.intValue());
                        break;
                    case "price":
                        fieldValues.put(field, value != null && value.compareTo(new BigDecimal("100000000000")) > 0 ? null : value);
                        break;
                    default:
                        fieldValues.put(field, value);
                        break;
                }
            }

            for (String field : dateFields) {
                switch (field) {
                    case "dateDocument":
                        Date dateDocument = getXLSXDateFieldValue(sheet, i, defaultColumns.get(field));
                        Date dateVAT = dateDocument == null ? currentDateDocument : dateDocument;
                        fieldValues.put(field, dateDocument);
                        fieldValues.put("dateVAT", dateVAT);
                        break;
                    case "expiryDate":
                        fieldValues.put(field, getXLSXDateFieldValue(sheet, i, defaultColumns.get(field), true));
                        break;
                    default:
                        fieldValues.put(field, getXLSXDateFieldValue(sheet, i, defaultColumns.get(field)));
                        break;
                }
            }

            for(String field : timeFields) {
                fieldValues.put(field, getXLSXTimeFieldValue(sheet, i, defaultColumns.get(field)));
            }
            
            String numberDocument = getXLSXFieldValue(sheet, i, defaultColumns.get("numberDocument"));
            String idDocument = getXLSXFieldValue(sheet, i, defaultColumns.get("idDocument"), numberDocument);
            String idUserInvoiceDetail = makeIdUserInvoiceDetail(idDocument, userInvoiceObject, i);
            BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("quantity"));
            BigDecimal netWeight = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("netWeight"));
            BigDecimal netWeightSum = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("netWeightSum"));
            netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
            BigDecimal grossWeight = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("grossWeight"));
            BigDecimal grossWeightSum = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get("grossWeightSum"));
            grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;

            LinkedHashMap<String, String> customValues = new LinkedHashMap<>();
            for(Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                customValues.put(column.getKey(), getXLSXFieldValue(sheet, i, column.getValue()));
            }

            if(checkInvoice(purchaseInvoiceSet, idDocument, checkInvoiceExistence)) {
                PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(customValues, fieldValues, importSettings.isPosted(), 
                        idDocument, numberDocument, idUserInvoiceDetail, quantity, netWeight, netWeightSum, grossWeight, grossWeightSum);

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

        return checkArticles(context, session, importSettings.getPropertyImportType(), staticNameImportType, staticCaptionImportType, 
                primaryList, secondaryList) ? Arrays.asList(primaryList, secondaryList) : null;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromDBF(ExecutionContext context, DataSession session, byte[] importFile, 
                                                                        Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                        List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, List<String> timeFields,
                                                                        Set<String> purchaseInvoiceSet, 
                                                                        boolean checkInvoiceExistence, ImportDocumentSettings importSettings, DataObject userInvoiceObject,
                                                                        String staticNameImportType, String staticCaptionImportType)
            throws IOException, xBaseJException, UniversalImportException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<>();

        String primaryKeyColumn = getItemKeyColumn(importSettings.getPrimaryKeyType());
        String secondaryKeyColumn = getItemKeyColumn(importSettings.getSecondaryKeyType());

        File tempFile = null;
        DBF file = null;
        try {

            tempFile = File.createTempFile("purchaseInvoice", ".dbf");
            IOUtils.putFileBytes(tempFile, importFile);

            file = new DBF(tempFile.getPath());
            String charset = getDBFCharset(tempFile);

            int totalRecordCount = file.getRecordCount();

            Date currentDateDocument = getCurrentDateDocument(session, userInvoiceObject);
            currentTimestamp = getCurrentTimestamp();
            for (int i = 0; i < importSettings.getStartRow() - 1; i++) {
                file.read();
            }

            for (int i = importSettings.getStartRow() - 1; i < totalRecordCount; i++) {

                file.read();

                Map<String, Object> fieldValues = new HashMap<>();

                for (String field : stringFields) {

                    String value = getDBFFieldValue(file, defaultColumns.get(field), i, charset);
                    switch (field) {
                        case "nameCountry":
                        case "nameOriginCountry":
                            fieldValues.put(field, modifyNameCountry(value));
                            break;
                        case "valueVAT":
                            fieldValues.put(field, VATifAllowed(parseVAT(value)));
                            break;
                        case "barcodeItem":
                            fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7));
                            break;
                        case "idCustomerStock":
                            value = importSettings.getStockMapping().containsKey(value) ? importSettings.getStockMapping().get(value) : value;
                            fieldValues.put("idCustomerStock", value);
                            fieldValues.put("idCustomer", readIdCustomer(session, value));
                            break;
                        default:
                            fieldValues.put(field, value);
                            break;
                    }

                }

                for (String field : bigDecimalFields) {
                    ImportColumnDetail column = defaultColumns.get(field);
                    BigDecimal value = getDBFBigDecimalFieldValue(file, column, i, charset);
                    switch (field) {
                        case "sumVAT":
                            fieldValues.put(field, value == null && column != null ? BigDecimal.ZERO : value);
                            break;
                        case "dataIndex":
                            fieldValues.put(field, value == null ? (primaryList.size() + secondaryList.size() + 1) : value.intValue());
                            break;
                        case "price":
                            fieldValues.put(field, value != null && value.compareTo(new BigDecimal("100000000000")) > 0 ? null : value);
                            break;
                        default:
                            fieldValues.put(field, value);
                            break;
                    }
                }

                for (String field : dateFields) {
                    switch (field) {
                        case "dateDocument":
                            Date dateDocument = getDBFDateFieldValue(file, defaultColumns.get(field), i, charset);
                            Date dateVAT = dateDocument == null ? currentDateDocument : dateDocument;
                            fieldValues.put(field, dateDocument);
                            fieldValues.put("dateVAT", dateVAT);
                            break;
                        case "expiryDate":
                            fieldValues.put(field, getDBFDateFieldValue(file, defaultColumns.get(field), i, charset, true));
                            break;
                        default:
                            fieldValues.put(field, getDBFDateFieldValue(file, defaultColumns.get(field), i, charset));
                            break;
                    }
                }

                for (String field : timeFields) {
                    fieldValues.put(field, getDBFTimeFieldValue(file, defaultColumns.get(field), i, charset));
                }

                String numberDocument = getDBFFieldValue(file, defaultColumns.get("numberDocument"), i, charset);
                String idDocument = getDBFFieldValue(file, defaultColumns.get("idDocument"), i, charset, numberDocument);
                String idUserInvoiceDetail = makeIdUserInvoiceDetail(idDocument, userInvoiceObject, i);
                BigDecimal quantity = getDBFBigDecimalFieldValue(file, defaultColumns.get("quantity"), i, charset);
                BigDecimal netWeight = getDBFBigDecimalFieldValue(file, defaultColumns.get("netWeight"), i, charset);
                BigDecimal netWeightSum = getDBFBigDecimalFieldValue(file, defaultColumns.get("netWeightSum"), i, charset);
                netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
                BigDecimal grossWeight = getDBFBigDecimalFieldValue(file, defaultColumns.get("grossWeight"), i, charset);
                BigDecimal grossWeightSum = getDBFBigDecimalFieldValue(file, defaultColumns.get("grossWeightSum"), i, charset);
                grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;

                LinkedHashMap<String, String> customValues = new LinkedHashMap<>();
                for (Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                    customValues.put(column.getKey(), getDBFFieldValue(file, column.getValue(), i, charset));
                }

                if (checkInvoice(purchaseInvoiceSet, idDocument, checkInvoiceExistence)) {
                    PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(customValues, fieldValues, importSettings.isPosted(),
                            idDocument, numberDocument, idUserInvoiceDetail, quantity, netWeight, netWeightSum, grossWeight, grossWeightSum);

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

        } finally {
            if(file != null)
                file.close();
            if(tempFile != null)
                tempFile.delete();
        }
       
        return checkArticles(context, session, importSettings.getPropertyImportType(), staticNameImportType, staticCaptionImportType, 
                primaryList, secondaryList) ? Arrays.asList(primaryList, secondaryList) : null;
    }

    private boolean checkInvoice(Set<String> invoiceSet, String idInvoice, boolean checkInvoiceExistence) {
        return !checkInvoiceExistence || !invoiceSet.contains(idInvoice);
    }

    private boolean checkArticles(ExecutionContext context, DataSession session, String propertyImportType, String staticNameImportType, 
                                  String staticCaptionImportType, List<PurchaseInvoiceDetail> primaryList, List<PurchaseInvoiceDetail> secondaryList)
            throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        if (propertyImportType != null) {
            LCP<?> sidProp = (LCP)context.getBL().findSafeProperty(propertyImportType);
            if (sidProp != null) {
                ScriptingLogicsModule itemArticleLM = context.getBL().getModule("ItemArticle");
                LCP<?> idArticleProp = itemArticleLM.findProperty("idArticle");

                List<Object> articles = getArticlesMap(session, idArticleProp, sidProp);
                Set<String> articleSet = (Set<String>) articles.get(0);
                Map<String, String> articlePropertyMap = (Map<String, String>) articles.get(1);
                Map<String, Object[]> duplicateArticles = new HashMap<>();
                primaryList.addAll(secondaryList);
                for (PurchaseInvoiceDetail invoiceDetail : primaryList) {
                    String oldPropertyArticle = articlePropertyMap.get(invoiceDetail.getFieldValue("idArticle"));
                    Object propertyValue = getField(invoiceDetail, staticNameImportType, staticCaptionImportType);
                    if (propertyValue != null && oldPropertyArticle != null && !oldPropertyArticle.equals(propertyValue) 
                            && !(propertyValue.toString().contains(oldPropertyArticle)) && !duplicateArticles.containsKey(invoiceDetail.getFieldValue("idArticle"))) {
                        duplicateArticles.put((String) invoiceDetail.getFieldValue("idArticle"), new Object[]{oldPropertyArticle, propertyValue});
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
                        idArticleProp.change(entry.getValue(), context, (DataObject) itemArticleLM.findProperty("articleId").readClasses(context, new DataObject(entry.getKey())));
                    }
                }
            }
        }
        return true;
    }
    
    private List<Object> getArticlesMap(DataSession session, LCP<?> idArticleProp, LCP<?> sidProperty)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {        
        
        Set<String> articleSet = new HashSet<>();
        Map<String, String> articlePropertyMap = new HashMap<>();

        KeyExpr articleExpr = new KeyExpr("Article");
        ImRevMap<Object, KeyExpr> articleKeys = MapFact.singletonRev((Object) "Article", articleExpr);

        QueryBuilder<Object, Object> articleQuery = new QueryBuilder<>(articleKeys);
        articleQuery.addProperty("idArticle", idArticleProp.getExpr(session.getModifier(), articleExpr));
        articleQuery.addProperty("sid", sidProperty.getExpr(session.getModifier(), articleExpr));
        articleQuery.and(idArticleProp.getExpr(session.getModifier(), articleExpr).getWhere());
        
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> articleResult = articleQuery.execute(session);

        for (ImMap<Object, Object> entry : articleResult.values()) {

            String idArticle = (String) entry.get("idArticle");
            String property = (String) entry.get("sid");
            if(property != null)
                articlePropertyMap.put(idArticle, property);
            articleSet.add(idArticle);           
        }
        return Arrays.asList(articleSet, articlePropertyMap);
    }

    protected Set<String> getPurchaseInvoiceSet(DataSession session, boolean checkInvoiceExistence) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if(!checkInvoiceExistence)
            return null;

        Set<String> purchaseInvoiceSet = new HashSet<>();

        KeyExpr key = new KeyExpr("purchase.invoice");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "Purchase.Invoice", key);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

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

    private Object getField(PurchaseInvoiceDetail purchaseInvoiceDetail, String fieldName, String fieldCaption) {
        try {
            //custom fields
            if(fieldName == null && fieldCaption != null)
                return purchaseInvoiceDetail.customValues.get(fieldCaption);
            //default fields
            else if (purchaseInvoiceDetail.fieldValues != null && purchaseInvoiceDetail.fieldValues.containsKey(fieldName))
                return purchaseInvoiceDetail.fieldValues.get(fieldName);
            //purchaseInvoiceDetail fields
            else {
                Field field = PurchaseInvoiceDetail.class.getField(fieldName);
                return field.get(purchaseInvoiceDetail);
            }
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
    
    private String readIdCustomer(DataSession session, String idCustomerStock) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ObjectValue customerStockObject = idCustomerStock == null ? null : findProperty("stockId").readClasses(session, new DataObject(idCustomerStock));
        ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : findProperty("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
        return (String) (customerObject == null ? null : findProperty("idLegalEntity").read(session, customerObject));
    }
}

