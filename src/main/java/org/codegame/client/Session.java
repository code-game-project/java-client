package org.codegame.client;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;

import org.apache.commons.io.FilenameUtils;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Represents a CodeGame session.
 */
public class Session {
    private static final String gamesPath = FilenameUtils.concat(Dirs.DataHome(), "codegame/games");

    @Expose(serialize = false)
    String gameURL = "";
    @Expose(serialize = false)
    String username = "";
    @SerializedName("game_id")
    String gameId = "";
    @SerializedName("player_id")
    String playerId = "";
    @SerializedName("player_secret")
    String playerSecret = "";

    /**
     * Creates a new empty session.
     */
    public Session() {
    }

    /**
     * Creates a new session.
     *
     * @param gameURL      The URL of the game.
     * @param username     The username of the player.
     * @param gameId       The ID of the game.
     * @param playerId     The ID of the player.
     * @param playerSecret The player secret.
     */
    public Session(String gameURL, String username, String gameId, String playerId, String playerSecret) {
        this.gameURL = gameURL;
        this.username = username;
        this.gameId = gameId;
        this.playerId = playerId;
        this.playerSecret = playerSecret;
    }

    /**
     * Loads a session from disk.
     *
     * @param gameURL  The URL of the game.
     * @param username The username of the player.
     * @return The loaded session.
     * @throws IOException Thrown when the session doesn't exist or is invalid.
     */
    public static Session load(String gameURL, String username) throws IOException {
        var reader = new InputStreamReader(new FileInputStream(
                FilenameUtils.concat(gamesPath, URLEncoder.encode(gameURL, "UTF-8") + "/" + username + ".json")));
        try {
            var session = Api.json.fromJson(reader, Session.class);
            if (session.gameId == null || session.gameId.isEmpty() || session.playerId == null
                    || session.playerId.isEmpty()
                    || session.playerSecret == null || session.playerSecret.isEmpty()) {
                throw new IOException("Incomplete session file.");
            }
            session.gameURL = gameURL;
            session.username = username;
            return session;
        } finally {
            reader.close();
        }
    }

    /**
     * Writes the session to disk.
     *
     * @throws IOException Thrown when the session file cannot be written.
     * @throws Exception   Thrown when one or more fields of the session are empty.
     */
    public void save() throws IOException, Exception {
        if (gameURL.isEmpty() || username.isEmpty() || gameId.isEmpty() || playerId.isEmpty()
                || playerSecret.isEmpty()) {
            throw new Exception("Incomplete session file.");
        }
        var dir = new File(FilenameUtils.concat(gamesPath, URLEncoder.encode(gameURL, "UTF-8")));
        dir.mkdirs();
        var writer = new BufferedWriter(
                new FileWriter(FilenameUtils.concat(dir.getAbsolutePath(), username + ".json")));
        try {
            var data = Api.json.toJson(this);
            writer.write(data);
        } finally {
            writer.close();
        }
    }

    /**
     * Deletes the session file.
     */
    public void remove() {
        try {
            if (gameURL.isEmpty())
                return;

            var dir = new File(FilenameUtils.concat(gamesPath, URLEncoder.encode(gameURL, "UTF-8")));
            if (!dir.isDirectory())
                return;
            var file = new File(FilenameUtils.concat(dir.getAbsolutePath(), username + ".json"));
            file.delete();
            if (dir.list().length == 0)
                dir.delete();
        } catch (Exception e) {
        }
    }

    /**
     * @return The URL of the game.
     */
    public String getGameURL() {
        return gameURL;
    }

    /**
     * @return The username of the player.
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return The ID of the game.
     */
    public String getGameId() {
        return gameId;
    }

    /**
     * @return The ID of the player.
     */
    public String getPlayerId() {
        return playerId;
    }

    /**
     * @return The player secret.
     */
    public String getPlayerSecret() {
        return playerSecret;
    }
}
