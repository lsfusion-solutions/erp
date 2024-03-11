package lsfusion.erp.machinery.terminal;

import lsfusion.server.data.value.DataObject;

public class UserInfo {
        DataObject user;
        String login;
        String idTerminal;
        String idApplication;

        String idStock;
        public UserInfo(DataObject user, String login, String idTerminal, String idApplication, String idStock) {
            this.user = user;
            this.login = login;
            this.idTerminal = idTerminal;
            this.idApplication = idApplication;
            this.idStock = idStock;
        }
    }