TILE_HANDLERS.set('plotly', plotlyHandler)
function plotlyHandler(tile, value) {
    if (!tile.clientWidth || !tile.clientHeight) return
    const plotly = value.plotly
    Plotly.newPlot(tile, plotly.data, plotly.layout, { displayModeBar: false })
}
