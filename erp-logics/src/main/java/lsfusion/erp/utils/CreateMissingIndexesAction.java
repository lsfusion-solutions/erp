package lsfusion.erp.utils;

import lsfusion.server.data.OperationOwner;
import lsfusion.server.data.sql.SQLSession;
import lsfusion.server.data.sql.adapter.DataAdapter;
import lsfusion.server.data.sql.connection.ExConnection;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.sql.syntax.PostgreSQLSyntax;
import lsfusion.server.data.table.Field;
import lsfusion.server.data.table.IndexOptions;
import lsfusion.server.data.table.KeyField;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.data.TSVectorClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.log.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.physics.exec.db.controller.manager.DBManager;
import lsfusion.server.physics.exec.db.table.ImplementTable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

// Создаёт индексы, которые объявлены в логике, но которых нет в базе (PostgreSQL).
// Платформа пропускает их в двух случаях:
// - при переходе со структуры базы старее 37 обычный индекс и индекс LIKE при INDEXED MATCH / LIKE
//   считаются уже созданными (DBManager.createAdditionalIndexRecords);
// - индексы LIKE / MATCH не создаются без pg_trgm, а поставленное позже расширение их не досоздаёт.
// «Проверить индексы» (checkIndicesAction) для этого не годится: имя ищется в pg_indexes без перевода в нижний регистр,
// поэтому индекс со старым именем не переименовывается, а рядом строится второй такой же.
// Здесь индекс считается существующим, если на таблице есть индекс с тем же именем или с тем же составом колонок
// (без учёта ASC / DESC и NULLS): так не задваиваются индексы со старыми именами и после переименования таблиц.
// Имя и текст CREATE INDEX — как у платформы (SQLSession.checkIndex, checkExtraIndexes).
public class CreateMissingIndexesAction extends InternalAction {

    public CreateMissingIndexesAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        try {
            createMissingIndexes(context);
        } catch (Throwable t) { // старт сервера не должен падать из-за досоздания индексов
            ServerLoggers.startLogger.error("Create missing indexes failed", t);
        }
    }

    private void createMissingIndexes(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        SQLSession sql = context.getSession().sql;
        if (!(sql.syntax instanceof PostgreSQLSyntax)) // состав индексов читается из каталога PostgreSQL
            return;

        long started = System.currentTimeMillis();
        Set<String> names = new HashSet<>();
        Map<String, Set<String>> signatures = new HashMap<>();
        readIndexes(sql, names, signatures);

        boolean trgm = DataAdapter.hasTrgmExtension();
        int created = 0, failed = 0;
        for (Map.Entry<ImplementTable, List<DBManager.IndexData<Field>>> entry : context.getDbManager().getIndexesMap().entrySet()) {
            ImplementTable table = entry.getKey();
            String tableName = table.getName(sql.syntax);
            List<String> keys = new ArrayList<>();
            for (KeyField key : table.keys)
                keys.add(key.getName(sql.syntax));

            List<Index> expected = new ArrayList<>();
            for (DBManager.IndexData<Field> index : entry.getValue()) {
                IndexOptions options = index.options;
                if (!options.type.isDefault() && !trgm) // как SQLSession.createLikeIndex / createMatchIndex
                    continue;
                List<String> fields = new ArrayList<>();
                List<Boolean> notNull = new ArrayList<>();
                for (Field field : index.fields) {
                    fields.add(field.getName(sql.syntax));
                    notNull.add(field instanceof KeyField);
                }
                if (options.order && options.type.isDefault()) { // SQLSession.getOrderFields
                    fields.addAll(keys);
                    for (int i = 0; i < keys.size(); i++)
                        notNull.add(true);
                }
                boolean tsVector = index.fields.size() == 1 && index.fields.get(0).type instanceof TSVectorClass;
                expected.add(new Index(tableName, fields, notNull, options, tsVector));
            }
            for (int i = 1; i < keys.size(); i++) { // SQLSession.getExtraIndexes
                List<String> fields = new ArrayList<>(keys.subList(i, keys.size()));
                expected.add(new Index(tableName, fields, new ArrayList<>(Collections.nCopies(fields.size(), true)), null, false));
            }

            Set<String> tableSignatures = signatures.getOrDefault(tableName.toLowerCase(), Collections.emptySet());
            for (Index index : expected) {
                if (names.contains(dbName(index.name())) || tableSignatures.contains(index.signature()))
                    continue;
                String ddl = index.ddl();
                long start = System.currentTimeMillis();
                try {
                    sql.executeDDL(ddl);
                    created++;
                    names.add(dbName(index.name()));
                    ServerLoggers.startLogger.info(String.format("Created missing index: %s, %sms", ddl, System.currentTimeMillis() - start));
                } catch (Exception e) { // в том числе обработанные платформой ошибки SQL: остальные индексы создаются дальше
                    failed++;
                    ServerLoggers.startLogger.error("Create missing index failed: " + ddl, e);
                }
            }
        }
        ServerLoggers.startLogger.info(String.format("Create missing indexes: created %s, failed %s, %sms", created, failed, System.currentTimeMillis() - started));
    }

    // Имена и составы индексов текущей схемы — только из каталога, без pg_get_indexdef: та ждет блокировку таблицы,
    // и одна занятая таблица задержала бы старт. Состав — «метод(колонка,колонка)» без ASC / DESC и NULLS:
    // колонка с gin_trgm_ops → «trgm:колонка», выражение (to_tsvector) → «expr:колонки выражения».
    // Недействительные и частичные (WHERE) индексы составом не считаются.
    private static void readIndexes(SQLSession sql, Set<String> names, Map<String, Set<String>> signatures) throws SQLException {
        ExConnection connection = sql.getConnection();
        try (Statement statement = connection.sql.createStatement();
             ResultSet rs = statement.executeQuery("SELECT c.relname, i.relname, x.indisvalid AND x.indpred IS NULL, am.amname, " +
                     "(SELECT string_agg(CASE WHEN k.attnum = 0 THEN 'expr:' || COALESCE((SELECT string_agg(a2.attname, '+' ORDER BY a2.attnum) " +
                     "FROM pg_attribute a2 WHERE a2.attrelid = x.indrelid AND a2.attnum IN (SELECT m[1]::int FROM regexp_matches(x.indexprs::text, ':varattno (\\d+)', 'g') m)), '') " +
                     "WHEN o.opcname = 'gin_trgm_ops' THEN 'trgm:' || a.attname ELSE a.attname END, ',' ORDER BY k.ord) " +
                     "FROM unnest(x.indkey::int2[], x.indclass::oid[]) WITH ORDINALITY AS k(attnum, opclass, ord) " +
                     "LEFT JOIN pg_attribute a ON a.attrelid = x.indrelid AND a.attnum = k.attnum LEFT JOIN pg_opclass o ON o.oid = k.opclass) " +
                     "FROM pg_index x JOIN pg_class i ON i.oid = x.indexrelid JOIN pg_class c ON c.oid = x.indrelid " +
                     "JOIN pg_namespace n ON n.oid = c.relnamespace JOIN pg_am am ON am.oid = i.relam WHERE n.nspname = current_schema()")) {
            while (rs.next()) {
                names.add(rs.getString(2));
                if (rs.getBoolean(3) && rs.getString(5) != null)
                    signatures.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(4) + "(" + rs.getString(5) + ")");
            }
        } finally {
            sql.returnConnection(connection, OperationOwner.unknown);
        }
    }

    // PostgreSQL переводит имена без кавычек в нижний регистр и обрезает до 63 байт
    private static String dbName(String name) {
        String lower = name.toLowerCase();
        return lower.length() > 63 ? lower.substring(0, 63) : lower;
    }

    private static class Index {
        final String table;
        final List<String> fields;
        final List<Boolean> notNull;
        final IndexOptions options; // null — дополнительный индекс по хвосту ключей
        final boolean tsVector;

        Index(String table, List<String> fields, List<Boolean> notNull, IndexOptions options, boolean tsVector) {
            this.table = table;
            this.fields = fields;
            this.notNull = notNull;
            this.options = options;
            this.tsVector = tsVector;
        }

        boolean isDefault() {
            return options == null || options.type.isDefault();
        }

        String name() { // SQLSession.getIndexName, новый стиль
            if (options != null && options.dbName != null)
                return options.dbName;
            String suffix = isDefault() ? "" : (options.type.isLike() ? "_like" : "_match");
            return String.join("_", fields) + suffix + "_idx_" + table;
        }

        String signature() {
            List<String> columns = new ArrayList<>();
            for (String field : fields) {
                String f = field.toLowerCase();
                columns.add(isDefault() || tsVector ? f : (options.type.isLike() ? "trgm:" + f : "expr:" + f));
            }
            return (isDefault() ? "btree" : "gin") + "(" + String.join(",", columns) + ")";
        }

        String ddl() { // SQLSession.getColumns, createDefaultIndex / createLikeIndex / createMatchIndex
            String body;
            if (isDefault()) {
                List<String> columns = new ArrayList<>();
                for (int i = 0; i < fields.size(); i++)
                    columns.add(fields.get(i) + " " + (notNull.get(i) ? "ASC" : "ASC NULLS FIRST"));
                body = " (" + String.join(",", columns) + ")";
            } else if (options.type.isLike()) {
                body = " USING GIN (" + String.join(",", fields) + " gin_trgm_ops)";
            } else if (tsVector) {
                body = " USING GIN (" + String.join(",", fields) + ")";
            } else {
                body = " USING GIN (to_tsvector(" + (options.language != null ? "'" + options.language + "', " : "") + String.join(",", fields) + "))";
            }
            return "CREATE INDEX IF NOT EXISTS " + name() + " ON " + table + body;
        }
    }
}
