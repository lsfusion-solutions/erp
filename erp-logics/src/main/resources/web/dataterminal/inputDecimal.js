function isDecimalKey(e) {
    const targetText = e.target.innerText;
    const charCode = (e.which) ? e.which : e.keyCode;

    if ((charCode === 190 || charCode === 110) && targetText.indexOf('.') === -1) return;
    isNumericKey(e);
}

function isNumericKey(e) {
    const charCode = (e.which) ? e.which : e.keyCode;

    if (charCode >= 112 && charCode <= 123) return; // F1 - F12
    if (charCode < 32 || charCode > 36 && charCode < 41) return;
    if (charCode > 95 && charCode < 110) return;
    if (charCode < 48 || charCode > 57) {
        e.preventDefault();
        e.stopPropagation();
    }
}

function setDateMask(e) {
    $(e.target).mask("99.99.9999")
}

function checkQuantityValue(controller, str) {
    const maxValue = Number(getMapEntity("dataTerminalMaxQuantity"));
    const value = Number(str)
    if (maxValue && value && value > maxValue || isNaN(value)) return null;
    return value;
}

function toDateDto(controller, str) {
    if (!str) return null;
    const date = new Date(str.split('.').reverse().join('-'))
    if (isNaN(date)) return null;
    return date
}

function fromDateDto(controller, dto) {
    if (!dto) return null
    const s = dto.toISOString().substring(0, 10)
    return s.split('-').reverse().join('.')
}

function inputDecimal() {
    const options = {
        listeners: {
            keydown: isDecimalKey
        }
    }
    return initInputRender("decimal", options)
}

function inputDecimalAlwaysFocused() {
    const options = {
        listeners: {
            keydown: isDecimalKey
        },
        alwaysFocused: true,
        postprocessor: (controller, element, str) => checkQuantityValue(controller, str)
    }
    return initInputRender("decimal", options)
}

function inputInteger() {
    const options = {
        listeners: {
            keydown: isNumericKey
        }
    }
    return initInputRender("numeric", options)
}

function inputIntegerAlwaysFocused() {
    const options = {
        listeners: {
            keydown: isNumericKey
        },
        alwaysFocused: true,
        postprocessor: (controller, element, str) => checkQuantityValue(controller, str)
    }
    return initInputRender("numeric", options)
}

// Текстовое поле с системной экранной клавиатурой.
// Без alwaysFocused: иначе фокус постоянно возвращается в поле и клавиатура не закрывается
function inputText() {
    const options = {
        inputMode: 'text',
        listeners: {
            // Печатные символы не отдаем горячим клавишам формы ('1', '2', ...)
            keydown: (e) => { if (e.key && e.key.length === 1) e.stopPropagation() },
            // Отправляем текст при каждом изменении: касание кнопки OK не всегда снимает фокус с поля,
            // а экранная клавиатура Android может не прислать Enter, и без этого значение не дошло бы до сервера
            input: (e) => {
                const inp = e.target
                if (inp.controller) {
                    inp.controller.changeValue(inp.value ? inp.value : null)
                    inp.prevValue = inp.value
                }
            }
        },
        postprocessor: (controller, element, str) => str,
        onRender: (element) => {
            element.input.type = 'text'
            element.input.setAttribute('enterkeyhint', 'done')
        }
    }
    return initInputRender("text", options)
}

function inputStringAlwaysFocused() {
    const options = {
        alwaysFocused: true,
        postprocessor: (controller, element, str) => str
    }
    return initInputRender("text", options)
}


function inputDate() {
    const options = {
        listeners: {
            keydown: isNumericKey,
            focus: setDateMask
        },
        attributes: {
            placeholder: "__.__.____"
        },
        postprocessor: (controller, element, str) => toDateDto(controller, str),
        preprocessor: (controller, element, dto) => fromDateDto(controller, dto)
    }

    return initInputRender("date", options)
}

function inputDateAlwaysFocused() {
    const options = {
        listeners: {
            keydown: isNumericKey,
            focus: setDateMask
        },
        attributes: {
            placeholder: "__.__.____"
        },
        postprocessor: (controller, element, str) => toDateDto(controller, str),
        preprocessor: (controller, element, dto) => fromDateDto(controller, dto),
        alwaysFocused: true
    }

    return initInputRender("date", options)
}

function inputKeyboard() {
    function postprocess(controller, element, str) {
        if (!str || isNaN(Number(str))) return null
        if (str.substring(str.length - 1) === '.') return str.substring(0, str.length - 1)
        //return checkQuantityValue(controller, str)
        return str
    }

    function getStringValue(input) {
        const oldType = input.type
        input.type = 'text'
        const stringValue = input.value
        input.type = oldType
        return stringValue
    }

    function preprocess(controller, element, value) {
        if (!element || !element.input) return null

        const input = element.input
        const oldValue = input.value
        const newValue = value ? value : 0

        const stringValue = getStringValue(input)
        const decPos = stringValue.search('[\.]{1}[0]*$')

        return decPos < 0 ? value : value + stringValue.substring(decPos)
    }

    function onRender(element) {
        const inp = element.input
        if (!inp) return
        inp.style.textAlign = 'right'
        inp.type = 'numeric'
        inp.placeholder = '0'
        inp.maxLength = 16
    }

    const options = {
        listeners: {
            keydown: isDecimalKey
        },
        alwaysFocused: true,
        postprocessor: postprocess,
        preprocessor: preprocess,
        onRender: onRender
    }
    return initInputRender("decimal", options)
}

function inputKeyboardInteger() {
    function postprocess(controller, element, str) {
        if (!str || isNaN(Number(str))) return null
        if (str.includes('.')) return null
        return str
    }

    function preprocess(controller, element, value) {
        if (!element || !element.input) return null
        return value
    }

    function onRender(element) {
        const inp = element.input
        if (!inp) return
        inp.style.textAlign = 'right'
        inp.type = 'numeric'
        inp.placeholder = '0'
        inp.maxLength = 16
    }

    const options = {
        listeners: { keydown: isNumericKey },
        alwaysFocused: true,
        postprocessor: postprocess,
        preprocessor: preprocess,
        onRender: onRender
    }
    return initInputRender("integer", options)
}