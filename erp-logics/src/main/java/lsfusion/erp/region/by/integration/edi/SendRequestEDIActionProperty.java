package lsfusion.erp.region.by.integration.edi;

import com.google.common.base.Throwables;
import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.server.classes.DynamicFormatFileClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.apache.http.HttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Iterator;

public class SendRequestEDIActionProperty extends EDIActionProperty {
    protected final ClassPropertyInterface hostInterface;
    protected final ClassPropertyInterface portInterface;
    protected final ClassPropertyInterface loginInterface;
    protected final ClassPropertyInterface passwordInterface;
    protected final ClassPropertyInterface urlInterface;
    protected final ClassPropertyInterface xmlFileInterface;

    public SendRequestEDIActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        hostInterface = i.next();
        portInterface = i.next();
        loginInterface = i.next();
        passwordInterface = i.next();
        urlInterface = i.next();
        xmlFileInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            String host = (String) context.getDataKeyValue(hostInterface).getValue();
            Integer port = (Integer) context.getDataKeyValue(portInterface).getValue();
            String login = (String) context.getDataKeyValue(loginInterface).getValue();
            String password = (String) context.getDataKeyValue(passwordInterface).getValue();
            String url = (String) context.getDataKeyValue(urlInterface).getValue();
            String xml = new String(((RawFileData) context.getDataKeyValue(xmlFileInterface).getValue()).getBytes(), StandardCharsets.UTF_8);

            HttpResponse httpResponse = sendRequest(host, port, login, password, url, xml, null);

            String responseMessage = getResponseMessage(httpResponse);
            DataObject fileObject = new DataObject(new FileData(new RawFileData(responseMessage.getBytes(StandardCharsets.UTF_8)), "xml"), DynamicFormatFileClass.get());
            findProperty("sendRequestEDIResponse[]").change(fileObject, context);
            findProperty("sendRequestEDIStatus[]").change(httpResponse.getStatusLine().getStatusCode(), context);
        } catch (ScriptingErrorLog.SemanticErrorException | IOException e) {
            throw Throwables.propagate(e);
        }
    }
}