package lsfusion.erp.integration.universal.userpricelist;

import com.hexiong.jdbf.DBFReader;
import com.hexiong.jdbf.JDBFException;
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
import lsfusion.erp.integration.universal.ImportColumnDetail;
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

                List<LinkedHashMap<String, ImportColumnDetail>> importColumns = readImportColumns(context, importUserPriceListTypeObject);
                Map<DataObject, String[]> priceColumns = readPriceImportColumns(context, importUserPriceListTypeObject);
                ImportPriceListSettings priceListSettings = readImportPriceListSettings(context, importUserPriceListTypeObject);
                String fileExtension = priceListSettings.getFileExtension();
                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            importData(context, userPriceListObject, priceListSettings, priceColumns, importColumns.get(0), importColumns.get(1), file, false);

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

    public boolean importData(ExecutionContext context, DataObject userPriceListObject, ImportPriceListSettings settings, Map<DataObject, String[]> priceColumns, 
                              Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns, byte[] file, boolean apply)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException, UniversalImportException, SQLHandledException, JDBFException {

        this.itemArticleLM = context.getBL().getModule("ItemArticle");
        this.purchasePackLM = context.getBL().getModule("PurchasePack");
        this.salePackLM = context.getBL().getModule("SalePack");
        this.stockAdjustmentLM = context.getBL().getModule("ImportUserPriceListStockAdjustment");
        
        List<UserPriceListDetail> userPriceListDetailList;
        
        Date dateDocument = (Date) findProperty("dateUserPriceList").read(context, userPriceListObject);
        dateDocument = dateDocument == null ? new Date(Calendar.getInstance().getTime().getTime()) : dateDocument;
        String fileExtension = settings.getFileExtension();

        List<String> stringFields = Arrays.asList("idUserPriceList", "idItemGroup", "extraBarcodeItem", "articleItem", "captionItem", 
                "idUOMItem", "valueVAT", "originalName", "originalBarcode");

        List<String> bigDecimalFields = Arrays.asList("amountPackBarcode", "netWeightItem", "grossWeightItem");

        List<String> dateFields = Arrays.asList("dateTo");        
        
        if (fileExtension.equals("DBF"))
            userPriceListDetailList = importUserPriceListsFromDBF(file, settings, priceColumns, defaultColumns, customColumns, stringFields, bigDecimalFields, dateFields, dateDocument);
        else if (fileExtension.equals("XLS"))
            userPriceListDetailList = importUserPriceListsFromXLS(file, settings, priceColumns, defaultColumns, customColumns, stringFields, bigDecimalFields, dateFields, dateDocument);
        else if (fileExtension.equals("XLSX"))
            userPriceListDetailList = importUserPriceListsFromXLSX(file, settings, priceColumns, defaultColumns, customColumns, stringFields, bigDecimalFields, dateFields, dateDocument);
        else if (fileExtension.equals("CSV"))
            userPriceListDetailList = importUserPriceListsFromCSV(file, settings, priceColumns, defaultColumns, customColumns, stringFields, bigDecimalFields, dateFields, dateDocument);
        else
            userPriceListDetailList = null;

        boolean result = importUserPriceListDetails(context, userPriceListDetailList, settings, priceColumns.keySet(), defaultColumns, customColumns, userPriceListObject, apply)
                && (settings.getQuantityAdjustmentColumn() == null || importAdjustmentDetails(context, userPriceListDetailList, settings.getStockObject(), settings.getItemKeyType(), apply));


        findProperty("originalUserPriceList").change(new DataObject(BaseUtils.mergeFileAndExtension(file, fileExtension.getBytes()), 
                DynamicFormatFileClass.get(false, true)), context, userPriceListObject);
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

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(userPriceListDetailList.size());

            if (settings.getOperationObject() != null) {
                props.add(new ImportProperty(settings.getOperationObject(), findProperty("operationUserPriceList").getMapping(userPriceListObject)));
            }

            if (settings.getCompanyObject() != null) {
                props.add(new ImportProperty(settings.getCompanyObject(), findProperty("companyUserPriceList").getMapping(userPriceListObject)));
            }

            ImportField idBarcodeSkuField = new ImportField(findProperty("idBarcodeSku"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(idBarcodeSkuField));
            if (settings.isDoNotCreateItems())
                barcodeKey.skipKey = true;
            keys.add(barcodeKey);
            props.add(new ImportProperty(idBarcodeSkuField, findProperty("idBarcode").getMapping(barcodeKey), getReplaceOnlyNull(defaultColumns, "barcodeItem")));
            props.add(new ImportProperty(idBarcodeSkuField, findProperty("extIdBarcode").getMapping(barcodeKey), getReplaceOnlyNull(defaultColumns, "barcodeItem")));
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < userPriceListDetailList.size(); i++)
                data.get(i).add(userPriceListDetailList.get(i).barcodeItem);

            ImportField idExtraBarcodeSkuField = new ImportField(findProperty("idBarcodeSku"));
            ImportKey<?> extraBarcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(idExtraBarcodeSkuField));
            if (settings.isDoNotCreateItems())
                extraBarcodeKey.skipKey = true;
            keys.add(extraBarcodeKey);
            props.add(new ImportProperty(idExtraBarcodeSkuField, findProperty("idBarcode").getMapping(extraBarcodeKey), getReplaceOnlyNull(defaultColumns, "extraBarcodeItem")));
            props.add(new ImportProperty(idExtraBarcodeSkuField, findProperty("extIdBarcode").getMapping(extraBarcodeKey), getReplaceOnlyNull(defaultColumns, "extraBarcodeItem")));
            fields.add(idExtraBarcodeSkuField);
            for (int i = 0; i < userPriceListDetailList.size(); i++)
                data.get(i).add(userPriceListDetailList.get(i).getFieldValue("extraBarcodeItem"));

            ImportField extIdPackBarcodeSkuField = new ImportField(findProperty("extIdBarcode"));
            ImportKey<?> packBarcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(extIdPackBarcodeSkuField));
            if (settings.isDoNotCreateItems())
                packBarcodeKey.skipKey = true;
            keys.add(packBarcodeKey);
            props.add(new ImportProperty(extIdPackBarcodeSkuField, findProperty("extIdBarcode").getMapping(packBarcodeKey), getReplaceOnlyNull(defaultColumns, "packBarcode")));
            fields.add(extIdPackBarcodeSkuField);
            for (int i = 0; i < userPriceListDetailList.size(); i++)
                data.get(i).add(userPriceListDetailList.get(i).extIdPackBarcode);

            if (showField(userPriceListDetailList, "packBarcode")) {
                ImportField packBarcodeSkuField = new ImportField(findProperty("idBarcodeSku"));
                props.add(new ImportProperty(packBarcodeSkuField, findProperty("idBarcode").getMapping(packBarcodeKey), getReplaceOnlyNull(defaultColumns, "packBarcode")));
                fields.add(packBarcodeSkuField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).packBarcode);
            }

            if (showField(userPriceListDetailList, "amountPackBarcode")) {
                ImportField amountBarcodeField = new ImportField(findProperty("amountBarcode"));
                props.add(new ImportProperty(amountBarcodeField, findProperty("amountBarcode").getMapping(packBarcodeKey), getReplaceOnlyNull(defaultColumns, "amountPackBarcode")));
                fields.add(amountBarcodeField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("amountPackBarcode"));
            }

            boolean isItemKey = (settings.getItemKeyType() == null || settings.getItemKeyType().equals("item"));
            
            ImportField idItemField = new ImportField(findProperty("idItem"));
            
            LCP iGroupAggr = findProperty(isItemKey ? "itemId" : "skuBarcodeId");
            ImportField iField = isItemKey ? idItemField : idBarcodeSkuField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    iGroupAggr.getMapping(iField));
            
            if (settings.isDoNotCreateItems())
                itemKey.skipKey = true;
            keys.add(itemKey);
            
            if (isItemKey || showField(userPriceListDetailList, "idItem")) {
                props.add(new ImportProperty(idItemField, findProperty("idItem").getMapping(itemKey)));
                fields.add(idItemField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).idItem);
            }
            
            if (!settings.isDoNotCreateItems()) {
                if (purchasePackLM != null)
                    props.add(new ImportProperty(extIdPackBarcodeSkuField, purchasePackLM.findProperty("Purchase.packBarcodeSku").getMapping(itemKey),
                            object(findClass("Barcode")).getMapping(packBarcodeKey), getReplaceOnlyNull(defaultColumns, "packBarcode")));
                if (salePackLM != null)
                    props.add(new ImportProperty(extIdPackBarcodeSkuField, salePackLM.findProperty("Sale.packBarcodeSku").getMapping(itemKey),
                            object(findClass("Barcode")).getMapping(packBarcodeKey), getReplaceOnlyNull(defaultColumns, "packBarcode")));
            }

            if (showField(userPriceListDetailList, "idItemGroup")) {
                ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
                ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) findClass("ItemGroup"),
                        findProperty("itemGroupId").getMapping(idItemGroupField));
                keys.add(itemGroupKey);
                props.add(new ImportProperty(idItemGroupField, findProperty("itemGroupItem").getMapping(itemKey),
                        object(findClass("ItemGroup")).getMapping(itemGroupKey), getReplaceOnlyNull(defaultColumns, "idItemGroup")));
                fields.add(idItemGroupField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("idItemGroup"));
            }

            if (itemArticleLM != null && showField(userPriceListDetailList, "articleItem")) {
                ImportField idArticleItemField = new ImportField(itemArticleLM.findProperty("idArticleItem"));
                ImportKey<?> articleKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClass("Article"),
                        itemArticleLM.findProperty("articleId").getMapping(idArticleItemField));
                keys.add(articleKey);
                props.add(new ImportProperty(idArticleItemField, itemArticleLM.findProperty("idArticle").getMapping(articleKey)));
                props.add(new ImportProperty(idArticleItemField, itemArticleLM.findProperty("articleItem").getMapping(itemKey),
                        itemArticleLM.object(itemArticleLM.findClass("Article")).getMapping(articleKey)));
                fields.add(idArticleItemField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("articleItem"));
            }

            if (showField(userPriceListDetailList, "idUserPriceList")) {
                ImportField idUserPriceListField = new ImportField(findProperty("idUserPriceList"));
                props.add(new ImportProperty(idUserPriceListField, findProperty("idUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "idUserPriceList")));
                props.add(new ImportProperty(idUserPriceListField, findProperty("numberUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "idUserPriceList")));
                fields.add(idUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("idUserPriceList"));

                ImportField isPostedUserPriceListField = new ImportField(findProperty("isPostedUserPriceList"));
                props.add(new ImportProperty(isPostedUserPriceListField, findProperty("isPostedUserPriceList").getMapping(userPriceListObject)));
                fields.add(isPostedUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
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
            for (int i = 0; i < userPriceListDetailList.size(); i++)
                data.get(i).add(userPriceListDetailList.get(i).idUserPriceListDetail);

            ImportField indexField = new ImportField(findProperty("dataIndexUserPriceListDetail"));
            props.add(new ImportProperty(indexField, findProperty("dataIndexUserPriceListDetail").getMapping(userPriceListDetailKey)));
            fields.add(indexField);
            for (int i = 0; i < userPriceListDetailList.size(); i++)
                data.get(i).add(i+1);

            if (settings.getDefaultItemGroupObject() != null) {
                props.add(new ImportProperty(settings.getDefaultItemGroupObject(), findProperty("itemGroupItem").getMapping(itemKey)));
            }
            
            if (showField(userPriceListDetailList, "originalName")) {
                ImportField originalNameField = new ImportField(findProperty("originalNameSkuUserPriceListDetail"));
                props.add(new ImportProperty(originalNameField, findProperty("originalNameSkuUserPriceListDetail").getMapping(userPriceListDetailKey)));
                fields.add(originalNameField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("originalName"));
            }

            if (showField(userPriceListDetailList, "originalBarcode")) {
                ImportField originalIdBarcodeField = new ImportField(findProperty("originalIdBarcodeSkuUserPriceListDetail"));
                props.add(new ImportProperty(originalIdBarcodeField, findProperty("originalIdBarcodeSkuUserPriceListDetail").getMapping(userPriceListDetailKey)));
                fields.add(originalIdBarcodeField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("originalBarcode"));
            }

            if (showField(userPriceListDetailList, "captionItem")) {
                ImportField captionItemField = new ImportField(findProperty("captionItem"));
                props.add(new ImportProperty(captionItemField, findProperty("captionItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "captionItem")));
                fields.add(captionItemField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("captionItem"));
            }

            if (showField(userPriceListDetailList, "idUOMItem")) {
                ImportField idUOMField = new ImportField(findProperty("idUOM"));
                ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) findClass("UOM"),
                        findProperty("UOMId").getMapping(idUOMField));
                keys.add(UOMKey);
                props.add(new ImportProperty(idUOMField, findProperty("idUOM").getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOMItem")));
                props.add(new ImportProperty(idUOMField, findProperty("shortNameUOM").getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOMItem")));
                props.add(new ImportProperty(idUOMField, findProperty("UOMItem").getMapping(itemKey),
                        object(findClass("UOM")).getMapping(UOMKey), getReplaceOnlyNull(defaultColumns, "idUOMItem")));
                fields.add(idUOMField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("idUOMItem"));
            }

            if (showField(userPriceListDetailList, "netWeightItem")) {
                ImportField netWeightItemField = new ImportField(findProperty("netWeightItem"));
                props.add(new ImportProperty(netWeightItemField, findProperty("netWeightItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "netWeightItem")));
                fields.add(netWeightItemField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("netWeightItem"));
            }

            if (showField(userPriceListDetailList, "grossWeightItem")) {
                ImportField grossWeightItemField = new ImportField(findProperty("grossWeightItem"));
                props.add(new ImportProperty(grossWeightItemField, findProperty("grossWeightItem").getMapping(itemKey), getReplaceOnlyNull(defaultColumns, "grossWeightItem")));
                fields.add(grossWeightItemField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("grossWeightItem"));
            }

            for (DataObject dataPriceListTypeObject : priceColumns) {
                ImportField pricePriceListDetailDataPriceListTypeField = new ImportField(findProperty("pricePriceListDetailDataPriceListType"));
                props.add(new ImportProperty(pricePriceListDetailDataPriceListTypeField, findProperty("pricePriceListDetailDataPriceListType").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                fields.add(pricePriceListDetailDataPriceListTypeField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).prices.get(dataPriceListTypeObject));
            }

            if (showField(userPriceListDetailList, "dateUserPriceList")) {
                ImportField dateUserPriceListField = new ImportField(findProperty("dateUserPriceList"));
                props.add(new ImportProperty(dateUserPriceListField, findProperty("dateUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "date")));
                props.add(new ImportProperty(dateUserPriceListField, findProperty("fromDateUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "date")));
                fields.add(dateUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).dateUserPriceList);

                ImportField timeUserPriceListField = new ImportField(findProperty("timeUserPriceList"));
                props.add(new ImportProperty(timeUserPriceListField, findProperty("timeUserPriceList").getMapping(userPriceListObject)));
                props.add(new ImportProperty(timeUserPriceListField, findProperty("fromTimeUserPriceList").getMapping(userPriceListObject)));
                fields.add(timeUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(new Time(0, 0, 0));
            }

            if (showField(userPriceListDetailList, "isPosted")) {
                ImportField isPostedUserPriceListField = new ImportField(findProperty("isPostedUserPriceList"));
                props.add(new ImportProperty(isPostedUserPriceListField, findProperty("isPostedUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "isPosted")));
                fields.add(isPostedUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).isPosted);
            }

            if (showField(userPriceListDetailList, "dateFrom")) {
                ImportField dateFromUserPriceListField = new ImportField(findProperty("fromDateUserPriceList"));
                props.add(new ImportProperty(dateFromUserPriceListField, findProperty("fromDateUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "dateFrom")));
                fields.add(dateFromUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).dateFrom);
            }

            if (showField(userPriceListDetailList, "dateTo")) {
                ImportField dateToUserPriceListField = new ImportField(findProperty("toDateUserPriceList"));
                props.add(new ImportProperty(dateToUserPriceListField, findProperty("toDateUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(defaultColumns, "dateTo")));
                fields.add(dateToUserPriceListField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("dateTo"));
            }

            if (showField(userPriceListDetailList, "valueVAT")) {
                ImportField valueVATUserPriceListDetailField = new ImportField(findProperty("valueVATUserPriceListDetail"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) findClass("Range"),
                        findProperty("valueCurrentVATDefaultValue").getMapping(valueVATUserPriceListDetailField));
                keys.add(VATKey);
                fields.add(valueVATUserPriceListDetailField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).getFieldValue("valueVAT"));

                ImportField dateField = new ImportField(DateClass.instance);
                props.add(new ImportProperty(dateField, findProperty("dataDateBarcode").getMapping(barcodeKey)));
                fields.add(dateField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(userPriceListDetailList.get(i).dateVAT);

                ImportField countryVATField = new ImportField(findProperty("nameCountry"));
                ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) findClass("Country"),
                        findProperty("countryName").getMapping(countryVATField));
                keys.add(countryKey);
                props.add(new ImportProperty(valueVATUserPriceListDetailField, findProperty("VATItemCountry").getMapping(itemKey, countryKey),
                        object(findClass("Range")).getMapping(VATKey), getReplaceOnlyNull(defaultColumns, "valueVAT")));
                fields.add(countryVATField);
                for (int i = 0; i < userPriceListDetailList.size(); i++)
                    data.get(i).add(defaultCountry);
            }

            for (Map.Entry<String, ImportColumnDetail> entry : customColumns.entrySet()) {
                ImportColumnDetail customColumn = entry.getValue();
                ScriptingLogicsModule customModuleLM = context.getBL().getModule(customColumn.moduleName);
                if (customModuleLM != null) {
                    ImportField customField = new ImportField(customModuleLM.findProperty(customColumn.property));
                    ImportKey<?> customKey = null;
                    if (customColumn.key.equals("userPriceListDetail"))
                        customKey = userPriceListDetailKey;
                    else if(customColumn.key.equals("item"))
                        customKey = itemKey;
                    if (customKey != null) {
                        props.add(new ImportProperty(customField, customModuleLM.findProperty(customColumn.property).getMapping(customKey), getReplaceOnlyNull(customColumns, entry.getKey())));
                        fields.add(customField);
                        for (int i = 0; i < userPriceListDetailList.size(); i++)
                            data.get(i).add(userPriceListDetailList.get(i).customValues.get(entry.getKey()));
                    }
                }
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

    private List<UserPriceListDetail> importUserPriceListsFromXLS(byte[] importFile, ImportPriceListSettings settings, Map<DataObject, String[]> priceColumns,
                                                                  Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns, 
                                                                  List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, 
                                                                  Date dateDocument)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding("cp1251");
        Workbook wb = Workbook.getWorkbook(new ByteArrayInputStream(importFile), ws);
        Sheet sheet = wb.getSheet(0);

        for (int i = settings.getStartRow() - 1; i < sheet.getRows(); i++) {

            Map<String, Object> fieldValues = new HashMap<String, Object>();
            for(String field : stringFields) {
                String value = getXLSFieldValue(sheet, i, defaultColumns.get(field));
                if(field.equals("extraBarcodeItem")){
                    fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7, settings.isBarcodeMaybeUPC()));
                } else if(field.equals("valueVAT")) {
                  fieldValues.put(field, parseVAT(value));  
                } else 
                    fieldValues.put(field, value);
            }
            for(String field : bigDecimalFields) {
                BigDecimal value = getXLSBigDecimalFieldValue(sheet, i, defaultColumns.get(field));
                fieldValues.put(field, value);
            }
            for(String field : dateFields) {
                Date value = getXLSDateFieldValue(sheet, i, defaultColumns.get(field));
                fieldValues.put(field, value);
            }
           
            String idItem = getXLSFieldValue(sheet, i, defaultColumns.get("idItem"));
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSFieldValue(sheet, i, defaultColumns.get("barcodeItem")), 7, settings.isBarcodeMaybeUPC());
            String packBarcode = BarcodeUtils.appendCheckDigitToBarcode(getXLSFieldValue(sheet, i, defaultColumns.get("packBarcode")), 7, settings.isBarcodeMaybeUPC());
            Date dateUserPriceList = getXLSDateFieldValue(sheet, i, defaultColumns.get("dateUserPriceList"));
            Date dateFrom = getXLSDateFieldValue(sheet, i, defaultColumns.get("dateFrom"), dateDocument);
            Date dateVAT = dateUserPriceList == null ? dateFrom : dateUserPriceList;
            BigDecimal quantityAdjustment = getXLSBigDecimalFieldValue(sheet, i, new ImportColumnDetail("quantityAdjustment", settings.getQuantityAdjustmentColumn(), false));
            String idUserPriceListDetail = makeIdUserPriceListDetail((String) fieldValues.get("idUserPriceList"), idItem, barcodeItem);
            String extIdPackBarcode = packBarcode == null ? ((settings.getItemKeyType().equals("barcode") ? barcodeItem : idItem) + "_pack") : packBarcode;

            LinkedHashMap<String, String> customValues = new LinkedHashMap<String, String>();
            for(Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                customValues.put(column.getKey(), getXLSFieldValue(sheet, i, column.getValue()));
            }
            
            if (idItem != null || barcodeItem != null) {
                Map<DataObject, BigDecimal> prices = new HashMap<DataObject, BigDecimal>();
                for (Map.Entry<DataObject, String[]> entry : priceColumns.entrySet()) {
                    BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, new ImportColumnDetail("price", entry.getValue(), false));
                    prices.put(entry.getKey(), price);
                }
                userPriceListDetailList.add(new UserPriceListDetail(customValues, fieldValues, settings.getIsPosted(), 
                        idUserPriceListDetail, idItem, barcodeItem, extIdPackBarcode, packBarcode, prices, quantityAdjustment,
                        dateUserPriceList, dateFrom, dateVAT));

            }
        }

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromCSV(byte[] importFile, ImportPriceListSettings settings, Map<DataObject, String[]> priceColumns,
                                                                  Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                  List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, 
                                                                  Date dateDocument)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile)));
        String line;

        List<String[]> valuesList = new ArrayList<String[]>();
        while ((line = br.readLine()) != null) {
            valuesList.add(line.split(settings.getSeparator()));
        }

        for (int count = settings.getStartRow(); count <= valuesList.size(); count++) {

            if (count >= settings.getStartRow()) {

                Map<String, Object> fieldValues = new HashMap<String, Object>();
                for(String field : stringFields) {
                    String value = getCSVFieldValue(valuesList, defaultColumns.get(field), count);
                    if(field.equals("extraBarcodeItem")) {
                        fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7, settings.isBarcodeMaybeUPC()));
                    } else if(field.equals("valueVAT")) {
                        fieldValues.put(field, parseVAT(value));
                    } else 
                        fieldValues.put(field, value);
                }
                for(String field : bigDecimalFields) {
                    BigDecimal value = getCSVBigDecimalFieldValue(valuesList, defaultColumns.get(field), count);
                    fieldValues.put(field, value);
                }
                for(String field : dateFields) {
                    Date value = getCSVDateFieldValue(valuesList, defaultColumns.get(field), count);
                    fieldValues.put(field, value);
                }
                
                String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getCSVFieldValue(valuesList, defaultColumns.get("barcodeItem"), count), 7, settings.isBarcodeMaybeUPC());
                String packBarcode = BarcodeUtils.appendCheckDigitToBarcode(getCSVFieldValue(valuesList, defaultColumns.get("packBarcode"), count), 7, settings.isBarcodeMaybeUPC());
                String idItem = getCSVFieldValue(valuesList, defaultColumns.get("idItem"), count);
                Date dateUserPriceList = getCSVDateFieldValue(valuesList, defaultColumns.get("dateFrom"), count);
                Date dateFrom = getCSVDateFieldValue(valuesList, defaultColumns.get("dateFrom"), count, dateDocument);
                Date dateVAT = dateUserPriceList == null ? dateFrom : dateUserPriceList;
                BigDecimal quantityAdjustment = getCSVBigDecimalFieldValue(valuesList, new ImportColumnDetail("quantityAdjustment", settings.getQuantityAdjustmentColumn(), false), count);
                String idUserPriceListDetail = makeIdUserPriceListDetail((String) fieldValues.get("idUserPriceList"), idItem, barcodeItem);
                String extIdPackBarcode = packBarcode == null ? ((settings.getItemKeyType().equals("barcode") ? barcodeItem : idItem) + "_pack") : packBarcode;

                LinkedHashMap<String, String> customValues = new LinkedHashMap<String, String>();
                for(Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                    customValues.put(column.getKey(), getCSVFieldValue(valuesList, column.getValue(), count));
                }
                
                if (idItem != null || barcodeItem != null) {
                    Map<DataObject, BigDecimal> prices = new HashMap<DataObject, BigDecimal>();
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

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromXLSX(byte[] importFile, ImportPriceListSettings settings, Map<DataObject, String[]> priceColumns,
                                                                   Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                   List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, 
                                                                   Date dateDocument)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        for (int i = settings.getStartRow() - 1; i <= sheet.getLastRowNum(); i++) {

            Map<String, Object> fieldValues = new HashMap<String, Object>();
            for(String field : stringFields) {
                String value = getXLSXFieldValue(sheet, i, defaultColumns.get(field));
                if(field.equals("extraBarcodeItem"))
                    fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7, settings.isBarcodeMaybeUPC()));
                else if(field.equals("valueVAT")) {
                    fieldValues.put(field, parseVAT(value));
                } else
                    fieldValues.put(field, value);
            }
            for(String field : bigDecimalFields) {
                BigDecimal value = getXLSXBigDecimalFieldValue(sheet, i, defaultColumns.get(field));
                fieldValues.put(field, value);
            }
            for(String field : dateFields) {
                Date value = getXLSXDateFieldValue(sheet, i, defaultColumns.get(field));
                fieldValues.put(field, value);
            }
            
            String idItem = getXLSXFieldValue(sheet, i, defaultColumns.get("idItem"));
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSXFieldValue(sheet, i, defaultColumns.get("barcodeItem")),7, settings.isBarcodeMaybeUPC());
            String packBarcode = BarcodeUtils.appendCheckDigitToBarcode(getXLSXFieldValue(sheet, i, defaultColumns.get("packBarcode")), 7, settings.isBarcodeMaybeUPC());
            BigDecimal quantityAdjustment = getXLSXBigDecimalFieldValue(sheet, i, new ImportColumnDetail("quantityAdjustment", settings.getQuantityAdjustmentColumn(), false));
            Date dateUserPriceList = getXLSXDateFieldValue(sheet, i, defaultColumns.get("dateUserPriceList"));
            Date dateFrom = getXLSXDateFieldValue(sheet, i, defaultColumns.get("dateFrom"), dateDocument);
            Date dateVAT = dateUserPriceList == null ? dateFrom : dateUserPriceList;

            String idUserPriceListDetail = makeIdUserPriceListDetail((String) fieldValues.get("idUserPriceList"), idItem, barcodeItem);
            String extIdPackBarcode = packBarcode == null ? ((settings.getItemKeyType().equals("barcode") ? barcodeItem : idItem) + "_pack") : packBarcode;

            LinkedHashMap<String, String> customValues = new LinkedHashMap<String, String>();
            for(Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                customValues.put(column.getKey(), getXLSXFieldValue(sheet, i, column.getValue()));
            }
            
            if (idItem != null || barcodeItem != null) {
                Map<DataObject, BigDecimal> prices = new HashMap<DataObject, BigDecimal>();
                for (Map.Entry<DataObject, String[]> entry : priceColumns.entrySet()) {
                    BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, new ImportColumnDetail("price", entry.getValue(), false));
                    prices.put(entry.getKey(), price);
                }
                userPriceListDetailList.add(new UserPriceListDetail(customValues, fieldValues, settings.getIsPosted(), idUserPriceListDetail,
                        idItem, barcodeItem, extIdPackBarcode, packBarcode, prices, quantityAdjustment, dateUserPriceList, 
                        dateFrom, dateVAT));
            }
        }

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromDBF(byte[] importFile, ImportPriceListSettings settings, Map<DataObject, String[]> priceColumns, 
                                                                  Map<String, ImportColumnDetail> defaultColumns, Map<String, ImportColumnDetail> customColumns,
                                                                  List<String> stringFields, List<String> bigDecimalFields, List<String> dateFields, 
                                                                  Date dateDocument)
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

        for (int i = 0; i < settings.getStartRow() - 1; i++) {
            dbfReader.nextRecord(Charset.forName(charset));
        }

        for (int i = settings.getStartRow() - 1; dbfReader.hasNextRecord(); i++) {

            Object entry[] = dbfReader.nextRecord(Charset.forName(charset));

            Map<String, Object> fieldValues = new HashMap<String, Object>();
            for (String field : stringFields) {
                String value = getJDBFFieldValue(entry, fieldNamesMap, defaultColumns.get(field), i);
                if(field.equals("extraBarcodeItem"))
                    fieldValues.put(field, BarcodeUtils.appendCheckDigitToBarcode(value, 7, settings.isBarcodeMaybeUPC()));
                else if(field.equals("valueVAT")) {
                    fieldValues.put(field, parseVAT(value));
                } else
                    fieldValues.put(field, value);
            }
            for(String field : bigDecimalFields) {
                BigDecimal value = getJDBFBigDecimalFieldValue(entry, fieldNamesMap, defaultColumns.get(field), i);
                fieldValues.put(field, value);
            }
            for(String field : dateFields) {
                Date value = getJDBFDateFieldValue(entry, fieldNamesMap, defaultColumns.get(field), i);
                fieldValues.put(field, value);
            }

            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getJDBFFieldValue(entry, fieldNamesMap, defaultColumns.get("barcodeItem"), i), 7, settings.isBarcodeMaybeUPC());
            String packBarcode = BarcodeUtils.appendCheckDigitToBarcode(getJDBFFieldValue(entry, fieldNamesMap, defaultColumns.get("packBarcode"), i), 7, settings.isBarcodeMaybeUPC());
            String idItem = getJDBFFieldValue(entry, fieldNamesMap, defaultColumns.get("idItem"), i);
            BigDecimal quantityAdjustment = getJDBFBigDecimalFieldValue(entry, fieldNamesMap, new ImportColumnDetail("quantityAdjustment", settings.getQuantityAdjustmentColumn(), false), i);
            Date dateUserPriceList = getJDBFDateFieldValue(entry, fieldNamesMap, defaultColumns.get("dateUserPriceList"), i);
            Date dateFrom = getJDBFDateFieldValue(entry, fieldNamesMap, defaultColumns.get("dateFrom"), i, dateDocument);
            String idUserPriceListDetail = makeIdUserPriceListDetail((String) fieldValues.get("idUserPriceList"), idItem, barcodeItem);
            String extIdPackBarcode = packBarcode == null ? ((settings.getItemKeyType().equals("barcode") ? barcodeItem : idItem) + "_pack") : packBarcode;

            LinkedHashMap<String, String> customValues = new LinkedHashMap<String, String>();
            for (Map.Entry<String, ImportColumnDetail> column : customColumns.entrySet()) {
                customValues.put(column.getKey(), getJDBFFieldValue(entry, fieldNamesMap, column.getValue(), i));
            }
            
            if (idItem != null || barcodeItem != null) {
                Map<DataObject, BigDecimal> prices = new HashMap<DataObject, BigDecimal>();
                for (Map.Entry<DataObject, String[]> priceEntry : priceColumns.entrySet()) {
                    BigDecimal price = getJDBFBigDecimalFieldValue(entry, fieldNamesMap, new ImportColumnDetail("price", priceEntry.getValue(), false), i);
                    prices.put(priceEntry.getKey(), price);
                }
                userPriceListDetailList.add(new UserPriceListDetail(customValues, fieldValues, settings.getIsPosted(), idUserPriceListDetail,
                        idItem, barcodeItem, extIdPackBarcode, packBarcode, prices, quantityAdjustment, dateUserPriceList, 
                        dateFrom, dateFrom));
            }
        }

        dbfReader.close();
        tempFile.delete();
        return userPriceListDetailList;
    }

    public List<LinkedHashMap<String, ImportColumnDetail>> readImportColumns(ExecutionContext context, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        LinkedHashMap<String, ImportColumnDetail> defaultColumns = new LinkedHashMap<String, ImportColumnDetail>();
        LinkedHashMap<String, ImportColumnDetail> customColumns = new LinkedHashMap<String, ImportColumnDetail>();

        KeyExpr importUserPriceListTypeDetailExpr = new KeyExpr("importUserPriceListTypeDetail");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "importUserPriceListTypeDetail", importUserPriceListTypeDetailExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        String[] names = new String[] {"staticName", "staticCaption", "propertyImportUserPriceListTypeDetail", "nameKeyImportUserPriceListTypeDetail"};
        LCP[] properties = findProperties("staticName", "staticCaption", "propertyImportTypeDetail", "nameKeyImportTypeDetail");
        for (int j = 0; j < properties.length; j++) {
            query.addProperty(names[j], properties[j].getExpr(importUserPriceListTypeDetailExpr));
        }
        query.addProperty("replaceOnlyNullImportUserPriceListTypeImportUserPriceListTypeDetail", findProperty("replaceOnlyNullImportUserPriceListTypeImportUserPriceListTypeDetail").getExpr(importTypeObject.getExpr(), importUserPriceListTypeDetailExpr));
        query.addProperty("indexImportUserPriceListTypeImportUserPriceListTypeDetail", findProperty("indexImportUserPriceListTypeImportUserPriceListTypeDetail").getExpr(importTypeObject.getExpr(), importUserPriceListTypeDetailExpr));
        query.and(findProperty("indexImportUserPriceListTypeImportUserPriceListTypeDetail").getExpr(importTypeObject.getExpr(), importUserPriceListTypeDetailExpr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String staticNameProperty = trim((String) entry.get("staticName"));
            String field = getSplittedPart(staticNameProperty, "\\.", -1);
            String staticCaptionProperty = trim((String) entry.get("staticCaption"));
            String propertyImportTypeDetail = (String) entry.get("propertyImportUserPriceListTypeDetail");
            String moduleName = getSplittedPart(propertyImportTypeDetail, "\\.", 0);
            String sidProperty = getSplittedPart(propertyImportTypeDetail, "\\.", 1);
            String keyImportTypeDetail = getSplittedPart((String) entry.get("nameKeyImportUserPriceListTypeDetail"), "\\.", 1);
            boolean replaceOnlyNull = entry.get("replaceOnlyNullImportUserPriceListTypeImportUserPriceListTypeDetail") != null;
            String indexes = (String) entry.get("indexImportUserPriceListTypeImportUserPriceListTypeDetail");
            if (indexes != null) {
                String[] splittedIndexes = indexes.split("\\+");
                for (int i = 0; i < splittedIndexes.length; i++)
                    splittedIndexes[i] = splittedIndexes[i].contains("=") ? splittedIndexes[i] : trim(splittedIndexes[i]);
                if(field != null)
                    defaultColumns.put(field, new ImportColumnDetail(staticCaptionProperty, indexes, splittedIndexes, replaceOnlyNull));
                else if(keyImportTypeDetail != null)
                    customColumns.put(staticCaptionProperty, new ImportColumnDetail(staticCaptionProperty, indexes, splittedIndexes, replaceOnlyNull,
                            moduleName, sidProperty, keyImportTypeDetail));
            }
        }
        return Arrays.asList(defaultColumns, customColumns);
    }

    public Map<DataObject, String[]> readPriceImportColumns(ExecutionContext context, ObjectValue importUserPriceListTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Map<DataObject, String[]> importColumns = new HashMap<DataObject, String[]>();

        LCP<PropertyInterface> isDataPriceListType = (LCP<PropertyInterface>) is(findClass("DataPriceListType"));
        ImRevMap<PropertyInterface, KeyExpr> keys = isDataPriceListType.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<PropertyInterface, Object> query = new QueryBuilder<PropertyInterface, Object>(keys);
        query.addProperty("indexImportUserPriceListTypeDataPriceListType", findProperty("indexImportUserPriceListTypeDataPriceListType").getExpr(context.getModifier(), importUserPriceListTypeObject.getExpr(), key));
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
        String fileExtension = trim((String) findProperty("captionImportUserPriceListTypeFileExtensionImportUserPriceListType").read(context, importTypeObject));        
        String quantityAdjustmentColumn = (String) findProperty("quantityAdjustmentImportUserPriceListType").read(context, importTypeObject);

        ObjectValue operation = findProperty("operationImportUserPriceListType").readClasses(context, (DataObject) importTypeObject);
        DataObject operationObject = operation instanceof NullValue ? null : (DataObject) operation;

        ObjectValue company = findProperty("companyImportUserPriceListType").readClasses(context, (DataObject) importTypeObject);
        DataObject companyObject = company instanceof NullValue ? null : (DataObject) company;

        ObjectValue stock = findProperty("stockImportUserPriceListType").readClasses(context, (DataObject) importTypeObject);
        DataObject stockObject = stock instanceof NullValue ? null : (DataObject) stock;

        ObjectValue defaultItemGroup = findProperty("defaultItemGroupImportUserPriceListType").readClasses(context, (DataObject) importTypeObject);
        DataObject defaultItemGroupObject = defaultItemGroup instanceof NullValue ? null : (DataObject) defaultItemGroup;
        
        String itemKeyType = (String) findProperty("nameImportUserPriceListKeyTypeImportUserPriceListType").read(context, importTypeObject);
        String[] parts = itemKeyType == null ? null : itemKeyType.split("\\.");
        itemKeyType = parts == null ? null : trim(parts[parts.length - 1]);
        
        String separator = trim((String) findProperty("separatorImportUserPriceListType").read(context, importTypeObject), ";");
        Integer startRow = (Integer) findProperty("startRowImportUserPriceListType").read(context, importTypeObject);
        startRow = startRow == null || startRow.equals(0) ? 1 : startRow;
        Boolean isPosted = (Boolean) findProperty("isPostedImportUserPriceListType").read(context, importTypeObject);
        boolean doNotCreateItems = findProperty("doNotCreateItemsImportUserPriceListType").read(context, importTypeObject) != null;
        boolean barcodeMaybeUPC = findProperty("barcodeMaybeUPCImportUserPriceListType").read(context, importTypeObject) != null;
        return new ImportPriceListSettings(fileExtension, quantityAdjustmentColumn, operationObject, companyObject, stockObject, defaultItemGroupObject, itemKeyType, separator, startRow, isPosted, doNotCreateItems, barcodeMaybeUPC);
    }


    private String makeIdUserPriceListDetail(String idUserPriceList, String idItem, String barcodeItem) {
        return (idUserPriceList == null ? "" : idUserPriceList) + "_" + (idItem == null ? "" : idItem) + "_" + (barcodeItem == null ? "" : barcodeItem);
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
        } catch (NoSuchFieldException e) {
            return true;
        } catch (IllegalAccessException e) {
            return true;
        }
        return false;
    }
}

