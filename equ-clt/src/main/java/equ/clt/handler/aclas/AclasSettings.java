package equ.clt.handler.aclas;

public class AclasSettings {

    //если true, грузим состав. По умолчанию false
    private boolean loadMessages;

    public boolean isLoadMessages() {
        return loadMessages;
    }

    public void setLoadMessages(boolean loadMessages) {
        this.loadMessages = loadMessages;
    }
}