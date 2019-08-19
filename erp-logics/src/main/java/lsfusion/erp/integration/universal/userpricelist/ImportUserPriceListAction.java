package lsfusion.erp.integration.universal.userpricelist;

import com.hexiong.jdbf.DBFReader;
import com.hexiong.jdbf.JDBFException;
import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.erp.integration.universal.ImportUniversalAction;
import lsfusion.erp.integration.universal.UniversalImportException;
import lsfusion.erp.stock.BarcodeUtils;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.classes.*;
import lsfusion.server.logics.classes.data.ParseException;
import lsfusion.server.logics.classes.data.file.CustomStaticFormatFileClass;
import lsfusion.server.logics.classes.data.time.DateClass;
import lsfusion.server.logics.classes.data.file.DynamicFormatFileClass;
import lsfusion.server.logics.classes.data.LogicalClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.classes.user.CustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.oraction.PropertyInterface;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.physics.dev.integration.service.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.util.*;

public class ImportUserPriceListAction extends ImportUniversalAction {
    private final ClassPropertyInterface userPriceListInterface;

    // Опциональные модули
    private ScriptingLogicsModule itemAlcoholLM;
    private ScriptingLogicsModule itemFoodLM;
    private ScriptingLogicsModule itemArticleLM;
    private ScriptingLogicsModule purchasePackLM;
    private ScriptingLogicsModule salePackLM;
    private ScriptingLogicsModule stockAdjustmentLM;

    public ImportUserPriceListAction(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        this(LM, LM.findClass("UserPriceList"));
    }
    
    public ImportUserPriceListAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        userPriceListInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataObject userPriceListObject = context.getDataKeyValue(userPriceListInterface);

            ObjectValue importUserPriceListTypeObject = findProperty("importUserPriceListType[UserPriceList]").readClasses(context, userPriceListObject);

            if (!(importUserPriceListTypeObject instanceof NullValue)) {

                List<LinkedHashMap<String, ImportColumnDetail>> importColumns = readImportColumns(context, importUserPriceListTypeObject);
                Map<DataObject, String[]> priceColumns = readPriceImportColumns(context, importUserPriceListTypeObject);
                ImportPriceListSettings priceListSettings = readImportPriceListSettings(context, importUserPriceListTypeObject);
                String fileExtension = priceListSettings.getFileExtension();
                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        importData(context, userPriceListObject, priceListSettings, priceColumns, importColumns.get(0), importColumns.get(1), (RawFileData) objectValue.getValue(), false);
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | IOException | JDBFException e) {
            throw new RuntimeException(e);
        } catch (UniversalImportException e) {
            e.printStackTrace();
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), e.getTitle()));
        }
    }

    public boolean importData(ExecutionContext context, DataObject userPriceListObject, ImportPriceListSettings settings, Map<DataObject, String[]> priceColumns,
                              Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns, RawFileData file, boolean apply)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, UniversalImportException, SQLHandledException, JDBFException {

        this.itemArticleLM = context.getBL().getModule("ItemArticle");
        this.itemAlcoholLM = context.getBL().getModule("ItemAlcohol");
        this.itemFoodLM = context.getBL().getModule("ItemFood");
        this.purchasePackLM = context.getBL().getModule("PurchasePack");
        this.salePackLM = context.getBL().getModule("SalePack");
        this.stockAdjustmentLM = context.getBL().getModule("ImportUserPriceListStockAdjustment");
        
        List<UserPriceListDetail> userPriceListDetailList;
        
        Date dateDocument = (Date) findProperty("date[UserPriceList]").read(context, userPriceListObject);
        Date dateFromDocument = (Date) findProperty("fromDate[UserPriceList]").read(context, userPriceListObject);
        if(dateFromDocument == null)
            dateFromDocument = dateDocument == null ? new Date(Calendar.getInstance().getTime().getTime()) : dateDocument;
        String fileExtension = settings.getFileExtension();

        List<String> stringFields = Arrays.asList("idUserPriceList", "idItemGroup", "extraBarcodeItem", "articleItem", "captionItem", 
                "idUOMItem", "valueVAT", "originalName", "originalBarcode", "alcoholItem", "sidOrigin2Country", "nameCountry", "nameOriginCountry");

        List<String> bigDecimalFields = Arrays.asList("amountPackBarcode", "netWeightItem", "grossWeightItem", "alcoholSupplierType");

        List<String> dateFields = Collections.singletonList("dateTo");

        switch (fileExtension) {
            case "DBF":
                userPriceListDetailList = importUserPriceListsFromDBF(file, userPriceListObject, settings, priceColumns, defaultColumns, customColumns, stringFields, bigDecimalFields, dateFields, dateFromDocument);
                break;
            case "XLS":
                userPriceListDetailList = importUserPriceListsFromXLS(file, userPriceListObject, settings, priceColumns, defaultColumns, customColumns, stringFields, bigDecimalFields, dateFields, dateFromDocument);
                break;
            case "XLSX":
                userPriceListDetailList = importUserPriceListsFromXLSX(file, userPriceListObject, settings, priceColumns, defaultColumns, customColumns, stringFields, bigDecimalFields, dateFields, dateFromDocument);
                break;
            case "CSV":
                userPriceListDetailList = importUserPriceListsFromCSV(file, userPriceListObject, settings, priceColumns, defaultColumns, customColumns, stringFields, bigDecimalFields, dateFields, dateFromDocument);
                break;
            default:
                userPriceListDetailList = null;
                break;
        }

        boolean result = importUserPriceListDetails(context, userPriceListDetailList, settings, priceColumns.keySet(), defaultColumns, customColumns, userPriceListObject, apply)
                && (settings.getQuantityAdjustmentColumn() == null || importAdjustmentDetails(context, userPriceListDetailList, settings.getStockObject(), settings.getItemKeyType(), apply));


        findProperty("original[UserPriceList]").change(new DataObject(new FileData(file, fileExtension),
                DynamicFormatFileClass.get()), context, userPriceListObject);
        if(apply)
            context.apply();
        
        return result; 
    }

    private boolean importUserPriceListDetails(ExecutionContext context, List<UserPriceListDetail> userPriceListDetailList, ImportPriceListSettings settings, Set<DataObject> priceColumns, 
                                               Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns, DataObject userPriceListObject, boolean apply) 
                                               throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (userPriceListDetailList != null) {
            
            if(userPriceListDetailList.isEmpty())
                return true;

            boolean isItemKey = (settings.getItemKeyType() == null || settings.getItemKeyType().equals("item"));
            LP iGroupAggr = findProperty(isItemKey ? "item[STRING[100]]" : "skuBarcode[BPSTRING[15]]");

            if(settings.isCheckExistence()) {
                for (Iterator<UserPriceListDetail> iterator = userPriceListDetailList.iterator(); iterator.hasNext();) {
                    UserPriceListDetail detail = iterator.next();
                    String fieldValue = isItemKey ? detail.idItem : detail.barcodeItem;
                    if (fieldValue == null || iGroupAggr.read(context, new DataObject(fieldValue)) == null) {
                        iterator.remove();
                    }
                }
            }

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ObjectValue companyDocument = findProperty("company[UserPriceList]").readClasses(context, userPriceListObject);
            DataObject companyObject = companyDocument instanceof NullValue ? settings.getCompanyObject() : (DataObject) companyDocument;
            
            List<List<Object>> data = initData(userPriceListDetailList.size());

            if (settings.getOperationObject() != null) {
                props.add(new ImportProperty(settings.getOperationObject(), findProperty("operation[UserPriceList]").getMapping(userPriceListObject)));
            }

            if (companyObject!= null) {
                props.add(new ImportProperty(companyObject, findProperty("company[UserPriceList]").getMapping(userPriceListObject)));
            }

            ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcode[Sku]"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcode[STRING[100]]").getMapping(idBarcodeSkuField));
            if (settings.isDoNotCreateItems())
                barcodeKey.skipKey = true;
            keys.add(barcodeKey);
            props.add(new ImportProperty(idBarcodeSkuField, findProperty("id[Barcode]").getMapping(barcodeKey), getReplaceOnlyNull(defaultColumns, "barcodeItem")));
            props.add(new ImportProperty(idBarcodeSkuField, findProperty("extId[Barcode]").getMapping(barcodeKey), getReplaceOnlyNull(defaultColumns, "barcodeItem")));
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < userPriceListDetailList.size(); i++)
                data.get(i).add(userPriceListDetailList.get(i).barcodeItem);

            ImportField idExtraBarcodeSkuField = new ImportField(findProperty("idBarcode[Sku]"));
            ImportKey<?> extraBarcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcode[STRING[100]]").getMapping(idExtraBarcodeSkuField));
            if (settings.isDoNotCreateItems())
                extraBarcodeKey.skipKey = true;
            keys.add(extraBarcodeKey);
            props.add(new ImportProperty(idExtraBarcodeSkuField, findProperty("id[Barcode]").getMapping(extraBarcodeKey), getReplaceOnlyNull(defaultColumns, "extraBarcodeItem")));
            props.add(new ImportProperty(idExtraBarcodeSkuField, findProperty("extId[Barcode]").getMapping(extraBarcodeKey), getReplaceOnlyNull(defaultColumns, "extraBarcodeItem")));
            fields.add(idExtraBarcodeSkuField);
            for (int i = 0; i < userPriceListDetailList.size(); i++)
                data.get(i).add(userPriceListDetailList.get(i).getFieldValue("extraBarcodeItem"));

            ImportField extIdPackBarcodeSkuField = new ImportField(findProperty("extId[Barcode]"));
            ImportKey<?> packBarcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcode[STRING[100]]").getMapping(extIdPackBarcodeSkuField));
            if (settings.isDoNotCreateItems())
                packBarcodeKey.skipKey = true;
            keys.add(packBarcodeKey);
            props.add(new ImportProperty(extIdPackBarcodeSkuField, findProperty("extId[Barcode]").getMapping(packBarcodeKey), getReplaceOnlyNull(defaultColumns, "packBarcode")));
            fields.add(extIdPackBarcodeSkuField);
            for (int i = 0; i < userPriceListDetailList.size(); i++)
                data.get(i).add(userPriceListDetailList.get(i).extIdPackBarcode);

            if (showField(userPriceListDetailList, "packBarcode")) {
                ImportField packBarcodeSkuField = new ImportField(findProperty("idBarcode[Sku]"));
                props.add(new ImportProperty(packBarcodeSkuField, findProperty("id[Barcode]").getMapping(packBarcodeKey), getReplaceOnlyNull(defaultColumns, "packBarcode")));
                fields.add(packBarcodeSkuField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).packBarcode);
            }

            if (showField(userPriceListDetailList, "amountPackBarcode")) {
                ImportField amountBarcodeField = new ImportField(findProperty("dataAmount[Barcode]"));
                props.add(new ImportProperty(amountBarcodeField, findProperty("dataAmount[Barcode]").getMapping(packBarcodeKey), getReplaceOnlyNull(defaultColumns, "amountPackBarcode")));
                fields.add(amountBarcodeField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("amountPackBarcode"));
            }

            ImportField idItemField = new ImportField(findProperty("id[Item]"));

            ImportField iField = isItemKey ? idItemField : idBarcodeSkuField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    iGroupAggr.getMapping(iField));
            
            if (settings.isDoNotCreateItems())
                itemKey.skipKey = true;
            keys.add(itemKey);
            
            if (isItemKey || showField(userPriceListDetailList, "idItem")) {
                props.add(new ImportProperty(idItemField, findProperty("id[Item]").getMapping(itemKey)));
                fields.add(idItemField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).idItem);
            }
            
            if (!settings.isDoNotCreateItems()) {
                if (purchasePackLM != null)
                    props.add(new ImportProperty(extIdPackBarcodeSkuField, purchasePackLM.findProperty("packBarcode[Sku]").getMapping(itemKey),
                            object(findClass("Barcode")).getMapping(packBarcodeKey), getReplaceOnlyNull(defaultColumns, "packBarcode")));
                if (salePackLM != null)
                    props.add(new ImportProperty(extIdPackBarcodeSkuField, salePackLM.findProperty("packBarcode[Sku]").getMapping(itemKey),
                            object(findClass("Barcode")).getMapping(packBarcodeKey), getReplaceOnlyNull(defaultColumns, "packBarcode")));
            }

            if (showField(userPriceListDetailList, "idItemGroup")) {
                ImportField idItemGroupField = new ImportField(findProperty("id[ItemGroup]"));
                ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) findClass("ItemGroup"),
                        findProperty("itemGroup[STRING[100]]").getMapping(idItemGroupField));
                keys.add(itemGroupKey);
                props.add(new ImportProperty(idItemGroupField, findProperty("itemGroup[Item]").getMapping(itemKey),
                        object(findClass("ItemGroup")).getMapping(itemGroupKey), getReplaceOnlyNull(defaultColumns, "idItemGroup")));
                fields.add(idItemGroupField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("idItemGroup"));
            }

            if (itemArticleLM != null && showField(userPriceListDetailList, "articleItem")) {
                ImportField idArticleItemField = new ImportField(itemArticleLM.findProperty("idArticle[Item]"));
                ImportKey<?> articleKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClass("Article"),
                        itemArticleLM.findProperty("article[STRING[100]]").getMapping(idArticleItemField));
                keys.add(articleKey);
                props.add(new ImportProperty(idArticleItemField, itemArticleLM.findProperty("id[Article]").getMapping(articleKey)));
                props.add(new ImportProperty(idArticleItemField, itemArticleLM.findProperty("article[Item]").getMapping(itemKey),
                        itemArticleLM.object(itemArticleLM.findClass("Article")).getMapping(articleKey)));
                fields.add(idArticleItemField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("articleItem"));
            }

            if (showField(userPriceListDetailList, "idUserPriceList")) {
                ImportField idUserPriceListField = new ImportField(findProperty("id[UserPriceList]"));
                props.add(new ImportProperty(idUserPriceListField, findProperty("id[UserPriceList]").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "idUserPriceList")));
                props.add(new ImportProperty(idUserPriceListField, findProperty("number[UserPriceList]").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "idUserPriceList")));
                fields.add(idUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("idUserPriceList"));

                ImportField isPostedUserPriceListField = new ImportField(findProperty("isPosted[UserPriceList]"));
                props.add(new ImportProperty(isPostedUserPriceListField, findProperty("isPosted[UserPriceList]").getMapping(userPriceListObject)));
                fields.add(isPostedUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(true);
            }

            ImportField idUserPriceListDetailField = new ImportField(findProperty("id[UserPriceListDetail]"));
            ImportKey<?> userPriceListDetailKey = new ImportKey((ConcreteCustomClass) findClass("UserPriceListDetail"),
                    findProperty("userPriceListDetail[STRING[100],UserPriceList]").getMapping(idUserPriceListDetailField, userPriceListObject));
            keys.add(userPriceListDetailKey);
            props.add(new ImportProperty(userPriceListObject, findProperty("userPriceList[UserPriceListDetail]").getMapping(userPriceListDetailKey)));
            props.add(new ImportProperty(idUserPriceListDetailField, findProperty("id[UserPriceListDetail]").getMapping(userPriceListDetailKey)));
            props.add(new ImportProperty(iField, findProperty("sku[UserPriceListDetail]").getMapping(userPriceListDetailKey),
                    object(findClass("Sku")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, findProperty("sku[Barcode]").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, findProperty("sku[Barcode]").getMapping(extraBarcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, findProperty("sku[Barcode]").getMapping(packBarcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            fields.add(idUserPriceListDetailField);
            for (int i = 0; i < userPriceListDetailList.size(); i++)
                data.get(i).add(userPriceListDetailList.get(i).idUserPriceListDetail);

            ImportField indexField = new ImportField(findProperty("dataIndex[UserPriceListDetail]"));
            props.add(new ImportProperty(indexField, findProperty("dataIndex[UserPriceListDetail]").getMapping(userPriceListDetailKey)));
            fields.add(indexField);
            for (int i = 0; i < userPriceListDetailList.size(); i++)
                data.get(i).add(i+1);

            if (settings.getDefaultItemGroupObject() != null) {
                props.add(new ImportProperty(settings.getDefaultItemGroupObject(), findProperty("itemGroup[Item]").getMapping(itemKey)));
            }
            
            if (showField(userPriceListDetailList, "originalName")) {
                ImportField originalNameField = new ImportField(findProperty("originalNameSku[UserPriceListDetail]"));
                props.add(new ImportProperty(originalNameField, findProperty("originalNameSku[UserPriceListDetail]").getMapping(userPriceListDetailKey)));
                fields.add(originalNameField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("originalName"));
            }

            if (showField(userPriceListDetailList, "originalBarcode")) {
                ImportField originalIdBarcodeField = new ImportField(findProperty("originalIdBarcodeSku[UserPriceListDetail]"));
                props.add(new ImportProperty(originalIdBarcodeField, findProperty("originalIdBarcodeSku[UserPriceListDetail]").getMapping(userPriceListDetailKey)));
                fields.add(originalIdBarcodeField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("originalBarcode"));
            }

            if (showField(userPriceListDetailList, "captionItem")) {
                ImportField captionItemField = new ImportField(findProperty("caption[Item]"));
                props.add(new ImportProperty(captionItemField, findProperty("caption[Item]").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "captionItem")));
                fields.add(captionItemField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("captionItem"));
            }

            if (showField(userPriceListDetailList, "idUOMItem")) {
                ImportField idUOMField = new ImportField(findProperty("id[UOM]"));
                ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) findClass("UOM"),
                        findProperty("UOM[STRING[100]]").getMapping(idUOMField));
                keys.add(UOMKey);
                props.add(new ImportProperty(idUOMField, findProperty("id[UOM]").getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOMItem")));
                props.add(new ImportProperty(idUOMField, findProperty("shortName[UOM]").getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOMItem")));
                props.add(new ImportProperty(idUOMField, findProperty("UOM[Item]").getMapping(itemKey),
                        object(findClass("UOM")).getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOMItem")));
                fields.add(idUOMField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("idUOMItem"));
            }

            if (showField(userPriceListDetailList, "netWeightItem")) {
                ImportField netWeightItemField = new ImportField(findProperty("netWeight[Item]"));
                props.add(new ImportProperty(netWeightItemField, findProperty("netWeight[Item]").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "netWeightItem")));
                fields.add(netWeightItemField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("netWeightItem"));
            }

            if (showField(userPriceListDetailList, "grossWeightItem")) {
                ImportField grossWeightItemField = new ImportField(findProperty("grossWeight[Item]"));
                props.add(new ImportProperty(grossWeightItemField, findProperty("grossWeight[Item]").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "grossWeightItem")));
                fields.add(grossWeightItemField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("grossWeightItem"));
            }

            for (DataObject dataPriceListTypeObject : priceColumns) {
                ImportField pricePriceListDetailDataPriceListTypeField = new ImportField(findProperty("price[PriceListDetail,DataPriceListType]"));
                props.add(new ImportProperty(pricePriceListDetailDataPriceListTypeField, findProperty("price[PriceListDetail,DataPriceListType]").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                fields.add(pricePriceListDetailDataPriceListTypeField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).prices.get(dataPriceListTypeObject));
            }

            if (showField(userPriceListDetailList, "dateUserPriceList")) {
                ImportField dateUserPriceListField = new ImportField(findProperty("date[UserPriceList]"));
                props.add(new ImportProperty(dateUserPriceListField, findProperty("date[UserPriceList]").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "date")));
                props.add(new ImportProperty(dateUserPriceListField, findProperty("fromDate[UserPriceList]").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "date")));
                fields.add(dateUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).dateUserPriceList);

                ImportField timeUserPriceListField = new ImportField(findProperty("time[UserPriceList]"));
                props.add(new ImportProperty(timeUserPriceListField, findProperty("time[UserPriceList]").getMapping(userPriceListObject)));
                props.add(new ImportProperty(timeUserPriceListField, findProperty("fromTime[UserPriceList]").getMapping(userPriceListObject)));
                fields.add(timeUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(new Time(0, 0, 0));
            }

            if (showField(userPriceListDetailList, "isPosted")) {
                ImportField isPostedUserPriceListField = new ImportField(findProperty("isPosted[UserPriceList]"));
                props.add(new ImportProperty(isPostedUserPriceListField, findProperty("isPosted[UserPriceList]").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "isPosted")));
                fields.add(isPostedUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).isPosted);
            }

            if (showField(userPriceListDetailList, "dateFrom")) {
                ImportField dateFromUserPriceListField = new ImportField(findProperty("fromDate[UserPriceList]"));
                props.add(new ImportProperty(dateFromUserPriceListField, findProperty("fromDate[UserPriceList]").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "dateFrom")));
                fields.add(dateFromUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).dateFrom);
            }

            if (showField(userPriceListDetailList, "dateTo")) {
                ImportField dateToUserPriceListField = new ImportField(findProperty("toDate[UserPriceList]"));
                props.add(new ImportProperty(dateToUserPriceListField, findProperty("toDate[UserPriceList]").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "dateTo")));
                fields.add(dateToUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("dateTo"));
            }

            if (showField(userPriceListDetailList, "valueVAT")) {
                ImportField valueVATUserPriceListDetailField = new ImportField(findProperty("valueVAT[UserPriceListDetail]"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) findClass("Range"),
                        findProperty("valueCurrentVATDefault[NUMERIC[10,5]]").getMapping(valueVATUserPriceListDetailField));
                keys.add(VATKey);
                fields.add(valueVATUserPriceListDetailField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("valueVAT"));

                ImportField dateField = new ImportField(DateClass.instance);
                props.add(new ImportProperty(dateField, findProperty("dataDate[Barcode]").getMapping(barcodeKey), true));
                fields.add(dateField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).dateVAT);

                ImportField countryVATField = new ImportField(findProperty("name[Country]"));
                ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) findClass("Country"),
                        findProperty("countryName[ISTRING[50]]").getMapping(countryVATField));
                keys.add(countryKey);
                props.add(new ImportProperty(valueVATUserPriceListDetailField, findProperty("VAT[Item,Country]").getMapping(itemKey, countryKey),
                        object(findClass("Range")).getMapping(VATKey), getReplaceOnlyNull(defaultColumns, "valueVAT")));
                fields.add(countryVATField);
                String defaultCountry = getDefaultCountry(context);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(defaultCountry);
            }

            if (itemAlcoholLM != null && showField(userPriceListDetailList, "alcoholSupplierType")) {
                ImportField numberAlcoholSupplierTypeField = new ImportField(itemAlcoholLM.findProperty("number[AlcoholSupplierType]"));
                ImportKey<?> alcoholSupplierTypeKey = new ImportKey((ConcreteCustomClass) itemAlcoholLM.findClass("AlcoholSupplierType"),
                        itemAlcoholLM.findProperty("alcoholSupplierType[INTEGER]").getMapping(numberAlcoholSupplierTypeField));
                alcoholSupplierTypeKey.skipKey = true;
                keys.add(alcoholSupplierTypeKey);
                if (companyObject != null)
                    props.add(new ImportProperty(numberAlcoholSupplierTypeField, itemAlcoholLM.findProperty("alcoholSupplierType[LegalEntity,Item]").getMapping(companyObject, itemKey),
                            object(itemAlcoholLM.findClass("AlcoholSupplierType")).getMapping(alcoholSupplierTypeKey), getReplaceOnlyNull(defaultColumns, "alcoholSupplierType")));
                fields.add(numberAlcoholSupplierTypeField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("alcoholSupplierType"));
            }

            if (itemFoodLM != null && showField(userPriceListDetailList, "alcoholItem")) {
                ImportField nameAlcoholField = new ImportField(itemFoodLM.findProperty("name[Alcohol]"));
                ImportKey<?> alcoholKey = new ImportKey((ConcreteCustomClass) itemFoodLM.findClass("Alcohol"),
                        itemFoodLM.findProperty("alcoholName[ISTRING[50]]").getMapping(nameAlcoholField));
                keys.add(alcoholKey);
                props.add(new ImportProperty(nameAlcoholField, itemFoodLM.findProperty("name[Alcohol]").getMapping(alcoholKey), getReplaceOnlyNull(defaultColumns, "alcoholItem")));
                props.add(new ImportProperty(nameAlcoholField, itemFoodLM.findProperty("alcohol[Item]").getMapping(itemKey),
                            object(itemFoodLM.findClass("Alcohol")).getMapping(alcoholKey), getReplaceOnlyNull(defaultColumns, "alcoholItem")));
                fields.add(nameAlcoholField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("alcoholItem"));
            }

            ImportField sidOrigin2CountryField = new ImportField(LM.findProperty("sidOrigin2[Country]"));
            ImportField nameCountryField = new ImportField(LM.findProperty("name[Country]"));
            ImportField nameOriginCountryField = new ImportField(LM.findProperty("nameOrigin[Country]"));

            boolean showSidOrigin2Country = showField(userPriceListDetailList, "sidOrigin2Country");
            boolean showNameCountry = showField(userPriceListDetailList, "nameCountry");
            boolean showNameOriginCountry = showField(userPriceListDetailList, "nameOriginCountry");

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
                    for (int i = 0; i < userPriceListDetailList.size(); i++)
                        data.get(i).add(userPriceListDetailList.get(i).getFieldValue("sidOrigin2Country"));
                }
                if (showNameCountry) {
                    props.add(new ImportProperty(nameCountryField, LM.findProperty("name[Country]").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "nameCountry")));
                    fields.add(nameCountryField);
                    for (int i = 0; i < userPriceListDetailList.size(); i++)
                        data.get(i).add(userPriceListDetailList.get(i).getFieldValue("nameCountry"));
                }
                if (showNameOriginCountry) {
                    props.add(new ImportProperty(nameOriginCountryField, LM.findProperty("nameOrigin[Country]").getMapping(countryKey), getReplaceOnlyNull(defaultColumns, "nameOriginCountry")));
                    fields.add(nameOriginCountryField);
                    for (int i = 0; i < userPriceListDetailList.size(); i++)
                        data.get(i).add(userPriceListDetailList.get(i).getFieldValue("nameOriginCountry"));
                }
            }

            for (Map.Entry<String, ImportColumnDetail> entry : customColumns.entrySet()) {
                ImportColumnDetail customColumn = entry.getValue();
                LP<?> customProp = (LP<?>) context.getBL().findSafeProperty(customColumn.propertyCanonicalName);
                if (customProp != null) {
                    ImportField customField = new ImportField(customProp);
                    ImportKey<?> customKey = null;
                    if (customColumn.key.equals("userPriceListDetail"))
                        customKey = userPriceListDetailKey;
                    else if(customColumn.key.equals("item"))
                        customKey = itemKey;
                    if (customKey != null) {
                        props.add(new ImportProperty(customField, customProp.getMapping(customKey), getReplaceOnlyNull(customColumns, entry.getKey())));
                        fields.add(customField);
                        for (int i = 0; i < userPriceListDetailList.size(); i++)
                            data.get(i).add(safeParse(customField, userPriceListDetailList.get(i).customValues.get(entry.getKey())));
                    }
                }
            }

            ImportTable table = new ImportTable(fields, data);

            IntegrationService service = new IntegrationService(context, table, keys, props);
            service.synchronize(true, false);
            String result = null;
            if (apply)
                result = context.applyMessage();

            findAction("formRefresh[]").execute(context);

            return result == null;
        }
        return false;
    }

    private Object safeParse(ImportField field, String value) {
        try {
            return value == null ? null : field.getFieldClass().parseString(value);
        } catch (ParseException e) {
            return null;
        }
    }

    private boolean importAdjustmentDetails(ExecutionContext context, List<UserPriceListDetail> dataAdjustment,
                                            DataObject stockObject, String itemKeyType, boolean apply)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (stockAdjustmentLM != null && dataAdjustment != null) {

            stockAdjustmentLM.findAction("unpostAllUserAdjustment[]").execute(context.getSession(), context.stack);
            stockAdjustmentLM.findAction("overImportAdjustment[]").execute(context.getSession(), context.stack);

            DataObject userAdjustmentObject = context.addObject((ConcreteCustomClass) stockAdjustmentLM.findClass("UserAdjustment"));

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(dataAdjustment.size());

            if (stockObject != null) {
                props.add(new ImportProperty(stockObject, stockAdjustmentLM.findProperty("stock[UserAdjustment]").getMapping(userAdjustmentObject)));
            }

            ImportField idItemField = new ImportField(findProperty("id[Item]"));
            fields.add(idItemField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(dataAdjustment.get(i).idItem);

            ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcode[Sku]"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcode[STRING[100]]").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(dataAdjustment.get(i).barcodeItem);

            LP<?> iGroupAggr = findProperty((itemKeyType == null || itemKeyType.equals("item")) ? "item[STRING[100]]" : "skuBarcode[BPSTRING[15]]");
            ImportField iField = (itemKeyType == null || itemKeyType.equals("item")) ? idItemField : idBarcodeSkuField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    iGroupAggr.getMapping(iField));
            keys.add(itemKey);

            ImportField idUserAdjustmentDetailField = new ImportField(stockAdjustmentLM.findProperty("id[UserAdjustmentDetail]"));
            ImportKey<?> userAdjustmentDetailKey = new ImportKey((ConcreteCustomClass) stockAdjustmentLM.findClass("UserAdjustmentDetail"),
                    stockAdjustmentLM.findProperty("userAdjustmentDetail[STRING[100],UserAdjustment]").getMapping(idUserAdjustmentDetailField, userAdjustmentObject));
            keys.add(userAdjustmentDetailKey);
            props.add(new ImportProperty(userAdjustmentObject, stockAdjustmentLM.findProperty("userAdjustment[UserAdjustmentDetail]").getMapping(userAdjustmentDetailKey)));
            props.add(new ImportProperty(idUserAdjustmentDetailField, stockAdjustmentLM.findProperty("id[UserAdjustmentDetail]").getMapping(userAdjustmentDetailKey)));
            props.add(new ImportProperty(iField, stockAdjustmentLM.findProperty("sku[UserAdjustmentDetail]").getMapping(userAdjustmentDetailKey),
                    object(findClass("Sku")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, findProperty("sku[Barcode]").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            fields.add(idUserAdjustmentDetailField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(dataAdjustment.get(i).idUserPriceListDetail);

            ImportField quantityUserAdjustmentDetailField = new ImportField(stockAdjustmentLM.findProperty("quantity[UserAdjustmentDetail]"));
            props.add(new ImportProperty(quantityUserAdjustmentDetailField, stockAdjustmentLM.findProperty("quantity[UserAdjustmentDetail]").getMapping(userAdjustmentDetailKey)));
            fields.add(quantityUserAdjustmentDetailField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(dataAdjustment.get(i).quantityAdjustment);

            if (showField(dataAdjustment, "dateUserPriceList")) {
                ImportField dateUserAdjustmentField = new ImportField(stockAdjustmentLM.findProperty("date[UserAdjustment]"));
                props.add(new ImportProperty(dateUserAdjustmentField, stockAdjustmentLM.findProperty("date[UserAdjustment]").getMapping(userAdjustmentObject)));
                fields.add(dateUserAdjustmentField);
                for (int i = 0; i < dataAdjustment.size(); i++)
                    data.get(i).add(dataAdjustment.get(i).dateUserPriceList);

                ImportField timeUserAdjustmentField = new ImportField(stockAdjustmentLM.findProperty("time[UserAdjustment]"));
                props.add(new ImportProperty(timeUserAdjustmentField, stockAdjustmentLM.findProperty("time[UserAdjustment]").getMapping(userAdjustmentObject)));
                fields.add(timeUserAdjustmentField);
                for (int i = 0; i < dataAdjustment.size(); i++)
                    data.get(i).add(new Time(0, 0, 0));
            }

            ImportField isPostedAdjustmentField = new ImportField(stockAdjustmentLM.findProperty("isPosted[Adjustment]"));
            props.add(new ImportProperty(isPostedAdjustmentField, stockAdjustmentLM.findProperty("isPosted[Adjustment]").getMapping(userAdjustmentObject)));
            fields.add(isPostedAdjustmentField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(true);


            ImportTable table = new ImportTable(fields, data);

            IntegrationService service = new IntegrationService(context, table, keys, props);
            service.synchronize(true, false);
            String result = null;
            if (apply)
                result = context.applyMessage();

            findAction("formRefresh[]").execute(context);

            return result == null;
        }
        return false;
    }

    private List<UserPriceListDetail> importUserPriceListsFromXLS(RawFileData importFile, DataObject userPriceListObject, ImportPriceListSettings settings,
                                                                  Map<DataObject, String[]> priceColumns, Map<String, ImportColumnDetail> defaultColumns,
                                                                  Map<String, ImportColumnDetail> customColumns, List<String> stringFields,
                                                                  List<String> bigDecimalFields, List<String> dateFields, Date dateFromDocument)
            throws IOException, UniversalImportException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<>();

        HSSFWorkbook wb = new HSSFWorkbook(importFile.getInputStream());
        FormulaEvaluator formulaEvaluator = new HSSFFormulaEvaluator(wb);
        HSSFSheet sheet = wb.getSheetAt(0);

        for (int i = settings.getStartRow() - 1; i <= sheet.getLastRowNum(); i++) {

            String checkColumn = getXLSFieldValue(formulaEvaluator, sheet, i, new ImportColumnDetail(settings.getCheckColumn(), settings.getCheckColumn(), false));
            if(settings.getCheckColumn() == null || checkColumn != null) {
                Map<String, Object> fieldValues = new HashMap<>();
                for (String field : stringFields) {
                    String value = getXLSFieldValue(formulaEvaluator, sheet, i, defaultColumns.get(field));
                    switch (field) {
                        case "extraBarcodeItem":
                            fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 5, settings.isBarcodeMaybeUPC()));
                            break;
                        case "valueVAT":
                            fieldValues.put(field, parseVAT(value));
                            break;
                        default:
                            fieldValues.put(field, value);
                            break;
                    }
                }
                for (String field : bigDecimalFields) {
                    BigDecimal value = getXLSBigDecimalFieldValue(formulaEvaluator, sheet, i, defaultColumns.get(field));
                    if(field.equals("alcoholSupplierType")) {
                        fieldValues.put(field, value == null ? null : value.intValue());
                    } else                   
                        fieldValues.put(field, value);
                }
                for (String field : dateFields) {
                    Date value = getXLSDateFieldValue(formulaEvaluator, sheet, i, defaultColumns.get(field));
                    fieldValues.put(field, value);
                }

                String idItem = getXLSFieldValue(formulaEvaluator, sheet, i, defaultColumns.get("idItem"));
                String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSFieldValue(formulaEvaluator, sheet, i, defaultColumns.get("barcodeItem")), 5, settings.isBarcodeMaybeUPC());
                String packBarcode = BarcodeUtils.appendCheckDigitToBarcode(getXLSFieldValue(formulaEvaluator, sheet, i, defaultColumns.get("packBarcode")), 5, settings.isBarcodeMaybeUPC());
                Date dateUserPriceList = getXLSDateFieldValue(formulaEvaluator, sheet, i, defaultColumns.get("dateUserPriceList"));
                Date dateFrom = getXLSDateFieldValue(formulaEvaluator, sheet, i, defaultColumns.get("dateFrom"), dateFromDocument);
                Date dateVAT = dateUserPriceList == null ? dateFrom : dateUserPriceList;
                BigDecimal quantityAdjustment = getXLSBigDecimalFieldValue(formulaEvaluator, sheet, i, new ImportColumnDetail("quantityAdjustment", settings.getQuantityAdjustmentColumn(), false));
                String idUserPriceListDetail = makeIdUserPriceListDetail((String) fieldValues.get("idUserPriceList"), userPriceListObject, i);
                String extIdPackBarcode = packBarcode == null ? ((settings.getItemKeyType().equals("barcode") ? barcodeItem : idItem) + "_pack") : packBarcode;

                LinkedHashMap<String, String> customValues = new LinkedHashMap<>();
                for (Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                    customValues.put(column.getKey(), getXLSFieldValue(formulaEvaluator, sheet, i, column.getValue()));
                }

                if (!idUserPriceListDetail.startsWith("_")) {
                    Map<DataObject, BigDecimal> prices = new HashMap<>();
                    for (Map.Entry<DataObject, String[]> entry : priceColumns.entrySet()) {
                        BigDecimal price = getXLSBigDecimalFieldValue(formulaEvaluator, sheet, i, new ImportColumnDetail("price", entry.getValue(), false));
                        prices.put(entry.getKey(), price);
                    }
                    userPriceListDetailList.add(new UserPriceListDetail(customValues, fieldValues, settings.getIsPosted(),
                            idUserPriceListDetail, idItem, barcodeItem, extIdPackBarcode, packBarcode, prices, quantityAdjustment,
                            dateUserPriceList, dateFrom, dateVAT));

                }
            }
        }

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromCSV(RawFileData importFile, DataObject userPriceListObject, ImportPriceListSettings settings,
                                                                  Map<DataObject, String[]> priceColumns, Map<String, ImportColumnDetail> defaultColumns,
                                                                  Map<String, ImportColumnDetail> customColumns, List<String> stringFields,
                                                                  List<String> bigDecimalFields, List<String> dateFields, Date dateFromDocument)
            throws IOException, UniversalImportException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<>();

        BufferedReader br = new BufferedReader(new InputStreamReader(importFile.getInputStream()));
        String line;

        List<String[]> valuesList = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            valuesList.add(line.split(settings.getSeparator()));
        }

        for (int count = settings.getStartRow(); count <= valuesList.size(); count++) {

            if (count >= settings.getStartRow()) {

                String checkColumn = getCSVFieldValue(valuesList, new ImportColumnDetail(settings.getCheckColumn(), settings.getCheckColumn(), false), count);
                if(settings.getCheckColumn() == null || checkColumn != null) {
                    Map<String, Object> fieldValues = new HashMap<>();
                    for (String field : stringFields) {
                        String value = getCSVFieldValue(valuesList, defaultColumns.get(field), count);
                        switch (field) {
                            case "extraBarcodeItem":
                                fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 5, settings.isBarcodeMaybeUPC()));
                                break;
                            case "valueVAT":
                                fieldValues.put(field, parseVAT(value));
                                break;
                            default:
                                fieldValues.put(field, value);
                                break;
                        }
                    }
                    for (String field : bigDecimalFields) {
                        BigDecimal value = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get(field), count);
                        if(field.equals("alcoholSupplierType")) {
                            fieldValues.put(field, value == null ? null : value.intValue());
                        } else
                            fieldValues.put(field, value);
                    }
                    for (String field : dateFields) {
                        Date value = getCSVDateFieldValue(valuesList, defaultColumns.get(field), count);
                        fieldValues.put(field, value);
                    }

                    String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getCSVFieldValue(valuesList, defaultColumns.get("barcodeItem"), count), 5, settings.isBarcodeMaybeUPC());
                    String packBarcode = BarcodeUtils.appendCheckDigitToBarcode(getCSVFieldValue(valuesList, defaultColumns.get("packBarcode"), count), 5, settings.isBarcodeMaybeUPC());
                    String idItem = getCSVFieldValue(valuesList, defaultColumns.get("idItem"), count);
                    Date dateUserPriceList = getCSVDateFieldValue(valuesList, defaultColumns.get("dateFrom"), count);
                    Date dateFrom = getCSVDateFieldValue(valuesList, defaultColumns.get("dateFrom"), count, dateFromDocument);
                    Date dateVAT = dateUserPriceList == null ? dateFrom : dateUserPriceList;
                    BigDecimal quantityAdjustment = getCSVBigDecimalFieldValue(valuesList, new ImportColumnDetail("quantityAdjustment", settings.getQuantityAdjustmentColumn(), false), count);
                    String idUserPriceListDetail = makeIdUserPriceListDetail((String) fieldValues.get("idUserPriceList"), userPriceListObject, count);
                    String extIdPackBarcode = packBarcode == null ? ((settings.getItemKeyType().equals("barcode") ? barcodeItem : idItem) + "_pack") : packBarcode;

                    LinkedHashMap<String, String> customValues = new LinkedHashMap<>();
                    for (Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                        customValues.put(column.getKey(), getCSVFieldValue(valuesList, column.getValue(), count));
                    }

                    if (!idUserPriceListDetail.startsWith("_")) {
                        Map<DataObject, BigDecimal> prices = new HashMap<>();
                        for (Map.Entry<DataObject, String[]> entry : priceColumns.entrySet()) {
                            BigDecimal price = getCSVBigDecimalFieldValue(valuesList, new ImportColumnDetail("price", entry.getValue(), false), count);
                            prices.put(entry.getKey(), price);
                        }
                        userPriceListDetailList.add(new UserPriceListDetail(customValues, fieldValues, settings.getIsPosted(), idUserPriceListDetail,
                                idItem, barcodeItem, extIdPackBarcode, packBarcode, prices, quantityAdjustment,
                                dateUserPriceList, dateFrom, dateVAT));
                    }
                }
            }
        }

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromXLSX(RawFileData importFile, DataObject userPriceListObject, ImportPriceListSettings settings,
                                                                   Map<DataObject, String[]> priceColumns, Map<String, ImportColumnDetail> defaultColumns,
                                                                   Map<String, ImportColumnDetail> customColumns, List<String> stringFields,
                                                                   List<String> bigDecimalFields, List<String> dateFields, Date dateFromDocument)
            throws IOException, UniversalImportException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<>();

        XSSFWorkbook Wb = new XSSFWorkbook(importFile.getInputStream());
        XSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = settings.getStartRow() - 1; i <= sheet.getLastRowNum(); i++) {

            String checkColumn = getXLSXFieldValue(sheet, i, new ImportColumnDetail(settings.getCheckColumn(), settings.getCheckColumn(), false));
            if(settings.getCheckColumn() == null || checkColumn != null) {
                Map<String, Object> fieldValues = new HashMap<>();
                for (String field : stringFields) {
                    String value = getXLSXFieldValue(sheet, i, defaultColumns.get(field));
                    switch (field) {
                        case "extraBarcodeItem":
                            fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 5, settings.isBarcodeMaybeUPC()));
                            break;
                        case "valueVAT":
                            fieldValues.put(field, parseVAT(value));
                            break;
                        default:
                            fieldValues.put(field, value);
                            break;
                    }
                }
                for (String field : bigDecimalFields) {
                    BigDecimal value = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get(field));
                    if(field.equals("alcoholSupplierType")) {
                        fieldValues.put(field, value == null ? null : value.intValue());
                    } else
                        fieldValues.put(field, value);
                }
                for (String field : dateFields) {
                    Date value = getXLSXDateFieldValue(sheet, i, defaultColumns.get(field));
                    fieldValues.put(field, value);
                }

                String idItem = getXLSXFieldValue(sheet, i, defaultColumns.get("idItem"));
                String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSXFieldValue(sheet, i, defaultColumns.get("barcodeItem")), 5, settings.isBarcodeMaybeUPC());
                String packBarcode = BarcodeUtils.appendCheckDigitToBarcode(getXLSXFieldValue(sheet, i, defaultColumns.get("packBarcode")), 5, settings.isBarcodeMaybeUPC());
                BigDecimal quantityAdjustment = getXLSXBigDecimalFieldValue(sheet, i, new ImportColumnDetail("quantityAdjustment", settings.getQuantityAdjustmentColumn(), false));
                Date dateUserPriceList = getXLSXDateFieldValue(sheet, i, defaultColumns.get("dateUserPriceList"));
                Date dateFrom = getXLSXDateFieldValue(sheet, i, defaultColumns.get("dateFrom"), dateFromDocument);
                Date dateVAT = dateUserPriceList == null ? dateFrom : dateUserPriceList;

                String idUserPriceListDetail = makeIdUserPriceListDetail((String) fieldValues.get("idUserPriceList"), userPriceListObject, i);
                String extIdPackBarcode = packBarcode == null ? ((settings.getItemKeyType().equals("barcode") ? barcodeItem : idItem) + "_pack") : packBarcode;

                LinkedHashMap<String, String> customValues = new LinkedHashMap<>();
                for (Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                    customValues.put(column.getKey(), getXLSXFieldValue(sheet, i, column.getValue()));
                }

                if (!idUserPriceListDetail.startsWith("_")) {
                    Map<DataObject, BigDecimal> prices = new HashMap<>();
                    for (Map.Entry<DataObject, String[]> entry : priceColumns.entrySet()) {
                        BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, new ImportColumnDetail("price", entry.getValue(), false));
                        prices.put(entry.getKey(), price);
                    }
                    userPriceListDetailList.add(new UserPriceListDetail(customValues, fieldValues, settings.getIsPosted(), idUserPriceListDetail,
                            idItem, barcodeItem, extIdPackBarcode, packBarcode, prices, quantityAdjustment, dateUserPriceList,
                            dateFrom, dateVAT));
                }
            }
        }

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromDBF(RawFileData importFile, DataObject userPriceListObject, ImportPriceListSettings settings,
                                                                  Map<DataObject, String[]> priceColumns, Map<String, ImportColumnDetail> defaultColumns,
                                                                  Map<String, ImportColumnDetail> customColumns, List<String> stringFields,
                                                                  List<String> bigDecimalFields, List<String> dateFields, Date dateFromDocument)
            throws IOException, UniversalImportException, JDBFException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<>();

        File tempFile = File.createTempFile("dutiesTNVED", ".dbf");
        importFile.write(tempFile);

        DBFReader dbfReader = new DBFReader(new FileInputStream(tempFile));
        String charset = getDBFCharset(tempFile);

        Map<String, Integer> fieldNamesMap = new HashMap<>();
        for (int i = 0; i < dbfReader.getFieldCount(); i++) {
            fieldNamesMap.put(dbfReader.getField(i).getName(), i);
        }

        for (int i = 0; i < settings.getStartRow() - 1; i++) {
            dbfReader.nextRecord(Charset.forName(charset));
        }

        for (int i = settings.getStartRow() - 1; dbfReader.hasNextRecord(); i++) {

            Object entry[] = dbfReader.nextRecord(Charset.forName(charset));

            String checkColumn = getJDBFFieldValue(entry, fieldNamesMap, new ImportColumnDetail(settings.getCheckColumn(), settings.getCheckColumn(), false), i);
            if (settings.getCheckColumn() == null || checkColumn != null) {
                Map<String, Object> fieldValues = new HashMap<>();
                for (String field : stringFields) {
                    String value = getJDBFFieldValue(entry, fieldNamesMap, defaultColumns.get(field), i);
                    switch (field) {
                        case "extraBarcodeItem":
                            fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 5, settings.isBarcodeMaybeUPC()));
                            break;
                        case "valueVAT":
                            fieldValues.put(field, parseVAT(value));
                            break;
                        default:
                            fieldValues.put(field, value);
                            break;
                    }
                }
                for (String field : bigDecimalFields) {
                    BigDecimal value = getJDBFBigDecimalFieldValue(entry, fieldNamesMap, defaultColumns.get(field), i);
                    if(field.equals("alcoholSupplierType")) {
                        fieldValues.put(field, value == null ? null : value.intValue());
                    } else
                        fieldValues.put(field, value);
                }
                for (String field : dateFields) {
                    Date value = getJDBFDateFieldValue(entry, fieldNamesMap, defaultColumns.get(field), i);
                    fieldValues.put(field, value);
                }

                String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getJDBFFieldValue(entry, fieldNamesMap, defaultColumns.get("barcodeItem"), i), 5, settings.isBarcodeMaybeUPC());
                String packBarcode = BarcodeUtils.appendCheckDigitToBarcode(getJDBFFieldValue(entry, fieldNamesMap, defaultColumns.get("packBarcode"), i), 5, settings.isBarcodeMaybeUPC());
                String idItem = getJDBFFieldValue(entry, fieldNamesMap, defaultColumns.get("idItem"), i);
                BigDecimal quantityAdjustment = getJDBFBigDecimalFieldValue(entry, fieldNamesMap, new ImportColumnDetail("quantityAdjustment", settings.getQuantityAdjustmentColumn(), false), i);
                Date dateUserPriceList = getJDBFDateFieldValue(entry, fieldNamesMap, defaultColumns.get("dateUserPriceList"), i);
                Date dateFrom = getJDBFDateFieldValue(entry, fieldNamesMap, defaultColumns.get("dateFrom"), i, dateFromDocument);
                String idUserPriceListDetail = makeIdUserPriceListDetail((String) fieldValues.get("idUserPriceList"), userPriceListObject, i);
                String extIdPackBarcode = packBarcode == null ? ((settings.getItemKeyType().equals("barcode") ? barcodeItem : idItem) + "_pack") : packBarcode;

                LinkedHashMap<String, String> customValues = new LinkedHashMap<>();
                for (Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                    customValues.put(column.getKey(), getJDBFFieldValue(entry, fieldNamesMap, column.getValue(), i));
                }

                if (!idUserPriceListDetail.startsWith("_")) {
                    Map<DataObject, BigDecimal> prices = new HashMap<>();
                    for (Map.Entry<DataObject, String[]> priceEntry : priceColumns.entrySet()) {
                        BigDecimal price = getJDBFBigDecimalFieldValue(entry, fieldNamesMap, new ImportColumnDetail("price", priceEntry.getValue(), false), i);
                        prices.put(priceEntry.getKey(), price);
                    }
                    userPriceListDetailList.add(new UserPriceListDetail(customValues, fieldValues, settings.getIsPosted(), idUserPriceListDetail,
                            idItem, barcodeItem, extIdPackBarcode, packBarcode, prices, quantityAdjustment, dateUserPriceList,
                            dateFrom, dateFrom));
                }
            }
        }

        dbfReader.close();
        if(!tempFile.delete())
            tempFile.deleteOnExit();
        return userPriceListDetailList;
    }

    public List<LinkedHashMap<String, ImportColumnDetail>> readImportColumns(ExecutionContext context, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        LinkedHashMap<String, ImportColumnDetail> defaultColumns = new LinkedHashMap<>();
        LinkedHashMap<String, ImportColumnDetail> customColumns = new LinkedHashMap<>();

        KeyExpr importUserPriceListTypeDetailExpr = new KeyExpr("importUserPriceListTypeDetail");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("importUserPriceListTypeDetail", importUserPriceListTypeDetailExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        String[] names = new String[] {"staticName", "staticCaption", "propertyImportUserPriceListTypeDetail", "nameKeyImportUserPriceListTypeDetail"};
        LP[] properties = findProperties("staticName[ImportUserPriceListTypeDetail]", "staticCaption[ImportUserPriceListTypeDetail]", "canonicalNamePropImport[ImportUserPriceListTypeDetail]", "nameKeyImport[ImportUserPriceListTypeDetail]");
        for (int j = 0; j < properties.length; j++) {
            query.addProperty(names[j], properties[j].getExpr(importUserPriceListTypeDetailExpr));
        }
        query.addProperty("replaceOnlyNullImportUserPriceListTypeImportUserPriceListTypeDetail", findProperty("replaceOnlyNull[ImportUserPriceListType,ImportUserPriceListTypeDetail]").getExpr(importTypeObject.getExpr(), importUserPriceListTypeDetailExpr));
        query.addProperty("indexImportUserPriceListTypeImportUserPriceListTypeDetail", findProperty("index[ImportUserPriceListType,ImportUserPriceListTypeDetail]").getExpr(importTypeObject.getExpr(), importUserPriceListTypeDetailExpr));
        query.and(findProperty("index[ImportUserPriceListType,ImportUserPriceListTypeDetail]").getExpr(importTypeObject.getExpr(), importUserPriceListTypeDetailExpr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String staticNameProperty = trim((String) entry.get("staticName"));
            String field = getSplittedPart(staticNameProperty, "\\.", -1);
            String staticCaptionProperty = trim((String) entry.get("staticCaption"));

            String propertyImportTypeDetail = (String) entry.get("propertyImportUserPriceListTypeDetail");
            LP<?> customProp = propertyImportTypeDetail == null ? null : (LP<?>) context.getBL().findSafeProperty(propertyImportTypeDetail);
            boolean isBoolean = customProp != null && customProp.property.getType() instanceof LogicalClass;

            String keyImportTypeDetail = getSplittedPart((String) entry.get("nameKeyImportUserPriceListTypeDetail"), "\\.", 1);
            boolean replaceOnlyNull = entry.get("replaceOnlyNullImportUserPriceListTypeImportUserPriceListTypeDetail") != null;
            String indexes = (String) entry.get("indexImportUserPriceListTypeImportUserPriceListTypeDetail");
            if (indexes != null) {
                int openingParentheses = StringUtils.countMatches(indexes, "(");
                int closingParentheses = StringUtils.countMatches(indexes, ")");
                String[] splittedIndexes = (openingParentheses == 0 || openingParentheses != closingParentheses) ? indexes.split("\\+") : new String[] {indexes};
                for (int i = 0; i < splittedIndexes.length; i++)
                    splittedIndexes[i] = splittedIndexes[i].contains("=") ? splittedIndexes[i] : trim(splittedIndexes[i]);
                if(field != null)
                    defaultColumns.put(field, new ImportColumnDetail(staticCaptionProperty, indexes, splittedIndexes, replaceOnlyNull, isBoolean));
                else if(keyImportTypeDetail != null)
                    customColumns.put(staticCaptionProperty, new ImportColumnDetail(staticCaptionProperty, indexes, splittedIndexes, replaceOnlyNull,
                            propertyImportTypeDetail, keyImportTypeDetail, isBoolean));
            }
        }
        return Arrays.asList(defaultColumns, customColumns);
    }

    public Map<DataObject, String[]> readPriceImportColumns(ExecutionContext context, ObjectValue importUserPriceListTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Map<DataObject, String[]> importColumns = new HashMap<>();

        LP<PropertyInterface> isDataPriceListType = (LP<PropertyInterface>) is(findClass("DataPriceListType"));
        ImRevMap<PropertyInterface, KeyExpr> keys = isDataPriceListType.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<PropertyInterface, Object> query = new QueryBuilder<>(keys);
        query.addProperty("indexImportUserPriceListTypeDataPriceListType", findProperty("index[ImportUserPriceListType,DataPriceListType]").getExpr(context.getModifier(), importUserPriceListTypeObject.getExpr(), key));
        query.and(isDataPriceListType.getExpr(key).getWhere());
        ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(context);

        for (int i = 0, size = result.size(); i < size; i++) {
            ImMap<Object, ObjectValue> entryValue = result.getValue(i);

            DataObject dataPriceListTypeObject = result.getKey(i).valueIt().iterator().next();
            String indexes = (String) entryValue.get("indexImportUserPriceListTypeDataPriceListType").getValue();
            if (indexes != null) {
                String[] splittedIndexes = indexes.split("\\+");
                for (int j = 0; j < splittedIndexes.length; j++)
                    splittedIndexes[j] = trim(splittedIndexes[j]);
                importColumns.put(dataPriceListTypeObject, splittedIndexes);
            }
        }
        return importColumns;
    }

    public ImportPriceListSettings readImportPriceListSettings(ExecutionContext context, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String fileExtension = trim((String) findProperty("captionImportUserPriceListTypeFileExtension[ImportUserPriceListType]").read(context, importTypeObject));
        String quantityAdjustmentColumn = (String) findProperty("quantityAdjustment[ImportUserPriceListType]").read(context, importTypeObject);

        ObjectValue operation = findProperty("operation[ImportUserPriceListType]").readClasses(context, (DataObject) importTypeObject);
        DataObject operationObject = operation instanceof NullValue ? null : (DataObject) operation;

        ObjectValue company = findProperty("company[ImportUserPriceListType]").readClasses(context, (DataObject) importTypeObject);
        DataObject companyObject = company instanceof NullValue ? null : (DataObject) company;

        ObjectValue stock = findProperty("stock[ImportUserPriceListType]").readClasses(context, (DataObject) importTypeObject);
        DataObject stockObject = stock instanceof NullValue ? null : (DataObject) stock;

        ObjectValue defaultItemGroup = findProperty("defaultItemGroup[ImportUserPriceListType]").readClasses(context, (DataObject) importTypeObject);
        DataObject defaultItemGroupObject = defaultItemGroup instanceof NullValue ? null : (DataObject) defaultItemGroup;
        
        String itemKeyType = (String) findProperty("nameImportUserPriceListKeyType[ImportUserPriceListType]").read(context, importTypeObject);
        String[] parts = itemKeyType == null ? null : itemKeyType.split("\\.");
        itemKeyType = parts == null ? null : trim(parts[parts.length - 1]);
        
        String separator = trim((String) findProperty("separator[ImportUserPriceListType]").read(context, importTypeObject), ";");
        Integer startRow = (Integer) findProperty("startRow[ImportUserPriceListType]").read(context, importTypeObject);
        startRow = startRow == null || startRow.equals(0) ? 1 : startRow;
        Boolean isPosted = (Boolean) findProperty("isPosted[ImportUserPriceListType]").read(context, importTypeObject);
        boolean doNotCreateItems = findProperty("doNotCreateItems[ImportUserPriceListType]").read(context, importTypeObject) != null;
        boolean checkExistence = findProperty("checkExistence[ImportUserPriceListType]").read(context, importTypeObject) != null;
        boolean barcodeMaybeUPC = findProperty("barcodeMaybeUPC[ImportUserPriceListType]").read(context, importTypeObject) != null;
        String checkColumn = (String) findProperty("checkColumn[ImportUserPriceListType]").read(context, importTypeObject);
        return new ImportPriceListSettings(fileExtension, quantityAdjustmentColumn, operationObject, companyObject, stockObject, defaultItemGroupObject, 
                itemKeyType, separator, startRow, isPosted, doNotCreateItems, checkExistence, barcodeMaybeUPC, checkColumn);
    }


    private String makeIdUserPriceListDetail(String idUserPriceList, DataObject userPriceListObject, int i) {
        return (idUserPriceList == null ? (userPriceListObject == null ? "" : userPriceListObject.object) : idUserPriceList) + "_" + i;
    }

    protected static String getSplittedPart(String value, String splitPattern, int index) {
        if(value == null) return null;
        String[] splittedValue = value.trim().split(splitPattern);
        int len = splittedValue.length;
        return index >= 0 ? (len <= index ? null : splittedValue[index]) : len < -index ? null : splittedValue[len + index];
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
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return true;
        }
        return false;
    }
}

