function checkMobileDataTerminal() {
    return typeof MobileDataTerminal !== 'undefined'
}

function run(func) {
    if (!checkMobileDataTerminal()) return
    try {
        func.apply()
        return true
    } catch (e) {
        return false
    }
}

function showToast(text) {
    return run(() => MobileDataTerminal.showToast(text))
}

function showMessage(text) {
    return run(() => MobileDataTerminal.showMessage(text))
}

function captureImage(tag) {
    return run(() => MobileDataTerminal.captureImage(tag))
}

function getApplicationInfo() {
    if (!checkMobileDataTerminal()) return;
    return JSON.parse(MobileDataTerminal.getAppInfo())
}

function playRingtone() {
    if (!checkMobileDataTerminal()) return;
    return run(() => MobileDataTerminal.playRingtone())
}

function playAlarm() {
    if (!checkMobileDataTerminal()) return;
    return run(() => MobileDataTerminal.playAlarm())
}

function playNotify() {
    if (!checkMobileDataTerminal()) return;
    return run(() => MobileDataTerminal.playNotify())
}

// Звук ошибки. Временная реализация через Web Audio API, поскольку
// в текущем mobileclient метода MobileDataTerminal.playError() еще нет.
// Когда метод появится — заменить тело на: return run(() => MobileDataTerminal.playError())
function playError() {
    var AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) return;
    try {
        var ctx = new AudioCtx();
        function beep(freq, startOffset, duration) {
            var osc = ctx.createOscillator();
            var gain = ctx.createGain();
            osc.type = 'square';
            osc.frequency.value = freq;
            osc.connect(gain);
            gain.connect(ctx.destination);
            var t = ctx.currentTime + startOffset;
            gain.gain.setValueAtTime(0.0001, t);
            gain.gain.exponentialRampToValueAtTime(0.5, t + 0.005);
            gain.gain.exponentialRampToValueAtTime(0.0001, t + duration);
            osc.start(t);
            osc.stop(t + duration + 0.02);
        }
        beep(700, 0,    0.15);
        beep(350, 0.18, 0.25);
        setTimeout(function () { try { ctx.close(); } catch (e) {} }, 800);
    } catch (e) { /* silent fallback */ }
}

function stopRingtone() {
    if (!checkMobileDataTerminal()) return;
    return run(() => MobileDataTerminal.stopRingtone())
}

function waitBarcode(tag, callback) {
    if (!checkMobileDataTerminal()) return
    return run(() => MobileDataTerminal.waitBarcode(tag, callback))
}

function stopWaitingBarcode() {
    if (!checkMobileDataTerminal()) return
    return run(() => MobileDataTerminal.stopWaitingBarcode())
}

function alwaysFocused(element) {
    element.addEventListener('blur', () => {
        if (onTop(element)) element.focus()
    })

    const options = {attributes: true}

    const callback = (entries) => {
        const entry = entries.find((it) => it.target = element)
        if (entry === undefined) return

        if (entry.intersectionRatio) element.focus()
    }

    const observer = new IntersectionObserver(callback, options)
    observer.observe(element)
}

function onTop(element) {
    const domRect = element.getBoundingClientRect()
    const x = (domRect.left + domRect.right) / 2
    const y = (domRect.top + domRect.bottom) / 2
    return element === document.elementFromPoint(x, y)
}

function inputBarcode() {
    return {
        render: (element) => {
            element.setAttribute('customType', 'inputBarcode')
            element.id = Date.now() + Math.random()

            const inp = document.createElement('input')
            inp.type = 'text'
            // Ручной ввод штрихкода - цифровая виртуальная клавиатура ('none' прятал ее совсем).
            // type остается text: ведущие нули и не цифровые символы аппаратного сканера не теряются
            inp.inputMode = 'numeric'
            element.append(inp)
            alwaysFocused(inp)
            element.inp = inp

            inp.addEventListener('focus', () => waitBarcode(element.id, 'inputBarcode().callback'))
            inp.addEventListener('blur', () => stopWaitingBarcode())
            inp.addEventListener('input', () => element.controller.changeValue(inp.value))
        }, update: (element, controller, value) => {
            element.controller = controller
            element.inp.value = value
        }, clear: (element) => {

        }, callback: (tag, value) => {
            // Вызывается Android-клиентом на broadcast аппаратного сканера (action и extra - в настройках клиента).
            // Скан - законченный ввод: завершаем значение переводом строки, чтобы defReadBarcode
            // обработал его сразу (wordCount(s, '\n') > 1), без нажатия OK. Свой суффикс сканера (\r, \n) отбрасываем
            const element = document.getElementById(tag)
            if (element) {
                element.controller.changeValue(String(value).replace(/[\r\n]+$/, '') + '\n')
                waitBarcode(element.id, 'inputBarcode().callback')
            }
        }
    }
}

// Поле штрихкода на экране с другими полями ввода (добавление строки документа ТСД сканированием).
// В отличие от inputBarcode фокус не удерживает (он в полях строки), а сканер слушает все время, пока поле видно
// на экране, - скан работает и во время ввода количества. Скан и Enter в поле - законченный ввод: значение
// с завершающим '\n', как у inputBarcode
function inputBarcodeScan() {
    return {
        render: (element) => {
            element.setAttribute('customType', 'inputBarcode')
            element.id = Date.now() + Math.random()

            const inp = document.createElement('input')
            inp.type = 'text'
            inp.inputMode = 'numeric'
            // Подсказка в пустом поле
            inp.placeholder = 'Отсканируйте штрихкод товара'
            element.append(inp)
            element.inp = inp

            new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.intersectionRatio > 0) {
                        element.waiting = true
                        waitBarcode(element.id, 'inputBarcodeScan().callback')
                    } else if (element.waiting) {
                        element.waiting = false
                        stopWaitingBarcode()
                    }
                })
            }).observe(inp)

            // Набранный текст - на сервер по мере ввода (для кнопки «↵» справа от поля)
            inp.addEventListener('input', () => {
                if (element.controller) element.controller.changeValue(inp.value)
            })
            inp.addEventListener('keydown', (e) => {
                const charCode = e.which ? e.which : e.keyCode
                if (charCode === 13) {
                    // Enter - ручной ввод закончен; горячей клавише формы (Enter - «Далее») не отдаем
                    e.preventDefault()
                    e.stopPropagation()
                    const value = inp.value.trim()
                    if (value && element.controller) {
                        inp.value = ''
                        element.controller.changeValue(value + '\n')
                    }
                } else if (e.key && e.key.length === 1) {
                    // Цифры не отдаем горячим клавишам формы
                    e.stopPropagation()
                }
            })
        }, update: (element, controller, value) => {
            element.controller = controller
            // Набранный текст хранится на сервере до обработки, после скана / Enter / «↵» приходит NULL - поле очищается
            const text = value ? String(value).replace(/[\r\n]+$/, '') : ''
            if (element.inp.value !== text) element.inp.value = text
        }, clear: (element) => {

        }, callback: (tag, value) => {
            const element = document.getElementById(tag)
            if (element && element.controller) {
                element.inp.value = ''
                element.controller.changeValue(String(value).replace(/[\r\n]+$/, '') + '\n')
                waitBarcode(element.id, 'inputBarcodeScan().callback')
            }
        }
    }
}
