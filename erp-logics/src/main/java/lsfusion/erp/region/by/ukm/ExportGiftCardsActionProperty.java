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
                    exportItems(connection, giftCards, version);
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
                statement.setObject(4, giftCard.dateFrom); //date_from
                statement.setObject(5, giftCard.dateTo); //date_to
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
                if(giftCard.active && amount != null) {
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

    private void exportItems(Connection conn, List<GiftCard> giftCards, int version) throws SQLException {
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO items (id, name, descr, measure, measprec, classif, prop, summary, exp_date, version, deleted, tax) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE name=VALUES(name), descr=VALUES(descr), measure=VALUES(measure), measprec=VALUES(measprec), classif=VALUES(classif),"
                + " prop=VALUES(prop), summary=VALUES(summary), exp_date=VALUES(exp_date), deleted=VALUES(deleted), tax=VALUES(tax)")) {

            for (GiftCard giftCard : giftCards) {
                ps.setString(1, giftCard.idBarcode); //id
                ps.setString(2, giftCard.nameSku); //name
                ps.setString(3, ""); //descr
                ps.setString(4, trim(giftCard.shortNameUOM, "", 40)); //measure
                ps.setInt(5, 0); //measprec
                ps.setString(6, parseGroup(giftCard.overIdSkuGroup)); //classif
                ps.setInt(7, 1); //prop - признак товара ?
                ps.setString(8, ""); //summary
                ps.setDate(9, null); //exp_date
                ps.setInt(10, version); //version
                ps.setInt(11, 0); //deleted
                ps.setInt(12, 0);
                ps.addBatch();
            }

            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private String parseGroup(String idItemGroup) {
        try {
            return idItemGroup == null || idItemGroup.equals("Все") ? "0" : idItemGroup;
        } catch (Exception e) {
            return "0";
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

    private List<GiftCard> getGiftCards(ExecutionContext context) throws SQLHandledException, ScriptingErrorLog.SemanticErrorException, SQLException {
        List<GiftCard> giftCards = new ArrayList<>();

        KeyExpr giftCardExpr = new KeyExpr("giftCard");
        ImRevMap<Object, KeyExpr> giftCardKeys = MapFact.singletonRev((Object) "giftCard", giftCardExpr);

        QueryBuilder<Object, Object> giftCardQuery = new QueryBuilder<>(giftCardKeys);

        String[] articleNames = new String[]{"number", "price", "idBarcode", "nameSku", "idDepartmentStore", "expiryDays",
                "isSoldInvoice", "isDefect", "useGiftCardDates", "dateSold", "expireDate", "shortNameUOM", "overIdSkuGroup",
                "allowReturn", "allowReturnPayment"};
        LCP[] articleProperties = findProperties("number[GiftCard]", "price[GiftCard]", "idBarcode[GiftCard]", "nameSku[GiftCard]",
                "idDepartmentStore[GiftCard]", "expiryDays[GiftCard]", "isSoldInvoice[GiftCard]", "isDefect[GiftCard]", "useGiftCardDates[GiftCard]",
                "dateSold[GiftCard]", "expireDate[GiftCard]", "shortNameUOM[GiftCard]", "overIdSkuGroup[GiftCard]",
                "allowReturn[GiftCard]", "allowReturnPayment[GiftCard]");
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
                giftCards.add(new GiftCard(id, number, price, idBarcode, departmentStore, active ? dateFrom : null, active || defect ? dateTo : null,
                        expiryDays, active, nameSku, shortNameUOM, overIdSkuGroup, allowReturn, allowReturnPayment));
            } else {
                context.delayUserInteraction(new MessageClientAction(String.format("Невозможно сконвертировать штрихкод %s в integer id", idBarcode), "Ошибка"));
                return null;
            }
        }

        return giftCards;
    }

    private void finishExport(ExecutionContext context, List<GiftCard> giftCards) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        List<List<Object>> data = new ArrayList<>();
        for(GiftCard giftCard : giftCards) {
            data.add(Arrays.<Object>asList(giftCard.number, true, giftCard.active ? true : null));
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

    private class GiftCard {
        Integer id;
        String number;
        BigDecimal price;
        String idBarcode;
        String departmentStore;
        Date dateFrom;
        Date dateTo;
        Integer expiryDays;
        boolean active;
        String nameSku;
        String shortNameUOM;
        String overIdSkuGroup;
        boolean allowReturn;
        boolean allowReturnPayment;

        public GiftCard(Integer id, String number, BigDecimal price, String idBarcode, String departmentStore, Date dateFrom, Date dateTo,
                        Integer expiryDays, boolean active, String nameSku, String shortNameUOM, String overIdSkuGroup,
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
            this.nameSku = nameSku;
            this.shortNameUOM = shortNameUOM;
            this.overIdSkuGroup = overIdSkuGroup;
            this.allowReturn = allowReturn;
            this.allowReturnPayment = allowReturnPayment;
        }
    }
}
