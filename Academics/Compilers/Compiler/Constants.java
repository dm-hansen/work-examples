/**
 * This class is a placeholder for enumerations used by the compiler
 */

public abstract class Constants
{
    /**
     * Public enumeration of all the types of grammar symbols
     */
    public static enum NON_TERMINAL implements Symbol
    {
        STATEMENT, 
        ELSE_CLAUSE,
        STATEMENT_LIST,
        SEPARATED_LIST,
        PRINT_EXPRESSION,
        EXPRESSION,
        ARITHMETIC_TERM,
        RELATIONAL_OP,
        BOOLEAN_EXPRESSION,
        TERM;

        /* NON Terminal is true, all others are false and we do nothing */
        public boolean isTerminal() {return false;};
        public boolean isNonTerminal() {return true;};
        public boolean isOperator() {return false;};
        public boolean isAction() {return false;};
        public void doYourThing() throws Exception {throw new Exception("Non-Terminal symbol is not an action");};
    }



    /**
     * Public enumeration of all the types of tokens
     */
    public static enum TOKEN implements Symbol
    {
        END_OF_INPUT,
        VARIABLE, 
        IDENTIFIER,
        NUMBER,
        PRINT,
        IF,
        THEN,
        ELSE,
        WHILE,
        DO,
        BEGIN,
        END,
        STATEMENT_SEPARATOR,
        ASSIGNMENT_OP,
        // Operators need to be marked as such
        LESS_THAN_OP(){public boolean isOperator(){return true;}},
        LESS_THAN_OR_EQUAL_OP(){public boolean isOperator(){return true;}},
        NOT_EQUAL_OP(){public boolean isOperator(){return true;}},
        EQUAL_OP(){public boolean isOperator(){return true;}},
        GREATER_THAN_OP(){public boolean isOperator(){return true;}},
        GREATER_THAN_OR_EQUAL_OP(){public boolean isOperator(){return true;}},
        ADD_OP(){public boolean isOperator(){return true;}},
        SUBTRACT_OP(){public boolean isOperator(){return true;}},
        MULTIPLY_OP(){public boolean isOperator(){return true;}},
        DIVIDE_OP(){public boolean isOperator(){return true;}},
        COMMENT, 
        STRING, 
        WHITESPACE,
        LEFT_PAREN,
        RIGHT_PAREN;

        /* Terminal is true, all others are false and we do nothing */
        public boolean isTerminal() {return true;};
        public boolean isNonTerminal() {return false;};
        public boolean isOperator() {return false;};
        public boolean isAction() {return false;};
        public void doYourThing() throws Exception {throw new Exception("Token is not an action");};
    }



    // The following constants are strings that are the prologue for the
    // code we generate and the end
    public static final String CODE_HEADER = ".class public %s\n.super java/lang/Object\n.method public <init>()V\naload_0\ninvokenonvirtual java/lang/Object/<init>()V\nreturn\n.end method\n.method public static main([Ljava/lang/String;)V\n.limit locals 10\n.limit stack 10\n";

    public static final String CODE_FOOTER = "return\n.end method";




    /**
     * Public enumeration of all the types of actions, along with their
     * implementation
     */
    public static enum ACTION implements Symbol
    {
        STORE() 
        {
            /**
             *  Emit code to store the most recent variable pushed
             *  onto the compilers argument stack
             */
            public void doYourThing() throws Exception
            {
                Compiler.outstream.println("istore "+Compiler.operandStack.pop().getAddress());
            }
        },
        LOAD() 
        {
            /**
             *  Emit code to load the most recent variable pushed onto
             *  the compilers argument stack
             */
            public void doYourThing() throws Exception
            {
                Compiler.outstream.println("iload "+Compiler.operandStack.pop().getAddress());
            }
        },
        PUSH() 
        {
            /**
             *  Emit code to push the most recent constant value pushed
             *  onto the compilers argument stack
             */
            public void doYourThing() throws Exception
            {
                Compiler.outstream.println("sipush "+Compiler.operandStack.pop().getLexeme());
            }
        },
        LOAD_CONST() 
        {
            /**
             *  Emit code to push the most recent constant value pushed
             *  onto the compilers argument stack
             */
            public void doYourThing() throws Exception
            {
                Compiler.outstream.println("ldc "+Compiler.operandStack.pop().getLexeme());
            }
        },
        COMPUTE() 
        {
            /**
             *  Emit code to load the most recent variable pushed
             */
            public void doYourThing() throws Exception
            {
                switch(Compiler.operatorStack.pop().getTokenType())
                {
                    case LESS_THAN_OP:
                        // If !< (i.e., if >=) jump to end
                        Compiler.outstream.println("if_icmpge "+Compiler.endLabelStack.peek());
                        break;
                    case LESS_THAN_OR_EQUAL_OP:
                        // If !<= (i.e., if >) jump to end
                        Compiler.outstream.println("if_icmpgt "+Compiler.endLabelStack.peek());
                        break;
                    case NOT_EQUAL_OP:
                        // If != (i.e., if =) jump to end
                        Compiler.outstream.println("if_icmpeq "+Compiler.endLabelStack.peek());
                        break;
                    case EQUAL_OP:
                        // If = (i.e., if !=) jump to end
                        Compiler.outstream.println("if_icmpne "+Compiler.endLabelStack.peek());
                        break;
                    case GREATER_THAN_OP:
                        // If !> (i.e., if <=) jump to end
                        Compiler.outstream.println("if_icmple "+Compiler.endLabelStack.peek());
                        break;
                    case GREATER_THAN_OR_EQUAL_OP:
                        // If !>= (i.e., if <) jump to end
                        Compiler.outstream.println("if_icmplt "+Compiler.endLabelStack.peek());
                        break;
                    case ADD_OP:
                        // add
                        Compiler.outstream.println("iadd");
                        break;
                    case SUBTRACT_OP:
                        // subtract
                        Compiler.outstream.println("isub");
                        break;
                    case MULTIPLY_OP:
                        // multiply 
                        Compiler.outstream.println("imul");
                        break;
                    case DIVIDE_OP:
                        // divide
                        Compiler.outstream.println("idiv");
                        break;
                    default:
                        throw new Exception("Unknown operation to COMPUTE");
                }// switch
            }
        },
        OP_PUSH() 
        {
            /**
             *  Emit code to load the most recent variable pushed
             */
            public void doYourThing()
            {
                // Handle when reading lexemes just like numbers and
                // identifiers
            }
        },
        DECLARE() 
        {
            /**
             *  Add this lexeme to the symbol table and set its location 
             */
            public void doYourThing()
            {
                Lexeme variable = Compiler.operandStack.pop();
                variable.setAddress(Compiler.variableNumber++);
                Compiler.symbolTable.put(variable.getLexeme(), variable);
            }
        },
        PRINT_HEADER() 
        {
            /**
             *  Emit code to load System.out
             */
            public void doYourThing()
            {
                Compiler.outstream.println("getstatic java/lang/System/out Ljava/io/PrintStream;");
            }
        },
        PRINT_IFOOTER() 
        {
            /**
             *  Emit code to call print for an integer
             */
            public void doYourThing()
            {
                Compiler.outstream.println("invokevirtual java/io/PrintStream/print(I)V");
            }
        },
        PRINT_SFOOTER() 
        {
            /**
             *  Emit code to call print for a string
             */
            public void doYourThing()
            {
                Compiler.outstream.println("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V");
            }
        },
        GEN_LABELS() 
        {
            /**
             * Create new begin and end labels and push them onto the 
             * label stacks
             */
            public void doYourThing()
            {
                Compiler.beginLabelStack.push("begin"+Compiler.labelNumber);
                Compiler.endLabelStack.push("end"+Compiler.labelNumber++);
            }
        },
        POP_LABELS() 
        {
            /**
             * Pop old begin and end labels from the 
             * label stacks
             */
            public void doYourThing()
            {
                Compiler.beginLabelStack.pop();
                Compiler.endLabelStack.pop();
            }
        },
        GOTO_BEGIN() 
        {
            /**
             *  Emit code to goto the current begin label
             */
            public void doYourThing()
            {
                Compiler.outstream.println("goto "+Compiler.beginLabelStack.peek());
            }
        },
        GOTO_END() 
        {
            /**
             *  Emit code to goto the current end label
             */
            public void doYourThing()
            {
                Compiler.outstream.println("goto "+Compiler.endLabelStack.peek());
            }
        },
        BEGIN_LABEL() 
        {
            /**
             *  Emit begin label
             */
            public void doYourThing()
            {
                Compiler.outstream.println(Compiler.beginLabelStack.peek()+":");
            }
        },
        END_LABEL() 
        {
            /**
             *  Emit end label
             */
            public void doYourThing()
            {
                Compiler.outstream.println(Compiler.endLabelStack.peek()+":");
            }
        };

        /* Terminal is true, all others are false and we do nothing */
        public boolean isTerminal() {return false;};
        public boolean isNonTerminal() {return false;};
        public boolean isOperator() {return false;};
        public boolean isAction() {return true;};
    }
}
