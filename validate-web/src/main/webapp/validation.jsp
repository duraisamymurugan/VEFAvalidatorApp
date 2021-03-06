<%@ page language="java" pageEncoding="utf-8" contentType="text/html; charset=utf-8" %>
<jsp:include page="includes/html_start.jsp"/>
<jsp:include page="includes/head.jsp"/>
<body>

<jsp:include page="includes/top.jsp"/>

<jsp:include page="includes/header.jsp"/>

<jsp:include page="includes/menu.jsp"/>

<div class="container main">

	<div class="row submenues">
	   <!-- the submenus in the nav element are moved to this element with Javascript for none mobil screens. -->
	</div><!-- /.row submenues -->

	<div class="row">
		<div class="twelvecol last">
			<p>This tool allows you to validate an XML sample against different sets of business rules. </p>

			<ol>
				<li><em>Choose the business rule set you wish to validate against from the select box</em></li>
				<li><em>Copy and paste the XML into the text input box</em></li>
				<li><em>Click the 'Validate XML' button</em></li>
				<li><em>The results of the validation will be displayed underneath the text input box</em></li>
			</ol>

			<p>Please note that this tool validates whether the submitted text is well-formed XML, but it does not validate against an XSD Schema.</p>
			<!--p>Currently there are validations for the Invoice and Credit Note documents.</p-->


			<form id="xmlSourceForm" action="#">

				<label for="xsltSelect">Validation artifact:</label>
				<select id="xsltSelect"></select>

				<label for="xmlTextSource">Paste XML data:</label>
				<textarea id="xmlTextSource" rows="10" cols="100"></textarea>
			</form>

			<button id="readFileButton">Validate XML</button>

			<div id="transformResult"></div>

		</div><!-- /.ninecol -->
	</div><!-- /.row -->

</div><!-- /.container main -->

<jsp:include page="includes/footer.jsp"/>

<jsp:include page="includes/js.jsp"/>

<script type="text/javascript">
	$(document).ready(function(){
		var wsUrl = '/validate-ws';
		
		// Get available versions
		$.ajax({
			url: wsUrl,
			dataType: 'xml',
			success: getVersions,
			async: false
		});				
		
		function getVersions(xml){
			// Get versions and add to array
			var versions = [];
			
			$(xml).find('version').each(function(){
				var version = $(this).text();
				
				versions.push({
					version: version
				});
			});
			
			// Sort array descending to get highest versions first, eg. 1.5, 1.4 etc
			//versions.sort();
			versions.reverse();
			
			// For each version get available schemas
		    jQuery.each(versions, function(i, val) {
				var version = versions[i].version;

				// Get available schemas for version
				$.ajax({
					url: wsUrl + '/' + version,
					dataType: 'xml',
					success: getSchemas,
					async: false
				});
				
				function getSchemas(xml){
					$(xml).find('schema').each(function(){
						var id = $(this).attr('id');
						var href = $(this).attr('xlink:href');												
						
						var name = '';
						$(this).find('name').each(function(){
							name = $(this).find('en').text();														 
						});
						
						$('#xsltSelect').append($("<option></option>").attr("value",version + '/' + id).text(version + ' - ' + name));
					});			
				};
		    });									
		}		
		
		// Send XML to ws and prosess result
		$("#readFileButton").click(function() {
			var r = '';
			
			if ($('#xmlTextSource').val() == '') {
				r = '<div style="height: 20px;"></div>';
				r += '<h2>No XML data</h2>';
				r += '<h3 style="color: red;">No xml data detected!</h3>';
				r += '<p>Please enter some XML data to be validated!</p>';
				$('#transformResult').html(r);
				return false;
			}
			
			// Empty result and display wait text
			r = '<div style="height: 20px;"></div>';
			r += '<h2>' + $('#xsltSelect :selected').text() + '</h2>';
			r += '<h3>Waiting for transformation result!</h3>';
			r += '<p>Please be patient:-)</p>';			
			$('#transformResult').html(r);
			
			// Get result
			var url = wsUrl + '/' + escape($('#xsltSelect :selected').val());
			
			$.ajax({
				url: url,
				dataType: 'xml',
				type: 'POST',
				data: $('#xmlTextSource').val(),
				//data: '<test></test>',
				processData: false,
				contentType: 'application/xml',
				success: getResult
			});			
		});		
		
		function getResult(xml){
			var rOuter = '<div style="height: 20px;"></div>';
			rOuter += '<h2>' + $('#xsltSelect :selected').text() + '</h2>';
			var rInner = '';
			
			$(xml).find('message').each(function(){								
				var schema = $(this).attr('schema');
				var validationType = $(this).attr('validationType');
				var version = $(this).attr('version');
				
				var messageType = $(this).find('messageType').text();
				var title = $(this).find('title').text();
				var description = $(this).find('description').text();	
				
				var style = '';
				var messageTypeTitle = '';
				
				if (messageType == 'FatalError') {
					style = 'color: red;'
						messageTypeTitle = 'Error';
				} else if (messageType == 'Fatal') {
					style = 'color: red;'
						messageTypeTitle = 'Error';
				} else if (messageType == 'Warning') {
					style = 'color: black;'
						messageTypeTitle = 'Warning';
				}
				
				rInner += '<h3 style="' + style + '" title="' + messageTypeTitle + '">' + messageTypeTitle + ': ' + title + '</h3>';
				rInner += '<p>' + description + '</p>';
			});
			
			if (rInner.length == 0) {
				rInner = "<h3 style=\"color: green;\">Document is valid</h3>";
			}
			
			$('#transformResult').html(rOuter + rInner);
		};
	});
</script>
<!-- end scripts -->

</body>
<jsp:include page="includes/html_stop.jsp"/>