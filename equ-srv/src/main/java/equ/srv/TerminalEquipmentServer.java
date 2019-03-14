package equ.srv;

import com.google.common.base.Throwables;
import equ.api.terminal.*;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.classes.DateTimeClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.language.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.IsClassProperty;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.awt.*;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.trim;

public class TerminalEquipmentServer {

    static ScriptingLogicsModule terminalOrderLM;

    public static void init(BusinessLogics BL) {
        terminalOrderLM = BL.getModule("TerminalOrder");
    }

    public static List<TerminalOrder> readTerminalOrderList(DataSession session, ObjectValue customerStockObject) throws RemoteException, SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        Map<String, TerminalOrder> terminalOrderMap = new HashMap<>();

        if (terminalOrderLM != null) {
            try {
                KeyExpr orderExpr = new KeyExpr("terminalOrder");
                KeyExpr orderDetailExpr = new KeyExpr("terminalOrderDetail");
                ImRevMap<Object, KeyExpr> orderKeys = MapFact.toRevMap((Object) "TerminalOrder", orderExpr, "TerminalOrderDetail", orderDetailExpr);
                QueryBuilder<Object, Object> orderQuery = new QueryBuilder<>(orderKeys);
                String[] orderNames = new String[]{"dateOrder", "numberOrder", "idSupplierOrder"};
                LCP<?>[] orderProperties = terminalOrderLM.findProperties("date[TerminalOrder]", "number[TerminalOrder]", "idSupplier[TerminalOrder]");
                for (int i = 0; i < orderProperties.length; i++) {
                    orderQuery.addProperty(orderNames[i], orderProperties[i].getExpr(orderExpr));
                }
                String[] orderDetailNames = new String[]{"idBarcodeSkuOrderDetail", "idSkuOrderDetail", "nameSkuOrderDetail", "priceOrderDetail",
                        "quantityOrderDetail", "nameManufacturerSkuOrderDetail", "passScalesSkuOrderDetail", "minDeviationQuantityOrderDetail",
                        "maxDeviationQuantityOrderDetail", "minDeviationPriceOrderDetail", "maxDeviationPriceOrderDetail",
                        "color", "headField1", "headField2", "headField3", "posField1", "posField2", "posField3"};
                LCP<?>[] orderDetailProperties = terminalOrderLM.findProperties("idBarcodeSku[TerminalOrderDetail]", "idSku[TerminalOrderDetail]",
                        "nameSku[TerminalOrderDetail]", "price[TerminalOrderDetail]", "orderQuantity[TerminalOrderDetail]",
                        "nameManufacturerSku[TerminalOrderDetail]", "passScalesSku[TerminalOrderDetail]", "minDeviationQuantity[TerminalOrderDetail]",
                        "maxDeviationQuantity[TerminalOrderDetail]", "minDeviationPrice[TerminalOrderDetail]", "maxDeviationPrice[TerminalOrderDetail]",
                        "color[TerminalOrderDetail]", "headField1[TerminalOrderDetail]", "headField2[TerminalOrderDetail]", "headField3[TerminalOrderDetail]",
                        "posField1[TerminalOrderDetail]", "posField2[TerminalOrderDetail]", "posField3[TerminalOrderDetail]");
                for (int i = 0; i < orderDetailProperties.length; i++) {
                    orderQuery.addProperty(orderDetailNames[i], orderDetailProperties[i].getExpr(orderDetailExpr));
                }

                orderQuery.and(terminalOrderLM.findProperty("filter[TerminalOrder, Stock]").getExpr(orderExpr, customerStockObject.getExpr()).getWhere());
                orderQuery.and((terminalOrderLM.findProperty("isOpened[TerminalOrder]")).getExpr(orderExpr).getWhere());
                orderQuery.and(terminalOrderLM.findProperty("order[TerminalOrderDetail]").getExpr(orderDetailExpr).compare(orderExpr, Compare.EQUALS));
                orderQuery.and(terminalOrderLM.findProperty("number[TerminalOrder]").getExpr(orderExpr).getWhere());
                orderQuery.and(terminalOrderLM.findProperty("idBarcodeSku[TerminalOrderDetail]").getExpr(orderDetailExpr).getWhere());

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
                    String color = formatColor((Color) entry.get("color"));

                    String headField1 = (String) entry.get("headField1");
                    String headField2 = (String) entry.get("headField2");
                    String headField3 = (String) entry.get("headField3");
                    String posField1 = (String) entry.get("posField1");
                    String posField2 = (String) entry.get("posField2");
                    String posField3 = (String) entry.get("posField3");
                    String key = numberOrder + "/" + barcode;
                    TerminalOrder terminalOrder = terminalOrderMap.get(key);
                    if (terminalOrder != null)
                        terminalOrder.quantity = safeAdd(terminalOrder.quantity, quantity);
                    else
                        terminalOrderMap.put(key, new TerminalOrder(dateOrder, numberOrder, idSupplier, barcode, idItem, name, price,
                                quantity, minQuantity, maxQuantity, minPrice, maxPrice, nameManufacturer, weight, color,
                                headField1, headField2, headField3, posField1, posField2, posField3));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return new ArrayList<>(terminalOrderMap.values());
    }

    private static String formatColor(Color color) {
        return color == null ? null : String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
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

    public static List<TerminalDocumentType> readTerminalDocumentTypeList(DataSession session, BusinessLogics BL, DataObject userObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
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
            if(userObject != null) {
                query.and(terminalLM.findProperty("notSkip[TerminalDocumentType, CustomUser]").getExpr(terminalDocumentTypeExpr, userObject.getExpr()).getWhere());
            } else {
                query.and(terminalLM.findProperty("notSkip[TerminalDocumentType]").getExpr(terminalDocumentTypeExpr).getWhere());
            }
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

    public static List<TerminalLegalEntity> readCustomANAList(DataSession session, BusinessLogics BL, DataObject userObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalLegalEntity> customANAList = new ArrayList<>();
        ScriptingLogicsModule terminalLM = BL.getModule("Terminal");
        if (terminalLM != null) {

            KeyExpr terminalHandbookTypeExpr = new KeyExpr("terminalHandbookType");
            ImRevMap<Object, KeyExpr> terminalHandbookTypeKeys = MapFact.singletonRev((Object) "terminalHandbookType", terminalHandbookTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(terminalHandbookTypeKeys);
            String[] names = new String[]{"exportId", "name", "propertyID", "propertyName", "filterProperty", "extInfoProperty"};
            LCP<?>[] properties = terminalLM.findProperties("exportId[TerminalHandbookType]", "name[TerminalHandbookType]",
                    "canonicalNamePropertyID[TerminalHandbookType]", "canonicalNamePropertyName[TerminalHandbookType]",
                    "canonicalNameFilterProperty[TerminalHandbookType]", "canonicalNameExtInfoProperty[TerminalHandbookType]");
            for (int i = 0, propertiesLength = properties.length; i < propertiesLength; i++) {
                query.addProperty(names[i], properties[i].getExpr(terminalHandbookTypeExpr));
            }
            query.and(terminalLM.findProperty("exportId[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            query.and(terminalLM.findProperty("canonicalNamePropertyID[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            query.and(terminalLM.findProperty("canonicalNamePropertyName[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String prefix = trim((String) entry.get("exportId"));
                LCP propertyID = (LCP<?>) BL.findSafeProperty(trim((String) entry.get("propertyID")));
                LCP propertyName = (LCP<?>) BL.findSafeProperty(trim((String) entry.get("propertyName")));
                String canonicalNameFilterProperty = trim((String) entry.get("filterProperty"));
                LCP filterProperty = canonicalNameFilterProperty != null ? (LCP<?>) BL.findSafeProperty(canonicalNameFilterProperty) : null;
                String canonicalNameExtInfoProperty = trim((String) entry.get("extInfoProperty"));
                LCP extInfoProperty = canonicalNameExtInfoProperty != null ? (LCP<?>) BL.findSafeProperty(canonicalNameExtInfoProperty) : null;

                if(propertyID != null && propertyName != null) {
                    ImOrderSet<PropertyInterface> interfaces = propertyID.listInterfaces;
                    if (interfaces.size() == 1) {
                        KeyExpr customANAExpr = new KeyExpr("customANA");
                        ImRevMap<Object, KeyExpr> customANAKeys = MapFact.singletonRev((Object) "customANA", customANAExpr);
                        QueryBuilder<Object, Object> customANAQuery = new QueryBuilder<>(customANAKeys);
                        customANAQuery.addProperty("id", propertyID.getExpr(customANAExpr));
                        customANAQuery.addProperty("name", propertyName.getExpr(customANAExpr));
                        if(extInfoProperty != null) {
                            customANAQuery.addProperty("extInfo", extInfoProperty.getExpr(customANAExpr));
                        }
                        if (filterProperty != null) {
                            switch (filterProperty.listInterfaces.size()) {
                                case 1:
                                    customANAQuery.and(filterProperty.getExpr(customANAExpr).getWhere());
                                    break;
                                case 2:
                                    //небольшой хак, для случая, когда второй параметр в фильтре - пользователь
                                    Object interfaceObject = filterProperty.listInterfaces.get(1);
                                    if (interfaceObject instanceof ClassPropertyInterface) {
                                        if (IsClassProperty.fitClass(userObject.objectClass, ((ClassPropertyInterface) interfaceObject).interfaceClass))
                                            customANAQuery.and(filterProperty.getExpr(customANAExpr, userObject.getExpr()).getWhere());
                                    } else { //если не data property и второй параметр не пользователь, то фильтр отсечёт всё
                                        customANAQuery.and(filterProperty.getExpr(customANAExpr, userObject.getExpr()).getWhere());
                                    }
                                    break;
                            }
                        }

                        customANAQuery.and(propertyID.getExpr(customANAExpr).getWhere());
                        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> customANAResult = customANAQuery.execute(session);
                        for (ImMap<Object, Object> customANAEntry : customANAResult.values()) {
                            String idCustomANA = trim((String) customANAEntry.get("id"));
                            String nameCustomANA = trim((String) customANAEntry.get("name"));
                            String extInfo = trim((String) customANAEntry.get("extInfo"));
                            customANAList.add(new TerminalLegalEntity(prefix + idCustomANA, nameCustomANA, extInfo));
                        }
                    }
                }
            }
        }
        return customANAList;
    }

    public static List<TerminalLegalEntity> readTerminalLegalEntityList(DataSession session, BusinessLogics BL) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalLegalEntity> terminalLegalEntityList = new ArrayList<>();
        ScriptingLogicsModule terminalLM = BL.getModule("EquipmentTerminal");
        if (terminalLM != null) {
            KeyExpr legalEntityExpr = new KeyExpr("legalEntity");
            ImRevMap<Object, KeyExpr> legalEntityKeys = MapFact.singletonRev((Object) "LegalEntity", legalEntityExpr);
            QueryBuilder<Object, Object> legalEntityQuery = new QueryBuilder<>(legalEntityKeys);
            String[] legalEntityNames = new String[]{"idLegalEntity", "nameLegalEntity"};
            LCP<?>[] legalEntityProperties = terminalLM.findProperties("id[LegalEntity]", "name[LegalEntity]");
            for (int i = 0; i < legalEntityProperties.length; i++) {
                legalEntityQuery.addProperty(legalEntityNames[i], legalEntityProperties[i].getExpr(legalEntityExpr));
            }
            legalEntityQuery.and(terminalLM.findProperty("id[LegalEntity]").getExpr(legalEntityExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> legalEntityResult = legalEntityQuery.execute(session);
            for (ImMap<Object, Object> entry : legalEntityResult.values()) {
                String idLegalEntity = trim((String) entry.get("idLegalEntity"));
                String nameLegalEntity = trim((String) entry.get("nameLegalEntity"));
                terminalLegalEntityList.add(new TerminalLegalEntity(idLegalEntity, nameLegalEntity, null));
            }
        }
        return terminalLegalEntityList;
    }

    private static BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }
}