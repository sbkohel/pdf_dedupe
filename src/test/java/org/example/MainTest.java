package org.example;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @Test
    void testFindDuplicatesForFile_existingDuplicate() {
        String folderPath = "/home/sterling/Downloads/FGG_test";
        String fileName = "2025-06-25-Buy_Sheet_.pdf";
        int threshold = 8;
        List<String> duplicates = Main.findDuplicatesForFile(folderPath, fileName, threshold);
        assertTrue(duplicates.contains(fileName));
        // Optionally print duplicates for manual inspection
        System.out.println("Duplicates for '" + fileName + "': " + duplicates);
        // If you know the expected duplicates, you can check for them:
        // assertTrue(duplicates.contains("other_duplicate.pdf"));
        // assertEquals(expectedCount, duplicates.size());
    }

    @Test
    void testFindDuplicatesForFile_noDuplicate() {
        String folderPath = "/home/sterling/Downloads/FGG_test";
        String fileName = "unique.pdf"; // Replace with a real unique file name in your test folder
        int threshold = 8;
        List<String> duplicates = Main.findDuplicatesForFile(folderPath, fileName, threshold);
        // Should be empty if the file is not in any duplicate group
        assertTrue(duplicates.isEmpty());
    }

    @Test
    void testFindDuplicatesForFile_nonexistentFile() {
        String folderPath = "/home/sterling/Downloads/FGG_test";
        String fileName = "does_not_exist.pdf";
        int threshold = 8;
        List<String> duplicates = Main.findDuplicatesForFile(folderPath, fileName, threshold);
        assertTrue(duplicates.isEmpty());
    }

    @Test
    void printAllFileHashes() {
        String folderPath = "/home/sterling/Downloads/FGG_test";
        var fileHashes = Main.computePdfHashes(folderPath);
        System.out.println("File name and perceptual hash (in hex):");
        for (var entry : fileHashes.entrySet()) {
            System.out.printf("%s : %016x\n", entry.getKey(), entry.getValue());
        }
        System.out.println("\nPairwise Hamming distances:");
        var files = new java.util.ArrayList<>(fileHashes.keySet());
        for (int i = 0; i < files.size(); i++) {
            for (int j = i + 1; j < files.size(); j++) {
                String f1 = files.get(i);
                String f2 = files.get(j);
                long h1 = fileHashes.get(f1);
                long h2 = fileHashes.get(f2);
                int dist = Long.bitCount(h1 ^ h2);
                System.out.printf("%s <-> %s : %d\n", f1, f2, dist);
            }
        }
    }

    @Test
    void printAllFileHashesForBuySheet() {
        String folderPath = "/home/sterling/Downloads/FGG_test";
        String targetFile = "2025-06-25-Buy_Sheet_(8).pdf";
        var allRegionHashes = Main.computeAllRegionHashes(folderPath);
        Map<String, Long> targetRegionHashes = allRegionHashes.get(targetFile);
        if (targetRegionHashes == null) {
            System.out.println("Target file not found: " + targetFile);
            return;
        }
        System.out.println("Comparing '" + targetFile + "' to all other files (sorted by file name), region-wise Hamming distances:");
        allRegionHashes.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(targetFile))
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> {
                String file = entry.getKey();
                Map<String, Long> regionHashes = entry.getValue();
                Map<String, Integer> distances = Main.compareRegionHashes(targetRegionHashes, regionHashes);
                System.out.printf("%s <-> %s : top=%d, middle=%d, bottom=%d\n",
                    targetFile, file,
                    distances.getOrDefault("top", -1),
                    distances.getOrDefault("middle", -1),
                    distances.getOrDefault("bottom", -1));
            });
    }
}
