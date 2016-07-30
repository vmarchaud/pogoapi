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

import java.util.concurrent.atomic.AtomicReference;

import org.pogoapi.api.auth.ITokenProvider;
import org.pogoapi.api.objects.Location;
import org.pogoapi.internal.NetworkHandler;

import okhttp3.OkHttpClient;

public class NetworkClient {
	
	private NetworkHandler				network;
	private AtomicReference<Location>	location;
	
	/**
	 * Constructor the client that will be used to make network cal
	 * 
	 * @param tokenProvider : the interface used to provide access_token 
	 * @param httpClient : the http client that will be used to make http call
	 */
	public NetworkClient(OkHttpClient httpClient, ITokenProvider tokenProvider) {
		location = new AtomicReference<Location>();
		network = new NetworkHandler(tokenProvider, httpClient, location);
		new Thread(network).start();
	}
	
	/**
	 * Ask the NetworkHandler to send a request
	 * @param request : the request that will be send (with the defined callback)
	 * @return boolean : if the request has been put in queue or not
	 */
	public boolean offerRequest(NetworkRequest request) {
		return network.getQueue().offer(request);
	}
	
	/**
	 * This method is used to atomicly update the position of the client
	 * @param newLoc : the new location
	 */
	public void updateLocation(Location newLoc) {
		location.set(newLoc);
	}
}
