package equ.srv;

import com.google.common.base.Throwables;
import equ.api.ItemInfo;
import equ.api.MachineryInfo;
import equ.api.StopListInfo;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.scales.ScalesInfo;
import lsfusion.base.DateConverter;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.classes.ConcreteClass;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DBManager;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.trim;

public class StopListEquipmentServer {
    static ScriptingLogicsModule stopListLM;

    public static void init(BusinessLogics BL) {
        stopListLM = BL.getModule("StopList");
    }

    public static void succeedStopList(BusinessLogics BL, ExecutionStack stack, DBManager dbManager, String numberStopList, Set<String> idStockSet) throws RemoteException, SQLException {
        if(stopListLM != null) {
            try (DataSession session = dbManager.createSession()) {
                DataObject stopListObject = (DataObject) stopListLM.findProperty("stopList[STRING[18]]").readClasses(session, new DataObject(numberStopList));
                for (String idStock : idStockSet) {
                    DataObject stockObject = (DataObject) stopListLM.findProperty("stock[VARSTRING[100]]").readClasses(session, new DataObject(idStock));
                    stopListLM.findProperty("succeeded[Stock,StopList]").change(true, session, stockObject, stopListObject);
                }
                session.apply(BL, stack);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

}