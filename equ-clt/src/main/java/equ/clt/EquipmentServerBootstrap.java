package equ.clt;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;

public class EquipmentServerBootstrap {

    private static FileSystemXmlApplicationContext springContext;
    private static String serverHost;

    public EquipmentServerBootstrap() {
    }

    protected final static Logger logger = Logger.getLogger(EquipmentServerBootstrap.class);

    private static EquipmentServer equ;

    public static void start() throws IOException {
        logger.info("Server is starting...");

        initSpringContext();

        try {
            equ = (EquipmentServer) springContext.getBean("equipmentServer");
            logger.info("Server has successfully started");
        } catch (BeanCreationException bce) {
            logger.info("Exception while starting equipment server: ", bce);
        }
    }

    private static void initSpringContext() {
        springContext = new FileSystemXmlApplicationContext("conf/settings.xml");
        serverHost = (String) springContext.getBean("serverHost");
    }
    
    public static FileSystemXmlApplicationContext getSpringContext() {
        return springContext;
    }

    public static void stop() {

        logger.info("Server is stopping...");

        if (equ != null) {
            equ.stop();
            equ = null;
        }

        Thread dumpThread = new Thread(() -> {
            boolean exit = false;

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                exit = true;
            }

            while (!exit) {
                ThreadInfo[] threadsInfo = ManagementFactory.getThreadMXBean().dumpAllThreads(true, false);
                logger.info("--------------------------Active threads--------------------------");
                int active = 0;
                for (ThreadInfo threadInfo : threadsInfo) {
                    int id = (int) threadInfo.getThreadId();
                    String status = String.valueOf(threadInfo.getThreadState());
                    String name = threadInfo.getThreadName();
                    String lockName = threadInfo.getLockName();
                    String lockOwnerId = String.valueOf(threadInfo.getLockOwnerId());
                    String lockOwnerName = threadInfo.getLockOwnerName();
                    String stackTrace = stackTraceToString(threadInfo.getStackTrace());
                    if (!stackTrace.startsWith("sun.management.ThreadImpl.dumpThreads")) {
                        logger.info(String.format("ID: %s, status: %s, name: %s, lockName: %s, lockOwnerId: %s, lockOwnerName: %s\n%s",
                                id, status, name, lockName, lockOwnerId, lockOwnerName, stackTrace));
                        active++;
                    }
                }
                logger.info("--------------------------Active threads count: " + active + "--------------------------");
                if (active == 0)
                    exit = true;
                else {
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException e) {
                        exit = true;
                    }
                }
            }
        });
        dumpThread.setDaemon(true);
        dumpThread.start();
    }

    private static String stackTraceToString(StackTraceElement[] stackTrace) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : stackTrace) {
            sb.append(element.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    // -------------------------------
    // интерфейс для старта через jsvc
    // -------------------------------

    public static void init(String[] args) {
    }

    public static void destroy() {
    }

    // ----------------------------------
    // интерфейс для старта через procrun
    // ----------------------------------

    public static void start(String[] args) throws IOException {
        start();
    }

    public static void stop(String[] args) {
        stop();
    }

    // -----------------------------
    // интерфейс для обычного старта
    // -----------------------------

    public static void main(String[] args) throws IOException {
        start();
    }


}
