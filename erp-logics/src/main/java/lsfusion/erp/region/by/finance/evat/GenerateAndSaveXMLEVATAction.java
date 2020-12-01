package lsfusion.erp.region.by.finance.evat;

import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;

import java.util.Iterator;

public class GenerateAndSaveXMLEVATAction extends GenerateXMLEVATAction {

    private final ClassPropertyInterface evatInterface;

    public GenerateAndSaveXMLEVATAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        evatInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        DataObject evatObject = context.getDataKeyValue(evatInterface);
        generateXML(context, evatObject, false, true);
    }
}