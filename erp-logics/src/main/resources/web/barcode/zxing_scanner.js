function scanBarcode(canvasElement, format) {
    var imgWidth = canvasElement.width;
    var imgHeight = canvasElement.height;
    var imageData = canvasElement.getContext('2d').getImageData(0, 0, imgWidth, imgHeight);
    var sourceBuffer = imageData.data;

    if (zxing != null) {
        var buffer = zxing._malloc(sourceBuffer.byteLength);
        zxing.HEAPU8.set(sourceBuffer, buffer);
        var result = zxing.readBarcodeFromPixmap(buffer, imgWidth, imgHeight, true, format);
        zxing._free(buffer);
        return result;
    } else {
        return '1';
    }

}

let zxing = null;
let timerId;
let barcode;

async function initZXing(){
    var script = document.createElement('script');
    script.src = 'https://zxing-cpp.github.io/zxing-cpp/zxing_reader.js';
    script.type = 'text/javascript';
    document.getElementsByTagName('head')[0].appendChild(script);
  
}


function scan(controller, func){
    try{
        var video = document.getElementById("videoRenderId");
        var canvasElement = document.createElement("canvas")
        canvasElement.hidden = false;
        canvasElement.id = "canvas"
        canvasElement.height = video.video.videoHeight;
        canvasElement.width = video.video.videoWidth;

        var canvas = canvasElement.getContext("2d");
        canvas.drawImage(video.video, 0, 0, canvasElement.width, canvasElement.height);
        let resultCode = scanBarcode(canvasElement, '');
        console.log(resultCode);
        if (resultCode.text != ''){
            console.log(resultCode.text);
            clearInterval(timerId);
            stopStreamedVideo();
            func(resultCode.text, controller);
        }
    }
    catch(err){
        console.log('Failed scan: ' + err.message);
    }
 
}

function setValue(str, controller){
    controller.changeValue(str);
}


function barcodeRender() {
    return {
        render: function (element) {
            element.id = "videoRenderId";
            var error = document.createElement("div");
            error.setAttribute('style', 'text-align: center;font-size: 1.25em;');
            var video = document.createElement("video");
            video.innerText = 'Video stream not available.';
            video.setAttribute('autoplay', '');
            video.setAttribute('muted', '');
            video.setAttribute('playsinline', '');
            element.video = video;
            element.appendChild(video);
            element.appendChild(error);
            navigator.mediaDevices.getUserMedia({ 
                video: 
                    {
                        height : 400,
                        width : 400,
                        facingMode: 'environment',
                        focusMode: 'continuous'
                    },
                audio: false })
            .then(function(stream) {
                video.srcObject = stream;
                video.play();
            })
            .catch(function(err) {
                console.log("An error occurred: " + err);
                error.innerText = 'Нет доступа к видеопотоку.';
            });
            //
            stateFlash = false;
            //
            var canvas = document.createElement("canvas")
            element.canvas = canvas;
        },

        update: function (element, controller) {
            if(!zxing){
                ZXing().then(function(value) {
                    zxing = value;
                });
            }
        }
    }
}

function barcodeReader() {
    return {
        render: function (element) {
            element.id = "buttonReader";
            var button = document.createElement("button");
            element.button = button;
            button.innerText = 'Скан';
            element.appendChild(button);

        },
        update: function (element, controller, value) {
            element.button.onclick = function() {
                element.innerText = 'Идет сканирование...';
                timerId = setInterval(() => barcode = scan(controller, setValue), value.interval);
            }
        }
    }
}


function stopStreamedVideo() {
    clearInterval(timerId);
    let videoElem = document.getElementById('videoRenderId');
    if (videoElem){
        const stream = videoElem.video.srcObject;
        const tracks = stream?.getTracks();
        tracks?.forEach((track) => {
            track.stop();
        });
        videoElem.video.srcObject = null;
    }
}

let stateFlash;

function flashlight(){
    try{
        stateFlash = !stateFlash;
        var video = document.getElementById('videoRenderId')
        const stream = video.video.srcObject;
        const track = stream.getVideoTracks()[0];
        track.applyConstraints({
            advanced: [{torch: stateFlash}]
        })
    }
    catch(err){
        console.log(err.message);
    }
}

//v2
function scanCustom(element, controller, func){
    try{
        var video = document.getElementById("videoRenderId");
        var canvasElement = document.createElement("canvas")
        canvasElement.hidden = false;
        canvasElement.id = "canvas"
        canvasElement.height = video.video.videoHeight;
        canvasElement.width = video.video.videoWidth;

        var canvas = canvasElement.getContext("2d");
        canvas.drawImage(video.video, 0, 0, canvasElement.width, canvasElement.height);
        let resultCode = scanBarcode(canvasElement, '');
        console.log(resultCode);
        if (resultCode.text != ''){
            console.log(resultCode.text);
            if (lastResult != resultCode.text){
                lastResult = resultCode.text;
                clearInterval(timerId);
                func(resultCode.text, controller);
                pause = true;
            }else{
                if (lastResult)
                    setTimeout(skipResult, 5000);
            }
            
        }
    }
    catch(err){
        console.log('Failed scan: ' + err.message);
    }
}

function barcodeReaderCustom() {
    return {
        render: function (element) {
            element.id = "caption";
            var label = document.createElement("caption");
            label.text = 'Здесь будет штрихкод товара';
            element.appendChild(label);

        },
        update: function (element, controller, value) {
            if(!interval){
                interval = value.interval;
            }
            if(!timerId) 
                startScan(element, controller);
            if (pause) setTimeout(startScan, 1000, element, controller);
        }
    }
}

let interval;
let pause;
let lastResult;

function skipResult(){
    lastResult = null;
}
function startScan(element, controller){
    pause = false;
    timerId = setInterval(() => barcode = scanCustom(element, controller, setValue), interval);
}

function vibrate(){
    if(!window.navigator.vibrate)
        return;
    navigator.vibrate(200);
}