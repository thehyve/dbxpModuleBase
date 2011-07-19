package org.dbxp.moduleBase

class DataTablesTagLib {
    static namespace = 'dt';

    def dataTable = {attrs, body ->

        // TODO: add comment

        // id is required
        if(attrs.id == null)
            throwTagError("Tag [datatablesForm] is missing required attribute [id]");

        out << "<form name='"+attrs.id+"_form' id='"+attrs.id+"_form'>";

        Map mapInputs = attrs.inputs;
        mapInputs.each { item ->
            out << "<input type='hidden' id='"+item.key +"' value='"+ item.value +"' />";
        }
        out << "</form>";

        String strClass="";
        if(!(attrs.class == null))
            strClass = " " + attrs.class;

        out << "<table id='"+attrs.id+"_table' class='datatables"+strClass+"' rel='" + attrs.rel + "'>";
        out << body{};
        out << "</table>";
    }

    def buttonsViewEditDelete = {attrs ->
        // This tag generates 3 default buttons for each row (show, edit, delete)
        //
        // Usage:
        // <g:buttonsViewEditDelete controller="measurement" id="${measurementInstance.id}"/>
        // <g:buttonsViewEditDelete controller="measurement" id="${measurementInstance.id}" mapEnabled="[blnShow: true, blnEdit: false, blnDelete: true]" />

        // id is required
        if(attrs.id == null)
            throwTagError("Tag [buttonsViewEditDelete] is missing required attribute [id]");

        // controller is required
        if(attrs.controller == null)
            throwTagError("Tag [buttonsViewEditDelete] is missing required attribute [controller]");

        // mapEnabled is optional
        if(attrs.mapEnabled == null) {
            // By default all buttons are enabled
            attrs.mapEnabled = [blnShow: true, blnEdit: true, blnDelete: true];
        }

        // create show button
        out << "<td class='buttonColumn'>";
        if(attrs.mapEnabled.blnShow) {
            out << g.link(action:"show", class:"show", controller:attrs.controller, id:attrs.id, "<img src=\"${fam.icon( name: 'magnifier')}\" alt=\"show\"/>");
        } else {
            out << "<img class='disabled' src=\"${fam.icon( name: 'magnifier')}\" alt=\"show\"/>";
        }
        out << "</td>";

        // create edit button
        out << "<td class='buttonColumn'>";
        if(attrs.mapEnabled.blnEdit) {
            out << g.link(action:"edit", class:"edit", controller:attrs.controller, id:attrs.id, "<img src=\"${fam.icon( name: 'pencil')}\" alt=\"edit\"/>");
        } else {
            out << "<img class='disabled' src=\"${fam.icon( name: 'pencil')}\" alt=\"edit\"/>";
        }
        out << "</td>";

        // create delete button
        out << "<td class='buttonColumn'>";
        if(attrs.mapEnabled.blnDelete) {
            out << "<form id='"+attrs.controller+"_"+attrs.id+"_deleteform' name='"+attrs.controller+"_"+attrs.id+"_deleteform' method='post' action='delete'>";
            out << g.link(action:"delete", class:"delete", onclick:"if(confirm('${message(code: 'default.button.delete.confirm.message', default: 'Are you sure?')}')) {\$('#${attrs.controller}_${attrs.id}_deleteform').submit(); return false;} else {return false;} ;", controller:attrs.controller, "<img src=\"${fam.icon( name: 'delete')}\" alt=\"delete\"/>");
            out << "<input type='hidden' name='ids' value='"+attrs.id+"' />";
            out << "</form>";
        } else {
            out << "<img class='disabled' src=\"${fam.icon( name: 'delete')}\" alt=\"delete\"/>";
        }
        out << "</td>";

	}

    def buttonsHeader = {attrs ->
        // This tag generates a number of empty headers (default=3)
        //
        // Usage:
        // <g:buttonsHeader/>
        // <g:buttonsHeader numColumns="2"/>

        if(attrs.numColumns == null) {
            // Default = 3
            attrs.numColumns = 3;
        } else {
            attrs.numColumns = Integer.valueOf(attrs.numColumns);
        }

        for(int i=0; i<attrs.numColumns; i++) {
            // Create empty header
            out << '<th class="nonsortable buttonColumn"></th>';
        }

    }
}
