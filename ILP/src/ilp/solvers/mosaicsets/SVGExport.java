package ilp.solvers.mosaicsets;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.io.*;


/** Helper class to export MosaicSet renderings as SVG
 * 
 */
public class SVGExport {

  SVGGraphics2D svgGenerator;

  public Graphics2D createContext() {
    // Get a DOMImplementation.
    DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();

    // Create an instance of org.w3c.dom.Document.
    String svgNS = "http://www.w3.org/2000/svg";
    Document document = domImpl.createDocument(svgNS, "svg", null);
    svgGenerator = new SVGGraphics2D(document);

    return svgGenerator;
  }

  public void writeToFile(String file) throws TransformerException {
    try {

      Node elem = svgGenerator.getRoot();
      TransformerFactory transfac = TransformerFactory.newInstance();
      Transformer trans = transfac.newTransformer();
      trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      trans.setOutputProperty(OutputKeys.INDENT, "yes");

      // create string from xml tree
      StringWriter sw = new StringWriter();
      StreamResult result = new StreamResult(sw);
      DOMSource source = new DOMSource(elem);
      trans.transform(source, result);
      String xmlString = sw.toString();

      BufferedReader bufReader = new BufferedReader(
          new StringReader(xmlString));

      String line = null;
      while ((line = bufReader.readLine()) != null) {
        if (line.trim().startsWith("<polygon")) {
          if (line.contains("450 104")) {
          }
        }
      }

      BufferedWriter writer = new BufferedWriter(
          new FileWriter(new File(file)));
      writer.write(xmlString);
      writer.close();

    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}