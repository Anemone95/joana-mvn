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

/**
 * utilities for manipulating values at the bit-level.
 */
public class Bits {

  // there's no reason to instantiate this class.
  private Bits() {
  }

  /**
   * Return the lower 8 bits (as an int) of an int
   */
  public static int lower8(int value) {
    return (value & 0xff);
  }

  /**
   * Return the lower 16 bits (as an int) of an int
   */
  public static int lower16(int value) {
    return (value & 0xffff);
  }

  /**
   * Return the upper 16 bits (as an int) of an int
   */
  public static int upper16(int value) {
    return value >>> 16;
  }

  /**
   * Return the upper 24 bits (as an int) of an int
   */
  public static int upper24(int value) {
    return value >>> 8;
  }

  /**
   * Return the lower 32 bits (as an int) of a long
   */
  public static int lower32(long value) {
    return (int) value;
  }

  /**
   * Return the upper 32 bits (as an int) of a long
   */
  public static int upper32(long value) {
    return (int) (value >>> 32);
  }

  /**
   * Does an int literal val fit in bits bits?
   */
  public static boolean fits(int val, int bits) {
    val = val >> bits - 1;
    return (val == 0 || val == -1);
  }

  /**
   * Return the number of ones in the binary representation of an integer.
   */
  public static int populationCount(int value) {
    return Integer.bitCount(value);
  }
  
  public static int populationCount(long value) {
	    return Long.bitCount(value);
	  }
}
