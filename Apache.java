/*(
 * CS 656 / Fall 20 / Apache / V2.00
 * Group: N1 / Leader: Jung Hyun Kim (jk599)
 * Group Members: Abdelrhman Moustafa (ahm26), Gagandeep Sandhu (gss28), Yi-Hsuan Hsu (yh454)
 *
 *   ADC - add your code here
 *   NOC - do not change this
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
// other imports go here
import java.io.IOException;

/*--------------- end imports --------------*/

public class Apache {


    // NOC these 3 fields
    private byte []     HOST ;      /* should be initialized to 1024 bytes in the constructor */
    private int         PORT ;      /* port this Apache should listen on, from cmdline        */
    private InetAddress PREFERRED ; /* must set this in dns() */
    // ADC additional fields here
    private byte []     FILE ;      /* name of the file in URL, if you like */
    private ServerSocket serverSocket;
    private int totalRequests = 0;
    private static final byte[] REQ = new byte[] {'R', 'E', 'Q', ':', ' '};
    private static final byte[] IP = new byte[] {'I', 'P', ':', ' '};
    private static final byte[] ERROR = new byte[] {'E', 'R', 'R', 'O', 'R', ' '};
    private static final byte[] PARSE_ERROR = new byte[] {'P', 'A', 'R', 'S', 'E'};
    private static final byte[] DNS_ERROR = new byte[] {'D', 'N', 'S'};
    private static final byte[] GET = new byte[] {'G', 'E', 'T'};
    private static final byte[] httpVersion = new byte[] {'H','T','T','P','/','1','.','1'};
    private static final byte[] httpProtocol = new byte[] {'h','t','t','p',':','/','/'};
    private static final byte[] ftpProtocol = new byte[] {'f','t','p',':','/','/'};
    private static final byte[] CRLF = new byte[] {'\r', '\n'};
    private byte[] userReq;
    private byte[] userReqFull;
    private byte[] userReqToSend;
    private boolean isRequest = false;
    private boolean isHttp = false;
    private boolean isFtp = false;
    private boolean hasUserAgent = false;
    private boolean multipleHeader = false;
    private long startTime;
    private static final int maxSize = 20971520;

    public static void main(String [] a) { // NOC - do not change main()
        Apache Apache = new Apache(Integer.parseInt(a[0]));
        Apache.run(2);
    }

    Apache(int port) {
        PORT = port;
        // other init stuff ADC here
        try {
            this.serverSocket = new ServerSocket();
            this.HOST = new byte[1024];
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        System.out.println("Apache listening on socket " + this.PORT);
    }

    // Note: parse() must set HOST correctly
    int parse(byte [] buffer) throws Exception // >=0 for success, else for failure
    {
        // [GET http://www.example.com/  HTTP/1.1\r\nHost: example.com\r\n\r\n]
        this.HOST = new byte[1024];

        // subtract 10 for "GET ftp://", subtract 17 for " HTTP/1.1\r\nHost: ", and subtract 2 for "\r\n\r\n"
        this.FILE = new byte[65535-10-17-2];
        boolean hasFile = false;


        try {
            // split buffer into lines split by CRLF
            // find the location of each CRLF
            byte[][] lines = new byte[][]{};
            byte[] line = new byte[]{};

            for (int i = 0; i < buffer.length-1 && (buffer[i] != 0 || buffer[i+1] != 0); i++) {
                if (buffer[i] == '\r' && buffer[i+1] == '\n') {
                    lines = appendToByteArrArr(lines, line);
                    line = new byte[]{};
                    i++;
                } else {
                    line = appendToByteArr(line, buffer[i]);
                }
            }

            // lines contains the input buffer split by CRLF, now can check for specific lines
            //     check for proper GET line
            //     GET is always the 0th line
            byte[] get = lines[0];
            this.isHttp = containsSubArray(get, this.httpProtocol, 4);
            this.isFtp = containsSubArray(get, this.ftpProtocol, 4);
            boolean containsHTTP = containsSubArray(get, this.httpVersion, 0);
            boolean hasHost = false;

            if (!containsSubArray(get, charToByte(new char[] {'G', 'E', 'T', ' '}), 0)) {
                throw new Exception("no GET");
            }

            // check for "Host: " line
            int hostLine = -1;
            for (int i = 0; i < lines.length; i++) {
                if (containsSubArray(lines[i], charToByte(new char[] {'H', 'o', 's', 't', ':', ' '}), 0)) {
                    hostLine = i;
                    hasHost = true;
                    break;
                }
            }
            // get HOST
            //     if hostLine = -1, means it's a part 1 dns get request, host is in the GET line
            if (hostLine == -1) {
                int start = 4;
                byte lastChar = get[start];
                for (int i = start; (isHttp || isFtp) && i < get.length; i++) {
                    if (lastChar == '/' && get[i] == '/') {
                        start = i+1;
                        break;
                    }
                    lastChar = get[i];
                }
                       
                if (isHttp){
                    //System.out.printf("Length of GET line for HTTP is %d\n", get.length);
                    for (int i = start, j = 0; i < get.length && get[i] != ' ' && get[i] != '/'; i++, j++) {
                        this.HOST[j] = get[i];
                    }
                }
                else if (isFtp){
                    //System.out.printf("Length of GET line for FTP is %d\n", get.length);
                    for (int i = start, j = 0; i < get.length && get[i] != ' ' && get[i] != '/'; ++i, ++j){
                        this.HOST[j] = get[i];
                    }
                }
            } else {
                // host is in the host line
                // "Host: "
                this.HOST = subarray(lines[hostLine], 6, lines[hostLine].length);
                if (isFtp){
                    this.HOST = subarrayFtp(this.HOST);
                }
            }

            // determine file path from the GET line
            int start = 4;
            if (isFtp) {
                start += 10;
            } else if (isHttp) {
                start += 7;
            }
            if (hasHost && get[start] == this.HOST[start]) {
                start += this.HOST.length;
            }
            
/*
            //DEBUG
            System.out.printf("Starting index for parse is %d\n", start);
            if (start != -1){
                System.out.printf("Character at [starting index] is %c\n", get[start]);
            }
            for (int i=start; i<get.length ; ++i){
                System.out.printf("%c", get[i]);
            }
            System.out.println();
*/

            // If http:// is present in GET line, consume until first slash or space
            if ((isHttp || isFtp) && containsHTTP){
                boolean hasSlash = false;
                int slashIndex = -1;
                for (int i=start ; i < get.length && get[i] != ' ' ; ++i){
                    // If there is a slash, save its index, and break
                    if (get[i] == '/'){
                        hasSlash = true;
                        slashIndex = i;
                        break;
                    }
                    // If no slash, then set file path to '/'
                    else if (!hasSlash && get[i+1] == ' '){
                        //System.out.printf("There is no slash\n");
                        this.FILE = new byte[] {'/'};
                        break;
                    }
                }

/*
                //DEBUG
                System.out.printf("Value of slash index is %d\n", slashIndex);
                if (slashIndex != -1){
                    System.out.printf("Character at [slash index] is %c\n", get[slashIndex]);
                }
*/

                // If there is a slash, check if it's followed by a space or non-space char
                if (hasSlash && get[slashIndex + 1] == ' ') {
                    this.FILE = new byte[] {'/'};
                }
                else if (hasSlash && get[slashIndex+1] != ' '){
                    //System.out.printf("Slash is followed by a non-space character\n");
                    this.FILE = new byte[] {};
                    for (int j = slashIndex ; get[j] != ' ' ; ++j, ++slashIndex){
                        this.FILE = appendToByteArr (this.FILE, get[j]); 
                    }
                }
            }


            else if (!isHttp && !isFtp){
                // If neither ftp:// nor http:// is not present in GET line, read from start (index 4) until a space
                this.FILE = new byte[] {};
                for (int i=start ; i < get.length && get[i] != ' ' ; ++i){
                    this.FILE = appendToByteArr (this.FILE, get[i]);
                }
            }

            // check if anything changed at all in HOST and FILE
            boolean hasChar = false;
            for (byte b : this.HOST) {
                if (!hasChar && b != 0) {
                    hasChar = true;
                } else if (!hasChar) {
                    throw new Exception("no HOST");
                } else if (b == 0) {
                    break;
                }
            }
            hasChar = false;
            if (hasFile) {
                for (byte b : this.FILE) {
                    if (!hasChar && b != 0) {
                        hasChar = true;
                    } else if (!hasChar) {
                        throw new Exception("no FILE");
                    } else if (b == 0) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            return -1;
        }
        if (isHttp){return 1;}
        else if (isFtp){return 2;}
        return 0;
    }


    // Note: dns() must set PREFERRED
    // preferred = lowest delay time
    int dns(int X)  // NOC - do not change this signature; X is whatever you want
    {
        try {
            InetAddress[] inetAddresses = InetAddress.getAllByName(byteToString(this.HOST));
            
            long[] times = new long[inetAddresses.length];

            // time response time per inetaddress
            for (int i = 0; i < inetAddresses.length; i++) {
                long start = System.nanoTime();
                boolean reachable = inetAddresses[i].isReachable(5000);
                long end = System.nanoTime();

                if (reachable) {
                    times[i] = end - start;
                }
            }
            // pick out the one with shortest response time
            int shortest = -1;
            for (int i = 0; i < times.length; i++) {
                if ((shortest == -1 || times[i] < times[shortest]) && inetAddresses[i] instanceof Inet4Address) {
                    shortest = i;
                }
            }
            // set PREFERRED
            this.PREFERRED = inetAddresses[shortest];
            
			/*
            // For P3 Extension, pick the first IP address returned by DNS
            this.PREFERRED = inetAddresses[0];
			*/

        } catch (Exception e) {
            return -1;
        }
        return 0;
    }

    int run(int X)  // NOC - do not change the signature for run()
    {
        ServerSocket s0 = null; // NOC - this is the listening socket
        Socket       s1 = null; // NOC - this is the accept-ed socket i.e. client
        byte []      b0 = new byte[65536] ;  // ADC - general purpose buffer

        // ADC here
        s0 = this.serverSocket;
        while ( true ) {        // NOC - main loop, do not change this!
            // listens for requests from a single client
            // ADC from here to LOOPEND : add or change code
            try {
                if (!s0.isBound()) {
                    s0.bind(new InetSocketAddress(this.PORT), 20);
                }
                s1 = s0.accept();


                while (!s1.isClosed()) {
                    this.totalRequests++;
                    // print format:
                    //     (totalRequests) Incoming client connection from [A.B.C.D:nnnnn] to me [E.F.G.H:nnnnn]
                    System.out.printf("(%s) Incoming client connection from [%s:%4d] to me [%s:%4d]\n",
                            this.totalRequests, s1.getInetAddress().getHostAddress(), s1.getPort(),
                            s0.getInetAddress().getLocalHost().getHostAddress(), this.PORT);

                    int len = b0.length;
                    int off = 0;
                    byte[] last4bytes = new byte[] {-1, -1, -1, -1};
                    int firstNull = -1;
                    boolean l4bAreCRLF = false;
                    do {
                        int numBytesRead = s1.getInputStream().read(b0, off, len);
                
                    // Mark starting time
                    startTime = System.currentTimeMillis();

                        if (numBytesRead == -1) {
                            s1.close();
                            break;
                        }
                        off += numBytesRead;
                        len -= numBytesRead;
                        // look for first null byte
                        for (int i = 0; i < b0.length; i++) {
                            if (b0[i] == 0) {
                                firstNull = i;
                                break;
                            }
                        }
                        // [... \r \n \r \n 0]?
                        if (firstNull >= 4) {
                            last4bytes[0] = b0[firstNull - 4];
                            last4bytes[1] = b0[firstNull - 3];
                            last4bytes[2] = b0[firstNull - 2];
                            last4bytes[3] = b0[firstNull - 1];
                            l4bAreCRLF = last4bytes[0] == '\r' && last4bytes[1] == '\n' && last4bytes[2] == '\r' && last4bytes[3] == '\n';
                        }
                    } while (!l4bAreCRLF);

                    // check for max size of 65535 byte request
                    if (b0[b0.length-1] != 0 || off == 0) {
                        b0 = new byte[65536];
                        this.HOST = new byte[1024];
                        this.PREFERRED = null;
                        s1.close();
                        break;
                    }

                    // Set b0 to byte[] userReqFull 
                    this.userReqFull = appendByteArrayToByteArrayWithLength(userReqFull, 0, b0, off);

                    int parseStatus = this.parse(b0); // set HOST as 's' 'i' 't' 'e' '.' 'c' 'o' 'm'

                    // Measure time taken for parse() 
                    System.out.printf("Parse() done. Time took = %f\n", (float)(System.currentTimeMillis()-startTime)/1000);                

                    OutputStream os = s1.getOutputStream();
                    // Part 1 - do this:
                    // REQ: www.berkeley.edu
                    if (this.isRequest) {
                        userReq = appendByteArray(this.REQ, this.HOST);
                        userReq = appendByteArray(userReq, this.CRLF); 
                        os.write(userReq);
                    }
                    int dnsStatus = this.dns(0);  // sets PREFERRED
                    
                    // Measure time taken for dns()
                    //System.out.printf("dns() done. Time took = %f\n", (float)(System.currentTimeMillis()-startTime)/1000);                

                    // IP: 1.2.3.5 (PREFERRED)
                    // IP: ERROR (Your error string here)
                    if (this.isRequest) {
                        os.write(this.IP);
                    }
                    if ((parseStatus != 0 && parseStatus != 1 && parseStatus != 2) || dnsStatus != 0) {
                        if (this.isRequest) {
                            os.write(this.ERROR);
                            if (parseStatus != 0 && parseStatus != 1 && parseStatus != 2) {
                                os.write(this.PARSE_ERROR);
                            } else if (dnsStatus != 0) {
                                os.write(this.DNS_ERROR);
                            }
                            os.write('\n');
                        }
                    } else if (this.PREFERRED != null) {
                        if (this.isRequest) {
                            os.write(charToByte(this.PREFERRED.getHostAddress().toCharArray()));
                            os.write(charToByte(new char[]{' ', '(', 'P', 'R', 'E', 'F', 'E', 'R', 'R', 'E', 'D', ')'}));
                            os.write('\n');
                        }
                    }

                    // reset the buffers and close the connection
                    if (isHttp || !isFtp){
                        try {
                            int bytesTrsf = http_fetch(s1);
                            System.out.printf("    REQ: %s (%d bytes transferred)\n", this.byteToString(this.HOST), bytesTrsf);
                        } catch (IOException e){
                            System.out.println(e.toString());
                        }
                    } else if (isFtp) {
                        ftp_fetch(s1);
                    }

                    b0 = new byte[65536];
                    this.HOST = new byte[1024];
                    this.PREFERRED = null;
                    // s1.close();
                    // Part 1 ends here
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

    /* Part 2 - hints
    is it http_fetch or ftp_fetch ??
    nbytes = http_fetch(s1) or ftp_fetch(s1);
    LOG "REQ http://site.com/dir1/dir2/file2.html transferred nbytes"
    */
            // LOOPEND
        } // NOC - main loop
    }

    int http_fetch(Socket c) throws IOException // NOC - don't change signature
    {
        System.out.printf("http_fetch() reached. Time took = %f\n", (float)(System.currentTimeMillis()-startTime)/1000);                

        // c == client
        byte [] temp_data = new byte[5000000];
        byte [] data = new byte [20971520];
        byte [] httpVer = new byte[] {' ','H','T','T','P','/','1','.','1'};
        byte [] httpVer_noSpace = new byte[] {'H','T','T','P','/','1','.','1',' '};
        byte [] httpRespCode = new byte[3];
        byte [] redir_url = new byte [1024];
        byte [] CRLF = {'\r','\n'};
        int peerReadIndex = 0;
        int bytes_read = 0;
        int temp_bytes_read = 0;
        int iteration = 0;


        try {
            Socket p = new Socket(this.PREFERRED, 80); // peer, connection to HOST

            // Open an input stream from peer, and an output stream to peer
            InputStream fromPeer = p.getInputStream();
            OutputStream toPeer = p.getOutputStream();

            // Open an output stream to client
            OutputStream toClient = c.getOutputStream();

            
            // Check if header includes User-Agent: and other headers excluding GET (1st line) and Host:
            if (containsSubArray (userReqFull, new byte[] {'U', 's', 'e', 'r', '-', 'A', 'g', 'e', 'n', 't'}, 0)){
                    hasUserAgent = true;
            }
            

            // Create a GET request to be sent to server
            byte[] getRequest = buildGETRequest(this.FILE,
                  httpVer,
                  charToByte(this.PREFERRED.getHostName().toCharArray()));

            int subArrayIndex = -1;
            boolean sawHost = false;
            byte[] temp = new byte[1024];
            userReqToSend = getRequest;
            for (int i = 0 ; i < userReqFull.length ; ++i){
                
                if (userReqFull[i] == '\r' && userReqFull[i+1] == '\n' && userReqFull[i+2] == '\r' && userReqFull[i+3] == '\n'){
                    break;
                } 
                else if (userReqFull[i] == '\r' && userReqFull[i+1] == '\n' && userReqFull[i+2] != -1){
                    subArrayIndex = i+2;
                    multipleHeader = true;
                    break;
                } 
            }
    
            // While each header line doesn't contain "Host: ", append it to userReqToSend
            if (subArrayIndex != -1){
                for (int i = subArrayIndex, k=0 ; i < userReqFull.length ; ++i, ++k){
                    temp[k] = userReqFull[i];
                    if (userReqFull[i] == '\r' && userReqFull[i+1] == '\n' && userReqFull[i+2] == '\r' && userReqFull[i+3] == '\n'){
                        if (!containsSubArray(temp, new byte[]{'H', 'o', 's', 't', ':', ' '}, 0)){
                            userReqToSend = appendByteArrayToByteArrayWithLength(userReqToSend, -1, temp, k+1);
                            userReqToSend = appendByteArray(userReqToSend, new byte[] {'\n', '\r', '\n'});
                        }
                        break;
                    } 
                    else if (userReqFull[i] == '\n' && userReqFull[i-1] == '\r' && userReqFull[i+1] != -1){
                        if (!containsSubArray(temp, new byte[]{'H', 'o', 's', 't', ':', ' '}, 0)){
                            userReqToSend = appendByteArrayToByteArrayWithLength(userReqToSend, -1, temp, k+1);
                        }
                        temp = new byte[1024];
                        k=-1;
                    }
                }
            }
            
            if (userReqToSend != null){
                toPeer.write(userReqToSend);
            }

            else {
            // send a GET request to peer before getting file
            toPeer.write(getRequest);
            }

            // Measure time taken to http_fetch()
            System.out.printf("request sent to server in http_fetch(). Time took = %f\n\n", (float)(System.currentTimeMillis()-startTime)/1000);                
        

            // Read the server's response into data[], and save total bytes read into bytes_read
            while ((temp_bytes_read = fromPeer.read(data)) != -1){

                // Write to client as data is received from server
                toClient.write(data, 0, temp_bytes_read);
                ++iteration;                
                
                // During initial iteration, save response code and analyze it
                if (iteration == 0){
                    
                    // Check the HTTP version on server's response
                    int i;
                    for (i=0 ; i < httpVer_noSpace.length ; ++i){
                        if (data[i] != httpVer_noSpace[i]){
                            System.out.printf("505 Incorrect HTTP version\n");
                        }
                    }

                    // Save peer's response code
                    for (int j=0 ; i < httpVer_noSpace.length+3 ; ++i, ++j){
                        httpRespCode[j] = data[i];
                    }

                    // Response code is 301
                    if (httpRespCode[0] == '3' && httpRespCode[1] == '0' && httpRespCode[2] == '1'){
                        System.out.printf("301 Moved Permanently\n");
                    }

                    // Response code is 400
                    else if (httpRespCode[0] == '4' && httpRespCode[1] == '0' && httpRespCode[2] == '0'){
                        System.out.printf("400 Bad Request\n");
                    }

                    // Response code is 404
                    else if (httpRespCode[0] == '4' && httpRespCode[1] == '0' && httpRespCode[2] == '4'){
                        System.out.printf("404 Not Found\n");
                    }

                    // if 200 OK, send data to client (socket c)
                    else if (httpRespCode[0] == '2' && httpRespCode[1] == '0' && httpRespCode[2] == '0'){
                    }
                }

                bytes_read += temp_bytes_read;

                if (bytes_read > maxSize*10){
                    System.out.printf("File is over the maximum file size limit");
                    toClient.write(new byte[]{'F', 'i', 'l', 'e', ' ', 's', 'i', 'z', 'e', ' ', 'i', 's', ' ', 'o', 'v', 'e', 'r', ' ', 'l', 'i', 'm', 'i', 't'});

                    toClient.close();
                    fromPeer.close();
                    toPeer.close();
                    p.close();
                    c.close();
                
                    return -1;
                }

                // Wait for 10 milli-seconds to see if there is next available data
                try{
                    Thread.sleep(10);
                } catch (InterruptedException e){
                    System.out.printf("InterruptedException: %s\n", e);
                }
                if (fromPeer.available() == 0) {break;}
            } 

            System.out.printf("Total bytes read is %d\n\n", bytes_read);

            System.out.printf("Data read from server and sent to client in http_fetch(). Time took = %f\n", (float)(System.currentTimeMillis()-startTime)/1000);                
        
            
            // print response from server
            int x;
            for (x=0 ; x < bytes_read && data[x] != -1 ; ++x){
                System.out.printf("The value of first non-null byte in data[] is %c at index %d", data[x], x);
                System.out.print((char)data[x]);
            }


            // Close all intput/output streams, then close sockets to client and to peer
            toClient.close();
            fromPeer.close();
            toPeer.close();
            p.close();
            c.close();

            // Measure time taken to finish http_fetch()
            System.out.printf("http_fetch() finished. Time took = %f\n\n", (float)(System.currentTimeMillis()-startTime)/1000);                

        
            // return bytes transferred
            return bytes_read;
        } catch (IOException e){
            System.out.println(e.toString());
        }
        // shouldn't reach here
        return -1;
    }



    int ftp_fetch(Socket c) // NOC - don't change signature
    {
        // do FTP transaction with peer, get file, send back to c
        // Note: do not 'store' the file locally; it must be sent
        // back as it arrives
        //
        // return bytes transferred
        int hostPort;
        int bytes_read = 0;
        int temp_bytes_read = 0;
        int temp_resp_read = 0;
        int header_length = 0;
        int message_length = 0;
        Socket dataSocket = null;
        byte[] temp_message = new byte[65536];
        byte[] message = new byte[65536];
        byte[] data = new byte[maxSize];

        byte[] retrieveCommand = {'R', 'E', 'T', 'R', ' '};
        byte[] fetchCommand_1 = combineByteArrays(retrieveCommand, FILE);
        byte[] fetchCommand = combineByteArrays(fetchCommand_1, new byte[]{'\r','\n'});


        try {
            Socket p = new Socket(this.PREFERRED, 21);

            // Declare input/output streams for control & data connections to server
            // Declare output stream to client
            InputStream commInputStream, dataInputStream;
            OutputStream commOutputStream, dataOutputStream, clientOutputStream;

            commInputStream = p.getInputStream();
            commOutputStream = p.getOutputStream();

            // Prepare a stream to send data to client
            clientOutputStream = c.getOutputStream();
            
            header_length = commInputStream.read(message);


            // Print server's response
            System.out.printf("Response 0 is \n");
            for (int i=0 ; i<message.length ; ++i){
                System.out.printf("%c",message[i]);
            }

            if(message[0] != '2' || message[1] != '2' || message[2] != '0') {
                System.out.println("Unable to connect.");
                
                // Write error messsage from server to client, then close connections
                clientOutputStream.write(message); 
                clientOutputStream.close();
                commInputStream.close();
                commOutputStream.close();
                p.close();
                c.close();
                return -1;
            }

            // Reset the message []
            else {
                for (int i=0 ; i < header_length ; ++i){
                    message[i]=0;
                }
            }

            commOutputStream.write(new byte[] {
                'U', 'S', 'E', 'R', ' ', 
                //'a', 'n', 'o', 'n', 'y', 'm', 'o', 'u', 's', 'f','t','p','\r', '\n'
                'f','t','p','\r','\n'
            });

            // Read response from server on username, and save the length of response
            message_length = commInputStream.read(message);

            // Print server's response
            System.out.printf("Response 1 is \n");
            for (int i=0 ; i<message.length ; ++i){
                System.out.printf("%c",message[i]);
            }

            if(message[0] != '3' || message[1] != '3' || message[2] != '1') {
                System.out.println("Invalid User name.");

                // Write error messsage from server to client, then close connections
                clientOutputStream.write(message); 
                clientOutputStream.close();
                commInputStream.close();
                commOutputStream.close();
                p.close();
                c.close();
                return -1;
            }

            else {
                // Send password to server
                commOutputStream.write(new byte[] {
                'P', 'A', 'S', 'S', ' ', 
                'b','v','1','0','2','@','n','j','i','t','.','e','d','u','\r','\n'});

                for (int i=0 ; i < message_length ; ++i){
                    message[i]=0;
                }
            }
            
            // Read response from server on password
            message_length = commInputStream.read(message);

            //Second option to use while loop to read response
            message_length = 0;
            while ((temp_resp_read = commInputStream.read(temp_message)) != -1){
                for (int i = message_length ; i < message_length + temp_resp_read ; ++i){
                    message[i] = temp_message[i];
                }
                message_length += temp_resp_read;
                
                try{
                    Thread.sleep(1000);
                } catch (InterruptedException e){
                    System.out.printf("InterruptedException: %s\n", e);
                }
                if (commInputStream.available() == 0) {break;}
            };


            for (int i=0 ; i < message_length ; ++i){
                message[i]=0;
            }


            // Tell the server we want a passive connection 
            commOutputStream.write(new byte[] {'P', 'A', 'S', 'V', '\r', '\n'});
            
            // The next message will be the port to listen to for data connection
            message_length = commInputStream.read(message);

            // Read response from server on PASV
            System.out.printf("Response 3 is \n");

            for (int i=0 ; i<message.length ; ++i){
                System.out.printf("%c",message[i]);
            }


            hostPort = parsePASVResponse(message);

            for (int i=0 ; i < message_length; ++i){
                message[i]=0;
            }           

			// Print port for data connection
            System.out.printf("Port for data connection is %d ", hostPort);            
            System.out.println();

            
            // Establish data connection to server
            dataSocket = new Socket(this.PREFERRED, hostPort);    // This line fails to connect
            dataInputStream = dataSocket.getInputStream();
            dataOutputStream = dataSocket.getOutputStream();

            // Tell the server to retrieve the following file 
            // 		Command: RETR {FILEPATH}
            commOutputStream.write(fetchCommand);
            message_length = commInputStream.read(message);


            // Print response from server
            for (int i=0 ; i < message_length ; ++i){
                System.out.printf("%c",message[i]);
            }

            if(message[0] != '1' || message[1] != '5' || message[2] != '0') {
                System.out.println("Requested action not taken. File unavailable.");

                // Write error messsage from server to client, then close connections
                clientOutputStream.write(message); 
                clientOutputStream.close();
                commInputStream.close();
                commOutputStream.close();
                dataInputStream.close();
                dataOutputStream.close();
                dataSocket.close();
                p.close();
                c.close();

                return -1;
            }

            // Read file (data) from server
            while ((temp_bytes_read = dataInputStream.read(data)) != -1){
                
                // Calculate total bytes read
                bytes_read += temp_bytes_read;
                
                // Check for maximum file size
                if (bytes_read > maxSize*10){
                    System.out.printf("File to be transferred is over the maximum file size limit");
                    
                    // Write error messsage from server to client, then close connections
                    clientOutputStream.write(new byte[]{'F', 'i', 'l', 'e', ' ', 's', 'i', 'z', 'e', ' ', 'i', 's', ' ', 'o', 'v', 'e', 'r', ' ', 'l', 'i', 'm', 'i', 't'}); 
                    clientOutputStream.close();
                    commInputStream.close();
                    commOutputStream.close();
                    dataInputStream.close();
                    dataOutputStream.close();
                    dataSocket.close();
                    p.close();
                    c.close();
                
                    return -1;
                }
                
                // Write to client as data is received from server
                clientOutputStream.write(data, 0, temp_bytes_read);

                // Wait for 10 milli-seconds to see if there is next available data
                try{
                    Thread.sleep(10);
                } catch (InterruptedException e){
                    System.out.printf("InterruptedException: %s\n", e);
                }
                if (dataInputStream.available() == 0) {break;}

            }



            System.out.printf("    REQ: %s%s (%d bytes transferred)\n", this.byteToString(this.HOST), this.byteToString(this.FILE), bytes_read);
            commInputStream.close();
            commOutputStream.close();
            dataInputStream.close();
            dataOutputStream.close();
            clientOutputStream.close();
            p.close();
            dataSocket.close();
            c.close();

            //System.out.printf("File Transfer Complete.");
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        } 
        
        return 0;
    }




// Helper functions

    private String byteToString(byte[] buffer) {
        // find first null (0) byte
        int length = 0;
        for (byte b : buffer) {
            if (b != 0) {
                length++;
            }
        }
        return new String(buffer, 0, length);
    }

    private byte[] charToByte(char[] chars) {
        byte[] bytes = new byte[chars.length];
        for (int i = 0; i < chars.length; i++) {
            bytes[i] = (byte) chars[i];
        }
        return bytes;
    }



    /**
     * Given two byte arrays, this function combines the two arrays.
     *
     * @param a: Is the first byte array
     * @param b: Is the second byte array.
     *
     * @return: Returns a new byte array containing the data from a + b
     */

    private byte[] combineByteArrays(byte[] a, byte[] b) {
        int totalLength = a.length + b.length;
        byte[] result = new byte[totalLength];

        int i = 0;
        for(; i < a.length; i++) {
            result[i] = a[i];
        }

        for(int j = 0; j < b.length && i < totalLength; j++, i++) {
            result[i] = b[j];
        }

        return result;
    }



     /**
     * This function takes in a response message recieved from the PASV FTP
     * command, and returns the port number that the remote host will be
     * listening on.
     *
     * Response Message Format: 227 Entering Passive Mode (64,170,98,33,151,31).
     *
     * @param message: An array containing the response from PASV
     *
     * @return: Returns the port number contained in the response message.
     */

    public int parsePASVResponse(byte[] message) {
        final char delimiter = ',';
        final char endToken = ')';

        int startIndex = 0;
        int octetIndex = 0;
        int octetLength = 0;
        int octet4len = 0 , octet5len = 0;
        byte[][] octets = new byte[6][3];

        //Ignore everything up till opening parenthesis.
        for(int i = 0; i < message.length; i++) {

            if(message[i] != '(') { continue; }

            else {
                startIndex = i + 1;
                break;
            }
        }

        //Parse Octets
        for(int i = startIndex, j = 0; i < message.length; i++) {
            //No more octets
            if(message[i] == endToken) {
                octet5len = octetLength;
                break;
            }

            if(message[i] == delimiter) {
                if (octetIndex == 4){
                    octet4len = octetLength;
                }
                else if (octetIndex == 5){
                    octet5len = octetLength;
                }
                octetIndex++;
                j = 0;
                octetLength = 0;
                continue;
            }

            octets[octetIndex][j] = message[i];
            octetLength++;
            j++;
        }

        int firstHalf = byteArrayToInt(octets[4],octet4len);
        int secondHalf = byteArrayToInt(octets[5],octet5len);

        return firstHalf * 256 + secondHalf;
    }

    /**
     * Warning This is a hardcoded function. It expects a byte array of length 3.
     * 
     * @param b: Byte array of length 3.
     * 
     * @return returns the integer value stored in b. 
     */
    private int byteArrayToInt(byte[] b, int len)
    {
        int a = 0;
        int c = 0;
        
        for(int i = 0; i < len; i++) {
            c = (int) b[i] - 48;
            
            if (len == 3){
                if(i == 0) {
                    c = c * 100;
                }
                
                else if(i == 1) {
                    c = c * 10; 
                }
            }
            else if (len == 2){
                if (i==0){
                    c *= 10;
                }
            }
            a += c;
        }
        
        return a;
    }


    private int[] appendToIntArr(int[] array, int element) {
        int[] out = new int[array.length+1];
        for (int i = 0; i < array.length; i++) {
            out[i] = array[i];
        }
        out[array.length] = element;
        return out;
    }


    private byte[] appendToByteArr(byte[] array, byte element) {
        byte[] out = new byte[array.length+1];
        for (int i = 0; i < array.length; i++) {
            out[i] = array[i];
        }
        out[array.length] = element;
        return out;
    }


    private byte[][] appendToByteArrArr(byte[][] array, byte[] element) {
        byte[][] out = new byte[array.length+1][];
        for (int i = 0; i < array.length; i++) {
            out[i] = array[i];
        }
        out[array.length] = element;
        return out;
    }


    private boolean containsSubArray (byte[] array, byte[] subarray, int start){
        int counter = 0;
        for (int i = start; i<array.length ; i++){
            if (array[i] == subarray[counter]){
                counter++;
            }
            else { counter = 0;}
            if (counter == subarray.length){
                return true;
            }
        }
        return false;
    }


    private byte[] subarray(byte[] array, int start, int end) {
        byte[] out = new byte[end-start];
        for (int i = 0, j = start; j < end; i++, j++) {
            out[i] = array[j];
        }
        return out;
    }


    private byte[] subarrayFtp(byte[] array){

        byte[] newHost = new byte[(HOST.length)+4];
        for (int i = 0 ; i < newHost.length ; ++i){
            if (i == 0) {newHost[i]='f';}
            else if (i == 1) {newHost[i]='t';}
            else if (i == 2) {newHost[i]='p';}
            else if (i == 3) {newHost[i]='.';}
            else {newHost[i] = array[i-4];}
        }
        return newHost;
    }


    private byte[] subarrayFtpRev(byte[] array){
        
        byte[] newHost = new byte[(HOST.length)-4];
        for (int i = 0, j = 4 ; i < newHost.length ; ++i, ++j){
            newHost[i] = array[j];
        }
        return newHost;
    }


    private void printArray(byte[] bytes) {
        for (byte b : bytes) {
            System.out.print(printByte(b));
        }
        System.out.println();
    }


    private String printByte(byte b) {
        switch (b) {
            case '\r': {
                return "\\r";
            }
            case '\n': {
                return "\\n";
            }
            case 0: {
                return "\\0";
            }
            default: {
                return "" + (char)b;
            }
        }
    }

    /**
     * Given two byte arrays a, and b. This function creates a new array
     * and appends b to a.
     *
     * @param a: The first byte array.
     *
     * @param aLength: The number of bytes to copy from a.
     *
     * @param b: The byte array to append.
     *
     * @param bLength: The number of bytes to copy from b.
     *
     * @return: A new byte array, containing both a and b.
     */
    private byte[] appendByteArrayToByteArrayWithLength(byte[] a, int aLength, byte[] b, int bLength)
    {
        int i;
        int totalLength;
        byte [] result;

        if(aLength < 0)
            aLength = a.length;

        if(bLength < 0)
            bLength = b.length;

        totalLength = aLength + bLength;
        result = new byte[totalLength];

        for(i = 0; i < aLength; i++) {
            result[i] = a[i];
        }

        for(int j = 0; i < totalLength && j < bLength; i++, j++) {
            result[i] = b[j];
        }

        return result;
    }


    private byte[] appendByteArray(byte[] a, byte[] b) {
        return appendByteArrayToByteArrayWithLength(a, a.length, b, b.length);
    }


    private byte[] buildGETRequest(byte [] file, byte[] httpVersion, byte[] host) {
        byte[] request = new byte[] {};

        byte[] GET = new byte[] {'G', 'E', 'T', ' '};
        byte[] SPACE = new byte[] {' '};
        //byte[] CRLF = new byte[] {'\r', '\n'};

        request = appendByteArray(request, GET);
        request = appendByteArray(request, file);
        request = appendByteArray(request, httpVersion);
        request = appendByteArray(request, CRLF);
        request = appendByteArray(request, new byte[] {'H','o','s','t',':',' '});
        request = appendByteArray(request, host);
        request = appendByteArray(request, this.CRLF);
        if (!hasUserAgent){
            request = appendByteArray(request, new byte[] {'U', 's', 'e', 'r', '-', 'A', 'g', 'e', 'n', 't', ':', ' ', 'M', 'o', 'z', 'i', 'l', 'l', 'a', '/', '5', '.', '0'});

            request = appendByteArray(request, new byte[] {' ', 'W', 'i', 'n', 'd', 'o', 'w', 's', ' ', 'N', 'T', ' ', '6', '.', '1', ';', ' ', 'W', 'O', 'W', '6', '4', ';', ' ', 'r', 'v', ':', '2', '5', '.', '0', ')', ' ', 'G', 'e', 'c', 'k', 'o', '/', '2', '0', '1', '0', '0', '1', '0', '1', ' ', 'F', 'i', 'r', 'e', 'f', 'o', 'x', '/', '2', '5', '.', '0'});
        }
        request = appendByteArray(request, CRLF);
        if (!multipleHeader){
            request = appendByteArray(request, CRLF);
        }
        return request;
    }
} // class Apache
