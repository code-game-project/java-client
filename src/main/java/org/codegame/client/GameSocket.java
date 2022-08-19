package org.codegame.client;

import java.io.IOException;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

import org.codegame.client.Api.GameInfo;

public class GameSocket {
	private Api api;
	private Session session = new Session();
	private WebSocket websocket;
	private HashMap<String, String> usernameCache = new HashMap<>();
	private CountDownLatch exitEvent = new CountDownLatch(1);

	public GameSocket(String url) {
		api = new Api(url);
	}

	public Api.GameData createGame(boolean makePublic, boolean protect, Object config) throws IOException {
		return api.createGame(makePublic, protect, config);
	}

	public void join(String gameId, String username) throws IOException {
		join(gameId, username, "");
	}

	public void join(String gameId, String username, String joinSecret) throws IOException {
		if (session.gameURL != "")
			throw new IllegalStateException("This socket is already connected to a  game.");
		var player = api.createPlayer(gameId, username, joinSecret);
		connect(gameId, player.id, player.secret);
	}

	public void restoreSession(String username) throws IOException {
		if (session.gameURL != "")
			throw new IllegalStateException("This socket is already connected to a  game.");
		var session = Session.load(api.getURL(), username);
		try {
			connect(session.gameId, session.playerId, session.playerSecret);
		} catch (Exception e) {
			session.remove();
			throw e;
		}
	}

	public void connect(String gameId, String playerId, String playerSecret) throws IOException {
		if (session.gameURL != "")
			throw new IllegalStateException("This socket is already connected to a  game.");

		websocket = api.connectWebSocket(
				"/api/games/" + gameId + "/connect?player_id=" + playerId + "&player_secret=" + playerSecret,
				(String message) -> onMessage(message), () -> onClose());

		session = new Session(api.getURL(), "", gameId, playerId, playerSecret);

		usernameCache = api.fetchPlayers(gameId);
		session.username = usernameCache.get(playerId);
		try {
			session.save();
		} catch (Exception e) {
			System.err.println("ERROR: Failed to save session: " + e.getMessage());
		}
	}

	public void connect(String gameId) throws IOException {
		if (session.gameURL != "")
			throw new IllegalStateException("This socket is already connected to a  game.");

		websocket = api.connectWebSocket(
				"/api/games/" + gameId + "/spectate",
				(String message) -> onMessage(message), () -> onClose());

		session = new Session(api.getURL(), "", gameId, "", "");

		usernameCache = api.fetchPlayers(gameId);
	}

	public void block() {
		try {
			exitEvent.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void close() {
		websocket.sendClose(WebSocket.NORMAL_CLOSURE, "Normal closure.");
		block();
	}

	public Api getApi() {
		return api;
	}

	public Session getSession() {
		return session;
	}

	private void onMessage(String message) {
		System.out.println("Received: " + message);
	}

	private void onClose() {
		exitEvent.countDown();
	}
}
