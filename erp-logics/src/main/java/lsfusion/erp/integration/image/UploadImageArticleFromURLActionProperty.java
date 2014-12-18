package lsfusion.erp.integration.image;

import com.google.common.base.Throwables;
import lsfusion.server.classes.DateTimeClass;
import lsfusion.server.classes.ImageClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.poi.util.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Iterator;

public class UploadImageArticleFromURLActionProperty extends DefaultImageArticleActionProperty {
    private final ClassPropertyInterface articleInterface;

    public UploadImageArticleFromURLActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        articleInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataObject articleObject = context.getDataKeyValue(articleInterface);

            String urlImageArticle = (String) findProperty("urlImageArticle").read(context, articleObject);
            File imageFile = readImage(urlImageArticle);
            if (imageFile != null) {
                Timestamp timeChangedImageArticle = new Timestamp(Calendar.getInstance().getTime().getTime());
                findProperty("imageArticle").change(new DataObject(IOUtils.toByteArray(new FileInputStream(imageFile)), ImageClass.get(false, false)), context, articleObject);
                findProperty("timeChangedImageArticle").change(new DataObject(timeChangedImageArticle, DateTimeClass.instance), context, articleObject);
                imageFile.delete();

            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (FileNotFoundException e) {
            throw Throwables.propagate(e);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }


    }
}