package lsfusion.erp.integration.universal.userpricelist;

import com.hexiong.jdbf.DBFReader;
import com.hexiong.jdbf.JDBFException;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import lsfusion.base.BaseUtils;
import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.erp.integration.universal.ImportColumns;
import lsfusion.erp.integration.universal.ImportUniversalActionProperty;
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
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.text.ParseException;
import java.util.*;

public class ImportUserPriceListActionProperty extends ImportUniversalActionProperty {
    private final ClassPropertyInterface userPriceListInterface;

    String defaultCountry = "БЕЛАРУСЬ";

    // Опциональные модули
    private ScriptingLogicsModule itemArticleLM;
    private ScriptingLogicsModule purchasePackLM;
    private ScriptingLogicsModule salePackLM;
    private ScriptingLogicsModule stockAdjustmentLM;
    
    public ImportUserPriceListActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClass("UserPriceList"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        userPriceListInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataObject userPriceListObject = context.getDataKeyValue(userPriceListInterface);

            ObjectValue importUserPriceListTypeObject = findProperty("importUserPriceListTypeUserPriceList").readClasses(context, userPriceListObject);

            if (!(importUserPriceListTypeObject instanceof NullValue)) {

                ImportColumns importColumns = readImportColumns(context, importUserPriceListTypeObject);

                if (importColumns != null && importColumns.getFileExtension() != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, importColumns.getFileExtension() + " Files", importColumns.getFileExtension());
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            importData(context, userPriceListObject, importColumns, file, false);

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
        } catch (JDBFException e) {
            throw new RuntimeException(e);
        } catch (UniversalImportException e) {
            e.printStackTrace();
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), e.getTitle()));
        }
    }

    public boolean importData(ExecutionContext context, DataObject userPriceListObject, ImportColumns importColumns,
                              byte[] file, boolean apply)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException, UniversalImportException, SQLHandledException, JDBFException {

        this.itemArticleLM = context.getBL().getModule("ItemArticle");
        this.purchasePackLM = context.getBL().getModule("PurchasePack");
        this.salePackLM = context.getBL().getModule("SalePack");
        this.stockAdjustmentLM = context.getBL().getModule("ImportUserPriceListStockAdjustment");
        
        List<UserPriceListDetail> userPriceListDetailList;
        
        Date dateDocument = (Date) findProperty("dateUserPriceList").read(context, userPriceListObject);
        dateDocument = dateDocument == null ? new Date(Calendar.getInstance().getTime().getTime()) : dateDocument;
        boolean barcodeMaybeUPC = importColumns.getBarcodeMaybeUPC() != null && importColumns.getBarcodeMaybeUPC();

        List<String> stringFields = Arrays.asList("idUserPriceList", "idItemGroup", "extraBarcodeItem", "articleItem", "captionItem", 
                "idUOMItem", "valueVAT", "originalName", "originalBarcode");

        List<String> bigDecimalFields = Arrays.asList("amountPackBarcode");

        List<String> dateFields = Arrays.asList("dateTo");
        
        if (importColumns.getFileExtension().equals("DBF"))
            userPriceListDetailList = importUserPriceListsFromDBF(file, importColumns, stringFields, bigDecimalFields, dateFields, dateDocument, barcodeMaybeUPC);
        else if (importColumns.getFileExtension().equals("XLS"))
            userPriceListDetailList = importUserPriceListsFromXLS(file, importColumns, stringFields, bigDecimalFields, dateFields, dateDocument, barcodeMaybeUPC);
        else if (importColumns.getFileExtension().equals("XLSX"))
            userPriceListDetailList = importUserPriceListsFromXLSX(file, importColumns, stringFields, bigDecimalFields, dateFields, dateDocument, barcodeMaybeUPC);
        else if (importColumns.getFileExtension().equals("CSV"))
            userPriceListDetailList = importUserPriceListsFromCSV(file, importColumns, stringFields, bigDecimalFields, dateFields, dateDocument, barcodeMaybeUPC);
        else
            userPriceListDetailList = null;

        boolean result = importUserPriceListDetails(context, userPriceListDetailList, importColumns, userPriceListObject, apply)
                && (importColumns.getQuantityAdjustmentColumn() == null || importAdjustmentDetails(context, userPriceListDetailList, importColumns.getStockObject(), importColumns.getItemKeyType(), apply));


        findProperty("originalUserPriceList").change(new DataObject(BaseUtils.mergeFileAndExtension(file, importColumns.getFileExtension().getBytes()), 
                DynamicFormatFileClass.get(false, true)), context, userPriceListObject);
        if(apply)
            context.apply();
        
        return result; 
    }

    private boolean importUserPriceListDetails(ExecutionContext context, List<UserPriceListDetail> userPriceListDetailsList,
                                               ImportColumns importColumnProperties, DataObject userPriceListObject, boolean apply) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Map<String, ImportColumnDetail> importColumns = importColumnProperties.getColumns();
        DataObject operationObject = importColumnProperties.getOperationObject();
        DataObject companyObject = importColumnProperties.getCompanyObject();
        DataObject defaultItemGroupObject = importColumnProperties.getDefaultItemGroupObject();
        Set<DataObject> dataPriceListTypeObjectList = importColumnProperties.getPriceColumns().keySet();

        if (userPriceListDetailsList != null && !userPriceListDetailsList.isEmpty()) {
            
            boolean doNotCreateItems = importColumnProperties.getDoNotCreateItems() != null && importColumnProperties.getDoNotCreateItems();

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(userPriceListDetailsList.size());

            if (operationObject != null) {
                props.add(new ImportProperty(operationObject, findProperty("operationUserPriceList").getMapping(userPriceListObject)));
            }

            if (companyObject != null) {
                props.add(new ImportProperty(companyObject, findProperty("companyUserPriceList").getMapping(userPriceListObject)));
            }

            ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcodeSku"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(idBarcodeSkuField));
            if (importColumnProperties.getDoNotCreateItems() != null && importColumnProperties.getDoNotCreateItems())
                barcodeKey.skipKey = true;
            keys.add(barcodeKey);
            props.add(new ImportProperty(idBarcodeSkuField, findProperty("idBarcode").getMapping(barcodeKey), getReplaceOnlyNull(importColumns, "barcodeItem")));
            props.add(new ImportProperty(idBarcodeSkuField, findProperty("extIdBarcode").getMapping(barcodeKey), getReplaceOnlyNull(importColumns, "barcodeItem")));
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(userPriceListDetailsList.get(i).barcodeItem);

            ImportField idExtraBarcodeSkuField = new ImportField(findProperty("idBarcodeSku"));
            ImportKey<?> extraBarcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(idExtraBarcodeSkuField));
            if (doNotCreateItems)
                extraBarcodeKey.skipKey = true;
            keys.add(extraBarcodeKey);
            props.add(new ImportProperty(idExtraBarcodeSkuField, findProperty("idBarcode").getMapping(extraBarcodeKey), getReplaceOnlyNull(importColumns, "extraBarcodeItem")));
            props.add(new ImportProperty(idExtraBarcodeSkuField, findProperty("extIdBarcode").getMapping(extraBarcodeKey), getReplaceOnlyNull(importColumns, "extraBarcodeItem")));
            fields.add(idExtraBarcodeSkuField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(userPriceListDetailsList.get(i).getFieldValue("extraBarcodeItem"));

            ImportField extIdPackBarcodeSkuField = new ImportField(findProperty("extIdBarcode"));
            ImportKey<?> packBarcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(extIdPackBarcodeSkuField));
            if (doNotCreateItems)
                packBarcodeKey.skipKey = true;
            keys.add(packBarcodeKey);
            props.add(new ImportProperty(extIdPackBarcodeSkuField, findProperty("extIdBarcode").getMapping(packBarcodeKey), getReplaceOnlyNull(importColumns, "packBarcode")));
            fields.add(extIdPackBarcodeSkuField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(userPriceListDetailsList.get(i).extIdPackBarcode);

            if (showField(userPriceListDetailsList, "packBarcode")) {
                ImportField packBarcodeSkuField = new ImportField(findProperty("idBarcodeSku"));
                props.add(new ImportProperty(packBarcodeSkuField, findProperty("idBarcode").getMapping(packBarcodeKey), getReplaceOnlyNull(importColumns, "packBarcode")));
                fields.add(packBarcodeSkuField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).packBarcode);
            }

            if (showField(userPriceListDetailsList, "amountPackBarcode")) {
                ImportField amountBarcodeField = new ImportField(findProperty("amountBarcode"));
                props.add(new ImportProperty(amountBarcodeField, findProperty("amountBarcode").getMapping(packBarcodeKey), getReplaceOnlyNull(importColumns, "amountPackBarcode")));
                fields.add(amountBarcodeField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).getFieldValue("amountPackBarcode"));
            }

            boolean isItemKey = (importColumnProperties.getItemKeyType() == null || importColumnProperties.getItemKeyType().equals("item"));
            
            ImportField idItemField = new ImportField(findProperty("idItem"));
            
            LCP iGroupAggr = findProperty(isItemKey ? "itemId" : "skuBarcodeId");
            ImportField iField = isItemKey ? idItemField : idBarcodeSkuField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    iGroupAggr.getMapping(iField));
            
            if (doNotCreateItems)
                itemKey.skipKey = true;
            keys.add(itemKey);
            
            if (isItemKey || showField(userPriceListDetailsList, "idItem")) {
                props.add(new ImportProperty(idItemField, findProperty("idItem").getMapping(itemKey)));
                fields.add(idItemField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).idItem);
            }
            
            if (!doNotCreateItems) {
                if (purchasePackLM != null)
                    props.add(new ImportProperty(extIdPackBarcodeSkuField, purchasePackLM.findProperty("Purchase.packBarcodeSku").getMapping(itemKey),
                            object(findClass("Barcode")).getMapping(packBarcodeKey), getReplaceOnlyNull(importColumns, "packBarcode")));
                if (salePackLM != null)
                    props.add(new ImportProperty(extIdPackBarcodeSkuField, salePackLM.findProperty("Sale.packBarcodeSku").getMapping(itemKey),
                            object(findClass("Barcode")).getMapping(packBarcodeKey), getReplaceOnlyNull(importColumns, "packBarcode")));
            }

            if (showField(userPriceListDetailsList, "idItemGroup")) {
                ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
                ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) findClass("ItemGroup"),
                        findProperty("itemGroupId").getMapping(idItemGroupField));
                keys.add(itemGroupKey);
                props.add(new ImportProperty(idItemGroupField, findProperty("itemGroupItem").getMapping(itemKey),
                        object(findClass("ItemGroup")).getMapping(itemGroupKey), getReplaceOnlyNull(importColumns, "idItemGroup")));
                fields.add(idItemGroupField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).getFieldValue("idItemGroup"));
            }

            if (itemArticleLM != null && showField(userPriceListDetailsList, "articleItem")) {
                ImportField idArticleItemField = new ImportField(itemArticleLM.findProperty("idArticleItem"));
                ImportKey<?> articleKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClass("Article"),
                        itemArticleLM.findProperty("articleId").getMapping(idArticleItemField));
                keys.add(articleKey);
                props.add(new ImportProperty(idArticleItemField, itemArticleLM.findProperty("idArticle").getMapping(articleKey)));
                props.add(new ImportProperty(idArticleItemField, itemArticleLM.findProperty("articleItem").getMapping(itemKey),
                        itemArticleLM.object(itemArticleLM.findClass("Article")).getMapping(articleKey)));
                fields.add(idArticleItemField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).getFieldValue("articleItem"));
            }

            if (showField(userPriceListDetailsList, "idUserPriceList")) {
                ImportField idUserPriceListField = new ImportField(findProperty("idUserPriceList"));
                props.add(new ImportProperty(idUserPriceListField, findProperty("numberUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(importColumns, "idUserPriceList")));
                fields.add(idUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).getFieldValue("idUserPriceList"));

                ImportField isPostedUserPriceListField = new ImportField(findProperty("isPostedUserPriceList"));
                props.add(new ImportProperty(isPostedUserPriceListField, findProperty("isPostedUserPriceList").getMapping(userPriceListObject)));
                fields.add(isPostedUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(true);
            }

            ImportField idUserPriceListDetailField = new ImportField(findProperty("idUserPriceListDetail"));
            ImportKey<?> userPriceListDetailKey = new ImportKey((ConcreteCustomClass) findClass("UserPriceListDetail"),
                    findProperty("userPriceListDetailIdUserPriceList").getMapping(idUserPriceListDetailField, userPriceListObject));
            keys.add(userPriceListDetailKey);
            props.add(new ImportProperty(userPriceListObject, findProperty("userPriceListUserPriceListDetail").getMapping(userPriceListDetailKey)));
            props.add(new ImportProperty(idUserPriceListDetailField, findProperty("idUserPriceListDetail").getMapping(userPriceListDetailKey)));
            props.add(new ImportProperty(iField, findProperty("skuUserPriceListDetail").getMapping(userPriceListDetailKey),
                    object(findClass("Sku")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, findProperty("skuBarcode").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, findProperty("skuBarcode").getMapping(extraBarcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, findProperty("skuBarcode").getMapping(packBarcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            fields.add(idUserPriceListDetailField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(userPriceListDetailsList.get(i).idUserPriceListDetail);

            ImportField indexField = new ImportField(findProperty("dataIndexUserPriceListDetail"));
            props.add(new ImportProperty(indexField, findProperty("dataIndexUserPriceListDetail").getMapping(userPriceListDetailKey)));
            fields.add(indexField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(i+1);

            if (defaultItemGroupObject != null) {
                props.add(new ImportProperty(defaultItemGroupObject, findProperty("itemGroupItem").getMapping(itemKey)));
            }
            
            if (showField(userPriceListDetailsList, "originalName")) {
                ImportField originalNameField = new ImportField(findProperty("originalNameSkuUserPriceListDetail"));
                props.add(new ImportProperty(originalNameField, findProperty("originalNameSkuUserPriceListDetail").getMapping(userPriceListDetailKey)));
                fields.add(originalNameField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).getFieldValue("originalName"));
            }

            if (showField(userPriceListDetailsList, "originalBarcode")) {
                ImportField originalIdBarcodeField = new ImportField(findProperty("originalIdBarcodeSkuUserPriceListDetail"));
                props.add(new ImportProperty(originalIdBarcodeField, findProperty("originalIdBarcodeSkuUserPriceListDetail").getMapping(userPriceListDetailKey)));
                fields.add(originalIdBarcodeField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).getFieldValue("originalBarcode"));
            }

            if (showField(userPriceListDetailsList, "captionItem")) {
                ImportField captionItemField = new ImportField(findProperty("captionItem"));
                props.add(new ImportProperty(captionItemField, findProperty("captionItem").getMapping(itemKey), getReplaceOnlyNull(importColumns, "captionItem")));
                fields.add(captionItemField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).getFieldValue("captionItem"));
            }

            if (showField(userPriceListDetailsList, "idUOMItem")) {
                ImportField idUOMField = new ImportField(findProperty("idUOM"));
                ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) findClass("UOM"),
                        findProperty("UOMId").getMapping(idUOMField));
                keys.add(UOMKey);
                props.add(new ImportProperty(idUOMField, findProperty("idUOM").getMapping(UOMKey), getReplaceOnlyNull(importColumns, "idUOMItem")));
                props.add(new ImportProperty(idUOMField, findProperty("shortNameUOM").getMapping(UOMKey), getReplaceOnlyNull(importColumns, "idUOMItem")));
                props.add(new ImportProperty(idUOMField, findProperty("UOMItem").getMapping(itemKey),
                        object(findClass("UOM")).getMapping(UOMKey), getReplaceOnlyNull(importColumns, "idUOMItem")));
                fields.add(idUOMField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).getFieldValue("idUOMItem"));
            }

            for (DataObject dataPriceListTypeObject : dataPriceListTypeObjectList) {
                ImportField pricePriceListDetailDataPriceListTypeField = new ImportField(findProperty("pricePriceListDetailDataPriceListType"));
                props.add(new ImportProperty(pricePriceListDetailDataPriceListTypeField, findProperty("pricePriceListDetailDataPriceListType").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                fields.add(pricePriceListDetailDataPriceListTypeField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).prices.get(dataPriceListTypeObject));
            }

            if (showField(userPriceListDetailsList, "dateUserPriceList")) {
                ImportField dateUserPriceListField = new ImportField(findProperty("dateUserPriceList"));
                props.add(new ImportProperty(dateUserPriceListField, findProperty("dateUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(importColumns, "date")));
                props.add(new ImportProperty(dateUserPriceListField, findProperty("fromDateUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(importColumns, "date")));
                fields.add(dateUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).dateUserPriceList);

                ImportField timeUserPriceListField = new ImportField(findProperty("timeUserPriceList"));
                props.add(new ImportProperty(timeUserPriceListField, findProperty("timeUserPriceList").getMapping(userPriceListObject)));
                props.add(new ImportProperty(timeUserPriceListField, findProperty("fromTimeUserPriceList").getMapping(userPriceListObject)));
                fields.add(timeUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(new Time(0, 0, 0));
            }

            if (showField(userPriceListDetailsList, "isPosted")) {
                ImportField isPostedUserPriceListField = new ImportField(findProperty("isPostedUserPriceList"));
                props.add(new ImportProperty(isPostedUserPriceListField, findProperty("isPostedUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(importColumns, "isPosted")));
                fields.add(isPostedUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).isPosted);
            }

            if (showField(userPriceListDetailsList, "dateFrom")) {
                ImportField dateFromUserPriceListField = new ImportField(findProperty("fromDateUserPriceList"));
                props.add(new ImportProperty(dateFromUserPriceListField, findProperty("fromDateUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(importColumns, "dateFrom")));
                fields.add(dateFromUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).dateFrom);
            }

            if (showField(userPriceListDetailsList, "dateTo")) {
                ImportField dateToUserPriceListField = new ImportField(findProperty("toDateUserPriceList"));
                props.add(new ImportProperty(dateToUserPriceListField, findProperty("toDateUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(importColumns, "dateTo")));
                fields.add(dateToUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).getFieldValue("dateTo"));
            }

            if (showField(userPriceListDetailsList, "valueVAT")) {
                ImportField valueVATUserPriceListDetailField = new ImportField(findProperty("valueVATUserPriceListDetail"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) findClass("Range"),
                        findProperty("valueCurrentVATDefaultValue").getMapping(valueVATUserPriceListDetailField));
                keys.add(VATKey);
                fields.add(valueVATUserPriceListDetailField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).getFieldValue("valueVAT"));

                ImportField dateField = new ImportField(DateClass.instance);
                props.add(new ImportProperty(dateField, findProperty("dataDateBarcode").getMapping(barcodeKey)));
                fields.add(dateField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).dateVAT);

                ImportField countryVATField = new ImportField(findProperty("nameCountry"));
                ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) findClass("Country"),
                        findProperty("countryName").getMapping(countryVATField));
                keys.add(countryKey);
                props.add(new ImportProperty(valueVATUserPriceListDetailField, findProperty("VATItemCountry").getMapping(itemKey, countryKey),
                        object(findClass("Range")).getMapping(VATKey), getReplaceOnlyNull(importColumns, "valueVAT")));
                fields.add(countryVATField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(defaultCountry);
            }

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.getSession();
            if(apply)
                session.pushVolatileStats("UPL_PLD");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            String result = null;
            if (apply) {
                result = session.applyMessage(context);
                session.popVolatileStats();
            }

            findAction("formRefresh").execute(context);

            return result == null;
        }
        return false;
    }

    private boolean importAdjustmentDetails(ExecutionContext context, List<UserPriceListDetail> dataAdjustment,
                                            DataObject stockObject, String itemKeyType, boolean apply)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (stockAdjustmentLM != null && dataAdjustment != null) {

            stockAdjustmentLM.findAction("unpostAllUserAdjustment").execute(context.getSession());
            stockAdjustmentLM.findAction("overImportAdjustment").execute(context.getSession());

            DataObject userAdjustmentObject = context.addObject((ConcreteCustomClass) stockAdjustmentLM.findClass("UserAdjustment"));

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(dataAdjustment.size());

            if (stockObject != null) {
                props.add(new ImportProperty(stockObject, stockAdjustmentLM.findProperty("stockUserAdjustment").getMapping(userAdjustmentObject)));
            }

            ImportField idItemField = new ImportField(findProperty("idItem"));
            fields.add(idItemField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(dataAdjustment.get(i).idItem);

            ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcodeSku"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(dataAdjustment.get(i).barcodeItem);

            LCP<?> iGroupAggr = findProperty((itemKeyType == null || itemKeyType.equals("item")) ? "itemId" : "skuBarcodeId");
            ImportField iField = (itemKeyType == null || itemKeyType.equals("item")) ? idItemField : idBarcodeSkuField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    iGroupAggr.getMapping(iField));
            keys.add(itemKey);

            ImportField idUserAdjustmentDetailField = new ImportField(stockAdjustmentLM.findProperty("idUserAdjustmentDetail"));
            ImportKey<?> userAdjustmentDetailKey = new ImportKey((ConcreteCustomClass) stockAdjustmentLM.findClass("UserAdjustmentDetail"),
                    stockAdjustmentLM.findProperty("userAdjustmentDetailIdUserAdjustment").getMapping(idUserAdjustmentDetailField, userAdjustmentObject));
            keys.add(userAdjustmentDetailKey);
            props.add(new ImportProperty(userAdjustmentObject, stockAdjustmentLM.findProperty("userAdjustmentUserAdjustmentDetail").getMapping(userAdjustmentDetailKey)));
            props.add(new ImportProperty(idUserAdjustmentDetailField, stockAdjustmentLM.findProperty("idUserAdjustmentDetail").getMapping(userAdjustmentDetailKey)));
            props.add(new ImportProperty(iField, stockAdjustmentLM.findProperty("skuUserAdjustmentDetail").getMapping(userAdjustmentDetailKey),
                    object(findClass("Sku")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, findProperty("skuBarcode").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            fields.add(idUserAdjustmentDetailField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(dataAdjustment.get(i).idUserPriceListDetail);

            ImportField quantityUserAdjustmentDetailField = new ImportField(stockAdjustmentLM.findProperty("quantityUserAdjustmentDetail"));
            props.add(new ImportProperty(quantityUserAdjustmentDetailField, stockAdjustmentLM.findProperty("quantityUserAdjustmentDetail").getMapping(userAdjustmentDetailKey)));
            fields.add(quantityUserAdjustmentDetailField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(dataAdjustment.get(i).quantityAdjustment);

            if (showField(dataAdjustment, "dateUserPriceList")) {
                ImportField dateUserAdjustmentField = new ImportField(stockAdjustmentLM.findProperty("dateUserAdjustment"));
                props.add(new ImportProperty(dateUserAdjustmentField, stockAdjustmentLM.findProperty("dateUserAdjustment").getMapping(userAdjustmentObject)));
                fields.add(dateUserAdjustmentField);
                for (int i = 0; i < dataAdjustment.size(); i++)
                    data.get(i).add(dataAdjustment.get(i).dateUserPriceList);

                ImportField timeUserAdjustmentField = new ImportField(stockAdjustmentLM.findProperty("timeUserAdjustment"));
                props.add(new ImportProperty(timeUserAdjustmentField, stockAdjustmentLM.findProperty("timeUserAdjustment").getMapping(userAdjustmentObject)));
                fields.add(timeUserAdjustmentField);
                for (int i = 0; i < dataAdjustment.size(); i++)
                    data.get(i).add(new Time(0, 0, 0));
            }

            ImportField isPostedAdjustmentField = new ImportField(stockAdjustmentLM.findProperty("isPostedAdjustment"));
            props.add(new ImportProperty(isPostedAdjustmentField, stockAdjustmentLM.findProperty("isPostedAdjustment").getMapping(userAdjustmentObject)));
            fields.add(isPostedAdjustmentField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(true);


            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.getSession();
            if(apply)
                session.pushVolatileStats("UPL_AD");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            String result = null;
            if (apply) {
                result = session.applyMessage(context);
                session.popVolatileStats();
            }

            findAction("formRefresh").execute(context);

            return result == null;
        }
        return false;
    }

    private List<UserPriceListDetail> importUserPriceListsFromXLS(byte[] importFile, ImportColumns importColumns, 
                                                                  List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, 
                                                                  Date dateDocument, boolean barcodeMaybeUPC)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding("cp1251");
        Workbook wb = Workbook.getWorkbook(new ByteArrayInputStream(importFile), ws);
        Sheet sheet = wb.getSheet(0);

        for (int i = importColumns.getStartRow() - 1; i < sheet.getRows(); i++) {

            Map<String, Object> fieldValues = new HashMap<String, Object>();
            for(String field : stringFields) {
                String value = getXLSFieldValue(sheet, i, importColumns.getColumns().get(field));
                if(field.equals("extraBarcodeItem")){
                    fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7, barcodeMaybeUPC));
                } else if(field.equals("valueVAT")) {
                  fieldValues.put(field, parseVAT(value));  
                } else 
                    fieldValues.put(field, value);
            }
            for(String field : bigDecimalFields) {
                BigDecimal value = getXLSBigDecimalFieldValue(sheet, i, importColumns.getColumns().get(field));
                fieldValues.put(field, value);
            }
            for(String field : dateFields) {
                Date value = getXLSDateFieldValue(sheet, i, importColumns.getColumns().get(field));
                fieldValues.put(field, value);
            }
           
            String idItem = getXLSFieldValue(sheet, i, importColumns.getColumns().get("idItem"));
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSFieldValue(sheet, i, importColumns.getColumns().get("barcodeItem")), 7, barcodeMaybeUPC);
            String packBarcode = BarcodeUtils.appendCheckDigitToBarcode(getXLSFieldValue(sheet, i, importColumns.getColumns().get("packBarcode")), 7, barcodeMaybeUPC);
            Date dateUserPriceList = getXLSDateFieldValue(sheet, i, importColumns.getColumns().get("dateUserPriceList"));
            Date dateFrom = getXLSDateFieldValue(sheet, i, importColumns.getColumns().get("dateFrom"), dateDocument);
            Date dateVAT = dateUserPriceList == null ? dateFrom : dateUserPriceList;
            BigDecimal quantityAdjustment = getXLSBigDecimalFieldValue(sheet, i, new ImportColumnDetail("quantityAdjustment", importColumns.getQuantityAdjustmentColumn(), false));
            String idUserPriceListDetail = (idItem == null ? "" : idItem) + "_" + (barcodeItem == null ? "" : barcodeItem);
            String extIdPackBarcode = packBarcode == null ? ((importColumns.getItemKeyType().equals("barcode") ? barcodeItem : idItem) + "_pack") : packBarcode;
            if (!idUserPriceListDetail.equals("_")) {
                Map<DataObject, BigDecimal> prices = new HashMap<DataObject, BigDecimal>();
                for (Map.Entry<DataObject, String[]> entry : importColumns.getPriceColumns().entrySet()) {
                    BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, new ImportColumnDetail("price", entry.getValue(), false));
                    prices.put(entry.getKey(), price);
                }
                userPriceListDetailList.add(new UserPriceListDetail(fieldValues, importColumns.getIsPosted(), idUserPriceListDetail,
                        idItem, barcodeItem, extIdPackBarcode, packBarcode, prices, quantityAdjustment, dateUserPriceList, 
                        dateFrom, dateVAT));

            }
        }

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromCSV(byte[] importFile, ImportColumns importColumns, 
                                                                  List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, 
                                                                  Date dateDocument, boolean barcodeMaybeUPC)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile)));
        String line;

        List<String[]> valuesList = new ArrayList<String[]>();
        while ((line = br.readLine()) != null) {
            valuesList.add(line.split(importColumns.getSeparator()));
        }

        for (int count = importColumns.getStartRow(); count <= valuesList.size(); count++) {

            if (count >= importColumns.getStartRow()) {

                Map<String, Object> fieldValues = new HashMap<String, Object>();
                for(String field : stringFields) {
                    String value = getCSVFieldValue(valuesList, importColumns.getColumns().get(field), count);
                    if(field.equals("extraBarcodeItem")) {
                        fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7, barcodeMaybeUPC));
                    } else if(field.equals("valueVAT")) {
                        fieldValues.put(field, parseVAT(value));
                    } else 
                        fieldValues.put(field, value);
                }
                for(String field : bigDecimalFields) {
                    BigDecimal value = getCSVBigDecimalFieldValue(valuesList, importColumns.getColumns().get(field), count);
                    fieldValues.put(field, value);
                }
                for(String field : dateFields) {
                    Date value = getCSVDateFieldValue(valuesList, importColumns.getColumns().get(field), count);
                    fieldValues.put(field, value);
                }
                
                String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getCSVFieldValue(valuesList, importColumns.getColumns().get("barcodeItem"), count), 7, barcodeMaybeUPC);
                String packBarcode = BarcodeUtils.appendCheckDigitToBarcode(getCSVFieldValue(valuesList, importColumns.getColumns().get("packBarcode"), count), 7, barcodeMaybeUPC);
                String idItem = getCSVFieldValue(valuesList, importColumns.getColumns().get("idItem"), count);
                Date dateUserPriceList = getCSVDateFieldValue(valuesList, importColumns.getColumns().get("dateFrom"), count);
                Date dateFrom = getCSVDateFieldValue(valuesList, importColumns.getColumns().get("dateFrom"), count, dateDocument);
                Date dateVAT = dateUserPriceList == null ? dateFrom : dateUserPriceList;
                BigDecimal quantityAdjustment = getCSVBigDecimalFieldValue(valuesList, new ImportColumnDetail("quantityAdjustment", importColumns.getQuantityAdjustmentColumn(), false), count);
                String idUserPriceListDetail = (idItem == null ? "" : idItem) + "_" + (barcodeItem == null ? "" : barcodeItem);
                String extIdPackBarcode = packBarcode == null ? ((importColumns.getItemKeyType().equals("barcode") ? barcodeItem : idItem) + "_pack") : packBarcode;
                if (!idUserPriceListDetail.equals("_")) {
                    Map<DataObject, BigDecimal> prices = new HashMap<DataObject, BigDecimal>();
                    for (Map.Entry<DataObject, String[]> entry : importColumns.getPriceColumns().entrySet()) {
                        BigDecimal price = getCSVBigDecimalFieldValue(valuesList, new ImportColumnDetail("price", entry.getValue(), false), count);
                        prices.put(entry.getKey(), price);
                    }
                    userPriceListDetailList.add(new UserPriceListDetail(fieldValues, importColumns.getIsPosted(), idUserPriceListDetail,
                            idItem, barcodeItem, extIdPackBarcode, packBarcode, prices, quantityAdjustment,
                            dateUserPriceList, dateFrom, dateVAT));
                }
            }
        }

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromXLSX(byte[] importFile, ImportColumns importColumns, 
                                                                   List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, 
                                                                   Date dateDocument, boolean barcodeMaybeUPC)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = importColumns.getStartRow() - 1; i <= sheet.getLastRowNum(); i++) {

            Map<String, Object> fieldValues = new HashMap<String, Object>();
            for(String field : stringFields) {
                String value = getXLSXFieldValue(sheet, i, importColumns.getColumns().get(field));
                if(field.equals("extraBarcodeItem"))
                    fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7, barcodeMaybeUPC));
                else if(field.equals("valueVAT")) {
                    fieldValues.put(field, parseVAT(value));
                } else
                    fieldValues.put(field, value);
            }
            for(String field : bigDecimalFields) {
                BigDecimal value = getXLSXBigDecimalFieldValue(sheet, i, importColumns.getColumns().get(field));
                fieldValues.put(field, value);
            }
            for(String field : dateFields) {
                Date value = getXLSXDateFieldValue(sheet, i, importColumns.getColumns().get(field));
                fieldValues.put(field, value);
            }
            
            String idItem = getXLSXFieldValue(sheet, i, importColumns.getColumns().get("idItem"));
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSXFieldValue(sheet, i, importColumns.getColumns().get("barcodeItem")),7, barcodeMaybeUPC);
            String packBarcode = BarcodeUtils.appendCheckDigitToBarcode(getXLSXFieldValue(sheet, i, importColumns.getColumns().get("packBarcode")), 7, barcodeMaybeUPC);
            BigDecimal quantityAdjustment = getXLSXBigDecimalFieldValue(sheet, i, new ImportColumnDetail("quantityAdjustment", importColumns.getQuantityAdjustmentColumn(), false));
            Date dateUserPriceList = getXLSXDateFieldValue(sheet, i, importColumns.getColumns().get("dateUserPriceList"));
            Date dateFrom = getXLSXDateFieldValue(sheet, i, importColumns.getColumns().get("dateFrom"), dateDocument);
            Date dateVAT = dateUserPriceList == null ? dateFrom : dateUserPriceList;

            String idUserPriceListDetail = (idItem == null ? "" : idItem) + "_" + (barcodeItem == null ? "" : barcodeItem);
            String extIdPackBarcode = packBarcode == null ? ((importColumns.getItemKeyType().equals("barcode") ? barcodeItem : idItem) + "_pack") : packBarcode;
            if (!idUserPriceListDetail.equals("_")) {
                Map<DataObject, BigDecimal> prices = new HashMap<DataObject, BigDecimal>();
                for (Map.Entry<DataObject, String[]> entry : importColumns.getPriceColumns().entrySet()) {
                    BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, new ImportColumnDetail("price", entry.getValue(), false));
                    prices.put(entry.getKey(), price);
                }
                userPriceListDetailList.add(new UserPriceListDetail(fieldValues, importColumns.getIsPosted(), idUserPriceListDetail,
                        idItem, barcodeItem, extIdPackBarcode, packBarcode, prices, quantityAdjustment, dateUserPriceList, 
                        dateFrom, dateVAT));
            }
        }

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromDBF(byte[] importFile, ImportColumns importColumns, 
                                                                  List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, 
                                                                  Date dateDocument, boolean barcodeMaybeUPC)
            throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException, JDBFException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        File tempFile = File.createTempFile("dutiesTNVED", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);

        DBFReader dbfReader = new DBFReader(new FileInputStream(tempFile));
        String charset = getDBFCharset(tempFile);

        Map<String, Integer> fieldNamesMap = new HashMap<String, Integer>();
        for (int i = 0; i < dbfReader.getFieldCount(); i++) {
            fieldNamesMap.put(dbfReader.getField(i).getName(), i);
        }

        for (int i = 0; i < importColumns.getStartRow() - 1; i++) {
            dbfReader.nextRecord(Charset.forName(charset));
        }

        for (int i = importColumns.getStartRow() - 1; dbfReader.hasNextRecord(); i++) {

            Object entry[] = dbfReader.nextRecord(Charset.forName(charset));

            Map<String, Object> fieldValues = new HashMap<String, Object>();
            for (String field : stringFields) {
                String value = getJDBFFieldValue(entry, fieldNamesMap, importColumns.getColumns().get(field), i);
                if(field.equals("extraBarcodeItem"))
                    fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7, barcodeMaybeUPC));
                else if(field.equals("valueVAT")) {
                    fieldValues.put(field, parseVAT(value));
                } else
                    fieldValues.put(field, value);
            }
            for(String field : bigDecimalFields) {
                BigDecimal value = getJDBFBigDecimalFieldValue(entry, fieldNamesMap, importColumns.getColumns().get(field), i);
                fieldValues.put(field, value);
            }
            for(String field : dateFields) {
                Date value = getJDBFDateFieldValue(entry, fieldNamesMap, importColumns.getColumns().get(field), i);
                fieldValues.put(field, value);
            }

            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getJDBFFieldValue(entry, fieldNamesMap, importColumns.getColumns().get("barcodeItem"), i), 7, barcodeMaybeUPC);
            String packBarcode = BarcodeUtils.appendCheckDigitToBarcode(getJDBFFieldValue(entry, fieldNamesMap, importColumns.getColumns().get("packBarcode"), i), 7, barcodeMaybeUPC);
            String idItem = getJDBFFieldValue(entry, fieldNamesMap, importColumns.getColumns().get("idItem"), i);
            BigDecimal quantityAdjustment = getJDBFBigDecimalFieldValue(entry, fieldNamesMap, new ImportColumnDetail("quantityAdjustment", importColumns.getQuantityAdjustmentColumn(), false), i);
            Date dateUserPriceList = getJDBFDateFieldValue(entry, fieldNamesMap, importColumns.getColumns().get("dateUserPriceList"), i);
            Date dateFrom = getJDBFDateFieldValue(entry, fieldNamesMap, importColumns.getColumns().get("dateFrom"), i, dateDocument);
            String idUserPriceListDetail = (idItem == null ? "" : idItem) + "_" + (barcodeItem == null ? "" : barcodeItem);
            String extIdPackBarcode = packBarcode == null ? ((importColumns.getItemKeyType().equals("barcode") ? barcodeItem : idItem) + "_pack") : packBarcode;
            if (!idUserPriceListDetail.equals("_")) {
                Map<DataObject, BigDecimal> prices = new HashMap<DataObject, BigDecimal>();
                for (Map.Entry<DataObject, String[]> priceEntry : importColumns.getPriceColumns().entrySet()) {
                    BigDecimal price = getJDBFBigDecimalFieldValue(entry, fieldNamesMap, new ImportColumnDetail("price", priceEntry.getValue(), false), i);
                    prices.put(priceEntry.getKey(), price);
                }
                userPriceListDetailList.add(new UserPriceListDetail(fieldValues, importColumns.getIsPosted(), idUserPriceListDetail,
                        idItem, barcodeItem, extIdPackBarcode, packBarcode, prices, quantityAdjustment, dateUserPriceList, 
                        dateFrom, dateFrom));
            }
        }

        dbfReader.close();
        tempFile.delete();
        return userPriceListDetailList;
    }

    public ImportColumns readImportColumns(ExecutionContext context, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, ImportColumnDetail> columns = readColumns(context, importTypeObject);
        Map<DataObject, String[]> priceColumns = readPriceImportColumns(context, importTypeObject);
        String quantityAdjustmentColumn = (String) findProperty("quantityAdjustmentImportUserPriceListType").read(context, importTypeObject);

        ObjectValue operation = findProperty("operationImportUserPriceListType").readClasses(context, (DataObject) importTypeObject);
        DataObject operationObject = operation instanceof NullValue ? null : (DataObject) operation;

        ObjectValue company = findProperty("companyImportUserPriceListType").readClasses(context, (DataObject) importTypeObject);
        DataObject companyObject = company instanceof NullValue ? null : (DataObject) company;

        ObjectValue stock = findProperty("stockImportUserPriceListType").readClasses(context, (DataObject) importTypeObject);
        DataObject stockObject = stock instanceof NullValue ? null : (DataObject) stock;

        ObjectValue defaultItemGroup = findProperty("defaultItemGroupImportUserPriceListType").readClasses(context, (DataObject) importTypeObject);
        DataObject defaultItemGroupObject = defaultItemGroup instanceof NullValue ? null : (DataObject) defaultItemGroup;

        String fileExtension = (String) findProperty("captionImportUserPriceListTypeFileExtensionImportUserPriceListType").read(context, importTypeObject);
        fileExtension = fileExtension == null ? null : fileExtension.trim();
        
        String itemKeyType = (String) findProperty("nameImportUserPriceListKeyTypeImportUserPriceListType").read(context, importTypeObject);
        String[] parts = itemKeyType == null ? null : itemKeyType.split("\\.");
        itemKeyType = parts == null ? null : parts[parts.length - 1].trim();
        
        String separator = trim((String) findProperty("separatorImportUserPriceListType").read(context, importTypeObject), ";");
        Integer startRow = (Integer) findProperty("startRowImportUserPriceListType").read(context, importTypeObject);
        startRow = startRow == null || startRow.equals(0) ? 1 : startRow;
        
        Boolean isPosted = (Boolean) findProperty("isPostedImportUserPriceListType").read(context, importTypeObject);
        Boolean doNotCreateItems = (Boolean) findProperty("doNotCreateItemsImportUserPriceListType").read(context, importTypeObject);        
        Boolean barcodeMaybeUPC = (Boolean) findProperty("barcodeMaybeUPCImportUserPriceListType").read(context, importTypeObject);

        return new ImportColumns(columns, priceColumns, quantityAdjustmentColumn, operationObject, companyObject, stockObject, defaultItemGroupObject, 
                fileExtension, itemKeyType, separator, startRow, isPosted, doNotCreateItems, barcodeMaybeUPC);
    }

    private Map<String, ImportColumnDetail> readColumns(ExecutionContext context, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Map<String, ImportColumnDetail> importColumns = new HashMap<String, ImportColumnDetail>();

        LCP<PropertyInterface> isImportTypeDetail = (LCP<PropertyInterface>) is(findClass("ImportUserPriceListTypeDetail"));
        ImRevMap<PropertyInterface, KeyExpr> keys = isImportTypeDetail.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<PropertyInterface, Object> query = new QueryBuilder<PropertyInterface, Object>(keys);
        query.addProperty("staticName", findProperty("staticName").getExpr(context.getModifier(), key));
        query.addProperty("staticCaption", findProperty("staticCaption").getExpr(context.getModifier(), key));
        query.addProperty("replaceOnlyNullImportUserPriceListTypeImportUserPriceListTypeDetail", findProperty("replaceOnlyNullImportUserPriceListTypeImportUserPriceListTypeDetail").getExpr(context.getModifier(), importTypeObject.getExpr(), key));
        query.addProperty("indexImportUserPriceListTypeImportUserPriceListTypeDetail", findProperty("indexImportUserPriceListTypeImportUserPriceListTypeDetail").getExpr(context.getModifier(), importTypeObject.getExpr(), key));
        query.and(isImportTypeDetail.getExpr(key).getWhere());
        ImOrderMap<ImMap<PropertyInterface, Object>, ImMap<Object, Object>> result = query.execute(context);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String[] field = ((String) entry.get("staticName")).trim().split("\\.");
            String captionProperty = (String) entry.get("staticCaption");
            captionProperty = captionProperty == null ? null : captionProperty.trim();
            boolean replaceOnlyNull = entry.get("replaceOnlyNullImportUserPriceListTypeImportUserPriceListTypeDetail") != null;
            String indexes = (String) entry.get("indexImportUserPriceListTypeImportUserPriceListTypeDetail");
            if (indexes != null) {
                String[] splittedIndexes = indexes.split("\\+");
                for (int i = 0; i < splittedIndexes.length; i++)
                    splittedIndexes[i] = splittedIndexes[i].contains("=") ? splittedIndexes[i] : splittedIndexes[i].trim();
                importColumns.put(field[field.length - 1], new ImportColumnDetail(captionProperty, indexes, splittedIndexes, replaceOnlyNull));
            }
        }
        return importColumns;
    }

    private Map<DataObject, String[]> readPriceImportColumns(ExecutionContext context, ObjectValue importUserPriceListTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Map<DataObject, String[]> importColumns = new HashMap<DataObject, String[]>();

        LCP<PropertyInterface> isDataPriceListType = (LCP<PropertyInterface>) is(findClass("DataPriceListType"));
        ImRevMap<PropertyInterface, KeyExpr> keys = isDataPriceListType.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<PropertyInterface, Object> query = new QueryBuilder<PropertyInterface, Object>(keys);
        query.addProperty("indexImportUserPriceListTypeDataPriceListType", findProperty("indexImportUserPriceListTypeDataPriceListType").getExpr(context.getModifier(), importUserPriceListTypeObject.getExpr(), key));
        query.and(isDataPriceListType.getExpr(key).getWhere());
        ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(context.getSession());

        for (int i = 0, size = result.size(); i < size; i++) {
            ImMap<Object, ObjectValue> entryValue = result.getValue(i);

            DataObject dataPriceListTypeObject = result.getKey(i).valueIt().iterator().next();
            String indexes = (String) entryValue.get("indexImportUserPriceListTypeDataPriceListType").getValue();
            if (indexes != null) {
                String[] splittedIndexes = indexes.split("\\+");
                for (int j = 0; j < splittedIndexes.length; j++)
                    splittedIndexes[j] = splittedIndexes[j].trim();
                importColumns.put(dataPriceListTypeObject, splittedIndexes);
            }
        }
        return importColumns;
    }

    protected Boolean showField(List<UserPriceListDetail> data, String fieldName) {
        try {

            boolean found = false;
            Field fieldValues = UserPriceListDetail.class.getField("fieldValues");
            for (UserPriceListDetail entry : data) {
                Map<String, Object> values = (Map<String, Object>) fieldValues.get(entry);
                if(!found) {
                    if (values.containsKey(fieldName))
                        found = true;
                    else
                        break;
                }
                if (values.get(fieldName) != null)
                    return true;
            }

            if(!found) {
                Field field = UserPriceListDetail.class.getField(fieldName);

                for (UserPriceListDetail entry : data) {
                    if (field.get(entry) != null)
                        return true;
                }
            }
        } catch (NoSuchFieldException e) {
            return true;
        } catch (IllegalAccessException e) {
            return true;
        }
        return false;
    }
}

