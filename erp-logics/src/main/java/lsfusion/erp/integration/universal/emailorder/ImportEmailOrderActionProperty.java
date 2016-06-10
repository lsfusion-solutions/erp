package lsfusion.erp.integration.universal.emailorder;

import com.google.common.base.Throwables;
import lsfusion.base.BaseUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultImportXLSXActionProperty;
import lsfusion.interop.Compare;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.CustomClass;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImportEmailOrderActionProperty extends DefaultImportXLSXActionProperty {
    public ImportEmailOrderActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        Map<DataObject, List<Object>> attachmentMap = readAttachmentMap(context);

        for (Map.Entry<DataObject, List<Object>> attachment : attachmentMap.entrySet()) {
            byte[] file = (byte[]) attachment.getValue().get(0);
            String fileName = (String) attachment.getValue().get(1);
            try {
                importOrder(context, file);
                finishImportOrder(context, attachment.getKey());
            } catch (ScriptingErrorLog.SemanticErrorException | IOException | ParseException e) {
                ServerLoggers.importLogger.error("Импорт из почты: ошибка при чтении файла" + fileName);
            }

        }

    }

    private Map<DataObject, List<Object>> readAttachmentMap(ExecutionContext context) throws SQLException, SQLHandledException {

        Map<DataObject, List<Object>> attachmentMap = new HashMap<DataObject, List<Object>>();

        try {

            ObjectValue accountObject = findProperty("importEmailOrderAccount[]").readClasses(context);
            if (accountObject instanceof NullValue) {
                ServerLoggers.importLogger.error("Импорт из почты: не задан почтовый аккаунт");
            } else {
                KeyExpr emailExpr = new KeyExpr("email");
                KeyExpr attachmentEmailExpr = new KeyExpr("attachmentEmail");
                ImRevMap<Object, KeyExpr> emailKeys = MapFact.toRevMap((Object) "email", emailExpr, "attachmentEmail", attachmentEmailExpr);

                QueryBuilder<Object, Object> emailQuery = new QueryBuilder<Object, Object>(emailKeys);
                emailQuery.addProperty("fileAttachmentEmail", findProperty("file[AttachmentEmail]").getExpr(attachmentEmailExpr));
                emailQuery.addProperty("nameAttachmentEmail", findProperty("name[AttachmentEmail]").getExpr(attachmentEmailExpr));

                emailQuery.and(findProperty("email[AttachmentEmail]").getExpr(attachmentEmailExpr).compare(emailExpr, Compare.EQUALS));
                emailQuery.and(findProperty("account[Email]").getExpr(emailExpr).compare(accountObject.getExpr(), Compare.EQUALS));
                emailQuery.and(findProperty("notImportedOrder[AttachmentEmail]").getExpr(attachmentEmailExpr).getWhere());
                emailQuery.and(findProperty("file[AttachmentEmail]").getExpr(attachmentEmailExpr).getWhere());

                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> emailResult = emailQuery.executeClasses(context);

                for (int j = 0, sizej = emailResult.size(); j < sizej; j++) {
                    ImMap<Object, ObjectValue> emailEntryValue = emailResult.getValue(j);
                    DataObject attachmentEmailObject = emailResult.getKey(j).get("attachmentEmail");
                    byte[] fileAttachment = BaseUtils.getFile((byte[]) emailEntryValue.get("fileAttachmentEmail").getValue());
                    String nameAttachmentEmail = trim((String) emailEntryValue.get("nameAttachmentEmail").getValue());
                    if (nameAttachmentEmail != null) {
                        if (nameAttachmentEmail.toLowerCase().endsWith(".xls") || nameAttachmentEmail.toLowerCase().endsWith(".xlsx")) {
                            attachmentMap.put(attachmentEmailObject, Arrays.asList((Object) fileAttachment, nameAttachmentEmail));
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
        return attachmentMap;
    }

    private void importOrder(ExecutionContext context, byte[] file) throws IOException, ParseException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Integer firstRow = (Integer) findProperty("importEmailOrderFirstRow[]").read(context);
        String numberCell = (String) findProperty("importEmailOrderNumberCell[]").read(context);
        String quantityColumn = (String) findProperty("importEmailOrderQuantityColumn[]").read(context);

        if (firstRow == null || !notNullNorEmpty(numberCell) || !notNullNorEmpty(quantityColumn)) {
            ServerLoggers.importLogger.error("Импорт из почты: не все параметры заданы (начинать со строки, ячейка номера заказа или колонка количества)");
        } else {

            List<List<Object>> data = importOrderFromXLSX(file, firstRow, numberCell, quantityColumn);

            if (data != null) {

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                ImportField seriesNumberUserOrderField = new ImportField(findProperty("seriesNumber[UserOrder]"));
                ImportKey<?> userOrderKey = new ImportKey((CustomClass) findClass("Purchase.UserOrder"),
                        findProperty("order[STRING[18]]").getMapping(seriesNumberUserOrderField));
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

                try(DataSession session = context.createSession()) {
                    session.pushVolatileStats("EO_PL");
                    IntegrationService service = new IntegrationService(session, table, keys, props);
                    service.synchronize(true, false);
                    session.apply(context);
                    session.popVolatileStats();
                }
            }
        }
    }

    private void finishImportOrder(ExecutionContext context, DataObject orderObject) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (DataSession session = context.createSession()) {
            findProperty("importedOrder[AttachmentEmail]").change(true, session, (DataObject) orderObject);
            session.apply(context);
        }
    }

    private List<List<Object>> importOrderFromXLSX(byte[] file, Integer firstRow, String numberCell, String quantityColumnValue) throws IOException, ParseException {

        List<List<Object>> result = new ArrayList<List<Object>>();

        Pattern p = Pattern.compile("(\\w+)(\\d+)");
        Matcher m = p.matcher(numberCell);
        if (!m.matches()) {
            ServerLoggers.importLogger.error("Импорт из почты: некорректно задана ячейка номера заказа");
        } else {
            Integer numberColumn = getCellIndex(m.group(1));
            Integer numberRow = Integer.parseInt(m.group(2)) - 1;

            Integer quantityColumn = getCellIndex(quantityColumnValue);

            XSSFWorkbook Wb = new XSSFWorkbook(new ByteArrayInputStream(file));
            XSSFSheet sheet = Wb.getSheetAt(0);

            int recordCount = sheet.getLastRowNum();

            String numberOrder = getXLSXFieldValue(sheet, numberRow, numberColumn);
            if (numberOrder != null) {
                for (int i = firstRow - 1; i <= recordCount; i++) {
                    BigDecimal index;
                    try {
                        index = getXLSXBigDecimalFieldValue(sheet, i, B);
                    } catch (ParseException e) {
                        index = null;
                    }
                    if (index != null) {
                        BigDecimal quantity = getXLSXBigDecimalFieldValue(sheet, i, quantityColumn);
                        if (quantity != null)
                            result.add(Arrays.asList((Object) numberOrder, true, index.intValue(), quantity));
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
