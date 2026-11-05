package dev.oriel.buildlogic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/** Hashes entry names and bytes, excluding ZIP order, compression and timestamps. */
public final class CanonicalJarDigest {
    private CanonicalJarDigest() {}

    public static String sha256(Path file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        digest.update("ORIEL-CANONICAL-JAR-1".getBytes(StandardCharsets.UTF_8));
        try (JarFile jar = new JarFile(file.toFile(), false)) {
            var entries = jar.stream().filter(e -> !e.isDirectory())
                    .sorted(Comparator.comparing(ZipEntry::getName)).toList();
            var names = new HashSet<String>();
            byte[] buffer = new byte[65536];
            for (var entry : entries) {
                if (!names.add(entry.getName())) throw new IOException("Duplicate JAR entry: " + entry.getName());
                byte[] name = entry.getName().getBytes(StandardCharsets.UTF_8);
                if (entry.getSize() < 0) throw new IOException("Unknown entry size: " + entry.getName());
                digest.update(ByteBuffer.allocate(4).putInt(name.length).array());
                digest.update(name);
                digest.update(ByteBuffer.allocate(8).putLong(entry.getSize()).array());
                long bytes = 0;
                try (var input = jar.getInputStream(entry)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        digest.update(buffer, 0, count);
                        bytes += count;
                    }
                }
                if (bytes != entry.getSize()) throw new IOException("Entry size mismatch: " + entry.getName());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Supply exactly one JAR path");
        System.out.println(sha256(Path.of(args[0])));
    }
}
