package equ.srv;

import com.google.common.base.Throwables;
import equ.api.terminal.*;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.Employee;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.data.time.DateTimeClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.property.classes.IsClassProperty;
import lsfusion.server.logics.property.oraction.PropertyInterface;

import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static lsfusion.erp.integration.DefaultIntegrationAction.*;
import static org.apache.commons.lang3.StringUtils.trim;

public class TerminalEquipmentServer {

    static ScriptingLogicsModule terminalOrderLM;
    static ScriptingLogicsModule terminalHandlerLM;

    public static void init(BusinessLogics BL) {
        terminalOrderLM = BL.getModule("TerminalOrder");
        terminalHandlerLM = BL.getModule("TerminalHandler");
    }

    public static List<ServerTerminalOrder> readTerminalOrderList(DataSession session, ObjectValue customerStockObject, DataObject userObject) throws SQLException {
        Map<String, ServerTerminalOrder> terminalOrderMap = new HashMap<>();

        if (terminalOrderLM != null) {
            try {
                KeyExpr orderExpr = new KeyExpr("terminalOrder");
                KeyExpr orderDetailExpr = new KeyExpr("terminalOrderDetail");
                ImRevMap<Object, KeyExpr> orderKeys = MapFact.toRevMap("TerminalOrder", orderExpr, "TerminalOrderDetail", orderDetailExpr);
                QueryBuilder<Object, Object> orderQuery = new QueryBuilder<>(orderKeys);
                String[] orderNames = new String[]{"dateOrder", "numberOrder", "idSupplierOrder"};
                LP<?>[] orderProperties = terminalOrderLM.findProperties("date[TerminalOrder]", "number[TerminalOrder]", "idSupplier[TerminalOrder]");
                for (int i = 0; i < orderProperties.length; i++) {
                    orderQuery.addProperty(orderNames[i], orderProperties[i].getExpr(orderExpr));
                }
                String[] orderDetailNames = new String[]{"idBarcodeSkuOrderDetail", "idSkuOrderDetail", "nameSkuOrderDetail", "priceOrderDetail",
                        "quantityOrderDetail", "nameManufacturerSkuOrderDetail", "passScalesSkuOrderDetail", "minDeviationQuantityOrderDetail",
                        "maxDeviationQuantityOrderDetail", "minDeviationPriceOrderDetail", "maxDeviationPriceOrderDetail",
                        "color", "headField1", "headField2", "headField3", "posField1", "posField2", "posField3",
                        "minDeviationDate", "maxDeviationDate", "vop"};
                LP<?>[] orderDetailProperties = terminalOrderLM.findProperties("idBarcodeSku[TerminalOrderDetail]", "idSku[TerminalOrderDetail]",
                        "nameSku[TerminalOrderDetail]", "price[TerminalOrderDetail]", "orderQuantity[TerminalOrderDetail]",
                        "nameManufacturerSku[TerminalOrderDetail]", "passScalesSku[TerminalOrderDetail]", "minDeviationQuantity[TerminalOrderDetail]",
                        "maxDeviationQuantity[TerminalOrderDetail]", "minDeviationPrice[TerminalOrderDetail]", "maxDeviationPrice[TerminalOrderDetail]",
                        "color[TerminalOrderDetail]", "headField1[TerminalOrderDetail]", "headField2[TerminalOrderDetail]", "headField3[TerminalOrderDetail]",
                        "posField1[TerminalOrderDetail]", "posField2[TerminalOrderDetail]", "posField3[TerminalOrderDetail]",
                        "minDeviationDate[TerminalOrderDetail]", "maxDeviationDate[TerminalOrderDetail]", "vop[TerminalOrderDetail]");
                for (int i = 0; i < orderDetailProperties.length; i++) {
                    orderQuery.addProperty(orderDetailNames[i], orderDetailProperties[i].getExpr(orderDetailExpr));
                }

                orderQuery.and(terminalOrderLM.findProperty("filter[TerminalOrder, Stock]").getExpr(orderExpr, customerStockObject.getExpr()).getWhere());
                orderQuery.and(terminalOrderLM.findProperty("checkUser[TerminalOrder, Employee]").getExpr(orderExpr, userObject.getExpr()).getWhere());
                orderQuery.and((terminalOrderLM.findProperty("isOpened[TerminalOrder]")).getExpr(orderExpr).getWhere());
                orderQuery.and(terminalOrderLM.findProperty("order[TerminalOrderDetail]").getExpr(orderDetailExpr).compare(orderExpr, Compare.EQUALS));
                orderQuery.and(terminalOrderLM.findProperty("number[TerminalOrder]").getExpr(orderExpr).getWhere());
                orderQuery.and(terminalOrderLM.findProperty("idBarcodeSku[TerminalOrderDetail]").getExpr(orderDetailExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> orderResult = orderQuery.execute(session);
                for (ImMap<Object, Object> entry : orderResult.values()) {
                    LocalDate dateOrder = (LocalDate) entry.get("dateOrder");
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
                    String minDeviationDate = formatDate((LocalDate) entry.get("minDeviationDate"));
                    String maxDeviationDate = formatDate((LocalDate) entry.get("maxDeviationDate"));
                    String vop = (String) entry.get("vop");
                    String key = numberOrder + "/" + barcode;
                    TerminalOrder terminalOrder = terminalOrderMap.get(key);
                    if (terminalOrder != null)
                        terminalOrder.quantity = safeAdd(terminalOrder.quantity, quantity);
                    else
                        terminalOrderMap.put(key, new ServerTerminalOrder(dateOrder, numberOrder, idSupplier, barcode, idItem, name, price,
                                quantity, minQuantity, maxQuantity, minPrice, maxPrice, nameManufacturer, weight, color,
                                headField1, headField2, headField3, posField1, posField2, posField3, minDeviationDate, maxDeviationDate, vop));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return new ArrayList<>(terminalOrderMap.values());
    }

    private static String formatDate(LocalDate date) {
        return date != null ? date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null;
    }

    private static String formatColor(Color color) {
        return color == null ? null : String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static List<TerminalAssortment> readTerminalAssortmentList(DataSession session, BusinessLogics BL, ObjectValue priceListTypeObject, ObjectValue stockGroupMachineryObject)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalAssortment> terminalAssortmentList = new ArrayList<>();
        ScriptingLogicsModule machineryPriceTransactionLM = BL.getModule("MachineryPriceTransaction");
        if (machineryPriceTransactionLM != null) {

            DataObject currentDateTimeObject = new DataObject(LocalDateTime.now(), DateTimeClass.instance);

            KeyExpr skuExpr = new KeyExpr("Sku");
            KeyExpr legalEntityExpr = new KeyExpr("legalEntity");
            ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap("Sku", skuExpr, "LegalEntity", legalEntityExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            if (terminalHandlerLM != null) {
                query.addProperty("filterAssortment", terminalHandlerLM.findProperty("filterAssortment[Sku,Stock,LegalEntity]").getExpr(skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr));
                query.addProperty("price", terminalHandlerLM.findProperty("price[Sku,Stock,LegalEntity]").getExpr(skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr));
                query.addProperty("minPrice", terminalHandlerLM.findProperty("minDeviationPrice[Sku,Stock,LegalEntity]").getExpr(skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr));
                query.addProperty("maxPrice", terminalHandlerLM.findProperty("maxDeviationPrice[Sku,Stock,LegalEntity]").getExpr(skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr));
            } else
                query.addProperty("priceALedgerPriceListTypeSkuStockCompanyDateTime", machineryPriceTransactionLM.findProperty("priceA[LedgerPriceListType,Sku,Stock,LegalEntity,DATETIME]").getExpr(priceListTypeObject.getExpr(),
                        skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr, currentDateTimeObject.getExpr()));
            query.addProperty("idBarcodeSku", machineryPriceTransactionLM.findProperty("idBarcode[Sku]").getExpr(skuExpr));
            query.addProperty("idLegalEntity", machineryPriceTransactionLM.findProperty("id[LegalEntity]").getExpr(legalEntityExpr));

            query.and(machineryPriceTransactionLM.findProperty("id[LegalEntity]").getExpr(legalEntityExpr).getWhere());
            query.and(machineryPriceTransactionLM.findProperty("idBarcode[Sku]").getExpr(skuExpr).getWhere());
            if (terminalHandlerLM != null) {
                query.and(terminalHandlerLM.findProperty("filterAssortment[Sku,Stock,LegalEntity]").getExpr(skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr).getWhere());
            } else
                query.and(machineryPriceTransactionLM.findProperty("priceA[LedgerPriceListType,Sku,Stock,LegalEntity,DATETIME]").getExpr(priceListTypeObject.getExpr(),
                    skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr, currentDateTimeObject.getExpr()).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String idBarcodeSku = trim((String) entry.get("idBarcodeSku"));
                String idLegalEntity = trim((String) entry.get("idLegalEntity"));
                BigDecimal price;
                if (terminalHandlerLM != null)
                    price = (BigDecimal) entry.get("price");
                else
                    price = (BigDecimal) entry.get("priceALedgerPriceListTypeSkuStockCompanyDateTime");

                BigDecimal maxPrice = null;
                BigDecimal minPrice = null;
                if (terminalHandlerLM != null) {
                    minPrice = (BigDecimal) entry.get("minPrice");
                    maxPrice = (BigDecimal) entry.get("maxPrice");
                }
                terminalAssortmentList.add(new TerminalAssortment(idBarcodeSku, idLegalEntity, price, minPrice, maxPrice));
            }
        }
        return terminalAssortmentList;
    }

    public static List<TerminalHandbookType> readTerminalHandbookTypeList(DataSession session, BusinessLogics BL) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalHandbookType> terminalHandbookTypeList = new ArrayList<>();
        ScriptingLogicsModule terminalLM = BL.getModule("Terminal");
        if(terminalLM != null) {
            KeyExpr terminalHandbookTypeExpr = new KeyExpr("terminalHandbookType");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("terminalHandbookType", terminalHandbookTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            String[] names = new String[]{"idTerminalHandbookType", "nameTerminalHandbookType"};
            LP<?>[] properties = terminalLM.findProperties("id[TerminalHandbookType]", "name[TerminalHandbookType]");
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
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("terminalDocumentType", terminalDocumentTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            String[] names = new String[]{"idTerminalDocumentType", "nameTerminalDocumentType", "flagTerminalDocumentType",
                    "idTerminalHandbookType1TerminalDocumentType", "idTerminalHandbookType2TerminalDocumentType"};
            LP<?>[] properties = terminalLM.findProperties("id[TerminalDocumentType]", "name[TerminalDocumentType]", "flag[TerminalDocumentType]",
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
            ImRevMap<Object, KeyExpr> terminalHandbookTypeKeys = MapFact.singletonRev("terminalHandbookType", terminalHandbookTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(terminalHandbookTypeKeys);
            String[] names = new String[]{"exportId", "name", "propertyID", "propertyName", "filterProperty", "extInfoProperty"};
            LP<?>[] properties = terminalLM.findProperties("exportId[TerminalHandbookType]", "name[TerminalHandbookType]",
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
                LP propertyID = (LP<?>) BL.findSafeProperty(trim((String) entry.get("propertyID")));
                LP propertyName = (LP<?>) BL.findSafeProperty(trim((String) entry.get("propertyName")));
                String canonicalNameFilterProperty = trim((String) entry.get("filterProperty"));
                LP filterProperty = canonicalNameFilterProperty != null ? (LP<?>) BL.findSafeProperty(canonicalNameFilterProperty) : null;
                String canonicalNameExtInfoProperty = trim((String) entry.get("extInfoProperty"));
                LP extInfoProperty = canonicalNameExtInfoProperty != null ? (LP<?>) BL.findSafeProperty(canonicalNameExtInfoProperty) : null;

                if(propertyID != null && propertyName != null) {
                    ImOrderSet<PropertyInterface> interfaces = propertyID.listInterfaces;
                    if (interfaces.size() == 1) {
                        KeyExpr customANAExpr = new KeyExpr("customANA");
                        ImRevMap<Object, KeyExpr> customANAKeys = MapFact.singletonRev("customANA", customANAExpr);
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
            ImRevMap<Object, KeyExpr> legalEntityKeys = MapFact.singletonRev("LegalEntity", legalEntityExpr);
            QueryBuilder<Object, Object> legalEntityQuery = new QueryBuilder<>(legalEntityKeys);
            String[] legalEntityNames = new String[]{"idLegalEntity", "nameLegalEntity"};
            LP<?>[] legalEntityProperties = terminalLM.findProperties("id[LegalEntity]", "name[LegalEntity]");
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