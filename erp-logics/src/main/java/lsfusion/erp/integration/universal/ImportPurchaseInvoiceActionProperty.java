package lsfusion.erp.integration.universal;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
import lsfusion.erp.stock.BarcodeUtils;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.Settings;
import lsfusion.server.classes.*;
import lsfusion.server.data.SQLHandledException;
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

                String primaryKeyType = parseKeyType((String) getLCP("namePrimaryKeyTypeImportType").read(session, importTypeObject));
                boolean checkExistence = getLCP("checkExistencePrimaryKeyImportType").read(session, importTypeObject) != null;
                String secondaryKeyType = parseKeyType((String) getLCP("nameSecondaryKeyTypeImportType").read(session, importTypeObject));
                boolean keyIsDigit = getLCP("keyIsDigitImportType").read(session, importTypeObject) != null;

                String csvSeparator = trim((String) getLCP("separatorImportType").read(session, importTypeObject), ";");
                Integer startRow = (Integer) getLCP("startRowImportType").read(session, importTypeObject);
                startRow = startRow == null || startRow.equals(0) ? 1 : startRow;
                Boolean isPosted = (Boolean) getLCP("isPostedImportType").read(session, importTypeObject);

                ObjectValue operationObject = getLCP("autoImportOperationImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierObject = getLCP("autoImportSupplierImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierStockObject = getLCP("autoImportSupplierStockImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerObject = getLCP("autoImportCustomerImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerStockObject = getLCP("autoImportCustomerStockImportType").readClasses(session, (DataObject) importTypeObject);

                Map<String, ImportColumnDetail> importColumns = readImportColumns(session, LM, importTypeObject);

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            List<List<PurchaseInvoiceDetail>> userInvoiceDetailData = importUserInvoicesFromFile(context, session,
                                    userInvoiceObject, importColumns, file, fileExtension, startRow,
                                    isPosted, csvSeparator, primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit);

                            if (userInvoiceDetailData != null && userInvoiceDetailData.size() >= 1)
                                importUserInvoices(userInvoiceDetailData.get(0), session, importColumns, userInvoiceObject,
                                        primaryKeyType, operationObject, supplierObject, supplierStockObject,
                                        customerObject, customerStockObject, disableVolatileStats);

                            if (userInvoiceDetailData != null && userInvoiceDetailData.size() >= 2)
                                importUserInvoices(userInvoiceDetailData.get(1), session, importColumns, userInvoiceObject,
                                        secondaryKeyType, operationObject, supplierObject, supplierStockObject,
                                        customerObject, customerStockObject, disableVolatileStats);

                            session.apply(context);
                            session.close();

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
    
    public boolean makeImport(ExecutionContext<ClassPropertyInterface> context, DataSession session, DataObject userInvoiceObject,
                              Map<String, ImportColumnDetail> importColumns, byte[] file, String fileExtension, Integer startRow,
                              Boolean isPosted, String csvSeparator, String primaryKeyType, boolean checkExistence, String secondaryKeyType,
                              boolean keyIsDigit, ObjectValue operationObject, ObjectValue supplierObject, ObjectValue supplierStockObject,
                              ObjectValue customerObject, ObjectValue customerStockObject) 
            throws SQLHandledException, ParseException, UniversalImportException, IOException, SQLException, BiffException, 
            xBaseJException, ScriptingErrorLog.SemanticErrorException {

        boolean disableVolatileStats = Settings.get().isDisableExplicitVolatileStats();
        
        List<List<PurchaseInvoiceDetail>> userInvoiceDetailData = importUserInvoicesFromFile(context, session,
                userInvoiceObject, importColumns, file, fileExtension, startRow, isPosted, csvSeparator, primaryKeyType,
                checkExistence, secondaryKeyType, keyIsDigit);

        boolean result1 = (userInvoiceDetailData != null && userInvoiceDetailData.size() >= 1) && 
            importUserInvoices(userInvoiceDetailData.get(0), session, importColumns, userInvoiceObject,
                    primaryKeyType, operationObject, supplierObject, supplierStockObject,
                    customerObject, customerStockObject, disableVolatileStats);

        boolean result2 = (userInvoiceDetailData != null && userInvoiceDetailData.size() >= 2) &&
            importUserInvoices(userInvoiceDetailData.get(1), session, importColumns, userInvoiceObject,
                    secondaryKeyType, operationObject, supplierObject, supplierStockObject,
                    customerObject, customerStockObject, disableVolatileStats);
        
        return result1 && result2;
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

    public boolean importUserInvoices(List<PurchaseInvoiceDetail> userInvoiceDetailsList, DataSession session, Map<String, ImportColumnDetail> importColumns,
                                   DataObject userInvoiceObject, String keyType, ObjectValue operationObject, ObjectValue supplierObject,
                                   ObjectValue supplierStockObject, ObjectValue customerObject, ObjectValue customerStockObject, boolean disableVolatileStats)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException, SQLHandledException {


        if (userInvoiceDetailsList != null && !userInvoiceDetailsList.isEmpty()) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(userInvoiceDetailsList.size());

            ImportField idUserInvoiceField = null;
            ImportKey<?> userInvoiceKey = null;
            if (userInvoiceObject == null) {
                idUserInvoiceField = new ImportField(getLCP("idUserInvoice"));
                userInvoiceKey = new ImportKey((ConcreteCustomClass) getClass("Purchase.UserInvoice"),
                        getLCP("userInvoiceId").getMapping(idUserInvoiceField));
                keys.add(userInvoiceKey);
                props.add(new ImportProperty(idUserInvoiceField, getLCP("idUserInvoice").getMapping(userInvoiceKey), getReplaceOnlyNull(importColumns, "idUserInvoice")));
                fields.add(idUserInvoiceField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "numberUserInvoice")) {
                if (userInvoiceObject == null)
                    addDataField(props, fields, importColumns, "numberUserInvoice", "numberUserInvoice", userInvoiceKey);
                else
                    addDataField(props, fields, importColumns, "numberUserInvoice", "numberUserInvoice", userInvoiceObject);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).numberUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "dateUserInvoice")) {
                if (userInvoiceObject == null)
                    addDataField(props, fields, importColumns, "dateUserInvoice", "dateUserInvoice", userInvoiceKey);
                else
                    addDataField(props, fields, importColumns, "dateUserInvoice", "dateUserInvoice", userInvoiceObject);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).dateUserInvoice);
            }

            if (showField(userInvoiceDetailsList, "currencyUserInvoice")) {
                ImportField shortNameCurrencyField = new ImportField(getLCP("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) getClass("Currency"),
                        getLCP("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                if (userInvoiceObject == null)
                    props.add(new ImportProperty(shortNameCurrencyField, getLCP("currencyUserInvoice").getMapping(userInvoiceKey),
                            LM.object(getClass("Currency")).getMapping(currencyKey), getReplaceOnlyNull(importColumns, "currencyUserInvoice")));
                else
                    props.add(new ImportProperty(shortNameCurrencyField, getLCP("currencyUserInvoice").getMapping(userInvoiceObject),
                            LM.object(getClass("Currency")).getMapping(currencyKey), getReplaceOnlyNull(importColumns, "currencyUserInvoice")));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).currencyUserInvoice);
            }

            ImportField idUserInvoiceDetailField = new ImportField(getLCP("idUserInvoiceDetail"));
            ImportKey<?> userInvoiceDetailKey = new ImportKey((ConcreteCustomClass) getClass("Purchase.UserInvoiceDetail"),
                    getLCP("userInvoiceDetailId").getMapping(idUserInvoiceDetailField));
            keys.add(userInvoiceDetailKey);
            props.add(new ImportProperty(idUserInvoiceDetailField, getLCP("idUserInvoiceDetail").getMapping(userInvoiceDetailKey), getReplaceOnlyNull(importColumns, "idUserInvoiceDetail")));
            if (userInvoiceObject == null)
                props.add(new ImportProperty(idUserInvoiceField, getLCP("Purchase.userInvoiceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(getClass("UserInvoice")).getMapping(userInvoiceKey), getReplaceOnlyNull(importColumns, "idUserInvoice")));
            else
                props.add(new ImportProperty(userInvoiceObject, getLCP("Purchase.userInvoiceUserInvoiceDetail").getMapping(userInvoiceDetailKey), getReplaceOnlyNull(importColumns, "idUserInvoiceDetail")));
            fields.add(idUserInvoiceDetailField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idUserInvoiceDetail);

            if (operationObject instanceof DataObject) {
                if (userInvoiceObject == null)
                    props.add(new ImportProperty((DataObject) operationObject, getLCP("Purchase.operationUserInvoice").getMapping(userInvoiceKey)));
                else
                    props.add(new ImportProperty((DataObject) operationObject, getLCP("Purchase.operationUserInvoice").getMapping(userInvoiceObject)));
            }

            if (supplierObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierObject, getLCP("Purchase.supplierUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                if (userInvoiceObject == null)
                    props.add(new ImportProperty((DataObject) supplierObject, getLCP("Purchase.supplierUserInvoice").getMapping(userInvoiceKey)));
                else
                    props.add(new ImportProperty((DataObject) supplierObject, getLCP("Purchase.supplierUserInvoice").getMapping(userInvoiceObject)));
            }

            if (showField(userInvoiceDetailsList, "idSupplier")) {

                ImportField idSupplierField = new ImportField(getLCP("idLegalEntity"));
                ImportKey<?> supplierKey = new ImportKey((ConcreteCustomClass) getClass("LegalEntity"),
                        getLCP("legalEntityId").getMapping(idSupplierField));
                keys.add(supplierKey);
                props.add(new ImportProperty(idSupplierField, getLCP("supplierUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(getClass("LegalEntity")).getMapping(supplierKey), getReplaceOnlyNull(importColumns, "idSupplier")));
                if (userInvoiceObject == null)
                    props.add(new ImportProperty(idSupplierField, getLCP("supplierUserInvoice").getMapping(userInvoiceKey),
                            LM.object(getClass("LegalEntity")).getMapping(supplierKey), getReplaceOnlyNull(importColumns, "idSupplier")));
                else
                    props.add(new ImportProperty(idSupplierField, getLCP("supplierUserInvoice").getMapping(userInvoiceObject),
                            LM.object(getClass("LegalEntity")).getMapping(supplierKey), getReplaceOnlyNull(importColumns, "idSupplier")));
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
                        LM.object(getClass("Stock")).getMapping(supplierStockKey), getReplaceOnlyNull(importColumns, "idSupplierStock")));
                if (userInvoiceObject == null)
                    props.add(new ImportProperty(idSupplierStockField, getLCP("supplierStockUserInvoice").getMapping(userInvoiceKey),
                            LM.object(getClass("Stock")).getMapping(supplierStockKey), getReplaceOnlyNull(importColumns, "idSupplierStock")));
                else
                    props.add(new ImportProperty(idSupplierStockField, getLCP("supplierStockUserInvoice").getMapping(userInvoiceObject),
                            LM.object(getClass("Stock")).getMapping(supplierStockKey), getReplaceOnlyNull(importColumns, "idSupplierStock")));
                fields.add(idSupplierStockField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idSupplierStock);

            }

            if (supplierStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) supplierStockObject, getLCP("Purchase.supplierStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                if (userInvoiceObject == null)
                    props.add(new ImportProperty((DataObject) supplierStockObject, getLCP("Purchase.supplierStockUserInvoice").getMapping(userInvoiceKey)));
                else
                    props.add(new ImportProperty((DataObject) supplierStockObject, getLCP("Purchase.supplierStockUserInvoice").getMapping(userInvoiceObject)));
            }

            if (customerObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerObject, getLCP("Purchase.customerUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                if (userInvoiceObject == null)
                    props.add(new ImportProperty((DataObject) customerObject, getLCP("Purchase.customerUserInvoice").getMapping(userInvoiceKey)));
                else
                    props.add(new ImportProperty((DataObject) customerObject, getLCP("Purchase.customerUserInvoice").getMapping(userInvoiceObject)));
            }

            if (customerStockObject instanceof DataObject) {
                props.add(new ImportProperty((DataObject) customerStockObject, getLCP("Purchase.customerStockUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                if (userInvoiceObject == null)
                    props.add(new ImportProperty((DataObject) customerStockObject, getLCP("Purchase.customerStockUserInvoice").getMapping(userInvoiceKey)));
                else
                    props.add(new ImportProperty((DataObject) customerStockObject, getLCP("Purchase.customerStockUserInvoice").getMapping(userInvoiceObject)));
            }

            ImportField idBarcodeSkuField = new ImportField(getLCP("idBarcodeSku"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) getClass("Barcode"),
                    getLCP("extBarcodeId").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idBarcodeSkuField, getLCP("idBarcode").getMapping(barcodeKey), getReplaceOnlyNull(importColumns, "barcodeItem")));
            props.add(new ImportProperty(idBarcodeSkuField, getLCP("extIdBarcode").getMapping(barcodeKey), getReplaceOnlyNull(importColumns, "barcodeItem")));
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idBarcodeSku);

            ImportField idBatchField = new ImportField(getLCP("idBatch"));
            ImportKey<?> batchKey = new ImportKey((CustomClass) getClass("Batch"),
                    getLCP("batchId").getMapping(idBatchField));
            props.add(new ImportProperty(idBatchField, getLCP("idBatch").getMapping(batchKey), getReplaceOnlyNull(importColumns, "idBatch")));
            props.add(new ImportProperty(idBatchField, getLCP("idBatchUserInvoiceDetail").getMapping(userInvoiceDetailKey), getReplaceOnlyNull(importColumns, "idBatch")));
            fields.add(idBatchField);
            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                data.get(i).add(userInvoiceDetailsList.get(i).idBatch);

            if (purchaseShipmentBoxLM != null && showField(userInvoiceDetailsList, "idBox")) {
                ImportField idBoxField = new ImportField(purchaseShipmentBoxLM.findLCPByCompoundOldName("idBox"));
                ImportKey<?> boxKey = new ImportKey((ConcreteCustomClass) purchaseShipmentBoxLM.findClassByCompoundName("Box"),
                        purchaseShipmentBoxLM.findLCPByCompoundOldName("boxId").getMapping(idBoxField));
                keys.add(boxKey);
                props.add(new ImportProperty(idBoxField, purchaseShipmentBoxLM.findLCPByCompoundOldName("idBox").getMapping(boxKey), getReplaceOnlyNull(importColumns, "idBox")));
                props.add(new ImportProperty(idBoxField, purchaseShipmentBoxLM.findLCPByCompoundOldName("boxUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        purchaseShipmentBoxLM.object(purchaseShipmentBoxLM.findClassByCompoundName("Box")).getMapping(boxKey), getReplaceOnlyNull(importColumns, "idBox")));
                fields.add(idBoxField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idBox);

                if (showField(userInvoiceDetailsList, "nameBox")) {
                    addDataField(purchaseShipmentBoxLM, props, fields, importColumns, "nameBox", "nameBox", boxKey);
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

            String replaceField = (keyType == null || keyType.equals("item")) ? "idItem" : keyType.equals("barcode") ? "idBarcodeSku" : "idBatch";
            String iGroupAggr = getKeyGroupAggr(keyType);
            ImportField iField = (keyType == null || keyType.equals("item")) ? idItemField : keyType.equals("barcode") ? idBarcodeSkuField : idBatchField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) getClass("Item"),
                    getLCP(iGroupAggr).getMapping(iField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, getLCP("idItem").getMapping(itemKey), getReplaceOnlyNull(importColumns, "idItem")));
            props.add(new ImportProperty(iField, getLCP("Purchase.skuInvoiceDetail").getMapping(userInvoiceDetailKey),
                    LM.object(getClass("Sku")).getMapping(itemKey), getReplaceOnlyNull(importColumns, replaceField)));
            props.add(new ImportProperty(iField, getLCP("skuBarcode").getMapping(barcodeKey),
                    LM.object(getClass("Item")).getMapping(itemKey), getReplaceOnlyNull(importColumns, replaceField)));

            if (showField(userInvoiceDetailsList, "captionItem")) {
                addDataField(props, fields, importColumns, "captionItem", "captionItem", itemKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).captionItem);
            }

            if (showField(userInvoiceDetailsList, "originalCaptionItem")) {
                addDataField(props, fields, importColumns, "originalCaptionItem", "originalCaptionItem", itemKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).originalCaptionItem);
            }

            if (showField(userInvoiceDetailsList, "idUOM")) {
                ImportField idUOMField = new ImportField(getLCP("idUOM"));
                ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) getClass("UOM"),
                        getLCP("UOMId").getMapping(idUOMField));
                keys.add(UOMKey);
                props.add(new ImportProperty(idUOMField, getLCP("idUOM").getMapping(UOMKey), getReplaceOnlyNull(importColumns, "idUOM")));
                props.add(new ImportProperty(idUOMField, getLCP("nameUOM").getMapping(UOMKey), getReplaceOnlyNull(importColumns, "idUOM")));
                props.add(new ImportProperty(idUOMField, getLCP("UOMItem").getMapping(itemKey),
                        LM.object(getClass("UOM")).getMapping(UOMKey), getReplaceOnlyNull(importColumns, "idUOM")));
                fields.add(idUOMField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idUOM);
            }

            if (showField(userInvoiceDetailsList, "idManufacturer")) {
                ImportField idManufacturerField = new ImportField(getLCP("idManufacturer"));
                ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) getClass("Manufacturer"),
                        getLCP("manufacturerId").getMapping(idManufacturerField));
                keys.add(manufacturerKey);
                props.add(new ImportProperty(idManufacturerField, getLCP("idManufacturer").getMapping(manufacturerKey), getReplaceOnlyNull(importColumns, "idManufacturer")));
                props.add(new ImportProperty(idManufacturerField, getLCP("manufacturerItem").getMapping(itemKey),
                        LM.object(getClass("Manufacturer")).getMapping(manufacturerKey), getReplaceOnlyNull(importColumns, "idManufacturer")));
                fields.add(idManufacturerField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idManufacturer);

                if (showField(userInvoiceDetailsList, "nameManufacturer")) {
                    addDataField(props, fields, importColumns, "nameManufacturer", "nameManufacturer", manufacturerKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameManufacturer);
                }
            }

            if (showField(userInvoiceDetailsList, "nameCountry")) {
                ImportField nameCountryField = new ImportField(getLCP("nameCountry"));
                ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) getClass("Country"),
                        getLCP("countryName").getMapping(nameCountryField));
                keys.add(countryKey);
                props.add(new ImportProperty(nameCountryField, getLCP("nameCountry").getMapping(countryKey), getReplaceOnlyNull(importColumns, "nameCountry")));
                props.add(new ImportProperty(nameCountryField, getLCP("countryItem").getMapping(itemKey),
                        LM.object(getClass("Country")).getMapping(countryKey), getReplaceOnlyNull(importColumns, "nameCountry")));
                fields.add(nameCountryField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).nameCountry);

                if (showField(userInvoiceDetailsList, "nameOriginCountry")) {
                    addDataField(props, fields, importColumns, "nameOriginCountry", "nameOriginCountry", countryKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameOriginCountry);
                }
            } else if (showField(userInvoiceDetailsList, "nameOriginCountry")) {
                ImportField nameOriginCountryField = new ImportField(getLCP("nameOriginCountry"));
                ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) getClass("Country"),
                        getLCP("countryNameOrigin").getMapping(nameOriginCountryField));
                keys.add(countryKey);
                props.add(new ImportProperty(nameOriginCountryField, getLCP("nameOriginCountry").getMapping(countryKey), getReplaceOnlyNull(importColumns, "nameOriginCountry")));
                props.add(new ImportProperty(nameOriginCountryField, getLCP("countryItem").getMapping(itemKey),
                        LM.object(getClass("Country")).getMapping(countryKey), getReplaceOnlyNull(importColumns, "nameOriginCountry")));
                fields.add(nameOriginCountryField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).nameOriginCountry);
            }

            if (showField(userInvoiceDetailsList, "idCustomer")) {
                ImportField idCustomerField = new ImportField(getLCP("idLegalEntity"));
                ImportKey<?> customerKey = new ImportKey((ConcreteCustomClass) getClass("LegalEntity"),
                        getLCP("legalEntityId").getMapping(idCustomerField));
                keys.add(customerKey);
                if (userInvoiceObject == null)
                    props.add(new ImportProperty(idCustomerField, getLCP("Purchase.customerUserInvoice").getMapping(userInvoiceKey),
                            LM.object(getClass("LegalEntity")).getMapping(customerKey), getReplaceOnlyNull(importColumns, "idBCustomer")));
                else
                    props.add(new ImportProperty(idCustomerField, getLCP("Purchase.customerUserInvoice").getMapping(userInvoiceObject),
                            LM.object(getClass("LegalEntity")).getMapping(customerKey), getReplaceOnlyNull(importColumns, "idBCustomer")));
                fields.add(idCustomerField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idCustomer);
            }

            if (showField(userInvoiceDetailsList, "idCustomerStock")) {
                ImportField idCustomerStockField = new ImportField(getLCP("idStock"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) getClass("Stock"),
                        getLCP("stockId").getMapping(idCustomerStockField));
                keys.add(customerStockKey);
                if (userInvoiceObject == null)
                    props.add(new ImportProperty(idCustomerStockField, getLCP("Purchase.customerStockUserInvoice").getMapping(userInvoiceKey),
                            LM.object(getClass("Stock")).getMapping(customerStockKey), getReplaceOnlyNull(importColumns, "idCustomerStock")));
                else
                    props.add(new ImportProperty(idCustomerStockField, getLCP("Purchase.customerStockUserInvoice").getMapping(userInvoiceObject),
                            LM.object(getClass("Stock")).getMapping(customerStockKey), getReplaceOnlyNull(importColumns, "idCustomerStock")));
                fields.add(idCustomerStockField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idCustomerStock);
            }

            if (showField(userInvoiceDetailsList, "quantity")) {
                addDataField(props, fields, importColumns, "Purchase.quantityUserInvoiceDetail", "quantity", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).quantity);
            }

            if (showField(userInvoiceDetailsList, "price")) {
                addDataField(props, fields, importColumns, "Purchase.priceUserInvoiceDetail", "price", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).price);
            }

            if (showField(userInvoiceDetailsList, "sum")) {
                addDataField(props, fields, importColumns, "Purchase.sumUserInvoiceDetail", "sum", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).sum);
            }

            if (showField(userInvoiceDetailsList, "valueVAT")) {
                ImportField valueVATUserInvoiceDetailField = new ImportField(getLCP("Purchase.valueVATUserInvoiceDetail"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) getClass("Range"),
                        getLCP("valueCurrentVATDefaultValue").getMapping(valueVATUserInvoiceDetailField));
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATUserInvoiceDetailField, getLCP("Purchase.VATUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(getClass("Range")).getMapping(VATKey), getReplaceOnlyNull(importColumns, "valueVAT")));
                fields.add(valueVATUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).valueVAT);

                ImportField dateField = new ImportField(DateClass.instance);
                props.add(new ImportProperty(dateField, LM.findLCPByCompoundOldName("dataDateBarcode").getMapping(barcodeKey)));
                fields.add(dateField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).dateVAT);

                if(taxItemLM != null) {
                ImportField countryVATField = new ImportField(taxItemLM.findLCPByCompoundOldName("nameCountry"));
                ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) taxItemLM.findClassByCompoundName("Country"),
                        taxItemLM.findLCPByCompoundOldName("countryName").getMapping(countryVATField));
                keys.add(countryKey);
                props.add(new ImportProperty(valueVATUserInvoiceDetailField, taxItemLM.findLCPByCompoundOldName("dataVATItemCountryDate").getMapping(itemKey, countryKey, dateField),
                        LM.object(taxItemLM.findClassByCompoundName("Range")).getMapping(VATKey), getReplaceOnlyNull(importColumns, "valueVAT")));
                fields.add(countryVATField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).countryVAT);
                }
            }

            if (showField(userInvoiceDetailsList, "sumVAT")) {
                addDataField(props, fields, importColumns, "Purchase.VATSumUserInvoiceDetail", "sumVAT", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).sumVAT);
            }

            if (showField(userInvoiceDetailsList, "invoiceSum")) {
                addDataField(props, fields, importColumns, "Purchase.invoiceSumUserInvoiceDetail", "invoiceSum", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).invoiceSum);
            }

            if (purchaseDeclarationLM != null) {
                
                if (showField(userInvoiceDetailsList, "numberDeclaration")) {
                    ImportField numberDeclarationField = new ImportField(purchaseDeclarationLM.findLCPByCompoundOldName("numberDeclaration"));
                    ImportKey<?> declarationKey = new ImportKey((ConcreteCustomClass) purchaseDeclarationLM.findClassByCompoundName("Declaration"),
                            purchaseDeclarationLM.findLCPByCompoundOldName("declarationId").getMapping(numberDeclarationField));
                    keys.add(declarationKey);
                    props.add(new ImportProperty(numberDeclarationField, purchaseDeclarationLM.findLCPByCompoundOldName("numberDeclaration").getMapping(declarationKey), getReplaceOnlyNull(importColumns, "numberDeclaration")));
                    props.add(new ImportProperty(numberDeclarationField, purchaseDeclarationLM.findLCPByCompoundOldName("idDeclaration").getMapping(declarationKey), getReplaceOnlyNull(importColumns, "numberDeclaration")));
                    props.add(new ImportProperty(numberDeclarationField, purchaseDeclarationLM.findLCPByCompoundOldName("declarationUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                            LM.object(purchaseDeclarationLM.findClassByCompoundName("Declaration")).getMapping(declarationKey), getReplaceOnlyNull(importColumns, "numberDeclaration")));
                    fields.add(numberDeclarationField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).numberDeclaration);
                }
                
            }

            if (purchaseShipmentLM != null) {

                if (showField(userInvoiceDetailsList, "expiryDate")) {
                    addDataField(purchaseShipmentLM, props, fields, importColumns, "Purchase.expiryDateUserInvoiceDetail", "expiryDate", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).expiryDate);
                }

                if (showField(userInvoiceDetailsList, "manufactureDate")) {
                    addDataField(purchaseShipmentLM, props, fields, importColumns, "Purchase.manufactureDateUserInvoiceDetail", "manufactureDate", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).manufactureDate);
                }

                if (showField(userInvoiceDetailsList, "shipmentPrice")) {
                    addDataField(purchaseShipmentLM, props, fields, importColumns, "shipmentPriceUserInvoiceDetail", "shipmentPrice", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).shipmentPrice);
                }

                if (showField(userInvoiceDetailsList, "shipmentSum")) {
                    addDataField(purchaseShipmentLM, props, fields, importColumns, "shipmentSumUserInvoiceDetail", "shipmentSum", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).shipmentSum);
                }
                
            }
                
            if ((purchaseManufacturingPriceLM != null) && showField(userInvoiceDetailsList, "manufacturingPrice")) {
                addDataField(purchaseManufacturingPriceLM, props, fields, importColumns, "Purchase.manufacturingPriceUserInvoiceDetail", "manufacturingPrice", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).manufacturingPrice);
            }

            if (itemPharmacyByLM != null && showField(userInvoiceDetailsList, "idPharmacyPriceGroup")) {
                ImportField idPharmacyPriceGroupField = new ImportField(itemPharmacyByLM.findLCPByCompoundOldName("idPharmacyPriceGroup"));
                ImportKey<?> pharmacyPriceGroupKey = new ImportKey((ConcreteCustomClass) itemPharmacyByLM.findClassByCompoundName("PharmacyPriceGroup"),
                        itemPharmacyByLM.findLCPByCompoundOldName("pharmacyPriceGroupId").getMapping(idPharmacyPriceGroupField));
                keys.add(pharmacyPriceGroupKey);
                props.add(new ImportProperty(idPharmacyPriceGroupField, itemPharmacyByLM.findLCPByCompoundOldName("idPharmacyPriceGroup").getMapping(pharmacyPriceGroupKey), getReplaceOnlyNull(importColumns, "idPharmacyPriceGroup")));
                props.add(new ImportProperty(idPharmacyPriceGroupField, itemPharmacyByLM.findLCPByCompoundOldName("namePharmacyPriceGroup").getMapping(pharmacyPriceGroupKey), getReplaceOnlyNull(importColumns, "idPharmacyPriceGroup")));
                props.add(new ImportProperty(idPharmacyPriceGroupField, itemPharmacyByLM.findLCPByCompoundOldName("pharmacyPriceGroupItem").getMapping(itemKey),
                        LM.object(itemPharmacyByLM.findClassByCompoundName("PharmacyPriceGroup")).getMapping(pharmacyPriceGroupKey), getReplaceOnlyNull(importColumns, "idPharmacyPriceGroup")));
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
                    props.add(new ImportProperty(nameImportCountryField, purchaseInvoicePharmacyLM.findLCPByCompoundOldName("nameCountry").getMapping(importCountryKey), getReplaceOnlyNull(importColumns, "nameImportCountry")));
                    props.add(new ImportProperty(nameImportCountryField, purchaseInvoicePharmacyLM.findLCPByCompoundOldName("importCountryUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                            LM.object(purchaseInvoicePharmacyLM.findClassByCompoundName("Country")).getMapping(importCountryKey), getReplaceOnlyNull(importColumns, "nameImportCountry")));
                    fields.add(nameImportCountryField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).nameImportCountry);
                }

                if (showField(userInvoiceDetailsList, "seriesPharmacy")) {
                    addDataField(purchaseInvoicePharmacyLM, props, fields, importColumns, "Purchase.seriesPharmacyUserInvoiceDetail", "seriesPharmacy", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).seriesPharmacy);
                }

                if (showField(userInvoiceDetailsList, "contractPrice")) {
                    addDataField(purchaseInvoicePharmacyLM, props, fields, importColumns, "contractPriceUserInvoiceDetail", "contractPrice", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).contractPrice);
                }
            }
            
            if (showField(userInvoiceDetailsList, "rateExchange")) {
                addDataField(props, fields, importColumns, "rateExchangeUserInvoiceDetail", "rateExchange", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).rateExchange);
            }

            if (purchaseDeclarationDetailLM != null) {

                if (showField(userInvoiceDetailsList, "sumNetWeight")) {
                    addDataField(purchaseDeclarationDetailLM, props, fields, importColumns, "sumNetWeightUserInvoiceDetail", "sumNetWeight", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).sumNetWeight);
                }

                if (showField(userInvoiceDetailsList, "sumGrossWeight")) {
                    addDataField(purchaseDeclarationDetailLM, props, fields, importColumns, "sumGrossWeightUserInvoiceDetail", "sumGrossWeight", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).sumGrossWeight);
                }
            }
            
            if (showField(userInvoiceDetailsList, "isPosted")) {
                if (userInvoiceObject == null)
                    addDataField(props, fields, importColumns, "isPostedUserInvoice", "isPosted", userInvoiceKey);
                else
                    addDataField(props, fields, importColumns, "isPostedUserInvoice", "isPosted", userInvoiceObject);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).isPosted);
            }

            if (showField(userInvoiceDetailsList, "idItemGroup")) {
                ImportField idItemGroupField = new ImportField(getLCP("idItemGroup"));
                ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) getClass("ItemGroup"),
                        getLCP("itemGroupId").getMapping(idItemGroupField));
                keys.add(itemGroupKey);
                props.add(new ImportProperty(idItemGroupField, getLCP("idItemGroup").getMapping(itemGroupKey), getReplaceOnlyNull(importColumns, "idItemGroup")));
                props.add(new ImportProperty(idItemGroupField, getLCP("nameItemGroup").getMapping(itemGroupKey), true));
                props.add(new ImportProperty(idItemGroupField, getLCP("itemGroupItem").getMapping(itemKey),
                        LM.object(getClass("ItemGroup")).getMapping(itemGroupKey), getReplaceOnlyNull(importColumns, "idItemGroup")));
                fields.add(idItemGroupField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idItemGroup);
            }

            if ((itemArticleLM != null)) {

                ImportField idArticleField = new ImportField(itemArticleLM.findLCPByCompoundOldName("idArticle"));
                ImportKey<?> articleKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Article"),
                        itemArticleLM.findLCPByCompoundOldName("articleId").getMapping(idArticleField));
                keys.add(articleKey);
                props.add(new ImportProperty(idArticleField, itemArticleLM.findLCPByCompoundOldName("idArticle").getMapping(articleKey), getReplaceOnlyNull(importColumns, "idArticle")));
                props.add(new ImportProperty(idArticleField, itemArticleLM.findLCPByCompoundOldName("articleItem").getMapping(itemKey),
                        LM.object(itemArticleLM.findClassByCompoundName("Article")).getMapping(articleKey), getReplaceOnlyNull(importColumns, "idArticle")));
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
                            itemArticleLM.object(itemArticleLM.findClassByCompoundName("ItemGroup")).getMapping(itemGroupKey), getReplaceOnlyNull(importColumns, "idItemGroup")));
                    fields.add(idItemGroupField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idItemGroup);
                }

                if (showField(userInvoiceDetailsList, "captionArticle")) {
                    addDataField(itemArticleLM, props, fields, importColumns, "captionArticle", "captionArticle", articleKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).captionArticle);
                }

                if (showField(userInvoiceDetailsList, "originalCaptionArticle")) {
                    addDataField(itemArticleLM, props, fields, importColumns, "originalCaptionArticle", "originalCaptionArticle", articleKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).originalCaptionArticle);
                }

                if (showField(userInvoiceDetailsList, "netWeight")) {
                    ImportField netWeightField = new ImportField(itemArticleLM.findLCPByCompoundOldName("netWeightItem"));
                    props.add(new ImportProperty(netWeightField, itemArticleLM.findLCPByCompoundOldName("netWeightItem").getMapping(itemKey), getReplaceOnlyNull(importColumns, "netWeight")));
                    props.add(new ImportProperty(netWeightField, itemArticleLM.findLCPByCompoundOldName("netWeightArticle").getMapping(articleKey), getReplaceOnlyNull(importColumns, "netWeight")));
                    fields.add(netWeightField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).netWeight);
                }

                if (showField(userInvoiceDetailsList, "grossWeight")) {
                    ImportField grossWeightField = new ImportField(itemArticleLM.findLCPByCompoundOldName("grossWeightItem"));
                    props.add(new ImportProperty(grossWeightField, itemArticleLM.findLCPByCompoundOldName("grossWeightItem").getMapping(itemKey), getReplaceOnlyNull(importColumns, "grossWeight")));
                    props.add(new ImportProperty(grossWeightField, itemArticleLM.findLCPByCompoundOldName("grossWeightArticle").getMapping(articleKey), getReplaceOnlyNull(importColumns, "grossWeight")));
                    fields.add(grossWeightField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).grossWeight);
                }

                if (showField(userInvoiceDetailsList, "composition")) {
                    ImportField compositionField = new ImportField(itemArticleLM.findLCPByCompoundOldName("compositionItem"));
                    props.add(new ImportProperty(compositionField, itemArticleLM.findLCPByCompoundOldName("compositionItem").getMapping(itemKey), getReplaceOnlyNull(importColumns, "composition")));
                    props.add(new ImportProperty(compositionField, itemArticleLM.findLCPByCompoundOldName("compositionArticle").getMapping(articleKey), getReplaceOnlyNull(importColumns, "composition")));
                    fields.add(compositionField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).composition);
                }

                if (showField(userInvoiceDetailsList, "originalComposition")) {
                    ImportField originalCompositionField = new ImportField(itemArticleLM.findLCPByCompoundOldName("originalCompositionItem"));
                    props.add(new ImportProperty(originalCompositionField, itemArticleLM.findLCPByCompoundOldName("originalCompositionItem").getMapping(itemKey), getReplaceOnlyNull(importColumns, "originalComposition")));
                    props.add(new ImportProperty(originalCompositionField, itemArticleLM.findLCPByCompoundOldName("originalCompositionArticle").getMapping(articleKey), getReplaceOnlyNull(importColumns, "originalComposition")));
                    fields.add(originalCompositionField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).originalComposition);
                }

                if (customsGroupArticleLM != null && showField(userInvoiceDetailsList, "originalCustomsGroupItem")) {
                    ImportField originalCustomsGroupItemField = new ImportField(customsGroupArticleLM.findLCPByCompoundOldName("originalCustomsGroupItem"));
                    props.add(new ImportProperty(originalCustomsGroupItemField, customsGroupArticleLM.findLCPByCompoundOldName("originalCustomsGroupItem").getMapping(itemKey), getReplaceOnlyNull(importColumns, "originalCustomsGroupItem")));
                    props.add(new ImportProperty(originalCustomsGroupItemField, customsGroupArticleLM.findLCPByCompoundOldName("originalCustomsGroupArticle").getMapping(articleKey), getReplaceOnlyNull(importColumns, "originalCustomsGroupItem")));
                    fields.add(originalCustomsGroupItemField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).originalCustomsGroupItem);
                }

                if (showField(userInvoiceDetailsList, "idColor")) {
                    ImportField idColorField = new ImportField(itemArticleLM.findLCPByCompoundOldName("idColor"));
                    ImportKey<?> colorKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Color"),
                            itemArticleLM.findLCPByCompoundOldName("colorId").getMapping(idColorField));
                    keys.add(colorKey);
                    props.add(new ImportProperty(idColorField, itemArticleLM.findLCPByCompoundOldName("idColor").getMapping(colorKey), getReplaceOnlyNull(importColumns, "idColor")));
                    props.add(new ImportProperty(idColorField, itemArticleLM.findLCPByCompoundOldName("colorItem").getMapping(itemKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Color")).getMapping(colorKey), getReplaceOnlyNull(importColumns, "idColor")));
                    props.add(new ImportProperty(idColorField, itemArticleLM.findLCPByCompoundOldName("colorArticle").getMapping(articleKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Color")).getMapping(colorKey), getReplaceOnlyNull(importColumns, "idColor")));
                    fields.add(idColorField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idColor);

                    if (showField(userInvoiceDetailsList, "nameColor")) {
                        addDataField(itemArticleLM, props, fields, importColumns, "nameColor", "nameColor", colorKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameColor);
                    }
                }

                if (showField(userInvoiceDetailsList, "idSize")) {
                    ImportField idSizeField = new ImportField(itemArticleLM.findLCPByCompoundOldName("idSize"));
                    ImportKey<?> sizeKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Size"),
                            itemArticleLM.findLCPByCompoundOldName("sizeId").getMapping(idSizeField));
                    keys.add(sizeKey);
                    props.add(new ImportProperty(idSizeField, itemArticleLM.findLCPByCompoundOldName("idSize").getMapping(sizeKey), getReplaceOnlyNull(importColumns, "idSize")));
                    props.add(new ImportProperty(idSizeField, itemArticleLM.findLCPByCompoundOldName("sizeItem").getMapping(itemKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Size")).getMapping(sizeKey), getReplaceOnlyNull(importColumns, "idSize")));
                    fields.add(idSizeField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idSize);

                    if (showField(userInvoiceDetailsList, "nameSize")) {
                        addDataField(itemArticleLM, props, fields, importColumns, "nameSize", "nameSize", sizeKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameSize);

                        addDataField(itemArticleLM, props, fields, importColumns, "shortNameSize", "nameSize", sizeKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameSize);
                    }

                    if (showField(userInvoiceDetailsList, "nameOriginalSize")) {
                        addDataField(itemArticleLM, props, fields, importColumns, "nameOriginalSize", "nameOriginalSize", sizeKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).nameOriginalSize);
                    }
                }

                if (showField(userInvoiceDetailsList, "idSeasonYear")) {
                    ImportField idSeasonYearField = new ImportField(itemArticleLM.findLCPByCompoundOldName("idSeasonYear"));
                    ImportKey<?> seasonYearKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("SeasonYear"),
                            itemArticleLM.findLCPByCompoundOldName("seasonYearId").getMapping(idSeasonYearField));
                    keys.add(seasonYearKey);
                    props.add(new ImportProperty(idSeasonYearField, itemArticleLM.findLCPByCompoundOldName("idSeasonYear").getMapping(seasonYearKey), getReplaceOnlyNull(importColumns, "idSeasonYear")));
                    props.add(new ImportProperty(idSeasonYearField, itemArticleLM.findLCPByCompoundOldName("nameSeasonYear").getMapping(seasonYearKey), getReplaceOnlyNull(importColumns, "idSeasonYear")));
                    props.add(new ImportProperty(idSeasonYearField, itemArticleLM.findLCPByCompoundOldName("seasonYearArticle").getMapping(articleKey),
                            LM.object(itemArticleLM.findClassByCompoundName("SeasonYear")).getMapping(seasonYearKey), getReplaceOnlyNull(importColumns, "idSeasonYear")));
                    props.add(new ImportProperty(idSeasonYearField, itemArticleLM.findLCPByCompoundOldName("seasonYearItem").getMapping(itemKey),
                            LM.object(itemArticleLM.findClassByCompoundName("SeasonYear")).getMapping(seasonYearKey), getReplaceOnlyNull(importColumns, "idSeasonYear")));
                    fields.add(idSeasonYearField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idSeasonYear);
                }

                if (showField(userInvoiceDetailsList, "idBrand")) {
                    ImportField idBrandField = new ImportField(itemArticleLM.findLCPByCompoundOldName("idBrand"));
                    ImportKey<?> brandKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Brand"),
                            itemArticleLM.findLCPByCompoundOldName("brandId").getMapping(idBrandField));
                    keys.add(brandKey);
                    props.add(new ImportProperty(idBrandField, itemArticleLM.findLCPByCompoundOldName("idBrand").getMapping(brandKey), getReplaceOnlyNull(importColumns, "idBrand")));
                    props.add(new ImportProperty(idBrandField, itemArticleLM.findLCPByCompoundOldName("brandArticle").getMapping(articleKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Brand")).getMapping(brandKey), getReplaceOnlyNull(importColumns, "idBrand")));
                    props.add(new ImportProperty(idBrandField, itemArticleLM.findLCPByCompoundOldName("brandItem").getMapping(itemKey),
                            LM.object(itemArticleLM.findClassByCompoundName("Brand")).getMapping(brandKey), getReplaceOnlyNull(importColumns, "idBrand")));
                    fields.add(idBrandField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idBrand);

                    if (showField(userInvoiceDetailsList, "nameBrand")) {
                        addDataField(itemArticleLM, props, fields, importColumns, "nameBrand", "nameBrand", brandKey);
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
                        props.add(new ImportProperty(idSeasonField, itemFashionLM.findLCPByCompoundOldName("idSeason").getMapping(seasonKey), getReplaceOnlyNull(importColumns, "idSeason")));
                        props.add(new ImportProperty(idSeasonField, itemFashionLM.findLCPByCompoundOldName("seasonArticle").getMapping(articleKey),
                                LM.object(itemFashionLM.findClassByCompoundName("Season")).getMapping(seasonKey), getReplaceOnlyNull(importColumns, "idSeason")));
                        props.add(new ImportProperty(idSeasonField, itemFashionLM.findLCPByCompoundOldName("seasonItem").getMapping(itemKey),
                                LM.object(itemFashionLM.findClassByCompoundName("Season")).getMapping(seasonKey), getReplaceOnlyNull(importColumns, "idSeason")));
                        fields.add(idSeasonField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).idSeason);

                        if (showField(userInvoiceDetailsList, "nameSeason")) {
                            addDataField(itemFashionLM, props, fields, importColumns, "nameSeason", "nameSeason", seasonKey);
                            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                                data.get(i).add(userInvoiceDetailsList.get(i).nameSeason);
                        }
                    }

                    if (showField(userInvoiceDetailsList, "idCollection")) {
                        ImportField idCollectionField = new ImportField(itemFashionLM.findLCPByCompoundOldName("idCollection"));
                        ImportKey<?> collectionKey = new ImportKey((ConcreteCustomClass) itemFashionLM.findClassByCompoundName("Collection"),
                                itemFashionLM.findLCPByCompoundOldName("collectionId").getMapping(idCollectionField));
                        keys.add(collectionKey);
                        props.add(new ImportProperty(idCollectionField, itemFashionLM.findLCPByCompoundOldName("idCollection").getMapping(collectionKey), getReplaceOnlyNull(importColumns, "idCollection")));
                        props.add(new ImportProperty(idCollectionField, itemFashionLM.findLCPByCompoundOldName("collectionArticle").getMapping(articleKey),
                                LM.object(itemFashionLM.findClassByCompoundName("Collection")).getMapping(collectionKey), getReplaceOnlyNull(importColumns, "idCollection")));
                        fields.add(idCollectionField);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).idCollection);

                        if (showField(userInvoiceDetailsList, "nameCollection")) {
                            addDataField(itemFashionLM, props, fields, importColumns, "nameCollection", "nameCollection", collectionKey);
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
                            purchaseComplianceLM.findLCPByCompoundOldName("complianceId").getMapping(numberComplianceField));
                    keys.add(complianceKey);
                    props.add(new ImportProperty(numberComplianceField, purchaseComplianceLM.findLCPByCompoundOldName("numberCompliance").getMapping(complianceKey), getReplaceOnlyNull(importColumns, "numberCompliance")));
                    props.add(new ImportProperty(numberComplianceField, purchaseComplianceLM.findLCPByCompoundOldName("idCompliance").getMapping(complianceKey), getReplaceOnlyNull(importColumns, "numberCompliance")));
                    props.add(new ImportProperty(numberComplianceField, purchaseComplianceLM.findLCPByCompoundOldName("complianceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                            LM.object(purchaseComplianceLM.findClassByCompoundName("Compliance")).getMapping(complianceKey), getReplaceOnlyNull(importColumns, "numberCompliance")));
                    fields.add(numberComplianceField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).numberCompliance);


                    if (showField(userInvoiceDetailsList, "dateCompliance")) {
                        addDataField(purchaseComplianceLM, props, fields, importColumns, "dateCompliance", "dateCompliance", complianceKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).dateCompliance);

                        addDataField(purchaseComplianceLM, props, fields, importColumns, "fromDateCompliance", "dateCompliance", complianceKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).dateCompliance);
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
        }
        return true;
    }

    protected List<List<PurchaseInvoiceDetail>> importUserInvoicesFromFile(ExecutionContext context, DataSession session, DataObject userInvoiceObject,
                                                                           Map<String, ImportColumnDetail> importColumns, byte[] file, String fileExtension,
                                                                           Integer startRow, Boolean isPosted, String csvSeparator,
                                                                           String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit)
            throws ParseException, UniversalImportException, IOException, SQLException, xBaseJException, ScriptingErrorLog.SemanticErrorException, BiffException, SQLHandledException {

        List<List<PurchaseInvoiceDetail>> userInvoiceDetailsList;

        if (fileExtension.equals("DBF"))
            userInvoiceDetailsList = importUserInvoicesFromDBF(session, file, importColumns, primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, userInvoiceObject);
        else if (fileExtension.equals("XLS"))
            userInvoiceDetailsList = importUserInvoicesFromXLS(context, session, file, importColumns, primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, userInvoiceObject);
        else if (fileExtension.equals("XLSX"))
            userInvoiceDetailsList = importUserInvoicesFromXLSX(session, file, importColumns, primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, userInvoiceObject);
        else if (fileExtension.equals("CSV"))
            userInvoiceDetailsList = importUserInvoicesFromCSV(session, file, importColumns, primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, csvSeparator, userInvoiceObject);
        else
            userInvoiceDetailsList = null;

        return userInvoiceDetailsList;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromXLS(ExecutionContext context, DataSession session, byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                                        String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit, Integer startRow,
                                                                        Boolean isPosted, DataObject userInvoiceObject)
            throws IOException, BiffException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<PurchaseInvoiceDetail>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<PurchaseInvoiceDetail>();

        String primaryKeyColumn = getKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getKeyColumn(secondaryKeyType);

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

            Date currentDateDocument = userInvoiceObject == null ? new Date(Calendar.getInstance().getTime().getTime()) :
                    (Date) getLCP("Purchase.dateUserInvoice").read(session, userInvoiceObject);
            currentTimestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Calendar.getInstance().getTime());

            for (int i = startRow - 1; i < sheet.getRows(); i++) {
                String numberDocument = getXLSFieldValue(sheet, i, importColumns.get("numberDocument"));
                String idDocument = getXLSFieldValue(sheet, i, importColumns.get("idDocument"), numberDocument);
                Date dateDocument = getXLSDateFieldValue(sheet, i, importColumns.get("dateDocument"));
                String idSupplier = getXLSFieldValue(sheet, i, importColumns.get("idSupplier"));
                String idSupplierStock = getXLSFieldValue(sheet, i, importColumns.get("idSupplierStock"));
                String currencyDocument = getXLSFieldValue(sheet, i, importColumns.get("currencyDocument"));
                String idUserInvoiceDetail = makeIdUserInvoiceDetail(idDocument, userInvoiceObject, i);
                String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSFieldValue(sheet, i, importColumns.get("barcodeItem")));
                String originalCustomsGroupItem = getXLSFieldValue(sheet, i, importColumns.get("originalCustomsGroupItem"));
                String idBatch = getXLSFieldValue(sheet, i, importColumns.get("idBatch"));
                BigDecimal dataIndexValue = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("dataIndex"));
                Integer dataIndex = dataIndexValue == null ? (primaryList.size() + secondaryList.size() + 1) : dataIndexValue.intValue();
                String idItem = getXLSFieldValue(sheet, i, importColumns.get("idItem"));
                String idItemGroup = getXLSFieldValue(sheet, i, importColumns.get("idItemGroup"));
                String captionItem = getXLSFieldValue(sheet, i, importColumns.get("captionItem"));
                String originalCaptionItem = getXLSFieldValue(sheet, i, importColumns.get("originalCaptionItem"));
                String UOMItem = getXLSFieldValue(sheet, i, importColumns.get("UOMItem"));
                String idManufacturer = getXLSFieldValue(sheet, i, importColumns.get("idManufacturer"));
                String nameManufacturer = getXLSFieldValue(sheet, i, importColumns.get("nameManufacturer"));
                String nameCountry = modifyNameCountry(getXLSFieldValue(sheet, i, importColumns.get("nameCountry")));
                String nameOriginCountry = modifyNameCountry(getXLSFieldValue(sheet, i, importColumns.get("nameOriginCountry")));
                String importCountryBatch = getXLSFieldValue(sheet, i, importColumns.get("importCountryBatch"));
                String idCustomerStock = getXLSFieldValue(sheet, i, importColumns.get("idCustomerStock"));
                ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
                ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
                String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
                BigDecimal quantity = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("quantity"));
                BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("price"));
                if (price != null && price.compareTo(new BigDecimal("100000000000")) > 0)
                    price = null;
                BigDecimal sum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("sum"));
                BigDecimal valueVAT = parseVAT(getXLSFieldValue(sheet, i, importColumns.get("valueVAT")));
                BigDecimal sumVAT = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("sumVAT"));
                Date dateVAT = dateDocument == null ? currentDateDocument : dateDocument;
                BigDecimal invoiceSum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("invoiceSum"));
                BigDecimal manufacturingPrice = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("manufacturingPrice"));
                String contractPrice = getXLSFieldValue(sheet, i, importColumns.get("contractPrice"));
                BigDecimal shipmentPrice = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("shipmentPrice"));
                BigDecimal shipmentSum = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("shipmentSum"));
                BigDecimal rateExchange = getXLSBigDecimalFieldValue(sheet, i, importColumns.get("rateExchange"));
                String numberCompliance = getXLSFieldValue(sheet, i, importColumns.get("numberCompliance"));
                Date dateCompliance = getXLSDateFieldValue(sheet, i, importColumns.get("dateCompliance"));
                String declaration = getXLSFieldValue(sheet, i, importColumns.get("declaration"));
                Date expiryDate = getXLSDateFieldValue(sheet, i, importColumns.get("expiryDate"), true);
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

                PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(isPosted, idDocument, numberDocument,
                        dateDocument, idSupplier, idSupplierStock, currencyDocument, idUserInvoiceDetail, barcodeItem, idBatch,
                        dataIndex, idItem, idItemGroup, originalCustomsGroupItem, captionItem, originalCaptionItem, UOMItem,
                        idManufacturer, nameManufacturer, nameCountry, nameOriginCountry, importCountryBatch, idCustomer,
                        idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT, dateVAT, defaultCountry,
                        invoiceSum, manufacturingPrice, contractPrice, shipmentPrice, shipmentSum, rateExchange,
                        numberCompliance, dateCompliance, declaration, expiryDate, manufactureDate, pharmacyPriceGroupItem,
                        seriesPharmacy, idArticle, captionArticle, originalCaptionArticle, idColor, nameColor, idCollection,
                        nameCollection, idSize, nameSize, nameOriginalSize, idSeasonYear, idSeason, nameSeason, idBrand,
                        nameBrand, idBox, nameBox, idTheme, nameTheme, netWeight, netWeightSum, grossWeight, grossWeightSum,
                        composition, originalComposition);

                String primaryKeyColumnValue = getXLSFieldValue(sheet, i, importColumns.get(primaryKeyColumn));
                String secondaryKeyColumnValue = getXLSFieldValue(sheet, i, importColumns.get(secondaryKeyColumn));
                if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, keyIsDigit, session, primaryKeyType, checkExistence))
                    primaryList.add(purchaseInvoiceDetail);
                else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, keyIsDigit))
                    primaryList.add(purchaseInvoiceDetail);
            }
            currentTimestamp = null;

            return Arrays.asList(primaryList, secondaryList);
        } else
            return null;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromCSV(DataSession session, byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                                        String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit,
                                                                        Integer startRow, Boolean isPosted, String csvSeparator, DataObject userInvoiceObject)
            throws IOException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<PurchaseInvoiceDetail>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<PurchaseInvoiceDetail>();

        String primaryKeyColumn = getKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getKeyColumn(secondaryKeyType);
        
        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile), "cp1251"));
        String line;


        Date currentDateDocument = userInvoiceObject == null ? new Date(Calendar.getInstance().getTime().getTime()) :
                (Date) getLCP("Purchase.dateUserInvoice").read(session, userInvoiceObject);
        currentTimestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Calendar.getInstance().getTime());

        List<String[]> valuesList = new ArrayList<String[]>();
        while ((line = br.readLine()) != null) {
                valuesList.add(line.split(csvSeparator));
        }

        for (int count = startRow; count <= valuesList.size(); count++) {
            
            String numberDocument = getCSVFieldValue(valuesList, importColumns.get("numberDocument"), count);
            String idDocument = getCSVFieldValue(valuesList, importColumns.get("idDocument"), count, numberDocument);
            Date dateDocument = getCSVDateFieldValue(valuesList, importColumns.get("dateDocument"), count);
            String idSupplier = getCSVFieldValue(valuesList, importColumns.get("idSupplier"), count);
            String idSupplierStock = getCSVFieldValue(valuesList, importColumns.get("idSupplierStock"), count);
            String currencyDocument = getCSVFieldValue(valuesList, importColumns.get("currencyDocument"), count);
            String idUserInvoiceDetail = makeIdUserInvoiceDetail(idDocument, userInvoiceObject, count);
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getCSVFieldValue(valuesList, importColumns.get("barcodeItem"), count));
            String idBatch = getCSVFieldValue(valuesList, importColumns.get("idBatch"), count);
            BigDecimal dataIndexValue = getCSVBigDecimalFieldValue(valuesList, importColumns.get("dataIndex"), count);
            Integer dataIndex = dataIndexValue == null ? (primaryList.size() + secondaryList.size() + 1) : dataIndexValue.intValue();
            String idItem = getCSVFieldValue(valuesList, importColumns.get("idItem"), count);
            String idItemGroup = getCSVFieldValue(valuesList, importColumns.get("idItemGroup"), count);
            String originalCustomsGroupItem = getCSVFieldValue(valuesList, importColumns.get("originalCustomsGroupItem"), count);
            String captionItem = getCSVFieldValue(valuesList, importColumns.get("captionItem"), count);
            String originalCaptionItem = getCSVFieldValue(valuesList, importColumns.get("originalCaptionItem"), count);
            String UOMItem = getCSVFieldValue(valuesList, importColumns.get("UOMItem"), count);
            String idManufacturer = getCSVFieldValue(valuesList, importColumns.get("idManufacturer"), count);
            String nameManufacturer = getCSVFieldValue(valuesList, importColumns.get("nameManufacturer"), count);
            String nameCountry = modifyNameCountry(getCSVFieldValue(valuesList, importColumns.get("nameCountry"), count));
            String nameOriginCountry = modifyNameCountry(getCSVFieldValue(valuesList, importColumns.get("nameOriginCountry"), count));
            String importCountryBatch = getCSVFieldValue(valuesList, importColumns.get("importCountryBatch"), count);
            String idCustomerStock = getCSVFieldValue(valuesList, importColumns.get("idCustomerStock"), count);
            ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getCSVBigDecimalFieldValue(valuesList, importColumns.get("quantity"), count);
            BigDecimal price = getCSVBigDecimalFieldValue(valuesList, importColumns.get("price"), count);
            if (price != null && price.compareTo(new BigDecimal("100000000000")) > 0)
                price = null;
            BigDecimal sum = getCSVBigDecimalFieldValue(valuesList, importColumns.get("sum"), count);
            BigDecimal valueVAT = parseVAT(getCSVFieldValue(valuesList, importColumns.get("valueVAT"), count));
            BigDecimal sumVAT = getCSVBigDecimalFieldValue(valuesList, importColumns.get("sumVAT"), count);
            Date dateVAT = dateDocument == null ? currentDateDocument : dateDocument;
            BigDecimal invoiceSum = getCSVBigDecimalFieldValue(valuesList, importColumns.get("invoiceSum"), count);
            BigDecimal manufacturingPrice = getCSVBigDecimalFieldValue(valuesList, importColumns.get("manufacturingPrice"), count);
            String contractPrice = getCSVFieldValue(valuesList, importColumns.get("contractPrice"), count);
            BigDecimal shipmentPrice = getCSVBigDecimalFieldValue(valuesList, importColumns.get("shipmentPrice"), count);
            BigDecimal shipmentSum = getCSVBigDecimalFieldValue(valuesList, importColumns.get("shipmentSum"), count);
            BigDecimal rateExchange = getCSVBigDecimalFieldValue(valuesList, importColumns.get("rateExchange"), count);
            String numberCompliance = getCSVFieldValue(valuesList, importColumns.get("numberCompliance"), count);
            Date dateCompliance = getCSVDateFieldValue(valuesList, importColumns.get("dateCompliance"), count);
            String declaration = getCSVFieldValue(valuesList, importColumns.get("declaration"), count);
            Date expiryDate = getCSVDateFieldValue(valuesList, importColumns.get("expiryDate"), count, true);
            Date manufactureDate = getCSVDateFieldValue(valuesList, importColumns.get("manufactureDate"), count);
            String pharmacyPriceGroupItem = getCSVFieldValue(valuesList, importColumns.get("pharmacyPriceGroupItem"), count);
            String seriesPharmacy = getCSVFieldValue(valuesList, importColumns.get("seriesPharmacy"), count);
            String idArticle = getCSVFieldValue(valuesList, importColumns.get("idArticle"), count);
            String captionArticle = getCSVFieldValue(valuesList, importColumns.get("captionArticle"), count);
            String originalCaptionArticle = getCSVFieldValue(valuesList, importColumns.get("originalCaptionArticle"), count);
            String idColor = getCSVFieldValue(valuesList, importColumns.get("idColor"), count);
            String nameColor = getCSVFieldValue(valuesList, importColumns.get("nameColor"), count);
            String idCollection = getCSVFieldValue(valuesList, importColumns.get("idCollection"), count);
            String nameCollection = getCSVFieldValue(valuesList, importColumns.get("nameCollection"), count);
            String idSize = getCSVFieldValue(valuesList, importColumns.get("idSize"), count);
            String nameSize = getCSVFieldValue(valuesList, importColumns.get("nameSize"), count);
            String nameOriginalSize = getCSVFieldValue(valuesList, importColumns.get("nameOriginalSize"), count);
            String idSeasonYear = getCSVFieldValue(valuesList, importColumns.get("idSeasonYear"), count);
            String idSeason = getCSVFieldValue(valuesList, importColumns.get("idSeason"), count);
            String nameSeason = getCSVFieldValue(valuesList, importColumns.get("nameSeason"), count);
            String idBrand = getCSVFieldValue(valuesList, importColumns.get("idBrand"), count);
            String nameBrand = getCSVFieldValue(valuesList, importColumns.get("nameBrand"), count);
            String idBox = getCSVFieldValue(valuesList, importColumns.get("idBox"), count);
            String nameBox = getCSVFieldValue(valuesList, importColumns.get("nameBox"), count);
            String idTheme = getCSVFieldValue(valuesList, importColumns.get("idTheme"), count);
            String nameTheme = getCSVFieldValue(valuesList, importColumns.get("nameTheme"), count);
            BigDecimal netWeight = getCSVBigDecimalFieldValue(valuesList, importColumns.get("netWeight"), count);
            BigDecimal netWeightSum = getCSVBigDecimalFieldValue(valuesList, importColumns.get("netWeightSum"), count);
            netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
            BigDecimal grossWeight = getCSVBigDecimalFieldValue(valuesList, importColumns.get("grossWeight"), count);
            BigDecimal grossWeightSum = getCSVBigDecimalFieldValue(valuesList, importColumns.get("grossWeight"), count);
            grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;
            String composition = getCSVFieldValue(valuesList, importColumns.get("composition"), count);
            String originalComposition = getCSVFieldValue(valuesList, importColumns.get("originalComposition"), count);

            PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(isPosted, idDocument, numberDocument,
                    dateDocument, idSupplier, idSupplierStock, currencyDocument, idUserInvoiceDetail, barcodeItem,
                    idBatch, dataIndex, idItem, idItemGroup, originalCustomsGroupItem, captionItem, originalCaptionItem,
                    UOMItem, idManufacturer, nameManufacturer, nameCountry, nameOriginCountry, importCountryBatch,
                    idCustomer, idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT, dateVAT,
                    defaultCountry, invoiceSum, manufacturingPrice, contractPrice, shipmentPrice, shipmentSum,
                    rateExchange, numberCompliance, dateCompliance, declaration, expiryDate, manufactureDate,
                    pharmacyPriceGroupItem, seriesPharmacy, idArticle, captionArticle, originalCaptionArticle,
                    idColor, nameColor, idCollection, nameCollection, idSize, nameSize, nameOriginalSize,
                    idSeasonYear, idSeason, nameSeason, idBrand, nameBrand, idBox, nameBox, idTheme, nameTheme,
                    netWeight, netWeightSum, grossWeight, grossWeightSum, composition, originalComposition);

            String primaryKeyColumnValue = getCSVFieldValue(valuesList, importColumns.get(primaryKeyColumn), count);
            String secondaryKeyColumnValue = getCSVFieldValue(valuesList, importColumns.get(secondaryKeyColumn), count);
            if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, keyIsDigit, session, primaryKeyType, checkExistence))
                primaryList.add(purchaseInvoiceDetail);
            else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, keyIsDigit))
                secondaryList.add(purchaseInvoiceDetail);

        }
        currentTimestamp = null;

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromXLSX(DataSession session, byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                                         String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit,
                                                                         Integer startRow, Boolean isPosted, DataObject userInvoiceObject)
            throws IOException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<PurchaseInvoiceDetail>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<PurchaseInvoiceDetail>();

        String primaryKeyColumn = getKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getKeyColumn(secondaryKeyType);

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        Date currentDateDocument = userInvoiceObject == null ? new Date(Calendar.getInstance().getTime().getTime()) :
                (Date) getLCP("Purchase.dateUserInvoice").read(session, userInvoiceObject);
        currentTimestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Calendar.getInstance().getTime());

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {

            String numberDocument = getXLSXFieldValue(sheet, i, importColumns.get("numberDocument"));
            String idDocument = getXLSXFieldValue(sheet, i, importColumns.get("idDocument"), numberDocument);
            Date dateDocument = getXLSXDateFieldValue(sheet, i, importColumns.get("dateDocument"));
            String idSupplier = getXLSXFieldValue(sheet, i, importColumns.get("idSupplier"));
            String idSupplierStock = getXLSXFieldValue(sheet, i, importColumns.get("idSupplierStock"));
            String currencyDocument = getXLSXFieldValue(sheet, i, importColumns.get("currencyDocument"));
            String idUserInvoiceDetail = makeIdUserInvoiceDetail(idDocument, userInvoiceObject, i);
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSXFieldValue(sheet, i, importColumns.get("barcodeItem")));
            String idBatch = getXLSXFieldValue(sheet, i, importColumns.get("idBatch"));
            BigDecimal dataIndexValue = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("dataIndex"));
            Integer dataIndex = dataIndexValue == null ? (primaryList.size() + secondaryList.size() + 1) : dataIndexValue.intValue();
            String idItem = getXLSXFieldValue(sheet, i, importColumns.get("idItem"));
            String idItemGroup = getXLSXFieldValue(sheet, i, importColumns.get("idItemGroup"));
            String originalCustomsGroupItem = getXLSXFieldValue(sheet, i, importColumns.get("originalCustomsGroupItem"));
            String captionItem = getXLSXFieldValue(sheet, i, importColumns.get("captionItem"));
            String originalCaptionItem = getXLSXFieldValue(sheet, i, importColumns.get("originalCaptionItem"));
            String UOMItem = getXLSXFieldValue(sheet, i, importColumns.get("UOMItem"));
            String idManufacturer = getXLSXFieldValue(sheet, i, importColumns.get("idManufacturer"));
            String nameManufacturer = getXLSXFieldValue(sheet, i, importColumns.get("nameManufacturer"));
            String nameCountry = modifyNameCountry(getXLSXFieldValue(sheet, i, importColumns.get("nameCountry")));
            String nameOriginCountry = modifyNameCountry(getXLSXFieldValue(sheet, i, importColumns.get("nameOriginCountry")));
            String importCountryBatch = getXLSXFieldValue(sheet, i, importColumns.get("importCountryBatch"));
            String idCustomerStock = getXLSXFieldValue(sheet, i, importColumns.get("idCustomerStock"));
            ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("quantity"));
            BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("price"));
            if (price != null && price.compareTo(new BigDecimal("100000000000")) > 0)
                price = null;
            BigDecimal sum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("sum"));
            BigDecimal valueVAT = parseVAT(getXLSXFieldValue(sheet, i, importColumns.get("valueVAT")));
            BigDecimal sumVAT = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("sumVAT"));
            Date dateVAT = dateDocument == null ? currentDateDocument : dateDocument;
            BigDecimal invoiceSum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("invoiceSum"));
            BigDecimal manufacturingPrice = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("manufacturingPrice"));
            String contractPrice = getXLSXFieldValue(sheet, i, importColumns.get("contractPrice"));
            BigDecimal shipmentPrice = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("shipmentPrice"));
            BigDecimal shipmentSum = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("shipmentSum"));
            BigDecimal rateExchange = getXLSXBigDecimalFieldValue(sheet, i, importColumns.get("rateExchange"));
            String numberCompliance = getXLSXFieldValue(sheet, i, importColumns.get("numberCompliance"));
            Date dateCompliance = getXLSXDateFieldValue(sheet, i, importColumns.get("dateCompliance"));
            String declaration = getXLSXFieldValue(sheet, i, importColumns.get("declaration"));
            Date expiryDate = getXLSXDateFieldValue(sheet, i, importColumns.get("expiryDate"), true);
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

            PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(isPosted, idDocument, numberDocument,
                    dateDocument, idSupplier, idSupplierStock, currencyDocument, idUserInvoiceDetail, barcodeItem,
                    idBatch, dataIndex, idItem, idItemGroup, originalCustomsGroupItem, captionItem, originalCaptionItem,
                    UOMItem, idManufacturer, nameManufacturer, nameCountry, nameOriginCountry, importCountryBatch,
                    idCustomer, idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT, dateVAT, defaultCountry,
                    invoiceSum, manufacturingPrice, contractPrice, shipmentPrice, shipmentSum, rateExchange,
                    numberCompliance, dateCompliance, declaration, expiryDate, manufactureDate, pharmacyPriceGroupItem,
                    seriesPharmacy, idArticle, captionArticle, originalCaptionArticle, idColor, nameColor, idCollection,
                    nameCollection, idSize, nameSize, nameOriginalSize, idSeasonYear, idSeason, nameSeason, idBrand,
                    nameBrand, idBox, nameBox, idTheme, nameTheme, netWeight, netWeightSum, grossWeight, grossWeightSum,
                    composition, originalComposition);

            String primaryKeyColumnValue = getXLSXFieldValue(sheet, i, importColumns.get(primaryKeyColumn));
            String secondaryKeyColumnValue = getXLSXFieldValue(sheet, i, importColumns.get(secondaryKeyColumn));
            if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, keyIsDigit, session, primaryKeyType, checkExistence))
                primaryList.add(purchaseInvoiceDetail);
            else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, keyIsDigit))
                primaryList.add(purchaseInvoiceDetail);
        }
        currentTimestamp = null;

        return Arrays.asList(primaryList, secondaryList);
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromDBF(DataSession session, byte[] importFile, Map<String, ImportColumnDetail> importColumns,
                                                                        String primaryKeyType, boolean checkExistence, String secondaryKeyType, boolean keyIsDigit,
                                                                        Integer startRow, Boolean isPosted, DataObject userInvoiceObject)
            throws IOException, xBaseJException, UniversalImportException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<PurchaseInvoiceDetail>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<PurchaseInvoiceDetail>();

        String primaryKeyColumn = getKeyColumn(primaryKeyType);
        String secondaryKeyColumn = getKeyColumn(secondaryKeyType);
        
        File tempFile = File.createTempFile("purchaseInvoice", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);

        DBF file = new DBF(tempFile.getPath());
        String charset = getDBFCharset(tempFile);

        int totalRecordCount = file.getRecordCount();

        Date currentDateDocument = userInvoiceObject == null ? new Date(Calendar.getInstance().getTime().getTime()) : 
                (Date) getLCP("Purchase.dateUserInvoice").read(session, userInvoiceObject);
        currentTimestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Calendar.getInstance().getTime());
        for (int i = 0; i < startRow - 1; i++) {
            file.read();
        }

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String numberDocument = getDBFFieldValue(file, importColumns.get("numberDocument"), i, charset);
            String idDocument = getDBFFieldValue(file, importColumns.get("idDocument"), i, charset, numberDocument);
            Date dateDocument = getDBFDateFieldValue(file, importColumns.get("dateDocument"), i, charset);
            String idSupplier = getDBFFieldValue(file, importColumns.get("idSupplier"), i, charset);
            String idSupplierStock = getDBFFieldValue(file, importColumns.get("idSupplierStock"), i, charset);
            String currencyDocument = getDBFFieldValue(file, importColumns.get("currencyDocument"), i, charset);
            String idUserInvoiceDetail = makeIdUserInvoiceDetail(idDocument, userInvoiceObject, i);
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getDBFFieldValue(file, importColumns.get("barcodeItem"), i, charset));
            String idBatch = getDBFFieldValue(file, importColumns.get("idBatch"), i, charset);
            BigDecimal dataIndexValue = getDBFBigDecimalFieldValue(file, importColumns.get("dataIndex"), i, charset, new BigDecimal(primaryList.size() + secondaryList.size() + 1));
            Integer dataIndex = dataIndexValue == null ? null : dataIndexValue.intValue();
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"), i, charset);
            String idItemGroup = getDBFFieldValue(file, importColumns.get("idItemGroup"), i, charset);
            String originalCustomsGroupItem = getDBFFieldValue(file, importColumns.get("originalCustomsGroupItem"), i, charset);
            String captionItem = getDBFFieldValue(file, importColumns.get("captionItem"), i, charset);
            String originalCaptionItem = getDBFFieldValue(file, importColumns.get("originalCaptionItem"), i, charset);
            String UOMItem = getDBFFieldValue(file, importColumns.get("UOMItem"), i, charset);
            String idManufacturer = getDBFFieldValue(file, importColumns.get("idManufacturer"), i, charset);
            String nameManufacturer = getDBFFieldValue(file, importColumns.get("nameManufacturer"), i, charset);
            String nameCountry = modifyNameCountry(getDBFFieldValue(file, importColumns.get("nameCountry"), i, charset));
            String nameOriginCountry = modifyNameCountry(getDBFFieldValue(file, importColumns.get("nameOriginCountry"), i, charset));
            String importCountryBatch = getDBFFieldValue(file, importColumns.get("importCountryBatch"), i, charset);
            String idCustomerStock = getDBFFieldValue(file, importColumns.get("idCustomerStock"), i, charset);
            ObjectValue customerStockObject = idCustomerStock == null ? null : getLCP("stockId").readClasses(session, new DataObject(idCustomerStock));
            ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : getLCP("legalEntityStock").readClasses(session, (DataObject) customerStockObject));
            String idCustomer = (String) (customerObject == null ? null : getLCP("idLegalEntity").read(session, customerObject));
            BigDecimal quantity = getDBFBigDecimalFieldValue(file, importColumns.get("quantity"), i, charset);
            BigDecimal price = getDBFBigDecimalFieldValue(file, importColumns.get("price"), i, charset);
            if (price != null && price.compareTo(new BigDecimal("100000000000")) > 0)
                price = null;
            BigDecimal sum = getDBFBigDecimalFieldValue(file, importColumns.get("sum"), i, charset);
            BigDecimal valueVAT = parseVAT(getDBFFieldValue(file, importColumns.get("valueVAT"), i, charset));
            BigDecimal sumVAT = getDBFBigDecimalFieldValue(file, importColumns.get("sumVAT"), i, charset);
            Date dateVAT = dateDocument == null ? currentDateDocument : dateDocument;
            BigDecimal invoiceSum = getDBFBigDecimalFieldValue(file, importColumns.get("invoiceSum"), i, charset);
            BigDecimal manufacturingPrice = getDBFBigDecimalFieldValue(file, importColumns.get("manufacturingPrice"), i, charset);
            String numberCompliance = getDBFFieldValue(file, importColumns.get("numberCompliance"), i, charset);
            String contractPrice = getDBFFieldValue(file, importColumns.get("contractPrice"), i, charset);
            BigDecimal shipmentPrice = getDBFBigDecimalFieldValue(file, importColumns.get("shipmentPrice"), i, charset);
            BigDecimal shipmentSum = getDBFBigDecimalFieldValue(file, importColumns.get("shipmentSum"), i, charset);
            BigDecimal rateExchange = getDBFBigDecimalFieldValue(file, importColumns.get("rateExchange"), i, charset);
            Date dateCompliance = getDBFDateFieldValue(file, importColumns.get("dateCompliance"), i, charset);
            String declaration = getDBFFieldValue(file, importColumns.get("declaration"), i, charset);
            Date expiryDate = getDBFDateFieldValue(file, importColumns.get("expiryDate"), i, charset, true);
            Date manufactureDate = getDBFDateFieldValue(file, importColumns.get("manufactureDate"), i, charset);
            String pharmacyPriceGroup = getDBFFieldValue(file, importColumns.get("pharmacyPriceGroupItem"), i, charset);
            String seriesPharmacy = getDBFFieldValue(file, importColumns.get("seriesPharmacy"), i, charset);
            String idArticle = getDBFFieldValue(file, importColumns.get("idArticle"), i, charset);
            String captionArticle = getDBFFieldValue(file, importColumns.get("captionArticle"), i, charset);
            String originalCaptionArticle = getDBFFieldValue(file, importColumns.get("originalCaptionArticle"), i, charset);
            String idColor = getDBFFieldValue(file, importColumns.get("idColor"), i, charset);
            String nameColor = getDBFFieldValue(file, importColumns.get("nameColor"), i, charset);
            String idCollection = getDBFFieldValue(file, importColumns.get("idCollection"), i, charset);
            String nameCollection = getDBFFieldValue(file, importColumns.get("nameCollection"), i, charset);
            String idSize = getDBFFieldValue(file, importColumns.get("idSize"), i, charset);
            String nameSize = getDBFFieldValue(file, importColumns.get("nameSize"), i, charset);
            String nameOriginalSize = getDBFFieldValue(file, importColumns.get("nameOriginalSize"), i, charset);
            String idSeasonYear = getDBFFieldValue(file, importColumns.get("idSeasonYear"), i, charset);
            String idSeason = getDBFFieldValue(file, importColumns.get("idSeason"), i, charset);
            String nameSeason = getDBFFieldValue(file, importColumns.get("nameSeason"), i, charset);
            String idBrand = getDBFFieldValue(file, importColumns.get("idBrand"), i, charset);
            String nameBrand = getDBFFieldValue(file, importColumns.get("nameBrand"), i, charset);
            String idBox = getDBFFieldValue(file, importColumns.get("idBox"), i, charset);
            String nameBox = getDBFFieldValue(file, importColumns.get("nameBox"), i, charset);
            String idTheme = getDBFFieldValue(file, importColumns.get("idTheme"), i, charset);
            String nameTheme = getDBFFieldValue(file, importColumns.get("nameTheme"), i, charset);
            BigDecimal netWeight = getDBFBigDecimalFieldValue(file, importColumns.get("netWeight"), i, charset);
            BigDecimal netWeightSum = getDBFBigDecimalFieldValue(file, importColumns.get("netWeightSum"), i, charset);
            netWeight = netWeight == null ? safeDivide(netWeightSum, quantity) : netWeight;
            BigDecimal grossWeight = getDBFBigDecimalFieldValue(file, importColumns.get("grossWeight"), i, charset);
            BigDecimal grossWeightSum = getDBFBigDecimalFieldValue(file, importColumns.get("grossWeightSum"), i, charset);
            grossWeight = grossWeight == null ? safeDivide(grossWeightSum, quantity) : grossWeight;
            String composition = getDBFFieldValue(file, importColumns.get("composition"), i, charset);
            String originalComposition = getDBFFieldValue(file, importColumns.get("originalComposition"), i, charset);

            PurchaseInvoiceDetail purchaseInvoiceDetail = new PurchaseInvoiceDetail(isPosted, idDocument, numberDocument,
                    dateDocument, idSupplier, idSupplierStock, currencyDocument, idUserInvoiceDetail, barcodeItem,
                    idBatch, dataIndex, idItem, idItemGroup, originalCustomsGroupItem, captionItem, originalCaptionItem,
                    UOMItem, idManufacturer, nameManufacturer, nameCountry, nameOriginCountry, importCountryBatch,
                    idCustomer, idCustomerStock, quantity, price, sum, VATifAllowed(valueVAT), sumVAT, dateVAT, defaultCountry,
                    invoiceSum, manufacturingPrice, contractPrice, shipmentPrice, shipmentSum, rateExchange,
                    numberCompliance, dateCompliance, declaration, expiryDate, manufactureDate, pharmacyPriceGroup,
                    seriesPharmacy, idArticle, captionArticle, originalCaptionArticle, idColor, nameColor, idCollection,
                    nameCollection, idSize, nameSize, nameOriginalSize, idSeasonYear, idSeason, nameSeason, idBrand,
                    nameBrand, idBox, nameBox, idTheme, nameTheme, netWeight, netWeightSum, grossWeight, grossWeightSum,
                    composition, originalComposition);

            String primaryKeyColumnValue = getDBFFieldValue(file, importColumns.get(primaryKeyColumn), i, charset);
            String secondaryKeyColumnValue = getDBFFieldValue(file, importColumns.get(secondaryKeyColumn), i, charset);
            if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, keyIsDigit, session, primaryKeyType, checkExistence))
                primaryList.add(purchaseInvoiceDetail);
            else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, keyIsDigit))
                secondaryList.add(purchaseInvoiceDetail);
        }

        currentTimestamp = null;
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

    private String makeIdUserInvoiceDetail(String idDocument, DataObject userInvoiceObject, int i) {
        return (idDocument != null ? idDocument : (userInvoiceObject == null ? "" : String.valueOf(userInvoiceObject.object))) + i;
    }

    private String modifyNameCountry(String nameCountry) {
        return nameCountry == null ? null : trim(nameCountry.replace("*", "")).toUpperCase();
    }
}

