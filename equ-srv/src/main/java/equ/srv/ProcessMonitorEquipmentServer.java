package equ.srv;

import com.google.common.base.Throwables;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DBManager;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.rmi.RemoteException;
import java.sql.SQLException;

import static equ.srv.EquipmentServer.getCurrentTimestamp;

public class ProcessMonitorEquipmentServer {

    static ScriptingLogicsModule equLM;

    public static void init(BusinessLogics BL) {
        equLM = BL.getModule("Equipment");
    }

    public static boolean needUpdateProcessMonitor(DBManager dbManager, EquipmentServer server, String sidEquipmentServer) throws RemoteException, SQLException {
        try (DataSession session = server.createSession()) {
            return equLM.findProperty("needLogProcesses[VARSTRING[20]]").read(session, new DataObject(sidEquipmentServer)) != null;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static void logProcesses(BusinessLogics BL, DBManager dbManager, EquipmentServer server, ExecutionStack stack, String sidEquipmentServer, String data) throws RemoteException, SQLException {

        try (DataSession session = server.createSession()) {
            ObjectValue equipmentServerObject = equLM.findProperty("sidTo[VARSTRING[20]]").readClasses(session, new DataObject(sidEquipmentServer));
            if(equipmentServerObject instanceof DataObject) {
                DataObject logObject = session.addObject((ConcreteCustomClass) equLM.findClass("EquipmentServerLog"));
                equLM.findProperty("equipmentServer[EquipmentServerLog]").change(equipmentServerObject.getValue(), session, logObject);
                equLM.findProperty("data[EquipmentServerLog]").change(data, session, logObject);
                equLM.findProperty("date[EquipmentServerLog]").change(getCurrentTimestamp(), session, logObject);

                equLM.findProperty("needLogProcesses[EquipmentServer]").change((Object) null, session, (DataObject) equipmentServerObject);
                session.applyException(BL, stack);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

}