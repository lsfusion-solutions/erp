class ResizeListener {
        #resizeObserver = new ResizeObserver(() => this.#onResize(this))
        #callback
        #element
        interval = 200
        #timeout

    constructor(element, callback, interval) {
        this.#element = element
        this.#callback = callback
        this.#resizeObserver.observe(element)
        if (interval) this.interval = interval
    }

    #onResize(listener) {
        if (listener.timeout) clearTimeout(listener.timeout)
        listener.timeout = setTimeout(listener.#callback, listener.interval, listener.#element)
    }
}
