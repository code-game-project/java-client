package org.codegame.client;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;

import javax.net.ssl.HttpsURLConnection;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

public class Api {
	private String url;
	private boolean tls;
	private String baseURL;

	private static Gson json = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.create();

	public Api(String url) {
		HttpURLConnection.setFollowRedirects(true);
		this.url = trimURL(url);
		this.tls = isTLS(this.url);
		this.baseURL = Api.baseURL("http", this.tls, this.url);
	}

	public class GameInfo {
		@SerializedName("name")
		public String name;
		@SerializedName("cg_version")
		public String cgVersion;
		@SerializedName("display_name")
		public String displayName;
		@SerializedName("description")
		public String description;
		@SerializedName("version")
		public String version;
		@SerializedName("repository_url")
		public String repositoryURL;
	}

	public GameInfo fetchInfo() throws IOException {
		return fetchJSON("/api/info", GameInfo.class);
	}

	public <T> T fetchGameConfig(String gameId, Class<T> configClass) throws IOException {
		GameConfigResponse<T> response = fetchJSON("/api/games/" + gameId,
				TypeToken.getParameterized(GameConfigResponse.class,
						configClass).getType());
		return response.config;
	}

	public class GameData {
		@SerializedName("game_id")
		public String id;
		@SerializedName("join_secret")
		public String joinSecret;
	}

	public GameData createGame(boolean makePublic, boolean protect) throws IOException {
		return createGame(makePublic, protect, null);
	}

	private class CreateGameRequest {
		@SerializedName("public")
		public boolean makePublic;
		@SerializedName("protected")
		public boolean protect;
		@SerializedName("config")
		public Object config;
	}

	public GameData createGame(boolean makePublic, boolean protect, Object config) throws IOException {
		var data = new CreateGameRequest();
		data.makePublic = makePublic;
		data.protect = protect;
		data.config = config;
		return postJSON("/api/games", data, GameData.class);
	}

	public class PlayerData {
		@SerializedName("player_id")
		public String id;
		@SerializedName("player_secret")
		public String secret;
	}

	public PlayerData createPlayer(String gameId, String username) throws IOException {
		return createPlayer(gameId, username, "");
	}

	private class CreatePlayerRequest {
		@SerializedName("username")
		public String username;
		@SerializedName("join_secret")
		public String joinSecret;
	}

	public PlayerData createPlayer(String gameId, String username, String joinSecret) throws IOException {
		var data = new CreatePlayerRequest();
		data.username = username;
		data.joinSecret = joinSecret;
		return postJSON("/api/games/" + gameId + "/players", data, PlayerData.class);
	}

	private class FetchUsernameResponse {
		@SerializedName("username")
		public String username;
	}

	public String fetchUsername(String gameId, String playerId) throws IOException {
		return fetchJSON("/api/games/" + gameId + "/players/" + playerId, FetchUsernameResponse.class).username;
	}

	public HashMap<String, String> fetchPlayers(String gameId) throws IOException {
		return fetchJSON("/api/games/" + gameId + "/players",
				TypeToken.getParameterized(HashMap.class, String.class, String.class).getType());
	}

	private <T> T postJSON(String endpoint, Object requestData, Class<T> responseType) throws IOException {
		URL obj = new URL(this.baseURL + endpoint);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/json");

		var reqData = json.toJson(requestData);
		System.out.println("Data: " + reqData);
		con.setDoOutput(true);
		var os = con.getOutputStream();
		os.write(reqData.getBytes());
		os.flush();
		os.close();

		int responseCode = con.getResponseCode();
		if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_CREATED) {
			throw new IOException("Failed to read response from " + endpoint + " endpoint: unexpected response code: "
					+ responseCode);
		}

		InputStreamReader reader = new InputStreamReader(con.getInputStream());
		T data = json.fromJson(reader, responseType);
		return data;
	}

	private <T> T fetchJSON(String endpoint, Class<T> responseType) throws IOException {
		var obj = new URL(this.baseURL + endpoint);
		var con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("GET");
		con.setRequestProperty("Accept", "application/json");
		var reader = new InputStreamReader(con.getInputStream());
		int responseCode = con.getResponseCode();
		if (responseCode != HttpURLConnection.HTTP_OK)
			throw new IOException("Failed to read response from " + endpoint + " endpoint: unexpected response code: "
					+ responseCode);
		T data = json.fromJson(reader, responseType);
		return data;
	}

	private <T> T fetchJSON(String endpoint, Type responseType) throws IOException {
		var obj = new URL(this.baseURL + endpoint);
		var con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("GET");
		con.setRequestProperty("Accept", "application/json");
		var reader = new InputStreamReader(con.getInputStream());
		int responseCode = con.getResponseCode();
		if (responseCode != HttpURLConnection.HTTP_OK)
			throw new IOException("Failed to read response from " + endpoint + " endpoint: unexpected response code: "
					+ responseCode);
		T data = json.fromJson(reader, responseType);
		return data;
	}

	static String trimURL(String url) {
		if (url.endsWith("/"))
			url = url.substring(0, url.length() - 1);
		String[] parts = url.split("://");
		if (parts.length < 2) {
			return url;
		}
		return String.join("://", Arrays.copyOfRange(parts, 1, parts.length));
	}

	static String baseURL(String protocol, boolean tls, String trimmedURL) {
		if (tls)
			return protocol + "s://" + trimmedURL;
		return protocol + "://" + trimmedURL;
	}

	static boolean isTLS(String trimmedURL) {
		try {
			var url = new URL(baseURL("http", true, trimmedURL) + "/api/info");
			var connection = (HttpsURLConnection) url.openConnection();
			connection.getInputStream();
			if (connection.getSSLSession().isEmpty())
				return false;
			return connection.getSSLSession().get().isValid();
		} catch (IOException e) {
			return false;
		}
	}

	public String getURL() {
		return url;
	}

	public String getBaseURL() {
		return baseURL;
	}

	public boolean isTLS() {
		return tls;
	}
}
