package playground.ikaddoura.parkAndRide.analyze.plans;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.config.ConfigUtils;

import playground.ikaddoura.parkAndRide.pR.ParkAndRideConstants;

public class PRPlansReader {
	
	private static final Logger log = Logger.getLogger(PRPlansReader.class);

	static String plansFile1; // initial Plans
	static String plansFile2; // output Plans
	static String netFile;
	static String outputPath;
	
	TextFileWriter writer = new TextFileWriter();
			
	public static void main(String[] args) throws IOException {
		
		plansFile1 = "/Users/Ihab/Desktop/test/population1.xml";
		plansFile2 = "/Users/Ihab/Desktop/test/population2.xml";
		netFile = "/Users/Ihab/Desktop/test/network.xml";
		outputPath = "/Users/Ihab/Desktop/test/";
		
		// ****************************
		
//		plansFile2 = "/Users/Ihab/Desktop/testBerlin/pop.gz";
//		netFile = "/Users/Ihab/Desktop/testBerlin/net.xml";
//		outputPath = "/Users/Ihab/Desktop/testBerlin/";
//		
//		plansFile1 = "/Users/Ihab/ils4/kaddoura/parkAndRide/output/berlin_run4_transferPenalty-5/ITERS/it.0/0.plans.xml.gz";
//		plansFile2 = "/Users/Ihab/ils4/kaddoura/parkAndRide/output/berlin_run4_transferPenalty-5/ITERS/it.20/20.plans.xml.gz";
//		netFile = "/Users/Ihab/ils4/kaddoura/parkAndRide/input/PRnetwork_berlin.xml";
//		outputPath = "/Users/Ihab/Desktop/test/";

		// ****************************
		
//		plansFile1 = args[0];
//		plansFile2 = args[1];
//		netFile = args[2];
//		outputPath = args[3];
		
		PRPlansReader analysis = new PRPlansReader();
		analysis.run();
	}
	
	public void run() {
		
		Scenario scenario1 = getScenario(netFile, plansFile1);
		Scenario scenario2 = getScenario(netFile, plansFile2);
		
		System.out.println("-------------------------------------------------------");
		
		compareScores(scenario1.getPopulation(), scenario2.getPopulation(), 1.0); // Verbesserungen mit / ohne P+R
		analyzePR(scenario2); // selected Plans mit PR

	}

	private void analyzePR(Scenario scenario2) {
		
		List<Person> personsPR = new ArrayList<Person>();
		List<Person> personsHomeWork = new ArrayList<Person>();

		for (Person person : scenario2.getPopulation().getPersons().values()){
			Plan selectedPlan = person.getSelectedPlan();
			
			boolean hasPR = false;
			boolean hasHome = false;
			boolean hasWork = false;

			for (PlanElement pe: selectedPlan.getPlanElements()) {
				if (pe instanceof ActivityImpl) {
					ActivityImpl act = (ActivityImpl)pe;
					if (act.getType().equals(ParkAndRideConstants.PARKANDRIDE_ACTIVITY_TYPE)){
						hasPR = true;
					}
					if (act.getType().equals("home")){
						hasHome = true;
					}
					if (act.getType().equals("work")){
						hasWork = true;
					}
				}
			}
			
			if (hasPR) {
				personsPR.add(person);
			}
			
			if (hasHome && hasWork){
				personsHomeWork.add(person);
			}
		}
		log.info("PRPlans: " + personsPR.size());
		log.info("WorkPlans: " + personsHomeWork.size());
		log.info("Park'n'Ride-Anteil: " + personsPR.size()/personsHomeWork.size()*100+"%");
		
		writer.writeFile2(personsPR, personsHomeWork, outputPath+"prPlans.txt");
		
		SortedMap<Id,Coord> homeCoordinates = getCoordinates(personsPR, "home");
		SortedMap<Id,Coord> workCoordinates = getCoordinates(personsPR, "work");
		SortedMap<Id,Coord> prCoordinates = getCoordinates(personsPR, ParkAndRideConstants.PARKANDRIDE_ACTIVITY_TYPE);

		MyShapeFileWriter shapeFileWriter = new MyShapeFileWriter();
		
		File directory = new File(outputPath+"shapeFiles");
		directory.mkdirs();
		
		shapeFileWriter.writeShapeFilePoints(scenario2, homeCoordinates, outputPath + "shapeFiles/homeCoordinates.shp");
		shapeFileWriter.writeShapeFilePoints(scenario2, workCoordinates, outputPath + "shapeFiles/workCoordinates.shp");
		shapeFileWriter.writeShapeFilePoints(scenario2, prCoordinates, outputPath + "shapeFiles/prCoordinates.shp");
		shapeFileWriter.writeShapeFileLines(scenario2, outputPath + "shapeFiles/network.shp");
		
		Map<Id, Integer> prLinkId2prActs = new HashMap<Id, Integer>();

		for (Person person : personsPR){
			for (PlanElement pe: person.getSelectedPlan().getPlanElements()) {
				if (pe instanceof ActivityImpl) {
					ActivityImpl act = (ActivityImpl)pe;
					if (act.getType().equals(ParkAndRideConstants.PARKANDRIDE_ACTIVITY_TYPE)){
						Id linkId = act.getLinkId();
						if (prLinkId2prActs.get(linkId) == null){
							prLinkId2prActs.put(linkId, 1);
						} else {
							int increasedPrActs = prLinkId2prActs.get(linkId) + 1;
							prLinkId2prActs.put(linkId, increasedPrActs);
						}
					}
				}
			}
		}
		
		writer.writeFile3(prLinkId2prActs, outputPath+"prUsage.txt");
		shapeFileWriter.writeShapeFilePRUsage(scenario2, prLinkId2prActs, outputPath + "shapeFiles/prUsage.shp");
		
	}

	private SortedMap<Id, Coord> getCoordinates(List<Person> persons, String activity) {
		SortedMap<Id,Coord> id2koordinaten = new TreeMap<Id,Coord>();
		for(Person person : persons){
			for (PlanElement pE : person.getSelectedPlan().getPlanElements()){
				if (pE instanceof Activity){
					Activity act = (Activity) pE;
					if (act.getType().equals(activity)){
						Coord coord = act.getCoord();
						id2koordinaten.put(person.getId(), coord);
					}
					else {}
				}
			}
		}
		return id2koordinaten;
	}
	
	
	// -----------------------------------------------

	private void compareScores(Population population1, Population population2, double tolerance) {
		List <Id> personIds = new ArrayList<Id>();
		
		int n = 0;
		
		for (Person person1 : population1.getPersons().values()){
			for (Plan plan1 : person1.getPlans()){
				if (plan1.getScore()==null){
					log.info("Plan1 hat keinen Score...");
				} else {
					double score1 = plan1.getScore();
					Id personId1 = person1.getId();
					
					if (population2.getPersons().get(personId1)==null){
					} else {
						Person person2 = population2.getPersons().get(personId1);
						for (Plan plan2 : person2.getPlans()){
							boolean plan2HasPR = false;
							
							if (plan2.getScore()==null){
								log.info("Plan2 hat keinen Score...");
							} else {
								double score2 = plan2.getScore();
								if (score2 > score1 + tolerance) {
									for (PlanElement pE : plan2.getPlanElements()){
										if (pE instanceof Activity){
											Activity act = (Activity) pE;
											if (act.toString().contains("parkAndRide")){
												plan2HasPR = true;
											}
										}
									}
									if (plan2HasPR){
//										System.out.println(person2.getId() + " hat in planFile2 einen Park'n'Ride-Plan mit höherem Score.");
									} else {
										log.info(person2.getId() + " hat in planFile2 einen Plan mit höherem Score, der kein Park'n'Ride enthält!!!");
										log.info("Verbesserung nicht aufgrund von Park'n'Ride!");
										personIds.add(person2.getId());
										n++;
									}
								}
							}	
						}
					}
				}
			}
		}
		log.info("Verbesserte Pläne nicht aufgrund von Park'n'Ride: " + n);
		
		writer.writeFile1(personIds, outputPath+"verbessertOhnePR.txt");		
	}

//------------------------------------------------------------------------------------------------------------------------
	
	private Scenario getScenario(String netFile, String plansFile){
		
		Config config = ConfigUtils.createConfig();
		config.network().setInputFile(netFile);
		config.plans().setInputFile(plansFile);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		return scenario;
	}

}
