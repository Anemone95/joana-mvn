package edu.kit.joana.wala.core.prune;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import edu.kit.joana.wala.core.SDGBuilder;

import java.util.Set;

public interface CGPruner {
    Set<CGNode> prune(final SDGBuilder.SDGBuilderConfig cfg, CallGraph cg);
}
