/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.util.collections;

/**
 * TODO: @author Add your name here.
 */
public interface Intrusable<T> {
	T getNext();
	void setNext(T next);

}
