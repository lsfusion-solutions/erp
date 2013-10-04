package lsfusion.erp.integration.universal;

import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.stock.BarcodeUtils;
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
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
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

        this.itemArticleLM = (ScriptingLogicsModule) context.getBL().getModule("ItemArticle");

        try {

            DataObject userPriceListObject = context.getDataKeyValue(userPriceListInterface);

            ObjectValue importUserPriceListTypeObject = LM.findLCPByCompoundName("importUserPriceListTypeUserPriceList").readClasses(context, userPriceListObject);

            if (!(importUserPriceListTypeObject instanceof NullValue)) {

                String fileExtension = (String) LM.findLCPByCompoundName("captionImportUserPriceListTypeFileExtensionImportUserPriceListType").read(context, importUserPriceListTypeObject);
                String itemKeyType = (String) LM.findLCPByCompoundName("nameImportUserPriceListKeyTypeImportUserPriceListType").read(context, importUserPriceListTypeObject);
                String[] parts = itemKeyType == null ? null : itemKeyType.split("\\.");
                itemKeyType = parts == null ? null : parts[parts.length - 1].trim();
                String csvSeparator = (String) LM.findLCPByCompoundName("separatorImportUserPriceListType").read(context, importUserPriceListTypeObject);
                csvSeparator = csvSeparator == null ? ";" : csvSeparator;
                Integer startRow = (Integer) LM.findLCPByCompoundName("startRowImportUserPriceListType").read(context, importUserPriceListTypeObject);
                startRow = startRow == null || startRow.equals(0) ? 1 : startRow;
                String dateRowString = (String) LM.findLCPByCompoundName("dateRowImportUserPriceListType").read(context, importUserPriceListTypeObject);
                Integer dateRow;
                try {
                    dateRow = dateRowString == null ? null : Integer.parseInt(dateRowString);
                } catch (Exception e) {
                    dateRow = null;
                }
                String dateColumnString = (String) LM.findLCPByCompoundName("dateColumnImportUserPriceListType").read(context, importUserPriceListTypeObject);
                Integer dateColumn;
                try {
                    dateColumn = dateColumnString == null ? null : Integer.parseInt(dateColumnString);
                } catch (Exception e) {
                    dateColumn = null;
                }

                ObjectValue company = LM.findLCPByCompoundName("companyImportUserPriceListType").readClasses(context, (DataObject) importUserPriceListTypeObject);
                DataObject companyObject = company instanceof NullValue ? null : (DataObject) company;

                Map<String, String[]> importColumns = readImportColumns(context, LM, importUserPriceListTypeObject);
                Map<String, String[]> importPriceColumns = readPriceImportColumns(context, LM, importUserPriceListTypeObject);

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension.trim() + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            importData(context, userPriceListObject, importColumns, importPriceColumns, file,
                                    fileExtension.trim(), startRow, dateRow, dateColumn, csvSeparator, itemKeyType, companyObject);

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

    public boolean importData(ExecutionContext context, DataObject userPriceListObject,
                              Map<String, String[]> importColumns, Map<String, String[]> importPriceColumns,
                              byte[] file, String fileExtension, Integer startRow, Integer dateRow, Integer dateColumn,
                              String csvSeparator, String itemKeyType, DataObject companyObject)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException {

        List<UserPriceListDetail> userPriceListDetailList;

        if (fileExtension.equals("DBF"))
            userPriceListDetailList = importUserPriceListsFromDBF(file, importColumns, importPriceColumns, startRow);
        else if (fileExtension.equals("XLS"))
            userPriceListDetailList = importUserPriceListsFromXLS(file, importColumns, importPriceColumns, startRow, dateRow, dateColumn);
        else if (fileExtension.equals("XLSX"))
            userPriceListDetailList = importUserPriceListsFromXLSX(file, importColumns, importPriceColumns, startRow, dateRow, dateColumn);
        else if (fileExtension.equals("CSV"))
            userPriceListDetailList = importUserPriceListsFromCSV(file, importColumns, importPriceColumns, startRow, dateRow, dateColumn, csvSeparator);
        else
            userPriceListDetailList = null;

        return importUserPriceListDetails(context, userPriceListDetailList, companyObject, userPriceListObject, itemKeyType);

    }

    private boolean importUserPriceListDetails(ExecutionContext context, List<UserPriceListDetail> userPriceListDetailsList,
                                               DataObject companyObject, DataObject userPriceListObject, String itemKeyType) throws ScriptingErrorLog.SemanticErrorException, SQLException {

        if (userPriceListDetailsList != null) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(userPriceListDetailsList.size());

            if (companyObject != null) {
                props.add(new ImportProperty(companyObject, LM.findLCPByCompoundName("companyUserPriceList").getMapping(userPriceListObject)));
            }

            ImportField idItemField = new ImportField(LM.findLCPByCompoundName("idItem"));
            fields.add(idItemField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(userPriceListDetailsList.get(i).idItem);

            ImportField idBarcodeSkuField = new ImportField(LM.findLCPByCompoundName("idBarcodeSku"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Barcode"),
                    LM.findLCPByCompoundName("extBarcodeId").getMapping(idBarcodeSkuField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idBarcodeSkuField, LM.findLCPByCompoundName("idBarcode").getMapping(barcodeKey)));
            props.add(new ImportProperty(idBarcodeSkuField, LM.findLCPByCompoundName("extIdBarcode").getMapping(barcodeKey)));
            fields.add(idBarcodeSkuField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(userPriceListDetailsList.get(i).barcodeItem);

            String iGroupAggr = (itemKeyType == null || itemKeyType.equals("item")) ? "itemId" : "skuIdBarcode";
            ImportField iField = (itemKeyType == null || itemKeyType.equals("item")) ? idItemField : idBarcodeSkuField;
            ImportKey<?> itemKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Item"),
                    LM.findLCPByCompoundName(iGroupAggr).getMapping(iField));
            keys.add(itemKey);

            if (itemArticleLM != null && showField(userPriceListDetailsList, "articleItem")) {
                ImportField idArticleItemField = new ImportField(itemArticleLM.findLCPByCompoundName("idArticleItem"));
                ImportKey<?> articleKey = new ImportKey((ConcreteCustomClass) itemArticleLM.findClassByCompoundName("Article"),
                        itemArticleLM.findLCPByCompoundName("articleId").getMapping(idArticleItemField));
                keys.add(articleKey);
                props.add(new ImportProperty(idArticleItemField, itemArticleLM.findLCPByCompoundName("idArticle").getMapping(articleKey)));
                props.add(new ImportProperty(idArticleItemField, itemArticleLM.findLCPByCompoundName("articleItem").getMapping(itemKey),
                        itemArticleLM.object(itemArticleLM.findClassByCompoundName("Article")).getMapping(articleKey)));
                fields.add(idArticleItemField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).articleItem);
            }

            if (showField(userPriceListDetailsList, "idUserPriceList")) {
                ImportField idUserPriceListField = new ImportField(LM.findLCPByCompoundName("idUserPriceList"));
                props.add(new ImportProperty(idUserPriceListField, LM.findLCPByCompoundName("numberUserPriceList").getMapping(userPriceListObject)));
                fields.add(idUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).idUserPriceList);
            }

            ImportField idUserPriceListDetailField = new ImportField(LM.findLCPByCompoundName("idUserPriceListDetail"));
            ImportKey<?> userPriceListDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserPriceListDetail"),
                    LM.findLCPByCompoundName("userPriceListDetailIdUserPriceList").getMapping(idUserPriceListDetailField, userPriceListObject));
            keys.add(userPriceListDetailKey);
            props.add(new ImportProperty(userPriceListObject, LM.findLCPByCompoundName("userPriceListUserPriceListDetail").getMapping(userPriceListDetailKey)));
            props.add(new ImportProperty(idUserPriceListDetailField, LM.findLCPByCompoundName("idUserPriceListDetail").getMapping(userPriceListDetailKey)));
            props.add(new ImportProperty(idItemField, LM.findLCPByCompoundName("idItem").getMapping(itemKey)));
            props.add(new ImportProperty(iField, LM.findLCPByCompoundName("skuUserPriceListDetail").getMapping(userPriceListDetailKey),
                    LM.object(LM.findClassByCompoundName("Sku")).getMapping(itemKey)));
            props.add(new ImportProperty(iField, LM.findLCPByCompoundName("skuBarcode").getMapping(barcodeKey),
                    LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));
            fields.add(idUserPriceListDetailField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(userPriceListDetailsList.get(i).idUserPriceListDetail);

            if (showField(userPriceListDetailsList, "captionItem")) {
                ImportField captionItemField = new ImportField(LM.findLCPByCompoundName("captionItem"));
                props.add(new ImportProperty(captionItemField, LM.findLCPByCompoundName("captionItem").getMapping(itemKey)));
                fields.add(captionItemField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).captionItem);
            }

            if (showField(userPriceListDetailsList, "idUOMItem")) {
                ImportField idUOMField = new ImportField(LM.findLCPByCompoundName("idUOM"));
                ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UOM"),
                        LM.findLCPByCompoundName("UOMId").getMapping(idUOMField));
                keys.add(UOMKey);
                props.add(new ImportProperty(idUOMField, LM.findLCPByCompoundName("idUOM").getMapping(UOMKey)));
                props.add(new ImportProperty(idUOMField, LM.findLCPByCompoundName("shortNameUOM").getMapping(UOMKey)));
                props.add(new ImportProperty(idUOMField, LM.findLCPByCompoundName("UOMItem").getMapping(itemKey),
                        LM.object(LM.findClassByCompoundName("UOM")).getMapping(UOMKey)));
                fields.add(idUOMField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).idUOMItem);
            }

            ImportField idDataPriceListTypeField = new ImportField(LM.findLCPByCompoundName("idDataPriceListType"));
            ImportKey<?> dataPriceListTypeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("DataPriceListType"),
                    LM.findLCPByCompoundName("dataPriceListTypeId").getMapping(idDataPriceListTypeField));
            dataPriceListTypeKey.skipKey = true;
            keys.add(dataPriceListTypeKey);
            fields.add(idDataPriceListTypeField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(userPriceListDetailsList.get(i).priceType);

            ImportField pricePriceListDetailDataPriceListTypeField = new ImportField(LM.findLCPByCompoundName("pricePriceListDetailDataPriceListType"));
            props.add(new ImportProperty(pricePriceListDetailDataPriceListTypeField, LM.findLCPByCompoundName("pricePriceListDetailDataPriceListType").getMapping(userPriceListDetailKey, dataPriceListTypeKey)));
            fields.add(pricePriceListDetailDataPriceListTypeField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(userPriceListDetailsList.get(i).price);

            ImportField inPriceListDataPriceListTypeField = new ImportField(LM.findLCPByCompoundName("inPriceListDataPriceListType"));
            props.add(new ImportProperty(inPriceListDataPriceListTypeField, LM.findLCPByCompoundName("inPriceListDataPriceListType").getMapping(userPriceListObject, dataPriceListTypeKey)));
            fields.add(inPriceListDataPriceListTypeField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(true);

            ImportField showPriceListPriceListTypeField = new ImportField(LM.findLCPByCompoundName("showPriceListPriceListType"));
            props.add(new ImportProperty(showPriceListPriceListTypeField, LM.findLCPByCompoundName("showPriceListPriceListType").getMapping(userPriceListObject, dataPriceListTypeKey)));
            fields.add(showPriceListPriceListTypeField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add(true);

            ImportField shortNameCurrencyField = new ImportField(LM.findLCPByCompoundName("shortNameCurrency"));
            ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Currency"),
                    LM.findLCPByCompoundName("currencyShortName").getMapping(shortNameCurrencyField));
            keys.add(currencyKey);
            props.add(new ImportProperty(shortNameCurrencyField, LM.findLCPByCompoundName("currencyUserPriceList").getMapping(userPriceListObject),
                    LM.object(LM.findClassByCompoundName("Currency")).getMapping(currencyKey)));
            fields.add(shortNameCurrencyField);
            for (int i = 0; i < userPriceListDetailsList.size(); i++)
                data.get(i).add("BLR");

            if (showField(userPriceListDetailsList, "date")) {
                ImportField dateUserPriceListField = new ImportField(LM.findLCPByCompoundName("dateUserPriceList"));
                props.add(new ImportProperty(dateUserPriceListField, LM.findLCPByCompoundName("dateUserPriceList").getMapping(userPriceListObject)));
                fields.add(dateUserPriceListField);
                for (int i = 0; i < userPriceListDetailsList.size(); i++)
                    data.get(i).add(userPriceListDetailsList.get(i).date);
            }

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.getSession();
            session.sql.pushVolatileStats(null);
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            String result = session.applyMessage(context.getBL());
            session.sql.popVolatileStats(null);
            session.close();

            LM.findLAPByCompoundName("formRefresh").execute(context);

            return result == null;
        }
        return false;
    }

    private List<UserPriceListDetail> importUserPriceListsFromXLS(byte[] importFile, Map<String, String[]> importColumns,
                                                                  Map<String, String[]> importPriceColumns, Integer startRow,
                                                                  Integer dateRow, Integer dateColumn) throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<UserPriceListDetail> userUserPriceListDetailList = new ArrayList<UserPriceListDetail>();

        HSSFWorkbook Wb = new HSSFWorkbook(new ByteArrayInputStream(importFile));
        HSSFSheet sheet = Wb.getSheetAt(0);

        Date date = (dateRow == null || dateColumn == null) ? null : getXLSDateFieldValue(sheet, dateRow - 1, dateColumn - 1, null);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {
            String idUserPriceList = getXLSFieldValue(sheet, i, getColumnNumbers(importColumns.get("idUserPriceList")));
            String idItem = getXLSFieldValue(sheet, i, getColumnNumbers(importColumns.get("idItem")));
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getXLSFieldValue(sheet, i, getColumnNumbers(importColumns.get("barcodeItem"))));
            String articleItem = getXLSFieldValue(sheet, i, getColumnNumbers(importColumns.get("articleItem")));
            String captionItem = getXLSFieldValue(sheet, i, getColumnNumbers(importColumns.get("captionItem")));
            String idUOMItem = getXLSFieldValue(sheet, i, getColumnNumbers(importColumns.get("idUOMItem")));
            String idUserPriceListDetail = (idItem == null ? "" : idItem) + "_" + (barcodeItem == null ? "" : barcodeItem);
            if (!idUserPriceListDetail.equals("_"))
                for (Map.Entry<String, String[]> entry : importPriceColumns.entrySet()) {
                    BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, getColumnNumbers(entry.getValue()));
                    userUserPriceListDetailList.add(new UserPriceListDetail(idUserPriceListDetail, idUserPriceList,
                            idItem, barcodeItem, articleItem, captionItem, idUOMItem, date, entry.getKey(), price));
                }
        }

        return userUserPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromCSV(byte[] importFile, Map<String, String[]> importColumns, Map<String, String[]> importPriceColumns, Integer startRow,
                                                                  Integer dateRow, Integer dateColumn, String csvSeparator) throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile)));
        String line;
        int count = 0;

        while ((line = br.readLine()) != null) {

            count++;

            if (count >= startRow) {

                String[] values = line.split(csvSeparator);

                Date date = (dateRow == null || dateColumn == null || (dateRow - 1) != count) ? null : getCSVDateFieldValue(values, dateColumn - 1, null);

                String idUserPriceList = getCSVFieldValue(values, getColumnNumbers(importColumns.get("idUserPriceList")));
                String barcodeItem = BarcodeUtils.convertBarcode12To13(getCSVFieldValue(values, getColumnNumbers(importColumns.get("barcodeItem"))));
                String articleItem = getCSVFieldValue(values, getColumnNumbers(importColumns.get("articleItem")));
                String idItem = getCSVFieldValue(values, getColumnNumbers(importColumns.get("idItem")));
                String captionItem = getCSVFieldValue(values, getColumnNumbers(importColumns.get("captionItem")));
                String idUOMItem = getCSVFieldValue(values, getColumnNumbers(importColumns.get("idUOMItem")));
                String idUserPriceListDetail = (idItem == null ? "" : idItem) + "_" + (barcodeItem == null ? "" : barcodeItem);
                for (Map.Entry<String, String[]> entry : importPriceColumns.entrySet()) {
                    BigDecimal price = getCSVBigDecimalFieldValue(values, getColumnNumbers(entry.getValue()));
                    userPriceListDetailList.add(new UserPriceListDetail(idUserPriceListDetail, idUserPriceList,
                            idItem, barcodeItem, articleItem, captionItem, idUOMItem, date, entry.getKey(), price));
                }
            }
        }

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromXLSX(byte[] importFile, Map<String, String[]> importColumns,
                                                                   Map<String, String[]> importPriceColumns,
                                                                   Integer startRow, Integer dateRow, Integer dateColumn) throws BiffException, IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));
        XSSFSheet sheet = Wb.getSheetAt(0);

        Date date = (dateRow == null || dateColumn == null) ? null : getXLSXDateFieldValue(sheet, dateRow - 1, dateColumn - 1, null);

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {

            String idUserPriceList = getXLSXFieldValue(sheet, i, getColumnNumbers(importColumns.get("idUserPriceList")));
            String idItem = getXLSXFieldValue(sheet, i, getColumnNumbers(importColumns.get("idItem")));
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getXLSXFieldValue(sheet, i, getColumnNumbers(importColumns.get("barcodeItem"))));
            String articleItem = getXLSXFieldValue(sheet, i, getColumnNumbers(importColumns.get("articleItem")));
            String captionItem = getXLSXFieldValue(sheet, i, getColumnNumbers(importColumns.get("captionItem")));
            String idUOMItem = getXLSXFieldValue(sheet, i, getColumnNumbers(importColumns.get("idUOMItem")));
            String idUserPriceListDetail = (idItem == null ? "" : idItem) + "_" + (barcodeItem == null ? "" : barcodeItem);
            for (Map.Entry<String, String[]> entry : importPriceColumns.entrySet()) {
                BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, getColumnNumbers(entry.getValue()));
                userPriceListDetailList.add(new UserPriceListDetail(idUserPriceListDetail, idUserPriceList,
                        idItem, barcodeItem, articleItem, captionItem, idUOMItem, date, entry.getKey(), price));
            }
        }

        return userPriceListDetailList;
    }

    private List<UserPriceListDetail> importUserPriceListsFromDBF(byte[] importFile, Map<String, String[]> importColumns, Map<String, String[]> importPriceColumns, Integer startRow) throws IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException {

        List<UserPriceListDetail> userPriceListDetailList = new ArrayList<UserPriceListDetail>();

        File tempFile = File.createTempFile("dutiesTNVED", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);
        DBF file = new DBF(tempFile.getPath());
        int totalRecordCount = file.getRecordCount();

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String idUserPriceList = getDBFFieldValue(file, importColumns.get("idUserPriceList"));
            String barcodeItem = BarcodeUtils.convertBarcode12To13(getDBFFieldValue(file, importColumns.get("barcodeItem")));
            String articleItem = getDBFFieldValue(file, importColumns.get("articleItem"));
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"));
            String captionItem = getDBFFieldValue(file, importColumns.get("captionItem"));
            String idUOMItem = getDBFFieldValue(file, importColumns.get("idUOMItem"));
            String idUserPriceListDetail = (idItem == null ? "" : idItem) + "_" + (barcodeItem == null ? "" : barcodeItem);
            for (Map.Entry<String, String[]> entry : importPriceColumns.entrySet()) {
                BigDecimal price = getDBFBigDecimalFieldValue(file, entry.getValue());
                userPriceListDetailList.add(new UserPriceListDetail(idUserPriceListDetail, idUserPriceList,
                        idItem, barcodeItem, articleItem, captionItem, idUOMItem, null, entry.getKey(), price));
            }
        }

        file.close();

        return userPriceListDetailList;
    }

    protected static Map<String, String[]> readImportColumns(ExecutionContext context, ScriptingLogicsModule LM, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException {

        Map<String, String[]> importColumns = new HashMap<String, String[]>();

        LCP<?> isImportTypeDetail = LM.is(LM.findClassByCompoundName("ImportUserPriceListTypeDetail"));
        ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isImportTypeDetail.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        query.addProperty("staticName", LM.findLCPByCompoundName("staticName").getExpr(context.getModifier(), key));
        query.addProperty("indexImportUserPriceListTypeImportUserPriceListTypeDetail", LM.findLCPByCompoundName("indexImportUserPriceListTypeImportUserPriceListTypeDetail").getExpr(context.getModifier(), importTypeObject.getExpr(), key));
        query.and(isImportTypeDetail.getExpr(key).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context.getSession().sql);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String[] field = ((String) entry.get("staticName")).trim().split("\\.");
            String indexes = (String) entry.get("indexImportUserPriceListTypeImportUserPriceListTypeDetail");
            if (indexes != null) {
                String[] splittedIndexes = indexes.split("\\+");
                for (int i = 0; i < splittedIndexes.length; i++)
                    splittedIndexes[i] = splittedIndexes[i].trim();
                importColumns.put(field[field.length - 1], splittedIndexes);
            }
        }
        return importColumns.isEmpty() ? null : importColumns;
    }

    protected static Map<String, String[]> readPriceImportColumns(ExecutionContext context, ScriptingLogicsModule LM, ObjectValue importUserPriceListTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException {

        Map<String, String[]> importColumns = new HashMap<String, String[]>();

        LCP<?> isDataPriceListType = LM.is(LM.findClassByCompoundName("DataPriceListType"));
        ImRevMap<Object, KeyExpr> keys = (ImRevMap<Object, KeyExpr>) isDataPriceListType.getMapKeys();
        KeyExpr key = keys.singleValue();
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        query.addProperty("idDataPriceListType", LM.findLCPByCompoundName("idDataPriceListType").getExpr(context.getModifier(), key));
        query.addProperty("indexImportUserPriceListTypeDataPriceListType", LM.findLCPByCompoundName("indexImportUserPriceListTypeDataPriceListType").getExpr(context.getModifier(), importUserPriceListTypeObject.getExpr(), key));
        query.and(isDataPriceListType.getExpr(key).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context.getSession().sql);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String idDataPriceListType = (String) entry.get("idDataPriceListType");
            String indexes = (String) entry.get("indexImportUserPriceListTypeDataPriceListType");
            if (indexes != null) {
                String[] splittedIndexes = indexes.split("\\+");
                for (int i = 0; i < splittedIndexes.length; i++)
                    splittedIndexes[i] = splittedIndexes[i].trim();
                importColumns.put(idDataPriceListType, splittedIndexes);
            }
        }
        return importColumns.isEmpty() ? null : importColumns;
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

