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
            writerSocket = zmqContext.socket(ZMQ.REQ);
            if (writerSocket == null)
                throw new Exception("cannot obtain local socket");

            //peer to send data out
            writerSocket.connect(addr);

            //send the image
            String msg = new String("v1");

            if (log != null) log.info("a");
            //dimensionality data
            msg += " dimNumber " + textP.length();
            if (log != null) log.info("a");

            //decipher the voxel type
            msg += " String";

            msg += " String ";
            if (log != null) log.info("a");

            //send header, metadata and text data afterwards
            if (log != null) log.info("sending header: "+msg);
            writerSocket.send(msg.getBytes(), 0);
            //ArrayPacker.waitForFirstMessage(socket, timeOut);

            msg = writerSocket.recvStr();
            if (log != null) log.info("Received answer: " + msg);

            if (log != null) log.info("sending the text...");
            writerSocket.send(textP.getBytes(), 0);
            //ArrayPacker.waitForFirstMessage(socket, timeOut);

            msg = writerSocket.recvStr();
            if (log != null) log.info("Received answer: " + msg);
            if (! msg.startsWith("done"))
                throw new RuntimeException("Protocol error, expected final confirmation from the receiver.");
            if (log != null) log.info("sending finished...");

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
            listenerSocket = zmqContext.socket(ZMQ.REP);
            //if (listenerSocket == null)
            //    throw new Exception("cannot obtain local socket");

            //port to listen for incoming data
            listenerSocket.bind("tcp://*:" + portNo);

            //"an entry point" for the input data
            if (log != null) log.info("receiver waiting");
            byte[] incomingData = waitForIncomingData(listenerSocket, "receiver", timeOut, log);

            //process incoming data if there is some...
            if (incomingData != null) {
                if (log != null) log.info("Received header2: " + new String(incomingData));
                if (log != null) log.info("sending answer1");
                listenerSocket.send("ready");
                if (log != null) log.info("answer1 sent");
                incomingData = waitForIncomingData(listenerSocket, "receiver", timeOut, log);
                if (incomingData != null){
                    text = new String(incomingData);
                    if (log != null) log.info("sending answer2");
                    listenerSocket.send("done");
                    if (log != null) log.info("answer2 sent");
                }
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
            //zmqContext.close();
            //zmqContext.term();
        }
        listenerSocket.unbind("tcp://*:" + portNo);
        listenerSocket.close();

        return text;
    }

}
