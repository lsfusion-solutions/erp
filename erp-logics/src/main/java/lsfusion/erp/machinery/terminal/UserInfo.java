package lsfusion.erp.machinery.terminal;

import lsfusion.server.data.value.DataObject;

public class UserInfo {
        public DataObject user;
        String idTerminal;
        public String idApplication = "1";

        public UserInfo(DataObject user, String idTerminal) {
            this.user = user;
            this.idTerminal = idTerminal;
        }
    }