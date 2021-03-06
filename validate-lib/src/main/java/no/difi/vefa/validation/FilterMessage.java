package no.difi.vefa.validation;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import no.difi.vefa.message.Message;
import no.difi.vefa.message.MessageType;
import no.difi.vefa.message.ValidationType;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class can be used to filter messages from message collection. The filtering
 * is based on an XSL transformation with the XML document.
 */
public class FilterMessage {

	/**
	 * Filter messages from message collection.
	 * 
	 * @param xmlDoc XML as Document
	 * @param xslFile Path to XSL file as String
	 * @param  messages  List of messages
	 * @param  rule  What SCEMATRON rule to filter as String
	 * @return Document Result of transformation as Document
	 * @throws Exception
	 */	
	public void main(Document xmlDoc, String xslFile, List<Message> messages, String rule) {
		try {
			// Status
			Boolean status = false;
			
			// Transform XML with XSL and return status of XSL check		
			no.difi.vefa.xml.XmlXslTransformation xmlXslTransformation = new no.difi.vefa.xml.XmlXslTransformation();
			Document result = xmlXslTransformation.main(xmlDoc, xslFile);		
								
			// Get status from XML/XSL transformation			
			NodeList statusNodeList = result.getElementsByTagName("status");
			for(int i=0; i<statusNodeList.getLength(); i++){
				Node statusNode = statusNodeList.item(i);
				status = "true".equals(statusNode.getTextContent());	
			}						
						
			// If result from XSL transformation is true then remove message from message collection where title = rule
			if (status == true) {
			    for (Iterator<Message> iterator = messages.iterator(); iterator.hasNext();) {
			        Message message = iterator.next();
			        
			        if (message.schematronRuleId.equals(rule)) {
			        	iterator.remove();
			        }
			    }			
			}			
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			String exceptionAsString = sw.toString();
			
			Message message = new Message();
			message.validationType = ValidationType.Filter;
			message.messageType = MessageType.Fatal;
			message.title = e.getMessage();
			message.description = exceptionAsString;			
			messages.add(message);
		}
	}
}
