function horizontalLegend() {
    return {
        plotly: {
            showlegend: true,
            legend: {
                "orientation": "h"
            },
            modebar: {
                orientation: "v"
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


function testConfig() {
    return{
        plotly: {
            title: "Покупатели",
            hovermode: "closest",
            hoverlabel: { bgcolor: "#FFF" },
            showlegend: false,
            xaxis: {
              title: "Количество чеков",
              zeroline: false
            },
            yaxis: {
              title: "",
            },
            modebar: {
                orientation: "v"
            }
        }
    }
}

function turnoverByGroup() {
    return{
        plotly: {
            title: "Оборачиваемость по группам",
            hovermode: "closest",
            hoverlabel: { bgcolor: "#FFF" },
            showlegend: false,
            xaxis: {
              title: "",
            },
            yaxis: {
              title: ""
            },
            modebar: {
                orientation: "v"
            }
        }
    }
}

function paymentType() {
    return{
        plotly: {
            title: "Виды оплат",
            hovermode: "closest",
            hoverlabel: { bgcolor: "#FFF" },
            showlegend: false,
            xaxis: {
              title: "Сумма в руб.",
              zeroline: false
            },
            yaxis: {
              title: "",
            }
        }
    }
}

function averageReceipt() {
    return{
        plotly: {
            title: "Средний чек",
            hovermode: "closest",
            hoverlabel: { bgcolor: "#FFF" },
            showlegend: false,
            xaxis: {
              title: "Сумма в руб.",
              zeroline: false
            },
            yaxis: {
              title: "",
            },
            modebar: {
                orientation: "v"
            }
        }
    }
}

function hideTitleHideLegend() {
    return {
        plotly: {
            title: 'Покупатели',
            showlegend: false
        }
    }
}
