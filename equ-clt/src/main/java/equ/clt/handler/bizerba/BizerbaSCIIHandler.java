package equ.clt.handler.bizerba;

import org.springframework.context.support.FileSystemXmlApplicationContext;

public class BizerbaSCIIHandler extends BizerbaHandler {

    public BizerbaSCIIHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected String getModel() {
        return "bizerbascii";
    }
}
