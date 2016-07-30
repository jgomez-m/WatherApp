package com.crossover.trial.weather;

import com.crossover.trial.weather.domain.AirportData;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.io.*;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * A simple airport loader which reads a file from disk and sends entries to the webservice
 *
 * TODO: Implement the Airport Loader
 * 
 * @author code test administrator
 */
public class AirportLoader {

    /** end point for read queries */
    private WebTarget query;

    /** end point to supply updates */
    private WebTarget collect;

    public AirportLoader() {
        Client client = ClientBuilder.newClient();
        query = client.target("http://localhost:8080/query");
        collect = client.target("http://localhost:8080/collect");
    }

    public void upload(InputStream airportDataStream) throws IOException{
        BufferedReader reader = new BufferedReader(new InputStreamReader(airportDataStream));
        String l = null;
        while ((l = reader.readLine()) != null) {
            String[] split = l.split(",");
            AirportData ad = new AirportData();
            ad.setName(split[0].replace("\"", ""));
            ad.setCity(split[1].replace("\"", ""));
            ad.setCountry(split[2].replace("\"", ""));
            ad.setIata(split[3].replace("\"", ""));
            ad.setIcao(split[4].replace("\"", ""));
            ad.setLatitude(Double.valueOf(split[5]));
            ad.setLongitude(Double.valueOf(split[6]));
            ad.setFeet(Integer.valueOf(split[7]));
            ad.setTimezone(Double.valueOf(split[8]));
            ad.setDst(split[9].replace("\"", ""));
            Response post = makeRequest(ad);
        }
    }
    
    private Response makeRequest(AirportData ad){
        WebTarget path = collect.path("/airport/"+ad.getIata()+"/"+ad.getLatitude()+"/"+ad.getLongitude());
        return path.request().post(Entity.entity(ad, MediaType.APPLICATION_JSON));
    }

    //CR: This method should not have to main method, just load the airports in memory
    public static void main(String args[]) throws IOException{
        File airportDataFile = new File(args[0]);
        if (!airportDataFile.exists() || airportDataFile.length() == 0) {
            System.err.println(airportDataFile + " is not a valid input");
            System.exit(1);
        }

        AirportLoader al = new AirportLoader();
        al.upload(new FileInputStream(airportDataFile));
        System.exit(0);
    }
}
