package org.example;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;

public class Main {

    /**
     * Computes perceptual hashes for all PDFs in the given folder.
     *
     * @param folderPath Folder containing PDFs
     * @return Map of file name to perceptual hash
     */
    public static Map<String, Long> computePdfHashes(String folderPath) {
        File[] files = new File(folderPath)
                .listFiles((d, n) -> n.toLowerCase().endsWith(".pdf"));
        Map<String, Long> fileHash = new LinkedHashMap<>();
        if (files == null) return fileHash;
        for (File pdf : files) {
            try {
                BufferedImage img = renderPdfPage(pdf, 0);
                long hash = averageHash64(img);
                fileHash.put(pdf.getName(), hash);
            } catch (Exception e) {
                System.err.println("Failed to process " + pdf.getName() + ": " + e.getMessage());
            }
        }
        return fileHash;
    }

    /**
     * Groups files by perceptual hash similarity (Hamming distance <= threshold).
     *
     * @param fileHashes Map of file name to hash
     * @param threshold  Hamming distance threshold
     * @return Map of original file name to list of duplicates (including original)
     */
    public static Map<String, List<String>> groupDuplicates(Map<String, Long> fileHashes, int threshold) {
        List<String> files = new ArrayList<>(fileHashes.keySet());
        boolean[] seen = new boolean[files.size()];
        Map<String, List<String>> duplicateGroups = new LinkedHashMap<>();
        for (int i = 0; i < files.size(); i++) {
            if (seen[i]) continue;
            String a = files.get(i);
            Long ha = fileHashes.get(a);
            if (ha == null) continue;
            List<String> group = new ArrayList<>();
            group.add(a);
            seen[i] = true;
            for (int j = i + 1; j < files.size(); j++) {
                if (seen[j]) continue;
                String b = files.get(j);
                Long hb = fileHashes.get(b);
                if (hb == null) continue;
                int dist = Long.bitCount(ha ^ hb);
                if (dist <= threshold) {
                    group.add(b);
                    seen[j] = true;
                }
            }
            if (group.size() > 1) {
                duplicateGroups.put(a, group);
            }
        }
        return duplicateGroups;
    }

    /**
     * Returns a list of all originals (first file in each group, including unique files).
     */
    public static List<String> getOriginals(Map<String, Long> fileHashes, Map<String, List<String>> duplicateGroups) {
        Set<String> originals = new LinkedHashSet<>();
        originals.addAll(duplicateGroups.keySet());
        // Add unique files (not in any group)
        for (String file : fileHashes.keySet()) {
            boolean isDuplicate = false;
            for (List<String> group : duplicateGroups.values()) {
                if (group.contains(file)) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) originals.add(file);
        }
        return new ArrayList<>(originals);
    }

    /**
     * Returns a list of duplicate file names for the given file name (including itself if it is an original), or empty if none.
     */
    public static List<String> findDuplicatesForFile(String folderPath, String fileName, int threshold) {
        Map<String, Long> fileHashes = computePdfHashes(folderPath);
        Map<String, List<String>> duplicateGroups = groupDuplicates(fileHashes, threshold);
        for (Map.Entry<String, List<String>> entry : duplicateGroups.entrySet()) {
            if (entry.getValue().contains(fileName)) {
                return new ArrayList<>(entry.getValue());
            }
        }
        return Collections.emptyList();
    }

    // Render specified page (0-based) of a PDF to a BufferedImage using PDFBox
    private static BufferedImage renderPdfPage(File pdfFile, int pageIndex) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            // render at 150 DPI to get decent fidelity
            return renderer.renderImageWithDPI(pageIndex, 150, ImageType.RGB);
        }
    }

    // Compute a simple 64-bit average hash (aHash) by resizing to 8x8 grayscale
    private static long averageHash64(BufferedImage img) {
        BufferedImage resized = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, 8, 8, null);
        g.dispose();

        int[] pixels = new int[64];
        resized.getRaster().getPixels(0, 0, 8, 8, pixels);
        long sum = 0L;
        for (int p : pixels) sum += p;
        int avg = (int) (sum / pixels.length);

        long hash = 0L;
        for (int i = 0; i < pixels.length; i++) {
            if (pixels[i] > avg) {
                hash |= (1L << i);
            }
        }
        return hash;
    }

    // Keep existing MD5 helper in case you want exact comparisons
    private static String md5OfFile(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (var is = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }
        byte[] digest = md.digest();
        return toHex(digest);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Compute region hashes (top, middle, bottom) for a PDF file's first page.
     * Returns a map: region name -> hash.
     */
    public static Map<String, Long> computeRegionHashes(File pdfFile) throws IOException {
        BufferedImage img = renderPdfPage(pdfFile, 0);
        int height = img.getHeight();
        int width = img.getWidth();
        int regionHeight = height / 3;
        Map<String, Long> regionHashes = new LinkedHashMap<>();
        // Top
        BufferedImage top = img.getSubimage(0, 0, width, regionHeight);
        regionHashes.put("top", averageHash64(top));
        // Middle
        BufferedImage middle = img.getSubimage(0, regionHeight, width, regionHeight);
        regionHashes.put("middle", averageHash64(middle));
        // Bottom (may be slightly taller if height not divisible by 3)
        int bottomY = regionHeight * 2;
        int bottomHeight = height - bottomY;
        BufferedImage bottom = img.getSubimage(0, bottomY, width, bottomHeight);
        regionHashes.put("bottom", averageHash64(bottom));
        return regionHashes;
    }

    /**
     * Compute region hashes for all PDFs in a folder. Returns map: file name -> region hashes.
     */
    public static Map<String, Map<String, Long>> computeAllRegionHashes(String folderPath) {
        File[] files = new File(folderPath)
                .listFiles((d, n) -> n.toLowerCase().endsWith(".pdf"));
        Map<String, Map<String, Long>> allRegionHashes = new LinkedHashMap<>();
        if (files == null) return allRegionHashes;
        for (File pdf : files) {
            try {
                allRegionHashes.put(pdf.getName(), computeRegionHashes(pdf));
            } catch (Exception e) {
                System.err.println("Failed to process " + pdf.getName() + ": " + e.getMessage());
            }
        }
        return allRegionHashes;
    }

    /**
     * Compare region hashes between two files. Returns map: region -> Hamming distance.
     */
    public static Map<String, Integer> compareRegionHashes(Map<String, Long> hashA, Map<String, Long> hashB) {
        Map<String, Integer> distances = new LinkedHashMap<>();
        for (String region : hashA.keySet()) {
            Long ha = hashA.get(region);
            Long hb = hashB.get(region);
            if (ha != null && hb != null) {
                distances.put(region, Long.bitCount(ha ^ hb));
            } else {
                distances.put(region, -1); // -1 means missing region
            }
        }
        return distances;
    }

    /**
     * Group files by region-wise match: all three regions must match (distance 0) to be considered duplicates.
     * Returns a list of groups, each group is a list of file names (duplicates).
     */
    public static List<List<String>> groupRegionWiseDuplicates(Map<String, Map<String, Long>> allRegionHashes) {
        List<String> files = new ArrayList<>(allRegionHashes.keySet());
        boolean[] seen = new boolean[files.size()];
        List<List<String>> groups = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            if (seen[i]) continue;
            String a = files.get(i);
            Map<String, Long> ha = allRegionHashes.get(a);
            if (ha == null) continue;
            List<String> group = new ArrayList<>();
            group.add(a);
            seen[i] = true;
            for (int j = i + 1; j < files.size(); j++) {
                if (seen[j]) continue;
                String b = files.get(j);
                Map<String, Long> hb = allRegionHashes.get(b);
                if (hb == null) continue;
                Map<String, Integer> dists = compareRegionHashes(ha, hb);
                boolean allZero = dists.values().stream().allMatch(dist -> dist == 0);
                if (allZero) {
                    group.add(b);
                    seen[j] = true;
                }
            }
            groups.add(group);
        }
        return groups;
    }

    public static void main(String[] args) throws Exception {
        String folderPath = "/media/sterling/2FCC-1E53/Remove_Duplicates/2021-01";
        File folder = new File(folderPath);
        File parentDir = folder.getParentFile();
        String outputDir = new File(parentDir, "distinct_files").getAbsolutePath();
        java.nio.file.Path outputPath = java.nio.file.Paths.get(outputDir);
        if (!java.nio.file.Files.exists(outputPath)) {
            java.nio.file.Files.createDirectory(outputPath);
        }
        Map<String, Map<String, Long>> allRegionHashes = computeAllRegionHashes(folderPath);
        List<List<String>> groups = groupRegionWiseDuplicates(allRegionHashes);
        System.out.println("Region-wise duplicate groups (all regions must match):\n");
        int groupNum = 1;
        for (List<String> group : groups) {
            if (group.size() > 1) {
                System.out.println("Group " + groupNum + " (" + group.size() + "): " + group);
            }
            groupNum++;
        }
        System.out.println("\nCopying distinct files to '" + outputDir + "'...");
        Set<String> copied = new HashSet<>();
        for (List<String> group : groups) {
            String fileToCopy = group.get(0); // Copy only the first file in each group
            if (copied.contains(fileToCopy)) continue;
            java.nio.file.Path src = java.nio.file.Paths.get(folderPath, fileToCopy);
            java.nio.file.Path dest = outputPath.resolve(fileToCopy);
            java.nio.file.Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            copied.add(fileToCopy);
        }
        System.out.println("Copied files:");
        copied.forEach(f -> System.out.println("  " + f));
    }
}
