import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Scanner;
import java.util.NoSuchElementException;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;

/**
 * A general-purpose implementation of a directed graph.
 *
 */

public class Graph<T> {

   public final static int NO_EDGE = -1; // No edge present between vertices

   private HashMap<T, HashMap<T, Integer>> _adjacencyMap;
   private boolean _isDirected;


   private Graph(){};

   /**
    * Create an empty graph
    *
    * @param isDirected is true if this is a directed graph
    */
   public Graph(boolean isDirected) {
      _isDirected = isDirected;
      _adjacencyMap = new HashMap<>();
   }



   /**
    * Create a weighted graph using the given vertex values.
    *
    * Edges between any two vertices are randomly added with random weight 
    *
    * @param isDirected is true if this is a directed graph
    * @param veritces a list of vertices
    * @param density determines probablility of an edge existing between
    *                any two random vertices; 1.0==totally connected, 
    *                0.0==no edges 
    * @param randomNumberGenerator optional seeded generator for 
    *                              repeatable results; if null, graph
    *                              is unrepeatably random.
    */
   public Graph(boolean isDirected, Collection<T> vertices, double density, 
                Random randomNumberGenerator) {
      // Construct empty graph
      this(isDirected);

      // Set a reasonable maximum length on edges to be the number of
      // vertices * 100 so that we don't have hugely long paths to deal
      // with yet have a reasonably large space for random edge values; 
      // watching out for integer overflow
      int maxEdgeLength = vertices.size()*100 > Integer.MAX_VALUE 
                          ? Integer.MAX_VALUE : vertices.size()*100;

      // Get the vertices as an array so we can explicitly iterate over
      // the list by an index
      @SuppressWarnings("unchecked")
      T[] vertexArray = (T[]) vertices.toArray();

      // If we should seed the random generator, do so
      if (randomNumberGenerator == null) {
         randomNumberGenerator = new Random();
      }

      // Add vertices
      for (T vertex : vertices) {
         addVertex(vertex);
      }

      // Populate the graph with random edges of random length. For each
      // vertex, probabilistically add an edge to every other vertex
      for (int i=0; i < vertexArray.length; i++) {
         // Consider adding an edge to every other vertex.
         // Note: if this is a directed graph we iterate over ALL
         // vertices but if undirected we only consider vertices 
         // beyond the current vertex since the graph is symmetrical 
         // and we only need to add one of the edges 
         for (int j = isDirected ? 0 : i+1; j < vertexArray.length; j++) {
            // Probabilistically add an edge of some random length
            if (randomNumberGenerator.nextFloat() < density) {
               // Add an edge of random length between these vertices;
               // we only want positive values > 0 so add 1 to the
               // random number to avoid 0-length edges.
               addEdge(vertexArray[i], vertexArray[j],
                     randomNumberGenerator.nextInt(maxEdgeLength)+1);
            }
         } // for
      } // for
   } // Graph


   /**
    * Ensure a particular path exists in the graph by adding any missing
    * edges necessary to complete the path. Generally used to
    * ensure that at least one tour (i.e., path including all vertices
    * and wrapping back around to the first vertex) exists in the graph.
    *
    * If used to ensure that a tour exists, this method must also be 
    * called after adding or removing a vertex to re-establish a 
    * tour, or removing an edge that may be part of an existing tour.
    *
    * @param aPath the sequence of connected vertices that should exist 
    *              in the graph
    */
   public void ensurePath(List<T> aPath) {
      // Make sure each vertex in the given path is connected to 
      // the vertex next to it, wrapping around at the end to the beginning
      for (int i=0; i < aPath.size()-1; i++) {
         if (!edgeExists(aPath.get(i), aPath.get(i+1))) {
            // No existing edge so add an edge of unit-weight 
            addEdge(aPath.get(i), aPath.get(i+1), 1);
         }
      }
   } // ensurePath


   /**
    * Add a vertex. Has no effect if the vertex already exists. Note
    * that caller must maintain a "tour," if one explicitly exists
    *
    * @param vertex to add
    *
    * @throws IllegalArgumentException if vertex already exists 
    */
   public void addVertex(T vertex) throws IllegalArgumentException {
      if (_adjacencyMap.containsKey(vertex)) {
         throw new IllegalArgumentException("Vertex "+vertex+" already exists");
      }
      _adjacencyMap.put(vertex, new HashMap<>());
   } // addVertex


   /**
    * Remove a vertex. Note that caller must maintain a "tour," if one
    * explicitly exists.
    *
    * @param vertex to remove
    *
    * @throws NoSuchElementException if vertex does not exist
    */
   public void removeVertex(T vertex) throws NoSuchElementException {
      if (!_adjacencyMap.containsKey(vertex)) {
         throw new NoSuchElementException("Vertex "+vertex+" does not exist");
      }
      // Remove any edges to this vertex
      for (T aVertex: _adjacencyMap.keySet()) {
         if (edgeExists(aVertex, vertex)) {
            removeEdge(aVertex, vertex);
         }
      }
      // Remove the vertex; essentially removes any edges from this vertex as
      // well
      _adjacencyMap.remove(vertex);
   } // removeVertex


   /**
    * @return list of vertices
    */
   public List<T> getVertices() {
      return new ArrayList<>(_adjacencyMap.keySet());
   } // getVertices 


   /**
    * Add an edge
    *
    * @param source source vertex
    * @param destination destination vertex
    * @param weight of the edge
    *
    * @throws IllegalArgumentException if weight is &lt; 0 or edge to itself
    * @throws NoSuchElementException if source or destination do not exist
    */
   public void addEdge(T source, T destination, int weight) 
      throws IllegalArgumentException, NoSuchElementException {
      // Make sure preconditions are met; if not, throw exception
      if (weight < 0) {
         throw new IllegalArgumentException("Weight can not be negative");
      }
      if (source.equals(destination)) {
         throw new IllegalArgumentException("Source and destination are the same");
      }
      if (!_adjacencyMap.containsKey(source)
            || !_adjacencyMap.containsKey(destination)) {
         throw new NoSuchElementException("Vertex does not exist");
      }
      // OK, preconditions met, add the edge
      _adjacencyMap.get(source).put(destination, weight);
      // If this is not a directed graph, add the inverse path. 
      if (!_isDirected) {
         _adjacencyMap.get(destination).put(source, weight);
      }

   } // addEdge


   /**
    * @param source vertex
    * @param destination vertex
    *
    * @return edge between source and destination
    *
    * @throws NoSuchElementException if source, destination or edge does not exist
    */
   public Edge<T> getEdge(T source, T destination) {
      Edge<T> anEdge = null;
      if (edgeExists(source, destination)) {
         anEdge = new Edge<>(source, destination, getEdgeWeight(source,destination));
      }
      return anEdge;
   } // getEdge


   /**
    * @param source vertex
    * @param destination vertex
    *
    * @return weight betwen source and destination, -1 if no edge
    */
   public int getEdgeWeight(T source, T destination) {
      int weight = NO_EDGE;
      // Make sure we have the source and an edge to the destination
      if (_adjacencyMap.containsKey(source)
          && _adjacencyMap.get(source).containsKey(destination)) {
            weight = _adjacencyMap.get(source).get(destination);
      }
      return weight;
   } // getEdgeWeight


   /**
    * @param source vertex
    * @param destination vertex
    *
    * @return true if an edge exists between source and destination
    */
   public boolean edgeExists(T source, T destination) {
      return getEdgeWeight(source, destination) != NO_EDGE;
   } // edgeExists


   /**
    * Remove the edge from source to destination. 
    *
    * @param source vertex
    * @param destination vertex
    *
    * @throws NoSuchElementException if source, destination or edge does not exist
    */
   public void removeEdge(T source, T destination) {
      if (!edgeExists(source, destination)) {
         throw new NoSuchElementException("Edge does not exist");
      }
      _adjacencyMap.get(source).remove(destination);
      // If this is not a directed graph, remove the inverse edge. 
      if (!_isDirected) {
         _adjacencyMap.get(destination).remove(source);
      }
   } // removeEdge


   /**
    * @return list of edges
    */
   public List<Edge<T>> getEdges() {
      ArrayList<Edge<T>> edges = new ArrayList<>();
      List<T> vertices = getVertices();
      // Add every edge that exists between any two vertices to the
      // list. Note that if this is NOT a directed graph, we only want
      // to see an edge once so we'll only look for edges to vertices
      // that are "greater" than the one we're looking at
      for (int i=0; i<vertices.size(); i++) {
         for (int j=0; j<vertices.size(); j++) {
            // Don't include duplicate edges if this is an undirected graph
            // so for undirected, only grab where j > i so we get the
            // edge (i, j) but not (j, i)
            if (_isDirected || j > i) {
               if (edgeExists(vertices.get(i), vertices.get(j))) {
                  edges.add(getEdge(vertices.get(i), vertices.get(j)));
               }
            }
         }
      }
      return edges;
   } // getEdges


   /**
    * @param source originating vertex of edges
    *
    * @return list of edges originating from the given source
    */
   private List<Edge<T>> getEdgesFrom(T source) {
      List<Edge<T>> edges = new ArrayList<>();
      Map<T, Integer> adjacentToSource = _adjacencyMap.get(source);
      // Add every edge that exists between the source and any
      // destination
      for (T destination : adjacentToSource.keySet()) {
         edges.add(new Edge<>(source, destination, adjacentToSource.get(destination)));
      }
      return edges;
   } // getEdgesFrom


   /**
    * @return the graph as a string
    */
   @Override
   public String toString() {
      return _adjacencyMap.toString();
   } // toString


   /**
    * @param path a list of connected vertices
    *
    * @return the length of the given path; -1 if not a path
    */
   public long pathLength(List<T> aPath) {
      // Initialize the length to -1 if path is empty, otherwise 0
      int length = (aPath.size() == 0 ? NO_EDGE : 0);

      // Starting with the first vertex to the next-to-last, if there's
      // an edge between the adjacent vertices, add it to the total; if
      // no edge then set the length to NO_EDGE and exit.
      for (int i=0; i<aPath.size()-1 && length > NO_EDGE; i++) {
         if (edgeExists(aPath.get(i), aPath.get(i+1))) {
            length += getEdgeWeight(aPath.get(i), aPath.get(i+1));
         }
         else {
            length = NO_EDGE; // and we'll exit and be done
         }
      }
      return length;
   } // pathLength


   /** 
    * Find the shortest path between two vertices using Djikstra's
    * algorithm
    *
    * @param source vertex
    * @param destination vertex
    *
    * @return shortest path from source to destination; null if no path
    * exists
    *
    * @throws NoSuchElementException if source or destination do not exist
    */
   public List<Edge<T>> shortestPathBetween(T source, T destination) 
              throws NoSuchElementException {
      LinkedList<Edge<T>> shortestPath = null; // Allocate if we find one
      // Use a priority queue of edges to add
      PriorityQueue<Edge<T>> edgeQueue = new PriorityQueue<>();
      // Map of destionation-source so we can walk forwards later and
      // return the path
      Map<T, T> backPath = new HashMap<>();
      // Map of the vertices we've found - with distance to source
      Map<T, Integer> reachable = new HashMap<>();
      Edge<T> nextEdge;
      // Prime the pump with the source as the "last vertex" and a
      // distance to itself of 0
      T lastVertex = source;
      reachable.put(source, 0);

      // Quick check to make sure source and destination even exist
      if (!_adjacencyMap.containsKey(source) 
            || !_adjacencyMap.containsKey(destination)) {
         throw new NoSuchElementException("Vertex does not exist");
      }

      // Until we reach our destination or run out of edges to consider,
      // add the next shortest edge from any vertex we know about to any
      // vertex we haven't reached
      do {
         // Add to the priority queue vertices adjacent to the last 
         // vertex added where the distance is the total distance from
         // source to the adjacent vertex through this last vertex. Note that
         // this MAY add a duplicate vertex to the queue, but in
         // considering that destination we'd only add it via the 
         // shortest path and would later discard any duplicate entries.
         for (Edge<T> edge : getEdgesFrom(lastVertex)) {
            int totalDistance = reachable.get(lastVertex);
            // Add an "edge" whose length is the current total distance
            // from the original source the the last vertex PLUS the
            // distance from that last vertex to this adjacent vertex
            edgeQueue.add(new Edge<>(edge.getSource(), 
                     edge.getDestination(), 
                     edge.getWeight() + totalDistance));
         }

         // See if we can find a new shortest edge to add among the
         // remaining vertices yet to be explored. Dequeue vertices
         // until we run out or we find one that hasn't already been
         // added to the set of vertices reachable from the source
         do {
            nextEdge = edgeQueue.poll();
         } while (nextEdge != null && reachable.containsKey(nextEdge.getDestination()));

         // We either have an edge to a new vertex or we ran out 
         // of edges to consider. If we have an edge, add it to the
         // backwards path and update the last vertex
         if (nextEdge != null) {
            backPath.put(nextEdge.getDestination(), nextEdge.getSource());
            reachable.put(nextEdge.getDestination(), nextEdge.getWeight());
            lastVertex = nextEdge.getDestination();
         }

      } while (nextEdge !=null && !reachable.containsKey(destination));

      // If we have a path then reachable contains the destination. In
      // that case, recreate the path by walking it backwards and adding
      // the edges that comprise the path
      if (reachable.containsKey(destination)) {
         // Walking backwards starting with the destination
         T vertex = destination;
         shortestPath = new LinkedList<>();
         // Walk backwards through the backPath and add edges in reverse
         // order to the shortest path list
         while (!vertex.equals(source)) {
            // Get the previous vertex
            T previous = backPath.get(vertex);
            // Put the edge at the head of the list and step back to the
            // previous vertex
            shortestPath.addFirst(getEdge(previous, vertex));
            vertex = previous;
         }
      }

      // Shortest paths is now populated or null if no path was found
      return shortestPath;

   } // shortestPathBetween


   /** 
    * Creates and returns a Graph with edges representing a minimal
    * spanning tree
    *
    * Uses Prim's algorithm
    *
    * @return Graph with minimal edges; or null if no tree exists
    *
    * @throws IllegalStateException if this graph is directed
    */
   public Graph<T> minimumSpanningTree() throws IllegalStateException {
      Graph<T> spanningTree = new Graph<>(false);
      // Use a priority queue of edges to add
      PriorityQueue<Edge<T>> edgeQueue = new PriorityQueue<>();
      Edge<T> nextEdge;
      T lastVertex;

      // Quick check to make sure source and destination even exist
      if (_isDirected) {
         throw new IllegalStateException("Graph is directed");
      }
      ///////// Naughty Hack
      if (_adjacencyMap.isEmpty()) {
         return null;
      }

      // Prime the pump by adding any vertex in the graph to the
      // spanningTree
      lastVertex = _adjacencyMap.keySet().iterator().next();
      spanningTree.addVertex(lastVertex);

      // Until the spanning tree contains all the vertices or we
      // run out of edges to consider, add the vertex reachable via
      // th shortest edge to to any vertex not yet in the tree
      do {
         // Add to the priority queue vertices adjacent to the last 
         // vertex added. Note that this MAY add a duplicate vertex 
         // to the queue, but in considering that destination we'd 
         // only add it via the shortest edge and would later discard 
         // any duplicate entries.
         for (Edge<T> edge : getEdgesFrom(lastVertex)) {
            edgeQueue.add(edge);
         }

         // See if we can find a new shortest edge to add among the
         // remaining vertices yet to be explored. Dequeue vertices
         // until we run out or we find one that hasn't already been
         // added to the tree
         do {
            nextEdge = edgeQueue.poll();
         } while (nextEdge != null 
               && spanningTree._adjacencyMap.containsKey(nextEdge.getDestination()));

         // We either have an edge to a new vertex or we ran out 
         // of edges to consider. If we have an edge, add the
         // destination vertex and the edge to the tree
         if (nextEdge != null) {
            spanningTree.addVertex(nextEdge.getDestination());
            spanningTree.addEdge(nextEdge.getSource(),
                  nextEdge.getDestination(),
                  nextEdge.getWeight());
            lastVertex = nextEdge.getDestination();
         }

      } while (nextEdge !=null 
            && spanningTree._adjacencyMap.keySet().size() 
            < _adjacencyMap.keySet().size());

      // If the spanning tree has all the vertices, return it; otherwise
      // return null
      return spanningTree._adjacencyMap.keySet().size() 
               == _adjacencyMap.keySet().size() ? spanningTree : null;

   } // minimumSpanningTree



   /** 
    * Find a quasi-optimal tour
    *
    * Uses the Inver-Over algorithm of Tao and Michalewicz
    *
    * @param populationSize number of random paths to use
    * @param inversionProbability likelihood of self-mutation
    * @param terminationIterations number of iterations with no
    *                              improvement before we terminate
    *
    * @return path close to optimal
    */
   @SuppressWarnings("unchecked")
   public List<T> getOptimalTour(int populationSize,
                                 float inversionProbability,
                                 int terminationIterations) {
      // random initialization of the population P
      ArrayList<T>[] population = (ArrayList<T>[]) new ArrayList[populationSize];
      long[] populationLengths = new long[populationSize];
      ArrayList<T> newPath;
      long newPathLength;
      int iterationsWithNoChange = 0;
      int bestSolution=0;
      long bestSolutionLength=Long.MAX_VALUE;
      int inversions = 0;
      int numCities = getVertices().size();
      T city;
      T cityPrime;
      boolean timeToMoveOn;
      Random rand = new Random();

      // Create an initial population of tours to explore by getting the
      // list of vertices and creating many random permutations
      population[0] = (ArrayList<T>) getVertices();
      populationLengths[0] = pathLength(population[0]) 
         + getEdgeWeight(population[0].get(0), population[0].get(numCities-1));

      // Create the population of random tours
      for (int i=1; i<populationSize; i++) {
         population[i] = (ArrayList<T>) population[0].clone();
         Collections.shuffle(population[i]);
         populationLengths[i] = pathLength(population[i])
            + getEdgeWeight(population[i].get(0), population[i].get(numCities-1));
      }

      // The following algorithm is taken from Tao and Michalewicz and
      // the comments correspond directly to the pseudo-code of the
      // algorithm as given

      // while (not satisfied termination-condition) do
      do {
         for (int i=0; i<populationSize; i++) {
            int cityIndex;
            int otherSolution;
            long newPopulationLength;
            newPath = (ArrayList<T>) population[i].clone();
            // select (randomly) a city c from S0
            cityIndex = rand.nextInt(newPath.size());
            city = newPath.get(cityIndex);
            timeToMoveOn = false;
            while (!timeToMoveOn) {
               int cityPrimeIndex;
               if (rand.nextFloat() <= inversionProbability) {
                  while (cityIndex==(cityPrimeIndex=rand.nextInt(newPath.size())));
                  cityPrime = newPath.get(cityPrimeIndex);
               }
               else {
                  while(i==(otherSolution=rand.nextInt(population.length)));
                  // assign to c' the `next' city to the city c in the selected individual
                  cityPrimeIndex =
                     (population[otherSolution].indexOf(city) + 1) % newPath.size();
                  cityPrime = population[otherSolution].get(cityPrimeIndex);
               }
               // if (the next city or the previous city of city c in S0 is c')
               // exit from repeat loop, otherwise continue processing
               if (! (timeToMoveOn = newPath.get((cityIndex + 1) 
                           % newPath.size()).equals(cityPrime)
                     || newPath.get((cityIndex + newPath.size()-1) 
                        % newPath.size()).equals(cityPrime))) {
                  // reverse the section from the next city of city c to the city c' in S0
                  cityPrimeIndex = newPath.indexOf(cityPrime);
                  reverseSection(newPath, (cityIndex + 1) % newPath.size(), cityPrimeIndex);
                  // c = c'
                  city = cityPrime;
                  cityIndex = cityPrimeIndex;
                  inversions++;
               }
            }
            // if (eval(S0 ) <= eval(Si))
            // Si = S0
            if ((newPopulationLength = pathLength(newPath)
                     + getEdgeWeight(newPath.get(0), newPath.get(numCities-1))) 
                  <= populationLengths[i]) {
               populationLengths[i] = newPopulationLength;
               population[i] = newPath;

               if (newPopulationLength < bestSolutionLength) {
                  bestSolution = i;
                  bestSolutionLength = newPopulationLength;
                  iterationsWithNoChange = 0;
               }
            }
         }
      } while (iterationsWithNoChange++ < terminationIterations);

      /****/
        System.out.println(inversions);
        System.out.println(pathLength(population[bestSolution])
        + getEdgeWeight(population[bestSolution].get(0), 
        population[bestSolution].get(numCities-1)));
       /*****/

      return population[bestSolution];

   } // getOptimalTour


   /**
    * Reverse a section of an array
    *
    * @param array to reverse a section of
    * @param from beginning of reversed section
    * @param to end of reversed section
    */
   private void reverseSection(ArrayList<T> array, int from, int to) {
      T temp;
      // If from is larger than to then we're wrapping around 
      boolean wrap = from > to;
      // Reverse the section
      while ((wrap && from > to) || (!wrap && from < to)) {
         temp = array.get(from);
         array.set(from, array.get(to));
         array.set(to, temp);
         from = (from + 1) % array.size();
         to = (to == 0) ? array.size()-1 : to-1;
      }
   }


   /** 
    * Find a quasi-optimal tour using default parameters
    *
    * @return path close to optimal
    */
   public List<T> getOptimalTour() {
      return getOptimalTour(200, .02f, 10);
   }



   /**
    * Instantiate a graph from the given input file
    *
    * Input must be formatted as:
    * <pre>
    * #vertices
    * vertex 1
    * vertex 2
    * ...
    * vertex N
    * #edges
    * source, destination, weight
    * ...
    * </pre>
    *
    * @param isDirected is true if this is a directed graph
    * @param inputFile holds graph data
    *
    * @return populated graph
    *
    * @throws IOException if any error occurs with input
    */
   public static Graph<String> fromCSVFile(boolean isDirected, Scanner inputFile) 
            throws IOException {
      Graph<String> theGraph = new Graph<>(isDirected);
      int numEdges;
      int numVertices;

      // Set the scanner to use newline and comman as delimiters to make 
      // it trivial to read and parse a correctly formatted file
      inputFile.useDelimiter("[\\n\\r,]+");

      // Now we can parse the file
      try {
         numVertices = inputFile.nextInt();
         // Read the vertices
         for (int i=0; i<numVertices; i++) {
            theGraph.addVertex(inputFile.next());
         }
         numEdges = inputFile.nextInt();
         // Read the edges
         for (int i=0; i<numEdges; i++) {
            theGraph.addEdge(inputFile.next(), 
                  inputFile.next(), 
                  inputFile.nextInt());
         }
      }
      // Any expected exception turns into an IO Exception
      catch (NumberFormatException 
            | NoSuchElementException 
            | IllegalStateException e) {
         throw new IOException("Input File Error: " + e.getMessage());
      }

      return theGraph;

   } // fromCSVFile



   /*
    * Construct an undirected graph from an XML encoded TSP file
    *
    * @param inputFile an XML encoded TSP file from 
    *        <a href="http://comopt.ifi.uni-heidelberg.de/software/TSPLIB95/">
    *        http://comopt.ifi.uni-heidelberg.de/software/TSPLIB95/</a>
    *
    * @return graph populated from the file
    * 
    * @throws ParserConfigurationException, SAXException, IOException if
    *         the file doesn't conform to the specification
    */
   public static Graph<String> fromTSPFile(InputStream inputFile) 
         throws ParserConfigurationException, SAXException, IOException {

      /**
       * The Handler for SAX Parser Events.
       *
       * This inner-class extends the default handler for the SAX parser
       * to construct a graph from a TSP file
       *
       * @see org.xml.sax.helpers.DefaultHandler
       */
      class TSPGraphHandler extends DefaultHandler {
         // Instantiate an undirected graph to populate; vertices are
         // integers though we treat them as strings for extension to other
         // similarly-formed files representing, say, GFU.
         private Graph<String> _theGraph = new Graph<>(false);

         private final int NO_WEIGHT = -1;
         // As we parse we need to keep track of when we've seen
         // vertices and edges
         private int _sourceVertexNumber = 0;
         private String _destinationVertexName = null;
         private String _sourceVertexName = null;
         private int _edgeWeight = NO_WEIGHT;
         private boolean _inEdge = false;


         /**
          * Parser has seen an opening tag
          *
          * For a <pre>vertex</pre> tag we add the vertex to the graph
          * the first time we encounter it.
          * For an <pre>edge</pre> tag we remember the weight of the edge.
          *
          * {@inheritDoc}
          */
         @Override
         public void startElement(String uri, String localName,
               String qName, Attributes attributes) throws SAXException {

            // We only care about vertex and edge elements
            switch (qName) {

               case "vertex":
                  // See if the vertices are named; if so, use the
                  // name, otherwise use the number
                  _sourceVertexName = attributes.getValue("name");
                  if (_sourceVertexName == null) {
                     _sourceVertexName = Integer.toString(_sourceVertexNumber);
                  }
                  // If is vertex 0 then it's the first time we're seeing it; 
                  // add it to the graph. Other vertices will be added
                  // as we encounter their edges
                  if (_sourceVertexNumber == 0) {
                     _theGraph.addVertex(_sourceVertexName);
                  }
                  break;

               case "edge":
                  // Edges have the destination vertex within so
                  // indicate that we're inside an edge so that the
                  // character-parsing method below will grab the
                  // destination vertex as it encounters it
                  _inEdge = true;
                  // The weight of the edge is given by the "cost"
                  // attribute
                  _edgeWeight = (int) Double.parseDouble(attributes.getValue("cost"));
                  break;

               default: // ignore any other opening tag
            }
         }


         /**
          * Parser has seen a closing tag.
          *
          * For a <pre>vertex</pre> tag we increment the vertex number
          * to keep track of which vertex we're parsing.
          * For a <pre>edge</pre> tag we use the number of the edge and
          * the weight we saw in the opening tag to add an edge to the
          * graph.
          *
          * {@inheritDoc}
          */
         @Override
         public void endElement(String uri, String localName,
               String qName) throws SAXException {

            // Again, we only care about vertex and edge tags
            switch (qName) {

               case "vertex":
                  // End of a vertex so we're moving on to the next
                  // source vertex number
                  _sourceVertexNumber++;
                  // Clear out the name so we don't inherit it in some
                  // mal-formed entry later
                  _sourceVertexName = null;
                  break;

               case "edge":
                  // We've finished an edge so we have collected all the
                  // information needed to add an edge to the graph
                  _inEdge = false;
                  // If this is the first set of edges (i.e., we're on
                  // the first source vertex) then this is the first
                  // time we've seen the destination vertex; add it to
                  // the graph
                  if (_sourceVertexNumber == 0) {
                     _theGraph.addVertex(_destinationVertexName);
                  }
                  // Should now be safe to add an edge between the
                  // source and destination
                  _theGraph.addEdge(_sourceVertexName, 
                                    _destinationVertexName, _edgeWeight);
                  // Clear out the attributes of this edge so we don't
                  // accidentally inherit them should we parse a
                  // mal-formed edge entry later
                  _destinationVertexName = null;
                  _edgeWeight = NO_WEIGHT;
                  break;

               default: // ignore any other closing tag
            }
         }


         /**
          * Parser has seen a string of characters between opening and
          * closing tag. The only characters we care about occur within
          * an <pre>edge</pre> tag and are the destination vertex.
          *
          * {@inheritDoc}
          */
         @Override
         public void characters(char[] ch, int start, int length) throws SAXException {
            // If we're within an edge, then this string of characters
            // is the number of the destination vertex for this edge.
            // Remember the destination vertex
            if (_inEdge) {
               _destinationVertexName = new String(ch, start, length);
            }
         }


         /**
          * @return the graph constructed
          */
         Graph<String> getGraph() {
            return _theGraph;
         }


      } // TSPHandler


      TSPGraphHandler tspHandler = new TSPGraphHandler();

      // Here's where we do the actual parsing using the local class
      // defined above. Give the parser an instance of the class above 
      // as the handler and parse away!
      SAXParserFactory.newInstance().newSAXParser().parse(inputFile, tspHandler);

      // Graph should now be populated, return it
      return tspHandler.getGraph();

   } // fromTSPFile




   /**
    * Class representing an edge in a graph
    */
   public static class Edge<E> implements Comparable<Edge<E>> {
      private E _source;
      private E _destination;
      private int _weight;

      /**
       * Create an edge between the source and destination of the given
       * weight
       *
       * @param source node
       * @param destination node
       * @param weight of the edge
       */
      public Edge(E source, E destination, int weight) {
         _source = source;
         _destination = destination;
         _weight = weight;
      }

      public E getSource() { return _source; }
      public E getDestination() { return _destination; }
      public int getWeight() { return _weight; }

      /**
       * Compare edges by weight
       *
       * @param o edge to compare to
       *
       * @return &lt; 0 if this edge is shorter than the parameter, &gt;
       * 0 if this edge is longer than the parameter, or 0 if the same
       * length.
       */
      public int compareTo(Edge<E> o) {
         return _weight - o._weight;
      }


      /**
       * Return a string with the source, destinaion, and weight
       *
       * @return a string representation
       */
      @Override
      public String toString() { return "("+_source+","+_destination+","+_weight+")";}

   } // Edge






   /**
    * Test program for optimal tour using genetic algorithm
    */
   public static void main(String[] args) throws Exception {

      Graph<String> newGraph = new Graph<>(false);

      newGraph.addVertex("Portland");
      newGraph.addVertex("Newberg");
      newGraph.addVertex("Yakima");
      newGraph.addVertex("Richland");
      newGraph.addVertex("Seattle");
      newGraph.addVertex("Dundee");
      newGraph.addVertex("Beaverton");
      newGraph.addVertex("The Dalles");
      newGraph.addEdge("Portland","Newberg", 25);
      newGraph.addEdge("Seattle","Yakima", 100);
      newGraph.addEdge("Richland","Yakima", 60);
      newGraph.addEdge("Portland","Seattle", 180);
      newGraph.addEdge("Portland","Beaverton", 10);
      newGraph.addEdge("Newberg","Beaverton", 14);
      newGraph.addEdge("Dundee","Newberg", 3);
      newGraph.addEdge("Dundee","Portland", 45);
      newGraph.addEdge("The Dalles","Portland", 80);
      newGraph.addEdge("The Dalles","Yakima", 80);
      newGraph.addEdge("The Dalles","Richland", 90);

      System.out.println(newGraph.shortestPathBetween("Newberg", "Richland"));
      System.out.println(newGraph.shortestPathBetween("Seattle", "The Dalles"));
   }


} // Graph
