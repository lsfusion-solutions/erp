const TILE_HANDLERS = new Map()

function clear(element) {
    while (element.lastElementChild) element.removeChild(element.lastElementChild)
}

function getHandler(value) {
    if (!value) return
    const keys = Object.keys(value)
    if (keys.length === 0) return
    return TILE_HANDLERS.get(keys[0])
}

function createLayout(element, tagName, className, onResize) {
    const layout = document.createElement(tagName ? tagName : 'div')
    if (className) layout.className = className
    element.layout = layout
    element.append(layout)
    if (!onResize) return layout

    new ResizeListener(layout, onResize)
    return layout
}

function singleTile() {
    function update(layout) {
        if (!layout) return
        clear(layout)
        const value = layout.value
        const handler = getHandler(value)
        if (handler) handler(layout, value)

    }
    return {
        render: (element) => {
            createLayout(element, 'div', 'single-tile', update)
        },
        update: (element, controller, value) => {
            const layout = element.layout
            if (!layout) return;
            layout.value = value
            update(layout)
        }
    }
}

function dashboard() {
    function handleTileClick(event) {
        const ans = JSON.parse('{ "click":' + this.index + ' }');
        const controller = this.parentNode.controller
        if (!controller.isReadOnly()) controller.changeValue(ans)
    }

    function handleTileDblClick(event) {
        const ans = JSON.parse('{ "dblclick":' + this.index + ' }');
        const controller = this.parentNode.controller
        if (!controller.isReadOnly()) controller.changeValue(ans)
    }

    function addClickListener(tile) {
        if (!tile.index) return

        tile.addEventListener('click', handleTileClick)
        tile.addEventListener('dblclick', handleTileDblClick)
    }
    function update(layout) {
        if (!layout) return
        const value = layout.value
        clear(layout)
        if (!value || !value.tiles) return

        layout.style.gridTemplateColumns = 'repeat(' + value.cols + ', 1fr)'
        layout.style.gridTemplateRows = 'repeat(' + value.rows + ', 1fr)'

        const size = value.cols * value.rows
        for (let data of value.tiles) {
            const vSpan = data.vSpan
            const hSpan = data.hSpan

            const tile = document.createElement('div')
            tile.className = 'tile'
            if (data.selected) tile.classList.add('selected')
            tile.index = data.index

            const tileContent = document.createElement('div')
            tileContent.className = 'content'
            tile.append(tileContent)

            layout.append(tile)
            addClickListener(tile)
            tile.style.gridColumn = 'span ' + hSpan
            tile.style.gridRow = 'span ' + vSpan

            const handler = getHandler(data.value)
            if (handler) handler(tileContent, data.value)
        }
    }

    return {
        render: (element) => {
            const layout = createLayout(element, 'div', 'dashboard', update)
            layout.style.display = 'grid'
        },
        update: (element, controller, value) => {
            if (!element.layout) return
            const layout = element.layout
            layout.value = value
            layout.controller = controller
            update(layout)
        }
    }
}
