package lsfusion.erp.integration.universal.emailorder;

import com.google.common.base.Throwables;
import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.ERPLoggers;
import lsfusion.erp.integration.DefaultImportXLSXAction;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.logics.classes.user.CustomClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.physics.dev.integration.service.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImportEmailOrderAction extends DefaultImportXLSXAction {
    public ImportEmailOrderAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        Map<DataObject, List<Object>> attachmentMap = readAttachmentMap(context);

        for (Map.Entry<DataObject, List<Object>> attachment : attachmentMap.entrySet()) {
            RawFileData file = (RawFileData) attachment.getValue().get(0);
            String fileName = (String) attachment.getValue().get(1);
            try {
                importOrder(context, file);
                finishImportOrder(context, attachment.getKey());
            } catch (ScriptingErrorLog.SemanticErrorException | IOException e) {
                ERPLoggers.importLogger.error("Импорт из почты: ошибка при чтении файла" + fileName);
            }

        }

    }

    private Map<DataObject, List<Object>> readAttachmentMap(ExecutionContext context) throws SQLException, SQLHandledException {

        Map<DataObject, List<Object>> attachmentMap = new HashMap<>();

        try {

            ObjectValue accountObject = findProperty("importEmailOrderAccount[]").readClasses(context);
            if (accountObject instanceof NullValue) {
                ERPLoggers.importLogger.error("Импорт из почты: не задан почтовый аккаунт");
            } else {
                KeyExpr emailExpr = new KeyExpr("email");
                KeyExpr attachmentEmailExpr = new KeyExpr("attachmentEmail");
                ImRevMap<Object, KeyExpr> emailKeys = MapFact.toRevMap("email", emailExpr, "attachmentEmail", attachmentEmailExpr);

                QueryBuilder<Object, Object> emailQuery = new QueryBuilder<>(emailKeys);
                emailQuery.addProperty("fileAttachmentEmail", findProperty("file[AttachmentEmail]").getExpr(attachmentEmailExpr));
                emailQuery.addProperty("nameAttachmentEmail", findProperty("filename[AttachmentEmail]").getExpr(attachmentEmailExpr));

                emailQuery.and(findProperty("email[AttachmentEmail]").getExpr(attachmentEmailExpr).compare(emailExpr, Compare.EQUALS));
                emailQuery.and(findProperty("account[Email]").getExpr(emailExpr).compare(accountObject.getExpr(), Compare.EQUALS));
                emailQuery.and(findProperty("notImportedOrder[AttachmentEmail]").getExpr(attachmentEmailExpr).getWhere());
                emailQuery.and(findProperty("file[AttachmentEmail]").getExpr(attachmentEmailExpr).getWhere());

                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> emailResult = emailQuery.executeClasses(context);

                for (int j = 0, sizej = emailResult.size(); j < sizej; j++) {
                    ImMap<Object, ObjectValue> emailEntryValue = emailResult.getValue(j);
                    DataObject attachmentEmailObject = emailResult.getKey(j).get("attachmentEmail");
                    RawFileData fileAttachment = ((FileData) emailEntryValue.get("fileAttachmentEmail").getValue()).getRawFile();
                    String nameAttachmentEmail = trim((String) emailEntryValue.get("nameAttachmentEmail").getValue());
                    if (nameAttachmentEmail != null) {
                        if (nameAttachmentEmail.toLowerCase().endsWith(".xls") || nameAttachmentEmail.toLowerCase().endsWith(".xlsx")) {
                            attachmentMap.put(attachmentEmailObject, Arrays.asList(fileAttachment, nameAttachmentEmail));
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
        return attachmentMap;
    }

    private void importOrder(ExecutionContext context, RawFileData file) throws IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Integer firstRow = (Integer) findProperty("importEmailOrderFirstRow[]").read(context);
        String numberCell = (String) findProperty("importEmailOrderNumberCell[]").read(context);
        String quantityColumn = (String) findProperty("importEmailOrderQuantityColumn[]").read(context);

        if (firstRow == null || !notNullNorEmpty(numberCell) || !notNullNorEmpty(quantityColumn)) {
            ERPLoggers.importLogger.error("Импорт из почты: не все параметры заданы (начинать со строки, ячейка номера заказа или колонка количества)");
        } else {

            List<List<Object>> data = importOrderFromXLSX(file, firstRow, numberCell, quantityColumn);

            if (data != null) {

                List<ImportProperty<?>> props = new ArrayList<>();
                List<ImportField> fields = new ArrayList<>();
                List<ImportKey<?>> keys = new ArrayList<>();

                ImportField seriesNumberUserOrderField = new ImportField(findProperty("seriesNumber[UserOrder]"));
                ImportKey<?> userOrderKey = new ImportKey((CustomClass) findClass("Purchase.UserOrder"),
                        findProperty("order[BPSTRING[18]]").getMapping(seriesNumberUserOrderField));
                userOrderKey.skipKey = true;
                keys.add(userOrderKey);
                fields.add(seriesNumberUserOrderField);

                ImportField isConfirmedOrderField = new ImportField(findProperty("isConfirmed[Purchase.Order]"));
                props.add(new ImportProperty(isConfirmedOrderField, findProperty("isConfirmed[Purchase.Order]").getMapping(userOrderKey)));
                fields.add(isConfirmedOrderField);

                ImportField indexUserOrderDetailField = new ImportField(findProperty("index[UserOrderDetail]"));
                ImportKey<?> userOrderDetailKey = new ImportKey((CustomClass) findClass("Purchase.UserOrderDetail"),
                        findProperty("orderDetail[INTEGER,STRING[18]]").getMapping(indexUserOrderDetailField, seriesNumberUserOrderField));
                userOrderDetailKey.skipKey = true;
                keys.add(userOrderDetailKey);
                props.add(new ImportProperty(seriesNumberUserOrderField, findProperty("userOrder[UserOrderDetail]").getMapping(userOrderDetailKey),
                        object(findClass("Purchase.UserOrder")).getMapping(userOrderKey)));
                fields.add(indexUserOrderDetailField);

                ImportField quantityUserOrderDetailField = new ImportField(findProperty("quantity[UserOrderDetail]"));
                props.add(new ImportProperty(quantityUserOrderDetailField, findProperty("quantity[UserOrderDetail]").getMapping(userOrderDetailKey)));
                fields.add(quantityUserOrderDetailField);

                ImportTable table = new ImportTable(fields, data);

                try(ExecutionContext.NewSession newContext = context.newSession()) {
                    IntegrationService service = new IntegrationService(newContext, table, keys, props);
                    service.synchronize(true, false);
                    newContext.apply();
                }
            }
        }
    }

    private void finishImportOrder(ExecutionContext context, DataObject orderObject) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try(ExecutionContext.NewSession newContext = context.newSession()) {
            findProperty("importedOrder[AttachmentEmail]").change(true, newContext, (DataObject) orderObject);
            newContext.apply();
        }
    }

    private List<List<Object>> importOrderFromXLSX(RawFileData file, Integer firstRow, String numberCell, String quantityColumnValue) throws IOException {

        List<List<Object>> result = new ArrayList<>();

        Pattern p = Pattern.compile("(\\w+)(\\d+)");
        Matcher m = p.matcher(numberCell);
        if (!m.matches()) {
            ERPLoggers.importLogger.error("Импорт из почты: некорректно задана ячейка номера заказа");
        } else {
            Integer numberColumn = getCellIndex(m.group(1));
            Integer numberRow = Integer.parseInt(m.group(2)) - 1;

            Integer quantityColumn = getCellIndex(quantityColumnValue);

            XSSFWorkbook Wb = new XSSFWorkbook(file.getInputStream());
            XSSFSheet sheet = Wb.getSheetAt(0);

            int recordCount = sheet.getLastRowNum();

            String numberOrder = getXLSXFieldValue(sheet, numberRow, numberColumn);
            if (numberOrder != null) {
                for (int i = firstRow - 1; i <= recordCount; i++) {
                    BigDecimal index;
                    index = getXLSXBigDecimalFieldValue(sheet, i, B);
                    if (index != null) {
                        BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, quantityColumn);
                        if (quantity != null)
                            result.add(Arrays.asList(numberOrder, true, index.intValue(), quantity));
                    }
                }
            }
        }
        return result;
    }

    private Integer getCellIndex(String column) {
        Integer result = 0;
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < column.length(); i++) {
            result += letters.indexOf(column.charAt(i)) + i * 26;
        }
        return result;
    }
}
