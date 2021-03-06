package com.googlecode.mrsqg.mrs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.googlecode.mrsqg.util.StringUtils;

/**
 * A class for Elementary Predication
 *
 * @author Xuchen Yao
 *
 */

public class EP {
//	<!ELEMENT ep ((pred|realpred), label, fvpair*)>
//	<!ATTLIST ep
//	          cfrom CDATA #IMPLIED
//	          cto   CDATA #IMPLIED
//	          surface   CDATA #IMPLIED
//	      base      CDATA #IMPLIED >

	/*
	 * !!! WARNING !!!
	 * Any new field added to this class must also be added to the copy constructor.
	 */

	private static Logger log = Logger.getLogger(EP.class);

	private int cfrom = -1;
	private int cto = -1;
	private String surface = null;
	private String base = null;
	/** "upper case" relations*/
	private String pred = null;
	/** "lower case" relations */
	private String spred = null;
	private String label = null;
	private String label_vid = null;
	private ArrayList<FvPair> fvpair = null;

	/**
	 * The use of flag purely serves engineering purposes. In decomposition, some EPs are
	 * needed to be removed after a copy construction. But it's not easy to trace these EPs
	 * by any sort of equals() methods. So a flag is set to mark these to-be-removed EPs.
	 */
	protected boolean flag = false;

	private FvPair currentFvPair = null;
	/**
	 * A set of EPs which govern the current EP by ARG*. In the theory of a
	 * A -> B relation, A is called a head and B is its dependent. We call
	 * A a governor here since "head" is ambiguous if used alone.
	 * @deprecated replaced by full DMRS
	 */
	private HashSet <EP> governorsByArg = null;
	/**
	 * A set of EPs which govern the current EP by relations other
	 * than ARG*, such as RSTR
	 * @deprecated replaced by full DMRS
	 */
	private HashSet<EP> governorsByNonArg = null;

	/**
	 * @deprecated replaced by full DMRS
	 */
	private HashSet <EP> dependentsByArg = null;

	/**
	 * @deprecated replaced by full DMRS
	 */
	private HashSet<EP> dependentsByNonArg = null;

	/**
	 * the rare /EQ relation as in dmrs.pdf
	 * @deprecated replaced by full DMRS
	 */
	private HashSet<EP> equalLabelSet = null;

	/**
	 * The DMRS relations of this EP
	 */
	protected HashSet<DMRS> dmrsSet = null;

	/**
	* Copy constructor.
	*/
	public EP(EP old) {
		if (old == null) return;
		this.cfrom = old.getCfrom();
		this.cto = old.getCto();
		this.surface = old.getSurface();
		this.base= old.getBase();
		this.pred = old.getPred();
		this.spred = old.getSpred();
		this.label = old.getLabel();
		this.label_vid = old.getLabelVid();
		this.flag = old.getFlag();
		this.fvpair = new ArrayList<FvPair>();
		for(FvPair p:old.getFvpair()) {
			this.fvpair.add(new FvPair(p));
		}
		governorsByArg = new HashSet<EP>();
		governorsByNonArg = new HashSet<EP>();
		dependentsByArg = new HashSet<EP>();
		dependentsByNonArg = new HashSet<EP>();
		equalLabelSet = new HashSet<EP>();
		dmrsSet = new HashSet<DMRS>();
	}


	public EP() {
		fvpair = new ArrayList<FvPair>();
		governorsByArg = new HashSet<EP>();
		governorsByNonArg = new HashSet<EP>();
		dependentsByArg = new HashSet<EP>();
		dependentsByNonArg = new HashSet<EP>();
		equalLabelSet = new HashSet<EP>();
		dmrsSet = new HashSet<DMRS>();
	}

	/**
	 * Construct a new EP by simply setting its type name and label
	 * @param typeName type name for this EP
	 * @param label label of this EP
	 */
	public EP(String typeName, String label) {
		this();
		if (StringUtils.containsUppercase(typeName))
			this.pred = typeName;
		else
			this.spred = typeName;
		this.cfrom = 0;
		this.cto = 0;
		this.setLabel(label);
	}


	public int getCfrom() {return cfrom;}
	public int getCto() {return cto;}
	public String getSurface() {return surface;}
	public String getBase() {return base;}
	public String getPred() {return pred;}
	public String getSpred() {return spred;}
	public String getLabel() {return label;}
	public String getLabelVid() {return label_vid;}
	public ArrayList<FvPair> getFvpair() {return fvpair;}
	public String getTypeName() {if (pred!=null) return pred; else return spred;};
	public boolean getFlag () {return flag;}
	public void setFlag (boolean f) {this.flag = f;}

	public void setPred(String s) {pred=s;}
	public void setSpred(String s) {spred=s;}
	public void setLabelVid(String s) {label_vid=s;label="h"+s;}
	public void setLabel(String s) {label=s; label_vid=s.substring(1);}

	public HashSet<EP> getGovernorsByArg () { return governorsByArg;}
	public HashSet<EP> getGovernorsByNonArg () { return governorsByNonArg;}
	public HashSet<EP> getDependentsByArg () { return dependentsByArg;}
	public HashSet<EP> getDependentsByNonArg () { return dependentsByNonArg;}
	public HashSet<EP> getEqualLabelSet () { return equalLabelSet;}
	public HashSet<DMRS> getDmrsSet () {return dmrsSet;}
	/**
	 * Add an EP to the set of governors which refer to the current EP by ARG*.
	 * @param ep An EP
	 *
	 * @deprecated replaced by full DMRS
	 */
	public void addGovernorByArg (EP ep) {
		if (ep != this)
			governorsByArg.add(ep);
	}

	/**
	 * Add an EP to the set of governors which refer to the current EP by relations
	 * other than ARG*.
	 * @param ep An EP
	 *
	 * @deprecated replaced by full DMRS
	 */
	public void addGovernorByNonArg (EP ep) {
		if (ep != this)
			governorsByNonArg.add(ep);
	}

	/**
	 * Add an EP to the set of dependents referred by the current EP by ARG*.
	 * @param ep An EP
	 *
	 * @deprecated replaced by full DMRS
	 */
	public void addDependentByArg (EP ep) {
		if (ep != this)
			dependentsByArg.add(ep);
	}

	/**
	 * Add an EP to the set of dependents referred by the current EP by relations
	 * other than ARG*.
	 * @param ep An EP
	 *
	 * @deprecated replaced by full DMRS
	 */
	public void addDependentByNonArg (EP ep) {
		if (ep != this)
			dependentsByNonArg.add(ep);
	}

	/**
	 * Add an EP to the set of EPs which have the same label but don't refer each other
	 * other than ARG*.
	 * @param ep An EP
	 *
	 * @deprecated replaced by full DMRS
	 */
	public void addEqualLabelSet (EP ep) {
		if (ep != this)
			equalLabelSet.add(ep);
	}

	/**
	 * Add an EP set to the set of EPs which have the same label but don't refer each other
	 * other than ARG*.
	 * @param epCollection a collection of EP
	 *
	 * @deprecated replaced by full DMRS
	 */
	public void addAllEqualLabelSet (Collection<EP> epCollection) {
		for (EP ep:epCollection) {
			if (ep != this)
				equalLabelSet.add(ep);
		}
	}

	/**
	 * Add a DMRS to the set
	 * @param d a DMRS
	 */
	public void addDmrs (DMRS d) {
		dmrsSet.add(d);
	}

	public void clearDependencies () {
		governorsByArg.clear();
		governorsByNonArg.clear();
		dependentsByArg.clear();
		dependentsByNonArg.clear();
		equalLabelSet.clear();
		dmrsSet.clear();
	}


	/**
	 * Check whether <code>ep</code> is in the dmrsSet of this EP.
	 * @param ep
	 * @return a boolean value
	 */
	public boolean isInDmrsSet (EP ep) {
		boolean ret = false;
		for (DMRS d:dmrsSet) {
			if (d.getEP() == ep) {
				ret = true;
				break;
			}
		}
		return ret;
	}

	/**
	 * Return all EPs that are the governor or dependent of this EP
	 * @return a HashSet of EPs
	 *
	 * @deprecated replaced by full DMRS
	 */
	public HashSet<EP> getAllConnections() {
		HashSet<EP> connections = new HashSet<EP>();
		connections.addAll(governorsByArg);
		connections.addAll(governorsByNonArg);
		connections.addAll(dependentsByArg);
		connections.addAll(dependentsByNonArg);
		connections.addAll(equalLabelSet);
		return connections;
	}

	/**
	 * Set the type name of this EP.
	 *
	 * Type names are of 2 types: pred for higher level EPs such as
	 * APPOS_REL and spred for lexicon level EPs such as _red_a_1_rel.
	 * @param typeName
	 */
	public void setTypeName(String typeName) {
		if (StringUtils.containsUppercase(typeName)) {
			this.pred = typeName;
			this.spred = null;
		} else {
			this.spred = typeName;
			this.pred = null;
		}
	}
	/**
	 * return all "ARG*" values in this EP.
	 * for instance, an EP looks like:
	 * <pre>
	 * [ _like_v_1_rel<5:10>
  	 * LBL: h8
  	 * ARG0: e9
  	 * ARG1: x6
     * ARG2: x10
	 * ]
	 * </pre>
	 * then it returns a list containing "e9", "x6" and "x10"
	 *
	 * @return a HashSet containing all "ARG*" values
	 */
	public HashSet<String> getAllARGvalue() {

		HashSet<String> set = new HashSet<String>();
		for (FvPair fp:fvpair) {
			if (fp.getFeature().startsWith("ARG")) {
				set.add(fp.getVar().getLabel());
			}
		}

		return set;
	}

	/**
	 * return all "ARG*" except ARG0 values in this EP.
	 * for instance, an EP looks like:
	 * <pre>
	 * [ _like_v_1_rel<5:10>
  	 * LBL: h8
  	 * ARG0: e9
  	 * ARG1: x6
     * ARG2: x10
	 * ]
	 * </pre>
	 * then it returns a list containing "e9", "x6" and "x10"
	 *
	 * @return a HashSet containing all "ARG*" values
	 */
	public HashSet<String> getAllARGvalueExceptARG0() {

		HashSet<String> set = new HashSet<String>();
		for (FvPair fp:fvpair) {
			if (fp.getFeature().startsWith("ARG") && !fp.getFeature().equals("ARG0")) {
				set.add(fp.getVar().getLabel());
			}
		}

		return set;
	}

	/**
	 * Get all values in an EP, such as "x9", "e2", etc.
	 * @return a HashSet containing all values
	 */
	public HashSet<String> getAllValue() {

		HashSet<String> set = new HashSet<String>();
		for (FvPair fp:fvpair) {
			if (fp.getVar() != null)
				set.add(fp.getVar().getLabel());
		}

		return set;
	}


	/**
	 * Get all values in an EP, such as "x9", "e2", etc, except ARG0
	 * @return a HashSet containing all "ARG*" values
	 */
	public HashSet<String> getAllValueExceptArg0() {

		HashSet<String> set = new HashSet<String>();
		for (FvPair fp:fvpair) {
			if (fp.getVar() != null && !fp.getFeature().equals("ARG0"))
				set.add(fp.getVar().getLabel());
		}

		return set;
	}

	/**
	 * Get all values and label in an EP, such as "x9", "e2", etc.
	 * @return a HashSet containing all "ARG*" values
	 */
	public HashSet<String> getAllValueAndLabel() {

		HashSet<String> set = new HashSet<String>();
		for (FvPair fp:fvpair) {
			if (fp.getVar() != null)
				set.add(fp.getVar().getLabel());
		}
		set.add(this.getLabel());

		return set;
	}

	/*
	public ArrayList<String> getAllARGvalue() {
		// TreeMap guarantees that the map will be in ascending key order
		// so the returned values are sorted by their keys
		TreeMap<String, String> map = new TreeMap<String, String>();
		ArrayList<String> list = new ArrayList<String>();
		for (FvPair fp:fvpair) {
			if (fp.getFeature().startsWith("ARG")) {
				map.put(fp.getFeature(), fp.getVar().getLabel());
			}
		}
		for (String v:(String[])map.values().toArray(new String[0])) {
			list.add(v);
		}
		return list;
	}
	*/

	/**
	 * return the ARG0 value of this EP, if any
	 * @return the ARG0 value, such as "e2", or null if none
	 */
	public String getArg0() {
		return getValueByFeature("ARG0");
	}

	/**
	 * Whether this EP is an EP for verbs, whose type name matches "_v_"
	 * and ARG0 is an event (with SF:PROP).
	 * @return a boolean value
	 */
	public boolean isVerbEP() {
		return this.getTypeName().toLowerCase().contains("_v_") && this.getArg0().startsWith("e");
	}

	/**
	 * Whether this is a "ing" verb in an relative clause, such as "with people suffering badly."
	 * @return a boolean value
	 */
	public boolean isVerbIngEPinRelative () {
		boolean ret = false;

		ret = isVerbEP();

		if (ret) {
			if (this.getValueVarByFeature("ARG0") != null &&
					this.getValueVarByFeature("ARG0").getExtrapair().containsValue("UNTENSED") &&
					this.getValueVarByFeature("ARG0").getExtrapair().containsValue("+")) {
				ret = true;
			}
		}

		return ret;
	}

	/**
	 * Whether this EP is an EP for prepositions, whose type name matches "_p_".
	 * @return a boolean value
	 */
	public boolean isPrepositionEP() {
		return this.getTypeName().toLowerCase().contains("_p_");
	}

	/**
	 * Whether this EP is an EP for passive form, i.e. "PARG_D_REL"
	 * @return a boolean value
	 */
	public boolean isPassiveEP() {
		return this.getTypeName().toLowerCase().equals("parg_d_rel");
	}

	/**
	 * Whether an EP is a preposition and it's position is before a <code>set</code> of EPS.
	 * This is used to judge whether <code>set</code> is in a PP or not.
	 * @param set
	 * @return a boolean value
	 */
	public boolean isPrepositionBefore(HashSet<EP> set) {
		boolean ret = true;
		if (this.isPrepositionEP()) {
			for (EP ep:set) {
				if (this.getCto() > ep.getCfrom()) {
					ret = false;
					break;
				}
			}
		} else
			ret = false;

		return ret;
	}
	/**
	 * whether this EP has any ARG* /EQ dependency
	 * @return a boolean value
	 */
	public boolean hasEQarg() {
		boolean ret = false;
		for (DMRS dmrs:this.dmrsSet) {
			if (dmrs.getPreSlash() == DMRS.PRE_SLASH.ARG && dmrs.getPostSlash() == DMRS.POST_SLASH.EQ) {
				ret = true;
				break;
			}
		}
		return ret;
	}

	/**
	 * whether this EP has any ARG* /EQ dependency to any non-prepositions or non-verbs
	 * @return a boolean value
	 */
	public boolean hasEQargToNonPPorVerb() {
		boolean ret = false;
		for (DMRS dmrs:this.dmrsSet) {
			if (dmrs.getPreSlash() == DMRS.PRE_SLASH.ARG && dmrs.getPostSlash() == DMRS.POST_SLASH.EQ &&
					!dmrs.getEP().isVerbEP() && !dmrs.getEP().isPrepositionEP()) {
				ret = true;
				break;
			}
		}
		return ret;
	}


	/**
	 * whether this EP has any empty ARG*
	 * @return a boolean value
	 */
	public boolean hasEPemptyArgs () {
		boolean ret = false;
		for (DMRS dmrs:this.dmrsSet) {
			if (dmrs.getPreSlash() == DMRS.PRE_SLASH.ARG && dmrs.getPostSlash() == DMRS.POST_SLASH.NULL) {
				ret = true;
				break;
			}
		}
		return ret;
	}

	/**
	 * whether this EP has any empty ARG* excluding passive form.
	 *
	 * In passive form a (verb) EP is likely to have an empty argument.
	 * @return a boolean value
	 */
	public boolean hasEPemptyArgsExceptPassive () {
		boolean ret = false;
		for (DMRS dmrs:this.dmrsSet) {
			if (dmrs.getPreSlash() == DMRS.PRE_SLASH.ARG && dmrs.getPostSlash() == DMRS.POST_SLASH.NULL) {
				ret = true;
				break;
			}
		}
		for (DMRS dmrs:this.dmrsSet) {
			if (dmrs.getEP()!=null && dmrs.getEP().isPassiveEP()) {
				ret = false;
				break;
			}
		}
		return ret;
	}

	/**
	 * Given a preposition EP, finds out the verb EP it modifies
	 * @return a verb EP
	 */
	public static EP getVerbEP (EP pEP) {
		EP vEP = null;

		if (!pEP.isPrepositionEP()) {
			log.error("must be a preposition EP: "+pEP);
			return null;
		}

		for (DMRS dmrs:pEP.getDmrsSet()) {
			if (dmrs.getPreSlash() == DMRS.PRE_SLASH.ARG && dmrs.getPostSlash() == DMRS.POST_SLASH.EQ &&
					dmrs.getDirection() == DMRS.DIRECTION.DEP) {
				if (dmrs.getEP().isVerbEP()) {
					vEP =  dmrs.getEP();
					break;
				} else if (dmrs.getEP().isPrepositionEP()) {
					vEP = EP.getVerbEP(dmrs.getEP());
					break;
				}
			}
		}

		return vEP;
	}

	/**
	 * Set the label of <code>tEP</code> to a new label without modifying
	 * <code>eEP<code>'s label. Usually tEP and eEP have the same label but
	 * we want them different
	 * @param tEP the target EP
	 * @param eEP an exception EP
	 * @param label a new label
	 */
	public static void assignNewLabel(EP tEP, EP eEP, String label) {
		EP ep;
		for (DMRS dmrs:tEP.getDmrsSet()) {
			ep = dmrs.getEP();
			if (ep==eEP) continue;
			if (dmrs.getPreSlash() == DMRS.PRE_SLASH.ARG && dmrs.getPostSlash() == DMRS.POST_SLASH.EQ &&
					ep.getLabel().equals(tEP.getLabel()) && !ep.getLabel().equals(label)) {
				tEP.setLabel(label);
				/*
				 *  we have to do it recursively since in the cases of prepositions, tEP might involve
				 *  multiple ARG/EQ relations through a transitive chain. e.g.
				 *  the girl with who John fell in love.
				 *  "with", "in" and "fall" share the same label but "with" only connects with "fall"
				 *  through "in". A recursion is needed in this case.
				 */
				assignNewLabel(ep, eEP, label);
			}
		}
		tEP.setLabel(label);
	}


	/**
	 * Return the value of a feature.
	 *
	 * @param s can be "ARG0", "RSTR", "BODY", "ARG1", "ARG2"...
	 * @return a label, such as "x3", or null if not found
	 */
	public String getValueByFeature (String s) {
		String label = null;
		s = s.toUpperCase();
		for (FvPair p:fvpair) {
			if (p.getFeature().equals(s)) {
				label = p.getValue();
				break;
			}
		}
		return label;
	}

	/**
	 * Whether this EP has a certain feature <code>f</code>
	 * @param f a feature name, such as "RSTR"
	 * @return a boolean value
	 */
	public boolean hasFeature (String f) {
		boolean ret = false;
		for (FvPair p:fvpair) {
			if (p.getFeature().equals(f)) {
				ret = true;
				break;
			}
		}
		return ret;
	}

	/**
	 * Return the extra type (Var) of a feature.
	 * @param feat can be "ARG0", "RSTR", "BODY", "ARG1", "ARG2"...
	 * @return a corresponding Var
	 */
	public Var getValueVarByFeature (String feat) {
		Var v = null;

		for (FvPair p:fvpair) {
			if (p.getFeature().equalsIgnoreCase(feat)) {
				v = p.getVar();
				break;
			}
		}
		return v;
	}

	/**
	 * Delete a FvPair with a specific label.
	 *
	 * @param s can be "ARG0", "RSTR", "BODY", "ARG1", "ARG2"...
	 */
	public void delFvpair(String s) {
		s = s.toUpperCase();
		for (FvPair p:fvpair) {
			if (p.getFeature().equals(s)) {
				fvpair.remove(p);
				break;
			}
		}
	}

	/**
	 * add a simple FvPair (such as "RSTR: h9") to this EP
	 *
	 * @param rargname "RSTR"
	 * @param vid "9"
	 * @param sort "h"
	 */
	public void addSimpleFvpair(String rargname, String vid, String sort) {
		FvPair p = new FvPair(rargname, vid, sort);
		this.fvpair.add(p);
	}

	/**
	 * add a simple FvPair (such as "RSTR: h9") to this EP
	 *
	 * @param feature "RSTR"
	 * @param value "h9"
	 */
	public void addSimpleFvpair(String feature, String value) {
		FvPair p = new FvPair(feature, value);
		this.fvpair.add(p);
	}

	/**
	 * add a complex FvPair to this EP.
	 * For instance, "ARG0: e13 [ e SF: PROP TENSE: UNTENSED MOOD: INDICATIVE ]"
	 *
	 * @param feature "ARG0"
	 * @param value "e13"
	 * @param extraPairs {"SF", "PROP", "TENSE", "UNTENSED", "MOOD", "INDICATIVE"}
	 */
	public void addFvpair(String feature, String value, String[] extraPairs) {
		FvPair p = new FvPair(feature, value, extraPairs);
		this.fvpair.add(p);
	}

	/**
	 * add a complex FvPair to this EP.
	 * @param feature a feature string, such as "ARG0".
	 * @param value a Var value.
	 */
	public void addFvpair(String feature, Var value) {
		FvPair p = new FvPair(feature, value);
		this.fvpair.add(p);
	}

	/**
	 * Set the value of a feature. For instance, set the value of
	 * <code>feature</code> "ARG0" to "x3" (a Var).
	 * @param feature feature's name
	 * @param value a Var
	 */
	public void setFvpairByFeatAndValue (String feature, Var value) {
		for (FvPair p:fvpair) {
			if (p.getFeature().equals(feature)) {
				p.setVar(value);
				break;
			}
		}
	}


	/**
	 * Set the value of a feature. For instance, set the value of
	 * <code>feature</code> "ARG0" to "x3" (a Var).
	 * @param feature feature's name
	 * @param value a string value
	 */
	public void setSimpleFvpairByFeatAndValue (String feature, String value) {
		for (FvPair p:fvpair) {
			if (p.getFeature().equals(feature)) {
				p.setValue(value);
				break;
			}
		}
	}

	/**
	 * Keep some extrapair in fvpair and remove all others.
	 *
	 * @param feature can be "ARG0", "RSTR", "BODY", "ARG1", "ARG2"...
	 * @param extra extrapair to be kept, such as {"NUM", "PERS"}
	 */
	public void keepExtrapairInFvpair(String feature, String[] extra) {
		feature = feature.toUpperCase();

		for (FvPair p:fvpair) {
			if (p.getFeature().equals(feature)) {
				if (p.getVar() != null)
					p.getVar().keepExtrapair(extra);
				break;
			}
		}
	}

	/**
	 * Only keep the fvpair with the feature list in <code>feats</code>
	 * @param feats the feature list, such as {"ARG0"}
	 */
	public void keepFvpair(String[] feats) {
		ArrayList<String> list = StringUtils.arrayToArrayList(feats);
		ArrayList<FvPair> fvlist = new ArrayList<FvPair>();

		for (FvPair p:fvpair) {
			if (!list.contains(p.getFeature())) {
				fvlist.add(p);
			}
		}

		if (fvlist.size() != 0 && !fvpair.removeAll(fvlist)) {
			log.error("Removing fvlist from fvpair failed!");
			log.error("fvlist: " + fvlist);
			log.error("fvpair: " + fvpair);
		}
	}



	/**
	 * return the Var list in fvpair.
	 */
	public ArrayList<Var> getVarList() {
		ArrayList<Var> varL = new ArrayList<Var>();
		Var v;
		for (FvPair p: fvpair) {
			v = p.getVar();
			if (v != null) varL.add(v);
		}
		return varL;
	}

	/**
	 * Add a value <code>shift</code> to the range.
	 * @param shift
	 */
	public void shiftRange (int shift) {
		cfrom += shift;
		cto += shift;
	}

	/**
	 * Return a string containing a pretty-formatted EP with dependencies.
	 */
	@Override public String toString() {
//		<!ELEMENT ep ((pred|realpred), label, fvpair*)>
//		<!ATTLIST ep
//		          cfrom CDATA #IMPLIED
//		          cto   CDATA #IMPLIED
//		          surface   CDATA #IMPLIED
//		      base      CDATA #IMPLIED >
		StringBuilder res = new StringBuilder();
		/*
		    [ proper_q_rel<0:7>
            LBL: h3
            ARG0: x6 [ x PERS: 3 NUM: SG IND: + ]
            RSTR: h5
            BODY: h4 ]
		 */
		if (pred!= null) res.append("[ "+pred);
		if (spred!= null) res.append("[ "+spred);
		if (cfrom!=-1 && cto!=-1) {
			res.append("<"+Integer.toString(cfrom)+":"+Integer.toString(cto)+">");
		}
		if (surface != null) log.debug("complete the code in toString()!");
		if (base != null) log.debug("complete the code in toString()!");
		res.append("\n");
		res.append("  LBL: "+label+"\n");
		for (FvPair p:fvpair) {
			res.append("  "+p+"\n");
		}
		res.append("]");
		res.append("\n\tDMRS: "+this.getDmrsSet());
		res.append("\n");
		return res.toString();
	}


//	@Override public boolean equals (Object obj) {
//		ElementaryPredication ep = (ElementaryPredication)obj;
//		boolean ret = true;
//
//		return ret;
//	}

	public void processStartElement (String qName, Attributes atts) {
//		;;; <!ELEMENT ep ((pred|realpred), label, fvpair*)>
//		;;; <!ATTLIST ep
//		;;;          cfrom CDATA #IMPLIED
//		;;;          cto   CDATA #IMPLIED
//		;;;          surface   CDATA #IMPLIED
//		;;;      base      CDATA #IMPLIED >

		if (qName.equals("ep")) {
			cfrom = Integer.parseInt(atts.getValue("cfrom"));
			cto = Integer.parseInt(atts.getValue("cto"));
			if (atts.getValue("surface") != null) {
				System.err.println("surface atts in <ep> element. " +
						"complete your code!");
			}
			if (atts.getValue("base") != null) {
				System.err.println("base atts in <ep> element. " +
						"complete your code!");
			}
		} else if (qName.equals("label")) {
			label_vid = atts.getValue("vid");
			label = "h"+label_vid;
		} else if (qName.equals("fvpair")) {
			currentFvPair = new FvPair();
			fvpair.add(currentFvPair);
		} else if (qName.equals("var")) {
			currentFvPair.setVar(new Var(atts));
		} else if (qName.equals("extrapair")) {
			currentFvPair.getVar().newExtraPair();
		}
	}

	public void processEndElement (String qName, String str) {
		if (qName.equals("pred")) {
			pred = str;
		} else if (qName.equals("spred")) {
			spred = str;
		} else if (qName.equals("realpred")) {
			// no such situation in sample files, need to complete
			// this part once met
			System.err.println("<realpred>: Manually check the code and complete it!");
		} else if (qName.equals("rargname")) {
			currentFvPair.setRargname(str);
		} else if (qName.equals("constant")) {
			currentFvPair.setConstant(str);
		} else if (qName.equals("path")) {
			currentFvPair.getVar().updatePath(str);
		} else if (qName.equals("value")) {
			currentFvPair.getVar().updateValue(str);
		}
	}

	/**
	 * Output EP in XML
	 * @param hd
	 */
	public void serializeXML (ContentHandler hd) {
//		<!ELEMENT ep ((pred|realpred), label, fvpair*)>
//		<!ATTLIST ep
//		          cfrom CDATA #IMPLIED
//		          cto   CDATA #IMPLIED
//		          surface   CDATA #IMPLIED
//		      base      CDATA #IMPLIED >
		AttributesImpl atts = new AttributesImpl();
		try {
			// <ep cfrom='0' cto='3'>
			atts.addAttribute("", "", "cfrom", "CDATA", Integer.toString(cfrom));
			atts.addAttribute("", "", "cto", "CDATA", Integer.toString(cto));
			if (base!=null)
				atts.addAttribute("", "", "surface", "CDATA", surface);
			if (base!=null)
				atts.addAttribute("", "", "base", "CDATA", base);
			hd.startElement("", "", "ep", atts);

			if (pred!=null) {
				//<pred>PROPER_Q_REL</pred>
				atts.clear();
				hd.startElement("", "", "pred", atts);
				hd.characters(pred.toCharArray(), 0, pred.length());
				hd.endElement("", "", "pred");
			} else if (spred!=null) {
				//<spred>_like_v_1_rel</pred>
				atts.clear();
				hd.startElement("", "", "spred", atts);
				hd.characters(spred.toCharArray(), 0, spred.length());
				hd.endElement("", "", "spred");
			}

			//<label vid='3'/>
			atts.clear();
			atts.addAttribute("", "", "vid", "CDATA", label_vid);
			hd.startElement("", "", "label", atts);
			hd.endElement("", "", "label");

			//<fvpair>
			for (FvPair p : fvpair) {
				p.serializeXML(hd);
			}
			hd.endElement("", "", "ep");
		} catch (SAXException e) {
			log.error("Error:", e);
		}

	}
}
