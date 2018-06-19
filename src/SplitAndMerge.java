import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

public class SplitAndMerge {

	
	public void splitFile(String fileName, float partSizeInMB) {
		
		File inputFile = new File(fileName);
		FileInputStream inputStream;
		String newFileName;
		FileOutputStream filePart;
		int fileSize = (int) inputFile.length();
		int nChunks = 0;
		int read = 0;
		int readLength = (int) (partSizeInMB * 1024 * 1024);
		byte[] byteChunkPart;
		try {
			inputStream = new FileInputStream(inputFile);
			while (fileSize > 0) {
				if (fileSize <= readLength) {
					readLength = fileSize;
				}
				byteChunkPart = new byte[readLength];
				read = inputStream.read(byteChunkPart, 0, readLength);
				fileSize -= read;
				assert (read == byteChunkPart.length);
				nChunks++;
				newFileName = fileName + ".part" + Integer.toString(nChunks - 1);
				filePart = new FileOutputStream(new File(newFileName));
				filePart.write(byteChunkPart);
				filePart.flush();
				filePart.close();
				byteChunkPart = null;
				filePart = null;
			}
			inputStream.close();
		} catch (IOException exception) {
			exception.printStackTrace();
		}
	}
	
	public void mergeFile(String dir, String fileStart) {
		File ofile = new File(dir+"/"+fileStart);
		FileOutputStream fos;
		FileInputStream fis;
		byte[] fileBytes;
		int bytesRead = 0;
		String[] files = new File(dir).list((path, name) -> Pattern.matches(fileStart+Pattern.quote(".")+"part.*", name));
		Arrays.sort(files);
		try {
		    fos = new FileOutputStream(ofile,false);
		    for (String fileName : files) {
		    	File f = new File(dir+"/"+fileName);
		    	System.out.println("Merging "+f.getAbsolutePath());
		        fis = new FileInputStream(f);
		        fileBytes = new byte[(int) f.length()];
		        bytesRead = fis.read(fileBytes, 0,(int)  f.length());
		        assert(bytesRead == fileBytes.length);
		        assert(bytesRead == (int) f.length());
		        fos.write(fileBytes);
		        fos.flush();
		        fileBytes = null;
		        fis.close();
		        fis = null;
		    }
		    fos.close();
		    fos = null;
		}catch (Exception exception){
			exception.printStackTrace();
		}
	}
}
