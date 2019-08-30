package equ.clt.handler.aclas;

import org.springframework.context.support.FileSystemXmlApplicationContext;

public class AclasOldHandler extends AclasHandler {

    public AclasOldHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected String getLogPrefix() {
        return "Aclas old: ";
    }

    @Override
    protected byte getWeightUnit(boolean weightItem) {
        processTransactionLogger.info(getLogPrefix() + "weightUnit " + (weightItem ? 100 : 10));
        return (byte) (weightItem ? 100 : 10); //10 - значение для штучных товаров не проверялось, взято с потолка
    }
}