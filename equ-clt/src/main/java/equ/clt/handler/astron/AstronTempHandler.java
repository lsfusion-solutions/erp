package equ.clt.handler.astron;

import org.springframework.context.support.FileSystemXmlApplicationContext;

//TODO: удалить, когда закончится переход
public class AstronTempHandler extends AstronHandler {

    public AstronTempHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected String getSalesNumField() {
        return "SRecNum";
    }

    @Override
    protected String getSalesRefundField() {
        return "SalesRefu";
    }
}