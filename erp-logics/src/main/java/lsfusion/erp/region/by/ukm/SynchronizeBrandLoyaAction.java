package lsfusion.erp.region.by.ukm;

import lsfusion.erp.ERPLoggers;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;

import java.util.Iterator;

public class SynchronizeBrandLoyaAction extends SynchronizeLoyaAction {
    private final ClassPropertyInterface brandInterface;

    public SynchronizeBrandLoyaAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        brandInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        try {

            DataObject brandObject = context.getDataKeyValue(brandInterface);
            Integer id = (Integer) findProperty("idLoya[Brand]").read(context, brandObject);
            String name = (String) findProperty("name[Brand]").read(context, brandObject);
            Brand brand = new Brand(id, name, brandObject);

            settings = login(context, false);
            if (settings.error == null) {
                String result = uploadBrand(context, brand, false);
                if(authenticationFailed(result)) {
                    settings = login(context, true);
                    if(settings.error == null) {
                        uploadBrand(context, brand, true);
                    }
                }
            } else messageClientAction(context, settings.error, failCaption);
        } catch (Exception e) {
            ERPLoggers.importLogger.error(failCaption, e);
            messageClientAction(context, e.getMessage(), failCaption);
        }
    }
}