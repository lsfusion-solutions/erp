package equ.clt;

import equ.api.EquipmentServerInterface;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.sql.SQLException;
import java.util.Set;

public class ProcessMonitorEquipmentServer {
    private final static Logger processMonitorLogger = Logger.getLogger("ProcessMonitorLogger");

    static void process(EquipmentServer equipmentServer, EquipmentServerInterface remote, String sidEquipmentServer) throws SQLException, IOException {
        processMonitorLogger.info("Process ProcessMonitor");
        boolean needUpdateProcessMonitor = remote.needUpdateProcessMonitor(sidEquipmentServer);
        if (needUpdateProcessMonitor) {
            processMonitorLogger.info("LogProcesses called");
            remote.logProcesses(sidEquipmentServer, getData(equipmentServer, Thread.getAllStackTraces().keySet()));
        }
    }

    private static String getData(EquipmentServer equipmentServer, Set<Thread> threads) {
        String result = equipmentServer.taskPool.getTaskInfo();
        if (threads != null && !threads.isEmpty()) {

            long[] threadIds = new long[threads.size()];
            int i = 0;
            for (Thread thread : threads) {
                threadIds[i] = thread.getId();
                i++;
            }
            ThreadInfo[] threadInfos = ManagementFactory.getThreadMXBean().getThreadInfo(threadIds, Integer.MAX_VALUE);

            i = 0;
            StringBuilder data = new StringBuilder();
            for (Thread thread : threads) {
                String processData = getProcessData(thread, threadInfos[i]);
                if (processData != null)
                    data.append(processData);
                i++;
            }
            result += "\n" + data.toString();
        }
        return result;
    }

    private static String getProcessData(Thread thread, ThreadInfo threadInfo) {
        long pid = thread.getId();
        if (!thread.equals(Thread.currentThread())) {
            String status = threadInfo == null ? null : String.valueOf(threadInfo.getThreadState());
            String stackTrace = threadInfo == null ? null : getJavaStack(threadInfo.getStackTrace());
            String name = threadInfo == null ? null : threadInfo.getThreadName();
            return isActiveJavaProcess(status) && stackTrace != null ? String.format("PID: %s, STATUS: %s, NAME: %s\nSTACKTRACE: %s\n", pid, status, name, stackTrace) : null;
        } else return null;
    }

    private static String getJavaStack(StackTraceElement[] stackTrace) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : stackTrace) {
            sb.append(element.toString());
            sb.append("\n");
        }
        String result = sb.toString();
        return result.isEmpty() ? null : result;
    }

    private static boolean isActiveJavaProcess(String status) {
        return status != null && (status.equals("RUNNABLE") || status.equals("BLOCKED"));
    }
}