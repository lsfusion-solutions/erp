function videoRender() {
    return {
        render: function (element) {
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

//            video.addEventListener('canplay', function(ev) {
//                if (!element.streaming) {
//                    height = video.videoHeight / (video.videoWidth / width);
//
//                    video.setAttribute('width', width);
//                    video.setAttribute('height', height);
//                    canvas.setAttribute('width', width);
//                    canvas.setAttribute('height', height);
//                    element.streaming = true;
//                }
//            }, false);

//            var takePhoto = document.createElement("button")
//            takePhoto.innerText = 'Сделать фото';
//            takePhoto.classList.add("stream-take-photo");
//            stream.appendChild(takePhoto);
//            element.takePhoto = takePhoto;

            var canvas = document.createElement("canvas")
            element.canvas = canvas;

//            element.stream = stream;
//            element.appendChild(stream);
        },

        update: function (element, controller, value) {

            takePhoto = function () {
                var context = element.canvas.getContext('2d');

                element.canvas.width = element.video.videoWidth;
                element.canvas.height = element.video.videoHeight;
                context.drawImage(element.video, 0, 0, element.video.videoWidth, element.video.videoHeight);

                var data = element.canvas.toDataURL('image/png');
                let encoded = data.toString().replace(/^data:(.*,)?/, '');
                if ((encoded.length % 4) > 0) {
                   encoded += '='.repeat(4 - (encoded.length % 4));
                }

                controller.changeValue(JSON.stringify({ value : encoded }))
            }

            if (value == true) {
                takePhoto();
                controller.changeValue('');
            }

            element.video.onclick = function(event) {
                takePhoto();

                event.preventDefault();
            }
        }
    }
}
