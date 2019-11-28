package equ.clt.handler.mettlerToledo;

public class MettlerToledoTiger3610Handler extends MettlerToledoTigerHandler {

    @Override
    protected String getLogPrefix() {
        return "MettlerToledo Tiger 3610: ";
    }

    @Override
    protected int getDescriptionLength() {
        return 60; //2 lines
    }
}