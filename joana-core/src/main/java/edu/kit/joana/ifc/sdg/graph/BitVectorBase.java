/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package edu.kit.joana.ifc.sdg.graph;

import java.io.Serializable;

import com.ibm.wala.util.intset.IntIterator;

/**
 * Abstract base class for implementations of bitvectors
 */
@SuppressWarnings("rawtypes")
abstract public class BitVectorBase<T extends BitVectorBase> implements Cloneable, Serializable {

	private static final long serialVersionUID = 6616478949492760615L;

	protected final static int LOG_BITS_PER_UNIT = 5;

	protected final static int BITS_PER_UNIT = 32;

	protected final static int MASK = 0xffffffff;

	protected final static int LOW_MASK = 0x1f;

	protected int bits[];

	public abstract void set(int bit);

	public abstract void clear(int bit);

	public abstract boolean get(int bit);

	public abstract int length();

	public abstract void and(T other);

	public abstract void andNot(T other);

	public abstract void or(T other);

	public abstract void xor(T other);

	public abstract boolean sameBits(T other);

	public abstract boolean isSubset(T other);

	public abstract boolean intersectionEmpty(T other);

	/**
	 * Convert bitIndex to a subscript into the bits[] array.
	 */
	public static int subscript(int bitIndex) {
		return bitIndex >> LOG_BITS_PER_UNIT;
	}

	/**
	 * Clears all bits.
	 */
	public final void clearAll() {
		for (int i = 0; i < bits.length; i++) {
			bits[i] = 0;
		}
	}

	@Override
	public int hashCode() {
		int h = 1234;
		for (int i = bits.length - 1; i >= 0;) {
			h ^= bits[i] * (i + 1);
			i--;
		}
		return h;
	}

	/**
	 * How many bits are set?
	 */
	public final int populationCount() {
		int count = 0;
		for (int i = 0; i < bits.length; i++) {
			count += Bits.populationCount(bits[i]);
		}
		return count;
	}

	public boolean isZero() {
		int setLength = bits.length;
		for (int i = setLength - 1; i >= 0;) {
			if (bits[i] != 0)
				return false;
			i--;
		}
		return true;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object clone() {
		BitVectorBase<T> result = null;
		try {
			result = (BitVectorBase<T>) super.clone();
		} catch (CloneNotSupportedException e) {
			// this shouldn't happen, since we are Cloneable
			throw new InternalError();
		}
		result.bits = new int[bits.length];
		System.arraycopy(bits, 0, result.bits, 0, result.bits.length);
		return result;
	}

	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		boolean needSeparator = false;
		buffer.append('{');
		int limit = length();
		for (int i = 0; i < limit; i++) {
			if (get(i)) {
				if (needSeparator) {
					buffer.append(", ");
				} else {
					needSeparator = true;
				}
				buffer.append(i);
			}
		}
		buffer.append('}');
		return buffer.toString();
	}

	/*
	 * @see com.ibm.wala.util.intset.IntSet#contains(int)
	 */
	public boolean contains(int i) {
		return get(i);
	}

	private static final int[][] masks = new int[][] {
			{ 0xFFFF0000 },
			{ 0xFF000000, 0x0000FF00 },
			{ 0xF0000000, 0x00F00000, 0x0000F000, 0x000000F0 },
			{ 0xC0000000, 0x0C000000, 0x00C00000, 0x000C0000, 0x0000C000, 0x00000C00, 0x000000C0, 0x0000000C },
			{ 0x80000000, 0x20000000, 0x08000000, 0x02000000, 0x00800000, 0x00200000, 0x00080000, 0x00020000,
					0x00008000, 0x00002000, 0x00000800, 0x00000200, 0x00000080, 0x00000020, 0x00000008, 0x00000002 } };

	public int max() {
		int lastWord = bits.length - 1;

		while (lastWord >= 0 && bits[lastWord] == 0)
			lastWord--;

		if (lastWord < 0)
			return -1;

		int count = lastWord * BITS_PER_UNIT;

		int top = bits[lastWord];

		int j = 0;
		for (int i = 0; i < masks.length; i++) {
			if ((top & masks[i][j]) != 0) {
				j <<= 1;
			} else {
				j <<= 1;
				j++;
			}
		}

		return count + (31 - j);
	}

	/**
	 * @return min j >= start s.t get(j)
	 */
	public int nextSetBit(int start) {
		if (start < 0) {
			throw new IllegalArgumentException("illegal start: " + start);
		}
		int word = subscript(start);
		int bit = (1 << (start & LOW_MASK));
		while (word < bits.length) {
			if (bits[word] != 0) {
				do {
					if ((bits[word] & bit) != 0)
						return start;
					bit <<= 1;
					start++;
				} while (bit != 0);
			} else {
				start += (BITS_PER_UNIT - (start & LOW_MASK));
			}

			word++;
			bit = 1;
		}

		return -1;
	}
	
	/**
	 * via: https://lemire.me/blog/2018/02/21/iterating-over-set-bits-quickly/
	 * @return
	 */
	public IntIterator intIterator() {
		assert bits.length > 0;
		return new IntIterator() {
			int k = 0;
			int bitset = bits[k];
			
			@Override
			public int next() {
				assert bitset != 0;
				int bs = bitset;
				final int t = Integer.lowestOneBit(bs);
				final int r = Integer.numberOfTrailingZeros(bs);
				final int result = (k << LOG_BITS_PER_UNIT) + r;
				bitset ^= t;
				return result;
			}
			
			@Override
			public boolean hasNext() {
				if (bitset != 0) return true;
				while (++k < bits.length) {
					int bitset = bits[k];
					if (bitset != 0) {
						this.bitset = bitset;
						return true;
					}
				}
				return false;
			}
		};
	}

	/**
	 * Copies the values of the bits in the specified set into this set.
	 * 
	 * @param set
	 *            the bit set to copy the bits from
	 * @throws IllegalArgumentException
	 *             if set is null
	 */
	public void copyBits(BitVectorBase set) {
		if (set == null) {
			throw new IllegalArgumentException("set is null");
		}
		int setLength = set.bits.length;
		bits = new int[setLength];
		for (int i = setLength - 1; i >= 0;) {
			bits[i] = set.bits[i];
			i--;
		}
	}
	
	public static void main(String[] args) {
		BitVector bv = new BitVector();
		bv.set(0);
		bv.set(3);
		bv.set(5);
		bv.set(31);
		bv.set(64);
		bv.set(18932);
		bv.set(53223);
		
		IntIterator it = bv.intIterator();
		while (it.hasNext()) {
			int next = it.next();
			System.out.println(next);
		}
		
		
	}
}
