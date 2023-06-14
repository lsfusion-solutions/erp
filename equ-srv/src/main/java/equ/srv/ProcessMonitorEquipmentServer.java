package equ.srv;

import com.google.common.base.Throwables;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.action.controller.stack.ExecutionStack;
import lsfusion.server.logics.action.session.DataSession;

public class ProcessMonitorEquipmentServer {

    static ScriptingLogicsModule equLM;

    public static void init(BusinessLogics BL) {
        equLM = BL.getModule("Equipment");
    }

    public static String getProcessMonitorTaskJSON(EquipmentServer server, String sidEquipmentServer) {
        try (DataSession session = server.createSession()) {
            return (String) equLM.findProperty("processMonitorTaskJSON[STRING]").read(session, new DataObject(sidEquipmentServer));
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static void finishProcessMonitorTask(BusinessLogics BL, EquipmentServer server, ExecutionStack stack, String sidEquipmentServer, String json) {
        try (DataSession session = server.createSession()) {
            equLM.findAction("finishProcessMonitorTask[STRING,STRING]").execute(session, stack, new DataObject(sidEquipmentServer), new DataObject(json));
            session.applyException(BL, stack);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

}