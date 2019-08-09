package equ.srv;

import com.google.common.base.Throwables;
import equ.api.DeleteBarcodeInfo;
import equ.api.cashregister.CashRegisterItemInfo;
import lsfusion.base.DateConverter;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.action.controller.stack.ExecutionStack;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.logics.*;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.physics.exec.db.controller.manager.DBManager;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

import static lsfusion.base.BaseUtils.trim;

class DeleteBarcodeEquipmentServer {

    static ScriptingLogicsModule deleteBarcodeLM;

    public static void init(BusinessLogics BL) {
        deleteBarcodeLM = BL.getModule("DeleteBarcode");
    }


    static boolean enabledDeleteBarcodeInfo() {
        return deleteBarcodeLM != null;
    }

    static List<DeleteBarcodeInfo> readDeleteBarcodeInfo(DBManager dbManager, EquipmentServer server) throws SQLException {

        Map<String, DeleteBarcodeInfo> barcodeMap = new HashMap<>();
        if(deleteBarcodeLM != null) {
            try (DataSession session = server.createSession()) {

                KeyExpr deleteBarcodeExpr = new KeyExpr("deleteBarcode");
                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "deleteBarcode", deleteBarcodeExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
                String[] names = new String[]{"barcodeObject", "barcode", "idSku", "nameSku", "idUOMSku", "shortNameUOMSku",
                        "nppGroupMachinery", "handlerModelGroupMachinery", "valueVATSku", "idItemGroup", "nameItemGroup", "directoryGroupMachinery",};
                LP[] properties = deleteBarcodeLM.findProperties("barcodeObject[DeleteBarcode]", "barcode[DeleteBarcode]", "idSku[DeleteBarcode]", "nameSku[DeleteBarcode]",
                        "idUOMSku[DeleteBarcode]", "shortNameUOMSku[DeleteBarcode]",
                        "nppGroupMachinery[DeleteBarcode]", "handlerModelGroupMachinery[DeleteBarcode]",
                        "valueVATSku[DeleteBarcode]", "idItemGroup[DeleteBarcode]", "nameItemGroup[DeleteBarcode]", "directoryGroupMachinery[DeleteBarcode]");
                for (int i = 0; i < properties.length; i++) {
                    query.addProperty(names[i], properties[i].getExpr(deleteBarcodeExpr));
                }

                query.and(deleteBarcodeLM.findProperty("activeGroupMachinery[DeleteBarcode]").getExpr(deleteBarcodeExpr).getWhere());
                query.and(deleteBarcodeLM.findProperty("barcode[DeleteBarcode]").getExpr(deleteBarcodeExpr).getWhere());
                query.and(deleteBarcodeLM.findProperty("succeeded[DeleteBarcode]").getExpr(deleteBarcodeExpr).getWhere().not());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
                for (ImMap<Object, Object> value : result.values()) {
                    Long barcodeObject = (Long) value.get("barcodeObject");
                    String barcode = (String) value.get("barcode");
                    String idSku = (String) value.get("idSku");
                    String name = (String) value.get("nameSku");
                    String idUOM = (String) value.get("idUOMSku");
                    String shortNameUOM = (String) value.get("shortNameUOMSku");
                    Integer nppGroupMachinery = (Integer) value.get("nppGroupMachinery");
                    String handlerModelGroupMachinery = (String) value.get("handlerModelGroupMachinery");
                    BigDecimal valueVAT = (BigDecimal) value.get("valueVATSku");
                    String idItemGroup = (String) value.get("idItemGroup");
                    String nameItemGroup = (String) value.get("nameItemGroup");
                    String key = handlerModelGroupMachinery + "/" + nppGroupMachinery;
                    String directory = trim((String) value.get("directoryGroupMachinery"));
                    DeleteBarcodeInfo deleteBarcodeInfo = barcodeMap.get(key);
                    if(deleteBarcodeInfo == null)
                        deleteBarcodeInfo = new DeleteBarcodeInfo(new ArrayList<>(), nppGroupMachinery,
                                null, handlerModelGroupMachinery, directory);
                    deleteBarcodeInfo.barcodeList.add(new CashRegisterItemInfo(idSku, barcode, name, null, false, null, null,
                            false, valueVAT, null, null, idItemGroup, nameItemGroup, idUOM, shortNameUOM, null, null, null, null, null, null, null,
                            null, null, null, null, null, null, null, null, barcodeObject, null));
                    barcodeMap.put(key, deleteBarcodeInfo);

                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return new ArrayList<>(barcodeMap.values());
    }

    static void errorDeleteBarcodeReport(BusinessLogics BL, DBManager dbManager, EquipmentServer server, ExecutionStack stack, Integer nppGroupMachinery, Exception exception) {
        try (DataSession session = server.createSession()) {
            DataObject errorObject = session.addObject((ConcreteCustomClass) deleteBarcodeLM.findClass("DeleteBarcodeError"));
            ObjectValue groupMachineryObject = deleteBarcodeLM.findProperty("groupMachineryNpp[INTEGER]").readClasses(session, new DataObject(nppGroupMachinery));
            deleteBarcodeLM.findProperty("groupMachinery[DeleteBarcodeError]").change(groupMachineryObject, session, errorObject);
            deleteBarcodeLM.findProperty("data[DeleteBarcodeError]").change(exception.toString(), session, errorObject);
            deleteBarcodeLM.findProperty("date[DeleteBarcodeError]").change(DateConverter.dateToStamp(Calendar.getInstance().getTime()), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            exception.printStackTrace(new PrintStream(os));
            deleteBarcodeLM.findProperty("errorTrace[DeleteBarcodeError]").change(os.toString(), session, errorObject);

            session.applyException(BL, stack);
        } catch (Exception e2) {
            throw Throwables.propagate(e2);
        }
    }

    static void finishDeleteBarcode(BusinessLogics BL, DBManager dbManager, EquipmentServer server, ExecutionStack stack, Integer nppGroupMachinery, boolean markSucceeded) {
        try (DataSession session = server.createSession()) {
            deleteBarcodeLM.findAction("finishDeleteBarcode[INTEGER, BOOLEAN]").execute(session, stack, new DataObject(nppGroupMachinery), markSucceeded ? new DataObject(true) : NullValue.instance);
            session.applyException(BL, stack);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    static void succeedDeleteBarcode(BusinessLogics BL, DBManager dbManager, EquipmentServer server, ExecutionStack stack, Integer nppGroupMachinery, Set<String> deleteBarcodeSet) {
        try (DataSession session = server.createSession()) {
            for (String barcode : deleteBarcodeSet) {
                deleteBarcodeLM.findAction("succeedDeleteBarcode[INTEGER, STRING[28]]").execute(session, stack, new DataObject(nppGroupMachinery), new DataObject(barcode));
            }
            session.applyException(BL, stack);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
