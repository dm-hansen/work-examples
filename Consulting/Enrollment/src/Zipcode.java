import java.util.HashMap;
import java.util.Scanner;
import java.io.FileInputStream;
import java.io.IOException;

public class Zipcode
{

   private final static String ZIPFILE_PATHNAME = "./postal.dat";

   /**
    * Compute the distance in miles between two zipcodes
    *
    * @param   zipFrom 
    * @param   zipTo
    *
    * @return distance between zipFrom and zipTo in miles
    */
   public static Double distanceBetween(String zipFrom, String zipTo)
   {
      double latFrom, longFrom, latTo, longTo;
      int zipIncrement = 1;

      // Initialize the map from the datafile if it's empty
      if (zipMap.size() == 0)
      {
         initMap();
      }

      while (!zipMap.containsKey(zipTo))
      {
         // If the zipcode appears to be from Canada or nothing close with data, return null;
         // nothing we can do
         if (Character.isLetter(zipTo.charAt(0)) || zipIncrement > 5)
         {
            return null;
         }
         // Otherwise it's US so try an "adjacent" zipcode until we get some data
         // Add the increment. If the increment is positive then negate it,
         // otherwise add 1 to it and make it positive for next time
         try
         {
            zipTo = String.format("%05d", Integer.parseInt(zipTo) + zipIncrement);
//System.err.println(searchZip+"...");
            zipIncrement = (zipIncrement > 0) ? zipIncrement*(-1) : zipIncrement*(-1)+1;
         }
         catch (NumberFormatException e) // nevermind...
         {
            return null;
         }
      } // while

      // From http://www.geodatasource.com/developers/java
      latFrom = zipMap.get(zipFrom)[0];
      longFrom = zipMap.get(zipFrom)[1];
      latTo = zipMap.get(zipTo)[0];
      longTo = zipMap.get(zipTo)[1];
      double theta = longTo - longFrom;
      double dist = rad2deg(
           Math.acos(
              Math.sin(deg2rad(latFrom)) * Math.sin(deg2rad(latTo)) + 
              Math.cos(deg2rad(latFrom)) * Math.cos(deg2rad(latTo)) * Math.cos(deg2rad(theta))));

      return new Double(dist * 60 * 1.1515);
   }

   // from http://www.geodatasource.com/developers/java
   private static double deg2rad(double deg) { return (deg * Math.PI / 180.0); }
   private static double rad2deg(double rad) { return (rad * 180.0 / Math.PI); }
   

   /**
    * Fill the map from the datafile
    */
   private static void initMap() 
   {
      try
      {
         FileInputStream f = new FileInputStream(ZIPFILE_PATHNAME);
         Scanner zipFile = new Scanner(f);
         while (zipFile.hasNext())
         {
            zipMap.put(zipFile.next(), 
                  new Double[] {zipFile.nextDouble(), zipFile.nextDouble()});
         }
         f.close();
      }
      catch (IOException e)
      {
         System.err.println(e+" : "+ZIPFILE_PATHNAME);
      }
   }

      
   private static HashMap<String, Double[]> zipMap = 
      new HashMap<String, Double[]>();




   public static void main(String[] args)
   {
      System.out.println("Distance between "+args[0]+" and "+args[1]+" is "+
            distanceBetween(args[0], args[1]));
   }
}
