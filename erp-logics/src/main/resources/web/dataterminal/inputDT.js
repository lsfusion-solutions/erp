function dropEscape() {
    document.addEventListener('keydown', (e) => {
        if (e.code === "Escape") {
            e.preventDefault();
            e.stopPropagation();
        }
    })
}
function setFocusOnVisible(element, event) {
    if (!document.getElementsByClassName('modal-backdrop').length &&
        !document.getElementsByClassName('gwt-PopupPanelGlass').length || element.modal) {
        if (event && event.type === 'focusout' && event.target === element) {
            event.preventDefault()
            event.stopPropagation()
        }

        setTimeout(() => element.focus({ focusVisible: true }), 0)
    }
}

function inspectionVisibility(element, callback) {
    new IntersectionObserver((entries, observer) => {
        entries.forEach(entry => {
            entry.target.isVisible = entry.intersectionRatio > 0;
            callback(element, element.isVisible);
        });
    }, { attributes: true, childList: true }).observe(element);
}

// todo - удалить
function inputBarcodeLiteAlwaysFocused() {
    return {
        render: function (element) {
            element.setAttribute('customType', 'inputBarcode');

            const inp = document.createElement('input');
            inp.type = 'text';
            inp.inputMode = 'none';
            element.append(inp);

            // Гарантируем, что фокус всегда остается на поле ввода
            inspectionVisibility(inp, setFocusOnVisible);
            inp.addEventListener("focusout", () => setFocusOnVisible(inp));
            inp.addEventListener("keypress", (e) => e.stopPropagation());
        },

        update: function (element, controller, value) {
            if (element.childNodes.length) {
                const inp = element.childNodes[0];

                inp.oninput = function () {
                    controller.changeValue(inp.value);
                };

                inp.onkeypress = (e) => {
                    const validCodes = new Set([29]); // Разрешенные коды (например, GS)
                    if (validCodes.has(e.keyCode)) {
                        inp.value += String.fromCharCode(e.keyCode);
                    }
                };

                inp.value = value;

                // Повторно устанавливаем фокус при обновлении
                setTimeout(() => inp.focus(), 0);
            }
        }
    };
}

function initInputRender(mode, options) {
    function postprocess(element, controller) {
        if (options && options.postprocessor) {
            const postValue = options.postprocessor(controller, element, element.input.value)
            if (!postValue) element.input.value = ""
            return postValue
        } else {
            return Number(element.input.value)
        }
    }

    function preprocess(element, controller, value) {
        if (options && options.preprocessor) {
            element.input.value = options.preprocessor(controller, element, value)
        } else {
            element.input.value = value;
        }
    }

    const listeners = options && options.listeners ? options.listeners : {};
    const attributes = options && options.attributes ? options.attributes : {};

    return {
        render: (element) => {
            const inp = document.createElement('input')

            element.setAttribute('customType', 'input' + mode)
            inp.className = mode
            element.defaultMode = mode
            inp.inputMode = 'none'
            element.append(inp)
            element.input = inp

            if (listeners) {
                for (let arg in listeners) {
                    inp.addEventListener(arg, listeners[arg])
                }
            }

            if (attributes) {
                for (var attr in attributes) {
                    inp.setAttribute(attr, attributes[attr])
                }
            }

            if (options && options.alwaysFocused) {
                inspectionVisibility(inp, setFocusOnVisible)
                inp.addEventListener("focusout", (event) => setFocusOnVisible(inp, event))
            }

            new MutationObserver(function (mutation, observer) {
                if (document.body.contains(element) && element.classList.contains("panelRendererValueFocused")) {
                    inp.select()
                    inp.focus()
                }
            }).observe(element, {attributes: true})

            inp.oncontextmenu = (event) => {
                event.preventDefault();
                event.stopPropagation();
            }

            if (options.onRender) options.onRender(element)
        },
        update: (element, controller, value) => {
            const inp = element.input
            inp.controller = controller
            const numValue = !isNaN(value) ? value : value ? value.value : null
            inp.modal = value ? value.modal : false


            function changeValue(event) {
                if (!inp.pattern || inp.patternMismatch) {
                    if (inp.value !== inp.prevValue) {
                        if (inp.value) {
                            controller.changeValue(postprocess(element, controller))
                        } else {
                            controller.changeValue(null)
                        }
                        inp.prevValue = inp.value
                    }
                }
            }
            function onEnter(event) {
                const charCode = (event.which) ? event.which : event.keyCode;
                if (charCode >= 112 && charCode <= 123 || charCode === 13) changeValue(event);
            }
            if (numValue == null) {
                inp.value = ""
            } else {
                preprocess(element, controller, numValue)
            }
            inp.prevValue = inp.value
            inp.addEventListener("blur", changeValue)
            inp.addEventListener("keydown", onEnter)
        }
    }
}

function keyPress(id, key) {

    function setCursorPosition(input, position) {
        const savedType = input.type
        input.type = 'text'
        input.setSelectionRange(position, position)
        input.type = savedType
    }

    function getCursorPosition(input) {
        const savedType = input.type
        input.type = 'text'
        const position = input.selectionStart
        input.type = savedType
        return position
    }

    function backspace(input) {
        const cursorPosition = getCursorPosition(input)
        if(!cursorPosition) return

        input.value = input.value.substring(0, cursorPosition - 1) + input.value.substring(cursorPosition)
        setCursorPosition(input, cursorPosition - 1)
    }

    function clear(input) {
        input.value = ''
    }

    function sign(input) {
        input.value = -input.value
    }

    function typeWrite(input, text) {
        const cursorPosition = getCursorPosition(input);
        input.value = input.value.substring(0, cursorPosition) + text + input.value.substring(cursorPosition)
        setCursorPosition(input, cursorPosition + 1)
        if (input.controller) input.controller.changeValue(input.value)
    }

    const element = document.querySelector('[id$=' + id + ']')
    if (!element || !element.input) return

    const input = element.input

    switch (key.toString().toUpperCase()) {
        case "BACKSPACE":
            backspace(input)
            break
        case "CLEAR":
            clear(input)
            break
        case "SIGN":
            sign(input)
            break
        case "DECIMAL":
            typeWrite(input, '.')
            break
        default:
            typeWrite(input, key)
    }
    if (input.controller !== undefined) input.controller.changeValue(Number(input.value))
}
