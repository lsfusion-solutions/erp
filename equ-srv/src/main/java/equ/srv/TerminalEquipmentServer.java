package equ.srv;

import com.google.common.base.Throwables;
import equ.api.terminal.TerminalOrder;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.trim;

public class TerminalEquipmentServer {

    static ScriptingLogicsModule purchaseInvoiceAgreementLM;
    static ScriptingLogicsModule purchaseOrderLM;
    static ScriptingLogicsModule terminalHandlerLM;

    public static void init(BusinessLogics BL) {
        purchaseInvoiceAgreementLM = BL.getModule("PurchaseInvoiceAgreement");
        purchaseOrderLM = BL.getModule("PurchaseOrder");
        terminalHandlerLM = BL.getModule("TerminalHandler");
    }

    public static List<TerminalOrder> readTerminalOrderList(DataSession session, ObjectValue customerStockObject) throws RemoteException, SQLException {
        List<TerminalOrder> terminalOrderList = new ArrayList<>();
        if (purchaseOrderLM != null) {
            try {
                KeyExpr orderExpr = new KeyExpr("order");
                KeyExpr orderDetailExpr = new KeyExpr("orderDetail");
                ImRevMap<Object, KeyExpr> orderKeys = MapFact.toRevMap((Object) "Order", orderExpr, "OrderDetail", orderDetailExpr);
                QueryBuilder<Object, Object> orderQuery = new QueryBuilder<>(orderKeys);
                String[] orderNames = new String[]{"dateOrder", "numberOrder", "idSupplierOrder"};
                LCP<?>[] orderProperties = purchaseOrderLM.findProperties("date[Purchase.Order]", "number[Purchase.Order]", "idSupplier[Purchase.Order]");
                for (int i = 0; i < orderProperties.length; i++) {
                    orderQuery.addProperty(orderNames[i], orderProperties[i].getExpr(orderExpr));
                }
                String[] orderDetailNames = new String[]{"idBarcodeSkuOrderDetail", "idSkuOrderDetail", "nameSkuOrderDetail", "priceOrderDetail",
                        "quantityOrderDetail"};
                LCP<?>[] orderDetailProperties = purchaseOrderLM.findProperties("overTerminalBarcode[Purchase.OrderDetail]", "idSku[Purchase.OrderDetail]",
                        "nameSku[Purchase.OrderDetail]", "price[Purchase.OrderDetail]", "quantity[Purchase.OrderDetail]");
                for (int i = 0; i < orderDetailProperties.length; i++) {
                    orderQuery.addProperty(orderDetailNames[i], orderDetailProperties[i].getExpr(orderDetailExpr));
                }
                if(terminalHandlerLM != null) {
                    String[] extraNames = new String[]{"nameManufacturerSkuOrderDetail", "passScalesSkuOrderDetail"};
                    LCP<?>[] extraProperties = terminalHandlerLM.findProperties("nameManufacturerSku[Purchase.OrderDetail]", "passScalesSku[Purchase.OrderDetail]");
                    for (int i = 0; i < extraProperties.length; i++) {
                        orderQuery.addProperty(extraNames[i], extraProperties[i].getExpr(orderDetailExpr));
                    }
                }
                if(purchaseInvoiceAgreementLM != null) {
                    String[] deviationNames = new String[]{"minDeviationQuantityOrderDetail", "maxDeviationQuantityOrderDetail",
                            "minDeviationPriceOrderDetail", "maxDeviationPriceOrderDetail"};
                    LCP<?>[]  deviationProperties = purchaseInvoiceAgreementLM.findProperties("minDeviationQuantity[Purchase.OrderDetail]", "maxDeviationQuantity[Purchase.OrderDetail]",
                            "minDeviationPrice[Purchase.OrderDetail]", "maxDeviationPrice[Purchase.OrderDetail]");
                    for (int i = 0; i < deviationNames.length; i++) {
                        orderQuery.addProperty(deviationNames[i], deviationProperties[i].getExpr(orderDetailExpr));
                    }
                }

                orderQuery.and(purchaseOrderLM.findProperty("isOpened[Purchase.Order]").getExpr(orderExpr).getWhere());
                orderQuery.and(purchaseOrderLM.findProperty("customerStock[Purchase.Order]").getExpr(orderExpr).compare(
                        customerStockObject.getExpr(), Compare.EQUALS));
                orderQuery.and(purchaseOrderLM.findProperty("order[Purchase.OrderDetail]").getExpr(orderDetailExpr).compare(orderExpr, Compare.EQUALS));
                orderQuery.and(purchaseOrderLM.findProperty("number[Purchase.Order]").getExpr(orderExpr).getWhere());
                orderQuery.and(purchaseOrderLM.findProperty("overTerminalBarcode[Purchase.OrderDetail]").getExpr(orderDetailExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> orderResult = orderQuery.execute(session);
                for (ImMap<Object, Object> entry : orderResult.values()) {
                    Date dateOrder = (Date) entry.get("dateOrder");
                    String numberOrder = trim((String) entry.get("numberOrder"));
                    String idSupplier = trim((String) entry.get("idSupplierOrder"));
                    String barcode = trim((String) entry.get("idBarcodeSkuOrderDetail"));
                    String idItem = trim((String) entry.get("idSkuOrderDetail"));
                    String name = trim((String) entry.get("nameSkuOrderDetail"));
                    BigDecimal price = (BigDecimal) entry.get("priceOrderDetail");
                    BigDecimal quantity = (BigDecimal) entry.get("quantityOrderDetail");
                    BigDecimal minQuantity = (BigDecimal) entry.get("minDeviationQuantityOrderDetail");
                    BigDecimal maxQuantity = (BigDecimal) entry.get("maxDeviationQuantityOrderDetail");
                    BigDecimal minPrice = (BigDecimal) entry.get("minDeviationPriceOrderDetail");
                    BigDecimal maxPrice = (BigDecimal) entry.get("maxDeviationPriceOrderDetail");
                    String nameManufacturer = (String) entry.get("nameManufacturerSkuOrderDetail");
                    String weight = entry.get("passScalesSkuOrderDetail") != null ? "1" : "0";
                    terminalOrderList.add(new TerminalOrder(dateOrder, numberOrder, idSupplier, barcode, idItem, name, price,
                            quantity, minQuantity, maxQuantity, minPrice, maxPrice, nameManufacturer, weight));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return terminalOrderList;
    }
}