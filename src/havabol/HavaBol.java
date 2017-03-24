/*
  This is a simple driver for the first programming assignment.
  Command Arguments:
      java HavaBol arg1
             arg1 is the havabol source file name.
  Output:
      Prints each token in a table.
  Notes:
      1. This creates a SymbolTable object which doesn't do anything
         for this first programming assignment.
      2. This uses the student's Scanner class to get each token from
         the input file.  It uses the getNext method until it returns
         an empty string.
      3. If the Scanner raises an exception, this driver prints
         information about the exception and terminates.
      4. The token is printed using the Token::printToken() method.
 */
package havabol;

import havabol.SymbolTable.SymbolTable;

public class HavaBol
{
    public static void main(String[] args)
    {
        // Create the SymbolTable
        SymbolTable symbolTable = new SymbolTable();
        StorageManager storageManager = new StorageManager();


        try
        {
            // create scanner and parser objects
            Scanner scan = new Scanner(args[0], symbolTable);
            Parser parser = new Parser(symbolTable, storageManager, scan);

            // start parsing file
            while (scan.currentToken.primClassif != Token.EOF)
            {
                //System.out.println("PARSER CALL WITH CURRENT TOKEN:");
                //scan.currentToken.printToken();
                parser.statement(true);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}