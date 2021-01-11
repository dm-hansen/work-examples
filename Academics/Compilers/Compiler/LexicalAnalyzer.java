import java.io.PushbackReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.HashMap;
import java.io.FileReader;

/**
 * Lexical Analyzer for Hansen Lite
 *
 * @author David M. Hansen
 *
 * @version 1.3
 *    2/11/2015 - DM Hansen
 *       Fixed bug(s) on EOF and \r as whitespace and _ in identifiers
 *
 *    2/2/2015 - DM Hansen
 *       Changed STATE to be an enum with methods
 *       Snuffed bug on START,'=' -> GREATER_THAN
 *
 */
class LexicalAnalyzer
{
   /**
    * Create a new lexical analyzer over the input stream and
    * initialize the internal maps used to drive the FSA
    *
    * @param input is the input file
    */
   public LexicalAnalyzer(PushbackReader input)
   {
      // Remember the input stream
      _input = input;

      // Initialize the FSA transitions. Many transitions are on
      // 'characters' or 'numbers'. We're going to cheat a bit here 
      // and create a transition on LETTER for all characters, DIGIT for all 
      // digits. This allows us to see if the input character is a
      // digit, for example, and then use the DIGIT transition in all
      // cases.
      //
      // Fill the table with only those transitions that are interesting. 
      // If no transition is present then we'll assume we're at the end
      // of a lexeme

      _transition = new HashMap<STATE, HashMap<Character, STATE>>();

      // Even though many states will have no transitions, fill this
      // hash map with empty hash maps for each state as this will
      // simplify our transition function later; otherwise we have to
      // watch out for null values when we look up transitions
      for (STATE s : STATE.values())
         _transition.put(s, new HashMap<Character, STATE>());


      // START state has lots of transitions, obviously, one for each
      // of the potential tokens. For letters and digits, I put only a
      // single entry in the state transition table and I'll map all
      // letters/digits to that one letter/digit when I look up
      // transitions. Some of these are single-character "machines"
      // that are a terminal, accepting state. For others, additional
      // transitions are filled in below
      _transition.get(STATE.START).put(LETTER, STATE.IN_SYMBOL);
      _transition.get(STATE.START).put('_', STATE.IN_SYMBOL);
      _transition.get(STATE.START).put(DIGIT, STATE.IN_NUMBER);
      _transition.get(STATE.START).put('(', STATE.LEFT_PAREN);
      _transition.get(STATE.START).put(')', STATE.RIGHT_PAREN);
      _transition.get(STATE.START).put('<', STATE.LESS_THAN);
      _transition.get(STATE.START).put('>', STATE.GREATER_THAN);
      _transition.get(STATE.START).put('=', STATE.EQUAL);
      _transition.get(STATE.START).put(':', STATE.COLON);
      _transition.get(STATE.START).put('+', STATE.ADD_OP);
      _transition.get(STATE.START).put('-', STATE.SUBTRACT_OP);
      _transition.get(STATE.START).put('*', STATE.MULTIPLY_OP);
      _transition.get(STATE.START).put('/', STATE.DIVIDE_OP);
      _transition.get(STATE.START).put(';', STATE.STATEMENT_SEPARATOR);
      _transition.get(STATE.START).put('"', STATE.IN_STRING);
      _transition.get(STATE.START).put('{', STATE.IN_COMMENT);
      _transition.get(STATE.START).put(' ', STATE.WHITESPACE);
      _transition.get(STATE.START).put('\t', STATE.WHITESPACE);
      _transition.get(STATE.START).put('\n', STATE.WHITESPACE);
      _transition.get(STATE.START).put('\r', STATE.WHITESPACE);

      // Symbols have transition on alpha, digit and _
      _transition.get(STATE.IN_SYMBOL).put(LETTER, STATE.IN_SYMBOL);
      _transition.get(STATE.IN_SYMBOL).put('_', STATE.IN_SYMBOL);
      _transition.get(STATE.IN_SYMBOL).put(DIGIT, STATE.IN_SYMBOL);

      // Numbers have transition on more digits
      _transition.get(STATE.IN_NUMBER).put(DIGIT, STATE.IN_NUMBER);

      // Less than can lead to less-than-or-equal or not equal
      _transition.get(STATE.LESS_THAN).put('=', STATE.LESS_THAN_EQUAL);
      _transition.get(STATE.LESS_THAN).put('>', STATE.NOT_EQUAL);

      // Greater than can lead to greater-than-or-equal 
      _transition.get(STATE.GREATER_THAN).put('=', STATE.GREATER_THAN_EQUAL);

      // Colon leads to assign-op
      _transition.get(STATE.COLON).put('=',STATE.ASSIGN_OP);

      // Whitespace leads to more whitespace
      _transition.get(STATE.WHITESPACE).put(' ', STATE.WHITESPACE);
      _transition.get(STATE.WHITESPACE).put('\t', STATE.WHITESPACE);
      _transition.get(STATE.WHITESPACE).put('\n', STATE.WHITESPACE);

      // Terminating " ends a string, moves us to an accepting state.
      // Note that I don't put any other transitions here as we'll
      // account for that as a special case below
      _transition.get(STATE.IN_STRING).put(STRING_END, STATE.STRING);

      // Terminating } ends a comment, moves us to an accepting state.
      // Note that I don't put any other transitions here as we'll
      // account for that as a special case below
      _transition.get(STATE.IN_COMMENT).put(COMMENT_END, STATE.COMMENT);

}


/**
 * Reads input from the input stream 
 *
 * @return the next Lexeme read or null indicating end of input
 * @throws IOException if an illegal (as defined by the language)
 * character is read
 */
public Lexeme getNextLexeme() throws IOException
{
   // The lexeme we accumulate
   StringBuffer lexemeString = new StringBuffer();

   char nextChar;

   // The Lexeme object we'll return
   Lexeme lexeme = null;

   // If the stream is at the end then we return the END_OF_INPUT token
   if (!_input.ready())
      return new Lexeme("",Constants.TOKEN.END_OF_INPUT);

   // Set ourself to the start state
   STATE currentState = STATE.START;
   STATE nextState = null;

   // Get input characters and transition until we find a lexeme
   while (lexeme == null)
   {
      // Read the next input character and use that to transition
      // to the next state
      nextChar = (char) _input.read();

      // Instead of filling my transition map with EVERY letter
      // and digit, I simply check to see if I have a letter or
      // digit here and, if so, use a single value for the LETTER
      // or DIGIT as appropriate. Otherwise use the actual letter.
      if (Character.isLetter(nextChar))
         nextState = _transition.get(currentState).get(LETTER);
      else if (Character.isDigit(nextChar))
         nextState = _transition.get(currentState).get(DIGIT);
      else // Otherwise look up the transition based on the actual character
         nextState = _transition.get(currentState).get(nextChar);

      // If we had no transition to a next state or are at the end of 
      // input then we either:
      //  1) have a valid lexeme already (so construct and return it)
      //  2) are in the middle of a string constant or comment
      //  3) hit an illegal character
      if (nextState == null)
      {
         // If we are in an accepting state, then we have a lexeme
         if (currentState.isAccepting())
         {
            // Push the character we just read back onto the stream
            // for the next lexeme unless we hit the end
            if (_input.ready()) _input.unread(nextChar);

            // If the accepting state maps to a token type, use
            // that for the lexeme
            if (currentState.getTokenType() != null)
            {
               lexeme = new Lexeme(lexemeString.toString(), currentState.getTokenType());
            }
            else // Lexeme is a keyword/symbol or an identifier
            {
               // Use the lexeme string itself to lookup the
               // lexeme and see if it is in the compiler keyword and
               // symbol map
               if (Compiler.KEYWORD_MAP.containsKey(lexemeString.toString()))
               {
                  lexeme = new Lexeme(lexemeString.toString(), Compiler.KEYWORD_MAP.get(lexemeString.toString()));
               }
               else // Not a language keyword or symbol, identifier
               {
                  lexeme = new Lexeme(lexemeString.toString(), Constants.TOKEN.IDENTIFIER);
               }

            } // else symbol

         } // In accepting state

         else // Not an accepting state; it's a string, comment, or an ERROR!
         {
            // Since we didn't provide a transition for every
            // single character in the comment and string cases,
            // we simply catch that here and allow any character
            // to keep us in the same IN_XXX state until we hit
            // the end.
            if ((currentState == STATE.IN_COMMENT ||
                     currentState == STATE.IN_STRING))
            {
               nextState = currentState; 
               if (currentState == STATE.IN_STRING) // Keep string around!
                  lexemeString.append(nextChar);
            }
            else // Illegal character!
            {
               // We read a character for which we have no valid
               // transition. The character must not be a legal
               // character in our language
               throw new IOException("Illegal Input Character: '"+nextChar+"'");
            }

         } // comment/string/error handling

      } // if no transition

      else // Next state is not null, so we're not finished reading a lexeme
      {
         // Not finished, so append the current character to the
         // lexeme and advance to the next state
         lexemeString.append(nextChar);
      }

      // Advance to whatever next state the machine should be in
      currentState = nextState;

   } // while not lexeme found or EOF


   // We either constructed a Lexeme above or none was found and we're
   // returning a token representing end of the input stream
   return lexeme;

} // getNextLexeme


// Some constants used here
private final static char LETTER = 'a';
private final static char DIGIT = '0';
private final static char STRING_END = '"';
private final static char COMMENT_END = '}';

private PushbackReader _input; // The input stream

// Transition map from the current state to a map of next
// state for a given input character
private HashMap<STATE, HashMap<Character, STATE>> _transition;

// The states of the FSA
private enum STATE
{
   START(){public boolean isAccepting(){return false;}},
      IN_SYMBOL, // Special case - accepting, but could be id or keyword
      IN_NUMBER(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.NUMBER;}},
      LESS_THAN(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.LESS_THAN_OP;}},
      LESS_THAN_EQUAL(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.LESS_THAN_OR_EQUAL_OP;}},
      GREATER_THAN(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.GREATER_THAN_OP;}},
      GREATER_THAN_EQUAL(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.GREATER_THAN_OR_EQUAL_OP;}},
      NOT_EQUAL(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.NOT_EQUAL_OP;}},
      EQUAL(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.EQUAL_OP;}},
      COLON(){public boolean isAccepting(){return false;}},
      ASSIGN_OP(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.ASSIGNMENT_OP;}},
      ADD_OP(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.ADD_OP;}},
      SUBTRACT_OP(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.SUBTRACT_OP;}},
      MULTIPLY_OP(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.MULTIPLY_OP;}},
      DIVIDE_OP(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.DIVIDE_OP;}},
      STATEMENT_SEPARATOR(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.STATEMENT_SEPARATOR;}},
      IN_STRING(){public boolean isAccepting(){return false;}},
      STRING(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.STRING;}},
      WHITESPACE(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.WHITESPACE;}},
      IN_COMMENT(){public boolean isAccepting(){return false;}}, 
      COMMENT(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.COMMENT;}},
      LEFT_PAREN(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.LEFT_PAREN;}},
      RIGHT_PAREN(){public Constants.TOKEN getTokenType(){return Constants.TOKEN.RIGHT_PAREN;}};

   // Most states are accepting; exceptions in individual states
   // above
   public boolean isAccepting() {return true;}
   public Constants.TOKEN getTokenType() {return null;}
}



/**
 * Simple test method that takes a filenamem argument and prints out
 * the sequence of tokens that we found
 *
 * @param args[0] is filename of program to tokenize
 */
public static void main(String[] args) throws java.io.FileNotFoundException, java.io.IOException, java.lang.Exception
{
   Compiler.initialize();

   // Create the lexical analyzer over the file provided as an
   // argument
   LexicalAnalyzer lexer = new LexicalAnalyzer(new PushbackReader(
            new FileReader(args[0])));
   // Get the next lexeme
   Lexeme lexeme = lexer.getNextLexeme();
   while (lexeme != null && lexeme.getTokenType() != Constants.TOKEN.END_OF_INPUT)
   {
      System.out.println(lexeme);
      lexeme = lexer.getNextLexeme();
   }
   if (lexeme != null)
      System.out.println(lexeme);
} // main

} // LexicalAnalyzer
