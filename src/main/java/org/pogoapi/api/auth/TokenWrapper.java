package org.pogoapi.api.auth;

import lombok.Data;

@Data
public class TokenWrapper {
	
	private String	refreshToken;
	private String	accessToken;
}
