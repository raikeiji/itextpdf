/*
 * $Id$
 *
 * This file is part of the iText project.
 * Copyright (c) 1998-2009 1T3XT BVBA
 * Authors: Bruno Lowagie, Paulo Soares, et al.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY 1T3XT,
 * 1T3XT DISCLAIMS THE WARRANTY OF NON INFRINGEMENT OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA, or download the license from the following URL:
 * http://itextpdf.com/terms-of-use/
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * In accordance with Section 7(b) of the GNU Affero General Public License,
 * you must retain the producer line in every PDF that is created or manipulated
 * using iText.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the iText software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers as an ASP,
 * serving PDFs on the fly in a web application, shipping iText with a closed
 * source product.
 *
 * For more information, please contact iText Software Corp. at this
 * address: sales@itextpdf.com
 */
package com.itextpdf.text.html.simpleparser;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.itextpdf.text.Chunk;
import com.itextpdf.text.DocListener;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.ElementTags;
import com.itextpdf.text.ExceptionConverter;
import com.itextpdf.text.FontProvider;
import com.itextpdf.text.Image;
import com.itextpdf.text.ListItem;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.TextElementArray;
import com.itextpdf.text.Utilities;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.draw.LineSeparator;
import com.itextpdf.text.xml.simpleparser.SimpleXMLDocHandler;
import com.itextpdf.text.xml.simpleparser.SimpleXMLParser;

public class HTMLWorker implements SimpleXMLDocHandler, DocListener {

	/**
	 * DocListener that will listen to the Elements
	 * produced by parsing the HTML.
	 * This can be a com.lowagie.text.Document adding
	 * the elements to a Document directly, or an
	 * HTMLWorker instance strong the objects in a List
	 */
	protected DocListener document;
	
	/**
	 * The map with all the supported tags.
	 * @since 5.0.6
	 */
	protected Map<String, TagProcessor> tags;

	/** The object defining all the styles. */
	private StyleSheet style = new StyleSheet();
	
	/**
	 * Creates a new instance of HTMLWorker
	 * @param document A class that implements <CODE>DocListener</CODE>
	 */
	public HTMLWorker(DocListener document) {
		this(document, null, null);
	}
	
	/**
	 * Creates a new instance of HTMLWorker
	 * @param document	A class that implements <CODE>DocListener</CODE>
	 * @param tags		A map containing the supported tags
	 * @param style		A StyleSheet
	 * @since 5.0.6
	 */
	public HTMLWorker(DocListener document, Map<String, TagProcessor> tags, StyleSheet style) {
		this.document = document;
		setSupportedTags(tags);
		setStyleSheet(style);
	}
	
	/**
	 * Sets the map with supported tags.
	 * @param tags
	 * @since 5.0.6
	 */
	public void setSupportedTags(Map<String, TagProcessor> tags) {
		if (tags == null)
			tags = new SupportedTags();
		this.tags = tags;
	}
	
	/**
	 * Setter for the StyleSheet
	 * @param style the StyleSheet
	 */
	public void setStyleSheet(StyleSheet style) {
		if (style == null)
			style = new StyleSheet();
		this.style = style;
	}

	/**
	 * Parses content read from a java.io.Reader object.
	 * @param reader	the content
	 * @throws IOException
	 */
	public void parse(Reader reader) throws IOException {
		SimpleXMLParser.parse(this, null, reader, true);
	}

	// state machine

	/**
	 * Stack with the Elements that already have been processed.
	 * @since iText 5.0.6 (private => protected)
	 */
	protected Stack<Element> stack = new Stack<Element>();

	/**
	 * Keeps the content of the current paragraph
	 * @since iText 5.0.6 (private => protected)
	 */
	protected Paragraph currentParagraph;
	
	/**
	 * The current hierarchy chain of tags.
	 * @since 5.0.6
	 */
	private AttributeChain chain = new AttributeChain();

	/**
	 * @see com.itextpdf.text.xml.simpleparser.SimpleXMLDocHandler#startDocument()
	 */
	public void startDocument() {
		HashMap<String, String> attrs = new HashMap<String, String>();
		style.applyStyle("body", attrs);
		chain.addToChain("body", attrs);
	}

    /**
     * @see com.itextpdf.text.xml.simpleparser.SimpleXMLDocHandler#startElement(java.lang.String, java.util.HashMap)
     */
    public void startElement(String tag, HashMap<String, String> attrs) {
		TagProcessor htmlTag = tags.get(tag);
		if (htmlTag == null) {
			return;
		}
		// apply the styles to attrs
		style.applyStyle(tag, attrs);
		// deal with the style attribute
		StyleSheet.resolveStyleAttribute(attrs, chain);
		// process the tag
		try {
			htmlTag.startElement(this, tag, attrs, chain);
		} catch (DocumentException e) {
			throw new ExceptionConverter(e);
		} catch (IOException e) {
			throw new ExceptionConverter(e);
		}
	}

	/**
	 * @see com.itextpdf.text.xml.simpleparser.SimpleXMLDocHandler#text(java.lang.String)
	 */
	public void text(String content) {
		if (skipText)
			return;
		if (currentParagraph == null) {
			currentParagraph = createParagraph();
		}
		if (!insidePRE) {
			// newlines and carriage returns are ignored
			if (content.trim().length() == 0 && content.indexOf(' ') < 0) {
				return;
			}
			content = Utilities.eliminateWhiteSpace(content);
		}
		Chunk chunk = createChunk(content);
		currentParagraph.add(chunk);
	}

	/**
	 * @see com.itextpdf.text.xml.simpleparser.SimpleXMLDocHandler#endElement(java.lang.String)
	 */
	public void endElement(String tag) {
		TagProcessor htmlTag = tags.get(tag);
		if (htmlTag == null) {
			return;
		}
		// process the tag
		try {
			htmlTag.endElement(this, tag, chain);
		} catch (DocumentException e) {
			throw new ExceptionConverter(e);
		}
	}

	/**
	 * @see com.itextpdf.text.xml.simpleparser.SimpleXMLDocHandler#endDocument()
	 */
	public void endDocument() {
		try {
			// flush the stack
			for (int k = 0; k < stack.size(); ++k)
				document.add(stack.elementAt(k));
			// add current paragraph
			if (currentParagraph != null)
				document.add(currentParagraph);
			currentParagraph = null;
		} catch (Exception e) {
			throw new ExceptionConverter(e);
		}
	}

	// stack and current paragraph operations

	/**
	 * Adds a new line to the currentParagraph.
	 * @since 5.0.6
	 */
	public void newLine() {
		if (currentParagraph == null) {
			currentParagraph = new Paragraph();
		}
		currentParagraph.add(createChunk("\n"));
	}
	
	/**
	 * Flushes the current paragraph, indicating that we're starting
	 * a new block.
	 * If the stack is empty, the paragraph is added to the document.
	 * Otherwise the Paragraph is added to the stack.
	 * @since 5.0.6
	 */
	public void carriageReturn() throws DocumentException {
		if (currentParagraph == null)
			return;
		if (stack.empty())
			document.add(currentParagraph);
		else {
			Element obj = stack.pop();
			if (obj instanceof TextElementArray) {
				TextElementArray current = (TextElementArray) obj;
				current.add(currentParagraph);
			}
			stack.push(obj);
		}
		currentParagraph = null;
	}
	
	/**
	 * Stacks the current paragraph, indicating that we're starting
	 * a new span.
	 * @since 5.0.6
	 */
	public void flushContent() {
		pushToStack(currentParagraph);
		currentParagraph = new Paragraph();
	}
	
	/**
	 * Pushes an element to the Stack.
	 * @param element
	 * @since 5.0.6
	 */
	public void pushToStack(Element element) {
		if (element != null)
			stack.push(element);
	}
	
	// providers that help find resources such as images and fonts
	
	/**
	 * Key used to store the image provider in the providers map.
	 * @since 5.0.6
	 */
	public static final String IMG_PROVIDER = "img_provider";
	
	/**
	 * Key used to store the image processor in the providers map.
	 * @since 5.0.6
	 */
	public static final String IMG_PROCESSOR = "img_interface";
	
	/**
	 * Key used to store the image store in the providers map.
	 * @since 5.0.6
	 */
	public static final String IMG_STORE = "img_static";

	/**
	 * Key used to store the image baseurl provider in the providers map.
	 * @since 5.0.6
	 */
	public static final String IMG_BASEURL = "img_baseurl";
	
	/**
	 * Key used to store the font provider in the providers map.
	 * @since 5.0.6
	 */
	public static final String FONT_PROVIDER = "font_factory";
	
	/**
	 * Key used to store the link provider in the providers map.
	 * @since 5.0.6
	 */
	public static final String LINK_PROVIDER = "alink_interface";

	/**
	 * Map containing providers such as a FontProvider or ImageProvider.
	 * @since 5.0.6 (renamed from interfaceProps)
	 */
	private Map<String, Object> providers = new HashMap<String, Object>();

	/**
	 * Setter for the providers.
	 * If a FontProvider is added, the ElementFactory is updated.
	 * @param providers a Map with different providers
	 * @since 5.0.6
	 */
	public void setProviders(Map<String, Object> providers) {
		if (providers == null)
			return;
		this.providers = providers;
		FontProvider ff = null;
		if (providers != null)
			ff = (FontProvider) providers.get(FONT_PROVIDER);
		if (ff != null)
			factory.setFontProvider(ff);
	}
	
	// factory that helps create objects
	
	/**
	 * Factory that is able to create iText Element objects.
	 * @since 5.0.6
	 */
	private ElementFactory factory = new ElementFactory();

	/**
	 * Creates a Chunk using the factory.
	 * @param content	the content of the chunk
	 * @return	a Chunk with content
	 * @since 5.0.6
	 */
	public Chunk createChunk(String content) {
		return factory.createChunk(content, chain);
	}
	/**
	 * Creates a Paragraph using the factory.
	 * @return	a Paragraph without any content
	 * @since 5.0.6
	 */
	public Paragraph createParagraph() {
		return factory.createParagraph(chain);
	}
	/**
	 * Creates a List object.
	 * @param tag should be "ol" or "ul"
	 * @return	a List object
	 * @since 5.0.6
	 */
	public com.itextpdf.text.List createList(String tag) {
		return factory.createList(tag, chain);
	}
	/**
	 * Creates a ListItem object.
	 * @return a ListItem object
	 * @since 5.0.6
	 */
	public ListItem createListItem() {
		return factory.createListItem(chain);
	}
	/**
	 * Creates a LineSeparator object.
	 * @param attrs	properties of the LineSeparator
	 * @return a LineSeparator object
	 * @since 5.0.6
	 */
	public LineSeparator createLineSeparator(Map<String, String> attrs) {
		return factory.createLineSeparator(attrs, currentParagraph.getLeading()/2);
	}
	
	/**
	 * Creates an Image object.
	 * @param attrs properties of the Image
	 * @return an Image object (or null if the Image couldn't be found)
	 * @throws DocumentException
	 * @throws IOException
	 * @since 5.0.6
	 */
	public Image createImage(Map<String, String> attrs) throws DocumentException, IOException {
		String src = attrs.get(ElementTags.SRC);
		if (src == null)
			return null;
		Image img = factory.createImage(
				src, attrs, chain, document,
				(ImageProvider)providers.get(IMG_PROVIDER),
				(ImageStore)providers.get(IMG_STORE),
				(String)providers.get(IMG_BASEURL));
		return img;
	}
	
	// processing objects
	
	/**
	 * Adds a link to the current paragraph.
	 */
	public void addLink() {
		if (currentParagraph == null) {
			currentParagraph = new Paragraph();
		}
		// The link provider allows you to do additional processing
		LinkProcessor i = (LinkProcessor) providers.get(HTMLWorker.LINK_PROVIDER);
		if (i == null || !i.process(currentParagraph, chain)) {
			// sets an Anchor for all the Chunks in the current paragraph
			String href = chain.getProperty("href");
			if (href != null) {
				for (Chunk ck : currentParagraph.getChunks()) {
					ck.setAnchor(href);
				}
			}
		}
		// a link should be added to the current paragraph as a phrase
		Paragraph tmp = (Paragraph) stack.pop();
		tmp.add(new Phrase(currentParagraph));
		currentParagraph = tmp;
	}
	

	
	public void addList() throws DocumentException {
		if (stack.empty())
			return;
		Element obj = stack.pop();
		if (!(obj instanceof com.itextpdf.text.List)) {
			stack.push(obj);
			return;
		}
		if (stack.empty())
			document.add(obj);
		else
			((TextElementArray) stack.peek()).add(obj);
	}
	
	public void addListItem() throws DocumentException {
		if (stack.empty())
			return;
		Element obj = stack.pop();
		if (!(obj instanceof ListItem)) {
			stack.push(obj);
			return;
		}
		if (stack.empty()) {
			document.add(obj);
			return;
		}
		Element list = stack.pop();
		if (!(list instanceof com.itextpdf.text.List)) {
			stack.push(list);
			return;
		}
		ListItem item = (ListItem) obj;
		((com.itextpdf.text.List) list).add(item);
		ArrayList<Chunk> cks = item.getChunks();
		if (!cks.isEmpty())
			item.getListSymbol()
					.setFont(cks.get(0).getFont());
		stack.push(list);
	}
	
	public void addImage(Image img, Map<String, String> attrs) throws DocumentException {
		ImageProcessor processor = (ImageProcessor)providers.get(HTMLWorker.IMG_PROCESSOR);
		boolean skip = false;
		if (processor != null)
			skip = processor.process(img, attrs, chain, document);
		if (!skip) {
			String align = attrs.get("align");
			if (align != null) {
				carriageReturn();
				int ralign = Image.MIDDLE;
				if (align.equalsIgnoreCase("left"))
					ralign = Image.LEFT;
				else if (align.equalsIgnoreCase("right"))
					ralign = Image.RIGHT;
				img.setAlignment(ralign);	
				document.add(img);
			} else {
				if (currentParagraph == null) {
					currentParagraph = createParagraph();
				}
				currentParagraph.add(new Chunk(img, 0, 0, true));
			}
		}
	}
	
	
	public void addTable() throws DocumentException{

		TableWrapper table = (TableWrapper) stack.pop();
		PdfPTable tb = table.createTable();
		tb.setSplitRows(true);
		if (stack.empty())
			document.add(tb);
		else
			((TextElementArray) stack.peek()).add(tb);
		boolean state[] = tableState.pop();
		pendingTR = state[0];
		pendingTD = state[1];
		skipText = false;
	}
	
	public void addRow() {
		ArrayList<PdfPCell> row = new ArrayList<PdfPCell>();
        ArrayList<Float> cellWidths = new ArrayList<Float>();
        boolean percentage = false;
        float width;
        float totalWidth = 0;
        int zeroWidth = 0;
		TableWrapper table = null;
		while (true) {
			Element obj = stack.pop();
			if (obj instanceof CellWrapper) {
                CellWrapper cell = (CellWrapper)obj;
                width = cell.getWidth();
                cellWidths.add(new Float(width));
                percentage |= cell.isPercentage();
                if (width == 0) {
                	zeroWidth++;
                }
                else {
                	totalWidth += width;
                }
                row.add(cell.getCell());
			}
			if (obj instanceof TableWrapper) {
				table = (TableWrapper) obj;
				break;
			}
		}
        table.addRow(row);
        if (cellWidths.size() > 0) {
            // cells come off the stack in reverse, naturally
        	totalWidth = 100 - totalWidth;
            Collections.reverse(cellWidths);
            float[] widths = new float[cellWidths.size()];
            for (int i = 0; i < widths.length; i++) {
                widths[i] = cellWidths.get(i).floatValue();
                if (widths[i] == 0 && percentage && zeroWidth > 0) {
                	widths[i] = totalWidth / zeroWidth;
                }
            }
            table.setColWidths(widths);
        }
		stack.push(table);
		skipText = true;
	}

	// state variables and methods
	
	/** Stack to keep track of table tags. */
	private Stack<boolean[]> tableState = new Stack<boolean[]>();
	
	/** Boolean to keep track of TR tags. */
	private boolean pendingTR = false;

	/** Boolean to keep track of TD and TH tags */
	private boolean pendingTD = false;

	/** Boolean to keep track of LI tags */
	private boolean pendingLI = false;

	/**
	 * Boolean to keep track of PRE tags
	 * @since 5.0.6 renamed from isPRE
	 */
	private boolean insidePRE = false;

	/**
	 * Indicates if text needs to be skipped.
	 * @since iText 5.0.6 (private => protected)
	 */
	protected boolean skipText = false;
	
	public void pushTableState() {
		tableState.push(new boolean[] { pendingTR, pendingTD });
	}

	/**
	 * @return the pendingTR
	 */
	public boolean isPendingTR() {
		return pendingTR;
	}

	/**
	 * @param pendingTR the pendingTR to set
	 */
	public void setPendingTR(boolean pendingTR) {
		this.pendingTR = pendingTR;
	}

	/**
	 * @return the pendingTD
	 */
	public boolean isPendingTD() {
		return pendingTD;
	}

	/**
	 * @param pendingTD the pendingTD to set
	 */
	public void setPendingTD(boolean pendingTD) {
		this.pendingTD = pendingTD;
	}

	/**
	 * @return the pendingLI
	 */
	public boolean isPendingLI() {
		return pendingLI;
	}

	/**
	 * @param pendingLI the pendingLI to set
	 */
	public void setPendingLI(boolean pendingLI) {
		this.pendingLI = pendingLI;
	}

	/**
	 * @return the insidePRE
	 */
	public boolean isInsidePRE() {
		return insidePRE;
	}

	/**
	 * @param insidePRE the insidePRE to set
	 */
	public void setInsidePRE(boolean insidePRE) {
		this.insidePRE = insidePRE;
	}

	/**
	 * @return the skipText
	 */
	public boolean isSkipText() {
		return skipText;
	}

	/**
	 * @param skipText the skipText to set
	 */
	public void setSkipText(boolean skipText) {
		this.skipText = skipText;
	}

	// static methods to parse HTML to a List of Element objects.
	
	/** The resulting list of elements. */
	protected List<Element> objectList;
	
	/**
	 * Parses an HTML source to a List of Element objects
	 * @param reader	the HTML source
	 * @param style		a StyleSheet object
	 * @return a List of Element objects
	 * @throws IOException
	 */
	public static List<Element> parseToList(Reader reader, StyleSheet style)
			throws IOException {
		return parseToList(reader, style, null);
	}

	/**
	 * Parses an HTML source to a List of Element objects
	 * @param reader	the HTML source
	 * @param style		a StyleSheet object
	 * @param providers	map containing classes with extra info
	 * @return a List of Element objects
	 * @throws IOException
	 */
	public static List<Element> parseToList(Reader reader, StyleSheet style,
			HashMap<String, Object> providers) throws IOException {
		return parseToList(reader, style, null, providers);
	}
	
	/**
	 * Parses an HTML source to a List of Element objects
	 * @param reader	the HTML source
	 * @param style		a StyleSheet object
	 * @param tags		a map containing supported tags and their processors
	 * @param providers	map containing classes with extra info
	 * @return a List of Element objects
	 * @throws IOException
	 */
	public static List<Element> parseToList(Reader reader, StyleSheet style,
			Map<String, TagProcessor> tags, HashMap<String, Object> providers) throws IOException {
		HTMLWorker worker = new HTMLWorker(null, tags, style);
		worker.document = worker;
		worker.setProviders(providers);
		worker.objectList = new ArrayList<Element>();
		worker.parse(reader);
		return worker.objectList;
	}
	
	// DocListener interface

	/**
	 * @see com.itextpdf.text.ElementListener#add(com.itextpdf.text.Element)
	 */
	public boolean add(Element element) throws DocumentException {
		objectList.add(element);
		return true;
	}

	/**
	 * @see com.itextpdf.text.DocListener#close()
	 */
	public void close() {
	}

	/**
	 * @see com.itextpdf.text.DocListener#newPage()
	 */
	public boolean newPage() {
		return true;
	}

	/**
	 * @see com.itextpdf.text.DocListener#open()
	 */
	public void open() {
	}

	/**
	 * @see com.itextpdf.text.DocListener#resetPageCount()
	 */
	public void resetPageCount() {
	}

	/**
	 * @see com.itextpdf.text.DocListener#setMarginMirroring(boolean)
	 */
	public boolean setMarginMirroring(boolean marginMirroring) {
		return false;
	}

	/**
     * @see com.itextpdf.text.DocListener#setMarginMirroring(boolean)
	 * @since	2.1.6
	 */
	public boolean setMarginMirroringTopBottom(boolean marginMirroring) {
		return false;
	}

	/**
	 * @see com.itextpdf.text.DocListener#setMargins(float, float, float, float)
	 */
	public boolean setMargins(float marginLeft, float marginRight,
			float marginTop, float marginBottom) {
		return true;
	}

	/**
	 * @see com.itextpdf.text.DocListener#setPageCount(int)
	 */
	public void setPageCount(int pageN) {
	}

	/**
	 * @see com.itextpdf.text.DocListener#setPageSize(com.itextpdf.text.Rectangle)
	 */
	public boolean setPageSize(Rectangle pageSize) {
		return true;
	}

	// deprecated methods
	
	/**
	 * Sets the providers.
	 * @deprecated use setProviders() instead
	 */
	public void setInterfaceProps(HashMap<String, Object> providers) {
		setProviders(providers);
	}
	/**
	 * Gets the providers
	 * @deprecated use getProviders() instead
	 */
	public Map<String, Object> getInterfaceProps() {
		return providers;
	}

}
