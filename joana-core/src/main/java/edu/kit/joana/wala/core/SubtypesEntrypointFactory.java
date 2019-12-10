package edu.kit.joana.wala.core;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.impl.SubtypesEntrypoint;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.MethodReference;

public class SubtypesEntrypointFactory implements EntrypointFactory {
    @Override
    public Entrypoint create(IMethod method, IClassHierarchy cha) {
        return new SubtypesEntrypoint(method, cha);
    }

    @Override
    public Entrypoint create(MethodReference method, IClassHierarchy cha) {
        return new SubtypesEntrypoint(method, cha);
    }
}
