package no.difi.vefa.validation;

import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import no.difi.vefa.configuration.Configuration;
import no.difi.vefa.logging.StatLogger;
import no.difi.vefa.message.Hint;
import no.difi.vefa.message.Message;
import no.difi.vefa.message.MessageType;
import no.difi.vefa.message.ValidationType;
import no.difi.vefa.properties.PropertiesFile;
import no.difi.vefa.xml.Utils;

/**
 * This class is used to validate a xml document according to the
 * given configuration.
 */
public class Validate {
	/**
	 * Schema to validate XML against.
	 */
	public String schema;
	
	/**
	 * Version to validate XML against.
	 */
	public String version;
	
	/**
	 * XML to validate.
	 */
	public String xml;

	/**
	 * Path to properties file.
	 */	
	public String pathToPropertiesFile = "/etc/opt/VEFAvalidator/validator.properties";

	/**
	 * Should the current validation suppress warnings from output? Default false.
	 */	
	public boolean suppressWarnings = false;
	
	/**
	 * Collection of messages generated by the validation.
	 */
	public List<Message> messages = new ArrayList<Message>();

	/**
	 * Is the current XML valid?.
	 */		
	public boolean valid;

	/**
	 * Is the current XML valid? Default false;
	 */
	public boolean autodetectSchema = false;
	
	/**
	 * Properties file as PropertiesFile object.
	 */	
	private PropertiesFile propertiesFile;
			
	
	/**
	 * Executes a validation according to the given configuration
	 * and adds messages to the message collection.
	 * 
	 * @throws Exception 
	 */
	public void main() throws Exception {
		// Setup
		Utils utils = new Utils();
		Configuration configuration = new Configuration();
		
		// Set Saxon as XML parser
		System.setProperty("javax.xml.transform.TransformerFactory","net.sf.saxon.TransformerFactoryImpl");		
		
		// Load properties file
		this.propertiesFile = this.getPropertiesFile();		
		
		// Always hide warnings?
		this.hideWarnings();		
		
		// Check if XML string is well formed
		if (this.isXMLWellFormed() == false) {
			return;
		}
						
		// Load XML string as XML DOM		
		Document xmlDoc = utils.stringToXMLDOM(this.xml);
		
		// Autodetect schema?
		if (this.tryToAutodetectSchema(xmlDoc) == false) {
			return;
		}
				
		// Get validations from config files	
		NodeList standardValidates = this.getConfigurationValidation(configuration, utils, this.propertiesFile.dataDir + "/STANDARD/config.xml");							
		NodeList customValidates = this.getConfigurationValidation(configuration, utils, this.propertiesFile.dataDir + "/CUSTOM/config.xml");
						
		// We have not found anything in configuration to validate against
		if (this.doesConfigurationContainValidationDefinitions(standardValidates, customValidates) == false) {
			return;
		}
		
		// Perform validation
		this.validation(standardValidates, xmlDoc);
		this.validation(customValidates, xmlDoc);
		
		// Set valid attribute
		this.setIsValid();
		
		// Log statistics
		this.logStat();
	}
	
	/**
	 * Loads the application properties file
	 * 
	 * @return PropertiesFile
	 * @throws Exception 
	 */	
	private PropertiesFile getPropertiesFile() throws Exception {
		PropertiesFile propFile = new PropertiesFile();
		propFile.main(this.pathToPropertiesFile);
		return propFile;
	}
	
	/**
	 * Checks if supperss warnings is set in properties files.
	 * If so...set suppresswarning properties.
	 */
	private void hideWarnings() {
		if (this.propertiesFile.suppressWarnings == true) {
			this.suppressWarnings = true;
		}
	}
	
	/**
	 * Checks if XML is well formed.
	 * 
	 * @return Boolean
	 */	
	private Boolean isXMLWellFormed() {
		WellFormed wellFormed = new WellFormed();
		if (wellFormed.main(this.xml, this.messages) == false) {
			return false;
		}
		return true;
	}
	
	/**
	 * Tries to read the XML file and auto detect schema.
	 * 
	 * @return Boolean
	 * @throws Exception 
	 */		
	private Boolean tryToAutodetectSchema(Document xmlDoc) throws Exception {
		Boolean r = true;
		
		if (this.autodetectSchema == true) {
			DetectSchema detectSchema = new DetectSchema();
			detectSchema.setSchemaIdentifier(xmlDoc, this.version, this.messages);
			this.schema = detectSchema.schema;

			// No schema is detected
			if (detectSchema.detected == false) {
				r = false;
			}			
		}
		return r;		
	}
	
	/**
	 * Read a configuration file and extracts validations as NodeList
	 * 
	 * @return NodeList
	 * @throws Exception 
	 */		
	private NodeList getConfigurationValidation(Configuration configuration, Utils utils, String config) throws Exception {
		Document xmlDoc = configuration.fileToXMLDOM(config, this.propertiesFile);
		NodeList validates = utils.xmlDOMXPathQuery(xmlDoc, "/config/validate[@id='" + this.schema + "' and @version='" + this.version + "']");
		return validates;
	}	
	
	/**
	 * Checks if standard and custom validation files contain any validation definitions 
	 * for given version and schema.
	 * 
	 * @return Boolean
	 */		
	private Boolean doesConfigurationContainValidationDefinitions(NodeList standardValidates, NodeList customValidates) {
		Boolean r = true;
		
		if (standardValidates.getLength() == 0 && customValidates.getLength() == 0) {
			Message message = new Message();
			message.validationType = ValidationType.Configuration;
			message.messageType = MessageType.Fatal;
			message.title = "No validation definition is found in configuration.";
			message.description = "No entry is found in configuration for version '" + this.version+ "' and identificator '" + this.schema + "', unable to perform validation!";			
			this.messages.add(message);
			r = false;
		}
		
		return r;
	}

	/**
	 * Perform Difi validation of XML based on Difi configuration.
	 * 
	 * @param validates Nodelist of validation definitions
	 * @param xmlDoc XML as Document
	 * @throws Exception
	 */
	private void validation(NodeList validates, Document xmlDoc) throws Exception {		
		// Loop NodeList for validation steps
		for(int i=0; i<validates.getLength(); i++){
			Element validate = (Element) validates.item(i);
			NodeList steps = validate.getElementsByTagName("step");
			
			for(int x=0; x<steps.getLength(); x++){
				Node step = steps.item(x);			
				String id = step.getAttributes().getNamedItem("id").getNodeValue();
				String file = step.getAttributes().getNamedItem("file").getNodeValue();				
				
				System.out.println(id + " " + file);
				
				if (id.equals("XSD")) {					
					// Perform XSD validation
					XSDValidation xsdValidation = new XSDValidation();		
					xsdValidation.main(xmlDoc, this.propertiesFile.dataDir + file, this.messages, this.propertiesFile);					
				} else if (id.equals("XSL")) {
					// Perform XSL transformation
					SchematronTransformation xmlXslTransformation = new SchematronTransformation();
					xmlXslTransformation.main(xmlDoc, this.propertiesFile.dataDir + file, this.messages);
				} else if (id.equals("FILTER")) {
					String rule = step.getAttributes().getNamedItem("rule").getNodeValue();
					
					FilterMessage filterMessage = new FilterMessage();
					filterMessage.main(xmlDoc, this.propertiesFile.dataDir + file, this.messages, rule);
				}			
			}										
		}
	}
	
	/**
	 * Sets attribute valid. That is if the current XML is valid.
	 * Does this by looping the message collection and checking for
	 * messages with fatal message type.
	 * 
	 * @throws Exception
	 */	
	private void setIsValid() throws Exception {
		this.valid = true;
		
		for (Message message : this.messages) {
			if (message.messageType == MessageType.Fatal) {
				this.valid = false;
				return;
			}
		}
	}
	
	/**
	 * Performs stat logging to file
	 */
	private void logStat() {
		if (this.propertiesFile.logStatistics == true) {
			// Set path where to place log files
			System.setProperty("statLoggerFilePath", this.propertiesFile.dataDir + "/LOG");
			
			// Perform logging
			StatLogger statLogger = new StatLogger();
			statLogger.logStats(this.schema, this.version, this.valid, this.messages);
		}		
	}
	
	/**
	 * Returns the validation message collection as XML.
	 * 
	 * @return String Messages as XML
	 * @throws Exception 
	 */
	public String messagesAsXML() throws Exception {		
		// Build XML document
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		
		Document doc = docBuilder.newDocument();
		Element rootElement = doc.createElement("messages");
		doc.appendChild(rootElement);
		
		// Iterate messages
		for (Message message : this.messages) {
			if (this.suppressWarnings == true && message.messageType == MessageType.Warning) {
			} else {
				// Create message
				Element msg = doc.createElement("message");
				rootElement.appendChild(msg);
				
				// Add message attribute schema
				msg.setAttribute("schema", schema);

				// Add message attribute version
				msg.setAttribute("version", version);

				// Add message attribute version
				msg.setAttribute("validationType", message.validationType.toString());
				
				// Add messagetype
				Element messageType = doc.createElement("messageType");
				messageType.appendChild(doc.createTextNode(message.messageType.toString()));
				msg.appendChild(messageType);
				
				// Add title
				Element title = doc.createElement("title");
				title.appendChild(doc.createTextNode(message.title));
				msg.appendChild(title);
				
				// Add description
				Element desc = doc.createElement("description");
				desc.appendChild(doc.createTextNode(message.description));
				msg.appendChild(desc);
				
				// Add schematron rule id
				Element schematronRuleId = doc.createElement("schematronRuleId");
				schematronRuleId.appendChild(doc.createTextNode(message.schematronRuleId));
				msg.appendChild(schematronRuleId);
				
				// Add hints to message
				if (message.hints != null) {
					// Create hints
					Element hints = doc.createElement("hints");
					
					for (Hint h : message.hints) {
						// Create hint
						Element hint = doc.createElement("hint");
						
						// Add title
						Element hintTitle = doc.createElement("title");
						hintTitle.appendChild(doc.createTextNode(h.title));
						hint.appendChild(hintTitle);					
						
						// Add description
						Element hintDesc = doc.createElement("description");
						hintDesc.appendChild(doc.createTextNode(h.description));
						hint.appendChild(hintDesc);
						
						// Add hint
						hints.appendChild(hint);
					}
					
					// Add hints
					msg.appendChild(hints);
				}				
			}
		}
		
		// Get XML utils and return DOM as string
		Utils utils = new Utils();				
		return utils.xmlDOMToString(doc);
	}	
}
