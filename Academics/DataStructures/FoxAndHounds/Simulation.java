import java.awt.*;
import java.util.*;

/**
 *  The Simulation class is a program that runs and animates a simulation of
 *  Foxes and Hounds.
 */

public class Simulation { 
   // The constant CELL_SIZE determines the size of each cell on the 
   // screen during animation.  (You may change this if you wish.)
   private static final int CELL_SIZE = 20;
   private static final String USAGE_MESSAGE = "Usage: java Simulation [--graphics] [--width int] [--height int] [--starvetime int] [--fox float] [--hound float]";


   /**
    * Computes the next state of the field from the current state and
    * returns the new state
    *
    * @param currentState is the current state of the Field
    *
    * @return new field state after one timestep
    */
   private static Field performTimestep(Field currentState) {
      // Define two constants that set bounds for minimum number
      // of hounds and foxes nearby before actions are taken
      final int MIN_HOUND_NEIGHBORS = 2; 
      final int MIN_FOX_NEIGHBORS = 2; 

      // Create the next state of the field; initially empty
      Field nextState = new Field(currentState.getWidth(), 
                                  currentState.getHeight());
      Set<FieldOccupant> neighbors;
      int neighboringHounds = 0;
      int neighboringFoxes = 0;
      FieldOccupant occupant = null;

      // Iterate over the current field's state and set up the
      // corresponding cell in the next state based on the 
      // occupants of the neighboring cells in the current state
      for (int i = 0; i<currentState.getWidth(); i++) { 
         for (int j = 0; j<currentState.getHeight(); j++) {
            // Iterate over the neighbors and see how many foxes and
            // hounds are nearby
            neighboringFoxes = 0;
            neighboringHounds = 0;
            for (FieldOccupant neighbor : currentState.getNeighborsOf(i, j)) {
               if (neighbor instanceof Fox) {
                  neighboringFoxes++;
               }
               else if (neighbor instanceof Hound) {
                  neighboringHounds++;
               }
            } // for

            // If this cell is occupied, change it's state based on what
            // it holds and what the neighboring cells hold
            if (currentState.isOccupied(i, j)) {
               occupant = currentState.getOccupantAt(i, j);

               // If a cell contains a Hound update its status based on
               // the neighbors as described below
               if (occupant instanceof Hound) {
                  // If any of its neighbors is a Fox, then the Hound eats 
                  // during the timestep, and it remains in the cell at 
                  // the end of the timestep with its hunger completely 
                  // gone. (We may have multiple Hounds sharing the same 
                  // Fox. This is fine; miraculously, they all get enough 
                  // to eat.)
                  if (neighboringFoxes > 0) {
                     ((Hound) occupant).eats();
                     nextState.setOccupantAt(i, j, occupant);
                  }
                  // If none of its neighbors is a Fox, it gets hungrier 
                  // during the timestep. If this timestep is the 
                  // (starveTime + 1)th consecutive timestep the Hound 
                  // has gone without eating, then the Hound dies 
                  // (disappears). Otherwise, it remains in the cell but 
                  // gets closer to starvation.
                  else {
                     ((Hound) occupant).getHungrier();
                     // If still alive, the hounds lives on in the
                     // new state, otherwise it's gone
                     if (!((Hound) occupant).hasStarved()) {
                        nextState.setOccupantAt(i, j, occupant);
                     }
                  }
               } // if Hound

               // Otherwise if a cell contains a Fox update its status 
               // based on the neighbors as described below
               else if (occupant instanceof Fox) {
                  // If enough of its neighbors are Hounds, then a 
                  // new Hound is born in this cell. Hounds are well-fed 
                  // at birth.
                  if (neighboringHounds >= MIN_HOUND_NEIGHBORS) {
                     // A new Hound replaces the Fox
                     nextState.setOccupantAt(i, j, new Hound());
                  }
                  // If all of its neighbor cells are either empty or 
                  // contain other Foxes, (i.e., no Hounds) then the 
                  // Fox stays where it is.  
                  else if (neighboringHounds == 0) {
                     nextState.setOccupantAt(i, j, occupant);
                  }
                  // If one of its neighbors is a Hound, then the Fox is 
                  // eaten by a Hound, and therefore disappears (i.e., 
                  // we don't put anything in the cell in the new state)
               } // if Fox
            } // if occupied

            else { // Cell is empty
               // If a minimum number of neighbors are Foxes, and at 
               // most one of its neighbors is a Hound, then a new Fox 
               // is born in that cell.
               if (neighboringHounds <= 1 
                   && neighboringFoxes >= MIN_FOX_NEIGHBORS) {
                  nextState.setOccupantAt(i, j, new Fox());
               }
               // If a minimum number of neighbors are Foxes, and a 
               // minimum number of neighbors are Hounds, then a 
               // new well-fed Hound is born in that cell.
               else if (neighboringHounds >= MIN_HOUND_NEIGHBORS 
                        && neighboringFoxes >= MIN_FOX_NEIGHBORS) {
                  nextState.setOccupantAt(i, j, new Hound());
               }
               // If a cell is empty, and fewer than the minimum number 
               // of neighbors contain Foxes, then the cell remains 
               // empty (i.e., we don't put anyting in the cell in 
               // the new state).
            } // empty cell
         } // for each row
      } // for each column

      return nextState;

   } // performTimestep


   /**
    * Draws the current state of the field
    *
    * @param graphicsContext is an optional GUI window to draw to
    * @param theField is the object to display
    */
   private static void drawField(Graphics graphicsContext, Field theField) {
      // If we have a graphics context then update the GUI, otherwise
      // output text-based display
      if (graphicsContext != null) {
         // Iterate over the cells and draw the thing in that cell
         for (int i = 0; i < theField.getHeight(); i++) {
            for (int j = 0; j < theField.getWidth(); j++) {
               // Get the color of the object in that cell and set the cell color
               if (theField.isOccupied(j,i)) {
                  graphicsContext.setColor(theField.getOccupantAt(j,i).getDisplayColor());       
               }
               else {
                  graphicsContext.setColor(Color.white);
               }
               graphicsContext.fillRect(j * CELL_SIZE, 
                                        i * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            } // for
         } // for
      }
      else { // No graphics, just text
         // Draw a line above the field
         for (int i = 0; i < theField.getWidth() * 2 + 1; i++) {
            System.out.print("-");
         }
         System.out.println();
         // For each cell, display the thing in that cell
         for (int i = 0; i < theField.getHeight(); i++) {
            System.out.print("|"); // separate cells with '|' 
            for (int j = 0; j < theField.getWidth(); j++) {
               if (theField.isOccupied(j,i)) {
                  System.out.print(theField.getOccupantAt(j,i)+"|");
               }
               else {
                  System.out.print(" |");
               }
            }
            System.out.println();
         } // for

         // Draw a line below the field
         for (int i = 0; i < theField.getWidth() * 2 + 1; i++) {
            System.out.print("-");
         }
         System.out.println();

      } // else
   } // drawField


   /**
    *  Main reads the parameters and performs the simulation and animation.
    */
   public static void main(String[] args) throws InterruptedException {
      final int ONE_SECOND = 1000; // 1,000ms
      /**
       *  Default parameters.  (You may change these if you wish.)
       */
      int width = 50;                              // Default width
      int height  = 25;                            // Default height
      int starveTime = Hound.DEFAULT_STARVE_TIME;  // Default starvation time
      double probabilityFox = 0.5;                 // Default probability of fox 
      double probabilityHound = 0.15;              // Default probability of hound
      boolean graphicsMode = false;                // Do NOT change the default
      Random randomGenerator = new Random();      
      Field theField = null;

      // If we attach a GUI to this program, these objects will hold
      // references to the GUI elements
      Frame windowFrame = null;
      Graphics graphicsContext = null;
      Canvas drawingCanvas = null;

      /*
       *  Process the input parameters. Switches we understand include:
       *  --graphics for "graphics" mode
       *  --width 999 to set the "width" 
       *  --height 999 to set the height
       *  --starvetime 999 to set the "starve time"
       *  --fox 0.999 to set the "fox probability"
       *  --hound 0.999 to set the "hound probability"
       */
      for (int argNum=0; argNum < args.length; argNum++) {
         try {
            switch(args[argNum]) {
               case "--graphics":  // Graphics mode
                  graphicsMode = true;
                  break;
                  
               case "--width": // Set width
                  width = Integer.parseInt(args[++argNum]);
                  break;

               case "--height": // set height
                  height = Integer.parseInt(args[++argNum]);
                  break;

               case "--starvetime": // set 'starve time'
                  starveTime = Integer.parseInt(args[++argNum]);
                  break;

               case "--fox": // set the probability for adding a fox
                  probabilityFox = Double.parseDouble(args[++argNum]);
                  break;

               case "--hound": // set the probability for adding a hound
                  probabilityHound = Double.parseDouble(args[++argNum]);
                  break;

               default: // Anything else is an error and we'll quit
                  System.err.println("Unrecognized switch.");
                  System.err.println(USAGE_MESSAGE);
                  System.exit(1);
            } // switch
         }
         catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            System.err.println("Illegal or missing argument.");
            System.err.println(USAGE_MESSAGE);
            System.exit(1);
         }
      } // for

      // Create the initial Field.
      theField = new Field(width, height);

      // Set the starve time for hounds
      Hound.setStarveTime(starveTime);

      // Visit each cell; randomly placing a Fox, Hound, or nothing in each.
      for (int i=0; i < theField.getWidth(); i++) {
         for (int j=0; j < theField.getHeight(); j++) {
            // If a random number is less than or equal to the probability
            // of adding a fox, then place a fox
            if (randomGenerator.nextFloat() <= probabilityFox) {
               theField.setOccupantAt(i, j, new Fox());
            } 
            // Otherwise if a random number is less than or equal to the 
            // probability of adding a hound, then place a hound. 
            else if (randomGenerator.nextFloat() <= probabilityHound) {    
               theField.setOccupantAt(i, j, new Hound());
            }
         } // for
      } // for

      // If we're in graphics mode, then create the frame, canvas, 
      // and window. If not in graphics mode, these will remain null
      if (graphicsMode) {
         windowFrame = new Frame("Foxes and Hounds");
         windowFrame.setSize(theField.getWidth() * CELL_SIZE + 10, 
                             theField.getHeight() * CELL_SIZE + 30);
         windowFrame.setVisible(true);

         // Create a "Canvas" we can draw upon; attach it to the window.
         drawingCanvas = new Canvas();
         drawingCanvas.setBackground(Color.white);
         drawingCanvas.setSize(theField.getWidth() * CELL_SIZE, 
                               theField.getHeight() * CELL_SIZE);
         windowFrame.add(drawingCanvas);
         graphicsContext = drawingCanvas.getGraphics();
      } // if 

      // Loop infinitely, performing timesteps. We could optionally stop
      // when the Field becomes empty or full, though there is no
      // guarantee either of those will ever arise...
      while (true) {                                              
         Thread.sleep(ONE_SECOND);                        // Wait one second 
         drawField(graphicsContext, theField);            // Draw the current state 
         theField = performTimestep(theField);            // Simulate a timestep
      }

   } // main

} 
