package org.codegame.client;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Api {
	private String url;
	private boolean tls;
	private String baseURL;

	private static Gson json = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.create();

	public Api(String url) {
		this.url = trimURL(url);
		this.tls = isTLS(this.url);
		this.baseURL = Api.baseURL("http", this.tls, this.url);
		HttpURLConnection.setFollowRedirects(true);
	}

	public GameInfo fetchInfo() throws IOException {
		URL obj = new URL(this.baseURL + "/api/info");
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestProperty("Accept", "application/json");
		InputStreamReader reader = new InputStreamReader(con.getInputStream());
		int responseCode = con.getResponseCode();
		if (responseCode != 200)
			throw new IOException("Failed to fetch game info: unexpected response code: " + responseCode);
		GameInfo info = json.fromJson(reader, GameInfo.class);
		return info;
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
			URL url = new URL(baseURL("http", true, trimmedURL) + "/api/info");
			HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
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
