function htmlText() {
    return {
        render: function( element ) {
            element.setAttribute( 'customType', 'htmlText' );
        },
        update: function( element, controller, value ) {
            element.innerHTML = value;
        }
    }
}