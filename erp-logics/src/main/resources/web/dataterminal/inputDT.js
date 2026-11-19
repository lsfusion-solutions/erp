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

// Поля экрана ввода (selectOnShow) и возврат фокуса после модальных окон: когда закрывается последнее окно
// (например «Партии» или сообщение об ошибке поля), а фокус не в поле ввода - фокус в видимое поле экрана ввода
// вне окон (на экране строки показывается только текущее поле). Одно наблюдение на документ, проверка дешевая:
// живые коллекции окон, срабатывает только на переходе «окно открыто -> окон нет»
var selectOnShowInputs = new Set()
var selectOnShowModalObserver = null

function focusSelectOnShowInput() {
    const active = document.activeElement
    if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA')) return
    for (const inp of selectOnShowInputs) {
        if (!inp.isConnected) {
            selectOnShowInputs.delete(inp)
            continue
        }
        if (!inp.closest('.modal-dialog') && inp.offsetParent !== null) {
            inp.focus()
            return
        }
    }
}

function observeModalsClosing() {
    if (selectOnShowModalObserver) return
    const backdrops = document.getElementsByClassName('modal-backdrop')
    const glasses = document.getElementsByClassName('gwt-PopupPanelGlass')
    let modalOpen = backdrops.length > 0 || glasses.length > 0
    selectOnShowModalObserver = new MutationObserver(() => {
        const open = backdrops.length > 0 || glasses.length > 0
        if (modalOpen && !open) {
            // окно при закрытии может вернуть фокус на свою кнопку-источник позже - вторая попытка
            setTimeout(focusSelectOnShowInput, 0)
            setTimeout(focusSelectOnShowInput, 300)
        }
        modalOpen = open
    })
    selectOnShowModalObserver.observe(document.body, {childList: true, subtree: true})
}

// Поле экрана ввода по одному полю: при появлении на экране (и при фокусе, например по ACTIVATE PROPERTY
// или после закрытия сообщения) - фокус и выделение текста, чтобы ввод заменял старое значение.
// Значение с сервера может прийти позже фокуса - тогда update выделяет его снова, пока пользователь не начал ввод.
// Пока открыто модальное окно, фокус берет только поле в самом окне (например ввод количества по партии):
// поле основного экрана под окном фокус у окна не перехватывает
function selectOnShow(inp) {
    selectOnShowInputs.add(inp)
    observeModalsClosing()
    function select() {
        if (document.activeElement !== inp) return
        inp.select()
        inp.selectOnUpdate = true
    }
    new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.intersectionRatio > 0 && (inp.closest('.modal-dialog') ||
                    (!document.getElementsByClassName('modal-backdrop').length &&
                     !document.getElementsByClassName('gwt-PopupPanelGlass').length)))
                setTimeout(() => {
                    inp.focus()
                    select()
                }, 0)
        })
    }).observe(inp)
    // select после mouseup касания, иначе касание ставит курсор и снимает выделение
    inp.addEventListener('focus', () => setTimeout(select, 0))
    const stopSelect = () => inp.selectOnUpdate = false
    inp.addEventListener('keydown', stopSelect)
    inp.addEventListener('input', stopSelect)
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
            // Экранная клавиатура запрещена, если поле явно не попросило другой режим (options.inputMode)
            inp.inputMode = options && options.inputMode ? options.inputMode : 'none'
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

            if (options && options.selectOnShow) {
                selectOnShow(inp)
                // ACTIVATE PROPERTY для уже видимого поля (например возврат к количеству после окна «Партии»):
                // платформа помечает значение классом panel-renderer-value-focused, но фокус в само поле не ставит.
                // Фокус - при появлении класса (выделение текста - обработчик focus в selectOnShow)
                new MutationObserver(() => {
                    const focused = element.classList.contains('panel-renderer-value-focused')
                    if (focused && !element.wasFocused && document.activeElement !== inp)
                        setTimeout(() => inp.focus(), 0)
                    element.wasFocused = focused
                }).observe(element, {attributes: true, attributeFilter: ['class']})
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
            // JSON вида {value, modal} разворачиваем, остальное (числа, даты, строки) берём как есть
            const numValue = value != null && typeof value === 'object' && 'value' in value ? value.value : value
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
            if (inp.selectOnUpdate && document.activeElement === inp) inp.select()
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
