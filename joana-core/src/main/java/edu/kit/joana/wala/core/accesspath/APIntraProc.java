/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.wala.core.accesspath;


import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.dataflow.graph.BitVectorSolver;
import com.ibm.wala.fixpoint.BitVectorVariable;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.MutableMapping;
import com.ibm.wala.util.intset.MutableSparseIntSet;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.intset.OrdinalSetMapping;

import edu.kit.joana.wala.core.PDG;
import edu.kit.joana.wala.core.PDGEdge;
import edu.kit.joana.wala.core.PDGField;
import edu.kit.joana.wala.core.PDGNode;
import edu.kit.joana.wala.core.SDGBuilder;
import edu.kit.joana.wala.core.SDGBuilder.SDGBuilderConfig;
import edu.kit.joana.wala.core.accesspath.AP.FieldNode;
import edu.kit.joana.wala.core.accesspath.AP.RootNode;
import edu.kit.joana.wala.core.accesspath.APContextManagerView.CallContext;
import edu.kit.joana.wala.core.accesspath.AccessPath.AliasEdge;
import edu.kit.joana.wala.core.accesspath.nodes.APCallNode;
import edu.kit.joana.wala.core.accesspath.nodes.APEntryNode;
import edu.kit.joana.wala.core.accesspath.nodes.APGraph;
import edu.kit.joana.wala.core.accesspath.nodes.APGraph.APEdge;
import edu.kit.joana.wala.core.accesspath.nodes.APNode;
import edu.kit.joana.wala.core.accesspath.nodes.APParamNode;
import edu.kit.joana.wala.core.dataflow.GenReach;
import edu.kit.joana.wala.util.PrettyWalaNames;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

public class APIntraProc {

	private final PDG pdg;
	private final IClassHierarchy cha;
	private final APGraph graph;
	// contains only edges for heap dependencies that are due to aliasing
	private final List<APGraph.APEdge> alias;
	private final MergeInfo mnfo;
	private final SDGBuilder.SDGBuilderConfig cfg;

	public static APIntraProc compute(final PDG pdg, final SDGBuilder.SDGBuilderConfig cfg) {
		final APGraph graph = APGraph.create(pdg);

		final List<APGraph.APEdge> alias = APGraph.findAliasEdges(graph);

		final APIntraProc ap = new APIntraProc(pdg, graph, alias, cfg);

		ap.computeIntra();

		return ap;
	}

	private APIntraProc(final PDG pdg, final APGraph graph, final List<APGraph.APEdge> alias,
			final SDGBuilder.SDGBuilderConfig cfg) {
		this.pdg = pdg;
		this.graph = graph;
		this.alias = alias;
		this.mnfo = new MergeInfo(pdg);
		this.cfg = cfg;
		this.cha = pdg.cgNode.getClassHierarchy();
	}

	public SDGBuilderConfig getConfig() {
		return cfg;
	}
	
	/**
	 * is only run once: directly after creation of this object.
	 */
	private void computeIntra() {
		adjustNonAliasEdges();

		boolean changed = true;
		while (changed) {
			changed = propagateIntra();
			if (changed) {
				adjustNonAliasEdges();
			}
		}
		
		extractIntraprocMerge();
	}
	
	public final CallContext extractContext(final APIntraProc aipCallee, final PDGNode call) {
		final CallContext ctx = new CallContext(pdg.getId(), aipCallee.pdg.getId(), call.getId());

		final APCallNode apCall = graph.getCall(call);
		final APEntryNode apEntry = aipCallee.graph.getEntry();

		final Map<APParamNode, APParamNode> callee2call = form2actual(apCall, apEntry);
		
		for (final APParamNode fnode : callee2call.keySet()) {
			final APParamNode anode = callee2call.get(fnode);
			final int aid = anode.node.getId();
			final int fid = fnode.node.getId();
			if (anode.isInput()) {
				ctx.actualIns.add(aid);
			}
			
			ctx.act2formal.put(aid, fid);
		}
				
		return ctx;
	}

	public boolean propagateFrom(final APIntraProc aipCallee, final PDGNode call) {
		boolean changed = false;

		final APCallNode apCall = graph.getCall(call);
		final APEntryNode apEntry = aipCallee.graph.getEntry();

		/**
		 * maps formal-in nodes of callee to actual-in nodes of call-site
		 */
		final Map<APParamNode, APParamNode> callee2call = form2actual(apCall, apEntry);

		/**
		 * creates a map from the root node of the initial value of the formal-in nodes of the callee to the
		 * actual-in nodes of the call
		 */
		final Map<RootNode, APParamNode> root2ap = aipCallee.graph.createRoot2ActInMap(callee2call);

		{
			final APParamNode fRet = apEntry.getReturn();
			if (fRet != null) {
				final APParamNode aRet = apCall.getReturn();
				changed |= fRet.propagateTo(root2ap, aRet);
				// propagate children
				changed |= propagateToChildren(callee2call, root2ap, fRet, aRet);
			}
		}

		{
			final APParamNode fRetExc = apEntry.getReturnException();
			if (fRetExc != null) {
				final APParamNode aRetExc = apCall.getReturnException();
				changed |= fRetExc.propagateTo(root2ap, aRetExc);
				// propagate children
				changed |= propagateToChildren(callee2call, root2ap, fRetExc, aRetExc);
			}
		}

		for (int i = 0; i < apEntry.getParameterNum(); i++) {
			final APParamNode fIn = apEntry.getParameterIn(i);
			final APParamNode aIn = apCall.getParameterIn(i);
			// propagate children
			changed |= propagateToChildren(callee2call, root2ap, fIn, aIn);
		}

		for (final Entry<IField, APParamNode> sIn : apEntry.getStaticIns()) {
			final APParamNode fIn = sIn.getValue();
			final APParamNode aIn = apCall.getParameterStaticIn(sIn.getKey());
			// propagate children
			changed |= propagateToChildren(callee2call, root2ap, fIn, aIn);
		}

		for (final Entry<IField, APParamNode> sOut : apEntry.getStaticOuts()) {
			final APParamNode fOut = sOut.getValue();
			final APParamNode aOut = apCall.getParameterStaticOut(sOut.getKey());
			changed |= fOut.propagateTo(root2ap, aOut);
			// propagate children
			changed |= propagateToChildren(callee2call, root2ap, fOut, aOut);
		}

		//System.out.println("propagation from " + apCall + " to " + apEntry);

		final CallMergeOps cmop = mnfo.getCallMop(call);
		for (final MergeOp mop : aipCallee.getMergeInfo().getAllMergeOps()) {
			final MergeOp moptr = mop.replaceRoots(root2ap);
			if (!cmop.ops.contains(moptr)) {
				cmop.ops.add(moptr);
				changed = true;
			}
 		}
		
		return changed;
	}

	private static boolean propagateToChildren(final Map<APParamNode, APParamNode> callee2call,
			final Map<RootNode, APParamNode> root2ap, final APParamNode form, final APParamNode act) {
		// propagate to out nodes - match trees and search out nodes
		// propagate accesspaths from formal-out to actual-out

		if (!form.hasChildren()) {
			return false;
		}

		boolean changed = false;

		for (final APParamNode fChild : form.getChildren()) {
			final APParamNode aChild = callee2call.get(fChild);

			if (fChild.isOutput()) {
				final APParamNode fChildIn = form.findChild(fChild.node.getBytecodeIndex(), fChild.node.getBytecodeName(), true);
				if (fChildIn != null) {
					// propagate paths from fIn to fOut
					changed |= propagateFromTo(fChildIn, fChild);
				}

				// propagate actual child paths to formal child paths by replacing the corresponding roots
				if (aChild != null) {
					changed |= fChild.propagateTo(root2ap, aChild);
				}
			}

			if (aChild != null) {
				// (outdated) do not propagate to already visited children (ignore backlinks)
				// is not necessary, because we traverse the APParamNode Structure which does not contain any backlinks.
				changed |= propagateToChildren(callee2call, root2ap, fChild, aChild);
			}
		}

		return changed;
	}

	private Map<APParamNode, APParamNode> form2actual(final APCallNode apCall, final APEntryNode apEntry) {
		final Map<APParamNode, APParamNode> callee2call = new HashMap<APParamNode, APParamNode>();
		for (int i = 0; i < apEntry.getParameterNum(); i++) {
			final APParamNode fIn = apEntry.getParameterIn(i);
			final APParamNode aIn = apCall.getParameterIn(i);
			assert !callee2call.containsKey(fIn);
			callee2call.put(fIn, aIn);
			addChildrenToMap(fIn, aIn, callee2call);
		}

		if (apEntry.getReturn() != null) {
			final APParamNode fRet = apEntry.getReturn();
			final APParamNode aRet = apCall.getReturn();
			assert !callee2call.containsKey(fRet);
			callee2call.put(fRet, aRet);
			addChildrenToMap(fRet, aRet, callee2call);
		}

		if (apEntry.getReturnException() != null) {
			final APParamNode fRetExc = apEntry.getReturnException();
			final APParamNode aRetExc = apCall.getReturnException();
			assert !callee2call.containsKey(fRetExc);
			callee2call.put(fRetExc, aRetExc);
			addChildrenToMap(fRetExc, aRetExc, callee2call);
		}

		for (final Entry<IField, APParamNode> sInEntry : apEntry.getStaticIns()) {
			final APParamNode aIn = apCall.getParameterStaticIn(sInEntry.getKey());
			final APParamNode fIn = sInEntry.getValue();
			assert !callee2call.containsKey(fIn);
			callee2call.put(fIn, aIn);
			addChildrenToMap(fIn, aIn, callee2call);
		}

		for (final Entry<IField, APParamNode> sOutEntry : apEntry.getStaticOuts()) {
			final APParamNode aOut = apCall.getParameterStaticOut(sOutEntry.getKey());
			final APParamNode fOut = sOutEntry.getValue();
			assert !callee2call.containsKey(fOut);
			callee2call.put(fOut, aOut);
			addChildrenToMap(fOut, aOut, callee2call);
		}

		return callee2call;
	}

	private static void addChildrenToMap(final APParamNode form, final APParamNode act,
			final Map<APParamNode, APParamNode> callee2call) {
		if (!form.hasChildren() || !act.hasChildren()) {
			return;
		}

		for (final APParamNode fChild : form.getChildren()) {
			if (callee2call.containsKey(fChild)) {
				continue;
			}

			final PDGNode fn = fChild.node;
			final APParamNode aChild = act.findChild(fn.getBytecodeIndex(), fn.getBytecodeName(), APParamNode.isInput(fn));

			if (aChild != null) {
				assert fChild.isInput() == aChild.isInput();
				assert !callee2call.containsKey(fChild);
				callee2call.put(fChild, aChild);
				addChildrenToMap(fChild, aChild, callee2call);
			}
		}
	}

	public void dumpGraph(final String suffix) {
		if (!cfg.debugAccessPath) {
			return;
		}
		
		try {
			APUtil.writeAPGraphToFile(graph, cfg.debugAccessPathOutputDir, pdg, suffix);
		} catch (WalaException e) {
			e.printStackTrace();
		} catch (CancelException e) {
			e.printStackTrace();
		}
	}

	public String toString() {
		return "APIntra of " + pdg.toString() + " - (" + graph.getNumberOfNodes() + ")";
	}

	/**
	 * Adds heap data edges to to graph that are not due to aliasing.
	 */
	public void adjustNonAliasEdges() {
//		/* DEBUG */ final int a = alias.size();

		for (final Iterator<APEdge> it = alias.iterator(); it.hasNext();) {
			final APEdge e = it.next();

			if (shareAccessPath(e.from, e.to)) {
				graph.addEdge(e.from, e.to);
				it.remove();
			}
		}

//		/* DEBUG */ final int b = alias.size();
//		/* DEBUG */ if (changed) {
//		/* DEBUG */		System.err.println(pdg.toString() + ": removed " + (a - b) + " alias edges of " + a + " total.");
//		/* DEBUG */ }
	}

	public boolean shareAccessPath(final APNode n1, final APNode n2) {
		if (n1 == n2) {
			return true;
		}

		return n1.sharesPathWith(n2);
	}

	/**
	 * Propagates accesspath along intraprocedural edges
	 * @return true if accesspaths changed
	 */
	public boolean propagateIntra() {
		boolean modified = false;

		boolean changed = true;
		while (changed) {
			changed = false;

			for (final APNode n : graph) {
				final Iterator<APNode> succs = graph.getSuccNodes(n);
				while (succs.hasNext()) {
					final APNode succ = succs.next();
					changed |= propagateFromTo(n, succ);
				}
			}

			modified |= changed;
		}

		return modified;
	}

	private static boolean propagateFromTo(final APNode from, final APNode to) {
		boolean changed = false;

		final Iterator<AP> paths = from.getOutgoingPaths();
		while (paths.hasNext()) {
			final AP p = paths.next();

			changed |= to.addPath(p);
		}

		return changed;
	}
	
	public void computeReachingMerges(final IProgressMonitor progress) throws CancelException {
		mnfo.computeReach(cfg, progress);
	}
	
	public void buildAP2NodeMap() {
		for (final PDGNode n : pdg.vertexSet()) {
			if (n.getPdgId() != pdg.getId()) {
				continue;
			}
			
			final APNode apn = findAPNode(n);
			if (apn == null) {
				continue;
			}

			final Set<AP> aps = new HashSet<>();
			final Iterator<AP> it = apn.getOutgoingPaths();
			while (it.hasNext()) {
				aps.add(it.next());
			}
			
			mnfo.addAPs(n, aps);
		}
	}

	/**
	 * In order to minimize the amount of different alias contexts we have to consider, it is helpful
	 * to precompute which parameters can actually be aliased with each other according to type information.
	 * Two parameters p1, p2, are considered aliased, if p1 of any of its (transitive reachable) fields
	 * point to the same location as p2 or any of its reachable fields.
	 * So basically we have to collect all types of all fields reachable from a formal-in root node and compare
	 * it to the reachable types from another root node in order to decide if it is even possible that those two
	 * params may be aliased.
	 */
	public void addPotentialAliasInfoToFormalIns() {
		/*
		 * 1. get all formal-in roots
		 * 2. for each formal-in root do
		 * 2.1 get declared type
		 * 2.2 get reachable fields (ref and mod)
		 * 2.3 get types for all fields
		 * 2.4 store set of types
		 */
		final Map<PDGNode, Set<TypeReference>> root2types = new HashMap<PDGNode, Set<TypeReference>>();

		final APEntryNode entry = graph.getEntry();
		for (int i = 0; i < entry.getParameterNum(); i++) {
			final APParamNode rootIn = entry.getParameterIn(i);
			final Set<TypeReference> types = findReachableTypes(rootIn);
			root2types.put(rootIn.node, types);
		}

		for (final Entry<IField, APParamNode> sIn : entry.getStaticIns()) {
			final APParamNode sRootIn = sIn.getValue();
			final Set<TypeReference> types = findReachableTypes(sRootIn);
			root2types.put(sRootIn.node, types);
		}

		for (final Entry<IField, APParamNode> sOut : entry.getStaticOuts()) {
			final APParamNode sRootOut = sOut.getValue();
			final Set<TypeReference> types = findReachableTypes(sRootOut);
			root2types.put(sRootOut.node, types);
		}

		for (final Entry<PDGNode, Set<TypeReference>> e1 : root2types.entrySet()) {
			final PDGNode n1 = e1.getKey();
			final Set<TypeReference> t1 = e1.getValue();

			for (final Entry<PDGNode, Set<TypeReference>> e2 : root2types.entrySet()) {
				final PDGNode n2 = e2.getKey();
				if (n1 == n2) {
					if (!n1.getTypeRef().isPrimitiveType()) {
						n1.addAliasDataSource(n1.getId());
					}
					continue;
				}

				final Set<TypeReference> t2 = e2.getValue();
				// check if alias possible
				if (typesMayAlias(t1, t2)) {
					n1.addAliasDataSource(n2.getId());
				}
			}
		}
	}

	private boolean typesMayAlias(final Set<TypeReference> types1, final Set<TypeReference> types2) {
		for (final TypeReference t1 : types1) {
			for (final TypeReference t2 : types2) {
				if (typesMayAlias(t1, t2)) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean typesMayAlias(final TypeReference paramType, final TypeReference otherType) {
		if (paramType == otherType) {
			return true;
		} else if (paramType.isClassType() && otherType.isClassType()) {
			final IClassHierarchy cha = pdg.cgNode.getClassHierarchy();
			final IClass paramClass = cha.lookupClass(paramType);
			final IClass otherClass = cha.lookupClass(otherType);

			if (paramClass == null || otherClass == null) {
				return false;
			} else {
				return cha.isSubclassOf(otherClass, paramClass);
			}
		} else if (paramType.isPrimitiveType() && otherType.isPrimitiveType()) {
			return paramType.equals(otherType);
		} else if (paramType.isArrayType() && otherType.isArrayType()) {
			// switch param and other as the subclass relation is invers for arrays.
			return typesMayAlias(otherType.getArrayElementType(), paramType.getArrayElementType());
		}

		return false;
	}

	private Set<TypeReference> findReachableTypes(final APParamNode root) {
		final IClassHierarchy cha = pdg.cgNode.getClassHierarchy();

		final Set<TypeReference> types = new HashSet<TypeReference>();
		final TypeReference firstRef = root.node.getTypeRef();
		if (firstRef.isPrimitiveType()) {
			return types;
		}
		types.add(firstRef);

		final LinkedList<TypeReference> work = new LinkedList<TypeReference>();
		work.add(firstRef);

		while (!work.isEmpty()) {
			final TypeReference tref = work.removeFirst();

			if (!pdg.isImmutable(tref)) {
				if (tref.isClassType()) {
					final IClass cls = cha.lookupClass(tref);
					if (cls == null) {
						continue;
					}

					for (final IField f : cls.getAllFields()) {
						if (f.isStatic()) {
							continue;
						}

						final TypeReference fType = f.getFieldTypeReference();
						if (!fType.isPrimitiveType() && !types.contains(fType)) {
							work.add(fType);
							types.add(fType);
						}
					}
				} else if (tref.isArrayType()) {
					final TypeReference aType = tref.getArrayElementType();
					if (!aType.isPrimitiveType() && !types.contains(aType)) {
						work.add(tref.getArrayElementType());
						types.add(aType);
					}
				}
			}
		}

		return types;
	}

//	private Set<TypeReference> findReachableTypes(final APParamNode root) {
//		final Set<TypeReference> types = new HashSet<TypeReference>();
//
//		final LinkedList<APParamNode> work = new LinkedList<APParamNode>();
//		work.add(root);
//
//		while (!work.isEmpty()) {
//			final APParamNode p = work.removeFirst();
//
//			final TypeReference tref = p.node.getTypeRef();
//			if (!tref.isPrimitiveType()) {
//				types.add(tref);
//			}
//
//			if (!pdg.isImmutable(tref) && p.hasChildren()) {
//				for (final APParamNode child : p.getChildren()) {
//					work.add(child);
//				}
//			}
//		}
//
//		return types;
//	}

	/**
	 * actual-in alias conditions are a set of nodes that may be
	 * data sources for the input of the current actual in.
	 * These nodes may be
	 * 	1. any (root) formal-in node
	 * 	2. any (new) node
	 *  3. the current actual-in root-node itself in case some of its fields are aliasing
	 *
	 *  This information can then be used to compute the alias-configuration, when
	 *  the configuration of the formal-in nodes is known.
	 *
	 *  So act-in a1,a2 are aliasing if their alias conditions ac1, ac2 meet the following conditions
	 *	Iff a1 != a2:
	 *  1. ac1 and ac2 share a common element.
	 *  2. If the formal-ins f1 and f2 are aliased and f1 is contained in a1 and f2 is contained in a2
	 *  Iff a1 == a2:
	 *  1. ac1 contains a1.
	 *  2. ac1 contains formal-ins f1, f2 (f1 != f2) where f1 is aliased with f2.
	 *  3. ac1 contains a formal-in f1 that has aliasing fields.
	 */
	public void addAliasConditionToActualIns() {
		/*
		 * 1. find actuals-in roots
		 * 2. for each root r do
		 * 2.1 get all children of r
		 * 2.2 get all matching APNodes
		 * 2.3 check accesspaths for reaching data roots
		 * 2.4 check for potential self-alias (same ap element in different children)
		 * 2.5 generate alias condition
		 */
		for (final APCallNode call : graph.getCalls()) {
			for (int i = 0; i < call.getParameterNum(); i++) {
				final APParamNode apRoot = call.getParameterIn(i);
				addAliasConditionTo(apRoot);
			}

			for (final Entry<IField, APParamNode> sIn : call.getStaticIns()) {
				addAliasConditionTo(sIn.getValue());
			}
		}
	}

	/*
	 * 2.1 get all children of r
	 * 2.2 get all matching APNodes
	 * 2.3 check accesspaths for reaching data roots
	 * 2.4 check for potential self-alias (same ap element in different children)
	 * 2.5 generate alias condition
	 */
	private void addAliasConditionTo(final APParamNode p) {
		final Set<AP> aliasPaths = new HashSet<AP>();

		boolean isAliased = false;

		final LinkedList<APParamNode> work = new LinkedList<APParamNode>();
		work.add(p);

		while (!work.isEmpty()) {
			final APParamNode cur = work.removeFirst();

			for (final Iterator<AP> apIt = cur.getOutgoingPaths(); apIt.hasNext();) {
				final AP ap = apIt.next();
				if (aliasPaths.contains(ap)) {
					// 2.4
					isAliased = true;
				} else {
					aliasPaths.add(ap);
				}
			}

			if (cur.hasChildren()) {
				for (final APParamNode ch : cur.getChildren()) {
					// 2.1 2.2
					if (ch.isInput()) {
						work.add(ch);
					}
				}
			}
		}

		final Set<RootNode> roots = new HashSet<AP.RootNode>();
		for (final AP ap : aliasPaths) {
			// 2.3
			roots.add(ap.getRoot());
		}

		// 2.5
		if (isAliased) {
			p.node.addAliasDataSource(p.node.getId());
		}

		for (final RootNode r : roots) {
			final int nSourceId = graph.getPDGNodeForRoot(r);
			p.node.addAliasDataSource(nSourceId);
		}
	}

	public int findAndMarkAliasEdges() {
		int found = 0;
		
		for (final APEdge ae : alias) {
			final PDGNode from = ae.from.node;
			final PDGNode to = ae.to.node;
			PDGEdge pdgEdge = null;
			for (final PDGEdge out : pdg.outgoingEdgesOf(from)) {
				if (out.to == to && out.kind == PDGEdge.Kind.DATA_HEAP) {
					pdgEdge = out;
					break;
				}
			}

			if (pdgEdge != null) {
				final AliasEdge edge = new AliasEdge(pdgEdge);
				findAndAddReason(ae, edge);
				found++;
				pdg.removeEdge(pdgEdge);
				final PDGEdge pdgAlias = pdg.addEdge(from, to, PDGEdge.Kind.DATA_ALIAS);
				pdgAlias.setLabel(convertToReason(edge));
			}
		}
		
		return found;
	}

	private static String convertToReason(final AliasEdge ae) {
		final StringBuffer buf = new StringBuffer("[");

		final TIntIterator itFrom = ae.fromAlias.iterator();
		while (itFrom.hasNext()) {
			final int cur = itFrom.next();
			buf.append(cur);
			if (itFrom.hasNext()) {
				buf.append(",");
			}
		}

		buf.append("][");

		final TIntIterator itTo = ae.toAlias.iterator();
		while (itTo.hasNext()) {
			final int cur = itTo.next();
			buf.append(cur);
			if (itTo.hasNext()) {
				buf.append(",");
			}
		}

		buf.append("]");

		return buf.toString();
	}

	private void findAndAddReason(final APEdge ae, final AliasEdge edge) {
		// add alias reasons found
		final Iterator<AP> fromIt = ae.from.getOutgoingPaths();
		while (fromIt.hasNext()) {
			final AP from = fromIt.next();
			final RootNode fromRoot = from.getRoot();
			final int fromRootPDGId = graph.getPDGNodeForRoot(fromRoot);
			edge.fromAlias.add(fromRootPDGId);
		}

		assert !edge.fromAlias.isEmpty();

		final Iterator<AP> toIt = ae.to.getOutgoingPaths();
		while (toIt.hasNext()) {
			final AP to = toIt.next();
			final RootNode toRoot = to.getRoot();
			final int toRootPDGId = graph.getPDGNodeForRoot(toRoot);
			edge.toAlias.add(toRootPDGId);
		}

		assert !edge.toAlias.isEmpty();
	}

	public APNode findAPNode(final PDGNode n) {
		return graph.findNode(n);
	}

	private Set<AP> findAllAPs() {
		final Set<AP> aps = new HashSet<>();

		for (final APNode n : graph){
			final Iterator<AP> it = n.getOutgoingPaths();
			while (it.hasNext()) {
				aps.add(it.next());
			}
		}
		
		return aps;
	}
	
	private static Set<APParamNode> collectReachable(final APParamNode n) {
		final Set<APParamNode> result = new HashSet<>();
		
		final LinkedList<APParamNode> work = new LinkedList<>();
		work.add(n);
		
		while (!work.isEmpty()) {
			final APParamNode cur = work.removeLast();

			if (!cur.getType().isPrimitiveType()) {
				result.add(cur);

				if (cur.hasChildren()) {
					for (final APParamNode ch : cur.getChildren()) {
						work.addLast(ch);
					}
				}
			}
		}
		
		return result;
	}
	
	private boolean compatibleTypes(final TypeReference t1, final TypeReference t2) {
		if (t1.equals(t2)) {
			return true;
		}
		
		final IClass c1 = cha.lookupClass(t1);
		final IClass c2 = cha.lookupClass(t2);

		return cha.isSubclassOf(c1, c2) || cha.isSubclassOf(c2, c1);
	}
	
	private void populateMaxMerge(final APParamNode[] roots, final Set<MergeOp> max) {
		for (int i = 0; i < roots.length; i++) {
			final APParamNode r1 = roots[i];
			final Set<APParamNode> reach1 = collectReachable(r1);
			
			for (int j = i+1; j < roots.length; j++) {
				final APParamNode r2 = roots[j];
				final Set<APParamNode> reach2 = collectReachable(r2);
				
				for (final APParamNode n1 : reach1) {
					Set<AP> from = null;

					for (final APParamNode n2 : reach2) {
						if (from != null && from.isEmpty()) break;
						
						if (compatibleTypes(n1.getType(), n2.getType())) {
							if (from == null) {
								from = new HashSet<AP>();
								
								final Iterator<AP> it = n1.getOutgoingPaths();
								while (it.hasNext()) {
									final AP ap = it.next();
									from.add(ap);
								}
							}
							
							final Set<AP> to = new HashSet<>();
							if (!from.isEmpty()) {
								final Iterator<AP> it = n2.getOutgoingPaths();
								while (it.hasNext()) {
									final AP ap = it.next();
									to.add(ap);
								}
							}
							
							if (!from.isEmpty() && !to.isEmpty()) {
								final MergeOp mop = new MergeOp(from, to);
								max.add(mop);
							}
						}
					}
				}
				
			}
		}
	}

	public Set<MergeOp> computeMaxMerge() {
		final Set<MergeOp> max = new HashSet<>();
		final APEntryNode entry = graph.getEntry();
		
		final Set<APParamNode> roots = new HashSet<>();
		
		for (int i = 0; i < entry.getParameterNum(); i++) {
			final APParamNode p = entry.getParameterIn(i);
			roots.add(p);
		}
		
		for (final Entry<IField, APParamNode> e : entry.getStaticIns()) {
			roots.add(e.getValue());
		}
		
		final APParamNode[] rootsarray = roots.toArray(new APParamNode[roots.size()]);
		populateMaxMerge(rootsarray, max);
		
		return max;
	}
	
	public static class MergeInfo {
		public final PDG pdg;
		
		private int numAliasEdges = 0;
		private boolean intraprocDone = false;
		private final Set<Merges> merges = new HashSet<>();
		private final Map<PDGNode, Merges> n2m = new HashMap<>(); // initial gen merge
		private final TIntObjectMap<Set<AP>> n2ap = new TIntObjectHashMap<>();
		private final Map<PDGNode, OrdinalSet<Merges>> n2reach = new HashMap<>();
		private Set<AP> allAPs;
		private final Set<CallContext> calls = new HashSet<>();
		
		public MergeInfo(final PDG pdg) {
			this.pdg = pdg;
		}
		
		public void addCallCtx(final CallContext ctx) {
			calls.add(ctx);
		}
		
		public APIntraprocContextManager extractContext(final Set<MergeOp> maxMerges) {
			if (!intraprocDone || allAPs == null) {
				throw new IllegalStateException("run intraproc first and add all access paths.");
			}
			
			final Set<MergeOp> allMerges = getAllMergeOps();
			final MutableMapping<MergeOp> mapping = createMapping(allMerges);
			final TIntObjectMap<OrdinalSet<MergeOp>> reachOp = flattenReachMap(allMerges, mapping);
			final APIntraprocContextManager ctx = APIntraprocContextManager.create(PrettyWalaNames.methodName(pdg.getMethod()),
					pdg.getId(), allAPs, allMerges, n2ap, maxMerges, reachOp, mapping);
			
			for (final CallContext callctx : calls) {
				ctx.addCallContext(callctx);
			}
			
			return ctx;
		}
		
		private TIntObjectMap<OrdinalSet<MergeOp>> flattenReachMap(final Set<MergeOp> allMerges,
				final OrdinalSetMapping<MergeOp> mapping) {
			final TIntObjectMap<OrdinalSet<MergeOp>> reachOp = new TIntObjectHashMap<>();
			
			for (final PDGNode n : n2reach.keySet()) {
				final OrdinalSet<Merges> reach = n2reach.get(n);
				final MutableIntSet rset = MutableSparseIntSet.makeEmpty();
				for (final Merges m : reach) {
					if (m instanceof MergeOp) {
						final int id = mapping.getMappedIndex(m);
						rset.add(id);
					} else if (m instanceof CallMergeOps) {
						final CallMergeOps cmo = (CallMergeOps) m;
						for (final MergeOp mo : cmo.ops) {
							final int id = mapping.getMappedIndex(mo);
							rset.add(id);
						}
					}
				}
				
				final OrdinalSet<MergeOp> rop = new OrdinalSet<MergeOp>(rset, mapping);
				reachOp.put(n.getId(), rop);
			}
			
			return reachOp;
		}

		private static MutableMapping<MergeOp> createMapping(final Set<MergeOp> allMerges) {
			final MergeOp[] allMergesArr = new MergeOp[allMerges.size()];
			{
				int id = 0;
				for (final MergeOp op : allMerges) {
					op.id = id;
					allMergesArr[id] = op;
					id++;
				}
			}

			final MutableMapping<MergeOp> mutable = new MutableMapping<>(allMergesArr);
			
			return mutable;
		}
		
		void setAllAPs(final Set<AP> allAPs) {
			this.allAPs = allAPs;
		}
		
		public Set<AP> getAllAPs() {
			 return allAPs;
		}
		
		public int getNumAliasEdges() {
			return numAliasEdges;
		}
		
		void setNumAliasEdges(final int numAliasEdges) {
			this.numAliasEdges = numAliasEdges;
		}
		
		@SuppressWarnings("unchecked")
		public void computeReach(final SDGBuilderConfig cfg, final IProgressMonitor progress) throws CancelException {
			final Map<PDGNode, Collection<Merges>> gen = new HashMap<>();
			for (final PDGNode n : pdg.vertexSet()) {
				if (n.getPdgId() != pdg.getId()) { continue; }
				final Merges m = n2m.get(n);
				if (m != null) {
					gen.put(n, Collections.singleton(m));
				} else {
					gen.put(n, (Set<Merges>) Collections.EMPTY_SET);
				}
			}
			final Graph<PDGNode> flowGraph = APUtil.extractCFG(pdg);
			if (cfg.debugAccessPath) {
				try {
					APUtil.writeCFGtoFile(flowGraph, cfg.debugAccessPathOutputDir, pdg, "-cfg.dot");
				} catch (WalaException e) {
					e.printStackTrace();
				}
			}
			final GenReach<PDGNode, Merges> genreach = GenReach.createUnionFramework(flowGraph, gen);
			final BitVectorSolver<PDGNode> solver = new BitVectorSolver<PDGNode>(genreach);
			solver.solve(progress);
			final OrdinalSetMapping<Merges> mapping = genreach.getLatticeValues();
			for (final PDGNode n : gen.keySet()) {
				final BitVectorVariable out = solver.getOut(n);
				final OrdinalSet<Merges> outSet = new OrdinalSet<>(out.getValue(), mapping);
				n2reach.put(n, outSet);
			}
		}
		
		public OrdinalSet<Merges> getReachM(final PDGNode n) {
			return n2reach.get(n);
		}
		
		public void addMergeOp(final PDGNode n, final MergeOp op) {
			merges.add(op);
			n2m.put(n, op);
		}
		
		public void addCallMerge(final CallMergeOps cmop) {
			n2m.put(cmop.call, cmop);
			merges.add(cmop);
		}
		
		public CallMergeOps getCallMop(final PDGNode call) {
			if (call == null || call.getKind() != PDGNode.Kind.CALL) {
				throw new IllegalArgumentException();
			}
			
			final Merges m = n2m.get(call);
			final CallMergeOps cmop = (CallMergeOps) m;
			
			return cmop;
		}
		
		public void addAPs(final PDGNode n, final Set<AP> aps) {
			n2ap.put(n.getId(), aps);
		}
		
		public Set<AP> getAPs(final PDGNode n) {
			return n2ap.get(n.getId());
		}
		
		public Set<Merges> getAllMerges() {
			return Collections.unmodifiableSet(merges);
		}
		
		public Set<MergeOp> getAllMergeOps() {
			final Set<MergeOp> mops = new HashSet<>();
			for (final Merges m : merges) {
				if (m instanceof MergeOp) {
					mops.add((MergeOp) m);
				} else if (m instanceof CallMergeOps) {
					final CallMergeOps cmop = (CallMergeOps) m;
					mops.addAll(cmop.ops);
				} else {
					throw new IllegalStateException("unknown merges type: " + m.getClass().getCanonicalName());
				}
			}
			
			return mops;
		}
		
	}
	
	public interface Merges {}

	public static class CallMergeOps implements Merges {
		public final Set<MergeOp> ops = new HashSet<>();
		public final PDGNode call;
		public boolean isPropagated = false;
		
		public CallMergeOps(final PDGNode call) {
			if (call == null || call.getKind() != PDGNode.Kind.CALL) {
				throw new IllegalArgumentException();
			}
			
			this.call = call;
		}
		
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append("merge-ops(" + call.getId() + ") of " + call.getLabel() + "\n");
			for (final MergeOp mop : ops) {
				sb.append("\t" + mop + "\n");
			}
			sb.append("merge-ops(" + call.getId() + ") end");
			return sb.toString();
		}
		
		public int hashCode() {
			return call.hashCode();
		}
		
		public boolean equals(final Object o) {
			if (this == o) {
				return true;
			} else if (o instanceof CallMergeOps) {
				final CallMergeOps other = (CallMergeOps) o;
				return call.equals(other.call);
			}
			
			return false;
		}
	}
	
	public static class MergeOp implements Merges {
		
		public final Set<AP> from;
		public final Set<AP> to;
		private final int hashCode;
		public static final int ID_UNDEF = -1;
		public int id = ID_UNDEF; // used later on for ordinal set mapping

		public MergeOp(final Set<AP> from, final Set<AP> to) {
			if (from.size() < 1 || to.size() < 1) {
				throw new IllegalArgumentException("from and to sets of merge op need to contain at least a single path");
			}
			
			this.from = Collections.unmodifiableSet(from);
			this.to = Collections.unmodifiableSet(to);
			this.hashCode = from.hashCode() + (4711 * to.hashCode())
				+ (23 * from.size()) + (217 * to.size());
		}
		
		/**
		 * Replace the root nodes of all access paths in this merge op with the access paths from the mapped parameter
		 * node.
		 * @param root2ap Map from root node to param node.
		 * @return A merge op with replaced access path roots.
		 */
		public MergeOp replaceRoots(final Map<RootNode, APParamNode> root2ap) {
			final Set<AP> fromRp = replace(from, root2ap);
			final Set<AP> toRp = replace(to, root2ap);
			final MergeOp mop = new MergeOp(fromRp, toRp);
			
			return mop;
		}
		
		public boolean matches(final AP ap) {
			return from.contains(ap) || to.contains(ap);
		}
		
		private static Set<AP> replace(final Set<AP> aps, final Map<RootNode, APParamNode> root2ap) {
			final Set<AP> repl = new HashSet<>();
			
			for (final AP ap : aps) {
				final RootNode r = ap.getRoot();
				if (root2ap.containsKey(r)) {
					final List<FieldNode> fp = ap.getFieldPath();
					final APParamNode pn = root2ap.get(r);
					final Iterator<AP> it = pn.getOutgoingPaths();
					while (it.hasNext()) {
						final AP apRoot = it.next();
						// add everything from ap to apRoot, except the root node r itself.
						final AP expanded = apRoot.expand(fp);
						repl.add(expanded);
					}
				} else {
					repl.add(ap);
				}
			}
			
			if (repl.isEmpty()) {
				repl.addAll(aps);
			}
			
			return repl;
		}

		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append("merge([");
			for (final AP fAP : from) {
				sb.append(fAP);
				sb.append(",");
			}
			sb.deleteCharAt(sb.length() -1);
			sb.append("],[");
			for (final AP tAP : to) {
				sb.append(tAP);
				sb.append(",");
			}
			sb.deleteCharAt(sb.length() -1);
			sb.append("])");
			
			return sb.toString();
		}
		
		public boolean equals(final Object o) {
			if (o == this) {
				return true;
			} else if (o instanceof MergeOp) {
				final MergeOp other = (MergeOp) o;
				return from.equals(other.from) && to.equals(other.to);
			}
			
			return false;
		}
		
		public int hashCode() {
			return hashCode;
		}
	}
	
	public MergeInfo getMergeInfo() {
		return mnfo;
	}
	
	public APGraph getAliasGraph() {
		return graph;
	}
	
	private void extractIntraprocMerge() {
		if (mnfo.intraprocDone) {
			return;
		}
		
		for (final PDGField f : pdg.getFieldWrites()) {
			// f.accfield; // access path of extend(base, fieldname)
			final Set<AP> to = new HashSet<AP>(); 
			{
				final APNode apTo = findAPNode(f.accfield);
				final Iterator<AP> itTo = apTo.getOutgoingPaths();
				while (itTo.hasNext()) {
					to.add(itTo.next());
				}
			}

			// f.node // access paths merged of field and data deps
			final Set<AP> from = new HashSet<AP>(); 
			for (final PDGEdge e : pdg.incomingEdgesOf(f.node)) {
				if (e.kind == PDGEdge.Kind.DATA_DEP && e.from != f.base && e.from != f.index) {
					// get all aps from e.from
					final APNode apFrom = findAPNode(e.from);
					final Iterator<AP> itFrom = apFrom.getOutgoingPaths();
					while (itFrom.hasNext()) {
						from.add(itFrom.next());
					}
				}
			}
			
			if (from.isEmpty() || to.isEmpty()) {
//				System.err.println("empty set for: " + f);
//				System.err.println("from: " + from);
//				System.err.println("to: " + to);
			} else {
				final MergeOp mop = new MergeOp(from, to);
				mnfo.addMergeOp(f.accfield, mop);
			}
		}
		
		for (final PDGNode call : pdg.getCalls()) {
			final CallMergeOps cmop = new CallMergeOps(call);
			mnfo.addCallMerge(cmop);
		}
		
		final Set<AP> allAPs = findAllAPs();
		mnfo.setAllAPs(allAPs);
		
		mnfo.intraprocDone = true;;
	}
}
