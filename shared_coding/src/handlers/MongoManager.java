package handlers;

import org.bson.Document;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class MongoManager {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String uri = "mongodb+srv://admin:admin@cluster0-sjsqe.mongodb.net/test";
		MongoClientURI clientURI = new MongoClientURI(uri);
		MongoClient mongoClient = new MongoClient(clientURI);
		
		MongoDatabase mongoDb = mongoClient.getDatabase("Shared_coding");
		MongoCollection projects = mongoDb.getCollection("Projects");
		
		Document document = new Document("cas","csa");
	}

}
