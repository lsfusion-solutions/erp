function hideLegend() {
    return {
        plotly: {
            showlegend: false
        }
    }
}

function horizontalLegend() {
    return {
        plotly: {
            showlegend: true,
            legend: {
                "orientation": "h"
            }
        }
    }
}