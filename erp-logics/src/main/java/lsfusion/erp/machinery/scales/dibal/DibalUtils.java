package lsfusion.erp.machinery.scales.dibal;

import com.google.common.base.Throwables;
import lsfusion.base.file.IOUtils;

import java.io.BufferedInputStream;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class DibalUtils {

    static int masterAddress = 1; //scales number
    static int txPort = 3001;
    static int rxPort = 3000;
    static String model = "LSERIES";
    static String display ="1"; // not in use
    static String section = "1";
    static int group = 50;

    static int imageType = 2; //The type of the image. 1-> Publicity 2-> Item Image.;
    static int assignPlu = 0; //It is used to determine if the image must be associated or not
    static int displaySize = 1; //0-> 7’’(800x480), 1-> 12’’(800x600), 2->15’’(1024x768).
    static int showWindow = 0; //If this value is greater than 0 the window showing the status of the communication is shown.
    static int closeTime = 0; //It determines the time which the communcation window will wait to close after the process ends.

    public static String sendImageToSingleScale(String dibalImageFusionPath, String ip, Integer indexImage, String imagePath, String logsPath) {
        return DibalUtils.sendCommand(String.format("%s sendimagetosinglescale %s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s",
                dibalImageFusionPath, masterAddress, ip, txPort, rxPort, model, display, section, group, trimToEmpty(logsPath), imageType, indexImage, imagePath, assignPlu, displaySize, showWindow, closeTime));
    }

    public static String sendMultiImagesToSingleScale(String dibalImageFusionPath, String ip, String imageDir, String logsPath) {
        return DibalUtils.sendCommand(String.format("%s sendmultiimagestosinglescale %s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s",
                dibalImageFusionPath, masterAddress, ip, txPort, rxPort, model, display, section, group, trimToEmpty(logsPath), imageType, imageDir, assignPlu, displaySize, showWindow, closeTime));
    }

    public static String sendCommand(String command) {
        try {
            Process p = Runtime.getRuntime().exec(command);
            try (BufferedInputStream responseStream = new BufferedInputStream(p.getInputStream())) {
                String result = new String(IOUtils.readBytesFromStream(responseStream));
                return result.replace("\r\n", "");
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

}
