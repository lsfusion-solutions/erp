package lsfusion.erp.integration.image;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.server.logics.classes.data.time.DateTimeClass;
import lsfusion.server.logics.classes.data.file.ImageClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Iterator;

public class UploadImageArticleFromURLActionProperty extends DefaultImageArticleActionProperty {
    private final ClassPropertyInterface articleInterface;

    public UploadImageArticleFromURLActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        articleInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataObject articleObject = context.getDataKeyValue(articleInterface);

            String urlImageArticle = (String) findProperty("urlImage[Article]").read(context, articleObject);
            File imageFile = readImage(urlImageArticle);
            if (imageFile != null) {
                Timestamp timeChangedImageArticle = new Timestamp(Calendar.getInstance().getTime().getTime());
                findProperty("image[Article]").change(new DataObject(new RawFileData(new FileInputStream(imageFile)), ImageClass.get()), context, articleObject);
                findProperty("timeChangedImage[Article]").change(new DataObject(timeChangedImageArticle, DateTimeClass.instance), context, articleObject);
                if(!imageFile.delete())
                    imageFile.deleteOnExit();

            }
        } catch (ScriptingErrorLog.SemanticErrorException | IOException e) {
            throw Throwables.propagate(e);
        }


    }
}