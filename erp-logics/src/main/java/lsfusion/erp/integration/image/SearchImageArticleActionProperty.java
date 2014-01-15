package lsfusion.erp.integration.image;

import com.google.common.base.Throwables;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class SearchImageArticleActionProperty extends DefaultImageArticleActionProperty {
    private final ClassPropertyInterface articleInterface;

    public SearchImageArticleActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Article"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        articleInterface = i.next();

    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        DataObject articleObject = context.getDataKeyValue(articleInterface);

        resetImages(context);

        loadImages(context, articleObject, 0, 8);
    }

    public void resetImages(ExecutionContext context) {

        try {

            for (int i = 0; i < 64; i++) {

                DataObject currentObject = new DataObject(i);
                getLCP("thumbnailImage").change((Object) null, context, currentObject);
                getLCP("urlImage").change((Object)null, context, currentObject);
                getLCP("sizeImage").change((Object)null, context, currentObject);
            }

            getLCP("startImage").change((Object)null, context);
            getLCP("articleImage").change((Object)null, context);

        } catch (SQLException e) {
            throw Throwables.propagate(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}