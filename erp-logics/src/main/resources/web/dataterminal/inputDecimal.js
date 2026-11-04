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
function inputText(selectOnShow) {
    const options = {
        inputMode: 'text',
        selectOnShow: selectOnShow,
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

// Варианты ...Selected - для экрана ввода по одному полю: при появлении поля фокус в нем и текст выделен (selectOnShow)
function inputTextSelected() {
    return inputText(true)
}

// Число (количество, цена) с системной цифровой экранной клавиатурой - для экранов ввода полей, где
// нет своих кнопок-цифр (inputKeyboard рассчитан на кнопки формы inputQuantityNumeric и системную клавиатуру отключает).
// Запятая принимается как десятичная точка
function toDecimalValue(str) {
    const v = (str || '').trim().replace(',', '.')
    if (!v || v === '.' || isNaN(Number(v))) return null
    return Number(v)
}

// Число отдаем серверу строкой (как inputKeyboard): 0 числом платформа принимает за NULL, и поле очищалось
function toDecimalChange(str) {
    const value = toDecimalValue(str)
    return value === null ? null : String(value)
}

function inputDecimalText(selectOnShow) {
    const options = {
        inputMode: 'decimal',
        selectOnShow: selectOnShow,
        listeners: {
            // Цифры не отдаем горячим клавишам формы ('1', '2', ...)
            keydown: (e) => { if (e.key && e.key.length === 1) e.stopPropagation() },
            // Отправляем число при каждом изменении: экранная клавиатура Android может не прислать Enter
            input: (e) => {
                const inp = e.target
                const value = toDecimalChange(inp.value)
                if (inp.controller && (value !== null || !inp.value.trim()) && inp.value !== inp.prevValue) {
                    inp.controller.changeValue(value)
                    inp.prevValue = inp.value
                }
            }
        },
        postprocessor: (controller, element, str) => toDecimalChange(str),
        onRender: (element) => {
            element.input.type = 'text'
            element.input.setAttribute('enterkeyhint', 'next')
        }
    }
    return initInputRender("decimal", options)
}

function inputDecimalTextSelected() {
    return inputDecimalText(true)
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

// Дата в формате ДД.ММ.ГГ (29.10.26) с цифровой экранной клавиатурой.
// Точки ставятся сами по мере ввода цифр. Маску не используем: экранная клавиатура Android
// присылает в keydown код 229, поэтому лишние символы вычищаются в событии input, а не в keydown
function toDateShortDto(controller, str) {
    const m = /^(\d{2})\.(\d{2})\.(\d{2})$/.exec(str || '')
    if (!m) return null
    const iso = '20' + m[3] + '-' + m[2] + '-' + m[1]
    const date = new Date(iso)
    // 31.02.26 и т.п.: Date «перекатывает» такую дату на следующий месяц - считаем ее неверной
    if (isNaN(date) || date.toISOString().substring(0, 10) !== iso) return null
    return date
}

// Платформа присылает дату как полночь по местному времени, поэтому число, месяц и год берем местные:
// toISOString (UTC) в поясе UTC+3 давал предыдущий день (01.10.26 -> 30.09.26)
function fromDateShortDto(controller, dto) {
    if (!dto) return null
    const pad = (n) => (n < 10 ? '0' : '') + n
    return pad(dto.getDate()) + '.' + pad(dto.getMonth() + 1) + '.' + pad(dto.getFullYear() % 100)
}

function inputDateKeyboard(selectOnShow) {
    function formatDate(e) {
        const inp = e.target
        const digits = inp.value.replace(/\D/g, '').substring(0, 6)
        let value = digits.substring(0, 2)
        if (digits.length > 2) value += '.' + digits.substring(2, 4)
        if (digits.length > 4) value += '.' + digits.substring(4, 6)
        if (inp.value !== value) inp.value = value

        // Полную дату (и очистку поля) отправляем сразу: касание кнопки OK не всегда снимает фокус с поля,
        // а экранная клавиатура может не прислать Enter
        if (inp.controller && (value.length === 8 || !value) && value !== inp.prevValue) {
            const dto = value ? toDateShortDto(inp.controller, value) : null
            if (value && !dto) return
            inp.controller.changeValue(dto)
            inp.prevValue = value
        }
    }

    const options = {
        inputMode: 'numeric',
        selectOnShow: selectOnShow,
        listeners: {
            // Цифры не отдаем горячим клавишам формы ('1', '2', ...)
            keydown: (e) => { if (e.key && e.key.length === 1) e.stopPropagation() },
            input: formatDate
        },
        attributes: {
            placeholder: 'ДД.ММ.ГГ',
            maxlength: 8
        },
        postprocessor: (controller, element, str) => toDateShortDto(controller, str),
        preprocessor: (controller, element, dto) => fromDateShortDto(controller, dto),
        onRender: addDateClearButton
    }

    return initInputRender("date", options)
}

function inputDateKeyboardSelected() {
    return inputDateKeyboard(true)
}

// Крестик сброса справа в поле даты: очищает поле и сразу отправляет NULL.
// Пока поле пустое (видна подсказка), крестик скрыт стилем input:placeholder-shown + .date-clear
function addDateClearButton(element) {
    const inp = element.input
    if (!inp) return
    const clear = document.createElement('span')
    clear.className = 'date-clear'
    clear.textContent = '✕'
    // Фокус остается в поле: иначе касание крестика сначала снимет фокус и закроет клавиатуру
    clear.addEventListener('mousedown', (e) => e.preventDefault())
    clear.addEventListener('click', (e) => {
        e.preventDefault()
        e.stopPropagation()
        if (!inp.value) return
        inp.value = ''
        if (inp.controller && inp.prevValue) inp.controller.changeValue(null)
        inp.prevValue = ''
        inp.focus()
    })
    element.classList.add('date-clearable')
    element.append(clear)
}
