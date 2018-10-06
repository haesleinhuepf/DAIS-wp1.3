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


        final String confirmation = socket.recvStr();
        if (! confirmation.startsWith("ready"))
            throw new RuntimeException("Protocol error, expected initial confirmation from the receiver.");
    }
}
