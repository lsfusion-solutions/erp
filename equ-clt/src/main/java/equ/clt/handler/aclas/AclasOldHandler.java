package equ.clt.handler.aclas;

import org.springframework.context.support.FileSystemXmlApplicationContext;

public class AclasOldHandler extends AclasHandler {

    public AclasOldHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected byte getWeightUnit(boolean weightItem) {
        return (byte) (weightItem ? 100 : 10); //10 - значение для штучных товаров не проверялось, взято с потолка
    }
}