function serialPortRequestPort() {
    return {
        render: function (element) {
            var request = document.createElement("button")
            request.classList.add("serial-port-request");

            element.request = request;
            element.appendChild(request);
        },

        update: function (element, controller, value) {
            element.request.innerText = value.caption;

            element.request.onclick = async function() {
                const port = await navigator.serial.requestPort();
                if (port) {
                    const vid = port.getInfo().usbVendorId;
                    const pid = port.getInfo().usbProductId;
                    controller.changeValue(JSON.stringify({ vid : (vid ? vid.toString(16) : null),
                                                            pid : (pid ? pid.toString(16) : null) }));
                }
            }
        }
    }
}

async function getPort(info) {
    const ports = await navigator.serial.getPorts();
    const devices = ports.filter(port => port.getInfo().usbProductId === (info.pid ? parseInt(info.pid, 16) : undefined)
                                      && port.getInfo().usbVendorId === (info.vid ? parseInt(info.vid, 16) : undefined));

    const number = info.number ? info.number-1 : 0;

    if (devices.length > number)
        return devices[number];
    else {
        throw { code : "", message : "Устройство с номером " + info.number + "(" + info.vid + "/" + info.pid + ")" + " не найдено", name : "" };
    }
}

let serialPortReader;
let onSerialPortReceive;
let onSerialPortError;

async function openPortReader(info, timeout) {
    if (serialPortReader) return;

    let port;
    try {
        port = await getPort(info);
        if (!port) {
            alert("Не удалось получить порт");
            return;
        }

        await port.open({ baudRate: info.baudRate });
    } catch (error) {
        if (onSerialPortError) onSerialPortError(error);
        alert("Код " + error.code + " : " + error.message + " / " + error.name);
        return;
    }

    keepReading = true;
    while (port.readable && keepReading) {
        serialPortReader = port.readable.getReader();
        serialPortReader.writer = port.writable.getWriter();
        try {
            let buffer;
            while (true) {
                const { value, done } = await serialPortReader.read();
                if (done) {
                    keepReading = false;
                    break;
                }
                console.log(value);
                if (onSerialPortReceive) {
                    if (buffer) {
                        buffer = new Uint8Array([...buffer, ...value]);
                    } else {
                        buffer = value;
                        setTimeout(function() {
                            console.log("send : " + buffer);
                            onSerialPortReceive(buffer);
                            buffer = undefined;
                        }, timeout);
                    }
                }
            }
        } catch (error) {
            alert("Код " + error.code + " : " + error.message + " / " + error.name);
        } finally {
            serialPortReader.releaseLock();
            serialPortReader.writer.releaseLock();
        }
    }

    await port.close();
    serialPortReader = undefined;
}

async function sendPortReader(value) {
    const encoder = new TextEncoder();
    await serialPortReader.writer.write(encoder.encode(value));
}

async function closePortReader() {
    if (serialPortReader)
        serialPortReader.cancel();
}

function serialPortReceiveRender() {
    return {
        render: function (element) {
            var status = document.createElement("div")
            status.classList.add("serial-port-status");

            element.status = status;
            element.appendChild(status);
        },

        update: function (element, controller, options) {
            element.parentElement.style.setProperty("border", "none");
            element.parentElement.style.setProperty("margin", "0");

            onSerialPortReceive = function (value) {
                if (options && options.debugInfo)
                    element.status.innerText = value;
                controller.changeValue(JSON.stringify(Array.from(value)));
            }
        }
    }
}

let serialPortWriter;

async function serialPortOpen (options) {
    let port;

    try {
        port = await getPort(options);
        if (!port) return;

        await port.open({ baudRate: options.baudRate });
    } catch (error) {
        alert("Code " + error.code + " : " + error.message + " / " + error.name);
        return;
    }

    return port;
}


async function serialPortSend (options, value) {
    if (!serialPortWriter) {
        serialPortWriter = await serialPortOpen(options);
        if (!serialPortWriter) return;
    }

    const encoder = new TextEncoder();

    const writer = serialPortWriter.writable.getWriter();
    await writer.write(encoder.encode(value));

    writer.releaseLock();
}

async function serialPortClose () {
    if (serialPortWriter) {
        await serialPortWriter.close();
        serialPortWriter = undefined;
    }
}

async function serialPortTest (options, alertSuccess) {
    await serialPortClose();
    let port = await serialPortOpen(options);
    if (port) {
        if (alertSuccess)
            alert("Success");
        await port.close();
    }
}