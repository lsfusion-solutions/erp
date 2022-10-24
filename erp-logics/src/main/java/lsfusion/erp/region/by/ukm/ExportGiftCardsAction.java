package lsfusion.erp.region.by.ukm;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultExportAction;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.user.CustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.service.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExportGiftCardsAction extends DefaultExportAction {

    public ExportGiftCardsAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            List<GiftCard> giftCards = getGiftCards(context);
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

    private void exportCertificateType(Connection connection, List<GiftCard> giftCards, int monoAccount, int checkUnderpay, int version) throws SQLException {
        PreparedStatement statement = null;
        try {
            String sql = "INSERT INTO certificate_type (id, name, nominal, mono_account, check_underpay, " +
                    "multi_sell, allow_return, allow_return_payment, check_store, item_id, use_pincode, print_in_receipt, " +
                    "fixed_nominal, min_nominal, max_nominal, version, deleted) " +
                    "VALUES(?, ?, ?, ?, ?, 0, ?, ?, 0, ?, 0, 1, 1, ?, ?, ?, 0) ON DUPLICATE KEY UPDATE nominal=VALUES(nominal), " +
                    "name=VALUES(name), " +
                    "item_id=VALUES(item_id), " +
                    "min_nominal=VALUES(min_nominal), max_nominal=VALUES(max_nominal), " +
                    "version=VALUES(version), deleted=VALUES(deleted);";
            statement = connection.prepareStatement(sql);
            for (GiftCard giftCard : giftCards) {
                statement.setObject(1, giftCard.id); //id
                statement.setObject(2, giftCard.nameSku); //name
                statement.setObject(3, giftCard.price); //nominal
                statement.setObject(4, monoAccount); //mono_account
                statement.setObject(5, checkUnderpay); //check_underpay
                statement.setObject(6, giftCard.allowReturn ? 1 : 0); //allow_return
                statement.setObject(7, giftCard.allowReturnPayment ? 1 : 0); //allow_return_payment
                statement.setObject(8, giftCard.idBarcode); //item_id
                statement.setObject(9, giftCard.price); //min_nominal
                statement.setObject(10, giftCard.price); //max_nominal
                statement.setObject(11, version); //version
                statement.addBatch();
            }
            statement.executeBatch();
        } finally {
            if (statement != null)
                statement.close();
        }
    }

    private void exportCertificate(Connection connection, List<GiftCard> giftCards, int version) throws SQLException {
        PreparedStatement statement = null;
        try {
            String sql = "INSERT INTO certificate (account_type_id, number, active, " +
                    "date_from, date_to, days_from_after_activate, days_to_after_activate, version, deleted) " +
                    "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE active=VALUES(active), " +
                    "date_from=VALUES(date_from), date_to=VALUES(date_to), days_from_after_activate=VALUES(days_from_after_activate), days_to_after_activate=VALUES(days_to_after_activate), " +
                    "version=VALUES(version), deleted=VALUES(deleted);";
            statement = connection.prepareStatement(sql);
            for (GiftCard giftCard : giftCards) {
                statement.setObject(1, giftCard.id); //account_type_id
                statement.setObject(2, giftCard.number); //number
                statement.setObject(3, giftCard.active); //active
                statement.setObject(4, localDateToSqlDate(giftCard.dateFrom)); //date_from
                statement.setObject(5, localDateToSqlDate(giftCard.dateTo)); //date_to
                statement.setObject(6, 0); //days_from_after_activate
                statement.setObject(7, giftCard.expiryDays); //days_to_after_activate
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

    private void exportCertificateOperations(Connection connection, List<GiftCard> giftCards, int version) throws SQLException {
        PreparedStatement statement = null;
        try {
            String sql = "INSERT INTO certificate_operations (number, amount, version, deleted) VALUES(?, ?, ?, 0);";
            statement = connection.prepareStatement(sql);
            for (GiftCard giftCard : giftCards) {
                BigDecimal amount = giftCard.price;
                if(giftCard.active && !giftCard.defect && amount != null) {
                    statement.setObject(1, giftCard.number); //number
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
        try (Statement statement = conn.createStatement()) {
            String sql = String.format("INSERT INTO `signal` (`signal`, version) VALUES('%s', '%s') ON DUPLICATE KEY UPDATE `signal`=VALUES(`signal`);", "incr", version);
            statement.executeUpdate(sql);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private List<GiftCard> getGiftCards(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException, ScriptingErrorLog.SemanticErrorException, SQLException {
        List<GiftCard> giftCards = new ArrayList<>();

        KeyExpr giftCardExpr = new KeyExpr("giftCard");
        ImRevMap<Object, KeyExpr> giftCardKeys = MapFact.singletonRev("giftCard", giftCardExpr);

        QueryBuilder<Object, Object> giftCardQuery = new QueryBuilder<>(giftCardKeys);

        String[] articleNames = new String[]{"number", "price", "idBarcode", "nameSku", "idDepartmentStore", "expiryDays",
                "isSoldInvoice", "isDefect", "useGiftCardDates", "dateSold", "expireDate", "allowReturn", "allowReturnPayment"};
        LP<?>[] articleProperties = findProperties("number[GiftCard]", "price[GiftCard]", "idBarcode[GiftCard]", "nameSku[GiftCard]",
                "idDepartmentStore[GiftCard]", "expiryDays[GiftCard]", "isSoldInvoice[GiftCard]", "isDefect[GiftCard]", "useGiftCardDates[GiftCard]",
                "dateSold[GiftCard]", "expireDate[GiftCard]", "allowReturn[GiftCard]", "allowReturnPayment[GiftCard]");
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
            String shortNameUOM = (String) resultValues.get("shortNameUOM");
            String overIdSkuGroup = (String) resultValues.get("overIdSkuGroup");
            boolean allowReturn = resultValues.get("allowReturn") != null;
            boolean allowReturnPayment = resultValues.get("allowReturnPayment") != null;
            LocalDate dateFrom;
            LocalDate dateTo;
            if(useGiftCardDates) {
                dateFrom = (LocalDate) resultValues.get("dateSold");
                dateTo = (LocalDate) resultValues.get("expireDate");
            } else {
                dateFrom = LocalDate.now();
                dateTo = LocalDate.now();
                if (defect)
                    dateTo = dateTo.minusDays(1);
                else if (expiryDays != null)
                    dateTo = dateTo.plusDays(expiryDays);
            }
            Integer id = getId(idBarcode);
            if (id != null) {
                giftCards.add(new GiftCard(id, number, price, idBarcode, departmentStore, active ? dateFrom : null, active || defect ? dateTo : null,
                        expiryDays, active, defect, nameSku, shortNameUOM, overIdSkuGroup, allowReturn, allowReturnPayment));
            } else {
                context.delayUserInteraction(new MessageClientAction(String.format("Невозможно сконвертировать штрихкод %s в integer id", idBarcode), "Ошибка"));
                return null;
            }
        }

        return giftCards;
    }

    private void finishExport(ExecutionContext<ClassPropertyInterface> context, List<GiftCard> giftCards) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<List<Object>> data = new ArrayList<>();
        for(GiftCard giftCard : giftCards) {
            data.add(Arrays.asList(giftCard.number, true, giftCard.active ? true : null));
        }

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        ImportField numberGiftCardField = new ImportField(findProperty("number[GiftCard]"));
        ImportKey<?> giftCardKey = new ImportKey((CustomClass) findClass("GiftCard"),
                findProperty("giftCardSeriesNumber[STRING[30]]").getMapping(numberGiftCardField));
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

    private int getVersion(Connection conn) {
        int version;
        try (Statement statement = conn.createStatement()) {
            String query = "select max(version) from `signal`";
            ResultSet rs = statement.executeQuery(query);
            version = rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            version = 0;
        }
        return version;
    }

    private class GiftCard {
        Integer id;
        String number;
        BigDecimal price;
        String idBarcode;
        String departmentStore;
        LocalDate dateFrom;
        LocalDate dateTo;
        Integer expiryDays;
        boolean active;
        boolean defect;
        String nameSku;
        String shortNameUOM;
        String overIdSkuGroup;
        boolean allowReturn;
        boolean allowReturnPayment;

        public GiftCard(Integer id, String number, BigDecimal price, String idBarcode, String departmentStore, LocalDate dateFrom, LocalDate dateTo,
                        Integer expiryDays, boolean active, boolean defect, String nameSku, String shortNameUOM, String overIdSkuGroup,
                        boolean allowReturn, boolean allowReturnPayment) {
            this.id = id;
            this.number = number;
            this.price = price;
            this.idBarcode = idBarcode;
            this.departmentStore = departmentStore;
            this.dateFrom = dateFrom;
            this.dateTo = dateTo;
            this.expiryDays = expiryDays;
            this.active = active;
            this.defect = defect;
            this.nameSku = nameSku;
            this.shortNameUOM = shortNameUOM;
            this.overIdSkuGroup = overIdSkuGroup;
            this.allowReturn = allowReturn;
            this.allowReturnPayment = allowReturnPayment;
        }
    }
}
