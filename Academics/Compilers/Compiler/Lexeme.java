/**
 * Class for representing lexemes encountered by the Lexical Analyzer.
 * The Lexical Analyzer will return objects of this type to the
 * Syntactic Analyzer. Non-keyword lexemes will be entered into the
 * symbol table and augmented with additional information by the
 * compiler. This class implements the symbol interface by passing the
 * Symbol messages on to the embedded token
 *
 * @see Symbol
 */
class Lexeme 
{

    /**
     * Create a new lexeme
     *
     * @param lexeme is the actual string
     * @param tokenType is the enumerated type for this lexeme
     */
    public Lexeme(String lexeme, Constants.TOKEN tokenType)
    {
        _lexeme = lexeme;
        _tokenType = tokenType;
    }

    /**
     * @return the string representation of this lexeme, i.e., its value
     */
    public String getLexeme()
    {
        return _lexeme;
    }

    /**
     * Set the address of this lexeme
     * @param the memory address of this lexeme
     */
    public void setAddress(int address)
    {
        _address = address;
    }

    /**
     * @return the memory address of this lexeme
     */
    public int getAddress()
    {
        return _address;
    }

    /**
     * @return the type of this lexeme
     */
    public Constants.TOKEN getTokenType()
    {
        return _tokenType;
    }

    /**
     * @return string representation of this lexeme and it's type
     */
    public String toString()
    {
        return "Lexeme: '"+_lexeme+"'  Token Type: '"+_tokenType+
            "'    Address "+_address;
    }

    private String _lexeme;
    private int _address;
    private Constants.TOKEN _tokenType;
}
