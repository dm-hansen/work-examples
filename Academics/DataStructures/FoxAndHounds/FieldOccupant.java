import java.awt.Color;

/**
 * Abstract parent class for objects that can occupy a cell in the Field
 */
public abstract class FieldOccupant { 
   /**
    * @return the color to use for a cell containing a particular kind
    *         of occupant
    */
   abstract public Color getDisplayColor();
}
