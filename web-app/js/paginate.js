/*
	Usage:

	Use a 'paginate' class on a table to create a paginated table using datatables plugin.

		<table class="paginate">
			<thead>
				<tr><th>Name</th><th># samples</th></tr>
			</thead>
			<tbody>
				<tr><td>Robert</td><td>182</td></tr>
				<tr><td>Siemen</td><td>418</td></tr>
			</tbody>
		</table>

	will automatically create a paginated table, without any further actions. The pagination
	buttons will only appear if there is more than 1 page.

	
	Serverside tables:
	
	When you have a table with lots of rows, creating the HTML table can take a while. You can also 
	create a table where the data for each page will be fetched from the server. This can be done using
	  
		<table class="paginate serverside" rel="/url/to/ajaxData">
			<thead>
				<tr><th>Name</th><th># samples</th></tr>
			</thead>
		</table>
	
	Where the /url/to/ajaxData is a method that returns the proper data for this table. See 
	http://www.datatables.net/examples/data_sources/server_side.html for more information about this method.	
 */

var numElements = new Array();		// Hashmap with the key being the id of the table, in order to facilitate multiple tables
var elementsSelected = new Array();	// Hashmap with the key being the id of the table, in order to facilitate multiple tables
var tableType = new Array();		// Hashmap with the key being the id of the table, in order to facilitate multiple tables	
var allElements = new Array();		// Hashmap with the key being the id of the table, in order to facilitate multiple tables

function initializePagination( selector ) {
	if( selector == undefined ) {
		selector = ''
	}
	
	// Initialize default pagination
	$( selector + ' table.paginate:not(.serverside)').each(function(idx, el) {
		var $el = $(el);
		
		tableType[ $el.attr( 'id' ) ] = "clientside";
		
		$el.dataTable({ 
			bJQueryUI: true, 
			bAutoWidth: false,
			bFilter: false, 
			bLengthChange: false, 
			iCookieDuration: 86400,				// Save cookie one day
			sPaginationType: 'full_numbers',
			iDisplayLength: 10,					// Number of items shown on one page.
			aoColumnDefs: [
				{ "bSortable": false, "aTargets": ["nonsortable"] },				// Disable sorting on all columns with th.nonsortable
				{ "sSortDataType": "formatted-num", "aTargets": ["formatted-num"] }	// Make sorting possible on formatted numbers
			]						
		});
	});

	// Initialize serverside pagination
	$( selector + ' table.paginate.serverside').each(function(idx, el) {
		var $el = $(el);
		
		// Determine data url from rel attribute
		var dataUrl = $el.attr('rel');
		var id = $el.attr( 'id' );
		
		tableType[ id ] = "serverside";
		elementsSelected[ id ] = new Array();
		
		$el.dataTable({ 
			"bProcessing": true,
			"bServerSide": true,
			"sAjaxSource": dataUrl,
			sDom: '<"H"lf>rt<"F"ip>',

			bJQueryUI: true, 
			bAutoWidth: false,
			bFilter: false, 
			bLengthChange: false, 
			iCookieDuration: 86400,				// Save cookie one day
			sPaginationType: 'full_numbers',
			iDisplayLength: 10,					// Number of items shown on one page.
			aoColumnDefs: [
				{ "bSortable": false, "aTargets": ["nonsortable"] },				// Disable sorting on all columns with th.nonsortable
				{ "sSortDataType": "formatted-num", "aTargets": ["formatted-num"] }	// Make sorting possible on formatted numbers
			],
			
			// Override the fnServerData in order to show/hide the paginated
			// buttons if data is loaded
			"fnServerData": function ( sSource, aoData, fnCallback ) {
				$.ajax( {
					"dataType": 'json', 
					"type": "POST", 
					"url": sSource, 
					"data": aoData, 
					"success": function( data, textStatus, jqXHR ) {
						fnCallback( data, textStatus, jqXHR );
						showHidePaginatedButtonsForTableWrapper( $el.parent() );
						
						// Save total number of elements
						numElements[ id ] = data[ "iTotalRecords" ];
						allElements[ id ] = data[ "aIds" ];
						
						// Find which checkboxes are selected
						checkSelectedCheckboxes( $el.parent() );
					}
				} );
			}			
		});
	});
	
	// Initialize tables that should be sortable, but not paginated
	$( selector + ' table.sortable').each(function(idx, el) {
		var $el = $(el);
		
		$el.dataTable({ 
			bJQueryUI: true, 
			bAutoWidth: false,
			bFilter: false, 
			bLengthChange: false, 
			bPaginate: false,
			iCookieDuration: 86400,				// Save cookie one day
			sScrollY: '350px',
			sScrollX: $el.hasClass( 'scrollX' ) ? '100%' : '',
			bScrollCollapse: true,
			aoColumnDefs: [
				{ "bSortable": false, "aTargets": ["nonsortable"] },			// Disable sorting on all columns with th.nonsortable
				{ "sType": "formatted-num", "aTargets": ["formatted_num"] }		// Make sorting possible on formatted numbers
			]						
		});
	});	
	
	// Show hide paginated buttons
	showHidePaginatedButtons( selector );
}

function showHidePaginatedButtons( selector ) {
	// Remove the top bar of the datatable and hide pagination with only one page
	$( selector + " .dataTables_wrapper").each(function(idx, el) {
		var $el = $(el);
		showHidePaginatedButtonsForTableWrapper( $el )
	});	
}

function showHidePaginatedButtonsForTableWrapper( el ) {
	// Hide pagination if only one page is present (that is: if no buttons can be clicked)
	if(el.find('span span.ui-state-default:not(.ui-state-disabled)').size() == 0 ){
		el.find('div.fg-toolbar').css( 'display', 'none' );
	} else {
		el.find('div.fg-toolbar').css( 'display', 'block' );
		el.find( 'div.ui-toolbar' ).first().hide();
		
		// Check whether a h1, h2 or h3 is present above the table, and move it into the table
		/*
		var $previousElement = $el.prev();
		if( $previousElement != undefined && $previousElement.get(0) != undefined ) {
			var tagName = $previousElement.get(0).tagName.toLowerCase();
			if( tagName == "h1" || tagName == "h2" || tagName == "h3" ) {
				// Put the margin that was on the title onto the table
				$el.css( "margin-top", $previousElement.css( "margin-top" ) );
				$previousElement.css( "margin-top", '4px' );
				$previousElement.css( "marginBottom", '4px' );

				// If so, move the element into the table
				$previousElement.remove();
				$el.find( 'div.ui-toolbar' ).first().append( $previousElement );
			}
		}
		*/						
	}	
}

/**
 * Check selectboxes that had been selected before
 * @param wrapper
 */
function checkSelectedCheckboxes( wrapper ) {
	var inputsOnScreen = $( 'input[type=checkbox]', $(wrapper) );
	var tableId = $( ".paginate", $(wrapper) ).attr( 'id' );
	
	for( var i = 0; i < inputsOnScreen.length; i++ ) {
		var input = $(inputsOnScreen[ i ] );
		if( input.attr( 'id' ) != "checkAll" ) {
			if( jQuery.inArray( parseInt( input.val() ), elementsSelected[ tableId ] ) > -1 ) {
				input.attr( 'checked', true );
			} else {
				input.attr( 'checked', false );
			}
		}
	}
}

function checkAllPaginated( input ) {
	var paginatedTable = $(input).closest( '.paginate' );
	var table_id = paginatedTable.attr( 'id' );
	
	switch( tableType[ table_id ] ) {
		case "clientside":	return checkAllPaginatedClientSide( paginatedTable );
		case "serverside":	return checkAllPaginatedServerSide( paginatedTable );
	}
}

function checkAllPaginatedClientSide( paginatedTable ) {
	var checkAll = $( '#checkAll', paginatedTable );
	
	var oTable = paginatedTable.dataTable();
	var inputs = $('input[type=checkbox]', oTable.fnGetNodes())
	
	// If any of the inputs is checked, uncheck all. Otherwise, check all
	var check = false;
	
	for(var i = 0; i < inputs.length; i++ ) {
		if( !$(inputs[i]).attr( 'checked' ) ) {
			check = true;
			break;
		}
	}
	
	inputs.each( function( idx, el ) {
		$(el).attr( 'checked', check );
	})
	
	updateCheckAllClientSide( checkAll );
}

function checkAllPaginatedServerSide( paginatedTable ) {
	var tableId = paginatedTable.attr( 'id' );
	var checkAll = $( '#checkAll', paginatedTable );
	
	// If everything is selected, the action is to deselect everything. Otherwise
	// select everything
	if( numElements[ tableId ] == elementsSelected[ tableId ].length ) {
		elementsSelected[ tableId ] = new Array();
	} else {
		// Otherwise, select everything. We make a copy of the allElements list, because we 
		// need to copy by value
		elementsSelected[ tableId ] = new Array();
		$.each( allElements[ tableId ] , function( idx, value ) {
			elementsSelected[ tableId ][ elementsSelected[ tableId ].length ] = value;
		});
	}
	
	checkSelectedCheckboxes( paginatedTable.parent() );
	updateCheckAll( checkAll );
}

function updateCheckAll( input ) {
	var paginatedTable = $(input).closest( '.paginate' );
	
	// Determine type
	var tableId = paginatedTable.attr( 'id' );
	
	switch( tableType[ tableId ] ) {
		case "clientside":	return updateCheckAllClientSide( input );
		case "serverside":	return updateCheckAllServerSide( input );
	}
}

function updateCheckAllClientSide( input ) {
	var paginatedTable = $(input).closest( '.paginate' );
	var dataTable = paginatedTable.closest( '.dataTables_wrapper' );
	
	var checkAll = $( '#checkAll', paginatedTable );
	
	var oTable = paginatedTable.dataTable();
	var inputs = $('input[type=checkbox]', oTable.fnGetNodes())
	
	// Is none checked, are all checked or are some checked
	var numChecked = 0
	for(var i = 0; i < inputs.length; i++ ) {
		if( $(inputs[i]).attr( 'checked' ) ) {
			numChecked++;
		}
	}
	
	checkAll.attr( 'checked', numChecked > 0 );
	
	if( numChecked > 0 && numChecked < inputs.length ) {
		checkAll.addClass( 'transparent' );
	} else {
		checkAll.removeClass( 'transparent' );
	}
}

function updateCheckAllServerSide( input ) {
	var paginatedTable = $(input).closest( '.paginate' );
	var dataTable = paginatedTable.closest( '.dataTables_wrapper' );
	var tableId = paginatedTable.attr( 'id' );
	
	// If the input is a normal checkbox, the user clicked on it. Update the elementsSelected array
	if( $(input).attr( 'id' ) != "checkAll" ) {
		var arrayPos = jQuery.inArray( parseInt( $(input).val() ), elementsSelected[ tableId ] );
		if( $(input).attr( 'checked' ) ) {
			// Put the id in the elementsSelected array, if it is not present
			if( arrayPos == -1 ) {
				elementsSelected[ tableId ][ elementsSelected[ tableId ].length ] = parseInt( $(input).val() );
			}
		} else {
			// Remove the id from the elementsSelected array, if it is present
			if( arrayPos > -1 ) {
				elementsSelected[ tableId ].splice( arrayPos, 1 );
			}
		}
	}
	
	var checkAll = $( '#checkAll', paginatedTable );
	
	checkAll.attr( 'checked', elementsSelected[ tableId ].length > 0 );
	
	if( elementsSelected[ tableId ].length > 0 && elementsSelected[ tableId ].length < numElements[ tableId ] ) {
		checkAll.addClass( 'transparent' );
	} else {
		checkAll.removeClass( 'transparent' );
	}
}

$(function() { initializePagination(); });
