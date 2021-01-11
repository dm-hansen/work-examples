/**
 * Interface that unifies all symbols that the parser needs to deal
 * with. These include lexemes (i.e., terminals), non-terminals, and
 * actions. This allows the parser to use a stack that holds varieties
 * of things and deal with each as appropriate
 */
public interface Symbol 
{
    /**
     * @return true if this symbol is a terminal symbol
     */
    public boolean isTerminal();

    /**
     * @return true if this symbol is a non-terminal symbol
     */
    public boolean isNonTerminal();

    /**
     * @return true if this symbol is an operator
     */
    public boolean isOperator();

    /**
     * @return true if this symbol is an action symbol
     */
    public boolean isAction();

    /**
     * Invoke the appropriate action for this action symbol
     */
    public void doYourThing() throws Exception;
}
