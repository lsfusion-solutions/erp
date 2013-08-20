package lsfusion.erp.integration;

import jxl.read.biff.BiffException;
import lsfusion.base.IOUtils;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
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
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class ImportPurchaseInvoiceActionProperty extends ImportDocumentActionProperty {
    private final ClassPropertyInterface userInvoiceInterface;

    public ImportPurchaseInvoiceActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Purchase.UserInvoice"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        userInvoiceInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {

            DataObject userInvoiceObject = context.getDataKeyValue(userInvoiceInterface);

            ObjectValue importTypeObject = LM.findLCPByCompoundName("importTypeUserInvoice").readClasses(context, userInvoiceObject);

            if (!(importTypeObject instanceof NullValue)) {

                String fileExtension = (String) LM.findLCPByCompoundName("captionImportTypeFileExtensionImportType").read(context, importTypeObject);
                Boolean byBarcode = LM.findLCPByCompoundName("byBarcodeImportType").read(context, importTypeObject) != null;
                String csvSeparator = (String) LM.findLCPByCompoundName("separatorImportType").read(context, importTypeObject);
                csvSeparator = csvSeparator == null ? ";" : csvSeparator;
                Integer startRow = (Integer) LM.findLCPByCompoundName("startRowImportType").read(context, importTypeObject);
                startRow = startRow == null ? 1 : startRow;

                Map<String, String> importColumns = readImportColumns(context, importTypeObject);

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension.trim() + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {

                            importUserInvoices(context, userInvoiceObject, importColumns, file, fileExtension.trim(), startRow, csvSeparator, byBarcode);

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

    protected void importUserInvoices(ExecutionContext context, DataObject userInvoiceObject, Map<String, String> importColumns,
                                      byte[] file, String fileExtension, Integer startRow, String csvSeparator, Boolean byBarcode)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, ParseException, BiffException {

        List<List<Object>> userInvoiceDetailsList;

        if (fileExtension.equals("DBF"))
            userInvoiceDetailsList = importUserInvoicesFromDBF(file, importColumns, startRow, (Integer) userInvoiceObject.object);
        else if (fileExtension.equals("XLS"))
            userInvoiceDetailsList = importUserInvoicesFromXLS(file, importColumns, startRow, (Integer) userInvoiceObject.object);
        else if (fileExtension.equals("XLSX"))
            userInvoiceDetailsList = importUserInvoicesFromXLSX(file, importColumns, startRow, (Integer) userInvoiceObject.object);
        else if (fileExtension.equals("CSV"))
            userInvoiceDetailsList = importUserInvoicesFromCSV(file, importColumns, startRow, csvSeparator, (Integer) userInvoiceObject.object);
        else
            userInvoiceDetailsList = null;

        if (userInvoiceDetailsList != null) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            ImportField numberUserInvoiceField = new ImportField(LM.findLCPByCompoundName("numberObject"));
            props.add(new ImportProperty(numberUserInvoiceField, LM.findLCPByCompoundName("numberObject").getMapping(userInvoiceObject)));
            fields.add(numberUserInvoiceField);

            ImportField idUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("idUserInvoiceDetail"));
            ImportKey<?> userInvoiceDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Purchase.UserInvoiceDetail"),
                    LM.findLCPByCompoundName("userInvoiceDetailId").getMapping(idUserInvoiceDetailField));
            keys.add(userInvoiceDetailKey);
            props.add(new ImportProperty(idUserInvoiceDetailField, LM.findLCPByCompoundName("idUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            props.add(new ImportProperty(userInvoiceObject, LM.findLCPByCompoundName("Purchase.userInvoiceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(idUserInvoiceDetailField);

            ImportField idItemField = new ImportField(LM.findLCPByCompoundName(byBarcode ? "idBarcodeSku" : "idItem"));
            ImportKey<?> itemKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Item"),
                    LM.findLCPByCompoundName(byBarcode ? "skuIdBarcode" : "itemId").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, LM.findLCPByCompoundName("Purchase.skuInvoiceDetail").getMapping(userInvoiceDetailKey),
                    LM.object(LM.findClassByCompoundName("Sku")).getMapping(itemKey)));
            fields.add(idItemField);

            ImportField quantityUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.quantityUserInvoiceDetail"));
            props.add(new ImportProperty(quantityUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.quantityUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(quantityUserInvoiceDetailField);

            ImportField priceUserInvoiceDetail = new ImportField(LM.findLCPByCompoundName("Purchase.priceUserInvoiceDetail"));
            props.add(new ImportProperty(priceUserInvoiceDetail, LM.findLCPByCompoundName("Purchase.priceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(priceUserInvoiceDetail);

            ImportField sumUserInvoiceDetail = new ImportField(LM.findLCPByCompoundName("Purchase.sumUserInvoiceDetail"));
            props.add(new ImportProperty(sumUserInvoiceDetail, LM.findLCPByCompoundName("Purchase.sumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(sumUserInvoiceDetail);

            ImportField valueVATUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.valueVATUserInvoiceDetail"));
            ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Range"),
                    LM.findLCPByCompoundName("valueCurrentVATDefaultValue").getMapping(valueVATUserInvoiceDetailField));
            keys.add(VATKey);
            props.add(new ImportProperty(valueVATUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.VATUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                    LM.object(LM.findClassByCompoundName("Range")).getMapping(VATKey)));
            fields.add(valueVATUserInvoiceDetailField);

            ImportField VATSumUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.VATSumUserInvoiceDetail"));
            props.add(new ImportProperty(VATSumUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.VATSumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(VATSumUserInvoiceDetailField);

            ImportField invoiceSumUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.invoiceSumUserInvoiceDetail"));
            props.add(new ImportProperty(invoiceSumUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.invoiceSumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
            fields.add(invoiceSumUserInvoiceDetailField);

            ImportTable table = new ImportTable(fields, userInvoiceDetailsList);

            DataSession session = context.getSession();
            session.sql.pushVolatileStats(null);
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context.getBL());
            session.sql.popVolatileStats(null);
            session.close();

        }
    }

    private List<List<Object>> importUserInvoicesFromXLS(byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer userInvoiceObject) throws BiffException, IOException, ParseException {

        List<List<Object>> userInvoiceDetailList = new ArrayList<List<Object>>();

        HSSFWorkbook Wb = new HSSFWorkbook(new ByteArrayInputStream(importFile));

        HSSFSheet sheet = Wb.getSheetAt(0);

        Integer numberUserInvoiceColumn = getColumnNumber(importColumns.get("numberDocument"));
        Integer idItemColumn = getColumnNumber(importColumns.get("idItem"));
        Integer quantityColumn = getColumnNumber(importColumns.get("quantity"));
        Integer priceColumn = getColumnNumber(importColumns.get("price"));
        Integer sumColumn = getColumnNumber(importColumns.get("sum"));
        Integer VATColumn = getColumnNumber(importColumns.get("VAT"));
        Integer VATSumColumn = getColumnNumber(importColumns.get("VATSum"));
        Integer invoiceSumColumn = getColumnNumber(importColumns.get("invoiceSum"));

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {
            String numberUserInvoice = getXLSFieldValue(sheet, i, numberUserInvoiceColumn, null);
            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String idItem = getXLSFieldValue(sheet, i, idItemColumn, null);
            BigDecimal quantity = getXLSBigDecimalFieldValue(sheet, i, quantityColumn, null);
            BigDecimal price = getXLSBigDecimalFieldValue(sheet, i, priceColumn, null);
            BigDecimal sum = getXLSBigDecimalFieldValue(sheet, i, sumColumn, null);
            BigDecimal VAT = getXLSBigDecimalFieldValue(sheet, i, VATColumn, null);
            BigDecimal VATSum = getXLSBigDecimalFieldValue(sheet, i, VATSumColumn, null);
            BigDecimal invoiceSum = getXLSBigDecimalFieldValue(sheet, i, invoiceSumColumn, null);

            userInvoiceDetailList.add(Arrays.asList((Object) numberUserInvoice, idUserInvoiceDetail, idItem, quantity, price, sum,
                    allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));
        }

        return userInvoiceDetailList;
    }

    private List<List<Object>> importUserInvoicesFromCSV(byte[] importFile, Map<String, String> importColumns,
                                                         Integer startRow, String csvSeparator, Integer userInvoiceObject)
            throws BiffException, IOException, ParseException {

        List<List<Object>> userInvoiceDetailList = new ArrayList<List<Object>>();

        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(importFile)));
        String line;
        int count = 0;

        Integer numberUserInvoiceColumn = getColumnNumber(importColumns.get("numberDocument"));
        Integer idItemColumn = getColumnNumber(importColumns.get("idItem"));
        Integer quantityColumn = getColumnNumber(importColumns.get("quantity"));
        Integer priceColumn = getColumnNumber(importColumns.get("price"));
        Integer sumColumn = getColumnNumber(importColumns.get("sum"));
        Integer VATColumn = getColumnNumber(importColumns.get("VAT"));
        Integer VATSumColumn = getColumnNumber(importColumns.get("VATSum"));
        Integer invoiceSumColumn = getColumnNumber(importColumns.get("invoiceSum"));

        while ((line = br.readLine()) != null) {

            count++;

            if (count >= startRow) {

                String[] values = line.split(csvSeparator);

                String numberUserInvoice = getCSVFieldValue(values, numberUserInvoiceColumn, null);
                String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + count;
                String idItem = getCSVFieldValue(values, idItemColumn, null);
                BigDecimal quantity = getCSVBigDecimalFieldValue(values, quantityColumn, null);
                BigDecimal price = getCSVBigDecimalFieldValue(values, priceColumn, null);
                BigDecimal sum = getCSVBigDecimalFieldValue(values, sumColumn, null);
                BigDecimal VAT = getCSVBigDecimalFieldValue(values, VATColumn, null);
                BigDecimal VATSum = getCSVBigDecimalFieldValue(values, VATSumColumn, null);
                BigDecimal invoiceSum = getCSVBigDecimalFieldValue(values, invoiceSumColumn, null);

                userInvoiceDetailList.add(Arrays.asList((Object) numberUserInvoice, idUserInvoiceDetail, idItem, quantity, price, sum,
                        allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));

            }
        }

        return userInvoiceDetailList;
    }

    private List<List<Object>> importUserInvoicesFromXLSX(byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer userInvoiceObject) throws BiffException, IOException, ParseException {

        List<List<Object>> userInvoiceDetailList = new ArrayList<List<Object>>();

        XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(importFile));

        XSSFSheet sheet = Wb.getSheetAt(0);

        Integer numberUserInvoiceColumn = getColumnNumber(importColumns.get("numberDocument"));
        Integer idItemColumn = getColumnNumber(importColumns.get("idItem"));
        Integer quantityColumn = getColumnNumber(importColumns.get("quantity"));
        Integer priceColumn = getColumnNumber(importColumns.get("price"));
        Integer sumColumn = getColumnNumber(importColumns.get("sum"));
        Integer VATColumn = getColumnNumber(importColumns.get("VAT"));
        Integer VATSumColumn = getColumnNumber(importColumns.get("VATSum"));
        Integer invoiceSumColumn = getColumnNumber(importColumns.get("invoiceSum"));

        for (int i = startRow - 1; i <= sheet.getLastRowNum(); i++) {

            String numberUserInvoice = getXLSXFieldValue(sheet, i, numberUserInvoiceColumn, null);
            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String idItem = getXLSXFieldValue(sheet, i, idItemColumn, null);
            BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, quantityColumn, null);
            BigDecimal price = getXLSXBigDecimalFieldValue(sheet, i, priceColumn, null);
            BigDecimal sum = getXLSXBigDecimalFieldValue(sheet, i, sumColumn, null);
            BigDecimal VAT = getXLSXBigDecimalFieldValue(sheet, i, VATColumn, null);
            BigDecimal VATSum = getXLSXBigDecimalFieldValue(sheet, i, VATSumColumn, null);
            BigDecimal invoiceSum = getXLSXBigDecimalFieldValue(sheet, i, invoiceSumColumn, null);

            userInvoiceDetailList.add(Arrays.asList((Object) numberUserInvoice, idUserInvoiceDetail, idItem, quantity,
                    price, sum, allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));
        }

        return userInvoiceDetailList;
    }

    private List<List<Object>> importUserInvoicesFromDBF(byte[] importFile, Map<String, String> importColumns, Integer startRow, Integer userInvoiceObject) throws IOException, xBaseJException {

        List<List<Object>> userInvoicesList = new ArrayList<List<Object>>();

        File tempFile = File.createTempFile("dutiesTNVED", ".dbf");
        IOUtils.putFileBytes(tempFile, importFile);


        DBF file = new DBF(tempFile.getPath());

        int totalRecordCount = file.getRecordCount();

        for (int i = startRow - 1; i < totalRecordCount; i++) {

            file.read();

            String numberUserInvoice = getDBFFieldValue(file, importColumns.get("numberDocument"), "Cp866", "");
            String idUserInvoiceDetail = String.valueOf(userInvoiceObject) + i;
            String idItem = getDBFFieldValue(file, importColumns.get("idItem"), "Cp866", "");
            BigDecimal quantity = getDBFBigDecimalFieldValue(file, importColumns.get("quantity"), "Cp866", "0");
            BigDecimal price = getDBFBigDecimalFieldValue(file, importColumns.get("price"), "Cp866", null);
            BigDecimal sum = getDBFBigDecimalFieldValue(file, importColumns.get("sum"), "Cp866", null);
            BigDecimal VAT = getDBFBigDecimalFieldValue(file, importColumns.get("VAT"), "Cp866", null);
            BigDecimal VATSum = getDBFBigDecimalFieldValue(file, importColumns.get("VATSum"), "Cp866", null);
            BigDecimal invoiceSum = getDBFBigDecimalFieldValue(file, importColumns.get("invoiceSum"), "Cp866", null);

            userInvoicesList.add(Arrays.asList((Object) numberUserInvoice, idUserInvoiceDetail, idItem, quantity, price, sum, allowedVAT.contains(VAT) ? VAT : null, VATSum, invoiceSum));
        }

        return userInvoicesList;
    }
}

