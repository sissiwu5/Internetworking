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
        // Task 1.2.2: complete receive method
        Msg receivedMsg = null;

        final int amountOfAttempts = 3;
        int attempts = 0;

        while (attempts < amountOfAttempts) {
            try {
                // 2a. Wait for response with a 3-second timeout
                receivedMsg = this.PhyProto.receive(CP_TIMEOUT);

                // Verify the message is a CP protocol message
                if (((PhyConfiguration) receivedMsg.getConfiguration()).getPid() != proto_id.CP) {
                    return null;
                }

                //2b. Parse the message
                Msg responseMsg = new CPCommandMsg(cookie, id);
                responseMsg = ((CPCommandMsg) responseMsg).parse(receivedMsg.getData());

                //2c. Check if the response matches the sent command message
                String[] responseParts = responseMsg.getData().split("\\s+");
                int receivedId = Integer.parseInt(responseParts[2]);
                String successStatus = responseParts[3];

                if(this.id == receivedId){
                    if(successStatus.equals("ok")){
                        return responseMsg;
                    } else if(successStatus.equals("error")){
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                attempts++; // increment attempt counter if timeout occurs
            } catch (Exception e) {
                attempts++; // increment attempt counter if exception occurs
            }
        }

        throw new CookieTimeoutException();
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

