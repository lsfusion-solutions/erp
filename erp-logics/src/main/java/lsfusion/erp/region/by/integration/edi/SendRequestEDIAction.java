package lsfusion.erp.region.by.integration.edi;

import com.google.common.base.Throwables;
import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.server.logics.classes.data.file.DynamicFormatFileClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.apache.http.HttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Iterator;

public class SendRequestEDIAction extends EDIAction {
    protected final ClassPropertyInterface hostInterface;
    protected final ClassPropertyInterface portInterface;
    protected final ClassPropertyInterface loginInterface;
    protected final ClassPropertyInterface passwordInterface;
    protected final ClassPropertyInterface urlInterface;
    protected final ClassPropertyInterface xmlFileInterface;
    protected final ClassPropertyInterface preemptiveAuthenticationInterface;


    public SendRequestEDIAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        hostInterface = i.next();
        portInterface = i.next();
        loginInterface = i.next();
        passwordInterface = i.next();
        urlInterface = i.next();
        xmlFileInterface = i.next();
        preemptiveAuthenticationInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            String host = (String) context.getKeyValue(hostInterface).getValue();
            Integer port = (Integer) context.getKeyValue(portInterface).getValue();
            String login = (String) context.getKeyValue(loginInterface).getValue();
            String password = (String) context.getKeyValue(passwordInterface).getValue();
            String url = (String) context.getKeyValue(urlInterface).getValue();
            String xml = new String(((RawFileData) context.getKeyValue(xmlFileInterface).getValue()).getBytes(), StandardCharsets.UTF_8);
            boolean preemptiveAuthentication = context.getKeyValue(preemptiveAuthenticationInterface).getValue() != null;

            HttpResponse httpResponse = sendRequest(host, port, login, password, url, xml, null, preemptiveAuthentication);

            String responseMessage = getResponseMessage(httpResponse);
            DataObject fileObject = new DataObject(new FileData(new RawFileData(responseMessage.getBytes(StandardCharsets.UTF_8)), "xml"), DynamicFormatFileClass.get());
            findProperty("sendRequestEDIResponse[]").change(fileObject, context);
            findProperty("sendRequestEDIStatus[]").change(httpResponse.getStatusLine().getStatusCode(), context);
        } catch (ScriptingErrorLog.SemanticErrorException | IOException e) {
            throw Throwables.propagate(e);
        }
    }
}