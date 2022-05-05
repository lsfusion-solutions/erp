package lsfusion.erp.machinery.terminal;

import lsfusion.server.data.value.DataObject;

public class UserInfo {
        DataObject user;
        String idTerminal;
        String idApplication;

        public UserInfo(DataObject user, String idTerminal, String idApplication) {
            this.user = user;
            this.idTerminal = idTerminal;
            this.idApplication = idApplication;
        }
    }