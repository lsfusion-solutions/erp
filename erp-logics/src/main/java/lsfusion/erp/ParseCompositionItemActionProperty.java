package lsfusion.erp;

import lsfusion.erp.integration.DefaultIntegrationActionProperty;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParseCompositionItemActionProperty extends DefaultIntegrationActionProperty {
    private final ClassPropertyInterface itemInterface;

    public ParseCompositionItemActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Item"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        itemInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataObject itemObject = context.getDataKeyValue(itemInterface);

            String compositionItem = trim((String) getLCP("compositionItem").read(context, itemObject));

            if (notNullNorEmpty(compositionItem)) {
                Pattern compositionPattern = Pattern.compile("(?:(\\d+)%(?:\\s)?)(\\D*)");
                Matcher compositionMatcher = compositionPattern.matcher(compositionItem);
                while (compositionMatcher.find()) {
                    Integer percent = new Integer(compositionMatcher.group(1));
                    String nameMaterial = trim(upper(compositionMatcher.group(2).replace(",", "")));
                    ObjectValue materialObject = getLCP("materialName").readClasses(context, new DataObject(nameMaterial));
                    if (materialObject instanceof NullValue) {
                        materialObject = context.addObject((ConcreteCustomClass) getClass("Material"));
                        getLCP("nameMaterial").change(nameMaterial, context, (DataObject) materialObject);
                    }
                    getLCP("percentMaterialItem").change(percent, context, (DataObject) materialObject, itemObject);
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }

}