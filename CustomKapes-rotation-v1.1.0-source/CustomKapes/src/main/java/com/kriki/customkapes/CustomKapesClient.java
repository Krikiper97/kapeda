package com.kriki.customkapes;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Client-only cape manager. All texture registration takes place on the client thread. */
public final class CustomKapesClient implements ClientModInitializer {
    private static final Path CAPE_FOLDER = FabricLoader.getInstance().getGameDir().resolve("KapeLoader");
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("custom_kapes.json");
    private static final Path PREVIEW_FILE = FabricLoader.getInstance().getConfigDir().resolve("custom_kapes_preview.png");
    private static final Identifier CAPE_TEXTURE = Identifier.of("custom_kapes", "cape");
    private static final long SWITCH_INTERVAL = TimeUnit.MINUTES.toNanos(25);
    private static final int SCAN_TICKS = 40; // roughly every 2 seconds

    private static boolean enabled = true;
    private static boolean loaded = false;
    /** artwork = crop ordinary PNG to the outer face. atlas = preserve standard 2:1 UV layout. */
    private static String imageMode = "artwork";
    private static List<Path> files = List.of();
    private static int currentIndex = 0;
    private static Path currentFile;
    private static long currentModified = -1;
    private static long nextSwitchNanos;
    private static int tickCounter;

    @Override
    public void onInitializeClient() {
        try {
            Files.createDirectories(CAPE_FOLDER);
            Files.createDirectories(CONFIG_FILE.getParent());
        } catch (IOException ex) {
            System.err.println("[Custom Kapes] Cannot create folders: " + ex);
        }
        readConfig();
        nextSwitchNanos = System.nanoTime() + SWITCH_INTERVAL;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++tickCounter >= SCAN_TICKS) {
                tickCounter = 0;
                scanAndUpdate(client);
            }
        });
    }

    public static boolean isEnabled() { return enabled; }
    public static boolean hasCape() { return enabled && loaded; }
    public static Identifier capeId() { return CAPE_TEXTURE; }
    public static void toggle() { enabled = !enabled; writeConfig(); }

    public static String currentName() {
        return currentFile == null ? "none" : currentFile.getFileName().toString();
    }

    private static void readConfig() {
        if (!Files.isRegularFile(CONFIG_FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(CONFIG_FILE)).getAsJsonObject();
            if (root.has("enabled")) enabled = root.get("enabled").getAsBoolean();
            if (root.has("image_mode")) {
                String candidate = root.get("image_mode").getAsString().toLowerCase(Locale.ROOT);
                if (candidate.equals("artwork") || candidate.equals("atlas")) imageMode = candidate;
            }
        } catch (Exception ex) {
            System.err.println("[Custom Kapes] Cannot read config: " + ex);
        }
    }

    private static void writeConfig() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("enabled", enabled);
            root.addProperty("image_mode", imageMode);
            Files.writeString(CONFIG_FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException ex) {
            System.err.println("[Custom Kapes] Cannot save config: " + ex);
        }
    }

    private static void scanAndUpdate(MinecraftClient client) {
        try {
            List<Path> found;
            try (Stream<Path> stream = Files.list(CAPE_FOLDER)) {
                found = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
            }

            if (!found.equals(files)) {
                // Preserve the currently displayed file if the folder changes.
                int oldIndex = found.indexOf(currentFile);
                files = found;
                currentIndex = oldIndex >= 0 ? oldIndex : 0;
                if (oldIndex < 0) currentFile = null;
            }

            if (files.isEmpty()) {
                removeTexture(client);
                currentFile = null;
                currentModified = -1;
                nextSwitchNanos = System.nanoTime() + SWITCH_INTERVAL;
                return;
            }

            long now = System.nanoTime();
            boolean rotate = now - nextSwitchNanos >= 0;
            if (rotate) {
                currentIndex = (currentIndex + 1) % files.size();
                nextSwitchNanos = now + SWITCH_INTERVAL;
            }
            Path selected = files.get(currentIndex);
            long stamp = Files.getLastModifiedTime(selected).toMillis();
            if (loaded && Objects.equals(selected, currentFile) && stamp == currentModified) return;

            // If one file is damaged, try the remaining files instead of getting stuck.
            for (int offset = 0; offset < files.size(); offset++) {
                int candidateIndex = (currentIndex + offset) % files.size();
                Path candidate = files.get(candidateIndex);
                long modified = Files.getLastModifiedTime(candidate).toMillis();
                if (loadTexture(client, candidate)) {
                    currentIndex = candidateIndex;
                    currentFile = candidate;
                    currentModified = modified;
                    return;
                }
            }
            removeTexture(client);
        } catch (Exception ex) {
            System.err.println("[Custom Kapes] Folder scan failed: " + ex);
        }
    }

    private static boolean loadTexture(MinecraftClient client, Path path) {
        NativeImage nativeImage = null;
        try {
            BufferedImage source;
            try (InputStream stream = Files.newInputStream(path)) {
                source = ImageIO.read(stream);
            }
            if (source == null || source.getWidth() < 1 || source.getHeight() < 1
                || source.getWidth() > 4096 || source.getHeight() > 4096) {
                throw new IOException("Not a readable PNG or outside 1..4096 dimensions");
            }
            BufferedImage atlas = createAtlas(source);
            // A preview of the exact converted UV sheet is written for troubleshooting.
            ImageIO.write(atlas, "png", PREVIEW_FILE.toFile());
            nativeImage = new NativeImage(64, 32, true);
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 64; x++) {
                    nativeImage.setColorArgb(x, y, atlas.getRGB(x, y));
                }
            }
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "Custom Kapes", nativeImage);
            nativeImage = null; // ownership transfers to texture
            client.getTextureManager().destroyTexture(CAPE_TEXTURE);
            client.getTextureManager().registerTexture(CAPE_TEXTURE, texture);
            loaded = true;
            System.out.println("[Custom Kapes] Loaded " + path.getFileName() + " (" + imageMode + ")");
            return true;
        } catch (Exception ex) {
            System.err.println("[Custom Kapes] Skipping " + path.getFileName() + ": " + ex);
            return false;
        } finally {
            if (nativeImage != null) nativeImage.close();
        }
    }

    private static void removeTexture(MinecraftClient client) {
        if (loaded) client.getTextureManager().destroyTexture(CAPE_TEXTURE);
        loaded = false;
    }

    private static BufferedImage createAtlas(BufferedImage source) throws IOException {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage atlas = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        try {
            if (imageMode.equals("atlas")) {
                if (width != height * 2) throw new IOException("Atlas mode requires a 2:1 cape PNG");
                g.drawImage(source, 0, 0, 64, 32, null);
                return atlas;
            }

            // All input aspect ratios, INCLUDING 2:1, default to normal artwork.
            // Crop to 10:16 instead of treating the whole PNG as a UV sheet.
            double panelRatio = 10.0 / 16.0;
            int cropW = width;
            int cropH = height;
            if ((double) width / height > panelRatio) cropW = Math.max(1, (int) Math.round(height * panelRatio));
            else cropH = Math.max(1, (int) Math.round(width / panelRatio));
            int sx = (width - cropW) / 2;
            int sy = (height - cropH) / 2;

            // Cape geometry uses a solid render layer. Paint a visible fallback
            // behind transparent artwork so blank source pixels do not look black.
            g.setColor(new java.awt.Color(85, 85, 85));
            g.fillRect(1, 1, 10, 16);
            g.fillRect(12, 1, 10, 16);
            // Outside face: 1..10 x 1..16. Inside face: 12..21 x 1..16.
            g.drawImage(source, 1, 1, 11, 17, sx, sy, sx + cropW, sy + cropH, null);
            g.drawImage(source, 12, 1, 22, 17, sx, sy, sx + cropW, sy + cropH, null);
        } finally {
            g.dispose();
        }
        // Fill all four edge strips with neighboring face pixels, preventing dark gaps.
        for (int y = 1; y <= 16; y++) {
            atlas.setRGB(0, y, atlas.getRGB(1, y));
            atlas.setRGB(11, y, atlas.getRGB(10, y));
        }
        for (int x = 0; x < 10; x++) {
            atlas.setRGB(1 + x, 0, atlas.getRGB(1 + x, 1));
            atlas.setRGB(11 + x, 0, atlas.getRGB(1 + x, 16));
        }
        return atlas;
    }
}
