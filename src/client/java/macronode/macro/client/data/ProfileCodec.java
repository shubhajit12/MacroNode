package macronode.macro.client.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * JSON + Base64 share-code codec.
 * <p>
 * Share code format: {@code MN1:} + URL-safe Base64( GZIP( compact JSON ) ).
 * GZIP keeps codes short enough to paste in chat / Discord.
 */
public final class ProfileCodec {
	public static final String PREFIX = "MN1:";
	public static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	public static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

	private ProfileCodec() {
	}

	public static String toJson(MacroProfile p) {
		return PRETTY.toJson(p);
	}

	public static MacroProfile fromJson(String json) throws JsonParseException {
		MacroProfile p = COMPACT.fromJson(json, MacroProfile.class);
		if (p == null) throw new JsonParseException("Empty profile");
		p.sanitize();
		return p;
	}

	public static String toShareCode(MacroProfile p) {
		byte[] json = COMPACT.toJson(p).getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
			gz.write(json);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray());
	}

	/** Accepts a share code (with or without prefix) or raw JSON. */
	public static MacroProfile fromShareCode(String text) throws IllegalArgumentException {
		if (text == null) throw new IllegalArgumentException("Clipboard is empty");
		String s = text.trim();
		if (s.isEmpty()) throw new IllegalArgumentException("Clipboard is empty");
		try {
			if (s.startsWith("{")) return fromJson(s);
			if (s.startsWith(PREFIX)) s = s.substring(PREFIX.length());
			s = s.replaceAll("\\s", "");
			byte[] raw;
			try {
				raw = Base64.getUrlDecoder().decode(s);
			} catch (IllegalArgumentException e) {
				raw = Base64.getDecoder().decode(s);
			}
			String json;
			if (raw.length > 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B) {
				try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw))) {
					json = new String(gz.readAllBytes(), StandardCharsets.UTF_8);
				}
			} else {
				json = new String(raw, StandardCharsets.UTF_8);
			}
			return fromJson(json);
		} catch (IOException | JsonParseException | IllegalArgumentException | IllegalStateException e) {
			throw new IllegalArgumentException("Invalid share code: " + e.getMessage(), e);
		}
	}
}
