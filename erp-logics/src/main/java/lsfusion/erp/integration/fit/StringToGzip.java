package lsfusion.erp.integration.fit;

import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.io.*;
import java.sql.SQLException;
import java.util.zip.GZIPOutputStream;

public class StringToGzip extends InternalAction {
    public StringToGzip(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            String inputValue = (String) getParam(0, context);
            ByteArrayOutputStream fos = new ByteArrayOutputStream();
            GZIPOutputStream gzipOS = new GZIPOutputStream(fos);
            gzipOS.write(inputValue.getBytes());
            gzipOS.close();
            this.findProperty("resultFile").change(new FileData(new RawFileData(fos.toByteArray()), ""), context);
        } catch (IOException | ScriptingErrorLog.SemanticErrorException e) { }
    }
}