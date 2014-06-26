package equ.srv;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.api.terminal.*;
import lsfusion.base.BaseUtils;
import lsfusion.base.DateConverter;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.classes.*;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.lifecycle.LifecycleAdapter;
import lsfusion.server.lifecycle.LifecycleEvent;
import lsfusion.server.logics.*;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.log4j.Logger;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

public class EquipmentServer extends LifecycleAdapter implements EquipmentServerInterface, InitializingBean {
    private static final Logger logger = Logger.getLogger(EquipmentServer.class);

    public static final String EXPORT_NAME = "EquipmentServer";

    private LogicsInstance logicsInstance;

    private SoftCheckInterface softCheck;
    
    private ScriptingLogicsModule equLM;

    //Опциональные модули
    private ScriptingLogicsModule cashRegisterLM;
    private ScriptingLogicsModule collectionLM;
    private ScriptingLogicsModule discountCardLM;
    private ScriptingLogicsModule itemLM;
    private ScriptingLogicsModule legalEntityLM;
    private ScriptingLogicsModule priceCheckerLM;
    private ScriptingLogicsModule priceListLedgerLM;
    private ScriptingLogicsModule purchaseOrderLM;
    private ScriptingLogicsModule scalesLM;
    private ScriptingLogicsModule stopListLM;
    private ScriptingLogicsModule scalesItemLM;
    private ScriptingLogicsModule terminalLM;
    private ScriptingLogicsModule zReportLM;
    
    private boolean started = false;

    public void setLogicsInstance(LogicsInstance logicsInstance) {
        this.logicsInstance = logicsInstance;
    }

    public LogicsInstance getLogicsInstance() {
        return logicsInstance;
    }

    public void setSoftCheckHandler(SoftCheckInterface softCheck) {
        this.softCheck = softCheck;
    }

    public SoftCheckInterface getSoftCheckHandler() {
        return softCheck;
    }
    
    public RMIManager getRmiManager() {
        return logicsInstance.getRmiManager();
    }

    public BusinessLogics getBusinessLogics() {
        return logicsInstance.getBusinessLogics();
    }

    public DBManager getDbManager() {
        return logicsInstance.getDbManager();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }

    @Override
    protected void onInit(LifecycleEvent event) {
        equLM = (ScriptingLogicsModule) getBusinessLogics().getModule("Equipment");
        Assert.notNull(equLM, "can't find Equipment module");
        cashRegisterLM = (ScriptingLogicsModule) getBusinessLogics().getModule("EquipmentCashRegister");
        collectionLM = (ScriptingLogicsModule) getBusinessLogics().getModule("Collection");
        discountCardLM = (ScriptingLogicsModule) getBusinessLogics().getModule("DiscountCard");
        itemLM = (ScriptingLogicsModule) getBusinessLogics().getModule("Item");
        legalEntityLM = (ScriptingLogicsModule) getBusinessLogics().getModule("LegalEntity");
        priceCheckerLM = (ScriptingLogicsModule) getBusinessLogics().getModule("EquipmentPriceChecker");
        priceListLedgerLM = (ScriptingLogicsModule) getBusinessLogics().getModule("PriceListLedger");
        purchaseOrderLM = (ScriptingLogicsModule) getBusinessLogics().getModule("PurchaseOrder");
        scalesItemLM = (ScriptingLogicsModule) getBusinessLogics().getModule("ScalesItem");
        scalesLM = (ScriptingLogicsModule) getBusinessLogics().getModule("EquipmentScales");
        stopListLM = (ScriptingLogicsModule) getBusinessLogics().getModule("StopList");
        terminalLM = (ScriptingLogicsModule) getBusinessLogics().getModule("EquipmentTerminal");
        zReportLM = (ScriptingLogicsModule) getBusinessLogics().getModule("ZReport");
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        logger.info("Binding Equipment Server.");
        try {
            getRmiManager().bindAndExport(EXPORT_NAME, this);
            started = true;
        } catch (Exception e) {
            throw new RuntimeException("Error exporting Equipment Server: ", e);
        }
    }

    @Override
    protected void onStopping(LifecycleEvent event) {
        if (started) {
            logger.info("Stopping Equipment Server.");
            try {
                getRmiManager().unbindAndUnexport(EXPORT_NAME, this);
            } catch (Exception e) {
                throw new RuntimeException("Error stopping Equipment Server: ", e);
            }
        }
    }

    @Override
    public List<SoftCheckInfo> readSoftCheckInfo() throws RemoteException, SQLException {
        return softCheck == null ? null : softCheck.readSoftCheckInfo();
    }

    @Override
    public void finishSoftCheckInfo(Map<String, SoftCheckInvoice> invoiceMap) throws RemoteException, SQLException {
        if(softCheck != null)
            softCheck.finishSoftCheckInfo(invoiceMap);
    }

    @Override
    public String sendSucceededSoftCheckInfo(Map<String, Date> invoiceSet) throws RemoteException, SQLException {
        return softCheck == null ? null : softCheck.sendSucceededSoftCheckInfo(invoiceSet);
    }

    @Override
    public List<TransactionInfo> readTransactionInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        try {

            DataSession session = getDbManager().createSession();
            List<TransactionInfo> transactionList = new ArrayList<TransactionInfo>();

            LCP isMachineryPriceTransaction = equLM.is(equLM.findClassByCompoundName("MachineryPriceTransaction"));
            ImRevMap<Object, KeyExpr> keys = isMachineryPriceTransaction.getMapKeys();
            KeyExpr key = keys.singleValue();
            QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);

            String[] mptProperties = new String[]{"dateTimeMachineryPriceTransaction", "groupMachineryMachineryPriceTransaction",
                    "nppGroupMachineryMachineryPriceTransaction", "nameGroupMachineryMachineryPriceTransaction", "snapshotMachineryPriceTransaction"};
            for (String property : mptProperties) {
                query.addProperty(property, equLM.findLCPByCompoundOldName(property).getExpr(key));
            }
            query.and(equLM.findLCPByCompoundOldName("sidEquipmentServerMachineryPriceTransaction").getExpr(key).compare(new DataObject(sidEquipmentServer, StringClass.get(20)), Compare.EQUALS));
            query.and(equLM.findLCPByCompoundOldName("processMachineryPriceTransaction").getExpr(key).getWhere());

            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(session);
            List<Object[]> transactionObjects = new ArrayList<Object[]>();
            for (int i = 0, size = result.size(); i < size; i++) {
                ImMap<Object, ObjectValue> value = result.getValue(i);
                DataObject dateTimeMPT = (DataObject) value.get("dateTimeMachineryPriceTransaction");
                DataObject groupMachineryMPT = (DataObject) value.get("groupMachineryMachineryPriceTransaction");
                Integer nppGroupMachineryMPT = (Integer) value.get("nppGroupMachineryMachineryPriceTransaction").getValue();
                String nameGroupMachineryMPT = (String) value.get("nameGroupMachineryMachineryPriceTransaction").getValue();
                DataObject transactionObject = result.getKey(i).singleValue();
                Boolean snapshotMPT = value.get("snapshotMachineryPriceTransaction") instanceof DataObject;
                transactionObjects.add(new Object[]{groupMachineryMPT, nppGroupMachineryMPT, nameGroupMachineryMPT, transactionObject,
                        dateTimeCode((Timestamp) dateTimeMPT.getValue()), dateTimeMPT, snapshotMPT});
            }

            for (Object[] transaction : transactionObjects) {

                DataObject groupMachineryObject = (DataObject) transaction[0];
                Integer nppGroupMachinery = (Integer) transaction[1];
                String nameGroupMachinery = (String) transaction[2];
                DataObject transactionObject = (DataObject) transaction[3];
                String dateTimeCode = (String) transaction[4];
                Date date = new Date(((Timestamp) ((DataObject) transaction[5]).getValue()).getTime());
                Boolean snapshotTransaction = (Boolean) transaction[6];

                KeyExpr barcodeExpr = new KeyExpr("barcode");
                ImRevMap<Object, KeyExpr> skuKeys = MapFact.singletonRev((Object) "barcode", barcodeExpr);

                QueryBuilder<Object, Object> skuQuery = new QueryBuilder<Object, Object>(skuKeys);

                String[] skuProperties = new String[]{"nameMachineryPriceTransactionBarcode", "priceMachineryPriceTransactionBarcode",
                        "expiryDateMachineryPriceTransactionBarcode", "isWeightMachineryPriceTransactionBarcode", "passScalesMachineryPriceTransactionBarcode",
                        "skuGroupMachineryPriceTransactionBarcode", "idUOMMachineryPriceTransactionBarcode", "shortNameUOMMachineryPriceTransactionBarcode"};
                String[] extraSkuProperties = new String[]{"daysExpiryMachineryPriceTransactionBarcode", "hoursExpiryMachineryPriceTransactionBarcode",
                        "labelFormatMachineryPriceTransactionBarcode", "compositionMachineryPriceTransactionBarcode"};
                skuQuery.addProperty("idBarcode", equLM.findLCPByCompoundOldName("idBarcode").getExpr(barcodeExpr));
                for (String property : skuProperties) {
                    skuQuery.addProperty(property, equLM.findLCPByCompoundOldName(property).getExpr(transactionObject.getExpr(), barcodeExpr));
                }
                if (scalesItemLM != null) {
                    for (String property : extraSkuProperties) {
                        skuQuery.addProperty(property, scalesItemLM.findLCPByCompoundOldName(property).getExpr(transactionObject.getExpr(), barcodeExpr));
                    }
                }

                skuQuery.and(equLM.findLCPByCompoundOldName("inMachineryPriceTransactionBarcode").getExpr(transactionObject.getExpr(), barcodeExpr).getWhere());

                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> skuResult = skuQuery.executeClasses(session);                

                if (cashRegisterLM != null && transactionObject.objectClass.equals(cashRegisterLM.findClassByCompoundName("CashRegisterPriceTransaction"))) {
                    
                    String directoryGroupCashRegister = (String) cashRegisterLM.findLCPByCompoundOldName("directoryGroupCashRegister").read(session, groupMachineryObject);
                    java.sql.Date startDateGroupCashRegister = (java.sql.Date) cashRegisterLM.findLCPByCompoundOldName("startDateGroupCashRegister").read(session, groupMachineryObject);
                    Boolean notDetailedGroupCashRegister = cashRegisterLM.findLCPByCompoundOldName("notDetailedGroupCashRegister").read(session, groupMachineryObject) != null;

                    List<CashRegisterInfo> cashRegisterInfoList = new ArrayList<CashRegisterInfo>();
                    LCP<PropertyInterface> isCashRegister = (LCP<PropertyInterface>) cashRegisterLM.is(cashRegisterLM.findClassByCompoundName("CashRegister"));

                    ImRevMap<PropertyInterface, KeyExpr> cashRegisterKeys = isCashRegister.getMapKeys();
                    KeyExpr cashRegisterKey = cashRegisterKeys.singleValue();
                    QueryBuilder<PropertyInterface, Object> cashRegisterQuery = new QueryBuilder<PropertyInterface, Object>(cashRegisterKeys);

                    String[] cashRegisterProperties = new String[]{"nppMachinery", "portMachinery", "nameModelMachinery", "handlerModelMachinery"};
                    for (String property : cashRegisterProperties) {
                        cashRegisterQuery.addProperty(property, cashRegisterLM.findLCPByCompoundOldName(property).getExpr(cashRegisterKey));
                    }
                    cashRegisterQuery.and(isCashRegister.property.getExpr(cashRegisterKeys).getWhere());
                    cashRegisterQuery.and(cashRegisterLM.findLCPByCompoundOldName("groupCashRegisterCashRegister").getExpr(cashRegisterKey).compare(groupMachineryObject, Compare.EQUALS));

                    ImOrderMap<ImMap<PropertyInterface, Object>, ImMap<Object, Object>> cashRegisterResult = cashRegisterQuery.execute(session);

                    for (ImMap<Object, Object> row : cashRegisterResult.valueIt()) {
                        Integer nppMachinery = (Integer) row.get("nppMachinery");
                        String nameModel = (String) row.get("nameModelMachinery");
                        String handlerModel = (String) row.get("handlerModelMachinery");
                        String portMachinery = (String) row.get("portMachinery");
                        cashRegisterInfoList.add(new CashRegisterInfo(nppGroupMachinery, nppMachinery, nameModel, handlerModel, portMachinery, directoryGroupCashRegister, startDateGroupCashRegister, notDetailedGroupCashRegister));
                    }

                    List<CashRegisterItemInfo> cashRegisterItemInfoList = new ArrayList<CashRegisterItemInfo>();
                    for (int i = 0; i < skuResult.size(); i++) {
                        ImMap<Object, DataObject> keyRow = skuResult.getKey(i);
                        ImMap<Object, ObjectValue> valueRow = skuResult.getValue(i);
                        String barcode = trim((String) valueRow.get("idBarcode").getValue());
                        String name = trim((String) valueRow.get("nameMachineryPriceTransactionBarcode").getValue());
                        BigDecimal price = (BigDecimal) valueRow.get("priceMachineryPriceTransactionBarcode").getValue();
                        boolean isWeight = valueRow.get("isWeightMachineryPriceTransactionBarcode").getValue() != null;
                        boolean passScales = valueRow.get("passScalesMachineryPriceTransactionBarcode").getValue() != null;
                        String idUOM = (String) valueRow.get("idUOMMachineryPriceTransactionBarcode").getValue();
                        String shortNameUOM = (String) valueRow.get("shortNameUOMMachineryPriceTransactionBarcode").getValue();
                        Integer idItem = (Integer) itemLM.findLCPByCompoundOldName("skuBarcode").readClasses(session, keyRow.get("barcode")).getValue();
                        String composition = scalesItemLM == null ? null : (String) valueRow.get("compositionMachineryPriceTransactionBarcode").getValue();

                        List<ItemGroup> hierarchyItemGroup = new ArrayList<ItemGroup>();
                        String canonicalNameSkuGroup = null;
                        if (itemLM != null) {
                            ObjectValue skuGroupObject = valueRow.get("skuGroupMachineryPriceTransactionBarcode");
                            if (skuGroupObject instanceof DataObject) {
                                String idItemGroup = (String) itemLM.findLCPByCompoundOldName("idItemGroup").read(session, skuGroupObject);
                                String nameItemGroup = (String) itemLM.findLCPByCompoundOldName("nameItemGroup").read(session, skuGroupObject);
                                hierarchyItemGroup.add(new ItemGroup(idItemGroup, nameItemGroup));
                                ObjectValue parentSkuGroup;
                                while ((parentSkuGroup = equLM.findLCPByCompoundOldName("parentSkuGroup").readClasses(session, (DataObject) skuGroupObject)) instanceof DataObject) {
                                    String idParentGroup = (String) itemLM.findLCPByCompoundOldName("idItemGroup").read(session, parentSkuGroup);
                                    String nameParentGroup = (String) itemLM.findLCPByCompoundOldName("nameItemGroup").read(session, parentSkuGroup);
                                    hierarchyItemGroup.add(new ItemGroup(idParentGroup, nameParentGroup));
                                    skuGroupObject = parentSkuGroup;
                                }
                                canonicalNameSkuGroup = idItemGroup == null ? "" : trim((String) equLM.findLCPByCompoundOldName("canonicalNameSkuGroup").read(session, itemLM.findLCPByCompoundOldName("itemGroupId").readClasses(session, new DataObject(idItemGroup))));
                            }
                        }
                        
                        cashRegisterItemInfoList.add(new CashRegisterItemInfo(barcode, name, price, isWeight, idItem,
                                composition, canonicalNameSkuGroup, hierarchyItemGroup, idUOM, shortNameUOM, passScales));
                    }
                    
                    transactionList.add(new TransactionCashRegisterInfo((Integer) transactionObject.getValue(),
                            dateTimeCode, date, cashRegisterItemInfoList, cashRegisterInfoList, nppGroupMachinery, nameGroupMachinery));

                } else if (scalesLM != null && transactionObject.objectClass.equals(scalesLM.findClassByCompoundName("ScalesPriceTransaction"))) {
                    List<ScalesInfo> scalesInfoList = new ArrayList<ScalesInfo>();
                    String directory = (String) scalesLM.findLCPByCompoundOldName("directoryGroupScales").read(session, groupMachineryObject);
                    String pieceCodeGroupScales = (String) scalesLM.findLCPByCompoundOldName("pieceCodeGroupScales").read(session, groupMachineryObject);
                    String weightCodeGroupScales = (String) scalesLM.findLCPByCompoundOldName("weightCodeGroupScales").read(session, groupMachineryObject);

                    LCP<PropertyInterface> isScales = (LCP<PropertyInterface>) scalesLM.is(scalesLM.findClassByCompoundName("Scales"));

                    ImRevMap<PropertyInterface, KeyExpr> scalesKeys = isScales.getMapKeys();
                    KeyExpr scalesKey = scalesKeys.singleValue();
                    QueryBuilder<PropertyInterface, Object> scalesQuery = new QueryBuilder<PropertyInterface, Object>(scalesKeys);

                    String[] scalesProperties = new String[]{"portMachinery", "nppMachinery", "nameCheckModelCheck", "handlerModelMachinery"};
                    for (String property : scalesProperties) {
                        scalesQuery.addProperty(property, scalesLM.findLCPByCompoundOldName(property).getExpr(scalesKey));
                    }
                    scalesQuery.and(isScales.property.getExpr(scalesKeys).getWhere());
                    scalesQuery.and(scalesLM.findLCPByCompoundOldName("groupScalesScales").getExpr(scalesKey).compare(groupMachineryObject, Compare.EQUALS));

                    ImOrderMap<ImMap<PropertyInterface, Object>, ImMap<Object, Object>> scalesResult = scalesQuery.execute(session);

                    for (ImMap<Object, Object> values : scalesResult.valueIt()) {
                        String portMachinery = (String) values.get("portMachinery");
                        Integer nppMachinery = (Integer) values.get("nppMachinery");
                        String nameModel = (String) values.get("nameModelMachinery");
                        String handlerModel = (String) values.get("handlerModelMachinery");
                        scalesInfoList.add(new ScalesInfo(nppMachinery, nameModel, handlerModel, portMachinery, directory,
                                pieceCodeGroupScales, weightCodeGroupScales));
                    }

                    List<ScalesItemInfo> scalesItemInfoList = new ArrayList<ScalesItemInfo>();
                    for (ImMap<Object, ObjectValue> row : skuResult.valueIt()) {
                        String barcode = trim((String) row.get("idBarcode").getValue());
                        String name = trim((String) row.get("nameMachineryPriceTransactionBarcode").getValue());
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode").getValue();
                        Date expiryDate = (Date) row.get("expiryDateMachineryPriceTransactionBarcode").getValue();
                        boolean isWeight = row.get("isWeightMachineryPriceTransactionBarcode").getValue() != null;
                        BigDecimal daysExpiry = (BigDecimal) row.get("daysExpiryMachineryPriceTransactionBarcode").getValue();
                        Integer hoursExpiry = (Integer) row.get("hoursExpiryMachineryPriceTransactionBarcode").getValue();
                        Integer labelFormat = (Integer) row.get("labelFormatMachineryPriceTransactionBarcode").getValue();
                        String composition = (String) row.get("compositionMachineryPriceTransactionBarcode").getValue();

                        List<String> hierarchyItemGroup = new ArrayList<String>();
                        if (itemLM != null) {
                            ObjectValue skuGroupObject = row.get("skuGroupMachineryPriceTransactionBarcode");
                            if (skuGroupObject instanceof DataObject) {
                                String idItemGroup = (String) itemLM.findLCPByCompoundOldName("idItemGroup").read(session, skuGroupObject);
                                hierarchyItemGroup.add(idItemGroup);
                                ObjectValue parentSkuGroup;
                                while ((parentSkuGroup = equLM.findLCPByCompoundOldName("parentSkuGroup").readClasses(session, (DataObject) skuGroupObject)) instanceof DataObject) {
                                    hierarchyItemGroup.add((String) itemLM.findLCPByCompoundOldName("idItemGroup").read(session, parentSkuGroup));
                                    skuGroupObject = parentSkuGroup;
                                }
                            }
                        }
                        Integer cellScalesObject = composition == null ? null : (Integer) equLM.findLCPByCompoundOldName("cellScalesGroupScalesComposition").read(session, groupMachineryObject, new DataObject(composition, StringClass.text));
                        Integer compositionNumberCellScales = cellScalesObject == null ? null : (Integer) equLM.findLCPByCompoundOldName("numberCellScales").read(session, new DataObject(cellScalesObject, (ConcreteClass) equLM.findClassByCompoundName("CellScales")));

                        scalesItemInfoList.add(new ScalesItemInfo(barcode, name, price, isWeight, daysExpiry, 
                                hoursExpiry, expiryDate, labelFormat, composition, compositionNumberCellScales, hierarchyItemGroup));
                    }

                    transactionList.add(new TransactionScalesInfo((Integer) transactionObject.getValue(),
                            dateTimeCode, scalesItemInfoList, scalesInfoList, snapshotTransaction));

                } else if (priceCheckerLM != null && transactionObject.objectClass.equals(priceCheckerLM.findClassByCompoundName("PriceCheckerPriceTransaction"))) {
                    List<PriceCheckerInfo> priceCheckerInfoList = new ArrayList<PriceCheckerInfo>();
                    LCP<PropertyInterface> isCheck = (LCP<PropertyInterface>) priceCheckerLM.is(priceCheckerLM.findClassByCompoundName("Check"));

                    ImRevMap<PropertyInterface, KeyExpr> checkKeys = isCheck.getMapKeys();
                    KeyExpr checkKey = checkKeys.singleValue();
                    QueryBuilder<PropertyInterface, Object> checkQuery = new QueryBuilder<PropertyInterface, Object>(checkKeys);

                    String[] checkProperties = new String[]{"portMachinery", "nppMachinery", "nameCheckModelCheck"};
                    for (String property : checkProperties) {
                        checkQuery.addProperty(property, priceCheckerLM.findLCPByCompoundOldName(property).getExpr(checkKey));
                    }
                    checkQuery.and(isCheck.property.getExpr(checkKeys).getWhere());
                    checkQuery.and(priceCheckerLM.findLCPByCompoundOldName("groupPriceCheckerPriceChecker").getExpr(checkKey).compare(groupMachineryObject, Compare.EQUALS));

                    ImOrderMap<ImMap<PropertyInterface, Object>, ImMap<Object, Object>> checkResult = checkQuery.execute(session);

                    for (ImMap<Object, Object> values : checkResult.valueIt()) {
                        priceCheckerInfoList.add(new PriceCheckerInfo((Integer) values.get("nppMachinery"), (String) values.get("nameCheckModelCheck"),
                                null, (String) values.get("portMachinery")));
                    }

                    List<PriceCheckerItemInfo> priceCheckerItemInfoList = new ArrayList<PriceCheckerItemInfo>();
                    for (ImMap<Object, ObjectValue> row : skuResult.valueIt()) {
                        String barcode = trim((String) row.get("idBarcode").getValue());
                        String name = trim((String) row.get("nameMachineryPriceTransactionBarcode").getValue());
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode").getValue();
                        boolean isWeight = row.get("isWeightMachineryPriceTransactionBarcode").getValue() != null;
                        priceCheckerItemInfoList.add(new PriceCheckerItemInfo(barcode, name, price, isWeight));
                    }
                    
                    transactionList.add(new TransactionPriceCheckerInfo((Integer) transactionObject.getValue(),
                            dateTimeCode, priceCheckerItemInfoList, priceCheckerInfoList));


                } else if (terminalLM != null && transactionObject.objectClass.equals(terminalLM.findClassByCompoundName("TerminalPriceTransaction"))) {
                    List<TerminalInfo> terminalInfoList = new ArrayList<TerminalInfo>();
                    
                    Integer nppGroupTerminal = (Integer) terminalLM.findLCPByCompoundOldName("nppGroupMachinery").read(session, groupMachineryObject);
                    String directoryGroupTerminal = (String) terminalLM.findLCPByCompoundOldName("directoryGroupTerminal").read(session, groupMachineryObject);
                    ObjectValue priceListTypeGroupTerminal = terminalLM.findLCPByCompoundOldName("priceListTypeGroupTerminal").readClasses(session, groupMachineryObject);
                    ObjectValue stockGroupTerminal = terminalLM.findLCPByCompoundOldName("stockGroupTerminal").readClasses(session, groupMachineryObject);
                    String idPriceListType = (String) terminalLM.findLCPByCompoundOldName("idPriceListType").read(session, priceListTypeGroupTerminal);
                    
                    LCP<PropertyInterface> isTerminal = (LCP<PropertyInterface>) terminalLM.is(terminalLM.findClassByCompoundName("Terminal"));

                    ImRevMap<PropertyInterface, KeyExpr> terminalKeys = isTerminal.getMapKeys();
                    KeyExpr terminalKey = terminalKeys.singleValue();
                    QueryBuilder<PropertyInterface, Object> terminalQuery = new QueryBuilder<PropertyInterface, Object>(terminalKeys);

                    String[] terminalProperties = new String[]{"portMachinery", "nppMachinery", "nameModelMachinery", "handlerModelMachinery"};
                    for (String property : terminalProperties) {
                        terminalQuery.addProperty(property, terminalLM.findLCPByCompoundOldName(property).getExpr(terminalKey));
                    }
                    terminalQuery.and(isTerminal.property.getExpr(terminalKeys).getWhere());
                    terminalQuery.and(terminalLM.findLCPByCompoundOldName("groupTerminalTerminal").getExpr(terminalKey).compare(groupMachineryObject, Compare.EQUALS));

                    ImOrderMap<ImMap<PropertyInterface, Object>, ImMap<Object, Object>> terminalResult = terminalQuery.execute(session);

                    for (ImMap<Object, Object> values : terminalResult.valueIt()) {
                        terminalInfoList.add(new TerminalInfo((Integer) values.get("nppMachinery"),
                                (String) values.get("nameModelMachinery"), (String) values.get("handlerModelMachinery"),
                                (String) values.get("portMachinery"), idPriceListType, directoryGroupTerminal));
                    }

                    List<TerminalItemInfo> terminalItemInfoList = new ArrayList<TerminalItemInfo>();
                    for (ImMap<Object, ObjectValue> row : skuResult.valueIt()) {
                        String barcode = trim((String) row.get("idBarcode").getValue());
                        String name = trim((String) row.get("nameMachineryPriceTransactionBarcode").getValue());
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode").getValue();
                        boolean isWeight = row.get("isWeightMachineryPriceTransactionBarcode").getValue() != null;

                        terminalItemInfoList.add(new TerminalItemInfo(barcode, name, price, isWeight, null/*quantity*/, null/*image*/));
                    }

                    List<TerminalHandbookType> terminalHandbookTypeList = readTerminalHandbookTypeList(session);
                    List<TerminalDocumentType> terminalDocumentTypeList = readTerminalDocumentTypeList(session);                   
                    List<TerminalOrder> terminalOrderList = readTerminalOrderList(session);
                    List<TerminalLegalEntity> terminalLegalEntityList = readTerminalLegalEntityList(session);
                    List<TerminalAssortment> terminalAssortmentList = readTerminalAssortmentList(session, priceListTypeGroupTerminal, stockGroupTerminal);
                    
                    transactionList.add(new TransactionTerminalInfo((Integer) transactionObject.getValue(),
                            dateTimeCode, terminalItemInfoList, terminalInfoList, terminalHandbookTypeList,
                            terminalDocumentTypeList, terminalOrderList, terminalLegalEntityList, terminalAssortmentList, 
                            nppGroupTerminal, directoryGroupTerminal, snapshotTransaction));
                }
            }
            return transactionList;
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }
 
    @Override
    public List<StopListInfo> readStopListInfo(String sidEquipmentServer) throws RemoteException, SQLException {

        List<StopListInfo> stopListInfoList = new ArrayList<StopListInfo>();
        
        if(cashRegisterLM != null && stopListLM != null) {
            try {

                Map<String, StopListInfo> stopListInfoMap = new HashMap<String, StopListInfo>();

                DataSession session = getDbManager().createSession();

                Map<String, Map<String, Set<String>>> stockMap = getStockMap(session);
                         
                KeyExpr stopListExpr = new KeyExpr("stopList");
                ImRevMap<Object, KeyExpr> slKeys = MapFact.singletonRev((Object) "stopList", stopListExpr);
                QueryBuilder<Object, Object> slQuery = new QueryBuilder<Object, Object>(slKeys);
                String[] slProperties = new String[]{"numberStopList", "fromDateStopList", "fromTimeStopList", "toDateStopList", "toTimeStopList"};
                for (String property : slProperties)
                    slQuery.addProperty(property, stopListLM.findLCPByCompoundOldName(property).getExpr(stopListExpr));
                slQuery.and(stopListLM.findLCPByCompoundOldName("numberStopList").getExpr(stopListExpr).getWhere());
                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> slResult = slQuery.executeClasses(session);
                for (int i = 0, size = slResult.size(); i < size; i++) {
                    DataObject stopListObject = slResult.getKey(i).get("stopList");
                    ImMap<Object, ObjectValue> slEntry = slResult.getValue(i);
                    String numberStopList = trim((String) slEntry.get("numberStopList").getValue());
                    Date dateFrom = (Date) slEntry.get("fromDateStopList").getValue();
                    Date dateTo = (Date) slEntry.get("toDateStopList").getValue();
                    Time timeFrom = (Time) slEntry.get("fromTimeStopList").getValue();
                    Time timeTo = (Time) slEntry.get("toTimeStopList").getValue();                    
                                                                              
                    Set<String> idStockSet = new HashSet<String>();
                    Map<String, Set<String>> handlerDirectoryMap = new HashMap<String, Set<String>>();                  
                    KeyExpr stockExpr = new KeyExpr("stock");
                    ImRevMap<Object, KeyExpr> stockKeys = MapFact.singletonRev((Object) "stock", stockExpr);
                    QueryBuilder<Object, Object> stockQuery = new QueryBuilder<Object, Object>(stockKeys);
                    stockQuery.addProperty("idStock", stopListLM.findLCPByCompoundOldName("idStock").getExpr(stockExpr));
                    stockQuery.and(stopListLM.findLCPByCompoundOldName("inStockStopList").getExpr(stockExpr, stopListObject.getExpr()).getWhere());
                    stockQuery.and(stopListLM.findLCPByCompoundOldName("notSucceededStockStopList").getExpr(stockExpr, stopListObject.getExpr()).getWhere());
                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> stockResult = stockQuery.execute(session);
                    for (ImMap<Object, Object> stockEntry : stockResult.values()) {
                        String idStock = trim((String) stockEntry.get("idStock"));
                        idStockSet.add(idStock);                       
                        if(stockMap.containsKey(idStock))
                            for (Map.Entry<String, Set<String>> entry : stockMap.get(idStock).entrySet()) {
                                if (handlerDirectoryMap.containsKey(entry.getKey()))
                                    handlerDirectoryMap.get(entry.getKey()).addAll(entry.getValue());
                                else
                                    handlerDirectoryMap.put(entry.getKey(), entry.getValue());
                            }
                    }
                    
                    if(!handlerDirectoryMap.isEmpty()) {
                        List<String> stopListItemList = getStopListItemList(session, stopListObject);
                        stopListInfoMap.put(numberStopList, new StopListInfo(numberStopList, dateFrom, timeFrom, dateTo, timeTo, idStockSet, stopListItemList, handlerDirectoryMap));
                    }
                    for(StopListInfo stopList : stopListInfoMap.values())
                        stopListInfoList.add(stopList);
                    
                }
            } catch (ScriptingErrorLog.SemanticErrorException e) {
                throw Throwables.propagate(e);
            } catch (SQLHandledException e) {
                throw Throwables.propagate(e);
            }            
        }
       
        return stopListInfoList;
    }
    
    private Map<String, Map<String, Set<String>>> getStockMap(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, Map<String, Set<String>>> stockMap = new HashMap<String, Map<String, Set<String>>>();

        KeyExpr groupCashRegisterExpr = new KeyExpr("groupCashRegister");
        KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");
        ImRevMap<Object, KeyExpr> cashRegisterKeys = MapFact.toRevMap((Object) "groupCashRegister", groupCashRegisterExpr, "cashRegister", cashRegisterExpr);
        QueryBuilder<Object, Object> cashRegisterQuery = new QueryBuilder<Object, Object>(cashRegisterKeys);
        cashRegisterQuery.addProperty("handlerModelMachinery", stopListLM.findLCPByCompoundOldName("handlerModelMachinery").getExpr(cashRegisterExpr));
        cashRegisterQuery.addProperty("idStockGroupMachinery", stopListLM.findLCPByCompoundOldName("idStockGroupMachinery").getExpr(groupCashRegisterExpr));
        cashRegisterQuery.addProperty("directoryGroupCashRegister", cashRegisterLM.findLCPByCompoundOldName("directoryGroupCashRegister").getExpr(groupCashRegisterExpr));
        cashRegisterQuery.and(stopListLM.findLCPByCompoundOldName("handlerModelMachinery").getExpr(cashRegisterExpr).getWhere());
        cashRegisterQuery.and(stopListLM.findLCPByCompoundOldName("idStockGroupMachinery").getExpr(groupCashRegisterExpr).getWhere());
        cashRegisterQuery.and(cashRegisterLM.findLCPByCompoundOldName("directoryGroupCashRegister").getExpr(groupCashRegisterExpr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> cashRegisterResult = cashRegisterQuery.execute(session);
        for (ImMap<Object, Object> entry : cashRegisterResult.valueIt()) {
            String handlerModel = (String) entry.get("handlerModelMachinery");
            String directory = (String) entry.get("directoryGroupCashRegister");
            String idStockGroupMachinery = (String) entry.get("idStockGroupMachinery");

            Map<String, Set<String>> handlerMap = stockMap.containsKey(idStockGroupMachinery) ? stockMap.get(idStockGroupMachinery) : new HashMap<String, Set<String>>();
            if(!handlerMap.containsKey(handlerModel))
                handlerMap.put(handlerModel, new HashSet<String>());
            handlerMap.get(handlerModel).add(directory);
            stockMap.put(idStockGroupMachinery, handlerMap);
        }
        return stockMap;
    }
    
    private List<String> getStopListItemList(DataSession session, DataObject stopListObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<String> stopListItemList = new ArrayList<String>();

        KeyExpr sldExpr = new KeyExpr("stopListDetail");
        ImRevMap<Object, KeyExpr> sldKeys = MapFact.singletonRev((Object) "stopListDetail", sldExpr);
        QueryBuilder<Object, Object> sldQuery = new QueryBuilder<Object, Object>(sldKeys);
        sldQuery.addProperty("idBarcodeSkuStopListDetail", stopListLM.findLCPByCompoundOldName("idBarcodeSkuStopListDetail").getExpr(sldExpr));
        sldQuery.and(stopListLM.findLCPByCompoundOldName("idBarcodeSkuStopListDetail").getExpr(sldExpr).getWhere());
        sldQuery.and(stopListLM.findLCPByCompoundOldName("stopListStopListDetail").getExpr(sldExpr).compare(stopListObject, Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> sldResult = sldQuery.execute(session);
        for (ImMap<Object, Object> sldEntry : sldResult.values()) {
            stopListItemList.add(trim((String) sldEntry.get("idBarcodeSkuStopListDetail")));
        }
        return stopListItemList;
    }

    @Override
    public void errorStopListReport(String numberStopList, Exception e) throws RemoteException, SQLException {
        try {
            DataSession session = getDbManager().createSession();
            DataObject errorObject = session.addObject((ConcreteCustomClass) stopListLM.findClassByCompoundName("StopListError"));
            ObjectValue stopListObject = stopListLM.findLCPByCompoundOldName("stopListNumber").readClasses(session, new DataObject(numberStopList));
            stopListLM.findLCPByCompoundOldName("stopListStopListError").change(stopListObject.getValue(), session, errorObject);
            stopListLM.findLCPByCompoundOldName("dataStopListError").change(e.toString(), session, errorObject);
            stopListLM.findLCPByCompoundOldName("dateStopListError").change(DateConverter.dateToStamp(Calendar.getInstance().getTime()), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(os));
            stopListLM.findLCPByCompoundOldName("errorTraceStopListError").change(os.toString(), session, errorObject);

            session.apply(getBusinessLogics());
        } catch (Exception e2) {
            throw Throwables.propagate(e2);
        }
    }

    @Override
    public void succeedStopList(String numberStopList, Set<String> idStockSet) throws RemoteException, SQLException {
        try {
            DataSession session = getDbManager().createSession();
            DataObject stopListObject = (DataObject) stopListLM.findLCPByCompoundOldName("stopListNumber").readClasses(session, new DataObject(numberStopList));
            for(String idStock : idStockSet) {
                DataObject stockObject = (DataObject) stopListLM.findLCPByCompoundOldName("stockId").readClasses(session, new DataObject(idStock));
                stopListLM.findLCPByCompoundOldName("succeededStockStopList").change(true, session, stockObject, stopListObject);
            }
            session.apply(getBusinessLogics());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }


    private List<TerminalHandbookType> readTerminalHandbookTypeList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalHandbookType> terminalHandbookTypeList = new ArrayList<TerminalHandbookType>();
        KeyExpr terminalHandbookTypeExpr = new KeyExpr("terminalHandbookType");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "terminalHandbookType", terminalHandbookTypeExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        String[] properties = new String[]{"idTerminalHandbookType", "nameTerminalHandbookType"};
        for (String property : properties) {
            query.addProperty(property, terminalLM.findLCPByCompoundOldName(property).getExpr(terminalHandbookTypeExpr));
        }
        query.and(terminalLM.findLCPByCompoundOldName("idTerminalHandbookType").getExpr(terminalHandbookTypeExpr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
        for(ImMap<Object, Object> entry : result.values()) {
            String id = trim((String) entry.get("idTerminalHandbookType"));
            String name = trim((String) entry.get("nameTerminalHandbookType"));
            terminalHandbookTypeList.add(new TerminalHandbookType(id, name));
        }
        return terminalHandbookTypeList;
    }
    
    private List<TerminalDocumentType> readTerminalDocumentTypeList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalDocumentType> terminalDocumentTypeList = new ArrayList<TerminalDocumentType>();
        KeyExpr terminalDocumentTypeExpr = new KeyExpr("terminalDocumentType");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "terminalDocumentType", terminalDocumentTypeExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        String[] properties = new String[]{"idTerminalDocumentType", "nameTerminalDocumentType", "flagTerminalDocumentType",
                "idTerminalHandbookType1TerminalDocumentType", "idTerminalHandbookType2TerminalDocumentType"};
        for (String property : properties) {
            query.addProperty(property, terminalLM.findLCPByCompoundOldName(property).getExpr(terminalDocumentTypeExpr));
        }
        query.and(terminalLM.findLCPByCompoundOldName("idTerminalDocumentType").getExpr(terminalDocumentTypeExpr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
        for(ImMap<Object, Object> entry : result.values()) {
            String id = trim((String) entry.get("idTerminalDocumentType"));
            String name = trim((String) entry.get("nameTerminalDocumentType"));
            Integer flag = (Integer) entry.get("flagTerminalDocumentType");
            String analytics1 = trim((String) entry.get("idTerminalHandbookType1TerminalDocumentType"));
            String analytics2 = trim((String) entry.get("idTerminalHandbookType2TerminalDocumentType"));
            terminalDocumentTypeList.add(new TerminalDocumentType(id, name, analytics1, analytics2, flag));
        }
        return terminalDocumentTypeList;
    }

    private List<TerminalOrder> readTerminalOrderList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (purchaseOrderLM != null) {

            List<TerminalOrder> terminalOrderList = new ArrayList<TerminalOrder>();
            KeyExpr orderExpr = new KeyExpr("order");
            KeyExpr orderDetailExpr = new KeyExpr("orderDetail");
            ImRevMap<Object, KeyExpr> orderKeys = MapFact.toRevMap((Object) "Order", orderExpr, "OrderDetail", orderDetailExpr);
            QueryBuilder<Object, Object> orderQuery = new QueryBuilder<Object, Object>(orderKeys);
            String[] orderProperties = new String[]{"dateOrder", "numberOrder", "supplierOrder"};
            for (String property : orderProperties) {
                orderQuery.addProperty(property, purchaseOrderLM.findLCPByCompoundOldName(property).getExpr(orderExpr));
            }
            String[] orderDetailProperties = new String[]{"idBarcodeSkuOrderDetail", "nameSkuOrderDetail", "priceOrderDetail", "quantityOrderDetail"};
            for (String property : orderDetailProperties) {
                orderQuery.addProperty(property, purchaseOrderLM.findLCPByCompoundOldName(property).getExpr(orderDetailExpr));
            }
            orderQuery.and(purchaseOrderLM.findLCPByCompoundOldName("Purchase.orderOrderDetail").getExpr(orderDetailExpr).compare(orderExpr, Compare.EQUALS));
            orderQuery.and(purchaseOrderLM.findLCPByCompoundOldName("numberOrder").getExpr(orderExpr).getWhere());
            orderQuery.and(purchaseOrderLM.findLCPByCompoundOldName("isOpenedOrder").getExpr(orderExpr).getWhere());
            orderQuery.and(purchaseOrderLM.findLCPByCompoundOldName("idBarcodeSkuOrderDetail").getExpr(orderDetailExpr).getWhere());
            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> orderResult = orderQuery.executeClasses(session);
            for (ImMap<Object, ObjectValue> entry : orderResult.values()) {
                Date dateOrder = (Date) entry.get("dateOrder").getValue();
                String numberOrder = trim((String) entry.get("numberOrder").getValue());
                String idSupplier = trim((String) purchaseOrderLM.findLCPByCompoundOldName("idLegalEntity").read(session, entry.get("supplierOrder")));
                String barcode = trim((String) entry.get("idBarcodeSkuOrderDetail").getValue());
                String name = trim((String) entry.get("nameSkuOrderDetail").getValue());
                BigDecimal price = (BigDecimal) entry.get("priceOrderDetail").getValue();
                BigDecimal quantity = (BigDecimal) entry.get("quantityOrderDetail").getValue();
                terminalOrderList.add(new TerminalOrder(dateOrder, numberOrder, idSupplier, barcode, name, price, quantity));
            }
            return terminalOrderList;
        } else return null;
    }

    private List<TerminalLegalEntity> readTerminalLegalEntityList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (legalEntityLM != null) {

            List<TerminalLegalEntity> terminalLegalEntityList = new ArrayList<TerminalLegalEntity>();
            KeyExpr legalEntityExpr = new KeyExpr("legalEntity");
            ImRevMap<Object, KeyExpr> legalEntityKeys = MapFact.singletonRev((Object) "LegalEntity", legalEntityExpr);
            QueryBuilder<Object, Object> legalEntityQuery = new QueryBuilder<Object, Object>(legalEntityKeys);
            String[] legalEntityProperties = new String[]{"idLegalEntity", "nameLegalEntity"};
            for (String property : legalEntityProperties) {
                legalEntityQuery.addProperty(property, legalEntityLM.findLCPByCompoundOldName(property).getExpr(legalEntityExpr));
            }
            legalEntityQuery.and(legalEntityLM.findLCPByCompoundOldName("idLegalEntity").getExpr(legalEntityExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> legalEntityResult = legalEntityQuery.execute(session);
            for (ImMap<Object, Object> entry : legalEntityResult.values()) {
                String idLegalEntity = trim((String) entry.get("idLegalEntity"));
                String nameLegalEntity = trim((String) entry.get("nameLegalEntity"));
                terminalLegalEntityList.add(new TerminalLegalEntity(idLegalEntity, nameLegalEntity));
            }
            return terminalLegalEntityList;
        } else return null;
    }

    private List<TerminalAssortment> readTerminalAssortmentList(DataSession session, ObjectValue priceListTypeObject, ObjectValue stockGroupMachineryObject)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (legalEntityLM != null && priceListLedgerLM != null && itemLM != null) {
            
            DataObject currentDateTimeObject = new DataObject(new Timestamp(Calendar.getInstance().getTime().getTime()), DateTimeClass.instance);
            
            List<TerminalAssortment> terminalAssortmentList = new ArrayList<TerminalAssortment>();
            KeyExpr skuExpr = new KeyExpr("Sku");
            KeyExpr legalEntityExpr = new KeyExpr("legalEntity");
            ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "Sku", skuExpr, "LegalEntity", legalEntityExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
            String property = "priceALedgerPriceListTypeSkuStockCompanyDateTime";
            query.addProperty(property, priceListLedgerLM.findLCPByCompoundOldName(property).getExpr(priceListTypeObject.getExpr(), 
                    skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr, currentDateTimeObject.getExpr()));
            query.addProperty("idBarcodeSku", itemLM.findLCPByCompoundOldName("idBarcodeSku").getExpr(skuExpr));
            query.addProperty("idLegalEntity", legalEntityLM.findLCPByCompoundOldName("idLegalEntity").getExpr(legalEntityExpr));
            query.and(legalEntityLM.findLCPByCompoundOldName("idLegalEntity").getExpr(legalEntityExpr).getWhere());
            query.and(itemLM.findLCPByCompoundOldName("idBarcodeSku").getExpr(skuExpr).getWhere());
            query.and(priceListLedgerLM.findLCPByCompoundOldName(property).getExpr(priceListTypeObject.getExpr(), 
                    skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr, currentDateTimeObject.getExpr()).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String idBarcodeSku = trim((String) entry.get("idBarcodeSku"));
                String idLegalEntity = trim((String) entry.get("idLegalEntity"));
                terminalAssortmentList.add(new TerminalAssortment(idBarcodeSku, idLegalEntity));
            }
            return terminalAssortmentList;
        } else return null;
    }

    @Override
    public Map<Date, Set<String>> readRequestSalesInfo(String sidEquipmentServer) throws RemoteException, SQLException {

        Map<Date, Set<String>> directoriesMap = new HashMap<Date, Set<String>>();

        if (cashRegisterLM != null) {
            try {
                logger.info("RequestSalesInfoStock started");

                DataSession session = getDbManager().createSession();

                KeyExpr groupMachineryExpr = new KeyExpr("groupMachinery");
                KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");
                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "GroupMachinery", groupMachineryExpr, "cashRegister", cashRegisterExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);

                query.addProperty("stockGroupMachinery", equLM.findLCPByCompoundOldName("stockGroupMachinery").getExpr(groupMachineryExpr));
                query.addProperty("directoryGroupCashRegister", cashRegisterLM.findLCPByCompoundOldName("directoryGroupCashRegister").getExpr(groupMachineryExpr));
                query.and(equLM.findLCPByCompoundOldName("sidEquipmentServerGroupMachinery").getExpr(groupMachineryExpr).compare(new DataObject(sidEquipmentServer, StringClass.get(20)), Compare.EQUALS));
                query.and(cashRegisterLM.findLCPByCompoundOldName("groupCashRegisterCashRegister").getExpr(cashRegisterExpr).compare(groupMachineryExpr, Compare.EQUALS));
                query.and(cashRegisterLM.findLCPByCompoundOldName("directoryGroupCashRegister").getExpr(groupMachineryExpr).getWhere());

                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(session);
                for (int i = 0, size = result.size(); i < size; i++) {

                    DataObject departmentStoreObject = (DataObject) result.getValue(i).get("stockGroupMachinery");
                    String directoryGroupCashRegister = (String) result.getValue(i).get("directoryGroupCashRegister").getValue();
                    boolean requestSalesInfoStock = equLM.findLCPByCompoundOldName("requestSalesInfoStock").read(session, departmentStoreObject) != null;
                    Date dateRequestSalesInfoStock = (Date) equLM.findLCPByCompoundOldName("dateRequestSalesInfoStock").read(session, departmentStoreObject);

                    String nameDepartmentStore = (String) cashRegisterLM.findLCPByCompoundOldName("nameDepartmentStore").read(session, departmentStoreObject);
                    logger.info("RequestSalesInfoStock: " + nameDepartmentStore + ": " + requestSalesInfoStock);

                    if (requestSalesInfoStock) {
                        equLM.findLCPByCompoundOldName("requestSalesInfoStock").change((Object) null, session, departmentStoreObject);
                        Set<String> directories = directoriesMap.containsKey(dateRequestSalesInfoStock) ? directoriesMap.get(dateRequestSalesInfoStock) : new HashSet<String>();
                        directories.add(directoryGroupCashRegister);
                        directoriesMap.put(dateRequestSalesInfoStock, directories);
                    }
                }
                session.apply(getBusinessLogics());
            } catch (ScriptingErrorLog.SemanticErrorException e) {
                throw Throwables.propagate(e);
            } catch (SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return directoriesMap;
    }

    @Override
    public List<CashRegisterInfo> readCashRegisterInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        try {

            List<CashRegisterInfo> cashRegisterInfoList = new ArrayList<CashRegisterInfo>();

            if (cashRegisterLM != null) {

                DataSession session = getDbManager().createSession();

                KeyExpr groupCashRegisterExpr = new KeyExpr("groupCashRegister");
                KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");

                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "GroupCashRegister", groupCashRegisterExpr, "cashRegister", cashRegisterExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);

                String[] cashRegisterProperties = new String[] {"nppMachinery", "nameModelMachinery", "handlerModelMachinery", "portMachinery"};
                for(String property : cashRegisterProperties)
                    query.addProperty(property, cashRegisterLM.findLCPByCompoundOldName(property).getExpr(cashRegisterExpr));
                String[] groupCashRegisterProperties = new String[] {"directoryGroupCashRegister", "startDateGroupCashRegister", "notDetailedGroupCashRegister", "nppGroupMachinery"};
                for(String property : groupCashRegisterProperties) 
                    query.addProperty(property, cashRegisterLM.findLCPByCompoundOldName(property).getExpr(groupCashRegisterExpr));
                
                query.and(cashRegisterLM.findLCPByCompoundOldName("handlerModelMachinery").getExpr(cashRegisterExpr).getWhere());
                query.and(cashRegisterLM.findLCPByCompoundOldName("directoryGroupCashRegister").getExpr(groupCashRegisterExpr).getWhere());
                query.and(cashRegisterLM.findLCPByCompoundOldName("groupCashRegisterCashRegister").getExpr(cashRegisterExpr).compare(groupCashRegisterExpr, Compare.EQUALS));
                query.and(equLM.findLCPByCompoundOldName("sidEquipmentServerGroupMachinery").getExpr(groupCashRegisterExpr).compare(new DataObject(sidEquipmentServer, StringClass.get(20)), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    cashRegisterInfoList.add(new CashRegisterInfo((Integer) row.get("nppGroupMachinery"), (Integer) row.get("nppMachinery"),
                            (String) row.get("nameModelMachinery"), (String) row.get("handlerModelMachinery"), (String) row.get("portMachinery"),
                            (String) row.get("directoryGroupCashRegister"), (java.sql.Date) row.get("startDateGroupCashRegister"), row.get("notDetailedGroupCashRegister") != null));
                }
            }
            return cashRegisterInfoList;
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e.toString());
        } catch (SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public List<TerminalInfo> readTerminalInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        try {

            List<TerminalInfo> terminalInfoList = new ArrayList<TerminalInfo>();

            if (terminalLM != null) {

                DataSession session = getDbManager().createSession();

                KeyExpr groupTerminalExpr = new KeyExpr("groupTerminal");
                KeyExpr terminalExpr = new KeyExpr("terminal");

                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "GroupTerminal", groupTerminalExpr, "terminal", terminalExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);

                query.addProperty("nppMachinery", terminalLM.findLCPByCompoundOldName("nppMachinery").getExpr(terminalExpr));
                query.addProperty("nameModelMachinery", terminalLM.findLCPByCompoundOldName("nameModelMachinery").getExpr(terminalExpr));
                query.addProperty("handlerModelMachinery", terminalLM.findLCPByCompoundOldName("handlerModelMachinery").getExpr(terminalExpr));
                query.addProperty("portMachinery", terminalLM.findLCPByCompoundOldName("portMachinery").getExpr(terminalExpr));
                query.addProperty("directoryGroupTerminal", terminalLM.findLCPByCompoundOldName("directoryGroupTerminal").getExpr(groupTerminalExpr));
                query.addProperty("priceListTypeGroupTerminal", terminalLM.findLCPByCompoundOldName("priceListTypeGroupTerminal").getExpr(groupTerminalExpr));
                
                query.and(terminalLM.findLCPByCompoundOldName("handlerModelMachinery").getExpr(terminalExpr).getWhere());
                query.and(terminalLM.findLCPByCompoundOldName("directoryGroupTerminal").getExpr(groupTerminalExpr).getWhere());
                query.and(terminalLM.findLCPByCompoundOldName("groupTerminalTerminal").getExpr(terminalExpr).compare(groupTerminalExpr, Compare.EQUALS));
                query.and(equLM.findLCPByCompoundOldName("sidEquipmentServerGroupMachinery").getExpr(groupTerminalExpr).compare(new DataObject(sidEquipmentServer, StringClass.get(20)), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    Integer priceListType = (Integer) row.get("priceListTypeGroupTerminal");
                    String idPriceListType = priceListType == null ? null : (String) terminalLM.findLCPByCompoundOldName("idPriceListType").read(session, new DataObject(priceListType));
                    terminalInfoList.add(new TerminalInfo((Integer) row.get("nppMachinery"), (String) row.get("nameModelMachinery"), 
                            (String) row.get("handlerModelMachinery"), (String) row.get("portMachinery"), idPriceListType, (String) row.get("directoryGroupTerminal")));
                }
            }
            return terminalInfoList;
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e.toString());
        } catch (SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public String sendTerminalInfo(List<TerminalDocumentDetail> terminalDocumentDetailList, String sidEquipmentServer) throws RemoteException, SQLException {

        try {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(terminalDocumentDetailList.size());

            ImportField idTerminalDocumentField = new ImportField(terminalLM.findLCPByCompoundOldName("idTerminalDocument"));
            ImportKey<?> terminalDocumentKey = new ImportKey((ConcreteCustomClass) terminalLM.findClassByCompoundName("TerminalDocument"),
                    terminalLM.findLCPByCompoundOldName("terminalDocumentId").getMapping(idTerminalDocumentField));
            keys.add(terminalDocumentKey);
            props.add(new ImportProperty(idTerminalDocumentField, terminalLM.findLCPByCompoundOldName("idTerminalDocument").getMapping(terminalDocumentKey)));
            props.add(new ImportProperty(idTerminalDocumentField, terminalLM.findLCPByCompoundOldName("titleTerminalDocument").getMapping(terminalDocumentKey)));
            fields.add(idTerminalDocumentField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).numberTerminalDocument);

            ImportField idTerminalHandbookType1TerminalDocumentField = new ImportField(terminalLM.findLCPByCompoundOldName("idTerminalHandbookType1TerminalDocument"));
            props.add(new ImportProperty(idTerminalHandbookType1TerminalDocumentField, terminalLM.findLCPByCompoundOldName("idTerminalHandbookType1TerminalDocument").getMapping(terminalDocumentKey)));
            fields.add(idTerminalHandbookType1TerminalDocumentField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).idTerminalHandbookType1);

            ImportField idTerminalHandbookType2TerminalDocumentField = new ImportField(terminalLM.findLCPByCompoundOldName("idTerminalHandbookType2TerminalDocument"));
            props.add(new ImportProperty(idTerminalHandbookType2TerminalDocumentField, terminalLM.findLCPByCompoundOldName("idTerminalHandbookType2TerminalDocument").getMapping(terminalDocumentKey)));
            fields.add(idTerminalHandbookType2TerminalDocumentField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).idTerminalHandbookType2);

            ImportField idTerminalDocumentTypeField = new ImportField(terminalLM.findLCPByCompoundOldName("idTerminalDocumentType"));
            ImportKey<?> terminalDocumentTypeKey = new ImportKey((ConcreteCustomClass) terminalLM.findClassByCompoundName("TerminalDocumentType"),
                    terminalLM.findLCPByCompoundOldName("terminalDocumentTypeId").getMapping(idTerminalDocumentTypeField));
            keys.add(terminalDocumentTypeKey);
            props.add(new ImportProperty(idTerminalDocumentTypeField, terminalLM.findLCPByCompoundOldName("idTerminalDocumentType").getMapping(terminalDocumentTypeKey)));
            props.add(new ImportProperty(idTerminalDocumentTypeField, terminalLM.findLCPByCompoundOldName("terminalDocumentTypeTerminalDocument").getMapping(terminalDocumentKey),
                    terminalLM.object(terminalLM.findClassByCompoundName("TerminalDocumentType")).getMapping(terminalDocumentTypeKey)));
            fields.add(idTerminalDocumentTypeField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).idTerminalDocumentType);
            
            ImportField numberTerminalDocumentDetailField = new ImportField(terminalLM.findLCPByCompoundOldName("numberTerminalDocumentDetail"));
            ImportKey<?> terminalDocumentDetailKey = new ImportKey((ConcreteCustomClass) terminalLM.findClassByCompoundName("TerminalDocumentDetail"),
                    terminalLM.findLCPByCompoundOldName("terminalDocumentDetailIdTerminalDocumentNumber").getMapping(idTerminalDocumentField, numberTerminalDocumentDetailField));
            keys.add(terminalDocumentDetailKey);
            props.add(new ImportProperty(numberTerminalDocumentDetailField, terminalLM.findLCPByCompoundOldName("numberTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
            props.add(new ImportProperty(idTerminalDocumentField, terminalLM.findLCPByCompoundOldName("terminalDocumentTerminalDocumentDetail").getMapping(terminalDocumentDetailKey),
                    terminalLM.object(terminalLM.findClassByCompoundName("TerminalDocument")).getMapping(terminalDocumentKey)));
            fields.add(numberTerminalDocumentDetailField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).numberTerminalDocumentDetail);

            ImportField barcodeTerminalDocumentDetailField = new ImportField(terminalLM.findLCPByCompoundOldName("barcodeTerminalDocumentDetail"));
            props.add(new ImportProperty(barcodeTerminalDocumentDetailField, terminalLM.findLCPByCompoundOldName("barcodeTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
            fields.add(barcodeTerminalDocumentDetailField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).barcodeTerminalDocumentDetail);

            ImportField priceTerminalDocumentDetailField = new ImportField(terminalLM.findLCPByCompoundOldName("priceTerminalDocumentDetail"));
            props.add(new ImportProperty(priceTerminalDocumentDetailField, terminalLM.findLCPByCompoundOldName("priceTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
            fields.add(priceTerminalDocumentDetailField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).priceTerminalDocumentDetail);

            ImportField quantityTerminalDocumentDetailField = new ImportField(terminalLM.findLCPByCompoundOldName("quantityTerminalDocumentDetail"));
            props.add(new ImportProperty(quantityTerminalDocumentDetailField, terminalLM.findLCPByCompoundOldName("quantityTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
            fields.add(quantityTerminalDocumentDetailField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).quantityTerminalDocumentDetail);

            ImportField sumTerminalDocumentDetailField = new ImportField(terminalLM.findLCPByCompoundOldName("sumTerminalDocumentDetail"));
            props.add(new ImportProperty(sumTerminalDocumentDetailField, terminalLM.findLCPByCompoundOldName("sumTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
            fields.add(sumTerminalDocumentDetailField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).sumTerminalDocumentDetail);
            

            ImportTable table = new ImportTable(fields, data);

            DataSession session = getDbManager().createSession();
            session.pushVolatileStats("ES_TI");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            String result = session.applyMessage(getBusinessLogics());
            session.popVolatileStats();
            session.close(); 
            
            return result;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public String sendSalesInfo(List<SalesInfo> salesInfoList, String sidEquipmentServer, Integer numberAtATime) throws IOException, SQLException {
        return sendSalesInfoNonRemote(salesInfoList, sidEquipmentServer, numberAtATime);
    }


    public String sendSalesInfoNonRemote(List<SalesInfo> salesInfoList, String sidEquipmentServer, Integer numberAtATime) throws IOException, SQLException {
        try {

            if (zReportLM != null && salesInfoList != null && !salesInfoList.isEmpty()) {

                Collections.sort(salesInfoList, COMPARATOR);

                if (numberAtATime == null)
                    numberAtATime = salesInfoList.size();

                for (int start = 0; true;) {

                    int finish = (start + numberAtATime) < salesInfoList.size() ? (start + numberAtATime) : salesInfoList.size();
                    
                    Integer lastNumberReceipt = start < finish ? salesInfoList.get(finish - 1).numberReceipt : null;
                    if(lastNumberReceipt != null) {
                        while(start < finish && salesInfoList.size() > finish && salesInfoList.get(finish).numberReceipt.equals(lastNumberReceipt))
                            finish++;                        
                    }
                    
                    List<SalesInfo> data = start < finish ? salesInfoList.subList(start, finish) : new ArrayList<SalesInfo>();
                    start = finish;
                    if (data.isEmpty())
                        return null;

                    logger.info(String.format("Kristal: Sending SalesInfo from %s to %s", start, finish));
                    
                    DataSession session = getDbManager().createSession();
                    ImportField nppGroupMachineryField = new ImportField(zReportLM.findLCPByCompoundOldName("nppGroupMachinery"));
                    ImportField nppMachineryField = new ImportField(zReportLM.findLCPByCompoundOldName("nppMachinery"));

                    ImportField idZReportField = new ImportField(zReportLM.findLCPByCompoundOldName("idZReport"));
                    ImportField numberZReportField = new ImportField(zReportLM.findLCPByCompoundOldName("numberZReport"));

                    ImportField idReceiptField = new ImportField(zReportLM.findLCPByCompoundOldName("idReceipt"));
                    ImportField numberReceiptField = new ImportField(zReportLM.findLCPByCompoundOldName("numberReceipt"));
                    ImportField dateReceiptField = new ImportField(zReportLM.findLCPByCompoundOldName("dateReceipt"));
                    ImportField timeReceiptField = new ImportField(zReportLM.findLCPByCompoundOldName("timeReceipt"));
                    ImportField isPostedZReportField = new ImportField(zReportLM.findLCPByCompoundOldName("isPostedZReport"));

                    ImportField idReceiptDetailField = new ImportField(zReportLM.findLCPByCompoundOldName("idReceiptDetail"));
                    ImportField numberReceiptDetailField = new ImportField(zReportLM.findLCPByCompoundOldName("numberReceiptDetail"));
                    ImportField idBarcodeReceiptDetailField = new ImportField(zReportLM.findLCPByCompoundOldName("idBarcodeReceiptDetail"));

                    ImportField quantityReceiptSaleDetailField = new ImportField(zReportLM.findLCPByCompoundOldName("quantityReceiptSaleDetail"));
                    ImportField priceReceiptSaleDetailField = new ImportField(zReportLM.findLCPByCompoundOldName("priceReceiptSaleDetail"));
                    ImportField sumReceiptSaleDetailField = new ImportField(zReportLM.findLCPByCompoundOldName("sumReceiptSaleDetail"));
                    ImportField discountSumReceiptSaleDetailField = new ImportField(zReportLM.findLCPByCompoundOldName("discountSumReceiptSaleDetail"));
                    ImportField discountSumSaleReceiptField = new ImportField(zReportLM.findLCPByCompoundOldName("discountSumSaleReceipt"));

                    ImportField quantityReceiptReturnDetailField = new ImportField(zReportLM.findLCPByCompoundOldName("quantityReceiptReturnDetail"));
                    ImportField priceReceiptReturnDetailField = new ImportField(zReportLM.findLCPByCompoundOldName("priceReceiptReturnDetail"));
                    ImportField retailSumReceiptReturnDetailField = new ImportField(zReportLM.findLCPByCompoundOldName("sumReceiptReturnDetail"));
                    ImportField discountSumReceiptReturnDetailField = new ImportField(zReportLM.findLCPByCompoundOldName("discountSumReceiptReturnDetail"));
                    ImportField discountSumReturnReceiptField = new ImportField(zReportLM.findLCPByCompoundOldName("discountSumReturnReceipt"));

                    ImportField idPaymentField = new ImportField(zReportLM.findLCPByCompoundOldName("ZReport.idPayment"));
                    ImportField sidTypePaymentField = new ImportField(zReportLM.findLCPByCompoundOldName("sidPaymentType"));
                    ImportField sumPaymentField = new ImportField(zReportLM.findLCPByCompoundOldName("ZReport.sumPayment"));
                    ImportField numberPaymentField = new ImportField(zReportLM.findLCPByCompoundOldName("ZReport.numberPayment"));

                    ImportField seriesNumberDiscountCardField = null;
                    if (discountCardLM != null)
                        seriesNumberDiscountCardField = new ImportField(discountCardLM.findLCPByCompoundOldName("seriesNumberDiscountCard"));

                    List<ImportProperty<?>> saleProperties = new ArrayList<ImportProperty<?>>();
                    List<ImportProperty<?>> returnProperties = new ArrayList<ImportProperty<?>>();
                    List<ImportProperty<?>> paymentProperties = new ArrayList<ImportProperty<?>>();

                    ImportKey<?> zReportKey = new ImportKey((ConcreteCustomClass) zReportLM.findClassByCompoundName("ZReport"), zReportLM.findLCPByCompoundOldName("zReportId").getMapping(idZReportField));
                    ImportKey<?> cashRegisterKey = new ImportKey((ConcreteCustomClass) zReportLM.findClassByCompoundName("CashRegister"), zReportLM.findLCPByCompoundOldName("cashRegisterNppGroupCashRegisterNpp").getMapping(nppGroupMachineryField, nppMachineryField));
                    ImportKey<?> receiptKey = new ImportKey((ConcreteCustomClass) zReportLM.findClassByCompoundName("Receipt"), zReportLM.findLCPByCompoundOldName("receiptId").getMapping(idReceiptField));
                    ImportKey<?> skuKey = new ImportKey((CustomClass) zReportLM.findClassByCompoundName("Sku"), zReportLM.findLCPByCompoundOldName("skuBarcodeIdDate").getMapping(idBarcodeReceiptDetailField, dateReceiptField));
                    ImportKey<?> discountCardKey = null;
                    if (discountCardLM != null)
                        discountCardKey = new ImportKey((ConcreteCustomClass) discountCardLM.findClassByCompoundName("DiscountCard"), discountCardLM.findLCPByCompoundOldName("discountCardSeriesNumber").getMapping(seriesNumberDiscountCardField, dateReceiptField));

                    saleProperties.add(new ImportProperty(idZReportField, zReportLM.findLCPByCompoundOldName("idZReport").getMapping(zReportKey)));
                    saleProperties.add(new ImportProperty(numberZReportField, zReportLM.findLCPByCompoundOldName("numberZReport").getMapping(zReportKey)));
                    saleProperties.add(new ImportProperty(nppMachineryField, zReportLM.findLCPByCompoundOldName("cashRegisterZReport").getMapping(zReportKey),
                            zReportLM.object(zReportLM.findClassByCompoundName("CashRegister")).getMapping(cashRegisterKey)));
                    saleProperties.add(new ImportProperty(dateReceiptField, zReportLM.findLCPByCompoundOldName("dateZReport").getMapping(zReportKey)));
                    saleProperties.add(new ImportProperty(timeReceiptField, zReportLM.findLCPByCompoundOldName("timeZReport").getMapping(zReportKey)));
                    saleProperties.add(new ImportProperty(isPostedZReportField, zReportLM.findLCPByCompoundOldName("isPostedZReport").getMapping(zReportKey)));

                    saleProperties.add(new ImportProperty(idReceiptField, zReportLM.findLCPByCompoundOldName("idReceipt").getMapping(receiptKey)));
                    saleProperties.add(new ImportProperty(numberReceiptField, zReportLM.findLCPByCompoundOldName("numberReceipt").getMapping(receiptKey)));
                    saleProperties.add(new ImportProperty(dateReceiptField, zReportLM.findLCPByCompoundOldName("dateReceipt").getMapping(receiptKey)));
                    saleProperties.add(new ImportProperty(timeReceiptField, zReportLM.findLCPByCompoundOldName("timeReceipt").getMapping(receiptKey)));
                    saleProperties.add(new ImportProperty(discountSumSaleReceiptField, zReportLM.findLCPByCompoundOldName("discountSumSaleReceipt").getMapping(receiptKey)));
                    saleProperties.add(new ImportProperty(numberZReportField, zReportLM.findLCPByCompoundOldName("zReportReceipt").getMapping(receiptKey),
                            zReportLM.object(zReportLM.findClassByCompoundName("ZReport")).getMapping(zReportKey)));
                    if (discountCardLM != null) {
                        saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, discountCardLM.findLCPByCompoundOldName("seriesNumberDiscountCard").getMapping(discountCardKey)));
                        saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, discountCardLM.findLCPByCompoundOldName("discountCardReceipt").getMapping(receiptKey),
                                discountCardLM.object(discountCardLM.findClassByCompoundName("DiscountCard")).getMapping(discountCardKey)));
                    }
                    ImportKey<?> receiptSaleDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClassByCompoundName("ReceiptSaleDetail"), zReportLM.findLCPByCompoundOldName("receiptDetailId").getMapping(idReceiptDetailField));
                    saleProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findLCPByCompoundOldName("idReceiptDetail").getMapping(receiptSaleDetailKey)));
                    saleProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findLCPByCompoundOldName("numberReceiptDetail").getMapping(receiptSaleDetailKey)));
                    saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findLCPByCompoundOldName("idBarcodeReceiptDetail").getMapping(receiptSaleDetailKey)));
                    saleProperties.add(new ImportProperty(quantityReceiptSaleDetailField, zReportLM.findLCPByCompoundOldName("quantityReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                    saleProperties.add(new ImportProperty(priceReceiptSaleDetailField, zReportLM.findLCPByCompoundOldName("priceReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                    saleProperties.add(new ImportProperty(sumReceiptSaleDetailField, zReportLM.findLCPByCompoundOldName("sumReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                    if (discountCardLM != null) {
                        saleProperties.add(new ImportProperty(discountSumReceiptSaleDetailField, discountCardLM.findLCPByCompoundOldName("discountSumReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                    }
                    saleProperties.add(new ImportProperty(idReceiptField, zReportLM.findLCPByCompoundOldName("receiptReceiptDetail").getMapping(receiptSaleDetailKey),
                            zReportLM.object(zReportLM.findClassByCompoundName("Receipt")).getMapping(receiptKey)));

                    saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findLCPByCompoundOldName("skuReceiptSaleDetail").getMapping(receiptSaleDetailKey),
                            zReportLM.object(zReportLM.findClassByCompoundName("Sku")).getMapping(skuKey)));

                    returnProperties.add(new ImportProperty(idZReportField, zReportLM.findLCPByCompoundOldName("idZReport").getMapping(zReportKey)));
                    returnProperties.add(new ImportProperty(numberZReportField, zReportLM.findLCPByCompoundOldName("numberZReport").getMapping(zReportKey)));
                    returnProperties.add(new ImportProperty(nppMachineryField, zReportLM.findLCPByCompoundOldName("cashRegisterZReport").getMapping(zReportKey),
                            zReportLM.object(zReportLM.findClassByCompoundName("CashRegister")).getMapping(cashRegisterKey)));
                    returnProperties.add(new ImportProperty(dateReceiptField, zReportLM.findLCPByCompoundOldName("dateZReport").getMapping(zReportKey)));
                    returnProperties.add(new ImportProperty(timeReceiptField, zReportLM.findLCPByCompoundOldName("timeZReport").getMapping(zReportKey)));
                    returnProperties.add(new ImportProperty(isPostedZReportField, zReportLM.findLCPByCompoundOldName("isPostedZReport").getMapping(zReportKey)));

                    returnProperties.add(new ImportProperty(idReceiptField, zReportLM.findLCPByCompoundOldName("idReceipt").getMapping(receiptKey)));
                    returnProperties.add(new ImportProperty(numberReceiptField, zReportLM.findLCPByCompoundOldName("numberReceipt").getMapping(receiptKey)));
                    returnProperties.add(new ImportProperty(dateReceiptField, zReportLM.findLCPByCompoundOldName("dateReceipt").getMapping(receiptKey)));
                    returnProperties.add(new ImportProperty(timeReceiptField, zReportLM.findLCPByCompoundOldName("timeReceipt").getMapping(receiptKey)));
                    if (discountCardLM != null) {
                        returnProperties.add(new ImportProperty(discountSumReturnReceiptField, discountCardLM.findLCPByCompoundOldName("discountSumReturnReceipt").getMapping(receiptKey)));
                    }
                    returnProperties.add(new ImportProperty(numberZReportField, zReportLM.findLCPByCompoundOldName("zReportReceipt").getMapping(receiptKey),
                            zReportLM.object(zReportLM.findClassByCompoundName("ZReport")).getMapping(zReportKey)));
                    if (discountCardLM != null) {
                        returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, discountCardLM.findLCPByCompoundOldName("seriesNumberDiscountCard").getMapping(discountCardKey)));
                        returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, discountCardLM.findLCPByCompoundOldName("discountCardReceipt").getMapping(receiptKey),
                                discountCardLM.object(discountCardLM.findClassByCompoundName("DiscountCard")).getMapping(discountCardKey)));
                    }
                    ImportKey<?> receiptReturnDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClassByCompoundName("ReceiptReturnDetail"), zReportLM.findLCPByCompoundOldName("receiptDetailId").getMapping(idReceiptDetailField));
                    returnProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findLCPByCompoundOldName("idReceiptDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findLCPByCompoundOldName("numberReceiptDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findLCPByCompoundOldName("idBarcodeReceiptDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(quantityReceiptReturnDetailField, zReportLM.findLCPByCompoundOldName("quantityReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(priceReceiptReturnDetailField, zReportLM.findLCPByCompoundOldName("priceReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(retailSumReceiptReturnDetailField, zReportLM.findLCPByCompoundOldName("sumReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(discountSumReceiptReturnDetailField, zReportLM.findLCPByCompoundOldName("discountSumReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(idReceiptField, zReportLM.findLCPByCompoundOldName("receiptReceiptDetail").getMapping(receiptReturnDetailKey),
                            zReportLM.object(zReportLM.findClassByCompoundName("Receipt")).getMapping(receiptKey)));

                    returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findLCPByCompoundOldName("skuReceiptReturnDetail").getMapping(receiptReturnDetailKey),
                            zReportLM.object(zReportLM.findClassByCompoundName("Sku")).getMapping(skuKey)));

                    List<List<Object>> dataSale = new ArrayList<List<Object>>();
                    List<List<Object>> dataReturn = new ArrayList<List<Object>>();

                    List<List<Object>> dataPayment = new ArrayList<List<Object>>();

                    for (SalesInfo sale : data) {
                        String idZReport = sale.numberGroupCashRegister + "_" + sale.numberCashRegister + "_" + sale.numberZReport; 
                        String idReceipt = sale.numberGroupCashRegister + "_" + sale.numberCashRegister + "_" + sale.numberZReport + "_" + sale.numberReceipt;
                        String idReceiptDetail = sale.numberGroupCashRegister + "_" + sale.numberCashRegister + "_"  + sale.numberZReport + "_" + sale.numberReceipt + "_" + sale.numberReceiptDetail;
                        if (sale.quantityReceiptDetail.doubleValue() < 0) {
                            List<Object> row = Arrays.<Object>asList(sale.numberGroupCashRegister, sale.numberCashRegister, idZReport, sale.numberZReport,
                                    sale.dateReceipt, sale.timeReceipt, true, idReceipt, sale.numberReceipt,
                                    idReceiptDetail, sale.numberReceiptDetail, sale.barcodeItem, sale.quantityReceiptDetail.negate(),
                                    sale.priceReceiptDetail, sale.sumReceiptDetail.negate(), sale.discountSumReceiptDetail,
                                    sale.discountSumReceipt);
                            if (discountCardLM != null)
                                row.add(sale.seriesNumberDiscountCard);
                            dataReturn.add(row);
                        } else {
                            List<Object> row = Arrays.<Object>asList(sale.numberGroupCashRegister, sale.numberCashRegister, idZReport, sale.numberZReport,
                                    sale.dateReceipt, sale.timeReceipt, true, idReceipt, sale.numberReceipt,
                                    idReceiptDetail, sale.numberReceiptDetail, sale.barcodeItem, sale.quantityReceiptDetail,
                                    sale.priceReceiptDetail, sale.sumReceiptDetail, sale.discountSumReceiptDetail,
                                    sale.discountSumReceipt);
                            if (discountCardLM != null)
                                row.add(sale.seriesNumberDiscountCard);
                            dataSale.add(row);
                        }
                        if (sale.sumCash != null && sale.sumCash.doubleValue() != 0) {
                            dataPayment.add(Arrays.<Object>asList(idReceipt + "1", idReceipt, "cash", sale.sumCash, 1));
                        }
                        if (sale.sumCard != null && sale.sumCard.doubleValue() != 0) {
                            dataPayment.add(Arrays.<Object>asList(idReceipt + "2", idReceipt, "card", sale.sumCard, 2));
                        }
                    }

                    List<ImportField> saleImportFields = Arrays.asList(nppGroupMachineryField, nppMachineryField,
                            idZReportField, numberZReportField, dateReceiptField, timeReceiptField, isPostedZReportField, idReceiptField,
                            numberReceiptField, idReceiptDetailField, numberReceiptDetailField, idBarcodeReceiptDetailField,
                            quantityReceiptSaleDetailField, priceReceiptSaleDetailField, sumReceiptSaleDetailField,
                            discountSumReceiptSaleDetailField, discountSumSaleReceiptField);
                    if (discountCardLM != null)
                        saleImportFields.add(seriesNumberDiscountCardField);

                    List<ImportField> returnImportFields = Arrays.asList(nppGroupMachineryField, nppMachineryField,
                            idZReportField, numberZReportField, dateReceiptField, timeReceiptField, isPostedZReportField, idReceiptField,
                            numberReceiptField, idReceiptDetailField, numberReceiptDetailField, idBarcodeReceiptDetailField,
                            quantityReceiptReturnDetailField, priceReceiptReturnDetailField, retailSumReceiptReturnDetailField,
                            discountSumReceiptReturnDetailField, discountSumReturnReceiptField);
                    if (discountCardLM != null)
                        returnImportFields.add(seriesNumberDiscountCardField);


                    List<ImportKey<?>> saleKeys = Arrays.asList(zReportKey, cashRegisterKey, receiptKey, receiptSaleDetailKey, skuKey);
                    if (discountCardLM != null)
                        saleKeys.add(discountCardKey);

                    session.pushVolatileStats("ES_SI");
                    new IntegrationService(session, new ImportTable(saleImportFields, dataSale), saleKeys, saleProperties).synchronize(true);

                    List<ImportKey<?>> returnKeys = Arrays.asList(zReportKey, cashRegisterKey, receiptKey, receiptReturnDetailKey, skuKey);
                    if (discountCardLM != null)
                        returnKeys.add(discountCardKey);
                    new IntegrationService(session, new ImportTable(returnImportFields, dataReturn), returnKeys, returnProperties).synchronize(true);

                    ImportKey<?> paymentKey = new ImportKey((ConcreteCustomClass) zReportLM.findClassByCompoundName("ZReport.Payment"), zReportLM.findLCPByCompoundOldName("ZReport.paymentId").getMapping(idPaymentField));
                    ImportKey<?> paymentTypeKey = new ImportKey((ConcreteCustomClass) zReportLM.findClassByCompoundName("PaymentType"), zReportLM.findLCPByCompoundOldName("typePaymentSID").getMapping(sidTypePaymentField));
                    paymentProperties.add(new ImportProperty(idPaymentField, zReportLM.findLCPByCompoundOldName("ZReport.idPayment").getMapping(paymentKey)));
                    paymentProperties.add(new ImportProperty(sumPaymentField, zReportLM.findLCPByCompoundOldName("ZReport.sumPayment").getMapping(paymentKey)));
                    paymentProperties.add(new ImportProperty(numberPaymentField, zReportLM.findLCPByCompoundOldName("ZReport.numberPayment").getMapping(paymentKey)));
                    paymentProperties.add(new ImportProperty(sidTypePaymentField, zReportLM.findLCPByCompoundOldName("paymentTypePayment").getMapping(paymentKey),
                            zReportLM.object(zReportLM.findClassByCompoundName("PaymentType")).getMapping(paymentTypeKey)));
                    paymentProperties.add(new ImportProperty(idReceiptField, zReportLM.findLCPByCompoundOldName("receiptPayment").getMapping(paymentKey),
                            zReportLM.object(zReportLM.findClassByCompoundName("Receipt")).getMapping(receiptKey)));

                    List<ImportField> paymentImportFields = Arrays.asList(idPaymentField, idReceiptField, sidTypePaymentField,
                            sumPaymentField, numberPaymentField);

                    String message = "Загружено записей: " + (dataSale.size() + dataReturn.size());
                    List<Integer> cashRegisterNumbers = new ArrayList<Integer>();
                    List<String> fileNames = new ArrayList<String>();
                    Set<String> dates = new HashSet<String>();
                    for (SalesInfo salesInfo : data) {
                        if (!cashRegisterNumbers.contains(salesInfo.numberCashRegister))
                            cashRegisterNumbers.add(salesInfo.numberCashRegister);
                        if ((salesInfo.filename != null) && (!fileNames.contains(salesInfo.filename.trim())))
                            fileNames.add(salesInfo.filename.trim());
                        if(salesInfo.dateReceipt != null)
                            dates.add(new SimpleDateFormat("dd.MM.yyyy").format(salesInfo.dateReceipt));
                    }
                    message += "\nИз касс: ";
                    for (Integer cashRegisterNumber : cashRegisterNumbers)
                        message += cashRegisterNumber + ", ";
                    message = message.substring(0, message.length() - 2);

                    message += "\nИз файлов: ";
                    for (String filename : fileNames)
                        message += filename + ", ";
                    message = message.substring(0, message.length() - 2);
                    
                    if(!dates.isEmpty()) {
                        message += "\nЗа даты: ";
                        for (String date : dates)
                            message += date + ", ";
                        message = message.substring(0, message.length() - 2);
                    }

                    DataObject logObject = session.addObject((ConcreteCustomClass) equLM.findClassByCompoundName("EquipmentServerLog"));
                    Object equipmentServerObject = equLM.findLCPByCompoundOldName("sidToEquipmentServer").read(session, new DataObject(sidEquipmentServer, StringClass.get(20)));
                    equLM.findLCPByCompoundOldName("equipmentServerEquipmentServerLog").change(equipmentServerObject, session, logObject);
                    equLM.findLCPByCompoundOldName("dataEquipmentServerLog").change(message, session, logObject);
                    equLM.findLCPByCompoundOldName("dateEquipmentServerLog").change(DateConverter.dateToStamp(Calendar.getInstance().getTime()), session, logObject);

                    new IntegrationService(session, new ImportTable(paymentImportFields, dataPayment), Arrays.asList(paymentKey, paymentTypeKey, receiptKey),
                            paymentProperties).synchronize(true);

                    String result = session.applyMessage(getBusinessLogics());
                    session.popVolatileStats();
                    if(result != null)
                        return result;
                }
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Set<String> readCashDocumentSet(String sidEquipmentServer) throws IOException, SQLException {

        Set<String> cashDocumentSet = new HashSet<String>();

        try {

            if (collectionLM != null) {

                DataSession session = getDbManager().createSession();

                KeyExpr cashDocumentExpr = new KeyExpr("cashDocument");
                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "CashDocument", cashDocumentExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
                query.addProperty("idCashDocument", collectionLM.findLCPByCompoundOldName("idCashDocument").getExpr(cashDocumentExpr));
                query.and(collectionLM.findLCPByCompoundOldName("idCashDocument").getExpr(cashDocumentExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    cashDocumentSet.add((String) row.get("idCashDocument"));
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (SQLHandledException e) {
            throw Throwables.propagate(e);
        }
        return cashDocumentSet;
    }

    @Override
    public String sendCashDocumentInfo(List<CashDocument> cashDocumentList, String sidEquipmentServer) throws IOException, SQLException {

        if (collectionLM != null && cashDocumentList != null) {

            try {

                List<ImportField> fieldsIncome = new ArrayList<ImportField>();
                List<ImportField> fieldsOutcome = new ArrayList<ImportField>();

                List<ImportProperty<?>> propsIncome = new ArrayList<ImportProperty<?>>();
                List<ImportProperty<?>> propsOutcome = new ArrayList<ImportProperty<?>>();

                List<ImportKey<?>> keysIncome = new ArrayList<ImportKey<?>>();
                List<ImportKey<?>> keysOutcome = new ArrayList<ImportKey<?>>();
                
                List<List<Object>> dataIncome = new ArrayList<List<Object>>();
                List<List<Object>> dataOutcome = new ArrayList<List<Object>>();

                ImportField idCashDocumentField = new ImportField(collectionLM.findLCPByCompoundOldName("idCashDocument"));               
                
                ImportKey<?> incomeCashOperationKey = new ImportKey((CustomClass) collectionLM.findClassByCompoundName("IncomeCashOperation"),
                        collectionLM.findLCPByCompoundOldName("cashDocumentId").getMapping(idCashDocumentField));
                keysIncome.add(incomeCashOperationKey);
                propsIncome.add(new ImportProperty(idCashDocumentField, collectionLM.findLCPByCompoundOldName("idCashDocument").getMapping(incomeCashOperationKey)));
                propsIncome.add(new ImportProperty(idCashDocumentField, collectionLM.findLCPByCompoundOldName("numberIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(idCashDocumentField);

                ImportKey<?> outcomeCashOperationKey = new ImportKey((CustomClass) collectionLM.findClassByCompoundName("OutcomeCashOperation"),
                        collectionLM.findLCPByCompoundOldName("cashDocumentId").getMapping(idCashDocumentField));
                keysOutcome.add(outcomeCashOperationKey);
                propsOutcome.add(new ImportProperty(idCashDocumentField, collectionLM.findLCPByCompoundOldName("idCashDocument").getMapping(outcomeCashOperationKey)));
                propsOutcome.add(new ImportProperty(idCashDocumentField, collectionLM.findLCPByCompoundOldName("numberOutcomeCashOperation").getMapping(outcomeCashOperationKey)));                
                fieldsOutcome.add(idCashDocumentField);
                
                ImportField dateIncomeCashOperationField = new ImportField(collectionLM.findLCPByCompoundOldName("dateIncomeCashOperation"));
                propsIncome.add(new ImportProperty(dateIncomeCashOperationField, collectionLM.findLCPByCompoundOldName("dateIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(dateIncomeCashOperationField);

                ImportField dateOutcomeCashOperationField = new ImportField(collectionLM.findLCPByCompoundOldName("dateOutcomeCashOperation"));
                propsOutcome.add(new ImportProperty(dateOutcomeCashOperationField, collectionLM.findLCPByCompoundOldName("dateOutcomeCashOperation").getMapping(outcomeCashOperationKey)));                
                fieldsOutcome.add(dateOutcomeCashOperationField);

                ImportField timeIncomeCashOperationField = new ImportField(collectionLM.findLCPByCompoundOldName("timeIncomeCashOperation"));
                propsIncome.add(new ImportProperty(timeIncomeCashOperationField, collectionLM.findLCPByCompoundOldName("timeIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(timeIncomeCashOperationField);
                
                ImportField timeOutcomeCashOperationField = new ImportField(collectionLM.findLCPByCompoundOldName("timeOutcomeCashOperation"));
                propsOutcome.add(new ImportProperty(timeOutcomeCashOperationField, collectionLM.findLCPByCompoundOldName("timeOutcomeCashOperation").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(timeOutcomeCashOperationField);

                ImportField numberCashRegisterField = new ImportField(collectionLM.findLCPByCompoundOldName("nppMachinery"));
                ImportField sidEquipmentServerField = new ImportField(equLM.findLCPByCompoundOldName("sidEquipmentServer"));
                ImportKey<?> cashRegisterKey = new ImportKey((ConcreteCustomClass) collectionLM.findClassByCompoundName("CashRegister"),
                        equLM.findLCPByCompoundOldName("cashRegisterNppEquipmentServer").getMapping(numberCashRegisterField, sidEquipmentServerField));
                
                keysIncome.add(cashRegisterKey);
                propsIncome.add(new ImportProperty(numberCashRegisterField, collectionLM.findLCPByCompoundOldName("cashRegisterIncomeCashOperation").getMapping(incomeCashOperationKey),
                        collectionLM.object(collectionLM.findClassByCompoundName("CashRegister")).getMapping(cashRegisterKey)));
                fieldsIncome.add(numberCashRegisterField);
                fieldsIncome.add(sidEquipmentServerField);
                
                keysOutcome.add(cashRegisterKey);
                propsOutcome.add(new ImportProperty(numberCashRegisterField, collectionLM.findLCPByCompoundOldName("cashRegisterOutcomeCashOperation").getMapping(outcomeCashOperationKey),
                        collectionLM.object(collectionLM.findClassByCompoundName("CashRegister")).getMapping(cashRegisterKey)));                
                fieldsOutcome.add(numberCashRegisterField);
                fieldsOutcome.add(sidEquipmentServerField);                

                ImportField sumCashIncomeCashOperationField = new ImportField(collectionLM.findLCPByCompoundOldName("sumCashIncomeCashOperation"));
                propsIncome.add(new ImportProperty(sumCashIncomeCashOperationField, collectionLM.findLCPByCompoundOldName("sumCashIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(sumCashIncomeCashOperationField);

                ImportField sumCashOutcomeCashOperationField = new ImportField(collectionLM.findLCPByCompoundOldName("sumCashOutcomeCashOperation"));
                propsOutcome.add(new ImportProperty(sumCashOutcomeCashOperationField, collectionLM.findLCPByCompoundOldName("sumCashOutcomeCashOperation").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(sumCashOutcomeCashOperationField);

                for (CashDocument cashDocument : cashDocumentList) {
                    if (cashDocument.sumCashDocument != null) {
                        if (cashDocument.sumCashDocument.compareTo(BigDecimal.ZERO) >= 0)
                            dataIncome.add(Arrays.asList((Object) cashDocument.numberCashDocument, cashDocument.dateCashDocument,
                                    cashDocument.timeCashDocument, cashDocument.numberCashRegister, sidEquipmentServer, cashDocument.sumCashDocument));
                        else
                            dataOutcome.add(Arrays.asList((Object) cashDocument.numberCashDocument, cashDocument.dateCashDocument,
                                    cashDocument.timeCashDocument, cashDocument.numberCashRegister, sidEquipmentServer, cashDocument.sumCashDocument.negate()));
                    }
                }
                
                
                ImportTable table = new ImportTable(fieldsIncome, dataIncome);
                DataSession session = getDbManager().createSession();
                session.pushVolatileStats("ES_CDI");
                IntegrationService service = new IntegrationService(session, table, keysIncome, propsIncome);
                service.synchronize(true, false);
                String resultIncome = session.applyMessage(getBusinessLogics());
                session.popVolatileStats();
                session.close();

                if(resultIncome != null)
                    return resultIncome;
                
                table = new ImportTable(fieldsOutcome, dataOutcome);
                session = getDbManager().createSession();
                session.pushVolatileStats("ES_CDI");
                service = new IntegrationService(session, table, keysOutcome, propsOutcome);
                service.synchronize(true, false);
                String resultOutcome = session.applyMessage(getBusinessLogics());
                session.popVolatileStats();
                session.close();
                
                return resultOutcome;
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        } else return null;
    }

    @Override
    public void succeedTransaction(Integer transactionID, Timestamp dateTime) throws RemoteException, SQLException {
        try {
            DataSession session = getDbManager().createSession();
            equLM.findLCPByCompoundOldName("succeededMachineryPriceTransaction").change(true, session,
                    session.getDataObject(equLM.findClassByCompoundName("MachineryPriceTransaction"), transactionID));
            equLM.findLCPByCompoundOldName("dateTimeSucceededMachineryPriceTransaction").change(dateTime, session,
                    session.getDataObject(equLM.findClassByCompoundName("MachineryPriceTransaction"), transactionID));
            session.apply(getBusinessLogics());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public List<byte[][]> readLabelFormats(List<String> scalesModelsList) throws RemoteException, SQLException {
        try {
            DataSession session = getDbManager().createSession();

            List<byte[][]> fileLabelFormats = new ArrayList<byte[][]>();

            if (scalesLM != null) {

                for (String scalesModel : scalesModelsList) {

                    DataObject scalesModelObject = new DataObject(scalesLM.findLCPByCompoundOldName("scalesModelName").read(session, new DataObject(scalesModel)), (ConcreteClass) scalesLM.findClassByCompoundName("scalesModel"));

                    LCP<PropertyInterface> isLabelFormat = (LCP<PropertyInterface>) scalesLM.is(scalesLM.findClassByCompoundName("LabelFormat"));

                    ImRevMap<PropertyInterface, KeyExpr> labelFormatKeys = isLabelFormat.getMapKeys();
                    KeyExpr labelFormatKey = labelFormatKeys.singleValue();
                    QueryBuilder<PropertyInterface, Object> labelFormatQuery = new QueryBuilder<PropertyInterface, Object>(labelFormatKeys);

                    labelFormatQuery.addProperty("fileLabelFormat", scalesLM.findLCPByCompoundOldName("fileLabelFormat").getExpr(labelFormatKey));
                    labelFormatQuery.addProperty("fileMessageLabelFormat", scalesLM.findLCPByCompoundOldName("fileMessageLabelFormat").getExpr(labelFormatKey));
                    labelFormatQuery.and(isLabelFormat.property.getExpr(labelFormatKeys).getWhere());
                    labelFormatQuery.and(scalesLM.findLCPByCompoundOldName("scalesModelLabelFormat").getExpr(labelFormatKey).compare((scalesModelObject).getExpr(), Compare.EQUALS));

                    ImOrderMap<ImMap<PropertyInterface, Object>, ImMap<Object, Object>> labelFormatResult = labelFormatQuery.execute(session);

                    for (ImMap<Object, Object> row : labelFormatResult.valueIt()) {
                        byte[] fileLabelFormat = (byte[]) row.get("fileLabelFormat");
                        byte[] fileMessageLabelFormat = (byte[]) row.get("fileMessageLabelFormat");
                        fileLabelFormats.add(new byte[][]{fileLabelFormat, fileMessageLabelFormat});
                    }
                }
            }
            return fileLabelFormats;
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void errorTransactionReport(Integer transactionID, Exception e) throws RemoteException, SQLException {
        try {
            DataSession session = getDbManager().createSession();
            DataObject errorObject = session.addObject((ConcreteCustomClass) equLM.findClassByCompoundName("MachineryPriceTransactionError"));
            equLM.findLCPByCompoundOldName("machineryPriceTransactionMachineryPriceTransactionError").change(transactionID, session, errorObject);
            equLM.findLCPByCompoundOldName("dataMachineryPriceTransactionError").change(e.toString(), session, errorObject);
            equLM.findLCPByCompoundOldName("dateMachineryPriceTransactionError").change(DateConverter.dateToStamp(Calendar.getInstance().getTime()), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(os));
            equLM.findLCPByCompoundOldName("errorTraceMachineryPriceTransactionError").change(os.toString(), session, errorObject);

            session.apply(getBusinessLogics());
        } catch (Exception e2) {
            throw Throwables.propagate(e2);
        }
    }

    @Override
    public void errorEquipmentServerReport(String equipmentServer, Throwable exception) throws
            RemoteException, SQLException {
        try {
            DataSession session = getDbManager().createSession();
            DataObject errorObject = session.addObject((ConcreteCustomClass) equLM.findClassByCompoundName("EquipmentServerError"));
            Object equipmentServerObject = equLM.findLCPByCompoundOldName("sidToEquipmentServer").read(session, new DataObject(equipmentServer, StringClass.get(20)));
            equLM.findLCPByCompoundOldName("equipmentServerEquipmentServerError").change(equipmentServerObject, session, errorObject);
            equLM.findLCPByCompoundOldName("dataEquipmentServerError").change(exception.toString(), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            exception.printStackTrace(new PrintStream(os));
            equLM.findLCPByCompoundOldName("erTraceEquipmentServerError").change(os.toString(), session, errorObject);

            equLM.findLCPByCompoundOldName("dateEquipmentServerError").change(DateConverter.dateToStamp(Calendar.getInstance().getTime()), session, errorObject);

            session.apply(getBusinessLogics());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public EquipmentServerSettings readEquipmentServerSettings(String equipmentServer) throws RemoteException, SQLException {
        try {
            ThreadLocalContext.set(logicsInstance.getContext());
            DataSession session = getDbManager().createSession();
            Integer equipmentServerID = (Integer) equLM.findLCPByCompoundOldName("sidToEquipmentServer").read(session, new DataObject(equipmentServer, StringClass.get(20)));
            if (equipmentServerID != null) {
                DataObject equipmentServerObject = new DataObject(equipmentServerID, (ConcreteClass) equLM.findClassByCompoundName("EquipmentServer"));
                Integer delay = (Integer) equLM.findLCPByCompoundOldName("delayEquipmentServer").read(session, equipmentServerObject);
                Integer numberAtATime = (Integer) equLM.findLCPByCompoundOldName("numberAtATimeEquipmentServer").read(session, equipmentServerObject);
                return new EquipmentServerSettings(delay, numberAtATime);
            } else return null;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private String dateTimeCode(Timestamp timeStamp) {
        String result = "";
        long time = timeStamp.getTime() / 1000;
        while (time > 26) {
            result = (char) (time % 26 + 97) + result;
            time = time / 26;
        }
        result = (char) (time + 97) + result;
        return result;
    }

    private String trim(String input) {
        return input == null ? null : input.trim();
    }

    private List<List<Object>> initData(int size) {
        List<List<Object>> data = new ArrayList<List<Object>>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<Object>());
        }
        return data;
    }

    private static Comparator<SalesInfo> COMPARATOR = new Comparator<SalesInfo>() {
        public int compare(SalesInfo o1, SalesInfo o2) {
            int compareCashRegister = BaseUtils.nullCompareTo(o1.numberCashRegister, o2.numberCashRegister); 
            if (compareCashRegister == 0)
                return BaseUtils.nullCompareTo(o1.numberZReport, o2.numberZReport);                    
            else
                return compareCashRegister;
        }
    };

    @Aspect
    private static class RemoteLogicsContextHoldingAspect {
        @Before("execution(* equ.api.EquipmentServerInterface.*(..)) && target(remoteLogics)")
        public void beforeCall(EquipmentServer remoteLogics) {
            ThreadLocalContext.set(remoteLogics.logicsInstance.getContext());
        }

        @AfterReturning("execution(* equ.api.EquipmentServerInterface.*(..)) && target(remoteLogics)")
        public void afterReturning(EquipmentServer remoteLogics) {
            ThreadLocalContext.set(null);
        }
    }
}
