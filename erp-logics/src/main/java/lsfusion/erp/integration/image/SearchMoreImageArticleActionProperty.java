package lsfusion.erp.integration.image;

import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class SearchMoreImageArticleActionProperty extends DefaultImageArticleActionProperty {

    public SearchMoreImageArticleActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            ObjectValue articleObject = findProperty("articleImage[]").readClasses(context);
            Integer start = (Integer) findProperty("startImage[]").read(context);

            if (articleObject instanceof DataObject)
                loadImages(context, (DataObject) articleObject, start, 8);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }

    }
}