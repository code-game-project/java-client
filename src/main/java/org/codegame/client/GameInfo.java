package org.codegame.client;

import com.google.gson.annotations.*;

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
