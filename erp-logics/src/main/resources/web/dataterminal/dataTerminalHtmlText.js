function htmlText() {
    return {
        render: function( element ) {
            element.setAttribute( 'customType', 'htmlText' );
            // Касание миниатюры товара (img.sku-image, DataTerminalSkuImage.lsf) открывает окно изображения:
            // id товара из data-sku уходит в обработчик свойства-триггера dtShowSkuImage формы.
            // Обработчик один на элемент: innerHTML заменяется при каждом update
            element.addEventListener( 'click', function( event ) {
                const image = event.target.closest && event.target.closest( 'img.sku-image[data-sku]' );
                const controller = element.dtController;
                if ( !image || !controller || !controller.form )
                    return;
                event.stopPropagation();
                controller.form.changeProperty( 'dtShowSkuImage', Number( image.dataset.sku ) );
            } );
        },
        update: function( element, controller, value ) {
            element.dtController = controller;
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