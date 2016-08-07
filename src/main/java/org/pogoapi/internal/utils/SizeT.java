package org.pogoapi.internal.utils;

import com.sun.jna.IntegerType;
import com.sun.jna.Native;

public class SizeT extends IntegerType {
	 public SizeT(long value) { 
		  super(Native.SIZE_T_SIZE, value, true); 
	}
}