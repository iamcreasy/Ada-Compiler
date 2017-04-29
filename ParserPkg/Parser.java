package ParserPkg;

import SymbolTablePkg.*;
import TokenizerPkg.*;

import java.io.IOException;
import java.util.LinkedList;

/* Our grammar
 Prog			->	procedure idt Args is
                    DeclarativePart
                    Procedures
                    begin
                    SeqOfStatements
                    end idt;

 DeclarativePart    ->	IdentifierList : TypeMark ; DeclarativePart | ε
 IdentifierList	    ->	idt | idt IdentifierList_
 IdentifierList_    ->	,idt IdentifierList_ | ε
 TypeMark		->	integert | realt | chart | const assignop Value
 Value			->	NumericalLiteral
 Procedures		-> 	Prog Procedures | ε
 Args			->	( ArgList ) | ε
 ArgList		-> 	Mode IdentifierList : TypeMark MoreArgs
 MoreArgs		-> 	; ArgList | ε
 Mode			->	in | out | inout | ε

 SeqOfStatments	->	Statement  ; StatTail | ε
 StatTail		-> 	Statement  ; StatTail | ε
 Statement		-> 	AssignStat	| IOStat
 AssignStat		->	idt  :=  Expr
 IOStat			->	ε
 Expr			->	Relation
 Relation		->	SimpleExpr
 SimpleExpr		->	Term MoreTerm
 MoreTerm		->	Addop Term MoreTerm | ε
 Term			->	Factor  MoreFactor
 MoreFactor		->  Mulop Factor MoreFactor| ε
 Factor			->	id |
					num	|
					( Expr )|
					not Factor|
					SignOp Factor
 Addop			->	+ | - | or
 Mulop			-> 	* | / | mod | rem | and
 SignOp		    ->	-

 */

public class Parser {
    private Tokenizer tokenizer;
    private Token currentToken;
    private boolean isParsingSuccessful;
    private SymbolTable _symbolTable;
    private LinkedList<Symbol> identifierList = new LinkedList<>();
    private int _identifierListOffset = 0;
    private int _identifierOffset = 2;

    public Parser(String fileName) throws IOException {
        tokenizer = new Tokenizer(fileName);
        currentToken = tokenizer.getNextToken();

        // initialize symbol table before parsing
        _symbolTable = new SymbolTable();

        // initialize parsing
        Prog();

        // print the symbol table of global space
//        _symbolTable.printDepth(_symbolTable.CurrentDepth);

        if(currentToken.getTokenType() != TokenType.eof) {
            System.out.println("At line number " + currentToken.getLineNumber() + " unused token(" + currentToken.getTokenType() + ", " + currentToken.getLexeme() + ") found. Expecting End of File token.");
            System.exit(1);
        }
        else {
            isParsingSuccessful = true;
        }
    }

    // This function implements Prog	->	procedure idt Args is DeclarativePart Procedures begin SeqOfStatements end idt;
    private void Prog(){
        _identifierOffset = 4; // set it back to 4 for the start of new function
        match(currentToken, TokenType.PROCEDURE);
        String functionName = currentToken.getLexeme();
        checkForDuplicateSymbol();
        _symbolTable.insert(functionName, _symbolTable.CurrentDepth).setSymbolType(ESymbolType.function);
        _symbolTable.CurrentDepth++;
        match(currentToken, TokenType.id);

        Args(functionName);
        match(currentToken, TokenType.IS);
        _identifierOffset = 2; // set it back to 2 for local variable offset
        DeclarativePart(functionName);
        Procedures();
        match(currentToken, TokenType.BEGIN);
        SeqOfStatements();

        match(currentToken, TokenType.END);
        if(!functionName.equalsIgnoreCase(currentToken.getLexeme())){
            System.out.println("Error : Missing statement \"END " + functionName+";\"");
            System.exit(1);
        }
        // match the start id
        match(currentToken, TokenType.id);

        match(currentToken, TokenType.semicolon);

//        _symbolTable.printDepth(_symbolTable.CurrentDepth);
        _symbolTable.deleteDepth(_symbolTable.CurrentDepth);
        _symbolTable.CurrentDepth--;
    }

    // This function implements  DeclarativePart	->	IdentifierList : TypeMark ; DeclarativePart | E
    private void DeclarativePart(String functionName_) {
        if(currentToken.getTokenType() == TokenType.id){ // we do not use "currentToken = tokenizer.getNextToken()" here, since we are doing a look ahead
            IdentifierList();
            match(currentToken, TokenType.colon);
            TypeMark(functionName_, null);
            match(currentToken, TokenType.semicolon);
            DeclarativePart(functionName_);
        }
        // else empty production
    }

    // This function implements  IdentifierList  -> 	idt IdentifierList`
    private void IdentifierList() {
        checkForDuplicateSymbol();

        // add the lexeme and it's depth of an identifiers to a temporary data structure (identifierList)
        identifierList.add(_symbolTable.insert(currentToken.getLexeme(), _symbolTable.CurrentDepth));
        match(currentToken, TokenType.id);

        IdentifierList_();
    }

    // This function implements  IdentifierList`	->	,idt IdentifierList` | E
    private void IdentifierList_() {
        if(currentToken.getTokenType() == TokenType.comma){
            currentToken = tokenizer.getNextToken();
            checkForDuplicateSymbol();

            // add remaining the lexeme and it's depth of the identifiers to a temporary data structure (identifierList)
            identifierList.add(_symbolTable.insert(currentToken.getLexeme(), _symbolTable.CurrentDepth));
            match(currentToken, TokenType.id);

            IdentifierList_();
        }
        // else there is no more identifiers
    }

    // This function implements  TypeMark	->	integert | realt | chart | const assignop Value
    private void TypeMark(String functionName_, EParameterModeType parameterMode_) {
        if(currentToken.getTokenType() == TokenType.INTEGER |
                currentToken.getTokenType() == TokenType.FLOAT |
                currentToken.getTokenType() == TokenType.CHAR |
                currentToken.getTokenType() == TokenType.CONSTANT){

            // if TypeMark is integert, realt and chart,
            // then add respective attributes to the variable identifiers in the temporary data structure(identifierList)
            if(currentToken.getTokenType() == TokenType.INTEGER |
                    currentToken.getTokenType() == TokenType.FLOAT |
                    currentToken.getTokenType() == TokenType.CHAR){

                int variableSize;
                EVariableType variableType;
                if(currentToken.getTokenType() == TokenType.INTEGER) {
                    variableType = EVariableType.integerType;
                    variableSize = 2;
                } else if(currentToken.getTokenType() == TokenType.FLOAT) {
                    variableType = EVariableType.floatType;
                    variableSize = 4;
                }else {
                    variableType = EVariableType.characterType;
                    variableSize = 1;
                }

                // go through all the constant identifiers and set their attributes
                // such as, symbol type(variable or constant), variable type(int, float or char),
                // variable size, variable offset
                for(Symbol symbol : identifierList){
                    // if symbol has not been initialized
                    if(symbol.getSymbolType() == null){

                        // set symbol type
                        symbol.setSymbolType(ESymbolType.variable);

                        // set variable type
                        symbol.variableAttributes.typeOfVariable = variableType;

                        // set variable size
                        symbol.variableAttributes.size = variableSize;
                    }
                }

                currentToken = tokenizer.getNextToken();
            }

            // if TypeMark is constant
            // then add appropriate attributes to the constant identifiers in the temporary data structure(identifierList)
            else if(currentToken.getTokenType() == TokenType.CONSTANT){
                // get the attributes by parsing the rest of the grammar : assignOp value
                currentToken = tokenizer.getNextToken();
                match(currentToken, TokenType.assignop);
                String numberTokenString = Value(); // this block does not end with getNextToken because, it happens in Value function

                // populate the attributes
                EVariableType constantType;
                if(numberTokenString.indexOf('.') == -1)
                    constantType = EVariableType.integerType;
                else
                    constantType = EVariableType.floatType;

                // go through all the constant identifiers and set their attributes
                // such as, symbol type(variable or consant), constant type(integer constant or float constant), numeric value(numeric values)
                for(Symbol symbol : identifierList){
                    if(symbol.getSymbolType() == null){
                        // set symbol type
                        symbol.setSymbolType(ESymbolType.constant);

                        // set constant type
                        symbol.constantAttributes.typeOfConstant = constantType;
                        // set numeric value
                        if(constantType == EVariableType.integerType) {
                            symbol.constantAttributes.value = Integer.parseInt(numberTokenString);
                            symbol.constantAttributes.size = 2;
                        }
                        else {
                            symbol.constantAttributes.valueR = Float.parseFloat(numberTokenString);
                            symbol.constantAttributes.size = 4;
                        }
                    }
                }
            }

            Symbol funcSymbol = _symbolTable.lookup(functionName_, ESymbolType.function);
            // if a valid parameter mode was passed to this method then all the identifiers in the identifierList are function parameters
            if(parameterMode_ != null) {
                // since all identifiers are function parameter we need to add parameter type and mode in another linked list
                for(int i = _identifierListOffset; i<identifierList.size(); i++){
                    _identifierListOffset++;
                    Symbol symbol = identifierList.get(i);
                    if (symbol.constantAttributes == null) {
                        funcSymbol.functionAttributes.parameterTypeList.add(symbol.variableAttributes.typeOfVariable);
                        symbol.variableAttributes.isParameter = true;
                    }
                    else {
                        funcSymbol.functionAttributes.parameterTypeList.add(symbol.constantAttributes.typeOfConstant);
                        symbol.constantAttributes.isParameter = true;
                    }

                    // add the mode for every symbol
                    funcSymbol.functionAttributes.parameterModeList.add(parameterMode_);
                }
            }
            // if no valid parameter mode was passed in then all the identifiers in the identifierList are local variables
            else {
                // set offset to local variables to 2, 4, 6 so on
                for(Symbol symbol : identifierList){
                    symbol.setOffset(_identifierOffset);
                    _identifierOffset += symbol.getSize();
                }

                // offset also represents the size of all local variable because offset is always incremented when a new identifier is added
                // we subtract 2 because our offset started at 2
                funcSymbol.functionAttributes.sizeOfLocalVariable = _identifierOffset - 2;

                // clear the list
                identifierList.clear();
            }
        }

        // looking for TypeMark but didn't find any, stop parsing and report error
        else {
            System.out.println("At line number " + currentToken.getLineNumber() + ", expecting integer/float/char/const , but found " + currentToken.getTokenType() + " token with lexeme " + currentToken.getLexeme());
            System.exit(1);
        }
    }

    // This function implements  Value ->	NumericalLiteral
    private String Value() {
        String value = currentToken.getLexeme();
        match(currentToken,TokenType.num);
        return value;
    }

    // This function implements  Procedures  -> 	Prog Procedures | E
    private void Procedures() {
        if(currentToken.getTokenType() == TokenType.PROCEDURE){ // we do not use "currentToken = tokenizer.getNextToken()" here, since we are doing a look ahead
            Prog();
            Procedures();
        }
        // else empty statement
    }

    // This function implements  Args	->	( ArgList ) | E
    private void Args(String functionName_) {
        if(currentToken.getTokenType() == TokenType.lparen) {
            currentToken = tokenizer.getNextToken();
            ArgList(functionName_);
            match(currentToken, TokenType.rparen);

            // assign offset for all function parameters
            for(int i = identifierList.size()-1; i>= 0; i--){
                Symbol symbol = identifierList.get(i);
                symbol.setOffset(_identifierOffset);
                _identifierOffset += symbol.getSize();
            }

            // set the size of parameters in the function
            Symbol funcSymbol = _symbolTable.lookup(functionName_, ESymbolType.function);
            funcSymbol.functionAttributes.numberOfParameter = identifierList.size();
            funcSymbol.functionAttributes.sizeOfParameters = _identifierOffset - 4;

            // clear the function parameters from the list
            identifierList.clear();
        }
        // no more function parameters
    }

    // This function implements  ArgList	-> 	Mode IdentifierList : TypeMark MoreArgs
    private void ArgList(String functionName_) {
        EParameterModeType parameterMode = Mode();
        IdentifierList();
        match(currentToken, TokenType.colon);
        TypeMark(functionName_, parameterMode);

        MoreArgs(functionName_);
    }

    // This function implements MoreArgs	-> 	; ArgList | E
    private void MoreArgs(String functionName_) {
        if(currentToken.getTokenType() == TokenType.semicolon){
            currentToken = tokenizer.getNextToken();
            ArgList(functionName_);
        }
    }

    // This function implements Mode	->	in | out | inout | E
    private EParameterModeType Mode() {
        String lexeme = currentToken.getLexeme();
        if(lexeme.equalsIgnoreCase("IN") | lexeme.equalsIgnoreCase("OUT") | lexeme.equalsIgnoreCase("INOUT")) {

            EParameterModeType parameterMode;
            if(lexeme.equalsIgnoreCase("IN"))
                parameterMode = EParameterModeType.in;
            else if(lexeme.equalsIgnoreCase("OUT"))
                parameterMode = EParameterModeType.out;
            else
                parameterMode = EParameterModeType.inout;

            currentToken = tokenizer.getNextToken();
            return parameterMode;
        } else {
            return EParameterModeType.in;
        }
        // else empty production
    }

    // Thie grammar checks for id token because we allow only assignment statement and IO statement
    // All assignment statement has to start with an identifier token
    // This function implements  SeqOfStatments	->	Statement  ; StatTail | ε
    private void SeqOfStatements() {
        if(currentToken.getTokenType() == TokenType.id){
            Statement();
            match(currentToken, TokenType.semicolon);
            StatTail();
        }
        // else empty production
    }

    // StatTail		-> 	Statement  ; StatTail | ε
    private void StatTail(){
        if(currentToken.getTokenType() == TokenType.id){
            Statement();
            match(currentToken, TokenType.semicolon);
            StatTail();
        }
        // else empty production
    }

    // Statement		-> 	AssignStat	| IOStat
    private void Statement(){
        if(currentToken.getTokenType() == TokenType.id){
            AssignStat();
        } else {
            IOStat();
        }
    }

    // AssignStat		->	idt  :=  Expr
    private void AssignStat() {
        // check if the variable is declared before use
        Symbol symbol = _symbolTable.lookup(currentToken.getLexeme());
        if(symbol != null && symbol.depth <= _symbolTable.CurrentDepth) {
            match(currentToken, TokenType.id);
        } else {
            System.out.println("Error: Undefined identifier " + currentToken.getLexeme() + " at line number " + currentToken.getLineNumber());
            System.exit(1);
        }
        match(currentToken, TokenType.assignop);
        Expr();
    }

    // IOStat			->	ε
    private void IOStat() {
        return;
    }

    // Expr			->	Relation
    private void Expr() {
        Relation();
    }

    // Relation		->	SimpleExpr
    private void Relation() {
        SimpleExpr();
    }

    // SimpleExpr		->	Term MoreTerm
    private void SimpleExpr() {
        Term();
        MoreTerm();
    }

    // MoreTerm		->	Addop Term MoreTerm | ε
    private void MoreTerm() {
        if(currentToken.getTokenType() == TokenType.addop){
            match(currentToken, TokenType.addop);
            Term();
            MoreTerm();
        }
    }

    // Term			->	Factor  MoreFactor
    private void Term() {
        Factor();
        MoreFactor();
    }

    // MoreFactor		->  Mulop Factor MoreFactor| ε
    private void MoreFactor() {
        if(currentToken.getTokenType() == TokenType.mulop){
            match(currentToken, TokenType.mulop);
            Factor();
            MoreFactor();
        }
    }

    // Factor			->	id | num | ( Expr ) | not Factor | SignOp Factor
    private void Factor() {
        if(currentToken.getTokenType() == TokenType.id){
            Symbol symbol = _symbolTable.lookup(currentToken.getLexeme());
            if(symbol != null && symbol.depth <= _symbolTable.CurrentDepth) {
                match(currentToken, TokenType.id);
            } else {
                System.out.println("Error: Undefined identifier " + currentToken.getLexeme() + " at line number " + currentToken.getLineNumber());
                System.exit(1);
            }
            // todo replace the value of the variable with the value if the variable is constant
        } else if(currentToken.getTokenType() == TokenType.num){
            match(currentToken, TokenType.num);
        } else if(currentToken.getTokenType() == TokenType.lparen){
            match(currentToken, TokenType.lparen);
            Expr();
            match(currentToken, TokenType.rparen);
        } else
            SignOp();
    }

    // SignOp		    ->	-
    private void SignOp() {
        if(currentToken.getLexeme() == "-"){
            currentToken = tokenizer.getNextToken();
            Factor();
        }
    }

    /**
     * Matches if the currentToken is same as the desired token type.
     * If we do not get the desired token it is a fatal error, and we print the error and exit the program.
     * @param localCurrentToken Current token
     * @param desiredToken The token type we are looking for
     */
    private void match(Token localCurrentToken, TokenType desiredToken) {
        if(localCurrentToken.getTokenType() != desiredToken){
            System.out.println("At line number " + currentToken.getLineNumber() + ", expecting " + desiredToken + " token, but found " + currentToken.getTokenType() + " token with lexeme " + currentToken.getLexeme());
            System.exit(1);
        } else {
            currentToken = tokenizer.getNextToken();
        }
    }

    private void checkForDuplicateSymbol() {
        Symbol symbol = _symbolTable.lookup(currentToken.getLexeme());
        if(symbol != null && symbol.depth == _symbolTable.CurrentDepth){
            System.out.println("Error: Duplicate symbol: '" +currentToken.getLexeme() + "' at line number " + currentToken.getLineNumber());
            System.exit(1);
        }
    }

    public boolean isParsingSuccessful(){
        return isParsingSuccessful;
    }
}

