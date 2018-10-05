package de.mpicbg.rhaase.texttransfer;

import de.mpicbg.ulman.imgtransfer.ImgPacker;
import de.mpicbg.ulman.imgtransfer.ProgressCallback;
import de.mpicbg.ulman.transfer.AbstractTransfer;
import net.imagej.ImgPlus;
import net.imglib2.type.NativeType;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.io.IOException;

/**
 * TextTransfer
 * <p>
 * <p>
 * <p>
 * Author: @haesleinhuepf
 * 10 2018
 */
public class TextTransfer extends AbstractTransfer {
    public static
    void sendText(final String textP, final String addr,
                   final int timeOut, final ProgressCallback log)
            throws IOException
    {
        if (log != null) log.info("sender started");

        //init the communication side
        ZMQ.Context zmqContext = ZMQ.context(1);
        ZMQ.Socket writerSocket = null;
        try {
            writerSocket = zmqContext.socket(ZMQ.PAIR);
            if (writerSocket == null)
                throw new Exception("cannot obtain local socket");

            //peer to send data out
            writerSocket.connect(addr);

            //send the image
            TextPacker.packAndSend(textP, writerSocket, timeOut, log);

            if (log != null) log.info("sender finished");
        }
        catch (ZMQException e) {
            throw new IOException("sender crashed, ZeroMQ error: " + e.getMessage());
        }
        catch (Exception e) {
            throw new IOException("sender error: " + e.getMessage());
        }
        finally {
            if (log != null) log.info("sender cleaning");
            if (writerSocket != null)
            {
                writerSocket.disconnect(addr);
                writerSocket.close();
            }
            //zmqContext.close();
            //zmqContext.term();
        }
    }

    /**
     * Receives an image over network from someone who is sending/pushing it.
     *
     * Logging/reporting IS supported here whenever \e log != null.
     */
    public static String receiveText(final int portNo,
                            final int timeOut, final ProgressCallback log)
            throws IOException
    {
        if (log != null) log.info("receiver started");
        String text = null;

        //init the communication side
        ZMQ.Context zmqContext = ZMQ.context(1);
        ZMQ.Socket listenerSocket = null;
        try {
            listenerSocket = zmqContext.socket(ZMQ.PAIR);
            if (listenerSocket == null)
                throw new Exception("cannot obtain local socket");

            //port to listen for incoming data
            listenerSocket.bind("tcp://*:" + portNo);

            //"an entry point" for the input data
            if (log != null) log.info("receiver waiting");
            byte[] incomingData = waitForIncomingData(listenerSocket, "receiver", timeOut, log);

            //process incoming data if there is some...
            if (incomingData != null) {
                text = new String(waitForIncomingData(listenerSocket, "receiver", timeOut, log));
            }
            else
                throw new RuntimeException("Image not transferred, sender has not connected yet.");

            if (log != null) log.info("receiver finished");
        }
        catch (ZMQException e) {
            throw new IOException("receiver crashed, ZeroMQ error: " + e.getMessage());
        }
        catch (Exception e) {
            throw new IOException("receiver error: " + e.getMessage());
        }
        finally {
            if (log != null) log.info("receiver cleaning");
            if (listenerSocket != null)
            {
                listenerSocket.unbind("tcp://*:" + portNo);
                listenerSocket.close();
            }
        }

        return text;
    }

}
