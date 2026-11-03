package macronode.macro.client.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import macronode.macro.MacroNode;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Persists profiles to {@code .minecraft/config/macronode/profiles/*.json} and global settings
 * to {@code config/macronode/settings.json}. Also exposes an {@code imports/} folder whose
 * .json files are listed in the Config Hub import modal.
 */
public final class ProfileStore {
	private static final ProfileStore INSTANCE = new ProfileStore();

	private final List<MacroProfile> profiles = new ArrayList<>();
	private GlobalSettings settings = new GlobalSettings();

	public static ProfileStore get() {
		return INSTANCE;
	}

	public Path rootDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(MacroNode.MOD_ID);
	}

	public Path profilesDir() {
		return rootDir().resolve("profiles");
	}

	public Path importsDir() {
		return rootDir().resolve("imports");
	}

	public Path exportsDir() {
		return rootDir().resolve("exports");
	}

	public List<MacroProfile> profiles() {
		return profiles;
	}

	public GlobalSettings settings() {
		return settings;
	}

	public MacroProfile byId(String id) {
		for (MacroProfile p : profiles) if (p.id.equals(id)) return p;
		return null;
	}

	public void load() {
		profiles.clear();
		try {
			Files.createDirectories(profilesDir());
			Files.createDirectories(importsDir());
			Path s = rootDir().resolve("settings.json");
			if (Files.exists(s)) {
				GlobalSettings loaded = ProfileCodec.COMPACT.fromJson(Files.readString(s, StandardCharsets.UTF_8), GlobalSettings.class);
				if (loaded != null) settings = loaded;
			}
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(profilesDir(), "*.json")) {
				for (Path f : ds) {
					try {
						MacroProfile p = ProfileCodec.fromJson(Files.readString(f, StandardCharsets.UTF_8));
						if (byId(p.id) != null) p.id = MacroProfile.newId();
						profiles.add(p);
					} catch (Exception e) {
						MacroNode.LOGGER.warn("Skipping invalid profile {}: {}", f.getFileName(), e.getMessage());
					}
				}
			}
			profiles.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		} catch (IOException e) {
			MacroNode.LOGGER.error("Failed to load MacroNode profiles", e);
		}
		MacroNode.LOGGER.info("MacroNode loaded {} profile(s)", profiles.size());
	}

	public void saveSettings() {
		try {
			Files.createDirectories(rootDir());
			Files.writeString(rootDir().resolve("settings.json"), ProfileCodec.PRETTY.toJson(settings), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MacroNode.LOGGER.error("Failed to save MacroNode settings", e);
		}
	}

	/** Inserts or replaces the profile (matched by id) and writes it to disk. */
	public void save(MacroProfile p) {
		p.sanitize();
		MacroProfile existing = byId(p.id);
		if (existing == null) {
			profiles.add(p);
		} else if (existing != p) {
			profiles.set(profiles.indexOf(existing), p);
		}
		try {
			Files.createDirectories(profilesDir());
			Files.writeString(profilesDir().resolve(p.id + ".json"), ProfileCodec.toJson(p), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MacroNode.LOGGER.error("Failed to save profile {}", p.name, e);
		}
	}

	public void delete(MacroProfile p) {
		profiles.removeIf(o -> o.id.equals(p.id));
		try {
			Files.deleteIfExists(profilesDir().resolve(p.id + ".json"));
		} catch (IOException e) {
			MacroNode.LOGGER.error("Failed to delete profile {}", p.name, e);
		}
	}

	/** Adds an imported profile under a fresh id so it never overwrites an existing one. */
	public MacroProfile importProfile(MacroProfile p) {
		p.id = MacroProfile.newId();
		save(p);
		return p;
	}

	public List<Path> listImportFiles() {
		List<Path> out = new ArrayList<>();
		try {
			Files.createDirectories(importsDir());
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(importsDir(), "*.json")) {
				for (Path f : ds) out.add(f);
			}
		} catch (IOException e) {
			MacroNode.LOGGER.error("Failed to list import files", e);
		}
		out.sort(null);
		return out;
	}

	public MacroProfile importFile(Path file) throws IOException {
		return importProfile(ProfileCodec.fromJson(Files.readString(file, StandardCharsets.UTF_8)));
	}

	public Path exportFile(MacroProfile p) throws IOException {
		Files.createDirectories(exportsDir());
		String safe = p.name.replaceAll("[^A-Za-z0-9_-]+", "_");
		Path f = exportsDir().resolve(safe + "-" + p.id + ".json");
		Files.writeString(f, ProfileCodec.toJson(p), StandardCharsets.UTF_8);
		return f;
	}
}
