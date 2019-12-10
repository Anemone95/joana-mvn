/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.util.collections;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Set;

/**
 * Martin Hecker <martin.hecker@kit.edu>
 * 
 */
public final class ModifiableArraySet<E> extends ArraySet<E> {

	private final Class<? super E> clazz;
	
	private ModifiableArraySet(Object[] elements, Class<? super E> clazz) {
		super(elements);
		this.clazz = clazz;
	}
	
	@SuppressWarnings("unchecked")
	public ModifiableArraySet(Class<? super E> clazz) {
		super((E[])Array.newInstance(clazz, 0));
		this.clazz = clazz;
	}
	@SuppressWarnings("unchecked")
	public ModifiableArraySet(E element, Class<? super E> clazz) {
		super((E[])Array.newInstance(clazz, 1));
		this.elements[0] = element;
		this.clazz = clazz;
	}
	public ModifiableArraySet(Set<E> other, Class<? super E> clazz) {
		super(other);
		this.clazz = clazz;
	}
	
	public static <E> ModifiableArraySet<E> own(Object[] elements, Class<? super E> clazz) {
		if (elements == null) return new ModifiableArraySet<>(empty, clazz);
		return new ModifiableArraySet<>(elements, clazz);
	}
	
	@Override
	public final boolean add(E e) {
		if (e == null) throw new NullPointerException();
		
		final int index = binarySearch(0, elements.length, e);
		if (index >= 0) return false;
		
		final int insert = -index - 1;
		assert insert >= 0;
		assert insert == elements.length || elements[insert] != null;
		
		final int newSize = elements.length + 1;

		@SuppressWarnings("unchecked")
		E[] newElements = (E[]) Array.newInstance(clazz, newSize);
		if (insert > 0) {
			System.arraycopy(elements,      0, newElements,          0, insert);
		}
		if (insert < elements.length) {
			System.arraycopy(elements, insert, newElements, insert + 1, elements.length - insert);
		}
		newElements[insert] = e;
		
		this.elements = newElements;
		
		assert invariant();
		return true;
	}

	@Override
	public final boolean addAll(Collection<? extends E> c) {
		if (c.isEmpty()) return false;
		
		if (c instanceof ArraySet<?>) {
			final Object[] otherElements = ((ArraySet<?>)c).elements;
			
			assert otherElements.length > 0;
			
			@SuppressWarnings("unchecked")
			final E[] temp = (E[]) Array.newInstance(clazz, elements.length + otherElements.length);
			
			int i = 0;
			int j = 0;
			
			int nextIndex = 0;
			
			boolean changed = false;
			
			while (j < otherElements.length) {
				@SuppressWarnings("unchecked")
				final E other = (E) otherElements[j];
				final int otherHashCode = other.hashCode();
				
				int i0 = i;
				while (i < elements.length && elements[i].hashCode() < otherHashCode) { i++; }
				final int strideLength = i - i0;
				System.arraycopy(elements, i0, temp, nextIndex, strideLength);
				nextIndex += strideLength;
				
				boolean found = false;
				int ii = i;
				while (ii < elements.length && elements[ii].hashCode() == otherHashCode) {
					if (other.equals(elements[ii])) {
						found = true;
						break;
					}
					ii++;
				}
				
				if (!found) {
					changed = true;
					temp[nextIndex++] = other;
				}
				
				j++;
			}
			final int remaining = elements.length - i;
			System.arraycopy(elements, i, temp, nextIndex, remaining);
			
			final int total = nextIndex + remaining;
			
			assert total <= temp.length;
			if (total == temp.length) {
				this.elements = temp;
			} else {
				@SuppressWarnings("unchecked")
				E[] elements = (E[]) Array.newInstance(clazz, total);
				System.arraycopy(temp, 0, elements, 0, total);
				this.elements = elements; 

			}
			
			assert invariant();
			return changed;
		} else {
			boolean modified = false;
			for (E e : c) {
				if (add(e)) {
					modified = true;
				}
			}
			return modified;
		}
	}
	
	@Override
	public final boolean remove(Object o) {
		if (o == null) throw new NullPointerException();
		
		final int remove = binarySearch(0, elements.length, o);
		if (remove < 0) return false;
		
		assert (remove < elements.length);
		assert (elements.length > 0);
		
		@SuppressWarnings("unchecked")
		E[] newElements = (E[]) Array.newInstance(clazz, elements.length - 1);
		System.arraycopy(elements,          0, newElements,      0, remove);
		System.arraycopy(elements, remove + 1, newElements, remove, elements.length - remove - 1  );
		this.elements = newElements;
		
		assert invariant();
		return true;
	}
	
	@Override
	public final boolean removeAll(Collection<?> c) {
		int removed = 0;
		for (Object o : c) {
			final int remove = binarySearch0(0, elements.length, o);
			if (remove < 0) continue;
			
			assert (remove < elements.length);
			assert (elements.length > 0);
			
			elements[remove] = null;
			removed++;
		}
		
		if (removed > 0) {
			compact(removed);
			
			assert invariant();
			return true;
		}
		
		assert invariant();
		return false;
	}
	
	private void compact(int removed) {
		@SuppressWarnings("unchecked")
		E[] newElements = (E[]) new Object[elements.length - removed];
		
		int k = 0;
		int i = 0;
		
		while (true) {
			while (i < elements.length && elements[i] == null) i++;
			if (i == elements.length)  {
				this.elements = newElements;
				return;
			}
			assert elements[i] != null;
			
			int j = i;
			while (j < elements.length && elements[j] != null) j++;
			assert j == elements.length || elements[j] == null;
			
			int length = j - i;
			assert length > 0;
			
			System.arraycopy(elements, i, newElements,      k, length);
			k += length;
			
			i = j;
		}
	}

	@Override
	public final boolean retainAll(Collection<?> c) {
		int removed = 0;
		for (int i = 0; i < elements.length; i++) {
			assert elements[i] != null;
			if (!c.contains(elements[i])) {
				elements[i] = null;
				removed++;
			}
		}
		
		if (removed > 0) {
			compact(removed);
			
			assert invariant();
			return true;
		}
		
		assert invariant();
		return false;
	}
	

	@Override
	public final void clear() {
		@SuppressWarnings("unchecked")
		E[] newElements = (E[]) new Object[0];
		elements = newElements;
		
		assert invariant();
		return;
	}
	
	public final E[] disown() {
		@SuppressWarnings("unchecked")
		E[] result = (E[]) elements;
		elements = null;
		return result;
	}

}
