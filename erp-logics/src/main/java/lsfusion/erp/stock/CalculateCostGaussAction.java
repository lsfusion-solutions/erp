package lsfusion.erp.stock;

import lsfusion.base.col.ListFact;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.*;
import lsfusion.base.col.interfaces.mutable.MExclMap;
import lsfusion.base.col.interfaces.mutable.MRevMap;
import lsfusion.server.data.OperationOwner;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.Join;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.SQLSession;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.action.session.change.PropertyChange;
import lsfusion.server.logics.action.session.table.SessionTableUsage;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.Property;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.property.classes.infer.ClassType;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

public class CalculateCostGaussAction extends InternalAction {

    public CalculateCostGaussAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            KeyExpr skuExpr = new KeyExpr("Sku");
            KeyExpr stockExpr = new KeyExpr("Stock");
            ImRevMap<String, KeyExpr> quantityKeys = MapFact.toRevMap("Sku", skuExpr, "Stock", stockExpr);

            QueryBuilder<String, String> quantityQuery = new QueryBuilder<>(quantityKeys);
            quantityQuery.addProperty("quantity", findProperty("calcCostQuantity[Stock.Sku,Stock.Stock]").getExpr(context.getModifier(), skuExpr, stockExpr));
            quantityQuery.addProperty("sum", findProperty("calcCostSum[Stock.Sku,Stock.Stock]").getExpr(context.getModifier(), skuExpr, stockExpr));
            quantityQuery.addProperty("intra", findProperty("calcCostIntraTo[Stock.Sku,Stock.Stock]").getExpr(context.getModifier(), skuExpr, stockExpr));
            quantityQuery.and(findProperty("calcCostIntraTo[Stock.Sku,Stock.Stock]").getExpr(context.getModifier(), skuExpr, stockExpr).getWhere());

            ImOrderMap<ImMap<String, DataObject>, ImMap<String, ObjectValue>> costResult = quantityQuery.executeClasses(context);

            Map<DataObject,List<ImMap<String, DataObject>>> skus = new HashMap<>();
            for (ImMap<String, DataObject> entry : costResult.keys()) {
                DataObject sku = entry.get("Sku");
                if (!skus.containsKey(sku))
                    skus.put(sku, new ArrayList<>());
                skus.get(sku).add(entry);
            }

            KeyExpr fromExpr = new KeyExpr("From");
            KeyExpr toExpr = new KeyExpr("To");

            MRevMap<String, KeyExpr> mMap = MapFact.mRevMap(3);
            mMap.revAdd("Sku", skuExpr);
            mMap.revAdd("From", fromExpr);
            mMap.revAdd("To", toExpr);

            QueryBuilder<String, String> intraQuery = new QueryBuilder<>(mMap.immutableRev());
            intraQuery.addProperty("intra", findProperty("calcCostIntra[Stock.Sku,Stock.Stock,Stock.Stock]").getExpr(context.getModifier(), skuExpr, fromExpr, toExpr));
            intraQuery.and(findProperty("calcCostIntra[Stock.Sku,Stock.Stock,Stock.Stock]").getExpr(context.getModifier(), skuExpr, fromExpr, toExpr).getWhere());

            ImOrderMap<ImMap<String, DataObject>, ImMap<String, ObjectValue>> intraResult = intraQuery.executeClasses(context);

            MExclMap<ImMap<Integer,DataObject>,ImMap<LP<?>,ObjectValue>> mData = MapFact.mExclMap(costResult.size());

            LP cost = findProperty("calculatedCost[Stock.Sku,Stock.Stock]");

            StringBuilder errors = new StringBuilder();

            for (Map.Entry<DataObject, List<ImMap<String, DataObject>>> sku : skus.entrySet()) {
                List<ImMap<String, DataObject>> stocks = sku.getValue();
                int n = stocks.size();
                double[] fixed = new double[n];
                double[][] matrix = new double[n][n];
                double[] value = new double[n];
                buildMatrix(costResult, intraResult, sku, stocks, fixed, matrix, value);

                int variable = -1;
                boolean found = false;
                double[] result = new double[0];
                while (true) {
                    try {
                        result = lsolve(matrix, value);
                        found = true;
                        break;
                    } catch (ArithmeticException e) { // есть зависимые уравнения
                        buildMatrix(costResult, intraResult, sku, stocks, fixed, matrix, value);

                        variable++;
                        while (variable < n && Math.abs(fixed[variable]) < EPSILON) variable++;
                        if (variable >= n) break;

                        for (int i = 0; i <= variable; i++)
                            if (Math.abs(fixed[i]) >= EPSILON)
                                for (int j = 0; j < n; j++)
                                    matrix[variable][j] = variable == j ? fixed[variable] : 0.0;
                    }
                }

                if (!found) {
                    errors.append(sku.getKey().getValue()).append("\n");
                    continue;
                }

                for (int i = 0; i < n; i++) {
                    mData.exclAdd(MapFact.toMap(1, sku.getKey(), 2, stocks.get(i).get("Stock")),
                                  MapFact.singleton(cost, new DataObject(result[i])));
                }
            }

            changeProperty(Arrays.asList(cost), mData.immutable(), context);

            findProperty("calculatedErrors[]").change(errors.toString(), context);

        } catch (ScriptingErrorLog.SemanticErrorException e) {
        }
    }

    private void buildMatrix(ImOrderMap<ImMap<String, DataObject>, ImMap<String, ObjectValue>> costResult, ImOrderMap<ImMap<String, DataObject>, ImMap<String, ObjectValue>> intraResult, Map.Entry<DataObject, List<ImMap<String, DataObject>>> sku, List<ImMap<String, DataObject>> stocks, double[] fixed, double[][] matrix, double[] value) {
        int n = stocks.size();
        for (int i = 0; i < n; i++) {
            ImMap<String, DataObject> entry = stocks.get(i);
            for (int j = 0; j < n; j++)
                if (i != j) {
                    MRevMap<String, DataObject> miMap = MapFact.mRevMap(3);
                    miMap.revAdd("Sku", sku.getKey());
                    miMap.revAdd("From", stocks.get(j).get("Stock"));
                    miMap.revAdd("To", entry.get("Stock"));
                    ImMap<String, ObjectValue> intraRow = intraResult.get(miMap.immutableRev());
                    BigDecimal intra = intraRow == null ? null : (BigDecimal) intraRow.get("intra").getValue();
                    matrix[i][j] = intra == null ? 0.0 : -intra.doubleValue();
                } else {
                    BigDecimal quantity = (BigDecimal) costResult.get(entry).get("quantity").getValue();
                    fixed[i] = quantity == null ? 0.0 : quantity.doubleValue();
                    BigDecimal intra = (BigDecimal) costResult.get(entry).get("intra").getValue();
                    matrix[i][i] = (intra == null ? 0.0 : intra.doubleValue()) + fixed[i];
                }
            BigDecimal sum = (BigDecimal) costResult.get(entry).get("sum").getValue();
            value[i] = sum == null ? 0 : sum.doubleValue();
        }
    }

    private static final double EPSILON = 1e-10;

    // Gaussian elimination with partial pivoting

    public double[] lsolve(double[][] A, double[] b) {
        int n = b.length;

        for (int p = 0; p < n; p++) {

            // find pivot row and swap
            int max = p;
            for (int i = p + 1; i < n; i++) {
                if (Math.abs(A[i][p]) > Math.abs(A[max][p])) {
                    max = i;
                }
            }
            double[] temp = A[p]; A[p] = A[max]; A[max] = temp;
            double   t    = b[p]; b[p] = b[max]; b[max] = t;

            // singular or nearly singular
            if (Math.abs(A[p][p]) <= EPSILON) {
                throw new ArithmeticException("Matrix is singular or nearly singular");
            }

            // pivot within A and b
            for (int i = p + 1; i < n; i++) {
                double alpha = A[i][p] / A[p][p];
                b[i] -= alpha * b[p];
                for (int j = p; j < n; j++) {
                    A[i][j] -= alpha * A[p][j];
                }
            }
        }

        // back substitution
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = 0.0;
            for (int j = i + 1; j < n; j++) {
                sum += A[i][j] * x[j];
            }
            x[i] = (b[i] - sum) / A[i][i];
        }
        return x;
    }

    private void changeProperty(List<LP<?>> props, ImMap<ImMap<Integer,DataObject>,ImMap<LP<?>,ObjectValue>> data, ExecutionContext context) throws SQLException, SQLHandledException {

        LP<?> property = props.get(0);
        ImOrderSet<Integer> keys = ListFact.consecutiveList(property.listInterfaces.size());

        SessionTableUsage<Integer, LP<?>> importTable =
                new SessionTableUsage<Integer, LP<?>>("changeProperty",
                                                            keys,
                                                            SetFact.fromJavaOrderSet(props),
                                                            key -> property.getInterfaceClasses(ClassType.signaturePolicy)[key-1].getType(),
                                                            key -> ((LP<?>)key).property.getType());

        DataSession session = context.getSession();
        OperationOwner owner = session.getOwner();
        SQLSession sql = session.sql;

        importTable.writeRows(sql, data, owner);

        final ImRevMap<Integer, KeyExpr> mapKeys = importTable.getMapKeys();
        Join<LP<?>> importJoin = importTable.join(mapKeys);
        try {
            for (LP<?> lcp : importTable.getValues()) {
                PropertyChange<?> propChange = new PropertyChange<>(lcp.listInterfaces.mapSet(keys).join(mapKeys), importJoin.getExpr(lcp), importJoin.getWhere());
                context.getEnv().change((Property) lcp.property, propChange);
            }
        } finally {
            importTable.drop(sql, owner);
        }
    }

}
