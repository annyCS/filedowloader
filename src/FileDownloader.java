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
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;


public class FileDownloader {

	public static final String DOWNLOADS_PATH			= "downloads";
	public static final String DOWNLOAD_FILE			= "downloads/downloadfile";
	public static final int MAX_DOWNLOADS_CONCURRENTS	= 5;
	
	private List<Thread> threads;
	private Deque<String> downloadsList;
	private Deque<String> partsFile;
	private String currentDownload;
	private CyclicBarrier barrier;
	private boolean processFinished;
	
	
	public FileDownloader(int maxths) {
		processFinished = false;
		
		// threads del programa
		threads = new ArrayList<Thread>(maxths);
		
		// Creamos los hilo para las descargas
		for (int i = 0; i < maxths; i++) {
			threads.add(new Thread( () -> downloadFiles(), "ID-"+(i+1) ));
		}
				
		// lista con las lineas del fichero de texto leido
		downloadsList = new ConcurrentLinkedDeque<String>();
		
		// lista con las partes a eliminar
		partsFile = new ConcurrentLinkedDeque<String>();
		
		// tarea a ejecutarse al finalizar una descarga
		barrier = new CyclicBarrier(maxths, () -> downloadCompleted());
	}
	
	
	private void downloadCompleted() {
		//downloadCompleted = true;
		
		// unimos las partes del archivo descargado
		SplitAndMerge sm = new SplitAndMerge();
		sm.mergeFile(DOWNLOADS_PATH, currentDownload);
		
		// eliminamos las partes del fichero
		Iterator<String> it = partsFile.iterator();
		while ( it.hasNext() ) {
			deleteDownloadFile(DOWNLOADS_PATH + "/" + it.next());
		}
		
		partsFile.clear();	// se vacia la lista de partes para la nueva descarga
		
		System.out.println("\n------------------------> DOWNLOAD SUCCESSFUL!\n");
		
		// iniciamos la siguiente descarga
		//downloadCompleted = false;
	}

	
	/**
	 * 
	 * @param downloadsFile
	 */
	public void process(String downloadsFile) {
		
		// se crea el directorio para almacenar los archivos descargados
		createFolder();
				
		// se descarga el fichero con los enlaces de descarga
		if ( !downloadByURL(downloadsFile, DOWNLOAD_FILE) ) {
			return;
		}
		
		// Leemos el fichero de texto descargado
		try {
			readTextFile();
		} catch (IOException e) {
			System.err.println("Error when trying to read from the file: " + e.getMessage());
			return;
		}
		
		for (Thread th: threads) {
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
				
				// se va añadiendo cada enlace en la lista para descargas
				while( (line = br.readLine()) != null ) {
					downloadsList.add(line.replaceAll("\\s",""));	// se "limpia" la linea de espacios, tabuladores, retornos
				}
				
				br.close();
				
			} catch (FileNotFoundException e) {
				System.err.println("Specified path does not exist: " + e.getMessage());
			}
		}
		
		deleteDownloadFile(DOWNLOAD_FILE);
	}
	
	
	/**
	 * 
	 */
	public void downloadFiles() {

		while ( !processFinished ) {
			try {
				System.out.println("-------PROCESO: " + Thread.currentThread().getName());
				System.out.println("--------------------------------------------------------");
				String linkFilePart = downloadsList.remove();
				
				if( linkFilePart.contains("Fichero") ) {
					// se extrae el nombre del archivo de descarga actual
					currentDownload = linkFilePart.substring( linkFilePart.lastIndexOf(":")+1, linkFilePart.length());
					System.out.println("-----------------");
					System.out.println(currentDownload);
				}
				
				// descarga de cada una de las partes de los enlaces
				if( !linkFilePart.contains("Fichero") ) {
					
					System.out.println("DONWLOADING... " + linkFilePart);
					
					// se extrae el nombre de la parte del archivo a descargar
					String linkfile =  linkFilePart.substring(linkFilePart.lastIndexOf("/"), linkFilePart.length());
					downloadByURL(linkFilePart, (DOWNLOADS_PATH + linkfile) );
					
					partsFile.add(linkfile);
					
				} else {
					barrier.await();
				}
			}
			catch (InterruptedException e) {}
			catch (BrokenBarrierException e) {}
			catch (NoSuchElementException e) {
				processFinished = true;
				return;
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
		FileDownloader fd = new FileDownloader(4);
		fd.process(downloadFile);
	}
}
