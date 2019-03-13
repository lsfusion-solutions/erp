package lsfusion.erp.integration.image;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class SearchImageArticleActionProperty extends DefaultImageArticleActionProperty {
    private final ClassPropertyInterface articleInterface;

    public SearchImageArticleActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        articleInterface = i.next();

    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataObject articleObject = context.getDataKeyValue(articleInterface);

        resetImages(context);

        loadImages(context, articleObject, 0, 8);
    }

    public void resetImages(ExecutionContext context) throws SQLHandledException {

        try {

            for (int i = 0; i < 64; i++) {

                DataObject currentObject = new DataObject(i);
                findProperty("thumbnailImage[INTEGER]").change((RawFileData) null, context, currentObject);
                findProperty("urlImage[INTEGER]").change((String)null, context, currentObject);
                findProperty("sizeImage[INTEGER]").change((String)null, context, currentObject);
            }

            findProperty("startImage[]").change((Integer)null, context);
            findProperty("articleImage[]").change((Long)null, context);

        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}