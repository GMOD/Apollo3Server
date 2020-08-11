package org.bbop.apollo

import grails.util.Pair
import org.grails.web.json.JSONException
import org.grails.web.json.JSONObject
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 * Created by ndunn on 4/14/15.
 */
class FormatUtil {

    static JSONObject convertFromXMLToJSON(Document xmlDocument) throws JSONException {
        /*
        NodeList children = xmlDocument.getChildNodes();
        for (int i = 0; i < children.getLength(); ++i) {
            Pair<String, JSONObject> childJson = processXMLNode(children.item(i));
            json.put(childJson.getFirst(), childJson.getSecond());
        }
        */
        Pair<String, ? extends Object> retVal = processXMLNode(xmlDocument.getDocumentElement());
        return new JSONObject().put(retVal.aValue, retVal.bValue);
    }

    private static Pair<String, ? extends Object> processXMLNode(Node node) throws JSONException {
        JSONObject json = new JSONObject();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); ++i) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Pair<String, ? extends Object> childJson = processXMLNode(child);
                json.accumulate(childJson.aValue, childJson.bValue);
            }
            else if (child instanceof Text && children.getLength() == 1) {
                return new Pair<String, String>(node.getNodeName(), child.getTextContent());
//                json.put(node.getNodeName(), child.getTextContent());
            }
        }
        return new Pair<String, JSONObject>(node.getNodeName(), json);
    }


}
