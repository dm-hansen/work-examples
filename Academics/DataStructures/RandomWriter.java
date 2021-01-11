import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.io.PrintStream;
import java.io.IOException;

/**
 * RandomWriter generates pseudo-random output that corresponds to
 * patterns learned from input files
 * 
 * @author David M. Hansen
 * @version 1.0
 */
public class RandomWriter {

   /**
    * Add information to the map about what characters follow substrings 
    * in the input. Each substring is mapped to a list of characters 
    * found to follow that substring in the input
    * 
    * @param mapOfStringToChars is existing map of substrings to list of
    *                           characters that follow them
    * @param input stream to process
    * @param seedLength is the length of substrings 
    *
    * @return the populated map of substring to character list
    */
   private static Map<String, List<Character>> buildMapFromInput(
                           Map<String, List<Character>> mapOfStringToChars, 
                           Reader input, int seedLength)
         throws java.io.IOException {
      final int EOF = -1;
      int nextCharacter; // int is what read() returns; we'll cast to char
      // Create a string builder to use as a mutable "seed" - the
      // current substring
      StringBuilder currentSeed = new StringBuilder(seedLength); 
   
      // We need the first N characters from the input as our first
      // "seed"; if we happen to have an input with fewer characters than
      // the length of the seed we'll end up with EOF at the end of the
      // currentSeed and will just exit below when we try to read
      // further
      for (int i=0; i<seedLength; i++) {
         currentSeed.append((char) input.read());
      }

      // Read characters from the input until we hit EOF. For each
      // character we read, remember that we saw this character
      // following the currentSeed string and then drop the first
      // character and append the next character to the seed.
      while ((nextCharacter = input.read()) != EOF) {
         // If we haven't seen this seed before, add a new list of
         // characters to the map for this seed
         if (!mapOfStringToChars.containsKey(currentSeed.toString())) {
            mapOfStringToChars.put(currentSeed.toString(), 
                                   new ArrayList<Character>());
         }

         // Remember we saw this current character following the current
         // seed
         mapOfStringToChars.get(currentSeed.toString()).
            add((char) nextCharacter);
        
         // Drop the first character and append the next character the
         // seed and go back to get the next character
         currentSeed.deleteCharAt(0).append((char) nextCharacter);

      } // while characters in the input

      return mapOfStringToChars;

   } // buildMapFromInput


   /**
    * Outputs pseudo-random characters from the given map which maps
    * strings to lists of characters that can follow that string. Output
    * is based on the frequency of characters found in lists mapped to
    * string keys
    * 
    * @param mapOfStringToChars maps strings to a list of characters 
    *                           that may occur after the given string 
    *                           in the output
    * @param numCharsToOutput is number of characters to write to 
    *                         the output stream
    * @param output is the output stream to write to
    */
   private static void outputRandomTextFromMap(
         Map<String, List<Character>> mapOfStringToChars, 
         int numCharsToOutput, PrintStream output) {
      char nextCharacter;
      List<Character> charactersFollowingSeed;
      // Get an array of all the seeds (keys) in the map that we can 
      // use to pick a random seed to start with and if we reach a dead-end
      Object[] allSeeds = mapOfStringToChars.keySet().toArray();
      // Create a string builder to use as the current "seed";
      // initialize it with a randomly chosen seed from the map
      StringBuilder currentSeed = new StringBuilder(
         (String) allSeeds[(int)(Math.random() * allSeeds.length)]); 
   
      // Output the required number of characters
      for (int i=0; i<numCharsToOutput; i++) {
         // Make sure the current seed has some characters to choose
         // from; if not, randomly pick a new seed until we get one that
         // does
         while (mapOfStringToChars.get(currentSeed.toString()) == null ||
                mapOfStringToChars.get(currentSeed.toString()).size() == 0) {
            // Pick a new seed randomly from all seeds and replace the
            // contents of our current seed with that randomly chosen
            // seed
            currentSeed.replace(0, currentSeed.length()-1, 
               (String) allSeeds[(int)(Math.random() * allSeeds.length)]); 
         }

         // Pick a random character from the characters that follow the
         // current seed and output that character
         charactersFollowingSeed = 
            mapOfStringToChars.get(currentSeed.toString());

         // Using the list for this seed, choose random character 
         nextCharacter = 
            charactersFollowingSeed.
               get((int)(Math.random() * charactersFollowingSeed.size()));

         output.write(nextCharacter);

         // Now mutate the current seed by dropping the first character
         // and appending the character we just output
         currentSeed.deleteCharAt(0).append(nextCharacter);

      } // for number of characters to output

   } // outputRandomTextFromMap



   /**
    * Print pseudo-random output that is similar to the style of writing
    * "learned from the input 
    *
    * @param args[] seed-length, number of characters to output,
    *               optional list of files - read from System.in 
    *               if no files given
    */
   public static void main(String[] args) throws java.io.IOException {
      final int MIN_ARGS = 2;
      final String USAGE_MESSAGE = 
         "Usage: RandomWriter seed-length output-size [pathname list]";
      int seedLength = 0;
      int numCharsToOutput = 0;
      BufferedReader currentInputStream;
      // A map mapping substrings from the input files to the list of
      // characters that followed that substring
      Map<String, List<Character>> mapOfStringToChars = 
         new HashMap<String, List<Character>>();
   
      // Check to make sure we have sufficient arguments - must have 
      // at least a seed-length and number of characters to generate
      if (args.length < MIN_ARGS) {
         // Display an error message and exit.
         System.err.println("Insufficient Arguments\n\t"+USAGE_MESSAGE);
         System.exit(1);
      }
      
      // Try parsing the two input parameters; make sure they're positive
      try {
         seedLength = Integer.parseInt(args[0]);
         numCharsToOutput = Integer.parseInt(args[1]);
         // Make sure they're positive ints
         if (seedLength <= 0 || numCharsToOutput <= 0) {
            throw new NumberFormatException(
                  "Parameters must be positive numbers");
         }
      }
      catch (NumberFormatException e) {
         // Display and error message and exit.
         System.err.println(e.getMessage()+"\n\t"+USAGE_MESSAGE);
         System.exit(1);
      }
   
      // If we have filename arguments then process those. Otherwise
      // we'll take input from System.in
      if (args.length > MIN_ARGS) {
         // Filenames begin with the first "optional" argument. For each 
         // file, open and "learn" the pattern of what characters follow 
         // substrings
         for (int i=MIN_ARGS; i < args.length; i++) {
            // Open the file and pass it to the learning method to add
            // the contents of the file to the map of strings to
            // characters
            try {
               currentInputStream = 
                  new BufferedReader(new FileReader(args[i]));
               mapOfStringToChars = 
                  buildMapFromInput(mapOfStringToChars, 
                                    currentInputStream, seedLength);
               currentInputStream.close();
            }
            // If there's an error, we'll quit - force user to get it right
            catch (IOException e) {
               System.err.println("Error processing file: "+args[i]);
               System.exit(1);
            }
         } // for
      } 
      else { // No filenames, read and learn from System.in
         mapOfStringToChars = 
            buildMapFromInput(mapOfStringToChars, 
                  new BufferedReader(new InputStreamReader(System.in)), 
                  seedLength);
      }


      // If we have a populated map then we must have had valid input so
      // generate the output
      if (!mapOfStringToChars.isEmpty()) {
         // Generate output based on what we learned and print 
         // that to System.out
         outputRandomTextFromMap(mapOfStringToChars, numCharsToOutput, 
                                 System.out);
      }
      else { // Must not have read sufficient input - sort of an error...
         System.err.println("Insufficient Input");
         System.exit(1);
      }

   } // main


} // RandomWriter
