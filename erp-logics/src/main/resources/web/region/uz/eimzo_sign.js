var EIMZO_MAJOR = 3;
var EIMZO_MINOR = 37;
let resultSigned;

var errorCAPIWS =
  "Ошибка соединения с E-IMZO. Возможно у вас не установлен модуль E-IMZO или Браузер E-IMZO.";
var errorBrowserWS =
  "Браузер не поддерживает технологию WebSocket. Установите последнюю версию браузера.";
var errorUpdateApp =
  'ВНИМАНИЕ !!! Установите новую версию приложения E-IMZO или Браузера E-IMZO.<br /><a href="https://e-imzo.uz/main/downloads/" role="button">Скачать ПО E-IMZO</a>';
var errorWrongPassword = "Пароль неверный.";

function listCert(func, controller) {
  EIMZOClient.API_KEYS = [
    "localhost",
    "96D0C1491615C82B9A54D9989779DF825B690748224C2B04F500F370D51827CE2644D8D4A82C18184D73AB8530BB8ED537269603F61DB0D03D2104ABF789970B",
    "127.0.0.1",
    "A7BCFA5D490B351BE0754130DF03A068F855DB4333D43921125B9CF2670EF6A40370C646B90401955E1F7BC9CDBF59CE0B2C5467D820BE189C845D0B79CFC96F",
    "null",
    "E0A205EC4E7B78BBB56AFF83A733A1BB9FD39D562E67978CC5E7D73B0951DB1954595A20672A63332535E13CC6EC1E1FC8857BB09E0855D7E76E411B6FA16E9D",
  ];
  EIMZOClient.checkVersion(
    function (major, minor) {
      var newVersion = EIMZO_MAJOR * 100 + EIMZO_MINOR;
      var installedVersion = parseInt(major) * 100 + parseInt(minor);
      if (installedVersion < newVersion) {
        alert("обновите версию")
      } else {
        EIMZOClient.installApiKeys(
          function () {
            EIMZOClient.listAllUserKeys(
              function (o, i) {
                var itemId = "itm-" + o.serialNumber + "-" + i;
                return itemId;
              },
              function (itemId, v) {
                return uiCreateItem(itemId, v);
              },
              function (items, firstId) {
                func(controller, JSON.stringify(items));
              },
              function (e, r) {
                uiShowMessage(errorCAPIWS);
              }
            );
          },
          function (e, r) {
            if (r) {
              uiShowMessage(r);
            } else {
              wsError(e);
            }
          }
        );
      }
    },
    function (e, r) {
      if (r) {
        uiShowMessage(r);
      } else {
        func(controller, JSON.stringify({ error: true, description: e.type }));
      }
    }
  );
};

var uiShowMessage = function (message) {
  alert(message);
};


var wsError = function (e) {
  if (e) {
    uiShowMessage(errorCAPIWS + " : " + e);
  } else {
    uiShowMessage(errorBrowserWS);
  }
};
var uiCreateItem = function (itmkey, vo) {
  vo.id = itmkey;
  return vo;
};

function fillCertAsync() {
  return {
    render: function (element) {
      var request = document.createElement("button");
      request.classList.add("fillCertAsync");
      element.request = request;
      element.appendChild(request);
    },

    update: function (element, controller, value) {
      element.request.innerText = value.caption;
      element.request.onclick = async function () {
        listCert(setValueSignature, controller);
      }
    }
  }
}

function setValueSignature(controller, result) {
  controller.changeValue(result);
}


function signAttach(data, vo, id){
  EIMZOClient.loadKey(
    vo,
    function (key) {
      EIMZOClient.appendPkcs7Attached(
        key,
        data,
        null,
        function (pkcs7) {
          var json = JSON.stringify({ sign : pkcs7});
          resultSigned[id] = json;
          return;
        },
        function (e, r) {
          if (r) {
            if (r.indexOf("BadPaddingException") != -1) {
              resultSigned[id] = JSON.stringify({id : id, error: true, description : errorWrongPassword})
              return;
            } else {
              resultSigned[id] = JSON.stringify({id : id, error: true, description : r})
              return;
            }
          } else {
            resultSigned[id] = JSON.stringify({id : id, error: true, description : errorBrowserWS})
            return;
          }
        }
      );
    },
    function (e, r) {
      if (r) {
        if (r.indexOf("BadPaddingException") != -1) {
          resultSigned[id] = JSON.stringify({id : id, error: true, description : errorWrongPassword})
          return;
        } else {
          resultSigned[id] = JSON.stringify({id : id, error: true, description : r})
          return;
        }
      } else {
        resultSigned[id] = JSON.stringify({id : id, error: true, description : errorBrowserWS})
        return;
      }
    }
  );
}

function sign(data, vo, id){
  EIMZOClient.loadKey(
    vo,
    function (key) {
      EIMZOClient.createPkcs7(
        key,
        data,
        null,
        function (pkcs7) {
          var json = JSON.stringify({ sign : pkcs7});
          resultSigned[id] = json;
          return;
        },
        function (e, r) {
          if (r) {
            if (r.indexOf("BadPaddingException") != -1) {
              resultSigned[id] = JSON.stringify({id : id, error: true, description : errorWrongPassword})
              return;
            } else {
              resultSigned[id] = JSON.stringify({id : id, error: true, description : r})
              return;
            }
          } else {
            resultSigned[id] = JSON.stringify({id : id, error: true, description : errorBrowserWS})
            return;
          }
        }
      );
    },
    function (e, r) {
      if (r) {
        if (r.indexOf("BadPaddingException") != -1) {
          resultSigned[id] = JSON.stringify({id : id, error: true, description : errorWrongPassword})
          return;
        } else {
          resultSigned[id] = JSON.stringify({id : id, error: true, description : r})
          return;
        }
      } else {
        resultSigned[id] = JSON.stringify({id : id, error: true, description : errorBrowserWS})
        return;
      }
    }
  );
}

function signData(value){
  resultSigned = {};
  if(value.attach == 1) {
    signAttach(value.data, value.vo, value.id);
  } else {
      sign(value.data, value.vo, value.id);
  }
  console.log(`signed with id ${value.id} started`);
}

function checkSign(id){
  if (resultSigned[id]){
      console.log(`signed with id ${id} finished`);
      return resultSigned[id];
  }
  else {
      return JSON.stringify({id: id, status : 'in process'});
  }
}
