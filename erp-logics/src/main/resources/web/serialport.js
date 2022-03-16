let serialPortReader;

async function getPort(info) {
    if (info)  {
        const ports = await navigator.serial.getPorts();
        const devices = ports.filter(port => port.getInfo().usbProductId == parseInt(info.pid, 16) && port.getInfo().usbVendorId == parseInt(info.vid, 16))
        if (devices.length > 0)
            return devices[0];
        else {
            alert("Устройство " + info.vid + "/" + info.pid + " не найдено");
            return;
        }
    } else {
        return navigator.serial.requestPort();
    }
}

async function readUntilClosed(info, status) {
    let port = await getPort(info);
    if (!port) {
        status.innerText = "Не удалось получить порт";
        serialPortReader = undefined;
        return;
    } else
        status.innerText = '';

    try {
        await port.open({ baudRate: 9600 });
    } catch (error) {
        status.innerText = "Code " + error.code + " : " + error.message + " / " + error.name;
        serialPortReader = undefined;
        return;
    }

    while (port.readable) {
        reader = port.readable.getReader();
        try {
            while (true) {
                const { value, done } = await reader.read();
                if (done) {
                    break;
                }
                console.log(value);
                if (serialPortReader.onReceive !== undefined)
                    serialPortReader.onReceive(value);
            }
        } catch (error) {
            status.innerText = error;
        } finally {
            reader.releaseLock();
        }
    }

    await port.close();
    serialPortReader = undefined;
}

function serialPortReceiveRender() {
    return {
        render: function (element) {
            var status = document.createElement("div")
            status.classList.add("serial-port-status");

            element.status = status;
            element.appendChild(status);
        },

        update: function (element, controller, value) {
            element.parentElement.style.setProperty("border", "none");
            element.parentElement.style.setProperty("margin", "0");

            if (serialPortReader === undefined) {
                serialPortReader = readUntilClosed(value, element.status)
            }
            if (serialPortReader !== undefined) {
                serialPortReader.onReceive = function (value) {
                    controller.changeValue(JSON.stringify(Array.from(value)));
                }
            }
        }
    }
}

let serialPortWriter;

async function serialPortSend (port, value) {
    if (!serialPortWriter) {
        serialPortWriter = await getPort(port);
        if (!serialPortWriter) return;

        await serialPortWriter.open({ baudRate: 115200 });
    }

    const encoder = new TextEncoder();

    const writer = serialPortWriter.writable.getWriter();
    await writer.write(encoder.encode(value));

    writer.releaseLock();
}