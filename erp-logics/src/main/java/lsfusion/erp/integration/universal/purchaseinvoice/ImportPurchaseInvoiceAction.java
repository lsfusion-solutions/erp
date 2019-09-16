package lsfusion.erp.integration.universal.purchaseinvoice;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import lsfusion.base.Pair;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.erp.integration.universal.ImportPreviewClientAction;
import lsfusion.erp.integration.universal.UniversalImportException;
import lsfusion.erp.stock.BarcodeUtils;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.data.file.CustomStaticFormatFileClass;
import lsfusion.server.logics.classes.data.file.DynamicFormatFileClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.classes.user.CustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.service.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ImportPurchaseInvoiceAction extends ImportDefaultPurchaseInvoiceAction {
    private final ClassPropertyInterface userInvoiceInterface;

    public ImportPurchaseInvoiceAction(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        this(LM, LM.findClass("Purchase.UserInvoice"));
    }
    
    public ImportPurchaseInvoiceAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        userInvoiceInterface = i.next();
    }

    protected void makeCustomImport(ExecutionContext context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props,
                                    LinkedHashMap<String, ImportColumnDetail> defaultColumns, List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data,
                                    ImportKey<?> itemKey, ImportKey<?> userInvoiceDetailKey, String countryKeyType, boolean preImportCountries) throws ScriptingErrorLog.SemanticErrorException {
    }

    protected boolean showImportCountryBatch(List<PurchaseInvoiceDetail> userInvoiceDetailsList) {
        return false;
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeInternal(context);
        try {

            DataObject userInvoiceObject = context.getDataKeyValue(userInvoiceInterface);

            ObjectValue importTypeObject = findProperty("importType[UserInvoice]").readClasses(context, userInvoiceObject);

            if (importTypeObject instanceof DataObject) {

                ObjectValue operationObject = findProperty("autoImportOperation[ImportType]").readClasses(context, (DataObject) importTypeObject);
                ObjectValue supplierObject = findProperty("autoImportSupplier[ImportType]").readClasses(context, (DataObject) importTypeObject);
                ObjectValue supplierStockObject = findProperty("autoImportSupplierStock[ImportType]").readClasses(context, (DataObject) importTypeObject);
                ObjectValue customerObject = findProperty("autoImportCustomer[ImportType]").readClasses(context, (DataObject) importTypeObject);
                ObjectValue customerStockObject = findProperty("autoImportCustomerStock[ImportType]").readClasses(context, (DataObject) importTypeObject);
                boolean checkInvoiceExistence = findProperty("autoImportCheckInvoiceExistence[ImportType]").read(context, (DataObject) importTypeObject) != null;
                boolean completeIdItemAsEAN = findProperty("completeIdItemAsEAN[ImportType]").read(context, (DataObject) importTypeObject) != null;

                String staticCaptionImportType = trim((String) findProperty("staticCaptionImportTypeDetail[ImportType]").read(context, importTypeObject));
                String nameFieldImportType = trim((String) findProperty("staticNameImportTypeDetail[ImportType]").read(context, importTypeObject));
                String[] splittedFieldImportType = nameFieldImportType == null ? null : nameFieldImportType.split("\\.");
                String staticNameImportType = splittedFieldImportType == null ? null : splittedFieldImportType[splittedFieldImportType.length - 1];
                
                List<LinkedHashMap<String, ImportColumnDetail>> importColumns = readImportColumns(context, importTypeObject);
                Set<String> purchaseInvoiceSet = getPurchaseInvoiceSet(context, checkInvoiceExistence);

                ImportDocumentSettings importSettings = readImportDocumentSettings(context, importTypeObject);
                String fileExtension = importSettings.getFileExtension();
                
                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get( fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        RawFileData file = (RawFileData) objectValue.getValue();
                        List<List<PurchaseInvoiceDetail>> userInvoiceDetailData = importUserInvoicesFromFile(context, 
                                userInvoiceObject, importColumns.get(0), importColumns.get(1), purchaseInvoiceSet, completeIdItemAsEAN, checkInvoiceExistence, file, fileExtension, importSettings, staticNameImportType, staticCaptionImportType);

                        boolean needToApply = false;
                        if (userInvoiceDetailData != null && userInvoiceDetailData.size() >= 1) {
                            if(notNullNorEmpty(userInvoiceDetailData.get(0)))
                                needToApply = true;
                            Pair<Integer, DataObject> result = importUserInvoices(userInvoiceDetailData.get(0), context, importColumns.get(0),
                                    importColumns.get(1), userInvoiceObject, importSettings.getPrimaryKeyType(), importSettings.getCountryKeyType(),
                                    operationObject, supplierObject, supplierStockObject, customerObject,
                                    customerStockObject, false);
                            if(userInvoiceObject == null && result.second != null)
                                userInvoiceObject = result.second;
                        }
                        
                        if (userInvoiceDetailData != null && userInvoiceDetailData.size() >= 2) {
                            if(notNullNorEmpty(userInvoiceDetailData.get(1)))
                                needToApply = true;
                            Pair<Integer, DataObject> result = importUserInvoices(userInvoiceDetailData.get(1), context, importColumns.get(0),
                                    importColumns.get(1), userInvoiceObject, importSettings.getSecondaryKeyType(), importSettings.getCountryKeyType(),
                                    operationObject, supplierObject, supplierStockObject, customerObject,
                                    customerStockObject, false);
                            if(userInvoiceObject == null && result.second != null)
                                userInvoiceObject = result.second;
                        }

                        if(userInvoiceObject != null) {
                            findProperty("original[Purchase.Invoice]").change(new DataObject(new FileData(file, fileExtension), DynamicFormatFileClass.get()), context, userInvoiceObject);
                            findProperty("currentInvoice[]").change(userInvoiceObject, context);
                        }

                        boolean cancelSession = false;
                        String script = (String) findProperty("script[ImportType]").read(context, importTypeObject);
                        if(script != null && !script.isEmpty()) {
                            needToApply = true;
                            findAction("executeScript[ImportType]").execute(context, importTypeObject);
                            cancelSession = findProperty("cancelSession[]").read(context) != null;
                        }

                        if(needToApply) {
                            if(cancelSession)
                                context.cancel(SetFact.EMPTY());
                            else 
                                context.apply();
                        }

                        findAction("formRefresh[]").execute(context);
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | IOException | xBaseJException e) {
            throw new RuntimeException(e);
        } catch (UniversalImportException e) {
            e.printStackTrace();
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), e.getTitle()));
        }
    }

    public int makeImport(ExecutionContext<ClassPropertyInterface> context, DataObject userInvoiceObject,
                          DataObject importTypeObject, RawFileData file, String fileExtension, ImportDocumentSettings importSettings,
                          String staticNameImportType, String staticCaptionImportType, boolean completeIdItemAsEAN,
                          boolean checkInvoiceExistence, boolean ignoreInvoicesAfterDocumentsClosedDate)
            throws SQLHandledException, UniversalImportException, IOException, SQLException,
            xBaseJException, ScriptingErrorLog.SemanticErrorException {
        
        List<LinkedHashMap<String, ImportColumnDetail>> importColumns = readImportColumns(context, importTypeObject);
        Set<String> purchaseInvoiceSet = getPurchaseInvoiceSet(context, checkInvoiceExistence);

        ObjectValue operationObject = findProperty("autoImportOperation[ImportType]").readClasses(context, (DataObject) importTypeObject);
        ObjectValue supplierObject = findProperty("autoImportSupplier[ImportType]").readClasses(context, (DataObject) importTypeObject);
        ObjectValue supplierStockObject = findProperty("autoImportSupplierStock[ImportType]").readClasses(context, (DataObject) importTypeObject);
        ObjectValue customerObject = findProperty("autoImportCustomer[ImportType]").readClasses(context, (DataObject) importTypeObject);
        ObjectValue customerStockObject = findProperty("autoImportCustomerStock[ImportType]").readClasses(context, (DataObject) importTypeObject);

        List<List<PurchaseInvoiceDetail>> userInvoiceDetailData = importUserInvoicesFromFile(context,
                userInvoiceObject, importColumns.get(0), importColumns.get(1), purchaseInvoiceSet, completeIdItemAsEAN, checkInvoiceExistence, file, fileExtension,
                importSettings, staticNameImportType, staticCaptionImportType);

        Integer result1 = (userInvoiceDetailData == null || userInvoiceDetailData.size() < 1) ? IMPORT_RESULT_EMPTY :
            importUserInvoices(userInvoiceDetailData.get(0), context, importColumns.get(0), importColumns.get(1),
                    userInvoiceObject, importSettings.getPrimaryKeyType(), importSettings.getCountryKeyType(), operationObject, supplierObject,
                    supplierStockObject, customerObject, customerStockObject, ignoreInvoicesAfterDocumentsClosedDate).first;

        Integer result2 = (userInvoiceDetailData == null || userInvoiceDetailData.size() < 2) ? IMPORT_RESULT_EMPTY :
            importUserInvoices(userInvoiceDetailData.get(1), context, importColumns.get(0), importColumns.get(1),
                    userInvoiceObject, importSettings.getSecondaryKeyType(), importSettings.getCountryKeyType(), operationObject, supplierObject,
                    supplierStockObject, customerObject, customerStockObject, ignoreInvoicesAfterDocumentsClosedDate).first;

        if (result1 == IMPORT_RESULT_ERROR || result2 == IMPORT_RESULT_ERROR)
            return IMPORT_RESULT_ERROR;
        else if (result1 == IMPORT_RESULT_DOCUMENTS_CLOSED_DATE || result2 == IMPORT_RESULT_DOCUMENTS_CLOSED_DATE)
            return IMPORT_RESULT_DOCUMENTS_CLOSED_DATE;
        else return result1 + result2;
    }

    public Pair<Integer, DataObject> importUserInvoices(List<PurchaseInvoiceDetail> userInvoiceDetailsList, ExecutionContext context,
                                  LinkedHashMap<String, ImportColumnDetail> defaultColumns, LinkedHashMap<String, ImportColumnDetail> customColumns,
                                  DataObject userInvoiceObject, String keyType, String countryKeyType, ObjectValue operationObject,
                                  ObjectValue supplierObject, ObjectValue supplierStockObject, ObjectValue customerObject,
                                  ObjectValue customerStockObject, boolean ignoreInvoicesAfterDocumentsClosedDate)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {


        if (notNullNorEmpty(userInvoiceDetailsList)) {

            boolean skip = false;
            if (showField(userInvoiceDetailsList, "dateDocument")) {
                for (PurchaseInvoiceDetail detail : userInvoiceDetailsList) {
                    Object dateDocument = detail.getFieldValue("dateDocument");
                    String idStock = (String) detail.getFieldValue("idCustomerStock");
                    Date documentsClosedDate = idStock != null ?
                            (Date) findProperty("documentsClosedDate[ISTRING[100]]").read(context, new DataObject(idStock)) :
                            (Date) findProperty("documentsClosedDate[Stock]").read(context, customerStockObject);
                    if (overDocumentsClosedDate((Date) dateDocument, documentsClosedDate, ignoreInvoicesAfterDocumentsClosedDate)) {
                        skip = true;
                    }
                }
            }

            if (!skip) {

                List<ImportProperty<?>> props = new ArrayList<>();
                List<ImportField> fields = new ArrayList<>();
                List<ImportKey<?>> keys = new ArrayList<>();

                List<List<Object>> data = initData(userInvoiceDetailsList.size());

                ImportKey<?> userInvoiceKey = null;
                ImportField idUserInvoiceField = null;
                boolean multipleInvoices = userInvoiceObject == null && showField(userInvoiceDetailsList, "idUserInvoice");
                if (showField(userInvoiceDetailsList, "idUserInvoice")) {
                    idUserInvoiceField = new ImportField(findProperty("id[UserInvoice]"));
                    if (userInvoiceObject == null) {
                        userInvoiceKey = new ImportKey((CustomClass) findClass("UserInvoice"),
                                findProperty("userInvoice[STRING[100]]").getMapping(idUserInvoiceField));
                        keys.add(userInvoiceKey);
                    }
                    props.add(new ImportProperty(idUserInvoiceField, findProperty("id[UserInvoice]").getMapping(userInvoiceObject != null ? userInvoiceObject : userInvoiceKey)));
                    fields.add(idUserInvoiceField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).idUserInvoice);
                } else if (userInvoiceObject == null) {
                    userInvoiceObject = context.addObject((ConcreteCustomClass) findClass("Purchase.UserInvoice"));
                }
                Object invoiceKey = multipleInvoices ? userInvoiceKey : userInvoiceObject;

                if (showField(userInvoiceDetailsList, "numberUserInvoice")) {
                    addDataField(props, fields, defaultColumns, findProperty("number[UserInvoice]"), "numberDocument", invoiceKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).numberUserInvoice);
                }

                if (showField(userInvoiceDetailsList, "seriesUserInvoice")) {
                    addDataField(props, fields, defaultColumns, findProperty("series[UserInvoice]"), "seriesDocument", invoiceKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).seriesUserInvoice);
                }

                if (showField(userInvoiceDetailsList, "dateDocument")) {
                    addDataField(props, fields, defaultColumns, findProperty("date[UserInvoice]"), "dateDocument", invoiceKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("dateDocument"));
                }

                if (showField(userInvoiceDetailsList, "timeDocument")) {
                    addDataField(props, fields, defaultColumns, findProperty("time[UserInvoice]"), "timeDocument", invoiceKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("timeDocument"));
                }

                if (showField(userInvoiceDetailsList, "currencyDocument")) {
                    ImportField shortNameCurrencyField = new ImportField(findProperty("shortName[Currency]"));
                    ImportKey<?> currencyKey = new ImportKey((CustomClass) findClass("Currency"),
                            findProperty("currencyShortName[BPSTRING[3]]").getMapping(shortNameCurrencyField));
                    keys.add(currencyKey);
                    props.add(new ImportProperty(shortNameCurrencyField, findProperty("currency[UserInvoice]").getMapping(invoiceKey),
                            object(findClass("Currency")).getMapping(currencyKey), getReplaceOnlyNull(defaultColumns, "currencyDocument")));
                    fields.add(shortNameCurrencyField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("currencyDocument"));
                }

                ImportField idUserInvoiceDetailField = new ImportField(findProperty("id[UserInvoiceDetail]"));
                ImportKey<?> userInvoiceDetailKey = new ImportKey((CustomClass) findClass("Purchase.UserInvoiceDetail"),
                        findProperty("userInvoiceDetail[STRING[100]]").getMapping(idUserInvoiceDetailField));
                keys.add(userInvoiceDetailKey);
                props.add(new ImportProperty(idUserInvoiceDetailField, findProperty("id[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                if (multipleInvoices)
                    props.add(new ImportProperty(idUserInvoiceField, findProperty("userInvoice[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                            object(findClass("Purchase.UserInvoice")).getMapping(userInvoiceKey)));
                else
                    props.add(new ImportProperty(userInvoiceObject, findProperty("userInvoice[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                fields.add(idUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).idUserInvoiceDetail);

                if (operationObject instanceof DataObject) {
                    props.add(new ImportProperty((DataObject) operationObject, findProperty("operation[UserInvoice]").getMapping(invoiceKey)));
                }

                if (supplierObject instanceof DataObject) {
                    props.add(new ImportProperty((DataObject) supplierObject, findProperty("supplier[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) supplierObject, findProperty("supplier[UserInvoice]").getMapping(invoiceKey)));
                }

                if (showField(userInvoiceDetailsList, "idSupplier")) {

                    ImportField idSupplierField = new ImportField(findProperty("id[LegalEntity]"));
                    ImportKey<?> supplierKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                            findProperty("legalEntity[STRING[100]]").getMapping(idSupplierField));
                    keys.add(supplierKey);
                    props.add(new ImportProperty(idSupplierField, findProperty("supplier[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                            object(findClass("LegalEntity")).getMapping(supplierKey), getReplaceOnlyNull(defaultColumns, "idSupplier")));
                    props.add(new ImportProperty(idSupplierField, findProperty("supplier[UserInvoice]").getMapping(invoiceKey),
                            object(findClass("LegalEntity")).getMapping(supplierKey), getReplaceOnlyNull(defaultColumns, "idSupplier")));
                    fields.add(idSupplierField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idSupplier"));

                }

                if (showField(userInvoiceDetailsList, "idSupplierStock")) {

                    ImportField idSupplierStockField = new ImportField(findProperty("id[Stock]"));
                    ImportKey<?> supplierStockKey = new ImportKey((CustomClass) findClass("Stock"),
                            findProperty("stock[STRING[100]]").getMapping(idSupplierStockField));
                    keys.add(supplierStockKey);
//                props.add(new ImportProperty(idSupplierStockField, findProperty("supplierStock[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
//                        object(findClass("Stock")).getMapping(supplierStockKey), getReplaceOnlyNull(defaultColumns, "idSupplierStock")));
                    props.add(new ImportProperty(idSupplierStockField, findProperty("supplierStock[UserInvoice]").getMapping(invoiceKey),
                            object(findClass("Stock")).getMapping(supplierStockKey), getReplaceOnlyNull(defaultColumns, "idSupplierStock")));
                    fields.add(idSupplierStockField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idSupplierStock"));

                }

                if (supplierStockObject instanceof DataObject) {
//                props.add(new ImportProperty((DataObject) supplierStockObject, findProperty("supplierStock[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) supplierStockObject, findProperty("supplierStock[UserInvoice]").getMapping(invoiceKey)));
                }

                if (customerObject instanceof DataObject) {
//                props.add(new ImportProperty((DataObject) customerObject, findProperty("customer[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) customerObject, findProperty("customer[UserInvoice]").getMapping(invoiceKey)));
                }

                if (customerStockObject instanceof DataObject) {
//                props.add(new ImportProperty((DataObject) customerStockObject, findProperty("customerStock[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                    props.add(new ImportProperty((DataObject) customerStockObject, findProperty("customerStock[UserInvoice]").getMapping(invoiceKey)));
                }

                ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcode[Sku]"));
                ImportKey<?> barcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
                        findProperty("extBarcode[STRING[100]]").getMapping(idBarcodeSkuField));
                keys.add(barcodeKey);
                props.add(new ImportProperty(idBarcodeSkuField, findProperty("id[Barcode]").getMapping(barcodeKey), getReplaceOnlyNull(defaultColumns, "barcodeItem")));
                props.add(new ImportProperty(idBarcodeSkuField, findProperty("extId[Barcode]").getMapping(barcodeKey), getReplaceOnlyNull(defaultColumns, "barcodeItem")));
                fields.add(idBarcodeSkuField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("barcodeItem"));

                ImportField idBatchField = new ImportField(findProperty("id[Batch]"));
                ImportKey<?> batchKey = new ImportKey((CustomClass) findClass("Batch"),
                        findProperty("batch[STRING[100]]").getMapping(idBatchField));
                props.add(new ImportProperty(idBatchField, findProperty("id[Batch]").getMapping(batchKey)));
                props.add(new ImportProperty(idBatchField, findProperty("idBatch[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                fields.add(idBatchField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idBatch"));

                ImportField idImportCodeField = null;
                if (skuImportCodeLM != null) {
                    idImportCodeField = new ImportField(skuImportCodeLM.findProperty("skuId[ImportCode]"));
                    ImportKey<?> importCodeKey = new ImportKey((CustomClass) skuImportCodeLM.findClass("ImportCode"),
                            skuImportCodeLM.findProperty("skuImportCode[STRING[100]]").getMapping(idImportCodeField));
                    props.add(new ImportProperty(idImportCodeField, skuImportCodeLM.findProperty("skuId[ImportCode]").getMapping(importCodeKey)));
                    fields.add(idImportCodeField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idImportCode"));
                }

                ImportField dataIndexUserInvoiceDetailField = new ImportField(findProperty("dataIndex[UserInvoiceDetail]"));
                props.add(new ImportProperty(dataIndexUserInvoiceDetailField, findProperty("dataIndex[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                fields.add(dataIndexUserInvoiceDetailField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("dataIndex"));

                ImportField idItemField = new ImportField(findProperty("id[Item]"));
                fields.add(idItemField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idItem"));

                String replaceField;
                LP iGroupAggr;
                ImportField iField;
                if (keyType == null || keyType.equals("item")) {
                    replaceField = "idItem";
                    iGroupAggr = findProperty("item[STRING[100]]");
                    iField = idItemField;
                } else if (keyType.equals("barcode")) {
                    replaceField = "barcodeItem";
                    iGroupAggr = findProperty("skuBarcode[BPSTRING[15]]");
                    iField = idBarcodeSkuField;
                } else if (skuImportCodeLM != null && keyType.equals("importCode")) {
                    replaceField = "idImportCode";
                    iGroupAggr = skuImportCodeLM.findProperty("skuImportCode[BPSTRING[100]]");
                    iField = idImportCodeField;
                } else {
                    replaceField = "batch";
                    iGroupAggr = findProperty("skuBatch[STRING[100]]");
                    iField = idBatchField;
                }

                ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                        iGroupAggr.getMapping(iField));
                keys.add(itemKey);
                props.add(new ImportProperty(idItemField, findProperty("id[Item]").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "idItem", true)));
                props.add(new ImportProperty(iField, findProperty("sku[Purchase.InvoiceDetail]").getMapping(userInvoiceDetailKey),
                        object(findClass("Sku")).getMapping(itemKey), getReplaceOnlyNull(defaultColumns, replaceField)));
                props.add(new ImportProperty(iField, findProperty("sku[Barcode]").getMapping(barcodeKey),
                        object(findClass("Item")).getMapping(itemKey), getReplaceOnlyNull(defaultColumns, replaceField)));

                if (showField(userInvoiceDetailsList, "captionItem")) {
                    addDataField(props, fields, defaultColumns, findProperty("caption[Item]"), "captionItem", itemKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("captionItem"));
                }

                if (showField(userInvoiceDetailsList, "originalCaptionItem")) {
                    addDataField(props, fields, defaultColumns, findProperty("originalCaption[Item]"), "originalCaptionItem", itemKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("originalCaptionItem"));
                }

                if (showField(userInvoiceDetailsList, "netWeight")) {
                    addDataField(props, fields, defaultColumns, findProperty("netWeight[Item]"), "netWeight", itemKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).netWeight);
                }

                if (showField(userInvoiceDetailsList, "grossWeight")) {
                    addDataField(props, fields, defaultColumns, findProperty("grossWeight[Item]"), "grossWeight", itemKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).grossWeight);
                }

                if (showField(userInvoiceDetailsList, "sumNetWeight")) {
                    addDataField(props, fields, defaultColumns, findProperty("sumNetWeight[UserInvoiceDetail]"), "sumNetWeight", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).sumNetWeight);
                }

                if (showField(userInvoiceDetailsList, "sumGrossWeight")) {
                    addDataField(props, fields, defaultColumns, findProperty("sumGrossWeight[UserInvoiceDetail]"), "sumGrossWeight", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).sumGrossWeight);
                }

                if (showField(userInvoiceDetailsList, "UOMItem")) {
                    ImportField idUOMField = new ImportField(findProperty("id[UOM]"));
                    ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) findClass("UOM"),
                            findProperty("UOM[STRING[100]]").getMapping(idUOMField));
                    //from ImportPurchaseInvoiceSkuImportCode
                    UOMKey.skipKey = context.getBL().getModule("SkuImportCode") != null && showField(userInvoiceDetailsList, "importCodeUOM");
                    keys.add(UOMKey);
                    props.add(new ImportProperty(idUOMField, findProperty("id[UOM]").getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                    props.add(new ImportProperty(idUOMField, findProperty("name[UOM]").getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                    props.add(new ImportProperty(idUOMField, findProperty("shortName[UOM]").getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                    props.add(new ImportProperty(idUOMField, findProperty("UOM[Item]").getMapping(itemKey),
                            object(findClass("UOM")).getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOM")));
                    fields.add(idUOMField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("UOMItem"));
                }

                if (showField(userInvoiceDetailsList, "idManufacturer")) {
                    ImportField idManufacturerField = new ImportField(LM.findProperty("id[Manufacturer]"));
                    ImportKey<?> manufacturerKey = new ImportKey((CustomClass) LM.findClass("Manufacturer"),
                            LM.findProperty("manufacturer[STRING[100]]").getMapping(idManufacturerField));
                    //from ImportPurchaseInvoiceSkuImportCode
                    manufacturerKey.skipKey = context.getBL().getModule("SkuImportCode") != null && showField(userInvoiceDetailsList, "importCodeManufacturer");
                    keys.add(manufacturerKey);
                    props.add(new ImportProperty(idManufacturerField, LM.findProperty("id[Manufacturer]").getMapping(manufacturerKey), getReplaceOnlyNull(defaultColumns, "idManufacturer")));
                    props.add(new ImportProperty(idManufacturerField, LM.findProperty("manufacturer[Item]").getMapping(itemKey),
                            object(LM.findClass("Manufacturer")).getMapping(manufacturerKey), getReplaceOnlyNull(defaultColumns, "idManufacturer")));
                    fields.add(idManufacturerField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idManufacturer"));

                    if (showField(userInvoiceDetailsList, "nameManufacturer")) {
                        addDataField(props, fields, defaultColumns, LM.findProperty("name[Manufacturer]"), "nameManufacturer", manufacturerKey);
                        for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                            data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameManufacturer"));
                    }
                }

                ScriptingLogicsModule skuImportCodeLM = context.getBL().getModule("SkuImportCode");

                ImportField sidOrigin2CountryField = new ImportField(LM.findProperty("sidOrigin2[Country]"));
                ImportField nameCountryField = new ImportField(LM.findProperty("name[Country]"));
                ImportField nameOriginCountryField = new ImportField(LM.findProperty("nameOrigin[Country]"));
                ImportField countryIdImportCodeField = skuImportCodeLM != null ? new ImportField(skuImportCodeLM.findProperty("countryId[ImportCode]")) : null;

                boolean showSidOrigin2Country = showField(userInvoiceDetailsList, "sidOrigin2Country");
                boolean showNameCountry = showField(userInvoiceDetailsList, "nameCountry");
                boolean showNameOriginCountry = showField(userInvoiceDetailsList, "nameOriginCountry");
                boolean showImportCodeCountry = skuImportCodeLM != null && showField(userInvoiceDetailsList, "importCodeCountry");
                boolean showImportCountryBatch = showImportCountryBatch(userInvoiceDetailsList);

                //хак. Из-за проблемы одновременного создания страны по importCode (страна и страна ввоза) создаём страну предварительно
                boolean preImportCountries = countryKeyType != null && countryKeyType.equals("importCodeCountry") && showImportCodeCountry && showImportCountryBatch;

                if (preImportCountries) {
                    List<List<Object>> countryData = new ArrayList<>();
                    List<ImportProperty<?>> countryProps = new ArrayList<>();
                    ImportKey<?> countryKey = new ImportKey((CustomClass) LM.findClass("Country"), skuImportCodeLM.findProperty("countryIdImportCode[STRING[100]]").getMapping(countryIdImportCodeField));

                    ImportKey<?> importCodeKey = new ImportKey((ConcreteCustomClass) skuImportCodeLM.findClass("ImportCode"), skuImportCodeLM.findProperty("countryImportCode[STRING[100]]").getMapping(countryIdImportCodeField));

                    countryProps.add(new ImportProperty(countryIdImportCodeField, skuImportCodeLM.findProperty("countryId[ImportCode]").getMapping(importCodeKey)));
                    countryProps.add(new ImportProperty(countryIdImportCodeField, skuImportCodeLM.findProperty("country[ImportCode]").getMapping(importCodeKey), object(skuImportCodeLM.findClass("Country")).getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "importCodeCountry")));

                    Set<Object> countrySet = new HashSet<>();
                    for (PurchaseInvoiceDetail d : userInvoiceDetailsList) {
                        countrySet.add(d.getFieldValue("importCodeCountry"));
                        countrySet.add(d.getFieldValue("importCountryBatch"));
                    }
                    for (Object country : countrySet) {
                        countryData.add(Collections.singletonList(country));
                    }

                    new IntegrationService(context, new ImportTable(Collections.singletonList(countryIdImportCodeField), countryData), Arrays.asList(countryKey, importCodeKey), countryProps).synchronize(true, false);
                }


                if (showSidOrigin2Country || showNameCountry || showNameOriginCountry || showImportCodeCountry) {
                    if (countryKeyType != null) {
                        ImportField countryField = null;
                        LP<?> countryAggr = null;
                        switch (countryKeyType) {
                            case "sidOrigin2Country":
                                countryField = sidOrigin2CountryField;
                                countryAggr = LM.findProperty("countrySIDOrigin2[BPSTRING[2]]");
                                break;
                            case "nameCountry":
                                countryField = nameCountryField;
                                countryAggr = LM.findProperty("countryName[ISTRING[50]]");
                                break;
                            case "nameOriginCountry":
                                countryField = nameOriginCountryField;
                                countryAggr = LM.findProperty("countryOrigin[ISTRING[50]]");
                                break;
                            case "importCodeCountry":
                                if (showImportCodeCountry) {
                                    countryField = countryIdImportCodeField;
                                    countryAggr = skuImportCodeLM.findProperty("countryIdImportCode[STRING[100]]");
                                    break;
                                }
                        }

                        if (countryField != null) {
                            ImportKey<?> countryKey = new ImportKey((CustomClass) LM.findClass("Country"), countryAggr.getMapping(countryField));
                            countryKey.skipKey = preImportCountries;
                            keys.add(countryKey);
                            props.add(new ImportProperty(countryField, LM.findProperty("country[Item]").getMapping(itemKey),
                                    object(LM.findClass("Country")).getMapping(countryKey), getReplaceOnlyNull(defaultColumns, countryKeyType)));

                            if (showSidOrigin2Country) {
                                props.add(new ImportProperty(sidOrigin2CountryField, LM.findProperty("sidOrigin2[Country]").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "sidOrigin2Country")));
                                fields.add(sidOrigin2CountryField);
                                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("sidOrigin2Country"));
                            }
                            if (showNameCountry) {
                                boolean replaceOnlyNull = countryKeyType.equals("importCodeCountry") || getReplaceOnlyNull(defaultColumns, "nameCountry");
                                props.add(new ImportProperty(nameCountryField, LM.findProperty("name[Country]").getMapping(countryKey), replaceOnlyNull));
                                fields.add(nameCountryField);
                                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameCountry"));
                            }
                            if (showNameOriginCountry) {
                                props.add(new ImportProperty(nameOriginCountryField, LM.findProperty("nameOrigin[Country]").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "nameOriginCountry")));
                                fields.add(nameOriginCountryField);
                                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameOriginCountry"));
                            }
                            if (showImportCodeCountry) {
                                ImportKey<?> importCodeKey = new ImportKey((ConcreteCustomClass) skuImportCodeLM.findClass("ImportCode"),
                                        skuImportCodeLM.findProperty("countryImportCode[STRING[100]]").getMapping(countryIdImportCodeField));
                                importCodeKey.skipKey = preImportCountries;
                                keys.add(importCodeKey);
                                props.add(new ImportProperty(countryIdImportCodeField, skuImportCodeLM.findProperty("countryId[ImportCode]").getMapping(importCodeKey)));
                                props.add(new ImportProperty(countryIdImportCodeField, skuImportCodeLM.findProperty("country[ImportCode]").getMapping(importCodeKey),
                                        object(skuImportCodeLM.findClass("Country")).getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "importCodeCountry")));
                                fields.add(countryIdImportCodeField);
                                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("importCodeCountry"));
                            }
                        }

                    } else {
                        //старую логику оставляем для обратной совместимости
                        ImportField countryField = showSidOrigin2Country ? sidOrigin2CountryField :
                                (showNameCountry ? nameCountryField : (showNameOriginCountry ? nameOriginCountryField : null));
                        LP<?> countryAggr = showSidOrigin2Country ? LM.findProperty("countrySIDOrigin2[BPSTRING[2]]") :
                                (showNameCountry ? LM.findProperty("countryName[ISTRING[50]]") : (showNameOriginCountry ? LM.findProperty("countryOrigin[ISTRING[50]]") : null));
                        String countryReplaceField = showSidOrigin2Country ? "sidOrigin2Country" :
                                (showNameCountry ? "nameCountry" : (showNameOriginCountry ? "nameOriginCountry" : null));
                        ImportKey<?> countryKey = countryField == null ? null :
                                new ImportKey((CustomClass) LM.findClass("Country"), countryAggr.getMapping(countryField));

                        if (countryKey != null) {
                            keys.add(countryKey);

                            props.add(new ImportProperty(countryField, LM.findProperty("country[Item]").getMapping(itemKey),
                                    object(LM.findClass("Country")).getMapping(countryKey), getReplaceOnlyNull(defaultColumns, countryReplaceField)));

                            if (showSidOrigin2Country) {
                                props.add(new ImportProperty(sidOrigin2CountryField, LM.findProperty("sidOrigin2[Country]").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "sidOrigin2Country")));
                                fields.add(sidOrigin2CountryField);
                                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("sidOrigin2Country"));
                            }
                            if (showNameCountry) {
                                props.add(new ImportProperty(nameCountryField, LM.findProperty("name[Country]").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "nameCountry")));
                                fields.add(nameCountryField);
                                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameCountry"));
                            }
                            if (showNameOriginCountry) {
                                props.add(new ImportProperty(nameOriginCountryField, LM.findProperty("nameOrigin[Country]").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "nameOriginCountry")));
                                fields.add(nameOriginCountryField);
                                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("nameOriginCountry"));
                            }
                            if (showImportCodeCountry) {
                                ImportKey<?> importCodeKey = new ImportKey((ConcreteCustomClass) skuImportCodeLM.findClass("ImportCode"),
                                        skuImportCodeLM.findProperty("countryImportCode[STRING[100]]").getMapping(countryIdImportCodeField));
                                keys.add(importCodeKey);
                                props.add(new ImportProperty(countryIdImportCodeField, skuImportCodeLM.findProperty("countryId[ImportCode]").getMapping(importCodeKey)));
                                props.add(new ImportProperty(countryIdImportCodeField, skuImportCodeLM.findProperty("country[ImportCode]").getMapping(importCodeKey),
                                        object(skuImportCodeLM.findClass("Country")).getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "importCodeCountry")));
                                fields.add(countryIdImportCodeField);
                                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("importCodeCountry"));
                            }
                        }
                    }
                }

                if (showField(userInvoiceDetailsList, "idCustomer")) {
                    ImportField idCustomerField = new ImportField(findProperty("id[LegalEntity]"));
                    ImportKey<?> customerKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                            findProperty("legalEntity[STRING[100]]").getMapping(idCustomerField));
                    keys.add(customerKey);
                    props.add(new ImportProperty(idCustomerField, findProperty("customer[UserInvoice]").getMapping(invoiceKey),
                            object(findClass("LegalEntity")).getMapping(customerKey), getReplaceOnlyNull(defaultColumns, "idBCustomer")));
                    fields.add(idCustomerField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idCustomer"));
                }

                if (showField(userInvoiceDetailsList, "idCustomerStock")) {
                    ImportField idCustomerStockField = new ImportField(findProperty("id[Stock]"));
                    ImportKey<?> customerStockKey = new ImportKey((CustomClass) findClass("Stock"),
                            findProperty("stock[STRING[100]]").getMapping(idCustomerStockField));
                    keys.add(customerStockKey);
                    props.add(new ImportProperty(idCustomerStockField, findProperty("customerStock[UserInvoice]").getMapping(invoiceKey),
                            object(findClass("Stock")).getMapping(customerStockKey), getReplaceOnlyNull(defaultColumns, "idCustomerStock")));
                    fields.add(idCustomerStockField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idCustomerStock"));
                }

                if (showField(userInvoiceDetailsList, "quantity")) {
                    addDataField(props, fields, defaultColumns, findProperty("quantity[UserInvoiceDetail]"), "quantity", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).quantity);
                }

                if (showField(userInvoiceDetailsList, "price")) {
                    addDataField(props, fields, defaultColumns, findProperty("price[UserInvoiceDetail]"), "price", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("price"));
                }

                if (showField(userInvoiceDetailsList, "sum")) {
                    addDataField(props, fields, defaultColumns, findProperty("sum[UserInvoiceDetail]"), "sum", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("sum"));
                }

                if (showField(userInvoiceDetailsList, "valueVAT")) {
                    ImportField valueVATUserInvoiceDetailField = new ImportField(findProperty("valueVAT[UserInvoiceDetail]"));
                    ImportKey<?> VATKey = new ImportKey((CustomClass) findClass("Range"),
                            findProperty("valueCurrentVATDefault[NUMERIC[10,5]]").getMapping(valueVATUserInvoiceDetailField));
                    keys.add(VATKey);
                    props.add(new ImportProperty(valueVATUserInvoiceDetailField, findProperty("VAT[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                            object(findClass("Range")).getMapping(VATKey), getReplaceOnlyNull(defaultColumns, "valueVAT")));
                    fields.add(valueVATUserInvoiceDetailField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("valueVAT"));

                    new ImportPurchaseInvoiceTaxItem(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, valueVATUserInvoiceDetailField, itemKey, VATKey);

                }

                if (showField(userInvoiceDetailsList, "sumVAT")) {
                    addDataField(props, fields, defaultColumns, findProperty("VATSum[UserInvoiceDetail]"), "sumVAT", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("sumVAT"));
                }

                if (showField(userInvoiceDetailsList, "invoiceSum")) {
                    addDataField(props, fields, defaultColumns, findProperty("invoiceSum[UserInvoiceDetail]"), "invoiceSum", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("invoiceSum"));
                }

                new ImportPurchaseInvoicePurchaseShipmentBox(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);

                new ImportPurchaseInvoicePurchaseDeclaration(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);

                new ImportPurchaseInvoicePurchaseShipment(LM).makeImport(context, fields, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);

                new ImportPurchaseInvoicePurchaseManufacturingPrice(LM).makeImport(context, fields, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);

                makeCustomImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, itemKey, userInvoiceDetailKey, countryKeyType, preImportCountries);

                new ImportPurchaseInvoicePurchaseCompliance(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, data, userInvoiceDetailKey);

                new ImportPurchaseInvoiceSkuImportCode(LM).makeImport(context, fields, keys, props, defaultColumns, userInvoiceDetailsList, itemKey, data);

                if (showField(userInvoiceDetailsList, "rateExchange")) {
                    addDataField(props, fields, defaultColumns, findProperty("rateExchange[UserInvoiceDetail]"), "rateExchange", userInvoiceDetailKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("rateExchange"));
                }

                if (showField(userInvoiceDetailsList, "isPosted")) {
                    addDataField(props, fields, defaultColumns, findProperty("isPosted[UserInvoice]"), "isPosted", invoiceKey);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).isPosted);
                }

                if (showField(userInvoiceDetailsList, "idItemGroup")) {
                    ImportField idItemGroupField = new ImportField(findProperty("id[ItemGroup]"));
                    ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                            findProperty("itemGroup[STRING[100]]").getMapping(idItemGroupField));
                    keys.add(itemGroupKey);
                    props.add(new ImportProperty(idItemGroupField, findProperty("id[ItemGroup]").getMapping(itemGroupKey), getReplaceOnlyNull(defaultColumns, "idItemGroup")));
                    props.add(new ImportProperty(idItemGroupField, findProperty("name[ItemGroup]").getMapping(itemGroupKey), true));
                    props.add(new ImportProperty(idItemGroupField, findProperty("itemGroup[Item]").getMapping(itemKey),
                            object(findClass("ItemGroup")).getMapping(itemGroupKey), getReplaceOnlyNull(defaultColumns, "idItemGroup")));
                    fields.add(idItemGroupField);
                    for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                        data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("idItemGroup"));
                }

                ScriptingLogicsModule itemArticleLM = context.getBL().getModule("ItemArticle");
                ImportKey<?> articleKey = null;
                if ((itemArticleLM != null)) {

                    ImportField idArticleField = new ImportField(itemArticleLM.findProperty("id[Article]"));
                    articleKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClass("Article"),
                            itemArticleLM.findProperty("article[STRING[100]]").getMapping(idArticleField));
                    keys.add(articleKey);
                    props.add(new ImportProperty(idArticleField, itemArticleLM.findProperty("id[Article]").getMapping(articleKey), getReplaceOnlyNull(defaultColumns, "idArticle")));
                    props.add(new ImportProperty(idArticleField, itemArticleLM.findProperty("article[Item]").getMapping(itemKey),
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
                    LP<?> customProp = customColumn.propertyCanonicalName == null ? null : (LP<?>) context.getBL().findSafeProperty(customColumn.propertyCanonicalName);
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
                                data.get(i).add(safeParse(customField, userInvoiceDetailsList.get(i).customValues.get(entry.getKey())));
                        } else if (customColumn.key.equals("document")) {
                            props.add(new ImportProperty(customField, customProp.getMapping(invoiceKey), getReplaceOnlyNull(customColumns, entry.getKey())));
                            fields.add(customField);
                            for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                                data.get(i).add(safeParse(customField, userInvoiceDetailsList.get(i).customValues.get(entry.getKey())));
                        }
                    }
                }

                ImportTable table = new ImportTable(fields, data);

                IntegrationService service = new IntegrationService(context, table, keys, props);
                service.synchronize(true, false);
                return Pair.create(IMPORT_RESULT_OK, userInvoiceObject);
            } else {
                return Pair.create(IMPORT_RESULT_DOCUMENTS_CLOSED_DATE, userInvoiceObject);
            }
        }   else return Pair.create(IMPORT_RESULT_EMPTY, userInvoiceObject);
    }

    private boolean overDocumentsClosedDate(Date dateReceipt, Date documentsClosedDate, boolean ignoreReceiptsAfterDocumentsClosedDate) {
        return ignoreReceiptsAfterDocumentsClosedDate && dateReceipt != null && documentsClosedDate != null && dateReceipt.compareTo(documentsClosedDate) < 0;
    }

    private Object safeParse(ImportField field, String value) {
        try {
            return value == null ? null : field.getFieldClass().parseString(value);
        } catch (lsfusion.server.logics.classes.data.ParseException e) {
            return null;
        }
    }

    protected List<List<PurchaseInvoiceDetail>> importUserInvoicesFromFile(ExecutionContext<ClassPropertyInterface> context, DataObject userInvoiceObject,
                                                                           Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                           Set<String> purchaseInvoiceSet, boolean completeIdItemAsEAN, boolean checkInvoiceExistence,
                                                                           RawFileData file, String fileExtension, ImportDocumentSettings importSettings,
                                                                           String staticNameImportType, String staticCaptionImportType)
            throws UniversalImportException, IOException, SQLException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        List<List<PurchaseInvoiceDetail>> userInvoiceDetailsList;

        //as in ImportDocument.lsf, CLASS ImportTypeDetail
        List<String> stringFields = Arrays.asList("idSupplier", "idSupplierStock", "currencyDocument", "idItem", "idItemGroup",
                "barcodeItem", "originalCustomsGroupItem", "idBatch", "idImportCode", "idBox", "nameBox", "captionItem", "originalCaptionItem",
                "UOMItem", "importCodeUOM", "idManufacturer", "nameManufacturer", "importCodeManufacturer", "sidOrigin2Country", "nameCountry", "nameOriginCountry",
                "importCountryBatch", "importCodeCountry", "idCustomerStock", "contractPrice", "pharmacyPriceGroupItem", "valueVAT", "seriesPharmacy",
                "numberCompliance", "declaration", "idArticle", "captionArticle", "originalCaptionArticle", "idColor",
                "nameColor", "idTheme", "nameTheme", "composition", "originalComposition", "idSize", "nameSize", "nameOriginalSize",
                "idCollection", "nameCollection", "idSeasonYear", "idSeason", "nameSeason", "idBrand", "nameBrand");
        
        List<String> bigDecimalFields = Arrays.asList("dataIndex", "price", "manufacturingPrice", "shipmentPrice", 
                "shipmentSum", "rateExchange", "sum",  "sumVAT", "invoiceSum");

        List<String> dateFields = Arrays.asList("dateDocument", "manufactureDate", "dateCompliance", "expiryDate");

        List<String> timeFields = Collections.singletonList("timeDocument");

        switch (fileExtension) {
            case "DBF":
                userInvoiceDetailsList = importUserInvoicesFromDBF(context, file, defaultColumns, customColumns,
                        stringFields, bigDecimalFields, dateFields, timeFields, purchaseInvoiceSet, completeIdItemAsEAN, checkInvoiceExistence,
                        importSettings, userInvoiceObject, staticNameImportType, staticCaptionImportType);
                break;
            case "XLS":
                userInvoiceDetailsList = importUserInvoicesFromXLS(context, file, defaultColumns, customColumns,
                        stringFields, bigDecimalFields, dateFields, timeFields, purchaseInvoiceSet, completeIdItemAsEAN, checkInvoiceExistence,
                        importSettings, userInvoiceObject, staticNameImportType, staticCaptionImportType);
                break;
            case "XLSX":
                userInvoiceDetailsList = importUserInvoicesFromXLSX(context, file, defaultColumns, customColumns,
                        stringFields, bigDecimalFields, dateFields, timeFields, purchaseInvoiceSet, completeIdItemAsEAN, checkInvoiceExistence,
                        importSettings, userInvoiceObject, staticNameImportType, staticCaptionImportType);
                break;
            case "CSV":
            case "TXT":
                userInvoiceDetailsList = importUserInvoicesFromCSV(context, file, defaultColumns, customColumns,
                        stringFields, bigDecimalFields, dateFields, timeFields, purchaseInvoiceSet, completeIdItemAsEAN, checkInvoiceExistence,
                        importSettings, userInvoiceObject, staticNameImportType, staticCaptionImportType);
                break;
            default:
                userInvoiceDetailsList = null;
                break;
        }

        return userInvoiceDetailsList;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromXLS(ExecutionContext<ClassPropertyInterface> context, RawFileData importFile,
                                                                        Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                        List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, List<String> timeFields,
                                                                        Set<String> purchaseInvoiceSet, boolean completeIdItemAsEAN,
                                                                        boolean checkInvoiceExistence, ImportDocumentSettings importSettings, DataObject userInvoiceObject,
                                                                        String staticNameImportType, String staticCaptionImportType)
            throws UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<>();

        String primaryKeyColumn = getItemKeyColumn(importSettings.getPrimaryKeyType());
        String secondaryKeyColumn = getItemKeyColumn(importSettings.getSecondaryKeyType());

        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding("cp1251");
        ws.setGCDisabled(true);
        Workbook wb;
        try {
            wb = Workbook.getWorkbook(importFile.getInputStream(), ws);
        } catch (Exception e) {
            String error = "Файл неизвестного либо устаревшего формата";
            context.requestUserInteraction(new MessageClientAction(error, "Ошибка при открытии файла"));
            throw new RuntimeException(error, e);
        }
        Sheet sheet = wb.getSheet(0);

        currentTimestamp = getCurrentTimestamp();

        for (int i = importSettings.getStartRow() - 1; i < sheet.getRows(); i++) {

            Map<String, Object> fieldValues = new HashMap<>();
            for (String field : stringFields) {
                String value = getXLSFieldValue(sheet, i, defaultColumns.get(field));
                switch (field) {
                    case "idItem":
                        fieldValues.put(field, completeIdItemAsEAN ? BarcodeUtils.appendCheckDigitToBarcode(value, 7) : value);
                        break;
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
                        value = importSettings.getStockMapping().getOrDefault(value, value);
                        fieldValues.put("idCustomerStock", value);
                        fieldValues.put("idCustomer", readIdCustomer(context, value));
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
                        fieldValues.put(field, dateDocument);
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
            String seriesDocument = getXLSFieldValue(sheet, i, defaultColumns.get("seriesDocument"));
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
                        idDocument, numberDocument, seriesDocument, idUserInvoiceDetail, quantity, netWeight, netWeightSum,
                        grossWeight, grossWeightSum);

                String primaryKeyColumnValue = getXLSFieldValue(sheet, i, defaultColumns.get(primaryKeyColumn));
                String secondaryKeyColumnValue = getXLSFieldValue(sheet, i, defaultColumns.get(secondaryKeyColumn));
                if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, importSettings.isKeyIsDigit(), context.getSession(), importSettings.getPrimaryKeyType(), importSettings.isCheckExistence()))
                    primaryList.add(purchaseInvoiceDetail);
                else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, importSettings.isKeyIsDigit()))
                    secondaryList.add(purchaseInvoiceDetail);
            }
        }
        currentTimestamp = null;

        return checkArticles(context, importSettings.getPropertyImportType(), staticNameImportType, staticCaptionImportType,
                primaryList, secondaryList) ? Arrays.asList(primaryList, secondaryList) : null;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromCSV(ExecutionContext<ClassPropertyInterface> context, RawFileData importFile,
                                                                        Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                        List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, List<String> timeFields,
                                                                        Set<String> purchaseInvoiceSet, boolean completeIdItemAsEAN,
                                                                        boolean checkInvoiceExistence, ImportDocumentSettings importSettings, DataObject userInvoiceObject,
                                                                        String staticNameImportType, String staticCaptionImportType)
            throws IOException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<>();

        String primaryKeyColumn = getItemKeyColumn(importSettings.getPrimaryKeyType());
        String secondaryKeyColumn = getItemKeyColumn(importSettings.getSecondaryKeyType());
        
        BufferedReader br = new BufferedReader(new InputStreamReader(importFile.getInputStream(), "cp1251"));
        String line;

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
                    case "idItem":
                        fieldValues.put(field, completeIdItemAsEAN ? BarcodeUtils.appendCheckDigitToBarcode(value, 7) : value);
                        break;
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
                        value = importSettings.getStockMapping().getOrDefault(value, value);
                        fieldValues.put("idCustomerStock", value);
                        fieldValues.put("idCustomer", readIdCustomer(context, value));
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
                        fieldValues.put(field, dateDocument);
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
            String seriesDocument = getCSVFieldValue(valuesList, defaultColumns.get("seriesDocument"), count);
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
                        idDocument, numberDocument, seriesDocument, idUserInvoiceDetail, quantity, netWeight, netWeightSum, grossWeight, grossWeightSum);

                String primaryKeyColumnValue = getCSVFieldValue(valuesList, defaultColumns.get(primaryKeyColumn), count);
                String secondaryKeyColumnValue = getCSVFieldValue(valuesList, defaultColumns.get(secondaryKeyColumn), count);
                if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, importSettings.isKeyIsDigit(), context.getSession(),
                        importSettings.getPrimaryKeyType(), importSettings.isCheckExistence()))
                    primaryList.add(purchaseInvoiceDetail);
                else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, importSettings.isKeyIsDigit()))
                    secondaryList.add(purchaseInvoiceDetail);
            }
        }
        currentTimestamp = null;

        return checkArticles(context, importSettings.getPropertyImportType(), staticNameImportType, staticCaptionImportType, 
                primaryList, secondaryList) ? Arrays.asList(primaryList, secondaryList) : null;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromXLSX(ExecutionContext context, RawFileData importFile,
                                                                         Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                         List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, List<String> timeFields,
                                                                         Set<String> purchaseInvoiceSet, boolean completeIdItemAsEAN,
                                                                         boolean checkInvoiceExistence, ImportDocumentSettings importSettings, DataObject userInvoiceObject,
                                                                         String staticNameImportType, String staticCaptionImportType)
            throws IOException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<>();

        String primaryKeyColumn = getItemKeyColumn(importSettings.getPrimaryKeyType());
        String secondaryKeyColumn = getItemKeyColumn(importSettings.getSecondaryKeyType());

        XSSFWorkbook Wb = new XSSFWorkbook(importFile.getInputStream());
        XSSFSheet sheet = Wb.getSheetAt(0);

        currentTimestamp = getCurrentTimestamp();

        for (int i = importSettings.getStartRow() - 1; i <= sheet.getLastRowNum(); i++) {

            Map<String, Object> fieldValues = new HashMap<>();
            for (String field : stringFields) {
                String value = getXLSXFieldValue(sheet, i, defaultColumns.get(field));
                switch (field) {
                    case "idItem":
                        fieldValues.put(field, completeIdItemAsEAN ? BarcodeUtils.appendCheckDigitToBarcode(value, 7) : value);
                        break;
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
                        value = importSettings.getStockMapping().getOrDefault(value, value);
                        fieldValues.put("idCustomerStock", value);
                        fieldValues.put("idCustomer", readIdCustomer(context, value));
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
                        fieldValues.put(field, dateDocument);
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
            String seriesDocument = getXLSXFieldValue(sheet, i, defaultColumns.get("seriesDocument"));
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
                        idDocument, numberDocument, seriesDocument, idUserInvoiceDetail, quantity, netWeight, netWeightSum, grossWeight, grossWeightSum);

                String primaryKeyColumnValue = getXLSXFieldValue(sheet, i, defaultColumns.get(primaryKeyColumn));
                String secondaryKeyColumnValue = getXLSXFieldValue(sheet, i, defaultColumns.get(secondaryKeyColumn));
                if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, importSettings.isKeyIsDigit(),
                        context.getSession(), importSettings.getPrimaryKeyType(), importSettings.isCheckExistence()))
                    primaryList.add(purchaseInvoiceDetail);
                else if (checkKeyColumnValue(secondaryKeyColumn, secondaryKeyColumnValue, importSettings.isKeyIsDigit()))
                    primaryList.add(purchaseInvoiceDetail);
            }
        }
        currentTimestamp = null;

        return checkArticles(context, importSettings.getPropertyImportType(), staticNameImportType, staticCaptionImportType, 
                primaryList, secondaryList) ? Arrays.asList(primaryList, secondaryList) : null;
    }

    private List<List<PurchaseInvoiceDetail>> importUserInvoicesFromDBF(ExecutionContext context, RawFileData importFile,
                                                                        Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                        List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, List<String> timeFields,
                                                                        Set<String> purchaseInvoiceSet, boolean completeIdItemAsEAN,
                                                                        boolean checkInvoiceExistence, ImportDocumentSettings importSettings, DataObject userInvoiceObject,
                                                                        String staticNameImportType, String staticCaptionImportType)
            throws IOException, xBaseJException, UniversalImportException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<PurchaseInvoiceDetail> primaryList = new ArrayList<>();
        List<PurchaseInvoiceDetail> secondaryList = new ArrayList<>();

        String primaryKeyColumn = getItemKeyColumn(importSettings.getPrimaryKeyType());
        String secondaryKeyColumn = getItemKeyColumn(importSettings.getSecondaryKeyType());

        File tempFile = null;
        DBF file = null;
        try {

            tempFile = File.createTempFile("purchaseInvoice", ".dbf");
            importFile.write(tempFile);

            file = new DBF(tempFile.getPath());
            String charset = getDBFCharset(tempFile);

            int totalRecordCount = file.getRecordCount();

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
                        case "idItem":
                            fieldValues.put(field, completeIdItemAsEAN ? BarcodeUtils.appendCheckDigitToBarcode(value, 7) : value);
                            break;
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
                            value = importSettings.getStockMapping().getOrDefault(value, value);
                            fieldValues.put("idCustomerStock", value);
                            fieldValues.put("idCustomer", readIdCustomer(context, value));
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
                            fieldValues.put(field, dateDocument);
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
                String seriesDocument = getDBFFieldValue(file, defaultColumns.get("seriesDocument"), i, charset);
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
                            idDocument, numberDocument, seriesDocument, idUserInvoiceDetail, quantity, netWeight, netWeightSum, grossWeight, grossWeightSum);

                    String primaryKeyColumnValue = getDBFFieldValue(file, defaultColumns.get(primaryKeyColumn), i, charset);
                    String secondaryKeyColumnValue = getDBFFieldValue(file, defaultColumns.get(secondaryKeyColumn), i, charset);
                    if (checkKeyColumnValue(primaryKeyColumn, primaryKeyColumnValue, importSettings.isKeyIsDigit(), context.getSession(),
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
            if(tempFile != null && !tempFile.delete())
                tempFile.deleteOnExit();
        }
       
        return checkArticles(context, importSettings.getPropertyImportType(), staticNameImportType, staticCaptionImportType, 
                primaryList, secondaryList) ? Arrays.asList(primaryList, secondaryList) : null;
    }

    private boolean checkInvoice(Set<String> invoiceSet, String idInvoice, boolean checkInvoiceExistence) {
        return !checkInvoiceExistence || !invoiceSet.contains(idInvoice);
    }

    private boolean checkArticles(ExecutionContext context, String propertyImportType, String staticNameImportType, 
                                  String staticCaptionImportType, List<PurchaseInvoiceDetail> primaryList, List<PurchaseInvoiceDetail> secondaryList)
            throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        if (propertyImportType != null) {
            LP<?> sidProp = (LP)context.getBL().findSafeProperty(propertyImportType);
            if (sidProp != null) {
                ScriptingLogicsModule itemArticleLM = context.getBL().getModule("ItemArticle");
                LP<?> idArticleProp = itemArticleLM.findProperty("id[Article]");

                List<Object> articles = getArticlesMap(context, idArticleProp, sidProp);
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
                        idArticleProp.change(entry.getValue(), context, (DataObject) itemArticleLM.findProperty("article[STRING[100]]").readClasses(context, new DataObject(entry.getKey())));
                    }
                }
            }
        }
        return true;
    }
    
    private List<Object> getArticlesMap(ExecutionContext<ClassPropertyInterface> context, LP<?> idArticleProp, LP<?> sidProperty) throws SQLException, SQLHandledException {
        
        Set<String> articleSet = new HashSet<>();
        Map<String, String> articlePropertyMap = new HashMap<>();

        KeyExpr articleExpr = new KeyExpr("Article");
        ImRevMap<Object, KeyExpr> articleKeys = MapFact.singletonRev("Article", articleExpr);

        QueryBuilder<Object, Object> articleQuery = new QueryBuilder<>(articleKeys);
        articleQuery.addProperty("idArticle", idArticleProp.getExpr(context.getModifier(), articleExpr));
        articleQuery.addProperty("sid", sidProperty.getExpr(context.getModifier(), articleExpr));
        articleQuery.and(idArticleProp.getExpr(context.getModifier(), articleExpr).getWhere());
        
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> articleResult = articleQuery.execute(context);

        for (ImMap<Object, Object> entry : articleResult.values()) {

            String idArticle = (String) entry.get("idArticle");
            String property = (String) entry.get("sid");
            if(property != null)
                articlePropertyMap.put(idArticle, property);
            articleSet.add(idArticle);           
        }
        return Arrays.asList(articleSet, articlePropertyMap);
    }

    protected Set<String> getPurchaseInvoiceSet(ExecutionContext<ClassPropertyInterface> context, boolean checkInvoiceExistence) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if(!checkInvoiceExistence)
            return null;

        Set<String> purchaseInvoiceSet = new HashSet<>();

        KeyExpr key = new KeyExpr("purchase.invoice");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("Purchase.Invoice", key);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

        query.addProperty("Purchase.idUserInvoice", findProperty("id[UserInvoice]").getExpr(context.getModifier(), key));
        query.and(findProperty("id[UserInvoice]").getExpr(context.getModifier(), key).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);

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
        return (idDocument != null ? idDocument : (userInvoiceObject == null ? "" : String.valueOf(userInvoiceObject.object))) + "_" + i;
    }

    private String modifyNameCountry(String nameCountry) {
        return nameCountry == null ? null : trim(nameCountry.replace("*", "")).toUpperCase();
    }
    
    private String getCurrentTimestamp() {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Calendar.getInstance().getTime());
    }
    
    private String readIdCustomer(ExecutionContext<ClassPropertyInterface> context, String idCustomerStock) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ObjectValue customerStockObject = idCustomerStock == null ? null : findProperty("stock[STRING[100]]").readClasses(context, new DataObject(idCustomerStock));
        ObjectValue customerObject = ((customerStockObject == null || customerStockObject instanceof NullValue) ? null : findProperty("legalEntity[Stock]").readClasses(context, (DataObject) customerStockObject));
        return (String) (customerObject == null ? null : findProperty("id[LegalEntity]").read(context, customerObject));
    }
}

