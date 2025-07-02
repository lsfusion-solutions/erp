TILE_HANDLERS.set('html', htmlHandler)

function htmlHandler(tile, value) {
    const html = value.html
    if (!html) return
    if(html.className) tile.classList.add(html.className)
    tile.innerHTML = html.text;
}
