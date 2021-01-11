import java.io.FileReader;
import java.io.PrintStream;
import java.io.PushbackReader;
import java.util.HashMap;
import java.util.Stack;

/**
 * Main class for the compiler. Invokes the lexical analyzer and parses
 * the given input file
 */
public class Compiler
{

   /**
    * Initialize compiler data structures, especially the parse table
    * and the map of keywords to their token type 
    */
   protected static void initialize()
   {
      // Create a symbol table for identifiers 
      symbolTable = new HashMap<String, Lexeme>();

      // Instantiate some "global" stacks used during the parse phase
      // to remember context
      operandStack = new Stack<Lexeme>();
      operatorStack = new Stack<Lexeme>();
      endLabelStack = new Stack<String>();
      beginLabelStack = new Stack<String>();

      // Allocate the parse table. This table will be sparse and we'll
      // simply fill in the appropriate mappings from right-hand-side
      // symbol to the left-hand-side for legal productions. Note that
      // the array of productions must be pushed onto the parse stack
      // in REVERSE order
      _parseTable = new HashMap<Symbol, HashMap<Symbol, Symbol[]>>();

      // Even though many symbols will have no transitions, fill this
      // hash map with empty hash maps for each symbol as this will
      // simplify our processing later; otherwise we have to
      // watch out for null values when we look up replacements. Note
      // that we only need entries for non-terminal symbols since
      // they're the only type of symbol for which there will be a
      // replacement.
      for (Symbol s : Constants.NON_TERMINAL.values())
         _parseTable.put(s, new HashMap<Symbol, Symbol[]>());

      // Now fill in the parse table based on the FIRST & FOLLOW sets
      // for our grammar. The Symbol arrays will hold the
      // right-hand-sides of productions - they'll need to be pushed
      // onto the parse stack in reverse order.


      /********* Statement  *****************/

      // <Statement> -> identifier assign_op <Expression> [STORE]
      _parseTable.get(Constants.NON_TERMINAL.STATEMENT).put(
            Constants.TOKEN.IDENTIFIER, new Symbol[]
            {Constants.TOKEN.IDENTIFIER,
               Constants.TOKEN.ASSIGNMENT_OP,
         Constants.NON_TERMINAL.EXPRESSION,
         Constants.ACTION.STORE});

      // <Statement> -> if [GEN_LABELS] <boolean_expression> then <Statement> [GOTO_BEGIN] [END_LABEL] <else_clause> [BEGIN_LABEL] [POP_LABELS]
      _parseTable.get(Constants.NON_TERMINAL.STATEMENT).put(
            Constants.TOKEN.IF, new Symbol[]
            {Constants.TOKEN.IF,
               Constants.ACTION.GEN_LABELS,
         Constants.NON_TERMINAL.BOOLEAN_EXPRESSION,
         Constants.TOKEN.THEN,
         Constants.NON_TERMINAL.STATEMENT,
         Constants.ACTION.GOTO_BEGIN,
         Constants.ACTION.END_LABEL,
         Constants.NON_TERMINAL.ELSE_CLAUSE,
         Constants.ACTION.BEGIN_LABEL,
         Constants.ACTION.POP_LABELS});

      // <Statement> -> while [GEN_LABELS] [BEGIN_LABEL] <boolean_expression> do <Statement> [GOTO_BEGIN] [END_LABEL] [POP_LABELS]
      _parseTable.get(Constants.NON_TERMINAL.STATEMENT).put(
            Constants.TOKEN.WHILE, new Symbol[]
            {Constants.TOKEN.WHILE,
               Constants.ACTION.GEN_LABELS,
         Constants.ACTION.BEGIN_LABEL,
         Constants.NON_TERMINAL.BOOLEAN_EXPRESSION,
         Constants.TOKEN.DO,
         Constants.NON_TERMINAL.STATEMENT,
         Constants.ACTION.GOTO_BEGIN,
         Constants.ACTION.END_LABEL,
         Constants.ACTION.POP_LABELS});

      // <Statement> -> print [PRINT_HEADER] <print_expression>
      _parseTable.get(Constants.NON_TERMINAL.STATEMENT).put(
            Constants.TOKEN.PRINT, new Symbol[]
            {Constants.TOKEN.PRINT,
               Constants.ACTION.PRINT_HEADER,
         Constants.NON_TERMINAL.PRINT_EXPRESSION});

      // <Statement> -> begin <Statement_list> end
      _parseTable.get(Constants.NON_TERMINAL.STATEMENT).put(
            Constants.TOKEN.BEGIN, new Symbol[]
            {Constants.TOKEN.BEGIN,
               Constants.NON_TERMINAL.STATEMENT_LIST,
         Constants.TOKEN.END});

      // <Statement> -> variable identifier [DECLARE]
      _parseTable.get(Constants.NON_TERMINAL.STATEMENT).put(
            Constants.TOKEN.VARIABLE, new Symbol[]
            {Constants.TOKEN.VARIABLE,
               Constants.TOKEN.IDENTIFIER,
         Constants.ACTION.DECLARE});


      /********* else_clause *****************/

      // <else_clause> -> else <Statement>
      _parseTable.get(Constants.NON_TERMINAL.ELSE_CLAUSE).put(
            Constants.TOKEN.ELSE, new Symbol[]
            {Constants.TOKEN.ELSE,
               Constants.NON_TERMINAL.STATEMENT});

      // <else_clause> -> *epsilon* for any symbol in
      // FOLLOW(Statement)

      // For end
      _parseTable.get(Constants.NON_TERMINAL.ELSE_CLAUSE).put(
            Constants.TOKEN.END, new Symbol[0]);
      // Same for separator
      _parseTable.get(Constants.NON_TERMINAL.ELSE_CLAUSE).put(
            Constants.TOKEN.STATEMENT_SEPARATOR, 
            _parseTable.get(Constants.NON_TERMINAL.ELSE_CLAUSE).get(Constants.TOKEN.END));
      // Same for end-of-input 
      _parseTable.get(Constants.NON_TERMINAL.ELSE_CLAUSE).put(
            Constants.TOKEN.END_OF_INPUT, 
            _parseTable.get(Constants.NON_TERMINAL.ELSE_CLAUSE).get(Constants.TOKEN.END));


      /********* statement_list  *****************/

      // <Statement_list> -> <statement> <separated_list>

      // For identifier
      _parseTable.get(Constants.NON_TERMINAL.STATEMENT_LIST).put(
            Constants.TOKEN.IDENTIFIER, new Symbol[]
            {Constants.NON_TERMINAL.STATEMENT,
               Constants.NON_TERMINAL.SEPARATED_LIST});
      // Same for if
      _parseTable.get(Constants.NON_TERMINAL.STATEMENT_LIST).put(
            Constants.TOKEN.IF,
            _parseTable.get(Constants.NON_TERMINAL.STATEMENT_LIST).get(Constants.TOKEN.IDENTIFIER));
      // Same for while
      _parseTable.get(Constants.NON_TERMINAL.STATEMENT_LIST).put(
            Constants.TOKEN.WHILE, 
            _parseTable.get(Constants.NON_TERMINAL.STATEMENT_LIST).get(Constants.TOKEN.IDENTIFIER));
      // Same for print
      _parseTable.get(Constants.NON_TERMINAL.STATEMENT_LIST).put(
            Constants.TOKEN.PRINT, 
            _parseTable.get(Constants.NON_TERMINAL.STATEMENT_LIST).get(Constants.TOKEN.IDENTIFIER));
      // Same for begin
      _parseTable.get(Constants.NON_TERMINAL.STATEMENT_LIST).put(
            Constants.TOKEN.BEGIN, 
            _parseTable.get(Constants.NON_TERMINAL.STATEMENT_LIST).get(Constants.TOKEN.IDENTIFIER));
      // Same for variable
      _parseTable.get(Constants.NON_TERMINAL.STATEMENT_LIST).put(
            Constants.TOKEN.VARIABLE, 
            _parseTable.get(Constants.NON_TERMINAL.STATEMENT_LIST).get(Constants.TOKEN.IDENTIFIER));




      /********* separated_list *****************/

      // <separated_list> -> statement_separator <Statement> <separated_list>
      _parseTable.get(Constants.NON_TERMINAL.SEPARATED_LIST).put(
            Constants.TOKEN.STATEMENT_SEPARATOR, new Symbol[]
            {Constants.TOKEN.STATEMENT_SEPARATOR,
               Constants.NON_TERMINAL.STATEMENT,
         Constants.NON_TERMINAL.SEPARATED_LIST});

      // <separated_list> -> *epsilon* for end
      _parseTable.get(Constants.NON_TERMINAL.SEPARATED_LIST).put(
            Constants.TOKEN.END, new Symbol[0]);




      /********* print_expression *****************/

      // <print_expression> -> <expression> [PRINT_IFOOTER]

      // For identifier
      _parseTable.get(Constants.NON_TERMINAL.PRINT_EXPRESSION).put(
            Constants.TOKEN.IDENTIFIER, new Symbol[]
            {Constants.NON_TERMINAL.EXPRESSION,
               Constants.ACTION.PRINT_IFOOTER});
      // Same for number
      _parseTable.get(Constants.NON_TERMINAL.PRINT_EXPRESSION).put(
            Constants.TOKEN.NUMBER, 
            _parseTable.get(Constants.NON_TERMINAL.PRINT_EXPRESSION).get(Constants.TOKEN.IDENTIFIER));
      // Same for left-paren
      _parseTable.get(Constants.NON_TERMINAL.PRINT_EXPRESSION).put(
            Constants.TOKEN.LEFT_PAREN, 
            _parseTable.get(Constants.NON_TERMINAL.PRINT_EXPRESSION).get(Constants.TOKEN.IDENTIFIER));

      // <print_expression> -> string [LOAD_CONST] [PRINT_SFOOTER]
      _parseTable.get(Constants.NON_TERMINAL.PRINT_EXPRESSION).put(
            Constants.TOKEN.STRING, new Symbol[]
            {Constants.TOKEN.STRING,
               Constants.ACTION.LOAD_CONST,
         Constants.ACTION.PRINT_SFOOTER});



      /********* expression *****************/

      // <expression> -> <term> <arithmetic_term>

      // For identifier
      _parseTable.get(Constants.NON_TERMINAL.EXPRESSION).put(
            Constants.TOKEN.IDENTIFIER, new Symbol[]
            {Constants.NON_TERMINAL.TERM,
               Constants.NON_TERMINAL.ARITHMETIC_TERM});
      // Same for number
      _parseTable.get(Constants.NON_TERMINAL.EXPRESSION).put(
            Constants.TOKEN.NUMBER, 
            _parseTable.get(Constants.NON_TERMINAL.EXPRESSION).get(Constants.TOKEN.IDENTIFIER));
      // Same for left-paren
      _parseTable.get(Constants.NON_TERMINAL.EXPRESSION).put(
            Constants.TOKEN.LEFT_PAREN, 
            _parseTable.get(Constants.NON_TERMINAL.EXPRESSION).get(Constants.TOKEN.IDENTIFIER));




      /********* arithmetic_term *****************/

      // <arithmetic_term> -> arithmetic_operator [OP_PUSH] <term> [COMPUTE]

      // For add_op
      _parseTable.get(Constants.NON_TERMINAL.ARITHMETIC_TERM).put(
            Constants.TOKEN.ADD_OP, new Symbol[]
            {Constants.TOKEN.ADD_OP,
               Constants.ACTION.OP_PUSH,
         Constants.NON_TERMINAL.TERM,
         Constants.ACTION.COMPUTE});
      // Same for subtract_op
      _parseTable.get(Constants.NON_TERMINAL.ARITHMETIC_TERM).put(
            Constants.TOKEN.SUBTRACT_OP, new Symbol[]
            {Constants.TOKEN.SUBTRACT_OP,
               Constants.ACTION.OP_PUSH,
         Constants.NON_TERMINAL.TERM,
         Constants.ACTION.COMPUTE});
      // Same for multiply_op
      _parseTable.get(Constants.NON_TERMINAL.ARITHMETIC_TERM).put(
            Constants.TOKEN.MULTIPLY_OP, new Symbol[]
            {Constants.TOKEN.MULTIPLY_OP,
               Constants.ACTION.OP_PUSH,
         Constants.NON_TERMINAL.TERM,
         Constants.ACTION.COMPUTE});
      // Same for divide_op
      _parseTable.get(Constants.NON_TERMINAL.ARITHMETIC_TERM).put(
            Constants.TOKEN.DIVIDE_OP, new Symbol[]
            {Constants.TOKEN.DIVIDE_OP,
               Constants.ACTION.OP_PUSH,
         Constants.NON_TERMINAL.TERM,
         Constants.ACTION.COMPUTE});

      // <arithemtic_term> -> *epsilon* for any symbol in
      // FOLLOW(Statement)

      // For end
      _parseTable.get(Constants.NON_TERMINAL.ARITHMETIC_TERM).put(
            Constants.TOKEN.END, new Symbol[0]);
      // Same for separator
      _parseTable.get(Constants.NON_TERMINAL.ARITHMETIC_TERM).put(
            Constants.TOKEN.STATEMENT_SEPARATOR, 
            _parseTable.get(Constants.NON_TERMINAL.ARITHMETIC_TERM).get(Constants.TOKEN.END));
      // Same for end-of-input 
      _parseTable.get(Constants.NON_TERMINAL.ARITHMETIC_TERM).put(
            Constants.TOKEN.END_OF_INPUT, 
            _parseTable.get(Constants.NON_TERMINAL.ARITHMETIC_TERM).get(Constants.TOKEN.END));



      /********* boolean_expression *****************/

      // <boolean_expression> -> <term> <relational_op> [OP_PUSH] <term> [COMPUTE]

      // For identifier
      _parseTable.get(Constants.NON_TERMINAL.BOOLEAN_EXPRESSION).put(
            Constants.TOKEN.IDENTIFIER, new Symbol[]
            {Constants.NON_TERMINAL.TERM,
               Constants.NON_TERMINAL.RELATIONAL_OP,
         Constants.ACTION.OP_PUSH,
         Constants.NON_TERMINAL.TERM,
         Constants.ACTION.COMPUTE});

      // Same for number
      _parseTable.get(Constants.NON_TERMINAL.BOOLEAN_EXPRESSION).put(
            Constants.TOKEN.NUMBER, 
            _parseTable.get(Constants.NON_TERMINAL.BOOLEAN_EXPRESSION).get(Constants.TOKEN.IDENTIFIER));
      // Same for left-paren 
      _parseTable.get(Constants.NON_TERMINAL.BOOLEAN_EXPRESSION).put(
            Constants.TOKEN.LEFT_PAREN, 
            _parseTable.get(Constants.NON_TERMINAL.BOOLEAN_EXPRESSION).get(Constants.TOKEN.IDENTIFIER));



      /********* term *****************/

      // <term> -> identifier
      _parseTable.get(Constants.NON_TERMINAL.TERM).put(
            Constants.TOKEN.IDENTIFIER, new Symbol[]
            {Constants.TOKEN.IDENTIFIER,
               Constants.ACTION.LOAD});

      // <term> -> number 
      _parseTable.get(Constants.NON_TERMINAL.TERM).put(
            Constants.TOKEN.NUMBER, new Symbol[]
            {Constants.TOKEN.NUMBER,
               Constants.ACTION.PUSH});

      // <term> -> ( expression )
      _parseTable.get(Constants.NON_TERMINAL.TERM).put(
            Constants.TOKEN.LEFT_PAREN, new Symbol[]
            {Constants.TOKEN.LEFT_PAREN,
               Constants.NON_TERMINAL.EXPRESSION,
         Constants.TOKEN.RIGHT_PAREN});


      /********* relational_op *****************/

      // <relational_op> -> LESS_THAN_OP
      _parseTable.get(Constants.NON_TERMINAL.RELATIONAL_OP).put(
            Constants.TOKEN.LESS_THAN_OP, new Symbol[]
            {Constants.TOKEN.LESS_THAN_OP});

      // <relational_op> -> LESS_THAN_OR_EQUAL_OP
      _parseTable.get(Constants.NON_TERMINAL.RELATIONAL_OP).put(
            Constants.TOKEN.LESS_THAN_OR_EQUAL_OP, new Symbol[]
            {Constants.TOKEN.LESS_THAN_OR_EQUAL_OP});

      // <relational_op> -> GREATER_THAN_OP
      _parseTable.get(Constants.NON_TERMINAL.RELATIONAL_OP).put(
            Constants.TOKEN.GREATER_THAN_OP, new Symbol[]
            {Constants.TOKEN.GREATER_THAN_OP});

      // <relational_op> -> GREATER_THAN_OR_EQUAL_OP
      _parseTable.get(Constants.NON_TERMINAL.RELATIONAL_OP).put(
            Constants.TOKEN.GREATER_THAN_OR_EQUAL_OP, new Symbol[]
            {Constants.TOKEN.GREATER_THAN_OR_EQUAL_OP});

      // <relational_op> -> NOT_EQUAL_OP 
      _parseTable.get(Constants.NON_TERMINAL.RELATIONAL_OP).put(
            Constants.TOKEN.NOT_EQUAL_OP, new Symbol[]
            {Constants.TOKEN.NOT_EQUAL_OP});

      // <relational_op> -> EQUAL_OP
      _parseTable.get(Constants.NON_TERMINAL.RELATIONAL_OP).put(
            Constants.TOKEN.EQUAL_OP, new Symbol[]
            {Constants.TOKEN.EQUAL_OP});






      // Allocate and populate the keyword map
      KEYWORD_MAP = new HashMap<String, Constants.TOKEN>();

      // Fill the map by mapping lexemes to the type of the token.
      // Note that numbers and identifiers are not enumerated here - 
      // the lexical analyzer will handle them as special classes of
      // tokens
      KEYWORD_MAP.put("variable", Constants.TOKEN.VARIABLE);
      KEYWORD_MAP.put("print", Constants.TOKEN.PRINT);
      KEYWORD_MAP.put("if", Constants.TOKEN.IF);
      KEYWORD_MAP.put("then", Constants.TOKEN.THEN);
      KEYWORD_MAP.put("else", Constants.TOKEN.ELSE);
      KEYWORD_MAP.put("while", Constants.TOKEN.WHILE);
      KEYWORD_MAP.put("do", Constants.TOKEN.DO);
      KEYWORD_MAP.put("begin", Constants.TOKEN.BEGIN);
      KEYWORD_MAP.put("end", Constants.TOKEN.END);
      KEYWORD_MAP.put(";", Constants.TOKEN.STATEMENT_SEPARATOR);
      KEYWORD_MAP.put(":=", Constants.TOKEN.ASSIGNMENT_OP);
      KEYWORD_MAP.put("<", Constants.TOKEN.LESS_THAN_OP);
      KEYWORD_MAP.put("<=", Constants.TOKEN.LESS_THAN_OR_EQUAL_OP);
      KEYWORD_MAP.put("<>", Constants.TOKEN.NOT_EQUAL_OP);
      KEYWORD_MAP.put("=", Constants.TOKEN.EQUAL_OP);
      KEYWORD_MAP.put(">", Constants.TOKEN.GREATER_THAN_OP);
      KEYWORD_MAP.put(">=", Constants.TOKEN.GREATER_THAN_OR_EQUAL_OP);
      KEYWORD_MAP.put("+", Constants.TOKEN.ADD_OP);
      KEYWORD_MAP.put("-", Constants.TOKEN.SUBTRACT_OP);
      KEYWORD_MAP.put("*", Constants.TOKEN.MULTIPLY_OP);
      KEYWORD_MAP.put("/", Constants.TOKEN.DIVIDE_OP);
      KEYWORD_MAP.put("(", Constants.TOKEN.LEFT_PAREN);
      KEYWORD_MAP.put(")", Constants.TOKEN.RIGHT_PAREN);
   }


   private static Lexeme getNextLexeme(LexicalAnalyzer lexer) throws java.io.IOException
   {
      // Get the next lexeme
      Lexeme lexeme = lexer.getNextLexeme();

      // Is this an identifier? If so, check the symbol table
      if (lexeme.getTokenType() == Constants.TOKEN.IDENTIFIER)
      {
         // If we've seen this lexeme before (i.e., it's in the symbol
         // table) then return that lexeme instead
         if (symbolTable.get(lexeme.getLexeme()) != null)
         {
            lexeme = symbolTable.get(lexeme.getLexeme());
         }
         else
         {
            symbolTable.put(lexeme.getLexeme(), lexeme);
         }
      }

      // If this lexeme is an identifier or number, push it
      // onto the operand stack
      if (lexeme.getTokenType() == Constants.TOKEN.IDENTIFIER ||
            lexeme.getTokenType() == Constants.TOKEN.NUMBER || 
            lexeme.getTokenType() == Constants.TOKEN.STRING)
      {
         operandStack.push(lexeme);
      }
      else if (lexeme.getTokenType().isOperator())
         operatorStack.push(lexeme);

      return lexeme;
   }




   /**
    * Main program. Opens file and compiles source code
    *
    * @param args[0] filename of source code
    *
    * @return compiled code
    */
   public static void main(String[] args) throws java.io.FileNotFoundException, java.io.IOException, java.lang.Exception
   {
      // Make sure we have a program to compile!
      if (args.length < 1)
      {
         System.err.println("ERROR: Filename required\nUsage: java Compiler <filename>");
         System.exit(1);
      }

      Symbol[] rightHandSide;

      // Set up the keyword map and other data structures
      initialize();


      // Create the lexical analyzer over the file provided as an
      // argument
      LexicalAnalyzer lexer = new LexicalAnalyzer(new PushbackReader(
               new FileReader(args[0])));

      // Create a parse stack
      Stack<Symbol> parseStack = new Stack<Symbol>();

      parseStack.push(Constants.NON_TERMINAL.STATEMENT);

      // Get the first lexeme
      Lexeme lexeme = getNextLexeme(lexer);


      // Open the output file and print the program header
      /***********************/
      outstream = System.out;
      /***********************/

      // Name the class generated with the name of the file (except
      // for the suffix)
      outstream.printf(Constants.CODE_HEADER,
            args[0].lastIndexOf('.') > 0 ? 
                 args[0].substring(0, args[0].lastIndexOf('.')) : 
                 args[0]);


      // Parse the input until we're finished. It may seem that the
      // condition ought to be parse until we get a 'null' lexeme, but
      // then we must deal with left-over actions and nullable
      // non-terminals that may be left on the stack. So we attempt to
      // parse until the stack is empty to make sure we deal with all
      // symbols. We'll handle left-over input at the end.
      while (!parseStack.empty())
      {
         // If the lexeme is whitespace or comment, ignore it
         if (lexeme != null && 
               (lexeme.getTokenType() == Constants.TOKEN.WHITESPACE ||
                lexeme.getTokenType() == Constants.TOKEN.COMMENT))
         {
            // Get the next lexeme
            lexeme = getNextLexeme(lexer);
         }
         else // Not ignoring this lexeme or at the end of the parse
         {
            // Check the top of the parse stack
            // If the top-of-stack is a terminal, then match it against
            // the current token. Otherwise, consult the parse table for
            // the production to push onto the stack
            if (parseStack.peek().isTerminal())
            {
               // If the type of this lexeme matches the top of the
               // stack, then we have a successful match, so pop
               // the top of the stack and move on to the next
               // lexeme
               if (lexeme != null && 
                     parseStack.peek().equals(lexeme.getTokenType()))
               {
                  // Matching lexeme, pop stack and get next
                  // lexeme
                  parseStack.pop();
                  lexeme = getNextLexeme(lexer);
               }
               else // ERROR!
               {
                  // If we have a lexeme, it must have been the
                  // wrong one
                  if (lexeme == null)
                     System.err.print("Error. Saw "+lexeme+"\n\t");
                  else // We ran out of lexemes!
                     System.err.print("Error. Unexpected end of input.\n\t");
                  System.err.println("Expected "+parseStack.peek());
                  System.exit(1);
               }
            }
            else if (parseStack.peek().isNonTerminal())
            {
               // Look up the substitution in the parse table by 
               // indexing using the non-terminal on top of the stack and 
               // finding the matching production for the type of
               // token of the current lexeme. Push the substitution onto 
               // the stack in the reverse order
               /***********                    
                 System.err.println(parseStack);
                 System.err.println(lexeme);
                ***********/
               rightHandSide = _parseTable.get(parseStack.peek()).get(lexeme.getTokenType());

               // Check for an error here
               if (rightHandSide == null)
               {
                  System.err.println("Error. Saw "+lexeme+"\n\tExpected "+parseStack.peek());
                  System.exit(1);
               }

               // Otherwise we're good, so pop off the top and replace
               // it with the rightHandSide pushed onto the stack in
               // reverse
               parseStack.pop();
               for (int i = rightHandSide.length; i>0; i--)
               {
                  parseStack.push(rightHandSide[i-1]);
               }
            }
            else // Must be an action
            {
               // Pop the action off the stack and perform it
               parseStack.pop().doYourThing();
            }

         } // else not ignoring the lexeme

      } // while stack is not empty


      // So we've consumed the entire stack which means we saw a valid
      // program. The only other error we could have at this point
      // would be extra input. Check to verify that the lexeme is
      // the END_OF_INPUT indicating that we also consumed all of the input. If
      // not, then the lexeme is unexpected extra stuff.
      if (lexeme.getTokenType() != Constants.TOKEN.END_OF_INPUT)
      {
         System.err.println("Error. Unexpected "+lexeme);
         System.exit(1);
      }


      /////        System.out.println("Successful Parse");


      // Terminate the code file
      outstream.println(Constants.CODE_FOOTER);
      ///////////outstream.close();

   } // main


   // The parse table that maps a symbol X symbol to a list of symbols.
   private static HashMap<Symbol, HashMap<Symbol, Symbol[]>> _parseTable;

   // The symbol table
   public static HashMap<String, Lexeme> symbolTable;

   // Map of keyword strings to the type of token - public so that
   // other parts of the compiler can reference this map of keywords
   public static HashMap<String, Constants.TOKEN> KEYWORD_MAP;

   // Counter to give unique names to begin & end labels
   public static int labelNumber = 0;

   // Locations for variables - start with 1 so we can consider any
   // variable with the address 0 to be undeclared
   public static int variableNumber = 1;

   public static PrintStream outstream;

   // Stack for remembering operands as we parse
   public static Stack<Lexeme> operandStack;

   // Stack for remembering operators as we parse
   public static Stack<Lexeme> operatorStack;

   // Stack of begin labels we've generated
   public static Stack<String> beginLabelStack;

   // Stack of end labels we've generated
   public static Stack<String> endLabelStack;

}
