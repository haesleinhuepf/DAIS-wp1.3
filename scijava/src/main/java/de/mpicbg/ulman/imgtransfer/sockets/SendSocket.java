/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman.imgtransfer.sockets;

import de.mpicbg.ulman.imgtransfer.buffers.Sender;
import org.zeromq.ZMQ;
import java.nio.ByteBuffer;

public class SendSocket implements Socket
{
	final ZMQ.Socket socket;
	final Sender sender;

	public
	SendSocket(final ZMQ.Socket _socket, final Sender _sender)
	{
		socket = _socket;
		sender = _sender;
	}


	public
	void transmit(final Object arrayRead, int offset, int length,
	              final ByteBuffer auxBuf, final int sendOnlyFlags)
	{
		//ignores any given cache buffer, as ZMQ.Socket does not copy ByteBuffer into
		//its space; at the same time, it does not signal back if the data was transfered;
		//so, we need to create an extra ByteBuffer for every individual tranfer
		final ByteBuffer buf = ByteBuffer.allocateDirect(sender.getElemSize()*length);

		sender.send(buf, arrayRead, offset, length);
		buf.rewind();
		socket.sendByteBuffer(buf, sendOnlyFlags);
	}
}
