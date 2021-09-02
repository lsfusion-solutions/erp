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

function chartMobileMode() {
    return {
        plotly: {
            yaxis: {
                title: '',
                tickangle: 90,
                automargin: true,
            },
            showlegend: true,
            legend: {
                "orientation": "h"
            }
        }
    }
}

function hideTitleHideLegend() {
    return {
        plotly: {
            yaxis: {
                title: '',
                tickangle: 90,
                automargin: true,
            }
        }
    }
}