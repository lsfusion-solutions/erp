package lsfusion.erp.integration.universal;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.stock.BarcodeUtils;
import lsfusion.interop.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.Settings;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.classes.DateClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
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

public class ImportPurchaseInvoiceActionProperty extends ImportDocumentActionProperty {
    private final ClassPropertyInterface userInvoiceInterface;

    String defaultCountry = "БЕЛАРУСЬ";
    
    // Опциональные модули
    ScriptingLogicsModule purchaseManufacturingPriceLM;
    ScriptingLogicsModule itemPharmacyByLM;
    ScriptingLogicsModule purchaseInvoicePharmacyLM;
    ScriptingLogicsModule itemArticleLM;
    ScriptingLogicsModule itemFashionLM;
    ScriptingLogicsModule taxItemLM;
    ScriptingLogicsModule customsGroupArticleLM;
    ScriptingLogicsModule purchaseShipmentBoxLM;
    ScriptingLogicsModule purchaseComplianceLM;
    ScriptingLogicsModule purchaseDeclarationLM;
    ScriptingLogicsModule purchaseDeclarationDetailLM;
    ScriptingLogicsModule purchaseShipmentLM;

    public ImportPurchaseInvoiceActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Purchase.UserInvoice"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        userInvoiceInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataSession session = context.getSession();

            initModules(context);

            DataObject userInvoiceObject = context.getDataKeyValue(userInvoiceInterface);

            boolean disableVolatileStats = Settings.get().isDisableExplicitVolatileStats();

            ObjectValue importTypeObject = getLCP("importTypeUserInvoice").readClasses(session, userInvoiceObject);

            if (!(importTypeObject instanceof NullValue)) {

                String fileExtension = trim((String) getLCP("captionFileExtensionImportType").read(session, importTypeObject));

                ObjectValue operationObject = getLCP("autoImportOperationImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierObject = getLCP("autoImportSupplierImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierStockObject = getLCP("autoImportSupplierStockImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerObject = getLCP("autoImportCustomerImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerStockObject = getLCP("autoImportCustomerStockImportType").readClasses(session, (DataObject) importTypeObject);

                String nameFieldImportType = trim((String) getLCP("staticNameImportTypeDetailImportType").read(session, importTypeObject));
                String[] splittedFieldImportType = nameFieldImportType == null ? null : nameFieldImportType.split("\\.");
                String fieldImportType = splittedFieldImportType == null ? null : splittedFieldImportType[splittedFieldImportType.length - 1];
                
                List<LinkedHashMap<String, ImportColumnDetail>> importColumns = readImportColumns(session, LM, importTypeObject);
                Set<String> purchaseInvoiceSet = getPurchaseInvoiceSet(session, LM, false);

                ImportDocumentSettings importSettings = readImportDocumentSettings(session, importTypeObject);

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
                                        customerStockObject, disableVolatileStats);

                            if (userInvoiceDetailData != null && userInvoiceDetailData.size() >= 2)
                                importUserInvoices(userInvoiceDetailData.get(1), context, session, importColumns.get(0),
                                        importColumns.get(1), userInvoiceObject, importSettings.getSecondaryKeyType(),
                                        operationObject, supplierObject, supplierStockObject, customerObject,
                                        customerStockObject, disableVolatileStats);

                            session.apply(context);

                            getLAP("formRefresh").execute(context);
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

        initModules(context);
        
        boolean disableVolatileStats = Settings.get().isDisableExplicitVolatileStats();

        List<LinkedHashMap<String, ImportColumnDetail>> importColumns = readImportColumns(session, LM, importTypeObject);
        Set<String> purchaseInvoiceSet = getPurchaseInvoiceSet(session, LM, checkInvoiceExistence);

        ObjectValue operationObject = getLCP("autoImportOperationImportType").readClasses(context, (DataObject) importTypeObject);
        ObjectValue supplierObject = getLCP("autoImportSupplierImportType").readClasses(context, (DataObject) importTypeObject);
        ObjectValue supplierStockObject = getLCP("autoImportSupplierStockImportType").readClasses(context, (DataObject) importTypeObject);
        ObjectValue customerObject = getLCP("autoImportCustomerImportType").readClasses(context, (DataObject) importTypeObject);
        ObjectValue customerStockObject = getLCP("autoImportCustomerStockImportType").readClasses(context, (DataObject) importTypeObject);

        List<List<PurchaseInvoiceDetail>> userInvoiceDetailData = importUserInvoicesFromFile(context, session,
                userInvoiceObject, importColumns.get(0), importColumns.get(1), purchaseInvoiceSet, checkInvoiceExistence, file, fileExtension,
                importSettings, staticNameImportType);

        int result1 = (userInvoiceDetailData == null || userInvoiceDetailData.size() < 1) ? IMPORT_RESULT_EMPTY :
            importUserInvoices(userInvoiceDetailData.get(0), context, session, importColumns.get(0), importColumns.get(1),
                    userInvoiceObject, importSettings.getPrimaryKeyType(), operationObject, supplierObject,
                    supplierStockObject, customerObject, customerStockObject, disableVolatileStats);

        int result2 = (userInvoiceDetailData == null || userInvoiceDetailData.size() < 2) ? IMPORT_RESULT_EMPTY :
            importUserInvoices(userInvoiceDetailData.get(1), context, session, importColumns.get(0), importColumns.get(1),
                    userInvoiceObject, importSettings.getSecondaryKeyType(), operationObject, supplierObject,
                    supplierStockObject, customerObject, customerStockObject, disableVolatileStats);
        
        return (result1==IMPORT_RESULT_ERROR || result2==IMPORT_RESULT_ERROR) ? IMPORT_RESULT_ERROR : (result1 + result2);
    }

    public void initModules(ExecutionContext context) {
        this.purchaseManufacturingPriceLM = (ScriptingLogicsModule) context.getBL().getModule("PurchaseManufacturingPrice");
        this.itemPharmacyByLM = (ScriptingLogicsModule) context.getBL().getModule("ItemPharmacyBy");
        this.purchaseInvoicePharmacyLM = (ScriptingLogicsModule) context.getBL().getModule("PurchaseInvoicePharmacy");
        this.itemArticleLM = (ScriptingLogicsModule) context.getBL().getModule("ItemArticle");
        this.itemFashionLM = (ScriptingLogicsModule) context.getBL().getModule("ItemFashion");
        this.taxItemLM = (ScriptingLogicsModule) context.getBL().getModule("TaxItem");
        this.customsGroupArticleLM = (ScriptingLogicsModule) context.getBL().getModule("CustomsGroupArticle");
        this.purchaseShipmentBoxLM = (ScriptingLogicsModule) context.getBL().getModule("PurchaseShipmentBox");
        this.purchaseComplianceLM = (ScriptingLogicsModule) context.getBL().getModule("PurchaseCompliance");
        this.purchaseDeclarationLM = (ScriptingLogicsModule) context.getBL().getModule("PurchaseDeclaration");
        this.purchaseDeclarationDetailLM = (ScriptingLogicsModule) context.getBL().getModule("PurchaseDeclarationDetail");
        this.purchaseShipmentLM = (ScriptingLogicsModule) context.getBL().getModule("PurchaseShipment");
    }

    public int importUserInvoices(List<PurchaseInvoiceDetail> userInvoiceDetailsList, ExecutionContext context, DataSession session,
                                      LinkedHashMap<String, ImportColumnDetail> defaultColumns, LinkedHashMap<String, ImportColumnDetail> customColumns,
                                      DataObject userInvoiceObject, String keyType, ObjectValue operationObject,
                                      ObjectValue supplierObject, ObjectValue supplierStockObject, ObjectValue customerObject,
                                      ObjectValue customerStockObject, boolean disableVolatileStats)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException, SQLHandledException {


        if (userInvoiceDetailsList != null && !userInvoiceDetailsList.isEmpty()) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(userInvoiceDetailsList.size());

            if (showField(userInvoiceDetailsList, "idUserInvoice")) {
                ImportField idUserInvoiceField = new ImportField(getLCP("idUserInvoice"));
                props.add(new ImportProperty(idUserInvoiceField, getLCP("idUserInvoice").getMapping(userInvoiceObject), getReplaceOnlyNull(defaultColumns, "idUserInvoice")));
                fields.add(idUserInvoiceField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "numberUserInvoice")) {
                    addDataField(props, fields, defaultColumns, "numberUserInvoice", "numberUserInvoice", userInvoiceObject);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).numberUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "dateUserInvoice")) {
                addDataField(props, fields, defaultColumns, "dateUserInvoice", "dateUserInvoice", userInvoiceObject);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).dateUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "timeUserInvoice")) {
                addDataField(props, fields, defaultColumns, "timeUserInvoice", "timeUserInvoice", userInvoiceObject);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).timeUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "currencyUserInvoice")) {
                ImportField shortNameCurrencyField = new ImportField(getLCP("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) getClass("Currency"),
                        getLCP("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, getLCP("currencyUserInvoice").getMapping(userInvoiceObject),
                            LM.object(getClass("Currency")).getMapping(currencyKey), getReplaceOnlyNull(defaultColumns, "currencyUserInvoice")));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).currencyUserInvoice);
            }

            ImportField idUserInvoiceDetailField = new ImportField(getLCP("idUserInvoiceDetail"));
            ImportKey<?> userInvoiceDetailKey = new ImportKey((ConcreteCustomClass) getClass("Purchase.UserInvoiceDetail"),
                    getLCP("userInvoiceDetailId").getMapping(idUserInvoiceDetailField));
            keys.add(userInvoiceDetailKey);
            props.add(new ImportProperty(idUserInvoiceDetailField, getLCP("idUserInvoiceDetail").getMapping(userInvoiceDetailKey), getReplaceOnlyNull(defaultColumns, "idUserInvoiceDetail")));
            props.add(new ImportProperty(userInvoiceObject, getLCP("Purchase.userInvoiceUserInvoiceDetail").getMapping(userInvoiceDetailKey), getReplaceOnlyNull(defaultColumns, "idUserInvoiceDetail")));
            fields.add(idUserInvoiceDetailField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idUserInvoiceDetail);

            if (operationObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) operationObject, getLCP("Purchase.operationUserInvoice").getMapping(userInvoiceObject)));
            }

            if (supplierObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierObject, getLCP("Purchase.supplierUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) supplierObject, getLCP("Purchase.supplierUserInvoice").getMapping(userInvoiceObject)));
            }

            if (showField(userInvoiceDetailsList, "idSupplier")) {

                ImportField idSupplierField = new ImportField(getLCP("idLegalEntity"));
                ImportKey<?> supplierKey = new ImportKey((ConcreteCustomClass) getClass("LegalEntity"),
                        getLCP("legalEntityId").getMapping(idSupplierField));
                keys.add(supplierKey);
                props.add(new ImportProperty(idSupplierField, getLCP("supplierUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(getClass("LegalEntity")).getMapping(supplierKey), getReplaceOnlyNull(defaultColumns, "idSupplier")));
                    props.add(new ImportProperty(idSupplierField, getLCP("supplierUserInvoice").getMapping(userInvoiceObject),
                            LM.object(getClass("LegalEntity")).getMapping(supplierKey), getReplaceOnlyNull(defaultColumns, "idSupplier")));
                fields.add(idSupplierField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idSupplier);

            }

            if (showField(userInvoiceDetailsList, "idSupplierStock")) {

                ImportField idSupplierStockField = new ImportField(getLCP("idStock"));
                ImportKey<?> supplierStockKey = new ImportKey((CustomClass) getClass("Stock"),
                        getLCP("stockId").getMapping(idSupplierStockField));
                keys.add(supplierStockKey);
                props.add(new ImportProperty(idSupplierStockField, getLCP("supplierStockUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(getClass("Stock")).getMapping(supplierStockKey), getReplaceOnlyNull(defaultColumns, "idSupplierStock")));
                    props.add(new ImportProperty(idSupplierStockField, getLCP("supplierStockUserInvoice").getMapping(userInvoiceObject),
                            LM.object(getClass("Stock")).getMapping(supplierStockKey), getReplaceOnlyNull(defaultColumns, "idSupplierStock")));
                fields.add(idSupplierStockField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idSupplierStock);

            }

            if (supplierStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierStockObject, getLCP("Purchase.supplierStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) supplierStockObject, getLCP("Purchase.supplierStockUserInvoice").getMapping(userInvoiceObject)));
            }

            if (customerObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerObject, getLCP("Purchase.customerUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) customerObject, getLCP("Purchase.customerUserInvoice").getMapping(userInvoiceObject)));
            }

            if (customerStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerStockObject, getLCP("Purchase.customerStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) customerStockObject, getLCP("Purchase.customerStockUserInvoice").getMapping(userInvoiceObject)));
            }

            ImportField idBarcodeSkuField = new ImportField(getLCP("idBarcodeSku"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) getClass("Barcode"),
                    getLCP("extBarcodeId").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idBarcodeSkuField, getLCP("idBarcode").getMapping(barcodeKey), getReplaceOnlyNull(defaultColumns, "barcodeItem")));
            props.add(new ImportProperty(idBarcodeSkuField, getLCP("extIdBarcode").getMapping(barcodeKey), getReplaceOnlyNull(defaultColumns, "barcodeItem")));
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idBarcodeSku);

            ImportField idBatchField = new ImportField(getLCP("idBatch"));
            ImportKey<?> batchKey = new ImportKey((CustomClass) getClass("Batch"),
                    getLCP("batchId").getMapping(idBatchField));
            props.add(new ImportProperty(idBatchField, getLCP("idBatch").getMapping(batchKey), getReplaceOnlyNull(defaultColumns, "idBatch")));
            props.add(new ImportProperty(idBatchField, getLCP("idBatchUserInvoiceDetail").getMapping(userInvoiceDetailKey), getReplaceOnlyNull(defaultColumns, "idBatch")));
            fields.add(idBatchField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idBatch);

            if (purchaseShipmentBoxLM != null && showField(userInvoiceDetailsList, "idBox")) {
                ImportField idBoxField = new ImportField(purchaseShipmentBoxLM.findLCPByCompoundOldName("idBox"));
                ImportKey<?> boxKey = new ImportKey((ConcreteCustomClass) purchaseShipmentBoxLM.findClassByCompoundName("Box"),
                        purchaseShipmentBoxLM.findLCPByCompoundOldName("boxId").getMapping(idBoxField));
                keys.add(boxKey);
                props.add(new ImportProperty(idBoxField, purchaseShipmentBoxLM.findLCPByCompoundOldName("idBox").getMapping(boxKey), getReplaceOnlyNull(defaultColumns, "idBox")));
                props.add(new ImportProperty(idBoxField, purchaseShipmentBoxLM.findLCPByCompoundOldName("boxUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        purchaseShipmentBoxLM.object(purchaseShipmentBoxLM.findClassByCompoundName("Box")).getMapping(boxKey), getReplaceOnlyNull(defaultColumns, "idBox")));
                fields.add(idBoxField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idBox);

                if (showField(userInvoiceDetailsList, "nameBox")) {
                    addDataField(purchaseShipmentBoxLM, props, fields, defaultColumns, "nameBox", "nameBox", boxKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameBox);
                }
            }

            ImportField dataIndexUserInvoiceDetailField = new ImportField(getLCP("dataIndexUserInvoiceDetail"));
            props.add(new ImportProperty(dataIndexUserInvoiceDetailField, getLCP("dataIndexUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(dataIndexUserInvoiceDetailField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).dataIndex);

            ImportField idItemField = new ImportField(getLCP("idItem"));
            fields.add(idItemField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idItem);

            String replaceField = (keyType == null || keyType.equals("item")) ? "idItem" : keyType.equals("barcode") ? "barcodeItem" : "idBatch";
            String iGroupAggr = getItemKeyGroupAggr(keyType);
            ImportField iField = (keyType == null || keyType.equals("item")) ? idItemField : keyType.equals("barcode") ? idBarcodeSkuField : idBatchField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) getClass("Item"),
                    getLCP(iGroupAggr).getMapping(iField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, getLCP("idItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "idItem")));
            props.add(new ImportProperty(iField, getLCP("Purchase.skuInvoiceDetail").getMapping(userInvoiceDetailKey),
                    LM.object(getClass("Sku")).getMapping(itemKey), getReplaceOnlyNull(defaultColumns, replaceField)));
            props.add(new ImportProperty(iField, getLCP("skuBarcode").getMapping(barcodeKey),
                    LM.object(getClass("Item")).getMapping(itemKey), getReplaceOnlyNull(defaultColumns, replaceField)));

            if (showField(userInvoiceDetailsList, "captionItem")) {
                addDataField(props, fields, defaultColumns, "captionItem", "captionItem", itemKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).captionItem);
            }

            if (showField(userInvoiceDetailsList, "originalCaptionItem")) {
                addDataField(props, fields, defaultColumns, "originalCaptionItem", "originalCaptionItem", itemKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).originalCaptionItem);
            }

            if (showField(userInvoiceDetailsList, "idUOM")) {
                ImportField idUOMField = new ImportField(getLCP("idUOM"));
                ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) getClass("UOM"),
                        getLCP("UOMId").getMapping(idUOMField));
                keys.add(UOMKey);
                props.add(new ImportProperty(idUOMField, getLCP("idUOM").getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                props.add(new ImportProperty(idUOMField, getLCP("nameUOM").getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                props.add(new ImportProperty(idUOMField, getLCP("shortNameUOM").getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                props.add(new ImportProperty(idUOMField, getLCP("UOMItem").getMapping(itemKey),
                        LM.object(getClass("UOM")).getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                fields.add(idUOMField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idUOM);
            }

            if (showField(userInvoiceDetailsList, "idManufacturer")) {
                ImportField idManufacturerField = new ImportField(getLCP("idManufacturer"));
                ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) getClass("Manufacturer"),
                        getLCP("manufacturerId").getMapping(idManufacturerField));
                keys.add(manufacturerKey);
                props.add(new ImportProperty(idManufacturerField, getLCP("idManufacturer").getMapping(manufacturerKey), getReplaceOnlyNull(defaultColumns, "idManufacturer")));
                props.add(new ImportProperty(idManufacturerField, getLCP("manufacturerItem").getMapping(itemKey),
                        LM.object(getClass("Manufacturer")).getMapping(manufacturerKey), getReplaceOnlyNull(defaultColumns, "idManufacturer")));
                fields.add(idManufacturerField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idManufacturer);

                if (showField(userInvoiceDetailsList, "nameManufacturer")) {
                    addDataField(props, fields, defaultColumns, "nameManufacturer", "nameManufacturer", manufacturerKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameManufacturer);
                }
            }

            ImportField sidOrigin2CountryField = new ImportField(getLCP("sidOrigin2Country"));
            ImportField nameCountryField = new ImportField(getLCP("nameCountry"));
            ImportField nameOriginCountryField = new ImportField(getLCP("nameOriginCountry"));

            boolean showSidOrigin2Country = showField(userInvoiceDetailsList, "sidOrigin2Country");
            boolean showNameCountry = showField(userInvoiceDetailsList, "nameCountry");
            boolean showNameOriginCountry = showField(userInvoiceDetailsList, "nameOriginCountry");

            ImportField countryField = showSidOrigin2Country ? sidOrigin2CountryField :
                    (showNameCountry ? nameCountryField : (showNameOriginCountry ? nameOriginCountryField : null));
            String countryAggr = showSidOrigin2Country ? "countrySIDOrigin2" :
                    (showNameCountry ? "countryName" : (showNameOriginCountry ? "countryNameOrigin" : null));
            String countryReplaceField = showSidOrigin2Country ? "sidOrigin2Country" :
                    (showNameCountry ? "nameCountry" : (showNameOriginCountry ? "nameOriginCountry" : null));
            ImportKey<?> countryKey = countryField == null ? null : 
                    new ImportKey((ConcreteCustomClass) getClass("Country"), getLCP(countryAggr).getMapping(countryField));

            if (countryKey != null) {
                keys.add(countryKey);

                props.add(new ImportProperty(countryField, getLCP("countryItem").getMapping(itemKey),
                        LM.object(getClass("Country")).getMapping(countryKey), getReplaceOnlyNull(defaultColumns, countryReplaceField)));

                if (showSidOrigin2Country) {
                    props.add(new ImportProperty(sidOrigin2CountryField, getLCP("sidOrigin2Country").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "sidOrigin2Country")));
                    fields.add(sidOrigin2CountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).sidOrigin2Country);
                }
                if (showNameCountry) {
                    props.add(new ImportProperty(nameCountryField, getLCP("nameCountry").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "nameCountry")));
                    fields.add(nameCountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameCountry);
                }
                if (showNameOriginCountry) {
                    props.add(new ImportProperty(nameOriginCountryField, getLCP("nameOriginCountry").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "nameOriginCountry")));
                    fields.add(nameOriginCountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameOriginCountry);
                }
            }

            if (showField(userInvoiceDetailsList, "idCustomer")) {
                ImportField idCustomerField = new ImportField(getLCP("idLegalEntity"));
                ImportKey<?> customerKey = new ImportKey((ConcreteCustomClass) getClass("LegalEntity"),
                        getLCP("legalEntityId").getMapping(idCustomerField));
                keys.add(customerKey);
                    props.add(new ImportProperty(idCustomerField, getLCP("Purchase.customerUserInvoice").getMapping(userInvoiceObject),
                            LM.object(getClass("LegalEntity")).getMapping(customerKey), getReplaceOnlyNull(defaultColumns, "idBCustomer")));
                fields.add(idCustomerField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idCustomer);
            }

            if (showField(userInvoiceDetailsList, "idCustomerStock")) {
                ImportField idCustomerStockField = new ImportField(getLCP("idStock"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) getClass("Stock"),
                        getLCP("stockId").getMapping(idCustomerStockField));
                keys.add(customerStockKey);
                    props.add(new ImportProperty(idCustomerStockField, getLCP("Purchase.customerStockUserInvoice").getMapping(userInvoiceObject),
                            LM.object(getClass("Stock")).getMapping(customerStockKey), getReplaceOnlyNull(defaultColumns, "idCustomerStock")));
                fields.add(idCustomerStockField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idCustomerStock);
            }

            if (showField(userInvoiceDetailsList, "quantity")) {
                addDataField(props, fields, defaultColumns, "Purchase.quantityUserInvoiceDetail", "quantity", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).quantity);
            }

            if (showField(userInvoiceDetailsList, "price")) {
                addDataField(props, fields, defaultColumns, "Purchase.priceUserInvoiceDetail", "price", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).price);
            }

            if (showField(userInvoiceDetailsList, "sum")) {
                addDataField(props, fields, defaultColumns, "Purchase.sumUserInvoiceDetail", "sum", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).sum);
            }

            if (showField(userInvoiceDetailsList, "valueVAT")) {
                ImportField valueVATUserInvoiceDetailField = new ImportField(getLCP("Purchase.valueVATUserInvoiceDetail"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) getClass("Range"),
                        getLCP("valueCurrentVATDefaultValue").getMapping(valueVATUserInvoiceDetailField));
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATUserInvoiceDetailField, getLCP("Purchase.VATUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(getClass("Range")).getMapping(VATKey), getReplaceOnlyNull(defaultColumns, "valueVAT")));
                fields.add(valueVATUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).valueVAT);

                ImportField dateField = new ImportField(DateClass.instance);
                props.add(new ImportProperty(dateField, LM.findLCPByCompoundOldName("dataDateBarcode").getMapping(barcodeKey), true));
                fields.add(dateField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).dateVAT);

                if(taxItemLM != null) {
                ImportField countryVATField = new ImportField(taxItemLM.findLCPByCompoundOldName("nameCountry"));
                ImportKey<?> countryVATKey = new ImportKey((ConcreteCustomClass) taxItemLM.findClassByCompoundName("Country"),
                        taxItemLM.findLCPByCompoundOldName("countryName").getMapping(countryVATField));
                keys.add(countryVATKey);
                props.add(new ImportProperty(valueVATUserInvoiceDetailField, taxItemLM.findLCPByCompoundOldName("VATItemCountry").getMapping(itemKey, countryVATKey),
                        LM.object(taxItemLM.findClassByCompoundName("Range")).getMapping(VATKey), getReplaceOnlyNull(defaultColumns, "valueVAT")));
                fields.add(countryVATField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).countryVAT);
                }
            }

            if (showField(userInvoiceDetailsList, "sumVAT")) {
                addDataField(props, fields, defaultColumns, "Purchase.VATSumUserInvoiceDetail", "sumVAT", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).sumVAT);
            }

            if (showField(userInvoiceDetailsList, "invoiceSum")) {
                addDataField(props, fields, defaultColumns, "Purchase.invoiceSumUserInvoiceDetail", "invoiceSum", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).invoiceSum);
            }

            if (purchaseDeclarationLM != null) {
                
                if (showField(userInvoiceDetailsList, "numberDeclaration")) {
                    ImportField numberDeclarationField = new ImportField(purchaseDeclarationLM.findLCPByCompoundOldName("numberDeclaration"));
                    ImportKey<?> declarationKey = new ImportKey((ConcreteCustomClass) purchaseDeclarationLM.findClassByCompoundName("Declaration"),
                            purchaseDeclarationLM.findLCPByCompoundOldName("declarationId").getMapping(numberDeclarationField));
                    keys.add(declarationKey);
                    props.add(new ImportProperty(numberDeclarationField, purchaseDeclarationLM.findLCPByCompoundOldName("numberDeclaration").getMapping(declarationKey), getReplaceOnlyNull(defaultColumns, "numberDeclaration")));
                    props.add(new ImportProperty(numberDeclarationField, purchaseDeclarationLM.findLCPByCompoundOldName("idDeclaration").getMapping(declarationKey), getReplaceOnlyNull(defaultColumns, "numberDeclaration")));
                    props.add(new ImportProperty(numberDeclarationField, purchaseDeclarationLM.findLCPByCompoundOldName("declarationUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                            LM.object(purchaseDeclarationLM.findClassByCompoundName("Declaration")).getMapping(declarationKey), getReplaceOnlyNull(defaultColumns, "numberDeclaration")));
                    fields.add(numberDeclarationField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).numberDeclaration);
                }
                
            }

            if (purchaseShipmentLM != null) {

                if (showField(userInvoiceDetailsList, "expiryDate")) {
                    addDataField(purchaseShipmentLM, props, fields, defaultColumns, "Purchase.expiryDateUserInvoiceDetail", "expiryDate", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).expiryDate);
                }

                if (showField(userInvoiceDetailsList, "manufactureDate")) {
                    addDataField(purchaseShipmentLM, props, fields, defaultColumns, "Purchase.manufactureDateUserInvoiceDetail", "manufactureDate", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).manufactureDate);
                }

                if (showField(userInvoiceDetailsList, "shipmentPrice")) {
                    addDataField(purchaseShipmentLM, props, fields, defaultColumns, "shipmentPriceUserInvoiceDetail", "shipmentPrice", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).shipmentPrice);
                }

                if (showField(userInvoiceDetailsList, "shipmentSum")) {
                    addDataField(purchaseShipmentLM, props, fields, defaultColumns, "shipmentSumUserInvoiceDetail", "shipmentSum", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).shipmentSum);
                }
                
            }
                
            if ((purchaseManufacturingPriceLM != null) && showField(userInvoiceDetailsList, "manufacturingPrice")) {
                addDataField(purchaseManufacturingPriceLM, props, fields, defaultColumns, "Purchase.manufacturingPriceUserInvoiceDetail", "manufacturingPrice", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).manufacturingPrice);
            }

            if (itemPharmacyByLM != null && showField(userInvoiceDetailsList, "idPharmacyPriceGroup")) {
                ImportField idPharmacyPriceGroupField = new ImportField(itemPharmacyByLM.findLCPByCompoundOldName("idPharmacyPriceGroup"));
                ImportKey<?> pharmacyPriceGroupKey = new ImportKey((ConcreteCustomClass) itemPharmacyByLM.findClassByCompoundName("PharmacyPriceGroup"),
                        itemPharmacyByLM.findLCPByCompoundOldName("pharmacyPriceGroupId").getMapping(idPharmacyPriceGroupField));
                keys.add(pharmacyPriceGroupKey);
                props.add(new ImportProperty(idPharmacyPriceGroupField, itemPharmacyByLM.findLCPByCompoundOldName("idPharmacyPriceGroup").getMapping(pharmacyPriceGroupKey), getReplaceOnlyNull(defaultColumns, "idPharmacyPriceGroup")));
                props.add(new ImportProperty(idPharmacyPriceGroupField, itemPharmacyByLM.findLCPByCompoundOldName("namePharmacyPriceGroup").getMapping(pharmacyPriceGroupKey), getReplaceOnlyNull(defaultColumns, "idPharmacyPriceGroup")));
                props.add(new ImportProperty(idPharmacyPriceGroupField, itemPharmacyByLM.findLCPByCompoundOldName("pharmacyPriceGroupItem").getMapping(itemKey),
                        LM.object(itemPharmacyByLM.findClassByCompoundName("PharmacyPriceGroup")).getMapping(pharmacyPriceGroupKey), getReplaceOnlyNull(defaultColumns, "idPharmacyPriceGroup")));
                fields.add(idPharmacyPriceGroupField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idPharmacyPriceGroup);
            }

            if (purchaseInvoicePharmacyLM != null) {

                if (showField(userInvoiceDetailsList, "nameImportCountry")) {
                    ImportField nameImportCountryField = new ImportField(purchaseInvoicePharmacyLM.findLCPByCompoundOldName("nameCountry"));
                    ImportKey<?> importCountryKey = new ImportKey((ConcreteCustomClass) purchaseInvoicePharmacyLM.findClassByCompoundName("Country"),
                            purchaseInvoicePharmacyLM.findLCPByCompoundOldName("countryName").getMapping(nameImportCountryField));
                    keys.add(importCountryKey);
                    props.add(new ImportProperty(nameImportCountryField, purchaseInvoicePharmacyLM.findLCPByCompoundOldName("nameCountry").getMapping(importCountryKey), getReplaceOnlyNull(defaultColumns, "nameImportCountry")));
                    props.add(new ImportProperty(nameImportCountryField, purchaseInvoicePharmacyLM.findLCPByCompoundOldName("importCountryUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                            LM.object(purchaseInvoicePharmacyLM.findClassByCompoundName("Country")).getMapping(importCountryKey), getReplaceOnlyNull(defaultColumns, "nameImportCountry")));
                    fields.add(nameImportCountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameImportCountry);
                }

                if (showField(userInvoiceDetailsList, "seriesPharmacy")) {
                    addDataField(purchaseInvoicePharmacyLM, props, fields, defaultColumns, "Purchase.seriesPharmacyUserInvoiceDetail", "seriesPharmacy", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).seriesPharmacy);
                }

                if (showField(userInvoiceDetailsList, "contractPrice")) {
                    addDataField(purchaseInvoicePharmacyLM, props, fields, defaultColumns, "contractPriceUserInvoiceDetail", "contractPrice", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).contractPrice);
                }
            }
            
            if (showField(userInvoiceDetailsList, "rateExchange")) {
                addDataField(props, fields, defaultColumns, "rateExchangeUserInvoiceDetail", "rateExchange", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).rateExchange);
            }

            if (purchaseDeclarationDetailLM != null) {

                if (showField(userInvoiceDetailsList, "sumNetWeight")) {
                    addDataField(purchaseDeclarationDetailLM, props, fields, defaultColumns, "sumNetWeightUserInvoiceDetail", "sumNetWeight", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).sumNetWeight);
                }

                if (showField(userInvoiceDetailsList, "sumGrossWeight")) {
                    addDataField(purchaseDeclarationDetailLM, props, fields, defaultColumns, "sumGrossWeightUserInvoiceDetail", "sumGrossWeight", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).sumGrossWeight);
                }
            }
            
            if (showField(userInvoiceDetailsList, "isPosted")) {
                    addDataField(props, fields, defaultColumns, "isPostedUserInvoice", "isPosted", userInvoiceObject);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).isPosted);
            }

            if (showField(userInvoiceDetailsList, "idItemGroup")) {
                ImportField idItemGroupField = new ImportField(getLCP("idItemGroup"));
                ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) getClass("ItemGroup"),
                        getLCP("itemGroupId").getMapping(idItemGroupField));
                keys.add(itemGroupKey);
                props.add(new ImportProperty(idItemGroupField, getLCP("idItemGroup").getMapping(itemGroupKey), getReplaceOnlyNull(defaultColumns, "idItemGroup")));
                props.add(new ImportProperty(idItemGroupField, getLCP("nameItemGroup").getMapping(itemGroupKey), true));
                props.add(new ImportProperty(idItemGroupField, getLCP("itemGroupItem").getMapping(itemKey),
                        LM.object(getClass("ItemGroup")).getMapping(itemGroupKey), getReplaceOnlyNull(defaultColumns, "idItemGroup")));
                fields.add(idItemGroupField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idItemGroup);
            }

            ImportKey<?> articleKey = null;
            if ((itemArticleLM != null)) {

                ImportField idArticleField = new ImportField(itemArticleLM.findLCPByCompoundOldName("idArticle"));
                articleKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Article"),
                        itemArticleLM.findLCPByCompoundOldName("articleId").getMapping(idArticleField));
                keys.add(articleKey);
                props.add(new ImportProperty(idArticleField, itemArticleLM.findLCPByCompoundOldName("idArticle").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "idArticle")));
                props.add(new ImportProperty(idArticleField, itemArticleLM.findLCPByCompoundOldName("articleItem").getMapping(itemKey),
                        LM.object(itemArticleLM.findClassByCompoundName("Article")).getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "idArticle")));
                fields.add(idArticleField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idArticle);

                if (showField(userInvoiceDetailsList, "idItemGroup")) {
                    ImportField idItemGroupField = new ImportField(getLCP("idItemGroup"));
                    ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) getClass("ItemGroup"),
                            getLCP("itemGroupId").getMapping(idItemGroupField));
                    keys.add(itemGroupKey);
                    itemGroupKey.skipKey = true;
                    props.add(new ImportProperty(idItemGroupField, itemArticleLM.findLCPByCompoundOldName("itemGroupArticle").getMapping(articleKey),
                            itemArticleLM.object(itemArticleLM.findClassByCompoundName("ItemGroup")).getMapping(itemGroupKey), getReplaceOnlyNull(defaultColumns, "idItemGroup")));
                    fields.add(idItemGroupField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idItemGroup);
                }

                if (showField(userInvoiceDetailsList, "captionArticle")) {
                    addDataField(itemArticleLM, props, fields, defaultColumns, "captionArticle", "captionArticle", articleKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).captionArticle);
                }

                if (showField(userInvoiceDetailsList, "originalCaptionArticle")) {
                    addDataField(itemArticleLM, props, fields, defaultColumns, "originalCaptionArticle", "originalCaptionArticle", articleKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).originalCaptionArticle);
                }

                if (showField(userInvoiceDetailsList, "netWeight")) {
                    ImportField netWeightField = new ImportField(itemArticleLM.findLCPByCompoundOldName("netWeightItem"));
                    props.add(new ImportProperty(netWeightField, itemArticleLM.findLCPByCompoundOldName("netWeightItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "netWeight")));
                    props.add(new ImportProperty(netWeightField, itemArticleLM.findLCPByCompoundOldName("netWeightArticle").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "netWeight")));
                    fields.add(netWeightField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).netWeight);
                }

                if (showField(userInvoiceDetailsList, "grossWeight")) {
                    ImportField grossWeightField = new ImportField(itemArticleLM.findLCPByCompoundOldName("grossWeightItem"));
                    props.add(new ImportProperty(grossWeightField, itemArticleLM.findLCPByCompoundOldName("grossWeightItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "grossWeight")));
                    props.add(new ImportProperty(grossWeightField, itemArticleLM.findLCPByCompoundOldName("grossWeightArticle").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "grossWeight")));
                    fields.add(grossWeightField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).grossWeight);
                }

                if (showField(userInvoiceDetailsList, "composition")) {
                    ImportField compositionField = new ImportField(itemArticleLM.findLCPByCompoundOldName("compositionItem"));
                    props.add(new ImportProperty(compositionField, itemArticleLM.findLCPByCompoundOldName("compositionItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "composition")));
                    props.add(new ImportProperty(compositionField, itemArticleLM.findLCPByCompoundOldName("compositionArticle").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "composition")));
                    fields.add(compositionField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).composition);
                }

                if (showField(userInvoiceDetailsList, "originalComposition")) {
                    ImportField originalCompositionField = new ImportField(itemArticleLM.findLCPByCompoundOldName("originalCompositionItem"));
                    props.add(new ImportProperty(originalCompositionField, itemArticleLM.findLCPByCompoundOldName("originalCompositionItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "originalComposition")));
                    props.add(new ImportProperty(originalCompositionField, itemArticleLM.findLCPByCompoundOldName("originalCompositionArticle").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "originalComposition")));
                    fields.add(originalCompositionField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).originalComposition);
                }

                if (customsGroupArticleLM != null && showField(userInvoiceDetailsList, "originalCustomsGroupItem")) {
                    ImportField originalCustomsGroupItemField = new ImportField(customsGroupArticleLM.findLCPByCompoundOldName("originalCustomsGroupItem"));
                    props.add(new ImportProperty(originalCustomsGroupItemField, customsGroupArticleLM.findLCPByCompoundOldName("originalCustomsGroupItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "originalCustomsGroupItem")));
                    props.add(new ImportProperty(originalCustomsGroupItemField, customsGroupArticleLM.findLCPByCompoundOldName("originalCustomsGroupArticle").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "originalCustomsGroupItem")));
                    fields.add(originalCustomsGroupItemField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).originalCustomsGroupItem);
                }

                if (showField(userInvoiceDetailsList, "idColor")) {
                    ImportField idColorField = new ImportField(itemArticleLM.findLCPByCompoundOldName("idColor"));
                    ImportKey<?> colorKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Color"),
                            itemArticleLM.findLCPByCompoundOldName("colorId").getMapping(idColorField));
                    keys.add(colorKey);
                    props.add(new ImportProperty(idColorField, itemArticleLM.findLCPByCompoundOldName("idColor").getMapping(colorKey), getReplaceOnlyNull(defaultColumns, "idColor")));
                    props.add(new ImportProperty(idColorField, itemArticleLM.findLCPByCompoundOldName("colorItem").getMapping(itemKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Color")).getMapping(colorKey), getReplaceOnlyNull(defaultColumns, "idColor")));
                    props.add(new ImportProperty(idColorField, itemArticleLM.findLCPByCompoundOldName("colorArticle").getMapping(articleKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Color")).getMapping(colorKey), getReplaceOnlyNull(defaultColumns, "idColor")));
                    fields.add(idColorField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idColor);

                    if (showField(userInvoiceDetailsList, "nameColor")) {
                        addDataField(itemArticleLM, props, fields, defaultColumns, "nameColor", "nameColor", colorKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameColor);
                    }
                }

                if (showField(userInvoiceDetailsList, "idSize")) {
                    ImportField idSizeField = new ImportField(itemArticleLM.findLCPByCompoundOldName("idSize"));
                    ImportKey<?> sizeKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Size"),
                            itemArticleLM.findLCPByCompoundOldName("sizeId").getMapping(idSizeField));
                    keys.add(sizeKey);
                    props.add(new ImportProperty(idSizeField, itemArticleLM.findLCPByCompoundOldName("idSize").getMapping(sizeKey), getReplaceOnlyNull(defaultColumns, "idSize")));
                    props.add(new ImportProperty(idSizeField, itemArticleLM.findLCPByCompoundOldName("sizeItem").getMapping(itemKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Size")).getMapping(sizeKey), getReplaceOnlyNull(defaultColumns, "idSize")));
                    fields.add(idSizeField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idSize);

                    if (showField(userInvoiceDetailsList, "nameSize")) {
                        addDataField(itemArticleLM, props, fields, defaultColumns, "nameSize", "nameSize", sizeKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameSize);

                        addDataField(itemArticleLM, props, fields, defaultColumns, "shortNameSize", "nameSize", sizeKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameSize);
                    }

                    if (showField(userInvoiceDetailsList, "nameOriginalSize")) {
                        addDataField(itemArticleLM, props, fields, defaultColumns, "nameOriginalSize", "nameOriginalSize", sizeKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameOriginalSize);
                    }
                }

                if (itemFashionLM != null && showField(userInvoiceDetailsList, "idSeasonYear")) {
                    ImportField idSeasonYearField = new ImportField(itemFashionLM.findLCPByCompoundOldName("idSeasonYear"));
                    ImportKey<?> seasonYearKey = new ImportKey((ConcreteCustomClass) itemFashionLM.findClassByCompoundName("SeasonYear"),
                            itemFashionLM.findLCPByCompoundOldName("seasonYearId").getMapping(idSeasonYearField));
                    keys.add(seasonYearKey);
                    props.add(new ImportProperty(idSeasonYearField, itemFashionLM.findLCPByCompoundOldName("idSeasonYear").getMapping(seasonYearKey), getReplaceOnlyNull(defaultColumns, "idSeasonYear")));
                    props.add(new ImportProperty(idSeasonYearField, itemFashionLM.findLCPByCompoundOldName("nameSeasonYear").getMapping(seasonYearKey), getReplaceOnlyNull(defaultColumns, "idSeasonYear")));
                    props.add(new ImportProperty(idSeasonYearField, itemFashionLM.findLCPByCompoundOldName("seasonYearArticle").getMapping(articleKey),
                            LM.object(itemFashionLM.findClassByCompoundName("SeasonYear")).getMapping(seasonYearKey), getReplaceOnlyNull(defaultColumns, "idSeasonYear")));
                    props.add(new ImportProperty(idSeasonYearField, itemFashionLM.findLCPByCompoundOldName("seasonYearItem").getMapping(itemKey),
                            LM.object(itemFashionLM.findClassByCompoundName("SeasonYear")).getMapping(seasonYearKey), getReplaceOnlyNull(defaultColumns, "idSeasonYear")));
                    fields.add(idSeasonYearField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idSeasonYear);
                }

                if (showField(userInvoiceDetailsList, "idBrand")) {
                    ImportField idBrandField = new ImportField(itemArticleLM.findLCPByCompoundOldName("idBrand"));
                    ImportKey<?> brandKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Brand"),
                            itemArticleLM.findLCPByCompoundOldName("brandId").getMapping(idBrandField));
                    keys.add(brandKey);
                    props.add(new ImportProperty(idBrandField, itemArticleLM.findLCPByCompoundOldName("idBrand").getMapping(brandKey), getReplaceOnlyNull(defaultColumns, "idBrand")));
                    props.add(new ImportProperty(idBrandField, itemArticleLM.findLCPByCompoundOldName("brandArticle").getMapping(articleKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Brand")).getMapping(brandKey), getReplaceOnlyNull(defaultColumns, "idBrand")));
                    props.add(new ImportProperty(idBrandField, itemArticleLM.findLCPByCompoundOldName("brandItem").getMapping(itemKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Brand")).getMapping(brandKey), getReplaceOnlyNull(defaultColumns, "idBrand")));
                    fields.add(idBrandField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idBrand);

                    if (showField(userInvoiceDetailsList, "nameBrand")) {
                        addDataField(itemArticleLM, props, fields, defaultColumns, "nameBrand", "nameBrand", brandKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameBrand);
                    }
                }

                if (itemFashionLM != null) {
                    if (showField(userInvoiceDetailsList, "idSeason")) {
                        ImportField idSeasonField = new ImportField(itemFashionLM.findLCPByCompoundOldName("idSeason"));
                        ImportKey<?> seasonKey = new ImportKey((ConcreteCustomClass) itemFashionLM.findClassByCompoundName("Season"),
                                itemFashionLM.findLCPByCompoundOldName("seasonId").getMapping(idSeasonField));
                        keys.add(seasonKey);
                        props.add(new ImportProperty(idSeasonField, itemFashionLM.findLCPByCompoundOldName("idSeason").getMapping(seasonKey), getReplaceOnlyNull(defaultColumns, "idSeason")));
                        props.add(new ImportProperty(idSeasonField, itemFashionLM.findLCPByCompoundOldName("seasonArticle").getMapping(articleKey),
                                LM.object(itemFashionLM.findClassByCompoundName("Season")).getMapping(seasonKey), getReplaceOnlyNull(defaultColumns, "idSeason")));
                        props.add(new ImportProperty(idSeasonField, itemFashionLM.findLCPByCompoundOldName("seasonItem").getMapping(itemKey),
                                LM.object(itemFashionLM.findClassByCompoundName("Season")).getMapping(seasonKey), getReplaceOnlyNull(defaultColumns, "idSeason")));
                        fields.add(idSeasonField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).idSeason);

                        if (showField(userInvoiceDetailsList, "nameSeason")) {
                            addDataField(itemFashionLM, props, fields, defaultColumns, "nameSeason", "nameSeason", seasonKey);
                            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                                data.get(i).add(userInvoiceDetailsList.get(i).nameSeason);
                        }
                    }

                    if (showField(userInvoiceDetailsList, "idCollection")) {
                        ImportField idCollectionField = new ImportField(itemFashionLM.findLCPByCompoundOldName("idCollection"));
                        ImportKey<?> collectionKey = new ImportKey((ConcreteCustomClass) itemFashionLM.findClassByCompoundName("Collection"),
                                itemFashionLM.findLCPByCompoundOldName("collectionId").getMapping(idCollectionField));
                        keys.add(collectionKey);
                        props.add(new ImportProperty(idCollectionField, itemFashionLM.findLCPByCompoundOldName("idCollection").getMapping(collectionKey), getReplaceOnlyNull(defaultColumns, "idCollection")));
                        props.add(new ImportProperty(idCollectionField, itemFashionLM.findLCPByCompoundOldName("collectionArticle").getMapping(articleKey),
                                LM.object(itemFashionLM.findClassByCompoundName("Collection")).getMapping(collectionKey), getReplaceOnlyNull(defaultColumns, "idCollection")));
                        fields.add(idCollectionField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).idCollection);

                        if (showField(userInvoiceDetailsList, "nameCollection")) {
                            addDataField(itemFashionLM, props, fields, defaultColumns, "nameCollection", "nameCollection", collectionKey);
                            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                                data.get(i).add(userInvoiceDetailsList.get(i).nameCollection);
                        }
                    }
                }
            }

            if (purchaseComplianceLM != null) {

                if (showField(userInvoiceDetailsList, "numberCompliance")) {
                    ImportField numberComplianceField = new ImportField(purchaseComplianceLM.findLCPByCompoundOldName("numberCompliance"));
                    ImportKey<?> complianceKey = new ImportKey((ConcreteCustomClass) purchaseComplianceLM.findClassByCompoundName("Compliance"),
                            purchaseComplianceLM.findLCPByCompoundOldName("complianceNumber").getMapping(numberComplianceField));
                    keys.add(complianceKey);
                    props.add(new ImportProperty(numberComplianceField, purchaseComplianceLM.findLCPByCompoundOldName("numberCompliance").getMapping(complianceKey), getReplaceOnlyNull(defaultColumns, "numberCompliance")));
                    props.add(new ImportProperty(numberComplianceField, purchaseComplianceLM.findLCPByCompoundOldName("complianceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                            LM.object(purchaseComplianceLM.findClassByCompoundName("Compliance")).getMapping(complianceKey), getReplaceOnlyNull(defaultColumns, "numberCompliance")));
                    fields.add(numberComplianceField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).numberCompliance);


                    if (showField(userInvoiceDetailsList, "dateCompliance")) {
                        addDataField(purchaseComplianceLM, props, fields, defaultColumns, "dateCompliance", "dateCompliance", complianceKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).dateCompliance);

                        addDataField(purchaseComplianceLM, props, fields, defaultColumns, "fromDateCompliance", "dateCompliance", complianceKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).dateCompliance);
                    }
                }

            }

            for (Map.Entry<String, ImportColumnDetail> entry : customColumns.entrySet()) {
                ImportColumnDetail customColumn = entry.getValue();
                ScriptingLogicsModule customModuleLM = (ScriptingLogicsModule) context.getBL().getModule(customColumn.moduleName);
                if (customModuleLM != null) {
                    ImportField customField = new ImportField(customModuleLM.findLCPByCompoundOldName(customColumn.property));
                    ImportKey<?> customKey = null;
                    if (customColumn.key.equals("item"))
                        customKey = itemKey;
                    else if (customColumn.key.equals("article"))
                        customKey = articleKey;
                    else if (customColumn.key.equals("documentDetail"))
                        customKey = userInvoiceDetailKey;
                    if (customKey != null) {
                        props.add(new ImportProperty(customField, customModuleLM.findLCPByCompoundOldName(customColumn.property).getMapping(customKey), getReplaceOnlyNull(customColumns, entry.getKey())));
                        fields.add(customField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).customValues.get(entry.getKey()));
                    } else if(customColumn.key.equals("document")) {
                        props.add(new ImportProperty(customField, customModuleLM.findLCPByCompoundOldName(customColumn.property).getMapping(userInvoiceObject), getReplaceOnlyNull(customColumns, entry.getKey())));
                        fields.add(customField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).customValues.get(entry.getKey()));
                    }
                }
            }

            ImportTable table = new ImportTable(fields, data);

            if (!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            if (!disableVolatileStats)
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
                    purchaseInvoiceSet, checkInvoiceExistence, importSettings, userInvoiceObject,
                    staticNameImportType);
        else if (fileExtension.equals("XLS"))
            userInvoiceDetailsList = importUserInvoicesFromXLS(context, session, file, defaultColumns, customColumns,
                    purchaseInvoiceSet, checkInvoiceExistence, importSettings, userInvoiceObject,
                    staticNameImportType);
        else if (fileExtension.equals("XLSX"))
            userInvoiceDetailsList = importUserInvoicesFromXLSX(context, session, file, defaultColumns, customColumns, 
                    purchaseInvoiceSet, checkInvoiceExistence, importSettings, userInvoiceObject,
                    staticNameImportType);
        else if (fileExtension.equals("CSV"))
            userInvoiceDetailsList = importUserInvoicesFromCSV(context, session, file, defaultColumns, customColumns, 
                    purchaseInvoiceSet, checkInvoiceExistence, importSettings, userInvoiceObject,
                    staticNameImportType);
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
            currentTimestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Calendar.getInstance().getTime());

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
                ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
                ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
                String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
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
        currentTimestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Calendar.getInstance().getTime());

        List<String[]> valuesList = new ArrayList<String[]>();
        while ((line = br.readLine()) != null) {
                valuesList.add(line.split(importSettings.getCsvSeparator()));
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
            ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
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
        currentTimestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Calendar.getInstance().getTime());

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
            ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
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
        currentTimestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Calendar.getInstance().getTime());
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
            ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
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
            ScriptingLogicsModule moduleLM = (ScriptingLogicsModule) context.getBL().getModule(getSplittedPart(propertyImportType, "\\.", 0));

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
                        moduleLM.findLCPByCompoundOldName("idArticle").change(entry.getValue(), context, (DataObject) moduleLM.findLCPByCompoundOldName("articleId").readClasses(context, new DataObject(entry.getKey())));
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
        articleQuery.addProperty("idArticle", LM.findLCPByCompoundOldName("idArticle").getExpr(session.getModifier(), articleExpr));
        articleQuery.addProperty(sidProperty, LM.findLCPByCompoundOldName(sidProperty).getExpr(session.getModifier(), articleExpr));
        articleQuery.and(LM.findLCPByCompoundOldName("idArticle").getExpr(session.getModifier(), articleExpr).getWhere());
        
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

    protected static Set<String> getPurchaseInvoiceSet(DataSession session, ScriptingLogicsModule LM, boolean checkInvoiceExistence) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if(!checkInvoiceExistence)
            return null;

        Set<String> purchaseInvoiceSet = new HashSet<String>();

        KeyExpr key = new KeyExpr("purchase.invoice");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "Purchase.Invoice", key);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);

        query.addProperty("Purchase.idUserInvoice", LM.findLCPByCompoundOldName("Purchase.idUserInvoice").getExpr(session.getModifier(), key));
        query.and(LM.findLCPByCompoundOldName("Purchase.idUserInvoice").getExpr(key).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String idUserInvoice = (String) entry.get("Purchase.idUserInvoice");
            if(idUserInvoice != null)
                purchaseInvoiceSet.add(idUserInvoice);
        }
        return purchaseInvoiceSet;
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
    
    private Date getCurrentDateDocument(DataSession session, DataObject userInvoiceObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Date defaultDate = new Date(Calendar.getInstance().getTime().getTime());
        Date currentDateDocument = userInvoiceObject == null ? defaultDate :
                (Date) getLCP("Purchase.dateUserInvoice").read(session, userInvoiceObject);
        return currentDateDocument == null ? defaultDate : currentDateDocument;
    }
}

