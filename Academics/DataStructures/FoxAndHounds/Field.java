import java.util.Set;
import java.util.HashSet;

/**
 *  The Field class defines an object that models a field full of foxes and
 *  hounds. Descriptions of the methods you must implement appear below. 
 */
public class Field {
   /**
    *  Creates an empty field of given width and height
    *
    *  @param width of the field.
    *  @param height of the field.
    */
   public Field(int width, int height) {
      _occupants = new FieldOccupant[width][height];
   } // Field

   
   /**
    *  @return the width of the field.
    */
   public int getWidth() {
       return _occupants.length;
   } // getWidth


   /**
    *  @return the height of the field.
    */
   public int getHeight() {
       return _occupants[0].length;
   } // getHeight


   /**
    *  Place an occupant in cell (x, y).
    *
    *  @param x is the x-coordinate of the cell to place a mammal in.
    *  @param y is the y-coordinate of the cell to place a mammal in.
    *  @param toAdd is the occupant to place.
    */
   public void setOccupantAt(int x, int y, FieldOccupant toAdd) {
      _occupants[normalizeIndex(x, WIDTH_INDEX)]
                [normalizeIndex(y, !WIDTH_INDEX)] = toAdd;
   } // setOccupantAt


   /**
    *  @param x is the x-coordinate of the cell whose contents are queried.
    *  @param y is the y-coordinate of the cell whose contents are queried.
    *
    *  @return occupant of the cell (or null if unoccupied)
    */
   public FieldOccupant getOccupantAt(int x, int y) {
      return _occupants[normalizeIndex(x, WIDTH_INDEX)]
                       [normalizeIndex(y, !WIDTH_INDEX)];
   } // getOccupantAt


   /**
    *  @param x is the x-coordinate of the cell whose contents are queried.
    *  @param y is the y-coordinate of the cell whose contents are queried.
    *
    *  @return true if the cell is occupied
    */
   public boolean isOccupied(int x, int y) {
      return getOccupantAt(x,y) != null;
   } // isOccupied


   /**
    * @return a collection of the occupants of cells adjacent to the
    * given cell; collection does not include null objects
    */
   public Set<FieldOccupant> getNeighborsOf(int x, int y) {
      // For any cell there are 8 neighbors - left, right, above, below,
      // and the four diagonals. Define a collection of offset pairs that 
      // we'll step through to access each of the 8 neighbors
      final int[][] indexOffsets = { {0,1}, {1,0}, {0,-1}, {-1, 0}, {1,1}, 
                                     {1, -1}, {-1, 1}, {-1, -1}
                                   };
      Set<FieldOccupant> neighbors = new HashSet<FieldOccupant>();


      // Iterate over the set of offsets, adding them to the x and y
      // indexes to check the neighboring cells
      for (int[] offset : indexOffsets) {
         // If there's something at that location, add it to our
         // neighbor set
         if (getOccupantAt(x + offset[0], y + offset[1]) != null) {
            neighbors.add(getOccupantAt(x + offset[0], y + offset[1]));
         }
      }

      return neighbors;

   } // getNeighborsOf


   /**
    * Normalize an index (positive or negative) by translating it to a legal reference within
    * the bounds of the field
    *
    * @param index to normalize
    * @param isWidth is true when normalizing a width reference, false if
    *                a height reference
    *
    * @return the normalized index value 
    */
   private int normalizeIndex(int index, boolean isWidthIndex) {
      // Set the bounds depending on whether we're working with the
      // width or height (i.e., !width)
      int bounds = isWidthIndex ? getWidth() : getHeight();

      // If x is non-negative use modulo arithmetic to wrap around
      if (index >= 0) {
         return index % bounds;
      }
      // For negative values we convert to positive, mod the bounds and
      // then subtract from the width (i.e., we count from bounds down to
      // 0. If we get say, -12 on a field 10 wide, we convert -12 to
      // 12, mod with 10 to get 2 and then subract that from 10 to get 8)
      else {
         return bounds - (-index % bounds);
      }
   }


   /**
    *  Define any variables associated with a Field object here.  These
    *  variables MUST be private.
    */
   private FieldOccupant[][] _occupants;

   // Used in index normalizing method to distinguish between x and y
   // indices
   private final static boolean WIDTH_INDEX = true;

}
