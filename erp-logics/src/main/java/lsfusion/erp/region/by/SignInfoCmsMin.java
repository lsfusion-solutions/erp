package lsfusion.erp.region.by;

import com.google.common.base.Throwables;
import org.bouncycastle.util.encoders.Base64;

import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.util.*;

public class SignInfoCmsMin extends InternalAction {
    private final ClassPropertyInterface fileInterface;
    private final ClassPropertyInterface aliasInterface;
    private final ClassPropertyInterface passwordInterface;
    private final ClassPropertyInterface pathInterface;

    public SignInfoCmsMin(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = getOrderInterfaces().iterator();
        fileInterface = i.next();
        aliasInterface = i.next();
        passwordInterface = i.next();
        pathInterface = i.next();
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        try {
            String dataBase64 = (String) context.getDataKeyValue(fileInterface).getValue();
            byte[] dataBytes = Base64.decode(dataBase64);

            String alias = (String) context.getDataKeyValue(aliasInterface).getValue();
            String password = (String) context.getDataKeyValue(passwordInterface).getValue();
            String path = (String) context.getDataKeyValue(pathInterface).getValue();

            // Подписываем, получаем CMS
            Object result = context.requestUserInteraction(new SignAvestClientAction(dataBytes, alias, password.toCharArray(), path));
            if (result instanceof byte[]) {
                // Возвращаем Base64
                findProperty("rusultSignInfo[]").change(Base64.toBase64String((byte[]) result), context);
            } else {
                throw new RuntimeException((String) result);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}