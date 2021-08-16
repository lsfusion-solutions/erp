package lsfusion.erp.machinery.scales.dibal;

import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.util.Iterator;

public class DibalSendImageToSingleScaleAction extends InternalAction {
    public final ClassPropertyInterface dibalImageFusionPathInterface;
    public final ClassPropertyInterface ipInterface;
    public final ClassPropertyInterface indexImageInterface;
    public final ClassPropertyInterface imagePathInterface;
    public final ClassPropertyInterface logsPathInterface;


    public DibalSendImageToSingleScaleAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        dibalImageFusionPathInterface = i.next();
        ipInterface = i.next();
        indexImageInterface = i.next();
        imagePathInterface = i.next();
        logsPathInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {

        String dibalImageFusionPath = (String) context.getKeyValue(dibalImageFusionPathInterface).getValue();
        String ip = (String) context.getKeyValue(ipInterface).getValue();
        Integer indexImage = (Integer) context.getKeyValue(indexImageInterface).getValue();
        String imagePath = (String) context.getKeyValue(imagePathInterface).getValue();
        String logsPath = (String) context.getKeyValue(logsPathInterface).getValue();

        String result = DibalUtils.sendImageToSingleScale(dibalImageFusionPath, ip, indexImage, imagePath, logsPath);
        if(!result.equals("OK")) {
            throw new RuntimeException(String.format("DibalSendImageToSingleScaleAction %s failed: %s", indexImage, result));
        }
    }

    @Override
    protected boolean allowNulls() {
        return true;
    }
}
