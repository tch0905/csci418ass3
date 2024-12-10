import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public class MyDedup {

    // Metadata and statistics
    private static Map<String, Integer> fingerprintIndex = new HashMap<>();
    private static Map<String, List<String>> fileRecipes = new HashMap<>();
    private static int totalFiles = 0, totalChunks = 0, uniqueChunks = 0, totalContainers = 0;
    private static long totalBytes = 0, uniqueBytes = 0;

    private static final int CONTAINER_SIZE = 1 * 1024 * 1024; // 1 MiB

    public static void main(String[] args) throws Exception {

        String operation = args[0];


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
                System.out.println("Usage: java MyDedup <upload/download><min_chunk> <avg_chunk> <max_chunk>  <file_path> ");
                return;
            }

            loadMetadata();

            upload(filePath, minChunk, avgChunk, maxChunk);
        } else if (operation.equals("download")) {
            if (args.length < 3) {
                System.out.println("Usage: java MyDedup download <file_to_download> <local_file_name>");
                return;

            }

            // load the data from the index
            loadMetadata();

            // TODO: download

        } else {
            System.out.println("Invalid operation. Use 'upload' or 'download'.");
        }

        // Save metadata
        saveMetadata();
    }

    private static void upload(String filePath, int minChunk, int avgChunk, int maxChunk) throws Exception {
        File file = new File(filePath);

        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + filePath);
        }

        byte[] fileData = Files.readAllBytes(file.toPath());

        List<String> fileChunks = new ArrayList<>();
        ByteArrayOutputStream containerBuffer = new ByteArrayOutputStream();

        int start = 0;
        while (start < fileData.length) {
            int chunkSize = findNextChunk(fileData, start, minChunk, avgChunk, maxChunk);
            byte[] chunk = Arrays.copyOfRange(fileData, start, start + chunkSize);
            String fingerprint = getMD5(chunk);

            // Deduplication: Add only unique chunks
            if (!fingerprintIndex.containsKey(fingerprint)) {
                fingerprintIndex.put(fingerprint, chunkSize); // Store only the fingerprint and chunk size
                uniqueChunks++;
                uniqueBytes += chunk.length;

                // Add to container
                if (containerBuffer.size() + chunk.length > CONTAINER_SIZE) {
                    flushContainer(containerBuffer);
                }
                containerBuffer.write(chunk);
            }

            fileChunks.add(fingerprint);
            totalChunks++;
            totalBytes += chunk.length;
            start += chunkSize;
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

    private static void download(String filePath) throws Exception {
        List<String> fileChunks = fileRecipes.get(filePath);
        if (fileChunks == null) {
            throw new FileNotFoundException("File not found in metadata: " + filePath);
        }

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            for (String fingerprint : fileChunks) {
                fos.write(fingerprintIndex.get(fingerprint));
            }
        }

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

    private static void loadMetadata() throws IOException {
        Path metadataPath = Paths.get("./data/mydedup.index");

        // Check if the metadata file exists before attempting to read
        if (Files.exists(metadataPath)) {
            try (BufferedReader reader = Files.newBufferedReader(metadataPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        String fingerprint = parts[0];
                        int chunkSize = Integer.parseInt(parts[1]);

                        // Store only the fingerprint and its size
                        fingerprintIndex.put(fingerprint, chunkSize);
                    }
                }
                System.out.println("Metadata file loaded from: " + metadataPath);
            } catch (NumberFormatException e) {
                System.err.println("Error parsing chunk size from metadata: " + e.getMessage());
            }
        } else {
            System.out.println("Metadata file does not exist: " + metadataPath);
        }

        // Update totalContainers by scanning the ./data/ directory
        updateContainerCount();
    }
    private static void updateContainerCount() throws IOException {
        Path dataDir = Paths.get("./data/");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir, "container_*.bin")) {
            int maxContainerNum = 0;
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                // Extract the number from the filename
                String numberPart = fileName.replace("container_", "").replace(".bin", "");
                try {
                    int containerNum = Integer.parseInt(numberPart);
                    maxContainerNum = Math.max(maxContainerNum, containerNum);
                } catch (NumberFormatException e) {
                    System.err.println("Error parsing container number from file: " + fileName);
                }
            }
            totalContainers = maxContainerNum+1; // Update the total container count
        } catch (IOException e) {
            System.err.println("Error reading the data directory: " + e.getMessage());
        }
    }

    private static void saveMetadata() throws IOException {
        // Define the file path for the metadata
        Path metadataPath = Paths.get("./data/mydedup.index");
        Files.createDirectories(metadataPath.getParent()); // Create directory if it doesn't exist

        // Use a try-with-resources statement to ensure the writer is closed properly
        try (BufferedWriter writer = Files.newBufferedWriter(metadataPath)) {
            for (Map.Entry<String, Integer> entry : fingerprintIndex.entrySet()) {
                String fingerprint = entry.getKey();
                int chunkSize = entry.getValue();
                String metadataLine = String.format("%s,%d%n", fingerprint, chunkSize);
                writer.write(metadataLine);
            }
        }
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