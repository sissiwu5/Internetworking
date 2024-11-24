package cp;

import exceptions.IllegalCommandException;
import exceptions.IllegalMsgException;

import java.util.zip.CRC32;

public class CPCommandMsg extends CPMsg {
    protected static final String CP_HEADER = "cp";
    protected static final String CP_COMMAND_HEADER = "command";
    protected static final String COMMAND_RESPONSE_HEADER = "command_response";
    protected int commandId = 0;
    protected int cookie;
    protected CRC32 checksum;
    private CommandType commandType;

    public CPCommandMsg(int cookie, int commandId){
        this.commandId = commandId;
        this.cookie = cookie;
    }

    //Task 1.2.3: message class for creating command messages
    protected void create(String cmdString) {
        if (cmdString == null || cmdString.isEmpty()) {
            throw new IllegalArgumentException();
        }

        String[] cmdParts = cmdString.split("\\s+");
        if (cmdParts.length < 1) {
            throw new IllegalArgumentException();
        }

        String command = cmdParts[0];
        String message = cmdParts.length > 1 ? cmdParts[1] : "";

        if (command.equals("print")) {
            commandType = CommandType.PRINT;
        } else if (command.equals("status")) {
            commandType = CommandType.STATUS;
            message = ""; // No message for status command
        } else {
            throw new IllegalArgumentException();
        }

        //build command message
        StringBuilder commandBuilder = new StringBuilder();
        commandBuilder.append(CP_HEADER).append(" ").append(CP_COMMAND_HEADER).append(" ");
        commandBuilder.append(this.commandId).append(" ").append(this.cookie).append(" ");
        commandBuilder.append(command).append(" ").append(message.length()).append(" ").append(message);

        this.checksum = calculateChecksum(commandBuilder.toString());

        super.create(commandBuilder.toString() + " " + checksum.getValue());
    }


    // Task 1.2.3: message class for parsing command messages
    public CPMsg parse(String response) throws IllegalCommandException, IllegalMsgException {
        // Validate header
        String CP_COMMAND_RESPONSE_HEADER = CP_HEADER + " " + COMMAND_RESPONSE_HEADER;
        if (!response.startsWith(CP_COMMAND_RESPONSE_HEADER)) {
            throw new IllegalCommandException();
        }

        // Split response into parts
        String[] rspParts = response.split("\\s+");

        // Parse and validate checksum
        long rspChecksum = Long.parseUnsignedLong(rspParts[rspParts.length - 1]);
        String responseWithoutChecksum = response.substring(response.indexOf(" ") + 1, response.lastIndexOf(" ")).trim();
        CRC32 crc32 = new CRC32();
        crc32.update(responseWithoutChecksum.getBytes());
        if (rspChecksum != crc32.getValue()) {
            throw new IllegalCommandException();
        }

        // Parse other fields
        boolean isStatusCommand = this.commandType == CommandType.STATUS;
        int responseId = Integer.parseInt(rspParts[2]);
        String responseSuccess = rspParts[3];
        if (!responseSuccess.matches("ok|error")) {
            throw new IllegalCommandException();
        }
        int length = Integer.parseInt(rspParts[4]);
        String message = isStatusCommand ? rspParts[5] : "";

        // Build command message
        StringBuilder commandResponseBuilder = new StringBuilder();
        commandResponseBuilder.append(COMMAND_RESPONSE_HEADER).append(" ");
        commandResponseBuilder.append(responseId).append(" ").append(responseSuccess).append(" ");
        commandResponseBuilder.append(length).append(" ").append(message).append(" ").append(rspChecksum);

        CPMsg parsedCommandResponseMsg = new CPMsg();
        parsedCommandResponseMsg.create(commandResponseBuilder.toString());
        return parsedCommandResponseMsg;
    }

    protected CRC32 calculateChecksum(String data) {
        CRC32 checksum = new CRC32();
        checksum.update(data.getBytes());
        return checksum;
    }


    public int getCommandId() {
        return commandId;
    }
}

