// Поле быстрого поиска в шапке ТСД (headerSearch в DataTerminalInternal.lsf).
// Текст уходит на сервер через 500 мс после последнего ввода: от 3 символов - сам текст, короче - NULL.
// Подсказку берет из атрибута data-search-placeholder html шапки (.topInfoPanel), Esc в поле - как кнопка ✕
function headerSearchInput() {
    const delay = 500
    const minLength = 3

    function headerElement(element, selector) {
        const header = element.closest('.dt-header')
        return header ? header.querySelector(selector) : null
    }

    function send(element) {
        clearTimeout(element.timer)
        const text = element.input.value.trim()
        const value = text.length >= minLength ? text : null
        if (value !== element.sentValue && element.controller) {
            element.sentValue = value
            element.controller.changeValue(value)
        }
    }

    // Поле показывается при каждом включении поиска: подсказка текущего экрана, фокус в поле
    function onShow(element) {
        const info = headerElement(element, '.topInfoPanel')
        element.input.placeholder = info && info.dataset.searchPlaceholder ? info.dataset.searchPlaceholder : 'Поиск'
        if (element.sentValue == null) element.input.value = ''
        element.input.focus()
    }

    return {
        render: (element) => {
            element.setAttribute('customType', 'headerSearchInput')

            const inp = document.createElement('input')
            inp.type = 'text'
            inp.inputMode = 'text'
            inp.autocomplete = 'off'
            inp.className = 'dt-header-search-input'
            element.append(inp)
            element.input = inp
            element.sentValue = null

            inp.addEventListener('input', () => {
                clearTimeout(element.timer)
                element.timer = setTimeout(() => send(element), delay)
            })
            // Клавиши поля не отдаем горячим клавишам формы: печатные ('1', '2', ...), Enter, Esc (Назад)
            inp.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    e.preventDefault()
                    e.stopPropagation()
                    clearTimeout(element.timer)
                    inp.value = ''
                    const close = headerElement(element, '.dt-header-search-close')
                    if (close) close.click()
                } else if (e.key === 'Enter') {
                    e.preventDefault()
                    e.stopPropagation()
                    send(element)
                } else if (e.key && e.key.length === 1 || e.key === 'Backspace' || e.key === 'Delete') {
                    e.stopPropagation()
                }
            })
            inp.oncontextmenu = (e) => {
                e.preventDefault()
                e.stopPropagation()
            }

            new IntersectionObserver((entries) => {
                if (entries.some((entry) => entry.isIntersecting)) onShow(element)
            }).observe(element)
        },
        update: (element, controller, value) => {
            element.controller = controller
            const text = value == null ? null : String(value)
            // Значение поменял сервер (✕, смена экрана) - показываем его
            if (text !== element.sentValue) {
                clearTimeout(element.timer)
                element.sentValue = text
                element.input.value = text == null ? '' : text
            }
        },
        clear: (element) => {
            clearTimeout(element.timer)
        }
    }
}
