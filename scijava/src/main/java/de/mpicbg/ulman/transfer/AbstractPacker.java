package de.mpicbg.ulman.transfer;

import de.mpicbg.ulman.imgtransfer.ArrayPacker;
import org.zeromq.ZMQ;

/**
 * AbstractPacker
 * <p>
 * <p>
 * <p>
 * Author: @haesleinhuepf
 * 10 2018
 */
public abstract class AbstractPacker {
    /// this function sends the header AND WAITS FOR RESPONSE
    protected static
    void packAndSendHeader(final String hdr, final ZMQ.Socket socket, final int timeOut)
    {
        //send _complete_ message with just the header
        socket.send(hdr.getBytes(), 0);
        //NB: if message is not complete (i.e. SNDMORE is flagged),
        //system/ZeroMQ will not be ready to listen for confirmation message

        //wait for response, else complain for timeout-ing
        ArrayPacker.waitForFirstMessage(socket, timeOut);
        //NB: if we got here (after the waitFor..()), some message is ready to be read out

        final String confirmation = socket.recvStr();
        if (! confirmation.startsWith("ready"))
            throw new RuntimeException("Protocol error, expected initial confirmation from the receiver.");
    }
}
