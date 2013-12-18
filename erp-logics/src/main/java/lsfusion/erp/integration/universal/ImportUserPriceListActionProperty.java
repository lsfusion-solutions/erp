package lsfusion.erp.integration.universal;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.stock.BarcodeUtils;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
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
import java.util.*;

public class ImportUserPriceListActionProperty extends ImportUniversalActionProperty {
    private final ClassPropertyInterface userPriceListInterface;

    // Опциональные модули
    private ScriptingLogicsModule itemArticleLM;

    public ImportUserPriceListActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("UserPriceList"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        userPriceListInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {

            DataObject userPriceListObject = context.getDataKeyValue(userPriceListInterface);

            ObjectValue importUserPriceListTypeObject = getLCP("importUserPriceListTypeUserPriceList").readClasses(context, userPriceListObject);

            if (!(importUserPriceListTypeObject instanceof NullValue)) {

                String fileExtension = (String) getLCP("captionImportUserPriceListTypeFileExtensionImportUserPriceListType").read(context, importUserPriceListTypeObject);
                String itemKeyType = (String) getLCP("nameImportUserPriceListKeyTypeImportUserPriceListType").read(context, importUserPriceListTypeObject);
                String[] parts = itemKeyType == null ? null : itemKeyType.split("\\.");
                itemKeyType = parts == null ? null : parts[parts.length - 1].trim();
                String csvSeparator = (String) getLCP("separatorImportUserPriceListType").read(context, importUserPriceListTypeObject);
                csvSeparator = csvSeparator == null ? ";" : csvSeparator;
                Integer startRow = (Integer) getLCP("startRowImportUserPriceListType").read(context, importUserPriceListTypeObject);
                startRow = startRow == null || startRow.equals(0) ? 1 : startRow;
                Boolean isPosted = (Boolean) getLCP("isPostedImportUserPriceListType").read(context, importUserPriceListTypeObject);

                ImportColumns importColumns = readImportColumns(context, LM, importUserPriceListTypeObject);

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension.trim() + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            importData(context, userPriceListObject, importColumns, file, fileExtension.trim(), startRow,
                                    isPosted, csvSeparator, itemKeyType, false);

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

    public boolean importData(ExecutionContext context, DataObject userPriceListObject, ImportColumns importColumns,
                              byte[] file, String fileExtension, Integer startRow, Boolean isPosted, String csvSeparator, String itemKeyType,
                              boolean apply)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException, UniversalImportException {

        this.itemArticleLM = (ScriptingLogicsModule) context.getBL().getModule("ItemArticle");
        
        List<UserPriceListDetail> userPriceListDetailList;

        if (fileExtension.equals("DBF"))
            userPriceListDetailList = importUserPriceListsFromDBF(file, importColumns, startRow, isPosted);
        else if (fileExtension.equals("XLS"))
            userPriceListDetailList = importUserPriceListsFromXLS(file, importColumns, startRow, isPosted);
        else if (fileExtension.equals("XLSX"))
            userPriceListDetailList = importUserPriceListsFromXLSX(file, importColumns, startRow, isPosted);
        else if (fileExtension.equals("CSV"))
            userPriceListDetailList = importUserPriceListsFromCSV(file, importColumns, startRow, isPosted, csvSeparator);
        else
            userPriceListDetailList = null;

        return importUserPriceListDetails(context, userPriceListDetailList, importColumns, userPriceListObject, itemKeyType, apply)
                && (importColumns.getQuantityAdjustmentColumn() == null || importAdjustmentDetails(context, userPriceListDetailList, importColumns.getStockObject(), itemKeyType, apply));

    }

    private boolean importUserPriceListDetails(ExecutionContext context, List<UserPriceListDetail> userPriceListDetailsList,
                                               ImportColumns importColumnProperties, DataObject userPriceListObject,
                                               String itemKeyType, boolean apply) throws ScriptingErrorLog.SemanticErrorException, SQLException {

        Map<String, ImportColumnDetail> importColumns = importColumnProperties.getColumns();
        DataObject operationObject = importColumnProperties.getOperationObject();
        DataObject defaultItemGroupObject = importColumnProperties.getDefaultItemGroupObject();
        Set<DataObject> dataPriceListTypeObjectList = importColumnProperties.getPriceColumns().keySet();
        
        if (userPriceListDetailsList != null) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(userPriceListDetailsList.size());

            if (operationObject != null) {
                props.add(new ImportProperty(operationObject, getLCP("operationUserPriceList").getMapping(userPriceListObject)));
            }

            ImportField idItemField = new ImportField(getLCP("idItem"));
            fields.add(idItemField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(userPriceListDetailsList.get(i).idItem);

            ImportField idBarcodeSkuField = new ImportField(getLCP("idBarcodeSku"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Barcode"),
                    getLCP("extBarcodeId").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idBarcodeSkuField, getLCP("idBarcode").getMapping(barcodeKey), getReplaceOnlyNull(importColumns, "barcodeItem")));
            props.add(new ImportProperty(idBarcodeSkuField, getLCP("extIdBarcode").getMapping(barcodeKey), getReplaceOnlyNull(importColumns, "barcodeItem")));
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(userPriceListDetailsList.get(i).barcodeItem);

            String iGroupAggr = (itemKeyType == null || itemKeyType.equals("item")) ? "itemId" : "skuIdBarcode";
            ImportField iField = (itemKeyType == null || itemKeyType.equals("item")) ? idItemField : idBarcodeSkuField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Item"),
                    getLCP(iGroupAggr).getMapping(iField));
            keys.add(itemKey);

            if (itemArticleLM != null && showField(userPriceListDetailsList, "articleItem")) {
                ImportField idArticleItemField = new ImportField(itemArticleLM.findLCPByCompoundOldName("idArticleItem"));
                ImportKey<?> articleKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Article"),
                        itemArticleLM.findLCPByCompoundOldName("articleId").getMapping(idArticleItemField));
                keys.add(articleKey);
                props.add(new ImportProperty(idArticleItemField, itemArticleLM.findLCPByCompoundOldName("idArticle").getMapping(articleKey)));
                props.add(new ImportProperty(idArticleItemField, itemArticleLM.findLCPByCompoundOldName("articleItem").getMapping(itemKey),
                        itemArticleLM.object(itemArticleLM.findClassByCompoundName("Article")).getMapping(articleKey)));
                fields.add(idArticleItemField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).articleItem);
            }

            if (showField(userPriceListDetailsList, "idUserPriceList")) {
                ImportField idUserPriceListField = new ImportField(getLCP("idUserPriceList"));
                props.add(new ImportProperty(idUserPriceListField, getLCP("numberUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(importColumns, "idUserPriceList")));
                fields.add(idUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).idUserPriceList);

                ImportField isPostedUserPriceListField = new ImportField(getLCP("isPostedUserPriceList"));
                props.add(new ImportProperty(isPostedUserPriceListField, getLCP("isPostedUserPriceList").getMapping(userPriceListObject)));
                fields.add(isPostedUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(true);
            }

            ImportField idUserPriceListDetailField = new ImportField(getLCP("idUserPriceListDetail"));
            ImportKey<?> userPriceListDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserPriceListDetail"),
                    getLCP("userPriceListDetailIdUserPriceList").getMapping(idUserPriceListDetailField, userPriceListObject));
            keys.add(userPriceListDetailKey);
            props.add(new ImportProperty(userPriceListObject, getLCP("userPriceListUserPriceListDetail").getMapping(userPriceListDetailKey)));
            props.add(new ImportProperty(idUserPriceListDetailField, getLCP("idUserPriceListDetail").getMapping(userPriceListDetailKey)));
            props.add(new ImportProperty(idItemField, getLCP("idItem").getMapping(itemKey)));
            props.add(new ImportProperty(iField, getLCP("skuUserPriceListDetail").getMapping(userPriceListDetailKey),
                    LM.object(LM.findClassByCompoundName("Sku")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, getLCP("skuBarcode").getMapping(barcodeKey),
                    LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));
            fields.add(idUserPriceListDetailField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(userPriceListDetailsList.get(i).idUserPriceListDetail);

            if (defaultItemGroupObject != null) {
                props.add(new ImportProperty(defaultItemGroupObject, getLCP("itemGroupItem").getMapping(itemKey)));
            }

            if (showField(userPriceListDetailsList, "captionItem")) {
                ImportField captionItemField = new ImportField(getLCP("captionItem"));
                props.add(new ImportProperty(captionItemField, getLCP("captionItem").getMapping(itemKey), getReplaceOnlyNull(importColumns, "captionItem")));
                fields.add(captionItemField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).captionItem);
            }

            if (showField(userPriceListDetailsList, "idUOMItem")) {
                ImportField idUOMField = new ImportField(getLCP("idUOM"));
                ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UOM"),
                        getLCP("UOMId").getMapping(idUOMField));
                keys.add(UOMKey);
                props.add(new ImportProperty(idUOMField, getLCP("idUOM").getMapping(UOMKey), getReplaceOnlyNull(importColumns, "idUOMItem")));
                props.add(new ImportProperty(idUOMField, getLCP("shortNameUOM").getMapping(UOMKey), getReplaceOnlyNull(importColumns, "idUOMItem")));
                props.add(new ImportProperty(idUOMField, getLCP("UOMItem").getMapping(itemKey),
                        LM.object(LM.findClassByCompoundName("UOM")).getMapping(UOMKey), getReplaceOnlyNull(importColumns, "idUOMItem")));
                fields.add(idUOMField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).idUOMItem);
            }

            for (DataObject dataPriceListTypeObject : dataPriceListTypeObjectList) {
                ImportField pricePriceListDetailDataPriceListTypeField = new ImportField(getLCP("pricePriceListDetailDataPriceListType"));
                props.add(new ImportProperty(pricePriceListDetailDataPriceListTypeField, getLCP("pricePriceListDetailDataPriceListType").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                fields.add(pricePriceListDetailDataPriceListTypeField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).prices.get(dataPriceListTypeObject));
            }

            if (showField(userPriceListDetailsList, "date")) {
                ImportField dateUserPriceListField = new ImportField(getLCP("dateUserPriceList"));
                props.add(new ImportProperty(dateUserPriceListField, getLCP("dateUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(importColumns, "date")));
                props.add(new ImportProperty(dateUserPriceListField, getLCP("fromDateUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(importColumns, "date")));
                fields.add(dateUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).date);

                ImportField timeUserPriceListField = new ImportField(getLCP("timeUserPriceList"));
                props.add(new ImportProperty(timeUserPriceListField, getLCP("timeUserPriceList").getMapping(userPriceListObject)));
                props.add(new ImportProperty(timeUserPriceListField, getLCP("fromTimeUserPriceList").getMapping(userPriceListObject)));
                fields.add(timeUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(new Time(0, 0, 0));
            }

            if (showField(userPriceListDetailsList, "isPosted")) {
                ImportField isPostedUserPriceListField = new ImportField(getLCP("isPostedUserPriceList"));
                props.add(new ImportProperty(isPostedUserPriceListField, getLCP("isPostedUserPriceList").getMapping(userPriceListObject), getReplaceOnlyNull(importColumns, "isPosted")));
                fields.add(isPostedUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).isPosted);
            }

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.getSession();
            session.sql.pushVolatileStats(null);
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            String result = null;
            if (apply) {
                result = session.applyMessage(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }

            getLAP("formRefresh").execute(context);

            return result == null;
        }
        return false;
    }

    private boolean importAdjustmentDetails(ExecutionContext context, List<UserPriceListDetail> dataAdjustment,
                                            DataObject stockObject, String itemKeyType, boolean apply)
            throws ScriptingErrorLog.SemanticErrorException, SQLException {

        if (dataAdjustment != null) {

            getLAP("unpostAllUserAdjustment").execute(context.getSession());
            getLAP("overImportAdjustment").execute(context.getSession());

            DataObject userAdjustmentObject = context.addObject((ConcreteCustomClass) LM.findClassByCompoundName("UserAdjustment"));

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(dataAdjustment.size());

            if (stockObject != null) {
                props.add(new ImportProperty(stockObject, getLCP("stockUserAdjustment").getMapping(userAdjustmentObject)));
            }

            ImportField idItemField = new ImportField(getLCP("idItem"));
            fields.add(idItemField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(dataAdjustment.get(i).idItem);

            ImportField idBarcodeSkuField = new ImportField(getLCP("idBarcodeSku"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Barcode"),
                    getLCP("extBarcodeId").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(dataAdjustment.get(i).barcodeItem);

            String iGroupAggr = (itemKeyType == null || itemKeyType.equals("item")) ? "itemId" : "skuIdBarcode";
            ImportField iField = (itemKeyType == null || itemKeyType.equals("item")) ? idItemField : idBarcodeSkuField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Item"),
                    getLCP(iGroupAggr).getMapping(iField));
            keys.add(itemKey);

            ImportField idUserAdjustmentDetailField = new ImportField(getLCP("idUserAdjustmentDetail"));
            ImportKey<?> userAdjustmentDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserAdjustmentDetail"),
                    getLCP("userAdjustmentDetailIdUserAdjustment").getMapping(idUserAdjustmentDetailField, userAdjustmentObject));
            keys.add(userAdjustmentDetailKey);
            props.add(new ImportProperty(userAdjustmentObject, getLCP("userAdjustmentUserAdjustmentDetail").getMapping(userAdjustmentDetailKey)));
            props.add(new ImportProperty(idUserAdjustmentDetailField, getLCP("idUserAdjustmentDetail").getMapping(userAdjustmentDetailKey)));
            props.add(new ImportProperty(iField, getLCP("skuUserAdjustmentDetail").getMapping(userAdjustmentDetailKey),
                    LM.object(LM.findClassByCompoundName("Sku")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, getLCP("skuBarcode").getMapping(barcodeKey),
                    LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));
            fields.add(idUserAdjustmentDetailField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(dataAdjustment.get(i).idUserPriceListDetail);

            ImportField quantityUserAdjustmentDetailField = new ImportField(getLCP("quantityUserAdjustmentDetail"));
            props.add(new ImportProperty(quantityUserAdjustmentDetailField, getLCP("quantityUserAdjustmentDetail").getMapping(userAdjustmentDetailKey)));
            fields.add(quantityUserAdjustmentDetailField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(dataAdjustment.get(i).quantityAdjustment);

            if (showField(dataAdjustment, "date")) {
                ImportField dateUserAdjustmentField = new ImportField(getLCP("dateUserAdjustment"));
                props.add(new ImportProperty(dateUserAdjustmentField, getLCP("dateUserAdjustment").getMapping(userAdjustmentObject)));
                fields.add(dateUserAdjustmentField);
                for (int i = 0; i < dataAdjustment.size(); i++)
                    data.get(i).add(dataAdjustment.get(i).date);

                ImportField timeUserAdjustmentField = new ImportField(getLCP("timeUserAdjustment"));
                props.add(new ImportProperty(timeUserAdjustmentField, getLCP("timeUserAdjustment").getMapping(userAdjustmentObject)));
                fields.add(timeUserAdjustmentField);
                for (int i = 0; i < dataAdjustment.size(); i++)
                    data.get(i).add(new Time(0, 0, 0));
            }

            ImportField isPostedAdjustmentField = new ImportField(getLCP("isPostedAdjustment"));
            props.add(new ImportProperty(isPostedAdjustmentField, getLCP("isPostedAdjustment").getMapping(userAdjustmentObject)));
            fields.add(isPostedAdjustmentField);
            for (int i = 0; i < dataAdjustment.size(); i++)
                data.get(i).add(true);


            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.getSession();
            session.sql.pushVolatileStats(null);
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            String result = null;
            if (apply) {
                result = session.applyMessage(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }

            getLAP("formRefresh").execute(context);

            return result == null;
        }
        return false;
    }

    private List<UserPriceListDetail> importUserPriceListsFromXLS(byte[] importFile, ImportColumns importColumns, Integer startRow, Boolean isPosted)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding("cp1251");
        Workbook wb = Workbook.getWorkbook(new ByteArrayInputStream(importFile), ws);
        Sheet sheet = wb.getSheet(0);

        Date date = (importColumns.getDateRow() == null || importColumns.getDateColumn() == null) ?
                null : getXLSDateFieldValue(sheet, new ImportColumnDetail("date", String.valueOf(importColumns.getDateRow()), false), importColumns.getDateRow(), importColumns.getDateColumn());

        for (int i = startRow - 1; i < sheet.getRows(); i++) {
            String idUserPriceList = getXLSFieldValue(sheet, i, importColumns.getColumns().get("idUserPriceList"));
            String idItem = getXLSFieldValue(sheet, i, importColumns.getColumns().get("idItem"));
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSFieldValue(sheet, i, importColumns.getColumns().get("barcodeItem")));
            String articleItem = getXLSFieldValue(sheet, i, importColumns.getColumns().get("articleItem"));
            String captionItem = getXLSFieldValue(sheet, i, importColumns.getColumns().get("captionItem"));
            String idUOMItem = getXLSFieldValue(sheet, i, importColumns.getColumns().get("idUOMItem"));
            BigDecimal quantityAdjustment = getXLSBigDecimalFieldValue(sheet, i, new ImportColumnDetail("quantityAdjustment", importColumns.getQuantityAdjustmentColumn(), false));
            String idUserPriceListDetail = (idItem == null ? "" : idItem) + "_" + (barcodeItem == null ? "" : barcodeItem);
            if (!idUserPriceListDetail.equals("_")) {
                Map<DataObject, BigDecimal> prices = new HashMap<DataObject, BigDecimal>();
                for (Map.Entry<DataObject, String[]> entry : importColumns.getPriceColumns().entrySet()) {
                    BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, new ImportColumnDetail("price", entry.getValue(), false));
                    prices.put(entry.getKey(), price);
                }
                userPriceListDetailList.add(new UserPriceListDetail(isPosted, idUserPriceListDetail, idUserPriceList,
                        idItem, barcodeItem, articleItem, captionItem, idUOMItem, date, prices, quantityAdjustment));

            }
        }

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromCSV(byte[] importFile, ImportColumns importColumns, Integer startRow, Boolean isPosted, String csvSeparator)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile)));
        String line;
        int count = 0;

        while ((line = br.readLine()) != null) {

            count++;

            if (count >= startRow) {

                String[] values = line.split(csvSeparator);

                Date date = (importColumns.getDateRow() == null || importColumns.getDateColumn() == null || (importColumns.getDateRow()) != count) ?
                        null : getCSVDateFieldValue(values, new ImportColumnDetail("date", importColumns.getDateColumn(), false), importColumns.getDateColumn(), count, null);

                String idUserPriceList = getCSVFieldValue(values, importColumns.getColumns().get("idUserPriceList"), count);
                String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getCSVFieldValue(values, importColumns.getColumns().get("barcodeItem"), count));
                String articleItem = getCSVFieldValue(values, importColumns.getColumns().get("articleItem"), count);
                String idItem = getCSVFieldValue(values, importColumns.getColumns().get("idItem"), count);
                String captionItem = getCSVFieldValue(values, importColumns.getColumns().get("captionItem"), count);
                String idUOMItem = getCSVFieldValue(values, importColumns.getColumns().get("idUOMItem"), count);
                BigDecimal quantityAdjustment = getCSVBigDecimalFieldValue(values, new ImportColumnDetail("quantityAdjustment", importColumns.getQuantityAdjustmentColumn(), false), importColumns.getQuantityAdjustmentColumn(), count, null);
                String idUserPriceListDetail = (idItem == null ? "" : idItem) + "_" + (barcodeItem == null ? "" : barcodeItem);
                if (!idUserPriceListDetail.equals("_")) {
                    Map<DataObject, BigDecimal> prices = new HashMap<DataObject, BigDecimal>();
                    for (Map.Entry<DataObject, String[]> entry : importColumns.getPriceColumns().entrySet()) {
                        BigDecimal price = getCSVBigDecimalFieldValue(values, new ImportColumnDetail("price", entry.getValue(), false), count);
                        prices.put(entry.getKey(), price);
                    }
                    userPriceListDetailList.add(new UserPriceListDetail(isPosted, idUserPriceListDetail, idUserPriceList,
                            idItem, barcodeItem, articleItem, captionItem, idUOMItem, date, prices, quantityAdjustment));
                }
            }
        }

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromXLSX(byte[] importFile, ImportColumns importColumns, Integer startRow, Boolean isPosted)
            throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        Date date = (importColumns.getDateRow() == null || importColumns.getDateColumn() == null) ?
                null : getXLSXDateFieldValue(sheet, new ImportColumnDetail("date", String.valueOf(importColumns.getDateRow()), false), importColumns.getDateRow(), importColumns.getDateColumn());

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {

            String idUserPriceList = getXLSXFieldValue(sheet, i, importColumns.getColumns().get("idUserPriceList"));
            String idItem = getXLSXFieldValue(sheet, i, importColumns.getColumns().get("idItem"));
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getXLSXFieldValue(sheet, i, importColumns.getColumns().get("barcodeItem")));
            String articleItem = getXLSXFieldValue(sheet, i, importColumns.getColumns().get("articleItem"));
            String captionItem = getXLSXFieldValue(sheet, i, importColumns.getColumns().get("captionItem"));
            String idUOMItem = getXLSXFieldValue(sheet, i, importColumns.getColumns().get("idUOMItem"));
            BigDecimal quantityAdjustment = getXLSXBigDecimalFieldValue(sheet, new ImportColumnDetail("quantityAdjustment", String.valueOf(importColumns.getDateRow()), false), i, importColumns.getQuantityAdjustmentColumn());
            String idUserPriceListDetail = (idItem == null ? "" : idItem) + "_" + (barcodeItem == null ? "" : barcodeItem);
            if (!idUserPriceListDetail.equals("_")) {
                Map<DataObject, BigDecimal> prices = new HashMap<DataObject, BigDecimal>();
                for (Map.Entry<DataObject, String[]> entry : importColumns.getPriceColumns().entrySet()) {
                    BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, new ImportColumnDetail("price", entry.getValue(), false));
                    prices.put(entry.getKey(), price);
                }
                userPriceListDetailList.add(new UserPriceListDetail(isPosted, idUserPriceListDetail, idUserPriceList,
                        idItem, barcodeItem, articleItem, captionItem, idUOMItem, date, prices, quantityAdjustment));
            }
        }

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromDBF(byte[] importFile, ImportColumns importColumns, Integer startRow, Boolean isPosted)
            throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, UniversalImportException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        File tempFile = File.createTempFile("dutiesTNVED", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);
        DBF file = new DBF(tempFile.getPath());
        int totalRecordCount = file.getRecordCount();

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String idUserPriceList = getDBFFieldValue(file, importColumns.getColumns().get("idUserPriceList"), i);
            String barcodeItem = BarcodeUtils.appendCheckDigitToBarcode(getDBFFieldValue(file, importColumns.getColumns().get("barcodeItem"), i));
            String articleItem = getDBFFieldValue(file, importColumns.getColumns().get("articleItem"), i);
            String idItem = getDBFFieldValue(file, importColumns.getColumns().get("idItem"), i);
            String captionItem = getDBFFieldValue(file, importColumns.getColumns().get("captionItem"), i);
            String idUOMItem = getDBFFieldValue(file, importColumns.getColumns().get("idUOMItem"), i);
            BigDecimal quantityAdjustment = getDBFBigDecimalFieldValue(file, new ImportColumnDetail("quantityAdjustment", importColumns.getQuantityAdjustmentColumn(), false), importColumns.getQuantityAdjustmentColumn(), i);

            String idUserPriceListDetail = (idItem == null ? "" : idItem) + "_" + (barcodeItem == null ? "" : barcodeItem);
            if (!idUserPriceListDetail.equals("_")) {
                Map<DataObject, BigDecimal> prices = new HashMap<DataObject, BigDecimal>();
                for (Map.Entry<DataObject, String[]> entry : importColumns.getPriceColumns().entrySet()) {
                    BigDecimal price = getDBFBigDecimalFieldValue(file, new ImportColumnDetail("price", entry.getValue(), false), i);
                    prices.put(entry.getKey(), price);
                }
                userPriceListDetailList.add(new UserPriceListDetail(isPosted, idUserPriceListDetail, idUserPriceList,
                        idItem, barcodeItem, articleItem, captionItem, idUOMItem, null, prices, quantityAdjustment));
            }
        }

        file.close();

        return userPriceListDetailList;
    }

    protected static ImportColumns readImportColumns(ExecutionContext context, ScriptingLogicsModule LM, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException {
        Map<String, ImportColumnDetail> columns = readColumns(context, LM, importTypeObject);
        Map<DataObject, String[]> priceColumns = readPriceImportColumns(context, LM, importTypeObject);
        String quantityAdjustmentColumn = (String) LM.findLCPByCompoundOldName("quantityAdjustmentImportUserPriceListType").read(context, importTypeObject);

        String dateRowString = (String) LM.findLCPByCompoundOldName("dateRowImportUserPriceListType").read(context, importTypeObject);
        Integer dateRow;
        try {
            dateRow = dateRowString == null ? null : Integer.parseInt(dateRowString);
        } catch (Exception e) {
            dateRow = null;
        }
        String dateColumnString = (String) LM.findLCPByCompoundOldName("dateColumnImportUserPriceListType").read(context, importTypeObject);
        Integer dateColumn;
        try {
            dateColumn = dateColumnString == null ? null : Integer.parseInt(dateColumnString);
        } catch (Exception e) {
            dateColumn = null;
        }

        ObjectValue operation = LM.findLCPByCompoundOldName("operationImportUserPriceListType").readClasses(context, (DataObject) importTypeObject);
        DataObject operationObject = operation instanceof NullValue ? null : (DataObject) operation;

        ObjectValue stock = LM.findLCPByCompoundOldName("stockImportUserPriceListType").readClasses(context, (DataObject) importTypeObject);
        DataObject stockObject = stock instanceof NullValue ? null : (DataObject) stock;

        ObjectValue defaultItemGroup = LM.findLCPByCompoundOldName("defaultItemGroupImportUserPriceListType").readClasses(context, (DataObject) importTypeObject);
        DataObject defaultItemGroupObject = defaultItemGroup instanceof NullValue ? null : (DataObject) defaultItemGroup;

        return new ImportColumns(columns, priceColumns, quantityAdjustmentColumn, dateRow, dateColumn, operationObject,
                stockObject, defaultItemGroupObject);
    }

    private static Map<String, ImportColumnDetail> readColumns(ExecutionContext context, ScriptingLogicsModule LM, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException {

        Map<String, ImportColumnDetail> importColumns = new HashMap<String, ImportColumnDetail>();

        LCP<?> isImportTypeDetail = LM.is(LM.findClassByCompoundName("ImportUserPriceListTypeDetail"));
        ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isImportTypeDetail.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        query.addProperty("staticName", LM.findLCPByCompoundOldName("staticName").getExpr(context.getModifier(), key));
        query.addProperty("staticCaption", LM.findLCPByCompoundOldName("staticCaption").getExpr(context.getModifier(), key));
        query.addProperty("replaceOnlyNullImportUserPriceListTypeImportUserPriceListTypeDetail", LM.findLCPByCompoundOldName("replaceOnlyNullImportUserPriceListTypeImportUserPriceListTypeDetail").getExpr(context.getModifier(), importTypeObject.getExpr(), key));
        query.addProperty("indexImportUserPriceListTypeImportUserPriceListTypeDetail", LM.findLCPByCompoundOldName("indexImportUserPriceListTypeImportUserPriceListTypeDetail").getExpr(context.getModifier(), importTypeObject.getExpr(), key));
        query.and(isImportTypeDetail.getExpr(key).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context.getSession().sql);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String[] field = ((String) entry.get("staticName")).trim().split("\\.");
            String captionProperty = (String) entry.get("staticCaption");
            captionProperty = captionProperty == null ? null : captionProperty.trim();
            boolean replaceOnlyNull = entry.get("replaceOnlyNullImportUserPriceListTypeImportUserPriceListTypeDetail") != null;
            String indexes = (String) entry.get("indexImportUserPriceListTypeImportUserPriceListTypeDetail");
            if (indexes != null) {
                String[] splittedIndexes = indexes.split("\\+");
                for (int i = 0; i < splittedIndexes.length; i++)
                    splittedIndexes[i] = splittedIndexes[i].trim();
                importColumns.put(field[field.length - 1], new ImportColumnDetail(captionProperty, indexes, splittedIndexes, replaceOnlyNull));
            }
        }
        return importColumns;
    }

    private static Map<DataObject, String[]> readPriceImportColumns(ExecutionContext context, ScriptingLogicsModule LM, ObjectValue importUserPriceListTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException {

        Map<DataObject, String[]> importColumns = new HashMap<DataObject, String[]>();

        LCP<?> isDataPriceListType = LM.is(LM.findClassByCompoundName("DataPriceListType"));
        ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isDataPriceListType.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        query.addProperty("indexImportUserPriceListTypeDataPriceListType", LM.findLCPByCompoundOldName("indexImportUserPriceListTypeDataPriceListType").getExpr(context.getModifier(), importUserPriceListTypeObject.getExpr(), key));
        query.and(isDataPriceListType.getExpr(key).getWhere());
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(context.getSession());

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

    private Boolean showField(List<UserPriceListDetail> data, String fieldName) {
        try {
            Field field = UserPriceListDetail.class.getField(fieldName);

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

