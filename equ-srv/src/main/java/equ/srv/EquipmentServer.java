package equ.srv;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
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
import lsfusion.server.data.expr.ValueExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.lifecycle.LifecycleAdapter;
import lsfusion.server.lifecycle.LifecycleEvent;
import lsfusion.server.logics.*;
import lsfusion.server.logics.linear.LCP;
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

import static org.apache.commons.lang.StringUtils.trim;

public class EquipmentServer extends LifecycleAdapter implements EquipmentServerInterface, InitializingBean {
    private static final Logger logger = Logger.getLogger(EquipmentServer.class);

    public static final String EXPORT_NAME = "EquipmentServer";

    private LogicsInstance logicsInstance;

    private SoftCheckInterface softCheck;

    private PromotionInterface promotionInterface;
    
    private ScriptingLogicsModule equLM;

    //Опциональные модули
    private ScriptingLogicsModule cashRegisterLM;
    private ScriptingLogicsModule cashRegisterItemLM;
    private ScriptingLogicsModule collectionLM;
    private ScriptingLogicsModule discountCardLM;
    private ScriptingLogicsModule equipmentLM;
    private ScriptingLogicsModule equipmentCashRegisterLM;
    private ScriptingLogicsModule giftCardLM;
    private ScriptingLogicsModule itemLM;
    private ScriptingLogicsModule itemFashionLM;    
    private ScriptingLogicsModule legalEntityLM;
    private ScriptingLogicsModule machineryLM;
    private ScriptingLogicsModule machineryPriceTransactionLM;
    private ScriptingLogicsModule machineryPriceTransactionSectionLM;
    private ScriptingLogicsModule machineryPriceTransactionStockTaxLM;
    private ScriptingLogicsModule priceCheckerLM;
    private ScriptingLogicsModule priceListLedgerLM;
    private ScriptingLogicsModule purchaseInvoiceAgreementLM;
    private ScriptingLogicsModule scalesLM;
    private ScriptingLogicsModule scalesItemLM;
    private ScriptingLogicsModule stopListLM;
    private ScriptingLogicsModule terminalLM;
    private ScriptingLogicsModule zReportLM;
    private ScriptingLogicsModule zReportDiscountCardLM;
    private ScriptingLogicsModule zReportSectionLM;
    
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

    public void setPromotionHandler(PromotionInterface promotionInterface) {
        this.promotionInterface = promotionInterface;
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
        equLM = getBusinessLogics().getModule("Equipment");
        Assert.notNull(equLM, "can't find Equipment module");
        cashRegisterLM = getBusinessLogics().getModule("EquipmentCashRegister");
        cashRegisterItemLM = getBusinessLogics().getModule("CashRegisterItem");
        collectionLM = getBusinessLogics().getModule("Collection");
        discountCardLM = getBusinessLogics().getModule("DiscountCard");
        equipmentLM = getBusinessLogics().getModule("Equipment");
        equipmentCashRegisterLM = getBusinessLogics().getModule("EquipmentCashRegister");
        giftCardLM = getBusinessLogics().getModule("GiftCard");
        itemLM = getBusinessLogics().getModule("Item");
        itemFashionLM = getBusinessLogics().getModule("ItemFashion");
        legalEntityLM = getBusinessLogics().getModule("LegalEntity");
        machineryLM = getBusinessLogics().getModule("Machinery");
        machineryPriceTransactionLM = getBusinessLogics().getModule("MachineryPriceTransaction");
        machineryPriceTransactionSectionLM = getBusinessLogics().getModule("MachineryPriceTransactionSection");
        machineryPriceTransactionStockTaxLM = getBusinessLogics().getModule("MachineryPriceTransactionStockTax");
        priceCheckerLM = getBusinessLogics().getModule("EquipmentPriceChecker");
        priceListLedgerLM = getBusinessLogics().getModule("PriceListLedger");
        purchaseInvoiceAgreementLM = getBusinessLogics().getModule("PurchaseInvoiceAgreement");
        scalesLM = getBusinessLogics().getModule("EquipmentScales");
        scalesItemLM = getBusinessLogics().getModule("ScalesItem");
        stopListLM = getBusinessLogics().getModule("StopList");
        terminalLM = getBusinessLogics().getModule("EquipmentTerminal");
        zReportLM = getBusinessLogics().getModule("ZReport");
        zReportDiscountCardLM = getBusinessLogics().getModule("ZReportDiscountCard");
        zReportSectionLM = getBusinessLogics().getModule("ZReportSection");
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        if(getDbManager().isServer()) {
            logger.info("Binding Equipment Server.");
            try {
                getRmiManager().bindAndExport(EXPORT_NAME, this);
                started = true;
            } catch (Exception e) {
                throw new RuntimeException("Error exporting Equipment Server: ", e);
            }
        } else {
            logger.info("Equipment Server disabled, change serverComputer() to enable");
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

    public EquipmentServer() {
        super(HIGH_DAEMON_ORDER);
    }

    @Override
    public boolean enabledSoftCheckInfo() throws RemoteException, SQLException {
        return softCheck != null;
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
    public String sendSucceededSoftCheckInfo(Map<String, Timestamp> invoiceSet) throws RemoteException, SQLException {
        return softCheck == null ? null : softCheck.sendSucceededSoftCheckInfo(invoiceSet);
    }

    @Override
    public String sendCashierTimeList(List<CashierTime> cashierTimeList) throws RemoteException, SQLException {
        return softCheck == null ? null : softCheck.sendCashierTimeList(cashierTimeList);
    }

    @Override
    public List<TransactionInfo> readTransactionInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        try (DataSession session = getDbManager().createSession()) {
            List<TransactionInfo> transactionList = new ArrayList<>();

            KeyExpr machineryPriceTransactionExpr = new KeyExpr("machineryPriceTransaction");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "machineryPriceTransaction", machineryPriceTransactionExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

            String[] mptNames = new String[]{"dateTimeMachineryPriceTransaction", "groupMachineryMachineryPriceTransaction",
                    "nppGroupMachineryMachineryPriceTransaction", "nameGroupMachineryMachineryPriceTransaction", "snapshotMachineryPriceTransaction",
                    "descriptionMachineryPriceTransaction", "lastDateMachineryPriceTransactionErrorMachineryPriceTransaction"};
            LCP[] mptProperties = equLM.findProperties("dateTimeMachineryPriceTransaction", "groupMachineryMachineryPriceTransaction",
                    "nppGroupMachineryMachineryPriceTransaction", "nameGroupMachineryMachineryPriceTransaction", "snapshotMachineryPriceTransaction",
                    "descriptionMachineryPriceTransaction", "lastDateMachineryPriceTransactionErrorMachineryPriceTransaction");
            for (int i = 0; i < mptProperties.length; i++) {
                query.addProperty(mptNames[i], mptProperties[i].getExpr(machineryPriceTransactionExpr));
            }
            query.and(equLM.findProperty("sidEquipmentServerMachineryPriceTransaction").getExpr(machineryPriceTransactionExpr).compare(new DataObject(sidEquipmentServer), Compare.EQUALS));
            query.and(equLM.findProperty("processMachineryPriceTransaction").getExpr(machineryPriceTransactionExpr).getWhere());

            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(session);
            List<Object[]> transactionObjects = new ArrayList<>();
            for (int i = 0, size = result.size(); i < size; i++) {
                ImMap<Object, ObjectValue> value = result.getValue(i);
                ObjectValue dateTimeMPT = value.get("dateTimeMachineryPriceTransaction");
                if(dateTimeMPT instanceof DataObject) {
                    DataObject groupMachineryMPT = (DataObject) value.get("groupMachineryMachineryPriceTransaction");
                    Integer nppGroupMachineryMPT = (Integer) value.get("nppGroupMachineryMachineryPriceTransaction").getValue();
                    String nameGroupMachineryMPT = (String) value.get("nameGroupMachineryMachineryPriceTransaction").getValue();
                    DataObject transactionObject = result.getKey(i).singleValue();
                    boolean snapshotMPT = value.get("snapshotMachineryPriceTransaction") instanceof DataObject;
                    String descriptionMPT = (String) value.get("descriptionMachineryPriceTransaction").getValue();
                    Timestamp lastErrorDate = (Timestamp) value.get("lastDateMachineryPriceTransactionErrorMachineryPriceTransaction").getValue();
                    transactionObjects.add(new Object[]{groupMachineryMPT, nppGroupMachineryMPT, nameGroupMachineryMPT, transactionObject,
                            dateTimeCode((Timestamp) dateTimeMPT.getValue()), dateTimeMPT, snapshotMPT, descriptionMPT, lastErrorDate});
                }
            }

            Map<String, List<ItemGroup>> itemGroupMap = transactionObjects.isEmpty() ? null : readItemGroupMap(session);

            for (Object[] transaction : transactionObjects) {

                DataObject groupMachineryObject = (DataObject) transaction[0];
                Integer nppGroupMachinery = (Integer) transaction[1];
                String nameGroupMachinery = (String) transaction[2];
                DataObject transactionObject = (DataObject) transaction[3];
                String dateTimeCode = (String) transaction[4];
                Date date = new Date(((Timestamp) ((DataObject) transaction[5]).getValue()).getTime());
                boolean snapshotTransaction = (boolean) transaction[6];
                String descriptionTransaction = (String) transaction[7];
                Timestamp lastErrorDateTransaction = (Timestamp) transaction[8];

                boolean isCashRegisterPriceTransaction = cashRegisterLM != null && transactionObject.objectClass.equals(cashRegisterLM.findClass("CashRegisterPriceTransaction"));
                boolean isScalesPriceTransaction = scalesLM != null && transactionObject.objectClass.equals(scalesLM.findClass("ScalesPriceTransaction"));
                boolean isPriceCheckerPriceTransaction = priceCheckerLM != null && transactionObject.objectClass.equals(priceCheckerLM.findClass("PriceCheckerPriceTransaction"));
                boolean isTerminalPriceTransaction = terminalLM != null && transactionObject.objectClass.equals(terminalLM.findClass("TerminalPriceTransaction"));
                
                String handlerModelGroupMachinery = (String) equLM.findProperty("handlerModelGroupMachinery").read(session, groupMachineryObject);
                String nameModelGroupMachinery = (String) equLM.findProperty("nameModelGroupMachinery").read(session, groupMachineryObject);

                ValueExpr transactionExpr = transactionObject.getExpr();
                KeyExpr barcodeExpr = new KeyExpr("barcode");
                ImRevMap<Object, KeyExpr> skuKeys = MapFact.singletonRev((Object) "barcode", barcodeExpr);

                QueryBuilder<Object, Object> skuQuery = new QueryBuilder<>(skuKeys);

                String[] skuNames = new String[]{"nameMachineryPriceTransactionBarcode", "priceMachineryPriceTransactionBarcode",
                        "expiryDateMachineryPriceTransactionBarcode", "splitMachineryPriceTransactionBarcode", "passScalesMachineryPriceTransactionBarcode",
                        "idUOMMachineryPriceTransactionBarcode", "shortNameUOMMachineryPriceTransactionBarcode", "pluNumberMachineryPriceTransactionBarcode",
                        "flagsMachineryPriceTransactionBarcode", "expiryDaysMachineryPriceTransactionBarcode", "minPriceMachineryPriceTransactionBarcode",
                        "canonicalNameSkuGroupMachineryPriceTransactionBarcode"};
                LCP[] skuProperties = equLM.findProperties("nameMachineryPriceTransactionBarcode", "priceMachineryPriceTransactionBarcode",
                        "expiryDateMachineryPriceTransactionBarcode", "splitMachineryPriceTransactionBarcode", "passScalesMachineryPriceTransactionBarcode",
                        "idUOMMachineryPriceTransactionBarcode", "shortNameUOMMachineryPriceTransactionBarcode", "pluNumberMachineryPriceTransactionBarcode",
                        "flagsMachineryPriceTransactionBarcode", "expiryDaysMachineryPriceTransactionBarcode", "minPriceMachineryPriceTransactionBarcode",
                        "canonicalNameSkuGroupMachineryPriceTransactionBarcode");
                for (int i = 0; i < skuProperties.length; i++) {
                    skuQuery.addProperty(skuNames[i], skuProperties[i].getExpr(transactionExpr, barcodeExpr));
                }

                String[] barcodeNames = new String[]{"idBarcode", "skuBarcode", "idSkuBarcode", "skuGroupBarcode"};
                LCP[] barcodeProperties = equLM.findProperties("idBarcode", "skuBarcode", "idSkuBarcode", "skuGroupBarcode");
                for (int i = 0; i < barcodeProperties.length; i++) {
                    skuQuery.addProperty(barcodeNames[i], barcodeProperties[i].getExpr(barcodeExpr));
                }

                if (isCashRegisterPriceTransaction) {
                    skuQuery.addProperty("amountBarcode", equLM.findProperty("amountBarcode").getExpr(barcodeExpr));
                }
                
                if(itemLM != null) {
                    skuQuery.addProperty("idBrandBarcode", itemLM.findProperty("idBrandBarcode").getExpr(barcodeExpr));
                    skuQuery.addProperty("nameBrandBarcode", itemLM.findProperty("nameBrandBarcode").getExpr(barcodeExpr));
                }
                
                if(itemFashionLM != null) {
                    skuQuery.addProperty("idSeasonBarcode", itemFashionLM.findProperty("idSeasonBarcode").getExpr(barcodeExpr));
                    skuQuery.addProperty("nameSeasonBarcode", itemFashionLM.findProperty("nameSeasonBarcode").getExpr(barcodeExpr));
                }

                if(cashRegisterItemLM != null) {
                    skuQuery.addProperty("CashRegisterItem.idSkuGroupMachineryPriceTransactionBarcode", 
                            cashRegisterItemLM.findProperty("idSkuGroupMachineryPriceTransactionBarcode").getExpr(transactionExpr, barcodeExpr));
                    skuQuery.addProperty("CashRegisterItem.overIdSkuGroupMachineryPriceTransactionBarcode",
                            cashRegisterItemLM.findProperty("overIdSkuGroupMachineryPriceTransactionBarcode").getExpr(transactionExpr, barcodeExpr));
                }
                
                if(scalesItemLM != null) {
                    skuQuery.addProperty("ScalesItem.idSkuGroupMachineryPriceTransactionBarcode", 
                            scalesItemLM.findProperty("idSkuGroupMachineryPriceTransactionBarcode").getExpr(transactionExpr, barcodeExpr));
                }
                
                if (scalesItemLM != null) {
                    String[] scalesSkuNames = new String[]{"hoursExpiryMachineryPriceTransactionBarcode",
                            "labelFormatMachineryPriceTransactionBarcode", "descriptionMachineryPriceTransactionBarcode", "descriptionNumberMachineryPriceTransactionBarcode"};
                    LCP[] scalesSkuProperties = scalesItemLM.findProperties("hoursExpiryMachineryPriceTransactionBarcode",
                            "labelFormatMachineryPriceTransactionBarcode", "descriptionMachineryPriceTransactionBarcode", "descriptionNumberMachineryPriceTransactionBarcode");
                    for (int i = 0; i < scalesSkuProperties.length; i++) {
                        skuQuery.addProperty(scalesSkuNames[i], scalesSkuProperties[i].getExpr(transactionExpr, barcodeExpr));
                    }
                }
                
                if (machineryPriceTransactionStockTaxLM != null) {
                    String[] taxNames = new String[]{"VATMachineryPriceTransactionBarcode"};
                    LCP[] taxProperties = machineryPriceTransactionStockTaxLM.findProperties("VATMachineryPriceTransactionBarcode");
                    for (int i = 0; i < taxProperties.length; i++) {
                        skuQuery.addProperty(taxNames[i], taxProperties[i].getExpr(transactionExpr, barcodeExpr));
                    }
                }

                if(machineryPriceTransactionSectionLM != null) {
                    skuQuery.addProperty("sectionMachineryPriceTransactionBarcode",
                            machineryPriceTransactionSectionLM.findProperty("sectionMachineryPriceTransactionBarcode").getExpr(transactionExpr, barcodeExpr));
                    skuQuery.addProperty("deleteSectionBarcode",
                            machineryPriceTransactionSectionLM.findProperty("deleteSectionBarcode").getExpr(barcodeExpr));
                }

                skuQuery.and(equLM.findProperty("inMachineryPriceTransactionBarcode").getExpr(transactionExpr, barcodeExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> skuResult = skuQuery.execute(session);

                String[] machineryNames = new String[]{"nppMachinery", "portMachinery", "overDirectoryMachinery"};
                LCP[] machineryProperties = machineryLM.findProperties("nppMachinery", "portMachinery", "overDirectoryMachinery");
                
                if (isCashRegisterPriceTransaction) {
                    
                    java.sql.Date startDateGroupCashRegister = (java.sql.Date) cashRegisterLM.findProperty("startDateGroupCashRegister").read(session, groupMachineryObject);
                    boolean notDetailedGroupCashRegister = cashRegisterLM.findProperty("notDetailedGroupCashRegister").read(session, groupMachineryObject) != null;
                    Integer overDepartmentNumberGroupCashRegister = (Integer) cashRegisterLM.findProperty("overDepartmentNumberGroupCashRegister").read(session, groupMachineryObject);
                    String idDepartmentStoreGroupCashRegister = (String) cashRegisterLM.findProperty("idDepartmentStoreGroupCashRegister").read(session, groupMachineryObject);
                    String pieceCodeGroupCashRegister = (String) cashRegisterLM.findProperty("pieceCodeGroupCashRegister").read(session, groupMachineryObject);
                    String weightCodeGroupCashRegister = (String) cashRegisterLM.findProperty("weightCodeGroupCashRegister").read(session, groupMachineryObject);
                    String nameStockGroupCashRegister = (String) cashRegisterLM.findProperty("nameStockGroupMachinery").read(session, groupMachineryObject);

                    List<CashRegisterInfo> cashRegisterInfoList = new ArrayList<>();
                    KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");
                    ImRevMap<Object, KeyExpr> cashRegisterKeys = MapFact.singletonRev((Object) "cashRegister", cashRegisterExpr);
                    QueryBuilder<Object, Object> cashRegisterQuery = new QueryBuilder<>(cashRegisterKeys);
                    
                    for (int i = 0; i < machineryProperties.length; i++) {
                        cashRegisterQuery.addProperty(machineryNames[i], machineryProperties[i].getExpr(cashRegisterExpr));
                    }
                    cashRegisterQuery.addProperty("disableSalesCashRegister", cashRegisterLM.findProperty("disableSalesCashRegister").getExpr(cashRegisterExpr));
                    cashRegisterQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                            cashRegisterLM.findProperty("succeededMachineryMachineryPriceTransaction").getExpr(cashRegisterExpr, transactionExpr));
                    cashRegisterQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                            cashRegisterLM.findProperty("clearedMachineryMachineryPriceTransaction").getExpr(cashRegisterExpr, transactionExpr));
                    cashRegisterQuery.addProperty("inMachineryPriceTransactionMachinery",
                            equLM.findProperty("inMachineryPriceTransactionMachinery").getExpr(transactionExpr, cashRegisterExpr));
                    cashRegisterQuery.and(cashRegisterLM.findProperty("groupCashRegisterCashRegister").getExpr(cashRegisterExpr).compare(groupMachineryObject, Compare.EQUALS));
                    query.and(cashRegisterLM.findProperty("activeCashRegister").getExpr(cashRegisterExpr).getWhere());

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> cashRegisterResult = cashRegisterQuery.execute(session);

                    for (ImMap<Object, Object> row : cashRegisterResult.valueIt()) {
                        Integer nppMachinery = (Integer) row.get("nppMachinery");
                        String portMachinery = (String) row.get("portMachinery");
                        String directoryCashRegister = (String) row.get("overDirectoryMachinery");
                        boolean succeeded = row.get("succeededMachineryMachineryPriceTransaction") != null;
                        boolean cleared = row.get("clearedMachineryPriceTransaction") != null;
                        Boolean disableSalesCashRegister = row.get("disableSalesCashRegister") != null;
                        boolean enabled = row.get("inMachineryPriceTransactionMachinery") != null;
                        cashRegisterInfoList.add(new CashRegisterInfo(enabled, cleared, succeeded, nppGroupMachinery, nppMachinery,
                                nameModelGroupMachinery, handlerModelGroupMachinery, portMachinery, directoryCashRegister,
                                startDateGroupCashRegister, overDepartmentNumberGroupCashRegister, notDetailedGroupCashRegister,
                                disableSalesCashRegister, pieceCodeGroupCashRegister, weightCodeGroupCashRegister));
                    }

                    List<CashRegisterItemInfo> cashRegisterItemInfoList = new ArrayList<>();

                    for (ImMap<Object, Object> row : skuResult.valueIt()) {
                        String barcode = getRowValue(row, "idBarcode");
                        BigDecimal amountBarcode = (BigDecimal) row.get("amountBarcode");
                        String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
                        boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
                        Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
                        Date expiryDate = (Date) row.get("expiryDateMachineryPriceTransactionBarcode");
                        Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
                        boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
                        String idUOM = (String) row.get("idUOMMachineryPriceTransactionBarcode");
                        String shortNameUOM = (String) row.get("shortNameUOMMachineryPriceTransactionBarcode");
                        String idBrand = itemLM == null ? null : (String) row.get("idBrandBarcode");
                        String nameBrand = itemLM == null ? null : (String) row.get("nameBrandBarcode");
                        String idSeason = itemFashionLM == null ? null : (String) row.get("idSeasonBarcode");
                        String nameSeason = itemFashionLM == null ? null : (String) row.get("nameSeasonBarcode");
                        BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
                        String idItem = (String) row.get("idSkuBarcode");
                        Integer itemGroupObject = (Integer) row.get("skuGroupBarcode");
                        Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
                        String description = scalesItemLM == null ? null : (String) row.get("descriptionMachineryPriceTransactionBarcode");

                        String idItemGroup = cashRegisterItemLM == null ? null : (String) row.get("CashRegisterItem.idSkuGroupMachineryPriceTransactionBarcode");
                        String overIdItemGroup = cashRegisterItemLM == null ? null : (String) row.get("CashRegisterItem.overIdSkuGroupMachineryPriceTransactionBarcode");
                        String canonicalNameSkuGroup = (String) row.get("canonicalNameSkuGroupMachineryPriceTransactionBarcode");
                        String section = machineryPriceTransactionSectionLM == null ? null : (String) row.get("sectionMachineryPriceTransactionBarcode");
                        String deleteSection = machineryPriceTransactionSectionLM == null ? null : (String) row.get("deleteSectionBarcode");
                        BigDecimal minPrice = (BigDecimal) row.get("minPriceMachineryPriceTransactionBarcode");

                        cashRegisterItemInfoList.add(new CashRegisterItemInfo(idItem, barcode, name, price, split, daysExpiry, expiryDate, passScales, valueVAT, 
                                pluNumber, flags, idItemGroup, canonicalNameSkuGroup, idUOM, shortNameUOM, itemGroupObject, description, idBrand, nameBrand, idSeason,
                                nameSeason, idDepartmentStoreGroupCashRegister, section, deleteSection, minPrice, overIdItemGroup, amountBarcode));
                    }
                    
                    transactionList.add(new TransactionCashRegisterInfo((Integer) transactionObject.getValue(), dateTimeCode, 
                            date, handlerModelGroupMachinery, (Integer) groupMachineryObject.object, nppGroupMachinery,
                            nameGroupMachinery, descriptionTransaction, itemGroupMap, cashRegisterItemInfoList,
                            cashRegisterInfoList, snapshotTransaction, lastErrorDateTransaction, overDepartmentNumberGroupCashRegister,
                            weightCodeGroupCashRegister, nameStockGroupCashRegister));

                } else if (isScalesPriceTransaction) {
                    List<ScalesInfo> scalesInfoList = new ArrayList<>();
                    String directory = (String) scalesLM.findProperty("directoryGroupScales").read(session, groupMachineryObject);
                    String pieceCodeGroupScales = (String) scalesLM.findProperty("pieceCodeGroupScales").read(session, groupMachineryObject);
                    String weightCodeGroupScales = (String) scalesLM.findProperty("weightCodeGroupScales").read(session, groupMachineryObject);

                    KeyExpr scalesExpr = new KeyExpr("scales");
                    ImRevMap<Object, KeyExpr> scalesKeys = MapFact.singletonRev((Object) "scales", scalesExpr);
                    QueryBuilder<Object, Object> scalesQuery = new QueryBuilder<>(scalesKeys);
                    
                    for (int i = 0; i < machineryProperties.length; i++) {
                        scalesQuery.addProperty(machineryNames[i], machineryProperties[i].getExpr(scalesExpr));
                    }
                    scalesQuery.addProperty("inMachineryPriceTransactionMachinery", 
                            scalesLM.findProperty("inMachineryPriceTransactionMachinery").getExpr(transactionExpr, scalesExpr));
                    scalesQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                                scalesLM.findProperty("succeededMachineryMachineryPriceTransaction").getExpr(scalesExpr, transactionExpr));
                    scalesQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                            scalesLM.findProperty("clearedMachineryMachineryPriceTransaction").getExpr(scalesExpr, transactionExpr));
                    scalesQuery.and(scalesLM.findProperty("groupScalesScales").getExpr(scalesExpr).compare(groupMachineryObject, Compare.EQUALS));
                    scalesQuery.and(scalesLM.findProperty("activeScales").getExpr(scalesExpr).getWhere());

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> scalesResult = scalesQuery.execute(session);

                    for (ImMap<Object, Object> values : scalesResult.valueIt()) {
                        String portMachinery = (String) values.get("portMachinery");
                        Integer nppMachinery = (Integer) values.get("nppMachinery");
                        boolean enabled = values.get("inMachineryPriceTransactionMachinery") != null;
                        boolean succeeded = values.get("succeededMachineryMachineryPriceTransaction") != null;
                        boolean cleared = values.get("clearedMachineryMachineryPriceTransaction") != null;
                        scalesInfoList.add(new ScalesInfo(enabled, cleared, succeeded, nppGroupMachinery, nppMachinery,
                                nameModelGroupMachinery, handlerModelGroupMachinery, portMachinery, directory,
                                pieceCodeGroupScales, weightCodeGroupScales));
                    }

                    List<ScalesItemInfo> scalesItemInfoList = new ArrayList<>();
                    
                    for (ImMap<Object, Object> row : skuResult.valueIt()) {
                        String idItem = getRowValue(row, "idSkuBarcode");
                        String barcode = getRowValue(row, "idBarcode");
                        String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
                        Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
                        Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
                        Date expiryDate = (Date) row.get("expiryDateMachineryPriceTransactionBarcode");
                        boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
                        Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
                        Integer hoursExpiry = (Integer) row.get("hoursExpiryMachineryPriceTransactionBarcode");
                        Integer labelFormat = (Integer) row.get("labelFormatMachineryPriceTransactionBarcode");
                        String description = (String) row.get("descriptionMachineryPriceTransactionBarcode");
                        Integer descriptionNumberCellScales = (Integer) row.get("descriptionNumberMachineryPriceTransactionBarcode");
                        boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
                        BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
                        String idUOM = (String) row.get("idUOMMachineryPriceTransactionBarcode");
                        String shortNameUOM = (String) row.get("shortNameUOMMachineryPriceTransactionBarcode");
                        
                        String idItemGroup = scalesItemLM == null ? null : (String) row.get("ScalesItem.idSkuGroupMachineryPriceTransactionBarcode");
                        String canonicalNameSkuGroup = (String) row.get("canonicalNameSkuGroupMachineryPriceTransactionBarcode");

                        scalesItemInfoList.add(new ScalesItemInfo(idItem, barcode, name, price, split, daysExpiry, expiryDate, 
                                passScales, valueVAT, pluNumber, flags, idItemGroup, canonicalNameSkuGroup, hoursExpiry,
                                labelFormat, description, descriptionNumberCellScales, idUOM, shortNameUOM));
                    }

                    transactionList.add(new TransactionScalesInfo((Integer) transactionObject.getValue(), dateTimeCode, 
                            date, handlerModelGroupMachinery, (Integer) groupMachineryObject.object, nppGroupMachinery,
                            nameGroupMachinery, descriptionTransaction, scalesItemInfoList, scalesInfoList, snapshotTransaction,
                            lastErrorDateTransaction));

                } else if (isPriceCheckerPriceTransaction) {
                    List<PriceCheckerInfo> priceCheckerInfoList = new ArrayList<>();
                    KeyExpr priceCheckerExpr = new KeyExpr("priceChecker");
                    ImRevMap<Object, KeyExpr> priceCheckerKeys = MapFact.singletonRev((Object) "priceChecker", priceCheckerExpr);
                    QueryBuilder<Object, Object> priceCheckerQuery = new QueryBuilder<>(priceCheckerKeys);
                    
                    for (int i = 0; i < machineryProperties.length; i++) {
                        priceCheckerQuery.addProperty(machineryNames[i], machineryProperties[i].getExpr(priceCheckerExpr));
                    }
                    priceCheckerQuery.addProperty("inMachineryPriceTransactionMachinery",
                            priceCheckerLM.findProperty("inMachineryPriceTransactionMachinery").getExpr(transactionExpr, priceCheckerExpr));
                    priceCheckerQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                            priceCheckerLM.findProperty("succeededMachineryMachineryPriceTransaction").getExpr(priceCheckerExpr, transactionExpr));
                    priceCheckerQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                            priceCheckerLM.findProperty("clearedMachineryMachineryPriceTransaction").getExpr(priceCheckerExpr, transactionExpr));
                    priceCheckerQuery.and(priceCheckerLM.findProperty("groupPriceCheckerPriceChecker").getExpr(priceCheckerExpr).compare(groupMachineryObject, Compare.EQUALS));

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> priceCheckerResult = priceCheckerQuery.execute(session);

                    for (ImMap<Object, Object> row : priceCheckerResult.valueIt()) {
                        boolean enabled = row.get("inMachineryPriceTransactionMachinery") != null;
                        Boolean succeeded = row.get("succeededMachineryMachineryPriceTransaction") != null;
                        Boolean cleared = row.get("clearedMachineryMachineryPriceTransaction") != null;
                        priceCheckerInfoList.add(new PriceCheckerInfo(enabled, cleared, succeeded, nppGroupMachinery, (Integer) row.get("nppMachinery"),
                                nameModelGroupMachinery, handlerModelGroupMachinery, (String) row.get("portMachinery")));
                    }

                    List<PriceCheckerItemInfo> priceCheckerItemInfoList = new ArrayList<>();
                    for (ImMap<Object, Object> row : skuResult.valueIt()) {
                        String idItem = getRowValue(row, "idSkuBarcode");
                        String barcode = getRowValue(row, "idBarcode");
                        String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
                        boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
                        Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
                        Date expiryDate = (Date) row.get("expiryDateMachineryPriceTransactionBarcode");
                        boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
                        BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
                        Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
                        Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
                        
                        priceCheckerItemInfoList.add(new PriceCheckerItemInfo(idItem, barcode, name, price, split, 
                                daysExpiry, expiryDate, passScales, valueVAT, pluNumber, flags, null, null));
                    }
                    
                    transactionList.add(new TransactionPriceCheckerInfo((Integer) transactionObject.getValue(), dateTimeCode,
                            date, handlerModelGroupMachinery, (Integer) groupMachineryObject.object, nppGroupMachinery,
                            nameGroupMachinery, descriptionTransaction, priceCheckerItemInfoList, priceCheckerInfoList,
                            snapshotTransaction, lastErrorDateTransaction));


                } else if (isTerminalPriceTransaction) {
                    List<TerminalInfo> terminalInfoList = new ArrayList<>();
                    
                    Integer nppGroupTerminal = (Integer) terminalLM.findProperty("nppGroupMachinery").read(session, groupMachineryObject);
                    String directoryGroupTerminal = (String) terminalLM.findProperty("directoryGroupTerminal").read(session, groupMachineryObject);
                    ObjectValue priceListTypeGroupMachinery = terminalLM.findProperty("priceListTypeGroupMachinery").readClasses(session, groupMachineryObject);
                    ObjectValue stockGroupTerminal = terminalLM.findProperty("stockGroupTerminal").readClasses(session, groupMachineryObject);
                    String idPriceListType = (String) terminalLM.findProperty("idPriceListType").read(session, priceListTypeGroupMachinery);

                    KeyExpr terminalExpr = new KeyExpr("terminal");
                    ImRevMap<Object, KeyExpr> terminalKeys = MapFact.singletonRev((Object) "terminal", terminalExpr);
                    QueryBuilder<Object, Object> terminalQuery = new QueryBuilder<>(terminalKeys);
                    
                    for (int i = 0; i < machineryProperties.length; i++) {
                        terminalQuery.addProperty(machineryNames[i], machineryProperties[i].getExpr(terminalExpr));
                    }
                    terminalQuery.addProperty("inMachineryPriceTransactionMachinery",
                            terminalLM.findProperty("inMachineryPriceTransactionMachinery").getExpr(transactionExpr, terminalExpr));
                    terminalQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                            terminalLM.findProperty("succeededMachineryMachineryPriceTransaction").getExpr(terminalExpr, transactionExpr));
                    terminalQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                            terminalLM.findProperty("clearedMachineryMachineryPriceTransaction").getExpr(terminalExpr, transactionExpr));
                    terminalQuery.and(terminalLM.findProperty("groupTerminalTerminal").getExpr(terminalExpr).compare(groupMachineryObject, Compare.EQUALS));

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> terminalResult = terminalQuery.execute(session);

                    for (ImMap<Object, Object> row : terminalResult.valueIt()) {
                        boolean enabled = row.get("inMachineryPriceTransactionMachinery") != null;
                        Boolean succeeded = row.get("succeededMachineryMachineryPriceTransaction") != null;
                        Boolean cleared = row.get("clearedMachineryMachineryPriceTransaction") != null;
                        terminalInfoList.add(new TerminalInfo(enabled, cleared, succeeded, nppGroupMachinery, (Integer) row.get("nppMachinery"),
                                nameModelGroupMachinery, handlerModelGroupMachinery, getRowValue(row, "portMachinery"),
                                directoryGroupTerminal, idPriceListType));
                    }

                    List<TerminalItemInfo> terminalItemInfoList = new ArrayList<>();
                    for (ImMap<Object, Object> row : skuResult.valueIt()) {
                        String idItem = getRowValue(row, "idSkuBarcode");
                        String barcode = getRowValue(row, "idBarcode");
                        String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
                        boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
                        Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
                        Date expiryDate = (Date) row.get("expiryDateMachineryPriceTransactionBarcode");
                        Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
                        Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
                        boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
                        BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
                        String canonicalNameSkuGroup = (String) row.get("canonicalNameSkuGroupMachineryPriceTransactionBarcode");

                        terminalItemInfoList.add(new TerminalItemInfo(idItem, barcode, name, price, split, daysExpiry, 
                                expiryDate, passScales, valueVAT, pluNumber, flags, null, canonicalNameSkuGroup, null, null));
                    }

                    List<TerminalHandbookType> terminalHandbookTypeList = readTerminalHandbookTypeList(session);
                    List<TerminalDocumentType> terminalDocumentTypeList = readTerminalDocumentTypeList(session);                   
                    List<TerminalLegalEntity> terminalLegalEntityList = readTerminalLegalEntityList(session);
                    List<TerminalAssortment> terminalAssortmentList = readTerminalAssortmentList(session, priceListTypeGroupMachinery, stockGroupTerminal);
                    
                    transactionList.add(new TransactionTerminalInfo((Integer) transactionObject.getValue(), dateTimeCode, 
                            date, handlerModelGroupMachinery, (Integer) groupMachineryObject.object, nppGroupMachinery, nameGroupMachinery,
                            descriptionTransaction, terminalItemInfoList, terminalInfoList, snapshotTransaction, lastErrorDateTransaction,
                            terminalHandbookTypeList, terminalDocumentTypeList, terminalLegalEntityList, terminalAssortmentList,
                            nppGroupTerminal, directoryGroupTerminal));
                }
            }
            return transactionList;
        } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    private Map<String, List<ItemGroup>> readItemGroupMap(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, List<ItemGroup>> result = new HashMap<>();
        Map<String, ItemGroup> itemGroupMap = new HashMap<>();
        
        KeyExpr itemGroupExpr = new KeyExpr("ItemGroup");        
        ImRevMap<Object, KeyExpr> itemGroupKeys = MapFact.singletonRev((Object) "itemGroup", itemGroupExpr);
        QueryBuilder<Object, Object> itemGroupQuery = new QueryBuilder<>(itemGroupKeys);

        String[] itemGroupNames = new String[] {"idItemGroup", "overIdItemGroup", "nameItemGroup", "idParentItemGroup"};
        LCP[] itemGroupProperties = itemLM.findProperties("idItemGroup", "overIdItemGroup", "nameItemGroup", "idParentItemGroup");
        for (int i = 0; i < itemGroupProperties.length; i++) {
            itemGroupQuery.addProperty(itemGroupNames[i], itemGroupProperties[i].getExpr(itemGroupExpr));
        }
        
        itemGroupQuery.and(itemLM.findProperty("idItemGroup").getExpr(itemGroupExpr).getWhere());
        
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> itemGroupResult = itemGroupQuery.execute(session);

        for (ImMap<Object, Object> row : itemGroupResult.valueIt()) {
            String idItemGroup = getRowValue(row, "idItemGroup");
            String overIdItemGroup = getRowValue(row, "overIdItemGroup");
            String nameItemGroup = getRowValue(row, "nameItemGroup");
            String idParentItemGroup = getRowValue(row, "idParentItemGroup");
            itemGroupMap.put(overIdItemGroup, new ItemGroup(idItemGroup, overIdItemGroup, nameItemGroup, idParentItemGroup));
        }
        
        for(Map.Entry<String, ItemGroup> entry : itemGroupMap.entrySet()) {
            List<ItemGroup> hierarchy = new ArrayList<>(Collections.singletonList(entry.getValue()));
            String idParent = entry.getValue().idParentItemGroup;
            while(itemGroupMap.containsKey(idParent)) {
                ItemGroup parentItemGroup = itemGroupMap.get(idParent);
                hierarchy.add(parentItemGroup);
                idParent = parentItemGroup.idParentItemGroup;
            }
            result.put(entry.getKey(), hierarchy);
        }
        return result;
    }

    @Override
    public List<DiscountCard> readDiscountCardList(String idDiscountCardFrom, String idDiscountCardTo) throws RemoteException, SQLException {
        List<DiscountCard> discountCardList = new ArrayList<>();
        if(discountCardLM != null) {
            try (DataSession session = getDbManager().createSession()) {

                KeyExpr discountCardExpr = new KeyExpr("discountCard");
                ImRevMap<Object, KeyExpr> discountCardKeys = MapFact.singletonRev((Object) "discountCard", discountCardExpr);

                QueryBuilder<Object, Object> discountCardQuery = new QueryBuilder<>(discountCardKeys);
                String[] discountCardNames = new String[]{"idDiscountCard", "numberDiscountCard", "nameDiscountCard", 
                        "percentDiscountCard", "dateDiscountCard", "dateToDiscountCard"};
                LCP[] discountCardProperties = discountCardLM.findProperties("idDiscountCard", "numberDiscountCard", "nameDiscountCard",
                        "percentDiscountCard", "dateDiscountCard", "dateToDiscountCard");
                for (int i = 0; i < discountCardProperties.length; i++) {
                    discountCardQuery.addProperty(discountCardNames[i], discountCardProperties[i].getExpr(discountCardExpr));
                }
                discountCardQuery.and(discountCardLM.findProperty("numberDiscountCard").getExpr(discountCardExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> discountCardResult = discountCardQuery.execute(session);

                for (int i = 0, size = discountCardResult.size(); i < size; i++) {
                    ImMap<Object, Object> row = discountCardResult.getValue(i);
                    
                    String idDiscountCard = getRowValue(row, "idDiscountCard");
                    if (discountCardCompare(idDiscountCard, idDiscountCardFrom) >= 0 && discountCardCompare(idDiscountCard, idDiscountCardTo) <= 0) {
                        if (idDiscountCard == null)
                            idDiscountCard = String.valueOf(discountCardResult.getKey(i).get("discountCard"));
                        String numberDiscountCard = getRowValue(row, "numberDiscountCard");
                        String nameDiscountCard = getRowValue(row, "nameDiscountCard");
                        BigDecimal percentDiscountCard = (BigDecimal) row.get("percentDiscountCard");
                        Date dateFromDiscountCard = (Date) row.get("dateDiscountCard");
                        Date dateToDiscountCard = (Date) row.get("dateToDiscountCard");

                        discountCardList.add(new DiscountCard(idDiscountCard, numberDiscountCard, nameDiscountCard, percentDiscountCard, dateFromDiscountCard, dateToDiscountCard));
                    }
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return discountCardList;
    }

    private int discountCardCompare(String d1, String d2) {
        int result = 0;
        if (d1 != null && d2 != null) {
            try {
                result = Integer.parseInt(d1) - Integer.parseInt(d2);
            } catch (Exception e) {
                result = 0;
            }
        }
        return result;
    }

    @Override
    public List<CashierInfo> readCashierInfoList() throws RemoteException, SQLException {
        List<CashierInfo> cashierInfoList = new ArrayList<>();
        try (DataSession session = getDbManager().createSession()) {

            KeyExpr contactExpr = new KeyExpr("contact");
            ImRevMap<Object, KeyExpr> contactKeys = MapFact.singletonRev((Object) "contact", contactExpr);

            QueryBuilder<Object, Object> contactQuery = new QueryBuilder<>(contactKeys);
            String[] contactNames = new String[]{"loginCustomUser", "shortNameContact", "idPositionEmployee"};
            LCP[] contactProperties = equLM.findProperties("loginCustomUser", "shortNameContact", "idPositionEmployee");
            for (int i = 0; i < contactProperties.length; i++) {
                contactQuery.addProperty(contactNames[i], contactProperties[i].getExpr(contactExpr));
            }
            contactQuery.and(equLM.findProperty("loginCustomUser").getExpr(contactExpr).getWhere());
            contactQuery.and(equLM.findProperty("shortNameContact").getExpr(contactExpr).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> contactResult = contactQuery.execute(session);

            for (int i = 0, size = contactResult.size(); i < size; i++) {
                ImMap<Object, Object> row = contactResult.getValue(i);

                String numberCashier = getRowValue(row, "loginCustomUser");
                String nameCashier = getRowValue(row, "shortNameContact");
                String idPosition = getRowValue(row, "idPositionEmployee");
                cashierInfoList.add(new CashierInfo(numberCashier, nameCashier, idPosition));
            }
        } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
        return cashierInfoList;
    }

    @Override
    public boolean enabledStopListInfo() throws RemoteException, SQLException {
        return (cashRegisterLM != null || scalesLM != null)  && stopListLM != null;
    }

    @Override
    public List<StopListInfo> readStopListInfo() throws RemoteException, SQLException {

        List<StopListInfo> stopListInfoList = new ArrayList<>();
        Map<String, StopListInfo> stopListInfoMap = new HashMap<>();
        if(machineryLM != null && stopListLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                Map<String, Map<String, Set<MachineryInfo>>> stockMap = null;
                KeyExpr stopListExpr = new KeyExpr("stopList");
                ImRevMap<Object, KeyExpr> slKeys = MapFact.singletonRev((Object) "stopList", stopListExpr);
                QueryBuilder<Object, Object> slQuery = new QueryBuilder<>(slKeys);
                String[] slNames = new String[]{"excludeStopList", "numberStopList", "fromDateStopList", "fromTimeStopList", 
                        "toDateStopList", "toTimeStopList"};
                LCP<?>[] slProperties = stopListLM.findProperties("excludeStopList", "numberStopList", "fromDateStopList", "fromTimeStopList",
                        "toDateStopList", "toTimeStopList");
                for (int i = 0; i < slProperties.length; i++) {
                    slQuery.addProperty(slNames[i], slProperties[i].getExpr(stopListExpr));
                }
                slQuery.and(stopListLM.findProperty("numberStopList").getExpr(stopListExpr).getWhere());
                slQuery.and(stopListLM.findProperty("isPostedStopList").getExpr(stopListExpr).getWhere());
                slQuery.and(stopListLM.findProperty("toExportStopList").getExpr(stopListExpr).getWhere());
                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> slResult = slQuery.executeClasses(session);
                for (int i = 0, size = slResult.size(); i < size; i++) {
                    DataObject stopListObject = slResult.getKey(i).get("stopList");
                    ImMap<Object, ObjectValue> slEntry = slResult.getValue(i);
                    String numberStopList = trim((String) slEntry.get("numberStopList").getValue());
                    boolean excludeStopList = slEntry.get("excludeStopList").getValue() != null;
                    Date dateFrom = (Date) slEntry.get("fromDateStopList").getValue();
                    Date dateTo = (Date) slEntry.get("toDateStopList").getValue();
                    Time timeFrom = (Time) slEntry.get("fromTimeStopList").getValue();
                    Time timeTo = (Time) slEntry.get("toTimeStopList").getValue();                    
                                                                              
                    Set<String> idStockSet = new HashSet<>();
                    Map<String, Set<MachineryInfo>> handlerMachineryMap = new HashMap<>();
                    Map<Integer, Set<String>> itemsInGroupMachineryMap = new HashMap();
                    KeyExpr stockExpr = new KeyExpr("stock");
                    KeyExpr groupMachineryExpr = new KeyExpr("groupMachinery");
                    ImRevMap<Object, KeyExpr> stockKeys = MapFact.toRevMap((Object) "stock", stockExpr, "groupMachinery", groupMachineryExpr);
                    QueryBuilder<Object, Object> stockQuery = new QueryBuilder<>(stockKeys);
                    stockQuery.addProperty("idStock", stopListLM.findProperty("idStock").getExpr(stockExpr));
                    stockQuery.addProperty("nppGroupMachinery", machineryLM.findProperty("nppGroupMachinery").getExpr(groupMachineryExpr));
                    stockQuery.and(stopListLM.findProperty("inStockStopList").getExpr(stockExpr, stopListObject.getExpr()).getWhere());
                    stockQuery.and(stopListLM.findProperty("notSucceededStockStopList").getExpr(stockExpr, stopListObject.getExpr()).getWhere());
                    stockQuery.and(machineryLM.findProperty("stockGroupMachinery").getExpr(groupMachineryExpr).compare(stockExpr, Compare.EQUALS));
                    stockQuery.and(equipmentLM.findProperty("equipmentServerGroupMachinery").getExpr(groupMachineryExpr).getWhere());
                    stockQuery.and(machineryLM.findProperty("activeGroupMachinery").getExpr(groupMachineryExpr).getWhere());
                    ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> stockResult = stockQuery.executeClasses(session);
                    for (int j = 0; j < stockResult.size(); j++) {
                        ImMap<Object, ObjectValue> stockEntry = stockResult.getValue(j);
                        String idStock = trim((String) stockEntry.get("idStock").getValue());
                        idStockSet.add(idStock);
                        if(stockMap == null)
                            stockMap = getStockMap(session);
                        if(stockMap.containsKey(idStock))
                        for (Map.Entry<String, Set<MachineryInfo>> entry : stockMap.get(idStock).entrySet()) {
                            if (handlerMachineryMap.containsKey(entry.getKey()))
                                handlerMachineryMap.get(entry.getKey()).addAll(entry.getValue());
                            else
                                handlerMachineryMap.put(entry.getKey(), entry.getValue());
                        }
                        Integer groupMachinery = (Integer) stockEntry.get("nppGroupMachinery").getValue();
                        Set<String> itemsInGroupMachinerySet = new HashSet<>();
                        if(!itemsInGroupMachineryMap.containsKey(groupMachinery)) {
                            for (String item : getInGroupMachineryItemSet(session, stopListObject, stockResult.getKey(j).get("groupMachinery")))
                                itemsInGroupMachinerySet.add(item);
                            itemsInGroupMachineryMap.put(groupMachinery, itemsInGroupMachinerySet);
                        }
                    }
                    
                    if(!handlerMachineryMap.isEmpty()) {
                        Map<String, ItemInfo> stopListItemMap = getStopListItemMap(session, stopListObject, idStockSet);
                        StopListInfo stopList = stopListInfoMap.get(numberStopList);
                        Map<Integer, Set<String>> inGroupMachineryItemMap = stopList == null ? new HashMap<Integer, Set<String>>() : stopList.inGroupMachineryItemMap;
                        inGroupMachineryItemMap.putAll(itemsInGroupMachineryMap);
                        stopListInfoMap.put(numberStopList, new StopListInfo(excludeStopList, numberStopList, dateFrom, timeFrom, dateTo, timeTo,
                                idStockSet, inGroupMachineryItemMap, stopListItemMap, handlerMachineryMap));
                    }
                }

                for(StopListInfo stopList : stopListInfoMap.values()) {
                    stopListInfoList.add(stopList);
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
       
        return stopListInfoList;
    }

    private Map<String, Map<String, Set<MachineryInfo>>> getStockMap(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, Map<String, Set<MachineryInfo>>> stockMap = new HashMap<>();

        KeyExpr groupMachineryExpr = new KeyExpr("groupMachinery");
        KeyExpr machineryExpr = new KeyExpr("machinery");
        ImRevMap<Object, KeyExpr> machineryKeys = MapFact.toRevMap((Object) "groupMachinery", groupMachineryExpr, "machinery", machineryExpr);
        QueryBuilder<Object, Object> machineryQuery = new QueryBuilder<>(machineryKeys);

        String[] groupMachineryNames = new String[] {"nppGroupMachinery", "handlerModelGroupMachinery", "idStockGroupMachinery"};
        LCP[] groupMachineryProperties = machineryLM.findProperties("nppGroupMachinery", "handlerModelGroupMachinery", "idStockGroupMachinery");
        for (int i = 0; i < groupMachineryProperties.length; i++) {
            machineryQuery.addProperty(groupMachineryNames[i], groupMachineryProperties[i].getExpr(groupMachineryExpr));
        }
        machineryQuery.addProperty("overDirectoryMachinery", machineryLM.findProperty("overDirectoryMachinery").getExpr(machineryExpr));
        machineryQuery.addProperty("portMachinery", machineryLM.findProperty("portMachinery").getExpr(machineryExpr));
        machineryQuery.addProperty("nppMachinery", machineryLM.findProperty("nppMachinery").getExpr(machineryExpr));

        machineryQuery.and(machineryLM.findProperty("handlerModelGroupMachinery").getExpr(groupMachineryExpr).getWhere());
        machineryQuery.and(machineryLM.findProperty("idStockGroupMachinery").getExpr(groupMachineryExpr).getWhere());
        machineryQuery.and(machineryLM.findProperty("groupMachineryMachinery").getExpr(machineryExpr).compare(groupMachineryExpr, Compare.EQUALS));
        machineryQuery.and(machineryLM.findProperty("activeGroupMachinery").getExpr(groupMachineryExpr).getWhere());
        machineryQuery.and(equipmentLM.findProperty("equipmentServerGroupMachinery").getExpr(groupMachineryExpr).getWhere());
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> machineryResult = machineryQuery.executeClasses(session);
        ValueClass cashRegisterClass = cashRegisterLM == null ? null : cashRegisterLM.findClass("CashRegister");
        ValueClass scalesClass = scalesLM == null ? null : scalesLM.findClass("Scales");
        for (int i = 0; i < machineryResult.size(); i++) {
            ImMap<Object, ObjectValue> values = machineryResult.getValue(i);
            Integer nppGroupMachinery = (Integer) values.get("nppGroupMachinery").getValue();
            String handlerModel = (String) values.get("handlerModelGroupMachinery").getValue();
            String directory = (String) values.get("overDirectoryMachinery").getValue();
            String port = (String) values.get("portMachinery").getValue();
            Integer nppMachinery = (Integer) values.get("nppMachinery").getValue();
            ConcreteClass machineryClass = machineryResult.getKey(i).get("machinery").objectClass;
            boolean isCashRegister = machineryClass != null && machineryClass.equals(cashRegisterClass);
            boolean isScales = machineryClass != null && machineryClass.equals(scalesClass);
            String idStockGroupMachinery = (String) values.get("idStockGroupMachinery").getValue();

            Map<String, Set<MachineryInfo>> handlerMap = stockMap.containsKey(idStockGroupMachinery) ? stockMap.get(idStockGroupMachinery) : new HashMap<String, Set<MachineryInfo>>();
            if(!handlerMap.containsKey(handlerModel))
                handlerMap.put(handlerModel, new HashSet<MachineryInfo>());
            if(isCashRegister) {
                handlerMap.get(handlerModel).add(new CashRegisterInfo(nppGroupMachinery, nppMachinery, handlerModel, port, directory, idStockGroupMachinery));
            } else if(isScales){
                handlerMap.get(handlerModel).add(new ScalesInfo(nppGroupMachinery, nppMachinery, handlerModel, port, directory, idStockGroupMachinery));
            }
            stockMap.put(idStockGroupMachinery, handlerMap);
        }
        return stockMap;
    }
    
    private Map<String, ItemInfo> getStopListItemMap(DataSession session, DataObject stopListObject, Set<String> idStockSet) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, ItemInfo> stopListItemList = new HashMap<>();

        KeyExpr sldExpr = new KeyExpr("stopListDetail");
        ImRevMap<Object, KeyExpr> sldKeys = MapFact.singletonRev((Object) "stopListDetail", sldExpr);
        QueryBuilder<Object, Object> sldQuery = new QueryBuilder<>(sldKeys);
        String[] sldNames = new String[] {"idBarcodeSkuStopListDetail", "idSkuStopListDetail", "nameSkuStopListDetail", "idSkuGroupStopListDetail",
                "nameSkuGroupStopListDetail", "idUOMSkuStopListDetail", "shortNameUOMSkuStopListDetail", "splitSkuStopListDetail", "passScalesSkuStopListDetail"};
        LCP[] sldProperties = stopListLM.findProperties("idBarcodeSkuStopListDetail", "idSkuStopListDetail", "nameSkuStopListDetail", "idSkuGroupStopListDetail",
                "nameSkuGroupStopListDetail", "idUOMSkuStopListDetail", "shortNameUOMSkuStopListDetail", "splitSkuStopListDetail", "passScalesSkuStopListDetail");
        for (int i = 0; i < sldProperties.length; i++) {
            sldQuery.addProperty(sldNames[i], sldProperties[i].getExpr(sldExpr));
        }
        if(scalesItemLM != null) {
            sldQuery.addProperty("skuStopListDetail", stopListLM.findProperty("skuStopListDetail").getExpr(sldExpr));
            sldQuery.and(stopListLM.findProperty("skuStopListDetail").getExpr(sldExpr).getWhere());
        }
        sldQuery.and(stopListLM.findProperty("idBarcodeSkuStopListDetail").getExpr(sldExpr).getWhere());
        sldQuery.and(stopListLM.findProperty("stopListStopListDetail").getExpr(sldExpr).compare(stopListObject, Compare.EQUALS));
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> sldResult = sldQuery.executeClasses(session);
        for (int i = 0; i < sldResult.size(); i++) {
            ImMap<Object, ObjectValue> values = sldResult.getValue(i);
            ObjectValue skuObject = values.get("skuStopListDetail");
            String idBarcode = trim((String) values.get("idBarcodeSkuStopListDetail").getValue());
            String idItem = trim((String) values.get("idSkuStopListDetail").getValue());
            String nameItem = trim((String) values.get("nameSkuStopListDetail").getValue());
            String idSkuGroup = trim((String) values.get("idSkuGroupStopListDetail").getValue());
            String nameSkuGroup = trim((String) values.get("nameSkuGroupStopListDetail").getValue());
            String idUOM = trim((String) values.get("idUOMSkuStopListDetail").getValue());
            String shortNameUOM = trim((String) values.get("shortNameUOMSkuStopListDetail").getValue());
            boolean split = values.get("splitSkuStopListDetail").getValue() != null;
            boolean passScales = values.get("passScalesSkuStopListDetail").getValue() != null;
            Map<String, Integer> stockPluNumberMap = new HashMap();
            for(String idStock : idStockSet) {
                Integer pluNumber = (Integer) scalesItemLM.findProperty("pluIdStockSku").read(session, new DataObject(idStock), skuObject);
                stockPluNumberMap.put(idStock, pluNumber);
            }
            stopListItemList.put(idBarcode, new ItemInfo(stockPluNumberMap, idItem, idBarcode, nameItem, null, split, null, null, passScales,
                    null, null, null, idSkuGroup, nameSkuGroup, idUOM, shortNameUOM));
        }
        return stopListItemList;
    }

    private Set<String> getInGroupMachineryItemSet(DataSession session, DataObject stopListObject, DataObject groupMachineryObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Set<String> inGroupMachineryItemSet = new HashSet<>();

        KeyExpr sldExpr = new KeyExpr("stopListDetail");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "stopListDetail", sldExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("idSkuStopListDetail", stopListLM.findProperty("idSkuStopListDetail").getExpr(sldExpr));
        query.and(stopListLM.findProperty("inGroupMachineryStopListDetail").getExpr(groupMachineryObject.getExpr(), sldExpr).getWhere());
        query.and(stopListLM.findProperty("stopListStopListDetail").getExpr(sldExpr).compare(stopListObject, Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
        for (int i = 0; i < result.size(); i++) {
            String idItem = trim((String) result.getValue(i).get("idSkuStopListDetail"));
            inGroupMachineryItemSet.add(idItem);
        }
        return inGroupMachineryItemSet;
    }

    @Override
    public void errorStopListReport(String numberStopList, Exception e) throws RemoteException, SQLException {
        try (DataSession session = getDbManager().createSession()) {
            DataObject errorObject = session.addObject((ConcreteCustomClass) stopListLM.findClass("StopListError"));
            ObjectValue stopListObject = stopListLM.findProperty("stopListNumber").readClasses(session, new DataObject(numberStopList));
            stopListLM.findProperty("stopListStopListError").change(stopListObject.getValue(), session, errorObject);
            stopListLM.findProperty("dataStopListError").change(e.toString(), session, errorObject);
            stopListLM.findProperty("dateStopListError").change(getCurrentTimestamp(), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(os));
            stopListLM.findProperty("errorTraceStopListError").change(os.toString(), session, errorObject);

            session.apply(getBusinessLogics());
        } catch (Exception e2) {
            throw Throwables.propagate(e2);
        }
    }

    @Override
    public void succeedStopList(String numberStopList, Set<String> idStockSet) throws RemoteException, SQLException {
        try (DataSession session = getDbManager().createSession()) {
            DataObject stopListObject = (DataObject) stopListLM.findProperty("stopListNumber").readClasses(session, new DataObject(numberStopList));
            for(String idStock : idStockSet) {
                DataObject stockObject = (DataObject) stopListLM.findProperty("stockId").readClasses(session, new DataObject(idStock));
                stopListLM.findProperty("succeededStockStopList").change(true, session, stockObject, stopListObject);
            }
            session.apply(getBusinessLogics());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }


    private List<TerminalHandbookType> readTerminalHandbookTypeList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalHandbookType> terminalHandbookTypeList = new ArrayList<>();
        KeyExpr terminalHandbookTypeExpr = new KeyExpr("terminalHandbookType");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "terminalHandbookType", terminalHandbookTypeExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        String[] names = new String[]{"idTerminalHandbookType", "nameTerminalHandbookType"};
        LCP<?>[] properties = terminalLM.findProperties("idTerminalHandbookType", "nameTerminalHandbookType");
        for (int i = 0, propertiesLength = properties.length; i < propertiesLength; i++) {
            query.addProperty(names[i], properties[i].getExpr(terminalHandbookTypeExpr));
        }
        query.and(terminalLM.findProperty("idTerminalHandbookType").getExpr(terminalHandbookTypeExpr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
        for(ImMap<Object, Object> entry : result.values()) {
            String id = trim((String) entry.get("idTerminalHandbookType"));
            String name = trim((String) entry.get("nameTerminalHandbookType"));
            terminalHandbookTypeList.add(new TerminalHandbookType(id, name));
        }
        return terminalHandbookTypeList;
    }
    
    private List<TerminalDocumentType> readTerminalDocumentTypeList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalDocumentType> terminalDocumentTypeList = new ArrayList<>();
        KeyExpr terminalDocumentTypeExpr = new KeyExpr("terminalDocumentType");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "terminalDocumentType", terminalDocumentTypeExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        String[] names = new String[]{"idTerminalDocumentType", "nameTerminalDocumentType", "flagTerminalDocumentType",
                "idTerminalHandbookType1TerminalDocumentType", "idTerminalHandbookType2TerminalDocumentType"};
        LCP<?>[] properties = terminalLM.findProperties("idTerminalDocumentType", "nameTerminalDocumentType", "flagTerminalDocumentType",
                "idTerminalHandbookType1TerminalDocumentType", "idTerminalHandbookType2TerminalDocumentType");
        for (int i = 0; i < properties.length; i++) {
            query.addProperty(names[i], properties[i].getExpr(terminalDocumentTypeExpr));
        }
        query.and(terminalLM.findProperty("idTerminalDocumentType").getExpr(terminalDocumentTypeExpr).getWhere());
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

    @Override
    public List<TerminalOrder> readTerminalOrderList(RequestExchange requestExchange) throws RemoteException, SQLException {
        List<TerminalOrder> terminalOrderList = new ArrayList<>();
        if (purchaseInvoiceAgreementLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                KeyExpr orderExpr = new KeyExpr("order");
                KeyExpr orderDetailExpr = new KeyExpr("orderDetail");
                ImRevMap<Object, KeyExpr> orderKeys = MapFact.toRevMap((Object) "Order", orderExpr, "OrderDetail", orderDetailExpr);
                QueryBuilder<Object, Object> orderQuery = new QueryBuilder<>(orderKeys);
                String[] orderNames = new String[]{"dateOrder", "numberOrder", "idSupplierOrder"};
                LCP<?>[] orderProperties = purchaseInvoiceAgreementLM.findProperties("Purchase.dateOrder", "Purchase.numberOrder", "Purchase.idSupplierOrder");
                for (int i = 0; i < orderProperties.length; i++) {
                    orderQuery.addProperty(orderNames[i], orderProperties[i].getExpr(orderExpr));
                }
                String[] orderDetailNames = new String[]{"idBarcodeSkuOrderDetail", "nameSkuOrderDetail", "priceOrderDetail",
                        "quantityOrderDetail", "minDeviationQuantityOrderDetail", "maxDeviationQuantityOrderDetail",
                        "minDeviationPriceOrderDetail", "maxDeviationPriceOrderDetail"};
                LCP<?>[] orderDetailProperties = purchaseInvoiceAgreementLM.findProperties("Purchase.idBarcodeSkuOrderDetail", "Purchase.nameSkuOrderDetail", "Purchase.priceOrderDetail",
                        "Purchase.quantityOrderDetail", "minDeviationQuantityOrderDetail", "maxDeviationQuantityOrderDetail",
                        "minDeviationPriceOrderDetail", "maxDeviationPriceOrderDetail");
                for (int i = 0; i < orderDetailProperties.length; i++) {
                    orderQuery.addProperty(orderDetailNames[i], orderDetailProperties[i].getExpr(orderDetailExpr));
                }
                if (requestExchange.dateFrom != null)
                    orderQuery.and(purchaseInvoiceAgreementLM.findProperty("Purchase.dateOrder").getExpr(orderExpr).compare(
                            new DataObject(requestExchange.dateFrom, DateClass.instance).getExpr(), Compare.GREATER_EQUALS));
                if (requestExchange.dateTo != null)
                    orderQuery.and(purchaseInvoiceAgreementLM.findProperty("Purchase.dateOrder").getExpr(orderExpr).compare(
                            new DataObject(requestExchange.dateTo, DateClass.instance).getExpr(), Compare.LESS_EQUALS));
                if (requestExchange.idStock != null)
                    orderQuery.and(purchaseInvoiceAgreementLM.findProperty("Purchase.customerStockOrder").getExpr(orderExpr).compare(
                            purchaseInvoiceAgreementLM.findProperty("stockId").readClasses(session, new DataObject(requestExchange.idStock)).getExpr(), Compare.EQUALS));
                orderQuery.and(purchaseInvoiceAgreementLM.findProperty("Purchase.orderOrderDetail").getExpr(orderDetailExpr).compare(orderExpr, Compare.EQUALS));
                orderQuery.and(purchaseInvoiceAgreementLM.findProperty("Purchase.numberOrder").getExpr(orderExpr).getWhere());
                orderQuery.and(purchaseInvoiceAgreementLM.findProperty("Purchase.isOpenedOrder").getExpr(orderExpr).getWhere());
                orderQuery.and(purchaseInvoiceAgreementLM.findProperty("Purchase.idBarcodeSkuOrderDetail").getExpr(orderDetailExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> orderResult = orderQuery.execute(session);
                for (ImMap<Object, Object> entry : orderResult.values()) {
                    Date dateOrder = (Date) entry.get("dateOrder");
                    String numberOrder = trim((String) entry.get("numberOrder"));
                    String idSupplier = trim((String) entry.get("idSupplierOrder"));
                    String barcode = trim((String) entry.get("idBarcodeSkuOrderDetail"));
                    String name = trim((String) entry.get("nameSkuOrderDetail"));
                    BigDecimal price = (BigDecimal) entry.get("priceOrderDetail");
                    BigDecimal quantity = (BigDecimal) entry.get("quantityOrderDetail");
                    BigDecimal minQuantity = (BigDecimal) entry.get("minDeviationQuantityOrderDetail");
                    BigDecimal maxQuantity = (BigDecimal) entry.get("maxDeviationQuantityOrderDetail");
                    BigDecimal minPrice = (BigDecimal) entry.get("minDeviationPriceOrderDetail");
                    BigDecimal maxPrice = (BigDecimal) entry.get("maxDeviationPriceOrderDetail");
                    terminalOrderList.add(new TerminalOrder(dateOrder, numberOrder, idSupplier, barcode, name, price,
                            quantity, minQuantity, maxQuantity, minPrice, maxPrice));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return terminalOrderList;
    }

    private List<TerminalLegalEntity> readTerminalLegalEntityList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalLegalEntity> terminalLegalEntityList = new ArrayList<>();
        if (legalEntityLM != null) {
            KeyExpr legalEntityExpr = new KeyExpr("legalEntity");
            ImRevMap<Object, KeyExpr> legalEntityKeys = MapFact.singletonRev((Object) "LegalEntity", legalEntityExpr);
            QueryBuilder<Object, Object> legalEntityQuery = new QueryBuilder<>(legalEntityKeys);
            String[] legalEntityNames = new String[]{"idLegalEntity", "nameLegalEntity"};
            LCP<?>[] legalEntityProperties = legalEntityLM.findProperties("idLegalEntity", "nameLegalEntity");
            for (int i = 0; i < legalEntityProperties.length; i++) {
                legalEntityQuery.addProperty(legalEntityNames[i], legalEntityProperties[i].getExpr(legalEntityExpr));
            }
            legalEntityQuery.and(legalEntityLM.findProperty("idLegalEntity").getExpr(legalEntityExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> legalEntityResult = legalEntityQuery.execute(session);
            for (ImMap<Object, Object> entry : legalEntityResult.values()) {
                String idLegalEntity = trim((String) entry.get("idLegalEntity"));
                String nameLegalEntity = trim((String) entry.get("nameLegalEntity"));
                terminalLegalEntityList.add(new TerminalLegalEntity(idLegalEntity, nameLegalEntity));
            }
        }
        return terminalLegalEntityList;
    }

    private List<TerminalAssortment> readTerminalAssortmentList(DataSession session, ObjectValue priceListTypeObject, ObjectValue stockGroupMachineryObject)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalAssortment> terminalAssortmentList = new ArrayList<>();
        if (legalEntityLM != null && priceListLedgerLM != null && itemLM != null) {
            
            DataObject currentDateTimeObject = new DataObject(new Timestamp(Calendar.getInstance().getTime().getTime()), DateTimeClass.instance);
            
            KeyExpr skuExpr = new KeyExpr("Sku");
            KeyExpr legalEntityExpr = new KeyExpr("legalEntity");
            ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "Sku", skuExpr, "LegalEntity", legalEntityExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            query.addProperty("priceALedgerPriceListTypeSkuStockCompanyDateTime", priceListLedgerLM.findProperty("priceALedgerPriceListTypeSkuStockCompanyDateTime").getExpr(priceListTypeObject.getExpr(), 
                    skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr, currentDateTimeObject.getExpr()));
            query.addProperty("idBarcodeSku", itemLM.findProperty("idBarcodeSku").getExpr(skuExpr));
            query.addProperty("idLegalEntity", legalEntityLM.findProperty("idLegalEntity").getExpr(legalEntityExpr));
            query.and(legalEntityLM.findProperty("idLegalEntity").getExpr(legalEntityExpr).getWhere());
            query.and(itemLM.findProperty("idBarcodeSku").getExpr(skuExpr).getWhere());
            query.and(priceListLedgerLM.findProperty("priceALedgerPriceListTypeSkuStockCompanyDateTime").getExpr(priceListTypeObject.getExpr(), 
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

    @Override
    public List<RequestExchange> readRequestExchange(String sidEquipmentServer) throws RemoteException, SQLException {

        Map<String, RequestExchange> requestExchangeMap = new HashMap<>();
        Map<DataObject, List<Set<String>>> extraStockSetMap = new HashMap<>();
        if(machineryLM != null && machineryPriceTransactionLM != null) {

            try (DataSession session = getDbManager().createSession()) {

                KeyExpr requestExchangeExpr = new KeyExpr("requestExchange");
                KeyExpr machineryExpr = new KeyExpr("machinery");
                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "requestExchange", requestExchangeExpr, "machinery", machineryExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

                String[] names = new String[]{"dateFromRequestExchange", "dateToRequestExchange", "startDateRequestExchange",
                        "nameRequestExchangeTypeRequestExchange", "idDiscountCardFromRequestExchange", "idDiscountCardToRequestExchange"};
                LCP[] properties = machineryPriceTransactionLM.findProperties("dateFromRequestExchange", "dateToRequestExchange", "startDateRequestExchange",
                        "nameRequestExchangeTypeRequestExchange", "idDiscountCardFromRequestExchange", "idDiscountCardToRequestExchange");
                for (int i = 0; i < properties.length; i++) {
                    query.addProperty(names[i], properties[i].getExpr(requestExchangeExpr));
                }
                query.addProperty("overDirectoryMachinery", machineryLM.findProperty("overDirectoryMachinery").getExpr(machineryExpr));
                query.addProperty("idStockMachinery", machineryLM.findProperty("idStockMachinery").getExpr(machineryExpr));
                query.addProperty("nppMachinery", machineryLM.findProperty("nppMachinery").getExpr(machineryExpr));
                query.and(machineryPriceTransactionLM.findProperty("notSucceededRequestExchange").getExpr(requestExchangeExpr).getWhere());
                query.and(machineryPriceTransactionLM.findProperty("inMachineryRequestExchange").getExpr(machineryExpr, requestExchangeExpr).getWhere());
                query.and(machineryLM.findProperty("stockMachinery").getExpr(machineryExpr).compare(
                        machineryPriceTransactionLM.findProperty("stockRequestExchange").getExpr(requestExchangeExpr), Compare.EQUALS));
                query.and(machineryLM.findProperty("inactiveMachinery").getExpr(machineryExpr).getWhere().not());
                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(session);
                for (int i = 0, size = result.size(); i < size; i++) {

                    DataObject requestExchangeObject = result.getKey(i).get("requestExchange");
                    String directoryMachinery = trim((String) result.getValue(i).get("overDirectoryMachinery").getValue());
                    String idStockMachinery = trim((String) result.getValue(i).get("idStockMachinery").getValue());
                    Integer nppMachinery = (Integer) result.getValue(i).get("nppMachinery").getValue();
                    Date dateFromRequestExchange = (Date) result.getValue(i).get("dateFromRequestExchange").getValue();
                    Date dateToRequestExchange = (Date) result.getValue(i).get("dateToRequestExchange").getValue();
                    Date startDateRequestExchange = (Date) result.getValue(i).get("startDateRequestExchange").getValue();
                    String idDiscountCardFrom = trim((String) result.getValue(i).get("idDiscountCardFromRequestExchange").getValue());
                    String idDiscountCardTo = trim((String) result.getValue(i).get("idDiscountCardToRequestExchange").getValue());
                    String typeRequestExchange = trim((String) result.getValue(i).get("nameRequestExchangeTypeRequestExchange").getValue());

                    Set<String> directorySet = new HashSet<>(Collections.singletonList(directoryMachinery));
                    List<Set<String>> extraStockSet;
                    if (extraStockSetMap.containsKey(requestExchangeObject)) {
                        extraStockSet = extraStockSetMap.get(requestExchangeObject);
                    } else {
                        extraStockSet = readExtraStockRequestExchange(session, requestExchangeObject);
                        extraStockSetMap.put(requestExchangeObject, extraStockSet);
                    }
                    directorySet.addAll(extraStockSet.get(1));

                    String requestExchangeKey = requestExchangeObject.getValue() + directoryMachinery + idStockMachinery;
                    RequestExchange requestExchange = requestExchangeMap.get(requestExchangeKey);
                    if (requestExchange == null) {
                        requestExchangeMap.put(requestExchangeKey, new RequestExchange((Integer) requestExchangeObject.getValue(),
                                new HashSet<>(Collections.singletonList(nppMachinery)), directorySet, idStockMachinery,
                                extraStockSet.get(0), dateFromRequestExchange, dateToRequestExchange,
                                startDateRequestExchange, idDiscountCardFrom, idDiscountCardTo, typeRequestExchange));
                    } else {
                        requestExchange.cashRegisterSet.add(nppMachinery);
                    }
                }
                session.apply(getBusinessLogics());
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return new ArrayList<>(requestExchangeMap.values());
    }
        
    private List<Set<String>> readExtraStockRequestExchange(DataSession session, DataObject requestExchangeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Set<String> idStockSet = new HashSet<>();
        Set<String> directorySet = new HashSet<>();
        KeyExpr stockExpr = new KeyExpr("stock");
        KeyExpr machineryExpr = new KeyExpr("machinery");
        ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "stock", stockExpr, "machinery", machineryExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

        query.addProperty("idStock", machineryPriceTransactionLM.findProperty("idStock").getExpr(stockExpr));
        query.addProperty("overDirectoryMachinery", machineryPriceTransactionLM.findProperty("overDirectoryMachinery").getExpr(machineryExpr));
        query.and(machineryPriceTransactionLM.findProperty("inStockRequestExchange").getExpr(stockExpr, requestExchangeObject.getExpr()).getWhere());
        query.and(machineryPriceTransactionLM.findProperty("overDirectoryMachinery").getExpr(machineryExpr).getWhere());
        query.and(machineryLM.findProperty("stockMachinery").getExpr(machineryExpr).compare(stockExpr, Compare.EQUALS));
        query.and(machineryLM.findProperty("inactiveMachinery").getExpr(machineryExpr).getWhere().not());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
        for (ImMap<Object, Object> entry : result.values()) {
            idStockSet.add(trim((String) entry.get("idStock")));
            directorySet.add(trim((String) entry.get("overDirectoryMachinery")));
        }
        return Arrays.asList(idStockSet, directorySet);
    }

    @Override
    public void finishRequestExchange(Set<Integer> succeededRequestsSet) throws RemoteException, SQLException {
        if (machineryPriceTransactionLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                for (Integer request : succeededRequestsSet) {
                    DataObject requestExchangeObject = new DataObject(request, (ConcreteClass) machineryPriceTransactionLM.findClass("RequestExchange"));
                    machineryPriceTransactionLM.findProperty("succeededRequestExchange").change(true, session, requestExchangeObject);
                    machineryPriceTransactionLM.findProperty("dateTimeSucceededRequestExchange").change(getCurrentTimestamp(), session, requestExchangeObject);
                }
                session.apply(getBusinessLogics());
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void errorRequestExchange(Map<Integer, String> succeededRequestsMap) throws RemoteException, SQLException {
        if (machineryPriceTransactionLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                for (Map.Entry<Integer, String> request : succeededRequestsMap.entrySet()) {
                    DataObject errorObject = session.addObject((ConcreteCustomClass) machineryPriceTransactionLM.findClass("RequestExchangeError"));
                    machineryPriceTransactionLM.findProperty("messageRequestExchangeError").change(request.getValue(), session, errorObject);
                    machineryPriceTransactionLM.findProperty("dateRequestExchangeError").change(getCurrentTimestamp(), session, errorObject);
                    machineryPriceTransactionLM.findProperty("requestExchangeRequestExchangeError").change(request.getKey(), session, errorObject);
                }
                session.apply(getBusinessLogics());
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public Map<String, BigDecimal> readZReportSumMap() throws RemoteException, SQLException {
        Map<String, BigDecimal> zReportSumMap = new HashMap<>();
        if (zReportLM != null) {
            try (DataSession session = getDbManager().createSession()) {

                KeyExpr zReportExpr = new KeyExpr("zReport");

                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "ZReport", zReportExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

                query.addProperty("idZReport", zReportLM.findProperty("idZReport").getExpr(zReportExpr));
                query.addProperty("sumReceiptDetailZReport", zReportLM.findProperty("sumReceiptDetailZReport").getExpr(zReportExpr));

                query.and(zReportLM.findProperty("idZReport").getExpr(zReportExpr).getWhere());
                query.and(zReportLM.findProperty("succeededExtraCheckZReport").getExpr(zReportExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    zReportSumMap.put((String) row.get("idZReport"), (BigDecimal) row.get("sumReceiptDetailZReport"));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return zReportSumMap;
    }
    
    @Override
    public void succeedExtraCheckZReport(List<String> idZReportList) throws RemoteException, SQLException {
        try {
            if (zReportLM != null) {
                for (String idZReport : idZReportList) {
                    try (DataSession session = getDbManager().createSession()) {
                        zReportLM.findProperty("succeededExtraCheckZReport").change(true, session, (DataObject) zReportLM.findProperty("zReportId").readClasses(session, new DataObject(idZReport)));
                        session.apply(getBusinessLogics());
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public List<CashRegisterInfo> readCashRegisterInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        List<CashRegisterInfo> cashRegisterInfoList = new ArrayList<>();
        if (cashRegisterLM != null) {
            try (DataSession session = getDbManager().createSession()) {

                KeyExpr groupCashRegisterExpr = new KeyExpr("groupCashRegister");
                KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");

                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "groupCashRegister", groupCashRegisterExpr, "cashRegister", cashRegisterExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

                String[] cashRegisterNames = new String[]{"nppMachinery", "portMachinery", "overDirectoryMachinery", "disableSalesCashRegister"};
                LCP[] cashRegisterProperties = cashRegisterLM.findProperties("nppMachinery", "portMachinery", "overDirectoryMachinery", "disableSalesCashRegister");
                for (int i = 0; i < cashRegisterProperties.length; i++) {
                    query.addProperty(cashRegisterNames[i], cashRegisterProperties[i].getExpr(cashRegisterExpr));
                }

                String[] groupCashRegisterNames = new String[]{"nppGroupMachinery", "handlerModelGroupMachinery", "nameModelGroupMachinery",
                        "overDepartmentNumberGroupCashRegister", "pieceCodeGroupCashRegister", "weightCodeGroupCashRegister"};
                LCP[] groupCashRegisterProperties = cashRegisterLM.findProperties("nppGroupMachinery", "handlerModelGroupMachinery", "nameModelGroupMachinery",
                        "overDepartmentNumberGroupCashRegister", "pieceCodeGroupCashRegister", "weightCodeGroupCashRegister");
                for (int i = 0; i < groupCashRegisterProperties.length; i++) {
                    query.addProperty(groupCashRegisterNames[i], groupCashRegisterProperties[i].getExpr(groupCashRegisterExpr));
                }

                query.and(cashRegisterLM.findProperty("handlerModelGroupMachinery").getExpr(groupCashRegisterExpr).getWhere());
                //query.and(cashRegisterLM.findProperty("overDirectoryMachinery").getExpr(cashRegisterExpr).getWhere());
                query.and(cashRegisterLM.findProperty("groupMachineryMachinery").getExpr(cashRegisterExpr).compare(groupCashRegisterExpr, Compare.EQUALS));
                query.and(equLM.findProperty("sidEquipmentServerGroupMachinery").getExpr(groupCashRegisterExpr).compare(new DataObject(sidEquipmentServer), Compare.EQUALS));
                query.and(cashRegisterLM.findProperty("activeGroupCashRegister").getExpr(groupCashRegisterExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    cashRegisterInfoList.add(new CashRegisterInfo((Integer) row.get("nppGroupMachinery"), (Integer) row.get("nppMachinery"),
                            (String) row.get("nameModelGroupMachinery"), (String) row.get("handlerModelGroupMachinery"), (String) row.get("portMachinery"),
                            (String) row.get("overDirectoryMachinery"), (Integer) row.get("overDepartmentNumberGroupCashRegister"),
                            row.get("disableSalesCashRegister") != null, (String) row.get("pieceCodeGroupCashRegister"),
                            (String) row.get("weightCodeGroupCashRegister")));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return cashRegisterInfoList;
    }

    @Override
    public boolean enabledTerminalInfo() throws RemoteException, SQLException {
        return terminalLM != null;
    }

    @Override
    public List<TerminalInfo> readTerminalInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        List<TerminalInfo> terminalInfoList;
        terminalInfoList = new ArrayList<>();
        if (terminalLM != null) {
            try (DataSession session = getDbManager().createSession()) {

                KeyExpr groupTerminalExpr = new KeyExpr("groupTerminal");
                KeyExpr terminalExpr = new KeyExpr("terminal");

                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "GroupTerminal", groupTerminalExpr, "terminal", terminalExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

                String[] terminalNames = new String[]{"nppMachinery", "portMachinery"};
                LCP[] terminalProperties = machineryLM.findProperties("nppMachinery", "portMachinery");
                for (int i = 0; i < terminalProperties.length; i++) {
                    query.addProperty(terminalNames[i], terminalProperties[i].getExpr(terminalExpr));
                }

                String[] groupTerminalNames = new String[]{"nameModelGroupMachinery", "handlerModelGroupMachinery",
                        "directoryGroupTerminal", "idPriceListTypeGroupMachinery", "nppGroupMachinery"};
                LCP[] groupTerminalProperties = terminalLM.findProperties("nameModelGroupMachinery", "handlerModelGroupMachinery",
                        "directoryGroupTerminal", "idPriceListTypeGroupMachinery", "nppGroupMachinery");
                for (int i = 0; i < groupTerminalProperties.length; i++) {
                    query.addProperty(groupTerminalNames[i], groupTerminalProperties[i].getExpr(groupTerminalExpr));
                }

                query.and(terminalLM.findProperty("handlerModelGroupMachinery").getExpr(groupTerminalExpr).getWhere());
                query.and(terminalLM.findProperty("directoryGroupTerminal").getExpr(groupTerminalExpr).getWhere());
                query.and(terminalLM.findProperty("groupTerminalTerminal").getExpr(terminalExpr).compare(groupTerminalExpr, Compare.EQUALS));
                query.and(terminalLM.findProperty("activeGroupTerminal").getExpr(groupTerminalExpr).getWhere());
                query.and(equLM.findProperty("sidEquipmentServerGroupMachinery").getExpr(groupTerminalExpr).compare(new DataObject(sidEquipmentServer), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    terminalInfoList.add(new TerminalInfo(true, false, false, (Integer) row.get("nppGroupMachinery"), (Integer) row.get("nppMachinery"),
                            (String) row.get("nameModelGroupMachinery"), (String) row.get("handlerModelGroupMachinery"), (String) row.get("portMachinery"),
                            (String) row.get("directoryGroupTerminal"), (String) row.get("idPriceListTypeGroupMachinery")));
                }
            } catch (ScriptingErrorLog.SemanticErrorException e) {
                throw new RuntimeException(e.toString());
            } catch (SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return terminalInfoList;
    }

    @Override
    public List<MachineryInfo> readMachineryInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        List<MachineryInfo> machineryInfoList = new ArrayList<>();
        if (machineryLM != null) {
            try (DataSession session = getDbManager().createSession()) {

                KeyExpr groupMachineryExpr = new KeyExpr("groupMachinery");
                KeyExpr machineryExpr = new KeyExpr("machinery");

                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "groupMachinery", groupMachineryExpr, "machinery", machineryExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

                String[] machineryNames = new String[]{"nppMachinery", "portMachinery", "overDirectoryMachinery"};
                LCP[] machineryProperties = machineryLM.findProperties("nppMachinery", "portMachinery", "overDirectoryMachinery");
                for (int i = 0; i < machineryProperties.length; i++) {
                    query.addProperty(machineryNames[i], machineryProperties[i].getExpr(machineryExpr));
                }

                String[] groupMachineryNames = new String[]{"nppGroupMachinery", "handlerModelGroupMachinery", "nameModelGroupMachinery"};
                LCP[] groupMachineryProperties = machineryLM.findProperties("nppGroupMachinery", "handlerModelGroupMachinery", "nameModelGroupMachinery");
                for (int i = 0; i < groupMachineryProperties.length; i++) {
                    query.addProperty(groupMachineryNames[i], groupMachineryProperties[i].getExpr(groupMachineryExpr));
                }

                query.and(machineryLM.findProperty("handlerModelGroupMachinery").getExpr(groupMachineryExpr).getWhere());
                query.and(machineryLM.findProperty("overDirectoryMachinery").getExpr(machineryExpr).getWhere());
                query.and(machineryLM.findProperty("groupMachineryMachinery").getExpr(machineryExpr).compare(groupMachineryExpr, Compare.EQUALS));
                query.and(equLM.findProperty("sidEquipmentServerGroupMachinery").getExpr(groupMachineryExpr).compare(new DataObject(sidEquipmentServer), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    machineryInfoList.add(new MachineryInfo(true, false, false, (Integer) row.get("nppGroupMachinery"), (Integer) row.get("nppMachinery"),
                            (String) row.get("nameModelGroupMachinery"), (String) row.get("handlerModelGroupMachinery"), (String) row.get("portMachinery"),
                            (String) row.get("overDirectoryMachinery")));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return machineryInfoList;
    }

    @Override
    public String sendTerminalInfo(List<TerminalDocumentDetail> terminalDocumentDetailList, String sidEquipmentServer) throws RemoteException, SQLException {

        try {

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(terminalDocumentDetailList.size());

            if (notNullNorEmpty(terminalDocumentDetailList)) {

                ImportField idTerminalDocumentField = new ImportField(terminalLM.findProperty("idTerminalDocument"));
                ImportKey<?> terminalDocumentKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("TerminalDocument"),
                        terminalLM.findProperty("terminalDocumentId").getMapping(idTerminalDocumentField));
                keys.add(terminalDocumentKey);
                props.add(new ImportProperty(idTerminalDocumentField, terminalLM.findProperty("idTerminalDocument").getMapping(terminalDocumentKey)));
                fields.add(idTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idDocument);

                ImportField titleTerminalDocumentField = new ImportField(terminalLM.findProperty("titleTerminalDocument"));
                props.add(new ImportProperty(titleTerminalDocumentField, terminalLM.findProperty("titleTerminalDocument").getMapping(terminalDocumentKey)));
                fields.add(titleTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).numberDocument);

                ImportField dateTerminalDocumentField = new ImportField(terminalLM.findProperty("dateTerminalDocument"));
                props.add(new ImportProperty(dateTerminalDocumentField, terminalLM.findProperty("dateTerminalDocument").getMapping(terminalDocumentKey)));
                fields.add(dateTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).dateDocument);

                ImportField timeTerminalDocumentField = new ImportField(terminalLM.findProperty("timeTerminalDocument"));
                props.add(new ImportProperty(timeTerminalDocumentField, terminalLM.findProperty("timeTerminalDocument").getMapping(terminalDocumentKey)));
                fields.add(timeTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).timeDocument);

                ImportField commentTerminalDocumentField = new ImportField(terminalLM.findProperty("commentTerminalDocument"));
                props.add(new ImportProperty(commentTerminalDocumentField, terminalLM.findProperty("commentTerminalDocument").getMapping(terminalDocumentKey)));
                fields.add(commentTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).commentDocument);

                ImportField directoryGroupTerminalField = new ImportField(terminalLM.findProperty("directoryGroupTerminal"));
                ImportKey<?> groupTerminalKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("GroupTerminal"),
                        terminalLM.findProperty("groupTerminalDirectory").getMapping(directoryGroupTerminalField));
                keys.add(groupTerminalKey);
                props.add(new ImportProperty(directoryGroupTerminalField, terminalLM.findProperty("groupTerminalTerminalDocument").getMapping(terminalDocumentKey),
                        terminalLM.object(terminalLM.findClass("GroupTerminal")).getMapping(groupTerminalKey)));
                fields.add(directoryGroupTerminalField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).directoryGroupTerminal);

                ImportField idTerminalHandbookType1TerminalDocumentField = new ImportField(terminalLM.findProperty("idTerminalHandbookType1TerminalDocument"));
                props.add(new ImportProperty(idTerminalHandbookType1TerminalDocumentField, terminalLM.findProperty("idTerminalHandbookType1TerminalDocument").getMapping(terminalDocumentKey)));
                fields.add(idTerminalHandbookType1TerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idTerminalHandbookType1);

                ImportField idTerminalHandbookType2TerminalDocumentField = new ImportField(terminalLM.findProperty("idTerminalHandbookType2TerminalDocument"));
                props.add(new ImportProperty(idTerminalHandbookType2TerminalDocumentField, terminalLM.findProperty("idTerminalHandbookType2TerminalDocument").getMapping(terminalDocumentKey)));
                fields.add(idTerminalHandbookType2TerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idTerminalHandbookType2);

                ImportField idTerminalDocumentTypeField = new ImportField(terminalLM.findProperty("idTerminalDocumentType"));
                ImportKey<?> terminalDocumentTypeKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("TerminalDocumentType"),
                        terminalLM.findProperty("terminalDocumentTypeId").getMapping(idTerminalDocumentTypeField));
                terminalDocumentTypeKey.skipKey = true;
                keys.add(terminalDocumentTypeKey);
                props.add(new ImportProperty(idTerminalDocumentTypeField, terminalLM.findProperty("idTerminalDocumentType").getMapping(terminalDocumentTypeKey)));
                props.add(new ImportProperty(idTerminalDocumentTypeField, terminalLM.findProperty("terminalDocumentTypeTerminalDocument").getMapping(terminalDocumentKey),
                        terminalLM.object(terminalLM.findClass("TerminalDocumentType")).getMapping(terminalDocumentTypeKey)));
                fields.add(idTerminalDocumentTypeField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idTerminalDocumentType);

                ImportField quantityTerminalDocumentField = new ImportField(terminalLM.findProperty("quantityTerminalDocument"));
                props.add(new ImportProperty(quantityTerminalDocumentField, terminalLM.findProperty("quantityTerminalDocument").getMapping(terminalDocumentKey)));
                fields.add(quantityTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).quantityDocument);

                ImportField idTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("idTerminalDocumentDetail"));
                ImportKey<?> terminalDocumentDetailKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("TerminalDocumentDetail"),
                        terminalLM.findProperty("terminalDocumentDetailIdTerminalDocumentId").getMapping(idTerminalDocumentField, idTerminalDocumentDetailField));
                keys.add(terminalDocumentDetailKey);
                props.add(new ImportProperty(idTerminalDocumentDetailField, terminalLM.findProperty("idTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
                props.add(new ImportProperty(idTerminalDocumentField, terminalLM.findProperty("terminalDocumentTerminalDocumentDetail").getMapping(terminalDocumentDetailKey),
                        terminalLM.object(terminalLM.findClass("TerminalDocument")).getMapping(terminalDocumentKey)));
                fields.add(idTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idDocumentDetail);

                ImportField numberTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("numberTerminalDocumentDetail"));
                props.add(new ImportProperty(numberTerminalDocumentDetailField, terminalLM.findProperty("numberTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
                fields.add(numberTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).numberDocumentDetail);

                ImportField barcodeTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("barcodeTerminalDocumentDetail"));
                props.add(new ImportProperty(barcodeTerminalDocumentDetailField, terminalLM.findProperty("barcodeTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
                fields.add(barcodeTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).barcodeDocumentDetail);

                ImportField nameTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("nameTerminalDocumentDetail"));
                props.add(new ImportProperty(nameTerminalDocumentDetailField, terminalLM.findProperty("nameTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
                fields.add(nameTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).nameDocumentDetail);

                ImportField priceTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("priceTerminalDocumentDetail"));
                props.add(new ImportProperty(priceTerminalDocumentDetailField, terminalLM.findProperty("priceTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
                fields.add(priceTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).priceDocumentDetail);

                ImportField quantityTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("quantityTerminalDocumentDetail"));
                props.add(new ImportProperty(quantityTerminalDocumentDetailField, terminalLM.findProperty("quantityTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
                fields.add(quantityTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).quantityDocumentDetail);

                ImportField sumTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("sumTerminalDocumentDetail"));
                props.add(new ImportProperty(sumTerminalDocumentDetailField, terminalLM.findProperty("sumTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
                fields.add(sumTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).sumDocumentDetail);

                ImportTable table = new ImportTable(fields, data);

                try (DataSession session = getDbManager().createSession()) {
                    session.pushVolatileStats("ES_TI");
                    IntegrationService service = new IntegrationService(session, table, keys, props);
                    service.synchronize(true, false);
                    String result = session.applyMessage(getBusinessLogics());
                    session.popVolatileStats();
                    return result;
                }
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Map<String, List<Object>> readRequestZReportSumMap(String idStock, Date dateFrom, Date dateTo) {
        Map<String, List<Object>> zReportSumMap = new HashMap<>();
        if (zReportLM != null && equipmentCashRegisterLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                      
                DataObject stockObject = (DataObject) equipmentCashRegisterLM.findProperty("stockId").readClasses(session, new DataObject(idStock));
                
                KeyExpr zReportExpr = new KeyExpr("zReport");
                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "zReport", zReportExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
                String[] names = new String[]{"sumReceiptDetailZReport", "numberZReport", "numberCashRegisterZReport", "dateZReport"};
                LCP<?>[] properties = zReportLM.findProperties("sumReceiptDetailZReport", "numberZReport", "numberCashRegisterZReport", "dateZReport");
                for (int i = 0; i < properties.length; i++) {
                    query.addProperty(names[i], properties[i].getExpr(zReportExpr));
                }
                query.and(zReportLM.findProperty("dateZReport").getExpr(zReportExpr).compare(new DataObject(dateFrom, DateClass.instance), Compare.GREATER_EQUALS));
                query.and(zReportLM.findProperty("dateZReport").getExpr(zReportExpr).compare(new DataObject(dateTo, DateClass.instance), Compare.LESS_EQUALS));
                query.and(zReportLM.findProperty("departmentStoreZReport").getExpr(zReportExpr).compare(stockObject.getExpr(), Compare.EQUALS));
                query.and(zReportLM.findProperty("numberZReport").getExpr(zReportExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> zReportResult = query.execute(session);
                for (ImMap<Object, Object> entry : zReportResult.values()) {
                    String numberZReport = trim((String) entry.get("numberZReport"));
                    Integer numberCashRegisterZReport = (Integer) entry.get("numberCashRegisterZReport");
                    BigDecimal sumZReport = (BigDecimal) entry.get("sumReceiptDetailZReport");
                    Date dateZReport = (Date) entry.get("dateZReport");
                    zReportSumMap.put(numberZReport + "/" + numberCashRegisterZReport, Arrays.asList((Object) sumZReport, dateZReport));
                }
                
                session.apply(getBusinessLogics());
            } catch (ScriptingErrorLog.SemanticErrorException | SQLException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return zReportSumMap;
    }

    @Override
    public void logRequestZReportSumCheck(Integer idRequestExchange, Integer nppGroupMachinery, List<List<Object>> checkSumResult) throws RemoteException, SQLException {
        if (machineryPriceTransactionLM != null && cashRegisterLM != null && notNullNorEmpty(checkSumResult)) {
            try (DataSession session = getDbManager().createSession()) {
                for (List<Object> entry : checkSumResult) {
                    Object nppMachinery = entry.get(0);
                    Object message = entry.get(1);
                    DataObject logObject = session.addObject((ConcreteCustomClass) machineryPriceTransactionLM.findClass("RequestExchangeLog"));
                    ObjectValue cashRegisterObject = cashRegisterLM.findProperty("cashRegisterNppGroupCashRegisterNpp").readClasses(session, new DataObject(nppGroupMachinery), new DataObject((Integer) nppMachinery));
                    machineryPriceTransactionLM.findProperty("dateRequestExchangeLog").change(getCurrentTimestamp(), session, logObject);
                    machineryPriceTransactionLM.findProperty("messageRequestExchangeLog").change(message, session, logObject);
                    machineryPriceTransactionLM.findProperty("machineryRequestExchangeLog").change(cashRegisterObject.getValue(), session, logObject);
                    machineryPriceTransactionLM.findProperty("requestExchangeRequestExchangeLog").change(idRequestExchange, session, logObject);
                }
                session.apply(getBusinessLogics());
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public Map<Integer, List<List<Object>>> readCashRegistersStock(String idStock) throws RemoteException, SQLException {
        Map<Integer, List<List<Object>>> cashRegisterList = new HashMap<>();
        if(equipmentCashRegisterLM != null)
        try (DataSession session = getDbManager().createSession()) {

            DataObject stockObject = (DataObject) equipmentCashRegisterLM.findProperty("stockId").readClasses(session, new DataObject(idStock));

            KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "cashRegister", cashRegisterExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

            String[] machineryNames = new String[] {"nppMachinery", "nppGroupMachineryMachinery", "overDirectoryMachinery"};
            LCP[] machineryProperties = equipmentCashRegisterLM.findProperties("nppMachinery", "nppGroupMachineryMachinery", "overDirectoryMachinery");
            for (int i = 0; i < machineryProperties.length; i++) {
                query.addProperty(machineryNames[i], machineryProperties[i].getExpr(cashRegisterExpr));
            }
            
            query.and(equipmentCashRegisterLM.findProperty("departmentStoreCashRegister").getExpr(cashRegisterExpr).compare(stockObject.getExpr(), Compare.EQUALS));
            query.and(equipmentCashRegisterLM.findProperty("nppMachinery").getExpr(cashRegisterExpr).getWhere());
            query.and(equipmentCashRegisterLM.findProperty("activeCashRegister").getExpr(cashRegisterExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> zReportResult = query.execute(session);
            for (ImMap<Object, Object> entry : zReportResult.values()) {
                Integer nppMachinery = (Integer) entry.get("nppMachinery");
                Integer nppGroupMachinery = (Integer) entry.get("nppGroupMachineryMachinery");
                String overDirectoryMachinery = trim((String) entry.get("overDirectoryMachinery"));
                if(nppMachinery != null && nppGroupMachinery != null && overDirectoryMachinery != null) {
                    List<List<Object>> nppMachineryList = cashRegisterList.containsKey(nppGroupMachinery) ? cashRegisterList.get(nppGroupMachinery) : new ArrayList<List<Object>>();
                    nppMachineryList.add(Arrays.asList((Object) nppMachinery, overDirectoryMachinery));
                    cashRegisterList.put(nppGroupMachinery, nppMachineryList);
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | SQLException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }

        return cashRegisterList;
    }

    @Override
    public PromotionInfo readPromotionInfo() throws RemoteException, SQLException {
        return promotionInterface == null ? null : promotionInterface.readPromotionInfo();
    }

    @Override
    public String sendSalesInfo(List<SalesInfo> salesInfoList, String sidEquipmentServer, Integer numberAtATime) throws IOException, SQLException {
        return sendSalesInfoNonRemote(salesInfoList, sidEquipmentServer, numberAtATime);
    }


    public String sendSalesInfoNonRemote(List<SalesInfo> salesInfoList, String sidEquipmentServer, Integer numberAtATime) throws IOException, SQLException {
        try {

            if (zReportLM != null && notNullNorEmpty(salesInfoList)) {

                Collections.sort(salesInfoList, COMPARATOR);

                if (numberAtATime == null)
                    numberAtATime = salesInfoList.size();

                for (int start = 0; true;) {
                    
                    Timestamp timeStart = getCurrentTimestamp();

                    int finish = (start + numberAtATime) < salesInfoList.size() ? (start + numberAtATime) : salesInfoList.size();
                    
                    Integer lastNumberReceipt = start < finish ? salesInfoList.get(finish - 1).numberReceipt : null;
                    if(lastNumberReceipt != null) {
                        while(start < finish && salesInfoList.size() > finish && salesInfoList.get(finish).numberReceipt.equals(lastNumberReceipt))
                            finish++;                        
                    }
                    
                    List<SalesInfo> data = start < finish ? salesInfoList.subList(start, finish) : new ArrayList<SalesInfo>();
                    start = finish;
                    if (!notNullNorEmpty(data))
                        return null;

                    logger.info(String.format("Sending SalesInfo from %s to %s", start, finish));

                    try (DataSession session = getDbManager().createSession()) {

                        ObjectValue equipmentServerObject = equLM.findProperty("sidToEquipmentServer").readClasses(session, new DataObject(sidEquipmentServer));
                        Date startDate = (Date) equLM.findProperty("startDateEquipmentServer").read(session, equipmentServerObject);

                        ImportField nppGroupMachineryField = new ImportField(zReportLM.findProperty("nppGroupMachinery"));
                        ImportField nppMachineryField = new ImportField(zReportLM.findProperty("nppMachinery"));

                        ImportField idZReportField = new ImportField(zReportLM.findProperty("idZReport"));
                        ImportField numberZReportField = new ImportField(zReportLM.findProperty("numberZReport"));

                        ImportField idReceiptField = new ImportField(zReportLM.findProperty("idReceipt"));
                        ImportField numberReceiptField = new ImportField(zReportLM.findProperty("numberReceipt"));
                        ImportField dateReceiptField = new ImportField(zReportLM.findProperty("dateReceipt"));
                        ImportField timeReceiptField = new ImportField(zReportLM.findProperty("timeReceipt"));
                        ImportField isPostedZReportField = new ImportField(zReportLM.findProperty("isPostedZReport"));

                        ImportField idEmployeeField = new ImportField(zReportLM.findProperty("idEmployee"));
                        ImportField firstNameContactField = new ImportField(zReportLM.findProperty("firstNameContact"));
                        ImportField lastNameContactField = new ImportField(zReportLM.findProperty("lastNameContact"));

                        ImportField idReceiptDetailField = new ImportField(zReportLM.findProperty("idReceiptDetail"));
                        ImportField numberReceiptDetailField = new ImportField(zReportLM.findProperty("numberReceiptDetail"));
                        ImportField idBarcodeReceiptDetailField = new ImportField(zReportLM.findProperty("idBarcodeReceiptDetail"));

                        //sale 1
                        ImportField quantityReceiptSaleDetailField = new ImportField(zReportLM.findProperty("quantityReceiptSaleDetail"));
                        ImportField priceReceiptSaleDetailField = new ImportField(zReportLM.findProperty("priceReceiptSaleDetail"));
                        ImportField sumReceiptSaleDetailField = new ImportField(zReportLM.findProperty("sumReceiptSaleDetail"));
                        ImportField discountSumReceiptSaleDetailField = new ImportField(zReportLM.findProperty("discountSumReceiptSaleDetail"));
                        ImportField discountSumSaleReceiptField = new ImportField(zReportLM.findProperty("discountSumSaleReceipt"));

                        //return 1
                        ImportField idSaleReceiptReceiptReturnDetailField = new ImportField(zReportLM.findProperty("idReceipt"));
                        ImportField quantityReceiptReturnDetailField = new ImportField(zReportLM.findProperty("quantityReceiptReturnDetail"));
                        ImportField priceReceiptReturnDetailField = new ImportField(zReportLM.findProperty("priceReceiptReturnDetail"));
                        ImportField retailSumReceiptReturnDetailField = new ImportField(zReportLM.findProperty("sumReceiptReturnDetail"));
                        ImportField discountSumReceiptReturnDetailField = new ImportField(zReportLM.findProperty("discountSumReceiptReturnDetail"));
                        ImportField discountSumReturnReceiptField = new ImportField(zReportLM.findProperty("discountSumReturnReceipt"));

                        //giftCard 1
                        ImportField priceReceiptGiftCardSaleDetailField = null;
                        ImportField sumReceiptGiftCardSaleDetailField = null;
                        ImportField idGiftCardField = null;
                        if (giftCardLM != null) {
                            priceReceiptGiftCardSaleDetailField = new ImportField(giftCardLM.findProperty("priceReceiptGiftCardSaleDetail"));
                            sumReceiptGiftCardSaleDetailField = new ImportField(giftCardLM.findProperty("sumReceiptGiftCardSaleDetail"));
                            idGiftCardField = new ImportField(giftCardLM.findProperty("idGiftCard"));
                        }

                        ImportField idPaymentField = new ImportField(zReportLM.findProperty("ZReport.idPayment"));
                        ImportField sidTypePaymentField = new ImportField(zReportLM.findProperty("sidPaymentType"));
                        ImportField sumPaymentField = new ImportField(zReportLM.findProperty("ZReport.sumPayment"));
                        ImportField numberPaymentField = new ImportField(zReportLM.findProperty("ZReport.numberPayment"));

                        ImportField seriesNumberDiscountCardField = discountCardLM == null ? null : new ImportField(discountCardLM.findProperty("seriesNumberDiscountCard"));
                        ImportField idSectionField = zReportSectionLM == null ? null : new ImportField(zReportSectionLM.findProperty("idSection"));

                        List<ImportProperty<?>> saleProperties = new ArrayList<>();
                        List<ImportProperty<?>> returnProperties = new ArrayList<>();
                        List<ImportProperty<?>> giftCardProperties = new ArrayList<>();
                        List<ImportProperty<?>> paymentProperties = new ArrayList<>();

                        ImportKey<?> zReportKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ZReport"), zReportLM.findProperty("zReportId").getMapping(idZReportField));
                        ImportKey<?> cashRegisterKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("CashRegister"), zReportLM.findProperty("cashRegisterNppGroupCashRegisterNpp").getMapping(nppGroupMachineryField, nppMachineryField));
                        cashRegisterKey.skipKey = true;
                        ImportKey<?> receiptKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("Receipt"), zReportLM.findProperty("receiptId").getMapping(idReceiptField));
                        ImportKey<?> skuKey = new ImportKey((CustomClass) zReportLM.findClass("Sku"), zReportLM.findProperty("skuBarcodeIdDate").getMapping(idBarcodeReceiptDetailField, dateReceiptField));
                        ImportKey<?> employeeKey = new ImportKey((CustomClass) zReportLM.findClass("Employee"), zReportLM.findProperty("employeeId").getMapping(idEmployeeField));

                        ImportKey<?> discountCardKey = discountCardLM == null ? null : new ImportKey((ConcreteCustomClass) discountCardLM.findClass("DiscountCard"), discountCardLM.findProperty("discountCardSeriesNumber").getMapping(seriesNumberDiscountCardField, dateReceiptField));
                        ImportKey<?> giftCardKey = giftCardLM == null ? null : new ImportKey((ConcreteCustomClass) giftCardLM.findClass("GiftCard"), giftCardLM.findProperty("giftCardId").getMapping(idGiftCardField));
                        ImportKey<?> sectionKey = zReportSectionLM == null ? null : new ImportKey((ConcreteCustomClass) zReportSectionLM.findClass("Section"), zReportSectionLM.findProperty("sectionId").getMapping(idSectionField));

                        //sale 2
                        saleProperties.add(new ImportProperty(idZReportField, zReportLM.findProperty("idZReport").getMapping(zReportKey)));
                        saleProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("numberZReport").getMapping(zReportKey)));
                        saleProperties.add(new ImportProperty(nppMachineryField, zReportLM.findProperty("cashRegisterZReport").getMapping(zReportKey),
                                zReportLM.object(zReportLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
                        saleProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("dateZReport").getMapping(zReportKey)));
                        saleProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("timeZReport").getMapping(zReportKey)));
                        saleProperties.add(new ImportProperty(isPostedZReportField, zReportLM.findProperty("isPostedZReport").getMapping(zReportKey)));

                        saleProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("idReceipt").getMapping(receiptKey)));
                        saleProperties.add(new ImportProperty(numberReceiptField, zReportLM.findProperty("numberReceipt").getMapping(receiptKey)));
                        saleProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("dateReceipt").getMapping(receiptKey)));
                        saleProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("timeReceipt").getMapping(receiptKey)));
                        saleProperties.add(new ImportProperty(discountSumSaleReceiptField, zReportLM.findProperty("discountSumSaleReceipt").getMapping(receiptKey)));
                        saleProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReportReceipt").getMapping(receiptKey),
                                zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));
                        if (discountCardLM != null && zReportDiscountCardLM != null) {
                            saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, discountCardLM.findProperty("numberDiscountCard").getMapping(discountCardKey), true));
                            saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("discountCardReceipt").getMapping(receiptKey),
                                    discountCardLM.object(discountCardLM.findClass("DiscountCard")).getMapping(discountCardKey)));
                        }
                        saleProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("idEmployee").getMapping(employeeKey)));
                        saleProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("employeeReceipt").getMapping(receiptKey),
                                zReportLM.object(zReportLM.findClass("Employee")).getMapping(employeeKey)));
                        saleProperties.add(new ImportProperty(firstNameContactField, zReportLM.findProperty("firstNameContact").getMapping(employeeKey), true));
                        saleProperties.add(new ImportProperty(lastNameContactField, zReportLM.findProperty("lastNameContact").getMapping(employeeKey), true));

                        ImportKey<?> receiptSaleDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ReceiptSaleDetail"), zReportLM.findProperty("receiptDetailId").getMapping(idReceiptDetailField));
                        saleProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("idReceiptDetail").getMapping(receiptSaleDetailKey)));
                        saleProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("numberReceiptDetail").getMapping(receiptSaleDetailKey)));
                        saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("idBarcodeReceiptDetail").getMapping(receiptSaleDetailKey)));
                        saleProperties.add(new ImportProperty(quantityReceiptSaleDetailField, zReportLM.findProperty("quantityReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                        saleProperties.add(new ImportProperty(priceReceiptSaleDetailField, zReportLM.findProperty("priceReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                        saleProperties.add(new ImportProperty(sumReceiptSaleDetailField, zReportLM.findProperty("sumReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                        saleProperties.add(new ImportProperty(discountSumReceiptSaleDetailField, zReportLM.findProperty("discountSumReceiptSaleDetail").getMapping(receiptSaleDetailKey)));

                        saleProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receiptReceiptDetail").getMapping(receiptSaleDetailKey),
                                zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                        saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("skuReceiptSaleDetail").getMapping(receiptSaleDetailKey),
                                zReportLM.object(zReportLM.findClass("Sku")).getMapping(skuKey)));

                        if(zReportSectionLM != null) {
                            saleProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("idSection").getMapping(sectionKey), true));
                            saleProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("sectionReceiptDetail").getMapping(receiptSaleDetailKey),
                                    zReportSectionLM.object(zReportSectionLM.findClass("Section")).getMapping(sectionKey)));
                        }

                        //return 2
                        returnProperties.add(new ImportProperty(idZReportField, zReportLM.findProperty("idZReport").getMapping(zReportKey)));
                        returnProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("numberZReport").getMapping(zReportKey)));
                        returnProperties.add(new ImportProperty(nppMachineryField, zReportLM.findProperty("cashRegisterZReport").getMapping(zReportKey),
                                zReportLM.object(zReportLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
                        returnProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("dateZReport").getMapping(zReportKey)));
                        returnProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("timeZReport").getMapping(zReportKey)));
                        returnProperties.add(new ImportProperty(isPostedZReportField, zReportLM.findProperty("isPostedZReport").getMapping(zReportKey)));

                        returnProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("idReceipt").getMapping(receiptKey)));
                        returnProperties.add(new ImportProperty(numberReceiptField, zReportLM.findProperty("numberReceipt").getMapping(receiptKey)));
                        returnProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("dateReceipt").getMapping(receiptKey)));
                        returnProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("timeReceipt").getMapping(receiptKey)));
                        if (discountCardLM != null) {
                            returnProperties.add(new ImportProperty(discountSumReturnReceiptField, zReportLM.findProperty("discountSumReturnReceipt").getMapping(receiptKey)));
                        }
                        returnProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReportReceipt").getMapping(receiptKey),
                                zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));
                        if (discountCardLM != null && zReportDiscountCardLM != null) {
                            returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, discountCardLM.findProperty("seriesNumberDiscountCard").getMapping(discountCardKey)));
                            returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("discountCardReceipt").getMapping(receiptKey),
                                    discountCardLM.object(discountCardLM.findClass("DiscountCard")).getMapping(discountCardKey)));
                        }
                        returnProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("loginCustomUser").getMapping(employeeKey), true));
                        returnProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("employeeReceipt").getMapping(receiptKey),
                                zReportLM.object(zReportLM.findClass("CustomUser")).getMapping(employeeKey)));
                        returnProperties.add(new ImportProperty(firstNameContactField, zReportLM.findProperty("firstNameContact").getMapping(employeeKey), true));
                        returnProperties.add(new ImportProperty(lastNameContactField, zReportLM.findProperty("lastNameContact").getMapping(employeeKey), true));

                        ImportKey<?> receiptReturnDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ReceiptReturnDetail"), zReportLM.findProperty("receiptDetailId").getMapping(idReceiptDetailField));
                        returnProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("idReceiptDetail").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("numberReceiptDetail").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("idBarcodeReceiptDetail").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(quantityReceiptReturnDetailField, zReportLM.findProperty("quantityReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(priceReceiptReturnDetailField, zReportLM.findProperty("priceReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(retailSumReceiptReturnDetailField, zReportLM.findProperty("sumReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(discountSumReceiptReturnDetailField, zReportLM.findProperty("discountSumReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receiptReceiptDetail").getMapping(receiptReturnDetailKey),
                                zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                        returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("skuReceiptReturnDetail").getMapping(receiptReturnDetailKey),
                                zReportLM.object(zReportLM.findClass("Sku")).getMapping(skuKey)));

                        ImportKey<?> receiptSaleDetailReceiptReturnDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("Receipt"), zReportLM.findProperty("receiptId").getMapping(idSaleReceiptReceiptReturnDetailField));
                        receiptSaleDetailReceiptReturnDetailKey.skipKey = true;
                        returnProperties.add(new ImportProperty(idSaleReceiptReceiptReturnDetailField, zReportLM.findProperty("saleReceiptReceiptReturnDetail").getMapping(receiptReturnDetailKey),
                                zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptSaleDetailReceiptReturnDetailKey)));

                        if(zReportSectionLM != null) {
                            returnProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("idSection").getMapping(sectionKey), true));
                            returnProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("sectionReceiptDetail").getMapping(receiptReturnDetailKey),
                                    zReportSectionLM.object(zReportSectionLM.findClass("Section")).getMapping(sectionKey)));
                        }

                        //giftCard 2
                        ImportKey<?> receiptGiftCardSaleDetailKey = null;
                        if (giftCardLM != null) {
                            giftCardProperties.add(new ImportProperty(idZReportField, zReportLM.findProperty("idZReport").getMapping(zReportKey)));
                            giftCardProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("numberZReport").getMapping(zReportKey)));
                            giftCardProperties.add(new ImportProperty(nppMachineryField, zReportLM.findProperty("cashRegisterZReport").getMapping(zReportKey),
                                    zReportLM.object(zReportLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
                            giftCardProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("dateZReport").getMapping(zReportKey)));
                            giftCardProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("timeZReport").getMapping(zReportKey)));
                            giftCardProperties.add(new ImportProperty(isPostedZReportField, zReportLM.findProperty("isPostedZReport").getMapping(zReportKey)));

                            giftCardProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("idReceipt").getMapping(receiptKey)));
                            giftCardProperties.add(new ImportProperty(numberReceiptField, zReportLM.findProperty("numberReceipt").getMapping(receiptKey)));
                            giftCardProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("dateReceipt").getMapping(receiptKey)));
                            giftCardProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("timeReceipt").getMapping(receiptKey)));

                            giftCardProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReportReceipt").getMapping(receiptKey),
                                    zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));
                            giftCardProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("loginCustomUser").getMapping(employeeKey), true));
                            giftCardProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("employeeReceipt").getMapping(receiptKey),
                                    zReportLM.object(zReportLM.findClass("CustomUser")).getMapping(employeeKey)));
                            giftCardProperties.add(new ImportProperty(firstNameContactField, zReportLM.findProperty("firstNameContact").getMapping(employeeKey), true));
                            giftCardProperties.add(new ImportProperty(lastNameContactField, zReportLM.findProperty("lastNameContact").getMapping(employeeKey), true));

                            receiptGiftCardSaleDetailKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("ReceiptGiftCardSaleDetail"), zReportLM.findProperty("receiptDetailId").getMapping(idReceiptDetailField));
                            giftCardProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("idReceiptDetail").getMapping(receiptGiftCardSaleDetailKey)));
                            giftCardProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("numberReceiptDetail").getMapping(receiptGiftCardSaleDetailKey)));
                            giftCardProperties.add(new ImportProperty(priceReceiptGiftCardSaleDetailField, giftCardLM.findProperty("priceReceiptGiftCardSaleDetail").getMapping(receiptGiftCardSaleDetailKey)));
                            giftCardProperties.add(new ImportProperty(sumReceiptGiftCardSaleDetailField, giftCardLM.findProperty("sumReceiptGiftCardSaleDetail").getMapping(receiptGiftCardSaleDetailKey)));
                            giftCardProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receiptReceiptDetail").getMapping(receiptGiftCardSaleDetailKey),
                                    zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                            giftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("idGiftCard").getMapping(giftCardKey)));
                            giftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("numberGiftCard").getMapping(giftCardKey)));
                            giftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("giftCardReceiptGiftCardSaleDetail").getMapping(receiptGiftCardSaleDetailKey),
                                    zReportLM.object(giftCardLM.findClass("GiftCard")).getMapping(giftCardKey)));
                        }

                        List<List<Object>> dataSale = new ArrayList<>();
                        List<List<Object>> dataReturn = new ArrayList<>();
                        List<List<Object>> dataGiftCard = new ArrayList<>();

                        List<List<Object>> dataPayment = new ArrayList<>();

                        Map<Object, String> barcodeMap = new HashMap<>();
                        for (SalesInfo sale : data) {

                            String barcode = (notNullNorEmpty(sale.barcodeItem)) ? sale.barcodeItem :
                                    (sale.itemObject != null ? barcodeMap.get(sale.itemObject) : sale.idItem != null ? barcodeMap.get(sale.idItem) : null);
                            if (barcode == null && sale.itemObject != null) {
                                barcode = trim((String) itemLM.findProperty("idBarcodeSku").read(session, new DataObject(sale.itemObject, (ConcreteClass) itemLM.findClass("Item"))));
                                barcodeMap.put(sale.itemObject, barcode);
                            }
                            if (barcode == null && sale.idItem != null) {
                                barcode = trim((String) itemLM.findProperty("idBarcodeIdSku").read(session, new DataObject(sale.idItem, StringClass.get((100)))));
                                barcodeMap.put(sale.idItem, barcode);
                            }

                            String idReceipt = sale.getIdReceipt(startDate);
                            if (sale.isGiftCard) {
                                //giftCard 3
                                List<Object> row = Arrays.<Object>asList(sale.nppGroupMachinery, sale.nppMachinery, sale.getIdZReport(startDate), sale.numberZReport,
                                        sale.dateReceipt, sale.timeReceipt, true, sale.idEmployee, sale.firstNameContact, sale.lastNameContact,
                                        idReceipt, sale.numberReceipt, sale.getIdReceiptDetail(startDate), sale.numberReceiptDetail, barcode,
                                        sale.priceReceiptDetail, sale.sumReceiptDetail);
                                dataGiftCard.add(row);
                            } else if (sale.quantityReceiptDetail.doubleValue() < 0) {
                                //return 3
                                List<Object> row = Arrays.<Object>asList(sale.nppGroupMachinery, sale.nppMachinery, sale.getIdZReport(startDate), sale.numberZReport,
                                        sale.dateReceipt, sale.timeReceipt, true, sale.idEmployee, sale.firstNameContact, sale.lastNameContact,
                                        idReceipt, sale.numberReceipt, sale.getIdReceiptDetail(startDate), sale.numberReceiptDetail, barcode, sale.quantityReceiptDetail.negate(),
                                        sale.priceReceiptDetail, sale.sumReceiptDetail.negate(), sale.discountSumReceiptDetail, sale.discountSumReceipt, sale.idSaleReceiptReceiptReturnDetail);
                                if (discountCardLM != null) {
                                    row = new ArrayList<>(row);
                                    row.add(sale.seriesNumberDiscountCard);
                                }
                                if (zReportSectionLM != null) {
                                    row = new ArrayList<>(row);
                                    row.add(sale.idSection);
                                }
                                dataReturn.add(row);
                            } else {
                                //sale 3
                                List<Object> row = Arrays.<Object>asList(sale.nppGroupMachinery, sale.nppMachinery, sale.getIdZReport(startDate), sale.numberZReport,
                                        sale.dateReceipt, sale.timeReceipt, true, sale.idEmployee, sale.firstNameContact, sale.lastNameContact,
                                        idReceipt, sale.numberReceipt, sale.getIdReceiptDetail(startDate), sale.numberReceiptDetail, barcode, sale.quantityReceiptDetail,
                                        sale.priceReceiptDetail, sale.sumReceiptDetail, sale.discountSumReceiptDetail, sale.discountSumReceipt);
                                if (discountCardLM != null) {
                                    row = new ArrayList<>(row);
                                    row.add(sale.seriesNumberDiscountCard);
                                }
                                if (zReportSectionLM != null) {
                                    row = new ArrayList<>(row);
                                    row.add(sale.idSection);
                                }
                                dataSale.add(row);
                            }
                            if (sale.sumCash != null && sale.sumCash.doubleValue() != 0) {
                                dataPayment.add(Arrays.<Object>asList(idReceipt + "1", idReceipt, "cash", sale.sumCash, 1));
                            }
                            if (sale.sumCard != null && sale.sumCard.doubleValue() != 0) {
                                dataPayment.add(Arrays.<Object>asList(idReceipt + "2", idReceipt, "card", sale.sumCard, 2));
                            }
                            if (sale.sumGiftCard != null && sale.sumGiftCard.doubleValue() != 0) {
                                dataPayment.add(Arrays.<Object>asList(idReceipt + "3", idReceipt, "giftcard", sale.sumGiftCard, 3));
                            }
                        }

                        //sale 4
                        List<ImportField> saleImportFields = Arrays.asList(nppGroupMachineryField, nppMachineryField,
                                idZReportField, numberZReportField, dateReceiptField, timeReceiptField, isPostedZReportField,
                                idEmployeeField, firstNameContactField, lastNameContactField, idReceiptField, numberReceiptField,
                                idReceiptDetailField, numberReceiptDetailField, idBarcodeReceiptDetailField,
                                quantityReceiptSaleDetailField, priceReceiptSaleDetailField, sumReceiptSaleDetailField,
                                discountSumReceiptSaleDetailField, discountSumSaleReceiptField);
                        if (discountCardLM != null) {
                            saleImportFields = new ArrayList<>(saleImportFields);
                            saleImportFields.add(seriesNumberDiscountCardField);
                        }
                        if (zReportSectionLM != null) {
                            saleImportFields = new ArrayList<>(saleImportFields);
                            saleImportFields.add(idSectionField);
                        }

                        //return 4
                        List<ImportField> returnImportFields = Arrays.asList(nppGroupMachineryField, nppMachineryField,
                                idZReportField, numberZReportField, dateReceiptField, timeReceiptField, isPostedZReportField,
                                idEmployeeField, firstNameContactField, lastNameContactField, idReceiptField, numberReceiptField,
                                idReceiptDetailField, numberReceiptDetailField, idBarcodeReceiptDetailField,
                                quantityReceiptReturnDetailField, priceReceiptReturnDetailField, retailSumReceiptReturnDetailField,
                                discountSumReceiptReturnDetailField, discountSumReturnReceiptField, idSaleReceiptReceiptReturnDetailField);
                        if (discountCardLM != null) {
                            returnImportFields = new ArrayList<>(returnImportFields);
                            returnImportFields.add(seriesNumberDiscountCardField);
                        }
                        if (zReportSectionLM != null) {
                            returnImportFields = new ArrayList<>(returnImportFields);
                            returnImportFields.add(idSectionField);
                        }

                        //giftCard 4
                        List<ImportField> giftCardImportFields = Arrays.asList(nppGroupMachineryField, nppMachineryField,
                                idZReportField, numberZReportField, dateReceiptField, timeReceiptField, isPostedZReportField,
                                idEmployeeField, firstNameContactField, lastNameContactField, idReceiptField, numberReceiptField,
                                idReceiptDetailField, numberReceiptDetailField, idGiftCardField,
                                priceReceiptGiftCardSaleDetailField, sumReceiptGiftCardSaleDetailField);

                        //sale 5
                        List<ImportKey<?>> saleKeys = Arrays.asList(zReportKey, cashRegisterKey, receiptKey, receiptSaleDetailKey, skuKey, employeeKey);
                        if (discountCardLM != null) {
                            saleKeys = new ArrayList<>(saleKeys);
                            saleKeys.add(discountCardKey);
                        }
                        if (zReportSectionLM != null) {
                            saleKeys = new ArrayList<>(saleKeys);
                            saleKeys.add(sectionKey);
                        }
                        new IntegrationService(session, new ImportTable(saleImportFields, dataSale), saleKeys, saleProperties).synchronize(true);

                        //return 5
                        List<ImportKey<?>> returnKeys = Arrays.asList(zReportKey, cashRegisterKey, receiptKey, receiptReturnDetailKey, skuKey, employeeKey, receiptSaleDetailReceiptReturnDetailKey);
                        if (discountCardLM != null) {
                            returnKeys = new ArrayList<>(returnKeys);
                            returnKeys.add(discountCardKey);
                        }
                        if (zReportSectionLM != null) {
                            returnKeys = new ArrayList<>(returnKeys);
                            returnKeys.add(sectionKey);
                        }
                        new IntegrationService(session, new ImportTable(returnImportFields, dataReturn), returnKeys, returnProperties).synchronize(true);

                        //giftCard 5
                        if (giftCardLM != null) {
                            List<ImportKey<?>> giftCardKeys = Arrays.asList(zReportKey, cashRegisterKey, receiptKey, receiptGiftCardSaleDetailKey, giftCardKey, employeeKey);
                            new IntegrationService(session, new ImportTable(giftCardImportFields, dataGiftCard), giftCardKeys, giftCardProperties).synchronize(true);
                        }

                        ImportKey<?> paymentKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ZReport.Payment"), zReportLM.findProperty("ZReport.paymentId").getMapping(idPaymentField));
                        ImportKey<?> paymentTypeKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("PaymentType"), zReportLM.findProperty("typePaymentSID").getMapping(sidTypePaymentField));
                        paymentProperties.add(new ImportProperty(idPaymentField, zReportLM.findProperty("ZReport.idPayment").getMapping(paymentKey)));
                        paymentProperties.add(new ImportProperty(sumPaymentField, zReportLM.findProperty("ZReport.sumPayment").getMapping(paymentKey)));
                        paymentProperties.add(new ImportProperty(numberPaymentField, zReportLM.findProperty("ZReport.numberPayment").getMapping(paymentKey)));
                        paymentProperties.add(new ImportProperty(sidTypePaymentField, zReportLM.findProperty("paymentTypePayment").getMapping(paymentKey),
                                zReportLM.object(zReportLM.findClass("PaymentType")).getMapping(paymentTypeKey)));
                        paymentProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receiptPayment").getMapping(paymentKey),
                                zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                        List<ImportField> paymentImportFields = Arrays.asList(idPaymentField, idReceiptField, sidTypePaymentField,
                                sumPaymentField, numberPaymentField);

                        new IntegrationService(session, new ImportTable(paymentImportFields, dataPayment), Arrays.asList(paymentKey, paymentTypeKey, receiptKey),
                                paymentProperties).synchronize(true);

                        String result = session.applyMessage(getBusinessLogics());
                        if (result == null) {
                            logCompleteMessage(data, dataSale.size() + dataReturn.size() + dataGiftCard.size(), timeStart, sidEquipmentServer);
                        } else
                            return result;
                    }
                }
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
    
    private String logCompleteMessage(List<SalesInfo> data, int dataSize, Timestamp timeStart, String sidEquipmentServer) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        
        String message = formatCompleteMessage(data, dataSize, timeStart);
        
        try (DataSession session = getDbManager().createSession()) {
            ObjectValue equipmentServerObject = equLM.findProperty("sidToEquipmentServer").readClasses(session, new DataObject(sidEquipmentServer));
            DataObject logObject = session.addObject((ConcreteCustomClass) equLM.findClass("EquipmentServerLog"));
            equLM.findProperty("equipmentServerEquipmentServerLog").change(equipmentServerObject.getValue(), session, logObject);
            equLM.findProperty("dataEquipmentServerLog").change(message, session, logObject);
            equLM.findProperty("dateEquipmentServerLog").change(getCurrentTimestamp(), session, logObject);
            return session.applyMessage(getBusinessLogics());
        }
    }
    
    private String formatCompleteMessage(List<SalesInfo> data, int dataSize, Timestamp timeStart) {

        Timestamp timeFinish = getCurrentTimestamp();
        String message = String.format("Затрачено времени: %s с (%s - %s)\nЗагружено записей: %s",  
                (timeFinish.getTime() - timeStart.getTime())/1000, formatDateTime(timeStart), formatDateTime(timeFinish), dataSize);
        
        Map<Integer, Set<Integer>> nppCashRegisterMap = new HashMap<>();
        List<String> fileNames = new ArrayList<>();
        Set<String> dates = new HashSet<>();
        for (SalesInfo salesInfo : data) {
            if(nppCashRegisterMap.containsKey(salesInfo.nppGroupMachinery))
                nppCashRegisterMap.get(salesInfo.nppGroupMachinery).add(salesInfo.nppMachinery);
            else
                nppCashRegisterMap.put(salesInfo.nppGroupMachinery, new HashSet<>(Collections.singletonList(salesInfo.nppMachinery)));
            if ((salesInfo.filename != null) && (!fileNames.contains(salesInfo.filename.trim())))
                fileNames.add(salesInfo.filename.trim());
            if(salesInfo.dateReceipt != null)
                dates.add(formatDate(salesInfo.dateReceipt));
        }
        
        message += "\nИз касс: ";
        for (Map.Entry<Integer, Set<Integer>> cashRegisterEntry : nppCashRegisterMap.entrySet()) {
            for(Integer cashRegister : cashRegisterEntry.getValue())
                message += String.format("%s(%s), ", cashRegister, cashRegisterEntry.getKey());
        }
        message = message.substring(0, message.length() - 2);

        if(!fileNames.isEmpty()) {
            message += "\nИз файлов: ";
            for (String filename : fileNames)
                message += filename + ", ";
            message = message.substring(0, message.length() - 2);
        }

        if(notNullNorEmpty(dates)) {
            message += "\nЗа даты: ";
            for (String date : dates)
                message += date + ", ";
            message = message.substring(0, message.length() - 2);
        }
        return message;
    }

    @Override
    public Set<String> readCashDocumentSet(String sidEquipmentServer) throws IOException, SQLException {

        Set<String> cashDocumentSet = new HashSet<>();
        if (collectionLM != null) {
            try (DataSession session = getDbManager().createSession()) {

                KeyExpr cashDocumentExpr = new KeyExpr("cashDocument");
                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "CashDocument", cashDocumentExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
                query.addProperty("idCashDocument", collectionLM.findProperty("idCashDocument").getExpr(cashDocumentExpr));
                query.and(collectionLM.findProperty("idCashDocument").getExpr(cashDocumentExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    cashDocumentSet.add((String) row.get("idCashDocument"));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return cashDocumentSet;
    }

    @Override
    public String sendCashDocumentInfo(List<CashDocument> cashDocumentList, String sidEquipmentServer) throws IOException, SQLException {

        if (collectionLM != null && cashDocumentList != null) {

            try {

                List<ImportField> fieldsIncome = new ArrayList<>();
                List<ImportField> fieldsOutcome = new ArrayList<>();

                List<ImportProperty<?>> propsIncome = new ArrayList<>();
                List<ImportProperty<?>> propsOutcome = new ArrayList<>();

                List<ImportKey<?>> keysIncome = new ArrayList<>();
                List<ImportKey<?>> keysOutcome = new ArrayList<>();
                
                List<List<Object>> dataIncome = new ArrayList<>();
                List<List<Object>> dataOutcome = new ArrayList<>();

                ImportField idCashDocumentField = new ImportField(collectionLM.findProperty("idCashDocument"));               
                
                ImportKey<?> incomeCashOperationKey = new ImportKey((CustomClass) collectionLM.findClass("IncomeCashOperation"),
                        collectionLM.findProperty("cashDocumentId").getMapping(idCashDocumentField));
                keysIncome.add(incomeCashOperationKey);
                propsIncome.add(new ImportProperty(idCashDocumentField, collectionLM.findProperty("idCashDocument").getMapping(incomeCashOperationKey)));
                //propsIncome.add(new ImportProperty(idCashDocumentField, collectionLM.findProperty("numberIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(idCashDocumentField);

                ImportKey<?> outcomeCashOperationKey = new ImportKey((CustomClass) collectionLM.findClass("OutcomeCashOperation"),
                        collectionLM.findProperty("cashDocumentId").getMapping(idCashDocumentField));
                keysOutcome.add(outcomeCashOperationKey);
                propsOutcome.add(new ImportProperty(idCashDocumentField, collectionLM.findProperty("idCashDocument").getMapping(outcomeCashOperationKey)));
                //propsOutcome.add(new ImportProperty(idCashDocumentField, collectionLM.findProperty("numberOutcomeCashOperation").getMapping(outcomeCashOperationKey)));                
                fieldsOutcome.add(idCashDocumentField);

                ImportField numberIncomeCashOperationField = new ImportField(collectionLM.findProperty("numberIncomeCashOperation"));
                propsIncome.add(new ImportProperty(numberIncomeCashOperationField, collectionLM.findProperty("numberIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(numberIncomeCashOperationField);

                ImportField numberOutcomeCashOperationField = new ImportField(collectionLM.findProperty("numberOutcomeCashOperation"));
                propsOutcome.add(new ImportProperty(numberOutcomeCashOperationField, collectionLM.findProperty("numberOutcomeCashOperation").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(numberOutcomeCashOperationField);
                
                ImportField dateIncomeCashOperationField = new ImportField(collectionLM.findProperty("dateIncomeCashOperation"));
                propsIncome.add(new ImportProperty(dateIncomeCashOperationField, collectionLM.findProperty("dateIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(dateIncomeCashOperationField);

                ImportField dateOutcomeCashOperationField = new ImportField(collectionLM.findProperty("dateOutcomeCashOperation"));
                propsOutcome.add(new ImportProperty(dateOutcomeCashOperationField, collectionLM.findProperty("dateOutcomeCashOperation").getMapping(outcomeCashOperationKey)));                
                fieldsOutcome.add(dateOutcomeCashOperationField);

                ImportField timeIncomeCashOperationField = new ImportField(collectionLM.findProperty("timeIncomeCashOperation"));
                propsIncome.add(new ImportProperty(timeIncomeCashOperationField, collectionLM.findProperty("timeIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(timeIncomeCashOperationField);
                
                ImportField timeOutcomeCashOperationField = new ImportField(collectionLM.findProperty("timeOutcomeCashOperation"));
                propsOutcome.add(new ImportProperty(timeOutcomeCashOperationField, collectionLM.findProperty("timeOutcomeCashOperation").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(timeOutcomeCashOperationField);

                ImportField nppGroupMachineryField = new ImportField(collectionLM.findProperty("nppGroupMachinery"));
                ImportField nppMachineryField = new ImportField(collectionLM.findProperty("nppMachinery"));
                ImportKey<?> cashRegisterKey = new ImportKey((ConcreteCustomClass) collectionLM.findClass("CashRegister"),
                        zReportLM.findProperty("cashRegisterNppGroupCashRegisterNpp").getMapping(nppGroupMachineryField, nppMachineryField/*, sidEquipmentServerField*/));
                
                keysIncome.add(cashRegisterKey);
                propsIncome.add(new ImportProperty(nppMachineryField, collectionLM.findProperty("cashRegisterIncomeCashOperation").getMapping(incomeCashOperationKey),
                        collectionLM.object(collectionLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
                fieldsIncome.add(nppGroupMachineryField);
                fieldsIncome.add(nppMachineryField);
                
                keysOutcome.add(cashRegisterKey);
                propsOutcome.add(new ImportProperty(nppMachineryField, collectionLM.findProperty("cashRegisterOutcomeCashOperation").getMapping(outcomeCashOperationKey),
                        collectionLM.object(collectionLM.findClass("CashRegister")).getMapping(cashRegisterKey)));             
                fieldsOutcome.add(nppGroupMachineryField);
                fieldsOutcome.add(nppMachineryField);
                
                ImportField sumCashIncomeCashOperationField = new ImportField(collectionLM.findProperty("sumCashIncomeCashOperation"));
                propsIncome.add(new ImportProperty(sumCashIncomeCashOperationField, collectionLM.findProperty("sumCashIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(sumCashIncomeCashOperationField);

                ImportField sumCashOutcomeCashOperationField = new ImportField(collectionLM.findProperty("sumCashOutcomeCashOperation"));
                propsOutcome.add(new ImportProperty(sumCashOutcomeCashOperationField, collectionLM.findProperty("sumCashOutcomeCashOperation").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(sumCashOutcomeCashOperationField);

                for (CashDocument cashDocument : cashDocumentList) {
                    if (cashDocument.sumCashDocument != null) {
                        if (cashDocument.sumCashDocument.compareTo(BigDecimal.ZERO) >= 0)
                            dataIncome.add(Arrays.asList((Object) cashDocument.idCashDocument, cashDocument.numberCashDocument, cashDocument.dateCashDocument,
                                    cashDocument.timeCashDocument, cashDocument.nppGroupMachinery, cashDocument.nppMachinery, cashDocument.sumCashDocument));
                        else
                            dataOutcome.add(Arrays.asList((Object) cashDocument.idCashDocument, cashDocument.numberCashDocument, cashDocument.dateCashDocument,
                                    cashDocument.timeCashDocument, cashDocument.nppGroupMachinery, cashDocument.nppMachinery, cashDocument.sumCashDocument.negate()));
                    }
                }
                
                
                ImportTable table = new ImportTable(fieldsIncome, dataIncome);
                String resultIncome;
                try (DataSession session = getDbManager().createSession()) {
                    session.pushVolatileStats("ES_CDI");
                    IntegrationService service = new IntegrationService(session, table, keysIncome, propsIncome);
                    service.synchronize(true, false);
                    resultIncome = session.applyMessage(getBusinessLogics());
                    session.popVolatileStats();
                }
                if(resultIncome != null)
                    return resultIncome;
                
                table = new ImportTable(fieldsOutcome, dataOutcome);
                String resultOutcome;

                try (DataSession session = getDbManager().createSession()) {
                    session.pushVolatileStats("ES_CDI");
                    IntegrationService service = new IntegrationService(session, table, keysOutcome, propsOutcome);
                    service.synchronize(true, false);
                    resultOutcome = session.applyMessage(getBusinessLogics());
                    session.popVolatileStats();
                }

                return resultOutcome;
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        } else return null;
    }

    @Override
    public void succeedTransaction(Integer transactionId, Timestamp dateTime) throws RemoteException, SQLException {
        try (DataSession session = getDbManager().createSession()) {
            DataObject transactionObject = session.getDataObject(equLM.findClass("MachineryPriceTransaction"), transactionId);
            equLM.findProperty("succeededMachineryPriceTransaction").change(true, session, transactionObject);
            equLM.findProperty("dateTimeSucceededMachineryPriceTransaction").change(dateTime, session, transactionObject);
            session.apply(getBusinessLogics());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void processingTransaction(Integer transactionId, Timestamp dateTime) throws RemoteException, SQLException {
        if (machineryPriceTransactionLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                DataObject transactionObject = session.getDataObject(equLM.findClass("MachineryPriceTransaction"), transactionId);
                machineryPriceTransactionLM.findProperty("dateTimeProcessingMachineryPriceTransaction").change(dateTime, session, transactionObject);
                session.apply(getBusinessLogics());
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void clearedMachineryTransaction(Integer transactionId, List<MachineryInfo> machineryInfoList) throws RemoteException, SQLException {
        if(machineryPriceTransactionLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                DataObject transactionObject = session.getDataObject(equLM.findClass("MachineryPriceTransaction"), transactionId);
                for (MachineryInfo machineryInfo : machineryInfoList) {
                    ObjectValue machineryObject = null;
                    if (machineryInfo instanceof CashRegisterInfo && cashRegisterLM != null)
                        machineryObject = cashRegisterLM.findProperty("cashRegisterNppGroupCashRegisterNpp").readClasses(session, new DataObject(machineryInfo.numberGroup), new DataObject(machineryInfo.number));
                    else if (machineryInfo instanceof ScalesInfo && scalesLM != null)
                        machineryObject = scalesLM.findProperty("scalesNppGroupScalesNpp").readClasses(session, new DataObject(machineryInfo.numberGroup), new DataObject(machineryInfo.number));
                    if (machineryObject != null && !machineryInfo.cleared)
                        machineryPriceTransactionLM.findProperty("clearedMachineryMachineryPriceTransaction").change(true, session, (DataObject) machineryObject, transactionObject);
                }
                session.apply(getBusinessLogics());
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void succeedMachineryTransaction(Integer transactionId, List<MachineryInfo> machineryInfoList, Timestamp dateTime) throws RemoteException, SQLException {
        if(machineryPriceTransactionLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                DataObject transactionObject = session.getDataObject(equLM.findClass("MachineryPriceTransaction"), transactionId);
                for (MachineryInfo machineryInfo : machineryInfoList) {
                    ObjectValue machineryObject = null;
                    if (machineryInfo instanceof CashRegisterInfo && cashRegisterLM != null)
                        machineryObject = cashRegisterLM.findProperty("cashRegisterNppGroupCashRegisterNpp").readClasses(session, new DataObject(machineryInfo.numberGroup), new DataObject(machineryInfo.number));
                    else if (machineryInfo instanceof ScalesInfo && scalesLM != null)
                        machineryObject = scalesLM.findProperty("scalesNppGroupScalesNpp").readClasses(session, new DataObject(machineryInfo.numberGroup), new DataObject(machineryInfo.number));
                    if (machineryObject != null && (!(machineryInfo instanceof CashRegisterInfo) || !((CashRegisterInfo) machineryInfo).succeeded)) {
                        boolean alreadySucceeded = machineryPriceTransactionLM.findProperty("succeededMachineryMachineryPriceTransaction").read(session, machineryObject, transactionObject) != null;
                        if(!alreadySucceeded) {
                            machineryPriceTransactionLM.findProperty("succeededMachineryMachineryPriceTransaction").change(true, session,
                                    (DataObject) machineryObject, transactionObject);
                            machineryPriceTransactionLM.findProperty("dateTimeSucceededMachineryMachineryPriceTransaction").change(dateTime, session,
                                    (DataObject) machineryObject, transactionObject);
                        }
                    }
                }
                session.apply(getBusinessLogics());
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public List<byte[][]> readLabelFormats(List<String> scalesModelsList) throws RemoteException, SQLException {
        try (DataSession session = getDbManager().createSession()) {

            List<byte[][]> fileLabelFormats = new ArrayList<>();

            if (scalesLM != null) {

                for (String scalesModel : scalesModelsList) {

                    ObjectValue scalesModelObject = scalesLM.findProperty("scalesModelName").readClasses(session, new DataObject(scalesModel));

                    KeyExpr labelFormatExpr = new KeyExpr("labelFormat");
                    ImRevMap<Object, KeyExpr> labelFormatKeys = MapFact.singletonRev((Object) "labelFormat", labelFormatExpr);
                    QueryBuilder<Object, Object> labelFormatQuery = new QueryBuilder<>(labelFormatKeys);
                    
                    labelFormatQuery.addProperty("fileLabelFormat", scalesLM.findProperty("fileLabelFormat").getExpr(labelFormatExpr));
                    labelFormatQuery.addProperty("fileMessageLabelFormat", scalesLM.findProperty("fileMessageLabelFormat").getExpr(labelFormatExpr));
                    labelFormatQuery.and(scalesLM.findProperty("scalesModelLabelFormat").getExpr(labelFormatExpr).compare(scalesModelObject.getExpr(), Compare.EQUALS));

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> labelFormatResult = labelFormatQuery.execute(session);

                    for (ImMap<Object, Object> row : labelFormatResult.valueIt()) {
                        byte[] fileLabelFormat = (byte[]) row.get("fileLabelFormat");
                        byte[] fileMessageLabelFormat = (byte[]) row.get("fileMessageLabelFormat");
                        fileLabelFormats.add(new byte[][]{fileLabelFormat, fileMessageLabelFormat});
                    }
                }
            }
            return fileLabelFormats;
        } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void errorTransactionReport(Integer transactionID, Throwable e) throws RemoteException, SQLException {
        try (DataSession session = getDbManager().createSession()) {
            DataObject errorObject = session.addObject((ConcreteCustomClass) equLM.findClass("MachineryPriceTransactionError"));
            equLM.findProperty("machineryPriceTransactionMachineryPriceTransactionError").change(transactionID, session, errorObject);
            equLM.findProperty("dataMachineryPriceTransactionError").change(e.toString(), session, errorObject);
            equLM.findProperty("dateMachineryPriceTransactionError").change(getCurrentTimestamp(), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(os));
            equLM.findProperty("errorTraceMachineryPriceTransactionError").change(os.toString(), session, errorObject);

            session.apply(getBusinessLogics());
        } catch (Exception e2) {
            throw Throwables.propagate(e2);
        }
    }

    @Override
    public void errorEquipmentServerReport(String equipmentServer, Throwable exception) throws
            RemoteException, SQLException {
        try (DataSession session = getDbManager().createSession()) {
            DataObject errorObject = session.addObject((ConcreteCustomClass) equLM.findClass("EquipmentServerError"));
            Object equipmentServerObject = equLM.findProperty("sidToEquipmentServer").read(session, new DataObject(equipmentServer, StringClass.get(20)));
            equLM.findProperty("equipmentServerEquipmentServerError").change(equipmentServerObject, session, errorObject);
            equLM.findProperty("dataEquipmentServerError").change(exception.toString(), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            exception.printStackTrace(new PrintStream(os));
            equLM.findProperty("erTraceEquipmentServerError").change(os.toString(), session, errorObject);

            equLM.findProperty("dateEquipmentServerError").change(getCurrentTimestamp(), session, errorObject);

            session.apply(getBusinessLogics());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public EquipmentServerSettings readEquipmentServerSettings(String equipmentServer) throws RemoteException, SQLException {
        try {
            ThreadLocalContext.set(logicsInstance.getContext());
            try (DataSession session = getDbManager().createSession()) {
                ObjectValue equipmentServerObject = equLM.findProperty("sidToEquipmentServer").readClasses(session, new DataObject(equipmentServer));
                if (equipmentServerObject instanceof DataObject) {
                    Integer delay = (Integer) equLM.findProperty("delayEquipmentServer").read(session, equipmentServerObject);
                    Integer numberAtATime = (Integer) equLM.findProperty("numberAtATimeEquipmentServer").read(session, equipmentServerObject);
                    Integer sendSalesDelay = (Integer) equLM.findProperty("sendSalesDelayEquipmentServer").read(session, equipmentServerObject);
                    return new EquipmentServerSettings(delay, numberAtATime, sendSalesDelay);
                } else return null;
            }
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

    private String getRowValue(ImMap<Object, Object> row, String key) {
        return trim((String) row.get(key));
    }

    protected boolean notNullNorEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    protected boolean notNullNorEmpty(List value) {
        return value != null && !value.isEmpty();
    }

    protected boolean notNullNorEmpty(Set value) {
        return value != null && !value.isEmpty();
    }
    
    protected String formatDate(Date date) {
        return new SimpleDateFormat("dd.MM.yyyy").format(date);
    }
    
    protected String formatDateTime(Timestamp date) {
        return new SimpleDateFormat("dd.MM.yyyy hh:mm:ss").format(date);
    }
    
    protected Timestamp getCurrentTimestamp() {
        return DateConverter.dateToStamp(Calendar.getInstance().getTime());
    }
    
    private List<List<Object>> initData(int size) {
        List<List<Object>> data = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<>());
        }
        return data;
    }

    private static Comparator<SalesInfo> COMPARATOR = new Comparator<SalesInfo>() {
        public int compare(SalesInfo o1, SalesInfo o2) {
            int compareCashRegister = BaseUtils.nullCompareTo(o1.nppMachinery, o2.nppMachinery); 
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
