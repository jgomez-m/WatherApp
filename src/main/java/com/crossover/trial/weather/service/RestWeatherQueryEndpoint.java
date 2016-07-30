package com.crossover.trial.weather.service;

import com.crossover.trial.weather.domain.AtmosphericInformation;
import com.crossover.trial.weather.domain.AirportData;
import com.crossover.trial.weather.exception.WeatherException;
import com.google.gson.Gson;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Weather App REST endpoint allows clients to query, update and check health stats. Currently, all data is
 * held in memory. The end point deploys to a single container
 *
 * @author code test administrator
 */

@Path("/query")
public class RestWeatherQueryEndpoint implements IWeatherQueryEndpoint {

    /**
     *
     */
    public final static Logger LOGGER = Logger.getLogger("WeatherQuery");

    /** earth radius in KM */
    public static final double R = 6372.8;

    /** shared gson json to object factory */
    public static final Gson gson = new Gson();

//    /** all known airports */
//    //CR: This property should be encapsulate with private accessor 
//    
//    private final static List<AirportData> allAirports = new ArrayList<>();
//
//    /** atmospheric information for each airport, idx corresponds with airportData */
//     //CR: This property should be encapsulate with private accessor
//     //It is difficult to understand the code, AirportData and AtmosphericInformation should be 
//     //stored into a Map as HashMap
//    private final static List<AtmosphericInformation> atmosphericInformation = new LinkedList<>();
    private final static Map<AirportData, AtmosphericInformation> allAirports = 
            new HashMap<AirportData, AtmosphericInformation>();

    /**
     * Internal performance counter to better understand most requested information, this map can be improved but
     * for now provides the basis for future performance optimizations. Due to the stateless deployment architecture
     * we don't want to write this to disk, but will pull it off using a REST request and aggregate with other
     * performance metrics {@link #ping()}
     */
    private final static Map<AirportData, Integer> requestFrequency = new HashMap<AirportData, Integer>();

    private final static Map<Double, Integer> radiusFreq = new HashMap<Double, Integer>();

    /**
     * Retrieve service health including total size of valid data points and request frequency information.
     *
     * @return health stats for the service as a string
     */
    @GET
    @Path("/ping")
    @Override
    public String ping() {
        Map<String, Object> retval = new HashMap<>();

        int datasize = 0;
        for (AtmosphericInformation ai : allAirports.values()) {
            // we only count recent readings
            if (ai.getCloudCover() != null
                || ai.getHumidity() != null
                || ai.getPressure() != null
                || ai.getPrecipitation() != null
                || ai.getTemperature() != null
                || ai.getWind() != null) {
                // updated in the last day
                if (ai.getLastUpdateTime() > System.currentTimeMillis() - 86400000) {
                    datasize++;
                }
            }
        }
        retval.put("datasize", datasize);

        Map<String, Double> freq = new HashMap<>();
        // fraction of queries
        for (AirportData data : allAirports.keySet()) {
            double frac = (double)requestFrequency.getOrDefault(data, 0) / requestFrequency.size();
            freq.put(data.getIata(), frac);
        }
        retval.put("iata_freq", freq);

        int m = radiusFreq.keySet().stream()
                .max(Double::compare)
                .orElse(1000.0).intValue() + 1;

        int[] hist = new int[m];
        for (Map.Entry<Double, Integer> e : radiusFreq.entrySet()) {
            int i = e.getKey().intValue() % 10;
            hist[i] += e.getValue();
        }
        retval.put("radius_freq", hist);

        return gson.toJson(retval);
    }

    /**
     * Given a query in json format {'iata': CODE, 'radius': km} extracts the requested airport information and
     * return a list of matching atmosphere information.
     *
     * @param iata the iataCode
     * @param radiusString the radius in km
     *
     * @return a list of atmospheric information
     */
    @GET
    @Path("/weather/{iata}/{radius}")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response get(@PathParam("iata") String iata, @PathParam("radius") String radiusString) {
        double radius = radiusString == null || radiusString.trim().isEmpty() ? 0 : Double.valueOf(radiusString);
        updateRequestFrequency(iata, radius);

        List<AtmosphericInformation> answer = new ArrayList<>();
        AirportData airport = findAirportData(iata);
        if (radius == 0) {
            if(airport != null){
                answer.add(allAirports.get(airport));
            }
        } else {
            for (AirportData ad : allAirports.keySet()){
                if (calculateDistance(airport, ad) <= radius){
                    AtmosphericInformation ai = allAirports.get(ad);
                    if (ai.getCloudCover() != null || ai.getHumidity() != null || ai.getPrecipitation() != null
                       || ai.getPressure() != null || ai.getTemperature() != null || ai.getWind() != null){
                        answer.add(ai);
                    }
                }
            }
        }
        return Response.status(Response.Status.OK).entity(answer).build();
    }


    /**
     * Records information about how often requests are made
     *
     * @param iata an iata code
     * @param radius query radius
     */
    public void updateRequestFrequency(String iata, Double radius) {
        AirportData airportData = findAirportData(iata);
        requestFrequency.put(airportData, requestFrequency.getOrDefault(airportData, 0) + 1);
        radiusFreq.put(radius, radiusFreq.getOrDefault(radius, 0));
    }

    /**
     * Given an iataCode find the airport data
     *
     * @param iataCode as a string
     * @return airport data or null if not found
     */
     public AirportData findAirportData(String iataCode) {
        return allAirports.keySet().stream()
            .filter(ap -> ap.getIata().equals(iataCode))
            .findFirst().orElse(null);
    }
    /**
     * Haversine distance between two airports.
     *
     * @param ad1 airport 1
     * @param ad2 airport 2
     * @return the distance in KM
     */
    private double calculateDistance(AirportData ad1, AirportData ad2) {
        double deltaLat = Math.toRadians(ad2.getLatitude() - ad1.getLatitude());
        double deltaLon = Math.toRadians(ad2.getLongitude() - ad1.getLongitude());
        double a =  Math.pow(Math.sin(deltaLat / 2), 2) + Math.pow(Math.sin(deltaLon / 2), 2)
                * Math.cos(ad1.getLatitude()) * Math.cos(ad2.getLatitude());
        double c = 2 * Math.asin(Math.sqrt(a));
        return R * c;
    }
    
    /**
     *
     * @return All IATA codes in String Format
     */
    public Set<String> getAirportsByIata(){
        Set<String> retval = new HashSet<>();
        for (AirportData ad : allAirports.keySet()) {
            retval.add(ad.getIata());
        }
        return retval;
    }
    
     /**
     * Add a new known airport to our list.
     *
     * @param iataCode 3 letter code
     * @param latitude in degrees
     * @param longitude in degrees
     *
     * @return the added airport
     */
    public AirportData addAirport(AirportData ad) throws WeatherException {
        AtmosphericInformation ai = new AtmosphericInformation();
        try{
            if(!allAirports.containsKey(ad)){
                allAirports.put(ad, ai);
            } else{
                ai = allAirports.get(ad);
                allAirports.replace(ad, ai);
            }
        return ad;
        } catch(Exception e){
            LOGGER.log(Level.SEVERE, "Error adding an airport: "+ ad.getIata(), e);
            throw new WeatherException("Error adding an airport: "+ ad.getIata(), e);
        }
    }
    /**
     * Cleans the memory objects
     */
    public void clear(){
        allAirports.clear();
        requestFrequency.clear();
    }
    
    /**
     * Gets an Atmospheric Information from a IATACode airport
     * @param iataCode
     * @return AtmosphericInformation
     */
    @Override
    public AtmosphericInformation getAtmosphericInformation(String iataCode){
        AirportData ad = findAirportData(iataCode);
        AtmosphericInformation ai = null;
        if(ad != null){
            ai = allAirports.get(ad);
        }
        return ai;
    }
    
    /**
     * Remove an airport from the list
     * @param airport
     * @return true if it was deleted, false if not
     * @throws WeatherException When ocurrs something while removing an element
     */
    public boolean removeAirport(AirportData airport) throws WeatherException{
        try{
            if(allAirports.containsKey(airport)){
                allAirports.remove(airport);
                return true;
            }
        }catch(Exception e){
            LOGGER.log(Level.SEVERE, "Error removing an airport: "+ airport.getIata(), e);
            throw new WeatherException("Error removing an airport: "+ airport.getIata(), e);
        }
        return false;
    }
}
