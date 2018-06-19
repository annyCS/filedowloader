import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;

public class FileDownloader {

	public static final String DOWNLOADS_PATH			= "./downloads/";
	public static final String DOWNLOAD_FILE			= "./downloads/download_file";
	public static final int MAX_DOWNLOADS_CONCURRENTS	= 5;
	
	private List<Thread> threads;
	private Queue<String> downloadsList;
	private List<String> partsFile;
	private CyclicBarrier barrier;
	
	
	public FileDownloader(int maxths) {
		// threads del programa
		threads = new ArrayList<Thread>(maxths);
		// Creamos los hilo para las descargas
		for (int i = 0; i < maxths; i++) {
			threads.add(new Thread( () -> downloadFiles(), "ID-"+(i+1) ));
		}
				
		// lista con las lineas del fichero de texto leido
		downloadsList = new ConcurrentLinkedQueue<String>();
		// lista con las partes a eliminar
		partsFile = new ArrayList<>();
		
		// creamos el directorio donde se almacenaran los archivos descargados
		createFolder(DOWNLOADS_PATH);
		
		barrier = new CyclicBarrier(maxths, new Runnable(){
			public void run() {
				// Unimos los ficheros descargados
				SplitAndMerge sm = new SplitAndMerge();
				sm.mergeFile(DOWNLOADS_PATH, "nameFile");
				
				// eliminamos las partes del fichero
				System.out.println("Descargado!");
			}
		});
	}

	
	
	public void process(String downloadsFile) {
		
		// Descargamos el fichero de texto con informacion de los dicheros a descargar
		downloadByURL(downloadsFile, DOWNLOAD_FILE);
		
		// Leemos el fichero de texto descargado
		try {
			readTextFile();
		} catch (IOException e) {
			System.err.println("Error when trying to read from the file: " + e.getMessage());
		}
		
		for (Thread th: threads) {
			th.start();
		}
		
		for (Thread th: threads) {
			try {
				th.join();
			} catch (InterruptedException e) {}
		}
	}
	
	
	
	private void readTextFile() throws IOException {
		
		File file = new File(DOWNLOAD_FILE);
		
		if ( file.exists() ) {
			try {
				// leer el fichero
				BufferedReader br = new BufferedReader(new FileReader(file));
				String line = "";
				
				while( (line = br.readLine()) != null ) {
					downloadsList.add(line);
				}
				
				br.close();
				
			} catch (FileNotFoundException e) {
				System.err.println("Specified path does not exist: " + e.getMessage());
			}
		}
		
		//deleteFile(DOWNLOAD_FILE);
	}
	

	public void downloadFiles() {

		while( !downloadsList.isEmpty() ) {
			try {
				String linkFilePart = downloadsList.poll();
				
				System.out.println(linkFilePart); //
				
				if( !linkFilePart.contains("Fichero") ) {
					downloadByURL(linkFilePart, DOWNLOADS_PATH);
					
				} else {
					barrier.await();
				}
			}
			catch (InterruptedException e) {}
			catch (BrokenBarrierException e) {}
		}
	}
	
	
	
	private void downloadByURL(String url, String destinationPath) {
		try {
			
			URL website = new URL(url);
			InputStream in = website.openStream();
			Path pathOut = Paths.get(destinationPath);
			Files.copy(in, pathOut, StandardCopyOption.REPLACE_EXISTING);
			in.close();
			
		} catch (MalformedURLException e) {
			System.err.println("URL not exists or not support: " + e.getMessage());
		}
		catch (IOException e) {
			System.err.println("Specified path does not exist: " + e.getMessage());
		}
	}
	
	

	private void createFolder(String path) {
		File folder = new File(path);
		
		if ( !folder.exists() || !folder.isDirectory()) {
			folder.mkdir();
		}
	}
	
	
	private void deleteFile(String filePath) {
		try {
			Files.deleteIfExists(Paths.get(filePath));
		} catch (IOException e) {
			System.err.println("The file could not be removed: " + e.getMessage());
		}
	}
	
	
	public static void main(String[] args) throws IOException {
		String downloadFile = "https://github.com/jesussanchezoro/PracticaPC/raw/master/descargas.txt";
		FileDownloader fd = new FileDownloader(5);
		fd.process(downloadFile);
	}
}
