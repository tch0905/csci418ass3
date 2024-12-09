import java.io.*;
import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MyDedup {

    private static final int CONTAINER_SIZE = 1 * 1024 * 1024;


    public static void upload(){

    }

    public static void delete(){

    }

    public static List<Integer> computeBoundaries(byte[] input, int m, int d, int q, int max) {

        List<Integer> pList = new ArrayList<>();
        List<Integer> boundaries = new ArrayList<>();

        boundaries.add(0);

        int mark = q-1;

        int prev = 0;


        for (int s = 0, count = 0; s < input.length; s++, count++) {

            if(count == max){
                boundaries.add(s);
                count = -m;
            }
            if (s+m > input.length-1){
                break;
            }
            if (s==0){
                int sum = 0;
                for (int i = 0; i < m; i++){
                    sum += input[i] * (int) Math.pow(d,m-i-1);
                }
                prev = sum % q;
                pList.add(sum % q);
            }
            else {
                int temp = Math.floorMod(d * (prev - (int) Math.pow(d, m  - 1) * input[s])+ input[s+m], q);

                pList.add(temp);
                prev = temp;

            }
            // If the prev is equal to mark and our count can't less than zero to fulfill min-size
            if ((prev & mark) == mark && count >= 0) {
                boundaries.add(s);
            }
        }


        return boundaries;
    }

    private static void splitFile(byte[] data, List<Integer> boundaries) throws IOException {
        for (int i = 0; i < boundaries.size() - 1; i++) {
            int start = boundaries.get(i);
            int end = boundaries.get(i + 1);
            byte[] segment = Arrays.copyOfRange(data, start, end);

            File outputFile = new File("segment_" + i + ".bin");
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                outputStream.write(segment);
                System.out.println("Successfully wrote segment " + i + " to " + outputFile.getPath());
            } catch (IOException e) {
                System.err.println("Error writing segment " + i + ": " + e.getMessage());
            }
        }
    }



    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {


        String command = args[0];

        if ((!command.equals("download") ) && (!command.equals("upload"))) {
            System.out.println("Invalid command. Please use 'download' or 'upload'.");
            return;
        }


        if (command.equals("download")){
            if (args.length < 3) {
                System.out.println("Usage: java MyDedup download <file_to_download> <local_file_name>");
                return;
            }


            String fileToDownload = args[1];
            String localFileName = args[2];
            System.out.println("Downloading file: " + fileToDownload + " to " + localFileName);

        }
        else if(command.equals("upload")){

            if (args.length < 5) {
                System.out.println("Usage: java MyDedup upload <min_chunk> <avg_chunk> <max_chunk> <file_to_upload>");
                return;
            }

            String minChunk = args[1];
            String avgChunk = args[2];
            String maxChunk = args[3];;

            int d = 257;

            File fileToUpload = new File(args[4]);
            FileInputStream file_to_upload = new FileInputStream(fileToUpload);
            byte[] data = new byte[(int)fileToUpload.length()];

            int t = file_to_upload.read(data);
            String temp = String.valueOf(file_to_upload.read(data));
            List<Integer> computeBoundaries = computeBoundaries(data,2,d,512,10);
            splitFile(data, computeBoundaries);
            System.out.print('[');
            for (int i = 0; i < computeBoundaries.size(); i++) {
                System.out.print(computeBoundaries.get(i));
                System.out.print(',');
            }
            System.out.print(']');
            file_to_upload.close();


            System.out.println(' ');


            System.out.println("Uploading file: " + fileToUpload);
            System.out.println("Minimum Chunk Size: " + minChunk);
            System.out.println("Average Chunk Size: " + avgChunk);
            System.out.println("Maximum Chunk Size: " + maxChunk);

        }



        System.out.println("Download complete.");
    }
}