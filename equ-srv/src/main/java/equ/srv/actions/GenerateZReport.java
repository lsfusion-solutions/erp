package equ.srv.actions;

import equ.api.SalesInfo;
import equ.srv.EquipmentServer;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultIntegrationAction;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static equ.srv.EquipmentServer.*;

public class GenerateZReport extends DefaultIntegrationAction {
    private static final double deviationPercent = 0.25;

    public GenerateZReport(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        List<SalesInfo> salesInfoList = new ArrayList<>();
        try {
            ObjectValue priceListType = findProperty("priceListTypeGenerateZReport[]").readClasses(context);
            if (!(priceListType instanceof NullValue)) {
                Random r = new Random();
                Integer zReportCount = addDeviation((Integer) findProperty("averageZReportCountGenerateZReport[]").read(context), r);
                Integer receiptCount = (Integer) findProperty("averageReceiptCountGenerateZReport[]").read(context);
                Integer receiptDetailCount = (Integer) findProperty("averageReceiptDetailCountGenerateZReport[]").read(context);
                ObjectValue stockObject = findProperty("departmentStoreGenerateZReport[]").readClasses(context);

                Timestamp dateFrom = (Timestamp) findProperty("dateFromGenerateZReport[]").read(context);
                dateFrom = dateFrom == null ? new Timestamp(System.currentTimeMillis()) : dateFrom;
                Timestamp dateTo = (Timestamp) findProperty("dateToGenerateZReport[]").read(context);
                dateTo = dateTo == null ? new Timestamp(System.currentTimeMillis()) : dateTo;

                KeyExpr departmentStoreExpr = new KeyExpr("departmentStore");
                KeyExpr itemExpr = new KeyExpr("item");
                ImRevMap<Object, KeyExpr> newKeys = MapFact.toRevMap("departmentStore", departmentStoreExpr, "item", itemExpr);

                QueryBuilder<Object, Object> query = new QueryBuilder<>(newKeys);
                query.addProperty("currentBalanceSkuStock", findProperty("currentBalance[Sku,Stock]").getExpr(itemExpr, departmentStoreExpr));
                query.addProperty("priceSkuStock", findProperty("price[Sku,Stock]").getExpr(itemExpr, departmentStoreExpr));
                query.addProperty("idBarcode", findProperty("idBarcode[Sku]").getExpr(itemExpr));
                query.addProperty("split", findProperty("split[Sku]").getExpr(itemExpr));
                query.and(findProperty("currentBalance[Sku,Stock]").getExpr(itemExpr, departmentStoreExpr).getWhere());
                query.and(findProperty("price[Sku,Stock]").getExpr(itemExpr, departmentStoreExpr).getWhere());
                query.and(findProperty("idBarcode[Sku]").getExpr(itemExpr, departmentStoreExpr).getWhere());
                if(stockObject instanceof DataObject)
                    query.and(departmentStoreExpr.compare((DataObject) stockObject, Compare.EQUALS));
                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(context);

                List<ItemZReportInfo> itemZReportInfoList = new ArrayList<>();
                List<DataObject> departmentStoreList = new ArrayList<>();
                for (int i = 0, size = result.size(); i < size; i++) {
                    ImMap<Object, DataObject> resultKeys = result.getKey(i);
                    ImMap<Object, ObjectValue> resultValues = result.getValue(i);
                    BigDecimal currentBalanceSkuStock = (BigDecimal) resultValues.get("currentBalanceSkuStock").getValue();
                    if (currentBalanceSkuStock.doubleValue() > 0) {
                        DataObject departmentStore = resultKeys.get("departmentStore");
                        if ((departmentStore != null) && (!departmentStoreList.contains(departmentStore)))
                            departmentStoreList.add(departmentStore);
                        BigDecimal priceSkuStock = (BigDecimal) resultValues.get("priceSkuStock").getValue();
                        priceSkuStock = priceSkuStock.setScale(2, RoundingMode.HALF_DOWN); //на случай, если цена имеет больше 2 знаков после запятой
                        String barcodeItem = (String) resultValues.get("idBarcode").getValue();
                        boolean splitItem = resultValues.get("split").getValue() != null;
                        itemZReportInfoList.add(new ItemZReportInfo(barcodeItem, currentBalanceSkuStock, priceSkuStock, splitItem, departmentStore));
                    }
                }

                List<CashRegisterInfo> cashRegisterInfoList = new ArrayList<>();
                for (DataObject departmentStoreObject : departmentStoreList) {
                    KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");

                    ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("cashRegister", cashRegisterExpr);
                    QueryBuilder<Object, Object> cashRegisterQuery = new QueryBuilder<>(keys);
                    cashRegisterQuery.addProperty("maxNumberZReport", findProperty("maxNumberZReport[CashRegister]").getExpr(cashRegisterExpr));
                    cashRegisterQuery.addProperty("nppMachinery", findProperty("npp[Machinery]").getExpr(cashRegisterExpr));
                    cashRegisterQuery.addProperty("nppGroupMachinery", findProperty("nppGroupMachinery[Machinery]").getExpr(cashRegisterExpr));
                    cashRegisterQuery.and(findProperty("npp[Machinery]").getExpr(cashRegisterExpr).getWhere());
                    cashRegisterQuery.and(findProperty("stock[CashRegister]").getExpr(cashRegisterExpr).compare(departmentStoreObject.getExpr(), Compare.EQUALS));

                    ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> cashRegisterResult = cashRegisterQuery.executeClasses(context);

                    for (int i = 0; i < cashRegisterResult.size(); i++) {
                        DataObject cashRegisterObject = cashRegisterResult.getKey(i).getValue(0);
                        Integer maxNumberZReport = (Integer) cashRegisterResult.getValue(i).get("maxNumberZReport").getValue();
                        Integer nppMachinery = (Integer) cashRegisterResult.getValue(i).get("nppMachinery").getValue();
                        Integer nppGroupMachinery = (Integer) cashRegisterResult.getValue(i).get("nppGroupMachinery").getValue();
                        cashRegisterInfoList.add(new CashRegisterInfo(cashRegisterObject, departmentStoreObject, maxNumberZReport,
                                nppMachinery, nppGroupMachinery));
                    }
                }

                List<String> discountCardList = new ArrayList<>();

                ScriptingLogicsModule zReportDiscountCardLM = context.getBL().getModule("ZReportDiscountCard");
                if(zReportDiscountCardLM != null) {
                    KeyExpr discountCardExpr = new KeyExpr("discountCard");

                    ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("discountCard", discountCardExpr);
                    QueryBuilder<Object, Object> discountCardQuery = new QueryBuilder<>(keys);
                    discountCardQuery.addProperty("seriesNumber", zReportDiscountCardLM.findProperty("seriesNumber[DiscountCard]").getExpr(discountCardExpr));
                    discountCardQuery.and(zReportDiscountCardLM.findProperty("seriesNumber[DiscountCard]").getExpr(discountCardExpr).getWhere());
                    discountCardQuery.and(zReportDiscountCardLM.findProperty("firstNameContact[DiscountCard]").getExpr(discountCardExpr).getWhere());

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> discountCardResult = discountCardQuery.execute(context);
                    for (ImMap<Object, Object> entry : discountCardResult.values()) {
                        discountCardList.add((String) entry.get("seriesNumber"));
                    }
                }

                if (!cashRegisterInfoList.isEmpty()) {
                    Map<String, DataObject> numberZReportCashRegisterMap = new HashMap<>();

                    for (int z = 1; z <= zReportCount; z++) {

                        CashRegisterInfo cashRegister = cashRegisterInfoList.get(r.nextInt(cashRegisterInfoList.size()));
                        String numberZReport = null;
                        while (numberZReport == null || (numberZReportCashRegisterMap.containsKey(numberZReport) && numberZReportCashRegisterMap.containsValue(cashRegister.cashRegisterObject)))
                            numberZReport = String.valueOf((cashRegister.maxNumberZReport == null ? 0 : cashRegister.maxNumberZReport) + (zReportCount < 1 ? 0 : r.nextInt(zReportCount)) + 1);
                        if (!numberZReportCashRegisterMap.containsKey(numberZReport))
                            numberZReportCashRegisterMap.put(numberZReport, cashRegister.cashRegisterObject);
                        Timestamp dateTime = dateFrom.getTime() <= dateTo.getTime() ? dateFrom : (new Timestamp(dateFrom.getTime() + Math.abs(r.nextLong() % (dateTo.getTime() - dateFrom.getTime()))));
                        LocalDate date = dateTime.toLocalDateTime().toLocalDate();
                        BigDecimal discountSum = null;
                        for (int receiptNumber = 1; receiptNumber <= addDeviation(receiptCount, r); receiptNumber++) {

                            Integer numberReceiptDetail = 0;
                            BigDecimal sumCash = BigDecimal.ZERO;
                            BigDecimal sumCard = BigDecimal.ZERO;
                            List<SalesInfo> receiptSalesInfoList = new ArrayList<>();

                            LocalTime time = sqlTimeToLocalTime(new Time(r.nextLong() % dateTime.getTime()));
                            Integer currentReceiptDetailCount = addDeviation(receiptDetailCount, r);
                            Set<Integer> usedItems = new HashSet<>();
                            while(currentReceiptDetailCount >= numberReceiptDetail && usedItems.size() < itemZReportInfoList.size()) {
                                int currentItemIndex = r.nextInt(itemZReportInfoList.size());
                                ItemZReportInfo item = itemZReportInfoList.get(currentItemIndex);
                                BigDecimal currentBalanceSkuStock = item.count;
                                if ((currentBalanceSkuStock.doubleValue() > 0) && (cashRegister.departmentStoreObject.equals(item.departmentStore))) {
                                    BigDecimal quantityReceiptDetail;
                                    if (item.splitItem)
                                        quantityReceiptDetail = currentBalanceSkuStock.doubleValue() <= 0.005 ? currentBalanceSkuStock : BigDecimal.valueOf((double) Math.round(r.nextDouble() * currentBalanceSkuStock.doubleValue() / 5 * 1000) / 1000);
                                    else
                                        quantityReceiptDetail = BigDecimal.valueOf(Math.ceil(currentBalanceSkuStock.doubleValue() / 5) == 1 ? 1.0 : r.nextInt((int) Math.ceil(currentBalanceSkuStock.doubleValue() / 5)));
                                    if ((quantityReceiptDetail.doubleValue() > 0) && (currentReceiptDetailCount >= numberReceiptDetail)) {
                                        BigDecimal sumReceiptDetail = item.price == null ? null : safeMultiply(quantityReceiptDetail, item.price);
                                        numberReceiptDetail++;
                                        if(r.nextBoolean())
                                            sumCash = safeAdd(sumCash, sumReceiptDetail);
                                        else
                                            sumCard = safeAdd(sumCard, sumReceiptDetail);
                                        BigDecimal discountSumReceiptDetail = r.nextDouble() > 0.8 ? safeDivide(safeMultiply(sumReceiptDetail, r.nextInt(10)), 100, 2) : BigDecimal.ZERO;
                                        discountSum = safeAdd(discountSum, discountSumReceiptDetail);
                                        sumReceiptDetail = safeSubtract(sumReceiptDetail, discountSumReceiptDetail);
                                        receiptSalesInfoList.add(new SalesInfo(false, false, cashRegister.nppGroupMachinery, cashRegister.nppMachinery,
                                                numberZReport, date, time, receiptNumber, date, time, null, null, null, BigDecimal.ZERO,
                                                BigDecimal.ZERO, null, null, item.barcode, null, null, null, quantityReceiptDetail,
                                                item.price, sumReceiptDetail, null, discountSumReceiptDetail, null, null, numberReceiptDetail,
                                                null, null, false, null, null));
                                        item.count = safeSubtract(item.count, quantityReceiptDetail);
                                    }
                                }
                                usedItems.add(currentItemIndex);
                            }

                            String seriesNumberDiscountCard = discountSum != null && !discountCardList.isEmpty() ? discountCardList.get(r.nextInt(discountCardList.size())) : null;
                            for (SalesInfo s : receiptSalesInfoList) {
                                s.sumCash = sumCash;
                                s.sumCard = sumCard;
                                s.seriesNumberDiscountCard = seriesNumberDiscountCard;
                            }
                            salesInfoList.addAll(receiptSalesInfoList);
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
        EquipmentServer equipmentServer = context.getLogicsInstance().getCustomObject(EquipmentServer.class);
        String res = equipmentServer.sendSalesInfoNonRemote(context.stack, salesInfoList, "equServer1", null);
        if (res != null) {
            throw new RuntimeException(res);
        }
    }

    private Integer addDeviation(Integer value, Random r) {
        return value != null ? value + (int) (value * r.nextDouble() * deviationPercent * (r.nextDouble() > 0.5 ? 1 : -1)) : 1;
    }

    private class ItemZReportInfo {
        String barcode;
        BigDecimal count;
        BigDecimal price;
        Boolean splitItem;
        DataObject departmentStore;

        ItemZReportInfo(String barcode, BigDecimal count, BigDecimal price, Boolean splitItem, DataObject departmentStore) {
            this.barcode = barcode;
            this.count = count;
            this.price = price;
            this.splitItem = splitItem;
            this.departmentStore = departmentStore;
        }
    }

    private class CashRegisterInfo {
        DataObject cashRegisterObject;
        DataObject departmentStoreObject;
        Integer maxNumberZReport;
        Integer nppMachinery;
        Integer nppGroupMachinery;

        CashRegisterInfo(DataObject cashRegisterObject, DataObject departmentStoreObject, Integer maxNumberZReport, Integer nppMachinery, Integer nppGroupMachinery) {
            this.cashRegisterObject = cashRegisterObject;
            this.departmentStoreObject = departmentStoreObject;
            this.maxNumberZReport = maxNumberZReport;
            this.nppMachinery = nppMachinery;
            this.nppGroupMachinery = nppGroupMachinery;
        }
    }
}