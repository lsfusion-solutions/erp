package equ.srv;

import equ.api.terminal.TerminalAssortment;
import equ.api.terminal.TerminalDocumentType;
import equ.api.terminal.TerminalHandbookType;
import equ.api.terminal.TerminalLegalEntity;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
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

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.trim;

public class TerminalEquipmentServer {

    public static List<TerminalAssortment> readTerminalAssortmentList(DataSession session, BusinessLogics BL, ObjectValue priceListTypeObject, ObjectValue stockGroupMachineryObject)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalAssortment> terminalAssortmentList = new ArrayList<>();
        ScriptingLogicsModule machineryPriceTransactionLM = BL.getModule("MachineryPriceTransaction");
        if (machineryPriceTransactionLM != null) {

            DataObject currentDateTimeObject = new DataObject(LocalDateTime.now(), DateTimeClass.instance);

            KeyExpr skuExpr = new KeyExpr("Sku");
            KeyExpr supplierExpr = new KeyExpr("Stock");
            ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap("Sku", skuExpr, "Stock", supplierExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            query.addProperty("priceALedgerPriceListTypeSkuStockCompanyDateTime", machineryPriceTransactionLM.findProperty("Machinery.priceA[LedgerPriceListType,Sku,Stock,Stock,DATETIME]").getExpr(priceListTypeObject.getExpr(),
                    skuExpr, stockGroupMachineryObject.getExpr(), supplierExpr, currentDateTimeObject.getExpr()));
            query.addProperty("idBarcodeSku", machineryPriceTransactionLM.findProperty("idBarcode[Sku]").getExpr(skuExpr));
            query.addProperty("idSupplier", machineryPriceTransactionLM.findProperty("id[Stock]").getExpr(supplierExpr));

            query.and(machineryPriceTransactionLM.findProperty("id[Stock]").getExpr(supplierExpr).getWhere());
            query.and(machineryPriceTransactionLM.findProperty("idBarcode[Sku]").getExpr(skuExpr).getWhere());
            query.and(machineryPriceTransactionLM.findProperty("Machinery.priceA[LedgerPriceListType,Sku,Stock,Stock,DATETIME]").getExpr(priceListTypeObject.getExpr(),
                skuExpr, stockGroupMachineryObject.getExpr(), supplierExpr, currentDateTimeObject.getExpr()).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String idBarcodeSku = trim((String) entry.get("idBarcodeSku"));
                String idSupplier = trim((String) entry.get("idSupplier"));
                BigDecimal price;
                price = (BigDecimal) entry.get("priceALedgerPriceListTypeSkuStockCompanyDateTime");

                terminalAssortmentList.add(new TerminalAssortment(idBarcodeSku, idSupplier, price, null, null, null));
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

    public static List<TerminalDocumentType> readTerminalDocumentTypeListEquipment(DataSession session, BusinessLogics BL, DataObject groupMachineryObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
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
            query.and(terminalLM.findProperty("notSkip[TerminalDocumentType, GroupTerminal]").getExpr(terminalDocumentTypeExpr, groupMachineryObject.getExpr()).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String id = trim((String) entry.get("idTerminalDocumentType"));
                String name = trim((String) entry.get("nameTerminalDocumentType"));
                Long flag = (Long) entry.get("flagTerminalDocumentType");
                String analytics1 = trim((String) entry.get("idTerminalHandbookType1TerminalDocumentType"));
                String analytics2 = trim((String) entry.get("idTerminalHandbookType2TerminalDocumentType"));
                terminalDocumentTypeList.add(new TerminalDocumentType(id, name, analytics1, analytics2, flag));
            }
        }
        return terminalDocumentTypeList;
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
}