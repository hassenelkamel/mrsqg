package com.googlecode.mrsqg.mrs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import org.apache.log4j.Logger;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;

import com.googlecode.mrsqg.nlp.SnowballStemmer;
import com.googlecode.mrsqg.nlp.indices.IrregularVerbs;

/**
 * An MRS representation class that provides MRS manipulation utilities.<p>
 * Naming convention: <br/>
 * In an example EP: <br/>
 * [ <br/>
 *   _poet_n_1_rel<4:8> <br/>
 *   LBL: h10 <br/>
 *   ARG0: x6 [ x PERS: 3 NUM: SG IND: + ] <br/>
 * ] <br/>
 * <b>type(EP) label</b>: _poet_n_1_rel <br/>
 * <b>feature</b>: LBL/ARG0 <br/>
 * <b>value</b>: h10/x6 <br/>
 * <b>FvPair (feature/value pair)</b>: LBL: h10 <br/>
 * <b>extra type (Var)</b>: [ x PERS: 3 NUM: SG IND: + ], h10 (simplified value)<br/>
 * <b>extra feature</b>: PERS/NUM/IND <br/>
 * <b>extra value</b>: 3/SG/+<br/>
 *
 * @author Xuchen Yao
 *
 *
 */
public class MRS {

	/*
	 * !!! WARNING !!!
	 * Any new field added to this class must also be added to the copy constructor.
	 */

	private static Logger log = Logger.getLogger(MRS.class);
	/** the LTOP value of this MRS, e.g. h1 */
	protected String ltop = "";
	/** the label index number of this MRS, e.g. 10 (from h10)*/
	protected String label_vid = "";
	/** the index of this MRS, e.g. e2. Usually this is the ARG0 value of the main verb. */
	protected String index = "";
	/** the index number, e.g. 2 (from e2) */
	protected String index_vid = "";
	/** the type of sentence, e.g. PROP, WHEN, WHERE, etc */
	protected String sent_type = "PROP";
	/** which decomposer this MRS comes from. */
	protected ArrayList<String> decomposer = null;
	/** the character range of answer phrase {cfrom, cto}.
	 * @see MRS#getAnsPhrase(String)
	 */
	protected int[] ansCrange = {0,0};

	/** the list of ElementaryPredications */
	protected ArrayList <EP> eps;
	/** the list of HCONS (Handle Constraints) */
	protected ArrayList<HCONS> hcons;
	/** Every characteristic variable (see dmrs.pdf) is mapped to an EP.*/
	protected HashMap<String, EP> charVariableMap;
	private MrsParser parser = new MrsParser();

	public String getLTOP() {return ltop;}
	public String getLabelVid() {return label_vid;}
	public String getIndex() {return index;}
	public String getIndexVid() {return index_vid;}
	public String getSentType() {return sent_type;}
	public ArrayList <EP> getEps() {return eps;}
	public ArrayList<HCONS> getHcons() {return hcons;}
	public HashMap<String, EP> getCharVariableMap() {return charVariableMap;}
	public void setSentType (String sentType) {sent_type = sentType;}
	public void setDecomposer (String p) {decomposer.add(p);}
	public ArrayList<String> getDecomposer () {return decomposer;}
	public void setIndex (String index) {
		if (!index.startsWith("e")) {
			log.warn("the main event of this MRS doesn't start with an e: "+index+"!");
			log.warn(this.toString());
		}
		this.index = index;
		// index usually looks like "e2".
		this.index_vid = index.substring(1);
	}

	public int[] getAnsCrange () {return this.ansCrange;}

	/**
	 * Get the answer phrase from the original input sentence <code>sent</code>.
	 *
	 * During transformation, the answer phrase is kept in the field {@link MRS#ansCrange}.
	 * This method gets the answer phrase by returning the substring of <code>sent</code>.
	 * For Y/N questions, it always returns "yes".
	 * @param sent the original input sentence where this MRS comes from
	 * @return an answer phrase
	 */
	public String getAnsPhrase (String sent) {
		if (this.getSentType().equals("Y/N")) return "yes";
		else if (ansCrange[0] == 0 && ansCrange[1] == 0) return "";
		else if (sent == null) return "";
		else {
			String phrase;
			if (ansCrange[0] > sent.length() || ansCrange[1] > sent.length() ||
					ansCrange[0] < 0 || ansCrange[1] < 0 || ansCrange[1] - ansCrange[0] <0) {
				phrase = "";
			} else
				phrase = sent.substring(ansCrange[0], ansCrange[1]);
			if (phrase.endsWith(" .") || phrase.endsWith(" ,"))
				phrase = phrase.substring(0, phrase.length()-2);
			return phrase;
		}

	}

	/**
	 * Set the character range of answer term by extracting the range of <code>dEPS</code>.
	 *
	 * Usually <code>dEPS</code> comes from the replaced set of EP during transformation.
	 *
	 * @param dEPS a set of EPs that represent answer terms
	 */
	public void setAnsCrange (HashSet<EP> dEPS) {
		int cfrom=Integer.MAX_VALUE, cto=Integer.MIN_VALUE;
		if (dEPS == null || dEPS.size() == 0) return;
		for (EP ep:dEPS) {
			/*
			 *  some of EP doesn't have any Cfrom/Cto values, such as
			 *  NOMINALIZATION_REL, perhaps a bug from ERG/LKB?
			 */
			if (ep.getCfrom() == -1 || ep.getCto() == -1) continue;
			if (ep.getCfrom()<cfrom) cfrom=ep.getCfrom();
			if (ep.getCto()>cto) cto = ep.getCto();
		}
		this.ansCrange[0] = cfrom;
		this.ansCrange[1] = cto;
		if (cfrom == -1)
			log.warn("here");
	}


	/**
	 * Plain text output of this MRS and dependencies for each EP.
	 */
	@Override public String toString() {
		StringBuilder res = new StringBuilder();
		res.append("\n");
		res.append("SentType: "+sent_type+"\n");
		res.append("Decomposer: "+decomposer+"\n");
		// LTOP: h1
		res.append("LTOP: "+ltop+"\n");
		// INDEX: e2 [ e SF: PROP-OR-QUES TENSE: PRES MOOD: INDICATIVE PROG: - PERF: - ]
		res.append("INDEX: "+index+"\n");
		res.append("RELS: <\n");
		for (EP ep: eps) {
			res.append(ep);
		}
		res.append(">\n");
		res.append("HCONS: < ");
		for (HCONS h: hcons) {
			res.append(h+" ");
		}
		res.append(">\n");
		return res.toString();
	}

	public MRS() {
		hcons = new ArrayList<HCONS>();
		eps = new ArrayList<EP>();
		decomposer = new ArrayList<String>();
		charVariableMap = new HashMap<String, EP>();
	}

	/**
	 * Read an MRS from an MRX file
	 * @param file an MRS file in XML format
	 */
	public MRS(File file) {
		this();
		parse(file);
	}

	/**
	 * Read an MRS from an MRX string
	 * @param mrx a String in MRX
	 */
	public MRS(String mrx) {
		this();
		parseString(mrx);
	}

	/**
	* Deep copy constructor.
	*/
	public MRS(MRS old) {
		if (old == null) return;
		this.ltop = old.getLTOP();
		this.label_vid = old.getLabelVid();
		this.index = old.getIndex();
		this.index_vid = old.getIndexVid();
		this.sent_type = old.getSentType();
		this.eps = new ArrayList<EP>();
		this.decomposer = new ArrayList<String>();
		this.ansCrange[0] = old.getAnsCrange()[0];
		this.ansCrange[1] = old.getAnsCrange()[1];
		for (String s:old.getDecomposer()) decomposer.add(s);
		for (EP ep:old.getEps()) {
			this.eps.add(new EP(ep));
		}
		this.hcons = new ArrayList<HCONS>();
		for (HCONS h:old.getHcons()) {
			this.hcons.add(new HCONS(h));
		}
		charVariableMap = new HashMap<String, EP>();
		this.postprocessing();
	}

	/**
	 * Clean up and re-build dependencies for this MRS.
	 *
	 * After manipulating (decomposition, transformation) an MRS, this method
	 * should be called to rebuild the {@link MRS#charVariableMap} and EPS' dependencies.
	 */
	public void postprocessing() {
		this.charVariableMap.clear();
		for (EP ep:this.eps) {
			ep.clearDependencies();
		}
		this.buildCoref();
		this.mapCharacteristicVariables();
		this.buildDependencies();
	}

	private class MrsParser extends DefaultHandler {

		private Stack<String> stack;
		private StringBuilder chars;
		// whether we are processing in an <ep> element
		private boolean inEP = false;
		private EP currentEP = null;

		public MrsParser () {
			super();
			this.stack = new Stack<String>();
			this.chars = new StringBuilder();
		}

		public void parse(File file) {
			try {
				XMLReader xr = XMLReaderFactory.createXMLReader();
				MrsParser handler = new MrsParser();
				xr.setContentHandler(handler);
				xr.setErrorHandler(handler);

				FileReader r = new FileReader(file);
				xr.parse(new InputSource(r));
			} catch (Exception e) {
				log.error("Error:", e);
			}

		}

		public void parseString(String str) {
			try {
				XMLReader xr = XMLReaderFactory.createXMLReader();
				MrsParser handler = new MrsParser();
				xr.setContentHandler(handler);
				xr.setErrorHandler(handler);


				StringReader r = new StringReader(str);
				xr.parse(new InputSource(r));
			} catch (Exception e) {
				log.error("Error:", e);
			}

		}

		public void startDocument ()
	    {
			//System.out.println("Start document");
	    }

		public void endDocument ()
	    {
			//System.out.println("End document");
	    }

		public void startElement (String uri, String name,
				String qName, Attributes atts)
		{
			String vid;
			String parent;

			if (qName.equals("mrs")) {
				// if stack is not empty, then error
				if (stack.empty() == false) {
					log.error("Error, non-empty stack: " +
							"<mrs> shouldn't have parent element");
				}
			} else if (qName.equals("label")) {
				parent = stack.peek();
				vid = atts.getValue("vid");

				// top element, indicating the LTOP of MRS
				if (parent.equals("mrs")) {
					ltop = "h"+vid;
					label_vid = vid;
				} else if (parent.equals("ep")) {
					// label for <ep>
					currentEP.processStartElement(qName, atts);
				} else if (parent.equals("lo")) {
					HCONS h = hcons.get(hcons.size()-1);
					h.setLoLabelRare(atts.getValue("vid"));
					h.setLo(atts.getValue("vid"));
					log.error("Warning: <label> inisde <lo>. " +
							"not in sample. check the code!");
				} else {
					log.error("file format error: unknown" +
							"element label");
				}
			} else if (qName.equals("var")) {
				parent = stack.peek();
				vid = atts.getValue("vid");

				// top element, indicating the INDEX of MRS
				if (parent.equals("mrs")) {
					index = "e"+vid;
					index_vid = vid;
				} else if (parent.equals("fvpair")) {
					// label for <fvpair>
					if (inEP) {
						currentEP.processStartElement(qName, atts);
					} else {
						log.error("error: <fvpair> outside <ep>");
					}
				} else if (parent.equals("hi")) {
					// get the last one in the list
					HCONS h = hcons.get(hcons.size()-1);
					// should be sth. like "h11"
					h.setHiVar(new Var(atts));
				} else if (parent.equals("lo")) {
					String sort = atts.getValue("sort");
					// get the last one in the list
					HCONS h = hcons.get(hcons.size()-1);
					// should be sth. like "h11"
					h.setLoVar(new Var(atts));
				} else {
					log.error("file format error: unknown" +
							"element var");
				}
			} else if (qName.equals("hcons")) {
//				;;; <!ELEMENT hcons (hi, lo)>
//				;;; <!ATTLIST hcons
//				;;;          hreln (qeq|lheq|outscopes) #REQUIRED >
//				;;;
//				;;; <!ELEMENT hi (var)>
//				;;; <!ELEMENT lo (label|var)>

				String hreln = atts.getValue("hreln");
				if (hreln.equals("lheq") || hreln.equals("outscopes")) {
					// no such situation in sample files, need to complete
					// this part once met
					log.error("Manually check the code and complete it!");
				}
				HCONS hcon = new HCONS(hreln);
				hcons.add(hcon);
			} else if (qName.equals("ep")) {
				inEP = true;
				EP e = new EP();
				eps.add(e);
				currentEP = e;
				currentEP.processStartElement(qName, atts);
			} else if (inEP == true) {
				currentEP.processStartElement(qName, atts);
			} else if (qName.equals("hi") || qName.equals("lo")) {
			} else {
				log.error("Unknown element "+qName);
			}
			chars = new StringBuilder();
			stack.push(qName);
		}

		public void endElement (String uri, String name, String qName)
		{
			if (qName.equals("mrs")) {

			} else if (qName.equals("hcons")) {
				HCONS h = hcons.get(hcons.size()-1);
				if (h.checkValid() == false) {
					log.error("HCONS read error!");
				}
			} else if (qName.equals("ep")) {
				inEP = false;
				currentEP = null;
			} else if (inEP) {
				currentEP.processEndElement(qName, chars.toString());
			}
			stack.pop();
		}

		public void characters (char ch[], int start, int length)
		{
			chars.append(ch, start, length);
		}

	}

	/**
	 * Return all ElementaryPredication starting from cfrom and ending to cto.
	 *
	 * @param cfrom the starting position in characters
	 * @param cto the starting position in characters
	 *
	 * @return an ArrayList of EP (empty if it was found)
	 */
	public ArrayList<EP> getEPS (int cfrom, int cto) {
		ArrayList<EP> epsList= new ArrayList<EP>();

		for (EP ep:this.eps) {
			if (ep.getCfrom()==cfrom && ep.getCto()==cto) {
				epsList.add(ep);
			}
		}

		return epsList;
	}

	/**
	 * Return a list of EP with a label value <code>label</code>.
	 * for instance, return all EPs with a label value "h3".
	 * @param label the label value of the EP, such as "h3"
	 * @return an ArrayList of EP with the matching label or null if none
	 */
	public ArrayList<EP> getEPbyLabelValue (String label) {
		ArrayList<EP> retEP = new ArrayList<EP>();
		for (EP ep:eps) {
			if (ep.getLabel().equals(label)) {
				retEP.add(ep);
			}
		}

		return retEP.size() == 0? null: retEP;
	}

	/**
	 * Return a list of EP elements with a type name <code>name</code>.
	 * @param name a type name, such as "APPOS_REL"
	 * @return an ArrayList of EP with a matching type name, or null if no matching.
	 */
	public ArrayList<EP> getEPbyTypeName (String name) {
		ArrayList<EP> retEP = new ArrayList<EP>();
		for (EP ep:eps) {
			if (ep.getTypeName().equals(name)) {
				retEP.add(ep);
				break;
			}
		}
		return retEP.size() == 0? null: retEP;
	}

	/**
	 * get a list of the feature labels of all EPs
	 * @return an ArrayList containing all the labels of EPS (none if not found)
	 */
	public ArrayList<String> getEPSfeatList () {
		ArrayList<String> list = new ArrayList<String>();
		for(EP ep:eps) {
			list.add(ep.getLabel());
		}

		return list;
	}

	/**
	 * Get a list of the values of all EPs.<p>
	 * In an EP like: <br/>
	 * [ _like_v_1_rel<5:10><br/>
  	 * LBL: h8<br/>
  	 * ARG0: e9<br/>
  	 * ARG1: x6<br/>
     * ARG2: x10<br/>
	 * ]<br/>
	 * h8 is label. e9, x6, x10 are handles
	 * @return an ArrayList containing all the values of EPS (empty if not found)
	 */
	public ArrayList<String> getEPSvalueList () {
		ArrayList<String> list = new ArrayList<String>();
		for(EP ep:eps) {
			for (FvPair fp:ep.getFvpair()) {
				list.add(fp.getValue());
			}
		}

		return list;
	}

	/**
	 * Get the EP before <code>cfrom</code> and <code>cto</code>.
	 *
	 * This method is mainly used to
	 * find out the preposition before a time/location term.
	 * For instance, "in Germany", one EP is  _IN_P_REL ("in"),
	 * the other is PROPER_Q_REL ("Germany"), feeding cfrom and cto with
	 * those of PROPER_Q_REL returns the _IN_P_TEMP_REL EP.
	 * There can be multiple EPs matching <code>cfrom</code> and
	 * <code>cto</code> but only the first one is returned.
	 *
	 * @param cfrom the starting position in characters
	 * @param cto the starting position in characters
	 *
	 * @return an EP (null if it was found)
	 *
	 */
	public EP getEPbefore (int cfrom, int cto) {
		EP ret = null;
		EP next = null;

		// In a well-formed MRS, all EPs are lined up according to
		// their position in the sentence
		for (EP ep:this.eps) {
			if (ep.getCfrom()==cfrom && ep.getCto()==cto) {
				next = ep;
				break;
			}
			ret = ep;
		}

		// extra safety
		if (ret!=null && next!= null && ret.getCto() >= next.getCfrom()) {
			log.error("ep1 should be before ep2 in EPS list.");
			log.error("ep1: "+ret);
			log.error("ep2: "+next);
			ret = null;
		}

		return ret;
	}

	/**
	 * Find out the set of EPs that are not in a range of <<code>cfrom</code>, <code>cto</code>>
	 *
	 * @param cfrom the starting position in characters
	 * @param cto the starting position in characters
	 *
	 * @return a HashSet of EP (empty if it was found)
	 */
	public HashSet<EP> getEPSnotInRange (int cfrom, int cto) {
		HashSet<EP> retSet = new HashSet<EP>();

		// In a well-formed MRS, all EPs are lined up according to
		// their position in the sentence
		for (EP ep:this.eps) {
			if (ep.getCfrom()>=cto|| ep.getCto()<=cfrom) {
				retSet.add(ep);
			}
		}

		return retSet;
	}

	/**
	 * Find out the set of EPs that are in a range of <<code>cfrom</code>, <code>cto</code>>
	 *
	 * @param cfrom the starting position in characters
	 * @param cto the starting position in characters
	 *
	 * @return a HashSet of EP (empty if it was found)
	 */
	public HashSet<EP> getEPSinRange (int cfrom, int cto) {
		HashSet<EP> retSet = new HashSet<EP>();

		// In a well-formed MRS, all EPs are lined up according to
		// their position in the sentence
		for (EP ep:this.eps) {
			if (ep.getCfrom()>=cfrom|| ep.getCto()<=cto) {
				retSet.add(ep);
			}
		}

		return retSet;
	}
	/**
	 * Find out a list of extra type whose extra feature/value match <code>feat</code> and <code>value</code>.
	 *
	 * @param feat the name of extra feature, such as "ARG0"
	 * @param value the name of extra value, such as "e2"
	 * @return a list of matching FvPair
	 */
	public ArrayList<FvPair> getExtraTypeByFeatAndValue (String feat, String value) {

		ArrayList<FvPair> list = new ArrayList<FvPair>();
		for (EP ep:this.eps) {
			for (FvPair f: ep.getFvpair()) {
				if (f.getFeature().equals(feat) && f.getVar() != null && f.getVar().getLabel().equals(value)) {
					list.add(f);
				}
			}
		}
		return list.size() == 0 ? null : list;
	}

	/**
	 * Find out a list of extra type whose extra value match <code>value</code>.
	 *
	 * @param value the name of extra value, such as "e2"
	 * @return an ArrayList of matching FvPair (null if not found)
	 */
	public ArrayList<FvPair> getExtraTypeByValue (String value) {

		ArrayList<FvPair> list = new ArrayList<FvPair>();
		for (EP ep:this.eps) {
			for (FvPair f: ep.getFvpair()) {
				if (f.getVar() != null && f.getVar().getLabel().equals(value)) {
					list.add(f);
				}
			}
		}
		return list.size() == 0 ? null : list;
	}

	/**
	 * find out an EP whose feature/value match <code>feat</code> and <code>value</code>
	 *
	 * @param feat feature name, such as "ARG0"
	 * @param value value name, such as "e2"
	 * @return an ArrayList of matching EP (null if not found)
	 */
	public ArrayList<EP> getEPbyFeatAndValue (String feat, String value) {
		if (value==null) return null;
		ArrayList<EP> list = new ArrayList<EP>();
		for (EP ep:this.eps) {
			for (FvPair f: ep.getFvpair()) {
				if (f.getFeature().equals(feat) && f.getVar() != null && f.getVar().getLabel().equals(value)) {
					list.add(ep);
				}
			}
		}
		return list.size()==0?null:list;
	}

	/**
	 * Retrieve the same EP as <code>copyEP</code> in <code>copyMrs</code>.
	 *
	 * This is used when the current MRS is a copy of <code>copyEP</code>,
	 * then we return the same EP as <code>copyEP</code>
	 * by computing parallel index in the current MRS.
	 *
	 * @param copyMrs an MRS which the current MRS is copied from
	 * @param copyEP one EP in <code>copyMrs</code>
	 * @return a corresponding EP the "same" to <code>copyEP</code>
	 */
	public EP getEPbyParallelIndex (MRS copyMrs, EP copyEP) {
		return this.getEps().get(copyMrs.getEps().indexOf(copyEP));
	}

	/**
	 * similar with {@link MRS#getEPbyParallelIndex(MRS, EP)}, but deal with a set of EPs
	 * @param copyMrs
	 * @param copyEPS
	 * @return a set of EPs
	 *
	 */
	public HashSet<EP> getEPSbyParallelIndex (MRS copyMrs, HashSet<EP> copyEPS) {
		HashSet<EP> retEPS = new HashSet<EP>();
		if (copyEPS == null || copyEPS.size() == 0) return null;
		for (EP ep:copyEPS) {
			retEPS.add(this.getEps().get(copyMrs.getEps().indexOf(ep)));
		}
		return retEPS;
	}

	/**
	 * Get the EP of the main verb
	 * @return an EP representing the main verb in this MRS
	 */
	public EP getVerbEP () {
		EP verbEP = null;
		EP modalEP = null;

		// the main event of this MRS
		String event = this.getIndex();
		ArrayList<EP> eventEPS = this.getEPbyFeatAndValue("ARG0", event);
		if (eventEPS == null) return null;
		for (EP ep:eventEPS) {
			if (ep.getTypeName().contains("_modal_")) {
				// it could be that a modal verb, such as 'can'/'must',
				// takes this event
				modalEP = ep;
			} else {
				verbEP = ep;
			}
		}

		if (modalEP != null) {
			// the modal verb refers to the main verb by a qeq relation
			String hiLabel = modalEP.getValueByFeature("ARG1");
			String loLabel = this.getLoLabelFromHconsList(hiLabel);
			ArrayList<EP> verbList = this.getEPbyLabelValue(loLabel);
			if (verbList.size() == 1) {
				verbEP = verbList.get(0);
			} else {
				log.warn("DEBUG YOUR CODE: this MRS contains more than one verbEP?\n"+verbList);
			}
		}

		return verbEP;
	}

	/**
	 * Change all <code>oldValue</code> values to <code>newValue</code>.
	 * For instance, change all "x5" to "x6".
	 * @param oldValue "x5"
	 * @param newValue "x6"
	 */
	public void changeEPvalue (String oldValue, String newValue) {
		for (EP ep:this.eps) {
			for (FvPair f: ep.getFvpair()) {
				if (f.getValue() != null && f.getValue().equals(oldValue)) {
					f.setValue(newValue);
				}
			}
		}
	}

	/**
	 * Change all <code>oldValue</code> values to <code>newVar</code>.
	 * @param oldValue
	 * @param newVar
	 *
	 * @see MRS#changeEPvalue(String, String)
	 */
	public void changeEPvalueVar (String oldValue, Var newVar) {
		for (EP ep:this.eps) {
			for (FvPair f: ep.getFvpair()) {
				if (f.getVar() != null && f.getValue() != null && f.getVar().getLabel().equals(oldValue)) {
					f.setVar(newVar);
				}
			}
		}
	}

	/**
	 * Add <code>ep</code> to the list of EPS.
	 * @param ep
	 */
	public void addEPtoEPS (EP ep) {
		if (ep!=null) this.eps.add(ep);
	}

	/**
	 * Get the tense of this MRS.
	 *
	 * In a malformed MRS, it's possible that there's no tense found.
	 * in this case, returns "PRES"
	 * @return a string of tense
	 */
	public String getTense () {
		try {
			String tense = (getExtraTypeByValue(getIndex()).get(0)).getVar().getExtrapair().get("TENSE");
			return tense==null?"PRES":tense;
		} catch (NullPointerException e) {
			//
			return "PRES";
		}
	}

	/**
	 * Set the tense of this MRS
	 * @param tense
	 */
	public void setTense (String tense) {
		ArrayList<FvPair> list = getExtraTypeByValue(getIndex());
		for (FvPair p:list)
			p.getVar().getExtrapair().put("TENSE", tense);
	}

	/**
	 * Add a simple HCONS to HCONS, such as "h1 qeq h2".
	 * @param hreln "qeq"
	 * @param hi_vid "1"
	 * @param hi_sort "h"
	 * @param lo_vid "2"
	 * @param lo_sort "h"
	 */
	public void addToHCONSsimple (String hreln, String hi_vid, String hi_sort,
			String lo_vid, String lo_sort) {
		this.hcons.add(new HCONS(hreln, hi_vid, hi_sort, lo_vid, lo_sort));
	}

	/**
	 * Add a simple HCONS to HCONS, such as "h1 qeq h2".
	 *
	 * @param hreln "qeq"
	 * @param hi "h1"
	 * @param lo "lo"
	 */
	public void addToHCONSsimple (String hreln, String hi, String lo) {
		this.hcons.add(new HCONS(hreln, hi, lo));
	}

	/**
	 * Given a hiLabel, retrieve the corresponding loLabel in the list.
	 * For instance, the list contains a "h1 qeq h2" relation, then
	 * given a hiLabel "h1", the function returns the loLabel "h2"
	 * @param hiLabel a hiLabel
	 * @return a corresponding loLabel in the list, or null if not found
	 */
	public String getLoLabelFromHconsList (String hiLabel) {
		ArrayList<HCONS> list = this.getHcons();
		String loLabel = null;
		if (hiLabel != null) {
			for (HCONS h:list) {
				if(h.getHi().equals(hiLabel)) {
					loLabel = h.getLo();
					break;
				}
			}
		}

		/*
		 * There's a very rare case that hiLabel = loLabel rather than
		 * hiLabel = loLabel, for instance, from the sentence:
		 * how much societies are organized round specialized technical fields?
		 * [ WHICH_Q_REL<0:3>
		  LBL: h12
		  ARG0: x14
		  RSTR: h15
		  BODY: h13
		]
		[ PROPERTY_REL<0:3>
		  LBL: h15
		  ARG0: x14
		]
		 */
		if (loLabel == null) {
			for (EP e:this.eps) {
				if (e.getLabel().equals(hiLabel)) {
					loLabel = hiLabel;
					break;
				}
			}
		}
		return loLabel;
	}

	/**
	 * Given a loLabel, check the corresponding HiLabel in the list.
	 * For instance, the list contains a "h1 qeq h2" relation, then
	 * given a loLabel "h2", the function returns the hiLabel "h1"
	 * @param loLabel a loLabel
	 * @return a corresponding hiLabel in the list, or null if not found
	 */
	public String getHiLabelFromHconsList (String loLabel) {
		String hiLabel = null;
		if (loLabel != null) {
			for (HCONS h:this.hcons) {
				if(h.getLo().equals(loLabel)) {
					hiLabel = h.getHi();
					break;
				}
			}
		}
		return hiLabel;
	}

	/**
	 * Give an EP ArrayList, determine which is the HiEP, and return the
	 * index (0 or 1).
	 * @param eps an EP ArrayList
	 * @param mrs the MRS in which <code>eps</code> comes from
	 * @return an ArrayList with the 0th the HiEP and the 1st the LoEP, or null if not found
	 *
	 * @deprecated this is only used in the first version of {@link com.googlecode.mrsqg#MrsTransformer}
	 */
	@Deprecated
	public static ArrayList<EP> determineHiLowEP (ArrayList<EP> eps, MRS mrs) {
		ArrayList<EP> hiloEPS = new ArrayList<EP>();

		//TODO: totally rewrite this with DMRS
		// it would be much simpler with a RSTR/H relation
		// e.g. the wedding will be held Monday.

		String hi=null, lo=null, rstr=null;
		EP hiEP =null, loEP = null;

		for (EP ep:eps) {
			rstr = ep.getValueByFeature("RSTR");
			if (rstr != null) {
				if (hiEP != null)
					log.warn ("more than one EP with RSTR value. DEBUG YOUR CODE!"+eps);
				hiEP = ep;
				hi = rstr;
			}
		}

		if (hi == null) return null;
		lo = mrs.getLoLabelFromHconsList(hi);
		if (lo == null) return null;

		for (EP ep:eps) {
			if (ep.getLabel().equals(lo) && ep.getArg0().equals(hiEP.getArg0())) {
				loEP = ep;
				break;
			}
		}

		if (loEP == null) return null;

		hiloEPS.add(hiEP);
		hiloEPS.add(loEP);

		return hiloEPS;

	}


	/**
	 * Change the type name of unknown words to avoid generation error.
	 *
	 * When FSC is input to cheap, NEs are labeled as NAMED_UNK_REL,
	 * which generates the following error in LKB generation:
	 * Warning: invalid predicates: |named_unk_rel("Washington DC")|
	 * Changing named_unk_rel to NAMED_REL solves this (hopefully).
	 */
	public void changeFromUnkToNamed () {
		for (EP ep:this.eps) {
			if (ep.getPred()!=null && ep.getTypeName().equalsIgnoreCase("NAMED_UNK_REL")) {
				ep.setTypeName("NAMED_REL");
			}
		}
	}

	/**
	 *
	 * This is used to check whether there are any basic_yofc_rel to prevent the following error:
	 * Warning: invalid predicates: |basic_yofc_rel("1980")|, |basic_yofc_rel("1982")|.
	 *
	 * The above is usually caused by:
	 * <pre>
          [ _in_p_temp_rel<0:1>
            LBL: h3
            ARG0: e4
            ARG1: e2
            ARG2: x6 [ x PERS: 3 NUM: SG IND: + ] ]
          [ proper_q_rel<1:2>
            LBL: h7
            ARG0: x6
            RSTR: h9
            BODY: h8 ]
          [ basic_yofc_rel<1:2>
            LBL: h10
            ARG0: x6
            ARG1: u11
            CARG: "1999" ]

     * One solution is to change to:
          [ _in_p_rel<0:1>
            LBL: h3
            ARG0: e4
            ARG1: e2
            ARG2: x6 [ x PERS: 3 NUM: SG ] ]
          [ number_q_rel<1:2>
            LBL: h7
            ARG0: x6
            RSTR: h9
            BODY: h8 ]
          [ card_rel<1:2>
            LBL: h10
            ARG0: x6
            ARG1: i11 [ i PERS: 3 NUM: PL ]
            CARG: "1999" ]

 	 * </pre>
     * 	@deprecated In ERG 1004 this problem is solved.
	 */
	public void preventInvalidPredicate () {
		String ep1Orig="_IN_P_TEMP_REL", ep1Dest="_IN_P_REL";
		String ep2Orig="PROPER_Q_REL", ep2Dest="NUMBER_Q_REL";
		String ep3Orig="BASIC_YOFC_REL", ep3Dest="CARD_REL";

		for (int i=2;i<eps.size();i++) {
			if (eps.get(i).getTypeName().equals(ep3Orig) &&
					eps.get(i-1).getTypeName().equals(ep2Orig) &&
					eps.get(i-2).getTypeName().equals(ep1Orig)) {
				eps.get(i).setTypeName(ep3Dest);
				eps.get(i-1).setTypeName(ep2Dest);
				eps.get(i-2).setTypeName(ep1Dest);
			} else if (eps.get(i).getTypeName().equals(ep3Orig)) {
				// change it anyway
				eps.get(i).setTypeName(ep3Dest);
			}
		}
	}

	/**
	 * Normalize unknown words in an MRS, see erg/lkb/sample.mrs
	 * or a sample file and *mrs-normalization-heuristics* in
	 * erg/lkb/mrsglobals.lsp for the rules.
	 *
	 * This function is only called when parsing an MRS
	 */
	protected void normalizeUnknownWords () {

		String typeName;

		// erg/lkb/mrsglobals.lsp
		// _glimpy/JJ_u_unknown_rel -> _glimpy_a_unknown_rel
		for (EP ep:eps) {
			typeName = ep.getTypeName();
			if (!typeName.contains("_unknown_rel")) continue;
			if (typeName.contains("/JJ_u"))
				typeName = typeName.replaceFirst("/JJ_u", "_a");
			else if (typeName.contains("/JJR_u"))
				// adjective, comparative
				typeName = typeName.replaceFirst("/JJR_u", "_a");
			else if (typeName.contains("/JJS_u"))
				// adjective, superlative
				typeName = typeName.replaceFirst("/JJS_u", "_a");
			else if (typeName.contains("/NN_u"))
				typeName = typeName.replaceFirst("/NN_u", "_n");
			else if (typeName.contains("/RB_u"))
				// adverb
				typeName = typeName.replaceFirst("/RB_u", "_a");
			else if (typeName.contains("/FW_u"))
				// foreign word
				typeName = typeName.replaceFirst("/FW_u", "_n");
			else if (typeName.contains("/NNS_u")) {
				String noun, stem = null;
				Pattern p = Pattern.compile("_(.+)/(.+?)_.*rel");
				Matcher m = p.matcher(typeName);
				if (m.find()) {
					noun = m.group(1);
				} else {
					log.error("Regex didn't find the plural noun from "+typeName);
					continue;
				}
				stem = SnowballStemmer.stem(noun);
				typeName = "_"+stem+"_n_unknown_rel";
			} else if (typeName.contains("/VB")) {
				String verb, stem = null, pos;
				Pattern p = Pattern.compile("_(.+)/(.+?)_.*rel");
				Matcher m = p.matcher(typeName);
				if (m.find()) {
					verb = m.group(1);
					pos = m.group(2);
				} else {
					log.error("Regex didn't find the verb from "+typeName);
					continue;
				}
				// VB, VBD, VBG, VBN, VBP, VBZ
				if (pos.equalsIgnoreCase("VB")||pos.equalsIgnoreCase("VBP")) {
					stem = verb;
				} else if (pos.equalsIgnoreCase("VBD")||pos.equalsIgnoreCase("VBN")) {
					// past tense or past participle, IrregularVerbs can only deal with this
					String[] stems = IrregularVerbs.getInfinitive(verb);
					if (stems != null) stem = stems[0];
				}
				if (stem == null) {
					// unfortunately we didn't find out the stem of the verb
					stem = SnowballStemmer.stem(verb);
				}
				// construct the typeName, such as _baze_v_rel
				typeName = "_"+stem+"_v_unknown_rel";
				// we don't care about its argument, let LKB handle it
			}
			ep.setTypeName(typeName);
		}
	}

	/**
	 * Get all the extra pairs in this MRS.
	 *
	 * Extra pairs are sth. like [TENSE: PRES].
	 *
	 * @return an ArrayList of all extra pairs
	 */
	public ArrayList<LinkedHashMap<String, String>> getExtrapair () {
		ArrayList<LinkedHashMap<String, String>> list = new ArrayList<LinkedHashMap<String, String>>();
		Var v;
		LinkedHashMap<String, String> p;
		for (EP ep:this.eps) {
			for (FvPair f: ep.getFvpair()) {
				if((v=f.getVar())!= null) {
					if ((p=v.getExtrapair())!=null) {
						list.add(p);
					}
				}
			}
		}

		return list;
	}

	/**
	 * Get a list of FvPair which has value matching <code>value</code>.
	 * @param value a matching value, such as "x2".
	 * @return an ArrayList of FvPair (empty if not found)
	 */
	public ArrayList<FvPair> getFvPairByValue (String value) {
		ArrayList<FvPair> list = new ArrayList<FvPair>();
		for (EP ep:this.eps) {
			for (FvPair p:ep.getFvpair()) {
				if (p.getValue()!=null && p.getValue().equals(value)) {
					list.add(p);
					break;
				}
			}
		}

		return list;
	}

	/**
	 * Set the sentence force of all events variables to "QUES". Theoretically only
	 * the events of predicates should be set, but practically all of them are set.
	 */
	public void setAllSF2QUES () {
		for (LinkedHashMap<String, String> p:getExtrapair()) {
			if (p.get("SF")!=null) {
				p.put("SF", "QUES");
			}
		}
	}

	/**
	 * Set the sentence force of the main event variables to "QUES".
	 */
	public void setSF2QUES() {
		try {
			for (FvPair p: getExtraTypeByValue(getIndex())) {
				p.getVar().setExtrapairValue("SF", "QUES");
			}
		} catch (java.lang.NullPointerException e) {
			log.error("Error:", e);
			log.error("MRS is malformed with index: "+getIndex());
			log.error(this.toString());
		}
	}

	/**
	 * Extract a new MRS from <code>mrs</code>, containing only EPs that are indirectly associated with label.
	 * currently, the label should only be a predicate's label. for instance, an EP looks like:
	 * <pre>
	 * [ _like_v_1_rel<5:10>
  	 * LBL: h8
  	 * ARG0: e9
  	 * ARG1: x6
     * ARG2: x10
	 * ]
	 * </pre>
	 * then all EPs with x6 and x10 as ARG* (indirectly) are extracted. Those EPs make a new MRS.
	 * @param label the label value of the predicate, such as "h8"
	 * @param mrs the original mrs to be extracted from
	 * @return a new MRS with only EPs concerning label
	 *
	 * @deprecated The same functionality can be achieved by DMRS operation.
	 */
	public static MRS extractByLabelValue (String label, MRS mrs) {
		MRS extracted = new MRS(mrs);
		// targetEP is the one with a label as the label in the parameter

		EP targetEP = null;
		for (EP ep:extracted.getEPbyLabelValue(label)) {
			if (ep.getArg0().equals(mrs.getIndex())) {
				// the target EP is the main predicate of this MRS
				targetEP = ep;
			}
		}
		// targetEPS is a list of all EPs that have connections with targetEP

		if (targetEP == null) {
			log.error("Can't find the EP with a label " + label +" in MRS:\n" + mrs);
			return null;
		}

		HashSet<String> argSet = targetEP.getAllARGvalue();
		HashSet<String> referredLabelSet = extracted.getAllReferredLabelByEP(targetEP);
		if (argSet.size() <= 1) {
			log.warn("the EP "+targetEP+" contains less than 2 ARG." +
					" Decomposition will probably fail.");
		}

		// suppose targetEP is the main predicate of the sentence,
		// then the main event would be ARG0 of targetEP.
		String event = targetEP.getArg0();
		if (event.startsWith("e")) {
			extracted.setIndex(event);
		} else {
			log.error("ARG0 of EP isn't an event: "+targetEP);
		}

		// Remove all EPs whose ARG0 is not associated with ARG* of targetEP
		// TODO: this is only the simplest case. A good algorithm should do it
		// recursively: other relevant EPs might be attached to EPs which are
		// not targetEP (Currently also consider the labels of relevant
		// EPs who have ARG0 in the argSet of targetEP -- so it's not that simple case).
		ArrayList<EP> copy = new ArrayList<EP>(extracted.getEps());
		for (EP ep:copy) {
			if (!argSet.contains(ep.getArg0()) && !referredLabelSet.contains(ep.getLabel())) {
				if (!extracted.removeEP(ep)) {
					log.error("Error: EP " +ep+ " can't be removed from MRS:\n" + extracted);
				}
			}
		}

		// clean up HCONS list
		extracted.cleanHCONS();

		return extracted;
	}

	/**
	 * Extract a new MRS from mrs, containing only EPs that are directly associated with label.
	 * This method is used when the label isn't a predicate's label. for instance, an EP looks like:
	 * <pre>
	 * [ _like_v_1_rel<5:10>
  	 * LBL: h8
  	 * ARG0: e9
  	 * ARG1: x6
     * ARG2: x10
	 * ]
	 * </pre>
	 * then all EPs with x6 and x10 as ARG0 (directly) are extracted. Those EPs make a new MRS.
	 * @param targetEP an EP to find references for
	 * @param mrs the original mrs to be extracted from
	 * @return a new MRS with only EPs concerning <code>targetEP</code>
	 *
	 * @deprecated The same functionality can be achieved by DMRS operation.
	 */
	public static MRS extractByEPandArg0 (EP targetEP, MRS mrs) {

		if (targetEP == null) {
			log.error("Can't find the EP " + targetEP +" in MRS:\n" + mrs);
			return null;
		}

		MRS extracted = new MRS(mrs);

		HashSet<String> argSet = targetEP.getAllARGvalue();
		if (argSet.size() <= 1) {
			log.warn("the EP "+targetEP+" contains less than 2 ARG." +
					" Decomposition will probably fail.");
		}

		extracted.markDeletionByEPref(targetEP);

		extracted.removeEPbyFlag(true);
		// clean up HCONS list
		extracted.cleanHCONS();

		return extracted;
	}

	/**
	 * This method returns a new MRS containing all EPs referred by label, both
	 * directly or indirectly. The new MRS doesn't contain any EPs referred by
	 * <code>exceptionEP</code>. Note this method doesn't set the event index
	 * of the whole MRS. You have to do it yourself.
	 * @param label a h* label
	 * @param exceptionEP
	 * @return a new MRS, or null if none matches
	 *
	 * @deprecated The same functionality can be achieved by DMRS operation.
	 */
	public MRS extractByLabel (String label, EP exceptionEP) {
		MRS mrs = new MRS(this);
		HashSet<String> set = new HashSet<String>();
		HashSet<String> moreSet = new HashSet<String>();
		set.add(label);
		String loLabel = mrs.getLoLabelFromHconsList(label);
		if (loLabel!=null) set.add(loLabel);
		if (exceptionEP != null)
			mrs.getEps().get(this.getEps().indexOf(exceptionEP)).setFlag(true);
		int oldSize = 0;

		// get all h* and x* referred by label and loLabel
		for (String l:set) {
			ArrayList<EP> list = mrs.getEPbyLabelValue(l);
			if (list == null) continue;
			for (EP ep:list) {
				for (String v:ep.getAllValue()) {
					if (v.startsWith("h") || v.startsWith("x")) {
						moreSet.add(v);
						String lo = mrs.getLoLabelFromHconsList(v);
						if (lo != null) moreSet.add(lo);
					}
				}
			}
		}
		set.addAll(moreSet);

		// loop recursively to find out all referred EPs.
		while (oldSize < set.size()) {
			oldSize = set.size();
			for (EP ep:mrs.getEps()) {
				if (ep.getFlag()) continue;
				for (String v:ep.getAllValueAndLabel()) {
					if (v.startsWith("h")) {
						String lo = mrs.getLoLabelFromHconsList(v);
						if (lo!=null && set.contains(lo))
							set.add(lo);
					}
					if (set.contains(v)) {
						set.add(v);
						for (String vv:ep.getAllValueAndLabel()) {
							if (vv.startsWith("h") || vv.startsWith("x")) set.add(vv);
						}
					}
				}
			}
		}

		for (EP ep:mrs.getEps()) {
			if (ep.getFlag()) continue;
			boolean flag = true;
			for (String v:ep.getAllValue()) {
				if (set.contains(v)) {
					flag = false;
					break;
				}
			}
			ep.setFlag(flag);
		}

		if (mrs.removeEPbyFlag(true)) {
			mrs.cleanHCONS();
			mrs.postprocessing();
			return mrs;
		} else return null;
	}

	/**
	 * This method returns the range of EPs referred by value, both
	 * directly or indirectly. For instance, in sentence "John likes green
	 * apples and red oranges.", if <code>exceptionEP</code> is "and", and
	 * <code>value</code> is R-INDX of <code>exceptionEP</code>, then it
	 * returns the range of "red oranges".
	 * @param value a x* value
	 * @param exceptionEP
	 * @return a range {cfrom, cto}
	 */
	public int[] extractRangeByXValue (String value, EP exceptionEP) {
		MRS mrs = this;
		HashSet<String> set = new HashSet<String>();
		set.add(value);

		if (exceptionEP != null)
			mrs.getEps().get(this.getEps().indexOf(exceptionEP)).setFlag(true);
		int oldSize = 0;

		// loop recursively to find out all referred EPs.
		while (oldSize < set.size()) {
			oldSize = set.size();
			for (EP ep:mrs.getEps()) {
				if (ep.getFlag()) continue;
				for (String v:ep.getAllValueAndLabel()) {
					if (v.startsWith("h")) {
						String lo = mrs.getLoLabelFromHconsList(v);
						if (lo!=null && set.contains(lo))
							set.add(lo);
					}
					if (set.contains(v)) {
						set.add(v);
						for (String vv:ep.getAllValueAndLabel()) {
							if (vv.startsWith("h") || vv.startsWith("x")) set.add(vv);
						}
					}
				}
			}
		}

		for (EP ep:mrs.getEps()) {
			if (ep.getFlag()) continue;
			boolean flag = true;
			for (String v:ep.getAllValue()) {
				if (set.contains(v)) {
					flag = false;
					break;
				}
			}
			ep.setFlag(flag);
		}


		int cfrom=10000, cto=0;
		for (EP ep:mrs.getEps()) {
			if (!ep.getFlag()) {
				if (ep.getCfrom()<cfrom) cfrom = ep.getCfrom();
				if (ep.getCto() > cto) cto = ep.getCto();
			}
		}

		mrs.setAllFlag(false);
		if (cto>cfrom) return new int[]{cfrom, cto};
		else return null;
	}

	/**
	 * Clean up the HCONS list.
	 *
	 * Any HCONS pairs, such as "h1 qeq h2", whose
	 * hiLabel and loLabel can't be both found in the EPS, are removed.
	 * This step is very important in avoiding unnecessary memory consumption
	 * in LKB, see <a href="http://lists.delph-in.net/archive/lkb/2010/000242.html">this post</a>.
	 */
	public void cleanHCONS () {
		ArrayList<String> labelList = this.getEPSfeatList();
		ArrayList<String> handleList = this.getEPSvalueList();
		ArrayList<HCONS> hcopy = new ArrayList<HCONS>(this.getHcons());
		for (HCONS h:hcopy) {
			if (!handleList.contains(h.getHi()) || !labelList.contains(h.getLo())) {
				if (!this.removeHCONS(h)) {
					log.error("Error: HCONS "+h+" can't be removed from MRS:\n" + this);
				}
			}
		}
	}

	/**
	 * This method retrieves the labels
	 * of all EPs which are referred by the ARG0 values of <code>ep</code>.
	 * @param ep An EP which has ARG* entries
	 * @return a HashSet of labels referred by the ARG0 of this <code>ep</code> (empty if not found).
	 */
	public HashSet<String> getAllReferredLabelByEP (EP ep) {
		HashSet<String> labelSet = new HashSet<String>();

		labelSet.add(ep.getLabel());
		HashSet<String> argList = ep.getAllARGvalue();

		for (EP e:getEps()) {
			if (e==ep) continue;
			for (String label:argList) {
				if (e.getArg0().equals(label)) {
					labelSet.add(e.getLabel());
					break;
				}
			}
		}

		return labelSet;
	}

	/**
	 * Mark deletion of one EP by judging its ARG0 doesn't refer to
	 * any ARG* values of <code>ep</code>
	 * @param ep An EP which has ARG* entries
	 *
	 * @deprecated this function is only used by another deprecated function
	 */
	protected void markDeletionByEPref (EP ep) {

		HashSet<String> argList = ep.getAllARGvalue();

		for (EP e:getEps()) {
			if (e==ep) continue;
			if (!argList.contains(e.getArg0())) {
				e.setFlag(true);
			}
		}
	}

	/**
	 * remove <code>ep</code> from the EPS list
	 * @param ep the ep to be removed
	 * @return success status
	 */
	public boolean removeEP (EP ep) {

		if (eps.remove(ep)==false) {
			log.error("Can't remove ep:\n"+ep+"\nfrom EPS list:\n"+eps);
			return false;
		}
		return true;
	}

	/**
	 * remove all EPs whose flag is set to <code>flag</code> from the EPS list.
	 *
	 * After a successful removal, {@link MRS#postprocessing()} is called.
	 * @return a boolean success status
	 */
	public boolean removeEPbyFlag (boolean flag) {
		ArrayList<EP> removedList = new ArrayList<EP>();
		for (EP ep:this.eps) {
			if (ep.getFlag() == flag) {
				removedList.add(ep);
			}
		}

		if(this.eps.removeAll(removedList)) {
			// rebuild dependencies since something is removed
			postprocessing();
			return true;
		} else {
			log.error("Removing EP by flag failed!");
			if (removedList.size() == 0) {
				log.error("None of EPS is set to have a "+ flag +" flag!");
			}
			log.error(this.toString());
			return false;
		}

	}

	/**
	 * Set the flag of all the EPS to <code>flag</code>
	 * @param flag a boolean value
	 */
	public void setAllFlag (boolean flag) {
		for (EP ep:this.getEps()) {
			ep.setFlag(flag);
		}
	}

	/**
	 * remove a <code>list</code> of EP from the EPS list
	 * @param list an ArrayList of EP
	 * @return success status
	 */
	public boolean removeEPlist (ArrayList<EP> list) {
		boolean ret = false;
		for (EP ep:list) {
			ret = eps.remove(ep);
			if (!ret) {
				log.error("Can't remove ep:\n"+ep+"\nfrom EPS list:\n"+eps);
				break;
			}
		}

		return ret;
	}

	/**
	 * remove h from the hcons list
	 * @param h the HCONS to be removed
	 * @return success status
	 */
	public boolean removeHCONS (HCONS h) {
		return hcons.remove(h);
	}

	/**
	 * Generate a list of the index for unused labels.
	 *
	 * @param num the number of unused labels to return
	 * @return an ArrayList containing <code>num</code> members, such as "7", "8", "9", ...
	 */
	public ArrayList<String> generateUnusedLabel (int num) {
		HashSet<Integer> valueSet = new HashSet<Integer>();
		ArrayList<String> list = new ArrayList<String>();

		// LKB takes index globally, so we count everything under "h/x/e/p..."
		// http://lists.delph-in.net/archive/developers/2010/001402.html
		for (EP e:getEps()) {
			for (String value:e.getAllValue()) {
				valueSet.add(Integer.parseInt(value.substring(1)));
			}
			valueSet.add(Integer.parseInt(e.getLabel().substring(1)));
		}

		Integer[] valueArray = (Integer[]) valueSet.toArray(new Integer[]{});
		Arrays.sort(valueArray);
		if (valueArray.length == 0) return null;
		for (int i=valueArray[valueArray.length-1]+1; num>0; i++, num--) {
			list.add(String.valueOf(i));
		}

		return list;
	}

	/**
	 * output as an MRX String.
	 *
	 * LKB cannot read properly indented XML so a one-line string is output.
	 * @return a one-line string with an &lt;mrs&gt; element
	 */
	public String toMRXstring() {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		toXML(os);
		String mrx = os.toString();
		return mrx;
	}

	/**
	 * Output MRS to an OutputStream <code>os</code>.
	 * @param os an OutputStream
	 */
	public void toXML(OutputStream os) {
		OutputFormat of = new OutputFormat("XML","UTF-8",true);
		// LKB doesn't support properly indented xml files. thus set indentation off.
		of.setIndenting(false);
		//of.setIndent(1);
		//of.setDoctype(null, "mrs.dtd");
//		FileOutputStream fos = null;
//		try {
//			fos = new FileOutputStream("");
//		} catch (FileNotFoundException e) {
//			log.error("Error:", e);
//		}
		XMLSerializer serializer = new XMLSerializer(os,of);
		// SAX2.0 ContentHandler.
		ContentHandler hd;
		try {
			hd = serializer.asContentHandler();
			hd.startDocument();
			AttributesImpl atts = new AttributesImpl();
			// <mrs>
			hd.startElement("", "", "mrs", atts);
			// <label  vid='1'/>
			atts.clear();
			atts.addAttribute("", "", "vid", "CDATA", label_vid);
			hd.startElement("", "", "label", atts);
			hd.endElement("", "", "label");
			// <var vid='2'/>
			atts.clear();
			atts.addAttribute("", "", "vid", "CDATA", index_vid);
			hd.startElement("", "", "var", atts);
			hd.endElement("", "", "var");
			// <ep>
			for (EP e: eps) {
				e.serializeXML(hd);
			}
			// <hcons>
			for (HCONS h: hcons) {
				h.serializeXML(hd);
			}

			hd.endElement("", "", "mrs");

		} catch (IOException e) {
			log.error("Error:", e);
		} catch (SAXException e) {
			log.error("Error:", e);
		}

	}


	/**
	 * This method builds cross references for an MRS representation.
	 * After reading/parsing an MRX, the extra type (Var) of some arguments are
	 * referenced individually. This method make them refer to the same one.
	 */
	public void buildCoref() {
		HashMap<String, Var> varM = new HashMap<String, Var>();
		String label;
		Var v;
		for (EP ep: eps) {
			for (FvPair p:ep.getFvpair()) {
				v = p.getVar();
				// only deal with argument
				if (v!=null && v.getSort().equals("x")) {
					label = v.getLabel();
					if (varM.get(label) == null) {
						varM.put(label, v);
					} else {
						p.setVar(varM.get(label));
					}
				}
			}
		}
	}

	/**
	 * Maps each characteristic variable with an EP, such as that
	 * "x28" maps with:
	 * 	<pre>
	   [ "_cat_n_1_rel"<8:9>
            LBL: h32
            ARG0: x28 ]
        </pre>
	 */
	public void mapCharacteristicVariables () {
		String arg0;
		for (EP ep: eps) {
			arg0 = ep.getArg0();
			if (!arg0.startsWith("x") && !arg0.startsWith("e")) continue;
			ArrayList<EP> arg0EPlist = this.getEPbyFeatAndValue("ARG0", arg0);
			if (arg0EPlist.size()==1) {
				this.charVariableMap.put(arg0, arg0EPlist.get(0));
			} else if (arg0EPlist.size()==2) {
				/*
				 * Multiple EPs can have arg0 as their ARG0. Usually these multiple
				 * EPs are in a qeq relation.
				 */
				boolean ep0HasRstr = arg0EPlist.get(0).hasFeature("RSTR");
				boolean ep1HasRstr = arg0EPlist.get(1).hasFeature("RSTR");
				if (ep0HasRstr && !ep1HasRstr) {
					this.charVariableMap.put(arg0, arg0EPlist.get(1));
				} else if (ep1HasRstr && !ep0HasRstr) {
					this.charVariableMap.put(arg0, arg0EPlist.get(0));
				} else {
					/*
					 * in some cases, the 2 EPs are not in a qeq relation.
					 * e.g. It uses swords that are much closer to blades.
[ _close_a_to_rel<46:52>
  LBL: h8
  ARG0: e23 [ e SF: PROP TENSE: PRES MOOD: INDICATIVE PROG: - PERF: - ]
  ARG1: x4 [ x PERS: 3 NUM: PL IND: + ]
  ARG2: x24 [ x PERS: 3 NUM: PL IND: + ]
]
[ COMP_REL<46:52>
  LBL: h8
  ARG0: e23 [ e SF: PROP TENSE: PRES MOOD: INDICATIVE PROG: - PERF: - ]
  ARG1: e23 [ e SF: PROP TENSE: PRES MOOD: INDICATIVE PROG: - PERF: - ]
]
					 *
					 */
					EP dEP = MRS.getDependentEP(arg0EPlist);
					if (dEP != null)
						this.charVariableMap.put(arg0, dEP);
					else {
						log.error("MapCharacteristicVarariable error, DEBUG YOUR CODE!");
						log.error(arg0EPlist);
					}
				}

			} else {
				EP dEP = MRS.getDependentEP(arg0EPlist);
				if (dEP != null)
					this.charVariableMap.put(arg0, dEP);
				else {
					log.error("MapCharacteristicVarariable error, DEBUG YOUR CODE!");
					log.error(arg0EPlist);
				}
			}

		}
	}

	/**
	 * Build dependencies for each EP, find out their governors and dependents.
	 */
	public void buildDependencies() {
		//TODO: PARG_D?
		/*
		 * whether this EP is a hiEP in a qeq relation, by
		 * indicating whether the RSTR feature exists
		 */
		String rstr;
		DMRS.POST_SLASH postSlash = DMRS.POST_SLASH.NOTHING;
		EP dEP = null;
		DMRS dmrsL, dmrsR;
		String argNum;

		for (EP ep: eps) {
			rstr = null;
			dEP = null;
			for (FvPair p:ep.getFvpair()) {
				if (p.getFeature().equals("RSTR")) {
					rstr = p.getValue();
					break;
				}
			}
			if (rstr == null) {
				if (ep.getFvpair().size()==1) {
					/* This EP doesn't govern any other EP
					    [ _man_n_1_rel<4:7>
						  LBL: h7
						  ARG0: x6 [ x PERS: 3 NUM: SG ]
						]
					 */
					continue;
				} else {
					for (FvPair pair:ep.getFvpair()) {
						String feature = pair.getFeature();
						String value = pair.getValue();
						argNum = "";
						dEP = null;
						boolean isArgFeature = feature.startsWith("ARG");
						if (feature.equals("ARG0")) continue;
						if (isArgFeature) {
							argNum = pair.getFeature().substring(3);
						}
						if (feature.equals("RSTR")) continue;
						if (feature.equals("BODY")) continue;
						ArrayList<EP> l=null;
						if (value.startsWith("x") || value.startsWith("e")) {
							dEP = this.charVariableMap.get(value);
							if (dEP == null) {
								continue;
							}
							if (dEP.getLabel().equals(ep.getLabel())) {
								postSlash = DMRS.POST_SLASH.EQ;
							} else {
								postSlash = DMRS.POST_SLASH.NEQ;
							}
						} else if (value.startsWith("h")) {
							/* HEQ (one ep’s argument is the other ep’s label)
L-HNDL:h8 -> _like_v-1_rel
[ _AND_C_REL<16:19>
  LBL: h15
  ARG0: e2 [ e SF: PROP TENSE: PRES MOOD: INDICATIVE PROG: - PERF: - ]
  L-HNDL: h8
  L-INDEX: e10 [ e SF: PROP TENSE: PRES MOOD: INDICATIVE PROG: - PERF: - ]
  R-HNDL: h17
  R-INDEX: e16 [ e SF: PROP TENSE: PRES MOOD: INDICATIVE PROG: - PERF: - ]
]
[ _like_v_1_rel<5:10>
  LBL: h8
  ARG0: e10 [ e SF: PROP TENSE: PRES MOOD: INDICATIVE PROG: - PERF: - ]
  ARG1: x6 [ x PERS: 3 NUM: SG GEND: M IND: + ]
  ARG2: x9 [ x PERS: 3 NUM: PL IND: + ]
]
							 */
							l = this.getEPbyLabelValue(value);
							if (l != null) {
								dEP = getDependentEP(l);
								postSlash = DMRS.POST_SLASH.HEQ;
							} else {
								/*
								 * H (qeq relationship)
								 */
								String loLabel = this.getLoLabelFromHconsList(value);
								if (loLabel != null) value = loLabel;
								l = this.getEPbyLabelValue(value);
								if (l!=null) {
									dEP = getDependentEP(l);
									postSlash = DMRS.POST_SLASH.H;
								}
							}
						} else {
						}
						if (dEP != null) {
							if (isArgFeature) {
								dEP.addGovernorByArg(ep);
								ep.addDependentByArg(dEP);
								dmrsL = new DMRS(dEP, DMRS.PRE_SLASH.ARG, postSlash, DMRS.DIRECTION.DEP, argNum);
								dmrsR = new DMRS(ep, DMRS.PRE_SLASH.ARG, postSlash, DMRS.DIRECTION.GOV, argNum);

							} else {
								dEP.addGovernorByNonArg(ep);
								ep.addDependentByNonArg(dEP);
								dmrsL = new DMRS(dEP, DMRS.mapPreSlash(feature), postSlash, DMRS.DIRECTION.DEP);
								dmrsR = new DMRS(ep, DMRS.mapPreSlash(feature), postSlash, DMRS.DIRECTION.GOV);
							}
							ep.addDmrs(dmrsL);
							dEP.addDmrs(dmrsR);
						} else {
							if (isArgFeature) {
								// ARG*/NULL situation
								dmrsL = new DMRS(null, DMRS.PRE_SLASH.ARG, DMRS.POST_SLASH.NULL, DMRS.DIRECTION.NULL, argNum);
								ep.addDmrs(dmrsL);
							}
						}
					}
				}

			} else {
				/*
				 *   _THE_Q_REL<0:3>
					  LBL: h3
					  ARG0: x6 [ x PERS: 3 NUM: SG ]
					  RSTR: h5
					  BODY: h4
				 */
				String loLabel = this.getLoLabelFromHconsList(rstr);
				/*
				 * find out all EPs with a loLabel. There could be multiple ones, especially
				 * in the case of qeq relations. To choose loEP from multiple ones, rule out
				 * others by the fact that loEP has the same ARG0 with hiEP
				 */
				ArrayList<EP> loList = getEPbyLabelValue(loLabel);
				// possible bug from LKB to parse the sentence "there was a problem with the laws in the 1700s in England."
				//
				if (loList == null) continue;
				for (EP eep:loList) {
					if (eep.getArg0().equals(ep.getArg0())) {
						dEP = eep;
					}
				}
				if (dEP==null) dEP = getDependentEP(loList);
				if (dEP!=null) {
					dEP.addGovernorByNonArg(ep);
					ep.addDependentByNonArg(dEP);
					dmrsL = new DMRS(dEP, DMRS.PRE_SLASH.RSTR, DMRS.POST_SLASH.H, DMRS.DIRECTION.DEP);
					dmrsR = new DMRS(ep, DMRS.PRE_SLASH.RSTR, DMRS.POST_SLASH.H, DMRS.DIRECTION.GOV);
					ep.addDmrs(dmrsL);
					dEP.addDmrs(dmrsR);
				} else {
					log.error("DEBUG YOUR CODE! Can't find a loEP for "+ep);
				}
			}
		}

		// a final run to find out NULL/EQ relation
		/*
		 * The following code finds out NULL/EQ but doesn't take "equalities" as described in
		 * Copestake's dmrs.pdf into account. Thus it over-produces NULL/EQ relations.
		 * For instance, in "how many roses does Mary have", MEASURE_REL and _rose_n_1_rel
		 * should have NULL/EQ relation (according to my theory) but LKB doesn't indicate this.
		 * Since NULL/EQ relation is basically useless in sentence decomposition, the implementation
		 * is not enabled here.
		 */
		/*
		HashSet<String> labelSet = new HashSet<String>();
		String label;
		ArrayList<ElementaryPredication> list = new ArrayList<ElementaryPredication>();
		for (ElementaryPredication ep:this.eps) {
			label = ep.getLabel();
			if (labelSet.contains(label)) continue;

			labelSet.add(label);
			list = this.getEPbyLabelValue(label);
			if (list == null) continue;
			for (int i=0; i<list.size(); i++) {
				for (int j=i+1;j<list.size();j++) {
					if (!list.get(i).isInDmrsSet(list.get(j))) {
						dmrsL = new DMRS(list.get(j), DMRS.PRE_SLASH.NULL, DMRS.POST_SLASH.EQ, DMRS.DIRECTION.NULL);
						dmrsR = new DMRS(list.get(i), DMRS.PRE_SLASH.NULL, DMRS.POST_SLASH.EQ, DMRS.DIRECTION.NULL);
						list.get(i).addDmrs(dmrsL);
						list.get(j).addDmrs(dmrsR);
						log.warn("Found a NULL/EQ relation between: \n"+list.get(i)+list.get(j));
					}
				}
			}
		}
		*/
		for (EP ep:this.eps) {
			if (ep.getDmrsSet().size() == 0) {
				log.error("EP "+ep+" doesn't have any dependencies from MRS: "+this.toString());
				log.error("YOU MIGHT HAVE FOUND A BUG FROM ERG!");
			}
		}
	}


	/**
	 * Find out the dependent EP from a list of EP. Usually this list of EP has the same
	 * label but all except one governs the dependent EP
	 * @param list An non-empty ElementaryPredication list
	 * @return the dependent EP, or null if not found
	 */
	public static EP getDependentEP (ArrayList<EP> list) {
		if (list==null||list.size()==0) return null;
		if (list.size() == 1) return list.get(0);
		EP dEP = null;

		int nGovernor = 0;
		HashSet<String> valueSet = new HashSet<String>();
		for (int i=list.size()-1; i>=0; i--) {
			// usually the last one is the dependent, so we loop backward
			dEP = list.get(i);
			valueSet.clear();
			nGovernor = 0;
			valueSet.add(dEP.getLabel());
			valueSet.add(dEP.getArg0());
			for (EP ep:list) {
				if (ep==dEP) continue;
				for (String v:ep.getAllValueExceptArg0()) {
					if (valueSet.contains(v)) {
						nGovernor++;
						break;
					}
				}
			}
			// all other EPs are the governor of this EP, we found the dependent
			if (nGovernor == list.size()-1) break;
		}
		if (nGovernor == list.size()-1)
			return dEP;
		else {

			// first try to build dependencies for the EPs in the list
			String arg0;
			HashSet<String> argSet;
			for (EP ep1:list) {
				arg0 = ep1.getArg0();
				for (EP ep2:list) {
					if (ep2==ep1) continue;
					argSet = ep2.getAllARGvalueExceptARG0();
					if (argSet.contains(arg0)) {
						ep1.addGovernorByArg(ep2);
						ep2.addDependentByArg(ep1);
					}

				}
			}

			dEP = null;
			for (EP ep:list) {
				if (getLevelGovernors(ep, 0) == list.size()-1) {
					dEP = ep;
					break;
				}
			}

			if (dEP!=null) return dEP;
			else {
				// last chance: check whether this is a /EQ relation
				boolean shareSameLabel = true;
				String firstLabel = list.get(0).getLabel();
				for (int i=1; i<list.size(); i++) {
					if (!list.get(i).getLabel().equals(firstLabel)) {
						shareSameLabel = false;
					}
				}
				if (shareSameLabel) {
					// Bingo, we have a very rare /EQ relation here
					for (EP e:list) {
						// every EP has the same equalLabelSet
						e.addAllEqualLabelSet(list);
					}
					// let the last one be the depEP
					dEP = list.get(list.size()-1);
				}
				if (dEP!=null) return dEP;
				else {
					log.error("Can't find the dependent EP from:\n"+list);
					log.error("DEBUG YOUR CODE (if this is not a /EQ relation)!");
					return null;
				}
			}
		}
	}

	/**
	 * Find out how many layers of governors <code>ep</code> has.
	 * @param ep an EP
	 * @param level initial level offset, usually 0
	 * @return an integer number
	 */
	protected static int getLevelGovernors (EP ep, int level) {
		HashSet<EP> set = ep.getGovernorsByArg();
		int max = level, n;
		for (EP e:set) {
			n = getLevelGovernors(e, level+1);
			if (n > max) {
				max = n;
			}
		}
		return max;
	}

	/**
	 * Wrapper code for {@link MRS#decompose(HashSet, HashSet, boolean, boolean)}.
	 *
	 * All related EPS are set to a false flag. Must call {@link MRS#removeEPbyFlag(boolean)}
	 * to remove them.
	 *
	 */
	public HashSet<EP> doDecompositionbyEP (EP rEP, EP eEP, boolean relaxEQ, boolean keepEQ) {
		HashSet<EP> rEPS = new HashSet<EP>();
		HashSet<EP> eEPS = new HashSet<EP>();
		if (rEP!=null) rEPS.add(rEP);
		if (eEP!=null) eEPS.add(eEP);
		return doDecomposition(rEPS, eEPS, relaxEQ, keepEQ);
	}

	/**
	 * Wrapper code for {@link MRS#decompose(HashSet, HashSet, boolean, boolean)}.
	 *
	 * All related EPS are set to a false flag. Must call {@link MRS#removeEPbyFlag(boolean)}
	 * to remove them.
	 *
	 * @param rEPS a set of target EPS
	 * @param eEPS a set of exception EPS
	 * @param relaxEQ whether to relax /EQ to /NEQ
	 * @param keepEQ whether to keep EPs with /EQ relations with <code>rEPS</code>
	 * @return a set of EPs related to <code>rEPS</code>
	 */
	public HashSet<EP> doDecomposition (HashSet<EP> rEPS, HashSet<EP> eEPS, boolean relaxEQ, boolean keepEQ) {

		if (eEPS == null)
			eEPS = new HashSet<EP>();
		if (rEPS.size() == 1 && rEPS.contains(null)) {
			return new HashSet<EP>();
		}

		this.setAllFlag(true);
		HashSet<EP> retEPS = decompose(rEPS, eEPS, relaxEQ, keepEQ);
		if (retEPS.size()!=0) {
			for (EP ep:retEPS)
				ep.setFlag(false);
		}
		return retEPS;
	}

	/**
	 * Wrapper code for {@link MRS#decompose(HashSet, HashSet, boolean, boolean)}.
	 *
	 * All related EPS are set to a false flag. Must call {@link MRS#removeEPbyFlag(boolean)}
	 * to remove them.
	 *
	 * @param label the label for the target EP
	 * @param eEP a single exception EP
	 */
	public HashSet<EP> doDecompositionByLabel (String label, EP eEP, boolean relaxEQ, boolean keepEQ) {

		HashSet<EP> eEPS = new HashSet<EP>();
		eEPS.add(eEP);

		return doDecompositionByLabelSet(label, eEPS, relaxEQ, keepEQ);
	}

	/**
	 * Wrapper code for {@link MRS#decompose(HashSet, HashSet, boolean, boolean)}.
	 *
	 * All related EPS are set to a false flag. Must call {@link MRS#removeEPbyFlag(boolean)}
	 * to remove them.
	 *
	 */
	public HashSet<EP> doDecompositionByLabelSet (String label, HashSet<EP> eEPS, boolean relaxEQ, boolean keepEQ) {
		HashSet<EP> rEPS = new HashSet<EP>();

		// find out all the EPs label governs, these are EPs we'd like to keep
		if (label.startsWith("h")) {
			String loLabel = this.getLoLabelFromHconsList(label);
			if (loLabel != null) label = loLabel;
			ArrayList<EP> tmp = this.getEPbyLabelValue(label);
			if (tmp == null) return rEPS;
			else
				rEPS.addAll(tmp);
		} else {
			rEPS.add(this.charVariableMap.get(label));
		}

		if (eEPS == null)
			eEPS = new HashSet<EP>();

		this.setAllFlag(true);
		HashSet<EP> retEPS = decompose(rEPS, eEPS, relaxEQ, keepEQ);
		if (retEPS.size()!=0) {
			for (EP ep:retEPS)
				ep.setFlag(false);
		}
		return retEPS;
	}


	/**
	 * Decompose this MRS.
	 *
	 * This algorithm find out all EPs that are related to <code>rEPS</code>.
	 * See detailed explanation in the author's master thesis
	 *
	 * @param rEPS a set of target EPS
	 * @param eEPS a set of exception EPS
	 * @param relaxEQ whether to relax /EQ to /NEQ
	 * @param keepEQ whether to keep EPs with /EQ relations with <code>rEPS</code>
	 * @return a set of EPs related to <code>rEPS</code>
	 */
	public HashSet<EP> decompose(HashSet<EP> rEPS, HashSet<EP> eEPS,
			boolean relaxEQ, boolean keepEQ) {
		HashSet<EP> retEPS = new HashSet<EP>();
		if (eEPS == null) eEPS = new HashSet<EP>();
		if (rEPS.size() == 0) return rEPS;
		EP ep;
		DMRS.PRE_SLASH preSlash;
		DMRS.POST_SLASH postSlash;
		DMRS.DIRECTION direction;

		for (EP tEP: rEPS) {
			if (eEPS.contains(tEP)) continue;
			if (tEP == null) continue;
			for (DMRS dmrs:tEP.getDmrsSet()) {

				preSlash = dmrs.getPreSlash();
				if (preSlash == DMRS.PRE_SLASH.NULL) continue;
				ep = dmrs.getEP();
				if (rEPS.contains(ep) || eEPS.contains(ep)) continue;
				postSlash = dmrs.getPostSlash();
				direction = dmrs.getDirection();
				//!ep.getTypeName().equals("PARG_D_REL")
				if (direction == DMRS.DIRECTION.DEP) {
					// ep is the dependant of tEP
					if (relaxEQ && postSlash == DMRS.POST_SLASH.EQ &&
							(tEP.isVerbEP() || (tEP.isPrepositionEP() && dmrs.isPreArg2())) ) {
						/*
						 *  assign tEP a new label. we could have assigned ep a new label but in
						 *  this way we have to fix the qeq relations since in most cases ep is
						 *  an NP. tEP is usually a verb EP or preposition EP that doesn't involve
						 *  qeq relations.
						 */
						String newLabel = "h"+this.generateUnusedLabel(1).get(0);
						EP.assignNewLabel(tEP, ep, newLabel);
					}
					retEPS.add(ep);
				} else if (direction == DMRS.DIRECTION.GOV) {
					if (!keepEQ && (ep.isVerbEP() || ep.isPrepositionEP()) &&
							postSlash == DMRS.POST_SLASH.EQ && !ep.hasEPemptyArgs()) {
						continue;
					}
					if (!((postSlash == DMRS.POST_SLASH.NEQ || postSlash == DMRS.POST_SLASH.H) &&
							(ep.isVerbEP() || ep.isPassiveEP() || ep.isPrepositionBefore(rEPS)))) {
						/*
						 * ep.isPrepositionBefore(rEPS): we want to remove the preposition if rEPS
						 * is in a PP, such as that "with people suffering badly...", we don't want
						 * "with", but in a normal sentence, such as "people suffer badly with pain",
						 * we want to keep the PP.
						 */
						retEPS.add(ep);
					}
				} else {
				}

			}
		}


		if (retEPS.size() != 0) {
			rEPS.addAll(retEPS);
			return decompose(rEPS, eEPS, false, true);
		} else
			return rEPS;
	}

	/**
	 * Set the flag of all EPs that are related to <code>label</code> to false, all
	 * other EPs and <code>excepEP</code> are set to true
	 * @param label a String indicating an EP, starting with an "h" or "x".
	 * @param excepEP an exception EP, whose flag is always set to true.
	 *
	 * @deprecated replaced by full DMRS operation
	 */
	public void keepDependentEPbyLabel (String label, EP excepEP) {
		HashSet<EP> depSet = new HashSet<EP>();

		this.setAllFlag(true);

		// find out all the EPs label governs, these are EPs we'd like to keep
		if (label.startsWith("h")) {
			String loLabel = this.getLoLabelFromHconsList(label);
			if (loLabel != null) label = loLabel;
			depSet.addAll(this.getEPbyLabelValue(label));
		} else {
			depSet.add(this.charVariableMap.get(label));
		}
		setAllConnectionsFlag(depSet, excepEP, false);
	}

	/**
	 * Used to extract all dependents from a verb EP. vEP is kept.
	 * @param vEP
	 *
	 * @deprecated replaced by full DMRS operation
	 */
	public void keepDependentEPfromVerbEP (EP vEP) {
		this.setAllFlag(true);
		HashSet<EP> depSet = new HashSet<EP>();

		// keep all vEP's governors
		depSet.addAll(vEP.getGovernorsByArg());
		depSet.addAll(vEP.getGovernorsByNonArg());
		depSet.addAll(vEP.getDependentsByNonArg());
		depSet.addAll(vEP.getEqualLabelSet());

		setAllConnectionsFlag(depSet, vEP, false);

		for (EP ep:vEP.getDependentsByArg()) {
			depSet.clear();
			// keep all dependents EP after vEP
			if (ep.getCfrom() >= vEP.getCfrom() && vEP.getDependentsByArg().size() != 1) {
				depSet.add(ep);
				setAllConnectionsFlag(depSet, vEP, false);
			} else {
			/*
			 * For dependents EP before vEP, probably this EP is vEP's
			 * ARG1 EP, we have to remove any preprosition EP that governs this EP.
			 */
				depSet.add(ep);
				setAllConnectionsFlagExceptPP(depSet, vEP, false);
			}
		}
		vEP.setFlag(false);

	}


	/**
	 * Used to extract a sentence from a verb EP. vEP is kept.
	 * @param vEP
	 * @param excepEP
	 *
	 * @deprecated replaced by full DMRS operation
	 */
	public void keepDependentEPandVerbEP (EP vEP, EP excepEP) {
		this.setAllFlag(true);
		HashSet<EP> depSet = new HashSet<EP>();

		// keep all vEP's governors
		if (vEP==null)
			return;
		depSet.addAll(vEP.getGovernorsByArg());
		depSet.addAll(vEP.getGovernorsByNonArg());
		depSet.addAll(vEP.getDependentsByNonArg());
		depSet.addAll(vEP.getEqualLabelSet());

		if (depSet.contains(excepEP)) depSet.remove(excepEP);
		setAllConnectionsFlag(depSet, vEP, false);

		for (EP ep:vEP.getDependentsByArg()) {
			depSet.clear();
			// keep all dependents EP after vEP
			if (ep.getCfrom() >= vEP.getCfrom() && vEP.getDependentsByArg().size() != 1) {
				depSet.add(ep);
				setAllConnectionsFlag(depSet, vEP, false);
			} else {
			/*
			 * For dependents EP before vEP, probably this EP is vEP's
			 * ARG1 EP, we have to remove any preprosition EP that governs this EP.
			 */
				depSet.add(ep);
				setAllConnectionsFlagExceptPP(depSet, vEP, false);
			}
		}
		excepEP.setFlag(true);
		vEP.setFlag(false);

	}

	/**
	 * Set the flag of all connections (governors & dependents) of EP in <code>depSet</code>
	 * to <code>flag</flag>, with the flag of <code>excepEP</code> unset.
	 * @param depSet
	 * @param excepEP
	 * @param flag
	 *
	 * @deprecated replaced by full DMRS operation
	 */
	public static void setAllConnectionsFlag (HashSet<EP> depSet,
			EP excepEP, boolean flag) {
		for (EP ep:depSet) {
			if (ep!=excepEP && ep.getFlag() != flag && !ep.getTypeName().equals("PARG_D_REL")) {
				ep.setFlag(flag);
				setAllConnectionsFlag(ep.getAllConnections(), excepEP, flag);
			} else if (ep.getTypeName().equals("PARG_D_REL")) {
				ep.setFlag(flag);
			}
		}
	}

	/**
	 *
	 * Set the flag of all connections (governors & dependents, excluding prepositions)
	 * of EP in <code>depSet</code>
	 * to <code>flag</flag>, with the flag of <code>excepEP</code> unset.
	 * @param depSet
	 * @param excepEP
	 * @param flag
	 *
	 * @deprecated replaced by full DMRS operation
	 */
	public static void setAllConnectionsFlagExceptPP (HashSet<EP> depSet,
			EP excepEP, boolean flag) {
		for (EP ep:depSet) {
			if (ep!=excepEP && ep.getFlag() != flag && !ep.getTypeName().equals("PARG_D_REL")
					&& !ep.getTypeName().toLowerCase().contains("_p_")
					&& !ep.getTypeName().toLowerCase().contains("_v_")) {
				ep.setFlag(flag);
				setAllConnectionsFlagExceptPP(ep.getAllConnections(), excepEP, flag);
			} else if (ep.getTypeName().equals("PARG_D_REL")) {
				ep.setFlag(flag);
			}
		}
	}

	/**
	 * This method parses a MRS document in XML, then calls {@link #buildCoref}.
	 *
	 * @param file an MRS XML file
	 */
	public void parse(File file) {
		this.parser.parse(file);
		//preventInvalidPredicate();
		normalizeUnknownWords();
		postprocessing();
	}

	/**
	 * This method parses a MRS document in a string, then calls {@link #buildCoref}.
	 *
	 * @param str a string containing an MRS structure
	 */
	public void parseString(String str) {
		this.parser.parseString(str);
		//preventInvalidPredicate();
		normalizeUnknownWords();
		postprocessing();
	}

	/**
	 *
	 * Use RegEx to match all <mrs/> in a multiline string
	 *
	 * @param multiline cheap output spreading multilines
	 * @return an array list containing all <mrs/> elements
	 */
	public static ArrayList<String> getMrxStringsFromCheap (String multiline) {
		if (multiline==null) return null;
		ArrayList<String> list = new ArrayList<String>();

		Pattern p = Pattern.compile("<mrs>(.*?)<\\/mrs>",
				Pattern.MULTILINE|Pattern.DOTALL);
		Matcher m = p.matcher(multiline);
		while (m.find()) {
			String mrs = m.group();
			list.add(mrs);
		}

		if (list.size()==0) {
			log.error("Cheap output:");
			log.error("No parsed MRS from Cheap:\n"+multiline);
		}
		return list;
	}

	/**
	 * Simple test program to read and parse an MRX file
	 * @param args a list of MRX files
	 * @throws Exception
	 */
	public static void main(String args[])
	throws Exception {
		MRS m = new MRS();
		for(String file:args) {
			File f = new File(file);
			m.parse(f);
			System.out.println("done");
			m.toXML(System.out);
			System.out.println(m);
		}
	}
}