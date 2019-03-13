package lsfusion.erp.region.by.finance.evat;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class GenerateAndSaveXMLEVATActionProperty extends GenerateXMLEVATActionProperty {

    private final ClassPropertyInterface evatInterface;

    public GenerateAndSaveXMLEVATActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        evatInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        DataObject evatObject = context.getDataKeyValue(evatInterface);
        generateXML(context, evatObject, false, true);
    }
}