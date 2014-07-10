package lsfusion.erp;

import com.google.common.base.Throwables;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class ParseCompositionArticleActionProperty extends ParseCompositionActionProperty {
    private final ClassPropertyInterface articleInterface;

    public ParseCompositionArticleActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClass("Article"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        articleInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataObject articleObject = context.getDataKeyValue(articleInterface);
            String compositionArticle = trim((String) findProperty("compositionArticle").read(context, articleObject));
            parseComposition(context, false, articleObject, compositionArticle);
            
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}