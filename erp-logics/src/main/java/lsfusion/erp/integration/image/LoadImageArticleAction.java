package lsfusion.erp.integration.image;

import lsfusion.base.file.RawFileData;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;

public class LoadImageArticleAction extends DefaultImageArticleAction {
    private final ClassPropertyInterface articleInterface;
    private final ClassPropertyInterface urlInterface;

    public LoadImageArticleAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        articleInterface = i.next();
        urlInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataObject articleObject = context.getDataKeyValue(articleInterface);
        DataObject urlObject = context.getDataKeyValue(urlInterface);

        try {

            File file = readImage((String) urlObject.object);
            if (file != null) {
                findProperty("image[Article]").change(new RawFileData(file), context, articleObject);
                if(!file.delete())
                    file.deleteOnExit();
            }
        } catch (IOException | ScriptingErrorLog.SemanticErrorException e) {
            e.printStackTrace();
        }
    }
}