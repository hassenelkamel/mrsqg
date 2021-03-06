package com.googlecode.mrsqg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.googlecode.mrsqg.analysis.Term;
import com.googlecode.mrsqg.analysis.TermExtractor;
import com.googlecode.mrsqg.nlp.NETagger;
import com.googlecode.mrsqg.nlp.OpenNLP;
import com.googlecode.mrsqg.util.Dictionary;
import com.googlecode.mrsqg.util.StringUtils;



/**
 * This class preprocesses a declarative sentence.
 *
 * @author Xuchen Yao
 * @version 2010-02-26
 */
public class Preprocessor {

	private static Logger log = Logger.getLogger(Preprocessor.class);

	private int countOfSents = 0;
	private String[][] tokens;
	private String[][] pos;
	private String[][] npChunks;
	private String[][] ppChunks;
	private String[][] chunks;
	private String[] sentences;
	private String[][][] nes;
	//private ArrayList<String>[] to;
	private Term[][] terms;
	//private boolean[] firstCapitalize;
	/** <code>Dictionaries</code> for term extraction. */
	private static ArrayList<Dictionary> dicts = new ArrayList<Dictionary>();

	public String getOriginalSentence() {return this.sentences[0];}
	public 	String[][] getTokens() {return this.tokens;}
	public String[] getSentences() {return this.sentences;}
	public String getFirstSent() {return this.sentences[0];}
	public Term[][] getTerms () {return this.terms;}
	public int getNumTokens() {return this.tokens[0].length;}
	public String[][] getNpChunks() {return this.npChunks;}
	public String[][] getPpChunks() {return this.ppChunks;}
	public String[][] getChunks() {return this.chunks;}
	public String[][] getPos() {return this.pos;}

	public boolean preprocess (String sents, boolean singleSentence) {
		log.info("Preprocessing");
		String[] originalSentences;

		if (singleSentence) {
			originalSentences = new String[]{sents};
		} else {
			originalSentences = OpenNLP.sentDetect(sents);
		}
		this.countOfSents = originalSentences.length;
		log.info("Count of original one: "+countOfSents);

		String original;
		this.tokens = new String[countOfSents][];
		this.pos = new String[countOfSents][];
		this.npChunks = new String[countOfSents][];
		this.ppChunks = new String[countOfSents][];
		this.chunks = new String[countOfSents][];
		this.sentences = new String[countOfSents];
		for (int i = 0; i < countOfSents; i++) {
			original = originalSentences[i];
			log.info("Sentence "+i+": "+original);
			//tokens[i] = NETagger.tokenize(original);
			tokens[i] = OpenNLP.tokenize(original);
			pos[i] = OpenNLP.tagPos(tokens[i]);
			chunks[i] = OpenNLP.tagChunks(tokens[i], pos[i]);
			npChunks[i] = OpenNLP.joinNounPhrases(tokens[i], chunks[i]);
			ppChunks[i] = OpenNLP.joinCoordPhrases(tokens[i], chunks[i]);
//			log.info("NP chunks: ");
//			for (int j=0; j<chunks[i].length; j++) {
//				log.info(chunks[i][j]);
//			}
			// temporarily avoid errors such as invalid predicates: |"_thermoplastics_nns_rel"|
			// X. Yao 2010-05-16: Disable it to use the new LKB/logon with generation from unknown words.
//			for (int j=0; j<pos[i].length; j++) {
//				if (pos[i][j].equals("NNS")) pos[i][j] = "NNPS";
//			}
			sentences[i] = StringUtils.concatWithSpaces(this.tokens[i]);
		}

		this.terms = new Term[this.countOfSents][];
		// extract named entities
		this.nes = NETagger.extractNes(this.tokens);
		if (this.nes != null) {
			for (int i=0; i<this.countOfSents; i++){
				original = originalSentences[i];
				this.terms[i] = TermExtractor.getTerms(original, "", this.nes[i],
						Preprocessor.getDictionaries());
				log.info("Sentence "+i+" terms:");
				for (int j=0; j<this.terms[i].length; j++) {
					log.info(this.terms[i][j]+"  ");
				}
			}
		}

		return true;
	}

	/**
	 * return the preposition before a term t in sentence number sentNum, if any
	 * @param t the term, such as "Germany"
	 * @param sentNum the sentence number
	 * @return the preposition, such as "in"
	 */
	public String getPrepositionBeforeTerm (Term t, int sentNum) {
		String p = null;
		int start = t.getFrom()-1;
		if (start < 0) start = 0;
		String pPos = pos[sentNum][start];
		if (pPos.equalsIgnoreCase("IN")) {
			p = tokens[sentNum][t.getFrom()-1];
		}
		return p;
	}

	/**
	 * Output preprocessed sentence to FSC format by tokens
	 *
	 * <p>This is the <tt>fsc.dtd</tt>: </p>
	 *
	 * <pre>
	 * &lt;!ELEMENT fsc ( chart ) &gt;
	 * &lt;!ATTLIST fsc version NMTOKEN #REQUIRED &gt;
	 *
	 * &lt;!ELEMENT chart ( text, lattice ) &gt;
	 * &lt;!ATTLIST chart id CDATA #REQUIRED &gt;
	 *
	 * &lt;!ELEMENT text ( #PCDATA ) &gt;
	 *
	 * &lt;!ELEMENT lattice ( edge* ) &gt;
	 *
	 * &lt;!ATTLIST lattice final CDATA #REQUIRED &gt;
	 * &lt;!ATTLIST lattice init CDATA #REQUIRED &gt;
	 *
	 * &lt;!ELEMENT edge ( fs ) &gt;
	 * &lt;!ATTLIST edge source CDATA #REQUIRED &gt;
	 * &lt;!ATTLIST edge target CDATA #REQUIRED &gt;
	 *
	 * &lt;!ELEMENT fs ( f* ) &gt;
	 * &lt;!ATTLIST fs type CDATA #REQUIRED &gt;
	 *
	 * &lt;!ELEMENT f ( fs | str )* &gt;
	 * &lt;!ATTLIST f name CDATA #REQUIRED &gt;
	 * &lt;!ATTLIST f org ( list ) #IMPLIED &gt;
	 *
	 * &lt;!ELEMENT str ( #PCDATA ) &gt;
	 * </pre>
	 *
	 */

	@Deprecated
	public void outputFSC () {

		if (countOfSents == 0) {
			log.info("No input sentence.");
			return;
		}
		String sent = sentences[0];
		int nTokens = tokens[0].length;
		String[] tokens = this.tokens[0];

		OutputFormat of = new OutputFormat("XML","UTF-8",true);
		of.setCDataElements(new String[]{"str"});
		of.setIndent(1);
		of.setIndenting(true);
		XMLSerializer serializer = new XMLSerializer(System.out,of);
		// SAX2.0 ContentHandler.
		ContentHandler hd;
		try {
			hd = serializer.asContentHandler();
			hd.startDocument();
			AttributesImpl atts = new AttributesImpl();
			// <fsc version="1.0">
			atts.addAttribute("", "", "version", "CDATA", "1.0");
			hd.startElement("", "", "fsc", atts);

			// <chart id="fsc-test">
			atts.clear();
			atts.addAttribute("", "", "id", "CDATA", "fsc");
			hd.startElement("", "", "chart", atts);

			// <text>The dog chases the orc.</text>
			atts.clear();
			hd.startElement("", "", "text", atts);
			hd.characters(sent.toCharArray(), 0, sent.length());
			hd.endElement("", "", "text");

			// <lattice init="v0" final="v6">
			atts.clear();
			atts.addAttribute("", "", "init", "CDATA", "v0");
			atts.addAttribute("", "", "final", "CDATA", "v"+Integer.toString(nTokens));
			hd.startElement("", "", "lattice", atts);

			int tokenStart = 0;
			int tokenLen = 0;
			for (int i=0; i<nTokens; i++) {
				tokenLen = tokens[i].length();
				// <edge source="v0" target="v1">
				atts.clear();
				atts.addAttribute("", "", "source", "CDATA", "v"+Integer.toString(i));
				atts.addAttribute("", "", "target", "CDATA", "v"+Integer.toString(i+1));
				hd.startElement("", "", "edge", atts);

				// <fs type="token">
				atts.clear();
				atts.addAttribute("", "", "type", "CDATA", "token");
				hd.startElement("", "", "fs", atts);

				// <f name="+FORM"><str>The</str></f>
				atts.clear();
				atts.addAttribute("", "", "name", "CDATA", "+FORM");
				hd.startElement("", "", "f", atts);
				atts.clear();
				hd.startElement("", "", "str", atts);
				hd.characters(tokens[i].toCharArray(), 0, tokenLen);
				hd.endElement("", "", "str");
				hd.endElement("", "", "f");

				// <f name="+FROM"><str>0</str></f>
				String num;
				atts.clear();
				atts.addAttribute("", "", "name", "CDATA", "+FROM");
				hd.startElement("", "", "f", atts);
				atts.clear();
				hd.startElement("", "", "str", atts);
				num = Integer.toString(tokenStart);
				hd.characters(num.toCharArray(), 0, num.length());
				hd.endElement("", "", "str");
				hd.endElement("", "", "f");

				// <f name="+TO"><str>3</str></f>
				atts.clear();
				atts.addAttribute("", "", "name", "CDATA", "+TO");
				hd.startElement("", "", "f", atts);
				atts.clear();
				hd.startElement("", "", "str", atts);
				num = Integer.toString(tokenStart+tokenLen);
				hd.characters(num.toCharArray(), 0, num.length());
				hd.endElement("", "", "str");
				hd.endElement("", "", "f");

				hd.endElement("", "", "fs");
				hd.endElement("", "", "edge");

//				<f name="+TNT">
//	              <fs type="tnt">
//	                <f name="+TAGS" org="list"><str>DT</str></f>
//	                <f name="+PRBS" org="list"><str>1.000000e+00</str></f>
//	              </fs>
//	            </f>


				// 1 for a space
				tokenStart += (tokenLen+1);
			}



			hd.endElement("", "", "lattice");
			hd.endElement("", "", "chart");
			hd.endElement("", "", "fsc");
		} catch (IOException e) {
			log.error("Error:", e);
		} catch (SAXException e) {
			log.error("Error:", e);
		}
	}

	/**
	 * Output preprocessed sentence to FSC format by terms. <code>tokenPos</code>
	 * controls whether to also output the POS tags of tokens. If set to false,
	 * then only output POS of terms. When sentences are highly possible to contain
	 * unknown words, set it to true so PET parses.
	 *
	 * @param os an output stream, could be a file, stdout, etc.
	 * @param tokenPos a boolean value.
	 */
	public void outputFSCbyTerms (OutputStream os, boolean tokenPos) {

		if (countOfSents == 0) {
			log.error("No input sentence.");
			return;
		}
		String sent = sentences[0];
		int nTokens = tokens[0].length;
		String[] tokens = this.tokens[0];
		Term[] terms = this.terms[0];
		String[] pos = this.pos[0];
		/*
		 * OpenNLP POS tagger use `` as the opening double quote and
		 * '' as the closing double quote, but ERG only accepts "???
		 */
		// PUNCTUATION IN CM
		// http://lists.delph-in.net/archive/pet/2010-August/000139.html
//		for (int i=0; i<pos.length; i++) {
//			if (pos[i].equals("''")) pos[i]="”";
//			else if (pos[i].equals("``")) pos[i]="“";
//		}

		OutputFormat of = new OutputFormat("XML","UTF-8",true);
		// a bug in PET requires CDATA
		// http://lists.delph-in.net/archive/pet/2010-June/000102.html
		of.setCDataElements(new String[]{"str"});
		of.setIndent(1);
		of.setIndenting(true);
		XMLSerializer serializer = new XMLSerializer(os,of);
		// SAX2.0 ContentHandler.
		ContentHandler hd;
		try {
			hd = serializer.asContentHandler();
			hd.startDocument();
			AttributesImpl atts = new AttributesImpl();
			// <fsc version="1.0">
			atts.addAttribute("", "", "version", "CDATA", "1.0");
			hd.startElement("", "", "fsc", atts);

			// <chart id="fsc-test">
			atts.clear();
			atts.addAttribute("", "", "id", "CDATA", "fsc");
			hd.startElement("", "", "chart", atts);

			// <text>Al Gore was born in Washington DC .</text>
			atts.clear();
			hd.startElement("", "", "text", atts);
			hd.characters(sent.toCharArray(), 0, sent.length());
			hd.endElement("", "", "text");

			// <lattice init="v0" final="v8">
			atts.clear();
			atts.addAttribute("", "", "init", "CDATA", "v0");
			atts.addAttribute("", "", "final", "CDATA", "v"+Integer.toString(nTokens));
			hd.startElement("", "", "lattice", atts);

			int tokenStart = 0;
			int tokenLen = 0;
			int step = 1;
			for (int i=0; i<nTokens; i+=step) {
				tokenLen = tokens[i].length();
				step = 1;
				Term term = null;

				for (Term t:terms) {
					if (t.getFrom() == i) {
						// tokens starting from i is a term
						step = t.getTo() - t.getFrom();
						term = t;
						break;
					} else if (t.getFrom() > i) {
						break;
					}
				}
				// <edge source="v0" target="v2">
				atts.clear();
				if (term == null) {
					atts.addAttribute("", "", "source", "CDATA", "v"+Integer.toString(i));
					atts.addAttribute("", "", "target", "CDATA", "v"+Integer.toString(i+1));
				} else {
					atts.addAttribute("", "", "source", "CDATA", "v"+term.getFrom());
					atts.addAttribute("", "", "target", "CDATA", "v"+term.getTo());
				}
				hd.startElement("", "", "edge", atts);

				// <fs type="token">
				atts.clear();
				atts.addAttribute("", "", "type", "CDATA", "token");
				hd.startElement("", "", "fs", atts);

				// <f name="+FORM"><str>Al Gore</str></f>
				atts.clear();
				atts.addAttribute("", "", "name", "CDATA", "+FORM");
				hd.startElement("", "", "f", atts);
				atts.clear();
				hd.startElement("", "", "str", atts);
				if (term == null) {
					hd.characters(tokens[i].toCharArray(), 0, tokenLen);
				} else {
					hd.characters(term.getText().toCharArray(), 0, term.getText().length());
				}
				hd.endElement("", "", "str");
				hd.endElement("", "", "f");

				// <f name="+FROM"><str>0</str></f>
				String num;
				atts.clear();
				atts.addAttribute("", "", "name", "CDATA", "+FROM");
				hd.startElement("", "", "f", atts);
				atts.clear();
				hd.startElement("", "", "str", atts);
				// the same start from token and term
				num = Integer.toString(tokenStart);
				hd.characters(num.toCharArray(), 0, num.length());
				hd.endElement("", "", "str");
				hd.endElement("", "", "f");

				// <f name="+TO"><str>7</str></f>
				atts.clear();
				atts.addAttribute("", "", "name", "CDATA", "+TO");
				hd.startElement("", "", "f", atts);
				atts.clear();
				hd.startElement("", "", "str", atts);
				if (term == null) {
					num = Integer.toString(tokenStart+tokenLen);
					hd.characters(num.toCharArray(), 0, num.length());
				} else {
					num = Integer.toString(tokenStart+term.getText().length());
					hd.characters(num.toCharArray(), 0, num.length());
				}
				hd.endElement("", "", "str");
				hd.endElement("", "", "f");

				if (tokenPos) {
	//				<f name="+TNT">
	//	              <fs type="tnt">
	//	                <f name="+TAGS" org="list"><str>DT</str></f>
	//	                <f name="+PRBS" org="list"><str>1.000000e+00</str></f>
	//	              </fs>
	//	            </f>
					atts.clear();
					atts.addAttribute("", "", "name", "CDATA", "+TNT");
					hd.startElement("", "", "f", atts);
					atts.clear();
					atts.addAttribute("", "", "type", "CDATA", "tnt");
					hd.startElement("", "", "fs", atts);

					// <f name="+TAGS" org="list"><str>DT</str></f>
					atts.clear();
					atts.addAttribute("", "", "name", "CDATA", "+TAGS");
					atts.addAttribute("", "", "org", "CDATA", "list");
					hd.startElement("", "", "f", atts);
					atts.clear();
					hd.startElement("", "", "str", atts);
					if (term != null)
						hd.characters(term.getPosFSC().toCharArray(), 0, term.getPosFSC().length());
					else
						hd.characters(pos[i].toCharArray(), 0, pos[i].length());
					hd.endElement("", "", "str");
					hd.endElement("", "", "f");

					//<f name="+PRBS" org="list"><str>1.000000e+00</str></f>
					atts.clear();
					atts.addAttribute("", "", "name", "CDATA", "+PRBS");
					atts.addAttribute("", "", "org", "CDATA", "list");
					hd.startElement("", "", "f", atts);
					atts.clear();
					hd.startElement("", "", "str", atts);
					hd.characters("1.000000e+00".toCharArray(), 0, "1.000000e+00".length());
					hd.endElement("", "", "str");
					hd.endElement("", "", "f");


					hd.endElement("", "", "fs");
					hd.endElement("", "", "f");

				}

				hd.endElement("", "", "fs");
				hd.endElement("", "", "edge");

				if (term == null) {
					// 1 for a space
					tokenStart += (tokenLen+1);
				} else {
					tokenStart += (term.getText().length()+1);
				}
			}

			hd.endElement("", "", "lattice");
			hd.endElement("", "", "chart");
			hd.endElement("", "", "fsc");
		} catch (IOException e) {
			log.error("Error:", e);
		} catch (SAXException e) {
			log.error("Error:", e);
		}
	}

	/**
	 * This functions first calls preprocess() and then output
	 * FSC XML by terms.
	 * @param input a raw sentence
	 * @param tokenPos a boolean value, whether to also output the POS tags of tokens.
	 *  If set to false, then only output POS of terms.
	 * @param singleSentence whether the input is a single sentence or not. If not,
	 * sentence detection is performed.
	 * @return a string representing FSC in XML
	 */
	public String getFSCbyTerms(String input, boolean tokenPos, boolean singleSentence) {

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		preprocess(input, singleSentence);
		outputFSCbyTerms(os, tokenPos);
		String fsc = os.toString();

		return fsc;
	}

	public static void addDictionary(Dictionary dict) {
		dicts.add(dict);
	}

	/**
	 * Returns the <code>Dictionaries</code>.
	 *
	 * @return dictionaries
	 */
	public static Dictionary[] getDictionaries() {
		return dicts.toArray(new Dictionary[dicts.size()]);
	}
	/**
	 * Unregisters all <code>Dictionaries</code>.
	 */
	public static void clearDictionaries() {
		dicts.clear();
	}

	public static void main(String[] args) {
		String answers  = "Al Gore was born in Washington DC. Al Gore lives in Washington DC.";
		Preprocessor t = new Preprocessor();
		// possibly fail because of dict is not loaded
		t.preprocess(answers, false);
	}

	public static String cleanInput (String input) {
		input = input.replaceAll("\\(.*?\\)", "");
		// PUNCTUATION IN CM
		// http://lists.delph-in.net/archive/pet/2010-August/000139.html
		input = input.replaceAll("\"", "");
		return input;
	}

}
