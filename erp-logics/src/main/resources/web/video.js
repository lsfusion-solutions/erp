function videoRender() {
    return {
        render: function (element) {
            element.id = "videoRenderId";

            var video = document.createElement("video")
            video.innerText = 'Video stream not available.'

            element.video = video;
            element.appendChild(video);

            navigator.mediaDevices.getUserMedia({ video: true, audio: false })
            .then(function(stream) {
                video.srcObject = stream;
                video.play();
            })
            .catch(function(err) {
                console.log("An error occurred: " + err);
            });

            var canvas = document.createElement("canvas")
            element.canvas = canvas;
        },

        update: function (element, controller, value) {
            element.video.onclick = function(event) {
                controller.change(takePhoto(value));

                event.preventDefault();
            }
        }
    }
}

function takePhoto(options) {
    const element = document.getElementById("videoRenderId");

    const context = element.canvas.getContext('2d');

    const width = element.video.videoWidth / options.scale;
    const height = element.video.videoHeight / options.scale;

    element.canvas.width = width;
    element.canvas.height = height;
    context.drawImage(element.video, 0, 0, width, height);

    const data = element.canvas.toDataURL('image/' + options.mimeType);
    let encoded = data.toString().replace(/^data:(.*,)?/, '');
    if ((encoded.length % 4) > 0) {
       encoded += '='.repeat(4 - (encoded.length % 4));
    }

    return encoded;
}
