async function takeBarcode(fun, controller) {
    const codeReader = new ZXing.BrowserMultiFormatReader();
           codeReader
                .getVideoInputDevices()
                .then(videoInputDevices => {
                    if (videoInputDevices.length > 0) {
                        codeReader.decodeFromInputVideoDevice(undefined, 'videoBarcode').then((result) => {
                           console.log(result);
                           fun(result.text, controller);
                           codeReader.reset()
                           return;
                        }).catch((err) => {
                           console.log(err);
                        })
                   }
               })
}

function setValue(str, controller){
    controller.changeValue(str);
}

function barcodeRender() {
    return {
        render: function (element) {
            var video = document.createElement("video")
            video.innerText = 'Video stream not available.'
            video.id = "videoBarcode"

            element.video = video;
            element.appendChild(video);
            var script = document.createElement('script');
            script.src = 'https://unpkg.com/@zxing/library@latest/umd/index.min.js';
            script.type = 'text/javascript';
            document.getElementsByTagName('head')[0].appendChild(script);

        },
        update: function (element, controller) {
            element.video.onclick = async function()  {
                await takeBarcode(setValue, controller);
            }
        }
    }
}