/**
 * Modifica la práctica 1 de manera que se guarde acumulado el tiempo de descarga de cada una de las partes.
 * Para ello se tendrá una variable entera compartida por todos los threads donde se irá acumulando 
 * el tiempo de cada descarga. Este tiempo se medirá con el método System.currentTimeMillis() de Java, 
 * que devuelve el tiempo (en milisegundos). Será necesario tomar el tiempo antes de cada descarga y 
 * después y restar ambos valores. 
 * Cuando terminen de descargarse todos los ficheros, se mostrará por pantalla el tiempo que ha 
 * pasado descargando el programa en total.
 */

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
import java.util.Iterator;
import java.util.Deque;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;


public class FileDownloader {

	public static final String DOWNLOADS_PATH			= "downloads";
	public static final String DOWNLOAD_FILE			= "downloads/downloadfile";
	
	public static volatile long timeDownload	= 0;	// TIEMPO TOTAL DE DESCARGA
	private Semaphore exmTimeDownload			= new Semaphore(1);		// SEMAFORO PARA LA EXCLUSION MUTUA
	
	private Deque<String> downloadsList;	// LISTA CON LAS LINEAS DEL FICHERO DE DESCARGA
	private Deque<String> partsFile;		// LISTA CON LAS PARTES DESCARGADAS DEL FICHERO
	private String currentDownload;			// ARCHIVO ACTUAL A DESCARGAR
	private CyclicBarrier barrier;
	private int maxThreads;
	private boolean processFinished;
	
	
	public FileDownloader(int maxths) {
		processFinished = false;

		maxThreads		= maxths;
		downloadsList	= new ConcurrentLinkedDeque<String>();
		partsFile		= new ConcurrentLinkedDeque<String>();
		barrier			= new CyclicBarrier(maxths, () -> downloadCompleted());
	}
	
	
	private void downloadCompleted() {
		
		System.out.println("-----------------> MERGIN... ");
		SplitAndMerge sm = new SplitAndMerge();
		sm.mergeFile(DOWNLOADS_PATH, currentDownload);
		
		// ELIMINACION DE LAS PARTES DEL FICHERO
		Iterator<String> it = partsFile.iterator();
		while ( it.hasNext() ) {
			deleteDownloadFile(DOWNLOADS_PATH + "/" + it.next());
		}

		partsFile.clear();	// SE VACIA LA LISTA DE PARTES PARA LA NUEVA DESCARGA

		if( downloadsList.isEmpty() ) {
			processFinished = true;
			System.out.println("\n-----------------> SUCCESSFUL!");
			System.out.println("TOTAL DOWNLOAD TIME: " + timeDownload + " ms.");
		}
		
		else {
			// EXTRACCION DEL SIGUIENTE ARCHIVO A DESCARGAR
			nextFileToDownload();
		}
	}
	
	
	/**
	 * Extrae el siguiente archivo a descargar de la lista de descargas pendientes
	 */
	private void nextFileToDownload() {
		
		currentDownload = downloadsList.remove();
		currentDownload = currentDownload.substring( currentDownload.lastIndexOf(":")+1, currentDownload.length());
		System.out.println("\n-----------------> DOWNLOADING... " + currentDownload);
	}

	
	/**
	 * 
	 * @param downloadsFile
	 */
	public void process(String downloadsFile) {
		createFolder();		// DIRECTORIO ARCHIVOS DESCARGADOS
		
		// DESCARGA DEL FICHERO DE ENLACES
		if ( !downloadByURL(downloadsFile, DOWNLOAD_FILE) ) {
			return;
		}
		
		// LECTURA DEL FICHERO DE ENLACES
		try {
			readTextFile();		
		} catch (IOException e) {
			System.err.println("Error when trying to read from the file: " + e.getMessage());
			return;
		}
		
		nextFileToDownload();
		
		// HILOS DE EJECUCION
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
	 * Lee un fichero y almancena en una lista concurrente cada línea leída del fichero
	 * @throws IOException
	 */
	private void readTextFile() throws IOException {
		
		File file = new File(DOWNLOAD_FILE);
		
		if ( file.exists() ) {
			try {
				// LEER EL FICHERO
				BufferedReader br = new BufferedReader(new FileReader(file));
				String line = "";

				while( (line = br.readLine()) != null ) {
					downloadsList.add(line.replaceAll("\\s",""));	// SE "LIMPIA" LA LINEA DE ESPACIOS, TABULADORES, RETORNOS
				}
				
				br.close();
				
			} catch (FileNotFoundException e) {
				System.err.println("Specified path does not exist: " + e.getMessage());
			}
		}
		
		deleteDownloadFile(DOWNLOAD_FILE);
	}
	
	
	/** Descarga los ficheros de todos los enlaces de la lista de enlaces
	 * @throws BrokenBarrierException 
	 * @throws InterruptedException 
	 * 
	 */
	public void downloadFiles() throws InterruptedException, BrokenBarrierException {

		// MIENTRAS NO SE HAYA TERMINADO DE DESCARGAR TODOS LOS ENLACES...
		while( !processFinished ) {	
			
			// DESCARGA DE CADA UNA DE LAS PARTES DE LOS ENLACES
			if ( !downloadsList.isEmpty() && !downloadsList.getFirst().contains("Fichero:") ) {
				
				String line = downloadsList.remove();
				System.out.println("DONWLOADING... " + line);
				
				String link = line.substring(line.lastIndexOf("/"), line.length());
				
				exmTimeDownload.acquire();
				long timeAnt = System.currentTimeMillis();
				downloadByURL(line, (DOWNLOADS_PATH + link) );
				long timeDesp = System.currentTimeMillis();
				timeDownload = timeDesp - timeAnt;
				exmTimeDownload.release();
				
				partsFile.add(link);
			}
			
			else {
				barrier.await();
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
		FileDownloader fd = new FileDownloader(6);
		fd.process(downloadFile);
	}
}
