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

/**
 * Represents a connection to a game server.
 */
public class GameSocket {
	private static final String cgVersion = "0.7";

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

	/**
	 * Creates a new game socket.
	 *
	 * @param url The URL of the game server. The protocol should be omitted.
	 * @throws IOException Thrown when the URL does not point to a valid CodeGame
	 *                     game server.
	 */
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

	/**
	 * Creates a new game on the server.
	 *
	 * @param makePublic Whether to make the created game public.
	 * @param protect    Whether to protect the game with a join secret.
	 * @param config     The game config.
	 * @return Information about the created game. Including the game ID and the
	 *         join secret when protected == true.
	 * @throws IOException Thrown when the request fails.
	 */
	public Api.GameData createGame(boolean makePublic, boolean protect, Object config) throws IOException {
		return api.createGame(makePublic, protect, config);
	}

	/**
	 * Creates a new player in the game and connects to it.
	 *
	 * @param gameId   The ID of the game.
	 * @param username The desired username.
	 * @throws IOException Thrown when the request fails.
	 */
	public void join(String gameId, String username) throws IOException {
		join(gameId, username, "");
	}

	/**
	 * Creates a new player in the protected game and connects to it.
	 *
	 * @param gameId     The ID of the game.
	 * @param username   The desired username.
	 * @param joinSecret The join secret of the game.
	 * @throws IOException Thrown when the request fails.
	 */
	public void join(String gameId, String username, String joinSecret) throws IOException {
		if (session.gameURL != "")
			throw new IllegalStateException("This socket is already connected to a  game.");
		var player = api.createPlayer(gameId, username, joinSecret);
		connect(gameId, player.id, player.secret);
	}

	/**
	 * Loads the session from disk and reconnects to the game.
	 *
	 * @param username The username of the session.
	 * @throws IOException Thrown when the session doesn't exist or the request
	 *                     fails.
	 */
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

	/**
	 * Connects to a player on the server.
	 *
	 * @param gameId       The ID of the game.
	 * @param playerId     The ID of the player.
	 * @param playerSecret The secret of the player.
	 * @throws IOException Thrown when the connection fails.
	 */
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

	/**
	 * Connects to a game as a spectator.
	 *
	 * @param gameId The ID of the game.
	 * @throws IOException Thrown when the connection fails.
	 */
	public void spectate(String gameId) throws IOException {
		if (session.gameURL != "")
			throw new IllegalStateException("This socket is already connected to a  game.");

		websocket = api.connectWebSocket(
				"/api/games/" + gameId + "/spectate",
				(String message) -> onMessage(message), () -> onClose());

		session = new Session(api.getURL(), "", gameId, "", "");

		usernameCache = api.fetchPlayers(gameId);
	}

	/**
	 * Blocks until the connection is closed.
	 */
	public void listen() {
		try {
			exitEvent.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Close the underlying websocket connection.
	 */
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

	/**
	 * Registers a callback that is triggered every time the event is received.
	 *
	 * @param <T>       The type of the event data.
	 * @param eventName The name of the event.
	 * @param type      The type of the event data.
	 * @param callback  The callback function.
	 * @return An ID that can be used to remove the callback.
	 */
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

	/**
	 * Registers a callback that is triggered the next time the event is received.
	 *
	 * @param <T>       The type of the event data.
	 * @param eventName The name of the event.
	 * @param type      The type of the event data.
	 * @param callback  The callback function.
	 * @return An ID that can be used to remove the callback.
	 */
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

	/**
	 * Sends the command to the server.
	 *
	 * @param <T>         The type of the command data.
	 * @param commandName The name of the command.
	 * @param data        The command data.
	 */
	public <T> void send(String commandName, T data) {
		if (websocket == null || session.getPlayerId().isEmpty())
			throw new IllegalStateException("The socket is not connected to a player.");
		Event<T> e = new Event<>(commandName, data);
		var json = Api.json.toJson(e, TypeToken.getParameterized(Event.class, data.getClass()).getType());
		websocket.sendText(json, true).join();
	}

	/**
	 * Removes the event callback.
	 *
	 * @param eventName The name of the event.
	 * @param id        The ID of the callback.
	 */
	public void removeCallback(String eventName, String id) {
		if (!eventListeners.containsKey(eventName))
			return;
		eventListeners.get(eventName).callbacks.remove(id);
	}

	/**
	 * Retrieves the username of the player from the player cache or fetches it from
	 * the server if it is not already there.
	 *
	 * @param playerID The ID of the player.
	 * @return Ther usernamer of the player.
	 * @throws IOException Thrown when the request fails.
	 */
	public String username(String playerID) throws IOException {
		if (usernameCache.containsKey(playerID))
			return usernameCache.get(playerID);
		var username = api.fetchUsername(session.gameId, playerID);
		usernameCache.put(playerID, username);
		return username;
	}

	/**
	 * @return An instance of the Api class which can be used to make requests to
	 *         the game server.
	 */
	public Api getApi() {
		return api;
	}

	/**
	 * @return The current session object.
	 */
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
