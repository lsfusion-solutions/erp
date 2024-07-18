Date.prototype.yyyymmdd = function () {
    var yyyy = this.getFullYear().toString();
    var mm = (this.getMonth() + 1).toString(); // getMonth() is zero-based
    var dd = this.getDate().toString();
    return yyyy + (mm[1] ? mm : "0" + mm[0]) + (dd[1] ? dd : "0" + dd[0]); // padding
};
Date.prototype.ddmmyyyy = function () {
    var yyyy = this.getFullYear().toString();
    var mm = (this.getMonth() + 1).toString(); // getMonth() is zero-based
    var dd = this.getDate().toString();
    return (dd[1] ? dd : "0" + dd[0]) + "." + (mm[1] ? mm : "0" + mm[0]) + "." + yyyy; // padding
};
var dates = {
    convert: function (d) {
        // Converts the date in d to a date-object. The input can be:
        //   a date object: returned without modification
        //  an array      : Interpreted as [year,month,day]. NOTE: month is 0-11.
        //   a number     : Interpreted as number of milliseconds
        //                  since 1 Jan 1970 (a timestamp)
        //   a string     : Any format supported by the javascript engine, like
        //                  "YYYY/MM/DD", "MM/DD/YYYY", "Jan 31 2009" etc.
        //  an object     : Interpreted as an object with year, month and date
        //                  attributes.  **NOTE** month is 0-11.
        return (
            d.constructor === Date ? d :
                d.constructor === Array ? new Date(d[0], d[1], d[2]) :
                    d.constructor === Number ? new Date(d) :
                        d.constructor === String ? new Date(d) :
                            typeof d === "object" ? new Date(d.year, d.month, d.date) :
                                NaN
        );
    },
    compare: function (a, b) {
        // Compare two dates (could be of any type supported by the convert
        // function above) and returns:
        //  -1 : if a < b
        //   0 : if a = b
        //   1 : if a > b
        // NaN : if a or b is an illegal date
        // NOTE: The code inside isFinite does an assignment (=).
        return (
            isFinite(a = this.convert(a).valueOf()) &&
                isFinite(b = this.convert(b).valueOf()) ?
                (a > b) - (a < b) :
                NaN
        );
    },
    inRange: function (d, start, end) {
        // Checks if date in d is between dates in start and end.
        // Returns a boolean or NaN:
        //    true  : if d is between start and end (inclusive)
        //    false : if d is before start or after end
        //    NaN   : if one or more of the dates is illegal.
        // NOTE: The code inside isFinite does an assignment (=).
        return (
            isFinite(d = this.convert(d).valueOf()) &&
                isFinite(start = this.convert(start).valueOf()) &&
                isFinite(end = this.convert(end).valueOf()) ?
                start <= d && d <= end :
                NaN
        );
    }
};
String.prototype.splitKeep = function (splitter, ahead) {
    var self = this;
    var result = [];
    if (splitter != '') {
        // Substitution of matched string
        function getSubst(value) {
            var substChar = value[0] == '0' ? '1' : '0';
            var subst = '';
            for (var i = 0; i < value.length; i++) {
                subst += substChar;
            }
            return subst;
        };
        var matches = [];
        // Getting mached value and its index
        var replaceName = splitter instanceof RegExp ? "replace" : "replaceAll";
        var r = self[replaceName](splitter, function (m, i, e) {
            matches.push({ value: m, index: i });
            return getSubst(m);
        });
        // Finds split substrings
        var lastIndex = 0;
        for (var i = 0; i < matches.length; i++) {
            var m = matches[i];
            var nextIndex = ahead == true ? m.index : m.index + m.value.length;
            if (nextIndex != lastIndex) {
                var part = self.substring(lastIndex, nextIndex);
                result.push(part);
                lastIndex = nextIndex;
            }
        };
        if (lastIndex < self.length) {
            var part = self.substring(lastIndex, self.length);
            result.push(part);
        };
    } else {
        result.add(self);
    };
    return result;
};

var EIMZOClient = {
    NEW_API: false,
    API_KEYS: [
        'localhost', '96D0C1491615C82B9A54D9989779DF825B690748224C2B04F500F370D51827CE2644D8D4A82C18184D73AB8530BB8ED537269603F61DB0D03D2104ABF789970B',
        '127.0.0.1', 'A7BCFA5D490B351BE0754130DF03A068F855DB4333D43921125B9CF2670EF6A40370C646B90401955E1F7BC9CDBF59CE0B2C5467D820BE189C845D0B79CFC96F',
        'null', 'E0A205EC4E7B78BBB56AFF83A733A1BB9FD39D562E67978CC5E7D73B0951DB1954595A20672A63332535E13CC6EC1E1FC8857BB09E0855D7E76E411B6FA16E9D'
    ],
    checkVersion: function (success, fail) {
        CAPIWS.version(function (event, data) {
            if (data.success === true) {
                if (data.major && data.minor) {
                    var installedVersion = parseInt(data.major) * 100 + parseInt(data.minor);
                    EIMZOClient.NEW_API = installedVersion >= 336;
                    success(data.major, data.minor);
                } else {
                    fail(null, 'E-IMZO Version is undefined');
                }
            } else {
                fail(null, data.reason);
            }
        }, function (e) {
            fail(e, null);
        });
    },
    installApiKeys: function (success, fail) {
        CAPIWS.apikey(EIMZOClient.API_KEYS, function (event, data) {
            if (data.success) {
                success();
            } else {
                fail(null, data.reason);
            }
        }, function (e) {
            fail(e, null);
        });
    },
    listAllUserKeys: function (itemIdGen, itemUiGen, success, fail) {
        var items = [];
        var errors = [];
        if (!EIMZOClient.NEW_API) {
            EIMZOClient._findCertKeys(itemIdGen, itemUiGen, items, errors, function (firstItmId) {
                EIMZOClient._findPfxs(itemIdGen, itemUiGen, items, errors, function (firstItmId2) {
                    if (items.length === 0 && errors.length > 0) {
                        fail(errors[0].e, errors[0].r);
                    } else {
                        var firstId = null;
                        if (items.length === 1) {
                            if (firstItmId) {
                                firstId = firstItmId;
                            } else if (firstItmId2) {
                                firstId = firstItmId2;
                            }
                        }
                        success(items, firstId);
                    }
                });
            });
        } else {
            EIMZOClient._findCertKeys2(itemIdGen, itemUiGen, items, errors, function (firstItmId) {
                EIMZOClient._findPfxs2(itemIdGen, itemUiGen, items, errors, function (firstItmId2) {
                    EIMZOClient._findTokens2(itemIdGen, itemUiGen, items, errors, function (firstItmId3) {
                        if (items.length === 0 && errors.length > 0) {
                            fail(errors[0].e, errors[0].r);
                        } else {
                            var firstId = null;
                            if (items.length === 1) {
                                if (firstItmId) {
                                    firstId = firstItmId;
                                } else if (firstItmId2) {
                                    firstId = firstItmId2;
                                } else if (firstItmId3) {
                                    firstId = firstItmId3;
                                }
                            }
                            success(items, firstId);
                        }
                    });
                });
            });
        }
    },
    loadKey: function (itemObject, success, fail, verifyPassword) {
        if (itemObject) {
            var vo = itemObject;
            if (vo.type === "certkey") {
                CAPIWS.callFunction({ plugin: "certkey", name: "load_key", arguments: [vo.disk, vo.path, vo.name, vo.serialNumber] }, function (event, data) {
                    if (data.success) {
                        var id = data.keyId;
                        success(id);
                    } else {
                        fail(null, data.reason);
                    }
                }, function (e) {
                    fail(e, null);
                });
            } else if (vo.type === "pfx") {
                CAPIWS.callFunction({ plugin: "pfx", name: "load_key", arguments: [vo.disk, vo.path, vo.name, vo.alias] }, function (event, data) {
                    if (data.success) {
                        var id = data.keyId;
                        if (verifyPassword) {
                            CAPIWS.callFunction({ name: "verify_password", plugin: "pfx", arguments: [id] }, function (event, data) {
                                if (data.success) {
                                    success(id);
                                } else {
                                    fail(null, data.reason);
                                }
                            }, function (e) {
                                fail(e, null);
                            });
                        } else {
                            success(id);
                        }
                    } else {
                        fail(null, data.reason);
                    }
                }, function (e) {
                    fail(e, null);
                });
            } else if (vo.type === "ftjc") {
                CAPIWS.callFunction({ plugin: "ftjc", name: "load_key", arguments: [vo.cardUID] }, function (event, data) {
                    if (data.success) {
                        var id = data.keyId;
                        if (verifyPassword) {
                            CAPIWS.callFunction({ plugin: "ftjc", name: "verify_pin", arguments: [id, '1'] }, function (event, data) {
                                if (data.success) {
                                    success(id);
                                } else {
                                    fail(null, data.reason);
                                }
                            }, function (e) {
                                fail(e, null);
                            });
                        } else {
                            success(id);
                        }
                    } else {
                        fail(null, data.reason);
                    }
                }, function (e) {
                    fail(e, null);
                });
            }
        }
    },
    changeKeyPassword: function (itemObject, success, fail) {
        if (itemObject) {
            var vo = itemObject;
            if (vo.type === "pfx") {
                CAPIWS.callFunction({ plugin: "pfx", name: "load_key", arguments: [vo.disk, vo.path, vo.name, vo.alias] }, function (event, data) {
                    if (data.success) {
                        var id = data.keyId;
                        CAPIWS.callFunction({ name: "change_password", plugin: "pfx", arguments: [id] }, function (event, data) {
                            if (data.success) {
                                success();
                            } else {
                                fail(null, data.reason);
                            }
                        }, function (e) {
                            fail(e, null);
                        });
                    } else {
                        fail(null, data.reason);
                    }
                }, function (e) {
                    fail(e, null);
                });
            } else if (vo.type === "ftjc") {
                CAPIWS.callFunction({ plugin: "ftjc", name: "load_key", arguments: [vo.cardUID] }, function (event, data) {
                    if (data.success) {
                        var id = data.keyId;
                        CAPIWS.callFunction({ name: "change_pin", plugin: "ftjc", arguments: [id, '1'] }, function (event, data) {
                            if (data.success) {
                                success();
                            } else {
                                fail(null, data.reason);
                            }
                        }, function (e) {
                            fail(e, null);
                        });
                    } else {
                        fail(null, data.reason);
                    }
                }, function (e) {
                    fail(e, null);
                });
            }
        }
    },
    createPkcs7: function (id, data, timestamper, success, fail) {
        CAPIWS.callFunction({ plugin: "pkcs7", name: "create_pkcs7", arguments: [Base64.encode(data), id, 'no'] }, function (event, data) {
            if (data.success) {
                var pkcs7 = data.pkcs7_64;
                if (timestamper) {
                    var sn = data.signer_serial_number;
                    timestamper(data.signature_hex, function (tst) {
                        CAPIWS.callFunction({ plugin: "pkcs7", name: "attach_timestamp_token_pkcs7", arguments: [pkcs7, sn, tst] }, function (event, data) {
                            if (data.success) {
                                var pkcs7tst = data.pkcs7_64;
                                success(pkcs7tst);
                            } else {
                                fail(null, data.reason);
                            }
                        }, function (e) {
                            fail(e, null);
                        });
                    }, fail);
                } else {
                    success(pkcs7);
                }
            } else {
                fail(null, data.reason);
            }
        }, function (e) {
            fail(e, null);
        });
    },
    appendPkcs7Attached: function (id, data, timestamper, success, fail) {
        CAPIWS.callFunction({ plugin: "pkcs7", name: "append_pkcs7_attached", arguments: [data, id] }, function (event, data) {
            if (data.success) {
                var pkcs7 = data.pkcs7_64;
                if (timestamper) {
                    var sn = data.signer_serial_number;
                    timestamper(data.signature_hex, function (tst) {
                        CAPIWS.callFunction({ plugin: "pkcs7", name: "attach_timestamp_token_pkcs7", arguments: [pkcs7, sn, tst] }, function (event, data) {
                            if (data.success) {
                                var pkcs7tst = data.pkcs7_64;
                                success(pkcs7tst);
                            } else {
                                fail(null, data.reason);
                            }
                        }, function (e) {
                            fail(e, null);
                        });
                    }, fail);
                } else {
                    success(pkcs7);
                }
            } else {
                fail(null, data.reason);
            }
        }, function (e) {
            fail(e, null);
        });
    },
    _getX500Val: function (s, f) {
        var res = s.splitKeep(/,[A-Z]+=/g, true);
        for (var i in res) {
            var n = res[i].search((i > 0 ? "," : "") + f + "=");
            if (n !== -1) {
                return res[i].slice(n + f.length + 1 + (i > 0 ? 1 : 0));
            }
        }
        return "";
    },
    _findCertKeyCertificates: function (itemIdGen, itemUiGen, items, errors, allDisks, diskIndex, params, callback) {
        if (parseInt(diskIndex) + 1 > allDisks.length) {
            callback(params);
            return;
        }
        CAPIWS.callFunction({ plugin: "certkey", name: "list_certificates", arguments: [allDisks[diskIndex]] }, function (event, data) {
            if (data.success) {
                for (var rec in data.certificates) {
                    var el = data.certificates[rec];
                    var vo = {
                        disk: el.disk,
                        path: el.path,
                        name: el.name,
                        serialNumber: el.serialNumber,
                        subjectName: el.subjectName,
                        validFrom: new Date(el.validFrom),
                        validTo: new Date(el.validTo),
                        issuerName: el.issuerName,
                        publicKeyAlgName: el.publicKeyAlgName,
                        CN: EIMZOClient._getX500Val(el.subjectName, "CN"),
                        TIN: EIMZOClient._getX500Val(el.subjectName, "INITIALS"),
                        O: EIMZOClient._getX500Val(el.subjectName, "O"),
                        T: EIMZOClient._getX500Val(el.subjectName, "T"),
                        type: 'certkey'
                    };
                    if (!vo.TIN)
                        continue;
                    var itmkey = itemIdGen(vo, rec);
                    if (params.length === 0) {
                        params.push(itmkey);
                    }
                    var itm = itemUiGen(itmkey, vo);
                    items.push(itm);
                }
            } else {
                errors.push({ r: data.reason });
            }
            EIMZOClient._findCertKeyCertificates(itemIdGen, itemUiGen, items, errors, allDisks, parseInt(diskIndex) + 1, params, callback);
        }, function (e) {
            errors.push({ e: e });
            EIMZOClient._findCertKeyCertificates(itemIdGen, itemUiGen, items, errors, allDisks, parseInt(diskIndex) + 1, params, callback);
        });
    },
    _findCertKeys: function (itemIdGen, itemUiGen, items, errors, callback) {
        var allDisks = [];
        CAPIWS.callFunction({ plugin: "certkey", name: "list_disks" }, function (event, data) {
            if (data.success) {
                for (var rec in data.disks) {
                    allDisks.push(data.disks[rec]);
                    if (parseInt(rec) + 1 >= data.disks.length) {
                        var params = [];
                        EIMZOClient._findCertKeyCertificates(itemIdGen, itemUiGen, items, errors, allDisks, 0, params, function (params) {
                            callback(params[0]);
                        });
                    }
                }
            } else {
                errors.push({ r: data.reason });
            }
        }, function (e) {
            errors.push({ e: e });
            callback();
        });
    },
    _findPfxCertificates: function (itemIdGen, itemUiGen, items, errors, allDisks, diskIndex, params, callback) {
        if (parseInt(diskIndex) + 1 > allDisks.length) {
            callback(params);
            return;
        }
        CAPIWS.callFunction({ plugin: "pfx", name: "list_certificates", arguments: [allDisks[diskIndex]] }, function (event, data) {
            if (data.success) {
                for (var rec in data.certificates) {
                    var el = data.certificates[rec];
                    var x500name_ex = el.alias.toUpperCase();
                    x500name_ex = x500name_ex.replace("1.2.860.3.16.1.1=", "INN=");
                    x500name_ex = x500name_ex.replace("1.2.860.3.16.1.2=", "PINFL=");
                    var vo = {
                        disk: el.disk,
                        path: el.path,
                        name: el.name,
                        alias: el.alias,
                        serialNumber: EIMZOClient._getX500Val(x500name_ex, "SERIALNUMBER"),
                        validFrom: new Date(EIMZOClient._getX500Val(x500name_ex, "VALIDFROM").replace(/\./g, "-").replace(" ", "T")),
                        validTo: new Date(EIMZOClient._getX500Val(x500name_ex, "VALIDTO").replace(/\./g, "-").replace(" ", "T")),
                        CN: EIMZOClient._getX500Val(x500name_ex, "CN"),
                        TIN: (EIMZOClient._getX500Val(x500name_ex, "INN") ? EIMZOClient._getX500Val(x500name_ex, "INN") : (EIMZOClient._getX500Val(x500name_ex, "UID") ? EIMZOClient._getX500Val(x500name_ex, "UID") : EIMZOClient._getX500Val(x500name_ex, "PINFL"))),
                        UID: (EIMZOClient._getX500Val(x500name_ex, "UID") ? EIMZOClient._getX500Val(x500name_ex, "UID") : EIMZOClient._getX500Val(x500name_ex, "PINFL")),
                        O: EIMZOClient._getX500Val(x500name_ex, "O"),
                        T: EIMZOClient._getX500Val(x500name_ex, "T"),
                        type: 'pfx'
                    };
                    if (!vo.TIN)
                        continue;
                    var itmkey = itemIdGen(vo, rec);
                    if (params.length === 0) {
                        params.push(itmkey);
                    }
                    var itm = itemUiGen(itmkey, vo);
                    items.push(itm);
                }
            } else {
                errors.push({ r: data.reason });
            }
            EIMZOClient._findPfxCertificates(itemIdGen, itemUiGen, items, errors, allDisks, parseInt(diskIndex) + 1, params, callback);
        }, function (e) {
            errors.push({ e: e });
            EIMZOClient._findPfxCertificates(itemIdGen, itemUiGen, items, errors, allDisks, parseInt(diskIndex) + 1, params, callback);
        });
    },
    _findPfxs: function (itemIdGen, itemUiGen, items, errors, callback) {
        var allDisks = [];
        CAPIWS.callFunction({ plugin: "pfx", name: "list_disks" }, function (event, data) {
            if (data.success) {
                var disks = data.disks;
                for (var rec in disks) {
                    allDisks.push(data.disks[rec]);
                    if (parseInt(rec) + 1 >= data.disks.length) {
                        var params = [];
                        EIMZOClient._findPfxCertificates(itemIdGen, itemUiGen, items, errors, allDisks, 0, params, function (params) {
                            callback(params[0]);
                        });
                    }
                }
            } else {
                errors.push({ r: data.reason });
            }
        }, function (e) {
            errors.push({ e: e });
            callback();
        });
    },
    _findCertKeys2: function (itemIdGen, itemUiGen, items, errors, callback) {
        var itmkey0;
        CAPIWS.callFunction({ plugin: "certkey", name: "list_all_certificates" }, function (event, data) {
            if (data.success) {
                for (var rec in data.certificates) {
                    var el = data.certificates[rec];
                    var vo = {
                        disk: el.disk,
                        path: el.path,
                        name: el.name,
                        serialNumber: el.serialNumber,
                        subjectName: el.subjectName,
                        validFrom: new Date(el.validFrom),
                        validTo: new Date(el.validTo),
                        issuerName: el.issuerName,
                        publicKeyAlgName: el.publicKeyAlgName,
                        CN: EIMZOClient._getX500Val(el.subjectName, "CN"),
                        TIN: EIMZOClient._getX500Val(el.subjectName, "INITIALS"),
                        O: EIMZOClient._getX500Val(el.subjectName, "O"),
                        T: EIMZOClient._getX500Val(el.subjectName, "T"),
                        type: 'certkey'
                    };
                    if (!vo.TIN)
                        continue;
                    var itmkey = itemIdGen(vo, rec);
                    if (!itmkey0) {
                        itmkey0 = itmkey;
                    }
                    var itm = itemUiGen(itmkey, vo);
                    items.push(itm);
                }
            } else {
                errors.push({ r: data.reason });
            }
            callback(itmkey0);
        }, function (e) {
            errors.push({ e: e });
            callback(itmkey0);
        });
    },
    _findPfxs2: function (itemIdGen, itemUiGen, items, errors, callback) {
        var itmkey0;
        CAPIWS.callFunction({ plugin: "pfx", name: "list_all_certificates" }, function (event, data) {
            if (data.success) {
                for (var rec in data.certificates) {
                    var el = data.certificates[rec];
                    var x500name_ex = el.alias.toUpperCase();
                    x500name_ex = x500name_ex.replace("1.2.860.3.16.1.1=", "INN=");
                    x500name_ex = x500name_ex.replace("1.2.860.3.16.1.2=", "PINFL=");
                    var vo = {
                        disk: el.disk,
                        path: el.path,
                        name: el.name,
                        alias: el.alias,
                        serialNumber: EIMZOClient._getX500Val(x500name_ex, "SERIALNUMBER"),
                        validFrom: new Date(EIMZOClient._getX500Val(x500name_ex, "VALIDFROM").replace(/\./g, "-").replace(" ", "T")),
                        validTo: new Date(EIMZOClient._getX500Val(x500name_ex, "VALIDTO").replace(/\./g, "-").replace(" ", "T")),
                        CN: EIMZOClient._getX500Val(x500name_ex, "CN"),
                        TIN: (EIMZOClient._getX500Val(x500name_ex, "INN") ? EIMZOClient._getX500Val(x500name_ex, "INN") : (EIMZOClient._getX500Val(x500name_ex, "UID") ? EIMZOClient._getX500Val(x500name_ex, "UID") : EIMZOClient._getX500Val(x500name_ex, "PINFL"))),
                        UID: (EIMZOClient._getX500Val(x500name_ex, "UID") ? EIMZOClient._getX500Val(x500name_ex, "UID") : EIMZOClient._getX500Val(x500name_ex, "PINFL")),
                        O: EIMZOClient._getX500Val(x500name_ex, "O"),
                        T: EIMZOClient._getX500Val(x500name_ex, "T"),
                        type: 'pfx'
                    };
                    if (!vo.TIN)
                        continue;
                    var itmkey = itemIdGen(vo, rec);
                    if (!itmkey0) {
                        itmkey0 = itmkey;
                    }
                    var itm = itemUiGen(itmkey, vo);
                    items.push(itm);
                }
            } else {
                errors.push({ r: data.reason });
            }
            callback(itmkey0);
        }, function (e) {
            errors.push({ e: e });
            callback(itmkey0);
        });
    },
    _findTokens2: function (itemIdGen, itemUiGen, items, errors, callback) {
        var itmkey0;
        CAPIWS.callFunction({ plugin: "ftjc", name: "list_all_keys", arguments: [''] }, function (event, data) {
            if (data.success) {
                for (var rec in data.tokens) {
                    var el = data.tokens[rec];
                    var x500name_ex = el.info.toUpperCase();
                    x500name_ex = x500name_ex.replace("1.2.860.3.16.1.1=", "INN=");
                    x500name_ex = x500name_ex.replace("1.2.860.3.16.1.2=", "PINFL=");
                    var vo = {
                        cardUID: el.cardUID,
                        statusInfo: el.statusInfo,
                        ownerName: el.ownerName,
                        info: el.info,
                        serialNumber: EIMZOClient._getX500Val(x500name_ex, "SERIALNUMBER"),
                        validFrom: new Date(EIMZOClient._getX500Val(x500name_ex, "VALIDFROM")),
                        validTo: new Date(EIMZOClient._getX500Val(x500name_ex, "VALIDTO")),
                        CN: EIMZOClient._getX500Val(x500name_ex, "CN"),
                        TIN: (EIMZOClient._getX500Val(x500name_ex, "INN") ? EIMZOClient._getX500Val(x500name_ex, "INN") : (EIMZOClient._getX500Val(x500name_ex, "UID") ? EIMZOClient._getX500Val(x500name_ex, "UID") : EIMZOClient._getX500Val(x500name_ex, "PINFL"))),
                        UID: (EIMZOClient._getX500Val(x500name_ex, "UID") ? EIMZOClient._getX500Val(x500name_ex, "UID") : EIMZOClient._getX500Val(x500name_ex, "PINFL")),
                        O: EIMZOClient._getX500Val(x500name_ex, "O"),
                        T: EIMZOClient._getX500Val(x500name_ex, "T"),
                        type: 'ftjc'
                    };
                    if (!vo.TIN)
                        continue;
                    var itmkey = itemIdGen(vo, rec);
                    if (!itmkey0) {
                        itmkey0 = itmkey;
                    }
                    var itm = itemUiGen(itmkey, vo);
                    items.push(itm);
                }
            } else {
                errors.push({ r: data.reason });
            }
            callback(itmkey0);
        }, function (e) {
            errors.push({ e: e });
            callback(itmkey0);
        });
    }
};
(function (global) {
    'use strict';
    // existing version for noConflict()
    var _Base64 = global.Base64;
    var version = "2.1.4";
    // if node.js, we use Buffer
    var buffer;
    if (typeof module !== 'undefined' && module.exports) {
        buffer = require('buffer').Buffer;
    }
    // constants
    var b64chars
        = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    var b64tab = function (bin) {
        var t = {};
        for (var i = 0, l = bin.length; i < l; i++) t[bin.charAt(i)] = i;
        return t;
    }(b64chars);
    var fromCharCode = String.fromCharCode;
    // encoder stuff
    var cb_utob = function (c) {
        if (c.length < 2) {
            var cc = c.charCodeAt(0);
            return cc < 0x80 ? c
                : cc < 0x800 ? (fromCharCode(0xc0 | (cc >>> 6))
                    + fromCharCode(0x80 | (cc & 0x3f)))
                    : (fromCharCode(0xe0 | ((cc >>> 12) & 0x0f))
                        + fromCharCode(0x80 | ((cc >>> 6) & 0x3f))
                        + fromCharCode(0x80 | (cc & 0x3f)));
        } else {
            var cc = 0x10000
                + (c.charCodeAt(0) - 0xD800) * 0x400
                + (c.charCodeAt(1) - 0xDC00);
            return (fromCharCode(0xf0 | ((cc >>> 18) & 0x07))
                + fromCharCode(0x80 | ((cc >>> 12) & 0x3f))
                + fromCharCode(0x80 | ((cc >>> 6) & 0x3f))
                + fromCharCode(0x80 | (cc & 0x3f)));
        }
    };
    var re_utob = /[\uD800-\uDBFF][\uDC00-\uDFFFF]|[^\x00-\x7F]/g;
    var utob = function (u) {
        return u.replace(re_utob, cb_utob);
    };
    var cb_encode = function (ccc) {
        var padlen = [0, 2, 1][ccc.length % 3],
            ord = ccc.charCodeAt(0) << 16
                | ((ccc.length > 1 ? ccc.charCodeAt(1) : 0) << 8)
                | ((ccc.length > 2 ? ccc.charCodeAt(2) : 0)),
            chars = [
                b64chars.charAt(ord >>> 18),
                b64chars.charAt((ord >>> 12) & 63),
                padlen >= 2 ? '=' : b64chars.charAt((ord >>> 6) & 63),
                padlen >= 1 ? '=' : b64chars.charAt(ord & 63)
            ];
        return chars.join('');
    };
    var btoa = global.btoa ? function (b) {
        return global.btoa(b);
    } : function (b) {
        return b.replace(/[\s\S]{1,3}/g, cb_encode);
    };
    var _encode = buffer
        ? function (u) { return (new buffer(u)).toString('base64') }
        : function (u) { return btoa(utob(u)) }
        ;
    var encode = function (u, urisafe) {
        return !urisafe
            ? _encode(u)
            : _encode(u).replace(/[+\/]/g, function (m0) {
                return m0 == '+' ? '-' : '_';
            }).replace(/=/g, '');
    };
    var encodeURI = function (u) { return encode(u, true) };
    // decoder stuff
    var re_btou = new RegExp([
        '[\xC0-\xDF][\x80-\xBF]',
        '[\xE0-\xEF][\x80-\xBF]{2}',
        '[\xF0-\xF7][\x80-\xBF]{3}'
    ].join('|'), 'g');
    var cb_btou = function (cccc) {
        switch (cccc.length) {
            case 4:
                var cp = ((0x07 & cccc.charCodeAt(0)) << 18)
                    | ((0x3f & cccc.charCodeAt(1)) << 12)
                    | ((0x3f & cccc.charCodeAt(2)) << 6)
                    | (0x3f & cccc.charCodeAt(3)),
                    offset = cp - 0x10000;
                return (fromCharCode((offset >>> 10) + 0xD800)
                    + fromCharCode((offset & 0x3FF) + 0xDC00));
            case 3:
                return fromCharCode(
                    ((0x0f & cccc.charCodeAt(0)) << 12)
                    | ((0x3f & cccc.charCodeAt(1)) << 6)
                    | (0x3f & cccc.charCodeAt(2))
                );
            default:
                return fromCharCode(
                    ((0x1f & cccc.charCodeAt(0)) << 6)
                    | (0x3f & cccc.charCodeAt(1))
                );
        }
    };
    var btou = function (b) {
        return b.replace(re_btou, cb_btou);
    };
    var cb_decode = function (cccc) {
        var len = cccc.length,
            padlen = len % 4,
            n = (len > 0 ? b64tab[cccc.charAt(0)] << 18 : 0)
                | (len > 1 ? b64tab[cccc.charAt(1)] << 12 : 0)
                | (len > 2 ? b64tab[cccc.charAt(2)] << 6 : 0)
                | (len > 3 ? b64tab[cccc.charAt(3)] : 0),
            chars = [
                fromCharCode(n >>> 16),
                fromCharCode((n >>> 8) & 0xff),
                fromCharCode(n & 0xff)
            ];
        chars.length -= [0, 0, 2, 1][padlen];
        return chars.join('');
    };
    var atob = global.atob ? function (a) {
        return global.atob(a);
    } : function (a) {
        return a.replace(/[\s\S]{1,4}/g, cb_decode);
    };
    var _decode = buffer
        ? function (a) { return (new buffer(a, 'base64')).toString() }
        : function (a) { return btou(atob(a)) };
    var decode = function (a) {
        return _decode(
            a.replace(/[-_]/g, function (m0) { return m0 == '-' ? '+' : '/' })
                .replace(/[^A-Za-z0-9\+\/]/g, '')
        );
    };
    var noConflict = function () {
        var Base64 = global.Base64;
        global.Base64 = _Base64;
        return Base64;
    };
    // export Base64
    global.Base64 = {
        VERSION: version,
        atob: atob,
        btoa: btoa,
        fromBase64: decode,
        toBase64: encode,
        utob: utob,
        encode: encode,
        encodeURI: encodeURI,
        btou: btou,
        decode: decode,
        noConflict: noConflict
    };
    // if ES5 is available, make Base64.extendString() available
    if (typeof Object.defineProperty === 'function') {
        var noEnum = function (v) {
            return { value: v, enumerable: false, writable: true, configurable: true };
        };
        global.Base64.extendString = function () {
            Object.defineProperty(
                String.prototype, 'fromBase64', noEnum(function () {
                    return decode(this)
                }));
            Object.defineProperty(
                String.prototype, 'toBase64', noEnum(function (urisafe) {
                    return encode(this, urisafe)
                }));
            Object.defineProperty(
                String.prototype, 'toBase64URI', noEnum(function () {
                    return encode(this, true)
                }));
        };
    }
    // that's it!
})(this);

CAPIWS = (typeof EIMZOEXT !== 'undefined') ? EIMZOEXT : {
    URL: (window.location.protocol.toLowerCase() === "https:" ? "wss://127.0.0.1:64443" : "ws://127.0.0.1:64646") + "/service/cryptapi",
    callFunction: function (funcDef, callback, error) {
        if (!window.WebSocket) {
            if (error)
                error();
            return;
        }
        var socket;
        try {
            socket = new WebSocket(this.URL);
        } catch (e) {
            error(e);
        }
        socket.onerror = function (e) {
            if (error)
                error(e);
        };
        socket.onmessage = function (event) {
            var data = JSON.parse(event.data);
            socket.close();
            callback(event, data);
        };
        socket.onopen = function () {
            socket.send(JSON.stringify(funcDef));
        };
    },
    version: function (callback, error) {
        if (!window.WebSocket) {
            if (error)
                error();
            return;
        }
        var socket;
        try {
            socket = new WebSocket(this.URL);
        } catch (e) {
            error(e);
        }
        socket.onerror = function (e) {
            if (error)
                error(e);
        };
        socket.onmessage = function (event) {
            var data = JSON.parse(event.data);
            socket.close();
            callback(event, data);
        };
        socket.onopen = function () {
            var o = { name: 'version' };
            socket.send(JSON.stringify(o));
        };
    },
    apidoc: function (callback, error) {
        if (!window.WebSocket) {
            if (error)
                error();
            return;
        }
        var socket;
        try {
            socket = new WebSocket(this.URL);
        } catch (e) {
            error(e);
        }
        socket.onerror = function (e) {
            if (error)
                error(e);
        };
        socket.onmessage = function (event) {
            var data = JSON.parse(event.data);
            socket.close();
            callback(event, data);
        };
        socket.onopen = function () {
            var o = { name: 'apidoc' };
            socket.send(JSON.stringify(o));
        };
    },
    apikey: function (domainAndKey, callback, error) {
        if (!window.WebSocket) {
            if (error)
                error();
            return;
        }
        var socket;
        try {
            socket = new WebSocket(this.URL);
        } catch (e) {
            error(e);
        }
        socket.onerror = function (e) {
            if (error)
                error(e);
        };
        socket.onmessage = function (event) {
            var data = JSON.parse(event.data);
            socket.close();
            callback(event, data);
        };
        socket.onopen = function () {
            var o = { name: 'apikey', arguments: domainAndKey };
            socket.send(JSON.stringify(o));
        };
    }
};