package server;

import handlers.Edit;
import handlers.EditManager;
import handlers.Encoding;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Projections;

import debug.Debug;

/**
 * The Server listens for the message sent over the network from the client. It
 * updates its own state and sends the appropriate message back to either the
 * client that sent the message or all the alive clients, depending on what's
 * the input message.
 * 
 * private fields: documentMap: a map that maps the document name to its text.
 * We store all the document in the server. serverSocket: the socket of the
 * server threadList: a list of Threads, each is for one client connection
 * editManager represents a queue of edits
 */

public class Server {
	private static final boolean DEBUG = Debug.DEBUG;
	private final Map<String, StringBuffer> documentMap;
	private final Map<String, Integer> documentVersionMap;
	private ServerSocket serverSocket;
	private ArrayList<OurThreadClass> threadList;
	private ArrayList<String> usernameList;
	private final EditManager editManager;
	private final String INITIAL_TEXT = 
			"public+class+michael_test+%7B%0A%0A%09"
			+ "public+static+void+main%28String%5B%5D+args%29+%7B%0A%09%09"
			+ "%2F%2F+TODO+Auto-generated+method+stub%0A%0A%09%7D%0A%0A%7D";
	
	private MongoClientURI clientURI;
	private MongoClient mongoClient;	
	private MongoDatabase mongoDb;
	private MongoCollection<Document> projects;

	/**
	 * Make a Server that listens for connections on port.
	 * 
	 * @param port
	 *            port number,
	 * @requires 0 <= port <= 65535.
	 * @throws IOException
	 */
	public Server(int port, Map<String, StringBuffer> documents,
			Map<String, Integer> version) {
		try {
			serverSocket = new ServerSocket(port);
			System.out.println("Server created. Listening on port " + port);
		} catch (IOException e) {
			e.printStackTrace();
		}
		documentMap = Collections.synchronizedMap(documents);
		threadList = new ArrayList<OurThreadClass>();
		documentVersionMap = Collections.synchronizedMap(version);
		usernameList = new ArrayList<String>();
		editManager = new EditManager();
	}

	/**
	 * Run the server, listening for client connections and handling them. Never
	 * returns unless an exception is thrown.
	 * 
	 * @throws IOException
	 *             if the main server socket is broken
	 */
	public void serve() {
		connectToMongo();
		while (true) {
			try {
				// block until a client connects
				Socket socket = serverSocket.accept();
				// handle the client by making a new OurThreadClass thread
				// running for that client,
				// also add that thread to the threadList so that the server
				// could send the message to the client
				OurThreadClass t = new OurThreadClass(socket, this);
				threadList.add(t);
				t.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * @return the documentMap, a private field of the server
	 */
	public synchronized Map<String, StringBuffer> getDocumentMap() {
		return documentMap;

	}
	/**
	 * Checks if a username is already in the usernameList.
	 * @param name
	 * @return
	 */
	public synchronized boolean nameIsAvailable(String name){
		return !usernameList.contains(name);
	}
	
	public synchronized void addUsername(OurThreadClass t, String name){
		usernameList.add(name);
	}

	/**
	 * @return the documentVersionMap, a private field of the server
	 */
	public synchronized Map<String, Integer> getDocumentVersionMap() {
		return documentVersionMap;

	}

	/**
	 * 
	 * @return all of the document names on the server in a single string
	 *         separated by a space. Ex: " document1 document2"
	 */
	public synchronized String getAllDocuments() {
		String documentNames = "";
		for (String key : documentMap.keySet()) {
			documentNames += " " + key;
		}
		return documentNames;
	}

	/**
	 * Manage the edit made by a client
	 * 
	 * @param documentName
	 *            the name of document edited
	 * @param version
	 *            the version number of the document sent by the client
	 * @param offset
	 *            the position of the edit
	 * @return a string that is transformed message with version and offset
	 *         corrected to match the current document on server
	 */
	public synchronized String manageEdit(String key, int version,
			int offset) {
		return editManager.manageEdit(key, version, offset);
	}

	/**
	 * @return boolean true if documentMap is empty, false otherwise
	 */
	public synchronized boolean documentMapisEmpty() {
		return documentMap.isEmpty();
	}

	/**
	 * @return boolean true if documentVersionMap is empty, false otherwise
	 */
	public synchronized boolean versionMapisEmpty() {
		return documentVersionMap.isEmpty();
	}

	/**
	 * Add the edit to the queue
	 * 
	 * @param edit
	 *            the edit made
	 */
	public synchronized void logEdit(Edit edit) {
		editManager.logEdit(edit);
	}

	/**
	 * Removes a thread from the threadList
	 * 
	 * @param t
	 *            the thread going to be removed
	 */
	public synchronized void removeThread(OurThreadClass t) {
		if (DEBUG) {
			System.out.println("removing thread from threadlist");
		}
		usernameList.remove(t.getUsername());
		threadList.remove(t);
	}

	/**
	 * Creates a new document and adds it to the documentMap and the
	 * documentVersionMap with version 1.
	 * 
	 * @param documentName
	 *            name of document
	 */
	public synchronized void addNewDocument(String userName, String documentName) {
		String key = userName+"_"+documentName;
		/*documentMap.put(key, new StringBuffer());
		documentVersionMap.put(key, 1);
		editManager.createNewlog(key);*/
		
		Document document = new Document("key", key);
		document.append("name", documentName);
		document.append("creator", userName);
		document.append("version", 1); 
		document.append("text", Encoding.decode(INITIAL_TEXT)); //TODO public main
		
		uploadToMap(document);
		projects.insertOne(document);

	}

	/**
	 * Updates the version of the specified documentName in the
	 * documentVersionMap. If documentName is not yet a key in
	 * documentVersionMap, a new key-value pair is added to the map.
	 * 
	 * @param documentName
	 *            the name of the document
	 * @param version
	 *            the new version number
	 */
	public synchronized void updateVersion(String key, int version) {
		documentVersionMap.put(key, version);
	}

	/**
	 * Returns the current version of the specified document
	 * 
	 * @param documentName
	 *            the name of document that we want to get version number for
	 * @return the integer that is current version number corresponding to the
	 *         documentName in document version map
	 */
	public synchronized int getVersion(String key) {
		return documentVersionMap.get(key);
	}

	/**
	 * Deletes text in the specified document from the specified offset to the
	 * specified endPosition If starting position is smaller than 0 or the end
	 * position is smaller than 1, throw a run time exception
	 * 
	 * @param documentName
	 *            the name of the document
	 * @param offset
	 *            the starting position of the text going to be deleted
	 * @param endPosition
	 *            the end position of the text going to be deleted
	 */
	public synchronized void delete(String key, int offset,
			int endPosition) {
		if (offset < 0 || endPosition < 1) {
			throw new RuntimeException("invalid args");
		}
		documentMap.get(key).delete(offset, endPosition);
	}

	/**
	 * Inserts the text into the specified document at the specified offset
	 * 
	 * @param documentName
	 *            the name of the document
	 * @param offset
	 *            the starting position that the text is going to be inserted
	 *            into
	 * @param text
	 *            the text that we want to insert into
	 */
	public synchronized void insert(String key, int offset, String text) {
		documentMap.get(key).insert(offset, text);
	}

	/**
	 * Returns the string representing the document text
	 * 
	 * @param documentName
	 *            the name of the document
	 * @return document text the text of the document
	 */
	public synchronized String getDocumentText(String key) {
		String document = "";
		document = documentMap.get(key).toString();
		return document;
	}

	/**
	 * Returns the length of the specified document
	 * 
	 * @param documentName
	 *            the name of the document
	 * @return length the length of the text of that document
	 */
	public synchronized int getDocumentLength(String key) {
		return documentMap.get(key).length();
	}

	/**
	 * Sends a message from every other thread in the threadList except for the
	 * thread that originally sent the message (no duplicate messages) and
	 * threads that has already closed its on and in (i.e, client disconnects).
	 * 
	 * @param message
	 *            the String that the server is going to sent to clients
	 * @param thread
	 *            sending thread
	 */
	public void returnMessageToEveryOtherClient(String message,
			OurThreadClass thread) {
		for (OurThreadClass t : threadList) {
			if (!thread.equals(t) && !t.getSocket().isClosed()) {
				// if the thread is still alive and it's not the one that sends
				// the request, send message
				PrintWriter out;
				if (t.getSocket().isConnected()) {
					synchronized (t) {
						try {
							// for those threads, open a printWriter and write
							// message to its socket.
							out = new PrintWriter(t.getSocket()
									.getOutputStream(), true);
							out.println(message);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
	}
	
	private void connectToMongo() {
		String uri = "mongodb+srv://admin:admin@cluster0-sjsqe.mongodb.net/test";
		clientURI = new MongoClientURI(uri);
		mongoClient = new MongoClient(clientURI);
		
		mongoDb = mongoClient.getDatabase("Shared_coding");
		projects = mongoDb.getCollection("Projects");
	}
	
	public MongoCollection<Document> getProjects(){
		return projects;
	}
	
	public synchronized String getProjectsNames() {
		FindIterable<Document> docs = projects.find().projection(Projections.include("key"));
		String documentNames = "";
		for(Document doc : docs) 
			documentNames += " " + doc.getString("key"); // TODO SPLIT _
		return documentNames;
	}

	public void uploadToMap(Document found) {
		String key = found.get("key").toString();
		documentMap.put(key, new StringBuffer());
		insert(key, 0, found.get("text").toString());
		documentVersionMap.put(key, (Integer) found.get("version"));
		editManager.createNewlog(key);
	}

	public boolean updateMongo(String key) {
		Document found = (Document) projects.find(new Document("key",key)).first();
		if(DEBUG)System.out.println("Updating mongoDB with key " + key);
		if(found == null || documentMapisEmpty()) {
			return false;
		}
		Bson updateValue = new Document("text", documentMap.get(key).toString())
				.append("documentVersion", documentVersionMap.get(key));
		Bson setValue = new Document("$set", updateValue);
		projects.updateOne(found, setValue);
		return true;
	}
}
