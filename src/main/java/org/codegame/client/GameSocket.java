package org.codegame.client;

import java.io.IOException;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.fusesource.jansi.Ansi.*;

public class GameSocket {
	private static final String cgVersion = "0.8";

	@FunctionalInterface
	public interface EventCallback<T> {
		void cb(T data);
	}

	private class Callbacks<T> {
		Class<T> type;
		HashMap<String, EventCallback<T>> callbacks;

		Callbacks(Class<T> type) {
			this.type = type;
			this.callbacks = new HashMap<>();
		}
	}

	private Api api;
	private Session session = new Session();
	private WebSocket websocket;
	private HashMap<String, String> usernameCache = new HashMap<>();
	@SuppressWarnings("rawtypes")
	private HashMap<String, Callbacks> eventListeners = new HashMap<>();
	private CountDownLatch exitEvent = new CountDownLatch(1);

	public GameSocket(String url) throws IOException {
		AnsiConsole.systemInstall();
		api = new Api(url);
		var info = api.fetchInfo();
		if (!isVersionCompatible(info.cgVersion)) {
			System.out.println(Ansi.ansi().fg(Color.YELLOW)
					.a("WARNING: CodeGame version mismatch. Server: v" + info.cgVersion + ", client: v" + cgVersion)
					.reset());
		}
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

	public void spectate(String gameId) throws IOException {
		if (session.gameURL != "")
			throw new IllegalStateException("This socket is already connected to a  game.");

		websocket = api.connectWebSocket(
				"/api/games/" + gameId + "/spectate",
				(String message) -> onMessage(message), () -> onClose());

		session = new Session(api.getURL(), "", gameId, "", "");

		usernameCache = api.fetchPlayers(gameId);
	}

	public void listen() {
		try {
			exitEvent.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void close() {
		websocket.sendClose(WebSocket.NORMAL_CLOSURE, "Normal closure.");
		listen();
	}

	public static class Event<T> {
		@SerializedName("name")
		String name;
		@SerializedName("data")
		T data;

		public Event() {
		}

		public Event(String name, T data) {
			this.name = name;
			this.data = data;
		}
	}

	@SuppressWarnings("unchecked")
	public <T> String on(String eventName, Class<T> type, EventCallback<T> callback) {
		if (!eventListeners.containsKey(eventName))
			eventListeners.put(eventName, new Callbacks<>(type));

		var id = UUID.randomUUID().toString();
		var callbacks = (Callbacks<T>) eventListeners.get(eventName);
		if (!callbacks.type.getTypeName().equals(type.getTypeName()))
			throw new IllegalArgumentException("Wrong event listener type.");
		callbacks.type = type;
		callbacks.callbacks.put(id, callback);
		return id;
	}

	@SuppressWarnings("unchecked")
	public <T> String once(String eventName, Class<T> type, EventCallback<T> callback) {
		if (!eventListeners.containsKey(eventName))
			eventListeners.put(eventName, new Callbacks<>(type));

		var id = UUID.randomUUID().toString();
		var callbacks = (Callbacks<T>) eventListeners.get(eventName);
		if (!callbacks.type.getTypeName().equals(type.getTypeName()))
			throw new IllegalArgumentException("Wrong event listener type.");
		callbacks.type = type;
		callbacks.callbacks.put(id, (data) -> {
			callback.cb(data);
			removeCallback(eventName, id);
		});
		return id;
	}

	public <T> void send(String commandName, T data) {
		if (websocket == null || session.getPlayerId().isEmpty())
			throw new IllegalStateException("The socket is not connected to a player.");
		Event<T> e = new Event<>(commandName, data);
		var json = Api.json.toJson(e, TypeToken.getParameterized(Event.class, data.getClass()).getType());
		websocket.sendText(json, true).join();
	}

	public void removeCallback(String eventName, String id) {
		if (!eventListeners.containsKey(eventName))
			return;
		eventListeners.get(eventName).callbacks.remove(id);
	}

	public String username(String playerID) throws IOException {
		if (usernameCache.containsKey(playerID))
			return usernameCache.get(playerID);
		var username = api.fetchUsername(session.gameId, playerID);
		usernameCache.put(playerID, username);
		return username;
	}

	public Api getApi() {
		return api;
	}

	public Session getSession() {
		return session;
	}

	private class EventMessage {
		@SerializedName("name")
		String name;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void onMessage(String message) {
		var emptyEvent = Api.json.fromJson(message, EventMessage.class);
		if (!eventListeners.containsKey(emptyEvent.name))
			return;
		var callbacks = eventListeners.get(emptyEvent.name);
		Event event = Api.json.fromJson(message, TypeToken.getParameterized(Event.class, callbacks.type).getType());
		for (EventCallback cb : ((HashMap<String, EventCallback>) callbacks.callbacks).values()) {
			cb.cb(event.data);
		}
	}

	private void onClose() {
		exitEvent.countDown();
	}

	private static boolean isVersionCompatible(String serverVersion) {
		var serverParts = serverVersion.split("\\.");
		if (serverParts.length == 1)
			serverParts = new String[] { serverParts[0], "0" };
		var clientParts = cgVersion.split("\\.");
		if (clientParts.length == 1)
			clientParts = new String[] { clientParts[0], "0" };

		if (!serverParts[0].equals(clientParts[0]))
			return false;

		if (clientParts[0].equals("0"))
			return serverParts[1].equals(clientParts[1]);

		try {
			var serverMinor = Integer.parseInt(serverParts[1]);
			var clientMinor = Integer.parseInt(clientParts[1]);
			return clientMinor <= serverMinor;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
