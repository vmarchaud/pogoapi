/*******************************************************************************
 * MIT License
 *
 * Copyright (c) 2016 Valentin 'ThisIsMac' Marchaud
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *******************************************************************************/

package org.pogoapi.api;

import org.pogoapi.api.objects.NetworkResult;

import com.google.protobuf.GeneratedMessage;

import POGOProtos.Networking.Requests.RequestOuterClass.Request;
import POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType;
import lombok.Getter;

public class NetworkRequest {
	
	@Getter 
	private Request		request;
	@Getter 
	private ICallback	callback;
	
	/**
	 * Build a network request
	 * @param type : official RequestType from protos
	 * @param message : the message that has been build
	 * @param callback : the callback that will be call when we receive the response
	 */
	public NetworkRequest(RequestType type, GeneratedMessage message, ICallback callback) {
		Request.Builder requestBuilder = Request.newBuilder();
		requestBuilder.setRequestMessage(message.toByteString());
		requestBuilder.setRequestType(type);
		
		this.request = requestBuilder.build();
		this.callback = callback;
	}
	
	public interface ICallback {
		void callback(NetworkResult result, byte[] response);
	}
}
