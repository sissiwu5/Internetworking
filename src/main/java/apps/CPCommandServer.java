package apps;

import cp.*;
import exceptions.IWProtocolException;
import phy.PhyProtocol;

import java.io.IOException;

public class CPCommandServer {
    protected static final int COMMAND_SERVER_PORT = 2000;

    public static void main(String[] args) {

        PhyProtocol phy = new PhyProtocol(COMMAND_SERVER_PORT);

        CPProtocol cp;
        try {
            cp = new CPProtocol(phy, false);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // Start server processing
        while (true) {
            try {
                // Wait for incoming commands
                cp.receive();
            } catch (IOException e) {
                System.out.println("IO error");
                e.printStackTrace();
                return;
            } catch (IWProtocolException e) {
                System.out.println("Protocol error: " + e.getMessage());
            }
        }
    }
}
