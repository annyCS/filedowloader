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
import java.util.Iterator;
import java.util.List;
import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;


public class FileDownloader2 {

	public static final String DOWNLOADS_PATH			= "downloads";
	public static final String DOWNLOAD_FILE			= "downloads/downloadfile";
	public static final int MAX_DOWNLOADS_CONCURRENTS	= 5;
	
	private Deque<String> downloadsList;	// lista con las lineas del fichero de descarga
	private Deque<String> partsFile;		// lista con las partes descargadas del fichero
	private String currentDownload;			// nombre del archivo actual a descargar
	private CyclicBarrier barrier;
	private int maxThreads;
	private boolean processFinished;
	
	private List<Deque<String>> listalistas = new CopyOnWriteArrayList<Deque<String>>();
	private Deque<String> namefiles = new ConcurrentLinkedDeque<String>();
	
	
	public FileDownloader2(int maxths) {
		processFinished = false;

		maxThreads		= maxths;
		downloadsList	= new ConcurrentLinkedDeque<String>();
		partsFile		= new ConcurrentLinkedDeque<String>();
		barrier			= new CyclicBarrier(maxths, () -> downloadCompleted());
		
		
	}
	
	
	private void downloadCompleted() {	
		// unimos las partes del archivo descargado
		SplitAndMerge sm = new SplitAndMerge();
		sm.mergeFile(DOWNLOADS_PATH, currentDownload);
		
		// eliminamos las partes del fichero
		Iterator<String> it = listalistas.get(0).iterator();
		while ( it.hasNext() ) {
			deleteDownloadFile(DOWNLOADS_PATH + "/" + it.next());
		}
		
		System.out.println("\n------------------------> DOWNLOAD SUCCESSFUL!\n");
		
		if ( !listalistas.isEmpty() ) {
			listalistas.remove(0); // se elimina el primer elemento para siguiente descarga
			nextFileToDownload();
		}
		
		else {
			processFinished = true;
		}
	}

	
	/**
	 * 
	 * @param downloadsFile
	 */
	public void process(String downloadsFile) {
		createFolder();		// directorio para almacenar los archivos descargados
				
		if ( !downloadByURL(downloadsFile, DOWNLOAD_FILE) ) {	// se descarga el fichero con los enlaces a descargar
			return;
		}
		
		// lectura del fichero descargado
		try {
			readTextFile();		
		} catch (IOException e) {
			System.err.println("Error when trying to read from the file: " + e.getMessage());
			return;
		}
		
		nextFileToDownload();
		
		// creacion de los hilos de ejecucion de las descargas
		for (int i = 0; i < maxThreads; i++) {
			Thread th = new Thread(() -> {
				try {
					downloadFiles();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}, "ID-"+(i+1));
			th.start();
		}
	}
	
	
	/**
	 * 
	 * @throws IOException
	 */
	private void readTextFile() throws IOException {
		
		File file = new File(DOWNLOAD_FILE);
		
		if ( file.exists() ) {
			try {
				// leer el fichero
				BufferedReader br = new BufferedReader(new FileReader(file));
				String line = "";
				String namefile = "";
				int index = -1;
				
				// se va añadiendo cada enlace en la lista para descargas
				while( (line = br.readLine()) != null ) {
					line = line.replaceAll("\\s","");	// se "limpia" la linea de espacios, tabuladores, retornos
					
					if( line.contains("Fichero:") ) {
						
						namefile = line.substring( line.lastIndexOf(":")+1, line.length());
						namefiles.add(namefile);	// lista de nombres

						// si la linea leida contiene "FICHERO:" significa que es un archivo nuevo
						index++;
						listalistas.add(new ConcurrentLinkedDeque<String>());						
					}
					
					else {
						listalistas.get(index).add(line);
					}
				}
				
				br.close();
				
			} catch (FileNotFoundException e) {
				System.err.println("Specified path does not exist: " + e.getMessage());
			}
		}
		
		deleteDownloadFile(DOWNLOAD_FILE);
	}
	
	
	/**
	 * Extrae el siguiente archivo a descargar
	 */
	private void nextFileToDownload() {
		currentDownload = namefiles.poll();
		System.out.println("-----------------> DOWNLOADING... " + currentDownload);
	}
	
	
	/**
	 * @throws BrokenBarrierException 
	 * @throws InterruptedException 
	 * 
	 */
	public void downloadFiles() throws InterruptedException, BrokenBarrierException {

		while ( !processFinished ) {
			// mientras haya ficheros por descargar...
			while ( !namefiles.isEmpty() ) {
				
				if ( !listalistas.get(0).isEmpty() ) {
					String part = listalistas.get(0).remove();
					System.out.println("DONWLOADING... " + part);
					
					// descarga de cada una de las partes del fichero
					String link = part.substring(part.lastIndexOf("/"), part.length());
					downloadByURL(part, (DOWNLOADS_PATH + link) );
				}
				
				else {
					System.out.println("--ESPERO--");
					barrier.await();
				}
			}
		}
	}
	
	
	/**
	 * 
	 * @param url
	 * @param destinationPath
	 * @return
	 */
	private boolean downloadByURL(String url, String destinationPath) {
		
		boolean succes = true;
		
		try {
			URL website = new URL(url);
			InputStream in = website.openStream();
			Path pathOut = Paths.get(destinationPath);
			Files.copy(in, pathOut, StandardCopyOption.REPLACE_EXISTING);
			in.close();
			
		} catch (MalformedURLException e) {
			succes = false;
			System.err.println("URL not exists or not support: " + e.getMessage());
		}
		catch (IOException e) {
			succes = false;
			System.err.println("Specified path does not exist: " + e.getMessage());
		}
		
		return succes;
	}
	
	
	/**
	 * Crea la carpeta de descargas.
	 */
	private void createFolder() {
		File folder = new File(DOWNLOADS_PATH);
		
		if ( !folder.exists() || !folder.isDirectory()) {
			folder.mkdir();
		}
	}
	
	
	/**
	 * Elimina, si existe, el fichero indicado.
	 * @param filename
	 * @throws IOException 
	 */
	private void deleteDownloadFile(String filename) {
		try {
			Files.deleteIfExists(Paths.get(filename));
		} catch(IOException e) {
			System.err.println("The file could not be removed: " + e.getMessage());
		}
	}
	
	
	/**
	 * 
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		String downloadFile = "https://github.com/jesussanchezoro/PracticaPC/raw/master/descargas.txt";
		FileDownloader2 fd = new FileDownloader2(5);
		fd.process(downloadFile);
	}
}
