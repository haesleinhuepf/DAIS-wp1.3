package de.mpicbg.ulman.transfer;

import de.mpicbg.ulman.imgtransfer.ProgressCallback;
import org.zeromq.ZMQ;

/**
 * AbstractTransfer
 * <p>
 * <p>
 * <p>
 * Author: @haesleinhuepf
 * 10 2018
 */
public abstract class AbstractTransfer {
    /**
     * This is an internal helper function to poll socket for incoming data,
     * it reports progress of the polling too.
     *
     * Returns null if no data has arrived during the \e timeOut interval,
     * otherwise returns the data itself.
     */
    protected static byte[] waitForIncomingData(final ZMQ.Socket socket,
                                                final String waiter, final int timeOut, final ProgressCallback log)
            throws InterruptedException
    {
        //"an entry point" for the input data
        byte[] incomingData = null;

        //"busy wait" up to the given period of time
        int timeAlreadyWaited = 0;
        while (timeAlreadyWaited < timeOut && incomingData == null)
        {
            if (timeAlreadyWaited % 10 == 0 && timeAlreadyWaited > 0)
                if (log != null) log.info(waiter+" waiting already " + timeAlreadyWaited + " seconds");

            //check if there is some data from a sender
            incomingData = socket.recv(ZMQ.NOBLOCK);

            //if nothing found, wait a while before another checking attempt
            if (incomingData == null) Thread.sleep(1000);

            ++timeAlreadyWaited;
        }

        return incomingData;
    }

}
