package equ.srv.actions;

import equ.api.SalesInfo;
import equ.srv.EquipmentServer;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Time;
import java.util.*;

public class GenerateZReport extends ScriptingActionProperty {
    public GenerateZReport(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataSession session = context.getSession();
        List<SalesInfo> salesInfoList = new ArrayList<>();
        try {
            ObjectValue priceListType = findProperty("priceListTypeGenerateZReport[]").readClasses(context);
            if (!(priceListType instanceof NullValue)) {
                Random r = new Random();
                Integer zReportCount = addDeviation((Integer) findProperty("averageZReportCountGenerateZReport[]").read(context), 0.25, r);
                Integer receiptCount = (Integer) findProperty("averageReceiptCountGenerateZReport[]").read(context);
                Integer receiptDetailCount = (Integer) findProperty("averageReceiptDetailCountGenerateZReport[]").read(context);

                Date dateFrom = (Date) findProperty("dateFromGenerateZReport[]").read(context);
                dateFrom = dateFrom == null ? new Date(System.currentTimeMillis()) : dateFrom;
                Date dateTo = (Date) findProperty("dateToGenerateZReport[]").read(context);
                dateTo = dateTo == null ? new Date(System.currentTimeMillis()) : dateTo;

                KeyExpr departmentStoreExpr = new KeyExpr("departmentStore");
                KeyExpr itemExpr = new KeyExpr("item");
                ImRevMap<Object, KeyExpr> newKeys = MapFact.<Object, KeyExpr>toRevMap("departmentStore", departmentStoreExpr, "item", itemExpr);

                QueryBuilder<Object, Object> query = new QueryBuilder<>(newKeys);
                query.addProperty("currentBalanceSkuStock", findProperty("currentBalance[Sku,Stock]").getExpr(itemExpr, departmentStoreExpr));
                query.addProperty("priceSkuStock", findProperty("price[Sku,Stock]").getExpr(itemExpr, departmentStoreExpr));
                query.and(findProperty("currentBalance[Sku,Stock]").getExpr(itemExpr, departmentStoreExpr).getWhere());
                query.and(findProperty("price[Sku,Stock]").getExpr(itemExpr, departmentStoreExpr).getWhere());
                query.and(findProperty("idBarcode[Sku]").getExpr(itemExpr, departmentStoreExpr).getWhere());
                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(session);

                List<ItemZReportInfo> itemZReportInfoList = new ArrayList<>();
                List<DataObject> departmentStoreList = new ArrayList<>();
                for (int i = 0, size = result.size(); i < size; i++) {
                    ImMap<Object, DataObject> resultKeys = result.getKey(i);
                    ImMap<Object, ObjectValue> resultValues = result.getValue(i);
                    DataObject itemObject = resultKeys.get("item");
                    BigDecimal currentBalanceSkuStock = (BigDecimal) resultValues.get("currentBalanceSkuStock").getValue();
                    if (currentBalanceSkuStock.doubleValue() > 0) {
                        DataObject departmentStore = resultKeys.get("departmentStore");
                        if ((departmentStore != null) && (!departmentStoreList.contains(departmentStore)))
                            departmentStoreList.add(departmentStore);
                        BigDecimal priceSkuStock = (BigDecimal) resultValues.get("priceSkuStock").getValue();
                        String barcodeItem = trim((String) findProperty("idBarcode[Sku]").read(session, itemObject));
                        Boolean splitItem = (Boolean) findProperty("split[Item]").read(session, itemObject);
                        itemZReportInfoList.add(new ItemZReportInfo(barcodeItem, currentBalanceSkuStock, priceSkuStock, splitItem != null, departmentStore));
                    }
                }

                Map<DataObject, DataObject> groupCashRegisterDepartmentStoreMap = new HashMap<>();
                for (DataObject departmentStore : departmentStoreList) {

                    LCP<PropertyInterface> isGroupCashRegister = (LCP<PropertyInterface>) is(findClass("GroupCashRegister"));

                    ImRevMap<PropertyInterface, KeyExpr> groupCashRegisterKeys = isGroupCashRegister.getMapKeys();
                    KeyExpr groupCashRegisterKey = groupCashRegisterKeys.singleValue();
                    QueryBuilder<PropertyInterface, Object> groupCashRegisterQuery = new QueryBuilder<>(groupCashRegisterKeys);
                    groupCashRegisterQuery.and(isGroupCashRegister.property.getExpr(groupCashRegisterKeys).getWhere());
                    groupCashRegisterQuery.and(findProperty("departmentStore[GroupCashRegister]").getExpr(groupCashRegisterKey).compare(departmentStore.getExpr(), Compare.EQUALS));

                    ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> groupCashRegisterResult = groupCashRegisterQuery.executeClasses(session);

                    for (ImMap<PropertyInterface, DataObject> rows : groupCashRegisterResult.keyIt()) {
                        DataObject groupCashRegister = rows.getValue(0);
                        if ((groupCashRegister != null) && (!groupCashRegisterDepartmentStoreMap.containsKey(groupCashRegister)))
                            groupCashRegisterDepartmentStoreMap.put(groupCashRegister, departmentStore);
                    }
                }

                Map<DataObject, DataObject> cashRegisterDepartmentStoreMap = new HashMap<>();
                for (Map.Entry<DataObject, DataObject> groupCashRegisterDepartmentStore : groupCashRegisterDepartmentStoreMap.entrySet()) {

                    LCP<PropertyInterface> isCashRegister = (LCP<PropertyInterface>) is(findClass("CashRegister"));

                    ImRevMap<PropertyInterface, KeyExpr> cashRegisterKeys = isCashRegister.getMapKeys();
                    KeyExpr cashRegisterKey = cashRegisterKeys.singleValue();
                    QueryBuilder<PropertyInterface, Object> cashRegisterQuery = new QueryBuilder<>(cashRegisterKeys);
                    cashRegisterQuery.and(isCashRegister.property.getExpr(cashRegisterKeys).getWhere());
                    cashRegisterQuery.and(findProperty("groupCashRegister[CashRegister]").getExpr(cashRegisterKey).compare(groupCashRegisterDepartmentStore.getKey().getExpr(), Compare.EQUALS));

                    ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> cashRegisterResult = cashRegisterQuery.executeClasses(session);

                    for (int i = 0; i < cashRegisterResult.size(); i++) {
                        DataObject cashRegisterObject = cashRegisterResult.getKey(i).getValue(0);
                        if ((!cashRegisterDepartmentStoreMap.containsKey(cashRegisterObject)))
                            cashRegisterDepartmentStoreMap.put(cashRegisterObject, groupCashRegisterDepartmentStore.getValue());
                    }
                }

                if (!cashRegisterDepartmentStoreMap.isEmpty()) {
                    Map<String, DataObject> numberZReportCashRegisterMap = new HashMap<>();

                    for (int z = 1; z <= zReportCount; z++) {

                        Map.Entry<DataObject, DataObject> cashRegisterDepartmentStore = (Map.Entry<DataObject, DataObject>) (cashRegisterDepartmentStoreMap.entrySet().toArray()[r.nextInt(cashRegisterDepartmentStoreMap.size())/*1*/]);
                        DataObject cashRegisterObject = cashRegisterDepartmentStore.getKey();
                        DataObject departmentStoreObject = cashRegisterDepartmentStore.getValue();
                        Integer maxNumberZReport = (Integer) findProperty("maxNumberZReport[CashRegister]").read(session, cashRegisterObject);
                        String numberZReport = null;
                        while (numberZReport == null || (numberZReportCashRegisterMap.containsKey(numberZReport) && numberZReportCashRegisterMap.containsValue(cashRegisterObject)))
                            numberZReport = String.valueOf((maxNumberZReport == null ? 0 : maxNumberZReport) + (zReportCount < 1 ? 0 : r.nextInt(zReportCount)) + 1);
                        if (!numberZReportCashRegisterMap.containsKey(numberZReport))
                            numberZReportCashRegisterMap.put(numberZReport, cashRegisterObject);
                        java.sql.Date date = new java.sql.Date(dateFrom.getTime() + Math.abs(r.nextLong() % (dateTo.getTime() - dateFrom.getTime())));
                        for (int receiptNumber = 1; receiptNumber <= addDeviation(receiptCount, 0.25, r); receiptNumber++) {

                            Integer numberReceiptDetail = 0;
                            BigDecimal sumCash = BigDecimal.ZERO;
                            List<SalesInfo> receiptSalesInfoList = new ArrayList<>();

                            Time time = new Time(r.nextLong() % date.getTime());
                            Integer currentReceiptDetailCount = addDeviation(receiptDetailCount, 0.25, r);
                            Set<Integer> usedItems = new HashSet<>();
                            while(currentReceiptDetailCount >= numberReceiptDetail && usedItems.size() < itemZReportInfoList.size()) {
                                int currentItemIndex = r.nextInt(itemZReportInfoList.size());
                                ItemZReportInfo itemZReportInfo = itemZReportInfoList.get(currentItemIndex);
                                BigDecimal currentBalanceSkuStock = itemZReportInfo.count;
                                if ((currentBalanceSkuStock.doubleValue() > 0) && (departmentStoreObject.equals(itemZReportInfo.departmentStore))) {
                                    BigDecimal quantityReceiptDetail;
                                    if (itemZReportInfo.splitItem)
                                        quantityReceiptDetail = currentBalanceSkuStock.doubleValue() <= 0.005 ? currentBalanceSkuStock : BigDecimal.valueOf((double) Math.round(r.nextDouble() * currentBalanceSkuStock.doubleValue() / 5 * 1000) / 1000);
                                    else
                                        quantityReceiptDetail = BigDecimal.valueOf(Math.ceil(currentBalanceSkuStock.doubleValue() / 5) == 1 ? 1.0 : r.nextInt((int) Math.ceil(currentBalanceSkuStock.doubleValue() / 5)));
                                    if ((quantityReceiptDetail.doubleValue() > 0) && (currentReceiptDetailCount >= numberReceiptDetail)) {
                                        BigDecimal sumReceiptDetail = quantityReceiptDetail.multiply(itemZReportInfo.price == null ? BigDecimal.ZERO : itemZReportInfo.price);
                                        numberReceiptDetail++;
                                        sumCash = safeAdd(sumCash, sumReceiptDetail);
                                        BigDecimal discountSumReceiptDetail = BigDecimal.valueOf(r.nextDouble() > 0.8 ? (sumReceiptDetail.doubleValue() * r.nextInt(10) / 100) : 0);
                                        Integer numberCashRegister = (Integer) findProperty("npp[Machinery]").read(context, cashRegisterObject);
                                        Integer nppGroupMachinery = (Integer) findProperty("nppGroupMachinery[Machinery]").read(context, cashRegisterObject);
                                        SalesInfo salesInfo = new SalesInfo(false, nppGroupMachinery, numberCashRegister, numberZReport, date, time, receiptNumber, date,
                                                time, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, itemZReportInfo.barcode, null, null,
                                                null, quantityReceiptDetail, itemZReportInfo.price, sumReceiptDetail, discountSumReceiptDetail, null, null,
                                                numberReceiptDetail, null, null);
                                        receiptSalesInfoList.add(salesInfo);
                                        itemZReportInfo.count = safeSubtract(itemZReportInfo.count, quantityReceiptDetail);
                                    }
                                }
                                usedItems.add(currentItemIndex);
                            }
                            for (SalesInfo s : receiptSalesInfoList) {
                                s.sumCash = sumCash;
                            }
                            salesInfoList.addAll(receiptSalesInfoList);
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
        try {
            EquipmentServer equipmentServer = context.getLogicsInstance().getCustomObject(EquipmentServer.class);
            String res = equipmentServer.sendSalesInfoNonRemote(context.stack, salesInfoList, "equServer1", null);
            if (res != null) {
                throw new RuntimeException(res);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Integer addDeviation(Integer value, Double percent, Random r) {
        return value != null ? value + (int) (value * r.nextDouble() * percent * (r.nextDouble() > 0.5 ? 1 : -1)) : 1;
    }

    protected BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }
    
    protected BigDecimal safeSubtract(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else
            return (operand1 == null ? operand2.negate() : (operand2 == null ? operand1 : operand1.subtract((operand2))));
    }

    protected String trim(String input) {
        return input == null ? null : input.trim();
    }
    
    private class ItemZReportInfo {
        String barcode;
        BigDecimal count;
        BigDecimal price;
        Boolean splitItem;
        DataObject departmentStore;

        public ItemZReportInfo(String barcode, BigDecimal count, BigDecimal price, Boolean splitItem, DataObject departmentStore) {
            this.barcode = barcode;
            this.count = count;
            this.price = price;
            this.splitItem = splitItem;
            this.departmentStore = departmentStore;
        }
    }
}