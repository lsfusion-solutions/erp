package lsfusion.erp;

import lsfusion.erp.integration.DefaultIntegrationActionProperty;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParseCompositionActionProperty extends DefaultIntegrationActionProperty {

    public ParseCompositionActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) {
        super(LM, valueClass);
    }

    public ParseCompositionActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {       
    }
    
    protected void parseComposition(ExecutionContext context, boolean isItem, DataObject obj, String composition) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if (notNullNorEmpty(composition)) {
            Pattern compositionPattern = Pattern.compile("(?:(\\d+)%(?:\\s)?)(\\D*)");
            Matcher compositionMatcher = compositionPattern.matcher(composition);
            while (compositionMatcher.find()) {
                Integer percent = new Integer(compositionMatcher.group(1));
                String nameMaterial = trim(upper(compositionMatcher.group(2).replace(",", "")));
                ObjectValue materialObject = findProperty("material[VARSTRING[100]]").readClasses(context, new DataObject(nameMaterial));
                if (materialObject instanceof NullValue) {
                    materialObject = context.addObject((ConcreteCustomClass) findClass("Material"));
                    findProperty("name[Material]").change(nameMaterial, context, (DataObject) materialObject);
                    findProperty("id[Material]").change(nameMaterial, context, (DataObject) materialObject);
                }
                findProperty(isItem ? "percent[Material,Item]" : "percent[Material,Article]").change(percent, context, (DataObject) materialObject, obj);
            }
        }
    }

}