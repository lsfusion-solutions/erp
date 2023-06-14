package equ.clt;

import equ.api.EquipmentServerInterface;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.sql.SQLException;
import java.util.*;

public class ProcessMonitorEquipmentServer {
    private final static Logger processMonitorLogger = Logger.getLogger("ProcessMonitorLogger");

    private static Set<Long> interruptTransactions = new HashSet<>();

    public static boolean notInterrupted() {
        return !Thread.currentThread().isInterrupted();
    }

    public static boolean notInterruptedTransaction(Long transactionId) {
        boolean interrupted = Thread.currentThread().isInterrupted();
        boolean needInterrupt = ProcessMonitorEquipmentServer.interruptTransactions.contains(transactionId);
        if(!interrupted && needInterrupt) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("transaction interrupted");
        }
        return !interrupted && !needInterrupt;
    }

    public static void removeInterruptedTransaction(Long transactionId) {
        interruptTransactions.remove(transactionId);
    }

    static void process(EquipmentServer equipmentServer, EquipmentServerInterface remote, String sidEquipmentServer) throws SQLException, IOException {
        processMonitorLogger.info("Process ProcessMonitor");
        String processMonitorTaskJSON = remote.getProcessMonitorTaskJSON(sidEquipmentServer);

        if(processMonitorTaskJSON != null) {
            JSONObject processMonitorTask = new JSONObject(processMonitorTaskJSON);

            boolean needInterruptTransactions = processMonitorTask.has("interruptTransactions");
            boolean needLogProcesses = processMonitorTask.optBoolean("logProcesses");

            if (needInterruptTransactions) {
                List<String> transactions = Arrays.asList(processMonitorTask.getString("interruptTransactions").split(","));
                processMonitorLogger.info("Interrupt transactions called: " + transactions);
                for(String transaction : transactions) {
                    interruptTransactions.add(Long.parseLong(transaction));
                }
            }

            String logProcessesData = null;
            if (needLogProcesses) {
                processMonitorLogger.info("LogProcesses called");
                logProcessesData = getData(equipmentServer, Thread.getAllStackTraces().keySet());

            }

            if (needInterruptTransactions || needLogProcesses) {
                JSONObject result = new JSONObject();
                result.put("interruptedTransactions", needInterruptTransactions);
                result.put("logProcessesData", logProcessesData);
                remote.finishProcessMonitorTask(sidEquipmentServer, result.toString());
            }
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
            result += "\n" + data;
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