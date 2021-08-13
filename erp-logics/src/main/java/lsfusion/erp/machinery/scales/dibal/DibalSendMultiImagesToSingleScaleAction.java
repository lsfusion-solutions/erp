package lsfusion.erp.machinery.scales.dibal;

import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.util.Iterator;

public class DibalSendMultiImagesToSingleScaleAction extends InternalAction {
    public final ClassPropertyInterface dibalImageFusionPathInterface;
    public final ClassPropertyInterface ipInterface;
    public final ClassPropertyInterface imageDirInterface;
    public final ClassPropertyInterface logsPathInterface;


    public DibalSendMultiImagesToSingleScaleAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        dibalImageFusionPathInterface = i.next();
        ipInterface = i.next();
        imageDirInterface = i.next();
        logsPathInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
        String dibalImageFusionPath = (String) context.getKeyValue(dibalImageFusionPathInterface).getValue();
        String ip = (String) context.getKeyValue(ipInterface).getValue();
        String imageDir = (String) context.getKeyValue(imageDirInterface).getValue();
        String logsPath = (String) context.getKeyValue(logsPathInterface).getValue();

        String result = DibalUtils.sendMultiImagesToSingleScale(dibalImageFusionPath, ip, imageDir, logsPath);
        if(!result.equals("OK")) {
            throw new RuntimeException(String.format("DibalSendMultiImagesToSingleScaleAction %s failed: %s", imageDir, result));
        }
    }

    @Override
    protected boolean allowNulls() {
        return true;
    }
}
