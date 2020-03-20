package lsfusion.erp.integration.image;

import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.data.file.ImageClass;
import lsfusion.server.logics.classes.data.time.DateTimeClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Iterator;

import static lsfusion.erp.integration.DefaultIntegrationAction.*;

public class UploadImageArticleFromDirectoryAction extends InternalAction {
    private final ClassPropertyInterface articleInterface;

    public UploadImageArticleFromDirectoryAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        articleInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

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
                        Timestamp timeChangedImageArticle = localDateTimeToSqlTimestamp(getLocalDateTime(findProperty("timeChangedImage[Article]").read(context, articleObject)));
                        if (timeChangedImageArticle == null || timeChangedImageArticle.getTime() != imageFile.lastModified()) {
                            findProperty("image[Article]").change(new DataObject(new RawFileData(new FileInputStream(imageFile)), ImageClass.get()), context, articleObject);
                            findProperty("timeChangedImage[Article]").change(new DataObject(getWriteDateTime(new Timestamp(imageFile.lastModified())), DateTimeClass.instance), context, articleObject);
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | IOException ignored) {
        }


    }
}