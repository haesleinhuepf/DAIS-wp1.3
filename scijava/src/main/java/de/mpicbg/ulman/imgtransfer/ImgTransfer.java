/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer;

import de.mpicbg.ulman.transfer.AbstractTransfer;
import net.imagej.ImgPlus;
import net.imglib2.type.NativeType;

import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * This class provides convenience, front-end functions for ImgPlus transfer.
 *
 * There are actually two main sorts of the functions. The first sort
 * consists solely of static functions, which thus cannot remember any state
 * of the transfer after they end. These functions, therefore, allow to
 * send/receive only one image.
 *
 * The second sort of functions are not static functions. Caller must create
 * an object/instance of this class in order to use them. In this way,
 * multiple images can be sent/received one by one, always single image with
 * single function call. This necessitates that the looping over images to be
 * transferred has to happen on the caller's side, but it is not a difficult
 * piece of code and brings additional flexibility for the caller to decide
 * when he is ready for the transfer (e.g., allowing for streamlining of the
 * processing of the transferred images).
 *
 * In order to transfer multiple images, just call the transfer function
 * repeatedly (every time with different image, of course). The first call
 * would open the connection and transfer the first image, consequent calls
 * would just transfer their images. REMEMBER, however, to call the hangUpAndClose()
 * after last image was transferred! Receiving party should call
 * isThereNextImage() to determine if it should receive one more image or
 * hangUpAndClose() now. Note that the image transferring protocol knows, after every
 * image is transferred, whether there shall be next transfer.
 *
 * When sending images, you can optionally advice/send hint to the receiving
 * party about the number of images you plan to send. This can be achieved by
 * providing the constructor with the particular positive number. When receiving
 * images, you can optionally read the hint with getExpectedNumberOfImages()
 * anytime after the first image has arrived.
 *
 * Their might come, if requested, a third sort that would be collecting
 * convenience functions to send/receive an array of images.
 */
public class ImgTransfer extends AbstractTransfer
{
// ------------------ static, single-image handling functions ------------------

	/**
	 * Sends/pushes an image over network to someone who is receiving it.
	 *
	 * Logging/reporting IS supported here whenever \e log != null.
	 */
	@SuppressWarnings({"unchecked","rawtypes"})
	public static <T extends NativeType<T>>
	void sendImage(final ImgPlus<T> imgP, final String addr,
	               final int timeOut, final ProgressCallback log)
			throws Exception
	{
		if (log != null) log.info("sender started");

		//init the communication side
		ZMQ.Context zmqContext = ZMQ.context(1);
		ZMQ.Socket writerSocket = null;
//		try {
			writerSocket = zmqContext.socket(ZMQ.REQ);
			if (writerSocket == null) {
				throw new Exception("cannot obtain local socket");
			}

			//peer to send data out
			writerSocket.connect(addr);

			//send the image
			ImgPacker.packAndSend((ImgPlus) imgP, writerSocket, timeOut, log);

			if (log != null) log.info("sender finished");
//		}
//		catch (ZMQException e) {
//			throw new IOException("sender crashed, ZeroMQ error: " + e.getMessage());
//		}
//		catch (Exception e) {
//			throw new IOException("sender error: " + e.getMessage());
//		}
//		finally {
//			if (log != null) log.info("sender cleaning");
//			if (writerSocket != null)
//			{
				writerSocket.disconnect(addr);
				writerSocket.close();
//			}
//			//zmqContext.close();
//			//zmqContext.term();
//		}
	}

	/**
	 * Sends/pushes an image over network to someone who is receiving it.
	 *
	 * No logging/reporting is supported here.
	 */
	public static <T extends NativeType<T>>
	void sendImage(final ImgPlus<T> imgP, final String addr,
	               final int timeOut)
			throws Exception
	{ sendImage(imgP, addr, timeOut, null); }


	/**
	 * Receives an image over network from someone who is sending/pushing it.
	 *
	 * Logging/reporting IS supported here whenever \e log != null.
	 */
	public static <T extends NativeType<T>>
	ImgPlus<?> receiveImage(final int portNo,
	                        final int timeOut, final ProgressCallback log)
	throws IOException
	{
		if (log != null) log.info("receiver started");
		ImgPlus<?> imgP = null;

		//init the communication side
		ZMQ.Context zmqContext = ZMQ.context(1);
		ZMQ.Socket listenerSocket = null;
		try {
			listenerSocket = zmqContext.socket(ZMQ.REP);
			if (listenerSocket == null)
				throw new Exception("cannot obtain local socket");

			//port to listen for incoming data
			listenerSocket.bind("tcp://*:" + portNo);

			//"an entry point" for the input data
			if (log != null) log.info("receiver waiting");
			byte[] incomingData = waitForIncomingData(listenerSocket, "receiver", timeOut, log);

			//process incoming data if there is some...
			if (incomingData != null) {
				imgP = ImgPacker.receiveAndUnpack(new String(incomingData), listenerSocket, log);
				//NB: this guy returns the ImgPlus that we desire...
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

		return imgP;
	}

	/**
	 * Receives an image over network from someone who is sending/pushing it.
	 *
	 * No logging/reporting is supported here.
	 */
	public static <T extends NativeType<T>>
	ImgPlus<?> receiveImage(final int portNo,
	                        final int timeOut)
	throws IOException
	{ return receiveImage(portNo, timeOut, null); }


	/**
	 * Serves an image over network to someone who is receiving/pulling it,
	 * it acts in fact as the sendImage() but connection is initiated from
	 * the receiver (the other peer)
	 *
	 * Logging/reporting IS supported here whenever \e log != null.
	 */
	@SuppressWarnings({"unchecked","rawtypes"})
	public static <T extends NativeType<T>>
	void serveImage(final ImgPlus<T> imgP, final int portNo,
	                final int timeOut, final ProgressCallback log)
	throws IOException
	{
		if (log != null) log.info("server started");

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
			if (log != null) log.info("server waiting for initial request");
			byte[] incomingData = waitForIncomingData(listenerSocket, "server", timeOut, log);

			//if there is no incoming data, we need to close the server
			if (incomingData == null)
				throw new RuntimeException("Image not transferred, receiver has not connected yet.");

			//there is some incoming data, check it:
			if (! new String(incomingData).startsWith("can get"))
				throw new RuntimeException("Protocol error, expected initial ping from the receiver.");

			ImgPacker.packAndSend((ImgPlus) imgP, listenerSocket, timeOut, log);

			if (log != null) log.info("server finished");
		}
		catch (ZMQException e) {
			throw new IOException("server crashed, ZeroMQ error: " + e.getMessage());
		}
		catch (Exception e) {
			throw new IOException("server error: " + e.getMessage());
		}
		finally {
			if (log != null) log.info("server cleaning");
			if (listenerSocket != null)
			{
				listenerSocket.unbind("tcp://*:" + portNo);
				listenerSocket.close();
			}
			//zmqContext.close();
			//zmqContext.term();
		}
	}

	/**
	 * Serves an image over network to someone who is receiving/pulling it,
	 * it acts in fact as the sendImage() but connection is initiated from
	 * the receiver (the other peer)
	 *
	 * No logging/reporting is supported here.
	 */
	public static <T extends NativeType<T>>
	void serveImage(final ImgPlus<T> imgP, final int portNo,
	                final int timeOut)
	throws IOException
	{ serveImage(imgP, portNo, timeOut, null); }


	/**
	 * Receives/pulls an image over network from someone who is serving it,
	 * it acts in fact as the receiveImage() but connection is initiated from
	 * this function (the receiver).
	 *
	 * Logging/reporting IS supported here whenever \e log != null.
	 */
	public static <T extends NativeType<T>>
	ImgPlus<?> requestImage(final String addr,
	                        final int timeOut, final ProgressCallback log)
	throws IOException
	{
		if (log != null) log.info("receiver started");
		ImgPlus<?> imgP = null;

		//init the communication side
		ZMQ.Context zmqContext = ZMQ.context(1);
		ZMQ.Socket writerSocket = null;
		try {
			writerSocket = zmqContext.socket(ZMQ.PAIR);
			if (writerSocket == null)
				throw new Exception("cannot obtain local socket");

			//peer to send data out
			writerSocket.connect(addr);

			//send the request
			if (log != null) log.info("receiver initial request sent");
			writerSocket.send("can get");

			//wait for connection to happen...
			//wait for reply (already with image data)
			if (log != null) log.info("receiver waiting");
			byte[] incomingData = waitForIncomingData(writerSocket, "receiver", timeOut, log);

			//process incoming data if there is some...
			if (incomingData != null)
				imgP = ImgPacker.receiveAndUnpack(new String(incomingData), writerSocket, log);
			else
				throw new RuntimeException("Image not transferred, server has not replied yet.");

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
			if (writerSocket != null)
			{
				writerSocket.disconnect(addr);
				writerSocket.close();
			}
			//zmqContext.close();
			//zmqContext.term();
		}

		return imgP;
	}

	/**
	 * Receives/pulls an image over network from someone who is serving it,
	 * it acts in fact as the receiveImage() but connection is initiated from
	 * this function (the receiver).
	 *
	 * No logging/reporting is supported here.
	 */
	public static <T extends NativeType<T>>
	ImgPlus<?> requestImage(final String addr,
	                        final int timeOut)
	throws IOException
	{ return requestImage(addr, timeOut, null); }


// ------------------ non-static, multiple-images handling functions ------------------

	///names of the status of the object of this class
	public enum TransferMode
	{ SEND, RECEIVE, SERVE, REQUEST, CLOSED }

	/**
	 * Represents the "purpose" of this object to prevent users from mixing
	 * between the front-end convenience functions.
	 */
	private TransferMode transferMode;

	///connection stuff: peer's address -- used for SEND, REQUEST
	final String addr;
	///connection stuff: my port -- used for RECEIVE, SERVE
	final int portNo;

	///connection stuff: time in seconds for the first handshake
	final int timeOut;
	///optional reporter of the per-image progress
	final ProgressCallback log;

	///"hinting" constructor only for SEND senders, \e _log may be null
	public ImgTransfer(final String _addr, final int _expectedNumberOfImages, final int _timeOut, final ProgressCallback _log)
	{
		transferMode = TransferMode.SEND;
		addr = _addr;
		expectedNumberOfImages = _expectedNumberOfImages;
		timeOut = _timeOut;
		log = _log;

		//not used:
		portNo = 54545;
	}

	///constructor only for RECEIVE receivers, \e _log may be null
	public ImgTransfer(final int _portNo, final int _timeOut, final ProgressCallback _log)
	{
		transferMode = TransferMode.RECEIVE;
		portNo = _portNo;
		timeOut = _timeOut;
		log = _log;

		//not used:
		addr = null;
	}

	///"hinting" constructor only for SERVE senders, \e _log may be null
	public ImgTransfer(final int _portNo, final int _expectedNumberOfImages, final int _timeOut, final ProgressCallback _log)
	{
		transferMode = TransferMode.SERVE;
		portNo = _portNo;
		expectedNumberOfImages = _expectedNumberOfImages;
		timeOut = _timeOut;
		log = _log;

		//not used:
		addr = null;
	}

	///constructor only for REQUEST receivers, \e _log may be null
	public ImgTransfer(final String _addr, final int _timeOut, final ProgressCallback _log)
	{
		transferMode = TransferMode.REQUEST;
		addr = _addr;
		timeOut = _timeOut;
		log = _log;

		//not used:
		portNo = 54545;
	}


	/**
	 * how many images are expected to be sent away (when \e transferMode == SEND),
	 * or to be received (when \e transferMode == RECEIVE),
	 *
	 * Note that in the latter case the variable is not decremented after
	 * every transfer (it is not remainingNumberOfImages).
	 */
	private int expectedNumberOfImages = 0;

	/**
	 * Mirrors the current state of the image transfer protocol, i.e., it
	 * flags if last image has been received. This variable is valid only
	 * when \e transferMode == RECEIVE.
	 */
	private boolean allTransferred = false;

	///reads the expectedNumberOfImages variable
	public int getExpectedNumberOfImages()
	{ return expectedNumberOfImages; }

	///reads inverted value of the allTransferred variable
	public boolean isThereNextImage()
	{ return (allTransferred == false); }

	///standard alternative name to this.isThereNextImage()
	public boolean hasNext()
	{ return (allTransferred == false); }


	///holds the ZeroMQ context
	private ZMQ.Context zmqContext = ZMQ.context(1);
	///holds, if not null, the opened ZeroMQ socket
	private ZMQ.Socket zmqSocket = null;

	///closes the ZeroMQ stuff
	private void cleanUp()
	{
		if (log != null)
		{
			//report properly...
			switch (transferMode)
			{
			case SEND:
				log.info("sender cleaning");
				break;
			case SERVE:
				log.info("server cleaning");
				break;
			case RECEIVE:
			case REQUEST:
				log.info("receiver cleaning");
				break;
			default:
				//TransferMode.CLOSED, do nothing
				return;
			}
		}

		//this renders the object useless for transferring...
		transferMode = TransferMode.CLOSED;

		//close whatever remained opened
		if (zmqSocket != null)
		{
			if (transferMode == TransferMode.SEND || transferMode == TransferMode.REQUEST)
				zmqSocket.disconnect(addr);
			else
				zmqSocket.unbind("tcp://*:" + portNo);
			zmqSocket.close();
		}
		if (zmqContext != null)
		{
			//zmqContext.close();
			//zmqContext.term();
		}
	}

	///(emergency) clean up...
	@Override
	public void finalize()
	{ cleanUp(); }


	/**
	 * Sends/pushes an image over network to someone who is receiving it.
	 */
	@SuppressWarnings({"unchecked","rawtypes"})
	public <T extends NativeType<T>>
	void sendImage(final ImgPlus<T> imgP)
	throws IOException
	{
		try {
			if (this.transferMode != TransferMode.SEND)
				throw new Exception("this transferrer cannot be used for sending");

			if (log != null) log.info("sender started");

			//socket already obtained? aka first run?
			if (zmqSocket == null)
			{
				//first run
				zmqSocket = zmqContext.socket(ZMQ.PAIR);
				if (zmqSocket == null)
					throw new Exception("cannot obtain local socket");

				//peer to send data out
				zmqSocket.connect(addr);
			}

			//send always the "hint" before the image
			if (log != null) log.info("sending header: v0 expect "+expectedNumberOfImages+" images");
			zmqSocket.send("v0 expect "+expectedNumberOfImages+" images");

			//send the image
			ImgPacker.packAndSend((ImgPlus) imgP, zmqSocket, timeOut, log);

			if (log != null) log.info("sender finished");
		}
		catch (ZMQException e) {
			cleanUp();
			throw new IOException("sender crashed, ZeroMQ error: " + e.getMessage());
		}
		catch (Exception e) {
			cleanUp();
			throw new IOException("sender error: " + e.getMessage());
		}
	}

	///this guys sends also the "v0 header before the image", but without the image
	public
	void hangUpAndClose()
	throws IOException
	{
		try {
			if (this.transferMode != TransferMode.SEND && this.transferMode != TransferMode.SERVE)
				throw new Exception("this transferrer cannot signal that last image was _sent_");

			if (zmqSocket == null)
				throw new Exception("no socket opened");

			if (log != null)
			{
				if (transferMode == TransferMode.SEND)
					log.info("sender hanging up");
				else
					log.info("server hanging up");
			}
			zmqSocket.send("v0 hangup");

			//close the socket too! -> happens in the 'finally' catch-section
		}
		catch (ZMQException e) {
			throw new IOException("sender crashed, ZeroMQ error: " + e.getMessage());
		}
		catch (Exception e) {
			throw new IOException("sender error: " + e.getMessage());
		}
		finally {
			//clean up in any case, since this the end of the transfer
			cleanUp();
		}
	}

	/**
	 * Receives an image over network from someone who is sending/pushing it.
	 */
	public <T extends NativeType<T>>
	ImgPlus<?> receiveImage()
	throws IOException
	{
		ImgPlus<?> imgP = null;

		try {
			if (this.transferMode != TransferMode.RECEIVE)
				throw new Exception("this transferrer cannot be used for receiving");

			if (log != null) log.info("receiver started");

			//input aux byte buffer:
			byte[] incomingData = null;

			//socket already obtained? aka first run?
			if (zmqSocket == null)
			{
				//first run
				zmqSocket = zmqContext.socket(ZMQ.PAIR);
				if (zmqSocket == null)
					throw new Exception("cannot obtain local socket");

				//port to listen for incoming data
				zmqSocket.bind("tcp://*:" + portNo);

				//now should read the first "v0 header"
				if (log != null) log.info("receiver waiting for first v0 header");
				incomingData = waitForIncomingData(zmqSocket, "receiver", timeOut, log);

				//process 'incomingData' and extract 'expectedNumberOfImages'
				final String msg = incomingData != null ? new String(incomingData) : null;
				if (msg != null)
				{
					if (log != null) log.info("received header: "+msg);
					if (msg.startsWith("v0"))
					{
						//extract 'expectedNumberOfImages'
						StringTokenizer headerST = new StringTokenizer(msg, " ");
						headerST.nextToken(); //positions at "v0"
						if (headerST.nextToken().startsWith("expect"))
							expectedNumberOfImages = Integer.valueOf(headerST.nextToken());
					}
					else
						throw new RuntimeException("Protocol error, expected initial v0 header from the sender.");
				}
				else
					//msg == null
					throw new RuntimeException("Image not transferred, sender has not connected yet.");
			}

			//wait again for the proper image input data
			incomingData = waitForIncomingData(zmqSocket, "receiver", timeOut, log);

			//process incoming data if there is some...
			if (incomingData != null) {
				imgP = ImgPacker.receiveAndUnpack(new String(incomingData), zmqSocket, log);
				//NB: this guy returns the ImgPlus that we desire...

				//wait for the next "v0 header" to see if there is more images coming
				//NB: this next header signifies there is a new image already being sent out
				if (log != null) log.info("receiver waiting for next v0 header");
				incomingData = waitForIncomingData(zmqSocket, "receiver", timeOut, log);

				if (log != null && incomingData != null)
					log.info("received header: "+new String(incomingData));
			}

			//either timeout happened (incomingData = null), or there is some data...
			if (incomingData == null || new String(incomingData).startsWith("v0 hangup"))
			{
				allTransferred = true;
				if (log != null) log.info("receiver hanging up");

				//close the socket too!
				cleanUp();
			}
			else
				//we have received some msg for sure, hope it is the v0 header...
				//NB: we consider the v0 header only for the first time
				allTransferred = false;

			if (log != null) log.info("receiver finished");
		}
		catch (ZMQException e) {
			cleanUp();
			throw new IOException("receiver crashed, ZeroMQ error: " + e.getMessage());
		}
		catch (Exception e) {
			cleanUp();
			throw new IOException("receiver error: " + e.getMessage());
		}

		return imgP;
	}

	/**
	 * Serves an image over network to someone who is receiving/pulling it.
	 * Similar in principle to its static buddy this.serveImage(...).
	 */
	@SuppressWarnings({"unchecked","rawtypes"})
	public <T extends NativeType<T>>
	void serveImage(final ImgPlus<T> imgP)
	throws IOException
	{
		try {
			if (this.transferMode != TransferMode.SERVE)
				throw new Exception("this transferrer cannot be used for serving");

			if (log != null) log.info("server started");

			//socket already obtained? aka first run?
			if (zmqSocket == null)
			{
				//first run
				zmqSocket = zmqContext.socket(ZMQ.PAIR);
				if (zmqSocket == null)
					throw new Exception("cannot obtain local socket");

				//port to listen for incoming data
				zmqSocket.bind("tcp://*:" + portNo);

				//wait for the ping from the requester
				if (log != null) log.info("server waiting for initial request");
				byte[] incomingData = waitForIncomingData(zmqSocket, "server", timeOut, log);

				//if there is no incoming data, we need to close the server
				if (incomingData == null)
					throw new RuntimeException("Image not transferred, receiver has not connected yet.");

				//there is some incoming data, check it:
				if (! new String(incomingData).startsWith("can get"))
					throw new RuntimeException("Protocol error, expected initial ping from the receiver.");
			}

			//send always the "hint" before the image
			if (log != null) log.info("sending header: v0 expect "+expectedNumberOfImages+" images");
			zmqSocket.send("v0 expect "+expectedNumberOfImages+" images");

			//send the image
			ImgPacker.packAndSend((ImgPlus) imgP, zmqSocket, timeOut, log);

			if (log != null) log.info("server finished");
		}
		catch (ZMQException e) {
			cleanUp();
			throw new IOException("server crashed, ZeroMQ error: " + e.getMessage());
		}
		catch (Exception e) {
			cleanUp();
			throw new IOException("server error: " + e.getMessage());
		}
	}

	/**
	 * Receives/pulls an image over network from someone who is serving it.
	 * Similar in principle to its static buddy this.requestImage(...).
	 */
	public <T extends NativeType<T>>
	ImgPlus<?> requestImage()
	throws IOException
	{
		ImgPlus<?> imgP = null;

		try {
			if (this.transferMode != TransferMode.REQUEST)
				throw new Exception("this transferrer cannot be used for requesting");

			if (log != null) log.info("receiver started");

			//input aux byte buffer:
			byte[] incomingData = null;

			//socket already obtained? aka first run?
			if (zmqSocket == null)
			{
				//first run
				zmqSocket = zmqContext.socket(ZMQ.PAIR);
				if (zmqSocket == null)
					throw new Exception("cannot obtain local socket");

				//peer to send data out
				zmqSocket.connect(addr);

				//very first thing: send the ping to the server
				if (log != null) log.info("receiver initial request sent");
				zmqSocket.send("can get");

				//now should read the first "v0 header"
				if (log != null) log.info("receiver waiting for first v0 header");
				incomingData = waitForIncomingData(zmqSocket, "receiver", timeOut, log);

				//process 'incomingData' and extract 'expectedNumberOfImages'
				final String msg = incomingData != null ? new String(incomingData) : null;
				if (msg != null)
				{
					if (log != null) log.info("received header: "+msg);
					if (msg.startsWith("v0"))
					{
						//extract 'expectedNumberOfImages'
						StringTokenizer headerST = new StringTokenizer(msg, " ");
						headerST.nextToken(); //positions at "v0"
						if (headerST.nextToken().startsWith("expect"))
							expectedNumberOfImages = Integer.valueOf(headerST.nextToken());
					}
					else
						throw new RuntimeException("Protocol error, expected initial v0 header from the sender.");
				}
				else
					//msg == null
					throw new RuntimeException("Image not transferred, server has not replied yet.");
			}

			//wait again for the proper image input data
			incomingData = waitForIncomingData(zmqSocket, "receiver", timeOut, log);

			//process incoming data if there is some...
			if (incomingData != null) {
				imgP = ImgPacker.receiveAndUnpack(new String(incomingData), zmqSocket, log);
				//NB: this guy returns the ImgPlus that we desire...

				//wait for the next "v0 header" to see if there is more images coming
				//NB: this next header signifies there is a new image already being sent out
				if (log != null) log.info("receiver waiting for next v0 header");
				incomingData = waitForIncomingData(zmqSocket, "receiver", timeOut, log);

				if (log != null && incomingData != null)
					log.info("received header: "+new String(incomingData));
			}

			//either timeout happened (incomingData = null), or there is some data...
			if (incomingData == null || new String(incomingData).startsWith("v0 hangup"))
			{
				allTransferred = true;
				if (log != null) log.info("receiver hanging up");

				//close the socket too!
				cleanUp();
			}
			else
				//we have received some msg for sure, hope it is the v0 header...
				//NB: we consider the v0 header only for the first time
				allTransferred = false;

			if (log != null) log.info("receiver finished");
		}
		catch (ZMQException e) {
			cleanUp();
			throw new IOException("receiver crashed, ZeroMQ error: " + e.getMessage());
		}
		catch (Exception e) {
			cleanUp();
			throw new IOException("receiver error: " + e.getMessage());
		}

		return imgP;
	}

}
