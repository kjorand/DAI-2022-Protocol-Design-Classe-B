package ch.heigvd.api.calc;

import java.io.*;
import java.net.Socket;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Calculator worker implementation
 */
public class ServerWorker implements Runnable {
    private Socket clientSocket;
    private BufferedReader in = null;
    private PrintWriter out = null;

    private Stack<Double> stack = null;

    private class Error extends RuntimeException {
        int c;
        String d;
        public Error(int code, String descr) throws IOException {
            c = code;
            d = (descr == "" ? null : descr);
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, "Server Error! : "+getErr(), this);
            stack.clear();
            // Would be good to flush the input ...
        }

        private String errCode(){
            if(c == 0) {
                return "divideByZero";
            }else if(c >= 10 && c < 100) {
                return "timeout";
            }else if(c >= 200 && c < 210) {
                return "wrongMessageTypeSyntax"; // can be sent in any case the message type could not be identified
            }else if(c >= 210 && c < 220) {
                return "unrecognizedOperator"; // can be sent in any case a character is unrecognized
            }else if(c >= 220 && c < 230) {
                return "tooFewArguments"; // too few values are left on the stack
            }else if(c >= 230 && c < 300) {
                return "wrongSyntax"; // generic error in case of unrecognized syntax
            }else{
                return "Unknown error";
            }
        }

        private String getErr(){
            return String.format("ERROR\n%03d\n%s\n%s\n", c, errCode(), (d==null ? "" : d)); // "ERROR\n" + c + "\n" + errCode() + "\n" + (d==null ? "" : d + "\n");
        }
        public void send(){
            out.print(getErr()+"\n");
            out.flush();
        }
    }

    private void requireStack(int number) throws IOException {
        if(stack.size() < number){
            throw new Error(220, null);
        }
    }

    private void stackOp2(BiFunction<Double, Double, Double> op) throws IOException{
        requireStack(2);
        Double arg0 = stack.pop();
        stack.push(op.apply(arg0, stack.pop()));
    }

    private boolean parseOpLine(String line) throws IOException{
        try{
            stack.push(Double.parseDouble(line));
            return false;
        }catch(NumberFormatException ex){
            switch (line) {
                case "+":
                    stackOp2((a0, a1)->a0+a1);
                    break;
                case "-":
                    stackOp2((a0, a1)->a0-a1);
                    break;
                case "*":
                    stackOp2((a0, a1)->a0*a1);
                    break;
                case "/":
                    if(stack.peek() == 0)
                        throw new Error(000, null);
                    stackOp2((a0, a1)->a1/a0);
                    break;
                case "%":
                    stackOp2((a0, a1)->a0%a1);
                    break;
                case "^":
                    stackOp2(Math::pow);
                    break;
                case "rst":
                    stack.clear();
                    break;
                default:
                    throw  new Error(210, "Problem with : '" + line + "'");
            }
            return true;
        }
    }

    private  void printResult(){
        if(stack.size() > 1) { // "Final" result
            out.print("PARTIAL_");
        }
        out.print("RESULT\n" + stack.peek() + "\n\n");
        out.flush();
    }

    private final static Logger LOG = Logger.getLogger(ServerWorker.class.getName());

    /**
     * Instantiation of a new worker mapped to a socket
     *
     * @param clientSocket connected to worker
     */
    public ServerWorker(Socket clientSocket) throws IOException {
        // Log output on a single line
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%6$s%n");
        /* DONE: prepare everything for the ServerWorker to run when the
         *   server calls the ServerWorker.run method.
         *   Don't call the ServerWorker.run method here. It has to be called from the Server.
         */

        this.clientSocket = clientSocket;
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream());
            stack = new Stack<Double>();
        }catch(java.io.IOException exception){
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, exception);
            throw exception;
        }
    }

    /**
     * Run method of the thread.
     */
    @Override
    public void run() {
        /* TODO: implement the handling of a client connection according to the specification.
         *   The server has to do the following:
         *   - initialize the dialog according to the specification (for example send the list
         *     of possible commands)
         *   - In a loop:
         *     - Read a message from the input stream (using BufferedReader.readLine)
         *     - Handle the message
         *     - Send the result to the client
         */

        boolean quit = false;
        String line = "";
        out.print("VERSION\n1.0.0\n\n");
        out.flush();
        try {
            try{
                LOG.info("Reading until client sends QUIT or closes the connection...");
                while (!clientSocket.isClosed() && !quit && (line = in.readLine()) != null) {
                    switch(line){
                        case "NUMERIC_OPERATION" :
                        case "OP" : // Alias
                            boolean sendResult = false;
                            while ((line = in.readLine()) != null && line != "" && !(line.equalsIgnoreCase("")) ){
                                sendResult = parseOpLine(line);
                            }
                            if(sendResult){
                                printResult();
                            }
                            break;
                        case "ACTIVE" : // Unused since no ACTIVITY_PROBE is sent by this iter
                            break;
                        case "QUIT" :
                            quit = true;
                            break;
                        default:
                            throw new Error(200, null);
                    }
                }
            } catch (Error err) {
                LOG.info("Sending Error ... Cleaning up resources...");
                err.send();
                clientSocket.close();
                in.close();
                out.close();
            }

            LOG.info("Cleaning up resources...");
            clientSocket.close();
            in.close();
            out.close();

        } catch (IOException ex) {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ex1) {
                    LOG.log(Level.SEVERE, ex1.getMessage(), ex1);
                }
            }
            if (out != null) {
                out.close();
            }
            if (clientSocket != null) {
                try {
                    clientSocket.close();
                } catch (IOException ex1) {
                    LOG.log(Level.SEVERE, ex1.getMessage(), ex1);
                }
            }
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
        }

    }
}