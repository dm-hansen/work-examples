import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Scanner;


/**
* 
* A command-line calculator
*
* @author David M. Hansen
* @version 1.1
*/
public class CalcOOP {

   /**
    * @param args Equation
    *
    * @return the evaluation of the equation
    */
   public static void main(String[] args) {
      // A stack of operators that we'll reuse during conversion to postfix
      Deque<Operator> operatorStack = new ArrayDeque<Operator>();
      // A stack of operands that we'll reuse during computation
      Deque<Operand> operandStack = new ArrayDeque<Operand>();
      // A queue of Tokens to hold the postfix expression
      Deque<Token> postfixSequence = new ArrayDeque<Token>();
      // Some token variables for use during parsing
      Token lastToken = null;
      Token currentToken = null;
      // A scanner over the equation in args[0]
      Scanner inputScanner;

      // Make sure we have arguments
      if (args.length < 1) {
         System.err.println("Insufficient Arguments");
         System.exit(1);
      }

      inputScanner = new Scanner(args[0]);
      // For each input value, convert it to an instance of a Token using
      // the Token class. As we iterate over the input we'll 
      // convert the infix expression to a postfix expression.
      // Have each token perform its infixToPostfix
      // operation on the sequence and an accompanying stack
      while (inputScanner.hasNext()) {
         // Get the token and have it manipulate the operator
         // stack and postfix sequence as appropriate
         try {
            currentToken = Token.getTokenForString(inputScanner.next());
         }
         catch (IllegalArgumentException e) { // If the string isn't recognized as a token
            System.err.println("ERROR - operator or operand not understood: "+e.getMessage());
            System.exit(1);
         }

         // Make sure this token can follow the last token we saw (or
         // null if we're at the beginning) - this is a basic syntax check
         if (!currentToken.canFollow(lastToken)) {
             System.err.println("ERROR at '"+lastToken+"' - invalid equation");
             System.exit(1);
         }

         // So far, so good. Now have the token manipulate the
         // operator stack and the infix expression to build the
         // postfix expression
         currentToken.infixToPostfix(operatorStack, postfixSequence);

         // Remember this current token for the next syntax check
         lastToken = currentToken;
      }

      // If the last operator we saw had the highest precedence, it
      // may still be on the stack. So move any remaining operators on 
      // the operator stack over to the postfix sequence
      while(!operatorStack.isEmpty()) {
         postfixSequence.addLast(operatorStack.pop());
      }

      // Now we have a postfix sequence. Iterate over the Tokens and have
      // each Token perform its compute operation on the stack.
      try {
         for (Token aToken : postfixSequence) {
            aToken.compute(operandStack);
         }
      }
      catch (ArithmeticException e) { // Syntax error when trying to compute
         System.err.println("ERROR - invalid equation: "+e.getMessage());
         System.exit(1);
      }
 
      // Postcondition is that there should only one element left on the stack
      // and it's the answer; if there is more than one Token on the
      // stack, we have an error. Otherwise print the token out as the
      // answer.
      if (operandStack.size() == 1) { // Success!
         System.out.println(operandStack.peek().value());
      }
      else if (operandStack.size() > 1) { // Extraneous operands in the equation
         System.err.println("ERROR: extraneous operands: "+operandStack);
         System.exit(1);
      }
      // Can't imagine how the stack could be empty since underflow will be caught 
      // when operating on operands, but we'll catch it anyway...
      else {
         System.err.println("ERROR: no computation performed");
         System.exit(1);
      }

   } // Main
  
} // CalcOOP
 

/**
 * Super class to represent all types of operators and operands we expect to see. 
 */
abstract class Token {
   /**
    * Parse the string and return an appropriate token instance
    *
    * @param input a string representation of a token
    * @return a corresponding instance of a subclass
    * @throws IllegalArgumentExceptoin if the token is not recognized
    */
   static Token getTokenForString(String input) throws IllegalArgumentException {
      Token newToken;

      // See if the token matches any of our operators
      switch (input) {
         case "+":
            newToken = new AddOp();
            break;

         case "-":
            newToken = new SubOp();
            break;

         case "*":
            newToken = new MultOp();
            break;

         case "/":
            newToken = new DivOp();
            break;
      
         case "^":
            newToken = new ExpOp();
            break;
      
         case "(":
            newToken = new LeftParen();
            break;
      
         case ")":
            newToken = new RightParen();
            break;
      
         default: { // Must be an operand - try to parse an integer
            // If the string fails to parse, catch the number format
            // exception and turn it into an illegal argument exception
            // instead
            try {
               newToken = new Operand(input);
            }
            catch (NumberFormatException e) {
               throw new IllegalArgumentException(input);
            }
         }
      } // switch

      return newToken;

   } // getTokenForString


   /**
    * Manipulate the tokens on the token stack and move some tokens to
    * the postfix sequence depending on the precedence of this token and
    * the tokens on the stack
    *
    * @param operatorStack a runtime stack of operators
    * @param postfixSequence is the postfix sequence of tokens
    */
   abstract void infixToPostfix(Deque<Operator> operatorStack, Deque<Token> postfixSequence);

   /**
    * @return this token's precedence
    */
   abstract PRECEDENCE getPrecedence();

   /**
    * @return true if this token can appear to the right of the given
    * token
    */
   abstract boolean canFollow(Token aToken);

   /**
    * @return true if this token can appear to the left of an operator
    */
   abstract boolean canPreceedOperator();

   /**
    * @return true if this token can appear to the left of an operand
    */
   abstract boolean canPreceedOperand();

   /**
    * @return true if this token can appear to the left of a left paren
    */
   boolean canPreceedLeftParen() {
      // Treat a left parenthesis as though it were an operand so that
      // oeprators can preceed it
      return canPreceedOperand();
   }

   /**
    * @return true if this token can appear to the left of a right paren
    */
   boolean canPreceedRightParen() {
      // Treat a right parenthesis as though it were an operator so that
      // operands can preceed it
      return canPreceedOperator();
   }

   /**
    * Compute operation for this token
    *
    * @param operandStack a runtime stack of operands
    * @throws ArithmeticException if the stack does not hold sufficient operands
    */
   abstract void compute(Deque<Operand> operandStack) throws ArithmeticException;

   // Define the precedence of token types 
   static enum PRECEDENCE
      {PAREN, OPERAND, ADD_OPERATION, MULT_OPERATION, EXP_OPERATION}; 

} // Token



/**
 * Super class for all arithmetic operators. Operators can help convert infix to 
 * postfix and know how to carry out a computation. Operators also have precedence.
 */
abstract class Operator extends Token {
   /**
    * Move higher-precedence operands from the token stack to the postfix
    * sequence
    *
    * @param operatorStack a runtime stack of operators
    * @param postfixSequence is the postfix sequence of tokens
    */
   void infixToPostfix(Deque<Operator> operatorStack, Deque<Token> postfixSequence) {
      // While what's on the token stack has higher or equal precedence
      // to this token pop it off the token stack and append it to the
      // postfix sequence; don't pop any parenthesis however.
      while (!operatorStack.isEmpty() && 
             operatorStack.peek().getPrecedence().compareTo(getPrecedence()) >= 0) {
         postfixSequence.addLast(operatorStack.pop());
      }
      // Now push this token onto the operatorStack
      operatorStack.push(this);
   }


   /**
    * Remove two operands from the operand stack, perform our operation,
    * and push the result onto the operand stack
    *
    * @param operandStack a runtime stack of operands
    * @throws ArithmeticException if the stack does not hold sufficient operands
    */
   void compute(Deque<Operand> operandStack) throws ArithmeticException {
      Operand left, right;

      // Make sure we have some operands to operate on...
      if (operandStack.size() < 2) {
         throw new ArithmeticException("Too Few Operands");
      }
      // Pop two operands off the stack, compute with them, and push answer back
      // onto the stack
      right = operandStack.pop();
      left = operandStack.pop();
      operandStack.push(compute(left, right));
   }


   /**
    * Compute our result
    * @param left left-hand-side
    * @param right right-hand-side
    * @return result of left OP right
    */
   abstract Operand compute(Operand left, Operand right);


   /**
    * @return true if this token can appear to the right of the given
    * token
    */
   boolean canFollow(Token aToken) {
      // An operator can not appear at the beginning of an equation
      // (i.e., can not follow null) but can follow any token it can
      // preceed
      return aToken!=null && aToken.canPreceedOperator();
   }


   /**
    * @return false since an operator can not preceed another operator
    */
   boolean canPreceedOperator() {
      return false;
   }


   /**
    * @return true since an opeartor can preceed an operand
    */
   boolean canPreceedOperand() {
      return true;
   }

} // Operator



/**
 * Super class for all addition-type operators
 */
abstract class AddOperation extends Operator {
   /**
    * @return the precedence of an addition operator
    */
   PRECEDENCE getPrecedence() {
      return PRECEDENCE.ADD_OPERATION;
   }
} // AddOperation



/**
 * Addition operation performs addition in compute operation
 */
class AddOp extends AddOperation {
   /**
    * @param left is left-hand-side operator
    * @param right is right-hand-side operator
    * @return left + right
    */
   Operand compute(Operand left, Operand right) {
      return (new Operand(left.value() + right.value()));
   }
} // AddOp



/**
 * Subtraction operation performs addition in compute operation
 */
class SubOp extends AddOperation {
   /**
    * @param left is left-hand-side operator
    * @param right is right-hand-side operator
    * @return left / right
    */
   Operand compute(Operand left, Operand right) {
      return (new Operand(left.value() - right.value()));
   }
} // SubOp



/**
 * Super class for all multiplication-type operators
 */
abstract class MultOperation extends Operator {
   /**
    * @return the precedence of a multiplcation operator
    */
   PRECEDENCE getPrecedence() {
      return PRECEDENCE.MULT_OPERATION;
   }
} // MultOperation



/**
 * Multiply operation performs multipliation in compute operation
 */
class MultOp extends MultOperation {
   /**
    * @param left is left-hand-side operator
    * @param right is right-hand-side operator
    * @return left * right
    */
   Operand compute(Operand left, Operand right) {
      return (new Operand(left.value() * right.value()));
   }
} // MultOp



/**
 * @param left is left-hand-side operator
 * @param right is right-hand-side operator
 * @return left / right
 */
class DivOp extends MultOperation {
   Operand compute(Operand left, Operand right) {
      return (new Operand(left.value() / right.value()));
   }
} // DivOp



/**
 * Exponentiation operator raises a value to an exponent
 */
class ExpOp extends Operator {
   /**
    * @return the precedence of a exponentiation operator
    */
   PRECEDENCE getPrecedence() {
      return PRECEDENCE.EXP_OPERATION;
   }

   /**
    * @param left is left-hand-side operator
    * @param right is right-hand-side operator
    * @return left ^ right
    */
   Operand compute(Operand left, Operand right) {
      // Math.pow returns a double, but we're only dealing with int, so
      // convert to an int...
      return (new Operand( (int) Math.pow(left.value(), right.value())));
   }
} // ExpOp



/**
 * Left parenthesis are "operators" that can't compute, but simply push 
 * themselves onto the operator stack to be matched later by a right parenthesis
 */
class LeftParen extends Operator {
   /**
    * @return the precedence of a multiplcation operator
    */
   PRECEDENCE getPrecedence() {
      return PRECEDENCE.PAREN;
   }

   /**
    * Parentesis are not computable! 
    *
    * @throws ArithmeticException always
    */
   Operand compute(Operand left, Operand right) {
      // If we are called to compute then something is wrong!
      throw new ArithmeticException("Parenthesis Mismatch");
   }


   /**
    * Pushes the parenthesis onto the operator stack to wait for the
    * matching closing parenthesis
    */
   void infixToPostfix(Deque<Operator> operatorStack, Deque<Token> postfixSequence) {
      operatorStack.push(this);
   }


   /**
    * @return true if this token can appear to the right of the given
    * token
    */
   boolean canFollow(Token aToken) {
      // A left parenthesis can appear at the beginning of an equation
      // (i.e., can follow null) and can follow any token it can
      // preceed 
      return (aToken==null || aToken.canPreceedLeftParen());
   }


   /**
    * @return false since a left paren can not preceed an operator
    */
   boolean canPreceedOperator() {
      return false;
   }


   /**
    * @return true since a left paren can preceed an operand
    */
   boolean canPreceedOperand() {
      return true;
   }

} // LeftParen



/**
 * Right parenthesis are "operators" that can't compute, but simply empty the
 * operator stack during infix-to-postfix processing until they find a matching
 * left parenthesis.
 */
class RightParen extends Operator {
   /**
    * @return the precedence of a multiplcation operator
    */
   PRECEDENCE getPrecedence() {
      return PRECEDENCE.PAREN;
   }


   /**
    * Parentesis are not computable! 
    *
    * @throws ArithmeticException always
    */
   Operand compute(Operand left, Operand right) {
      // If we are called to compute then something is wrong!
      throw new ArithmeticException("Parenthesis Mismatch");
   }


   /**
    * Move operators from the token stack to the postfix sequence until
    * a matching left parenthesis is found
    *
    * @param operatorStack a runtime stack of tokens
    * @param postfixSequence is the postfix sequence of tokens
    */
   void infixToPostfix(Deque<Operator> operatorStack, Deque<Token> postfixSequence) {
      // Take all operators off the operator stack and push them onto
      // the operand stack until we find a left parenthesis - i.e., a
      // token with the same precedence
      while (!operatorStack.isEmpty() && 
              operatorStack.peek().getPrecedence() != getPrecedence()) {
         postfixSequence.addLast(operatorStack.pop());
      }
      // Better be a paren on top of the stack!
      if (operatorStack.isEmpty()) {
         throw new ArithmeticException("Parenthesis Mismatch");
      }
      operatorStack.pop(); // remove paren from the stack
   }


   /**
    * @return true if this token can appear to the right of the given
    * token
    */
   boolean canFollow(Token aToken) {
      // A right parenthesis can not appear at the beginning of an equation
      // (i.e., can not follow null) and can follow any token it can
      // preceed 
      return (aToken!=null &&  aToken.canPreceedRightParen());
   }


   /**
    * @return true since a right paren can preceed an operator
    */
   boolean canPreceedOperator() {
      return true;
   }


   /**
    * @return false since a right parent can not preceed an operand
    */
   boolean canPreceedOperand() {
      return false;
   }

} // RightParen



/**
 * Operands represent numbers
 */
class Operand extends Token {
   /**
    * Operand from an int value
    *
    * @param value value of the operand
    */
   Operand(int value) {
      _value = value;
   }


   /**
    * Operand from a String
    *
    * @param value value of the operand
    */
   Operand(String value) {
      this(Integer.decode(value));
   }


   /**
    * @return the precedence of this token
    */
   PRECEDENCE getPrecedence() {
      return PRECEDENCE.OPERAND;
   }


   /**
    * @return the value of this operand
    */
   int value() {
      return _value;
   }


   /**
    * Append operand to the postfix string
    */
   void infixToPostfix(Deque<Operator> operatorStack, Deque<Token> postfixSequence) {
      postfixSequence.addLast(this);
   }


   /**
    * Push operand onto operand stack
    */
   void compute(Deque<Operand> operandStack) throws ArithmeticException {
      operandStack.push(this);
   }


   /**
    * @return true if this token can appear to the right of the given
    * token
    */
   boolean canFollow(Token aToken) {
      // An opearnd can appear at the beginning of an equation
      // (i.e., can follow null) and can follow any token it can
      // preceed
      return aToken==null || aToken.canPreceedOperand();
   }


   /**
    * @return true since an opearand can preceed an operator
    */
   boolean canPreceedOperator() {
      return true;
   }


   /**
    * @return false since an operand can not preceed another operand
    */
   boolean canPreceedOperand() {
      return false;
   }


   private int _value;


} // Operand
