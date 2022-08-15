package org.codegame.client;

public class GameSocket {
	private Api api;

	public GameSocket(String url) {
		api = new Api(url);
	}

	public Api getApi() {
		return api;
	}
}
