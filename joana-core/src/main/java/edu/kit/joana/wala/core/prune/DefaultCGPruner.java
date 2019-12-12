package edu.kit.joana.wala.core.prune;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.pruned.CallGraphPruning;
import edu.kit.joana.wala.core.SDGBuilder;

import java.util.Set;

public class DefaultCGPruner implements CGPruner{
    /**
     * Get nodes which belong to Application
     * @param cfg
     * @param cg
     * @return
     */
    @Override
    public Set<CGNode> prune(SDGBuilder.SDGBuilderConfig cfg, CallGraph cg) {
        CallGraphPruning cgp = new CallGraphPruning(cg);
        return cgp.findNodes(cfg.prunecg, cfg.pruningPolicy);
    }
}
