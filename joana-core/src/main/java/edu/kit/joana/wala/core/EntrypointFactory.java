package edu.kit.joana.wala.core;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.MethodReference;

public interface EntrypointFactory {
    Entrypoint create(IMethod method, IClassHierarchy cha);
    Entrypoint create(MethodReference method, IClassHierarchy cha);
}
