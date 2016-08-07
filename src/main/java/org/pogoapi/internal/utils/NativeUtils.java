package org.pogoapi.internal.utils;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

public class NativeUtils {
	
	public interface EncryptLib extends Library {
		EncryptLib INSTANCE = (EncryptLib)Native.loadLibrary(("niantic"), EncryptLib.class);

		void encrypt(WString input, SizeT inputSize, WString iv, SizeT ivSize, WString output, Pointer output_size);
    }
	
	
}
