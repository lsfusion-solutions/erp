package lsfusion.erp.integration;

import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.LogicsModule;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.*;

public class GetTopModulesListActionProperty extends ScriptingActionProperty {

    public GetTopModulesListActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        BusinessLogics<?> BL = context.getBL();

        Map<String, Integer> degree = new HashMap<>();
        for (LogicsModule module : BL.getLogicModules()) {
            degree.put(module.getName(), 0);
        }

        Map<String, Set<String>> graph = BL.buildModuleGraph();

        List<ScriptingLogicsModule> topModulesList = new ArrayList<>();
        int count = 0;
        for (LogicsModule module : BL.getLogicModules()) {
            if (graph.get(module.getName()).size() == 0) {
                topModulesList.add((ScriptingLogicsModule) module);
                count++;
            }
        }

        Collections.sort(topModulesList, new Comparator<ScriptingLogicsModule>() {
            public int compare(ScriptingLogicsModule m1, ScriptingLogicsModule m2) {
                return m1.getPath().compareTo(m2.getPath());
            }
        });

        String result = "";
        for (ScriptingLogicsModule module : topModulesList) {
            result += module.getName() + ", ";
        }

        if (!topModulesList.isEmpty()) {
            System.out.println("Top Modules List:");
            System.out.println(result.substring(0, result.length() - 2));
        }

    }
}