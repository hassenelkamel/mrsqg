package com.googlecode.mrsqg.mrs;

import org.apache.log4j.Logger;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * A class for feature-value pairs such as ARG0: x6 [ x PERS: 3 NUM: SG IND: + ]
 * @author Xuchen Yao
 *
 */
public class FvPair {
//	<!ELEMENT fvpair (rargname, (var|constant))>
//	<!ELEMENT rargname (#PCDATA)>
//	<!ELEMENT constant (#PCDATA)>

	/*
	 * !!! WARNING !!!
	 * Any new field added to this class must also be added to the copy constructor.
	 */

	private static Logger log = Logger.getLogger(FvPair.class);

	private String rargname = null;
	private String constant = null;
	private Var var = null;
	public String getValue() {return constant==null?(var!=null?var.getLabel():null):constant;}
	public String getFeature() {return rargname;}

	public void setFeature (String feat) {rargname = feat;}
	@Deprecated public String getRargname() {return rargname;}
	@Deprecated public String getConstant() {return constant;}
	public Var getVar() {return var;}
	public void setValue(String v) {if (var!=null) var.setLabel(v);}
	public void setRargname(String s) {rargname = s;}
	public void setConstant(String s) {constant = s;}
	public void setVar(Var v) {var = v;}

	@Override public String toString() {
		// RSTR: h5
		// ARG0: x6 [ x PERS: 3 NUM: SG IND: + ]
		StringBuilder res = new StringBuilder();
		res.append(rargname+": ");
		if (var!=null) res.append(var);
		// CARG: "Al Gore"
		if (constant!=null) res.append("\""+constant+"\"");

		return res.toString();
	}

	/**
	* Copy constructor.
	*/
	public FvPair(FvPair old) {
		if (old == null) return;
		this.rargname = old.getRargname();
		this.constant = old.getConstant();
		if (old.getVar()!=null)
			this.var = new Var(old.getVar());
	}

	public FvPair() {
	}

	/**
	 * a simple constructor, constructs an FvPair such as "RSTR: h9"
	 * @param rargname "RSTR"
	 * @param vid "9"
	 * @param sort "h"
	 */
	public FvPair(String rargname, String vid, String sort) {
		this.rargname = rargname;
		this.var = new Var(vid, sort);
	}

	/**
	 * a simple constructor, constructs an FvPair such as "RSTR: h9"
	 * @param feature "RSTR"
	 * @param value "h9"
	 */
	public FvPair(String feature, String value) {
		this.rargname = feature;
		this.var = new Var(value);
	}

	/**
	 * constructs a complex FvPair such as "ARG0: e13 [ e SF: PROP TENSE: UNTENSED MOOD: INDICATIVE ]"
	 * @param feature "ARG0"
	 * @param value "e13"
	 * @param extraPairs {"SF", "PROP", "TENSE", "UNTENSED", "MOOD", "INDICATIVE"}
	 */
	public FvPair(String feature, String value, String[] extraPairs) {
		this.rargname = feature;
		this.var = new Var(value, extraPairs);
	}

	/**
	 * constructs a complex FvPair from <code>feature</code> and <code>value</code>.
	 * @param feature a feature String, such as "ARG0".
	 * @param value a value Var.
	 */
	public FvPair(String feature, Var value) {
		this.rargname = feature;
		this.var = value;
	}

	public void serializeXML (ContentHandler hd) {
		AttributesImpl atts = new AttributesImpl();
		try {
			atts.clear();
			hd.startElement("", "", "fvpair", atts);
			// <rargname>ARG0</rargname>
			hd.startElement("", "", "rargname", atts);
			hd.characters(rargname.toCharArray(), 0, rargname.length());
			hd.endElement("", "", "rargname");

			if (var!=null) {
				var.serializeXML(hd);
			} else if (constant != null) {
				// <constant>John</constant>
				atts.clear();
				hd.startElement("", "", "constant", atts);
				hd.characters(constant.toCharArray(), 0, constant.length());
				hd.endElement("", "", "constant");
			}
			hd.endElement("", "", "fvpair");
		} catch (SAXException e) {
			log.error("Error:", e);
		}
	}
}
