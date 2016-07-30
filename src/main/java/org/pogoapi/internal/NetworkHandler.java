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

package org.pogoapi.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.pogoapi.api.NetworkRequest;
import org.pogoapi.api.auth.ITokenProvider;
import org.pogoapi.api.objects.Location;
import org.pogoapi.api.objects.NetworkResult;
import org.pogoapi.internal.exceptions.BadResponseException;

import POGOProtos.Networking.Envelopes.AuthTicketOuterClass.AuthTicket;
import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass.RequestEnvelope;
import POGOProtos.Networking.Envelopes.ResponseEnvelopeOuterClass.ResponseEnvelope;
import lombok.Getter;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NetworkHandler implements Runnable {

	@Getter
	private ConcurrentLinkedQueue<NetworkRequest>	queue;
	
	// data needed for RequestEnveloppe
	private AuthTicket								ticket;
	private Long									requestId;
	private String									endpoint;
	private AtomicReference<Location>				location;
	private int										protocolVersion;
	
	// internal used data
	private ITokenProvider							tokenProvider;
	private OkHttpClient							http;
	private List<NetworkRequest>					requests;
	private boolean									retry;
	private Long									last;
	
	/**
	 * Construct the Network handler, this runnable will be started into a new thread to send async request.
	 * 
	 * @param ITokenProvider : the interface used to provide access_token 
	 * @param OkHttpClient : the http client that will be used to make http call
	 * @param AtomicReference<Location>: the location that will be accessed when trying to send client position
	 */
	public NetworkHandler(ITokenProvider tokenProvider, OkHttpClient httpClient, AtomicReference<Location> location) {
		this.tokenProvider = tokenProvider;
		this.http = httpClient;
		this.location = location;
		
		queue = new ConcurrentLinkedQueue<NetworkRequest>();
		last = System.currentTimeMillis();
		endpoint = "https://pgorelease.nianticlabs.com/plfe/rpc";
		requestId = new Random().nextLong();
		protocolVersion = new Random().nextInt(1000) + 1;
		retry = false;
	}
	
	@Override
	public void run() {
		// this loop should always run
		while ( true ) {
			// if we dont want to remake some call
			if (retry == false) {
				// ignore if the last request was send less than 200 ms ago
				if (System.currentTimeMillis() < last + 200) 
					continue ;
				
				// check if we have requests to send
				requests = pollRequests(5);
				if (requests.size() == 0)
					continue;
			}
			
			RequestEnvelope.Builder builder = RequestEnvelope.newBuilder();
			
			// setup the request envelope
			builder.setStatusCode(2);
			builder.setRequestId(getRequestId());
			builder.setUnknown12(protocolVersion); 
			
			// handle auth
			handleAuth(builder);
			
			// get the latest location reference to use for our network call
			Location currentLoc = location.get();
			if (currentLoc != null) {
				builder.setAltitude(currentLoc.getAltitude());
				builder.setLatitude(currentLoc.getLatitude());
				builder.setLongitude(currentLoc.getLongitude());
			}
			
			// serialize our all our NetworkRequests
			for(NetworkRequest nrequest : requests) {
				builder.addRequests(nrequest.getRequest());
			}
			
			// build the final http request
			RequestEnvelope reqEnvelope = builder.build();
			okhttp3.Request httpRequest = new okhttp3.Request.Builder()
					.url(endpoint)
					.post(RequestBody.create(null, reqEnvelope.toByteArray()))
					.build();
			
			// make the http call
			try (Response httpResp = http.newCall(httpRequest).execute()) {
				if (httpResp.code() != 200)
					throw new IOException("Bad http code from server : " + httpResp.code());
				
				// try to parse response from server
				ResponseEnvelope resEnvelope = ResponseEnvelope.parseFrom(httpResp.body().byteStream());
				
				// statusCode 53 is that we need to switch servers
				if (resEnvelope.getStatusCode() == 53) {
					endpoint = "https://" + resEnvelope.getApiUrl() + "/rpc";
					retry = true;
					continue ;
				}
				
				// update our auth ticket if the response contain one
				if (resEnvelope.hasAuthTicket())
					ticket = resEnvelope.getAuthTicket();
				
				// handle callback
				for (short i = 0; i < resEnvelope.getReturnsList().size(); i++) {
					// if the data is empty, call the callback with an custom exception
					if (resEnvelope.getReturns(i) == null) {
						requests.get(i).getCallback().callback(
								new NetworkResult(false, new BadResponseException("Null buffer sent by servers")), null);
					} else {
						requests.get(i).getCallback().callback(
								new NetworkResult(true, null), resEnvelope.getReturns(i).toByteArray());
					}
				}
				
			} catch (IOException e) {
				for (NetworkRequest nrequest : requests) {
					nrequest.getCallback().callback(new NetworkResult(false, e), null);
				}
			}
			// reset internal var
			retry = false;
			last = System.currentTimeMillis();
		}
	}
	
	/**
	 * This function is used to generate an new AuthInfo if needed
	 * @param RequestEnvelope.Builder : envelope builder to set AuthInfo / AuthTicket
	 */
	private void handleAuth(RequestEnvelope.Builder builder) {
		if (ticket == null || ticket.getExpireTimestampMs() < System.currentTimeMillis())
			builder.setAuthInfo(tokenProvider.getAuthInfo());
		else
			builder.setAuthTicket(ticket);
	}
	
	/**
	 * This function will pool a number of request from the queue
	 * @param Integer : the maximum number of request that we will pull
	 * @return List<NetworkRequest>
	 */
	private List<NetworkRequest> pollRequests(int max) {
		List<NetworkRequest> requests = new ArrayList<NetworkRequest>();
		
		while (requests.size() < max) {
			NetworkRequest request = queue.poll();
			if (request != null)
				requests.add(request);
			else
				break ;
		}
		
		return requests;
	}
	
	/**
	 * This function is used to increment and get a request id
	 * This is not really needed today since Niantic doesnt check it 
	 * but if one day it will.
	 * @return Long : a request id
	 */
	private Long getRequestId() {
		return ++requestId;
	}
}
