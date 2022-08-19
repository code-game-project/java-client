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

    public Session() {
    }

    public Session(String gameURL, String username, String gameId, String playerId, String playerSecret) {
        this.gameURL = gameURL;
        this.username = username;
        this.gameId = gameId;
        this.playerId = playerId;
        this.playerSecret = playerSecret;
    }

    public static Session load(String gameURL, String username) throws IOException {
        var reader = new InputStreamReader(new FileInputStream(
                FilenameUtils.concat(gamesPath, URLEncoder.encode(gameURL, "UTF-8") + "/" + username + ".json")));
        try {
            var session = Api.json.fromJson(reader, Session.class);
            if (session.gameId == null || session.gameId == "" || session.playerId == null || session.playerId == ""
                    || session.playerSecret == null || session.playerSecret == "") {
                throw new IOException("Incomplete session file.");
            }
            session.gameURL = gameURL;
            session.username = username;
            return session;
        } finally {
            reader.close();
        }
    }

    public void save() throws IOException, Exception {
        if (gameURL == "" || username == "" || gameId == "" || playerId == "" || playerSecret == "") {
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

    public void remove() {
        try {
            if (gameURL == "")
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

    public String getGameURL() {
        return gameURL;
    }

    public String getUsername() {
        return username;
    }

    public String getGameId() {
        return gameId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getPlayerSecret() {
        return playerSecret;
    }
}
