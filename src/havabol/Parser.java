package havabol;

import havabol.SymbolTable.STIdentifier;
import havabol.SymbolTable.SymbolTable;

import java.util.Stack;
import java.util.regex.Pattern;

/*
 * This is the simple Parser class for the HavaBol programming language.
 * All errors and exceptions are thrown up to the calling method and output to stderr from there.
 */
public class Parser
{
    // public values to help keep track of our havabol objects
    public SymbolTable symbolTable;
    public StorageManager storageManager;
    public Scanner scan;

    /**
     * Parser constructor that takes in the symbolTable, storageManager, and Scanner
     * objects and saves them to our class.
     * <p>
     * These objects will be used for the rest of the class in order to parse our source file
     * and interpret the code.
     *
     * @param symbolTable object that contains symbol definitions for our programming language
     * @param storageManager object that contains all user specified variables and their values and types
     * @param scan object that contains our source file and will aid in parsing the file
     */
    public Parser(SymbolTable symbolTable, StorageManager storageManager, Scanner scan)
    {
        this.symbolTable = symbolTable;
        this.storageManager = storageManager;
        this.scan = scan;
    }

    /**
     * This method gets the next token from the source file line using Scanner::getNext().
     * Then it goes through a switch statement to determine what to do with the given token.
     * <p>
     * Returns resultValues returned to the method by the helper functions provided to it.
     *
     * @param bExec Tells the statement function whether we need to execute the code we find or
     *              just look at it
     * @return A generic ResultValue with all fields VOID if the bottom is hit, otherwise returns
     *         the calculated ResultValue
     * @throws Exception generic Exception type to handle any processing errors
     */
    public ResultValue statement(Boolean bExec) throws Exception
    {
        // advance token
        scan.getNext();

        // determine what to do with the current token
        switch (scan.currentToken.primClassif)
        {// CONTROL, OPERAND, FUNCTION, OPERATOR, SEPARATOR, EOF, DEBUG, or defaults to error
            case Token.CONTROL:
                switch (scan.currentToken.subClassif)
                { // control token found, so determine the sub type for proper execution
                  // DECLARE, FLOW, END, defaults to error
                    case Token.DECLARE:
                        return declareStmt(bExec);
                    case Token.FLOW:
                        if (scan.currentToken.tokenStr.equals("if"))
                            return ifStmt(bExec);
                        else if (scan.currentToken.tokenStr.equals("while"))
                            return whileStmt(bExec);
                        else if (scan.currentToken.tokenStr.equals("for"))
                            return forStmt(bExec);
                        break;
                    case Token.END:
                        // end token so return
                        return new ResultValue("", Token.END
                                , ResultValue.primitive, scan.currentToken.tokenStr);
                    // should never hit this, otherwise MAJOR FUCK UP
                    default:
                        error("ERROR: UNIDENTIFIED CONTROL VARIABLE %s"
                                , scan.currentToken.tokenStr);
                }
                break;
            case Token.OPERAND:
                return assignStmt(bExec);
            case Token.FUNCTION:
                return function(bExec);
            case Token.OPERATOR:
            case Token.SEPARATOR:
            case Token.EOF:
                break;
            case Token.DEBUG:
                switch ( scan.getNext() )
                {// debug token found, determine which to change
                    case "Token":
                        scan.currentToken.primClassif = Token.DEBUG;

                        switch (scan.getNext())
                        {// are we turning on or off
                            case "on":
                                scan.currentToken.primClassif = Token.DEBUG;
                                if (!scan.getNext().equals(";"))
                                    error("MISSING ';'");
                                scan.bShowToken = true;
                                break;
                            case "off":
                                scan.currentToken.primClassif = Token.DEBUG;
                                if (!scan.getNext().equals(";"))
                                    error("MISSING ';'");
                                scan.bShowToken = false;
                                break;
                            default:
                                error("ERROR: HAS TO BE EITHER 'on' OR 'off' ");
                        }
                        break;
                    case "Expr":
                        scan.currentToken.primClassif = Token.DEBUG;

                        switch (scan.getNext())
                        {// are we turning on or off
                            case "on":
                                scan.currentToken.primClassif = Token.DEBUG;
                                if (!scan.getNext().equals(";"))
                                    error("MISSING ';'");
                                scan.bShowExpr = true;
                                break;
                            case "off":
                                scan.currentToken.primClassif = Token.DEBUG;
                                if (!scan.getNext().equals(";"))
                                    error("MISSING ';'");
                                scan.bShowExpr = false;
                                break;
                            default:
                                error("ERROR: HAS TO BE EITHER 'on' OR 'off' ");
                        }
                        break;
                    case "Assign":
                        scan.currentToken.primClassif = Token.DEBUG;

                        switch (scan.getNext())
                        {// are we turning on or off
                            case "on":
                                scan.currentToken.primClassif = Token.DEBUG;
                                if (!scan.getNext().equals(";"))
                                error("MISSING ';' ");
                                scan.bShowAssign = true;
                                break;
                            case "off":
                                scan.currentToken.primClassif = Token.DEBUG;
                                if (!scan.getNext().equals(";"))
                                    error("MISSING ';'");
                                scan.bShowAssign = false;
                                break;
                            default:
                                error("ERROR: HAS TO BE EITHER 'on' OR 'off' ");
                        }
                        break;
                    default:
                        error("ERROR: HAS TO BE EITHER 'Expr', 'Assign', OR 'Token' " +
                                "                       AND FOUND " + scan.currentToken.tokenStr);
                }
                break;
            // should never hit this, otherwise MAJOR FUCK UP
            default:
                error("INTERNAL ERROR CAUSED BY %s", scan.currentToken.tokenStr);
        }
        return new ResultValue("", Token.VOID, Token.VOID, scan.currentToken.tokenStr);
    }

    /**
     * This method is called when ever a Control Declare token is encountered.
     * It will determine the type and assign it to the next token's result value
     * <p>
     * This function will also allow havabol to support declaring and initializing on
     * the same line.
     *
     * @param bExec Tells the statement function whether we need to execute the code we find or
     *              just look at it
     * @return ResultValue object that contains the final result of execution
     * @throws Exception generic Exception type to handle any processing errors
     */
    public ResultValue declareStmt(Boolean bExec) throws Exception
    {
        int structure = -1;
        int dclType = -1;

        switch (scan.currentToken.tokenStr)
        {// check data type of the current token
            case "Int":
                dclType = Token.INTEGER;
                break;
            case "Float":
                dclType = Token.FLOAT;
                break;
            case "Bool":
                dclType = Token.BOOLEAN;
                break;
            case "String":
                dclType = Token.STRING;
                break;
            default:
                error("ERROR: INVALID DECLARE DATA TYPE %s", scan.currentToken.tokenStr);
        }

        // advance to the next token
        scan.getNext();

        // the next token should of been a variable name, otherwise error
        if(scan.currentToken.primClassif != Token.OPERAND)
            error("ERROR: %s SHOULD BE AN OPERAND FOR DECLARATION", scan.currentToken.tokenStr);

        if (bExec)
        {// we are executing, not ignoring
            // put declaration into symbol table and storage manager
            String variableStr = scan.currentToken.tokenStr;

            symbolTable.putSymbol(variableStr, new STIdentifier(variableStr
                                                , scan.currentToken.primClassif, dclType
                                                , 1, 1, 1));
            storageManager.putEntry(variableStr, new ResultValue(dclType, structure));
        }

        // if the next token is '=' call assignStmt to assign value to operand
        if(scan.nextToken.tokenStr.equals("="))
            return assignStmt(bExec);
        // else check if it is an operator, because that is an error
        else if (scan.nextToken.primClassif == Token.OPERATOR)
            error("ERROR: DECLARE CANNOT PERFORM %s OPERATION BEFORE INITIALIZATION"
                        , scan.nextToken.tokenStr);
        // else check for statement terminating ';'
        else if(! scan.getNext().equals(";"))
            error("ERROR: UNTERMINATED DECLARATION STATEMENT, ';' EXPECTED");

        return new ResultValue("", Token.DECLARE, ResultValue.primitive
                                                        , scan.currentToken.tokenStr);
    }

    /**
     * This method is called when ever an operand or a '=' is encountered.
     * It will determine the value and assign it to the specified operand
     * <p>
     * Also handles +=, -=, *=, /=
     *
     * @param bExec Tells the statement function whether we need to execute the code we find or
     *              just look at it
     * @return ResultValue object that contains the final result of execution
     * @throws Exception generic Exception type to handle any processing errors
     */
    public ResultValue assignStmt(Boolean bExec) throws Exception
    {
        // not used rn
        Numeric nOp2;  // numeric value of second operand
        Numeric nOp1;  // numeric value of first operand
        ResultValue res = null;
        ResultValue resExpr;

        // used
        String variableStr;
        int leftType = storageManager.getEntry(scan.currentToken.tokenStr).type;

        // make sure current token is an identifier to properly assign
        if (scan.currentToken.subClassif != Token.IDENTIFIER)
            error("ERROR: %s IS NOT A VALID TARGET VARIABLE FOR ASSIGNMENT"
                                                    , scan.currentToken.tokenStr);
        variableStr = scan.currentToken.tokenStr;

        // advance to the next token
        scan.getNext();

        // make sure current token is an operator
        if (scan.currentToken.primClassif != Token.OPERATOR)
            error("ERROR: ASSIGN EXPECTED AN OPERATOR BUT FOUND %s", scan.currentToken.tokenStr);

        // determine what kind of operation to execute
        switch (scan.currentToken.tokenStr)
        {
            case "=":
                if (bExec)
                {
                    ResultValue res1 = assign(variableStr, expression(), leftType);
                    return res1;
                }
                else
                    skipTo(scan.currentToken.tokenStr, ";");
                break;
            // see parsing part 2
            case "+=":
            case "-=":
            case "*=":
            case "/=":
            case "^=":
                break;
            default:
                error("ERROR: EXPECTED ASSIGNMENT OPERATOR BUT FOUND %s", scan.currentToken.tokenStr);
        }

        // if we ever hit this line, bExec is false
        return new ResultValue("", Token.VOID, ResultValue.primitive
                                                        , scan.currentToken.tokenStr);
    }

    /**
     * This method will assign the ResultValue object returned by expr() to the
     * variable string specified. The variable string will be the key.
     * <p>
     * Makes sure that the variable has been declared to a type already.
     *
     * @param variableStr contains the string of the variable we are assigning a value to
     * @param resExpr the ResultValue object which contains the value to assign
     * @param type Token type which we will be assigning
     * @return ResultValue object that contains the final result of execution
     * @throws Exception generic Exception type to handle any processing errors
     */
    private ResultValue assign(String variableStr, ResultValue resExpr, int type) throws Exception
    {
        // get entry from storage manager and make sure it is in the table
        ResultValue res = storageManager.getEntry(variableStr);
        if(res == null)
            error("ERROR: ASSIGN REQUIRES THAT '%s' BE DECLARED", variableStr);

        switch (type)
        {// determine the type of value to assign to variable
            case Token.INTEGER:
                 resExpr.value = Utilities.toInteger(this, resExpr);
                break;
            case Token.FLOAT:
                resExpr.value = Utilities.toFloat(this, resExpr);
                resExpr.type = Token.FLOAT;
                break;
            case Token.BOOLEAN:
                resExpr.value = Utilities.toBoolean(this, resExpr);
                break;
            case Token.STRING:
                break;
            default:
                error("ERROR: ASSIGN TYPE '%s' IS NOT A RECOGNIZED TYPE", variableStr);
        }

        // assign value to the variable and return result value
        storageManager.putEntry(variableStr, resExpr);

        // check for debug on
        if(scan.bShowAssign)
        {
            System.out.print("\t\t...");
            System.out.println("Variable Name: " + variableStr + " Value: " + resExpr.value);
        }

        return resExpr;
    }

    /**
     * This method will evaluate an expression and return a ResultValue object
     * that contains the final result.
     * <p>
     * Handles complex expression. This method assumes that the current token is
     * the token before the start of the expression. When it returns, the current token is at
     * the token succeeding the evaluated expression.
     *
     * @return ResultValue object that contains the final result of execution
     * @throws Exception generic Exception type to handle any processing errors
     */
    public ResultValue expression() throws Exception
    {
        //Stack for resulting values
        Stack outPutStack = new Stack<ResultValue>();
        //Operand stack
        Stack stack = new Stack<Token>();

        //Tokens used for operators and operands
        Token poppedOperator, popped;
        Token operand;
        //Resulting value for operands and final result.
        ResultValue firstResValue, secondResValue, res;

        Boolean bFound; //Boolean to determine if we found left paren
        Boolean inFunc = false; //Boolean to signal if we are in an expression called from function
        Boolean bCategory = false; //Boolean to check proper infix notation
        Boolean moveForward = true; //Boolean used to control moving forward to next token

        //Check if this expression was called from function()
        if(scan.currentToken.primClassif == Token.FUNCTION)
        {
            //System.out.println(scan.currentToken.tokenStr);
            inFunc = true;
            //Symbol token used to know when function operation ends
            Token hashTag = new Token("#");
            hashTag.primClassif = Token.SEPARATOR;
            stack.push(hashTag);
        }

        //Go to start of expression
        scan.getNext();

        //Control token used to check for unary minus
        Token prevToken = scan.currentToken;
        //Loop through expression
        while(scan.currentToken.primClassif == Token.OPERAND //Check if token is operand
                || scan.currentToken.primClassif == Token.OPERATOR //Check if it is an operator
                || scan.currentToken.primClassif == Token.FUNCTION // check for functions
                || "()[],".contains(scan.currentToken.tokenStr)) //Check if its separator

        {
            //System.out.println(scan.currentToken.tokenStr + " Current token in while loop");
            //Check token type
            switch (scan.currentToken.primClassif)
            {
                //If token is an operand
                case Token.OPERAND:
                    if(bCategory == true)
                        error("EXPECTED OPERATOR AND FOUND " + scan.currentToken.tokenStr);
                    //Set operand equal to current token
                    operand = scan.currentToken;

                    //Get result value of operand. If its an identifier, get it from the storage manager
                    if(operand.subClassif == Token.IDENTIFIER)
                        // if identifier get its result value
                        firstResValue = storageManager.getEntry(operand.tokenStr);
                    else// create a new result value object
                        firstResValue = new ResultValue(operand.tokenStr, operand.subClassif);

                    //Check and see if the next operator is a unary minus.
                    try
                    {
                        poppedOperator = (Token)stack.peek();
                        if(poppedOperator.tokenStr.equals("u-"))
                        {
                            stack.pop();
                            ResultValue minus = new ResultValue("-1",Token.INTEGER);
                            firstResValue = Utilities.mul(this, minus, firstResValue);
                        }
                    }
                    catch (Exception e)
                    {

                    }
                    outPutStack.push(firstResValue);
                    bCategory = true;
                    break;

                //If token is an operator
                case Token.OPERATOR:
                    if(bCategory == false && !scan.currentToken.tokenStr.equals("-"))
                        error("EXPECTED OPERAND AND FOUND " + scan.currentToken.tokenStr);

                    //Check what operator. Look for unary minus
                    switch (scan.currentToken.tokenStr)
                    {
                        //If minus
                        case "-":
                            //Check if the previous token was an operator.
                            //If it was, then we are expecting an operand, or unary minus.
                            if(prevToken.primClassif == Token.OPERATOR) //|| prevToken.primClassif == Token.SEPARATOR)
                            {
                                //Check if next token is an operand or separator. If any is true. The "-" is "u-"
                                if(scan.nextToken.primClassif == Token.OPERAND || scan.nextToken.equals("("))
                                {
                                    /**TODO
                                     * Unary minus logic
                                     */
                                    Token unaryMinus = new Token("u-");
                                    stack.push(unaryMinus);
                                }
                                //If it isn't, then we encountered an error
                                else
                                {
                                    //Throw exception
                                }
                                break;
                            }

                            //Case where operator is not unary minus.
                        default:
                            //If the stack is not empty, check precedence
                            //Check the precedence  of the current token compared to the last pushed to stack
                            while(!stack.empty())
                            {
                                //Check precdence
                                //if precedence of current operator is higher, break.
                                if(getPrecedence(scan.currentToken) < getPrecedence((Token)stack.peek()))
                                    break;

                                //If stack is not empty, and precedence is right evaluate
                                if(!stack.empty())
                                {
                                    //Pop operator
                                    poppedOperator = (Token)stack.pop();
                                    //Pop last value
                                    firstResValue = (ResultValue) outPutStack.pop();
                                    //Pop second value
                                    secondResValue = (ResultValue)outPutStack.pop();

                                    res = evaluate(secondResValue, firstResValue, poppedOperator.tokenStr);
                                    //Push value back to top of output stack
                                    outPutStack.push(res);

                                }
                            }
                            //Push the current token to the operator stack
                            stack.push(scan.currentToken);
                            break;

                    }
                    bCategory = false;
                    break;

                //handle functions
                case Token.FUNCTION:
                    //Cal function to give us result value
                    outPutStack.push(testFunc());
                    //When function return, current token might be next desired operator or terminator.
                    //Set to false so we dont skip over it.
                    moveForward = false;
                    bCategory = true;
                    break;


                //Handle separators
                case Token.SEPARATOR:

                    switch (scan.currentToken.tokenStr)
                    {
                        //found left paren
                        case "(":
                            stack.push(scan.currentToken);
                            break;
                        //found right paren
                        case ")":
                            bFound = false;
                            //Loop through and operatoe until matching left paren is found
                            while(!stack.empty())
                            {
                                popped = (Token)stack.pop();

                                if (popped.tokenStr.equals("(")) // If we found the lparn break
                                {
                                    bFound = true;
                                    //Check if we are in function call
                                    if(inFunc = true)
                                    {
                                        //Check that the next token is function symbol
                                        popped = (Token) stack.peek();
                                        if(popped.tokenStr.equals("#"))
                                        {
                                            //Pop
                                            res = (ResultValue) outPutStack.peek();
                                            scan.getNext();
                                            return res;
                                        }

                                    }
                                    break;
                                }
                                else
                                {
                                    firstResValue = (ResultValue) outPutStack.pop(); // Get two operands for our operator
                                    secondResValue = (ResultValue) outPutStack.pop();
                                    res = evaluate(secondResValue, firstResValue, popped.tokenStr);
                                    outPutStack.push(res); // Push result back onto stack
                                }
                            }
                            break;

                    }
            }
            //Set previous token = to the current toke, so that we end at the terminating string
            prevToken = scan.currentToken;
            //Move to next token unless we returned from function call
            if(moveForward)
                scan.getNext();
            moveForward = true;

        }
        //This should get the last result value
        while(!stack.empty())
        {
            poppedOperator = (Token)stack.pop();

            if(poppedOperator.tokenStr == "(")
            {
                //Throw error
                System.out.println("Parem should not be here");
            }
            if(poppedOperator.tokenStr.equals("u-"))
            {
                ResultValue resvalue = (ResultValue) outPutStack.pop();
                ResultValue minus = new ResultValue("-1",Token.INTEGER);
                outPutStack.push(evaluate(minus, resvalue, "*"));
            }
            else
            {
                ResultValue resvalue = (ResultValue) outPutStack.pop();
                ResultValue res2value = (ResultValue) outPutStack.pop();
                outPutStack.push(evaluate(res2value, resvalue, poppedOperator.tokenStr));
            }
        }
        //Final value
        res = (ResultValue) outPutStack.pop();

        if(scan.bShowExpr)
        {
            System.out.print("\t\t...");
            System.out.println("Result Value: " + res.value);
        }

        scan.setTo(prevToken);
        res.terminatingStr = scan.nextToken.tokenStr;
        //Return final result value
        return res;
    }


    /**
     *
     * This method recieves two operands and an operator and returns the resulting value
     * @param firstResValue first operand
     * @param secondResValue second operand
     * @param operator operator to determine what operation to perform on operands
     * @return Result value of operation
     * @throws Exception
     */
    public ResultValue evaluate(ResultValue firstResValue, ResultValue secondResValue, String operator) throws Exception
    {
        //Result value for return value
        ResultValue res = new ResultValue();


        //Operator string
        switch (operator)
        {
            case "+":
                res = Utilities.add(this, firstResValue, secondResValue);
                break;
            case "-":
                res = Utilities.sub(this, firstResValue, secondResValue);
                break;
            case "*":
                res = Utilities.mul(this, firstResValue, secondResValue);
                break;
            case "/":
                res = Utilities.div(this, firstResValue, secondResValue);
                break;
            case "^":
                res = Utilities.exp(this, firstResValue, secondResValue);
                break;
            case "<":
                res = Utilities.isLessThan(this, firstResValue, secondResValue);
                break;
            case ">":
                res = Utilities.isGreaterThan(this, firstResValue, secondResValue);
                break;
            case "<=":
                res = Utilities.isLessThanorEq(this, firstResValue, secondResValue);
                break;
            case ">=":
                res = Utilities.isGreaterThanorEq(this, firstResValue, secondResValue);
                break;
            case "==":
                res = Utilities.isEqual(this, firstResValue, secondResValue);
                break;
            case "!=":
                res = Utilities.notEqualTo(this, firstResValue, secondResValue);
                break;
            case "u-":
                //es = Utilities.mul(this, firstResValue, )
            default:
                error("ERROR: '%s' IS NOT A VALID OPERATOR FOR EXPRESSION", operator);
        }

        return res;
    }



    /**
     * This method will evaluate an expression and return a ResultValue object
     * that contains the final result.
     * <p>
     * Right now, this only handles single operation expressions
     *
     * @return ResultValue object that contains the final result of execution
     * @throws Exception generic Exception type to handle any processing errors
     */
    public ResultValue expr() throws Exception
    {
        //Result value for operands
        ResultValue firstResValue, secondResValue;

        //Result value for result
        ResultValue res = new ResultValue();

        // values for first and second operand
        int firstOperandSubClassif;
        String firstValue = "";
        int secondOperandSubClassif;
        String secondValue = "";

        // helper variables for operation
        Boolean isNegative = false;
        String operator;

        // advance token to the operand
        scan.getNext();

        // check if first operand is negative
        if (scan.currentToken.tokenStr.equals("-"))
        {
            isNegative = true;
            scan.getNext();
        }

        /* pablo you could probably use the same expression code but change it a little
         * to do complex expressions. I'd try to implement a while loop that goes until
         * base case "; , )" are encountered. Same way you determine if we have a single
         * value. Just seems like a good suggestion in order to not change your code that
         * much.
         */
        // save first operand attributes
        firstOperandSubClassif = scan.currentToken.subClassif;
        firstValue = scan.currentToken.tokenStr;

        // check if operand is an identifier or a constant
        if(firstOperandSubClassif == Token.IDENTIFIER)
            // if identifier get its result value
            firstResValue = storageManager.getEntry(firstValue);
        else// create a new result value object
            firstResValue = new ResultValue(scan.currentToken.tokenStr, scan.currentToken.subClassif);

        // check if first operand is supposed to be negative
        if(isNegative)
        {// supposed to be negative so change the sign
            firstResValue.value = Utilities.toNegative(this, firstResValue);
            isNegative = false;
        }

        // determine operator
        operator = scan.nextToken.tokenStr;

        if(operator.equals(";") || operator.equals(",") || operator.equals(")") || operator.equals("to")
                || operator.equals(":") || operator.equals("by"))
        { // assign is done, so return the single value
            // check for debug on
            if(scan.bShowExpr)
            {
                System.out.print("\t\t...");
                System.out.println("Result Value: " + res.value);
            }

            return new ResultValue(firstResValue.value, firstResValue.type,1, operator);
        }

        // advance once to the operator and then once more to the token after operator
        scan.getNext();
        scan.getNext();

        // check if second operator is negative
        if (scan.currentToken.tokenStr.equals("-"))
        {
            isNegative = true;
            scan.getNext();
        }

        // save second operand attributes
        secondOperandSubClassif = scan.currentToken.subClassif;
        secondValue = scan.currentToken.tokenStr;

        // check if second operand is identifier or constant
        if(secondOperandSubClassif == Token.IDENTIFIER)
            // if identifier get its result value
            secondResValue = storageManager.getEntry(secondValue);
        else// create a new result value object
            secondResValue = new ResultValue(scan.currentToken.tokenStr, scan.currentToken.subClassif);

        // check if second operand is supposed to be negative
        if(isNegative)
        {// if there was a unary minus, apply to the second operand
            secondResValue.value = Utilities.toNegative(this, secondResValue);
            isNegative = false;
        }

        // determine operation to perform
        switch (operator)
        {
            case "+":
                res = Utilities.add(this, firstResValue, secondResValue);
                break;
            case "-":
                res = Utilities.sub(this, firstResValue, secondResValue);
                break;
            case "*":
                res = Utilities.mul(this, firstResValue, secondResValue);
                break;
            case "/":
                res = Utilities.div(this, firstResValue, secondResValue);
                break;
            case "^":
                res = Utilities.exp(this, firstResValue, secondResValue);
                break;
            case "<":
                res = Utilities.isLessThan(this, firstResValue, secondResValue);
                break;
            case ">":
                res = Utilities.isGreaterThan(this, firstResValue, secondResValue);
                break;
            case "<=":
                res = Utilities.isLessThanorEq(this, firstResValue, secondResValue);
                break;
            case ">=":
                res = Utilities.isGreaterThanorEq(this, firstResValue, secondResValue);
                break;
            case "==":
                res = Utilities.isEqual(this, firstResValue, secondResValue);
                break;
            case "!=":
                res = Utilities.notEqualTo(this, firstResValue, secondResValue);
                break;
            default:
                error("ERROR: '%s' IS NOT A VALID OPERATOR FOR EXPRESSION", operator);
        }

        // create final result value of expression to return
        res = new ResultValue(res.value, res.type,1, scan.nextToken.tokenStr);

        // check for debug on
        if(scan.bShowExpr)
        {
            System.out.print("\t\t...");
            System.out.println("Result Value: " + res.value);
        }

        return res;
    }

    /**
     * This method is provided in order to skip to a specified position
     * in the source file.
     * <p>
     * Handles EOF exception
     *
     * @param start start string we are starting from, included more for error purposes
     * @param end string literal we are looking for to terminate looping
     * @throws Exception generic Exception type to handle any processing errors
     */
    public void skipTo(String start, String end) throws Exception
    {
        // for error purposes
        int iColPos = scan.currentToken.iColPos;
        int iSourceLineNr = scan.currentToken.iSourceLineNr;

        // loop until we encounter EOF or the string literal we are looking for
        while (! scan.currentToken.tokenStr.equals(end)
                && scan.currentToken.primClassif != Token.EOF)
            scan.getNext();

        if (scan.currentToken.primClassif == Token.EOF)
            // the while loop exited on an EOF, not a matching token
            error("ERROR: LINE %d POSITION %d" + "\n\t%s DID NOT HAVE SEPARATOR, %s"
                    , iSourceLineNr, iColPos, start, end);

        // we found a match, so we will return with no error
        return;
    }

    /**
     * This method is provided to Parser::ifStmt, whileStmt, and forStmt.
     * <p>
     * It goes through
     * all lines between the start of the statement and the end of the statement specified
     * by the terminatingStr parameter.
     *
     * @param bExec Tells the statement function whether we need to execute the code we find or
     *              just look at it
     * @param terminatingStr Tells us when we are done executing lines for the statement
     * @return ResultValue object that contains the final result of execution
     * @throws Exception generic Exception type to handle any processing errors
     */
    public ResultValue statements(Boolean bExec, String terminatingStr) throws Exception
    {
        ResultValue result = statement(bExec);

        // loop until we find our terminating string
        while (! terminatingStr.contains(result.terminatingStr))
            result = statement(bExec);

        result.terminatingStr = scan.currentToken.tokenStr;
        return result;
    }

    /**
     * This method executes 'if' statements for HavaBol. It uses bExec and the
     * ResultValue object returned from calling expression in order to determine
     * if we are executing.
     * <p>
     * If we are not executing, we will still look over the lines to catch errors
     *
     * @param bExec Tells the statement function whether we need to execute the code we find or
     *              just look at it
     * @return ResultValue object that contains the final result of execution
     * @throws Exception generic Exception type to handle any processing errors
     */
    public ResultValue ifStmt(Boolean bExec) throws Exception
    {
        ResultValue resCond;

        // do we need to evaluate the condition
        if (bExec)
        {// we are executing, not ignoring
            // evaluate expression
            resCond = expression();

            // did the condition return true?
            if (resCond.value.equals("T"))
            {// condition returned true, execute statements on the true part
                resCond = statements(true, "endif else");

                // what ended the statements after the true part? else of endif
                if (resCond.terminatingStr.equals("else"))
                {// has an else
                    if (! scan.getNext().equals(":"))
                        error("ERROR: EXPECTED ':' AFTER ELSE");

                    resCond = statements(false, "endif");
                }
            }
            else if (resCond.value.equals("F"))
            {// condition returned false, ignore all statements after the if
                resCond = statements(false, "endif else");

                // check for else
                if (resCond.terminatingStr.equals("else"))
                { // if it is an 'else', execute
                    if (! scan.getNext().equals(":"))
                        error("ERROR: EXPECTED ':' AFTER ELSE");

                    resCond = statements(true, "endif");
                }
            }
            else
             // resCond value was not a boolean so it is an error
                error("ERROR: EXPECTED BOOLEAN FOR IF STATEMENT " +
                                    "BUT FOUND %s", scan.currentToken.tokenStr);
        }
        else
        {// we are ignoring execution, so ignore conditional, true and false part
            // ignore conditional
            skipTo("if", ":");

            // ignore true part
            resCond = statements(false, "endif else");

            // if the statements terminated with an 'else', we need to parse statements
            if (resCond.terminatingStr.equals("else"))
            { // it is an else, so we need to skip statements
                if (! scan.getNext().equals(":"))
                    error("ERROR: EXPECTED ':' AFTER ELSE");

                // ignore false part
                resCond = statements(false, "endif");
            }
        }

        // did we have an 'endif;'?
        if (!resCond.terminatingStr.equals("endif") || !scan.nextToken.tokenStr.equals(";"))
            error("ERROR: EXPECTED 'endif;' FOR 'if' EXPRESSION");

        return new ResultValue("", Token.END, ResultValue.primitive, ";");
    }

    /**
     * This method executes 'while' statements for HavaBol. It uses bExec and the
     * ResultValue object returned from calling expression in order to determine
     * if we keep executing.
     * <p>
     * If we are not executing, we will still look over the lines to catch errors
     *
     * @param bExec Tells the statement function whether we need to execute the code we find or
     *              just look at it
     * @return ResultValue object that contains the final result of execution
     * @throws Exception generic Exception type to handle any processing errors
     */
    public ResultValue whileStmt(Boolean bExec) throws Exception
    {
        ResultValue resCond;

        // do we need to evaluate the condition
        if (bExec)
        {// we are executing, not ignoring
            Token whileToken = scan.currentToken;

            // evaluate expression
            resCond = expression();

            while (resCond.value.equals("T"))
            {// did the condition return true?
                resCond = statements(true, "endwhile");

                // did statements() end on an endwhile;?
                if (! resCond.terminatingStr.equals("endwhile") || !scan.nextToken.tokenStr.equals(";"))
                    error("ERROR: EXPECTED 'endwhile;' FOR 'while' EXPRESSION");

                // reset while loop token
                scan.setTo(whileToken);

                // check expression case
                resCond = expression();
            }

            // expr() returned false, so skip ahead to the end of the while
            resCond = statements(false, "endwhile");
        }
        else
        {// we are ignoring execution, so ignore conditional, true and false part
            // ignore conditional
            skipTo("while", ":");

            // ignore statements
            resCond = statements(false, "endwhile");
        }

        // did we have an endwhile;
        if (! resCond.terminatingStr.equals("endwhile") || !scan.nextToken.tokenStr.equals(";"))
            error("ERROR: EXPECTED 'endwhile;' FOR 'while' EXPRESSION");

        return new ResultValue("", Token.END, ResultValue.primitive, ";");
    }

    /**
     *
     * @param bExec
     * @return
     * @throws Exception
     */
    public ResultValue forStmt(Boolean bExec) throws Exception
    {
        ResultValue resCond;

        // do we need to evaluate the condition?
        if (bExec)
        {// we are executing, not ignoring
            Token forToken = scan.currentToken;

            // advance to the start of the control variable
            scan.getNext();

            // make sure that we have a control variable
            if (scan.currentToken.subClassif != Token.IDENTIFIER)
                error("ERROR: EXPECTED CONTROL VARIABLE BUT FOUND %s", scan.currentToken.tokenStr);

            // check look ahead token for the type of for loop
            switch (scan.nextToken.tokenStr)
            {// =, in, from, or error
                // counting for
                case "=":
                    // declare for loop variables
                    int cv, ev, iv;
                    String cvStr = scan.currentToken.tokenStr;

                    // create int control variable
                    cv = Integer.parseInt(assignStmt(true).value);

                    // make sure we have the required end variable for our counting for loop
                    if ( !scan.getNext().equals("to") )
                        error("ERROR: EXPECTED END VARIABLE BUT FOUND %s", scan.currentToken.tokenStr);

                    // create end variable
                    ev = Integer.parseInt(expr().value);

                    // check if we have an increment variable, default to 1
                    if ( scan.getNext().equals("by"))
                    {
                        iv = Integer.parseInt(expr().value);

                        // advance token to the expected ':'
                        scan.getNext();
                    }
                    else
                        iv = 1;

                    // make sure we end on an ':'
                    if ( !scan.currentToken.tokenStr.equals(":"))
                        error("ERROR: EXPECTED ':' AFTER FOR LOOP VARIABLES");

                    // execute counting for
                    for (int i = cv; i < ev; i += iv)
                    {
                        resCond = statements(true, "endfor");

                        // did statements() end on an endfor;?
                        if (!resCond.terminatingStr.equals("endfor") || !scan.nextToken.tokenStr.equals(";"))
                            error("ERROR: EXPECTED 'endfor;' FOR 'for' EXPRESSION");

                        // update cv in storage manager
                        resCond = storageManager.getEntry(cvStr);
                        resCond.value = "" + (Integer.parseInt(resCond.value) + iv);
                        storageManager.putEntry(cvStr, resCond);

                        // set position back to the beginning of the for loop
                        scan.setTo(forToken);
                        skipTo("for", ":");
                    }
                    break;
                // for fuck in berto
                case "in":
                    // declare for loop variables
                    String item = scan.currentToken.tokenStr;
                    String object;

                    if (storageManager.getEntry(item) != null)
                     // make sure item has not been already defined
                        error("ERROR: VARIABLE '%s' IS ALREADY DEFINED IN THE SCOPE", item);

                    // advance to 'in' token
                    scan.getNext();

                    // make sure next token is an operand
                    if (scan.nextToken.primClassif != Token.OPERAND)
                        error("ERROR: EXPECTED VARIABLE BUT FOUND %s", scan.nextToken.tokenStr);

                    // evaluate iterable expression
                    resCond = expr();

                    // make sure we end on an ':'
                    if ( !scan.getNext().equals(":"))
                        error("ERROR: EXPECTED ':' AFTER FOR LOOP VARIABLES");

                    // make sure we have an appropriate iterable object (array or string)
                    if ( resCond.structure == ResultValue.fixedArray
                       ||resCond.structure == ResultValue.unboundedArray)
                    {// we are iterating through an array

                    }
                    else
                    {// we are iterating over a string or expression
                        object = resCond.value;

                        // add item to storage manager as a string
                        storageManager.putEntry(item, new ResultValue("", Token.STRING
                                , ResultValue.primitive, "in"));

                        for (char c : object.toCharArray())
                        {
                            // update cv in storage manager
                            resCond = storageManager.getEntry(item);
                            resCond.value = "" + c;
                            storageManager.putEntry(item, resCond);
                            resCond = statements(true, "endfor");

                            // did statements() end on an endfor;?
                            if( !resCond.terminatingStr.equals("endfor") ||
                                    !scan.nextToken.tokenStr.equals(";"))
                                error("ERROR: EXPECTED 'endfor;' FOR 'for' EXPRESSION");

                            // set position back to the beginning of the for loop
                            scan.setTo(forToken);
                            skipTo("for", ":");
                        }
                    }
                    break;
                // for by delimiter
                case "from":
                    // declare for loop variables
                    String stringCV = scan.currentToken.tokenStr;
                    String string, delimiter;
                    String stringM[];

                    if (storageManager.getEntry(stringCV) != null)
                        // make sure string cv has not been already defined
                        error("ERROR: VARIABLE '%s' IS ALREADY DEFINED IN THE SCOPE", stringCV);

                    // advance to 'from' token
                    scan.getNext();

                    // make sure next token is an operand
                    if (scan.nextToken.primClassif != Token.OPERAND)
                        error("ERROR: EXPECTED VARIABLE BUT FOUND %s", scan.nextToken.tokenStr);

                    // save string value to iter on
                    string = expr().value;

                    if ( !scan.getNext().equals("by") )
                     // make sure we have our delimiter
                        error("ERROR: MISSING 'BY' SEPARATOR FOR DELIMITER");

                    // save delimiter
                    delimiter = expr().value;

                    if ( !scan.getNext().equals(":") )
                        // make sure we have our ending ':'
                        error("ERROR: MISSING ':' SEPARATOR AT END OF FOR LOOP DECLARATION");

                    // split string into an array with our delimiter
                    stringM = string.split(Pattern.quote(delimiter));

                    // add string control variable to storage manager as a string
                    storageManager.putEntry(stringCV, new ResultValue("", Token.STRING
                            , ResultValue.primitive, "from"));

                    // iterate through our split string
                    for (String s : stringM)
                    {
                        // update string cv in storage manager
                        resCond = storageManager.getEntry(stringCV);
                        resCond.value = "" + s;
                        storageManager.putEntry(stringCV, resCond);
                        resCond = statements(true, "endfor");

                        // did statements() end on an endfor;?
                        if( !resCond.terminatingStr.equals("endfor") ||
                                !scan.nextToken.tokenStr.equals(";"))
                            error("ERROR: EXPECTED 'endfor;' FOR 'for' EXPRESSION");

                        // set position back to the beginning of the for loop
                        scan.setTo(forToken);
                        skipTo("for", ":");
                    }

                    break;
                // unrecognized for separator
                default:
                    error("ERROR: UNRECOGNIZED CONTROL SEPARATOR '%s'", scan.nextToken.tokenStr);
            }
        }
        else
         // we are ignoring execution, so control variables
            // ignore control variables
            skipTo("for", ":");

        // ignore statements
        resCond = statements(false, "endfor");

        // did we have an endfor;
        if (! resCond.terminatingStr.equals("endfor") || !scan.nextToken.tokenStr.equals(";"))
            error("ERROR: EXPECTED 'endfor;' FOR 'while' EXPRESSION");

        return new ResultValue("", Token.END, ResultValue.primitive, "endfor");
    }

    /**
     * This method is provided to Parser to execute HavaBol builtin and user
     * defined functions.
     * <p>
     * function uses bExec in order to determine if we are executing or skipping.
     *
     * @param bExec Tells the statement function whether we need to execute the code we find or
     *              just look at it
     * @return ResultValue object that contains the final result of execution
     * @throws Exception generic Exception type to handle any processing errors
     */
    private ResultValue function(Boolean bExec) throws Exception
    {
        int type = Token.BUILTIN;
        ResultValue res;
        String value = "";

        switch (scan.currentToken.subClassif)
        {// determine if function is built in or user defined
            case Token.BUILTIN:
                // make sure the function is in correct syntax
                if (! scan.nextToken.tokenStr.equals("(") )
                    error("ERROR: '%s' FUNCTION IS MISSING SEPARATOR '('", scan.currentToken.tokenStr);

                // determine function
                if (scan.currentToken.tokenStr.equals("print"))
                {// print function
                    String printLine = "";
                    Token previousToken = scan.currentToken;

                    while ( !scan.getNext().equals(")") )
                    {// loop until we find a ')'
                        if(scan.currentToken.subClassif <= Token.STRING && scan.currentToken.subClassif > 0)
                        {
                            scan.setTo(previousToken);
                            printLine += expr().value;
                        }
                        else if (scan.currentToken.tokenStr.equals(","))
                            // ',' automatically add a space to our line
                            printLine += " ";
                        else if (scan.currentToken.primClassif == Token.OPERATOR)
                        {// operator encountered, evaluate and add to print string
                            scan.setTo(previousToken);
                            printLine += expr().value;
                        }
                        /*else if (scan.currentToken.tokenStr.equals(";"))
                            // should not be encountered unless a ')' is missing
                            error("ERROR: EXPECTED ')' BEFORE ';' TOKEN %s"
                                    , scan.currentToken.tokenStr);*/

                        previousToken = scan.currentToken;
                    }

                    // check if we are executing
                    if (bExec)
                        System.out.println(printLine);
                }
                else if (scan.currentToken.tokenStr.equals("LENGTH"))
                {// length function
                    // advance to the left parenthesis
                    scan.getNext();

                    // get value of parameter
                    res = expression();

                    // make sure we only have one parameter
                    if (!scan.currentToken.tokenStr.equals(")"))
                        error("ERROR: EXPECTED ONLY ONE PARAMETER FOR LENGTH FUNCTION");

                    // calculate length of given string
                    value = "" + res.value.length();

                    // set type to an int
                    type = Token.INTEGER;
                }
                else if (scan.currentToken.tokenStr.equals("SPACES"))
                {
                    //advance to our paramater
                    scan.getNext();
                    scan.getNext();

                    //get value of parameter
                    res = expr();

                    // make sure we only have one parameter
                    if (!scan.currentToken.tokenStr.equals(")"))
                        error("ERROR: EXPECTED ONLY ONE PARAMETER FOR SPACES FUNCTION");

                    // determine if string contains spaces
                    if (res.value.trim().length() == 0)
                        value = "T";
                    else
                        value = "F";

                    // set type to a boolean
                    type = Token.BOOLEAN;
                }
                break;
            case Token.USER:
                // do other shit later
                skipTo(scan.currentToken.tokenStr, ";");
                break;
            default:// should never hit this, otherwise MAJOR FUCK UP
                error("INTERNAL ERROR: %s NOT A RECOGNIZED FUNCTION"
                           , scan.currentToken.tokenStr);
        }

        // make sure we end on a ';'
        if ( !scan.getNext().equals(";") )
            error("ERROR: PRINT FUNCTION IS MISSING TERMINATOR ';'");

        return new ResultValue(value, type, ResultValue.primitive, scan.currentToken.tokenStr);
    }

    public ResultValue testFunc() throws Exception
    {
        //System.out.println("Entering testFunc with current token as " + scan.currentToken.tokenStr);
        int type = Token.BUILTIN;
        ResultValue res;
        String value = "";

        switch (scan.currentToken.subClassif)
        {// determine if function is built in or user defined
            case Token.BUILTIN:
                // make sure the function is in correct syntax
                if (! scan.nextToken.tokenStr.equals("(") )
                    error("ERROR: '%s' FUNCTION IS MISSING SEPARATOR '('", scan.currentToken.tokenStr);

                // determine function
                if (scan.currentToken.tokenStr.equals("LENGTH"))
                {// length function
                    // advance to our parameter
                    //scan.getNext();
                    //scan.getNext();
                    // get value of parameter
                    res = expression();

                    // make sure we only have one parameter
                    //                    if (!scan.currentToken.tokenStr.equals(")"))
                    //                        error("ERROR: EXPECTED ONLY ONE PARAMETER FOR LENGTH FUNCTION");

                    // calculate length of given string
                    value = "" + res.value.length();

                    // set type to an int
                    type = Token.INTEGER;
                }

                break;
            case Token.USER:
                // do other shit later
                skipTo(scan.currentToken.tokenStr, ";");
                break;
            default:// should never hit this, otherwise MAJOR FUCK UP
                error("INTERNAL ERROR: %s NOT A RECOGNIZED FUNCTION"
                        , scan.currentToken.tokenStr);
        }

        // make sure we end on a ';'
        // if ( !scan.currentToken.tokenStr.equals(";") )
        //    error("ERROR: PRINT FUNCTION IS MISSING TERMINATOR ';'");

        return new ResultValue(value, type, ResultValue.primitive, scan.currentToken.tokenStr);
    }


    /**
     * This method is provided to the parser to simplify all processing errors encountered.
     * <p>
     * This method will call ParserException class in order to throw an error with a formatted
     * message
     *
     * @param fmt string that contains the method and variable formats
     * @param varArgs arguments to put in the format string
     * @throws Exception generic Exception type to handle any processing errors
     */
    public void error (String fmt, Object... varArgs) throws Exception
    {
        throw new ParserException(scan.currentToken.iSourceLineNr+1
                                , String.format(fmt, varArgs)
                                , scan.sourceFileNm);
    }




    /**
     * This method recieves an operator as a token and checks it precedence.
     * Operators with higher precedence have lower int value. As precedence decreases, int value goes up.
     * @param operator
     * @return int value of precedence.
     */
    public int getPrecedence(Token operator)
    {   int precedence;
        switch(operator.tokenStr)
        {
            case "u-":
                precedence = 0;
                break;
            case "^":
                precedence = 1;
                break;
            case "*":
                precedence = 2;
                break;
            case "/":
                precedence = 2;
                break;
            case "+":
                precedence = 3;
                break;
            case "-":
                precedence = 3;
                break;
            case "#":
                precedence = 4;
                break;
            default:
                precedence = 5;
        }

        return precedence;
    }

}

