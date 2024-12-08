package cp;

import core.*;
import exceptions.*;
import phy.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Random;

public class CPProtocol extends Protocol {
    private static final int CP_TIMEOUT = 2000;
    private static final int CP_HASHMAP_SIZE = 20;
    private int cookie;
    private int id;
    private PhyConfiguration PhyConfigCommandServer;
    private PhyConfiguration PhyConfigCookieServer;
    private final PhyProtocol PhyProto;
    private final cp_role role;
    HashMap<PhyConfiguration, Cookie> cookieMap;
    Random rnd;

    private static final int MAX_ID = 65535;
    private int lastMessageId = -1;
    private int generateMessageId() {
        if (lastMessageId >= MAX_ID) {
            lastMessageId = 0;
        } else {
            lastMessageId++;
        }
        return lastMessageId;
    }

    private enum cp_role {
        CLIENT, COOKIE, COMMAND
    }

    // Constructor for clients
    public CPProtocol(InetAddress rname, int rp, PhyProtocol phyP) throws UnknownHostException {
        this.PhyConfigCommandServer = new PhyConfiguration(rname, rp, proto_id.CP);
        this.PhyProto = phyP;
        this.role = cp_role.CLIENT;
        this.cookie = -1;
    }
    // Constructor for servers
    public CPProtocol(PhyProtocol phyP, boolean isCookieServer) {
        this.PhyProto = phyP;
        if (isCookieServer) {
            this.role = cp_role.COOKIE;
            this.cookieMap = new HashMap<>();
            this.rnd = new Random();
        } else {
            this.role = cp_role.COMMAND;
        }
    }

    public void setCookieServer(InetAddress rname, int rp) throws UnknownHostException {
        this.PhyConfigCookieServer = new PhyConfiguration(rname, rp, proto_id.CP);
    }


    @Override
    public void send(String s, Configuration config) throws IOException, IWProtocolException {

        if (cookie < 0) {
            // Request a new cookie from server
            // Either updates the cookie attribute or returns with an exception
            requestCookie();
        }

        // Task 1.2.1: complete send method
        // 1a. Check that a legal command is issued
        if (s == null || s.isEmpty()) {
            throw new IllegalMsgException();
        }

        int messageId = generateMessageId();
        this.id = messageId;
        // 1b. Create a command message object
        CPCommandMsg cmdMsg = new CPCommandMsg(cookie, messageId);
        cmdMsg.create(s);


        // 1c. Send the command to the command server
        // Use the PHY layer to send the message
        this.PhyProto.send(new String(cmdMsg.getDataBytes()), this.PhyConfigCommandServer);
        System.out.println("Sending command message data");
    }

    @Override
    public Msg receive() throws IOException, IWProtocolException {
        // The server logic: If we are a cookie server, we must handle incoming cookie requests
        if (role == cp_role.COOKIE) {
            // Continuously receive messages and process them
            while (true) {
                Msg receivedMsg = null;
                try {
                    receivedMsg = this.PhyProto.receive(CP_TIMEOUT);
                    if (receivedMsg == null) continue;

                    // Check protocol id
                    if (((PhyConfiguration) receivedMsg.getConfiguration()).getPid() != proto_id.CP) {
                        // Not a CP message, ignore
                        continue;
                    }

                    String data = receivedMsg.getData();
                    if (data.startsWith("cp cookie_request")) {
                        // This is a cookie request
                        processCookieRequest(receivedMsg);
                        // After processing cookie request, continue the server loop
                        continue;
                    } else {
                        // for handling other message types (e.g., verification requests)
                        // At the moment, ignore
                        continue;
                    }

                } catch (SocketTimeoutException e) {
                    // If timeout, continue to listen
                } catch (Exception e) {
                    // Some parsing or IO exception
                    continue;
                }
            }
        } else if (role == cp_role.CLIENT) {
            // Attempt to receive response from command server
            return receiveClientResponse();
        } else if (role == cp_role.COMMAND) {
            // handle command messages
            return null;
        }
        return null;
    }
    private Msg receiveClientResponse() throws IOException, IWProtocolException {
        final int amountOfAttempts = 3;
        int attempts = 0;
        Msg receivedMsg = null;

        while (attempts < amountOfAttempts) {
            try {
                receivedMsg = this.PhyProto.receive(CP_TIMEOUT);
                if (((PhyConfiguration) receivedMsg.getConfiguration()).getPid() != proto_id.CP)
                    return null;

                Msg responseMsg = new CPCommandMsg(cookie, id);
                responseMsg = ((CPCommandMsg) responseMsg).parse(receivedMsg.getData());

                String[] responseParts = responseMsg.getData().split("\\s+");
                int receivedId = Integer.parseInt(responseParts[2]);
                String successStatus = responseParts[3];

                if (this.id == receivedId) {
                    if (successStatus.equals("ok")) {
                        return responseMsg;
                    } else if (successStatus.equals("error")) {
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                attempts++;
            } catch (Exception e) {
                attempts++;
            }
        }

        throw new CookieTimeoutException();
    }

    private void processCookieRequest(Msg receivedMsg) throws IOException, IWProtocolException {
        PhyConfiguration clientConfig = (PhyConfiguration) receivedMsg.getConfiguration();

        // Log that we've received a cookie request
        System.out.println("Received a cookie request from: " + clientConfig);

        // Check if we have space
        if (!cookieMap.containsKey(clientConfig) && cookieMap.size() >= CP_HASHMAP_SIZE) {
            // No space for a new client
            System.out.println("Rejecting cookie request from " + clientConfig + " - too many clients.");
            CPCookieResponseMsg response = new CPCookieResponseMsg(false);
            String errorData = "too_many_clients";
            response.create(errorData);
            System.out.println("Sending NAK response to client.");
            this.PhyProto.send(new String(response.getDataBytes()), clientConfig);
            return;
        }

        // Generate a new cookie
        int newCookieValue = rnd.nextInt(Integer.MAX_VALUE);
        long now = System.currentTimeMillis();
        Cookie newCookie = new Cookie(now, newCookieValue);
        // Invalidate old cookie by overwriting
        cookieMap.put(clientConfig, newCookie);

        // Log that a new cookie was issued
        System.out.println("Issued new cookie (" + newCookieValue + ") for client: " + clientConfig);


        // Respond with ACK and the new cookie
        CPCookieResponseMsg ackResponse = new CPCookieResponseMsg(true);
        ackResponse.create(String.valueOf(newCookieValue));
        System.out.println("Sending ACK response to client with cookie: " + newCookieValue);
        this.PhyProto.send(new String(ackResponse.getDataBytes()), clientConfig);
    }


    // Method for the client to request a cookie
    public void requestCookie() throws IOException, IWProtocolException {
        CPCookieRequestMsg reqMsg = new CPCookieRequestMsg();
        reqMsg.create(null);
        Msg resMsg = new CPMsg();

        boolean waitForResp = true;
        int count = 0;
        while(waitForResp && count < 3) {
            this.PhyProto.send(new String(reqMsg.getDataBytes()), this.PhyConfigCookieServer);

            try {
                Msg in = this.PhyProto.receive(CP_TIMEOUT);
                if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.CP)
                    continue;
                resMsg = ((CPMsg) resMsg).parse(in.getData());
                if(resMsg instanceof CPCookieResponseMsg)
                    waitForResp = false;
            } catch (SocketTimeoutException e) {
                count += 1;
            } catch (IWProtocolException ignored) {
            }
        }

        if(count == 3)
            throw new CookieRequestException();
        if(resMsg instanceof CPCookieResponseMsg && !((CPCookieResponseMsg) resMsg).getSuccess()) {
            throw new CookieRequestException();
        }
         assert resMsg instanceof CPCookieResponseMsg;
         this.cookie = ((CPCookieResponseMsg)resMsg).getCookie();
    }
}

class Cookie {
    private final long timeOfCreation;
    private final int cookieValue;

    public Cookie(long toc, int c) {
        this.timeOfCreation = toc;
        this.cookieValue = c;
    }

    public long getTimeOfCreation() {
        return timeOfCreation;
    }

    public int getCookieValue() { return cookieValue;}
}

