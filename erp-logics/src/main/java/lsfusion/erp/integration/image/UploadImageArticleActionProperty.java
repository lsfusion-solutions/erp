package lsfusion.erp.integration.image;

import lsfusion.server.classes.DateTimeClass;
import lsfusion.server.classes.ImageClass;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.poi.util.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Iterator;

public class UploadImageArticleActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface articleInterface;

    public UploadImageArticleActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Article"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        articleInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {

            DataObject articleObject = context.getDataKeyValue(articleInterface);

            String pathImageArticles = (String) getLCP("pathImageArticles").read(context);
            pathImageArticles = pathImageArticles == null ? "" : pathImageArticles.trim();
            String idImageArticle = (String) getLCP("overIdImageArticle").read(context, articleObject);
            String idArticle = (String) getLCP("idArticle").read(context, articleObject);
            String idImage = idImageArticle != null ? idImageArticle : idArticle; 
            idImage = (idImage == null || idImage.endsWith(".jpg")) ? idImage : (idImage + ".jpg");
            String subDirectory = pathImageArticles + ((idImage == null || idImage.length() < 3) ? "" : "//" + idImage.substring(0, 3));
            if (idImage != null) {
                if (new File(subDirectory + "//" + idImage).exists())
                    pathImageArticles = subDirectory;
                if (!pathImageArticles.isEmpty()) {
                    File imageFile = new File(pathImageArticles + "//" + idImage);
                    if (imageFile.exists()) {
                        Timestamp timeChangedImageArticle = (Timestamp) getLCP("timeChangedImageArticle").read(context, articleObject);
                        if (timeChangedImageArticle == null || timeChangedImageArticle.getTime() != imageFile.lastModified()) {
                            getLCP("imageArticle").change(new DataObject(IOUtils.toByteArray(new FileInputStream(imageFile)), ImageClass.get(false, false)), context, articleObject);
                            getLCP("timeChangedImageArticle").change(new DataObject(new Timestamp(imageFile.lastModified()), DateTimeClass.instance), context, articleObject);
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException ignored) {
        } catch (FileNotFoundException ignored) {
        } catch (IOException ignored) {
        }


    }
}