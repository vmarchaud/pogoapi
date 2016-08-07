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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.pogoapi.api.auth.ITokenProvider;
import org.pogoapi.api.objects.Location;
import org.pogoapi.api.objects.NetworkRequest;
import org.pogoapi.api.objects.NetworkResult;
import org.pogoapi.internal.exceptions.BadResponseException;
import org.pogoapi.internal.utils.ArrayUtils;
import org.pogoapi.internal.utils.NativeUtils.EncryptLib;
import org.pogoapi.internal.utils.SizeT;

import com.google.protobuf.ByteString;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

import POGOProtos.Networking.Envelopes.AuthTicketOuterClass.AuthTicket;
import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass.RequestEnvelope;
import POGOProtos.Networking.Envelopes.ResponseEnvelopeOuterClass.ResponseEnvelope;
import POGOProtos.Networking.Envelopes.SignatureOuterClass.Signature;
import POGOProtos.Networking.Envelopes.SignatureOuterClass.Signature.DeviceInfo;
import POGOProtos.Networking.Envelopes.SignatureOuterClass.Signature.LocationFix;
import POGOProtos.Networking.Envelopes.SignatureOuterClass.Signature.SensorInfo;
import POGOProtos.Networking.Envelopes.SignatureOuterClass.Signature.iOSActivityStatus;
import POGOProtos.Networking.Envelopes.Unknown6OuterClass.Unknown6;
import POGOProtos.Networking.Envelopes.Unknown6OuterClass.Unknown6.Unknown2;
import lombok.Getter;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
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
	private Long									start;
	private Random									random;
	private byte[]									unk22;
	private XXHashFactory 							factory;
	
	/**
	 * Construct the Network handler, this runnable will be started into a new thread to send async request.
	 * 
	 * @param tokenProvider : the interface used to provide access_token 
	 * @param httpClient : the http client that will be used to make http call
	 * @param location : the location that will be accessed when trying to send client position
	 */
	public NetworkHandler(ITokenProvider tokenProvider, OkHttpClient httpClient, AtomicReference<Location> location) {
		this.tokenProvider = tokenProvider;
		this.http = httpClient;
		this.location = location;
		
		queue = new ConcurrentLinkedQueue<NetworkRequest>();
		last = System.currentTimeMillis();
		start = System.currentTimeMillis();
		endpoint = "https://pgorelease.nianticlabs.com/plfe/rpc";
		random = new Random();
		requestId = random.nextLong();
		protocolVersion = random.nextInt(1000) + 1;
		unk22 = new byte[16];
		random.nextBytes(unk22);
		retry = false;
		factory = XXHashFactory.fastestInstance();
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
			
			// handle sending AuthInfo or AuthTicket
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
			
			// add signature data
			if (ticket != null) {
				WString input = new WString(buildSignature(currentLoc, requests, ticket).toString());
				Pointer output_size = Pointer.NULL;
				byte[] ivbytes = new byte[32];
				random.nextBytes(ivbytes);
				WString iv = new WString(ivbytes.toString());
				
				// TODO this shouldnt work
				WString output = new WString("");
				
				EncryptLib.INSTANCE.encrypt(input, new SizeT(input.length()), iv, new SizeT(32), output, output_size);
				ByteString result = ByteString.EMPTY;
				
				builder.setUnknown6(Unknown6.newBuilder().setRequestType(6).setUnknown2(Unknown2.newBuilder().setUnknown1(result)));
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
			requests.clear();
		}
	}
	
	/**
	 * This function is used to generate an new AuthInfo if needed
	 * @param RequestEnvelope.Builder : envelope builder to set AuthInfo / AuthTicket
	 */
	private void handleAuth(RequestEnvelope.Builder builder) {
		if (ticket == null || ticket.getExpireTimestampMs() < System.currentTimeMillis()) {
			ticket = null;
			builder.setAuthInfo(tokenProvider.getAuthInfo());
		}
		else
			builder.setAuthTicket(ticket);
	}
	
	/**
	 * This function will pool a number of request from the queue
	 * @param max : the maximum number of request that we will pull
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
	 * 
	 * @return Long : a request id
	 */
	private Long getRequestId() {
		return ++requestId;
	}
	
	/**
	 * Build Signature message
	 * @param loc : the location of the client
	 * @param requests : all requests that will be send to compute their hash
	 * @param ticket : the current AuthTicket to compute his hash too
	 * 
	 * @return Signature Message
	 */
	private Signature buildSignature(Location loc, List<NetworkRequest> requests, AuthTicket ticket) {
		// put location data if we have it
		LocationFix.Builder locfix = LocationFix.newBuilder();
		if (loc != null) {
			locfix.setAltitude((float)loc.getAltitude())
				.setLatitude((float)loc.getLatitude())
				.setLongitude((float)loc.getLongitude())
				.setTimestampSinceStart(System.currentTimeMillis() - start)
				.setHorizontalAccuracy((random.nextFloat() * 2) - 1)
				.setProvider("gps")
				.setProviderStatus(3)
				.setLocationType(1)
				.build();
		}
		
		SensorInfo sensor = SensorInfo.newBuilder()
				.setAccelerometerAxes(3)
				.setAccelNormalizedX(random.nextLong())
				.setAccelNormalizedY(random.nextLong())
				.setAccelNormalizedZ(random.nextLong())
				.setAccelRawX(random.nextLong())
				.setAccelRawY(random.nextLong())
				.setAccelRawZ(random.nextLong())
				.setAngleNormalizedX(random.nextDouble())
				.setAngleNormalizedY(random.nextDouble())
				.setAngleNormalizedZ(random.nextDouble())
				.setTimestampSnapshot(System.currentTimeMillis() - start)
				.build();
		
		// put random data that look likes an iPhone
		DeviceInfo device = DeviceInfo.newBuilder()
				.setFirmwareBrand("iPhone OS")
				.setDeviceId("DD52EC54C31F0BFA4B86C786640BFA4B86C78664")
				.setFirmwareType("9.3.3")
				.setHardwareManufacturer("Apple")
				.build();
		
		XXHash32 hash32 = factory.hash32();
		XXHash64 hash64 = factory.hash64();
		List<Long>	hashs = new ArrayList<Long>();
		
		// compute 64 bits ticket hash to use as seed
		Long seed = hash64.hash(ticket.toByteString().asReadOnlyByteBuffer(), 0x1B845238);
		
		// compute all hashs request
		for(NetworkRequest request : requests)
			hashs.add(hash64.hash(request.getRequest().toByteString().asReadOnlyByteBuffer(), seed));
		
		// compute 32 bits ticket hash to use as seed
		int intSeed = hash32.hash(ticket.toByteString().asReadOnlyByteBuffer(), 0x1B845238);
		
		
		// compute the bytes of the location
		// TODO might not work too
		ByteBuffer locbytes = ByteBuffer.wrap(ArrayUtils.concat(ArrayUtils.toByteArray(loc.getLatitude()), 
				ArrayUtils.toByteArray(loc.getLongitude()),  ArrayUtils.toByteArray(loc.getAltitude())));
		// compute both hash
		int lochash1 = Integer.reverseBytes(hash32.hash(locbytes, intSeed));
		int locHash2 = Integer.reverseBytes(hash32.hash(locbytes, 0x1B845238));
		
		// finaly build the signature
		return Signature.newBuilder()
				.setDeviceInfo(device)
				.addLocationFix(locfix.build())
				.setTimestamp(System.currentTimeMillis())
				.setTimestampSinceStart(System.currentTimeMillis() - start)
				.setUnk22(ByteString.copyFrom(unk22))
				.addAllRequestHash(hashs)
				.setLocationHash1(lochash1)
				.setLocationHash2(locHash2)
				.setTimestamp(System.currentTimeMillis())
				.build();
	}
	
	
}
