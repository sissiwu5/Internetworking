package cp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;


class CPMsg extends Msg {
    protected static final String CP_HEADER = "cp";
    @Override
    protected void create(String sentence) {
        data = CP_HEADER + " " + sentence;
        this.dataBytes = data.getBytes();
    }

    @Override
    protected Msg parse(String sentence) throws IWProtocolException {
        if (!sentence.startsWith(CP_HEADER))
            throw new IllegalMsgException();

        String[] parts = sentence.split("\\s+", 2);
        if (parts.length < 2)
            throw new IllegalMsgException();

        String messageType = parts[1].split("\\s+")[0]; // Get the message type
        CPMsg parsedMsg;

        switch (messageType) {
            case CPCookieRequestMsg.CP_CREQ_HEADER:
                parsedMsg = new CPCookieRequestMsg();
                break;
            case CPCookieResponseMsg.CP_CRES_HEADER:
                parsedMsg = new CPCookieResponseMsg();
                break;
            default:
                throw new IllegalMsgException();
        }

        parsedMsg = (CPMsg)parsedMsg.parse(parts[1]);
        return parsedMsg;
    }

}

enum CommandType {
    STATUS,
    PRINT
}