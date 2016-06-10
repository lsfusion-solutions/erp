package equ.srv;

import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.remote.RmiServer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class EquipmentServerAspect {


    @Around("execution(* equ.api.EquipmentServerInterface.*(..)) && target(server)")
    public Object executeRemoteMethod(ProceedingJoinPoint thisJoinPoint, Object server) throws Throwable {
        RmiServer rmiServer = (RmiServer) server;

        ThreadLocalContext.aspectBeforeRmi(rmiServer);
        try {
            return thisJoinPoint.proceed();
        } finally {
            ThreadLocalContext.aspectAfterRmi();
        }
    }
}

