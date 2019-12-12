package edu.kit.joana.wala.core.prune;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import edu.kit.joana.wala.core.SDGBuilder;
import edu.kit.joana.wala.core.prune.CGPruner;

import java.util.*;

public class NodeLimitPruner implements CGPruner {
    private int nodeLimit;
    public NodeLimitPruner(){
        this.nodeLimit=Integer.MAX_VALUE;
    }
    public NodeLimitPruner(int nodeLimit){
        this.nodeLimit=nodeLimit;
    }
    @Override
    public Set<CGNode> prune(final SDGBuilder.SDGBuilderConfig cfg, final CallGraph cg) {

        Set<CGNode> keep=new HashSet<>();
        Set<CGNode> marked=new HashSet<>();

        // BFS
        Queue<CGNode> queue= new LinkedList<>();
        CGNode head=cg.getFakeRootNode();
        queue.add(head);
        while (!queue.isEmpty()){
            if (keep.size()>=nodeLimit)
                break;
            head=queue.poll();
            keep.add(head);

            for (Iterator<CGNode> it = cg.getSuccNodes(head); it.hasNext(); ) {
                CGNode childNode = it.next();
                if (!marked.contains(childNode)){
                    marked.add(childNode);
                    if (cfg.pruningPolicy.check(childNode)){
                        queue.add(childNode);
                    }
                }
            }
        }

        return keep;
    }
}
