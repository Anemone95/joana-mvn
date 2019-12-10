/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.wala.core;

import edu.kit.joana.util.graph.KnowsVertices;

public final class PDGEdge implements KnowsVertices<PDGNode>, Comparable<PDGEdge> {

	public final PDGNode from;
	public final PDGNode to;
	public final Kind kind;
	private String label = null;

	public static enum Kind {
		DATA_DEP("DD", true, true, false, false), // data dependence stack
		DATA_HEAP("DH", true, true, false, false), // data dependence heap
		DATA_ALIAS("DA", true, true, false, false), // data dependence heap alias
		SUMMARY_DATA("SD", true, true, false, false), // data dependence summary edges (for stack data deps)
		SUMMARY_NO_ALIAS("SU", true, true, false, false), // data dependence summary edges (for stack data deps)
		SUMMARY_ALIAS("SA", true, true, false, false), // data dependence summary edges (for stack data deps)
		CONTROL_DEP("CD", true, false, false, true), // control dependence
		CONTROL_DEP_EXPR("CE", true, false, false, true), // control dependence for expressions (used to connect parameter nodes)
		CONTROL_FLOW("CF", false, false, true, false), // control flow
		CONTROL_FLOW_EXC("CFE", false, false, true, false), // control flow through exception
		PARAM_STRUCT("PS", false, false, false, false), // parameter structure
		PARAM_EQUIV("PE", false, false, false, false), // parameter equivalence
		UTILITY("HE", false, false, false, false),		// help/utility edge for layouting in graphviewer
        PARAMETER_IN("PI", true, true, false, false), // parameter input
        PARAMETER_OUT("PO", true, true, false, false), // parameter output
		CALL_STATIC("CS", true, false, false, false), // call static
		CALL_VIRTUAL("CV", true, false, false, false), // call virtual (dynamic dispatch)
		RETURN("RET", true, false, false, false), // return edge
		INTERFERENCE("ID", false, true, false, false), // read-write interference edge
		INTERFERENCE_WRITE("IW", false, true, false, false),
		FORK("FORK", false, false, false, false), // special edges for calls of Thread.run() from Thread.start()
		FORK_IN("FORK_IN", false, false, false, false); // param-in edges for calls of Thread.run() from Thread.start()
        private final String name;
        private final boolean isRelevant; // signals kinds that represent a program dependence.
        private final boolean isData; // is a deta dependence
        private final boolean isFlow; // is control flow
        private final boolean isControl; //is a control dependence

        private Kind(String name, boolean relevant, boolean isData, boolean isFlow, boolean isControl) {
        	this.name = name;
        	this.isRelevant = relevant;
        	this.isData = isData;
        	this.isControl = isControl;
        	this.isFlow = isFlow;
        }

        /**
         * @return Returns the name of this kind.
         */
        public String toString() {
            return name;
        }

        /**
         * @return `true' if this kind denotes a program dependence.
         */
        public boolean isRelevant() {
            return isRelevant;
        }

		public boolean isData() {
			return isData;
		}

		public boolean isFlow() {
			return isFlow;
		}

		public boolean isControl() {
			return isControl;
		}

	};

	public PDGEdge(PDGNode from, PDGNode to, Kind kind) {
		if (from == null || to == null) {
			throw new IllegalArgumentException(from + " -" + kind + "-> " + to);
		}

		this.from = from;
		this.to = to;
		this.kind = kind;
	}

	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}

		if (obj instanceof PDGEdge) {
			PDGEdge other = (PDGEdge) obj;
			return kind == other.kind && from.equals(other.from) && to.equals(other.to) ;
		}

		return false;
	}


	@Deprecated
	@SuppressWarnings("unused")
	private int hashCodeOld() {
		return (from.hashCode() ^ (to.hashCode() >> 6)) + kind.hashCode();
	}
	

	@Deprecated
	@SuppressWarnings("unused")
	private int hashCodeEclipse() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (from.hashCode());
		result = prime * result + (kind.hashCode());
		result = prime * result + (to.hashCode());
		return result;
	}
	
	@Override
	public int hashCode() {
		// This one appears to be slightly (but consistently) better than hashCodeEclipse(),
		// and much better than hashCodeOld(), when measured in the time spent in, e.g.,
		// HashMap.add(): 
		// hashCode():      38725ms
		// hashCodeOld():   74769ms
		// hashCodeEclipse: 39605ms
		return (from.hashCode() ^ (Integer.rotateRight(to.hashCode(), 16))) + kind.hashCode();
	}

	@Override
	public final int compareTo(PDGEdge other) {
		final int compareFrom = from.compareTo(other.from);
		if (compareFrom != 0) return compareFrom;
		final int compareTo = to.compareTo(other.to);
		if (compareTo != 0) return compareTo;
		
		return kind.compareTo(other.kind);
	}

	public String toString() {
//		return from.toString() + "-" + kind.name() + "->" + to.toString();
		return kind.toString();
	}
	
	public String toDetailedString() {
		return from.toString() + "-" + kind.name() + "->" + to.toString();
	}

	public void setLabel(String string) {
		if (kind != Kind.DATA_ALIAS) {
			throw new IllegalStateException("setLabel not supported by " + this.getClass().getSimpleName());
		}

		this.label = string;
	}

	public String getLabel() {
		return label;
	}

	/* (non-Javadoc)
	 * @see edu.kit.joana.util.graph.KnowsVertices#getSource()
	 */
	@Override
	public PDGNode getSource() {
		return from;
	}

	/* (non-Javadoc)
	 * @see edu.kit.joana.util.graph.KnowsVertices#getTarget()
	 */
	@Override
	public PDGNode getTarget() {
		return to;
	}

}
