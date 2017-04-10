package equ.srv;

import com.google.common.base.Throwables;
import lsfusion.base.DateConverter;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.DBManager;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Set;

public class StopListEquipmentServer {
    static ScriptingLogicsModule stopListLM;

    public static void init(BusinessLogics BL) {
        stopListLM = BL.getModule("StopList");
    }


    public static void errorStopListReport(BusinessLogics BL, ExecutionStack stack, DBManager dbManager, String numberStopList, Exception e) throws RemoteException, SQLException {
        if(stopListLM != null) {
            try (DataSession session = dbManager.createSession()) {
                DataObject errorObject = session.addObject((ConcreteCustomClass) stopListLM.findClass("StopListError"));
                ObjectValue stopListObject = stopListLM.findProperty("stopList[STRING[18]]").readClasses(session, new DataObject(numberStopList));
                stopListLM.findProperty("stopList[StopListError]").change(stopListObject.getValue(), session, errorObject);
                stopListLM.findProperty("data[StopListError]").change(e.toString(), session, errorObject);
                stopListLM.findProperty("date[StopListError]").change(getCurrentTimestamp(), session, errorObject);
                OutputStream os = new ByteArrayOutputStream();
                e.printStackTrace(new PrintStream(os));
                stopListLM.findProperty("errorTrace[StopListError]").change(os.toString(), session, errorObject);

                session.apply(BL, stack);
            } catch (Exception e2) {
                throw Throwables.propagate(e2);
            }
        }
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

    private static Timestamp getCurrentTimestamp() {
        return DateConverter.dateToStamp(Calendar.getInstance().getTime());
    }

}