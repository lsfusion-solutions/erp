package equ.srv;

import com.google.common.base.Throwables;
import equ.api.terminal.TerminalAssortment;
import equ.api.terminal.TerminalHandbookType;
import equ.api.terminal.*;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.classes.DateTimeClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
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

    public static List<TerminalAssortment> readTerminalAssortmentList(DataSession session, BusinessLogics BL, ObjectValue priceListTypeObject, ObjectValue stockGroupMachineryObject)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalAssortment> terminalAssortmentList = new ArrayList<>();
        ScriptingLogicsModule machineryPriceTransactionLM = BL.getModule("MachineryPriceTransaction");
        if (machineryPriceTransactionLM != null) {

            DataObject currentDateTimeObject = new DataObject(new Timestamp(Calendar.getInstance().getTime().getTime()), DateTimeClass.instance);

            KeyExpr skuExpr = new KeyExpr("Sku");
            KeyExpr legalEntityExpr = new KeyExpr("legalEntity");
            ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "Sku", skuExpr, "LegalEntity", legalEntityExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            query.addProperty("priceALedgerPriceListTypeSkuStockCompanyDateTime", machineryPriceTransactionLM.findProperty("priceA[LedgerPriceListType,Sku,Stock,LegalEntity,DATETIME]").getExpr(priceListTypeObject.getExpr(),
                    skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr, currentDateTimeObject.getExpr()));
            query.addProperty("idBarcodeSku", machineryPriceTransactionLM.findProperty("idBarcode[Sku]").getExpr(skuExpr));
            query.addProperty("idLegalEntity", machineryPriceTransactionLM.findProperty("id[LegalEntity]").getExpr(legalEntityExpr));
            query.and(machineryPriceTransactionLM.findProperty("id[LegalEntity]").getExpr(legalEntityExpr).getWhere());
            query.and(machineryPriceTransactionLM.findProperty("idBarcode[Sku]").getExpr(skuExpr).getWhere());
            query.and(machineryPriceTransactionLM.findProperty("priceA[LedgerPriceListType,Sku,Stock,LegalEntity,DATETIME]").getExpr(priceListTypeObject.getExpr(),
                    skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr, currentDateTimeObject.getExpr()).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String idBarcodeSku = trim((String) entry.get("idBarcodeSku"));
                String idLegalEntity = trim((String) entry.get("idLegalEntity"));
                terminalAssortmentList.add(new TerminalAssortment(idBarcodeSku, idLegalEntity));
            }
        }
        return terminalAssortmentList;
    }

    public static List<TerminalHandbookType> readTerminalHandbookTypeList(DataSession session, BusinessLogics BL) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalHandbookType> terminalHandbookTypeList = new ArrayList<>();
        ScriptingLogicsModule terminalLM = BL.getModule("Terminal");
        if(terminalLM != null) {
            KeyExpr terminalHandbookTypeExpr = new KeyExpr("terminalHandbookType");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "terminalHandbookType", terminalHandbookTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            String[] names = new String[]{"idTerminalHandbookType", "nameTerminalHandbookType"};
            LCP<?>[] properties = terminalLM.findProperties("id[TerminalHandbookType]", "name[TerminalHandbookType]");
            for (int i = 0, propertiesLength = properties.length; i < propertiesLength; i++) {
                query.addProperty(names[i], properties[i].getExpr(terminalHandbookTypeExpr));
            }
            query.and(terminalLM.findProperty("id[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String id = trim((String) entry.get("idTerminalHandbookType"));
                String name = trim((String) entry.get("nameTerminalHandbookType"));
                terminalHandbookTypeList.add(new TerminalHandbookType(id, name));
            }
        }
        return terminalHandbookTypeList;
    }

    public static List<TerminalDocumentType> readTerminalDocumentTypeList(DataSession session, BusinessLogics BL) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalDocumentType> terminalDocumentTypeList = new ArrayList<>();
        ScriptingLogicsModule terminalLM = BL.getModule("Terminal");
        if(terminalLM != null) {
            KeyExpr terminalDocumentTypeExpr = new KeyExpr("terminalDocumentType");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "terminalDocumentType", terminalDocumentTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            String[] names = new String[]{"idTerminalDocumentType", "nameTerminalDocumentType", "flagTerminalDocumentType",
                    "idTerminalHandbookType1TerminalDocumentType", "idTerminalHandbookType2TerminalDocumentType"};
            LCP<?>[] properties = terminalLM.findProperties("id[TerminalDocumentType]", "name[TerminalDocumentType]", "flag[TerminalDocumentType]",
                    "idTerminalHandbookType1[TerminalDocumentType]", "idTerminalHandbookType2[TerminalDocumentType]");
            for (int i = 0; i < properties.length; i++) {
                query.addProperty(names[i], properties[i].getExpr(terminalDocumentTypeExpr));
            }
            query.and(terminalLM.findProperty("id[TerminalDocumentType]").getExpr(terminalDocumentTypeExpr).getWhere());
            query.and(terminalLM.findProperty("notSkip[TerminalDocumentType]").getExpr(terminalDocumentTypeExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String id = trim((String) entry.get("idTerminalDocumentType"));
                String name = trim((String) entry.get("nameTerminalDocumentType"));
                Integer flag = (Integer) entry.get("flagTerminalDocumentType");
                String analytics1 = trim((String) entry.get("idTerminalHandbookType1TerminalDocumentType"));
                String analytics2 = trim((String) entry.get("idTerminalHandbookType2TerminalDocumentType"));
                terminalDocumentTypeList.add(new TerminalDocumentType(id, name, analytics1, analytics2, flag));
            }
        }
        return terminalDocumentTypeList;
    }
}