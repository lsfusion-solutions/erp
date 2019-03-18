package lsfusion.erp.integration.image;

import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class SearchMoreImageArticleActionProperty extends DefaultImageArticleActionProperty {

    public SearchMoreImageArticleActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

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