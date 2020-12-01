package lsfusion.erp.integration.image;

import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;

import java.util.Iterator;

public class SearchFirstImageArticleAction extends DefaultImageArticleAction {
    private final ClassPropertyInterface articleInterface;

    public SearchFirstImageArticleAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        articleInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        DataObject articleObject = context.getDataKeyValue(articleInterface);
        loadFirstImage(context, articleObject);
    }
}