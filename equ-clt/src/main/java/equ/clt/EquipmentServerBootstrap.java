package equ.clt;

import lsfusion.interop.remote.RMIUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;

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
        
        RMIUtils.initRMI();
        RMIUtils.overrideRMIHostName(serverHost);

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

    public static void stop() {

        logger.info("Server is stopping...");

        if (equ != null) {
            equ.stop();
            equ = null;
        }
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
