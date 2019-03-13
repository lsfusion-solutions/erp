package lsfusion.erp.integration.image;

import lsfusion.base.file.RawFileData;
import lsfusion.server.classes.DateTimeClass;
import lsfusion.server.classes.ImageClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Iterator;

public class UploadImageArticleFromDirectoryActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface articleInterface;

    public UploadImageArticleFromDirectoryActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        articleInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataObject articleObject = context.getDataKeyValue(articleInterface);

            String pathImageArticles = (String) findProperty("pathImageArticles[]").read(context);
            pathImageArticles = pathImageArticles == null ? "" : pathImageArticles.trim();
            String idImageArticle = (String) findProperty("idImage[Article]").read(context, articleObject);
            String idArticle = (String) findProperty("id[Article]").read(context, articleObject);
            String idImage = idImageArticle != null ? idImageArticle : idArticle; 
            String idLImage = (idImage == null || idImage.endsWith(".jpg")) ? idImage : (idImage + ".jpg");
            String idUImage = (idImage == null || idImage.endsWith(".JPG")) ? idImage : (idImage + ".JPG"); 
            String subDirectory = pathImageArticles + ((idImageArticle == null || idImageArticle.length() < 3) ? "" : "//" + idImageArticle.substring(0, 3));
            if (idImage != null) {
                if (new File(subDirectory + "//" + idLImage).exists())
                    pathImageArticles = subDirectory;
                if (new File(subDirectory + "//" + idUImage).exists())
                    pathImageArticles = subDirectory;
                if (!pathImageArticles.isEmpty()) {
                    File imageFile = new File(pathImageArticles + "//" + idLImage);
                    if (!imageFile.exists())
                        imageFile = new File(pathImageArticles + "//" + idUImage);
                    if (imageFile.exists()) {
                        Timestamp timeChangedImageArticle = (Timestamp) findProperty("timeChangedImage[Article]").read(context, articleObject);
                        if (timeChangedImageArticle == null || timeChangedImageArticle.getTime() != imageFile.lastModified()) {
                            findProperty("image[Article]").change(new DataObject(new RawFileData(new FileInputStream(imageFile)), ImageClass.get()), context, articleObject);
                            findProperty("timeChangedImage[Article]").change(new DataObject(new Timestamp(imageFile.lastModified()), DateTimeClass.instance), context, articleObject);
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | IOException ignored) {
        }


    }
}