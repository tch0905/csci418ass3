import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

class FingerprintInfo {
    String fingerprint;
    int containerNumber;
    int start;
    int offset;
    int referencingFiles; // New field to track how many files reference this fingerprint

    public FingerprintInfo(String fingerprint, int containerNumber, int start, int offset) {
        this.fingerprint = fingerprint;
        this.containerNumber = containerNumber;
        this.start = start;
        this.offset = offset;
        this.referencingFiles = 0; // Initialize to 0 when created
    }

    // Method to increment the referencing file count
    public void incrementReferencingFiles() {
        this.referencingFiles++;
    }
}

public class MyDedup {

    // Metadata and statistics
    private static Map<String, FingerprintInfo> fingerprintIndex = new HashMap<>();
    private static Map<String, List<String>> fileRecipes = new HashMap<>();
    private static int totalFiles = 0, totalChunks = 0, uniqueChunks = 0, totalContainers = 0;
    private static long totalBytes = 0, uniqueBytes = 0;

    private static final int CONTAINER_SIZE = 1 * 1024 * 1024; // 1 MiB

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println("Usage: java MyDedup <upload/download> <min_chunk> <avg_chunk> <max_chunk> <file_path> ");
            return;
        }

        String operation = args[0];

        loadStatistics();
        loadMetadata();
        loadFileRecipes();

        if (operation.equals("upload")) {
            String filePath = args[4];
            int minChunk = Integer.parseInt(args[1]);
            int avgChunk = Integer.parseInt(args[2]);
            int maxChunk = Integer.parseInt(args[3]);

            // Validate chunk sizes
            if (!isPowerOfTwo(minChunk) || !isPowerOfTwo(avgChunk) || !isPowerOfTwo(maxChunk)) {
                throw new IllegalArgumentException("Chunk sizes must be powers of 2.");
            }


            if (args.length < 5) {
                System.out.println("Usage: java MyDedup <upload/download> <min_chunk> <avg_chunk> <max_chunk> <file_path> ");
                return;
            }


            upload(filePath, minChunk, avgChunk, maxChunk);
        } else if (operation.equals("download")) {
            if (args.length < 3) {
                System.out.println("Usage: java MyDedup download <file_to_download> <local_file_name>");
                return;
            }

            String filePath = args[1];
            String localFilePath = args[2];
            System.out.println("Filepath: " + filePath + "," + localFilePath);


            download(filePath, localFilePath);

        } else if (operation.equals("delete")) {
            if (args.length < 2) {
                System.out.println("Usage: java MyDedup delete <file_to_delete>");
                return;
            }

            // delete(args[1]);
        } 
        else {
            System.out.println("Invalid operation. Use 'upload' or 'download' or 'delete'.");
            return;
        }

        // Save metadata
        saveMetadata();
        saveStatistics();
        saveFileRecipes();
    }

    private static void upload(String filePath, int minChunk, int avgChunk, int maxChunk) throws Exception {
        File file = new File(filePath);

        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + filePath);
        }

        // Check if the file path is already in fileRecipes
        if (fileRecipes.containsKey(filePath)) {
            System.out.println("The file has already been uploaded; skipping update.");
            return; // Skip the upload
        }

        Set<String> fileListFingerprints = loadFileListFingerprints();
        byte[] fileData = Files.readAllBytes(file.toPath());

        int start = 0;
        boolean haveUniqueChunk = false;
        List<String> fileChunks = new ArrayList<>();
        ByteArrayOutputStream containerBuffer = new ByteArrayOutputStream();

        while (start < fileData.length) {
            System.out.println("Anchor: " + start);
            int chunkSize = findNextChunk(fileData, start, minChunk, avgChunk, maxChunk);
            byte[] chunk = Arrays.copyOfRange(fileData, start, start + chunkSize);
            String fingerprint = getMD5(chunk);

            // Deduplication: Add only unique chunks
            if (!fingerprintIndex.containsKey(fingerprint)) {
                haveUniqueChunk = true;
                // Store the fingerprint along with container number, start, and offset
                fingerprintIndex.put(fingerprint, new FingerprintInfo(fingerprint, totalContainers, containerBuffer.size(), chunkSize));
                uniqueChunks++;
                uniqueBytes += chunk.length;
                containerBuffer.write(chunk);
            }

            // Add to container
            if (containerBuffer.size() + chunk.length > CONTAINER_SIZE) {
                flushContainer(containerBuffer);
            }


            fileChunks.add(fingerprint);
            totalChunks++;
            totalBytes += chunk.length;
            start += chunkSize;
        }

        // Check if the fileChunks are already in the file list
        // dropped
//        if (!haveUniqueChunk && fileChunks.stream().allMatch(fileListFingerprints::contains)) {
//            System.out.println("The file has already been uploaded; skipping update.");
//            return; // Skip the upload
//        }

        // If any chunk was unique, we need to check if any chunk was duplicated
        for (String fingerprint : fileChunks) {
            if (fingerprintIndex.containsKey(fingerprint)) {
                fingerprintIndex.get(fingerprint).incrementReferencingFiles();
            }
        }

        // Flush remaining chunks to a container
        if (containerBuffer.size() > 0) {
            flushContainer(containerBuffer);
        }

        // Update metadata
        fileRecipes.put(filePath, fileChunks);
        totalFiles++;


        // Print statistics
        printStatistics();
    }

    private static void download(String filePath, String localFilePath) throws Exception {
        List<String> fileChunks = fileRecipes.get(filePath);
        // System.out.println("File Chunks: " + fileChunks);
        if (fileChunks == null) {
            throw new FileNotFoundException("File not found in metadata: " + filePath);
        }

        ByteArrayOutputStream originalFileArray = new ByteArrayOutputStream();
        
        for (String fingerprint : fileChunks) {
            FingerprintInfo info = fingerprintIndex.get(fingerprint);
            if (info == null) {
                throw new FileNotFoundException("Fingerprint not found in metadata: " + fingerprint);
            }

            int containerNumber = info.containerNumber;
            int start = info.start;
            int offset = info.offset;

            FileInputStream containerInputStream = new FileInputStream(String.format("./data/container_%d.bin", containerNumber));
            
            containerInputStream.skip(start);
            byte[] containerData = new byte[offset];
            containerInputStream.read(containerData);
            originalFileArray.write(containerData);
            
            containerInputStream.close();
        }

        if (!Files.exists(Paths.get(localFilePath))) {
            Files.createDirectories(Paths.get(localFilePath).getParent());
        }

        Files.write(Paths.get(localFilePath), originalFileArray.toByteArray());
        // FileOutputStream newFile = new FileOutputStream(localFilePath);
        // originalFileArray.writeTo(newFile);
        // newFile.close();

        System.out.println("File downloaded successfully: " + filePath);
    }

    private static int findNextChunk(byte[] data, int start, int minChunk, int avgChunk, int maxChunk) {

        int end =  Math.min(start + minChunk, data.length);; // Limit the chunk size to `maxChunk`
        int anchorMask = avgChunk - 1; // Anchor point mask

        int d = 257; // Multiplier for Rabin fingerprint
        int q = avgChunk; // A large prime modulus to avoid overflow
        int m = minChunk;
        int p = 0;
        for (int s = start; s - start < maxChunk; s++) {

            if (s+m > data.length-1){
                break;
            }

            if (s == start){
                int sum = 0;
                for (int i = 0; i < m; i++){
                    sum += data[s+i] * (int) Math.pow(d,m-i-1);
                }
                p = sum % q;

            } else {
                p = Math.floorMod(d * (p - (int) Math.pow(d, m  - 1) * data[s])+ data[s+m], q);
            }

            if ((p & anchorMask) == anchorMask) {
                end = s + m;
                break;
            }
        }


        return end - start;
    }


    private static String getMD5(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static void flushContainer(ByteArrayOutputStream containerBuffer) throws IOException {
        // Save the container contents to a binary file
        saveContainer(containerBuffer);

        // Reset the container buffer for future chunks
        totalContainers++;
        containerBuffer.reset();
    }

    private static void saveContainer(ByteArrayOutputStream containerBuffer) throws IOException {
        // Create a unique filename for each container if needed
        String filename = String.format("./data/container_%d.bin", totalContainers);
        Path outputPath = Paths.get(filename);
        Files.createDirectories(outputPath.getParent()); // Create directory if it doesn't exist
        try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
            containerBuffer.writeTo(outputStream);
        }
    }

    private static boolean isPowerOfTwo(int n) {
        return (n > 0) && ((n & (n - 1)) == 0);
    }


    private static void updateContainerCount() throws IOException {
        Path dataDir = Paths.get("./data/");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir, "container_*.bin")) {
            int maxContainerNum = 0;
            boolean hasContainers = false; // Flag to check if any containers are found

            for (Path entry : stream) {
                hasContainers = true; // At least one container file found
                String fileName = entry.getFileName().toString();
                // Extract the number from the filename
                String numberPart = fileName.replace("container_", "").replace(".bin", "");
                try {
                    int containerNum = Integer.parseInt(numberPart);
                    maxContainerNum = Math.max(maxContainerNum, containerNum);
                } catch (NumberFormatException e) {
//                    System.err.println("Error parsing container number from file: " + fileName);
                }
            }

            // Update the total container count; if no containers were found, it remains 0
            totalContainers = hasContainers ? maxContainerNum + 1 : 0;
        } catch (IOException e) {
//            System.err.println("Error reading the data directory: " + e.getMessage());
            totalContainers = 0; // Set to 0 in case of an error
        }
    }

    private static void loadMetadata() throws IOException {
        Path metadataPath = Paths.get("./data/mydedup.index");

        if (Files.exists(metadataPath)) {
            try (BufferedReader reader = Files.newBufferedReader(metadataPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 5) { // Expecting 5 parts now
                        String fingerprint = parts[0];
                        int containerNumber = Integer.parseInt(parts[1]);
                        int start = Integer.parseInt(parts[2]);
                        int offset = Integer.parseInt(parts[3]);
                        int referencingFiles = Integer.parseInt(parts[4]); // New field

                        // Store the fingerprint info with the referencingFiles count
                        FingerprintInfo info = new FingerprintInfo(fingerprint, containerNumber, start, offset);
                        info.referencingFiles = referencingFiles; // Set the referencingFiles count
                        fingerprintIndex.put(fingerprint, info);
                    }
                }
//                System.out.println("Metadata file loaded from: " + metadataPath);
            } catch (NumberFormatException e) {
//                System.err.println("Error parsing metadata from file: " + e.getMessage());
            }
        } else {
//            System.out.println("Metadata file does not exist: " + metadataPath);
        }

        // Update totalContainers by scanning the ./data/ directory
        updateContainerCount();
    }

    private static void saveMetadata() throws IOException {
        Path metadataPath = Paths.get("./data/mydedup.index");
        Files.createDirectories(metadataPath.getParent()); // Create directory if it doesn't exist

        try (BufferedWriter writer = Files.newBufferedWriter(metadataPath)) {
            for (FingerprintInfo info : fingerprintIndex.values()) {
                // Update the format to include the referencingFiles count
                String metadataLine = String.format("%s,%d,%d,%d,%d%n",
                        info.fingerprint,
                        info.containerNumber,
                        info.start,
                        info.offset,
                        info.referencingFiles); // Include referencingFiles
                writer.write(metadataLine);
            }
        }
    }

    private static void saveStatistics() throws IOException {
        Path statsPath = Paths.get("./data/stat.index");
        Files.createDirectories(statsPath.getParent()); // Create directory if it doesn't exist

        try (BufferedWriter writer = Files.newBufferedWriter(statsPath)) {
            String statsLine = String.format("%d,%d,%d,%d,%d,%d%n",
                    totalFiles,
                    totalChunks,
                    uniqueChunks,
                    totalContainers,
                    totalBytes,
                    uniqueBytes);
            writer.write(statsLine);
        }
    }

    private static void loadStatistics() throws IOException {
        Path statsPath = Paths.get("./data/stat.index");

        if (Files.exists(statsPath)) {
            try (BufferedReader reader = Files.newBufferedReader(statsPath)) {
                String line = reader.readLine();
                if (line != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 6) {
                        totalFiles = Integer.parseInt(parts[0]);
                        totalChunks = Integer.parseInt(parts[1]);
                        uniqueChunks = Integer.parseInt(parts[2]);
                        totalContainers = Integer.parseInt(parts[3]);
                        totalBytes = Long.parseLong(parts[4]);
                        uniqueBytes = Long.parseLong(parts[5]);
                    }
                }
//                System.out.println("Statistics loaded from: " + statsPath);
            } catch (NumberFormatException e) {
//                System.err.println("Error parsing statistics from file: " + e.getMessage());
            }
        } else {
//            System.out.println("Statistics file does not exist: " + statsPath);
        }
    }
    private static void loadFileRecipes() throws IOException {
        Path fileListPath = Paths.get("./data/file_list.index");

        if (Files.exists(fileListPath)) {
            try (BufferedReader reader = Files.newBufferedReader(fileListPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", 2); // Split into two parts
                    if (parts.length == 2) {
                        String filePath = parts[0];
                        List<String> fingerprints = Arrays.asList(parts[1].split(","));
                        // Store the file path and associated fingerprints
                        fileRecipes.put(filePath, fingerprints);
                    }
                }
//                System.out.println("File recipes loaded from: " + fileListPath);
            }
        } else {
//            System.out.println("File recipes file does not exist: " + fileListPath);
        }
    }

    private static void saveFileRecipes() throws IOException {
        Path fileListPath = Paths.get("./data/file_list.index");
        Files.createDirectories(fileListPath.getParent()); // Ensure the directory exists

        try (BufferedWriter writer = Files.newBufferedWriter(fileListPath)) {
            for (Map.Entry<String, List<String>> entry : fileRecipes.entrySet()) {
                String filePath = entry.getKey();
                List<String> fingerprints = entry.getValue();
                // Write the filePath followed by the fingerprints
                String line = String.format("%s,%s%n", filePath, String.join(",", fingerprints));
                writer.write(line);
            }
        }
    }

    private static Set<String> loadFileListFingerprints() throws IOException {
        Set<String> fileListFingerprints = new HashSet<>();
        Path fileListPath = Paths.get("./data/file_list.index");

        if (Files.exists(fileListPath)) {
            try (BufferedReader reader = Files.newBufferedReader(fileListPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length > 1) {
                        // Add all fingerprints to the set, skipping the fileId
                        for (int i = 1; i < parts.length; i++) {
                            fileListFingerprints.add(parts[i]);
                        }
                    }
                }
            } catch (IOException e) {
//                System.err.println("Error reading file list: " + e.getMessage());
            }
        }
        return fileListFingerprints;
    }

    private static void printStatistics() {
        double deduplicationRatio = totalBytes / (double) uniqueBytes;
        System.out.printf("Total files: %d\n", totalFiles);
        System.out.printf("Total pre-deduplicated chunks: %d\n", totalChunks);
        System.out.printf("Total unique chunks: %d\n", uniqueChunks);
        System.out.printf("Total bytes (pre-deduplicated): %d\n", totalBytes);
        System.out.printf("Total bytes (unique): %d\n", uniqueBytes);
        System.out.printf("Total containers: %d\n", totalContainers);
        System.out.printf("Deduplication ratio: %.2f\n", deduplicationRatio);
    }
}