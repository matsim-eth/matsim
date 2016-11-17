/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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

package playground.ikaddoura.berlin;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.scoring.functions.ActivityUtilityParameters;
import org.matsim.core.scoring.functions.CharyparNagelScoringParameters;
import org.matsim.core.scoring.functions.OpeningIntervalCalculator;

/**
* @author ikaddoura
*/

public class IKOpeningIntervalCalculator implements OpeningIntervalCalculator {

	private final Person person;
	private final CharyparNagelScoringParameters params;	
	private final CountActEventHandler actCounter;
	private final double tolerance = 900.;
	
	public IKOpeningIntervalCalculator(CharyparNagelScoringParameters parameters, Person person, CountActEventHandler actCount) {
		this.person = person;
		this.params = parameters;
		this.actCounter = actCount;
	}

	@Override
	public double[] getOpeningInterval(Activity act) {
		
		ActivityUtilityParameters actParams = this.params.utilParams.get(act.getType());
		if (actParams == null) {
			throw new IllegalArgumentException("acttype \"" + act.getType() + "\" is not known in utility parameters " +
					"(module name=\"planCalcScore\" in the config file).");
		}
		
		// identify the correct activity position in the plan
		int activityCounter = this.actCounter.getActivityCounter(person.getId());
		
		// get the original start/end times from survey / initial demand which is written in the person attributes
		String activityOpeningIntervals = (String) person.getAttributes().getAttribute("InitialActivityTimes");	
		String activityOpeningTimes[] = activityOpeningIntervals.split(";");
	
		double openingTime = Double.valueOf(activityOpeningTimes[activityCounter * 2]) - tolerance;
		if (openingTime < 0) {
			openingTime = 0.;
		}
		double closingTime = Double.valueOf(activityOpeningTimes[(activityCounter * 2) + 1]) + tolerance;
				
		return new double[]{openingTime, closingTime};
	}

}
