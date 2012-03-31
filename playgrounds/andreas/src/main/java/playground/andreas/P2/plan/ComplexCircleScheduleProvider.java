/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package playground.andreas.P2.plan;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.router.Dijkstra;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.misc.NetworkUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;


/**
 * Generates simple back and force routes for two given stops and operation time, number of vehicles plying that line can be specified.
 * 
 * @author aneumann
 *
 */
public class ComplexCircleScheduleProvider implements PRouteProvider {
	
	private final static Logger log = Logger.getLogger(ComplexCircleScheduleProvider.class);
	public final static String NAME = "ComplexCircleScheduleProvider";
	
	private String pIdentifier;
	private Network net;
	private TransitSchedule scheduleWithStopsOnly;
	
	public ComplexCircleScheduleProvider(String pIdentifier, TransitSchedule scheduleWithStopsOnly, Network network, int iteration) {
		this.pIdentifier = pIdentifier;
		this.net = network;
		this.scheduleWithStopsOnly = scheduleWithStopsOnly;
	}

	@Override
	public TransitLine createTransitLine(Id pLineId, double startTime, double endTime, int numberOfVehicles, ArrayList<TransitStopFacility> stopsToBeServed, Id routeId){
		
		// initialize
		TransitLine line = this.scheduleWithStopsOnly.getFactory().createTransitLine(pLineId);			
		routeId = new IdImpl(pLineId + "-" + routeId);
		TransitRoute transitRoute = createRoute(routeId, stopsToBeServed, startTime);
		
		// register route
		line.addRoute(transitRoute);
		
		// add departures
		int n = 0;
		int headway = (int) (transitRoute.getStops().get(transitRoute.getStops().size() - 1).getDepartureOffset()) / numberOfVehicles;
		for (int i = 0; i < numberOfVehicles; i++) {
			for (double j = startTime + i * headway; j < endTime; ) {
				Departure departure = this.scheduleWithStopsOnly.getFactory().createDeparture(new IdImpl(n), j);
				departure.setVehicleId(new IdImpl(transitRoute.getId().toString() + "-" + i));
				transitRoute.addDeparture(departure);
				j += transitRoute.getStops().get(transitRoute.getStops().size() - 1).getDepartureOffset() + 1 *60;
				n++;
			}
		}		
		
		log.info("added " + n + " departures");		
		return line;
	}

	private TransitRoute createRoute(Id routeID, ArrayList<TransitStopFacility> stopsToBeServed, double startTime){
		
		ArrayList<TransitStopFacility> tempStopsToBeServed = new ArrayList<TransitStopFacility>();
		for (TransitStopFacility transitStopFacility : stopsToBeServed) {
			tempStopsToBeServed.add(transitStopFacility);
		}
		tempStopsToBeServed.add(stopsToBeServed.get(0));
		
		// create links - network route		
		FreespeedTravelTimeAndDisutility tC = new FreespeedTravelTimeAndDisutility(-6.0, 0.0, 0.0);
		LeastCostPathCalculator routingAlgo = new Dijkstra(this.net, tC, tC);
				
		Id startLinkId = null;
		Id lastLinkId = null;
		
		List<Link> links = new LinkedList<Link>();				
		
		// for each stop
		for (TransitStopFacility stop : tempStopsToBeServed) {
			if(startLinkId == null){
				startLinkId = stop.getLinkId();
			}
			
			if(lastLinkId != null){
				links.add(this.net.getLinks().get(lastLinkId));
				Path path = routingAlgo.calcLeastCostPath(this.net.getLinks().get(lastLinkId).getToNode(), this.net.getLinks().get(stop.getLinkId()).getFromNode(), 0.0);

				for (Link link : path.links) {
					links.add(link);
				}
			}
			
			lastLinkId = stop.getLinkId();
		}

		links.remove(0);
		LinkNetworkRouteImpl route = new LinkNetworkRouteImpl(startLinkId, lastLinkId);
		route.setLinkIds(startLinkId, NetworkUtils.getLinkIds(links), lastLinkId);

		// get stops at Route
		List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
		double runningTime = 0.0;
		
		// first stop
		TransitRouteStop routeStop;
		routeStop = this.scheduleWithStopsOnly.getFactory().createTransitRouteStop(tempStopsToBeServed.get(0), runningTime, runningTime);
		stops.add(routeStop);
		
		// additional stops
		for (Link link : links) {
			runningTime += link.getLength() / link.getFreespeed();
			if(this.scheduleWithStopsOnly.getFacilities().get(new IdImpl(this.pIdentifier + link.getId())) == null){
				continue;
			}
			routeStop = this.scheduleWithStopsOnly.getFactory().createTransitRouteStop(this.scheduleWithStopsOnly.getFacilities().get(new IdImpl(this.pIdentifier + link.getId())), runningTime, runningTime);
			stops.add(routeStop);
		}
		
		// last stop
		runningTime += this.net.getLinks().get(tempStopsToBeServed.get(0).getLinkId()).getLength() / this.net.getLinks().get(tempStopsToBeServed.get(0).getLinkId()).getFreespeed();
		routeStop = this.scheduleWithStopsOnly.getFactory().createTransitRouteStop(tempStopsToBeServed.get(0), runningTime, runningTime);
		stops.add(routeStop);
		
		TransitRoute transitRoute = this.scheduleWithStopsOnly.getFactory().createTransitRoute(routeID, route, stops, TransportMode.pt);
		return transitRoute;
	}

	@Override
	public TransitStopFacility getRandomTransitStop(){
		int i = this.scheduleWithStopsOnly.getFacilities().size();
		for (TransitStopFacility stop : this.scheduleWithStopsOnly.getFacilities().values()) {
			if(MatsimRandom.getRandom().nextDouble() < 1.0 / i){
				return stop;
			}
			i--;
		}
		return null;
	}

	@Override
	public TransitLine createEmptyLine(Id id) {
		return this.scheduleWithStopsOnly.getFactory().createTransitLine(id);
	}

}