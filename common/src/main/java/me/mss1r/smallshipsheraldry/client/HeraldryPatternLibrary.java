package me.mss1r.smallshipsheraldry.client;

import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public final class HeraldryPatternLibrary {
    public static final String EXTENSION = ".sshpattern";
    public static final int MAX_NAME_LENGTH = 48;

    private static final int FORMAT_VERSION = 1;
    private static final long MAX_FILE_BYTES = 512L * 1024L;
    private static final long MAX_NBT_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 256;
    private static final String FORMAT_TAG = "LibraryFormat";
    private static final String PATTERN_TAG = "Pattern";
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private HeraldryPatternLibrary() {
    }

    public static Path directory() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(SmallShipsHeraldry.MOD_ID)
                .resolve("patterns")
                .toAbsolutePath()
                .normalize();
    }

    public static List<Entry> list() {
        Path directory = directory();
        try {
            Files.createDirectories(directory);
            List<Entry> entries = new ArrayList<>();
            try (var paths = Files.list(directory)) {
                paths.filter(HeraldryPatternLibrary::isLibraryFile)
                        .limit(MAX_ENTRIES)
                        .forEach(path -> readEntry(path).ifPresent(entries::add));
            }
            entries.sort(Comparator.comparingLong(Entry::modifiedMillis).reversed()
                    .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
            return List.copyOf(entries);
        } catch (IOException exception) {
            SmallShipsHeraldry.LOGGER.warn("Could not list local heraldry patterns", exception);
            return List.of();
        }
    }

    public static Optional<Entry> find(String name) {
        String safeName = sanitizeName(name);
        if (safeName.isEmpty()) {
            return Optional.empty();
        }
        return readEntry(resolve(safeName));
    }

    public static Result save(String name, HeraldryPatternData data, boolean overwrite) {
        String safeName = sanitizeName(name);
        if (safeName.isEmpty() || data == null) {
            return Result.INVALID_NAME;
        }

        Path directory = directory();
        Path target = resolve(safeName);
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            if (Files.exists(target) && (!overwrite || Files.isSymbolicLink(target))) {
                return Files.isSymbolicLink(target) ? Result.IO_ERROR : Result.ALREADY_EXISTS;
            }

            CompoundTag root = new CompoundTag();
            root.putInt(FORMAT_TAG, FORMAT_VERSION);
            root.put(PATTERN_TAG, data.toTag());
            temporary = Files.createTempFile(directory, ".saving-", ".tmp");
            NbtIo.writeCompressed(root, temporary.toFile());
            if (Files.size(temporary) > MAX_FILE_BYTES) {
                return Result.TOO_LARGE;
            }
            moveSavedFile(temporary, target, overwrite);
            return Result.SUCCESS;
        } catch (IOException | RuntimeException exception) {
            SmallShipsHeraldry.LOGGER.warn("Could not save local heraldry pattern {}", target, exception);
            return Result.IO_ERROR;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static Result rename(Entry entry, String newName) {
        String safeName = sanitizeName(newName);
        if (entry == null || safeName.isEmpty()) {
            return Result.INVALID_NAME;
        }
        Path source = checkedEntryPath(entry);
        Path target = resolve(safeName);
        if (source == null || Files.isSymbolicLink(source)) {
            return Result.IO_ERROR;
        }
        if (source.equals(target)) {
            return Result.SUCCESS;
        }
        if (Files.exists(target)) {
            return Result.ALREADY_EXISTS;
        }
        try {
            Files.move(source, target);
            return Result.SUCCESS;
        } catch (IOException exception) {
            SmallShipsHeraldry.LOGGER.warn("Could not rename local heraldry pattern {}", source, exception);
            return Result.IO_ERROR;
        }
    }

    public static Result delete(Entry entry) {
        Path path = checkedEntryPath(entry);
        if (path == null || Files.isSymbolicLink(path)) {
            return Result.IO_ERROR;
        }
        try {
            return Files.deleteIfExists(path) ? Result.SUCCESS : Result.NOT_FOUND;
        } catch (IOException exception) {
            SmallShipsHeraldry.LOGGER.warn("Could not delete local heraldry pattern {}", path, exception);
            return Result.IO_ERROR;
        }
    }

    public static String sanitizeName(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        boolean previousSpace = false;
        for (int offset = 0; offset < input.length() && result.length() < MAX_NAME_LENGTH; ) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);
            boolean allowed = Character.isLetterOrDigit(codePoint) || codePoint == '_' || codePoint == '-';
            if (Character.isWhitespace(codePoint)) {
                if (!previousSpace && result.length() > 0) {
                    result.append(' ');
                    previousSpace = true;
                }
            } else if (allowed && result.length() + Character.charCount(codePoint) <= MAX_NAME_LENGTH) {
                result.appendCodePoint(codePoint);
                previousSpace = false;
            }
        }
        String name = result.toString().strip();
        if (WINDOWS_RESERVED_NAMES.contains(name.toUpperCase(Locale.ROOT))) {
            name += "_";
        }
        return name;
    }

    private static Optional<Entry> readEntry(Path input) {
        Path path = input.toAbsolutePath().normalize();
        if (!isInsideLibrary(path) || !isLibraryFile(path)) {
            return Optional.empty();
        }
        try {
            if (Files.size(path) > MAX_FILE_BYTES) {
                return Optional.empty();
            }
            CompoundTag root;
            try (DataInputStream stream = new DataInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path))))) {
                root = NbtIo.read(stream, new NbtAccounter(MAX_NBT_BYTES));
            }
            if (root.getInt(FORMAT_TAG) != FORMAT_VERSION || !root.contains(PATTERN_TAG, Tag.TAG_COMPOUND)) {
                return Optional.empty();
            }
            Optional<HeraldryPatternData> data = HeraldryPatternData.tryFromRootTag(root.getCompound(PATTERN_TAG));
            if (data.isEmpty()) {
                return Optional.empty();
            }
            String fileName = path.getFileName().toString();
            String name = fileName.substring(0, fileName.length() - EXTENSION.length());
            return Optional.of(new Entry(name, path, data.get(), Files.getLastModifiedTime(path).toMillis()));
        } catch (IOException | RuntimeException exception) {
            SmallShipsHeraldry.LOGGER.warn("Ignoring invalid local heraldry pattern {}", path, exception);
            return Optional.empty();
        }
    }

    private static boolean isLibraryFile(Path path) {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            return false;
        }
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    private static Path resolve(String safeName) {
        return directory().resolve(safeName + EXTENSION).normalize();
    }

    private static boolean isInsideLibrary(Path path) {
        return path.getParent() != null && path.getParent().equals(directory());
    }

    private static Path checkedEntryPath(Entry entry) {
        Path path = entry.path().toAbsolutePath().normalize();
        return isInsideLibrary(path) && path.getFileName().toString().equalsIgnoreCase(entry.name() + EXTENSION) ? path : null;
    }

    private static void moveSavedFile(Path source, Path target, boolean overwrite) throws IOException {
        try {
            if (overwrite) {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException exception) {
            if (overwrite) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target);
            }
        }
    }

    public record Entry(String name, Path path, HeraldryPatternData data, long modifiedMillis) {
    }

    public enum Result {
        SUCCESS,
        INVALID_NAME,
        ALREADY_EXISTS,
        NOT_FOUND,
        TOO_LARGE,
        IO_ERROR
    }
}
