let global_selectbox_container;
let global_selectbox_counter;
let isPluginEnabled;
let fileContent; // Переменная для хранения информации из файла, значение присваивается в cades_bes_file.html
let resultSigned;
let global_isFromCont;

class CertExportLSF{
    constructor(dateFrom, dateTo, SN, legal, sha){
        //this.isValid = yield cert.IsValid().Result;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.SN = SN;
        this.legal = legal;
        this.sha = sha;
    }
    exportJSON = function(){
        return JSON.stringify(this);
    }
}

function fillCertsAsync() {
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
                FillCertList_Async(setValueSignature, controller);
            }
        }
    }
}


//получение сертификатов пользователя
function FillCertList_Async(func, controller) {
    global_selectbox_container = new Array();
    cadesplugin.async_spawn(function *() {

        var MyStoreExists = true;
        try {
            var oStore = yield cadesplugin.CreateObjectAsync("CAdESCOM.Store");
            if (!oStore) {
                alert("Create store failed");
                return;
            }

            yield oStore.Open();
        }
        catch (ex) {
            MyStoreExists = false;
        }
        var certCnt;
        var certs;
        if (MyStoreExists) {
            try {
                certs = yield oStore.Certificates;
                certCnt = yield certs.Count;
            }
            catch (ex) {
                alert("Ошибка при получении Certificates или Count: " + cadesplugin.getLastError(ex));
                return;
            }
            for (var i = 1; i <= certCnt; i++) {
                var cert;
                try {
                    cert = yield certs.Item(i);
                    global_selectbox_container.push(cert);
                }
                catch (ex) {
                    alert("Ошибка при перечислении сертификатов: " + cadesplugin.getLastError(ex));
                    return;
                }
            }
            let out = new Array();
            for(var cert of global_selectbox_container){
                let elem = new CertExportLSF(
                    new Date(yield cert.ValidFromDate),
                    new Date(yield cert.ValidToDate),
                    new String(yield cert.SerialNumber),
                    new String(yield cert.IssuerName),
                    new String(yield cert.Thumbprint)
                );
                out.push(elem);
            }
            if (out.length > 0){
                yield oStore.Close();
                func(controller, JSON.stringify({Data: out}));
            }
        }
    });
}

//подпись
function SignCadesBES(dataToSign, sha, id) {
    cadesplugin.async_spawn(function*(arg) {
        console.log('start');
        //получение сертификатов
        var MyStoreExists = true;
        var errormes = "";
        var certificate;
        try {
            var oStore = yield cadesplugin.CreateObjectAsync("CAdESCOM.Store");
            if (!oStore) {
                errormes = "Create store failed";
                resultSigned[id] = JSON.stringify({id : id, error: true, description : errormes})
                return;
            }

            yield oStore.Open();
        }
        catch (ex) {
            MyStoreExists = false;
        }
        var certCnt;
        var certs;
        if (MyStoreExists) {
            try {
                certs = yield oStore.Certificates;
                certCnt = yield certs.Count;
                testResult = certCnt;
            }
            catch (ex) {
                errormes = "Ошибка при получении Certificates или Count: " + cadesplugin.getLastError(ex);
                resultSigned[id] = JSON.stringify({id : id, error: true, description : errormes})
                return;
            }

            for (var i = 1; i <= certCnt; i++) {
                var cert;
                try {
                    cert = yield certs.Item(i);
                    var certSha = new String(yield cert.Thumbprint);
                    if(certSha == sha){
                        certificate = cert;
                    }
                }
                catch (ex) {
                    errormes = "Ошибка при перечислении сертификатов: " + cadesplugin.getLastError(ex);
                    resultSigned[id] = JSON.stringify({id : id, error: true, description : errormes})
                    return;
                }
            }
            yield oStore.Close();
        }
        if (!certificate){
            errormes = "Не найден сертификат";
            resultSigned[id] = JSON.stringify({id : id, error: true, description : errormes})
            return;
        }
        var description = new String(yield certificate.IssuerName);
        var Signature;
        try
        {
            try {
                var oSigner = yield cadesplugin.CreateObjectAsync("CAdESCOM.CPSigner");
            } catch (err) {
                errormes = "Failed to create CAdESCOM.CPSigner: " + err.number;
                resultSigned[id] = JSON.stringify({id : id, error: true, description : errormes})
                return;
            }
            var oSigningTimeAttr = yield cadesplugin.CreateObjectAsync("CADESCOM.CPAttribute");

            yield oSigningTimeAttr.propset_Name(cadesplugin.CAPICOM_AUTHENTICATED_ATTRIBUTE_SIGNING_TIME);
            var oTimeNow = new Date();
            yield oSigningTimeAttr.propset_Value(oTimeNow);
            var attr = yield oSigner.AuthenticatedAttributes2;
            yield attr.Add(oSigningTimeAttr);

            var oDocumentNameAttr = yield cadesplugin.CreateObjectAsync("CADESCOM.CPAttribute");
            yield oDocumentNameAttr.propset_Name(cadesplugin.CADESCOM_AUTHENTICATED_ATTRIBUTE_DOCUMENT_NAME);
            yield oDocumentNameAttr.propset_Value("Document Name");
            yield attr.Add(oDocumentNameAttr);

            if (oSigner) {
                yield oSigner.propset_Certificate(certificate);
            }
            else {
                errormes = "Failed to create CAdESCOM.CPSigner";
                resultSigned[id] = JSON.stringify({id : id, error: true, description : errormes})
                return;
            }

            var oSignedData = yield cadesplugin.CreateObjectAsync("CAdESCOM.CadesSignedData");
            if (dataToSign) {
                yield oSignedData.propset_ContentEncoding(cadesplugin.CADESCOM_BASE64_TO_BINARY); //
                yield oSignedData.propset_Content(dataToSign);
            }
            yield oSigner.propset_Options(cadesplugin.CAPICOM_CERTIFICATE_INCLUDE_WHOLE_CHAIN);

            try {
                Signature = yield oSignedData.SignCades(oSigner, cadesplugin.CADESCOM_CADES_BES);
            }
        catch (err) {
            errormes = "Не удалось создать подпись из-за ошибки: " + cadesplugin.getLastError(err);
            resultSigned[id] = JSON.stringify({id : id, error: true, description : errormes})
            return;
        }

            var json = JSON.stringify({ sign : Signature, description : description});
            resultSigned[id] = json;
            return;
        }
        catch(err)
        {
            errormes = "Не удалось создать подпись из-за ошибки: " + cadesplugin.getLastError(err);
            resultSigned[id] = JSON.stringify({id : id, error: true, description : errormes})
        }
    }, resultSigned);
}

function setValueSignature(controller, result){
    controller.changeValue(result);
}

function cadesPlugin(){
    var script = document.createElement('script');
    script.src = 'https://www.cryptopro.ru/sites/default/files/products/cades/cadesplugin_api.js?v=1';
    script.language = 'javascript';
    document.getElementsByTagName('head')[0].appendChild(script);
    console.log('cadesplugin_api connected');
    resultSigned = {};
}



function signData(value){
    SignCadesBES(value.data, value.sha, value.id);
    console.log(`signed with id ${value.id} started`);
}

function checkSign(id){
    if (resultSigned[id]){
        console.log(`signed with id ${id} finished`);
        return resultSigned[id];
    }
    else{
        return JSON.stringify({id: id, status : 'in process'});
    }
}
