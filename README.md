# Introduction
The original joana uses WALA 1.4.4-SNAPSHOT, which is a version that joana modified.
So it has some bugs of old WALA version and can't support the new feature of wala - e.g., It can't build CFG when lack some of the dependencies.
To deal with this, this repo rebuilds joana with the newest WALA version.

[中文文档](blob/master/README.zh.md)

# Build & Usage
```java
mvn clean install
```

To import it in maven, use the following xml:
```xml
<dependency>
    <groupId>top.anemone.joana</groupId>
    <artifactId>joana-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

# Changes
## 19/12/11
These function are deprecated or missing in wala 1.5.4, so I have to replace them with the new one.
However, they may cause exception. So I note them here to debug.
```java
from com.ibm.wala.ssa.SSAInstruction#iindex
to: com.ibm.wala.ssa.SSAInstruction#iIndex

from: com.ibm.wala.util.intset.MutableMapping#makeIdentityMapping
to: com.ibm.wala.util.intset.MutableMapping#make

from: NullPointerAnalysis.computeInterprocAnalysis(
						DEFAULT_IGNORE_EXCEPTIONS, nonPrunedCG,	cfg.defaultExceptionMethodState,
						progress, cfg.pruneDDEdgesToDanglingExceptionNodes, false);
to: NullPointerAnalysis.computeInterprocAnalysis(
                        DEFAULT_IGNORE_EXCEPTIONS, nonPrunedCG, cfg.defaultExceptionMethodState,
                        progress, false);

from: NullPointerAnalysis
                .createIntraproceduralExplodedCFGAnalysis(DEFAULT_IGNORE_EXCEPTIONS, n.getIR(),
                        null, cfg.defaultExceptionMethodState, cfg.pruneDDEdgesToDanglingExceptionNodes, false);
to: NullPointerAnalysis
                .createIntraproceduralExplodedCFGAnalysis(DEFAULT_IGNORE_EXCEPTIONS, n.getIR(),
                        null, cfg.defaultExceptionMethodState, false);

from: ReflectionOptions.JOANA
to: ReflectionOptions.ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD

from: com.ibm.wala.ssa.SSAInvokeInstruction#getNumberOfParameters
to: com.ibm.wala.ssa.SSAInvokeInstruction#getNumberOfUses

from: BitVectorIntSet(bvMayRef, maxSet)
to: BitVectorIntSet(bvMayRef)

from: com.ibm.wala.ipa.cfg.PrunedCFG#make(cfg, filter, prune)
to: com.ibm.wala.ipa.cfg.PrunedCFG#make(cfg, filter)

from: addClassPathToScope(String classPath, AnalysisScope scope, ClassLoaderReference loader, boolean addEntriesFromMANIFEST)
to: addClassPathToScope(String classPath, AnalysisScope scope, ClassLoaderReference loader)

from: new OrdinalSet<FieldAccess>(bv.getValue(), genReach.getLatticeValues()).iteratorOrdinalSorted()
to: new OrdinalSet<FieldAccess>(bv.getValue(), genReach.getLatticeValues()).iterator()

from: IntSet#intIteratorSorted() 
to: IntSet#intIterator()

```
And some modifications on edu.kit.joana.wala.core.PDGNodeCreationVisitor#PDGNodeCreationVisitor.

## 19/12/25
Fix null pointer exception in edu.kit.joana.ifc.sdg.mhpoptimization.JoinAnalysis#collectSDGNodesFromIds:
```plain
private static Set<SDGNode> collectSDGNodesFromIds(SDG sdg, int[] ids) {
    Set<SDGNode> ret = new HashSet<SDGNode>(); // possible allocation sites
                                                // of the thread on which
                                                // join() is called
+   /** @Anemone fix null pointer exception */
+   if(ids==null){
+       return ret;
+   }
    for (int alloc_id : ids) {
        ret.add(sdg.getNode(alloc_id));
    }
    return ret;
}
```