package equ.srv;

import com.google.common.base.Throwables;
import equ.api.DeleteBarcodeInfo;
import lsfusion.base.DateConverter;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DBManager;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.*;

class DeleteBarcodeEquipmentServer {

    static List<DeleteBarcodeInfo> readDeleteBarcodeInfo(BusinessLogics BL, DBManager dbManager) throws RemoteException, SQLException {

        Map<String, DeleteBarcodeInfo> barcodeMap = new HashMap<>();
        ScriptingLogicsModule deleteBarcodeLM = BL.getModule("DeleteBarcode");
        if(deleteBarcodeLM != null) {
            try (DataSession session = dbManager.createSession()) {

                KeyExpr deleteBarcodeExpr = new KeyExpr("deleteBarcode");
                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "deleteBarcode", deleteBarcodeExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
                query.addProperty("barcode", deleteBarcodeLM.findProperty("barcode[DeleteBarcode]").getExpr(deleteBarcodeExpr));
                query.addProperty("nppGroupMachinery", deleteBarcodeLM.findProperty("nppGroupMachinery[DeleteBarcode]").getExpr(deleteBarcodeExpr));
                query.addProperty("handlerModelGroupMachinery", deleteBarcodeLM.findProperty("handlerModelGroupMachinery[DeleteBarcode]").getExpr(deleteBarcodeExpr));

                query.and(deleteBarcodeLM.findProperty("barcode[DeleteBarcode]").getExpr(deleteBarcodeExpr).getWhere());
                query.and(deleteBarcodeLM.findProperty("succeeded[DeleteBarcode]").getExpr(deleteBarcodeExpr).getWhere().not());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
                for (ImMap<Object, Object> value : result.values()) {
                    String barcode = (String) value.get("barcode");
                    Integer nppGroupMachinery = (Integer) value.get("nppGroupMachinery");
                    String handlerModelGroupMachinery = (String) value.get("handlerModelGroupMachinery");
                    String key = handlerModelGroupMachinery + "/" + nppGroupMachinery;
                    DeleteBarcodeInfo deleteBarcodeInfo = barcodeMap.get(key);
                    if(deleteBarcodeInfo == null)
                        deleteBarcodeInfo = new DeleteBarcodeInfo(new ArrayList<String>(), nppGroupMachinery, handlerModelGroupMachinery);
                    deleteBarcodeInfo.barcodeList.add(barcode);
                    barcodeMap.put(key, deleteBarcodeInfo);

                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return new ArrayList<>(barcodeMap.values());
    }

    static void errorDeleteBarcodeReport(BusinessLogics BL, DBManager dbManager, ExecutionStack stack, Integer nppGroupMachinery, Exception exception) {
        ScriptingLogicsModule deleteBarcodeLM = BL.getModule("DeleteBarcode");
        try (DataSession session = dbManager.createSession()) {
            DataObject errorObject = session.addObject((ConcreteCustomClass) deleteBarcodeLM.findClass("DeleteBarcodeError"));
            ObjectValue groupMachineryObject = deleteBarcodeLM.findProperty("groupMachineryNpp[INTEGER]").readClasses(session, new DataObject(nppGroupMachinery));
            deleteBarcodeLM.findProperty("groupMachinery[DeleteBarcode]").change(groupMachineryObject.getValue(), session, errorObject);
            deleteBarcodeLM.findProperty("data[DeleteBarcodeError]").change(exception.toString(), session, errorObject);
            deleteBarcodeLM.findProperty("date[DeleteBarcodeError]").change(DateConverter.dateToStamp(Calendar.getInstance().getTime()), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            exception.printStackTrace(new PrintStream(os));
            deleteBarcodeLM.findProperty("errorTrace[DeleteBarcodeError]").change(os.toString(), session, errorObject);

            session.apply(BL, stack);
        } catch (Exception e2) {
            throw Throwables.propagate(e2);
        }
    }

    static void succeedDeleteBarcode(BusinessLogics BL, DBManager dbManager, ExecutionStack stack, Integer nppGroupMachinery) {
        ScriptingLogicsModule deleteBarcodeLM = BL.getModule("DeleteBarcode");
        try (DataSession session = dbManager.createSession()) {
            deleteBarcodeLM.findAction("succeedDeleteBarcode[INTEGER]").execute(session, stack, new DataObject(nppGroupMachinery));
            session.apply(BL, stack);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
