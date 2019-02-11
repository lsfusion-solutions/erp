package lsfusion.erp.region.by.ukm;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultExportActionProperty;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class ExportGiftCardsActionProperty extends DefaultExportActionProperty {

    public ExportGiftCardsActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            List<List<Object>> giftCards = getGiftCards(context);
            if(giftCards != null && !giftCards.isEmpty()) {
                String connectionString = (String) findProperty("connectionStringExportGiftCards[]").read(context);
                String user = (String) findProperty("userExportGiftCards[]").read(context);
                String password = (String) findProperty("passwordExportGiftCards[]").read(context);
                Integer checkUnderpay = (Integer) findProperty("checkUnderpayExportGiftCards[]").read(context);
                if(checkUnderpay == null)
                    checkUnderpay = 0;

                Integer monoAccount = (Integer) findProperty("monoAccountExportGiftCards[]").read(context);
                if(monoAccount == null)
                    monoAccount = 1;


                if (connectionString != null) {

                    Class.forName("com.mysql.jdbc.Driver");
                    Connection connection = DriverManager.getConnection(connectionString, user, password);
                    connection.setAutoCommit(false);

                    Integer version = getVersion(connection) + 1;

                    exportCertificateType(connection, giftCards, monoAccount, checkUnderpay, version);
                    exportCertificate(connection, giftCards, version);
                    exportCertificateOperations(connection, giftCards, version);
                    exportSignals(connection, version);

                    finishExport(context, giftCards);

                    context.delayUserInteraction(new MessageClientAction("Экспорт успешно завершён", "Экспорт"));

                } else {
                    context.delayUserInteraction(new MessageClientAction("Не задана строка подключения", "Ошибка"));
                }
            }
            context.apply();
        } catch (ScriptingErrorLog.SemanticErrorException | ClassNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }

    private void exportCertificateType(Connection connection, List<List<Object>> giftCards, int monoAccount, int checkUnderpay, int version) throws SQLException {
        PreparedStatement statement = null;
        try {
            String sql = "INSERT INTO certificate_type (id, name, nominal, mono_account, check_underpay, " +
                    "multi_sell, allow_return, allow_return_payment, check_store, item_id, use_pincode, print_in_receipt, " +
                    "fixed_nominal, min_nominal, max_nominal, version, deleted) " +
                    "VALUES(?, ?, ?, ?, ?, 0, 0, 0, 0, ?, 0, 1, 1, ?, ?, ?, 0) ON DUPLICATE KEY UPDATE nominal=VALUES(nominal), " +
                    "name=VALUES(name), " +
                    "item_id=VALUES(item_id), " +
                    "min_nominal=VALUES(min_nominal), max_nominal=VALUES(max_nominal), " +
                    "version=VALUES(version), deleted=VALUES(deleted);";
            statement = connection.prepareStatement(sql);
            for (List<Object> giftCard : giftCards) {
                statement.setObject(1, giftCard.get(0)); //id
                statement.setObject(2, giftCard.get(9)); //name
                statement.setObject(3, giftCard.get(2)); //nominal
                statement.setObject(4, monoAccount); //mono_account
                statement.setObject(5, checkUnderpay); //check_underpay
                statement.setObject(6, giftCard.get(3)); //item_id
                statement.setObject(7, giftCard.get(2)); //min_nominal
                statement.setObject(8, giftCard.get(2)); //max_nominal
                statement.setObject(9, version); //version
                statement.addBatch();
            }
            statement.executeBatch();
        } finally {
            if (statement != null)
                statement.close();
        }
    }

    private void exportCertificate(Connection connection, List<List<Object>> giftCards, int version) throws SQLException {
        PreparedStatement statement = null;
        try {
            String sql = "INSERT INTO certificate (account_type_id, number, active, " +
                    "date_from, date_to, days_from_after_activate, days_to_after_activate, version, deleted) " +
                    "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE active=VALUES(active), " +
                    "date_from=VALUES(date_from), date_to=VALUES(date_to), days_from_after_activate=VALUES(days_from_after_activate), days_to_after_activate=VALUES(days_to_after_activate), " +
                    "version=VALUES(version), deleted=VALUES(deleted);";
            statement = connection.prepareStatement(sql);
            for (List<Object> giftCard : giftCards) {
                statement.setObject(1, giftCard.get(0)); //account_type_id
                statement.setObject(2, giftCard.get(1)); //number
                statement.setObject(3, giftCard.get(8)); //active
                statement.setObject(4, giftCard.get(5)); //date_from
                statement.setObject(5, giftCard.get(6)); //date_to
                statement.setObject(6, 0); //days_from_after_activate
                statement.setObject(7, giftCard.get(7)); //days_to_after_activate
                statement.setObject(8, version); //version
                statement.setObject(9, 0); //deleted
                statement.addBatch();
            }
            statement.executeBatch();
        } finally {
            if (statement != null)
                statement.close();
        }
    }

    private void exportCertificateOperations(Connection connection, List<List<Object>> giftCards, int version) throws SQLException {
        PreparedStatement statement = null;
        try {
            String sql = "INSERT INTO certificate_operations (number, amount, version, deleted) VALUES(?, ?, ?, 0);";
            statement = connection.prepareStatement(sql);
            for (List<Object> giftCard : giftCards) {
                boolean active = (boolean) giftCard.get(8);
                BigDecimal amount = (BigDecimal) giftCard.get(2);
                if(active && amount != null) {
                    statement.setObject(1, giftCard.get(1)); //number
                    statement.setObject(2, amount); //amount
                    statement.setObject(3, version); //version
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        } finally {
            if (statement != null)
                statement.close();
        }
    }

    private void exportSignals(Connection conn, int version) throws SQLException {
        conn.setAutoCommit(true);
        Statement statement = null;
        try {
            statement = conn.createStatement();
            String sql = String.format("INSERT INTO `signal` (`signal`, version) VALUES('%s', '%s') ON DUPLICATE KEY UPDATE `signal`=VALUES(`signal`);",
                    "incr", version);
            statement.executeUpdate(sql);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (statement != null)
                statement.close();
        }
    }

    private List<List<Object>> getGiftCards(ExecutionContext context) throws SQLHandledException, ScriptingErrorLog.SemanticErrorException, SQLException {
        List<List<Object>> giftCards = new ArrayList<>();

        KeyExpr giftCardExpr = new KeyExpr("giftCard");
        ImRevMap<Object, KeyExpr> giftCardKeys = MapFact.singletonRev((Object) "giftCard", giftCardExpr);

        QueryBuilder<Object, Object> giftCardQuery = new QueryBuilder<>(giftCardKeys);

        String[] articleNames = new String[]{"number", "price", "idBarcode", "nameSku", "idDepartmentStore", "expiryDays",
                "isSoldInvoice", "isDefect", "useGiftCardDates", "dateSold", "expireDate"};
        LCP[] articleProperties = findProperties("number[GiftCard]", "price[GiftCard]", "idBarcode[GiftCard]", "nameSku[GiftCard]",
                "idDepartmentStore[GiftCard]", "expiryDays[GiftCard]", "isSoldInvoice[GiftCard]", "isDefect[GiftCard]", "useGiftCardDates[GiftCard]",
                "dateSold[GiftCard]", "expireDate[GiftCard]");
        for (int j = 0; j < articleProperties.length; j++) {
            giftCardQuery.addProperty(articleNames[j], articleProperties[j].getExpr(giftCardExpr));
        }
        giftCardQuery.and(findProperty("inExportGiftCards[GiftCard]").getExpr(context.getModifier(), giftCardExpr).getWhere());
//        giftCardQuery.and(findProperty("exportedExportGiftCards[GiftCard]").getExpr(context.getModifier(), giftCardExpr).getWhere().not());
        giftCardQuery.and(findProperty("idBarcode[GiftCard]").getExpr(context.getModifier(), giftCardExpr).getWhere());

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> articleResult = giftCardQuery.execute(context);

        for (int i = 0, size = articleResult.size(); i < size; i++) {
            ImMap<Object, Object> resultValues = articleResult.getValue(i);

            String number = (String) resultValues.get("number");
            BigDecimal price = (BigDecimal) resultValues.get("price");
            String idBarcode = (String) resultValues.get("idBarcode");
            String nameSku = (String) resultValues.get("nameSku");
            String departmentStore = (String) resultValues.get("idDepartmentStore");
            Integer expiryDays = (Integer) resultValues.get("expiryDays");
            boolean active = resultValues.get("isSoldInvoice") != null;
            boolean defect = resultValues.get("isDefect") != null;
            boolean useGiftCardDates = resultValues.get("useGiftCardDates") != null;
            Date dateFrom;
            Date dateTo;
            if(useGiftCardDates) {
                dateFrom = (Date) resultValues.get("dateSold");
                dateTo = (Date) resultValues.get("expireDate");
            } else {
                Calendar calendar = Calendar.getInstance();
                dateFrom = new Date(calendar.getTime().getTime());
                if (defect)
                    calendar.add(Calendar.DATE, -1);
                else if (expiryDays != null)
                    calendar.add(Calendar.DATE, expiryDays);
                dateTo = new Date(calendar.getTime().getTime());
            }
            Integer id = getId(idBarcode);
            if (id != null) {
                if (active)
                    giftCards.add(Arrays.<Object>asList(id, number, price, idBarcode, departmentStore, dateFrom, dateTo, expiryDays, true, nameSku));
                else
                    giftCards.add(Arrays.<Object>asList(id, number, price, idBarcode, departmentStore, null, defect ? dateTo : null, expiryDays, false, nameSku));
            } else {
                context.delayUserInteraction(new MessageClientAction(String.format("Невозможно сконвертировать штрихкод %s в integer id", idBarcode), "Ошибка"));
                return null;
            }
        }

        return giftCards;
    }

    private void finishExport(ExecutionContext context, List<List<Object>> giftCards) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<List<Object>> data = new ArrayList<>();
        for(List<Object> giftCard : giftCards) {
            data.add(Arrays.asList(giftCard.get(1), true, giftCard.get(8).equals(false) ? null : true));
        }

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField numberGiftCardField = new ImportField(findProperty("number[GiftCard]"));
        ImportKey<?> giftCardKey = new ImportKey((CustomClass) findClass("GiftCard"),
                findProperty("giftCardSeriesNumber[VARSTRING[30]]").getMapping(numberGiftCardField));
        giftCardKey.skipKey = true;
        keys.add(giftCardKey);
        fields.add(numberGiftCardField);

        ImportField exportedExportGiftCardsField = new ImportField(findProperty("exportedExportGiftCards[GiftCard]"));
        props.add(new ImportProperty(exportedExportGiftCardsField, findProperty("exportedExportGiftCards[GiftCard]").getMapping(giftCardKey)));
        fields.add(exportedExportGiftCardsField);

        ImportField exportedActiveGiftCardsField = new ImportField(findProperty("exportedActive[GiftCard]"));
        props.add(new ImportProperty(exportedActiveGiftCardsField, findProperty("exportedActive[GiftCard]").getMapping(giftCardKey)));
        fields.add(exportedActiveGiftCardsField);

        ImportTable table = new ImportTable(fields, data);

        try(ExecutionContext.NewSession newContext = context.newSession()) {
            IntegrationService service = new IntegrationService(newContext, table, keys, props);
            service.synchronize(true, false);
            newContext.apply();
        }
    }

    private Integer getId(String idBarcode) {
        try {
            return Integer.parseInt("11" + idBarcode);
        } catch (Exception e) {
            return null;
        }
    }

    private int getVersion(Connection conn) throws SQLException {
        int version;
        Statement statement = null;
        try {
            statement = conn.createStatement();
            String query = "select max(version) from `signal`";
            ResultSet rs = statement.executeQuery(query);
            version = rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            version = 0;
        } finally {
            if (statement != null)
                statement.close();
        }
        return version;
    }
}
