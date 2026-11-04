function htmlText() {
    return {
        render: function( element ) {
            element.setAttribute( 'customType', 'htmlText' );
        },
        update: function( element, controller, value ) {
            element.innerHTML = value;
            if ( element.querySelector( 'div.dialog-header' ) )
                markDialogWithHeader( element, 20 );
        }
    }
}

// Окно диалога с шапкой dialog-header помечается классом dt-dialog (стили - в dataTerminalDocuments.css).
// Вместо селектора .modal-dialog:has(div.dialog-header): :has() в WebView ТСД до Chrome 105 не поддерживается.
// Значение может прийти раньше, чем форма попадет в окно, - тогда повторяем, пока окно не появится
function markDialogWithHeader( element, attempts ) {
    const dialog = element.closest( '.modal-dialog' );
    if ( dialog )
        dialog.classList.add( 'dt-dialog' );
    else if ( attempts > 0 )
        setTimeout( () => markDialogWithHeader( element, attempts - 1 ), 50 );
}