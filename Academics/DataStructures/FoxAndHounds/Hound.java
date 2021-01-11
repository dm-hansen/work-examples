import java.awt.Color;

/**
 * Hounds can display themsevles. They also get hungry
 */
public class Hound extends FieldOccupant { 
   /**
    * Create a hound 
    */
   public Hound() {
      // Start out well-fed
      eats();
   }


   /**
    * @return true if this Hound has starved to death
    */
   public boolean hasStarved() {
      return _fedStatus == 0;
   }


   /**
    * Make this Hound hungrier
    *
    * @return true if the Hound has starved to death
    */
   public boolean getHungrier() {
      // Decrease the fed status of this Hound
      _fedStatus--;
      return hasStarved();
   }


   public void eats() {
      // Reset the fed status of this Hound
      _fedStatus = _houndStarveTime;
   }


   /**
    * @return the color to use for a cell occupied by a Hound
    */
   @Override
   public Color getDisplayColor() {
      return Color.red;
   } // getDisplayColor


   /**
    * @return the text representing a Hound
    */
   @Override
   public String toString() {
      return "H";
   }


   /**
    * Sets the starve time for this class
    *
    * @param starveTime 
    */
   public static void setStarveTime(int starveTime) {
      _houndStarveTime = starveTime;
   }

   
   /**
    * @return the starve time for Hounds
    */
   public static int getStarveTime() {
      return _houndStarveTime;
   }


   // Default starve time for Hounds
   public static final int DEFAULT_STARVE_TIME = 3;
   private static int _houndStarveTime = DEFAULT_STARVE_TIME; // Class variable for all hounds

   // Instance attributes to keep track of how hungry we are
   private int _fedStatus;
}
