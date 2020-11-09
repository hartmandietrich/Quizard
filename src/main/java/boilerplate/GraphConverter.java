package boilerplate;

import java.io.File;
import java.util.ArrayList;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Values;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.*;

public class GraphConverter {
	
	private static void importOntology(OWLOntology ontology) throws Exception{
		OWLReasonerFactory rf = new ReasonerFactory();
		OWLReasoner reasoner = rf.createReasoner(ontology);
		if(!reasoner.isConsistent()) {
			System.out.println("Ontology is inconsistent");
			throw new Exception("Ontology not consistent");
		}
		ArrayList<OWLClass> classes = new ArrayList<OWLClass>();
		ontology.classesInSignature().forEach(classes::add);
		
		Driver driver = GraphDatabase.driver( "bolt://localhost:7687", AuthTokens.basic( "neo4j", "quizardpassword" ));
		String rootname = "OWLThing";
		try(Session session = driver.session()){
			session.writeTransaction(tx -> tx.run("MERGE (a:RootNode {name: $rootname})", Values.parameters("rootname", "OWLThing")));
			int i = 1;
			System.out.println(classes.size());
			for(OWLClass c : classes) {
				String name = c.getIRI().getShortForm();
				System.out.println(i + ": " + name);
				i++;
				session.writeTransaction(tx -> tx.run("MERGE (a:Class {name: $name})", Values.parameters("name", name)));
			}
			for(OWLClass c : classes) {
				String name = c.getIRI().getShortForm();
				NodeSet<OWLClass> subclasses = reasoner.getSubClasses(c, true);
				for(Node<OWLClass> sc : subclasses) {
					ArrayList<OWLClass> eqsubclasses = new ArrayList<OWLClass>();
					sc.entities().forEach(eqsubclasses::add);
					for(OWLClass eqsc : eqsubclasses) {
						String className = c .getIRI().getShortForm();
						String scName = eqsc.getIRI().getShortForm();
						if(!scName.equals("Nothing")) {
							System.out.println(scName + " is a subclass of " + className);
							session.writeTransaction(tx -> tx.run("MATCH (a:Class {name: $scName}), " +
																  "(b:Class {name: $className}) " +
																  "MERGE (a)-[:Is_SubClass]->(b)", Values.parameters("scName", scName, "className", className)));
						}
					}
				}
			}
			
		}
		
	}
	
	public static void main(String[] args) throws Exception{
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		File file = new File("C:\\Users\\Dietrich\\Desktop\\OWL Stuff\\example.owl");
		OWLOntology o = man.loadOntologyFromOntologyDocument(file);
		importOntology(o);
		
	}

}
