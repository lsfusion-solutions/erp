package equ.clt.handler.digi;

import org.springframework.context.support.FileSystemXmlApplicationContext;

public class DigiSM5300Handler extends DigiHandler {

    public DigiSM5300Handler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected Integer getMaxCompositionLinesCount() {
        return null; //has no limits
    }
}