package org.pogoapi.api.auth;

public interface ITokenProvider {
	
	/**
	 * This function is called by the
	 * @return
	 */
	public TokenWrapper	refreshTokens();
	
	/**
	 * Know if the access token is expired or not
	 * @return true if we need to refresh them
	 */
	public boolean		isExpired();
	
	/**
	 * Get the wrapper used to store access and refresh tokens
	 * @return TokenWrapper
	 */
	public TokenWrapper getTokenWrapper();
}
