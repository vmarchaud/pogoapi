package org.pogoapi.internal.utils;

import java.nio.ByteBuffer;

public class ArrayUtils {
	
	public static byte[] toByteArray(double value) {
	    byte[] bytes = new byte[8];
	    ByteBuffer.wrap(bytes).putDouble(value);
	    return bytes;
	}
	
	public static byte[] concat(byte[]... arrays) {
	    int length = 0;
	    for (byte[] array : arrays) {
	        length += array.length;
	    }
	    byte[] result = new byte[length];
	    int pos = 0;
	    for (byte[] array : arrays) {
	        for (byte element : array) {
	            result[pos] = element;
	            pos++;
	        }
	    }
	    return result;
	}
}
