package lsfusion.erp.region.by.integration.edi;

import lsfusion.base.file.RawFileData;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SignEDIHandler {

    static Logger logger;

    static {
        try {
            logger = Logger.getLogger("edilog");
            logger.setLevel(Level.INFO);
            FileAppender fileAppender = new FileAppender(new EnhancedPatternLayout("%d{DATE} %5p %c{1} - %m%n%throwable{1000}"),
                    "logs/edi.log");
            logger.removeAllAppenders();
            logger.addAppender(fileAppender);

        } catch (Exception ignored) {
        }
    }

    public List<Object> sign(List<RawFileData> files, String signerPath, String outputDir, String cert, String password) throws IOException {

        logger.info("EDI: client action sign");

        StringBuilder filesString = new StringBuilder();
        for (int i = 0; i < files.size(); i++) {
            File file = new File("sign" + i + ".xml");
            files.get(i).write(file);
            filesString.append(String.format("%s\"%s\"", filesString.length() == 0 ? "" : " ", file.getAbsolutePath()));
        }

        List<Object> signed = new ArrayList<>();

        String command = String.format("%s\\signnogui.bat -cert %s -pass %s -files %s", signerPath, cert, password, filesString.toString());
        String result = getResultFor(command);

        for (int i = 0; i < files.size(); i++) {

            File file = new File("sign" + i + ".xml");
            try {
                if (result != null) {
                    if (result.contains(String.format("File [%s] was successfully signed", file.getName()))) {
                        logger.info(String.format("EDI: Документ %s был успешно подписан", file.getName()));
                        signed.add(FileUtils.readFileToByteArray(new File(outputDir + "/" + file.getName())));
                    } else {
                        logger.error(String.format("EDI: Документ %s НЕ был подписан. Ответ: %s", file.getName(), result));
                        signed.add(result);
                    }
                } else {
                    logger.error(String.format("EDI: Документ %s НЕ был подписан. Signer вернул нулевой ответ", file.getName()));
                    signed.add(null);
                }
            } finally {
                if (!file.delete()) {
                    file.deleteOnExit();
                }
            }
        }
        return signed;
    }

    private String getResultFor(String command) {
        try {
            Process p = Runtime.getRuntime().exec(command);
            BufferedInputStream in = new BufferedInputStream(p.getInputStream());
            BufferedInputStream err = new BufferedInputStream(p.getErrorStream());
            StringBuilder inS = new StringBuilder();
            byte[] b = new byte[1024];
            while (in.read(b) != -1) {
                inS.append(new String(b, "cp866").trim()).append("\n");
            }
            StringBuilder errS = new StringBuilder();
            b = new byte[1024];
            while (err.read(b) != -1) {
                errS.append(new String(b, "cp866").trim()).append("\n");
            }
            in.close();
            err.close();
            return inS.toString() + errS.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}