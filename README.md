# Introduction
The original joana uses WALA 1.4.4-SNAPSHOT, which is a version that joana modified.
So it has some bugs of old WALA version and can't support the new feature of wala - e.g., It can't build CFG when lack some of the dependencies.
To deal with this, this repo rebuilds joana with the newest WALA version.

# Build & Usage
```java
mvn clean install
```

Now it only provide joana-core. To import it in maven, use the following xml:
```xml

```

# Changes
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