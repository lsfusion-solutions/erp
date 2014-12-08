package equ.srv.actions;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.CashRegisterItemInfo;
import equ.api.cashregister.TransactionCashRegisterInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.api.terminal.*;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.*;
import lsfusion.interop.remote.RMIUtils;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.rmi.ConnectException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Date;
import java.util.*;

public class TransactionExchangeActionProperty extends DefaultIntegrationActionProperty {

    ScriptingLogicsModule itemFashionLM;
    
    public TransactionExchangeActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        String sidEquipmentServer = "1";
        String serverHost = "localhost";
        Integer connectPort = 7652;
        String serverDB = "default";
        EquipmentServerInterface remote;

        try {
            remote = RMIUtils.rmiLookup(serverHost, connectPort, serverDB, "EquipmentServer");
        } catch (ConnectException e) {
            throw Throwables.propagate(e);
        } catch (NoSuchObjectException e) {
            throw Throwables.propagate(e);
        } catch (RemoteException e) {
            throw Throwables.propagate(e);
        } catch (MalformedURLException e) {
            throw Throwables.propagate(e);
        } catch (NotBoundException e) {
            throw Throwables.propagate(e);
        }
        
        if(remote != null) {
            try {
                
                itemFashionLM = context.getBL().getModule("ItemFashion");
                
                readTransactionInfo(context, remote, sidEquipmentServer);
                
                sendReceiptInfo(context, remote, sidEquipmentServer);
                
                
            } catch (RemoteException e) {
                throw Throwables.propagate(e);
            } catch (ScriptingErrorLog.SemanticErrorException e) {
                throw Throwables.propagate(e);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }

    }
    
    private void readTransactionInfo(ExecutionContext context, EquipmentServerInterface remote, String sidEquipmentServer) 
            throws RemoteException, SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        List<TransactionInfo> transactionList = remote.readTransactionInfo(sidEquipmentServer);

        List<List<List<Object>>> transactionData = getTransactionData(transactionList);
        importCashRegisterTransactionList(context, transactionData.get(0));
        importScalesTransactionList(context,  transactionData.get(1));
        importTerminalTransactionList(context, transactionData.get(2));
        importPriceCheckerTransactionList(context, transactionData.get(3));
    }

    private void sendReceiptInfo(ExecutionContext context, EquipmentServerInterface remote, String sidEquipmentServer) 
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, IOException {

        KeyExpr receiptExpr = new KeyExpr("receipt");
        KeyExpr receiptDetailExpr = new KeyExpr("receiptDetail");
        ImRevMap<Object, KeyExpr> receiptKeys = MapFact.toRevMap((Object) "receipt", receiptExpr, "receiptDetail", receiptDetailExpr);
        
        QueryBuilder<Object, Object> receiptQuery = new QueryBuilder<Object, Object>(receiptKeys);
        
        String[] receiptNames = new String[]{"numberZReportReceipt", "numberReceipt", "dateReceipt", "timeReceipt", "discountSumReceipt", "idEmployeeReceipt",
                "firstNameEmployeeReceipt", "lastNameEmployeeReceipt", "numberGroupCashRegisterReceipt", "numberCashRegisterReceipt"};
        LCP<?>[] receiptProperties = findProperties("numberZReportReceipt", "numberReceipt", "dateReceipt", "timeReceipt", "discountSumReceipt", "idEmployeeReceipt",
                "firstNameEmployeeReceipt", "lastNameEmployeeReceipt", "numberGroupCashRegisterReceipt", "numberCashRegisterReceipt");
        for (int j = 0; j < receiptProperties.length; j++) {
            receiptQuery.addProperty(receiptNames[j], receiptProperties[j].getExpr(receiptExpr));
        }

        String[] receiptDetailNames = new String[]{"quantityReceiptDetail", "priceReceiptDetail", "sumReceiptDetail", "discountSumReceiptDetail", 
                "typeReceiptDetail", "numberReceiptDetail", "idSkuReceiptDetail"};
        LCP<?>[] receiptDetailProperties = findProperties("quantityReceiptDetail", "priceReceiptDetail", "sumReceiptDetail", "discountSumReceiptDetail",
                "typeReceiptDetail", "numberReceiptDetail", "idSkuReceiptDetail");
        for (int j = 0; j < receiptDetailProperties.length; j++) {
            receiptQuery.addProperty(receiptDetailNames[j], receiptDetailProperties[j].getExpr(receiptDetailExpr));
        }
        
        receiptQuery.and(findProperty("notExportedIncrementReceipt").getExpr(receiptExpr).getWhere());
        
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> receiptResult = receiptQuery.execute(context);
       
        ArrayList<SalesInfo> salesInfoList = new ArrayList<SalesInfo>();
        for (ImMap<Object, Object> entry : receiptResult.values()) {

            String numberZReport = (String) entry.get("numberZReportReceipt");
            Integer numberReceipt = (Integer) entry.get("numberReceipt");
            Date dateReceipt = (Date) entry.get("dateReceipt");
            Time timeReceipt = (Time) entry.get("timeReceipt");
            BigDecimal discountSumReceipt = (BigDecimal) entry.get("discountSumReceipt");
            String idEmployee = (String) entry.get("idEmployeeReceipt");
            String firstNameContact = (String) entry.get("firstNameEmployeeReceipt");
            String lastNameContact = (String) entry.get("lastNameEmployeeReceipt");
            Integer nppGroupMachinery = (Integer) entry.get("numberGroupCashRegisterReceipt");
            Integer nppMachinery = (Integer) entry.get("numberCashRegisterReceipt");
            
            String barcodeItem = (String) entry.get("idBarcodeSku"); 
            BigDecimal quantityReceiptDetail = (BigDecimal) entry.get("quantityReceiptDetail");
            BigDecimal priceReceiptDetail = (BigDecimal) entry.get("priceReceiptDetail");
            BigDecimal sumReceiptDetail = (BigDecimal) entry.get("sumReceiptDetail");
            BigDecimal discountSumReceiptDetail = (BigDecimal) entry.get("discountSumReceiptDetail");
            Integer numberReceiptDetail = (Integer) entry.get("numberReceiptDetail");
            String typeReceiptDetail = (String) entry.get("typeReceiptDetail");
            boolean isGiftCard = typeReceiptDetail != null && typeReceiptDetail.equals("Сертификат");
            
            salesInfoList.add(new SalesInfo(isGiftCard, nppGroupMachinery, nppMachinery, numberZReport, numberReceipt,
                    dateReceipt, timeReceipt, idEmployee, firstNameContact, lastNameContact, null/*sumCard*/, null/*sumCash*/, null/*sumGiftCard*/,
                    barcodeItem, null/*itemObject*/, quantityReceiptDetail, priceReceiptDetail, sumReceiptDetail, discountSumReceiptDetail,
                    discountSumReceipt, null/*seriesNumberDiscountCard*/, numberReceiptDetail, null/*filename*/));
        }
        
        remote.sendSalesInfo(salesInfoList, sidEquipmentServer, null);
    }
    
    private List<List<List<Object>>> getTransactionData(List<TransactionInfo> transactionList) {
        List<List<Object>> cashRegisterData = new ArrayList<List<Object>>();
        List<List<Object>> scalesData = new ArrayList<List<Object>>();
        List<List<Object>> terminalData = new ArrayList<List<Object>>();
        List<List<Object>> priceCheckerData = new ArrayList<List<Object>>();

        for(TransactionInfo transaction : transactionList) {

            String idTransaction = String.valueOf(transaction.id);
            Date dateTransaction = new Date(transaction.date.getTime());
            Time timeTransaction = dateTransaction == null ? null : new Time(dateTransaction.getTime());
            Map<String, List<lsfusion.erp.integration.ItemGroup>> itemGroupMapTransaction = transaction.itemGroupMap;
            List<ItemInfo> itemsListTransaction = transaction.itemsList;
            Boolean snapshotTransaction = transaction.snapshot;

            if(transaction instanceof TransactionCashRegisterInfo) {
                for(ItemInfo itemInfo : itemsListTransaction) {
                    CashRegisterItemInfo item = (CashRegisterItemInfo) itemInfo;
                    Boolean notPromotionItem = item.notPromotionItem ? true : null;
                    if (itemFashionLM == null)
                        cashRegisterData.add(Arrays.asList((Object) idTransaction, transaction.nppGroupMachinery, dateTransaction, timeTransaction, snapshotTransaction,
                                transaction.comment, item.idItem, item.idBarcode, item.name, item.idBrand, item.nameBrand,
                                item.price, item.splitItem, item.daysExpiry, item.idUOM, item.shortNameUOM, item.passScalesItem, item.vat, notPromotionItem,
                                item.flags, item.idItemGroup, true));
                    else
                        cashRegisterData.add(Arrays.asList((Object) idTransaction, transaction.nppGroupMachinery, dateTransaction, timeTransaction, snapshotTransaction,
                                transaction.comment, item.idItem, item.idBarcode, item.name, item.idBrand, item.nameBrand, item.idSeason, item.nameSeason,
                                item.price, item.splitItem, item.daysExpiry, item.idUOM, item.shortNameUOM, item.passScalesItem, item.vat, notPromotionItem,
                                item.flags, item.idItemGroup, true));
                }
            } else if(transaction instanceof TransactionScalesInfo) {               
                for(ItemInfo itemInfo : itemsListTransaction) {
                    ScalesItemInfo item = (ScalesItemInfo) itemInfo;
                    scalesData.add(Arrays.asList((Object) idTransaction, transaction.nppGroupMachinery, dateTransaction, timeTransaction, snapshotTransaction,
                            transaction.comment, item.idItem, item.idBarcode, item.name, item.price, item.splitItem, item.daysExpiry, item.hoursExpiry, item.expiryDate, 
                            item.labelFormat, item.idItemGroup, true));
                }
            } else if(transaction instanceof TransactionTerminalInfo) {               
                for(ItemInfo item : itemsListTransaction) {
                    terminalData.add(Arrays.asList((Object) idTransaction, transaction.nppGroupMachinery, dateTransaction, timeTransaction, snapshotTransaction,
                            transaction.comment, item.idItem, item.idBarcode, item.name, item.price, item.splitItem, item.daysExpiry, true));
                }
            } else if(transaction instanceof TransactionPriceCheckerInfo) {
                for(ItemInfo item : itemsListTransaction) {
                    priceCheckerData.add(Arrays.asList((Object) idTransaction, transaction.nppGroupMachinery, dateTransaction, timeTransaction, snapshotTransaction,
                            transaction.comment, item.idItem, item.idBarcode, item.name, item.price, item.splitItem, item.daysExpiry, true));
                }
            }
        }
        return Arrays.asList(cashRegisterData, scalesData, terminalData, priceCheckerData);
    }

    private void importCashRegisterTransactionList(ExecutionContext context, List<List<Object>> cashRegisterData) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (notNullNorEmpty(cashRegisterData)) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            ImportField idMachineryPriceTransactionField = new ImportField(findProperty("idMachineryPriceTransaction"));
            ImportKey<?> machineryPriceTransactionKey = new ImportKey((CustomClass) findClass("CashRegisterPriceTransaction"),
                    findProperty("machineryPriceTransactionId").getMapping(idMachineryPriceTransactionField));
            keys.add(machineryPriceTransactionKey);
            props.add(new ImportProperty(idMachineryPriceTransactionField, findProperty("idMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(idMachineryPriceTransactionField);

            ImportField nppGroupMachineryField = new ImportField(findProperty("nppGroupMachinery"));
            ImportKey<?> groupCashRegisterKey = new ImportKey((CustomClass) findClass("GroupCashRegister"),
                    findProperty("groupCashRegisterNpp").getMapping(nppGroupMachineryField));
            groupCashRegisterKey.skipKey = true;
            keys.add(groupCashRegisterKey);
            props.add(new ImportProperty(nppGroupMachineryField, findProperty("groupMachineryMachineryPriceTransaction").getMapping(machineryPriceTransactionKey),
                    object(findClass("GroupCashRegister")).getMapping(groupCashRegisterKey)));
            fields.add(nppGroupMachineryField);

            ImportField dateMachineryPriceTransactionField = new ImportField(findProperty("dateMachineryPriceTransaction"));
            props.add(new ImportProperty(dateMachineryPriceTransactionField, findProperty("dateMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(dateMachineryPriceTransactionField);

            ImportField timeMachineryPriceTransactionField = new ImportField(findProperty("timeMachineryPriceTransaction"));
            props.add(new ImportProperty(timeMachineryPriceTransactionField, findProperty("timeMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(timeMachineryPriceTransactionField);

            ImportField snapshotMachineryPriceTransactionField = new ImportField(findProperty("snapshotMachineryPriceTransaction"));
            props.add(new ImportProperty(snapshotMachineryPriceTransactionField, findProperty("snapshotMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(snapshotMachineryPriceTransactionField);

            ImportField commentMachineryPriceTransactionField = new ImportField(findProperty("commentMachineryPriceTransaction"));
            props.add(new ImportProperty(commentMachineryPriceTransactionField, findProperty("commentMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(commentMachineryPriceTransactionField);

            ImportField idItemField = new ImportField(findProperty("idItem"));
            ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) findClass("Item"),
                    findProperty("itemId").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, findProperty("idItem").getMapping(itemKey)));
            fields.add(idItemField);
            
            ImportField extIdBarcodeField = new ImportField(findProperty("extIdBarcode"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(extIdBarcodeField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idItemField, findProperty("skuBarcode").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("extIdBarcode").getMapping(barcodeKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("idBarcode").getMapping(barcodeKey)));
            fields.add(extIdBarcodeField);

            ImportField captionItemField = new ImportField(findProperty("captionItem"));
            props.add(new ImportProperty(captionItemField, findProperty("captionItem").getMapping(itemKey), true));
            props.add(new ImportProperty(captionItemField, findProperty("nameMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(captionItemField);

            ImportField idBrandField = new ImportField(findProperty("idBrand"));
            ImportKey<?> brandKey = new ImportKey((ConcreteCustomClass) findClass("Brand"),
                    findProperty("brandId").getMapping(idBrandField));
            keys.add(brandKey);
            props.add(new ImportProperty(idBrandField, findProperty("idBrand").getMapping(brandKey)));
            props.add(new ImportProperty(idBrandField, findProperty("brandItem").getMapping(itemKey),
                    object(findClass("Brand")).getMapping(brandKey), true));
            fields.add(idBrandField);

            ImportField nameBrandField = new ImportField(findProperty("nameBrand"));
            props.add(new ImportProperty(nameBrandField, findProperty("nameBrand").getMapping(brandKey), true));
            fields.add(nameBrandField);

            if(itemFashionLM != null) {
                ImportField idSeasonField = new ImportField(itemFashionLM.findProperty("idSeason"));
                ImportKey<?> seasonKey = new ImportKey((ConcreteCustomClass) itemFashionLM.findClass("Season"),
                        itemFashionLM.findProperty("seasonId").getMapping(idSeasonField));
                props.add(new ImportProperty(idSeasonField, itemFashionLM.findProperty("idSeason").getMapping(seasonKey)));
                keys.add(seasonKey);
                props.add(new ImportProperty(idSeasonField, itemFashionLM.findProperty("seasonItem").getMapping(itemKey),
                        object(itemFashionLM.findClass("Season")).getMapping(seasonKey), true));
                fields.add(idSeasonField);

                ImportField nameSeasonField = new ImportField(itemFashionLM.findProperty("nameSeason"));
                props.add(new ImportProperty(nameSeasonField, itemFashionLM.findProperty("nameSeason").getMapping(seasonKey), true));
                fields.add(nameSeasonField);
            }
            
            ImportField priceMachineryPriceTransactionBarcodeField = new ImportField(findProperty("priceMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(priceMachineryPriceTransactionBarcodeField, findProperty("priceMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(priceMachineryPriceTransactionBarcodeField);

            ImportField splitMachineryPriceTransactionBarcodeField = new ImportField(findProperty("splitMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(splitMachineryPriceTransactionBarcodeField, findProperty("splitMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(splitMachineryPriceTransactionBarcodeField);

            ImportField expiryDaysMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDaysMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(expiryDaysMachineryPriceTransactionBarcodeField, findProperty("expiryDaysMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDaysMachineryPriceTransactionBarcodeField);

            ImportField idUOMField = new ImportField(findProperty("idUOM"));
            ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) findClass("UOM"),
                    findProperty("UOMId").getMapping(idUOMField));
            UOMKey.skipKey = true;
            keys.add(UOMKey);
            props.add(new ImportProperty(idUOMField, findProperty("idUOM").getMapping(UOMKey)));
            props.add(new ImportProperty(idUOMField, findProperty("idUOMMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(idUOMField);

            ImportField shortNameUOMField = new ImportField(findProperty("shortNameUOM"));
            props.add(new ImportProperty(shortNameUOMField, findProperty("shortNameUOM").getMapping(UOMKey), true));
            props.add(new ImportProperty(shortNameUOMField, findProperty("shortNameUOMMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(shortNameUOMField);

            ImportField passScalesMachineryPriceTransactionBarcodeField = new ImportField(findProperty("passScalesMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(passScalesMachineryPriceTransactionBarcodeField, findProperty("passScalesMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(passScalesMachineryPriceTransactionBarcodeField);

            DataObject defaultCountryObject = (DataObject) findProperty("defaultCountry").readClasses(context);
            ImportField valueVATItemCountryDateField = new ImportField(findProperty("valueVATItemCountryDate"));
            ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) findClass("Range"),
                    findProperty("valueCurrentVATDefaultValue").getMapping(valueVATItemCountryDateField));
            VATKey.skipKey = true;
            keys.add(VATKey);
            props.add(new ImportProperty(valueVATItemCountryDateField, findProperty("VATItemCountry").getMapping(itemKey, defaultCountryObject),
                    object(findClass("Range")).getMapping(VATKey)));
            fields.add(valueVATItemCountryDateField);

            ImportField notPromotionItemField = new ImportField(findProperty("notPromotionItem"));
            props.add(new ImportProperty(notPromotionItemField, findProperty("notPromotionItem").getMapping(itemKey)));
            fields.add(notPromotionItemField);

            ImportField flagsMachineryPriceTransactionBarcodeField = new ImportField(findProperty("flagsMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(flagsMachineryPriceTransactionBarcodeField, findProperty("flagsMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(flagsMachineryPriceTransactionBarcodeField);

            ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) findClass("ItemGroup"),
                    findProperty("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, findProperty("skuGroupMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey),
                    object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            props.add(new ImportProperty(idItemGroupField, findProperty("itemGroupItem").getMapping(itemKey),
                    object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
                        
            ImportField inMachineryPriceTransactionBarcodeField = new ImportField(findProperty("inMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(inMachineryPriceTransactionBarcodeField, findProperty("inMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(inMachineryPriceTransactionBarcodeField);
            
            ImportTable table = new ImportTable(fields, cashRegisterData);

            DataSession session = context.createSession();
            session.pushVolatileStats("TE_CT");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importScalesTransactionList(ExecutionContext context, List<List<Object>> scalesData) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (notNullNorEmpty(scalesData)) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            
            ImportField idMachineryPriceTransactionField = new ImportField(findProperty("idMachineryPriceTransaction"));
            ImportKey<?> machineryPriceTransactionKey = new ImportKey((CustomClass) findClass("ScalesPriceTransaction"),
                    findProperty("machineryPriceTransactionId").getMapping(idMachineryPriceTransactionField));
            keys.add(machineryPriceTransactionKey);
            props.add(new ImportProperty(idMachineryPriceTransactionField, findProperty("idMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(idMachineryPriceTransactionField);

            ImportField nppGroupMachineryField = new ImportField(findProperty("nppGroupMachinery"));
            ImportKey<?> groupScalesKey = new ImportKey((CustomClass) findClass("GroupScales"),
                    findProperty("groupScalesNpp").getMapping(nppGroupMachineryField));
            groupScalesKey.skipKey = true;
            keys.add(groupScalesKey);
            props.add(new ImportProperty(nppGroupMachineryField, findProperty("groupMachineryMachineryPriceTransaction").getMapping(machineryPriceTransactionKey),
                    object(findClass("GroupScales")).getMapping(groupScalesKey)));
            fields.add(nppGroupMachineryField);

            ImportField dateMachineryPriceTransactionField = new ImportField(findProperty("dateMachineryPriceTransaction"));
            props.add(new ImportProperty(dateMachineryPriceTransactionField, findProperty("dateMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(dateMachineryPriceTransactionField);

            ImportField timeMachineryPriceTransactionField = new ImportField(findProperty("timeMachineryPriceTransaction"));
            props.add(new ImportProperty(timeMachineryPriceTransactionField, findProperty("timeMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(timeMachineryPriceTransactionField);

            ImportField snapshotMachineryPriceTransactionField = new ImportField(findProperty("snapshotMachineryPriceTransaction"));
            props.add(new ImportProperty(snapshotMachineryPriceTransactionField, findProperty("snapshotMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(snapshotMachineryPriceTransactionField);

            ImportField commentMachineryPriceTransactionField = new ImportField(findProperty("commentMachineryPriceTransaction"));
            props.add(new ImportProperty(commentMachineryPriceTransactionField, findProperty("commentMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(commentMachineryPriceTransactionField);

            ImportField idItemField = new ImportField(findProperty("idItem"));
            ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) findClass("Item"),
                    findProperty("itemId").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, findProperty("idItem").getMapping(itemKey)));
            fields.add(idItemField);

            ImportField extIdBarcodeField = new ImportField(findProperty("extIdBarcode"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(extIdBarcodeField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idItemField, findProperty("skuBarcode").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("extIdBarcode").getMapping(barcodeKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("idBarcode").getMapping(barcodeKey)));
            fields.add(extIdBarcodeField);
            
            ImportField captionItemField = new ImportField(findProperty("captionItem"));
            props.add(new ImportProperty(captionItemField, findProperty("captionItem").getMapping(itemKey), true));
            props.add(new ImportProperty(captionItemField, findProperty("nameMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(captionItemField);

            ImportField priceMachineryPriceTransactionBarcodeField = new ImportField(findProperty("priceMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(priceMachineryPriceTransactionBarcodeField, findProperty("priceMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(priceMachineryPriceTransactionBarcodeField);
            
            ImportField splitMachineryPriceTransactionBarcodeField = new ImportField(findProperty("splitMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(splitMachineryPriceTransactionBarcodeField, findProperty("splitMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(splitMachineryPriceTransactionBarcodeField);

            ImportField expiryDaysMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDaysMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(expiryDaysMachineryPriceTransactionBarcodeField, findProperty("expiryDaysMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDaysMachineryPriceTransactionBarcodeField);

            ImportField hoursExpiryMachineryPriceTransactionBarcodeField = new ImportField(findProperty("hoursExpiryMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(hoursExpiryMachineryPriceTransactionBarcodeField, findProperty("hoursExpiryMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(hoursExpiryMachineryPriceTransactionBarcodeField);

            ImportField expiryDateMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDateMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(expiryDateMachineryPriceTransactionBarcodeField, findProperty("expiryDateMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDateMachineryPriceTransactionBarcodeField);

            ImportField labelFormatMachineryPriceTransactionBarcodeField = new ImportField(findProperty("labelFormatMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(labelFormatMachineryPriceTransactionBarcodeField, findProperty("labelFormatMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(labelFormatMachineryPriceTransactionBarcodeField);

            ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) findClass("ItemGroup"),
                    findProperty("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, findProperty("skuGroupMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey),
                    object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            props.add(new ImportProperty(idItemGroupField, findProperty("itemGroupItem").getMapping(itemKey),
                    object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
            
            ImportField inMachineryPriceTransactionBarcodeField = new ImportField(findProperty("inMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(inMachineryPriceTransactionBarcodeField, findProperty("inMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(inMachineryPriceTransactionBarcodeField);

            ImportTable table = new ImportTable(fields, scalesData);

            DataSession session = context.createSession();
            session.pushVolatileStats("TE_CT");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importTerminalTransactionList(ExecutionContext context, List<List<Object>> terminalData) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (notNullNorEmpty(terminalData)) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            ImportField idMachineryPriceTransactionField = new ImportField(findProperty("idMachineryPriceTransaction"));
            ImportKey<?> machineryPriceTransactionKey = new ImportKey((CustomClass) findClass("TerminalPriceTransaction"),
                    findProperty("machineryPriceTransactionId").getMapping(idMachineryPriceTransactionField));
            keys.add(machineryPriceTransactionKey);
            props.add(new ImportProperty(idMachineryPriceTransactionField, findProperty("idMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(idMachineryPriceTransactionField);

            ImportField nppGroupMachineryField = new ImportField(findProperty("nppGroupMachinery"));
            ImportKey<?> groupTerminalKey = new ImportKey((CustomClass) findClass("GroupTerminal"),
                    findProperty("groupTerminalNpp").getMapping(nppGroupMachineryField));
            groupTerminalKey.skipKey = true;
            keys.add(groupTerminalKey);
            props.add(new ImportProperty(nppGroupMachineryField, findProperty("groupMachineryMachineryPriceTransaction").getMapping(machineryPriceTransactionKey),
                    object(findClass("GroupTerminal")).getMapping(groupTerminalKey)));
            fields.add(nppGroupMachineryField);

            ImportField dateMachineryPriceTransactionField = new ImportField(findProperty("dateMachineryPriceTransaction"));
            props.add(new ImportProperty(dateMachineryPriceTransactionField, findProperty("dateMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(dateMachineryPriceTransactionField);

            ImportField timeMachineryPriceTransactionField = new ImportField(findProperty("timeMachineryPriceTransaction"));
            props.add(new ImportProperty(timeMachineryPriceTransactionField, findProperty("timeMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(timeMachineryPriceTransactionField);

            ImportField snapshotMachineryPriceTransactionField = new ImportField(findProperty("snapshotMachineryPriceTransaction"));
            props.add(new ImportProperty(snapshotMachineryPriceTransactionField, findProperty("snapshotMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(snapshotMachineryPriceTransactionField);

            ImportField commentMachineryPriceTransactionField = new ImportField(findProperty("commentMachineryPriceTransaction"));
            props.add(new ImportProperty(commentMachineryPriceTransactionField, findProperty("commentMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(commentMachineryPriceTransactionField);

            ImportField idItemField = new ImportField(findProperty("idItem"));
            ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) findClass("Item"),
                    findProperty("itemId").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, findProperty("idItem").getMapping(itemKey)));
            fields.add(idItemField);
            
            ImportField extIdBarcodeField = new ImportField(findProperty("extIdBarcode"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(extIdBarcodeField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idItemField, findProperty("skuBarcode").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("extIdBarcode").getMapping(barcodeKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("idBarcode").getMapping(barcodeKey)));
            fields.add(extIdBarcodeField);

            ImportField captionItemField = new ImportField(findProperty("captionItem"));
            props.add(new ImportProperty(captionItemField, findProperty("captionItem").getMapping(itemKey), true));
            props.add(new ImportProperty(captionItemField, findProperty("nameMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(captionItemField);

            ImportField splitMachineryPriceTransactionBarcodeField = new ImportField(findProperty("splitMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(splitMachineryPriceTransactionBarcodeField, findProperty("splitMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(splitMachineryPriceTransactionBarcodeField);

            ImportField expiryDaysMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDaysMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(expiryDaysMachineryPriceTransactionBarcodeField, findProperty("expiryDaysMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDaysMachineryPriceTransactionBarcodeField);
            
            ImportField priceMachineryPriceTransactionBarcodeField = new ImportField(findProperty("priceMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(priceMachineryPriceTransactionBarcodeField, findProperty("priceMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(priceMachineryPriceTransactionBarcodeField);
            
            ImportField inMachineryPriceTransactionBarcodeField = new ImportField(findProperty("inMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(inMachineryPriceTransactionBarcodeField, findProperty("inMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(inMachineryPriceTransactionBarcodeField);

            ImportTable table = new ImportTable(fields, terminalData);

            DataSession session = context.createSession();
            session.pushVolatileStats("TE_CT");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importPriceCheckerTransactionList(ExecutionContext context, List<List<Object>> priceCheckerData) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (notNullNorEmpty(priceCheckerData)) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            ImportField idMachineryPriceTransactionField = new ImportField(findProperty("idMachineryPriceTransaction"));
            ImportKey<?> machineryPriceTransactionKey = new ImportKey((CustomClass) findClass("ScalesPriceTransaction"),
                    findProperty("machineryPriceTransactionId").getMapping(idMachineryPriceTransactionField));
            keys.add(machineryPriceTransactionKey);
            props.add(new ImportProperty(idMachineryPriceTransactionField, findProperty("idMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(idMachineryPriceTransactionField);

            ImportField nppGroupMachineryField = new ImportField(findProperty("nppGroupMachinery"));
            ImportKey<?> groupPriceCheckerKey = new ImportKey((CustomClass) findClass("GroupPriceChecker"),
                    findProperty("groupPriceCheckerNpp").getMapping(nppGroupMachineryField));
            groupPriceCheckerKey.skipKey = true;
            keys.add(groupPriceCheckerKey);
            props.add(new ImportProperty(nppGroupMachineryField, findProperty("groupMachineryMachineryPriceTransaction").getMapping(machineryPriceTransactionKey),
                    object(findClass("GroupPriceChecker")).getMapping(groupPriceCheckerKey)));
            fields.add(nppGroupMachineryField);

            ImportField dateMachineryPriceTransactionField = new ImportField(findProperty("dateMachineryPriceTransaction"));
            props.add(new ImportProperty(dateMachineryPriceTransactionField, findProperty("dateMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(dateMachineryPriceTransactionField);

            ImportField timeMachineryPriceTransactionField = new ImportField(findProperty("timeMachineryPriceTransaction"));
            props.add(new ImportProperty(timeMachineryPriceTransactionField, findProperty("timeMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(timeMachineryPriceTransactionField);

            ImportField snapshotMachineryPriceTransactionField = new ImportField(findProperty("snapshotMachineryPriceTransaction"));
            props.add(new ImportProperty(snapshotMachineryPriceTransactionField, findProperty("snapshotMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(snapshotMachineryPriceTransactionField);

            ImportField commentMachineryPriceTransactionField = new ImportField(findProperty("commentMachineryPriceTransaction"));
            props.add(new ImportProperty(commentMachineryPriceTransactionField, findProperty("commentMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(commentMachineryPriceTransactionField);

            ImportField idItemField = new ImportField(findProperty("idItem"));
            ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) findClass("Item"),
                    findProperty("itemId").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, findProperty("idItem").getMapping(itemKey)));
            fields.add(idItemField);
            
            ImportField extIdBarcodeField = new ImportField(findProperty("extIdBarcode"));
            ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(extIdBarcodeField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idItemField, findProperty("skuBarcode").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("extIdBarcode").getMapping(barcodeKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("idBarcode").getMapping(barcodeKey)));
            fields.add(extIdBarcodeField);

            ImportField captionItemField = new ImportField(findProperty("captionItem"));
            props.add(new ImportProperty(captionItemField, findProperty("captionItem").getMapping(itemKey), true));
            props.add(new ImportProperty(captionItemField, findProperty("nameMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(captionItemField);

            ImportField splitMachineryPriceTransactionBarcodeField = new ImportField(findProperty("splitMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(splitMachineryPriceTransactionBarcodeField, findProperty("splitMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(splitMachineryPriceTransactionBarcodeField);

            ImportField expiryDaysMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDaysMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(expiryDaysMachineryPriceTransactionBarcodeField, findProperty("expiryDaysMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDaysMachineryPriceTransactionBarcodeField);
            
            ImportField priceMachineryPriceTransactionBarcodeField = new ImportField(findProperty("priceMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(priceMachineryPriceTransactionBarcodeField, findProperty("priceMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(priceMachineryPriceTransactionBarcodeField);
            
            ImportField inMachineryPriceTransactionBarcodeField = new ImportField(findProperty("inMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(inMachineryPriceTransactionBarcodeField, findProperty("inMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(inMachineryPriceTransactionBarcodeField);
            
            ImportTable table = new ImportTable(fields, priceCheckerData);

            DataSession session = context.createSession();
            session.pushVolatileStats("TE_CT");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }



}

